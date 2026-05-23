package club.heiqi.uilib.net.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 逻辑消息分片重组器。
 */
public final class NetChunkAssembler {

    private static final long DEFAULT_TIMEOUT_MILLIS = 30_000L;

    private final Map<Long, Assembly> assemblies = new HashMap<Long, Assembly>();
    private final long timeoutMillis;

    /**
     * 创建默认分片重组器。
     */
    public NetChunkAssembler() {
        this(DEFAULT_TIMEOUT_MILLIS);
    }

    /**
     * 创建分片重组器。
     *
     * @param timeoutMillis 重组超时
     */
    public NetChunkAssembler(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * 创建分片 payload。
     *
     * @param streamId 分片流 id
     * @param sequence 分片序号
     * @param total 总分片数
     * @param originalLength 原始长度
     * @param chunk 分片内容
     * @return payload
     */
    public static byte[] encodeChunk(long streamId, int sequence, int total, int originalLength, byte[] chunk) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeLong(streamId);
            output.writeInt(sequence);
            output.writeInt(total);
            output.writeInt(originalLength);
            output.writeInt(chunk.length);
            output.write(chunk);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalArgumentException("分片编码失败", exception);
        }
    }

    /**
     * 接收一个分片，若重组完成则返回原始信封数据。
     *
     * @param payload 分片 payload
     * @return 完整信封数据，未完成时为 null
     */
    public synchronized byte[] accept(byte[] payload) {
        purgeExpired(System.currentTimeMillis());
        Chunk chunk = decodeChunk(payload);
        Assembly assembly = assemblies.get(Long.valueOf(chunk.streamId));
        if (assembly == null) {
            assembly = new Assembly(chunk.total, chunk.originalLength);
            assemblies.put(Long.valueOf(chunk.streamId), assembly);
        }
        byte[] completed = assembly.accept(chunk.sequence, chunk.bytes);
        if (completed != null) {
            assemblies.remove(Long.valueOf(chunk.streamId));
        }
        return completed;
    }

    /**
     * 清空所有重组状态。
     */
    public synchronized void clear() {
        assemblies.clear();
    }

    private void purgeExpired(long nowMillis) {
        assemblies.entrySet().removeIf(entry -> nowMillis - entry.getValue().createdAtMillis > timeoutMillis);
    }

    private static Chunk decodeChunk(byte[] payload) {
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload));
            long streamId = input.readLong();
            int sequence = input.readInt();
            int total = input.readInt();
            int originalLength = input.readInt();
            int length = input.readInt();
            if (sequence < 0 || total <= 0 || sequence >= total || originalLength < 0 || length < 0) {
                throw new IllegalArgumentException("非法分片头");
            }
            byte[] bytes = new byte[length];
            input.readFully(bytes);
            return new Chunk(streamId, sequence, total, originalLength, bytes);
        } catch (IOException exception) {
            throw new IllegalArgumentException("分片解码失败", exception);
        }
    }

    private static final class Chunk {

        final long streamId;
        final int sequence;
        final int total;
        final int originalLength;
        final byte[] bytes;

        Chunk(long streamId, int sequence, int total, int originalLength, byte[] bytes) {
            this.streamId = streamId;
            this.sequence = sequence;
            this.total = total;
            this.originalLength = originalLength;
            this.bytes = bytes;
        }
    }

    private static final class Assembly {

        final long createdAtMillis = System.currentTimeMillis();
        final byte[][] chunks;
        final int originalLength;
        int received;

        Assembly(int total, int originalLength) {
            this.chunks = new byte[total][];
            this.originalLength = originalLength;
        }

        byte[] accept(int sequence, byte[] bytes) {
            if (chunks[sequence] == null) {
                chunks[sequence] = bytes;
                received++;
            }
            if (received != chunks.length) {
                return null;
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream(originalLength);
            for (byte[] chunk : chunks) {
                output.write(chunk, 0, chunk.length);
            }
            byte[] completed = output.toByteArray();
            if (completed.length != originalLength) {
                throw new IllegalArgumentException("分片重组长度不匹配，expected=" + originalLength
                        + " actual=" + completed.length);
            }
            return completed;
        }
    }
}
