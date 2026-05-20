package club.heiqi.uilib.ui.screen.internal;

import java.util.Objects;

import club.heiqi.uilib.ui.diagnostic.UiRuntimeStats;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;
import club.heiqi.uilib.ui.screen.BaseScreen;
import club.heiqi.uilib.ui.screen.UiDocumentScreens;
import club.heiqi.uilib.ui.screen.page.DirectDocumentPageAuthoringSurface;
import club.heiqi.uilib.ui.screen.page.DocumentPageAuthoringSurface;
import club.heiqi.uilib.ui.screen.page.DocumentPageController;
import club.heiqi.uilib.ui.screen.page.DocumentPageRuntimeView;
import club.heiqi.uilib.ui.screen.page.DocumentScreenChrome;
import club.heiqi.uilib.ui.screen.page.DocumentUiScope;
import club.heiqi.uilib.ui.widget.Widget;
import net.minecraft.client.gui.GuiScreen;

/**
 * 内部托管页面工厂。
 *
 * <p>仅供库内诊断页、示例页和宿主托管页面机制使用，不构成对业务作者的稳定 API。
 * 类与必要嵌套类型对外提升为 public，仅供 ui.screen / ui.screen.internal 内的协作类使用。</p>
 */
public final class InternalHostedScreenFactory {

    public static final InternalScreenIdentity.PageDescriptor DOCUMENT_SCREEN =
            new InternalScreenIdentity.PageDescriptor("document_screen");
    public static final InternalHostedScreenDefinition<UiDocumentScreens.DocumentScreenContentBuilder> DOCUMENT_SCREEN_DEFINITION =
            new InternalHostedScreenDefinition<UiDocumentScreens.DocumentScreenContentBuilder>(
                    DOCUMENT_SCREEN,
                    DocumentScreenChrome::fillViewport,
                    new InternalDocumentPageControllerFactory<UiDocumentScreens.DocumentScreenContentBuilder>() {
                        @Override
                        public DocumentPageController create(DocumentUiScope documentUi,
                                DocumentPageAuthoringSurface documentPage,
                                DocumentPageRuntimeView runtimeView,
                                String pageId,
                                UiDocumentScreens.DocumentScreenContentBuilder provision) {
                            return new InternalInlineDocumentPageController(documentUi, documentPage, provision);
                        }
                    });

    private InternalHostedScreenFactory() {}

    /**
     * 基于页面定义创建内部 hosted screen。
     *
     * @param definition 页面定义
     * @param environment 文档页面创建环境
     * @param provision 页面专属 provision
     * @param <P> 页面 provision 类型
     * @return 文档型界面
     */
    public static <P> GuiScreen createScreen(InternalHostedScreenDefinition<P> definition,
            UiDocumentScreens.DocumentScreenEnvironment environment,
            P provision) {
        return new InternalDefinitionBackedHtmlLikeDocumentScreen<P>(environment, definition, provision);
    }

    /**
     * 文档页面控制器工厂。
     *
     * @param <P> 页面控制器所需的专属 provision 类型
     */
    public interface InternalDocumentPageControllerFactory<P> {

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
        DocumentPageController create(DocumentUiScope documentUi,
                DocumentPageAuthoringSurface documentPage,
                DocumentPageRuntimeView runtimeView,
                String pageId,
                P provision);
    }

    /**
     * 文档页面壳策略解析器。
     */
    public interface InternalScreenChromeResolver {

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
     * 内部托管页面定义。
     *
     * @param <P> 页面控制器所需的专属 provision 类型
     */
    public static final class InternalHostedScreenDefinition<P> {

        private final InternalScreenIdentity.PageDescriptor pageDescriptor;
        private final InternalScreenChromeResolver chromeResolver;
        private final InternalDocumentPageControllerFactory<P> controllerFactory;

        public InternalHostedScreenDefinition(InternalScreenIdentity.PageDescriptor pageDescriptor,
                InternalScreenChromeResolver chromeResolver,
                InternalDocumentPageControllerFactory<P> controllerFactory) {
            this.pageDescriptor = Objects.requireNonNull(pageDescriptor, "pageDescriptor");
            this.chromeResolver = Objects.requireNonNull(chromeResolver, "chromeResolver");
            this.controllerFactory = Objects.requireNonNull(controllerFactory, "controllerFactory");
        }

        /**
         * 返回页面稳定描述对象。
         *
         * @return 页面描述对象
         */
        public InternalScreenIdentity.PageDescriptor getPageDescriptor() {
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
        public DocumentPageController createController(DocumentUiScope documentUi,
                DocumentPageAuthoringSurface documentPage,
                DocumentPageRuntimeView runtimeView,
                String pageId,
                P provision) {
            return controllerFactory.create(documentUi, documentPage, runtimeView, pageId, provision);
        }
    }

    /**
     * 由页面定义驱动的 HTML-like 直接宿主界面。
     *
     * <p>当前已迁移页面直接把 `HtmlLikeDocumentWidget` 挂到根视口，不再套旧 retained 页面壳。</p>
     *
     * @param <P> 页面 provision 类型
     */
    private static final class InternalDefinitionBackedHtmlLikeDocumentScreen<P> extends BaseScreen
            implements InternalScreenIdentity.DescriptorOwner {

        private final InternalHostedScreenDefinition<P> definition;
        private final InternalScreenIdentity.PageDescriptor pageDescriptor;
        private final DocumentUiScope documentUiScope;
        private final DirectDocumentPageAuthoringSurface documentPage = new DirectDocumentPageAuthoringSurface();
        private final DocumentPageRuntimeView runtimeView = new DocumentPageRuntimeView() {
            @Override
            public int getHostWidth() {
                return InternalDefinitionBackedHtmlLikeDocumentScreen.this.getLatestHostWidth();
            }

            @Override
            public int getHostHeight() {
                return InternalDefinitionBackedHtmlLikeDocumentScreen.this.getLatestHostHeight();
            }

            @Override
            public int getMouseX() {
                return InternalDefinitionBackedHtmlLikeDocumentScreen.this.getLatestMouseX();
            }

            @Override
            public int getMouseY() {
                return InternalDefinitionBackedHtmlLikeDocumentScreen.this.getLatestMouseY();
            }

            @Override
            public UiRuntimeStats getUiRuntimeStats() {
                return InternalDefinitionBackedHtmlLikeDocumentScreen.this.getUiRuntimeStats();
            }
        };
        private final DocumentPageController controller;

        private InternalDefinitionBackedHtmlLikeDocumentScreen(UiDocumentScreens.DocumentScreenEnvironment environment,
                InternalHostedScreenDefinition<P> definition,
                P provision) {
            UiDocumentScreens.DocumentScreenEnvironment resolvedEnvironment = Objects.requireNonNull(environment,
                    "environment");
            this.definition = Objects.requireNonNull(definition, "definition");
            this.pageDescriptor = this.definition.getPageDescriptor();
            this.documentUiScope = new DocumentUiScope(resolvedEnvironment.getTextMeasureService(),
                    resolvedEnvironment.getRuntimeAdapters(), resolvedEnvironment.getDefaultTextContentMode());
            this.controller = this.definition.createController(documentUiScope, documentPage, runtimeView,
                    pageDescriptor.getPageId(), provision);
        }

        @Override
        public InternalScreenIdentity.PageDescriptor getPageDescriptor() {
            return pageDescriptor;
        }

        @Override
        protected UiRuntimeAdapters getRuntimeAdapters() {
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
