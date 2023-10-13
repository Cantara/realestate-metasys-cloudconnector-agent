package no.messom.chatbot.rasa;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RasaRestClientTest {

    private RasaRestClient rasaClient;
    private CloseableHttpClient httpClient;
    private String sender = "1234567890";
    private CloseableHttpResponse response;

    @BeforeEach
    void setUp() {
        httpClient = mock(CloseableHttpClient.class);
        rasaClient = new RasaRestClient(httpClient);
        response = mock(CloseableHttpResponse.class);

    }

    @Test
    void singlePartResponse() throws IOException {

        when(httpClient.execute(any())).thenReturn(response);
        when(response.getCode()).thenReturn(200);
        when(response.getEntity()).thenReturn(new StringEntity("""
        [{"recipient_id":"1234567890","text":"Hey! How are you?"}]
        """));
        String responseString = rasaClient.message(sender, "Hello");
        assertTrue(responseString.contains(sender));
        assertTrue(responseString.contains("Hey! How are you?"));
    }

    @Test
    void multiPartResponse() throws IOException {
        String cheerUpResponse = """
                [{"recipient_id":"1234567890","text":"Here is something to cheer you up:"},{"recipient_id":"1234567890","image":"https:\\/\\/i.imgur.com\\/nGF1K8f.jpg"},{"recipient_id":"1234567890","text":"Did that help you?"}]
                """; // Note: The response is not valid JSON, but it is valid JSON Lines
        when(httpClient.execute(any())).thenReturn(response);
        when(response.getCode()).thenReturn(200);
        when(response.getEntity()).thenReturn(new StringEntity(cheerUpResponse));
        String responseString = rasaClient.message(sender, "Sad");
        assertTrue(responseString.contains(sender));
        assertTrue(responseString.contains("cheer you up"));
        assertTrue(responseString.contains("image"));
        assertTrue(responseString.contains("Did that help you"));

    }
}