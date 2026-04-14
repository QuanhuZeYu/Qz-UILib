package club.heiqi.uilib.ui.screen;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;

/**
 * `UiDocumentScreens` 的页面描述契约测试。
 */
public class UiDocumentScreensTest {

    /**
     * 验证布局诊断页 definition 会暴露稳定 descriptor 与页面标识契约。
     */
    @Test
    public void shouldExposeStablePageIdForUiTestScreen() {
        Assert.assertSame(UiDocumentScreens.UI_TEST, UiDocumentScreens.UI_TEST_DEFINITION.getPageDescriptor());
        Assert.assertEquals("ui_test", UiDocumentScreens.UI_TEST_DEFINITION.getPageDescriptor().getPageId());
        Assert.assertEquals(UiDocumentScreens.UI_TEST.getPageId(), UiDocumentScreens.UI_TEST_DEFINITION.getPageDescriptor().getPageId());
        Assert.assertEquals("ui_test", UiDocumentScreens.UI_TEST.getPageId());
    }

    /**
     * 验证背包页 definition 会暴露独立稳定 descriptor 与页面标识契约。
     */
    @Test
    public void shouldExposeStablePageIdForInventoryOverviewScreen() {
        Assert.assertSame(UiDocumentScreens.INVENTORY_OVERVIEW,
                UiDocumentScreens.INVENTORY_OVERVIEW_DEFINITION.getPageDescriptor());
        Assert.assertEquals("inventory_overview",
                UiDocumentScreens.INVENTORY_OVERVIEW_DEFINITION.getPageDescriptor().getPageId());
        Assert.assertEquals(UiDocumentScreens.INVENTORY_OVERVIEW.getPageId(),
                UiDocumentScreens.INVENTORY_OVERVIEW_DEFINITION.getPageDescriptor().getPageId());
        Assert.assertEquals("inventory_overview", UiDocumentScreens.INVENTORY_OVERVIEW.getPageId());
    }

    /**
     * 验证非描述对象持有者不会被误判为布局诊断页。
     */
    @Test
    public void shouldReturnFalseForNonDescriptorScreen() {
        assumeGuiScreenRuntimeAvailable();
        GuiScreen screen = new GuiScreen() {};

        Assert.assertEquals("", UiDocumentScreens.getPageId(screen));
        Assert.assertFalse(UiDocumentScreens.isUiTest(screen));
    }

    /**
     * 探测 `GuiScreen` 所需的最小 LWJGL 运行时是否可用。
     */
    private static void assumeGuiScreenRuntimeAvailable() {
        try {
            Class<?> displayModeClass = Class.forName("org.lwjgl.opengl.DisplayMode");
            displayModeClass.getConstructor(int.class, int.class);
        } catch (Throwable throwable) {
            Assume.assumeTrue("当前测试 JVM 缺少兼容的 LWJGL DisplayMode 运行时，页面身份 smoke test 跳过。",
                    false);
        }
    }

    /**
     * 最小背包模型桩，仅用于页面描述契约测试。
     */
    private static final class NoopInventoryOverviewModel implements InventoryOverviewModel {

        private final InventoryOverviewSlotContentProvider emptySlotProvider = new InventoryOverviewSlotContentProvider() {
            @Override
            public ItemStack getStack(int localIndex) {
                return null;
            }
        };

        @Override
        public InventoryOverviewSlotContentProvider getHotbarSlotProvider() {
            return emptySlotProvider;
        }

        @Override
        public InventoryOverviewSlotContentProvider getBackpackSlotProvider() {
            return emptySlotProvider;
        }

        @Override
        public int getHotbarOccupiedCount() {
            return 0;
        }

        @Override
        public int getBackpackOccupiedCount() {
            return 0;
        }

        @Override
        public void returnToVanillaInventory() {
        }
    }
}
