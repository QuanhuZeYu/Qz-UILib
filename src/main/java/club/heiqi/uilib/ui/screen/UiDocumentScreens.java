package club.heiqi.uilib.ui.screen;

import java.util.Objects;

import club.heiqi.uilib.ui.diagnostic.UiRuntimeStats;
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
import club.heiqi.uilib.ui.widget.Widget;
import net.minecraft.client.gui.GuiScreen;

/**
 * 业务文档界面创建边界。
 */
public final class UiDocumentScreens {

    static final PageDescriptor DOCUMENT_SCREEN = new PageDescriptor("document_screen");
    static final DocumentScreenDefinition<DocumentScreenContentBuilder> DOCUMENT_SCREEN_DEFINITION = new DocumentScreenDefinition<DocumentScreenContentBuilder>(
            DOCUMENT_SCREEN, DocumentScreenChrome::fillViewport,
            new DocumentPageControllerFactory<DocumentScreenContentBuilder>() {
                @Override
                public DocumentPageController create(DocumentUiScope documentUi,
                        DocumentPageAuthoringSurface documentPage,
                        DocumentPageRuntimeView runtimeView, String pageId, DocumentScreenContentBuilder provision) {
                    return new InlineDocumentPageController(documentUi, documentPage, provision);
                }
            });

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
     * 文档型页面定义。
     *
     * @param <P> 页面控制器所需的专属 provision 类型
     */
    static final class DocumentScreenDefinition<P> {

        private final PageDescriptor pageDescriptor;
        private final DocumentScreenChromeResolver chromeResolver;
        private final DocumentPageControllerFactory<P> controllerFactory;

        public DocumentScreenDefinition(PageDescriptor pageDescriptor,
                DocumentScreenChromeResolver chromeResolver,
                DocumentPageControllerFactory<P> controllerFactory) {
            this.pageDescriptor = Objects.requireNonNull(pageDescriptor, "pageDescriptor");
            this.chromeResolver = Objects.requireNonNull(chromeResolver, "chromeResolver");
            this.controllerFactory = Objects.requireNonNull(controllerFactory, "controllerFactory");
        }

        /**
         * 返回页面稳定描述对象。
         *
         * @return 页面描述对象
         */
        public PageDescriptor getPageDescriptor() {
            return pageDescriptor;
        }

        /**
         * 基于当前宿主尺寸解析文档页面壳策略。
         *
         * @param width 当前宿主宽度
         * @param height 当前宿主高度
         * @return 页面壳策略
         */
        public DocumentScreenChrome resolveChrome(int width, int height) {
            return chromeResolver.resolve(width, height);
        }

        /**
         * 基于定义创建页面控制器。
         *
         * @param documentUi 文档组件作用域
         * @param documentPage 文档页面壳 authoring contract
         * @param runtimeView 宿主运行时视图
         * @param pageId 稳定页面标识
         * @param provision 页面专属 provision
         * @return 页面控制器
         */
        DocumentPageController createController(DocumentUiScope documentUi,
                DocumentPageAuthoringSurface documentPage,
                DocumentPageRuntimeView runtimeView, String pageId, P provision) {
            return controllerFactory.create(documentUi, documentPage, runtimeView, pageId, provision);
        }
    }

    /**
     * 文档页面控制器工厂。
     *
     * @param <P> 页面控制器所需的专属 provision 类型
     */
    interface DocumentPageControllerFactory<P> {

        /**
         * 创建页面控制器。
         *
         * @param documentUi 文档组件作用域
         * @param documentPage 文档页面壳 authoring contract
         * @param runtimeView 宿主运行时视图
         * @param pageId 稳定页面标识
         * @param provision 页面专属 provision
         * @return 页面控制器
         */
        DocumentPageController create(DocumentUiScope documentUi, DocumentPageAuthoringSurface documentPage,
                DocumentPageRuntimeView runtimeView, String pageId, P provision);
    }

    /**
     * 文档页面壳策略解析器。
     */
    interface DocumentScreenChromeResolver {

        /**
         * 基于当前宿主尺寸解析页面壳策略。
         *
         * @param width 当前宿主宽度
         * @param height 当前宿主高度
         * @return 页面壳策略
         */
        DocumentScreenChrome resolve(int width, int height);
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
     * 页面描述对象，仅承载稳定页面标识。
     */
    static final class PageDescriptor {

        private final String pageId;

        public PageDescriptor(String pageId) {
            this.pageId = Objects.requireNonNull(pageId, "pageId");
        }

        /**
         * 返回稳定页面标识。
         *
         * @return 页面标识
         */
        public String getPageId() {
            return pageId;
        }
    }

    /**
     * 描述对象持有者。
     */
    interface DescriptorOwner {

        /**
         * 返回当前页面的稳定描述对象。
         *
         * @return 页面描述对象
         */
        PageDescriptor getPageDescriptor();
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
        return createDefinitionBackedScreen(DOCUMENT_SCREEN_DEFINITION,
                Objects.requireNonNull(environment, "environment"), Objects.requireNonNull(contentBuilder, "contentBuilder"));
    }

    /**
     * 基于页面定义创建内部 hosted screen。
     *
     * @param definition 页面定义
     * @param environment 文档页面创建环境
     * @param provision 页面专属 provision
     * @param <P> 页面 provision 类型
     * @return 文档型界面
     */
    static <P> GuiScreen createDefinitionBackedScreen(DocumentScreenDefinition<P> definition,
            DocumentScreenEnvironment environment, P provision) {
        return new DefinitionBackedHtmlLikeDocumentScreen<P>(environment, definition, provision);
    }

    /**
     * 判断界面是否声明了目标页面标识。
     *
     * @param screen 待判断界面
     * @param expectedPageId 目标页面标识
     * @return 是否匹配
     */
    public static boolean hasPageId(GuiScreen screen, String expectedPageId) {
        return hasPageId((Object) screen, expectedPageId);
    }

    /**
     * 判断对象是否声明了目标页面标识。
     *
     * <p>该辅助入口仅供包内逻辑与测试使用，避免 descriptor 相关判定继续依赖 `GuiScreen`。</p>
     *
     * @param screen 待判断对象
     * @param expectedPageId 目标页面标识
     * @return 是否匹配
     */
    static boolean hasPageId(Object screen, String expectedPageId) {
        return expectedPageId.equals(getPageId(screen));
    }

    /**
     * 读取界面的运行时身份标识。
     *
     * <p>优先使用稳定页面标识；若界面尚未接入 descriptor seam，则回退到具体类名，
     * 避免宿主统计在过渡期出现空标识。</p>
     *
     * @param screen 目标界面
     * @return 运行时身份标识
     */
    public static String runtimeScreenNameOf(GuiScreen screen) {
        return runtimeScreenNameOf((Object) screen);
    }

    /**
     * 读取对象的运行时身份标识。
     *
     * <p>该辅助入口仅供包内逻辑与测试使用，优先返回 descriptor pageId，
     * 否则再回退到具体类名。</p>
     *
     * @param screen 目标对象
     * @return 运行时身份标识
     */
    static String runtimeScreenNameOf(Object screen) {
        String pageId = getPageId(screen);
        if (!pageId.isEmpty()) {
            return pageId;
        }
        return screen == null ? "" : screen.getClass().getSimpleName();
    }

    /**
     * 读取界面的稳定页面标识。
     *
     * @param screen 目标界面
     * @return 页面标识，不存在时返回空字符串
     */
    public static String getPageId(GuiScreen screen) {
        return getPageId((Object) screen);
    }

    /**
     * 读取对象的稳定页面标识。
     *
     * <p>该辅助入口仅供包内逻辑与测试使用，避免纯 descriptor 判定继续依赖 `GuiScreen` 运行时。</p>
     *
     * @param screen 目标对象
     * @return 页面标识，不存在时返回空字符串
     */
    static String getPageId(Object screen) {
        if (!(screen instanceof DescriptorOwner)) {
            return "";
        }
        PageDescriptor descriptor = ((DescriptorOwner) screen).getPageDescriptor();
        return descriptor == null ? "" : descriptor.getPageId();
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

    /**
     * 由页面定义驱动的 HTML-like 直接宿主界面。
     *
     * <p>当前已迁移页面直接把 `HtmlLikeDocumentWidget` 挂到根视口，不再套旧 retained 页面壳。</p>
     *
     * @param <P> 页面 provision 类型
     */
    private static final class DefinitionBackedHtmlLikeDocumentScreen<P> extends BaseScreen implements DescriptorOwner {

        private final DocumentScreenDefinition<P> definition;
        private final PageDescriptor pageDescriptor;
        private final DocumentUiScope documentUiScope;
        private final DirectDocumentPageAuthoringSurface documentPage = new DirectDocumentPageAuthoringSurface();
        private final DocumentPageRuntimeView runtimeView = new DocumentPageRuntimeView() {
            @Override
            public int getHostWidth() {
                return DefinitionBackedHtmlLikeDocumentScreen.this.getLatestHostWidth();
            }

            @Override
            public int getHostHeight() {
                return DefinitionBackedHtmlLikeDocumentScreen.this.getLatestHostHeight();
            }

            @Override
            public int getMouseX() {
                return DefinitionBackedHtmlLikeDocumentScreen.this.getLatestMouseX();
            }

            @Override
            public int getMouseY() {
                return DefinitionBackedHtmlLikeDocumentScreen.this.getLatestMouseY();
            }

            @Override
            public UiRuntimeStats getUiRuntimeStats() {
                return DefinitionBackedHtmlLikeDocumentScreen.this.getUiRuntimeStats();
            }
        };
        private final DocumentPageController controller;

        private DefinitionBackedHtmlLikeDocumentScreen(DocumentScreenEnvironment environment,
                DocumentScreenDefinition<P> definition,
                P provision) {
            DocumentScreenEnvironment resolvedEnvironment = Objects.requireNonNull(environment, "environment");
            this.definition = Objects.requireNonNull(definition, "definition");
            this.pageDescriptor = this.definition.getPageDescriptor();
            this.documentUiScope = new DocumentUiScope(resolvedEnvironment.getTextMeasureService(),
                    resolvedEnvironment.getRuntimeAdapters());
            this.controller = this.definition.createController(documentUiScope, documentPage, runtimeView,
                    pageDescriptor.getPageId(), provision);
        }

        @Override
        public PageDescriptor getPageDescriptor() {
            return pageDescriptor;
        }

        @Override
        protected club.heiqi.uilib.ui.runtime.UiRuntimeAdapters getRuntimeAdapters() {
            return documentUiScope.getRuntimeAdapters();
        }

        @Override
        protected void buildUi(Widget root) {
            documentPage.attachRoot(root);
            controller.configureDocumentPage();
            controller.buildDocument();
            controller.afterDocumentBuilt();
        }

        @Override
        protected void onResize(int width, int height) {
            super.onResize(width, height);
            setRootPadding(0, 0, 0, 0);
            documentPage.applyFrameBounds(width, height, definition.resolveChrome(width, height));
            controller.onDocumentResized();
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            controller.beforeDocumentFrame();
            super.drawScreen(mouseX, mouseY, partialTicks);
        }
    }
}
