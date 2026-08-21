package club.heiqi.uilib.internal.devtools.playground;

import java.util.ArrayList;
import java.util.List;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneScrollbar;
import club.heiqi.uilib.ui.scene.control.SceneSegmented;
import club.heiqi.uilib.ui.scene.host.AbstractSceneHostWidget;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;

/**
 * 测试场地 scene 宿主 —— 顶栏 + 分段导航 + 单槽演示页 + 滚动视口。
 *
 * <p>树结构（COLUMN 居中，内容受最大宽约束）：</p>
 * <pre>
 * root (COLUMN, fillParent, crossAxisAlign=CENTER, padding, gap, bg=ROOT_BG)
 *   ├ header                自建标题条（22px 主标题 + 12px 副标题）
 *   ├ navBar                SceneSegmented 分段导航（页清单单选项，受控 selectedIndex）
 *   └ scrollContainer (ROW, fillParentHeight, gap)
 *       ├ viewport (COLUMN, fillParentHeight, flexGrow=1, scrollable, clip) ← 页面内容单槽
 *       │   └ content (COLUMN) ← 当前页根挂载点
 *       └ scrollbarColumn        SceneScrollbar（反映 viewport 滚动）
 * </pre>
 *
 * <h3>页面切换守不变量</h3>
 * <ul>
 *   <li>导航请求经 {@link #activePageSignal} 受控源（R8：控件不自己改 selectedIndex，
 *       宿主在 onSelect 写回 signal；signal-first，handler 不直接改树）。</li>
 *   <li>单槽切换：先 dispose 旧 mount 句柄（回收旧页 Owner 内全部 bind/effect/on），
 *       再 mount 新页；任一时刻至多一个 live 页面。</li>
 *   <li>页切换后重置 viewport 滚动到顶部，并请求 hover 重对账。</li>
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

    /** 页面清单（构建期快照，不可变）。 */
    private final List<PlaygroundPage> pages;
    /** 受控导航源：当前页下标（0 起）。 */
    private final Signal<Integer> activePageSignal;

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
        runtime.__enableMotion();
        buildShell();
        runtime.bind(activePageSignal, this::requestPageTransition);
        mountPage(0);
        runtime.flush();
    }

    // ==================== 骨架构建 ====================

    private void buildShell() {
        root = SceneNode.column();
        root.setFillParentWidth(true);
        root.setFillParentHeight(true);
        root.setCrossAxisAlign(CrossAxisAlign.CENTER);
        root.setPadding(ROOT_PADDING);
        root.setGap(ROOT_GAP);
        root.setBackgroundColor(PlaygroundKit.ROOT_BG);

        header = SceneNode.column();
        header.setFillParentWidth(true);
        header.setMaxWidth(CONTENT_MAX_WIDTH);
        header.setGap(2);
        header.setHitTestable(false);
        header.appendChild(PlaygroundKit.text("Qz UILib 测试场地", PlaygroundKit.TEXT, 22));
        header.appendChild(PlaygroundKit.text("内部开发调试入口 · 输入命令 /qzuilib test 打开", PlaygroundKit.MUTED,
                SUBTITLE_FONT_SIZE));
        // 固定兄弟高度先验：root（COLUMN）的 grow 求解器要求固定兄弟可先验，容器型兄弟
        // 不设 preferredHeight 会 UNCONSTRAINED 早退 → viewport 高度解耦失败、maxScrollY 恒 0
        // （真机「只能看到样式继承、无法滚动」根因）。header = 标题行高 + gap + 副标题行高。
        header.setPreferredHeight(measurer.lineHeight(22) + header.getGap() + measurer.lineHeight(SUBTITLE_FONT_SIZE));
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
        viewport.setBackgroundColor(PlaygroundKit.PANEL_BG);
        viewport.setCornerRadius(SceneChromeTokens.RADIUS_LG);
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
        if (pageMount != null) {
            pageMount.dispose();
            pageMount = null;
        }
        viewport.setScrollOffsetY(0);
        SceneNode incoming = mountPage(index);
        runtime.__requestHoverReconcileAfterScroll();
        return incoming;
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
