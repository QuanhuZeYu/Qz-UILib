package club.heiqi.uilib.ui.screen.internal;

import club.heiqi.uilib.ui.screen.example.HtmlLikeBrowserSemanticsShowcaseDocumentPageController;
import club.heiqi.uilib.ui.screen.example.HtmlLikeGlassDocumentPageController;
import club.heiqi.uilib.ui.screen.example.HtmlLikeInventoryOverviewDocumentPageController;
import club.heiqi.uilib.ui.screen.example.HtmlLikeListDragDocumentPageController;
import club.heiqi.uilib.ui.screen.example.HtmlLikeSmokeDocumentPageController;
import club.heiqi.uilib.ui.screen.example.InventoryOverviewModel;
import club.heiqi.uilib.ui.screen.example.UiFontPerformanceBaselineDocumentPageController;
import club.heiqi.uilib.ui.screen.example.UiLayoutDiagnosticsDocumentPageController;
import club.heiqi.uilib.ui.screen.example.UiTestDocumentPageController;
import club.heiqi.uilib.ui.screen.example.UiTestMenuModel;
import club.heiqi.uilib.ui.screen.page.DocumentPageAuthoringSurface;
import club.heiqi.uilib.ui.screen.page.DocumentPageController;
import club.heiqi.uilib.ui.screen.page.DocumentPageRuntimeView;
import club.heiqi.uilib.ui.screen.page.DocumentScreenChrome;
import club.heiqi.uilib.ui.screen.page.DocumentUiScope;

/**
 * 内建诊断页与示例页的内部注册表。
 *
 * <p>类与 page 描述/定义/标识查询入口对外提升为 public，仅供 ui.screen / ui.screen.internal
 * 内的协作类与诊断工具跨包使用，不构成对业务作者的稳定 API。</p>
 */
public final class InternalDiagnosticScreenRegistry {

    public static final InternalScreenIdentity.PageDescriptor UI_TEST = new InternalScreenIdentity.PageDescriptor("ui_test");
    public static final InternalScreenIdentity.PageDescriptor UI_TEST_LAYOUT = new InternalScreenIdentity.PageDescriptor(
            "ui_test_layout");
    public static final InternalScreenIdentity.PageDescriptor FONT_PERFORMANCE_BASELINE = new InternalScreenIdentity.PageDescriptor(
            "font_performance_baseline");
    public static final InternalScreenIdentity.PageDescriptor HTML_LIKE_SMOKE = new InternalScreenIdentity.PageDescriptor(
            "html_like_smoke");
    public static final InternalScreenIdentity.PageDescriptor HTML_LIKE_GLASS = new InternalScreenIdentity.PageDescriptor(
            "html_like_glass");
    public static final InternalScreenIdentity.PageDescriptor INVENTORY_OVERVIEW = new InternalScreenIdentity.PageDescriptor(
            "inventory_overview");
    public static final InternalScreenIdentity.PageDescriptor LIST_ELEMENT_DRAG = new InternalScreenIdentity.PageDescriptor(
            "list_element_drag");
    public static final InternalScreenIdentity.PageDescriptor BROWSER_SEMANTICS_SHOWCASE = new InternalScreenIdentity.PageDescriptor(
            "browser_semantics_showcase");

    public static final InternalHostedScreenFactory.InternalHostedScreenDefinition<UiTestMenuModel> UI_TEST_DEFINITION = new InternalHostedScreenFactory.InternalHostedScreenDefinition<UiTestMenuModel>(
            UI_TEST,
            DocumentScreenChrome::resolve,
            new InternalHostedScreenFactory.InternalDocumentPageControllerFactory<UiTestMenuModel>() {
                @Override
                public DocumentPageController create(DocumentUiScope documentUi,
                        DocumentPageAuthoringSurface documentPage,
                        DocumentPageRuntimeView runtimeView,
                        String pageId,
                        UiTestMenuModel provision) {
                    return new UiTestDocumentPageController(documentUi, documentPage, provision);
                }
            });
    public static final InternalHostedScreenFactory.InternalHostedScreenDefinition<Void> UI_TEST_LAYOUT_DEFINITION = new InternalHostedScreenFactory.InternalHostedScreenDefinition<Void>(
            UI_TEST_LAYOUT,
            DocumentScreenChrome::resolve,
            new InternalHostedScreenFactory.InternalDocumentPageControllerFactory<Void>() {
                @Override
                public DocumentPageController create(DocumentUiScope documentUi,
                        DocumentPageAuthoringSurface documentPage,
                        DocumentPageRuntimeView runtimeView,
                        String pageId,
                        Void provision) {
                    return new UiLayoutDiagnosticsDocumentPageController(documentUi, documentPage, runtimeView, pageId);
                }
            });
    public static final InternalHostedScreenFactory.InternalHostedScreenDefinition<Void> FONT_PERFORMANCE_BASELINE_DEFINITION = new InternalHostedScreenFactory.InternalHostedScreenDefinition<Void>(
            FONT_PERFORMANCE_BASELINE,
            DocumentScreenChrome::resolve,
            new InternalHostedScreenFactory.InternalDocumentPageControllerFactory<Void>() {
                @Override
                public DocumentPageController create(DocumentUiScope documentUi,
                        DocumentPageAuthoringSurface documentPage,
                        DocumentPageRuntimeView runtimeView,
                        String pageId,
                        Void provision) {
                    return new UiFontPerformanceBaselineDocumentPageController(documentUi, documentPage, runtimeView,
                            pageId);
                }
            });
    public static final InternalHostedScreenFactory.InternalHostedScreenDefinition<Void> HTML_LIKE_SMOKE_DEFINITION = new InternalHostedScreenFactory.InternalHostedScreenDefinition<Void>(
            HTML_LIKE_SMOKE,
            DocumentScreenChrome::resolve,
            new InternalHostedScreenFactory.InternalDocumentPageControllerFactory<Void>() {
                @Override
                public DocumentPageController create(DocumentUiScope documentUi,
                        DocumentPageAuthoringSurface documentPage,
                        DocumentPageRuntimeView runtimeView,
                        String pageId,
                        Void provision) {
                    return new HtmlLikeSmokeDocumentPageController(documentUi, documentPage);
                }
            });
    public static final InternalHostedScreenFactory.InternalHostedScreenDefinition<Void> HTML_LIKE_GLASS_DEFINITION = new InternalHostedScreenFactory.InternalHostedScreenDefinition<Void>(
            HTML_LIKE_GLASS,
            DocumentScreenChrome::resolve,
            new InternalHostedScreenFactory.InternalDocumentPageControllerFactory<Void>() {
                @Override
                public DocumentPageController create(DocumentUiScope documentUi,
                        DocumentPageAuthoringSurface documentPage,
                        DocumentPageRuntimeView runtimeView,
                        String pageId,
                        Void provision) {
                    return new HtmlLikeGlassDocumentPageController(documentUi, documentPage);
                }
            });
    public static final InternalHostedScreenFactory.InternalHostedScreenDefinition<InventoryOverviewModel> INVENTORY_OVERVIEW_DEFINITION = new InternalHostedScreenFactory.InternalHostedScreenDefinition<InventoryOverviewModel>(
            INVENTORY_OVERVIEW,
            DocumentScreenChrome::resolve,
            new InternalHostedScreenFactory.InternalDocumentPageControllerFactory<InventoryOverviewModel>() {
                @Override
                public DocumentPageController create(DocumentUiScope documentUi,
                        DocumentPageAuthoringSurface documentPage,
                        DocumentPageRuntimeView runtimeView,
                        String pageId,
                        InventoryOverviewModel provision) {
                    return new HtmlLikeInventoryOverviewDocumentPageController(documentUi, documentPage, runtimeView,
                            provision);
                }
            });
    public static final InternalHostedScreenFactory.InternalHostedScreenDefinition<Void> LIST_ELEMENT_DRAG_DEFINITION = new InternalHostedScreenFactory.InternalHostedScreenDefinition<Void>(
            LIST_ELEMENT_DRAG,
            DocumentScreenChrome::resolve,
            new InternalHostedScreenFactory.InternalDocumentPageControllerFactory<Void>() {
                @Override
                public DocumentPageController create(DocumentUiScope documentUi,
                        DocumentPageAuthoringSurface documentPage,
                        DocumentPageRuntimeView runtimeView,
                        String pageId,
                        Void provision) {
                    return new HtmlLikeListDragDocumentPageController(documentUi, documentPage);
                }
            });
    public static final InternalHostedScreenFactory.InternalHostedScreenDefinition<Void> BROWSER_SEMANTICS_SHOWCASE_DEFINITION = new InternalHostedScreenFactory.InternalHostedScreenDefinition<Void>(
            BROWSER_SEMANTICS_SHOWCASE,
            DocumentScreenChrome::resolve,
            new InternalHostedScreenFactory.InternalDocumentPageControllerFactory<Void>() {
                @Override
                public DocumentPageController create(DocumentUiScope documentUi,
                        DocumentPageAuthoringSurface documentPage,
                        DocumentPageRuntimeView runtimeView,
                        String pageId,
                        Void provision) {
                    return new HtmlLikeBrowserSemanticsShowcaseDocumentPageController(documentUi, documentPage);
                }
            });

    private InternalDiagnosticScreenRegistry() {}

    public static String uiTestPageId() {
        return UI_TEST.getPageId();
    }

    public static String uiTestLayoutPageId() {
        return UI_TEST_LAYOUT.getPageId();
    }

    public static String fontPerformanceBaselinePageId() {
        return FONT_PERFORMANCE_BASELINE.getPageId();
    }

    public static String htmlLikeSmokePageId() {
        return HTML_LIKE_SMOKE.getPageId();
    }

    public static String htmlLikeGlassPageId() {
        return HTML_LIKE_GLASS.getPageId();
    }

    public static String inventoryOverviewPageId() {
        return INVENTORY_OVERVIEW.getPageId();
    }

    public static String listElementDragPageId() {
        return LIST_ELEMENT_DRAG.getPageId();
    }

    public static String browserSemanticsShowcasePageId() {
        return BROWSER_SEMANTICS_SHOWCASE.getPageId();
    }
}
