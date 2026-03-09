# realestate-metasys-cloudconnector-agent

## Purpose
Agent application that reads sensor observations from Johnson Controls Metasys building management systems and distributes them to the cloud via MQTT or Azure Digital Twin. Bridges on-premise building sensors to cloud analytics platforms.

## Tech Stack
- Language: Java 11+
- Framework: Standalone agent
- Build: Maven
- Key dependencies: Azure IoT Hub SDK, MQTT, Jackson

## Architecture
Standalone agent that connects to Metasys REST API (v4) to poll sensor observations, maps them to a room/building hierarchy defined in CSV configuration, and forwards observations to cloud services (Azure IoT Hub, Azure Digital Twin, MQTT). Supports filtering by real estate name to control which sensors are imported.

## Key Entry Points
- `local_override.properties` - Metasys API credentials and configuration
- `import-data/MetasysTfmRec.csv` - Sensor-to-room mapping configuration
- `MetasysTfmRec.csv_template` - Template for sensor configuration

## Development
```bash
# Build
mvn clean install

# Configure
cp local_override.properties_template local_override.properties
# Edit with Metasys API credentials

# Run
java -jar target/metasys-cloudconnector-app-*.jar
```

## Domain Context
IoT and building management. Bridges on-premise Johnson Controls Metasys building management systems to cloud analytics, enabling real-time monitoring of environmental sensors (temperature, humidity, CO2) across real estate portfolios.
