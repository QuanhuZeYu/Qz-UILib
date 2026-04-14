package club.heiqi.uilib.ui.screen;

import java.util.Objects;

import club.heiqi.uilib.ui.control.UiControlRuntimeAdapters;
import club.heiqi.uilib.ui.document.DocumentPageWidget;
import club.heiqi.uilib.ui.theme.UiDocumentTheme;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 单文档页面宿主。
 *
 * <p>负责将文档页面挂载到根视口，并统一处理根留白、文档壳留白与每帧页面刷新入口，
 * 避免具体页面继续直接感知 raw root、resize 细节与 `GuiScreen#drawScreen(...)` 宿主生命周期。</p>
 */
public abstract class BaseDocumentScreen extends BaseScreen {

    private final UiDocumentTheme documentTheme;
    private final TextMeasureService textMeasureService;
    private final UiControlRuntimeAdapters runtimeAdapters;
    private final DocumentUiScope documentUiScope;
    private final DocumentPageWidget documentPage;

    protected BaseDocumentScreen(UiDocumentTheme documentTheme) {
        this(documentTheme, DefaultTextMeasureService.getInstance(), UiControlRuntimeAdapters.minecraftDefaults());
    }

    protected BaseDocumentScreen(UiDocumentTheme documentTheme, TextMeasureService textMeasureService) {
        this(documentTheme, textMeasureService, UiControlRuntimeAdapters.minecraftDefaults());
    }

    protected BaseDocumentScreen(UiDocumentTheme documentTheme, TextMeasureService textMeasureService,
            UiControlRuntimeAdapters runtimeAdapters) {
        this.documentTheme = Objects.requireNonNull(documentTheme, "documentTheme");
        this.textMeasureService = Objects.requireNonNull(textMeasureService, "textMeasureService");
        this.runtimeAdapters = Objects.requireNonNull(runtimeAdapters, "runtimeAdapters");
        this.documentUiScope = new DocumentUiScope(this.documentTheme, this.textMeasureService, this.runtimeAdapters);
        this.documentPage = new DocumentPageWidget(this.documentTheme, this.textMeasureService);
    }

    protected final UiDocumentTheme getDocumentTheme() {
        return documentTheme;
    }

    /**
     * 获取文档作者作用域。
     *
     * @return 文档作用域
     */
    protected final DocumentUiScope ui() {
        return documentUiScope;
    }

    protected final DocumentPageWidget getDocumentPage() {
        return documentPage;
    }

    @Override
    protected final void buildUi(Widget root) {
        configureDocumentPage(documentPage);
        buildDocument(documentPage);
        root.addChild(documentPage);
        afterDocumentBuilt();
    }

    @Override
    protected final void onResize(int width, int height) {
        super.onResize(width, height);

        DocumentScreenChrome chrome = getDocumentChrome(width, height);
        DocumentScreenChrome.Insets rootPadding = chrome.getRootPadding();
        setRootPadding(rootPadding.getLeft(), rootPadding.getTop(), rootPadding.getRight(), rootPadding.getBottom());

        DocumentScreenChrome.Insets pagePadding = chrome.getPagePadding();
        documentPage.setShellPadding(pagePadding.getLeft(), pagePadding.getTop(), pagePadding.getRight(), pagePadding.getBottom());

        onDocumentResized(width, height);
    }

    @Override
    public final void drawScreen(int mouseX, int mouseY, float partialTicks) {
        beforeDocumentFrame();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /**
     * 配置文档壳的静态语义约束。
     *
     * @param page 文档页面
     */
    protected void configureDocumentPage(DocumentPageWidget page) {}

    /**
     * 构建文档内容。
     *
     * @param page 文档页面
     */
    protected abstract void buildDocument(DocumentPageWidget page);

    /**
     * 在文档首次构建完成后补充初始化状态。
     */
    protected void afterDocumentBuilt() {}

    /**
     * 在文档壳尺寸变化后执行页面刷新。
     *
     * @param width 当前宿主宽度
     * @param height 当前宿主高度
     */
    protected void onDocumentResized(int width, int height) {}

    /**
     * 每帧在宿主绘制前刷新页面状态。
     */
    protected void beforeDocumentFrame() {}

    /**
     * 获取当前窗口尺寸下的文档壳策略。
     *
     * @param width 当前宿主宽度
     * @param height 当前宿主高度
     * @return 文档壳策略
     */
    protected DocumentScreenChrome getDocumentChrome(int width, int height) {
        return DocumentScreenChrome.resolve(width, height);
    }
}
