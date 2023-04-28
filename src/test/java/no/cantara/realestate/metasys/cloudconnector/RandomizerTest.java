package no.cantara.realestate.metasys.cloudconnector;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import no.cantara.stingray.application.StingrayLogging;
import no.cantara.stingray.security.authentication.test.FakeStingrayAuthorization;
import no.cantara.stingray.test.StingrayTestClient;
import no.cantara.stingray.test.StingrayTestExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(StingrayTestExtension.class)
public class RandomizerTest {



    static {
        StingrayLogging.init();
    }

    private static final Logger log = LoggerFactory.getLogger(RandomizerTest.class);

    @Inject
    StingrayTestClient testClient;

    @Test
    public void thatViewerCanDoAllExceptReseed() {
        testClient.useAuthorization(FakeStingrayAuthorization.application().applicationId("junit-viewer").addAccessGroup("viewers").build());
        log.info("GET /metasyscloudconnector/str/10 Response: {}", testClient.get().path("/metasyscloudconnector/str/10").execute().expect200Ok().contentAsString());
        log.info("GET /metasyscloudconnector/int/1000 Response: {}", testClient.get().path("/metasyscloudconnector/int/1000").execute().expect200Ok().contentAsString());
        log.info("GET /metasyscloudconnector/health Response: {}", testClient.get().path("/metasyscloudconnector/health").execute().expect200Ok().contentAsType(JsonNode.class).toPrettyString());
        testClient.put().path("/metasyscloudconnector/seed/12345").execute().expect403Forbidden();
    }

    @Test
    public void thatAdminCanDoAll() {
        testClient.useAuthorization(FakeStingrayAuthorization.application().applicationId("junit-admin").addAccessGroup("admins").build());
        log.info("GET /metasyscloudconnector/str/10 Response: {}", testClient.get().path("/metasyscloudconnector/str/10").execute().expect200Ok().contentAsString());
        log.info("GET /metasyscloudconnector/int/1000 Response: {}", testClient.get().path("/metasyscloudconnector/int/1000").execute().expect200Ok().contentAsString());
        log.info("GET /metasyscloudconnector/health Response: {}", testClient.get().path("/metasyscloudconnector/health").execute().expect200Ok().contentAsType(JsonNode.class).toPrettyString());
        testClient.put().path("/metasyscloudconnector/seed/12345").execute().expect200Ok();
    }

    @Test
    public void thatOpenApiWorks() {
        String openApiYaml = testClient.get().path("/metasyscloudconnector/openapi.yaml").execute().expect200Ok().contentAsString();
        log.info("/metasyscloudconnector/openapi.yaml:\n{}", openApiYaml);
    }
}
