package no.messom.chatbot;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import no.cantara.stingray.security.application.StingrayAction;
import no.messom.chatbot.rasa.RasaRestClient;
import org.glassfish.jersey.server.ContainerRequest;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicLong;

import static org.slf4j.LoggerFactory.getLogger;

@Path("/chat")
public class ChatResource {

    private final RasaRestClient rasaClient;
    private static final Logger log = getLogger(ChatResource.class);

    private final AtomicLong requestCount = new AtomicLong();

    public ChatResource( RasaRestClient rasaClient) {
        this.rasaClient = rasaClient;
    }

    public long getRequestCount() {
        return requestCount.get();
    }


    @POST
    @Path("/message/")
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes(MediaType.APPLICATION_JSON)
    @StingrayAction("postMessage")
    public Response postMessage(final SmsMessage smsMessage) {
        requestCount.incrementAndGet();
        log.info("Received message: {}", smsMessage);
        String sender = smsMessage.getNumber();
        String message = smsMessage.getMsg();
        try {
            rasaClient.message(sender, message);
            return Response.ok(new String("Ok"), MediaType.TEXT_PLAIN_TYPE).build();
        } catch (Exception e) {
            log.info("Failed to send message to Rasa. Sender {}, message {}", sender, message, e);
            return Response.serverError().build();
        }
    }

    @POST
    @Path("/message/")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED) //application/x-www-form-urlencoded
    @StingrayAction("postFormMessage")
    public Response postMessage(@FormParam("number") String sender, @FormParam("msg") String message, Request request) {
        log.info("Received message: {}", request);
        ((ContainerRequest) request).getHeaders().forEach((k, v) -> log.info("Header {}: {}", k, v));
        try {
            rasaClient.message(sender, message);
            return Response.ok(new String("Ok"), MediaType.TEXT_PLAIN_TYPE).build();
        } catch (Exception e) {
            log.info("Failed to send message to Rasa. Sender {}, message {}", sender, message, e);
            return Response.serverError().build();
        }
    }
    @GET
    @Path("/message/")
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes(MediaType.APPLICATION_JSON)
    @StingrayAction("getMessage")
    public Response getMessage(@QueryParam("number") String number, @QueryParam("msg") String msg) {
        requestCount.incrementAndGet();
        log.info("Received message: {}, from {}", msg, number);
        String sender = number;
        String message = msg;
        try {
            rasaClient.message(sender, message);
            return Response.ok(new String("Ok"), MediaType.TEXT_PLAIN_TYPE).build();
        } catch (Exception e) {
            log.info("Failed to send message to Rasa. Sender {}, message {}", sender, message, e);
            return Response.serverError().build();
        }
    }

    @GET
    @Path("/requestCount/")
    @Produces(MediaType.TEXT_PLAIN)
    @StingrayAction("requestCount")
    public Response requestCount() {
        return Response.ok(String.valueOf(requestCount.get()), MediaType.TEXT_PLAIN_TYPE).build();
    }

}
