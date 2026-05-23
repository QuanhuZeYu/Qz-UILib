package club.heiqi.uilib.net.core;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.net.transport.NetSide;

/**
 * `NetChunkAssembler` 分片重组测试。
 */
public class NetChunkAssemblerTest {

    @Test
    public void shouldReassembleChunksInOrder() {
        byte[] payload = new byte[100_000];
        for (int index = 0; index < payload.length; index++) {
            payload[index] = (byte) index;
        }
        byte[] envelope = NetEnvelope.of(NetEnvelope.Kind.CHANNEL, NetSide.SERVER, "test:big", 1, 0L,
                payload).encode();
        NetChunkAssembler assembler = new NetChunkAssembler();

        byte[] completed = null;
        int chunkSize = 12_000;
        int total = (envelope.length + chunkSize - 1) / chunkSize;
        for (int sequence = 0; sequence < total; sequence++) {
            int offset = sequence * chunkSize;
            int length = Math.min(chunkSize, envelope.length - offset);
            byte[] chunk = new byte[length];
            System.arraycopy(envelope, offset, chunk, 0, length);
            completed = assembler.accept(NetChunkAssembler.encodeChunk(99L, sequence, total, envelope.length, chunk));
        }

        Assert.assertArrayEquals(envelope, completed);
        Assert.assertArrayEquals(payload, NetEnvelope.decode(completed).getPayload());
    }
}
