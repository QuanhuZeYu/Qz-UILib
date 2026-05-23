package club.heiqi.uilib.net.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Fetch 请求。
 */
public final class NetRequest {

    private final Map<String, String> headers;
    private final NetBody body;

    private NetRequest(Map<String, String> headers, NetBody body) {
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<String, String>(NetHeaders.normalize(headers)));
        this.body = Objects.requireNonNull(body, "body");
    }

    public static NetRequest of(NetBody body) {
        return new NetRequest(Collections.<String, String>emptyMap(), body);
    }

    public static NetRequest json(String json) {
        return of(NetBody.json(json));
    }

    public static NetRequest text(String text) {
        return of(NetBody.text(text));
    }

    public static NetRequest binary(byte[] bytes) {
        return of(NetBody.binary(bytes));
    }

    public NetRequest withHeader(String name, String value) {
        Map<String, String> next = new LinkedHashMap<String, String>(headers);
        next.put(NetHeaders.normalizeName(name), NetHeaders.normalizeValue(value));
        return new NetRequest(next, body);
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

    static NetRequest fromWire(Map<String, String> headers, NetBody body) {
        return new NetRequest(headers, body);
    }
}
