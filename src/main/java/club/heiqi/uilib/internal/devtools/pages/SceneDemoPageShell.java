package club.heiqi.uilib.internal.devtools.pages;

import java.util.function.Consumer;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.form.FormPageShell;
import club.heiqi.uilib.ui.scene.form.FormTheme;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

import com.github.bsideup.jabel.Desugar;

/**
 * demo 页统一骨架 builder：薄包装 {@link FormPageShell}，注入 demo 色板组装的 {@link FormTheme}。
 *
 * <p>本类是 {@link FormPageShell} 的 demo 适配层：色值经 {@link SceneDemoTokens} 与
 * {@link FormTheme#defaultDark()} 同源组装为 {@code DEMO_THEME}，构建逻辑全部委托
 * {@link FormPageShell#build}，仅保留 demo 语义的 {@code statusBarBuilder} 回调后置。
 * 7 个 public 尺寸常量转发 {@link FormPageShell} 同名常量，供
 * {@code SceneControlsHostWidget} / {@code SceneLayoutHostWidget} 引用。</p>
 *
 * <p>骨架结构、statusBar 回调语义与原实现完全一致，仅物理实现下沉至 uilib.form 包，
 * 守 8 个 HostWidget 调用点零改动。</p>
 */
public final class SceneDemoPageShell {
    /** 主流 root 内边距（转发 {@link FormPageShell#DEFAULT_ROOT_PADDING}）。 */
    public static final int DEFAULT_ROOT_PADDING = FormPageShell.DEFAULT_ROOT_PADDING;
    /** 主流 root 间距（转发 {@link FormPageShell#DEFAULT_ROOT_GAP}）。 */
    public static final int DEFAULT_ROOT_GAP = FormPageShell.DEFAULT_ROOT_GAP;
    /** 主流标题条固定高度（转发 {@link FormPageShell#DEFAULT_TITLE_BAR_HEIGHT}）。 */
    public static final int DEFAULT_TITLE_BAR_HEIGHT = FormPageShell.DEFAULT_TITLE_BAR_HEIGHT;
    /** 主流视口内边距（转发 {@link FormPageShell#DEFAULT_VIEWPORT_PADDING}）。 */
    public static final int DEFAULT_VIEWPORT_PADDING = FormPageShell.DEFAULT_VIEWPORT_PADDING;
    /** 主流视口间距（转发 {@link FormPageShell#DEFAULT_VIEWPORT_GAP}）。 */
    public static final int DEFAULT_VIEWPORT_GAP = FormPageShell.DEFAULT_VIEWPORT_GAP;
    /** 主流视口圆角（转发 {@link FormPageShell#DEFAULT_VIEWPORT_RADIUS}）。 */
    public static final int DEFAULT_VIEWPORT_RADIUS = FormPageShell.DEFAULT_VIEWPORT_RADIUS;
    /** scrollContainer 内 viewport 与 scrollbar 列间距（转发 {@link FormPageShell#SCROLL_GAP}）。 */
    public static final int SCROLL_GAP = FormPageShell.SCROLL_GAP;

    /**
     * demo 色板主题：以 {@link FormTheme#defaultDark()} 为基底，显式用 {@link SceneDemoTokens}
     * 同源色覆盖 rootBg/viewportBg/titleColor/mutedColor，表达 demo 色板与 FormTheme 同源。
     */
    private static final FormTheme DEMO_THEME = new FormTheme(
            FormTheme.defaultDark().cardBg(),
            FormTheme.defaultDark().cardBorder(),
            FormTheme.defaultDark().cardBorderDirty(),
            FormTheme.defaultDark().cardBorderError(),
            FormTheme.defaultDark().cardRadius(),
            FormTheme.defaultDark().cardPad(),
            FormTheme.defaultDark().fieldGap(),
            FormTheme.defaultDark().textColor(),
            SceneDemoTokens.MUTED_COLOR,
            FormTheme.defaultDark().errorColor(),
            FormTheme.defaultDark().dirtyColor(),
            FormTheme.defaultDark().fontLabel(),
            FormTheme.defaultDark().fontHelper(),
            FormTheme.defaultDark().fontError(),
            FormTheme.defaultDark().inputHeight(),
            SceneDemoTokens.ROOT_BG,
            SceneDemoTokens.VIEWPORT_BG,
            SceneDemoTokens.TITLE_COLOR
    );

    private SceneDemoPageShell() {
    }

    /**
     * demo 页骨架各部件（转发 {@link FormPageShell.Parts}）。
     */
    @Desugar
    public record Parts(
            SceneNode root,
            SceneNode viewport,
            SceneNode scrollContainer,
            SceneNode scrollbarColumn,
            Signal<Integer> scrollSignal
    ) {
        /**
         * 从 {@link FormPageShell.Parts} 适配构造。
         *
         * @param p 下游部件
         */
        public Parts(FormPageShell.Parts p) {
            this(p.root(), p.viewport(), p.scrollContainer(), p.scrollbarColumn(), p.scrollSignal());
        }
    }

    /**
     * 用主流默认参数构建 demo 页骨架（titleBarHeight=44, rootPadding=20, rootGap=12,
     * viewportPadding=14, viewportGap=14, viewportCornerRadius=10, attachScroll=true）。
     *
     * @param rt runtime
     * @param title 标题
     * @param subtitle 副标题/helper（可 null）
     * @param showScrollbar 是否带可视滚动条（demo 语义，等价 attachScroll）
     * @param statusBarBuilder statusBar 构建回调（可 null）；回调参数为 root，回调内自建 statusBar 并 appendChild 到 root 末尾
     * @return 骨架各部件
     */
    public static Parts build(SceneRuntime rt, String title, String subtitle,
                              boolean showScrollbar, Consumer<SceneNode> statusBarBuilder) {
        return build(rt, title, subtitle, DEFAULT_TITLE_BAR_HEIGHT,
                DEFAULT_ROOT_PADDING, DEFAULT_ROOT_GAP,
                DEFAULT_VIEWPORT_PADDING, DEFAULT_VIEWPORT_GAP, DEFAULT_VIEWPORT_RADIUS,
                showScrollbar, statusBarBuilder);
    }

    /**
     * 用完整参数化构建 demo 页骨架，供 titleBarHeight/padding/gap/cornerRadius 与主流不同的页（Layout/Transform）使用。
     *
     * @param rt runtime
     * @param title 标题
     * @param subtitle 副标题/helper（可 null）
     * @param titleBarHeight 标题条固定高度
     * @param rootPadding root 内边距
     * @param rootGap root 间距
     * @param viewportPadding 视口内边距
     * @param viewportGap 视口间距
     * @param viewportCornerRadius 视口圆角
     * @param showScrollbar 是否带可视滚动条（demo 语义，等价 attachScroll）
     * @param statusBarBuilder statusBar 构建回调（可 null）；回调参数为 root，回调内自建 statusBar 并 appendChild 到 root 末尾
     * @return 骨架各部件
     */
    public static Parts build(SceneRuntime rt, String title, String subtitle,
                              int titleBarHeight, int rootPadding, int rootGap,
                              int viewportPadding, int viewportGap, int viewportCornerRadius,
                              boolean showScrollbar, Consumer<SceneNode> statusBarBuilder) {
        FormPageShell.Parts p = FormPageShell.build(rt, title, subtitle,
                titleBarHeight, rootPadding, rootGap,
                viewportPadding, viewportGap, viewportCornerRadius,
                showScrollbar, true, DEMO_THEME);
        // statusBar 由回调自行构建并 appendChild 到 root 末尾（demo 语义后置）
        if (statusBarBuilder != null) {
            statusBarBuilder.accept(p.root());
        }
        return new Parts(p);
    }
}
