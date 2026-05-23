package club.heiqi.uilib.net.api;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 网络 header 规范化工具。
 */
public final class NetHeaders {

    /** 单帧最多 header 数量。 */
    public static final int MAX_HEADER_COUNT = 32;

    /** header 名 UTF-8 最大字节数。 */
    public static final int MAX_HEADER_NAME_BYTES = 64;

    /** header 值 UTF-8 最大字节数。 */
    public static final int MAX_HEADER_VALUE_BYTES = 1024;

    /** 单帧 header 名和值合计最大字节数。 */
    public static final int MAX_TOTAL_HEADER_BYTES = 8192;

    private NetHeaders() {}

    /**
     * 规范化 header map。
     *
     * @param headers 原始 headers
     * @return 规范化后的有序副本
     */
    public static Map<String, String> normalize(Map<String, String> headers) {
        Map<String, String> normalized = new LinkedHashMap<String, String>();
        if (headers == null || headers.isEmpty()) {
            return normalized;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            normalized.put(normalizeName(entry.getKey()), normalizeValue(entry.getValue()));
        }
        requireWithinLimits(normalized);
        return normalized;
    }

    /**
     * 规范化 header 名。
     *
     * @param name header 名
     * @return 小写 header 名
     */
    public static String normalizeName(String name) {
        if (name == null || name.trim().length() == 0) {
            throw new IllegalArgumentException("header name must not be blank");
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        int bytes = utf8Bytes(normalized);
        if (bytes > MAX_HEADER_NAME_BYTES) {
            throw new IllegalArgumentException("header name too large: " + bytes + " > " + MAX_HEADER_NAME_BYTES);
        }
        for (int index = 0; index < normalized.length(); index++) {
            char value = normalized.charAt(index);
            if (!isTokenChar(value)) {
                throw new IllegalArgumentException("header name contains invalid char: " + name);
            }
        }
        return normalized;
    }

    /**
     * 规范化 header 值。
     *
     * @param value header 值
     * @return header 值
     */
    public static String normalizeValue(String value) {
        String normalized = value == null ? "" : value;
        if (normalized.indexOf('\r') >= 0 || normalized.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("header value must not contain CR/LF");
        }
        int bytes = utf8Bytes(normalized);
        if (bytes > MAX_HEADER_VALUE_BYTES) {
            throw new IllegalArgumentException("header value too large: " + bytes + " > " + MAX_HEADER_VALUE_BYTES);
        }
        return normalized;
    }

    /**
     * 校验 header 数量和总体大小。
     *
     * @param headers headers
     */
    public static void requireWithinLimits(Map<String, String> headers) {
        if (headers.size() > MAX_HEADER_COUNT) {
            throw new IllegalArgumentException("too many headers: " + headers.size() + " > " + MAX_HEADER_COUNT);
        }
        int totalBytes = 0;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            totalBytes += utf8Bytes(entry.getKey());
            totalBytes += utf8Bytes(entry.getValue());
        }
        if (totalBytes > MAX_TOTAL_HEADER_BYTES) {
            throw new IllegalArgumentException("headers too large: " + totalBytes + " > " + MAX_TOTAL_HEADER_BYTES);
        }
    }

    private static int utf8Bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static boolean isTokenChar(char value) {
        return value >= 'a' && value <= 'z'
                || value >= '0' && value <= '9'
                || value == '!' || value == '#' || value == '$' || value == '%' || value == '&'
                || value == '\'' || value == '*' || value == '+' || value == '-' || value == '.'
                || value == '^' || value == '_' || value == '`' || value == '|' || value == '~';
    }
}
