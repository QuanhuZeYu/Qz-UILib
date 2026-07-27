package club.heiqi.uilib.ui.container.experimental.minecraft;

import java.io.IOException;
import java.util.Objects;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;

import club.heiqi.uilib.ui.container.experimental.model.ItemDescriptor;

/** 以规范化 ItemStack NBT 编解码 experimental `ItemDescriptor`。 */
public final class MinecraftItemDescriptorCodec {
    public static final String CODEC_ID = "qzuilib:minecraft-itemstack-nbt-v1";

    /** 将非空 stack 规范化为忽略数量的 descriptor。 */
    public ItemDescriptor describe(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.getItem() == null || stack.stackSize <= 0) {
            throw new IllegalArgumentException("stack must contain a positive item amount");
        }
        ItemStack normalized = stack.copy();
        normalized.stackSize = 1;
        NBTTagCompound tag = normalized.writeToNBT(new NBTTagCompound());
        Object registryName = Item.itemRegistry.getNameForObject(stack.getItem());
        if (registryName == null) throw new IllegalArgumentException("item is not registered");
        try {
            return new ItemDescriptor(String.valueOf(registryName), CODEC_ID, CompressedStreamTools.compress(tag));
        } catch (IOException exception) {
            throw new IllegalArgumentException("failed to encode ItemStack NBT", exception);
        }
    }

    /** 从 descriptor 物化可由 MC `int stackSize` 表达的数量。 */
    public ItemStack materialize(ItemDescriptor descriptor, long amount) {
        Objects.requireNonNull(descriptor, "descriptor");
        if (!CODEC_ID.equals(descriptor.codecId())) {
            throw new IllegalArgumentException("unsupported descriptor codec: " + descriptor.codecId());
        }
        if (amount <= 0 || amount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("amount is outside ItemStack range: " + amount);
        }
        try {
            NBTTagCompound tag = CompressedStreamTools.func_152457_a(
                    descriptor.payload(), NBTSizeTracker.field_152451_a);
            ItemStack stack = ItemStack.loadItemStackFromNBT(tag);
            if (stack == null || stack.getItem() == null) {
                throw new IllegalArgumentException("descriptor cannot materialize an ItemStack");
            }
            Object registryName = Item.itemRegistry.getNameForObject(stack.getItem());
            if (registryName == null || !descriptor.typeId().equals(String.valueOf(registryName))) {
                throw new IllegalArgumentException("descriptor typeId does not match payload");
            }
            stack.stackSize = (int) amount;
            return stack;
        } catch (IOException exception) {
            throw new IllegalArgumentException("failed to decode ItemStack NBT", exception);
        }
    }

    /** 比较 stack 的规范化 descriptor。 */
    public boolean matches(ItemStack stack, ItemDescriptor descriptor) {
        return stack != null && descriptor != null && descriptor.equals(describe(stack));
    }

    /** 每次操作从真实物品声明读取最大堆叠量，不增加 127 cap。 */
    public long maxStackSize(ItemDescriptor descriptor, ItemStack reference) {
        ItemStack stack = reference != null && matches(reference, descriptor)
                ? reference : materialize(descriptor, 1);
        return Math.max(1, stack.getMaxStackSize());
    }
}
