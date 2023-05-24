package no.cantara.realestate.metasys.cloudconnector.sensors;

public enum MeasurementUnit {
    percent, litre_minute, celsius, ppm, truefalse, unknown, count, kwh, m3_hour;

    public static MeasurementUnit mapFromSensorType(SensorType sensorType) {
        MeasurementUnit measurementUnit = unknown;
        switch (sensorType) {
            case temp_borverdi:
            case aggregat_tilluft_temp:
            case aggregat_avtrekk_temp:
            case temp:
                measurementUnit = celsius;
                break;
            case co2:
                measurementUnit = ppm;
                break;
            case humidity:
            case kjoling:
            case aggregat_kjoling:
            case varme:
            case aggregat_varme:
            case spjellvinkel:
            case spjellvinkel_tilluft:
            case spjellvinkel_avtrekk:
                measurementUnit = percent;
                break;
            case tilluft:
            case aggregat_tilluft:
            case avtrekk:
            case aggregat_avtrekk:
                measurementUnit = m3_hour;
                break;
            case tilstedevarelse:
                measurementUnit = truefalse;
                break;
            case energy:
            case power:
                measurementUnit = kwh;
                break;
            default:
                measurementUnit = unknown;
        }
        return measurementUnit;
    }
}
