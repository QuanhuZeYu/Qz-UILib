package club.heiqi.uilib.net.api;

import java.util.Locale;
import java.util.Objects;

/**
 * 网络 body 的内容语义类型。
 *
 * <p>网络层只理解内容类型，不把 Java POJO 类型作为协议身份。业务可以使用 JSON、
 * 文本或自定义二进制格式，并在自己的 handler 中解析 body。</p>
 */
public final class NetContentType {

    public static final NetContentType JSON = new NetContentType("application/json");
    public static final NetContentType BINARY = new NetContentType("application/octet-stream");
    public static final NetContentType TEXT = new NetContentType("text/plain; charset=utf-8");

    private final String value;

    private NetContentType(String value) {
        this.value = normalize(value);
    }

    /**
     * 创建自定义内容类型。
     *
     * @param value MIME-like 内容类型
     * @return 内容类型
     */
    public static NetContentType of(String value) {
        return new NetContentType(value);
    }

    /**
     * 返回线协议字符串。
     *
     * @return 内容类型
     */
    public String value() {
        return value;
    }

    /**
     * 判断是否为 JSON。
     *
     * @return true 表示 JSON
     */
    public boolean isJson() {
        String baseType = baseType();
        return "application/json".equals(baseType) || baseType.endsWith("+json");
    }

    /**
     * 判断是否为二进制。
     *
     * @return true 表示二进制
     */
    public boolean isBinary() {
        String baseType = baseType();
        return "application/octet-stream".equals(baseType) || baseType.startsWith("application/x-");
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NetContentType)) {
            return false;
        }
        NetContentType that = (NetContentType) other;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    private static String normalize(String value) {
        if (value == null || value.trim().length() == 0) {
            throw new IllegalArgumentException("contentType must not be blank");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String baseType() {
        int separatorIndex = value.indexOf(';');
        if (separatorIndex < 0) {
            return value;
        }
        return value.substring(0, separatorIndex).trim();
    }
}
