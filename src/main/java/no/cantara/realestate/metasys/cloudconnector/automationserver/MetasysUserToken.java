package no.cantara.realestate.metasys.cloudconnector.automationserver;

import no.cantara.realestate.security.UserToken;

import javax.json.bind.annotation.JsonbProperty;
import java.time.Instant;

public class MetasysUserToken extends UserToken {
    @JsonbProperty("accessToken")
    private String accessToken;
    @JsonbProperty("expires")
    private Instant expires;
    private int validSeconds = -1;
    private Instant createdAt;

    public MetasysUserToken() {
        createdAt = Instant.now();
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public Instant getExpires() {
        if (validSeconds > 0) {
            return createdAt.plusSeconds(validSeconds);
        }
        return expires;
    }

    public void setExpires(Instant expires) {
        this.expires = expires;
    }

    public int getValidSeconds() {
        return validSeconds;
    }

    public void setValidSeconds(int validSeconds) {
        this.validSeconds = validSeconds;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "MetasysUserToken{" +
                "accessToken='" + accessToken + '\'' +
                ", expires=" + expires +
                ", validSeconds=" + validSeconds +
                ", createdAt=" + createdAt +
                '}';
    }
}
