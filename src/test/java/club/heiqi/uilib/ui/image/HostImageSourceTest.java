package club.heiqi.uilib.ui.image;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Assert;
import org.junit.Test;

/** ItemStack icon source 的完整 snapshot copy 测试。 */
public class HostImageSourceTest {

    @Test
    public void itemIconCopiesCountMetadataAndNbtAtCreation() {
        ItemStack stack = new ItemStack(new Item(), 3, 2);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("marker", "snapshot");
        stack.setTagCompound(tag);

        HostImageSource source = HostImageSource.itemIcon(stack);

        stack.stackSize = 9;
        stack.setItemDamage(9);
        tag.setString("marker", "mutated");

        ItemStack snapshot = source.getItemIconStack();
        Assert.assertEquals(HostImageSource.Kind.ITEM_ICON, source.getKind());
        Assert.assertEquals(3, snapshot.stackSize);
        Assert.assertEquals(2, snapshot.getItemDamage());
        Assert.assertEquals("snapshot", snapshot.getTagCompound().getString("marker"));

        snapshot.setItemDamage(12);
        snapshot.getTagCompound().setString("marker", "returned-copy");
        Assert.assertEquals(2, source.getItemIconStack().getItemDamage());
        Assert.assertEquals("snapshot", source.getItemIconStack().getTagCompound().getString("marker"));
    }

    /** 显式分级键必须优先于自算键，且不改变图像与快照语义（ADR §5.1 的 S-1 根因接缝）。 */
    @Test
    public void explicitRegistryKeyWinsOverDerivedKey() {
        ItemStack stack = new ItemStack(new Item(), 1, 3);

        HostImageSource derived = HostImageSource.itemIcon(stack);
        HostImageSource explicit = HostImageSource.itemIcon(stack, "minecraft:stone@3");

        Assert.assertEquals("minecraft:stone@3", explicit.registryKey());
        Assert.assertEquals(HostImageSource.Kind.ITEM_ICON, explicit.getKind());
        Assert.assertEquals(derived.getItemIconStack().getItemDamage(), explicit.getItemIconStack().getItemDamage());
        Assert.assertEquals(derived.getItemIconStack().stackSize, explicit.getItemIconStack().stackSize);
    }

    /** null / 空串 / 空白显式键都必须回落到自算口径（不改变既有调用方行为）。 */
    @Test
    public void blankOrNullExplicitKeyFallsBackToDerivedKey() {
        ItemStack stack = new ItemStack(new Item(), 1, 3);
        String derived = HostImageSource.itemIcon(stack).registryKey();

        Assert.assertEquals(derived, HostImageSource.itemIcon(stack, null).registryKey());
        Assert.assertEquals(derived, HostImageSource.itemIcon(stack, "").registryKey());
        Assert.assertEquals(derived, HostImageSource.itemIcon(stack, "   ").registryKey());
    }

    /** 显式键按 trim 后原样返回（不做解析、不做归一，解析归 PickerIconKey）。 */
    @Test
    public void explicitKeyIsTrimmedAndReturnedVerbatim() {
        ItemStack stack = new ItemStack(new Item(), 1, 0);
        Assert.assertEquals("minecraft:stone", HostImageSource.itemIcon(stack, "  minecraft:stone  ").registryKey());
    }

    /** 非物品图标源仍返回 null（不参与渲染分级）。 */
    @Test
    public void nonItemKindsKeepNullRegistryKey() {
        java.awt.image.BufferedImage bitmap = new java.awt.image.BufferedImage(2, 2,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Assert.assertNull(HostImageSource.bufferedImage(bitmap, "k").registryKey());
    }
}
