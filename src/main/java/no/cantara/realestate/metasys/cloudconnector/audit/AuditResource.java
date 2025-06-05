package no.cantara.realestate.metasys.cloudconnector.audit;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import no.cantara.stingray.security.application.StingrayAction;
import no.cantara.stingray.security.application.StingraySecurityOverride;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

@Path("/audit")
public class AuditResource {
    private static final Logger log = getLogger(AuditResource.class);

    private final InMemoryAuditTrail inMemoryAuditTrail;

    public AuditResource(InMemoryAuditTrail inMemoryAuditTrail) {
        this.inMemoryAuditTrail = inMemoryAuditTrail;
    }

    //List all audit states as html

    @GET
    @Path("/{sensorId}")
    @Produces(MediaType.TEXT_HTML)
    @StingrayAction("auditBySensorId")
    @StingraySecurityOverride
    public Response getstr(@PathParam("sensorId") String sensorId) {
        Optional<AuditState> state = inMemoryAuditTrail.getState(sensorId);
        if (!state.isPresent()) {
            return Response.ok( "<html><body><h2>No state found for sensorId: " + sensorId + "</h2></body></html>").build();
        }
        List<AuditEvent> events = state.get().allEventsByTimestamp();
        if (events.isEmpty()) {
            return Response.ok( "<html><body><h2>No events found for sensorId: " + sensorId + "</h2></body></html>").build();
        }

        StringBuilder html = new StringBuilder("<html><body>");
        html.append("<h1>Audit for Sensor: ").append(sensorId).append("</h1><ul>");
        for (AuditEvent event : events) {
            html.append("<li>")
                    .append(event.getTimestamp()).append(" - ")
                    .append(event.getType()).append(" - ")
                    .append(event.getDetail())
                    .append("</li>");
        }
        html.append("</ul></body></html>");
        return Response.ok(html.toString(), MediaType.TEXT_HTML_TYPE).build();
    }

    @GET
    @Path("/")
    @Produces(MediaType.TEXT_HTML)
    @StingrayAction("auditAllStates")
    @StingraySecurityOverride
    public Response getAllStates() {
        Map<String, AuditState> allStates = inMemoryAuditTrail.getAll();
        if (allStates.isEmpty()) {
            return Response.ok("<html><body><h2>No audit states found</h2></body></html>", MediaType.TEXT_HTML).build();
        }

        StringBuilder html = new StringBuilder("<html><body>");
        html.append("<h1>All Audit States</h1><table border='1'>");
        html.append("<tr><th>Sensor ID</th><th>Last Observed</th></tr>");
        for (Map.Entry<String, AuditState> entry : allStates.entrySet()) {
            String sensorId = entry.getKey();
            AuditState auditState = entry.getValue();
            Instant lastUpdated = auditState.getLastObservedTimestamp();

            html.append("<tr>")
                    .append("<td><a href=\"./"+sensorId +"\">"+sensorId +"</a></td>")
                    .append("<td>").append(lastUpdated).append("</td>")
                    .append("</tr>");
        }
        html.append("</table></body></html>");
        return Response.ok(html.toString(), MediaType.TEXT_HTML).build();
    }
}
