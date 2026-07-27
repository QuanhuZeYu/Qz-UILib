package club.heiqi.uilib.ui.container.experimental.minecraft;

import net.minecraft.item.ItemStack;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.container.experimental.model.ItemDescriptor;

/** Minecraft ItemStack descriptor 编解码合同。 */
public class MinecraftItemDescriptorCodecTest {
    private final MinecraftItemDescriptorCodec codec = new MinecraftItemDescriptorCodec();

    @Test
    public void descriptorIgnoresStackAmountAndRoundTripsNbt() {
        ItemStack one = new ItemStack(MinecraftTestItems.ITEM, 1, 0);
        ItemStack many = new ItemStack(MinecraftTestItems.ITEM, 37, 0);
        ItemDescriptor descriptor = codec.describe(one);

        Assert.assertEquals(descriptor, codec.describe(many));
        Assert.assertTrue(codec.matches(many, descriptor));
        Assert.assertEquals(37, codec.materialize(descriptor, 37).stackSize);
    }

    @Test
    public void materializeDoesNotAddLegacyByteCap() {
        ItemDescriptor descriptor = codec.describe(new ItemStack(MinecraftTestItems.ITEM, 1, 0));
        Assert.assertEquals(200, codec.materialize(descriptor, 200).stackSize);
        try {
            codec.materialize(descriptor, (long) Integer.MAX_VALUE + 1L);
            Assert.fail();
        } catch (IllegalArgumentException expected) { }
    }

    @Test
    public void rejectsForeignCodec() {
        ItemDescriptor descriptor = codec.describe(new ItemStack(MinecraftTestItems.ITEM, 1, 0));
        try {
            codec.materialize(new ItemDescriptor(descriptor.typeId(), "foreign", descriptor.payload()), 1);
            Assert.fail();
        } catch (IllegalArgumentException expected) { }
    }
}
