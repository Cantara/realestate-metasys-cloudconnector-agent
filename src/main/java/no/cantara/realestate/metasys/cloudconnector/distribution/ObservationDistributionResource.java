package no.cantara.realestate.metasys.cloudconnector.distribution;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import no.cantara.realestate.distribution.ObservationDistributionClient;
import no.cantara.realestate.json.RealEstateObjectMapper;
import no.cantara.realestate.metasys.cloudconnector.MetasysCloudConnectorException;
import no.cantara.realestate.observations.ObservationMessage;
import no.cantara.stingray.security.application.StingrayAction;
import no.cantara.stingray.security.application.StingraySecurityOverride;
import org.slf4j.Logger;

import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

@Path("/")
public class ObservationDistributionResource {
    private static final Logger log = getLogger(ObservationDistributionResource.class);

    private final ObservationDistributionClient distributionClient;

    public ObservationDistributionResource(ObservationDistributionClient distributionClient) {
        this.distributionClient = distributionClient;
    }

    @GET
    @Path("/distributed")
    @StingraySecurityOverride
    @Produces(MediaType.APPLICATION_JSON)
    @StingrayAction("getDistributed")
    public Response getDistributed() {
        String body = null;
        List<ObservationMessage> observationMessages = null;

        try {
            observationMessages = distributionClient.getObservedMessages();
            if (observationMessages != null) {
                body = RealEstateObjectMapper.getInstance().getObjectMapper().writeValueAsString(observationMessages);
                return Response.ok(body, MediaType.APPLICATION_JSON_TYPE).build();
            } else {
                return Response.ok("[]", MediaType.APPLICATION_JSON_TYPE).build();
            }
        } catch (JsonProcessingException e) {
            String msg = "Failed to convert observationMessages to json. Reason is: " + e.getMessage();
            MetasysCloudConnectorException mcce = new MetasysCloudConnectorException(msg, e);
            log.debug("Failed to getDistributedObservations",mcce);
            return Response.status(412, "Failed to create json from observed messages.").build();        }
    }

    public long getDistributedCount() {
        return -1;
    }
}
