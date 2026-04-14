package club.heiqi.uilib.ui.screen;

import org.junit.Assert;
import org.junit.Test;

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
     * 验证 descriptor 持有者在没有 `GuiScreen` 运行时的情况下仍能暴露稳定页面标识。
     */
    @Test
    public void shouldResolvePageIdForDescriptorOwnerWithoutGuiScreen() {
        FakeDescriptorOwner screen = new FakeDescriptorOwner(UiDocumentScreens.UI_TEST);

        Assert.assertEquals(UiDocumentScreens.UI_TEST.getPageId(), UiDocumentScreens.getPageId(screen));
        Assert.assertTrue(UiDocumentScreens.isUiTest(screen));
        Assert.assertEquals(UiDocumentScreens.UI_TEST.getPageId(), UiDocumentScreens.runtimeScreenNameOf(screen));
    }

    /**
     * 验证普通对象不会被误判为布局诊断页。
     */
    @Test
    public void shouldReturnFalseForPlainObject() {
        Object screen = new Object();

        Assert.assertEquals("", UiDocumentScreens.getPageId(screen));
        Assert.assertFalse(UiDocumentScreens.isUiTest(screen));
        Assert.assertEquals("Object", UiDocumentScreens.runtimeScreenNameOf(screen));
    }

    /**
     * 供测试使用的最小 descriptor 持有者。
     */
    private static final class FakeDescriptorOwner implements UiDocumentScreens.DescriptorOwner {

        private final UiDocumentScreens.PageDescriptor descriptor;

        private FakeDescriptorOwner(UiDocumentScreens.PageDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public UiDocumentScreens.PageDescriptor getPageDescriptor() {
            return descriptor;
        }
    }
}
