package no.cantara.realestate.metasys.cloudconnector.tools;

import com.azure.digitaltwins.core.BasicDigitalTwin;
import no.cantara.config.ApplicationProperties;
import no.cantara.realestate.azure.digitaltwin.AzureDigitalTwinClient;
import no.cantara.realestate.mappingtable.csv.CsvWriter;
import no.cantara.realestate.metasys.cloudconnector.sensors.MeasurementUnit;
import no.cantara.realestate.metasys.cloudconnector.sensors.SensorType;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.slf4j.LoggerFactory.getLogger;

public class RecCsvCreatorAzureDigitalTwin {
    private static final Logger log = getLogger(RecCsvCreatorAzureDigitalTwin.class);

    public static void main(String[] args) {
        ApplicationProperties config = ApplicationProperties.builder().defaults().buildAndSetStaticSingleton();
        AzureDigitalTwinClient client = new AzureDigitalTwinClient(config);
        List<BasicDigitalTwin> twins = client.queryForTwins("SELECT * FROM digitaltwins T WHERE contains(T.customProperties.source.System, 'Metasys')");
//        for (BasicDigitalTwin twin : twins) {
//            System.out.println("twin = " + twin);
//        }
        String configDirectory = config.get("importdata.directory");
        String filename = "MetasysRecImported.csv";
        log.info("Writing {} Metasys sensor mappings to {}/{}", twins.size(),configDirectory,filename);
        writeMappingToFile(configDirectory, filename, false, twins);
        log.info("Wrote {} Metasys sensor mappings to {}", twins.size(), filename);
    }

    public static long writeMappingToFile(String configDirectory, String filename, boolean append, List<BasicDigitalTwin> twins) {
        File importDirectory = new File(configDirectory);
        int count = 0;
        Pattern buildingPattern = Pattern.compile("\\+(.*?)=");
        Pattern roomPattern = Pattern.compile("\\.(.*?)\\-");

        try {
            File filPath = new File(importDirectory, filename);
            CsvWriter csvWriter = new CsvWriter(filPath, append, "RecId","Tfm","MetasysObjectReference","MetasysObjectId","Name","Description","RealEstate","Building","Section","Floor","ServesRoom","PlacementRoom","SensorType","MeasurementUnit","Interval");
            for (BasicDigitalTwin twin : twins) {
                Map<String, Object> parameters = twin.getContents();
                String name = (String) parameters.get("name");
                String tfm = name;
                String description = name;
                String metasysObjectId = getValue("identifiers.metasys_id", parameters);
                String realestate = null;
                String building = null;
                String floor = null;
                String servesRoom = null;
                String placementRoom = null;
                String sensorModel = twin.getMetadata().getModelId();

                String sensorType = parseSensorType(sensorModel);
                String adtMeasurementUnit = getValue("customProperties.metadata.unit", parameters);
                String measurementUnit = parseMeasurementUnit(sensorModel, adtMeasurementUnit);
                Matcher buildingMatcher = buildingPattern.matcher(tfm);
                if (buildingMatcher.find()) {
                    realestate = buildingMatcher.group(1);
                    building = realestate;
                }
                Matcher roomMatcher = roomPattern.matcher(tfm);
                if (roomMatcher.find()) {
                    if (building != null) {
                        servesRoom = building + roomMatcher.group(1);
                    } else {
                        servesRoom = roomMatcher.group(1);
                    }
                    placementRoom = servesRoom;
                    floor = roomMatcher.group(1).substring(0, 2);
                }
                csvWriter.writeValues(twin.getId(),
                        tfm, "", metasysObjectId, name, description, realestate, building, "", floor, servesRoom, placementRoom, sensorType, measurementUnit, "");
                count++;
            }
            csvWriter.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return count;
    }

    public static String getValue(String key, Map<String, Object> map) {
        String[] keys = key.split("\\.", 2);
        Object current = map.get(keys[0]);
        if (keys.length > 1) {
            if (current instanceof Map) {
                return getValue(keys[1], (Map<String, Object>) current);
            } else {
                throw new IllegalArgumentException("Key " + keys[0] + " does not exist in the map or is not a map");
            }
        }
        return (String) current;
    }

    static String parseMeasurementUnit(String versionedSensorModel, String adtMeasurementUnit) {
        SensorType sensorType = null;
        MeasurementUnit measurementUnit = null;
        Pattern pattern = Pattern.compile("Brick:(.*?)_Sensor");
        Matcher matcher = pattern.matcher(versionedSensorModel);
        if (matcher.find()) {
            String matched = matcher.group(1);
            sensorType = SensorType.from(matched);
            if (sensorType != null) {
                measurementUnit = MeasurementUnit.mapFromSensorType(sensorType);
            }
        }
        if (measurementUnit == null) {
            String[] keys = versionedSensorModel.split("\\;", 2);
            String sensorModel = keys[0];
            switch (sensorModel) {
                case "dtmi:org:brickschema:schema:Brick:Occupancy_Sensor":
                case "dtmi:org:brickschema:schema:Brick:Motion_Sensor":
                    measurementUnit = MeasurementUnit.truefalse;
                    break;
                default:
                    measurementUnit = null;
            }
        }
        if (measurementUnit == null) {
            return adtMeasurementUnit;
        } else {
            return measurementUnit.name();
        }

    }

    static String parseSensorType(String versionedSensorModel) {

        String sensorType = null;
        Pattern pattern = Pattern.compile("Brick:(.*?)_Sensor");
        Matcher matcher = pattern.matcher(versionedSensorModel);
        if (matcher.find()) {
            sensorType = matcher.group(1);
            sensorType = SensorType.from(sensorType).name();
        }
        if (sensorType == null || sensorType.equals("other")) {
            String[] keys = versionedSensorModel.split("\\;", 2);
            String sensorModel = keys[0];
            switch (sensorModel) {
                //dtmi:org:brickschema:schema:Brick:Temperature_Sensor
                case "dtmi:org:brickschema:schema:Brick:Temperature_Sensor":
                    sensorType = "Temperature";
                    break;
                case "dtmi:org:brickschema:schema:Brick:Humidity_Sensor":
                    sensorType = "Humidity";
                    break;
                case "dtmi:org:brickschema:schema:Brick:CO2_Sensor":
                    sensorType = "CO2";
                    break;
                case "dtmi:org:brickschema:schema:Brick:Occupancy_Sensor":
                    sensorType = "Tilstedevarelse";
                    break;
                case "dtmi:org:brickschema:schema:Brick:Motion_Sensor":
                    sensorType = "Tilstedevarelse";
                    break;
                default:
                    sensorType = "Ukjent";
            }
        }

        return sensorType;
    }
}
