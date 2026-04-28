package club.heiqi.uilib.ui.screen;

import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.control.UiControlRuntimeAdapters;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * `UiDocumentScreens` 的页面描述契约测试。
 */
public class UiDocumentScreensTest {

    /**
     * 验证布局诊断页 definition 会暴露稳定 descriptor 与页面标识契约。
     */
    @Test
    public void shouldExposeStablePageIdForUiTestDefinition() {
        Assert.assertSame(UiDocumentScreens.UI_TEST, UiDocumentScreens.UI_TEST_DEFINITION.getPageDescriptor());
        Assert.assertEquals("ui_test", UiDocumentScreens.UI_TEST_DEFINITION.getPageDescriptor().getPageId());
        Assert.assertEquals(UiDocumentScreens.UI_TEST.getPageId(), UiDocumentScreens.UI_TEST_DEFINITION.getPageDescriptor().getPageId());
        Assert.assertEquals("ui_test", UiDocumentScreens.UI_TEST.getPageId());
    }

    /**
     * 验证布局诊断子页 definition 会暴露独立稳定 descriptor 与页面标识契约。
     */
    @Test
    public void shouldExposeStablePageIdForUiTestLayoutScreen() {
        Assert.assertSame(UiDocumentScreens.UI_TEST_LAYOUT, UiDocumentScreens.UI_TEST_LAYOUT_DEFINITION.getPageDescriptor());
        Assert.assertEquals("ui_test_layout", UiDocumentScreens.UI_TEST_LAYOUT_DEFINITION.getPageDescriptor().getPageId());
        Assert.assertEquals(UiDocumentScreens.UI_TEST_LAYOUT.getPageId(),
                UiDocumentScreens.UI_TEST_LAYOUT_DEFINITION.getPageDescriptor().getPageId());
        Assert.assertEquals("ui_test_layout", UiDocumentScreens.UI_TEST_LAYOUT.getPageId());
    }

    /**
     * 验证 HTML-like smoke 子页 definition 会暴露独立稳定 descriptor 与页面标识契约。
     */
    @Test
    public void shouldExposeStablePageIdForHtmlLikeSmokeScreen() {
        Assert.assertSame(UiDocumentScreens.HTML_LIKE_SMOKE,
                UiDocumentScreens.HTML_LIKE_SMOKE_DEFINITION.getPageDescriptor());
        Assert.assertEquals("html_like_smoke",
                UiDocumentScreens.HTML_LIKE_SMOKE_DEFINITION.getPageDescriptor().getPageId());
        Assert.assertEquals(UiDocumentScreens.HTML_LIKE_SMOKE.getPageId(),
                UiDocumentScreens.HTML_LIKE_SMOKE_DEFINITION.getPageDescriptor().getPageId());
        Assert.assertEquals("html_like_smoke", UiDocumentScreens.HTML_LIKE_SMOKE.getPageId());
    }

    /**
     * 验证大面积磨玻璃测试子页 definition 会暴露独立稳定 descriptor 与页面标识契约。
     */
    @Test
    public void shouldExposeStablePageIdForHtmlLikeGlassScreen() {
        Assert.assertSame(UiDocumentScreens.HTML_LIKE_GLASS,
                UiDocumentScreens.HTML_LIKE_GLASS_DEFINITION.getPageDescriptor());
        Assert.assertEquals("html_like_glass",
                UiDocumentScreens.HTML_LIKE_GLASS_DEFINITION.getPageDescriptor().getPageId());
        Assert.assertEquals(UiDocumentScreens.HTML_LIKE_GLASS.getPageId(),
                UiDocumentScreens.HTML_LIKE_GLASS_DEFINITION.getPageDescriptor().getPageId());
        Assert.assertEquals("html_like_glass", UiDocumentScreens.HTML_LIKE_GLASS.getPageId());
    }

    /**
     * 验证背包页 definition 会暴露独立稳定 descriptor 与页面标识契约。
     */
    @Test
    public void shouldExposeStablePageIdForInventoryOverviewDefinition() {
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
        Assert.assertFalse(UiDocumentScreens.isUiTestLayout(screen));
        Assert.assertFalse(UiDocumentScreens.isHtmlLikeSmoke(screen));
        Assert.assertFalse(UiDocumentScreens.isHtmlLikeGlass(screen));
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
        Assert.assertFalse(UiDocumentScreens.isHtmlLikeGlass(screen));
        Assert.assertEquals("Object", UiDocumentScreens.runtimeScreenNameOf(screen));
    }

    /**
     * 验证显式文档环境会原样保留 measure / runtime adapters。
     */
    @Test
    public void shouldKeepExplicitDocumentScreenEnvironmentDependencies() {
        NoOpTextMeasureService textMeasureService = new NoOpTextMeasureService();
        UiControlRuntimeAdapters runtimeAdapters = UiControlRuntimeAdapters.empty();

        UiDocumentScreens.DocumentScreenEnvironment environment = new UiDocumentScreens.DocumentScreenEnvironment(
                textMeasureService, runtimeAdapters);

        Assert.assertSame(textMeasureService, environment.getTextMeasureService());
        Assert.assertSame(runtimeAdapters, environment.getRuntimeAdapters());
    }

    /**
     * 验证页面 definition 会显式保留页面壳策略解析入口。
     */
    @Test
    public void shouldResolveChromeThroughDefinition() {
        DocumentScreenChrome chrome = UiDocumentScreens.UI_TEST_DEFINITION.resolveChrome(960, 720);

        Assert.assertNotNull(chrome);
        Assert.assertEquals(Math.max(24, 960 / 34), chrome.getRootPadding().getLeft());
        Assert.assertEquals(Math.max(28, 720 / 28), chrome.getRootPadding().getTop());
        Assert.assertEquals(Math.max(16, Math.min(960 / 48, 28)), chrome.getPagePadding().getLeft());
        Assert.assertEquals(Math.max(14, Math.min(720 / 36, 24)), chrome.getPagePadding().getTop());
    }

    /**
     * 验证 HTML-like 直接页面 surface 不再插入旧 retained 页面壳。
     */
    @Test
    public void shouldAttachDirectSurfaceBlocksWithoutLegacyPageShell() {
        Widget root = new Widget();
        Widget block = new Widget();
        DirectDocumentPageAuthoringSurface surface = new DirectDocumentPageAuthoringSurface();

        root.applyLayoutBounds(0, 0, 1000, 800);
        surface.attachRoot(root);
        surface.setContentWidthRange(700, 1080)
                .setMinContentHeight(540)
                .setViewportFillRatio(0.94F, 0.92F)
                .addBlock(block);
        surface.applyFrameBounds(1000, 800, DocumentScreenChrome.resolve(1000, 800));

        Assert.assertEquals(1, root.getChildren().size());
        Assert.assertSame(block, root.getChildren().get(0));
        Assert.assertEquals(57, block.getX());
        Assert.assertEquals(28, block.getY());
        Assert.assertEquals(885, block.getWidth());
        Assert.assertEquals(684, block.getHeight());
        Assert.assertEquals(885, surface.getWidth());
        Assert.assertEquals(684, surface.getHeight());
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

    /**
     * 供测试使用的空文本测量桩。
     */
    private static final class NoOpTextMeasureService implements TextMeasureService {

        @Override
        public int getEpoch() {
            return 1;
        }

        @Override
        public int getStringWidth(String text) {
            return text == null ? 0 : text.length() * 6;
        }

        @Override
        public int getLineHeight() {
            return 9;
        }

        @Override
        public String trimStringToWidth(String text, int targetWidth) {
            return text == null ? "" : text;
        }

        @Override
        public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            return Collections.singletonList(text == null ? "" : text);
        }
    }

}
