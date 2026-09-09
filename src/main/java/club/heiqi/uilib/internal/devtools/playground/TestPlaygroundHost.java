package club.heiqi.uilib.internal.devtools.playground;

import java.util.ArrayList;
import java.util.List;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneScrollbar;
import club.heiqi.uilib.ui.scene.control.SceneSegmented;
import club.heiqi.uilib.ui.scene.host.AbstractSceneHostWidget;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceBinder;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * 测试场地 scene 宿主 —— 顶栏 + 分段导航 + 单槽演示页 + 滚动视口。
 *
 * <p>树结构（COLUMN 居中，内容受最大宽约束）：</p>
 * <pre>
 * root (COLUMN, fillParent, crossAxisAlign=CENTER, padding, gap)  ← PANEL 主题配方
 *   ├ header                自建标题条（22px 主标题 + 12px 副标题）  ← TOOLBAR 主题配方
 *   ├ navBar                SceneSegmented 分段导航（页清单单选项，受控 selectedIndex）
 *   └ scrollContainer (ROW, fillParentHeight, gap)
 *       ├ viewport (COLUMN, fillParentHeight, flexGrow=1, scrollable, clip) ← GROUP 主题配方
 *       │   └ content (COLUMN) ← 当前页根挂载点
 *       └ scrollbarColumn        SceneScrollbar（反映 viewport 滚动）
 * </pre>
 *
 * <h3>外观归属（G16/外壳）</h3>
 * <p>外壳三块表面（root/header/viewport）的 background/border/borderWidth/cornerRadius/
 * backdrop/surfaceElevation 唯一写入者是 {@link SceneSurfaceBinder}，配方取当前来源主题的
 * PANEL/TOOLBAR/GROUP 角色；旧的静态底色/圆角写入者（{@code PlaygroundKit.ROOT_BG}、
 * {@code PANEL_BG}、{@code RADIUS_LG}）已删除，仅保留 PAD/GAP 等布局常量（主题不接管布局）。
 * 顶栏文字取主题 {@code foreground}/{@code mutedForeground}。</p>
 *
 * <p>导航底座由 {@link SceneSegmented} 在其根节点上自行绑定 TOOLBAR 配方（G09 已主题化，
 * 只读复用）；{@code navBar} 只做布局容器，<b>不再叠第二层玻璃</b>。</p>
 *
 * <p>主题来源是宿主自己的 runtime 默认主题信号（{@link #__getThemeSignal()}）：外壳在构造期
 * 解析一次并随信号更新，页面内容经 Owner 作用域继承同一来源；页面内的
 * {@link SceneThemes#withTheme} 局部覆盖只作用于该作用域内的内容，不外泄到外壳。</p>
 *
 * <h3>页面切换守不变量</h3>
 * <ul>
 *   <li>导航请求经 {@link #activePageSignal} 受控源（R8：控件不自己改 selectedIndex，
 *       宿主在 onSelect 写回 signal；signal-first，handler 不直接改树）。</li>
 *   <li>单槽切换：先 dispose 旧 mount 句柄（回收旧页 Owner 内全部 bind/effect/on），
 *       再 mount 新页；任一时刻至多一个 live 页面。</li>
 *   <li>页切换后重置 viewport 滚动到顶部，并请求 hover 重对账；主动刷新（{@link #refreshPage()}）
 *       走同一单槽路径但保留滚动位置。</li>
 * </ul>
 *
 * <p>构造器接受可为 null 的 {@link PlatformInputSource}（headless 测试传 null，
 * 纯渲染退化模式）。scan {@link PlaygroundPageRegistry#defaultPages()} 构建导航。</p>
 */
public class TestPlaygroundHost extends AbstractSceneHostWidget {

    /** 内容最大宽（UI 像素）。 */
    private static final int CONTENT_MAX_WIDTH = PlaygroundKit.MAX_CONTENT_WIDTH;
    /** root 内边距。 */
    private static final int ROOT_PADDING = 16;
    /** root 纵向间距。 */
    private static final int ROOT_GAP = 12;
    /** 副标题字号。 */
    private static final int SUBTITLE_FONT_SIZE = 12;
    /** 外壳 enabled：外壳表面不可禁用（表面绑定器只关心恒真）。 */
    private static final ReadableSignal<Boolean> SHELL_ENABLED = () -> Boolean.TRUE;

    /** 页面清单（构建期快照，不可变）。 */
    private final List<PlaygroundPage> pages;
    /** 受控导航源：当前页下标（0 起）。 */
    private final Signal<Integer> activePageSignal;
    /** runtime 默认主题信号：外壳与页面内容共用同一来源，可整体切换。 */
    private final Signal<SceneTheme> themeSignal;

    /** 场景树根节点。 */
    private SceneNode root;
    /** 顶栏节点（标题 + 副标题）。 */
    private SceneNode header;
    /** 导航条（SceneSegmented 挂载点）。 */
    private SceneNode navBar;
    /** 滚动视口（页面内容宿主）。 */
    private SceneNode viewport;
    /** 视口内容容器（当前页根挂载点）。 */
    private SceneNode content;
    /** 当前 live 页面的 mount 句柄；null 表示无页面。 */
    private MountHandle pageMount;
    /** 当前 live 页面下标。 */
    private int displayedPageIndex = -1;

    /**
     * 创建测试场地宿主。
     *
     * @param input 平台输入源，可为 null（headless 测试退化模式）
     */
    public TestPlaygroundHost(PlatformInputSource input) {
        super(input);
        this.pages = PlaygroundPageRegistry.defaultPages();
        this.activePageSignal = Signal.create(Integer.valueOf(0));
        this.themeSignal = Signal.create(SceneThemes.DEFAULT);
        runtime.__enableMotion();
        // 主题来源先于建树安装：外壳与页面都从 runtime 根作用域继承同一份主题信号。
        SceneThemes.install(runtime, themeSignal);
        // 公共构件（PlaygroundKit.card）没有 rt 形参，由宿主把 runtime 挂上 Owner 链。
        PlaygroundKit.installRuntime(runtime);
        // 外壳与首页都在 rootOwner 作用域内构建：构造期没有当前 Owner 时，主题解析与
        // bindComputed 创建的 Computed/Effect 不归属任何作用域，卸载无法回收（effect 泄漏）。
        runtime.__runRoot(() -> {
            buildShell();
            runtime.bind(activePageSignal, this::requestPageTransition);
            mountPage(0);
        });
        // A4c:构造期 flush 已收口——首帧管线 FLUSH 相位即物化本页;测试须自行 flush(见各页测试)。
    }

    // ==================== 骨架构建 ====================

    private void buildShell() {
        root = SceneNode.column();
        root.setFillParentWidth(true);
        root.setFillParentHeight(true);
        root.setCrossAxisAlign(CrossAxisAlign.CENTER);
        root.setPadding(ROOT_PADDING);
        root.setGap(ROOT_GAP);
        // 外壳主面板：PANEL 配方（旧 ROOT_BG 静态底色写入者已删）。
        bindShellSurface(root, SceneTheme.Role.PANEL);

        header = SceneNode.column();
        header.setFillParentWidth(true);
        header.setMaxWidth(CONTENT_MAX_WIDTH);
        header.setGap(2);
        header.setHitTestable(false);
        // 顶栏文字走主题前景派生（构建期不读值，随主题更新）；旧静态 TEXT/MUTED 取色已删。
        header.appendChild(PlaygroundKit.text(runtime, "Qz UILib 测试场地",
                SceneThemes.foreground(runtime), 22));
        header.appendChild(PlaygroundKit.text(runtime, "内部开发调试入口 · 输入命令 /qzuilib test 打开",
                SceneThemes.mutedForeground(runtime), SUBTITLE_FONT_SIZE));
        // 固定兄弟高度先验：root（COLUMN）的 grow 求解器要求固定兄弟可先验，容器型兄弟
        // 不设 preferredHeight 会 UNCONSTRAINED 早退 → viewport 高度解耦失败、maxScrollY 恒 0
        // （真机「只能看到样式继承、无法滚动」根因）。header = 标题行高 + gap + 副标题行高。
        header.setPreferredHeight(measurer.lineHeight(22) + header.getGap() + measurer.lineHeight(SUBTITLE_FONT_SIZE));
        // 顶栏底座：TOOLBAR 配方（与导航底座同角色）。
        bindShellSurface(header, SceneTheme.Role.TOOLBAR);
        root.appendChild(header);

        navBar = SceneNode.row();
        navBar.setFillParentWidth(true);
        navBar.setMaxWidth(CONTENT_MAX_WIDTH);
        // 高度先验口径与 SceneSegmented 内部一致：标签行高 lineHeight(16) + 2×PAD_LG。
        navBar.setPreferredHeight(measurer.lineHeight(16) + 2 * SceneChromeTokens.PAD_LG);
        List<String> titles = new ArrayList<String>(pages.size());
        for (PlaygroundPage page : pages) {
            titles.add(page.title());
        }
        SceneSegmented.Props segProps = new SceneSegmented.Props(
                activePageSignal, titles, Signal.create(Boolean.TRUE),
                idx -> activePageSignal.set(Integer.valueOf(idx)));
        // 导航底座 = SceneSegmented 根（G09 已在其根上绑定 TOOLBAR 配方，只读复用）；
        // navBar 只做布局容器，不在此再叠第二层玻璃。
        runtime.mount(navBar, SceneSegmented.create(runtime, segProps));
        root.appendChild(navBar);

        SceneNode scrollContainer = SceneNode.row(SceneChromeTokens.GAP_SM);
        scrollContainer.setFillParentWidth(true);
        scrollContainer.setFillParentHeight(true);
        scrollContainer.setMaxWidth(CONTENT_MAX_WIDTH);

        viewport = SceneNode.column();
        viewport.setFillParentHeight(true);
        viewport.setFlexGrow(1);
        viewport.setScrollable(true);
        viewport.setClipChildren(true);
        viewport.setPadding(SceneChromeTokens.PAD_LG);
        viewport.setGap(SceneChromeTokens.GAP_MD);
        // 内容底座：GROUP 配方（旧 PANEL_BG 底色与 RADIUS_LG 圆角静态写入者已删）。
        bindShellSurface(viewport, SceneTheme.Role.GROUP);
        scrollContainer.appendChild(viewport);

        content = SceneNode.column();
        content.setFillParentWidth(true);
        viewport.appendChild(content);

        // 滚动受控源 + 可视滚动条（与 FormPageShell attachScroll 同口径）。
        Signal<Integer> scrollSignal = SceneScrolls.attach(runtime, viewport);
        SceneScrollbar.Result sb = SceneScrollbar.createDefault(runtime, viewport, scrollSignal);
        scrollContainer.appendChild(sb.column());
        root.appendChild(scrollContainer);
    }

    /**
     * 绑定外壳表面：配方从当前来源主题解析（外壳在 rootOwner 作用域内构建，取宿主 runtime
     * 默认主题），background/border/borderWidth/cornerRadius/backdrop/surfaceElevation 唯一
     * 写入者是 {@link SceneSurfaceBinder}。
     *
     * @param node 外壳节点
     * @param role 材质角色
     */
    private void bindShellSurface(SceneNode node, SceneTheme.Role role) {
        SceneInteractionState interaction = runtime.interactionState(node);
        // 时序契约：Router 的 writeHovered/writePressed/writeFocused 对未创建的 signal 短路，
        // 故在构建期声明关心，保证后续 hover/focus 能驱动配方状态档。
        interaction.hovered();
        interaction.pressed();
        interaction.focused();
        SceneSurfaceBinder.bind(runtime, node, SceneThemes.surface(runtime, role), SHELL_ENABLED, interaction);
    }

    // ==================== 页面切换 ====================

    /** 规范化页下标；越界回退 clamp。 */
    private int normalizePageIndex(Integer requested) {
        int count = pages.size();
        if (count <= 0) {
            return -1;
        }
        int raw = requested == null ? 0 : requested.intValue();
        return Math.max(0, Math.min(count - 1, raw));
    }

    /** 导航请求回调：与当前页不同才切换（signal-first，不直接改树）。 */
    private void requestPageTransition(Integer requested) {
        int target = normalizePageIndex(requested);
        if (target < 0 || target == displayedPageIndex) {
            return;
        }
        switchPage(target);
    }

    /** 在 content 上挂载指定页；返回其根节点。 */
    private SceneNode mountPage(int index) {
        PlaygroundPage page = pages.get(index);
        pageMount = runtime.mount(content, page.build(runtime));
        displayedPageIndex = index;
        return pageMount == null ? null : pageMount.getRoot();
    }

    /** 单槽切换：先完整回收旧页 Owner（bind/effect/on 全部退订），再挂载新页。 */
    private SceneNode switchPage(int index) {
        return switchPage(index, true);
    }

    /**
     * 单槽替换：先完整回收旧页 Owner，再挂载新页；任一时刻至多一个 live 页。
     *
     * @param index       目标页下标
     * @param resetScroll 是否把视口滚动归零（导航切页为 true；主动刷新为 false，保留阅读位置）
     */
    private SceneNode switchPage(int index, boolean resetScroll) {
        if (pageMount != null) {
            pageMount.dispose();
            pageMount = null;
        }
        if (resetScroll) {
            viewport.setScrollOffsetY(0);
        }
        SceneNode incoming = mountPage(index);
        // 两种路径都必须重对账：outgoing 子树的 hoveredNode 已随卸载失效。
        runtime.__requestHoverReconcileAfterScroll();
        return incoming;
    }

    /**
     * 主动刷新当前页（F5 语义）：丢弃当前 live 页实例，重新执行页工厂，建立全新实例。
     *
     * <p>刷新与导航切页共用同一条 {@code dispose → mount} 单槽路径，区别只在触发源与滚动策略：
     * 导航由 {@link #activePageSignal} 驱动并归零滚动，刷新由宿主/测试显式请求且保留阅读位置
     * （对齐浏览器 F5）。位置保持来自 content 是 viewport 的<b>独占内容容器</b>——
     * 不引入占位锚点，因此不改变视口与兄弟节点的几何；任意父容器下的原位重挂不在本方法范围内。</p>
     *
     * <h4>状态契约（必须写死，否则用户会误判为 bug）</h4>
     * <ul>
     *   <li><b>重建</b>：页工厂重新执行一次，产出全新根节点；旧 live 树的节点、effect、
     *       输入 handler、浮层、动画全部随页 Owner 卸载回收。</li>
     *   <li><b>不保留</b>：页工厂内部的 Signal 与局部状态、页内输入控件的 caret / 选区 /
     *       撤销历史、页内焦点。这与浏览器 F5 一致（未持久化的运行态丢弃）。</li>
     *   <li><b>保留</b>：声明在刷新作用域之外的业务状态（例如注册表/宿主持有的跨页状态），
     *       以及本宿主的滚动位置。</li>
     *   <li><b>异常</b>：页工厂抛异常时挂载子作用域已被回收，异常原样传播，
     *       content 不会留下半挂载节点。</li>
     *   <li><b>与同页导航区分</b>：同页导航仍是 no-op（不重建），只有本方法才重建。</li>
     * </ul>
     *
     * @return 新页根节点；当前无 live 页时返回 {@code null}
     */
    public SceneNode refreshPage() {
        if (displayedPageIndex < 0) {
            return null;
        }
        return switchPage(displayedPageIndex, false);
    }

    // ==================== 基类实现 ====================

    @Override
    protected SceneNode getRoot() {
        return root;
    }

    // ==================== 测试探针访问器（包级） ====================

    /** @return 内部场景运行时 */
    SceneRuntime __getRuntime() {
        return runtime;
    }

    /** @return 场景树根节点 */
    SceneNode __getRoot() {
        return root;
    }

    /** @return 顶栏节点 */
    SceneNode __getHeader() {
        return header;
    }

    /** @return 导航条节点 */
    SceneNode __getNavBar() {
        return navBar;
    }

    /**
     * @return runtime 默认主题信号（外壳与页面内容的共同来源）；测试可
     *     {@code set(另一主题) + flush()} 验证外壳随主题更新而不重建节点
     */
    Signal<SceneTheme> __getThemeSignal() {
        return themeSignal;
    }

    /**
     * @return 受控导航源（R8 signal-first 唯一切页通道——SceneSegmented.onSelect 写的就是它；
     *     测试可 {@code set(index) + flush()} 确定性切页，不依赖点击命中坐标，防窄画布末段
     *     出界静默 miss——C9·7 #7 存活路径之一）
     */
    Signal<Integer> __getActivePageSignal() {
        return activePageSignal;
    }

    /** @return 滚动视口节点 */
    SceneNode __getViewport() {
        return viewport;
    }

    /** @return 视口内容容器（当前页挂载点） */
    SceneNode __getContent() {
        return content;
    }

    /** @return 页面清单（构建期快照） */
    List<PlaygroundPage> __getPages() {
        return pages;
    }

    /** @return 当前 live 页面下标；无页面时为 -1 */
    int __getDisplayedPageIndex() {
        return displayedPageIndex;
    }

    /** @return 当前 live 页面根节点；无页面时为 null */
    SceneNode __getDisplayedPageRoot() {
        return pageMount == null ? null : pageMount.getRoot();
    }

    /** @return 当前 live 页面 id；无页面时为 null */
    String __getDisplayedPageId() {
        if (displayedPageIndex < 0 || displayedPageIndex >= pages.size()) {
            return null;
        }
        return pages.get(displayedPageIndex).id();
    }
}
