package club.heiqi.uilib.ui.screen;

import java.util.Objects;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiStyleLength;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
import club.heiqi.uilib.ui.text.TextMeasureService;
import net.minecraft.client.gui.GuiScreen;

/**
 * 业务文档界面创建边界。
 *
 * <p>该类只保留面向业务作者的稳定入口，不再承载内部托管页面 definition、descriptor
 * 或运行时身份识别机制。</p>
 */
public final class UiDocumentScreens {

    private UiDocumentScreens() {}

    /**
     * 文档页面创建环境。
     *
     * <p>把文本测量与运行时适配器收敛成一个显式入口，
     * 让默认值只停留在最外层调用边界，而不是继续散落在 screen/scope 构造链路里。</p>
     */
    public static final class DocumentScreenEnvironment {

        private final TextMeasureService textMeasureService;
        private final UiRuntimeAdapters runtimeAdapters;

        /**
         * 创建文档页面环境。
         *
         * @param textMeasureService 文本测量服务
         * @param runtimeAdapters 运行时适配器集合
         */
        public DocumentScreenEnvironment(TextMeasureService textMeasureService,
                UiRuntimeAdapters runtimeAdapters) {
            this.textMeasureService = Objects.requireNonNull(textMeasureService, "textMeasureService");
            this.runtimeAdapters = Objects.requireNonNull(runtimeAdapters, "runtimeAdapters");
        }

        /**
         * 创建当前 Minecraft 宿主使用的默认文档环境。
         *
         * <p>默认值仍然存在，但现在被限制在最外层入口，调用方也可以显式替换。</p>
         */
        public static DocumentScreenEnvironment minecraftDefaults() {
            return new DocumentScreenEnvironment(DefaultTextMeasureService.getInstance(),
                    UiRuntimeAdapters.minecraftDefaults());
        }

        /**
         * 返回文本测量服务。
         *
         * @return 文本测量服务
         */
        public TextMeasureService getTextMeasureService() {
            return textMeasureService;
        }

        /**
         * 返回运行时适配器集合。
         *
         * @return 运行时适配器集合
         */
        public UiRuntimeAdapters getRuntimeAdapters() {
            return runtimeAdapters;
        }
    }

    /**
     * HTML-like 文档内容构建器。
     *
     * <p>宿主层负责创建 `UiDocument`、`HtmlLikeDocumentWidget` 和 Minecraft `GuiScreen`，
     * 使用者只需要在回调中组装文档树、样式和事件。</p>
     */
    public interface DocumentScreenContentBuilder {

        /**
         * 构建 HTML-like 文档内容。
         *
         * @param document 待填充的文档
         */
        void build(UiDocument document);
    }

    /**
     * 创建由调用方填充 `UiDocument` 的业务文档界面。
     *
     * <p>该入口用于 Minecraft 宿主层快速打开 HTML-like UI，调用方无需接触内部页面控制器、
     * 页面 definition 或 `HtmlLikeDocumentWidget` 挂载细节。根元素若未显式声明
     * `width`、`height` 或 `overflow-y`，框架会分别兜底为 `100%`、`100%` 与 `auto`。</p>
     *
     * @param contentBuilder 文档内容构建器
     * @return 文档型界面
     */
    public static GuiScreen createDocumentScreen(DocumentScreenContentBuilder contentBuilder) {
        return createDocumentScreen(DocumentScreenEnvironment.minecraftDefaults(), contentBuilder);
    }

    /**
     * 基于显式文档环境创建由调用方填充 `UiDocument` 的业务文档界面。
     *
     * @param environment 文档页面创建环境
     * @param contentBuilder 文档内容构建器
     * @return 文档型界面
     */
    public static GuiScreen createDocumentScreen(DocumentScreenEnvironment environment,
            DocumentScreenContentBuilder contentBuilder) {
        return InternalHostedScreenFactory.createScreen(InternalHostedScreenFactory.DOCUMENT_SCREEN_DEFINITION,
                Objects.requireNonNull(environment, "environment"),
                Objects.requireNonNull(contentBuilder, "contentBuilder"));
    }

    /**
     * 创建调用方内容驱动的文档页面控制器。
     */
    static DocumentPageController createInlineDocumentController(DocumentUiScope documentUi,
            DocumentPageAuthoringSurface documentPage,
            DocumentScreenContentBuilder contentBuilder) {
        return new InlineDocumentPageController(documentUi, documentPage, contentBuilder);
    }

    /**
     * 调用方内容驱动的 HTML-like 文档页面控制器。
     */
    private static final class InlineDocumentPageController extends DocumentPageController {

        private final DocumentPageAuthoringSurface documentPage;
        private final HtmlLikeDocumentWidget htmlLikeDocumentWidget;

        /**
         * 创建调用方内容驱动的文档页面控制器。
         *
         * @param documentUi 文档组件作用域
         * @param documentPage 文档页面挂载面
         * @param contentBuilder 文档内容构建器
         */
        private InlineDocumentPageController(DocumentUiScope documentUi, DocumentPageAuthoringSurface documentPage,
                DocumentScreenContentBuilder contentBuilder) {
            DocumentUiScope resolvedDocumentUi = Objects.requireNonNull(documentUi, "documentUi");
            this.documentPage = Objects.requireNonNull(documentPage, "documentPage");
            UiDocument document = UiDocument.create();
            Objects.requireNonNull(contentBuilder, "contentBuilder").build(document);
            applyDefaultRootContract(document.getRootElement());
            this.htmlLikeDocumentWidget = new HtmlLikeDocumentWidget(document, 320, 180,
                    resolvedDocumentUi.getTextMeasureService());
            this.htmlLikeDocumentWidget.setViewportRootScrollingEnabled(true);
            this.htmlLikeDocumentWidget.setLayoutSpec(new UiLayoutSpec()
                    .setWidth(UiLength.percent(1.0F))
                    .setHeight(UiLength.percent(1.0F)));
        }

        @Override
        void configureDocumentPage() {
            documentPage.setContentWidthRange(1, Integer.MAX_VALUE)
                    .setMinContentHeight(1)
                    .setViewportFillRatio(1.0F, 1.0F);
        }

        @Override
        void buildDocument() {
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
}
