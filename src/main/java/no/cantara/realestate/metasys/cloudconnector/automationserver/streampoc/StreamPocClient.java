package no.cantara.realestate.metasys.cloudconnector.automationserver.streampoc;

import no.cantara.config.ApplicationProperties;
import no.cantara.realestate.automationserver.BasClient;
import no.cantara.realestate.metasys.cloudconnector.MetasysCloudConnectorException;
import no.cantara.realestate.metasys.cloudconnector.MetasysCloudconnectorApplicationFactory;
import no.cantara.realestate.metasys.cloudconnector.automationserver.MetasysClient;
import no.cantara.realestate.metasys.cloudconnector.notifications.NotificationService;
import no.cantara.realestate.security.LogonFailedException;
import no.cantara.realestate.security.UserToken;
import org.slf4j.Logger;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.slf4j.LoggerFactory.getLogger;

public class StreamPocClient {
    private static final Logger log = getLogger(StreamPocClient.class);
    private final MetasysClient metasysClient;
    private final ScheduledExecutorService scheduler;
    private UserToken userToken;

    public StreamPocClient() {
        this(MetasysClient.getInstance());

    }

    // For testing
    protected StreamPocClient(MetasysClient metasysClient) {
        this.metasysClient = metasysClient.getInstance();
        scheduler = Executors.newScheduledThreadPool(1);
        findLatestUserToken();
        scheduleTokenRefresh();

    }

    protected void findLatestUserToken() {
        userToken = metasysClient.getUserToken();
        String accessToken = userToken.getAccessToken();
        String shortAccessToken = shortenedAccessToken(accessToken);
        log.debug("Latest user token: {}. Expires: {}", shortAccessToken, userToken.getExpires());
    }

    protected void scheduleTokenRefresh() {
        // 2 min before user token expires, call findLatestUserToken
        // create new shcedule to call findLatestUserToken
        Runnable reminderTask = () -> {
            this.findLatestUserToken();
            this.scheduleTokenRefresh();
        };
        scheduler.schedule(reminderTask, 30, TimeUnit.SECONDS);
    }

    public UserToken getUserToken() {
        return userToken;
    }

    private static MetasysClient initializeMetasysClient(ApplicationProperties config) {
        MetasysClient basClient = null;
        String apiUrl = config.get("sd.api.url");
        String username = config.get("sd.api.username");
        String password = config.get("sd.api.password");
        try {
            URI apiUri = new URI(apiUrl);
            log.info("Connect to Metasys API: {} with username: {}", apiUri, username);
            NotificationService notificationService = new NotificationService() {
                @Override
                public boolean sendWarning(String service, String warningMessage) {
                    log.info("Sending warning message: {}", warningMessage);
                    return true;
                }

                @Override
                public boolean sendAlarm(String service, String alarmMessage) {
                    log.info("Sending alarm message: {}", alarmMessage);
                    return true;
                }

                @Override
                public boolean clearService(String service) {
                    log.info("Clearing service: {}", service);
                    return true;
                }
            };
            basClient = MetasysClient.getInstance(username, password, apiUri, notificationService); //new MetasysApiClientRest(apiUri, notificationService);
            log.info("Running with a live REST SD.");
        } catch (URISyntaxException e) {
            throw new MetasysCloudConnectorException("Failed to connect SD Client to URL" + apiUrl, e);
        } catch (LogonFailedException e) {
            throw new MetasysCloudConnectorException("Failed to logon SD Client. URL used" + apiUrl, e);
        }
        return basClient;
    }

    public static String shortenedAccessToken(String accessToken) {
        return accessToken.length() > 200 ? accessToken.substring(0, 50) + "..." + accessToken.substring(accessToken.length() - 50) : accessToken;
    }

    public static void main(String[] args) {
        ApplicationProperties config = new MetasysCloudconnectorApplicationFactory()
                .conventions(ApplicationProperties.builder())
                .buildAndSetStaticSingleton();
        BasClient basClient = initializeMetasysClient(config);
        StreamPocClient streamPocClient = new StreamPocClient();

        //Verify that token refresh is working
        String accessToken = streamPocClient.getUserToken().getAccessToken();
        String shortAccessToken = shortenedAccessToken(accessToken);
        log.info("AT: {}", shortAccessToken);
        do {
            try {
                String newAccessToken = streamPocClient.getUserToken().getAccessToken();
                String newShortAccessToken = shortenedAccessToken(newAccessToken);
                if (!newShortAccessToken.equals(shortAccessToken)) {
                    log.info("AT: {} -> {}, expires: {}", newAccessToken, newShortAccessToken, streamPocClient.getUserToken().getExpires());
                    accessToken = newShortAccessToken;
                    shortAccessToken = newShortAccessToken;
                } else {
                    log.trace("Access token not changed. Expires: {}", streamPocClient.getUserToken().getExpires());
                }
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } while (true);
    }
}
