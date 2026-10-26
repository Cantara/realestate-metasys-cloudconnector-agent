package no.cantara.realestate.metasys.cloudconnector.automationserver;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The payload of a single Metasys API call, without anything that belongs to the connection.
 * <p>
 * The request deliberately has no apiUri, access token or timeout. Those are owned by
 * {@link MetasysCommandExecutor}, so a command can be built and tested without a running
 * client, and the executor stays the one place that decides whether a call may be sent at all.
 * <p>Example:
 * <pre>{@code
 * MetasysRequest request = MetasysRequest.get("objects/" + objectId + "/attributes/presentValue",
 *         Map.of("includeSchema", "false"));
 * }</pre>
 *
 * @param method   HTTP method, e.g. {@code GET}
 * @param path     path relative to the Metasys apiUri, without a leading slash, e.g. {@code objects/{id}/...}
 * @param query    query parameters, sent in the iteration order of the map
 * @param headers  extra headers; {@code Authorization} and {@code Content-Type} are added by the executor
 * @param jsonBody request body as JSON, or {@code null} for requests without a body
 */
public record MetasysRequest(String method, String path, Map<String, String> query, Map<String, String> headers,
                             String jsonBody) {

    public MetasysRequest {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(path, "path");
        query = query == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(query));
        headers = headers == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    }

    /**
     * A GET request without extra headers or body.
     *
     * @param path  path relative to the Metasys apiUri
     * @param query query parameters, sent in the iteration order of the map
     * @return the request
     */
    public static MetasysRequest get(String path, Map<String, String> query) {
        return new MetasysRequest("GET", path, query, Map.of(), null);
    }
}
