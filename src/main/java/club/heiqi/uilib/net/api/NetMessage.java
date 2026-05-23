package club.heiqi.uilib.net.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Channel 消息。
 */
public final class NetMessage {

    private final Map<String, String> headers;
    private final NetBody body;

    private NetMessage(Map<String, String> headers, NetBody body) {
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<String, String>(NetHeaders.normalize(headers)));
        this.body = Objects.requireNonNull(body, "body");
    }

    /**
     * 创建只有 body 的消息。
     *
     * @param body body
     * @return 消息
     */
    public static NetMessage of(NetBody body) {
        return new NetMessage(Collections.<String, String>emptyMap(), body);
    }

    /**
     * 创建 JSON 消息。
     *
     * @param json JSON 文本
     * @return 消息
     */
    public static NetMessage json(String json) {
        return of(NetBody.json(json));
    }

    /**
     * 创建文本消息。
     *
     * @param text 文本
     * @return 消息
     */
    public static NetMessage text(String text) {
        return of(NetBody.text(text));
    }

    /**
     * 创建二进制消息。
     *
     * @param bytes 字节
     * @return 消息
     */
    public static NetMessage binary(byte[] bytes) {
        return of(NetBody.binary(bytes));
    }

    /**
     * 返回带额外 header 的新消息。
     *
     * @param name header 名
     * @param value header 值
     * @return 新消息
     */
    public NetMessage withHeader(String name, String value) {
        Map<String, String> next = new LinkedHashMap<String, String>(headers);
        next.put(NetHeaders.normalizeName(name), NetHeaders.normalizeValue(value));
        return new NetMessage(next, body);
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

    static NetMessage fromWire(Map<String, String> headers, NetBody body) {
        return new NetMessage(headers, body);
    }
}
