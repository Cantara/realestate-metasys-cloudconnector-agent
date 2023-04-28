package no.cantara.realestate.metasys.cloudconnector.distribution;

public class MetasysTrendsFetchedFailed extends Metric {

    public MetasysTrendsFetchedFailed(Integer numberOfTrendsFetched) {
        super("metrics-metasys-cloudconnector", numberOfTrendsFetched);
        addTag("metasys-api-trends-fetched", "failed");
    }
}
