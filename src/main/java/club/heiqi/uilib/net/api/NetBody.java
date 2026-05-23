package club.heiqi.uilib.net.api;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * 网络消息 body。
 */
public final class NetBody {

    private static final NetBody EMPTY = new NetBody(NetContentType.BINARY, new byte[0]);

    private final NetContentType contentType;
    private final byte[] bytes;

    private NetBody(NetContentType contentType, byte[] bytes) {
        this.contentType = Objects.requireNonNull(contentType, "contentType");
        this.bytes = bytes == null ? new byte[0] : Arrays.copyOf(bytes, bytes.length);
    }

    /**
     * 创建空 body。
     *
     * @return 空 body
     */
    public static NetBody empty() {
        return EMPTY;
    }

    /**
     * 创建 JSON body。网络层不解析 JSON，只保留内容类型与字节。
     *
     * @param json JSON 文本
     * @return body
     */
    public static NetBody json(String json) {
        return textLike(NetContentType.JSON, json);
    }

    /**
     * 创建 UTF-8 文本 body。
     *
     * @param text 文本
     * @return body
     */
    public static NetBody text(String text) {
        return textLike(NetContentType.TEXT, text);
    }

    /**
     * 创建二进制 body。
     *
     * @param bytes 字节
     * @return body
     */
    public static NetBody binary(byte[] bytes) {
        return of(NetContentType.BINARY, bytes);
    }

    /**
     * 创建自定义内容类型 body。
     *
     * @param contentType 内容类型
     * @param bytes 字节
     * @return body
     */
    public static NetBody of(NetContentType contentType, byte[] bytes) {
        return new NetBody(contentType, bytes);
    }

    /**
     * 返回内容类型。
     *
     * @return 内容类型
     */
    public NetContentType getContentType() {
        return contentType;
    }

    /**
     * 返回 body 字节副本。
     *
     * @return 字节
     */
    public byte[] getBytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    /**
     * 返回 body 字节数。
     *
     * @return 字节数
     */
    public int size() {
        return bytes.length;
    }

    /**
     * 按 UTF-8 解读 body。
     *
     * @return 文本
     */
    public String asUtf8String() {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static NetBody textLike(NetContentType contentType, String text) {
        String safeText = text == null ? "" : text;
        return of(contentType, safeText.getBytes(StandardCharsets.UTF_8));
    }
}
