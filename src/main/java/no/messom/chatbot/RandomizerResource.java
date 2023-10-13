package no.messom.chatbot;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import no.cantara.stingray.security.application.StingrayAction;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

@Path("/")
public class RandomizerResource {

    private final AtomicLong requestCount = new AtomicLong();
    private final Random random;

    public RandomizerResource(Random random) {
        this.random = random;
    }

    public long getRequestCount() {
        return requestCount.get();
    }

    @GET
    @Path("/str/{length}")
    @Produces(MediaType.TEXT_PLAIN)
    @StingrayAction("getstr")
    public Response getstr(@PathParam("length") String lengthParam) {
        requestCount.incrementAndGet();
        final String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        int N = Math.min(Integer.parseInt(lengthParam), 100);
        char[] state = new char[N];
        for (int i = 0; i < N; i++) {
            int a = random.nextInt(alphabet.length());
            state[i] = alphabet.charAt(a);
        }
        return Response.ok(new String(state), MediaType.TEXT_PLAIN_TYPE).build();
    }

    @GET
    @Path("/int/{bound}")
    @Produces(MediaType.TEXT_PLAIN)
    @StingrayAction("getint")
    public Response getint(@PathParam("bound") String upperBoundExParam) {
        requestCount.incrementAndGet();
        int randomResult = random.nextInt(Integer.parseInt(upperBoundExParam));
        return Response.ok(String.valueOf(randomResult), MediaType.TEXT_PLAIN_TYPE).build();
    }

    @PUT
    @Path("/seed/{seed}")
    @Produces(MediaType.TEXT_PLAIN)
    @StingrayAction("reseed")
    public Response reseed(@PathParam("seed") String seedParam) {
        requestCount.incrementAndGet();
        long seed = Long.parseLong(seedParam);
        random.setSeed(seed);
        return Response.ok().build();
    }
}
