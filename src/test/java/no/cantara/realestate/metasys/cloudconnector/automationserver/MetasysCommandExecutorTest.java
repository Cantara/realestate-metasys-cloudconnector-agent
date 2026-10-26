package no.cantara.realestate.metasys.cloudconnector.automationserver;

import no.cantara.realestate.automationserver.TrendNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetasysCommandExecutorTest {

    private static final URI API_URI = URI.create("https://metasys.example.com/api/v4/");

    @Mock
    private HttpClient httpClient;
    @Mock
    private HttpResponse<String> response;

    private MetasysCircuitBreaker breaker;
    private Deque<String> tokens;
    private AtomicInteger tokenRequests;
    private MetasysCommandExecutor executor;

    @BeforeEach
    void setUp() {
        breaker = new MetasysCircuitBreaker(new MutableClock(Instant.parse("2026-09-05T10:00:00Z")));
        tokens = new ArrayDeque<>(List.of("token-1", "token-2", "token-3"));
        tokenRequests = new AtomicInteger();
        Supplier<String> tokenSource = () -> {
            tokenRequests.incrementAndGet();
            return tokens.poll();
        };
        executor = new MetasysCommandExecutor(API_URI, httpClient, tokenSource, breaker);
    }

    @Test
    void openBreakerRejectsLocallyWithoutHttpCall() {
        breaker.open("HTTP 403");

        assertThrows(CircuitOpenException.class, () -> executor.execute(new EchoCommand()));

        verifyNoInteractions(httpClient);
        assertEquals(0, tokenRequests.get(), "No token is needed for a call that is never sent");
    }

    @Test
    void successReturnsTheCommandsParsedResult() throws Exception {
        respondWith(200, "hello");

        String result = executor.execute(new EchoCommand());

        assertEquals("parsed:hello", result);
        assertFalse(breaker.isOpen());
    }

    @Test
    void requestIsBuiltFromApiUriPathQueryTokenAndHeaders() throws Exception {
        respondWith(200, "ok");
        Map<String, String> query = new LinkedHashMap<>();
        query.put("startTime", "2026-09-05T10:00:00Z");
        query.put("page", "1");
        MetasysRequest request = new MetasysRequest("GET", "objects/abc/samples", query,
                Map.of("METASYS-SUBSCRIBE", "sub-1"), null);

        executor.execute(new EchoCommand(request));

        HttpRequest sent = capturedRequests().get(0);
        assertEquals("GET", sent.method());
        assertEquals("https://metasys.example.com/api/v4/objects/abc/samples?startTime=2026-09-05T10%3A00%3A00Z&page=1",
                sent.uri().toString());
        assertEquals(Optional.of("Bearer token-1"), sent.headers().firstValue("Authorization"));
        assertEquals(Optional.of("application/json"), sent.headers().firstValue("Content-Type"));
        assertEquals(Optional.of("sub-1"), sent.headers().firstValue("METASYS-SUBSCRIBE"));
        assertEquals(Optional.of(Duration.ofSeconds(30)), sent.timeout());
    }

    @Test
    void jsonBodyIsSentWithTheGivenMethod() throws Exception {
        respondWith(200, "ok");
        MetasysRequest request = new MetasysRequest("POST", "subscriptions", Map.of(), Map.of(), "{\"a\":1}");

        executor.execute(new EchoCommand(request));

        HttpRequest sent = capturedRequests().get(0);
        assertEquals("POST", sent.method());
        assertEquals(7L, sent.bodyPublisher().orElseThrow().contentLength());
    }

    @Test
    void tokenSourceIsAskedOnEverySend() throws Exception {
        respondWith(200, "ok");

        executor.execute(new EchoCommand());
        executor.execute(new EchoCommand());

        List<HttpRequest> sent = capturedRequests();
        assertEquals(2, tokenRequests.get());
        assertEquals(Optional.of("Bearer token-1"), sent.get(0).headers().firstValue("Authorization"));
        assertEquals(Optional.of("Bearer token-2"), sent.get(1).headers().firstValue("Authorization"));
    }

    @Test
    void forbiddenOpensBreakerAndThrowsWithStatus() throws Exception {
        respondWith(403, "{\"message\":\"Access denied\"}");

        MetasysApiException e = assertThrows(MetasysApiException.class, () -> executor.execute(new EchoCommand()));

        assertEquals(403, e.getStatusCode());
        assertFalse(e instanceof CircuitOpenException, "The call was sent; this is not a local rejection");
        assertTrue(breaker.isOpen());
        verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void unauthorizedOpensBreakerAndThrowsWithStatus() throws Exception {
        respondWith(401, "");

        MetasysApiException e = assertThrows(MetasysApiException.class, () -> executor.execute(new EchoCommand()));

        assertEquals(401, e.getStatusCode());
        assertTrue(breaker.isOpen());
    }

    @Test
    void callAfterForbiddenIsRejectedLocally() throws Exception {
        respondWith(403, "");
        assertThrows(MetasysApiException.class, () -> executor.execute(new EchoCommand()));

        assertThrows(CircuitOpenException.class, () -> executor.execute(new EchoCommand()));

        verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void otherStatusesAreInterpretedByTheCommand() throws Exception {
        respondWith(404, "missing");

        FindTrendSamplesCommand command = new FindTrendSamplesCommand("abc",
                Instant.parse("2026-09-05T10:00:00Z"), Instant.parse("2026-09-05T11:00:00Z"));

        assertThrows(TrendNotFoundException.class, () -> executor.execute(command));
        assertFalse(breaker.isOpen());
    }

    @Test
    void serverErrorIsNotRetriedAndDoesNotOpenBreaker() throws Exception {
        respondWith(500, "boom");

        MetasysApiException e = assertThrows(MetasysApiException.class, () -> executor.execute(new EchoCommand()));

        assertEquals(500, e.getStatusCode());
        assertFalse(breaker.isOpen());
        verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void ioFailureIsMetasysApiExceptionAndDoesNotOpenBreaker() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpTimeoutException("request timed out"));

        MetasysApiException e = assertThrows(MetasysApiException.class, () -> executor.execute(new EchoCommand()));

        assertInstanceOf(IOException.class, e.getCause());
        assertFalse(breaker.isOpen());
    }

    @Test
    void tokenSourceThatOpensTheBreakerPreventsTheSend() {
        MetasysCommandExecutor executorWithFailingRefresh = new MetasysCommandExecutor(API_URI, httpClient, () -> {
            breaker.open("refreshToken returned 403");
            return "stale-token";
        }, breaker);

        assertThrows(CircuitOpenException.class, () -> executorWithFailingRefresh.execute(new EchoCommand()));

        verifyNoInteractions(httpClient);
    }

    private void respondWith(int status, String body) throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when(response.statusCode()).thenReturn(status);
        lenient().when(response.body()).thenReturn(body);
    }

    @SuppressWarnings("unchecked")
    private List<HttpRequest> capturedRequests() throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, atLeastOnce()).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        return captor.getAllValues();
    }

    private static final class EchoCommand implements MetasysCommand<String> {
        private final MetasysRequest request;

        EchoCommand() {
            this(MetasysRequest.get("objects/abc", Map.of()));
        }

        EchoCommand(MetasysRequest request) {
            this.request = request;
        }

        @Override
        public MetasysRequest request() {
            return request;
        }

        @Override
        public String onSuccess(String body) {
            return "parsed:" + body;
        }
    }
}
