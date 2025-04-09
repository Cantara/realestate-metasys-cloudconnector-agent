package no.cantara.realestate.metasys.cloudconnector.utils;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import org.slf4j.LoggerFactory;

import java.io.File;

public class LogbackConfigLoader {

    public static void loadExternalConfig(String configFilePath) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        File configFile = new File(configFilePath);

        if (configFile.exists() && configFile.isFile()) {
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            context.reset(); // Reset the current configuration
            try {
                configurator.doConfigure(configFile);
                System.out.println("Loaded external Logback configuration from: " + configFilePath);
            } catch (JoranException e) {
                System.err.println("Failed to load external Logback configuration: " + e.getMessage());
            }
        } else {
            System.err.println("External Logback configuration file not found: " + configFilePath);
        }
    }
}
