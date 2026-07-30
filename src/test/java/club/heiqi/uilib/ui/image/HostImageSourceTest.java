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
}
