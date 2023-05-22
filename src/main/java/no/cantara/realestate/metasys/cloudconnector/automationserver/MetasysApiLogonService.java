package no.cantara.realestate.metasys.cloudconnector.automationserver;

public interface MetasysApiLogonService {
    UserToken logon(String jsonBody);
}
