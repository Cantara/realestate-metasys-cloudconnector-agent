package no.cantara.realestate.metasys.cloudconnector.automationserver;

/**
 * One Metasys API operation: what to ask for, and how to interpret the answer.
 * <p>
 * A command carries only payload. It never knows the apiUri, the access token, the HttpClient or
 * the circuit breaker; {@link MetasysCommandExecutor} owns those. This keeps authentication and
 * circuit handling in one place instead of being repeated, slightly differently, in every API method.
 * <p>
 * The executor handles 401 and 403 itself, so {@link #onError(int, String)} is never called for them.
 * <p>Example:
 * <pre>{@code
 * Set<MetasysTrendSample> samples = executor.execute(new FindTrendSamplesCommand(objectId, from, to));
 * }</pre>
 *
 * @param <T> the result of a successful call
 */
public interface MetasysCommand<T> {

    /**
     * @return the request to send; must not depend on connection state
     */
    MetasysRequest request();

    /**
     * Interpret a 2xx response.
     *
     * @param body the response body
     * @return the parsed result
     */
    T onSuccess(String body);

    /**
     * Interpret a non-2xx response that is not an authentication failure.
     * <p>
     * Override to map statuses that mean something specific for this command, such as 404 for a
     * missing trend. The default treats every status as a failed call.
     *
     * @param status HTTP status code
     * @param body   the response body
     * @return a result, if the command can recover from this status
     * @throws MetasysApiException carrying {@code status}, unless overridden
     */
    default T onError(int status, String body) {
        throw new MetasysApiException("HTTP " + status, status);
    }
}
