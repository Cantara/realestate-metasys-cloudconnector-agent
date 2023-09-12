package no.cantara.realestate.metasys.cloudconnector.automationserver;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrendSamplesMapperTest {

    @Test
    void mapFromJson() {
        //Test that TrendSamplesMapper can map from json to MetasysTrendSampleResult
        String trendSampleJson = """
                {
                  "total": 1,
                  "items": [
                    {
                     		"value": {
                     			"value": 9398.001,
                     			"units": "https://metasysserver/api/v4/enumSets/507/members/19"
                     		},
                     		"timestamp": "2020-09-16T05:20:00Z",
                     		"isReliable": true
                     	}
                  ],
                  "next": "http://localhost:1080/api/v4/objects/05ccd193-a3f9-5db7-9c72-61987ca3d8dd/trendedAttributes/presentValue/samples?startTime=2023-05-24T00:46:47.000Z&endTime=2023-05-24T23:13:58.007Z&page=2&pageSize=1",
                  "attribute": "attributeEnumSet.presentValue",
                  "previous": null,
                  "self": "http://localhost:1080/api/v4/objects/05ccd193-a3f9-5db7-9c72-61987ca3d8dd/trendedAttributes/presentValue/samples?startTime=2023-05-24T00:46:47.000Z&endTime=2023-05-24T23:13:58.007Z&pageSize=1",
                  "objectUrl": "http://localhost:1080/api/v4/objects/05ccd193-a3f9-5db7-9c72-61987ca3d8dd"
                }
                
                """;
        MetasysTrendSampleResult result = TrendSamplesMapper.mapFromJson(trendSampleJson);
        assertEquals(1, result.getTotal());
        MetasysTrendSample sample = result.getItems().get(0);
        assertNotNull(sample);
        assertEquals(9398.001, sample.getValue());
        assertEquals("05ccd193-a3f9-5db7-9c72-61987ca3d8dd", sample.getObjectId());
        assertEquals("attributeEnumSet.presentValue", result.getAttribute());
        assertNull(sample.getTrendId());
    }
}