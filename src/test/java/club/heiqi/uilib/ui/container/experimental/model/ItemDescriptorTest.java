package club.heiqi.uilib.ui.container.experimental.model;

import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;

public class ItemDescriptorTest {
    @Test public void validatesStringsAndPayload() {
        try { new ItemDescriptor(null, "c", new byte[0]); Assert.fail(); } catch (NullPointerException expected) { }
        try { new ItemDescriptor("", "c", new byte[0]); Assert.fail(); } catch (IllegalArgumentException expected) { }
        try { new ItemDescriptor("t", null, new byte[0]); Assert.fail(); } catch (NullPointerException expected) { }
        try { new ItemDescriptor("t", "", new byte[0]); Assert.fail(); } catch (IllegalArgumentException expected) { }
        try { new ItemDescriptor("t", "c", null); Assert.fail(); } catch (NullPointerException expected) { }
    }
    @Test public void copiesPayloadBothDirectionsAndUsesContentEquality() {
        byte[] bytes = {1, 2};
        ItemDescriptor descriptor = new ItemDescriptor("t", "c", bytes);
        bytes[0] = 9;
        Assert.assertArrayEquals(new byte[] {1, 2}, descriptor.payload());
        byte[] returned = descriptor.payload();
        returned[1] = 8;
        Assert.assertArrayEquals(new byte[] {1, 2}, descriptor.payload());
        Assert.assertEquals(descriptor, new ItemDescriptor("t", "c", new byte[] {1, 2}));
        Assert.assertEquals(descriptor.hashCode(), new ItemDescriptor("t", "c", new byte[] {1, 2}).hashCode());
        Assert.assertFalse(descriptor.toString().contains("1, 2"));
        Assert.assertTrue(Arrays.equals(new byte[] {1, 2}, descriptor.payload()));
    }
}
