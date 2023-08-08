package no.cantara.realestate.metasys.cloudconnector.notifications;

import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import no.cantara.config.ApplicationProperties;
import no.cantara.realestate.metasys.cloudconnector.MetasysCloudconnectorApplicationFactory;

import static junit.framework.Assert.assertTrue;
import static no.cantara.realestate.metasys.cloudconnector.notifications.SlackNotificationService.*;

class SlackNotificationServiceManualTest {

    public static void main(String[] args) throws Exception {
        ApplicationProperties config = new MetasysCloudconnectorApplicationFactory()
                .conventions(ApplicationProperties.builder())
                .buildAndSetStaticSingleton();
//        sendStandalone(config);
        SlackNotificationService.clearService("Test");
        SlackNotificationService.sendAlarm("Test", "Test again");
        Thread.sleep(100);
//        SlackNotificationService.clearService("Test");
        Thread.sleep(200);
        SlackNotificationService.closeConnection();




    }

    private static void sendStandalone(ApplicationProperties config) throws Exception {
        boolean slackAlertingIsEnabled = config.asBoolean(SLACK_ALERTING_ENABLED_KEY);
        assertTrue(slackAlertingIsEnabled);
        Slack slack = Slack.getInstance();
        String slackToken = config.get(SLACK_TOKEN_KEY);
        MethodsClient methods = slack.methods(slackToken);

        String channel = config.get(SLACK_WARNING_CHANNEL_KEY);

        ChatPostMessageRequest request = ChatPostMessageRequest.builder()
                .channel(channel) // Use a channel ID `C1234567` is preferable
                .text(":wave: Hi from a bot written in Java!")
                .build();

        System.out.println("Post to channel: " + channel + "with token: " + slackToken);
        ChatPostMessageResponse response = methods.chatPostMessage(request);
        System.out.println("Response:" + response);
        slack.close();
    }



}