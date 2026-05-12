package club.heiqi.uilib.ui.screen;

/**
 * 内建诊断页与示例页的内部注册表。
 */
final class InternalDiagnosticScreenRegistry {

    static final InternalScreenIdentity.PageDescriptor UI_TEST = new InternalScreenIdentity.PageDescriptor("ui_test");
    static final InternalScreenIdentity.PageDescriptor UI_TEST_LAYOUT = new InternalScreenIdentity.PageDescriptor(
            "ui_test_layout");
    static final InternalScreenIdentity.PageDescriptor HTML_LIKE_SMOKE = new InternalScreenIdentity.PageDescriptor(
            "html_like_smoke");
    static final InternalScreenIdentity.PageDescriptor HTML_LIKE_GLASS = new InternalScreenIdentity.PageDescriptor(
            "html_like_glass");
    static final InternalScreenIdentity.PageDescriptor INVENTORY_OVERVIEW = new InternalScreenIdentity.PageDescriptor(
            "inventory_overview");

    static final InternalHostedScreenFactory.InternalHostedScreenDefinition<UiTestMenuModel> UI_TEST_DEFINITION = new InternalHostedScreenFactory.InternalHostedScreenDefinition<UiTestMenuModel>(
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
    static final InternalHostedScreenFactory.InternalHostedScreenDefinition<Void> UI_TEST_LAYOUT_DEFINITION = new InternalHostedScreenFactory.InternalHostedScreenDefinition<Void>(
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
    static final InternalHostedScreenFactory.InternalHostedScreenDefinition<Void> HTML_LIKE_SMOKE_DEFINITION = new InternalHostedScreenFactory.InternalHostedScreenDefinition<Void>(
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
    static final InternalHostedScreenFactory.InternalHostedScreenDefinition<Void> HTML_LIKE_GLASS_DEFINITION = new InternalHostedScreenFactory.InternalHostedScreenDefinition<Void>(
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
    static final InternalHostedScreenFactory.InternalHostedScreenDefinition<InventoryOverviewModel> INVENTORY_OVERVIEW_DEFINITION = new InternalHostedScreenFactory.InternalHostedScreenDefinition<InventoryOverviewModel>(
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

    private InternalDiagnosticScreenRegistry() {}

    static String uiTestPageId() {
        return UI_TEST.getPageId();
    }

    static String uiTestLayoutPageId() {
        return UI_TEST_LAYOUT.getPageId();
    }

    static String htmlLikeSmokePageId() {
        return HTML_LIKE_SMOKE.getPageId();
    }

    static String htmlLikeGlassPageId() {
        return HTML_LIKE_GLASS.getPageId();
    }

    static String inventoryOverviewPageId() {
        return INVENTORY_OVERVIEW.getPageId();
    }
}
