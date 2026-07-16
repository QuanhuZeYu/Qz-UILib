package club.heiqi.uilib.ui.image;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.junit.Assert;
import org.junit.Test;

/** ItemStack 图片源的 LIVE/SNAPSHOT 兼容测试。 */
public class HostImageSourceTest {
    /** 旧入口继续读取调用方当前值，静态入口固定创建时快照。 */
    @Test
    public void liveAndSnapshotHaveDifferentMutationSemantics() {
        ItemStack stack = new ItemStack(new Item(), 1, 2);
        HostImageSource live = HostImageSource.itemStack(stack);
        HostImageSource snapshot = HostImageSource.itemStackSnapshot(stack);
        stack.setItemDamage(9);
        Assert.assertEquals(HostImageSource.ItemPolicy.LIVE, live.getItemPolicy());
        Assert.assertEquals(9, live.getItemStack().getItemDamage());
        Assert.assertEquals(HostImageSource.ItemPolicy.SNAPSHOT, snapshot.getItemPolicy());
        Assert.assertEquals(2, snapshot.getItemStack().getItemDamage());
    }
}
