package club.heiqi.uilib.ui.scene.control;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.LayoutResult;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.paint.SceneStateColors;

/**
 * SceneBreadcrumb 端到端单元测试 —— Phase 4 批 2 纯展示 + 回调控件验收。
 *
 * <p>构造 SceneRuntime + SceneLayoutEngine + ScenePaintEngine 三件套，端到端验证：
 * 点击回调（点 segBtn 上抛该段 path）、命中穿透（点 label 装饰子节点穿透到所属 segBtn）、
 * 交互态切换零重排、键盘激活（Enter/Space）+ disabled 拦截。</p>
 *
 * <h3>root children 序列（separator 仅非首段）</h3>
 * <pre>
 *   [segBtn0, sep1, segBtn1, sep2, segBtn2]
 * </pre>
 */
public class SceneBreadcrumbTest {

    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    private ScenePaintEngine paintEngine;

    private Signal<Boolean> enabledSignal;
    /** onSelect 触发计数器 */
    private AtomicInteger selectCount;
    /** onSelect 最近一次收到的 path */
    private String lastSelectPath;

    private MountHandle handle;
    /** breadcrumb 根节点 */
    private SceneNode crumbRoot;

    private static final int CANVAS_WIDTH = 400;
    private static final int CANVAS_HEIGHT = 100;
    private static final int STUB_CHAR_WIDTH = 8;

    // SceneBreadcrumb 段背景期望（link 变体，走 SceneStateColors.linkBackground）
    private static final int SEGBTN_DEFAULT = 0;
    private static final int SEGBTN_HOVER = SceneChromeTokens.BG_HOVER;
    private static final int SEGBTN_PRESSED = SceneChromeTokens.BG_PRESSED;
    // focused 不再加背景（避免与 linkText focused 同色导致文本消失），focus 指示靠文本色提亮
    private static final int SEGBTN_FOCUSED = 0;

    private static final List<SceneBreadcrumb.Segment> SEGMENTS = Arrays.asList(
            new SceneBreadcrumb.Segment("/", "Home"),
            new SceneBreadcrumb.Segment("/docs", "Docs"),
            new SceneBreadcrumb.Segment("/docs/api", "API"));

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        runtime = new SceneRuntime();
        FixedTextMeasurer measurer = new FixedTextMeasurer(STUB_CHAR_WIDTH, 16);
        layoutEngine = new SceneLayoutEngine(measurer);
        paintEngine = new ScenePaintEngine(measurer);
        sceneRoot = new SceneNode();

        enabledSignal = Signal.create(Boolean.TRUE);
        selectCount = new AtomicInteger(0);
        lastSelectPath = null;

        SceneBreadcrumb.Props props = new SceneBreadcrumb.Props(
                SEGMENTS, enabledSignal,
                path -> {
                    selectCount.incrementAndGet();
                    lastSelectPath = path;
                });
        handle = runtime.mount(sceneRoot, SceneBreadcrumb.create(runtime, props));
        crumbRoot = handle.getRoot();

        runtime.flush();
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== 辅助方法 ====================

    private LayoutResult doLayout() {
        return layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
    }

    /**
     * segBtn[i] 节点。root children 序列为 [segBtn0, sep1, segBtn1, sep2, segBtn2]，
     * segBtn[0] 在索引 0，其余 segBtn[i] 在索引 2*i-1+1 = 2*i（首段无 separator）。
     *
     * @param i 段下标
     * @return segBtn[i] 节点
     */
    private SceneNode segBtnNode(int i) {
        // i=0 → 索引 0；i>=1 → 索引 2*i（前面有 i 个 segBtn + i 个 sep... 实际：每段贡献 1 segBtn，每非首段额外 1 sep）
        // 序列：idx0=segBtn0, idx1=sep1, idx2=segBtn1, idx3=sep2, idx4=segBtn2 → segBtn[i] 在 2*i
        return crumbRoot.__getChildren().get(2 * i);
    }

    /** segBtn[i] 的 label 子节点（segBtn 第一个孩子） */
    private SceneNode labelNode(int i) {
        return segBtnNode(i).__getChildren().get(0);
    }

    private LayoutBox box(SceneNode n) {
        return (LayoutBox) n.getCachedLayout();
    }

    /**
     * 计算节点几何中心的画布绝对坐标。
     *
     * <p>{@link LayoutBox#getX()}/{@code getY()} 是相对父的局部坐标，深层装饰子节点（label）
     * 需沿 {@code __getParent} 链累加各级局部偏移才能得到供 route 的画布绝对坐标。</p>
     *
     * @param n 目标节点
     * @return 长度 2 数组 {绝对中心 X, 绝对中心 Y}
     */
    private int[] absCenter(SceneNode n) {
        LayoutBox b = box(n);
        int ax = b.getX();
        int ay = b.getY();
        SceneNode p = n.__getParent();
        while (p != null) {
            LayoutBox pb = (LayoutBox) p.getCachedLayout();
            if (pb != null) {
                ax += pb.getX();
                ay += pb.getY();
            }
            p = p.__getParent();
        }
        return new int[] { ax + b.getWidth() / 2, ay + b.getHeight() / 2 };
    }

    private void routePointer(ScenePointerAction action, int x, int y) {
        InputFrameBuilder fb = new InputFrameBuilder(x, y);
        fb.push(RawInputEvent.ofPointer(action, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        SceneInputFrame f = fb.drainFrame();
        runtime.route(sceneRoot, f, 0, 0);
    }

    private void routeKey(SceneKey key, SceneKeyAction action) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofKey(key, action, false, false, false, false, 0, 0, 1000L));
        SceneInputFrame f = fb.drainFrame();
        runtime.route(sceneRoot, f, 0, 0);
    }

    /** 点击指定节点几何中心（DOWN+UP 合成 CLICK，用画布绝对坐标） */
    private void clickCenter(SceneNode n) {
        int[] c = absCenter(n);
        routePointer(ScenePointerAction.BUTTON_DOWN, c[0], c[1]);
        routePointer(ScenePointerAction.BUTTON_UP, c[0], c[1]);
    }

    // ==================== 验收 1：点击回调（点 segBtn[2] 上抛对应 path） ====================

    /**
     * 点 segBtn[2] 几何中心 → onSelect 收到对应 path "/docs/api"。
     */
    @Test
    public void clickSegmentShouldRaiseOnSelectWithPath() {
        doLayout();
        clickCenter(segBtnNode(2));
        runtime.flush();
        Assert.assertEquals("点 segBtn[2] 应触发一次 onSelect", 1, selectCount.get());
        Assert.assertEquals("onSelect 应收到对应 path", "/docs/api", lastSelectPath);

        // 再点 segBtn[0] → path "/"
        clickCenter(segBtnNode(0));
        runtime.flush();
        Assert.assertEquals("点 segBtn[0] 累计两次 onSelect", 2, selectCount.get());
        Assert.assertEquals("onSelect 应收到首段 path", "/", lastSelectPath);
    }

    // ==================== 验收 2：命中穿透（点 label 装饰子节点穿透到 segBtn） ====================

    /**
     * 命中穿透：点 label[1] 几何中心，穿透到 segBtn[1]，segBtn[1] 进 pressed 背景。
     */
    @Test
    public void hitTestShouldPassThroughDecorativeLabelToSegBtn() {
        doLayout();
        int[] c = absCenter(labelNode(1));
        int cx = c[0];
        int cy = c[1];

        // 按下 label[1] 中心 → 穿透到 segBtn[1] → pressed 背景
        routePointer(ScenePointerAction.BUTTON_DOWN, cx, cy);
        runtime.flush();
        doLayout();
        Assert.assertEquals("点 label[1] 穿透到 segBtn[1] → pressed 背景",
                SEGBTN_PRESSED, segBtnNode(1).getBackgroundColor());

        // 释放 → 合成 CLICK → onSelect 收到 segBtn[1] path
        routePointer(ScenePointerAction.BUTTON_UP, cx, cy);
        runtime.flush();
        Assert.assertEquals("点 label[1] 释放应合成 CLICK 触发 onSelect", 1, selectCount.get());
        Assert.assertEquals("期望 path /docs", "/docs", lastSelectPath);
    }

    // ==================== 验收 3：交互态切换零重排（终极反证 R-D） ====================

    /**
     * hover/pressed 切换帧 {@code result.getRelayoutCount()==0}——交互态没被误做成布局级的终极证明。
     */
    @Test
    public void interactionStateSwitchShouldOnlyPaintNotLayout() {
        LayoutResult result = doLayout();
        Assert.assertEquals("初始 segBtn[1] 透明背景", SEGBTN_DEFAULT, segBtnNode(1).getBackgroundColor());

        int[] c = absCenter(segBtnNode(1));
        int cx = c[0];
        int cy = c[1];

        // ① hover 进 → hover 背景，零重排
        routePointer(ScenePointerAction.MOVE, cx, cy);
        runtime.flush();
        result = doLayout();
        Assert.assertEquals("hover segBtn[1] 背景", SEGBTN_HOVER, segBtnNode(1).getBackgroundColor());
        Assert.assertEquals("R-D: hover 进零重排", 0, result.getRelayoutCount());

        // ② pressed → pressed 背景，零重排
        routePointer(ScenePointerAction.BUTTON_DOWN, cx, cy);
        runtime.flush();
        result = doLayout();
        Assert.assertEquals("pressed segBtn[1] 背景", SEGBTN_PRESSED, segBtnNode(1).getBackgroundColor());
        Assert.assertEquals("R-D: pressed 零重排", 0, result.getRelayoutCount());

        // ③ 释放 pressed：回 hover 背景（指针仍在内），零重排
        routePointer(ScenePointerAction.BUTTON_UP, cx, cy);
        runtime.flush();
        result = doLayout();
        Assert.assertEquals("释放后回 hover 背景", SEGBTN_HOVER, segBtnNode(1).getBackgroundColor());
        Assert.assertEquals("R-D: 释放 pressed 零重排", 0, result.getRelayoutCount());

        // ④ hover 出 → 指针离开 segBtn[1]，但 DOWN 时已隐式 focus，focus 保持文本提亮（背景透明），零重排
        routePointer(ScenePointerAction.MOVE, CANVAS_WIDTH - 1, CANVAS_HEIGHT - 1);
        runtime.flush();
        result = doLayout();
        Assert.assertEquals("hover 出后 focus 背景保持透明",
                SEGBTN_FOCUSED, segBtnNode(1).getBackgroundColor());
        Assert.assertEquals("hover 出后 focus 文本保持 ACCENT_HOVER 提亮",
                SceneChromeTokens.ACCENT_HOVER, labelNode(1).getTextColor());
        Assert.assertEquals("R-D: hover 出零重排", 0, result.getRelayoutCount());
    }

    /**
     * Breadcrumb 段按钮宽度由真实文本测量驱动，不再使用字符数估算宽度。
     */
    @Test
    public void segmentWidthShouldUseMeasuredTextWidth() {
        doLayout();

        Assert.assertEquals("Home 段宽=4*8+左右 padding12",
                44, box(segBtnNode(0)).getWidth());
        Assert.assertEquals("Docs 段宽=4*8+左右 padding12",
                44, box(segBtnNode(1)).getWidth());
        Assert.assertEquals("API 段宽=3*8+左右 padding12",
                36, box(segBtnNode(2)).getWidth());
    }

    // ==================== 验收 3.5：focus 视觉态（link 变体淡蓝高亮） ====================

    /**
     * focus segBtn[1] → 背景保持透明（focus 不再加背景，避免与文本同色）、
     * 文本切 ACCENT_HOVER 提亮作 focus 指示；失焦后回透明背景 + ACCENT 文本。零重排。
     */
    @Test
    public void focusStateShouldHighlightLinkSegment() {
        LayoutResult result = doLayout();
        Assert.assertEquals("初始 segBtn[1] 透明背景", SEGBTN_DEFAULT, segBtnNode(1).getBackgroundColor());
        Assert.assertEquals("初始 segBtn[1] 文本 ACCENT",
                SceneChromeTokens.ACCENT, labelNode(1).getTextColor());

        runtime.requestFocus(segBtnNode(1));
        runtime.flush();
        result = doLayout();
        Assert.assertEquals("focused segBtn[1] 背景保持透明（不加背景）",
                SEGBTN_FOCUSED, segBtnNode(1).getBackgroundColor());
        Assert.assertEquals("focused segBtn[1] 文本 ACCENT_HOVER",
                SceneChromeTokens.ACCENT_HOVER, labelNode(1).getTextColor());
        Assert.assertEquals("R-D: focus 零重排", 0, result.getRelayoutCount());

        // 焦点切到 segBtn[0] → segBtn[1] 失焦回透明背景 + ACCENT 文本
        runtime.requestFocus(segBtnNode(0));
        runtime.flush();
        result = doLayout();
        Assert.assertEquals("失焦后 segBtn[1] 回透明背景",
                SEGBTN_DEFAULT, segBtnNode(1).getBackgroundColor());
        Assert.assertEquals("失焦后 segBtn[1] 回 ACCENT 文本",
                SceneChromeTokens.ACCENT, labelNode(1).getTextColor());
    }

    // ==================== 验收 4：键盘激活（Enter/Space），disabled 拦截 ====================
    /**
     * Enter/Space 键盘激活调 onSelect 上抛聚焦段 path；disabled 态键盘/点击均不触发。
     */
    @Test
    public void keyboardActivationRaisesOnSelectAndDisabledBlocks() {
        doLayout();
        runtime.requestFocus(segBtnNode(1));

        // ① Enter 激活 → 上抛 segBtn[1] path
        int before = selectCount.get();
        routeKey(SceneKey.ENTER, SceneKeyAction.PRESSED);
        runtime.flush();
        Assert.assertEquals("Enter 应触发一次 onSelect", before + 1, selectCount.get());
        Assert.assertEquals("Enter 期望 path /docs", "/docs", lastSelectPath);

        // ② Space 激活
        before = selectCount.get();
        routeKey(SceneKey.SPACE, SceneKeyAction.PRESSED);
        runtime.flush();
        Assert.assertEquals("Space 应触发一次 onSelect", before + 1, selectCount.get());

        // ③ disabled 态：Enter / CLICK 均不触发
        enabledSignal.set(Boolean.FALSE);
        runtime.flush();
        before = selectCount.get();
        routeKey(SceneKey.ENTER, SceneKeyAction.PRESSED);
        runtime.flush();
        Assert.assertEquals("disabled 态 Enter 不触发", before, selectCount.get());

        doLayout();
        clickCenter(segBtnNode(1));
        runtime.flush();
        Assert.assertEquals("disabled 态 CLICK 不触发", before, selectCount.get());
    }
}
