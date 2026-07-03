package club.heiqi.uilib.ui.scene.form;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneScrollbar;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;

import com.github.bsideup.jabel.Desugar;

/**
 * 通用页骨架 builder：收口 root + titleBar + scrollContainer(viewport + scrollbar) 的同构页结构。
 *
 * <p>从 {@code SceneDemoPageShell} 提炼下沉的零业务依赖通用工具，照 demo 页骨架范式，
 * 供 demo 适配层与未来其它表单/页消费方复用。骨架结构：
 * <pre>
 * root(COLUMN, fillParentHeight, rootBg, padding, gap)
 *   ├─ titleBar(title + subtitle)
 *   ├─ scrollContainer(ROW, fillParentHeight, gap=SCROLL_GAP)
 *   │   ├─ viewport(COLUMN, scrollable, clip, fillParentHeight, flexGrow=1, viewportBg)
 *   │   └─ scrollbarColumn (attachScroll 时)
 *   └─ statusBar (由 statusBarBuilder 回调自行 appendChild 到 root 末尾)
 * </pre>
 *
 * <p><b>零 config 依赖</b>：本类不 import 任何 {@code club.heiqi.config.*}，
 * 主题色由 caller 以 {@link FormTheme} 注入。</p>
 *
 * <p><b>零 MC/Forge/GL 依赖（守 I10）</b>：本类禁止 import 任何 Minecraft / Forge / GL 平台类型，
 * 与 scene 栈其余子包一致，保持纯 Java 响应式组合层。</p>
 *
 * <p>外观为静态构建，无动态 bind 派生（页骨架不随状态变化）；滚动受控源经
 * {@link SceneScrolls#attach} 取得，由 caller 自行消费。</p>
 */
public final class FormPageShell {
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

    /** 工具类，禁止实例化 */
    private FormPageShell() {
    }

    /**
     * 页骨架各部件。
     *
     * <p>各页以 {@link #root} 作为页面根，卡片内容挂 {@link #viewport}，
     * accessor 委托 {@link #scrollContainer}/{@link #scrollbarColumn}/{@link #viewport} 字段。</p>
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
     * 用主流默认参数构建页骨架（titleBarHeight=44, rootPadding=20, rootGap=12,
     * viewportPadding=14, viewportGap=14, viewportCornerRadius=10, attachScroll=true,
     * theme=FormTheme.defaultDark()）。
     *
     * @param rt          runtime
     * @param title       标题
     * @param subtitle    副标题/helper（可 null）
     * @param attachScroll 是否挂滚动受控源与可视滚动条
     * @param theme       主题 token
     * @return 骨架各部件
     */
    public static Parts build(SceneRuntime rt, String title, String subtitle,
                              boolean attachScroll, FormTheme theme) {
        return build(rt, title, subtitle, DEFAULT_TITLE_BAR_HEIGHT,
                DEFAULT_ROOT_PADDING, DEFAULT_ROOT_GAP,
                DEFAULT_VIEWPORT_PADDING, DEFAULT_VIEWPORT_GAP, DEFAULT_VIEWPORT_RADIUS,
                attachScroll, theme);
    }

    /**
     * 用完整参数化构建页骨架，供 titleBarHeight/padding/gap/cornerRadius 与主流不同的页使用。
     *
     * @param rt                  runtime
     * @param title               标题
     * @param subtitle            副标题/helper（可 null）
     * @param titleBarHeight      标题条固定高度
     * @param rootPadding         root 内边距
     * @param rootGap             root 间距
     * @param viewportPadding     视口内边距
     * @param viewportGap         视口间距
     * @param viewportCornerRadius 视口圆角
     * @param attachScroll        是否挂滚动受控源与可视滚动条；false 时 scrollSignal 为 null、不创建 scrollbar
     * @param theme               主题 token
     * @return 骨架各部件
     */
    public static Parts build(SceneRuntime rt, String title, String subtitle,
                              int titleBarHeight, int rootPadding, int rootGap,
                              int viewportPadding, int viewportGap, int viewportCornerRadius,
                              boolean attachScroll, FormTheme theme) {
        // root: COLUMN, fillParentHeight, rootBg
        SceneNode root = new SceneNode();
        root.setFillParentHeight(true);
        root.setFlexDirection(FlexDirection.COLUMN);
        root.setPadding(rootPadding);
        root.setGap(rootGap);
        root.setBackgroundColor(theme.rootBg());

        // titleBar: COLUMN, 固定高, title + subtitle
        SceneNode titleBar = SceneNode.column();
        titleBar.setPreferredHeight(titleBarHeight);
        titleBar.setGap(4);
        titleBar.setHitTestable(false);
        titleBar.appendChild(text(title, theme.titleColor()));
        if (subtitle != null && !subtitle.isEmpty()) {
            titleBar.appendChild(text(subtitle, theme.mutedColor()));
        }
        root.appendChild(titleBar);

        // viewport: COLUMN, scrollable, clip, fillParentHeight, flexGrow=1, viewportBg
        SceneNode viewport = SceneNode.column();
        viewport.setFillParentHeight(true);
        viewport.setFlexGrow(1);
        viewport.setScrollable(true);
        viewport.setClipChildren(true);
        viewport.setPadding(viewportPadding);
        viewport.setGap(viewportGap);
        viewport.setBackgroundColor(theme.viewportBg());
        viewport.setCornerRadius(viewportCornerRadius);

        // scrollContainer: ROW, fillParentHeight, gap=SCROLL_GAP
        SceneNode scrollContainer = SceneNode.row();
        scrollContainer.setFillParentHeight(true);
        scrollContainer.setGap(SCROLL_GAP);
        scrollContainer.appendChild(viewport);
        root.appendChild(scrollContainer);

        // 滚动受控源 + 可视滚动条：仅在 attachScroll=true 时挂载
        Signal<Integer> scrollSignal = null;
        SceneNode scrollbarColumn = null;
        if (attachScroll) {
            scrollSignal = SceneScrolls.attach(rt, viewport);
            SceneScrollbar.Result sb = SceneScrollbar.createDefault(rt, viewport, scrollSignal);
            scrollbarColumn = sb.column();
            scrollContainer.appendChild(scrollbarColumn);
        }

        return new Parts(root, viewport, scrollContainer, scrollbarColumn, scrollSignal);
    }

    /**
     * 创建不可命中、带初始文本与颜色的文字节点（页骨架标题/副标题用，无字号需求）。
     *
     * @param value 文本
     * @param color 颜色
     * @return 文字节点
     */
    private static SceneNode text(String value, int color) {
        SceneNode node = new SceneNode();
        node.setText(value);
        node.setTextColor(color);
        node.setHitTestable(false);
        return node;
    }
}
