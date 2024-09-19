package no.cantara.realestate.metasys.cloudconnector.automationserver;

@Deprecated // Extend BasClient instead
public interface MetasysApiLogonService {
    MetasysUserToken logon(String jsonBody);
}
