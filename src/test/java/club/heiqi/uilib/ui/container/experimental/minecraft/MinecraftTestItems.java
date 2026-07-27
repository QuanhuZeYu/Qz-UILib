package club.heiqi.uilib.ui.container.experimental.minecraft;

import net.minecraft.item.Item;

/** 为不启动 Minecraft bootstrap 的纯 JUnit 测试提供已注册物品。 */
final class MinecraftTestItems {
    static final Item ITEM = registerItem();

    private MinecraftTestItems() { }

    private static Item registerItem() {
        Item item = new Item();
        Item.itemRegistry.addObject(32000, "qzuilib:test_item", item);
        return item;
    }
}
