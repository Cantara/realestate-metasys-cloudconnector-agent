package no.cantara.realestate.metasys.cloudconnector;

public class YamlMappedIdQuery  {

    private String realEstate;
    private String sensorType;

    public YamlMappedIdQuery() {
    }

    public String getRealEstate() {
        return realEstate;
    }

    public void setRealEstate(String realEstate) {
        this.realEstate = realEstate;
    }

    public String getSensorType() {
        return sensorType;
    }

    public void setSensorType(String sensorType) {
        this.sensorType = sensorType;
    }

    @Override
    public String toString() {
        return "YamlMappedIdQuery{" +
                "realEstate='" + realEstate + '\'' +
                ", sensorType='" + sensorType + '\'' +
                '}';
    }
}
