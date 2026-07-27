package club.heiqi.uilib.ui.container.experimental.minecraft;

import java.lang.reflect.Method;

import net.minecraft.item.Item;

/** 为不启动 Minecraft bootstrap 的纯 JUnit 测试提供已注册物品。 */
final class MinecraftTestItems {
    static final Item ITEM = registerItem();

    private MinecraftTestItems() { }

    private static Item registerItem() {
        Item item = new Item();
        try {
            // 公共入口依赖 LaunchWrapper；纯 JUnit 直接写入 Forge 1.7.10 的底层 registry。
            Method addObjectRaw = Item.itemRegistry.getClass().getDeclaredMethod(
                    "addObjectRaw", int.class, String.class, Object.class);
            addObjectRaw.setAccessible(true);
            addObjectRaw.invoke(Item.itemRegistry, 32000, "qzuilib:test_item", item);
            return item;
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
