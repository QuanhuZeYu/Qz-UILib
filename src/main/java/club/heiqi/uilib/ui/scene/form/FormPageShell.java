package club.heiqi.uilib.ui.scene.form;

import java.util.Objects;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneScrollbar;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceBinder;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

import com.github.bsideup.jabel.Desugar;

/**
 * 通用页骨架 builder：收口 root + titleBar + scrollContainer(viewport + scrollbar) 的同构页结构。
 *
 * <p>从 {@code SceneDemoPageShell} 提炼下沉的零业务依赖通用工具，照 demo 页骨架范式，
 * 供 demo 适配层与未来其它表单/页消费方复用。骨架结构：
 * <pre>
 * root(COLUMN, fillParentHeight, PANEL 表面, padding, gap)
 *   ├─ titleBar(title + subtitle)
 *   ├─ scrollContainer(ROW, fillParentHeight, gap=SCROLL_GAP)
 *   │   ├─ viewport(COLUMN, scrollable, clip, fillParentHeight, flexGrow=1, PANEL 表面)
 *   │   └─ scrollbarColumn (attachScroll 时)
 *   └─ statusBar (由 statusBarBuilder 回调自行 appendChild 到 root 末尾)
 * </pre>
 *
 * <p><b>两条外观路径（一个属性只有一个写入者，互斥不叠加）</b></p>
 * <ul>
 *   <li><b>默认路径</b>——不传 {@link FormTheme} 的重载：root/viewport 表面经
 *       {@link SceneSurfaceBinder#bind} 消费来源主题的 {@link SceneTheme.Role#PANEL} 配方
 *       （染色/边框/圆角/浮雕/滤镜全归绑定器，来源主题切换只重派生、不重建节点）；
 *       标题取主题 {@code foreground}、副标题取 {@code mutedForeground}（经 {@link FormThemes}
 *       映射为 {@code titleColor}/{@code mutedColor}）。</li>
 *   <li><b>显式路径</b>——保留的 {@link FormTheme} 重载：语义与迁移前一致，
 *       rootBg/viewportBg 静态写入、视口圆角取 {@code viewportCornerRadius}、
 *       标题取 {@code titleColor}/{@code mutedColor}；不装滤镜、不订阅主题，
 *       显式主题完全覆盖主题派生（旧调用方「显式底色后再覆盖」的写法继续成立）。</li>
 * </ul>
 * <p>两条路径的布局（标题条高度、内边距、间距、视口高度、滚动偏移）完全一致；
 * 滚动条沿用已主题化的 {@link SceneScrollbar#createDefault}。</p>
 *
 * <p><b>零 config 依赖</b>：本类不 import 任何 {@code club.heiqi.config.*}，
 * 主题色由 caller 以 {@link FormTheme} 注入或按来源主题派生。</p>
 *
 * <p><b>零 MC/Forge/GL 依赖（守 I10）</b>：本类禁止 import 任何 Minecraft / Forge / GL 平台类型，
 * 与 scene 栈其余子包一致，保持纯 Java 响应式组合层。</p>
 *
 * <p>滚动受控源经 {@link SceneScrolls#attach} 取得，由 caller 自行消费。</p>
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
    /** 主流视口圆角（显式 {@link FormTheme} 路径生效；默认路径圆角由 PANEL 配方提供）。 */
    public static final int DEFAULT_VIEWPORT_RADIUS = 10;
    /** scrollContainer 内 viewport 与 scrollbar 列间距。 */
    public static final int SCROLL_GAP = 3;

    /** 页骨架 root/viewport 恒定启用（页壳无禁用态），供表面绑定器判定状态档。 */
    private static final ReadableSignal<Boolean> ALWAYS_ENABLED = () -> Boolean.TRUE;

    /** 工具类，禁止实例化 */
    private FormPageShell() {
    }

    /**
     * 页骨架各部件。
     *
     * <p>各页以 {@link #root} 作为页面根，卡片内容挂 {@link #viewport}，
     * accessor 委托 {@link #scrollContainer}/{@link #scrollbarColumn}/{@link #viewport} 字段。</p>
     *
     * <p>{@link #titleBar} 为 shell 构建的标题条节点引用：
     * {@code buildTitleBar=true} 时为标题条节点（root 首子），
     * {@code buildTitleBar=false} 时为 null（caller 自建标题条）。
     * 消费者需摘除/引用标题条时应使用此字段，而非按下标 {@code root.__getChildren().get(0)}
     * 隐式耦合「titleBar 必为 root 首子」契约。</p>
     */
    @Desugar
    public record Parts(
            SceneNode root,
            SceneNode titleBar,
            SceneNode viewport,
            SceneNode scrollContainer,
            SceneNode scrollbarColumn,
            Signal<Integer> scrollSignal
    ) {
    }

    // ==================== 默认路径：外观跟随来源主题（不传 FormTheme） ====================

    /**
     * 用主流默认参数构建页骨架，外观跟随来源主题
     * （titleBarHeight=44, rootPadding=20, rootGap=12, viewportPadding=14, viewportGap=14,
     * attachScroll=true, buildTitleBar=true）。
     *
     * <p>root/viewport 表面取 {@link SceneThemes#surface} 的 {@link SceneTheme.Role#PANEL} 配方，
     * 标题/副标题取来源主题 {@code foreground}/{@code mutedForeground}；来源主题切换
     * （{@link SceneThemes#withTheme}）只重派生外观，不重建节点、不丢滚动偏移。</p>
     *
     * @param rt          runtime
     * @param title       标题
     * @param subtitle    副标题/helper（可 null）
     * @param attachScroll 是否挂滚动受控源与可视滚动条
     * @return 骨架各部件
     */
    public static Parts build(SceneRuntime rt, String title, String subtitle, boolean attachScroll) {
        return build(rt, title, subtitle, attachScroll, true);
    }

    /**
     * 用主流默认参数构建跟随主题的页骨架，显式控制是否构建 titleBar。
     *
     * <p>{@code buildTitleBar=false} 时跳过 titleBar 构造，root 首子直接是 scrollContainer，
     * 供需自建标题条（如字号特化）的 caller 使用。</p>
     *
     * @param rt            runtime
     * @param title         标题（buildTitleBar=false 时忽略）
     * @param subtitle      副标题/helper（可 null；buildTitleBar=false 时忽略）
     * @param attachScroll  是否挂滚动受控源与可视滚动条
     * @param buildTitleBar 是否构建 shell 标题条；false 时 root 首子直接是 scrollContainer，Parts.titleBar 为 null
     * @return 骨架各部件
     */
    public static Parts build(SceneRuntime rt, String title, String subtitle,
                              boolean attachScroll, boolean buildTitleBar) {
        return build(rt, title, subtitle, DEFAULT_TITLE_BAR_HEIGHT,
                DEFAULT_ROOT_PADDING, DEFAULT_ROOT_GAP,
                DEFAULT_VIEWPORT_PADDING, DEFAULT_VIEWPORT_GAP,
                attachScroll, buildTitleBar);
    }

    /**
     * 用完整参数化构建跟随主题的页骨架，供标题条高度/padding/gap 与主流不同的页使用。
     *
     * <p>不设视口圆角形参：默认路径的圆角/边框/浮雕/滤镜由 PANEL 配方提供，
     * 表面绑定器是该属性的唯一写入者（需要旧实色圆角语义的 caller 走显式 {@link FormTheme} 重载）。</p>
     *
     * @param rt                  runtime
     * @param title               标题（buildTitleBar=false 时忽略）
     * @param subtitle            副标题/helper（可 null；buildTitleBar=false 时忽略）
     * @param titleBarHeight      标题条固定高度（buildTitleBar=false 时忽略）
     * @param rootPadding         root 内边距
     * @param rootGap             root 间距
     * @param viewportPadding     视口内边距
     * @param viewportGap         视口间距
     * @param attachScroll        是否挂滚动受控源与可视滚动条；false 时 scrollSignal 为 null、不创建 scrollbar
     * @param buildTitleBar       是否构建 shell 标题条；false 时跳过 titleBar 构造，root 首子直接是 scrollContainer，Parts.titleBar 为 null
     * @return 骨架各部件
     */
    public static Parts build(SceneRuntime rt, String title, String subtitle,
                              int titleBarHeight, int rootPadding, int rootGap,
                              int viewportPadding, int viewportGap,
                              boolean attachScroll, boolean buildTitleBar) {
        return buildCore(rt, title, subtitle, titleBarHeight, rootPadding, rootGap,
                viewportPadding, viewportGap, DEFAULT_VIEWPORT_RADIUS,
                attachScroll, buildTitleBar, FormThemes.resolve(rt), true);
    }

    // ==================== 显式路径：旧 FormTheme 重载（语义不变，显式优先） ====================

    /**
     * 用主流默认参数构建页骨架（titleBarHeight=44, rootPadding=20, rootGap=12,
     * viewportPadding=14, viewportGap=14, viewportCornerRadius=10, attachScroll=true,
     * buildTitleBar=true, theme=调用方显式传入）。
     *
     * <p>兼容重载：默认构建 titleBar（绝大多数页沿用 shell 标题条）。需跳过 titleBar 构造的
     * caller（如 ConfigScreen 因字号需求自建标题条）改用 {@link #build(SceneRuntime, String, String, boolean, boolean, FormTheme)}
     * 传 {@code buildTitleBar=false}，避免 shell 无用构造后再被摘下丢弃。</p>
     *
     * @param rt          runtime
     * @param title       标题
     * @param subtitle    副标题/helper（可 null）
     * @param attachScroll 是否挂滚动受控源与可视滚动条
     * @param theme       显式主题 token（完全覆盖来源主题）
     * @return 骨架各部件
     */
    public static Parts build(SceneRuntime rt, String title, String subtitle,
                              boolean attachScroll, FormTheme theme) {
        return build(rt, title, subtitle, DEFAULT_TITLE_BAR_HEIGHT,
                DEFAULT_ROOT_PADDING, DEFAULT_ROOT_GAP,
                DEFAULT_VIEWPORT_PADDING, DEFAULT_VIEWPORT_GAP, DEFAULT_VIEWPORT_RADIUS,
                attachScroll, true, theme);
    }

    /**
     * 用主流默认参数构建页骨架，显式控制是否构建 titleBar。
     *
     * <p>{@code buildTitleBar=false} 时跳过 titleBar 构造，root 首子直接是 scrollContainer，
     * 供需自建标题条（如字号特化）的 caller 使用，避免「shell 建了又被摘下丢弃」的无用构造。</p>
     *
     * @param rt            runtime
     * @param title         标题（buildTitleBar=false 时忽略）
     * @param subtitle      副标题/helper（可 null；buildTitleBar=false 时忽略）
     * @param attachScroll  是否挂滚动受控源与可视滚动条
     * @param buildTitleBar 是否构建 shell 标题条；false 时 root 首子直接是 scrollContainer，Parts.titleBar 为 null
     * @param theme         显式主题 token（完全覆盖来源主题）
     * @return 骨架各部件
     */
    public static Parts build(SceneRuntime rt, String title, String subtitle,
                              boolean attachScroll, boolean buildTitleBar, FormTheme theme) {
        return build(rt, title, subtitle, DEFAULT_TITLE_BAR_HEIGHT,
                DEFAULT_ROOT_PADDING, DEFAULT_ROOT_GAP,
                DEFAULT_VIEWPORT_PADDING, DEFAULT_VIEWPORT_GAP, DEFAULT_VIEWPORT_RADIUS,
                attachScroll, buildTitleBar, theme);
    }

    /**
     * 用完整参数化构建页骨架，供 titleBarHeight/padding/gap/cornerRadius 与主流不同的页使用。
     *
     * <p>显式 {@link FormTheme} 路径：rootBg/viewportBg 静态写入节点、视口圆角取
     * {@code viewportCornerRadius}，不装滤镜、不订阅主题——与迁移前语义一致。</p>
     *
     * @param rt                  runtime
     * @param title               标题（buildTitleBar=false 时忽略）
     * @param subtitle            副标题/helper（可 null；buildTitleBar=false 时忽略）
     * @param titleBarHeight      标题条固定高度（buildTitleBar=false 时忽略）
     * @param rootPadding         root 内边距
     * @param rootGap             root 间距
     * @param viewportPadding     视口内边距
     * @param viewportGap         视口间距
     * @param viewportCornerRadius 视口圆角
     * @param attachScroll        是否挂滚动受控源与可视滚动条；false 时 scrollSignal 为 null、不创建 scrollbar
     * @param buildTitleBar       是否构建 shell 标题条；false 时跳过 titleBar 构造，root 首子直接是 scrollContainer，Parts.titleBar 为 null
     * @param theme               显式主题 token（完全覆盖来源主题）
     * @return 骨架各部件
     */
    public static Parts build(SceneRuntime rt, String title, String subtitle,
                              int titleBarHeight, int rootPadding, int rootGap,
                              int viewportPadding, int viewportGap, int viewportCornerRadius,
                              boolean attachScroll, boolean buildTitleBar, FormTheme theme) {
        Objects.requireNonNull(theme, "theme");
        return buildCore(rt, title, subtitle, titleBarHeight, rootPadding, rootGap,
                viewportPadding, viewportGap, viewportCornerRadius,
                attachScroll, buildTitleBar, () -> theme, false);
    }

    // ==================== 共用骨架装配 ====================

    /**
     * 骨架装配核心：两条外观路径共用同一结构与布局，只在「外观写入者」上分流。
     *
     * @param rt                   runtime
     * @param title                标题
     * @param subtitle             副标题/helper（可 null）
     * @param titleBarHeight       标题条固定高度
     * @param rootPadding          root 内边距
     * @param rootGap              root 间距
     * @param viewportPadding      视口内边距
     * @param viewportGap          视口间距
     * @param viewportCornerRadius 显式路径的视口圆角（主题路径由 PANEL 配方接管，不参与）
     * @param attachScroll         是否挂滚动受控源与可视滚动条
     * @param buildTitleBar        是否构建 shell 标题条
     * @param theme                表单主题信号：主题路径为 {@link FormThemes#resolve} 的派生，显式路径为常量
     * @param themedSurface        true = 表面/前景跟随主题重派生；false = 旧静态写入语义
     * @return 骨架各部件
     */
    private static Parts buildCore(SceneRuntime rt, String title, String subtitle,
                                   int titleBarHeight, int rootPadding, int rootGap,
                                   int viewportPadding, int viewportGap, int viewportCornerRadius,
                                   boolean attachScroll, boolean buildTitleBar,
                                   ReadableSignal<FormTheme> theme, boolean themedSurface) {
        Objects.requireNonNull(rt, "rt");
        Objects.requireNonNull(theme, "theme");

        // root: COLUMN, fillParentHeight（表面见下：主题路径 PANEL 配方 / 显式路径静态底色）
        SceneNode root = new SceneNode();
        root.setFillParentHeight(true);
        root.setFlexDirection(FlexDirection.COLUMN);
        root.setPadding(rootPadding);
        root.setGap(rootGap);

        // titleBar: COLUMN, 固定高, title + subtitle（buildTitleBar=false 时跳过构造）
        SceneNode titleBar = null;
        if (buildTitleBar) {
            titleBar = SceneNode.column();
            titleBar.setPreferredHeight(titleBarHeight);
            titleBar.setGap(4);
            titleBar.setHitTestable(false);
            titleBar.appendChild(text(rt, title, theme, true, themedSurface));
            if (subtitle != null && !subtitle.isEmpty()) {
                titleBar.appendChild(text(rt, subtitle, theme, false, themedSurface));
            }
            root.appendChild(titleBar);
        }

        // viewport: COLUMN, scrollable, clip, fillParentHeight, flexGrow=1（表面见下）
        SceneNode viewport = SceneNode.column();
        viewport.setFillParentHeight(true);
        viewport.setFlexGrow(1);
        viewport.setScrollable(true);
        viewport.setClipChildren(true);
        viewport.setPadding(viewportPadding);
        viewport.setGap(viewportGap);

        if (themedSurface) {
            // 主题路径：root/viewport 表面全归表面绑定器（染色/边框/圆角/浮雕/滤镜）。
            // 圆角等几何取自 PANEL 配方，故本路径不设视口圆角形参；绑定器是这些属性的唯一写入者。
            ReadableSignal<SceneSurfaceStyle> panel = SceneThemes.surface(rt, SceneTheme.Role.PANEL);
            bindPanelSurface(rt, root, panel);
            bindPanelSurface(rt, viewport, panel);
        } else {
            // 显式路径：旧静态写入者独占（无滤镜、无双写入者），语义与迁移前一致。
            root.setBackgroundColor(theme.get().rootBg());
            viewport.setBackgroundColor(theme.get().viewportBg());
            viewport.setCornerRadius(viewportCornerRadius);
        }

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

        return new Parts(root, titleBar, viewport, scrollContainer, scrollbarColumn, scrollSignal);
    }

    /**
     * 把来源主题的 PANEL 配方绑到页骨架节点：染色/边框/圆角/浮雕/滤镜全归表面绑定器。
     *
     * <p>页根与视口恒定启用（页壳无禁用态）。构建期先声明关心 hover/pressed/focus：
     * Router 对未创建的 signal 直接短路，不声明则后续事件永远驱动不了配方状态档。</p>
     *
     * @param rt    runtime
     * @param node  页根或视口
     * @param panel PANEL 配方信号（来源主题派生，主题切换自动重算）
     */
    private static void bindPanelSurface(SceneRuntime rt, SceneNode node,
                                         ReadableSignal<SceneSurfaceStyle> panel) {
        SceneInteractionState interaction = rt.interactionState(node);
        interaction.hovered();
        interaction.pressed();
        interaction.focused();
        SceneSurfaceBinder.bind(rt, node, panel, ALWAYS_ENABLED, interaction);
    }

    /**
     * 创建不可命中、带文本与颜色的文字节点（页骨架标题/副标题用，无字号需求）。
     *
     * <p>标题取主题 {@code titleColor}、副标题取 {@code mutedColor}；主题路径经
     * {@link FormThemes} 映射后即来源主题的 {@code foreground}/{@code mutedForeground}，
     * 且随来源主题重派生；显式路径按旧语义静态写入。</p>
     *
     * @param rt            runtime
     * @param value         文本
     * @param theme         表单主题信号
     * @param title         true = 标题色，false = 副标题色
     * @param themedSurface true = 随主题重派生，false = 静态写入
     * @return 文字节点
     */
    private static SceneNode text(SceneRuntime rt, String value, ReadableSignal<FormTheme> theme,
                                  boolean title, boolean themedSurface) {
        SceneNode node = new SceneNode();
        node.setText(value);
        node.setHitTestable(false);
        if (themedSurface) {
            rt.bindComputed(() -> title ? theme.get().titleColor() : theme.get().mutedColor(),
                    node::setTextColor);
        } else {
            node.setTextColor(title ? theme.get().titleColor() : theme.get().mutedColor());
        }
        return node;
    }
}
