package no.cantara.realestate.metasys.cloudconnector.automationserver;

import no.cantara.realestate.automationserver.BasClient;
import no.cantara.realestate.cloudconnector.notifications.NotificationService;
import no.cantara.realestate.observations.PresentValue;
import no.cantara.realestate.security.LogonFailedException;
import no.cantara.realestate.security.UserToken;
import no.cantara.realestate.sensors.SensorId;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

public class MetasysClientCB implements BasClient, AutoCloseable {

    MetasysClientCB(String username, String password, URI apiUri, NotificationService notificationService,
                    Clock clock, HttpClient httpClient, Duration recoveryCheckInterval) {
    }

    void start() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    void loginAtStartup() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    boolean attemptLogin() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    void recoveryCheck() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public boolean openForBusiness() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public Set<MetasysTrendSample> findTrendSamplesByDate(String objectId, int take, int skip, Instant onAndAfterDateTime) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public PresentValue findPresentValue(SensorId sensorId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public Integer subscribePresentValueChange(String subscriptionId, String objectId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void logon() throws LogonFailedException {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public boolean isLoggedIn() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public String getName() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public boolean isHealthy() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public long getNumberOfTrendSamplesReceived() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public UserToken getUserToken() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public UserToken refreshToken() throws LogonFailedException {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public Instant getWhenLastTrendSampleReceived() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void close() {
    }
}
