package no.messom.chatbot;

import no.cantara.config.ApplicationProperties;
import no.cantara.stingray.application.StingrayApplication;
import no.cantara.stingray.application.StingrayApplicationFactory;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

public class ChatBot42ApplicationFactory implements StingrayApplicationFactory<ChatBot42Application> {
    private static final Logger log = getLogger(ChatBot42ApplicationFactory.class);

    @Override
    public Class<?> providerClass() {
        return ChatBot42Application.class;
    }

    @Override
    public String alias() {
        return "ChatBot42";
    }

    @Override
    public StingrayApplication<ChatBot42Application> create(ApplicationProperties applicationProperties) {
        return new ChatBot42Application(applicationProperties);
    }

}
