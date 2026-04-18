package club.heiqi.uilib.ui.screen;

import java.util.Objects;

import club.heiqi.uilib.ui.diagnostic.UiRuntimeStats;
import club.heiqi.uilib.ui.document.DocumentPageWidget;

/**
 * 控制器驱动的文档页面宿主桥接层。
 *
 * <p>该基类只负责收敛 hosted document screen 的重复宿主样板：
 * descriptor 持有、运行时视图暴露、controller 绑定与生命周期转发。
 * 具体页面仍由子类在 `super(...)` 之后显式绑定控制器，避免在基类构造阶段提早触发生命周期依赖。</p>
 */
abstract class ControllerBackedDocumentScreen extends BaseDocumentScreen implements UiDocumentScreens.DescriptorOwner {

    private final UiDocumentScreens.PageDescriptor pageDescriptor;
    private final DocumentPageAuthoringSurface documentPageAuthoringSurface;
    private final DocumentPageRuntimeView runtimeView = new DocumentPageRuntimeView() {
        @Override
        public int getHostWidth() {
            return width;
        }

        @Override
        public int getHostHeight() {
            return height;
        }

        @Override
        public UiRuntimeStats getUiRuntimeStats() {
            return ControllerBackedDocumentScreen.this.getUiRuntimeStats();
        }
    };
    private DocumentPageController controller;

    protected ControllerBackedDocumentScreen(UiDocumentScreens.DocumentScreenEnvironment environment,
            UiDocumentScreens.PageDescriptor pageDescriptor) {
        super(environment);
        this.pageDescriptor = Objects.requireNonNull(pageDescriptor, "pageDescriptor");
        this.documentPageAuthoringSurface = DocumentPageAuthoringSurface.adapt(getDocumentPage());
    }

    /**
     * 返回当前页面描述对象。
     *
     * @return 页面描述对象
     */
    @Override
    public final UiDocumentScreens.PageDescriptor getPageDescriptor() {
        return pageDescriptor;
    }

    /**
     * 返回宿主运行时视图。
     *
     * @return 宿主运行时视图
     */
    protected final DocumentPageRuntimeView runtimeView() {
        return runtimeView;
    }

    /**
     * 返回页面壳 authoring contract。
     *
     * @return 页面壳 authoring contract
     */
    protected final DocumentPageAuthoringSurface documentPageAuthoringSurface() {
        return documentPageAuthoringSurface;
    }

    /**
     * 返回稳定页面标识。
     *
     * @return 页面标识
     */
    protected final String pageId() {
        return pageDescriptor.getPageId();
    }

    /**
     * 绑定页面控制器。
     *
     * @param controller 页面控制器
     */
    protected final void bindController(DocumentPageController controller) {
        this.controller = Objects.requireNonNull(controller, "controller");
    }

    @Override
    protected final void configureDocumentPage(DocumentPageWidget page) {
        requireController().configureDocumentPage();
    }

    @Override
    protected final void buildDocument(DocumentPageWidget page) {
        requireController().buildDocument();
    }

    @Override
    protected final void afterDocumentBuilt() {
        requireController().afterDocumentBuilt();
    }

    @Override
    protected final void onDocumentResized(int width, int height) {
        requireController().onDocumentResized();
    }

    @Override
    protected final void beforeDocumentFrame() {
        requireController().beforeDocumentFrame();
    }

    /**
     * 确保在宿主生命周期进入前已经完成控制器绑定。
     *
     * @return 已绑定的控制器
     */
    private DocumentPageController requireController() {
        if (controller == null) {
            throw new IllegalStateException("DocumentPageController must be bound after super(...) and before screen lifecycle");
        }
        return controller;
    }
}
