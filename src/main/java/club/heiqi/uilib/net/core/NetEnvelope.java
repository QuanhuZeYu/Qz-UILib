package club.heiqi.uilib.net.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import club.heiqi.uilib.net.codec.Varint;
import club.heiqi.uilib.net.transport.NetSide;

/**
 * Qz 网络层逻辑信封。
 */
public final class NetEnvelope {

    private static final int MAGIC = 0x515A4E4C; // QZNL
    private static final int VERSION = 1;

    private final Kind kind;
    private final NetSide targetSide;
    private final String key;
    private final int typeId;
    private final long requestId;
    private final byte[] payload;

    private NetEnvelope(Kind kind, NetSide targetSide, String key, int typeId, long requestId, byte[] payload) {
        this.kind = kind;
        this.targetSide = targetSide;
        this.key = key;
        this.typeId = typeId;
        this.requestId = requestId;
        this.payload = payload == null ? new byte[0] : payload;
    }

    /**
     * 创建信封。
     *
     * @param kind 类型
     * @param targetSide 接收侧
     * @param key 逻辑 id
     * @param typeId 类型 id
     * @param requestId 请求 id
     * @param payload 负载
     * @return 信封
     */
    public static NetEnvelope of(Kind kind, NetSide targetSide, String key, int typeId, long requestId,
            byte[] payload) {
        return new NetEnvelope(kind, targetSide, key, typeId, requestId, payload);
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
            int typeId = Varint.readUnsignedInt(input);
            long requestId = input.readLong();
            byte[] payload = readBytes(input);
            return new NetEnvelope(kind, targetSide, key, typeId, requestId, payload);
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
            Varint.writeUnsignedInt(output, typeId);
            output.writeLong(requestId);
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

    public int getTypeId() {
        return typeId;
    }

    public long getRequestId() {
        return requestId;
    }

    public byte[] getPayload() {
        return payload;
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
