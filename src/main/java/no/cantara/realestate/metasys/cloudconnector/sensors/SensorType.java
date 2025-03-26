package no.cantara.realestate.metasys.cloudconnector.sensors;

public enum SensorType {
    other, unknown, temp, temp_borverdi, co2, humidity, tilluft, avtrekk, kjoling, varme,
    spjellvinkel, spjellvinkel_tilluft, spjellvinkel_avtrekk, tilstedevarelse, energy, power,
    aggregat_tilluft, aggregat_avtrekk, aggregat_varme, aggregat_kjoling, aggregat_tilluft_temp, aggregat_avtrekk_temp, co2_borverdi;

    public static SensorType from(String sensorType) {
        SensorType mapped = null;
        if (sensorType == null) {
            mapped = unknown;
            return mapped;
        }

        for (SensorType sensorTypeEnum : SensorType.values()) {
            if (sensorTypeEnum.name().equals(sensorType.toLowerCase())) {
                mapped = sensorTypeEnum;
                break;
            }
        }

        if (mapped == null) {
            if (sensorType.toLowerCase().contains("humidity")) {
                mapped = humidity;
            } else {
                switch (sensorType.toLowerCase()) {
                    case "temp":
                    case "rt":
                    case "temperature":
                    case "temperatur":
                        mapped = temp;
                        break;
                    case "temp_borverdi":
                    case "temp_børverdi":
                        mapped = temp_borverdi;
                        break;
                    case "co2":
                    case "ry":
                        mapped = co2;
                        break;
                    case "co2_borverdi":
                    case "co2_børverdi":
                        mapped = co2_borverdi;
                        break;
                    case "rh":
                        mapped = humidity;
                        break;
                    case "rb":
                    case "presence":
                    case "tilstedeværelse":
                        mapped = tilstedevarelse;
                        break;
                    case "supply":
                    case "tilluft":
                        mapped = tilluft;
                        break;
                    case "exhaust":
                    case "avtrekk":
                        mapped = avtrekk;
                        break;
                    case "kjoling":
                    case "nedkjoling":
                    case "nedkjøling":
                        mapped = kjoling;
                        break;
                    case "varme":
                    case "varmepådrag":
                    case "varmepadrag":
                        mapped = varme;
                        break;
                    default:
                        mapped = other;
                }
            }
        }
        return mapped;
    }
}

