package club.heiqi.uilib.ui.screen.internal;

import java.util.Objects;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.host.DocumentHostWidgetFactory;
import club.heiqi.uilib.ui.screen.UiDocumentScreens;
import club.heiqi.uilib.ui.screen.page.DocumentPageAuthoringSurface;
import club.heiqi.uilib.ui.screen.page.DocumentPageController;
import club.heiqi.uilib.ui.screen.page.DocumentUiScope;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 调用方内容驱动的 HTML-like 文档页面控制器。
 *
 * <p>由 {@link UiDocumentScreens#createDocumentScreen(UiDocumentScreens.DocumentScreenEnvironment,
 * UiDocumentScreens.DocumentScreenContentBuilder)} 通过 {@link InternalHostedScreenFactory#DOCUMENT_SCREEN_DEFINITION}
 * 调用，把业务作者侧的 {@code DocumentScreenContentBuilder} 封装为内部 hosted screen 链路上的
 * {@link DocumentPageController}。</p>
 */
public final class InternalInlineDocumentPageController extends DocumentPageController {

    private final DocumentPageAuthoringSurface documentPage;
    private final HtmlLikeDocumentWidget htmlLikeDocumentWidget;

    /**
     * 创建调用方内容驱动的文档页面控制器。
     *
     * @param documentUi 文档组件作用域
     * @param documentPage 文档页面挂载面
     * @param contentBuilder 文档内容构建器
     */
    public InternalInlineDocumentPageController(DocumentUiScope documentUi,
            DocumentPageAuthoringSurface documentPage,
            UiDocumentScreens.DocumentScreenContentBuilder contentBuilder) {
        DocumentUiScope resolvedDocumentUi = Objects.requireNonNull(documentUi, "documentUi");
        this.documentPage = Objects.requireNonNull(documentPage, "documentPage");
        UiDocument document = UiDocument.create();
        document.setDefaultTextContentMode(resolvedDocumentUi.getDefaultTextContentMode());
        Objects.requireNonNull(contentBuilder, "contentBuilder").build(document);
        applyDefaultRootContract(document.getRootElement());
        this.htmlLikeDocumentWidget = DocumentHostWidgetFactory.createViewportDocumentWidget(document, 320, 180,
                resolvedDocumentUi.getTextMeasureService(), true);
    }

    @Override
    public void configureDocumentPage() {
        documentPage.setContentWidthRange(1, Integer.MAX_VALUE)
                .setMinContentHeight(1)
                .setViewportFillRatio(1.0F, 1.0F);
    }

    @Override
    public void buildDocument() {
        documentPage.addBlock(htmlLikeDocumentWidget);
    }

    /**
     * 为业务入口默认补齐根节点契约，减少首次接入需要记忆的样板。
     */
    private static void applyDefaultRootContract(ElementNode rootElement) {
        if (rootElement == null) {
            return;
        }
        if (rootElement.style().getWidth() == null) {
            rootElement.style().setWidth(UiStyleLength.percent(1.0F));
        }
        if (rootElement.style().getHeight() == null) {
            rootElement.style().setHeight(UiStyleLength.percent(1.0F));
        }
        if (rootElement.style().getOverflowY() == null) {
            rootElement.style().setOverflowY(UiOverflow.AUTO);
        }
    }
}
