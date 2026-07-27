package club.heiqi.uilib.ui.container.experimental.minecraft;

import java.util.Objects;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import club.heiqi.uilib.ui.container.experimental.model.EntryKey;
import club.heiqi.uilib.ui.container.experimental.model.ItemDescriptor;
import club.heiqi.uilib.ui.container.experimental.model.LongContainerSnapshot;
import club.heiqi.uilib.ui.container.experimental.model.LongEntrySnapshot;
import club.heiqi.uilib.ui.container.experimental.operation.LongContainerIntent;
import club.heiqi.uilib.ui.container.experimental.storage.LongContainerStorage;
import club.heiqi.uilib.ui.container.experimental.storage.TransferMode;
import club.heiqi.uilib.ui.container.experimental.storage.TransferResult;
import club.heiqi.uilib.ui.container.experimental.storage.TransferStatus;

/** 服务端主线程上的 long Entry storage 与 `InventoryPlayer` 协调器。 */
public final class MinecraftLongContainerController {
    private final LongContainerStorage storage;
    private final MinecraftItemDescriptorCodec codec;
    private final DropEmitter drops;

    /** 创建 controller；调用方负责只在服务端主线程执行公开操作。 */
    public MinecraftLongContainerController(LongContainerStorage storage,
            MinecraftItemDescriptorCodec codec, DropEmitter drops) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.drops = Objects.requireNonNull(drops, "drops");
    }

    /** 按服务端当前 snapshot、cursor 和 itemMax 执行 semantic intent。 */
    public TransferResult execute(LongContainerIntent intent, InventoryPlayer player) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(player, "player");
        ExperimentalContainerDiagnostics.log("controller request kind={} key={}", intent.kind(), intent.key());
        TransferResult result;
        switch (intent.kind()) {
            case TAKE_STACK:
                result = take(intent.key(), player, false);
                break;
            case TAKE_HALF_STACK:
                result = take(intent.key(), player, true);
                break;
            case DEPOSIT_ALL:
                result = deposit(player, false);
                break;
            case DEPOSIT_ONE:
                result = deposit(player, true);
                break;
            case QUICK_EXTRACT:
                result = quickExtract(intent.key(), player);
                break;
            case DROP_ONE:
                result = drop(intent.key(), 1);
                break;
            case DROP_STACK:
                result = drop(intent.key(), -1);
                break;
            default:
                result = noChange(storage.snapshot());
                break;
        }
        ExperimentalContainerDiagnostics.log("controller result kind={} status={} moved={}",
                intent.kind(), result.status(), Long.valueOf(result.moved()));
        return result;
    }

    /** 将玩家真实槽当前数量按 `UP_TO` 插入 storage，并只扣实际 moved。 */
    public TransferResult shiftFromPlayerSlot(int slotIndex, InventoryPlayer player) {
        Objects.requireNonNull(player, "player");
        if (slotIndex < 0 || slotIndex >= player.getSizeInventory()) {
            throw new IllegalArgumentException("slotIndex outside player inventory: " + slotIndex);
        }
        ItemStack stack = player.getStackInSlot(slotIndex);
        if (stack == null || stack.stackSize <= 0) return noChange(storage.snapshot());
        ItemDescriptor descriptor = codec.describe(stack);
        TransferResult result = storage.insert(descriptor, stack.stackSize, TransferMode.UP_TO);
        if (result.moved() > 0) {
            player.decrStackSize(slotIndex, checkedInt(result.moved()));
            player.markDirty();
        }
        ExperimentalContainerDiagnostics.log("controller shift-in slot={} status={} moved={}",
                Integer.valueOf(slotIndex), result.status(), Long.valueOf(result.moved()));
        return result;
    }

    /** 归还 host 明确拥有的 confirmed carried；余量走正常掉落出口。 */
    public void returnConfirmedCarried(InventoryPlayer player, DropEmitter dropEmitter) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(dropEmitter, "dropEmitter");
        ItemStack carried = player.getItemStack();
        if (carried == null || carried.stackSize <= 0) return;
        player.setItemStack(null);
        ItemStack remainder = carried.copy();
        player.addItemStackToInventory(remainder);
        if (remainder.stackSize > 0) dropEmitter.spawn(remainder);
        player.markDirty();
        ExperimentalContainerDiagnostics.log("controller returned carried initial={} remainder={}",
                Integer.valueOf(carried.stackSize), Integer.valueOf(remainder.stackSize));
    }

    private TransferResult take(EntryKey key, InventoryPlayer player, boolean half) {
        if (player.getItemStack() != null) return noChange(storage.snapshot());
        LongEntrySnapshot entry = find(storage.snapshot(), key);
        if (entry == null) return notFound(storage.snapshot());
        long itemMax = codec.maxStackSize(entry.item(), null);
        long stackAmount = Math.min(entry.amount(), itemMax);
        long requested = half ? (stackAmount + 1L) / 2L : stackAmount;
        ItemStack materialized = codec.materialize(entry.item(), requested);
        TransferResult result = storage.extract(key, requested, TransferMode.EXACT);
        if (result.moved() > 0) {
            materialized.stackSize = checkedInt(result.moved());
            player.setItemStack(materialized);
        }
        return result;
    }

    private TransferResult deposit(InventoryPlayer player, boolean one) {
        ItemStack carried = player.getItemStack();
        if (carried == null || carried.stackSize <= 0) return noChange(storage.snapshot());
        long requested = one ? 1L : carried.stackSize;
        TransferResult result = storage.insert(codec.describe(carried), requested, TransferMode.EXACT);
        if (result.moved() > 0) {
            carried.stackSize -= checkedInt(result.moved());
            if (carried.stackSize <= 0) player.setItemStack(null);
            player.markDirty();
        }
        return result;
    }

    private TransferResult quickExtract(EntryKey key, InventoryPlayer player) {
        LongContainerSnapshot snapshot = storage.snapshot();
        LongEntrySnapshot entry = find(snapshot, key);
        if (entry == null) return notFound(snapshot);
        ItemStack sample = codec.materialize(entry.item(), 1);
        long requested = Math.min(entry.amount(), codec.maxStackSize(entry.item(), sample));
        requested = Math.min(requested, inventoryCapacity(player, sample));
        if (requested <= 0) return noChange(snapshot);
        TransferResult result = storage.extract(key, requested, TransferMode.UP_TO);
        if (result.moved() > 0) insertKnownCapacity(player, codec.materialize(entry.item(), result.moved()));
        return result;
    }

    private TransferResult drop(EntryKey key, long fixedAmount) {
        LongContainerSnapshot snapshot = storage.snapshot();
        LongEntrySnapshot entry = find(snapshot, key);
        if (entry == null) return notFound(snapshot);
        long requested = fixedAmount > 0 ? fixedAmount
                : Math.min(entry.amount(), codec.maxStackSize(entry.item(), null));
        ItemStack materialized = codec.materialize(entry.item(), requested);
        TransferResult result = storage.extract(key, requested, TransferMode.EXACT);
        if (result.moved() > 0) {
            materialized.stackSize = checkedInt(result.moved());
            drops.spawn(materialized);
        }
        return result;
    }

    private static long inventoryCapacity(InventoryPlayer player, ItemStack sample) {
        int perStack = Math.max(1, Math.min(sample.getMaxStackSize(), player.getInventoryStackLimit()));
        long capacity = 0;
        for (ItemStack existing : player.mainInventory) {
            if (existing == null) {
                capacity += sample.isItemDamaged() ? 1L : perStack;
            } else if (!sample.isItemDamaged() && sameItem(existing, sample)) {
                capacity += Math.max(0, perStack - existing.stackSize);
            }
        }
        return capacity;
    }

    private static void insertKnownCapacity(InventoryPlayer player, ItemStack incoming) {
        int perStack = Math.max(1, Math.min(incoming.getMaxStackSize(), player.getInventoryStackLimit()));
        if (!incoming.isItemDamaged()) {
            for (ItemStack existing : player.mainInventory) {
                if (incoming.stackSize <= 0) break;
                if (existing != null && sameItem(existing, incoming) && existing.stackSize < perStack) {
                    int moved = Math.min(incoming.stackSize, perStack - existing.stackSize);
                    existing.stackSize += moved;
                    incoming.stackSize -= moved;
                }
            }
        }
        for (int index = 0; index < player.mainInventory.length && incoming.stackSize > 0; index++) {
            if (player.mainInventory[index] != null) continue;
            int moved = incoming.isItemDamaged() ? 1 : Math.min(incoming.stackSize, perStack);
            ItemStack placed = incoming.copy();
            placed.stackSize = moved;
            player.mainInventory[index] = placed;
            incoming.stackSize -= moved;
        }
        if (incoming.stackSize != 0) {
            throw new IllegalStateException("inventory capacity changed during server-thread transfer");
        }
        player.markDirty();
    }

    private static boolean sameItem(ItemStack left, ItemStack right) {
        return left.isItemEqual(right) && ItemStack.areItemStackTagsEqual(left, right);
    }

    private static LongEntrySnapshot find(LongContainerSnapshot snapshot, EntryKey key) {
        if (key == null) return null;
        for (LongEntrySnapshot entry : snapshot.entries()) {
            if (key.equals(entry.key())) return entry;
        }
        return null;
    }

    private static TransferResult noChange(LongContainerSnapshot snapshot) {
        return new TransferResult(TransferStatus.NO_CHANGE, 0, snapshot);
    }

    private static TransferResult notFound(LongContainerSnapshot snapshot) {
        return new TransferResult(TransferStatus.NOT_FOUND, 0, snapshot);
    }

    private static int checkedInt(long amount) {
        if (amount < 0 || amount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("amount outside ItemStack range: " + amount);
        }
        return (int) amount;
    }
}
