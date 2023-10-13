package no.messom.chatbot;

import no.cantara.config.ApplicationProperties;
import no.cantara.stingray.application.AbstractStingrayApplication;
import no.messom.chatbot.rasa.RasaRestClient;
import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.slf4j.Logger;

import java.util.Random;

import static org.slf4j.LoggerFactory.getLogger;

public class ChatBot42Application extends AbstractStingrayApplication<ChatBot42Application> {
    private static final Logger log = getLogger(ChatBot42Application.class);

    public ChatBot42Application(ApplicationProperties config) {
        super("ChatBot42",
                readMetaInfMavenPomVersion("no.metasys.chatbot", "ChatBot42"),
                config
        );
    }

    public static void main(String[] args) {
        ApplicationProperties config = new ChatBot42ApplicationFactory()
                .conventions(ApplicationProperties.builder())
                .buildAndSetStaticSingleton();

        try {
            ChatBot42Application application = new ChatBot42Application(config).init().start();

            log.info("Server started. See status on {}:{}{}/health", "http://localhost", config.get("server.port"), config.get("server.context-path"));
//            application.startImportingObservations();
        } catch (Exception e) {
            log.error("Failed to start MetasysCloudconnectorApplication", e);
        }

    }
    @Override
    protected void doInit() {

        initBuiltinDefaults();

//        StingraySecurity.initSecurity(this);
        //Random Example
        init(Random.class, this::createRandom);
        RandomizerResource randomizerResource = initAndRegisterJaxRsWsComponent(RandomizerResource.class, this::createRandomizerResource);

        //RasaRestClient
        RasaRestClient rasaClient = new RasaRestClient();
        ChatResource chatResource = initAndRegisterJaxRsWsComponent(ChatResource.class, () -> new ChatResource(rasaClient));
    }

    private void initRequestLog() {
        RequestLogHandler requestLogHandler = new RequestLogHandler();
        ((Server)ChatBot42Application.this.jettyServerRef.get()).insertHandler(requestLogHandler);
//        NCSARequestLog requestLog = new NCSARequestLog("./logs/jetty-yyyy_mm_dd.request.log");
//        requestLog.setRetainDays(90);
//        requestLog.setAppend(true);
//        requestLog.setExtended(false);
//        requestLog.setLogTimeZone("GMT");
        RequestLog requestLog = new CustomRequestLog("./logs/jetty-yyyy_mm_dd.request.log");
        requestLogHandler.setRequestLog(requestLog);
    }

    private Random createRandom() {
        return new Random(System.currentTimeMillis());
    }

    private RandomizerResource createRandomizerResource() {
        Random random = get(Random.class);
        return new RandomizerResource(random);
    }



}
