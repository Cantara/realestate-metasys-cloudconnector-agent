package no.messom.chatbot;

import no.cantara.config.ApplicationProperties;
import no.cantara.config.ProviderLoader;
import no.cantara.stingray.application.StingrayApplication;
import no.cantara.stingray.security.authorization.StingrayAccessManager;
import no.cantara.stingray.security.authorization.StingrayAccessManagerFactory;
import no.cantara.stingray.test.StingrayBeforeInitLifecycleListener;

public class ChatBot42ApplicationLifecycleListener implements StingrayBeforeInitLifecycleListener {

    @Override
    public void beforeInit(StingrayApplication application) {
        application.override(StingrayAccessManager.class, this::createAccessManager);
    }

    StingrayAccessManager createAccessManager() {
        ApplicationProperties authConfig = ApplicationProperties.builder()
                .classpathPropertiesFile("greet/service-authorization.properties")
                .classpathPropertiesFile("greet/authorization.properties")
                .filesystemPropertiesFile("greet/authorization.properties")
                .build();
        StingrayAccessManager accessManager = ProviderLoader.configure(authConfig, "default", StingrayAccessManagerFactory.class);
        return accessManager;
    }
}
