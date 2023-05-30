# realestate-metasys-cloudconnector-app
Read sensordata from Johnson Controls Metasys. Distribute these to the clud eg by MQTT or Azure Digital Twin



## Testing with Metasys Mock
### Start Mock server
````
mvn -Dmockserver.serverPort=1080 -Dmockserver.logLevel=INFO org.mock-server:mockserver-maven-plugin:5.15.0:runForked
`````
### Stop Mock server
````
mvn -Dmockserver.serverPort=80 org.mock-server:mockserver-maven-plugin:5.15.0:stopForked
````

### Add Login mock

Run  [MockServerSetup.java](src/test/java/no/cantara/realestate/metasys/cloudconnector/MockServerSetup.java)

### Add config
Rename local_override.properties_template to local_override.properties.
``` 
sd.api.prod=true
sd.api.username=jane-doe
sd.api.password=strongPassword
```

### Import of RealEstate sensor config
Move MetasysTfmRec.csv_template to import-data/MetasysTfmRec.csv

In local_override.properties add the RealEstate names identified in the file above.
The intention is to support filtering of which sensors, from defined buildings the agent should import.
``` 
importsensorsQuery.realestates=realEstateName1,realEstateName2
``` 

