package no.cantara.realestate.metasys.cloudconnector.automationserver;

public interface MetasysApiLogonService {
    MetasysUserToken logon(String jsonBody);
}
