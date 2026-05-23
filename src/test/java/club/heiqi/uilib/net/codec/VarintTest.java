package club.heiqi.uilib.net.codec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import org.junit.Assert;
import org.junit.Test;

/**
 * `Varint` 的边界测试。
 */
public class VarintTest {

    @Test
    public void shouldRoundTripSignedIntValues() throws Exception {
        int[] values = new int[] { 0, 1, -1, 127, 128, -128, Integer.MAX_VALUE, Integer.MIN_VALUE };
        for (int value : values) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            Varint.writeSignedInt(new DataOutputStream(bytes), value);
            int decoded = Varint.readSignedInt(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
            Assert.assertEquals(value, decoded);
        }
    }

    @Test
    public void shouldRoundTripSignedLongValues() throws Exception {
        long[] values = new long[] { 0L, 1L, -1L, 127L, 128L, -128L, Long.MAX_VALUE, Long.MIN_VALUE };
        for (long value : values) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            Varint.writeSignedLong(new DataOutputStream(bytes), value);
            long decoded = Varint.readSignedLong(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
            Assert.assertEquals(value, decoded);
        }
    }
}
