# realestate-metasys-cloudconnector-app
Read sensor observations from Johnson Controls Metasys. Distribute these to the cloud e.g. by MQTT or Azure Digital Twin

# Getting Started

## List of Sensors organized by rooms
See [MetasysTfmRec.csv_template](./MetasysTfmRec.csv_template) for a list of sensors with placement in the buildings, and rooms.
This template may be used to configure all sensors that should be imported from Metasys. 

## Import and configuration
1. Copy your MetasysTfmRec.csv to import-data/MetasysTfmRec.csv
2. Rename local_override.properties_template to local_override.properties.
### Required properties
```
sd.api.prod=true
sd.api.username=....
sd.api.password=....
sd.api.url=https://<metasysServer>:<port>/api/v4
importsensorsQuery.realestates=RealEstate1,RealEstate2...
```
**importsensorsQuery.realestates** is a comma separated list of the RealEstate names identified in the MetasysTfmRec.csv file. Only RealEstates in this list will be imported.
The intention is to support filtering of which sensors, from defined buildings the agent should import.


### Distribution of observations
```
distribution.azure.connectionString=HostName=<yourIoTHub>.azure-devices.net;SharedAccessKeyName=iothubowner;SharedAccessKey=<yourSharedAccessKey>
```
Azure ConnectionString is copied from Azure IoT Hub, Devices, <yourDevice>, Connection string-primary key. 
See [Azure IoT Hub configuration](https://github.com/Cantara/realestate-azure-client-lib) for details.

## Start the application
```
java -jar path_to/realestate-metasys-cloudconnector-agent-<version>.jar
```

### Monitoring
http://localhost:8081/metayscloudconnector/health/pretty
```:json
{
  "Status": "UP",
  "version": "unknown",
  "ip": "127.0.1.1",
  "running since": "2023-09-04T13:08:23.493546367Z",
  "MetasysStreamClient.isLoggedIn": "UP",
  "MetasysStreamImporter.isHealthy": "UP",
  "webserver.running": "UP",
  "AzureObservationDistributionClient-isConnected: ": "true",
  "AzureObservationDistributionClient-numberofMessagesObserved: ": "1",
  "mappedIdRepository.size": "68",
  "MetasysStreamClient-isHealthy: ": "true
  "MetasysStreamClient-isLoggedIn: ": "true",
  "MetasysStreamClient-isStreamOpen: ": "true",
  "MetasysStreamImporter-isHealthy: ": "true",
  "now": "2023-09-04T13:08:37.632070133Z"
}
```

### Alerting
There is support for Slack alerting.
When the connection to Metasys fails, or the distribution to Azure IoT Hub is lost, an alert is sent to Slack.
```
slack_alerting_enabled=true
slack_token=...
slack_alarm_channel=...
slack_warning_channel=...
```

# Development

```
mvn clean install
java -jar target/metasys-cloudconnector-app-<version>.jar
```

## Testing with Metasys Mock
### Start Mock server
````
mvn -Dmockserver.serverPort=1080 -Dmockserver.logLevel=INFO org.mock-server:mockserver-maven-plugin:5.15.0:runForked
`````
### Stop Mock server
````
mvn -Dmockserver.serverPort=1080 org.mock-server:mockserver-maven-plugin:5.15.0:stopForked
````

### Add Login mock

Run  [MockServerSetup.java](src/test/java/no/cantara/realestate/metasys/cloudconnector/MockServerSetup.java)


### Updates
* 0.7.0 - Support for Streming of sensors from Metasys, and distributing these observations.

