package club.heiqi.uilib.ui.screen;

import club.heiqi.uilib.ui.screen.internal.InternalDiagnosticScreenRegistry;
import club.heiqi.uilib.ui.screen.internal.InternalHostedScreenFactory;
import club.heiqi.uilib.ui.screen.internal.InternalScreenIdentity;
import club.heiqi.uilib.ui.screen.internal.UiDiagnosticsScreens;
import club.heiqi.uilib.ui.screen.page.DocumentPageController;
import club.heiqi.uilib.ui.screen.page.DirectDocumentPageAuthoringSurface;
import club.heiqi.uilib.ui.screen.page.DocumentPageRuntimeView;
import club.heiqi.uilib.ui.screen.page.DocumentScreenChrome;
import club.heiqi.uilib.ui.screen.page.DocumentUiScope;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.text.TextContentMode;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * `UiDocumentScreens` 的公开边界与内部托管页面契约测试。
 */
public class UiDocumentScreensTest {

    /**
     * 验证业务入口类不再暴露内部 definition / descriptor 结构。
     */
    @Test
    public void shouldKeepUiDocumentScreensAsBusinessFacadeOnly() {
        List<Class<?>> publicMemberClasses = Arrays.asList(UiDocumentScreens.class.getClasses());

        Assert.assertEquals(2, publicMemberClasses.size());
        Assert.assertTrue(publicMemberClasses.contains(UiDocumentScreens.DocumentScreenEnvironment.class));
        Assert.assertTrue(publicMemberClasses.contains(UiDocumentScreens.DocumentScreenContentBuilder.class));

        int publicStaticMethodCount = 0;
        for (Method method : UiDocumentScreens.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || !Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            publicStaticMethodCount++;
            Assert.assertTrue("createDocumentScreen".equals(method.getName()));
        }
        Assert.assertEquals(2, publicStaticMethodCount);
        Assert.assertEquals("club.heiqi.uilib.ui.screen.internal",
                UiDiagnosticsScreens.class.getPackage().getName());
    }

    /**
     * 验证内部诊断注册表仍保留稳定页面标识契约。
     */
    @Test
    public void shouldExposeStablePageIdsForInternalDiagnosticDefinitions() {
        Assert.assertSame(InternalDiagnosticScreenRegistry.UI_TEST,
                InternalDiagnosticScreenRegistry.UI_TEST_DEFINITION.getPageDescriptor());
        Assert.assertEquals("ui_test", InternalDiagnosticScreenRegistry.uiTestPageId());
        Assert.assertEquals("ui_test_layout", InternalDiagnosticScreenRegistry.uiTestLayoutPageId());
        Assert.assertEquals("html_like_smoke", InternalDiagnosticScreenRegistry.htmlLikeSmokePageId());
        Assert.assertEquals("html_like_glass", InternalDiagnosticScreenRegistry.htmlLikeGlassPageId());
        Assert.assertEquals("inventory_overview", InternalDiagnosticScreenRegistry.inventoryOverviewPageId());
        Assert.assertEquals("list_element_drag", InternalDiagnosticScreenRegistry.listElementDragPageId());
        Assert.assertEquals("browser_semantics_showcase",
                InternalDiagnosticScreenRegistry.browserSemanticsShowcasePageId());
        Assert.assertEquals("animation_capability_showcase",
                InternalDiagnosticScreenRegistry.animationCapabilityShowcasePageId());
        Assert.assertEquals("ui_framework_structure_audit",
                InternalDiagnosticScreenRegistry.uiFrameworkStructureAuditPageId());
        Assert.assertEquals("runtime_self_test", InternalDiagnosticScreenRegistry.runtimeSelfTestPageId());
        Assert.assertEquals("net_self_check", InternalDiagnosticScreenRegistry.netSelfCheckPageId());
    }

    /**
     * 验证通用业务文档内部 definition 仍保留稳定 descriptor 与全视口 chrome。
     */
    @Test
    public void shouldExposeStablePageIdAndChromeForDocumentScreenDefinition() {
        Assert.assertSame(InternalHostedScreenFactory.DOCUMENT_SCREEN,
                InternalHostedScreenFactory.DOCUMENT_SCREEN_DEFINITION.getPageDescriptor());
        Assert.assertEquals("document_screen",
                InternalHostedScreenFactory.DOCUMENT_SCREEN_DEFINITION.getPageDescriptor().getPageId());

        DocumentScreenChrome chrome = InternalHostedScreenFactory.DOCUMENT_SCREEN_DEFINITION.resolveChrome(960, 720);

        Assert.assertEquals(0, chrome.getRootPadding().getLeft());
        Assert.assertEquals(0, chrome.getRootPadding().getTop());
        Assert.assertEquals(0, chrome.getRootPadding().getRight());
        Assert.assertEquals(0, chrome.getRootPadding().getBottom());
    }

    /**
     * 验证内部 descriptor 持有者在没有 `GuiScreen` 运行时的情况下仍能暴露稳定页面标识。
     */
    @Test
    public void shouldResolvePageIdForDescriptorOwnerWithoutGuiScreen() {
        FakeDescriptorOwner screen = new FakeDescriptorOwner(InternalDiagnosticScreenRegistry.UI_TEST);

        Assert.assertEquals(InternalDiagnosticScreenRegistry.uiTestPageId(), InternalScreenIdentity.getPageId(screen));
        Assert.assertTrue(UiDiagnosticsScreens.isUiTest(screen));
        Assert.assertFalse(UiDiagnosticsScreens.isUiTestLayout(screen));
        Assert.assertFalse(UiDiagnosticsScreens.isHtmlLikeSmoke(screen));
        Assert.assertFalse(UiDiagnosticsScreens.isHtmlLikeGlass(screen));
        Assert.assertFalse(UiDiagnosticsScreens.isAnimationCapabilityShowcase(screen));
        Assert.assertFalse(UiDiagnosticsScreens.isUiFrameworkStructureAudit(screen));
        Assert.assertFalse(UiDiagnosticsScreens.isNetSelfCheck(screen));
        Assert.assertEquals(InternalDiagnosticScreenRegistry.uiTestPageId(), InternalScreenIdentity.runtimeScreenNameOf(screen));
    }

    /**
     * 验证普通对象不会被误判为内部页面。
     */
    @Test
    public void shouldReturnFalseForPlainObject() {
        Object screen = new Object();

        Assert.assertEquals("", InternalScreenIdentity.getPageId(screen));
        Assert.assertFalse(UiDiagnosticsScreens.isUiTest(screen));
        Assert.assertFalse(UiDiagnosticsScreens.isHtmlLikeGlass(screen));
        Assert.assertFalse(UiDiagnosticsScreens.isAnimationCapabilityShowcase(screen));
        Assert.assertEquals("Object", InternalScreenIdentity.runtimeScreenNameOf(screen));
    }

    /**
     * 验证显式文档环境会原样保留 measure / runtime adapters。
     */
    @Test
    public void shouldKeepExplicitDocumentScreenEnvironmentDependencies() {
        NoOpTextMeasureService textMeasureService = new NoOpTextMeasureService();
        UiRuntimeAdapters runtimeAdapters = UiRuntimeAdapters.empty();

        UiDocumentScreens.DocumentScreenEnvironment environment = new UiDocumentScreens.DocumentScreenEnvironment(
                textMeasureService, runtimeAdapters);

        Assert.assertSame(textMeasureService, environment.getTextMeasureService());
        Assert.assertSame(runtimeAdapters, environment.getRuntimeAdapters());
        Assert.assertEquals(TextContentMode.UILIB_RAW, environment.getDefaultTextContentMode());
    }

    /**
     * 验证业务默认环境与 Minecraft 兼容环境会暴露不同的文本默认模式。
     */
    @Test
    public void shouldExposeDifferentDefaultTextModesForBusinessAndDiagnosticEnvironments() {
        UiDocumentScreens.DocumentScreenEnvironment rawEnvironment = new UiDocumentScreens.DocumentScreenEnvironment(
                new NoOpTextMeasureService(), UiRuntimeAdapters.empty(), TextContentMode.UILIB_RAW);
        UiDocumentScreens.DocumentScreenEnvironment formattedEnvironment = new UiDocumentScreens.DocumentScreenEnvironment(
                new NoOpTextMeasureService(), UiRuntimeAdapters.empty(), TextContentMode.MINECRAFT_FORMATTED);

        Assert.assertEquals(TextContentMode.UILIB_RAW, rawEnvironment.getDefaultTextContentMode());
        Assert.assertEquals(TextContentMode.MINECRAFT_FORMATTED, formattedEnvironment.getDefaultTextContentMode());
    }

    /**
     * 验证通用文档内部 definition 会执行调用方文档构建回调。
     */
    @Test
    public void shouldCreateDocumentControllerFromContentBuilder() {
        NoOpTextMeasureService textMeasureService = new NoOpTextMeasureService();
        UiRuntimeAdapters runtimeAdapters = UiRuntimeAdapters.empty();
        DocumentUiScope documentUiScope = new DocumentUiScope(textMeasureService, runtimeAdapters);
        DirectDocumentPageAuthoringSurface surface = new DirectDocumentPageAuthoringSurface();
        final boolean[] called = new boolean[] { false };

        DocumentPageController controller = InternalHostedScreenFactory.DOCUMENT_SCREEN_DEFINITION.createController(
                documentUiScope, surface, EmptyRuntimeView.INSTANCE,
                InternalHostedScreenFactory.DOCUMENT_SCREEN.getPageId(),
                new UiDocumentScreens.DocumentScreenContentBuilder() {
            @Override
            public void build(UiDocument document) {
                called[0] = true;
                ElementNode root = document.getRootElement();
                root.style()
                        .setWidth(UiStyleLength.percent(1.0F))
                        .setHeight(UiStyleLength.percent(1.0F));
                ElementNode title = document.element("h1");
                title.appendText("业务页面");
                root.append(title);
            }
        });

        controller.configureDocumentPage();
        controller.buildDocument();

        Assert.assertTrue(called[0]);
        Assert.assertEquals(1, surface.getBlocks().size());
        Assert.assertTrue(surface.getBlocks().get(0) instanceof HtmlLikeDocumentWidget);
        Assert.assertTrue(((HtmlLikeDocumentWidget) surface.getBlocks().get(0)).isViewportRootScrollingEnabled());
    }

    /**
     * 验证业务入口会为未声明的根节点补齐全视口与根滚动契约。
     */
    @Test
    public void shouldApplyDefaultRootContractForDocumentScreen() {
        NoOpTextMeasureService textMeasureService = new NoOpTextMeasureService();
        DocumentUiScope documentUiScope = new DocumentUiScope(textMeasureService, UiRuntimeAdapters.empty());
        DirectDocumentPageAuthoringSurface surface = new DirectDocumentPageAuthoringSurface();
        final UiDocument[] builtDocument = new UiDocument[1];

        DocumentPageController controller = InternalHostedScreenFactory.DOCUMENT_SCREEN_DEFINITION.createController(
                documentUiScope, surface, EmptyRuntimeView.INSTANCE,
                InternalHostedScreenFactory.DOCUMENT_SCREEN.getPageId(),
                new UiDocumentScreens.DocumentScreenContentBuilder() {
                    @Override
                    public void build(UiDocument document) {
                        builtDocument[0] = document;
                        ElementNode title = document.element("h1");
                        title.appendText("业务页面");
                        document.getRootElement().append(title);
                    }
                });

        controller.buildDocument();

        ElementNode root = builtDocument[0].getRootElement();
        Assert.assertEquals(UiStyleLength.percent(1.0F), root.style().getWidth());
        Assert.assertEquals(UiStyleLength.percent(1.0F), root.style().getHeight());
        Assert.assertEquals(UiOverflow.AUTO, root.style().getOverflowY());
    }

    /**
     * 验证业务入口不会覆盖调用方已经声明的根节点样式。
     */
    @Test
    public void shouldPreserveExplicitRootContractForDocumentScreen() {
        NoOpTextMeasureService textMeasureService = new NoOpTextMeasureService();
        DocumentUiScope documentUiScope = new DocumentUiScope(textMeasureService, UiRuntimeAdapters.empty());
        DirectDocumentPageAuthoringSurface surface = new DirectDocumentPageAuthoringSurface();
        final UiDocument[] builtDocument = new UiDocument[1];

        DocumentPageController controller = InternalHostedScreenFactory.DOCUMENT_SCREEN_DEFINITION.createController(
                documentUiScope, surface, EmptyRuntimeView.INSTANCE,
                InternalHostedScreenFactory.DOCUMENT_SCREEN.getPageId(),
                new UiDocumentScreens.DocumentScreenContentBuilder() {
                    @Override
                    public void build(UiDocument document) {
                        builtDocument[0] = document;
                        ElementNode root = document.getRootElement();
                        root.style()
                                .setWidth(UiStyleLength.px(640))
                                .setHeight(UiStyleLength.px(360))
                                .setOverflowY(UiOverflow.HIDDEN);
                    }
                });

        controller.buildDocument();

        ElementNode root = builtDocument[0].getRootElement();
        Assert.assertEquals(UiStyleLength.px(640), root.style().getWidth());
        Assert.assertEquals(UiStyleLength.px(360), root.style().getHeight());
        Assert.assertEquals(UiOverflow.HIDDEN, root.style().getOverflowY());
    }

    /**
     * 验证内部诊断 definition 会显式保留页面壳策略解析入口。
     */
    @Test
    public void shouldResolveChromeThroughDefinition() {
        DocumentScreenChrome chrome = InternalDiagnosticScreenRegistry.UI_TEST_DEFINITION.resolveChrome(960, 720);

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
    private static final class FakeDescriptorOwner implements InternalScreenIdentity.DescriptorOwner {

        private final InternalScreenIdentity.PageDescriptor descriptor;

        private FakeDescriptorOwner(InternalScreenIdentity.PageDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public InternalScreenIdentity.PageDescriptor getPageDescriptor() {
            return descriptor;
        }
    }

    /**
     * 供测试使用的空运行时视图。
     */
    private enum EmptyRuntimeView implements DocumentPageRuntimeView {
        INSTANCE;

        @Override
        public int getHostWidth() {
            return 0;
        }

        @Override
        public int getHostHeight() {
            return 0;
        }

        @Override
        public int getMouseX() {
            return 0;
        }

        @Override
        public int getMouseY() {
            return 0;
        }

        @Override
        public club.heiqi.uilib.ui.diagnostic.UiRuntimeStats getUiRuntimeStats() {
            return club.heiqi.uilib.ui.diagnostic.UiRuntimeStats.empty();
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
