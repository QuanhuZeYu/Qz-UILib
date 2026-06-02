package club.heiqi.uilib.ui.screen.internal;

import club.heiqi.uilib.internal.devtools.pages.UiTestDocumentPageController;
import club.heiqi.uilib.ui.screen.page.DocumentPageAuthoringSurface;
import club.heiqi.uilib.ui.screen.page.DocumentPageController;
import club.heiqi.uilib.ui.screen.page.DocumentPageRuntimeView;
import club.heiqi.uilib.ui.screen.page.DocumentScreenChrome;
import club.heiqi.uilib.ui.screen.page.DocumentUiScope;

/**
 * `/qzuilib test` 内部页面注册表。
 *
 * <p>当前 test 页面集合进入系统性重构期，注册表暂时只保留规划页入口。</p>
 *
 * @apiNote 内部类型，LTS 不承诺其稳定性。业务代码不应直接引用页面 id 或 definition。
 */
public final class InternalDiagnosticScreenRegistry {

    public static final InternalScreenIdentity.PageDescriptor UI_TEST = new InternalScreenIdentity.PageDescriptor(
            "ui_test");

    public static final InternalHostedScreenFactory.InternalHostedScreenDefinition<Void> UI_TEST_DEFINITION = new InternalHostedScreenFactory.InternalHostedScreenDefinition<Void>(
            UI_TEST,
            DocumentScreenChrome::resolve,
            new InternalHostedScreenFactory.InternalDocumentPageControllerFactory<Void>() {
                @Override
                public DocumentPageController create(DocumentUiScope documentUi,
                        DocumentPageAuthoringSurface documentPage,
                        DocumentPageRuntimeView runtimeView,
                        String pageId,
                        Void provision) {
                    return new UiTestDocumentPageController(documentUi, documentPage);
                }
            });

    private InternalDiagnosticScreenRegistry() {}

    /**
     * 返回 test 规划页稳定 id。
     *
     * @return 页面 id
     */
    public static String uiTestPageId() {
        return UI_TEST.getPageId();
    }
}
