package club.heiqi.uilib.ui.container.experimental.minecraft;

import java.util.Collections;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.container.experimental.model.EntryKey;
import club.heiqi.uilib.ui.container.experimental.model.ItemDescriptor;
import club.heiqi.uilib.ui.container.experimental.model.LongContainerSnapshot;
import club.heiqi.uilib.ui.container.experimental.model.LongEntrySnapshot;
import club.heiqi.uilib.ui.container.experimental.operation.LongContainerIntent;
import club.heiqi.uilib.ui.container.experimental.storage.LongContainerStorage;
import club.heiqi.uilib.ui.container.experimental.storage.TransferMode;
import club.heiqi.uilib.ui.container.experimental.storage.TransferResult;
import club.heiqi.uilib.ui.container.experimental.storage.TransferStatus;

/** 服务端 controller 的数量守恒与真实 inventory/cursor 更新合同。 */
public class MinecraftLongContainerControllerTest {
    private static final EntryKey KEY = new EntryKey("test", "apple");
    private final MinecraftItemDescriptorCodec codec = new MinecraftItemDescriptorCodec();

    @Test
    public void takeHalfAndDepositOneUseConfirmedCursor() {
        FakeStorage storage = new FakeStorage(codec.describe(new ItemStack(Items.apple, 1)), 127);
        CapturingDrops drops = new CapturingDrops();
        MinecraftLongContainerController controller = new MinecraftLongContainerController(storage, codec, drops);
        InventoryPlayer player = new InventoryPlayer(null);

        TransferResult take = controller.execute(LongContainerIntent.takeHalfStack(KEY), player);
        Assert.assertEquals(32, take.moved());
        Assert.assertEquals(32, player.getItemStack().stackSize);
        Assert.assertEquals(95, storage.amount);

        TransferResult deposit = controller.execute(LongContainerIntent.depositOne(), player);
        Assert.assertEquals(1, deposit.moved());
        Assert.assertEquals(31, player.getItemStack().stackSize);
        Assert.assertEquals(96, storage.amount);
    }

    @Test
    public void quickExtractUsesInventoryCapacityAndDropUsesEmitter() {
        FakeStorage storage = new FakeStorage(codec.describe(new ItemStack(Items.apple, 1)), 100);
        CapturingDrops drops = new CapturingDrops();
        MinecraftLongContainerController controller = new MinecraftLongContainerController(storage, codec, drops);
        InventoryPlayer player = new InventoryPlayer(null);

        TransferResult quick = controller.execute(LongContainerIntent.quickExtract(KEY), player);
        Assert.assertEquals(64, quick.moved());
        Assert.assertEquals(64, player.mainInventory[0].stackSize);
        Assert.assertEquals(36, storage.amount);

        TransferResult drop = controller.execute(LongContainerIntent.dropOne(KEY), player);
        Assert.assertEquals(1, drop.moved());
        Assert.assertEquals(1, drops.last.stackSize);
        Assert.assertEquals(35, storage.amount);
    }

    @Test
    public void shiftInOnlyDeductsActualMoved() {
        FakeStorage storage = new FakeStorage(codec.describe(new ItemStack(Items.apple, 1)), 0);
        storage.capacity = 3;
        MinecraftLongContainerController controller = new MinecraftLongContainerController(
                storage, codec, new CapturingDrops());
        InventoryPlayer player = new InventoryPlayer(null);
        player.mainInventory[0] = new ItemStack(Items.apple, 8);

        TransferResult result = controller.shiftFromPlayerSlot(0, player);
        Assert.assertEquals(TransferStatus.PARTIAL, result.status());
        Assert.assertEquals(3, result.moved());
        Assert.assertEquals(5, player.mainInventory[0].stackSize);
    }

    private static final class CapturingDrops implements DropEmitter {
        private ItemStack last;
        @Override public void spawn(ItemStack stack) { last = stack.copy(); }
    }

    private static final class FakeStorage implements LongContainerStorage {
        private final ItemDescriptor item;
        private long amount;
        private long capacity = Long.MAX_VALUE;

        private FakeStorage(ItemDescriptor item, long amount) {
            this.item = item;
            this.amount = amount;
        }

        @Override public LongContainerSnapshot snapshot() {
            return amount == 0
                    ? new LongContainerSnapshot(Collections.<LongEntrySnapshot>emptyList())
                    : new LongContainerSnapshot(Collections.singletonList(new LongEntrySnapshot(KEY, item, amount)));
        }

        @Override public TransferResult insert(ItemDescriptor inserted, long requested, TransferMode mode) {
            long available = Math.max(0, capacity - amount);
            long moved = mode == TransferMode.EXACT && requested > available ? 0 : Math.min(requested, available);
            amount += moved;
            return result(moved, requested);
        }

        @Override public TransferResult extract(EntryKey key, long requested, TransferMode mode) {
            if (!KEY.equals(key)) return new TransferResult(TransferStatus.NOT_FOUND, 0, snapshot());
            long moved = mode == TransferMode.EXACT && requested > amount ? 0 : Math.min(requested, amount);
            amount -= moved;
            return result(moved, requested);
        }

        private TransferResult result(long moved, long requested) {
            TransferStatus status = moved == 0 ? TransferStatus.NO_CHANGE
                    : moved == requested ? TransferStatus.COMPLETED : TransferStatus.PARTIAL;
            return new TransferResult(status, moved, snapshot());
        }
    }
}
