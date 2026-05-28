package club.heiqi.uilib.net.api;

import java.util.Arrays;

/**
 * 实时 Channel 传输的小二进制帧。
 */
public final class NetRealtimeMessage {

    private final long streamId;
    private final int sequence;
    private final long timestampMillis;
    private final int flags;
    private final byte[] payload;

    private NetRealtimeMessage(long streamId, int sequence, long timestampMillis, int flags, byte[] payload) {
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must not be negative");
        }
        if (flags < 0) {
            throw new IllegalArgumentException("flags must not be negative");
        }
        this.streamId = streamId;
        this.sequence = sequence;
        this.timestampMillis = timestampMillis;
        this.flags = flags;
        this.payload = payload == null ? new byte[0] : Arrays.copyOf(payload, payload.length);
    }

    /**
     * 创建实时帧。
     *
     * @param streamId 业务流 id
     * @param sequence 帧序号
     * @param timestampMillis 业务时间戳
     * @param payload 负载
     * @return 实时帧
     */
    public static NetRealtimeMessage of(long streamId, int sequence, long timestampMillis, byte[] payload) {
        return of(streamId, sequence, timestampMillis, 0, payload);
    }

    /**
     * 创建带 flags 的实时帧。
     *
     * @param streamId 业务流 id
     * @param sequence 帧序号
     * @param timestampMillis 业务时间戳
     * @param flags 业务 flags
     * @param payload 负载
     * @return 实时帧
     */
    public static NetRealtimeMessage of(long streamId, int sequence, long timestampMillis, int flags,
            byte[] payload) {
        return new NetRealtimeMessage(streamId, sequence, timestampMillis, flags, payload);
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

    /**
     * 返回负载字节副本。
     *
     * @return 负载字节
     */
    public byte[] getPayload() {
        return Arrays.copyOf(payload, payload.length);
    }

    /**
     * 返回负载大小。
     *
     * @return 字节数
     */
    public int size() {
        return payload.length;
    }
}
