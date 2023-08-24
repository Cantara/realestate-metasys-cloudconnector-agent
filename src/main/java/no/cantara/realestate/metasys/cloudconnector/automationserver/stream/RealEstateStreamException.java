package no.cantara.realestate.metasys.cloudconnector.automationserver.stream;

import no.cantara.realestate.RealEstateException;

public class RealEstateStreamException extends RealEstateException {
    private Action action;

    enum Action {
        RECREATE_SUBSCRIPTION_NEEDED,
        ABORT
    }

    public RealEstateStreamException(String message) {
        super(message);
    }

    public RealEstateStreamException(String message, Action action ) {
        super(message);
        this.action = action;
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    @Override
    public String toString() {
        return "RealEstateStreamException{" +
                "action=" + action +
                "} " + super.toString();
    }
}
