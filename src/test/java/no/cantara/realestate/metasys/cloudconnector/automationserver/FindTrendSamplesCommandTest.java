package no.cantara.realestate.metasys.cloudconnector.automationserver;

import no.cantara.realestate.automationserver.TrendNotFoundException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FindTrendSamplesCommandTest {

    private static final String OBJECT_ID = "05ccd193-a3f9-5db7-9c72-61987ca3d8dd";
    private static final Instant FROM = Instant.parse("2026-09-05T10:00:00Z");
    private static final Instant TO = Instant.parse("2026-09-05T11:01:00Z");

    private static final String TREND_SAMPLES_JSON = """
            {
              "total": 2,
              "items": [
                {
                  "value": {
                    "value": 9398.001,
                    "units": "https://metasysserver/api/v4/enumSets/507/members/19"
                  },
                  "timestamp": "2020-09-16T05:20:00Z",
                  "isReliable": true
                },
                {
                  "value": {
                    "value": 9400.001,
                    "units": "https://metasysserver/api/v4/enumSets/507/members/19"
                  },
                  "timestamp": "2020-09-16T05:21:00Z",
                  "isReliable": true
                }
              ],
              "next": null,
              "attribute": "attributeEnumSet.presentValue",
              "previous": null,
              "self": "http://localhost:1080/api/v4/objects/05ccd193-a3f9-5db7-9c72-61987ca3d8dd/trendedAttributes/presentValue/samples",
              "objectUrl": "http://localhost:1080/api/v4/objects/05ccd193-a3f9-5db7-9c72-61987ca3d8dd"
            }
            """;

    private final FindTrendSamplesCommand command = new FindTrendSamplesCommand(OBJECT_ID, FROM, TO);

    @Test
    void requestTargetsTrendedPresentValueSamplesOfTheObject() {
        MetasysRequest request = command.request();

        assertEquals("GET", request.method());
        assertEquals("objects/" + OBJECT_ID + "/trendedAttributes/presentValue/samples", request.path());
        assertNull(request.jsonBody());
    }

    @Test
    void requestQueryCarriesTimeWindowAndPagingInOrder() {
        MetasysRequest request = command.request();

        assertEquals(List.of("startTime", "endTime", "page", "pageSize", "skip"), List.copyOf(request.query().keySet()));
        assertEquals("2026-09-05T10:00:00Z", request.query().get("startTime"));
        assertEquals("2026-09-05T11:01:00Z", request.query().get("endTime"));
        assertEquals("1", request.query().get("page"));
        assertEquals("1000", request.query().get("pageSize"));
        assertEquals("0", request.query().get("skip"));
    }

    @Test
    void successParsesSamplesAndStampsTrendId() {
        Set<MetasysTrendSample> samples = command.onSuccess(TREND_SAMPLES_JSON);

        assertEquals(2, samples.size());
        for (MetasysTrendSample sample : samples) {
            assertEquals(OBJECT_ID, sample.getTrendId());
            assertEquals(OBJECT_ID, sample.getObjectId());
        }
    }

    @Test
    void notFoundIsTrendNotFound() {
        TrendNotFoundException e = assertThrows(TrendNotFoundException.class,
                () -> command.onError(404, "{\"message\":\"Not Found\"}"));
        assertEquals(OBJECT_ID, e.getTrendId());
    }

    @Test
    void otherErrorStatusIsMetasysApiExceptionWithThatStatus() {
        MetasysApiException e = assertThrows(MetasysApiException.class, () -> command.onError(500, "boom"));
        assertEquals(500, e.getStatusCode());
    }
}
