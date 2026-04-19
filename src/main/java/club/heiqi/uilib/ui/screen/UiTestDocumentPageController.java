package club.heiqi.uilib.ui.screen;

import java.util.Objects;

import club.heiqi.uilib.ui.control.ButtonWidget;
import club.heiqi.uilib.ui.document.DocumentCardWidget;
import club.heiqi.uilib.ui.document.DocumentSectionWidget;
import club.heiqi.uilib.ui.document.DocumentTextWidget;
import club.heiqi.uilib.ui.document.DocumentToolbarWidget;

/**
 * 诊断首页菜单控制器。
 */
final class UiTestDocumentPageController extends DocumentPageController {

    private final DocumentUiScope documentUi;
    private final DocumentPageAuthoringSurface diagnosticPage;
    private final UiTestMenuModel menuModel;

    private final DocumentCardWidget overviewCard;
    private final DocumentCardWidget layoutEntryCard;
    private final ButtonWidget openLayoutDiagnosticsButton;

    /**
     * 创建诊断首页菜单控制器。
     *
     * @param documentUi 文档组件作用域
     * @param diagnosticPage 文档页面壳
     * @param menuModel 菜单跳转模型
     */
    UiTestDocumentPageController(DocumentUiScope documentUi, DocumentPageAuthoringSurface diagnosticPage,
            UiTestMenuModel menuModel) {
        this.documentUi = Objects.requireNonNull(documentUi, "documentUi");
        this.diagnosticPage = Objects.requireNonNull(diagnosticPage, "diagnosticPage");
        this.menuModel = Objects.requireNonNull(menuModel, "menuModel");

        this.overviewCard = this.documentUi.card();
        this.layoutEntryCard = this.documentUi.card();
        this.openLayoutDiagnosticsButton = this.documentUi.button("进入布局诊断页");
    }

    @Override
    void configureDocumentPage() {
        diagnosticPage.setContentWidthRange(680, 1080)
                .setMinContentHeight(420)
                .setViewportFillRatio(0.92F, 0.90F);
    }

    @Override
    void buildDocument() {
        configureMenuActions();
        assembleDocument();
    }

    private void configureMenuActions() {
        openLayoutDiagnosticsButton.setClickHandler(new Runnable() {
            @Override
            public void run() {
                menuModel.openLayoutDiagnostics();
            }
        });
    }

    private void assembleDocument() {
        DocumentSectionWidget overviewDiv = documentUi.section();
        overviewDiv.addChild(documentUi.text(DocumentTextWidget.Role.TITLE, "诊断首页", 2));
        overviewDiv.addChild(documentUi.text(DocumentTextWidget.Role.BODY,
                "这一层现在专门承担诊断目录职责：顶层 `ui_test` 不再堆放全部探针内容，而是作为稳定入口，继续跳到不同的 definition-backed 诊断子页。", 8));
        overviewCard.addChild(overviewDiv);

        DocumentSectionWidget layoutEntryDiv = documentUi.section();
        layoutEntryDiv.addChild(documentUi.text(DocumentTextWidget.Role.TITLE, "布局诊断子页", 2));
        layoutEntryDiv.addChild(documentUi.text(DocumentTextWidget.Role.BODY,
                "进入后继续查看页面壳尺寸、卡片换行、文本最小宽度、性能统计和高频字符变更探针。后续新的诊断主题也应继续按独立 definition-backed 子页扩展。", 8));
        DocumentToolbarWidget entryToolbar = documentUi.toolbar();
        entryToolbar.addChild(openLayoutDiagnosticsButton);
        layoutEntryDiv.addChild(entryToolbar);
        layoutEntryCard.addChild(layoutEntryDiv);

        diagnosticPage.addBlock(documentUi.text(DocumentTextWidget.Role.TITLE, "诊断菜单页", 2));
        diagnosticPage.addBlock(documentUi.text(DocumentTextWidget.Role.BODY,
                "这一页只负责诊断导航，不再直接承担所有探针内容；这样每个子页都能拥有独立 pageId、controller 和运行时统计语义。",
                8));
        diagnosticPage.addBlock(overviewCard);
        diagnosticPage.addBlock(layoutEntryCard);
    }
}
