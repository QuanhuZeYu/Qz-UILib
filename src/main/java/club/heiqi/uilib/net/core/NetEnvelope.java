package club.heiqi.uilib.net.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import club.heiqi.uilib.net.api.NetBody;
import club.heiqi.uilib.net.api.NetContentType;
import club.heiqi.uilib.net.api.NetHeaders;
import club.heiqi.uilib.net.codec.Varint;
import club.heiqi.uilib.net.transport.NetSide;

/**
 * Qz 网络层逻辑信封。
 *
 * <p>信封只描述网络语义：路由 key、内容类型、header、状态码和 body。
 * Java 类型不进入协议核心。</p>
 */
public final class NetEnvelope {

    private static final int MAGIC = 0x515A4E4C; // QZNL
    private static final int VERSION = 2;

    private final Kind kind;
    private final NetSide targetSide;
    private final String key;
    private final NetContentType contentType;
    private final long requestId;
    private final int statusCode;
    private final Map<String, String> headers;
    private final byte[] payload;

    private NetEnvelope(Kind kind, NetSide targetSide, String key, NetContentType contentType, long requestId,
            int statusCode, Map<String, String> headers, byte[] payload) {
        this.kind = kind;
        this.targetSide = targetSide;
        this.key = key == null ? "" : key;
        this.contentType = contentType == null ? NetContentType.BINARY : contentType;
        this.requestId = requestId;
        this.statusCode = statusCode;
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<String, String>(NetHeaders.normalize(headers)));
        this.payload = payload == null ? new byte[0] : Arrays.copyOf(payload, payload.length);
    }

    /**
     * 创建信封。
     *
     * @param kind 类型
     * @param targetSide 接收侧
     * @param key 路由 key
     * @param requestId Fetch 请求 id
     * @param statusCode Fetch 响应状态码，其它帧为 0
     * @param headers headers
     * @param body body
     * @return 信封
     */
    public static NetEnvelope of(Kind kind, NetSide targetSide, String key, long requestId, int statusCode,
            Map<String, String> headers, NetBody body) {
        NetBody resolvedBody = body == null ? NetBody.empty() : body;
        return new NetEnvelope(kind, targetSide, key, resolvedBody.getContentType(), requestId, statusCode,
                headers == null ? Collections.<String, String>emptyMap() : headers, resolvedBody.getBytes());
    }

    /**
     * 创建内部二进制信封。
     *
     * @param kind 类型
     * @param targetSide 接收侧
     * @param key 路由 key
     * @param requestId 请求 id
     * @param payload 二进制 payload
     * @return 信封
     */
    public static NetEnvelope binary(Kind kind, NetSide targetSide, String key, long requestId, byte[] payload) {
        return new NetEnvelope(kind, targetSide, key, NetContentType.BINARY, requestId, 0,
                Collections.<String, String>emptyMap(), payload);
    }

    /**
     * 解码信封。
     *
     * @param bytes 二进制数据
     * @return 信封
     */
    public static NetEnvelope decode(byte[] bytes) {
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));
            int magic = input.readInt();
            if (magic != MAGIC) {
                throw new IllegalArgumentException("未知网络信封 magic: " + Integer.toHexString(magic));
            }
            int version = input.readUnsignedByte();
            if (version != VERSION) {
                throw new IllegalArgumentException("不支持的网络信封版本: " + version);
            }
            Kind kind = Kind.fromWireId(input.readUnsignedByte());
            NetSide targetSide = NetSide.fromWireId(input.readUnsignedByte());
            String key = readString(input);
            NetContentType contentType = NetContentType.of(readString(input));
            long requestId = input.readLong();
            int statusCode = Varint.readUnsignedInt(input);
            Map<String, String> headers = readHeaders(input);
            byte[] payload = readBytes(input);
            return new NetEnvelope(kind, targetSide, key, contentType, requestId, statusCode, headers, payload);
        } catch (IOException exception) {
            throw new IllegalArgumentException("网络信封解码失败", exception);
        }
    }

    /**
     * 编码信封。
     *
     * @return 二进制数据
     */
    public byte[] encode() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeByte(VERSION);
            output.writeByte(kind.getWireId());
            output.writeByte(targetSide.getWireId());
            writeString(output, key);
            writeString(output, contentType.value());
            output.writeLong(requestId);
            Varint.writeUnsignedInt(output, Math.max(statusCode, 0));
            writeHeaders(output, headers);
            writeBytes(output, payload);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalArgumentException("网络信封编码失败", exception);
        }
    }

    public Kind getKind() {
        return kind;
    }

    public NetSide getTargetSide() {
        return targetSide;
    }

    public String getKey() {
        return key;
    }

    public NetContentType getContentType() {
        return contentType;
    }

    public long getRequestId() {
        return requestId;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public byte[] getPayload() {
        return Arrays.copyOf(payload, payload.length);
    }

    public NetBody toBody() {
        return NetBody.of(contentType, payload);
    }

    private static void writeHeaders(DataOutputStream output, Map<String, String> headers) throws IOException {
        Varint.writeUnsignedInt(output, headers.size());
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            writeString(output, entry.getKey());
            writeString(output, entry.getValue());
        }
    }

    private static Map<String, String> readHeaders(DataInputStream input) throws IOException {
        int size = Varint.readUnsignedInt(input);
        if (size > NetHeaders.MAX_HEADER_COUNT) {
            throw new IllegalArgumentException("too many headers: " + size + " > " + NetHeaders.MAX_HEADER_COUNT);
        }
        Map<String, String> headers = new LinkedHashMap<String, String>();
        for (int index = 0; index < size; index++) {
            headers.put(NetHeaders.normalizeName(readString(input)), NetHeaders.normalizeValue(readString(input)));
        }
        NetHeaders.requireWithinLimits(headers);
        return headers;
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        writeBytes(output, bytes);
    }

    private static String readString(DataInputStream input) throws IOException {
        return new String(readBytes(input), StandardCharsets.UTF_8);
    }

    private static void writeBytes(DataOutputStream output, byte[] bytes) throws IOException {
        Varint.writeUnsignedInt(output, bytes.length);
        output.write(bytes);
    }

    private static byte[] readBytes(DataInputStream input) throws IOException {
        int length = Varint.readUnsignedInt(input);
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return bytes;
    }

    /**
     * 信封类型。
     */
    public enum Kind {
        CHANNEL(1),
        FETCH_REQUEST(2),
        FETCH_RESPONSE(3),
        FETCH_ERROR(4),
        STORE_SNAPSHOT(5),
        STORE_DELTA(6),
        META(7),
        CHUNK(8);

        private final int wireId;

        Kind(int wireId) {
            this.wireId = wireId;
        }

        public int getWireId() {
            return wireId;
        }

        public static Kind fromWireId(int wireId) {
            for (Kind kind : values()) {
                if (kind.wireId == wireId) {
                    return kind;
                }
            }
            throw new IllegalArgumentException("未知信封类型 id：" + wireId);
        }
    }
}
