package no.messom.chatbot.rasa;

import jakarta.ws.rs.core.HttpHeaders;
import no.messom.chatbot.ChatBot42Exception;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.net.URIBuilder;
import org.slf4j.Logger;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

public class RasaRestClient {
    private static final Logger log = getLogger(RasaRestClient.class);
    public static final String RASA_REST_WEBHOOK = "http://localhost:5005/webhooks/rest/webhook";
    private final ClosableHttpClient httpClient;

    public RasaRestClient() {
        this(HttpClients.createDefault());
    }

    public RasaRestClient(CloseableHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public String message(String sender, String message) {
        log.trace("Send message to Rasa. Sender {}, message {}", sender, message);
        String body = null;
        HttpPost request = null;
        List<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair("sender", sender));
        nvps.add(new BasicNameValuePair("message", message));
        try {
            URI uri = new URIBuilder(RASA_REST_WEBHOOK)
                    .build();
            request = new HttpPost(uri);
            request.setEntity(new StringEntity("{\"sender\":\"" + sender + "\",\"message\":\"" + message + "\"}"));
            request.addHeader(HttpHeaders.CONTENT_TYPE, "application/json");
//        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
            CloseableHttpResponse response = httpClient.execute(request);

            int httpCode = response.getCode();
            if (httpCode == 200 || httpCode == 400) {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    body = EntityUtils.toString(entity);
                    log.info("Received body: {}", body);
//                    MetasysTrendSampleResult trendSampleResult = TrendSamplesMapper.mapFromJson(body);

                }
            } else {
                log.trace("Response code: {}", httpCode);
            }
        } catch (Exception e) {
//            setUnhealthy();
            throw new ChatBot42Exception("Failed to talk to RASA for senderId " + sender
                    + ", with message " + message + ". Reason: " + e.getMessage(), e);
        }
        return body;
    }
}


