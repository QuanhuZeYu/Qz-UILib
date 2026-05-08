package club.heiqi.uilib.ui.runtime;

import java.util.Objects;

import club.heiqi.uilib.ui.image.HostImageRenderer;
import club.heiqi.uilib.ui.image.MinecraftHostImageRenderer;
import club.heiqi.uilib.ui.inventory.InventorySlotGridItemRenderer;
import club.heiqi.uilib.ui.inventory.MinecraftInventorySlotGridItemRenderer;

/**
 * UI 运行时适配器集合。
 *
 * <p>当前仅承载背包网格物品渲染委托，用于把运行时渲染能力以窄类型形式透传到 HTML-like 控件层，
 * 避免引入通用注册表或 service locator。</p>
 */
public final class UiRuntimeAdapters {

    private final InventorySlotGridItemRenderer inventorySlotGridItemRenderer;
    private final HostImageRenderer hostImageRenderer;

    private UiRuntimeAdapters(InventorySlotGridItemRenderer inventorySlotGridItemRenderer,
            HostImageRenderer hostImageRenderer) {
        this.inventorySlotGridItemRenderer = inventorySlotGridItemRenderer;
        this.hostImageRenderer = hostImageRenderer;
    }

    /**
     * 创建不附带任何运行时默认值的适配器集合。
     *
     * <p>测试或非 Minecraft 宿主可以从该空集合开始，按需显式注入所需能力。</p>
     *
     * @return 空适配器集合
     */
    public static UiRuntimeAdapters empty() {
        return new UiRuntimeAdapters(null, null);
    }

    /**
     * 创建使用 Minecraft 默认运行时行为的适配器集合。
     *
     * <p>默认 renderer 的创建责任收敛在适配器边界，避免控件内部再隐式回退到 Minecraft 运行时。</p>
     *
     * @return 默认适配器集合
     */
    public static UiRuntimeAdapters minecraftDefaults() {
        return new UiRuntimeAdapters(new MinecraftInventorySlotGridItemRenderer(), new MinecraftHostImageRenderer());
    }

    /**
     * 返回注入指定背包网格物品渲染委托后的新适配器集合。
     *
     * @param inventorySlotGridItemRenderer 背包网格物品渲染委托
     * @return 新适配器集合
     */
    public UiRuntimeAdapters withInventorySlotGridItemRenderer(
            InventorySlotGridItemRenderer inventorySlotGridItemRenderer) {
        return new UiRuntimeAdapters(
                Objects.requireNonNull(inventorySlotGridItemRenderer, "inventorySlotGridItemRenderer"),
                hostImageRenderer);
    }

    /**
     * 返回注入指定宿主图片渲染委托后的新适配器集合。
     *
     * @param hostImageRenderer 宿主图片渲染委托
     * @return 新适配器集合
     */
    public UiRuntimeAdapters withHostImageRenderer(HostImageRenderer hostImageRenderer) {
        return new UiRuntimeAdapters(inventorySlotGridItemRenderer,
                Objects.requireNonNull(hostImageRenderer, "hostImageRenderer"));
    }

    /**
     * 获取背包网格物品渲染委托。
     *
     * @return 背包网格物品渲染委托；为空时调用方仅绘制槽背景/边框
     */
    public InventorySlotGridItemRenderer getInventorySlotGridItemRenderer() {
        return inventorySlotGridItemRenderer;
    }

    /**
     * 获取宿主图片渲染委托。
     *
     * @return 宿主图片渲染委托；为空时无法使用 `img`/背景贴图这类宿主图片能力
     */
    public HostImageRenderer getHostImageRenderer() {
        return hostImageRenderer;
    }
}
