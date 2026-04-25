package club.heiqi.uilib.ui.screen;

import java.util.Objects;

import club.heiqi.uilib.ui.control.UiControlRuntimeAdapters;
import club.heiqi.uilib.ui.document.DocumentPageWidget;
import club.heiqi.uilib.ui.theme.UiDocumentTheme;
import club.heiqi.uilib.ui.theme.UiDocumentThemes;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
import club.heiqi.uilib.ui.text.TextMeasureService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * 文档型界面创建边界。
 */
public final class UiDocumentScreens {

    public static final PageDescriptor UI_TEST = new PageDescriptor("ui_test");
    public static final PageDescriptor UI_TEST_LAYOUT = new PageDescriptor("ui_test_layout");
    public static final PageDescriptor HTML_LIKE_SMOKE = new PageDescriptor("html_like_smoke");
    public static final PageDescriptor INVENTORY_OVERVIEW = new PageDescriptor("inventory_overview");
    public static final DocumentScreenDefinition<UiTestMenuModel> UI_TEST_DEFINITION = new DocumentScreenDefinition<UiTestMenuModel>(UI_TEST,
            DocumentScreenChrome::resolve,
            new DocumentPageControllerFactory<UiTestMenuModel>() {
                @Override
                public DocumentPageController create(DocumentUiScope documentUi,
                        DocumentPageAuthoringSurface documentPage,
                        DocumentPageRuntimeView runtimeView, String pageId, UiTestMenuModel provision) {
                    return new UiTestDocumentPageController(documentUi, documentPage, provision);
                }
            });
    public static final DocumentScreenDefinition<Void> UI_TEST_LAYOUT_DEFINITION = new DocumentScreenDefinition<Void>(
            UI_TEST_LAYOUT, DocumentScreenChrome::resolve, new DocumentPageControllerFactory<Void>() {
                @Override
                public DocumentPageController create(DocumentUiScope documentUi,
                        DocumentPageAuthoringSurface documentPage,
                        DocumentPageRuntimeView runtimeView, String pageId, Void provision) {
                    return new UiLayoutDiagnosticsDocumentPageController(documentUi, documentPage, runtimeView, pageId);
                }
            });
    public static final DocumentScreenDefinition<Void> HTML_LIKE_SMOKE_DEFINITION = new DocumentScreenDefinition<Void>(
            HTML_LIKE_SMOKE, DocumentScreenChrome::resolve, new DocumentPageControllerFactory<Void>() {
                @Override
                public DocumentPageController create(DocumentUiScope documentUi,
                        DocumentPageAuthoringSurface documentPage,
                        DocumentPageRuntimeView runtimeView, String pageId, Void provision) {
                    return new HtmlLikeSmokeDocumentPageController(documentUi, documentPage);
                }
            });
    public static final DocumentScreenDefinition<InventoryOverviewModel> INVENTORY_OVERVIEW_DEFINITION = new DocumentScreenDefinition<InventoryOverviewModel>(
            INVENTORY_OVERVIEW, DocumentScreenChrome::resolve, new DocumentPageControllerFactory<InventoryOverviewModel>() {
                @Override
                public DocumentPageController create(DocumentUiScope documentUi,
                        DocumentPageAuthoringSurface documentPage,
                        DocumentPageRuntimeView runtimeView, String pageId, InventoryOverviewModel provision) {
                    return new InventoryOverviewDocumentPageController(documentUi, documentPage, runtimeView,
                            provision);
                }
            });

    private UiDocumentScreens() {}

    /**
     * 文档页面创建环境。
     *
     * <p>把主题、文本测量与运行时适配器收敛成一个显式入口，
     * 让默认值只停留在最外层调用边界，而不是继续散落在 screen/scope 构造链路里。</p>
     */
    public static final class DocumentScreenEnvironment {

        private final UiDocumentTheme documentTheme;
        private final TextMeasureService textMeasureService;
        private final UiControlRuntimeAdapters runtimeAdapters;

        public DocumentScreenEnvironment(UiDocumentTheme documentTheme, TextMeasureService textMeasureService,
                UiControlRuntimeAdapters runtimeAdapters) {
            this.documentTheme = Objects.requireNonNull(documentTheme, "documentTheme");
            this.textMeasureService = Objects.requireNonNull(textMeasureService, "textMeasureService");
            this.runtimeAdapters = Objects.requireNonNull(runtimeAdapters, "runtimeAdapters");
        }

        /**
         * 创建当前 Minecraft 宿主使用的默认文档环境。
         *
         * <p>默认值仍然存在，但现在被限制在最外层入口，调用方也可以显式替换。</p>
         */
        public static DocumentScreenEnvironment minecraftDefaults() {
            return new DocumentScreenEnvironment(UiDocumentThemes.current(), DefaultTextMeasureService.getInstance(),
                    UiControlRuntimeAdapters.minecraftDefaults());
        }

        public UiDocumentTheme getDocumentTheme() {
            return documentTheme;
        }

        public TextMeasureService getTextMeasureService() {
            return textMeasureService;
        }

        public UiControlRuntimeAdapters getRuntimeAdapters() {
            return runtimeAdapters;
        }
    }

    /**
     * 文档型页面定义。
     *
     * @param <P> 页面控制器所需的专属 provision 类型
     */
    public static final class DocumentScreenDefinition<P> {

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
        private DocumentPageController createController(DocumentUiScope documentUi,
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
    private interface DocumentPageControllerFactory<P> {

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
    public interface DocumentScreenChromeResolver {

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
     * 页面描述对象，仅承载稳定页面标识。
     */
    public static final class PageDescriptor {

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
    public interface DescriptorOwner {

        /**
         * 返回当前页面的稳定描述对象。
         *
         * @return 页面描述对象
         */
        PageDescriptor getPageDescriptor();
    }

    /**
     * 创建布局诊断页。
     *
     * @return 测试页界面
     */
    public static GuiScreen createUiTest() {
        return createUiTest(DocumentScreenEnvironment.minecraftDefaults());
    }

    /**
     * 基于显式文档环境创建布局诊断页。
     *
     * @param environment 文档页面创建环境
     * @return 测试页界面
     */
    public static GuiScreen createUiTest(DocumentScreenEnvironment environment) {
        DocumentScreenEnvironment resolvedEnvironment = Objects.requireNonNull(environment, "environment");
        return createDefinitionBackedScreen(UI_TEST_DEFINITION, resolvedEnvironment,
                createDefaultUiTestMenuModel(resolvedEnvironment));
    }

    /**
     * 创建布局诊断子页。
     *
     * @return 布局诊断子页界面
     */
    public static GuiScreen createUiTestLayout() {
        return createUiTestLayout(DocumentScreenEnvironment.minecraftDefaults());
    }

    /**
     * 基于显式文档环境创建布局诊断子页。
     *
     * @param environment 文档页面创建环境
     * @return 布局诊断子页界面
     */
    public static GuiScreen createUiTestLayout(DocumentScreenEnvironment environment) {
        return createDefinitionBackedScreen(UI_TEST_LAYOUT_DEFINITION, Objects.requireNonNull(environment, "environment"),
                null);
    }

    /**
     * 创建 HTML-like smoke 子页。
     *
     * @return HTML-like smoke 子页界面
     */
    public static GuiScreen createHtmlLikeSmoke() {
        return createHtmlLikeSmoke(DocumentScreenEnvironment.minecraftDefaults());
    }

    /**
     * 基于显式文档环境创建 HTML-like smoke 子页。
     *
     * @param environment 文档页面创建环境
     * @return HTML-like smoke 子页界面
     */
    public static GuiScreen createHtmlLikeSmoke(DocumentScreenEnvironment environment) {
        return createDefinitionBackedScreen(HTML_LIKE_SMOKE_DEFINITION,
                Objects.requireNonNull(environment, "environment"), null);
    }

    /**
     * 判断界面是否为布局诊断页。
     *
     * @param screen 待判断界面
     * @return 是否为布局诊断页
     */
    public static boolean isUiTest(GuiScreen screen) {
        return isUiTest((Object) screen);
    }

    /**
     * 判断对象是否声明了布局诊断页标识。
     *
     * <p>该辅助入口仅供包内逻辑与测试使用，避免 descriptor 相关判定继续被 `GuiScreen` 运行时绑住。</p>
     *
     * @param screen 待判断对象
     * @return 是否为布局诊断页
     */
    static boolean isUiTest(Object screen) {
        return hasPageId(screen, UI_TEST.getPageId());
    }

    /**
     * 判断对象是否声明了布局诊断子页标识。
     *
     * @param screen 待判断对象
     * @return 是否为布局诊断子页
     */
    static boolean isUiTestLayout(Object screen) {
        return hasPageId(screen, UI_TEST_LAYOUT.getPageId());
    }

    /**
     * 判断对象是否声明了 HTML-like smoke 子页标识。
     *
     * @param screen 待判断对象
     * @return 是否为 HTML-like smoke 子页
     */
    static boolean isHtmlLikeSmoke(Object screen) {
        return hasPageId(screen, HTML_LIKE_SMOKE.getPageId());
    }

    /**
     * 创建背包诊断页。
     *
     * @param model 背包诊断模型
     * @return 背包诊断页界面
     */
    public static GuiScreen createInventoryOverview(InventoryOverviewModel model) {
        return createInventoryOverview(DocumentScreenEnvironment.minecraftDefaults(), model);
    }

    /**
     * 基于显式文档环境创建背包诊断页。
     *
     * @param environment 文档页面创建环境
     * @param model 背包诊断模型
     * @return 背包诊断页界面
     */
    public static GuiScreen createInventoryOverview(DocumentScreenEnvironment environment, InventoryOverviewModel model) {
        return createDefinitionBackedScreen(INVENTORY_OVERVIEW_DEFINITION, Objects.requireNonNull(environment, "environment"),
                Objects.requireNonNull(model, "model"));
    }

    /**
     * 基于页面定义创建内部 hosted screen。
     *
     * @param definition 页面定义
     * @param documentTheme 当前主题
     * @param provision 页面专属 provision
     * @param <P> 页面 provision 类型
     * @return 文档型界面
     */
    private static <P> GuiScreen createDefinitionBackedScreen(DocumentScreenDefinition<P> definition,
            DocumentScreenEnvironment environment, P provision) {
        return new DefinitionBackedDocumentScreen<P>(environment, definition, provision);
    }

    /**
     * 创建默认诊断菜单跳转模型。
     *
     * @param environment 当前文档环境
     * @return 菜单跳转模型
     */
    private static UiTestMenuModel createDefaultUiTestMenuModel(final DocumentScreenEnvironment environment) {
        return new UiTestMenuModel() {
            @Override
            public void openLayoutDiagnostics() {
                Minecraft.getMinecraft().displayGuiScreen(createUiTestLayout(environment));
            }

            @Override
            public void openHtmlLikeSmoke() {
                Minecraft.getMinecraft().displayGuiScreen(createHtmlLikeSmoke(environment));
            }
        };
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
     * 由页面定义驱动的内部文档宿主界面。
     *
     * @param <P> 页面 provision 类型
     */
    private static final class DefinitionBackedDocumentScreen<P> extends ControllerBackedDocumentScreen {

        private final DocumentScreenDefinition<P> definition;

        private DefinitionBackedDocumentScreen(DocumentScreenEnvironment environment,
                DocumentScreenDefinition<P> definition,
                P provision) {
            super(Objects.requireNonNull(environment, "environment"),
                    Objects.requireNonNull(definition, "definition").getPageDescriptor());
            this.definition = definition;
            bindController(definition.createController(ui(), DocumentPageAuthoringSurface.adapt(getDocumentPage()),
                    runtimeView(), pageId(), provision));
        }

        @Override
        protected DocumentScreenChrome resolveDocumentChrome(int width, int height) {
            return definition.resolveChrome(width, height);
        }
    }
}
