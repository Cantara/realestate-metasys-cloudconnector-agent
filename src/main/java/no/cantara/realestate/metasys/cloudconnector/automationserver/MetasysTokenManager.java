package no.cantara.realestate.metasys.cloudconnector.automationserver;

import no.cantara.realestate.automationserver.BasClient;
import no.cantara.realestate.security.LogonFailedException;
import no.cantara.realestate.security.UserToken;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.slf4j.LoggerFactory.getLogger;

public class MetasysTokenManager {
    private static final Logger log = getLogger(MetasysTokenManager.class);
    public static final int REFRESH_TOKEN_BEFORE_EXPIRES_SECONDS = 300;
    private static MetasysTokenManager instance;
    private final BasClient basClient;
    private UserToken currentToken;
    private final ScheduledThreadPoolExecutor scheduler;
    private final Object tokenLock = new Object();

    private MetasysTokenManager(BasClient basClient) {
        this.basClient = basClient;
        this.scheduler = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(1);
        scheduler.setRemoveOnCancelPolicy(true);
        refreshAndSchedule();
    }

    public static synchronized MetasysTokenManager getInstance(BasClient basClient) {
        if (instance == null) {
            instance = new MetasysTokenManager(basClient);
        }
        return instance;
    }

    public UserToken getCurrentToken() {
        synchronized (tokenLock) {
            if (currentToken == null ||
                    currentToken.getExpires() == null ||
                    currentToken.getExpires().isBefore(Instant.now().plusSeconds(30))) {
                refreshToken();
            }
            return currentToken;
        }
    }

    private void refreshAndSchedule() {
        try {
            refreshToken();

            // Beregn tid til neste refresh (5 minutter før utløp)
            long delayUntilNextRefresh = 0;
            if (currentToken != null && currentToken.getExpires() != null) {
                delayUntilNextRefresh = Duration.between(
                        Instant.now(),
                        currentToken.getExpires().minusSeconds(REFRESH_TOKEN_BEFORE_EXPIRES_SECONDS)
                ).toSeconds();

                if (delayUntilNextRefresh < 0) {
                    delayUntilNextRefresh = 0;
                }
            }

            // Planlegg neste refresh
            log.info("Token vil bli fornyet om {} sekunder", delayUntilNextRefresh);
            scheduler.schedule(this::refreshAndSchedule, delayUntilNextRefresh, TimeUnit.SECONDS);

        } catch (Exception e) {
            log.error("Feil ved fornyelse av token. Prøver igjen om 60 sekunder", e);
            scheduler.schedule(this::refreshAndSchedule, 60, TimeUnit.SECONDS);
        }
    }

    private void refreshToken() {
        synchronized (tokenLock) {
            try {
                log.debug("Fornyer Metasys access token");
                if (currentToken == null) {
                    basClient.logon();
                    currentToken = basClient.getUserToken();
                } else {
                    currentToken = basClient.refreshToken();
                }
                log.info("Token fornyet. Nytt utløp: {}", currentToken.getExpires());
            } catch (LogonFailedException e) {
                log.error("Kunne ikke logge inn på Metasys", e);
                throw new RuntimeException("Token-fornyelse feilet", e);
            }
        }
    }
}
