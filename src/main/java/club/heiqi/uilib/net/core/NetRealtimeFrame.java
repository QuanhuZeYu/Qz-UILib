package club.heiqi.uilib.net.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import club.heiqi.uilib.net.api.NetRealtimeMessage;
import club.heiqi.uilib.net.codec.Varint;
import club.heiqi.uilib.net.transport.NetSide;

/**
 * 实时小帧专用线协议。
 */
public final class NetRealtimeFrame {

    private static final int MAGIC = 0x515A5254; // QZRT
    private static final int VERSION = 1;

    private final NetSide targetSide;
    private final String key;
    private final long streamId;
    private final int sequence;
    private final long timestampMillis;
    private final int flags;
    private final byte[] payload;

    private NetRealtimeFrame(NetSide targetSide, String key, long streamId, int sequence, long timestampMillis,
            int flags, byte[] payload) {
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must not be negative");
        }
        if (flags < 0) {
            throw new IllegalArgumentException("flags must not be negative");
        }
        this.targetSide = targetSide;
        this.key = key == null ? "" : key;
        this.streamId = streamId;
        this.sequence = sequence;
        this.timestampMillis = timestampMillis;
        this.flags = flags;
        this.payload = payload == null ? new byte[0] : Arrays.copyOf(payload, payload.length);
    }

    /**
     * 由业务消息创建实时线帧。
     *
     * @param targetSide 接收侧
     * @param key 逻辑 key
     * @param message 业务消息
     * @return 线帧
     */
    public static NetRealtimeFrame of(NetSide targetSide, String key, NetRealtimeMessage message) {
        return new NetRealtimeFrame(targetSide, key, message.getStreamId(), message.getSequence(),
                message.getTimestampMillis(), message.getFlags(), message.getPayload());
    }

    /**
     * 解码实时线帧。
     *
     * @param bytes 二进制数据
     * @return 线帧
     */
    public static NetRealtimeFrame decode(byte[] bytes) {
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));
            int magic = input.readInt();
            if (magic != MAGIC) {
                throw new IllegalArgumentException("未知实时帧 magic: " + Integer.toHexString(magic));
            }
            int version = input.readUnsignedByte();
            if (version != VERSION) {
                throw new IllegalArgumentException("不支持的实时帧版本: " + version);
            }
            NetSide targetSide = NetSide.fromWireId(input.readUnsignedByte());
            String key = readString(input);
            long streamId = input.readLong();
            int sequence = Varint.readUnsignedInt(input);
            long timestampMillis = input.readLong();
            int flags = Varint.readUnsignedInt(input);
            byte[] payload = readBytes(input);
            return new NetRealtimeFrame(targetSide, key, streamId, sequence, timestampMillis, flags, payload);
        } catch (IOException exception) {
            throw new IllegalArgumentException("实时帧解码失败", exception);
        }
    }

    /**
     * 判断字节数组是否命中实时帧 magic。
     *
     * @param bytes 字节数组
     * @return true 表示命中
     */
    public static boolean hasMagic(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return false;
        }
        int magic = (bytes[0] & 0xFF) << 24
                | (bytes[1] & 0xFF) << 16
                | (bytes[2] & 0xFF) << 8
                | (bytes[3] & 0xFF);
        return magic == MAGIC;
    }

    /**
     * 编码实时线帧。
     *
     * @return 二进制数据
     */
    public byte[] encode() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeByte(VERSION);
            output.writeByte(targetSide.getWireId());
            writeString(output, key);
            output.writeLong(streamId);
            Varint.writeUnsignedInt(output, sequence);
            output.writeLong(timestampMillis);
            Varint.writeUnsignedInt(output, flags);
            writeBytes(output, payload);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalArgumentException("实时帧编码失败", exception);
        }
    }

    public NetSide getTargetSide() {
        return targetSide;
    }

    public String getKey() {
        return key;
    }

    public long getStreamId() {
        return streamId;
    }

    public int getSequence() {
        return sequence;
    }

    public long getTimestampMillis() {
        return timestampMillis;
    }

    public int getFlags() {
        return flags;
    }

    public byte[] getPayload() {
        return Arrays.copyOf(payload, payload.length);
    }

    /**
     * 转回业务消息。
     *
     * @return 业务消息
     */
    public NetRealtimeMessage toMessage() {
        return NetRealtimeMessage.of(streamId, sequence, timestampMillis, flags, payload);
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
}
