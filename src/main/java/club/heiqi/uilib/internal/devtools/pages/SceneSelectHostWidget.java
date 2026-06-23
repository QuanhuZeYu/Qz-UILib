package club.heiqi.uilib.internal.devtools.pages;

import java.util.Arrays;
import java.util.List;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.scene.UiSurface;
import club.heiqi.uilib.ui.scene.component.MountHandle;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.control.SceneSelect;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.Invalidation;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.paint.ScenePaintReplayer;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextMeasureServiceSceneAdapter;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 新栈 ui.scene Select demo 宿主 Widget。
 *
 * <p>本页集中验证 {@link SceneSelect} 的受控选中值、top-layer 下拉浮层、长列表滚动、
 * disabled 状态和多 Select 独立展开/关闭行为；无字符输入需求，因此不接入文本旁路桥。</p>
 */
public class SceneSelectHostWidget extends Widget implements UiSurface {

    private static final int ROOT_BG = 0xFF0B1424;
    private static final int VIEWPORT_BG = 0xFF081120;
    private static final int CARD_BG = 0xFF0D1728;
    private static final int CARD_BORDER = 0xFF2F4D87;
    private static final int TITLE_COLOR = 0xFFC9D8F8;
    private static final int TEXT_COLOR = 0xFFEAF1FF;
    private static final int MUTED_COLOR = 0xFF8AA0C8;
    private static final int TITLE_BAR_HEIGHT = 44;
    private static final int SELECT_WIDTH = 180;
    private static final int SELECT_HEIGHT = 32;

    private final SceneRuntime runtime;
    private final SceneLayoutEngine layoutEngine;
    private final ScenePaintEngine paintEngine;
    private final ScenePaintReplayer replayer;
    private final SceneNode root;
    private final SceneNode viewport;
    private final SceneNode content;
    private final Signal<Integer> scrollSignal;
    private final PlatformInputSource inputSource;

    private final Signal<Integer> basicIndex;
    private final Signal<Integer> longIndex;
    private final Signal<Integer> disabledIndex;
    private final Signal<Integer> leftIndex;
    private final Signal<Integer> rightIndex;
    private final Signal<Boolean> enabled;
    private final Signal<Boolean> disabled;

    /**
     * 创建 Select demo 宿主 Widget，注入平台输入源。
     *
     * @param inputSource 平台输入源，可为 null（退化模式，无真机滚轮）
     */
    public SceneSelectHostWidget(PlatformInputSource inputSource) {
        this.inputSource = inputSource;
        SceneTextMeasurer measurer = new TextMeasureServiceSceneAdapter(DefaultTextMeasureService.getInstance());
        this.runtime = new SceneRuntime(measurer);
        this.layoutEngine = new SceneLayoutEngine(measurer);
        this.paintEngine = new ScenePaintEngine();
        this.replayer = new ScenePaintReplayer();

        this.basicIndex = Signal.create(Integer.valueOf(0));
        this.longIndex = Signal.create(Integer.valueOf(0));
        this.disabledIndex = Signal.create(Integer.valueOf(1));
        this.leftIndex = Signal.create(Integer.valueOf(0));
        this.rightIndex = Signal.create(Integer.valueOf(2));
        this.enabled = Signal.create(Boolean.TRUE);
        this.disabled = Signal.create(Boolean.FALSE);

        this.root = createRoot();
        root.appendChild(createTitleBar());
        this.viewport = createViewport();
        this.content = createContent();
        viewport.appendChild(content);
        root.appendChild(viewport);

        content.appendChild(createSelectCard("基础 Select", "点击展开，选择水果后由外部 signal 写回。",
                basicIndex, Arrays.asList("苹果", "香蕉", "橙子", "葡萄"), enabled));
        content.appendChild(createSelectCard("长列表 Select", "12 个选项用于验证 listbox 限高、滚轮滚动和 anchor 定位。",
                longIndex, createLongOptions(), enabled));
        content.appendChild(createSelectCard("Disabled Select", "禁用态应保留当前值但拒绝点击、键盘展开和 hover 指针。",
                disabledIndex, Arrays.asList("只读 A", "只读 B", "只读 C"), disabled));
        content.appendChild(createDualSelectCard());

        this.scrollSignal = Signal.create(Integer.valueOf(0));
        runtime.bind(Invalidation.COMPOSITE, scrollSignal, v -> viewport.setScrollOffsetY(v.intValue()));
        runtime.on(viewport, SceneEventType.SCROLL, (ev, ctx) -> {
            LayoutBox viewportBox = (LayoutBox) viewport.getCachedLayout();
            LayoutBox contentBox = (LayoutBox) content.getCachedLayout();
            if (viewportBox == null || contentBox == null) {
                return;
            }
            int maxScroll = Math.max(0, contentBox.getHeight() - viewportBox.getHeight());
            int next = Math.max(0, Math.min(maxScroll, scrollSignal.get().intValue() - ev.getWheelDelta()));
            scrollSignal.set(Integer.valueOf(next));
        });

        if (inputSource instanceof LwjglInputSource) {
            runtime.bindCursor(new LwjglCursorBackend());
        }
        runtime.flush();
    }

    /**
     * 创建根容器。
     *
     * @return 根节点
     */
    private SceneNode createRoot() {
        SceneNode node = new SceneNode();
        node.setFillParentHeight(true);
        node.setFlexDirection(FlexDirection.COLUMN);
        node.setPadding(20);
        node.setGap(12);
        node.setBackgroundColor(ROOT_BG);
        return node;
    }

    /**
     * 创建固定标题条。
     *
     * @return 标题条节点
     */
    private SceneNode createTitleBar() {
        SceneNode titleBar = new SceneNode();
        titleBar.setFlexDirection(FlexDirection.COLUMN);
        titleBar.setPreferredHeight(TITLE_BAR_HEIGHT);
        titleBar.setGap(4);
        titleBar.setHitTestable(false);
        titleBar.appendChild(text("Scene Select demo", TITLE_COLOR));
        titleBar.appendChild(text("top-layer 下拉 · anchor 定位 · 外部点击/ESC 关闭 · 键盘导航", MUTED_COLOR));
        return titleBar;
    }

    /**
     * 创建滚动视口。
     *
     * @return 视口节点
     */
    private SceneNode createViewport() {
        SceneNode node = new SceneNode();
        node.setFlexDirection(FlexDirection.COLUMN);
        node.setFillParentHeight(true);
        node.setScrollable(true);
        node.setClipChildren(true);
        node.setPadding(14);
        node.setGap(14);
        node.setBackgroundColor(VIEWPORT_BG);
        node.setCornerRadius(10);
        return node;
    }

    /**
     * 创建视口内容容器。
     *
     * @return 内容节点
     */
    private SceneNode createContent() {
        SceneNode node = new SceneNode();
        node.setFlexDirection(FlexDirection.COLUMN);
        node.setGap(14);
        return node;
    }

    /**
     * 创建单 Select 卡片。
     *
     * @param title 卡片标题
     * @param helper 帮助说明
     * @param selectedIndex 选中下标 signal
     * @param options 选项列表
     * @param enabledSignal 是否启用
     * @return 卡片节点
     */
    private SceneNode createSelectCard(String title, String helper, Signal<Integer> selectedIndex,
            List<String> options, ReadableSignal<Boolean> enabledSignal) {
        SceneNode card = createCardShell(title, helper);
        mountSelect(card, selectedIndex, options, enabledSignal);
        return card;
    }

    /**
     * 创建并排 Select 卡片。
     *
     * @return 卡片节点
     */
    private SceneNode createDualSelectCard() {
        SceneNode card = createCardShell("两个 Select 并排", "分别展开左右 Select，验证浮层互斥关闭与选中值互不干扰。");
        SceneNode row = new SceneNode();
        row.setFlexDirection(FlexDirection.ROW);
        row.setGap(12);
        mountSelect(row, leftIndex, Arrays.asList("左 A", "左 B", "左 C"), enabled);
        mountSelect(row, rightIndex, Arrays.asList("右 A", "右 B", "右 C", "右 D"), enabled);
        card.appendChild(row);
        return card;
    }

    /**
     * 创建卡片外壳。
     *
     * @param title 卡片标题
     * @param helper 帮助说明
     * @return 卡片节点
     */
    private SceneNode createCardShell(String title, String helper) {
        SceneNode card = new SceneNode();
        card.setFlexDirection(FlexDirection.COLUMN);
        card.setBackgroundColor(CARD_BG);
        card.setBorderWidth(1);
        card.setBorderColor(CARD_BORDER);
        card.setCornerRadius(10);
        card.setPadding(12);
        card.setGap(8);
        card.appendChild(text(title, TEXT_COLOR));
        card.appendChild(text(helper, MUTED_COLOR));
        return card;
    }

    /**
     * 挂载 Select 控件。
     *
     * @param parent 父节点
     * @param selectedIndex 选中下标 signal
     * @param options 选项列表
     * @param enabledSignal 是否启用
     */
    private void mountSelect(SceneNode parent, Signal<Integer> selectedIndex, List<String> options,
            ReadableSignal<Boolean> enabledSignal) {
        SceneSelect.Props props = new SceneSelect.Props(selectedIndex, options, enabledSignal, selectedIndex::set);
        MountHandle handle = runtime.mount(parent, SceneSelect.create(runtime, props));
        handle.getRoot().setPreferredWidth(SELECT_WIDTH);
        handle.getRoot().setPreferredHeight(SELECT_HEIGHT);
    }

    /**
     * 创建长列表选项。
     *
     * @return 长列表选项
     */
    private static List<String> createLongOptions() {
        return Arrays.asList("项目1", "项目2", "项目3", "项目4", "项目5", "项目6",
                "项目7", "项目8", "项目9", "项目10", "项目11", "项目12");
    }

    /**
     * 创建文字节点。
     *
     * @param value 文本
     * @param color 颜色
     * @return 文字节点
     */
    private SceneNode text(String value, int color) {
        SceneNode node = new SceneNode();
        node.setText(value);
        node.setTextColor(color);
        node.setHitTestable(false);
        return node;
    }

    @Override
    protected void drawSelf(UiRenderContext ctx) {
        render(getWidth(), getHeight(), ctx, getAbsoluteX(), getAbsoluteY());
    }

    /**
     * 驱动完整 scene Select demo pipeline。
     *
     * @param w 宿主宽度
     * @param h 宿主高度
     * @param ctx 渲染出口
     * @param absX 宿主绝对 X 偏移
     * @param absY 宿主绝对 Y 偏移
     */
    @Override
    public void render(int w, int h, UiRenderBackend ctx, int absX, int absY) {
        w = Math.max(0, w);
        h = Math.max(0, h);
        SceneInputFrame frame = inputSource != null ? inputSource.drainFrame() : SceneInputFrame.EMPTY;
        layoutEngine.layout(root, new Constraints(w, h));
        if (!frame.isEmpty()) {
            runtime.route(root, frame, absX, absY);
        }
        runtime.flush();
        layoutEngine.layout(root, new Constraints(w, h));
        PaintPlan plan = paintEngine.paint(root);
        replayer.replay(plan, ctx, absX, absY);
    }

    /**
     * 宿主键盘事件转发入口。
     *
     * @param typedChar 输入字符
     * @param keyCode 原生键码
     */
    @Override
    public void onKeyTyped(char typedChar, int keyCode) {
        if (inputSource instanceof LwjglInputSource) {
            ((LwjglInputSource) inputSource).pushKeyTyped(typedChar, keyCode, System.nanoTime());
        }
    }

    /**
     * Select demo 无文本输入旁路需求，保留 UiSurface 契约入口。
     *
     * @param text 文本内容
     */
    @Override
    public void pushText(String text) {
    }

    /**
     * Select demo 无文本输入旁路需求，保留 UiSurface 契约入口。
     *
     * @param external true 表示外部文本事件接管输入
     */
    @Override
    public void setExternalTextMode(boolean external) {
    }

    /** 释放 runtime 资源。 */
    @Override
    public void dispose() {
        runtime.dispose();
    }
}
