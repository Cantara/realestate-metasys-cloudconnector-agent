package no.cantara.realestate.zaphire.cloudconnector;

import org.mockserver.client.MockServerClient;

import static no.cantara.realestate.zaphire.cloudconnector.automationserver.ZaphireApiSimulator.*;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Statisk oppsett av MockServer-endepunkter for Zaphire API.
 * Brukes sammen med ZaphireMockServerRunner for standalone kjøring.
 */
public class ZaphireMockServerSetup {

    private static final int DEFAULT_PORT = 1081;

    public static void main(String[] args) {
        setupAll(DEFAULT_PORT);
    }

    public static void setupAll(int port) {
        tagValuesMock(port);
        tagValuesBatchMock(port);
        historyRecordsMock(port);
    }

    /**
     * GET /site/{site}/Tag/Values?name={tagName}
     */
    public static void tagValuesMock(int port) {
        new MockServerClient("localhost", port)
                .clear(request().withPath("/site/" + DEFAULT_SITE + "/Tag/Values").withMethod("GET"));

        // Temperature
        new MockServerClient("localhost", port)
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/site/" + DEFAULT_SITE + "/Tag/Values")
                                .withQueryStringParameter("name", TAG_TEMPERATURE)
                )
                .respond(
                        response()
                                .withStatusCode(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("[\n" +
                                        "  {\n" +
                                        "    \"Name\": \"" + TAG_TEMPERATURE + "\",\n" +
                                        "    \"Units\": \"DegreesCelsius\",\n" +
                                        "    \"UnitsDisplay\": \"°C\",\n" +
                                        "    \"Value\": 21.5\n" +
                                        "  }\n" +
                                        "]")
                );

        // Humidity
        new MockServerClient("localhost", port)
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/site/" + DEFAULT_SITE + "/Tag/Values")
                                .withQueryStringParameter("name", TAG_HUMIDITY)
                )
                .respond(
                        response()
                                .withStatusCode(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("[\n" +
                                        "  {\n" +
                                        "    \"Name\": \"" + TAG_HUMIDITY + "\",\n" +
                                        "    \"Units\": \"PercentRelativeHumidity\",\n" +
                                        "    \"UnitsDisplay\": \"%RH\",\n" +
                                        "    \"Value\": 45.3\n" +
                                        "  }\n" +
                                        "]")
                );

        // Energy
        new MockServerClient("localhost", port)
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/site/" + DEFAULT_SITE + "/Tag/Values")
                                .withQueryStringParameter("name", TAG_ENERGY)
                )
                .respond(
                        response()
                                .withStatusCode(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("[\n" +
                                        "  {\n" +
                                        "    \"Name\": \"" + TAG_ENERGY + "\",\n" +
                                        "    \"Units\": \"KilowattHours\",\n" +
                                        "    \"UnitsDisplay\": \"kWh\",\n" +
                                        "    \"Value\": 12487.6\n" +
                                        "  }\n" +
                                        "]")
                );

        // Not found tag (returns 200 with error message, as per Zaphire API spec)
        new MockServerClient("localhost", port)
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/site/" + DEFAULT_SITE + "/Tag/Values")
                                .withQueryStringParameter("name", TAG_NOT_FOUND)
                )
                .respond(
                        response()
                                .withStatusCode(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("[\n" +
                                        "  {\n" +
                                        "    \"Name\": \"" + TAG_NOT_FOUND + "\",\n" +
                                        "    \"ErrorMessage\": \"Not found.\",\n" +
                                        "    \"Value\": null\n" +
                                        "  }\n" +
                                        "]")
                );
    }

    /**
     * POST /site/{site}/Tag/Values (batch)
     */
    public static void tagValuesBatchMock(int port) {
        new MockServerClient("localhost", port)
                .clear(request().withPath("/site/" + DEFAULT_SITE + "/Tag/Values").withMethod("POST"));

        new MockServerClient("localhost", port)
                .when(
                        request()
                                .withMethod("POST")
                                .withPath("/site/" + DEFAULT_SITE + "/Tag/Values")
                                .withHeader("Content-Type", "application/json")
                )
                .respond(
                        response()
                                .withStatusCode(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("[\n" +
                                        "  {\n" +
                                        "    \"Name\": \"" + TAG_TEMPERATURE + "\",\n" +
                                        "    \"Units\": \"DegreesCelsius\",\n" +
                                        "    \"UnitsDisplay\": \"°C\",\n" +
                                        "    \"Value\": 21.5\n" +
                                        "  },\n" +
                                        "  {\n" +
                                        "    \"Name\": \"" + TAG_HUMIDITY + "\",\n" +
                                        "    \"Units\": \"PercentRelativeHumidity\",\n" +
                                        "    \"UnitsDisplay\": \"%RH\",\n" +
                                        "    \"Value\": 45.3\n" +
                                        "  },\n" +
                                        "  {\n" +
                                        "    \"Name\": \"" + TAG_ENERGY + "\",\n" +
                                        "    \"Units\": \"KilowattHours\",\n" +
                                        "    \"UnitsDisplay\": \"kWh\",\n" +
                                        "    \"Value\": 12487.6\n" +
                                        "  }\n" +
                                        "]")
                );
    }

    /**
     * GET /site/{site}/Tags/History/Records?name={tagName}&from={from}&to={to}
     */
    public static void historyRecordsMock(int port) {
        new MockServerClient("localhost", port)
                .clear(request().withPath("/site/" + DEFAULT_SITE + "/Tags/History/Records"));

        // Temperature history
        new MockServerClient("localhost", port)
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/site/" + DEFAULT_SITE + "/Tags/History/Records")
                                .withQueryStringParameter("name", TAG_TEMPERATURE)
                )
                .respond(
                        response()
                                .withStatusCode(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("[\n" +
                                        "  {\"Name\": \"" + TAG_TEMPERATURE + "\", \"Timestamp\": \"2024-01-15T08:00:00.0000000+00:00\", \"Value\": 20.1},\n" +
                                        "  {\"Name\": \"" + TAG_TEMPERATURE + "\", \"Timestamp\": \"2024-01-15T09:00:00.0000000+00:00\", \"Value\": 21.3},\n" +
                                        "  {\"Name\": \"" + TAG_TEMPERATURE + "\", \"Timestamp\": \"2024-01-15T10:00:00.0000000+00:00\", \"Value\": 22.0},\n" +
                                        "  {\"Name\": \"" + TAG_TEMPERATURE + "\", \"Timestamp\": \"2024-01-15T11:00:00.0000000+00:00\", \"Value\": 21.8},\n" +
                                        "  {\"Name\": \"" + TAG_TEMPERATURE + "\", \"Timestamp\": \"2024-01-15T12:00:00.0000000+00:00\", \"Value\": 22.5}\n" +
                                        "]")
                );

        // Energy history
        new MockServerClient("localhost", port)
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/site/" + DEFAULT_SITE + "/Tags/History/Records")
                                .withQueryStringParameter("name", TAG_ENERGY)
                )
                .respond(
                        response()
                                .withStatusCode(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("[\n" +
                                        "  {\"Name\": \"" + TAG_ENERGY + "\", \"Timestamp\": \"2024-01-15T00:00:00.0000000+00:00\", \"Value\": 12400.0},\n" +
                                        "  {\"Name\": \"" + TAG_ENERGY + "\", \"Timestamp\": \"2024-01-15T06:00:00.0000000+00:00\", \"Value\": 12425.5},\n" +
                                        "  {\"Name\": \"" + TAG_ENERGY + "\", \"Timestamp\": \"2024-01-15T12:00:00.0000000+00:00\", \"Value\": 12487.6}\n" +
                                        "]")
                );

        // Not found tag
        new MockServerClient("localhost", port)
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/site/" + DEFAULT_SITE + "/Tags/History/Records")
                                .withQueryStringParameter("name", TAG_NOT_FOUND)
                )
                .respond(
                        response()
                                .withStatusCode(404)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"ErrorMessage\": \"No tag found with name \\\"" + TAG_NOT_FOUND + "\\\".\"}")
                );

        // Tag without logging
        new MockServerClient("localhost", port)
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/site/" + DEFAULT_SITE + "/Tags/History/Records")
                                .withQueryStringParameter("name", TAG_NO_LOGGING)
                )
                .respond(
                        response()
                                .withStatusCode(422)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"ErrorMessage\": \"Tag \\\"" + TAG_NO_LOGGING + "\\\" doesn't have logging enabled.\"}")
                );
    }
}
