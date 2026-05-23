package club.heiqi.uilib.net.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Fetch 响应。
 */
public final class NetResponse {

    private final int statusCode;
    private final Map<String, String> headers;
    private final NetBody body;

    private NetResponse(int statusCode, Map<String, String> headers, NetBody body) {
        if (statusCode < 100 || statusCode > 999) {
            throw new IllegalArgumentException("statusCode must be 100..999");
        }
        this.statusCode = statusCode;
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<String, String>(NetHeaders.normalize(headers)));
        this.body = Objects.requireNonNull(body, "body");
    }

    public static NetResponse of(int statusCode, NetBody body) {
        return new NetResponse(statusCode, Collections.<String, String>emptyMap(), body);
    }

    public static NetResponse ok(NetBody body) {
        return of(200, body);
    }

    public static NetResponse json(String json) {
        return ok(NetBody.json(json));
    }

    public static NetResponse text(String text) {
        return ok(NetBody.text(text));
    }

    public static NetResponse binary(byte[] bytes) {
        return ok(NetBody.binary(bytes));
    }

    public static NetResponse error(int statusCode, String message) {
        return of(statusCode, NetBody.text(message));
    }

    public NetResponse withHeader(String name, String value) {
        Map<String, String> next = new LinkedHashMap<String, String>(headers);
        next.put(NetHeaders.normalizeName(name), NetHeaders.normalizeValue(value));
        return new NetResponse(statusCode, next, body);
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isOk() {
        return statusCode >= 200 && statusCode < 300;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getHeader(String name) {
        return headers.get(NetHeaders.normalizeName(name));
    }

    public NetBody getBody() {
        return body;
    }

    public NetContentType getContentType() {
        return body.getContentType();
    }

    static NetResponse fromWire(int statusCode, Map<String, String> headers, NetBody body) {
        return new NetResponse(statusCode, headers, body);
    }
}
