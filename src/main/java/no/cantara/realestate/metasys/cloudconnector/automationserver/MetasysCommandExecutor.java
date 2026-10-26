package no.cantara.realestate.metasys.cloudconnector.automationserver;

import org.apache.hc.core5.net.URIBuilder;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Sends {@link MetasysCommand}s to Metasys, guarded by a {@link MetasysCircuitBreaker}.
 * <p>
 * One executor lives as long as its client. It owns everything a command must not know: the
 * apiUri, the HttpClient, where the access token comes from, and the breaker. The rules are:
 * <ul>
 *   <li>Breaker open: throw {@link CircuitOpenException} without sending anything.</li>
 *   <li>2xx: {@link MetasysCommand#onSuccess(String)}.</li>
 *   <li>401/403: open the breaker and throw. No login, refresh or retry here; recovery belongs to
 *       the client, which knows how often Metasys allows a login.</li>
 *   <li>Any other status: {@link MetasysCommand#onError(int, String)}. No retry on 500.</li>
 *   <li>IOException or timeout: {@link MetasysApiException}; the breaker stays as it is, since a
 *       network blip says nothing about the session.</li>
 * </ul>
 * The breaker is checked only before sending. A request already in flight is never aborted.
 * <p>
 * Thread-safe as long as the token source is.
 * <p>Example:
 * <pre>{@code
 * MetasysCommandExecutor executor = new MetasysCommandExecutor(apiUri, httpClient, this::currentAccessToken, breaker);
 * Set<MetasysTrendSample> samples = executor.execute(new FindTrendSamplesCommand(objectId, from, to));
 * }</pre>
 */
public class MetasysCommandExecutor {
    private static final Logger log = getLogger(MetasysCommandExecutor.class);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final URI apiUri;
    private final HttpClient httpClient;
    private final Supplier<String> accessTokenSource;
    private final MetasysCircuitBreaker breaker;

    /**
     * @param apiUri            base uri of the Metasys API, ending with a slash, e.g. {@code https://host/api/v4/}
     * @param httpClient        client used for every request
     * @param accessTokenSource asked for the current access token before every send, so a refreshed
     *                          token is picked up without the executor knowing about refresh
     * @param breaker           breaker that is checked before sending and opened on 401/403
     */
    public MetasysCommandExecutor(URI apiUri, HttpClient httpClient, Supplier<String> accessTokenSource,
                                  MetasysCircuitBreaker breaker) {
        this.apiUri = Objects.requireNonNull(apiUri, "apiUri");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.accessTokenSource = Objects.requireNonNull(accessTokenSource, "accessTokenSource");
        this.breaker = Objects.requireNonNull(breaker, "breaker");
    }

    /**
     * Send the command and let it interpret the response.
     *
     * @param command what to send and how to read the answer
     * @param <T>     result type of the command
     * @return the command's result
     * @throws CircuitOpenException if the breaker is open; nothing was sent
     * @throws MetasysApiException  on 401/403 (breaker is now open), on network failure, or as
     *                              mapped by the command's {@code onError}
     */
    public <T> T execute(MetasysCommand<T> command) {
        rejectIfOpen();
        String accessToken = accessTokenSource.get();
        // The token source may have tried a refresh and opened the breaker; do not send with a dead token.
        rejectIfOpen();

        MetasysRequest request = command.request();
        HttpResponse<String> response = send(request, accessToken);
        int status = response.statusCode();
        String body = response.body();

        if (status >= 200 && status < 300) {
            return command.onSuccess(body);
        }
        if (status == 401 || status == 403) {
            String reason = "HTTP " + status + " on " + request.method() + " " + request.path();
            breaker.open(reason);
            throw new MetasysApiException(reason, status);
        }
        return command.onError(status, body);
    }

    private void rejectIfOpen() {
        if (breaker.isOpen()) {
            throw new CircuitOpenException("Metasys circuit is open since " + breaker.lastTransitionAt()
                    + ". Reason: " + breaker.reason());
        }
    }

    private HttpResponse<String> send(MetasysRequest request, String accessToken) {
        URI uri = toUri(request);
        try {
            return httpClient.send(toHttpRequest(request, uri, accessToken), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            log.debug("Metasys request {} {} failed: {}", request.method(), uri, e.getMessage());
            throw new MetasysApiException("Metasys request " + request.method() + " " + request.path()
                    + " failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MetasysApiException("Metasys request " + request.method() + " " + request.path()
                    + " was interrupted", e);
        }
    }

    private HttpRequest toHttpRequest(MetasysRequest request, URI uri, String accessToken) {
        HttpRequest.BodyPublisher body = request.jsonBody() == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(request.jsonBody());
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .timeout(REQUEST_TIMEOUT)
                .method(request.method(), body);
        for (Map.Entry<String, String> header : request.headers().entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }
        return builder.build();
    }

    private URI toUri(MetasysRequest request) {
        try {
            URIBuilder builder = new URIBuilder(apiUri + request.path());
            request.query().forEach(builder::addParameter);
            return builder.build();
        } catch (URISyntaxException e) {
            throw new MetasysApiException("Invalid Metasys request path: " + request.path(), e);
        }
    }
}
