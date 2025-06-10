package no.cantara.realestate.metasys.cloudconnector.notifications;

@Deprecated
public interface NotificationService {
    boolean sendWarning(String service, String warningMessage) ;

    boolean sendAlarm(String service, String alarmMessage);

    boolean clearService(String service);
}
