package no.cantara.realestate.metasys.cloudconnector.automationserver;

import io.github.resilience4j.ratelimiter.RateLimiter;
import no.cantara.realestate.cloudconnector.RealestateCloudconnectorException;
import no.cantara.realestate.cloudconnector.StatusType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SdClientSimulatorTest {



    @Test
    void shouldReturnStubbedResponse() {
        RateLimiter dummyLimiter = mock(RateLimiter.class);
        when(dummyLimiter.acquirePermission()).thenReturn(true);

        SdClientSimulator service = spy(new SdClientSimulator(dummyLimiter));

        Integer responseCode = service.subscribePresentValueChange(null, null);
        assertEquals(400, responseCode);
    }

    @Test
    void shouldRespectRateLimitFailure() {
        RateLimiter dummyLimiter = mock(RateLimiter.class);
        when(dummyLimiter.acquirePermission()).thenReturn(false);

        SdClientSimulator service = spy(new SdClientSimulator(dummyLimiter));
        RealestateCloudconnectorException exception = assertThrows(RealestateCloudconnectorException.class, () ->
                service.subscribePresentValueChange(null, null));
        assertTrue( exception.getMessage().contains("RateLimit exceeded"));
        assertEquals(StatusType.RETRY_MAY_FIX_ISSUE, exception.getStatusType());
    }
}