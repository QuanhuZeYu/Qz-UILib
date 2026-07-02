package club.heiqi.uilib.internal.devtools.pages;

import java.util.function.Consumer;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;
import club.heiqi.uilib.ui.scene.control.SceneScrollbar;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.node.SceneNode;

import com.github.bsideup.jabel.Desugar;

/**
 * demo 页统一骨架 builder：收口 7 滚动 demo 页同构的 root + titleBar + scrollContainer(viewport+scrollbar) 结构。
 *
 * <p>骨架结构：
 * <pre>
 * root(COLUMN, fillParentHeight, ROOT_BG, padding, gap)
 *   ├─ titleBar(title + subtitle)
 *   ├─ scrollContainer(ROW, fillParentHeight, gap=3)
 *   │   ├─ viewport(COLUMN, scrollable, clip, fillParentHeight, flexGrow=1, VIEWPORT_BG)
 *   │   └─ scrollbarColumn (showScrollbar 时)
 *   └─ statusBar (由 statusBarBuilder 回调自行 appendChild 到 root 末尾)
 * </pre>
 *
 * <p>各页卡片内容挂 {@link Parts#viewport}（各页自建 content 容器 appendChild 到 viewport，
 * 因 content gap 各页存在差异不收口）。statusBar 样式与位置由各页通过 statusBarBuilder 自行控制，
 * 回调参数为 root，回调内自建 statusBar 节点并 appendChild 到 root 末尾。</p>
 *
 * <p>带动态 bind 边框的复杂外壳（如 Form 的 createFieldShell）不在此收口，仍留各页私有。</p>
 */
public final class SceneDemoPageShell {
    /** 主流 root 内边距。 */
    public static final int DEFAULT_ROOT_PADDING = 20;
    /** 主流 root 间距。 */
    public static final int DEFAULT_ROOT_GAP = 12;
    /** 主流标题条固定高度。 */
    public static final int DEFAULT_TITLE_BAR_HEIGHT = 44;
    /** 主流视口内边距。 */
    public static final int DEFAULT_VIEWPORT_PADDING = 14;
    /** 主流视口间距。 */
    public static final int DEFAULT_VIEWPORT_GAP = 14;
    /** 主流视口圆角。 */
    public static final int DEFAULT_VIEWPORT_RADIUS = 10;
    /** scrollContainer 内 viewport 与 scrollbar 列间距。 */
    public static final int SCROLL_GAP = 3;

    private SceneDemoPageShell() {
    }

    /**
     * demo 页骨架各部件。
     *
     * <p>各页以 {@link #root} 作为页面根，卡片内容挂 {@link #viewport}，
     * accessor 委托 {@link #scrollContainer}/@{#scrollbarColumn}/@{#viewport} 字段。</p>
     */
    @Desugar
    public record Parts(
            SceneNode root,
            SceneNode viewport,
            SceneNode scrollContainer,
            SceneNode scrollbarColumn,
            Signal<Integer> scrollSignal
    ) {
    }

    /**
     * 用主流默认参数构建 demo 页骨架（titleBarHeight=44, rootPadding=20, rootGap=12,
     * viewportPadding=14, viewportGap=14, viewportCornerRadius=10）。
     *
     * @param rt runtime
     * @param title 标题
     * @param subtitle 副标题/helper（可 null）
     * @param showScrollbar 是否带可视滚动条
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
     * @param showScrollbar 是否带可视滚动条
     * @param statusBarBuilder statusBar 构建回调（可 null）；回调参数为 root，回调内自建 statusBar 并 appendChild 到 root 末尾
     * @return 骨架各部件
     */
    public static Parts build(SceneRuntime rt, String title, String subtitle,
                              int titleBarHeight, int rootPadding, int rootGap,
                              int viewportPadding, int viewportGap, int viewportCornerRadius,
                              boolean showScrollbar, Consumer<SceneNode> statusBarBuilder) {
        // root: COLUMN, fillParentHeight, ROOT_BG
        SceneNode root = new SceneNode();
        root.setFillParentHeight(true);
        root.setFlexDirection(FlexDirection.COLUMN);
        root.setPadding(rootPadding);
        root.setGap(rootGap);
        root.setBackgroundColor(SceneDemoTokens.ROOT_BG);

        // titleBar: COLUMN, 固定高, title + subtitle
        SceneNode titleBar = SceneNode.column();
        titleBar.setPreferredHeight(titleBarHeight);
        titleBar.setGap(4);
        titleBar.setHitTestable(false);
        titleBar.appendChild(SceneDemoCards.text(title, SceneDemoTokens.TITLE_COLOR));
        if (subtitle != null && !subtitle.isEmpty()) {
            titleBar.appendChild(SceneDemoCards.text(subtitle, SceneDemoTokens.MUTED_COLOR));
        }
        root.appendChild(titleBar);

        // viewport: COLUMN, scrollable, clip, fillParentHeight, flexGrow=1, VIEWPORT_BG
        SceneNode viewport = SceneNode.column();
        viewport.setFillParentHeight(true);
        viewport.setFlexGrow(1);
        viewport.setScrollable(true);
        viewport.setClipChildren(true);
        viewport.setPadding(viewportPadding);
        viewport.setGap(viewportGap);
        viewport.setBackgroundColor(SceneDemoTokens.VIEWPORT_BG);
        viewport.setCornerRadius(viewportCornerRadius);

        // scrollContainer: ROW, fillParentHeight, gap=3
        SceneNode scrollContainer = SceneNode.row();
        scrollContainer.setFillParentHeight(true);
        scrollContainer.setGap(SCROLL_GAP);
        scrollContainer.appendChild(viewport);
        root.appendChild(scrollContainer);

        // 滚动受控源
        Signal<Integer> scrollSignal = SceneScrolls.attach(rt, viewport);

        // 可视滚动条叠加在 viewport 右侧（scrollContainer ROW 内独立列）
        SceneNode scrollbarColumn = null;
        if (showScrollbar) {
            SceneScrollbar.Props sbProps = new SceneScrollbar.Props(
                    viewport, scrollSignal, scrollSignal::set,
                    SceneScrollbar.DEFAULT_TRACK_COLOR, SceneScrollbar.DEFAULT_THUMB_COLOR,
                    SceneScrollbar.DEFAULT_BAR_WIDTH, SceneScrollbar.DEFAULT_MIN_THUMB_HEIGHT);
            SceneScrollbar.Result sb = SceneScrollbar.create(rt, sbProps);
            scrollbarColumn = sb.column();
            scrollContainer.appendChild(scrollbarColumn);
        }

        // statusBar 由回调自行构建并 appendChild 到 root 末尾
        if (statusBarBuilder != null) {
            statusBarBuilder.accept(root);
        }

        return new Parts(root, viewport, scrollContainer, scrollbarColumn, scrollSignal);
    }
}
