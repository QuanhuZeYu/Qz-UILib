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
import club.heiqi.uilib.ui.scene.component.MountHandle;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
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

/**
 * SceneSegmented 端到端单元测试 —— Phase 4 批 2 分段单选受控控件（R8）验收。
 *
 * <p>端到端验证：受控闭环（点段只上抛期望下标、控件零状态不自改）、
 * R6 段穿透权威验证（点段内 label 文字穿透到所属段进 pressed）、四态切换零重排、
 * 键盘激活（Enter/Space）+ disabled 拦截、方向键导航（←/→ + 焦点移动）。</p>
 */
public class SceneSegmentedTest {

    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    private ScenePaintEngine paintEngine;

    private Signal<Integer> selectedSignal;
    private Signal<Boolean> enabledSignal;
    private AtomicInteger selectCount;
    private Integer lastSelectValue;

    private MountHandle handle;
    private SceneNode segRoot;

    private static final int CANVAS_WIDTH = 300;
    private static final int CANVAS_HEIGHT = 100;
    private static final int STUB_CHAR_WIDTH = 8;

    // SceneSegmented chrome token 镜像
    private static final int SEG_UNSEL_ENABLED = SceneChromeTokens.BG_DEFAULT;
    private static final int SEG_UNSEL_PRESSED = SceneChromeTokens.BG_PRESSED;
    private static final int SEG_SEL_ENABLED = SceneChromeTokens.ACCENT;
    private static final int SEG_SEL_PRESSED = SceneChromeTokens.ACCENT_PRESSED;
    private static final int SEG_DISABLED = SceneChromeTokens.BG_DISABLED;

    private static final List<String> OPTIONS = Arrays.asList("Day", "Week", "Month");

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(STUB_CHAR_WIDTH, 16);
        runtime = new SceneRuntime(measurer);
        layoutEngine = new SceneLayoutEngine(measurer);
        paintEngine = new ScenePaintEngine(measurer);
        sceneRoot = new SceneNode();

        selectedSignal = Signal.create(Integer.valueOf(0));
        enabledSignal = Signal.create(Boolean.TRUE);
        selectCount = new AtomicInteger(0);
        lastSelectValue = null;

        SceneSegmented.Props props = new SceneSegmented.Props(
                selectedSignal, OPTIONS, enabledSignal,
                next -> {
                    selectCount.incrementAndGet();
                    lastSelectValue = next;
                });
        handle = runtime.mount(sceneRoot, SceneSegmented.create(runtime, props));
        segRoot = handle.getRoot();

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

    /** segment[i] 节点（root 第 i 个孩子） */
    private SceneNode segmentNode(int i) {
        return segRoot.__getChildren().get(i);
    }

    /** segment[i] 的 label 节点（segment 第一个孩子） */
    private SceneNode labelNode(int i) {
        return segmentNode(i).__getChildren().get(0);
    }

    private LayoutBox box(SceneNode n) {
        return (LayoutBox) n.getCachedLayout();
    }

    /**
     * 计算节点几何中心的画布绝对坐标。
     *
     * <p>{@link LayoutBox#getX()}/{@code getY()} 是相对父的局部坐标，段内 label 装饰子节点
     * 需沿 {@code __getParent} 链累加各级局部偏移才能得到供 route 的画布绝对坐标，
     * 否则点击坐标错位命不中目标。</p>
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

    private int segBackground(int i) {
        return segmentNode(i).getBackgroundColor();
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

    private void clickCenter(SceneNode n) {
        int[] c = absCenter(n);
        routePointer(ScenePointerAction.BUTTON_DOWN, c[0], c[1]);
        routePointer(ScenePointerAction.BUTTON_UP, c[0], c[1]);
    }

    // ==================== 验收 1：受控闭环 ====================

    /**
     * 受控核心：初始 selectedIndex=0，点 segment[1] → onSelect 收到期望下标 1，
     * 但 selectedIndex 仍 0（控件零状态不自改）；外部 set 1 → flush → segment[1] 切选中背景。
     */
    @Test
    public void controlledClickShouldRaiseOnSelectWithoutSelfMutate() {
        doLayout();
        Assert.assertEquals("初始 segment[0] 选中背景", SEG_SEL_ENABLED, segBackground(0));
        Assert.assertEquals("初始 segment[1] 未选中背景", SEG_UNSEL_ENABLED, segBackground(1));

        clickCenter(segmentNode(1));
        runtime.flush();

        Assert.assertEquals("CLICK 应触发一次 onSelect", 1, selectCount.get());
        Assert.assertEquals("onSelect 应收到期望下标 1", Integer.valueOf(1), lastSelectValue);

        Assert.assertEquals("受控：外部未回写时 selectedIndex 仍 0",
                Integer.valueOf(0), selectedSignal.get());
        doLayout();
        Assert.assertEquals("受控：segment[1] 视觉未自选中", SEG_UNSEL_ENABLED, segBackground(1));

        selectedSignal.set(Integer.valueOf(1));
        runtime.flush();
        doLayout();
        Assert.assertEquals("外部回写后 segment[1] 选中背景", SEG_SEL_ENABLED, segBackground(1));
        Assert.assertEquals("外部回写后 segment[0] 退选背景", SEG_UNSEL_ENABLED, segBackground(0));
    }

    // ==================== 验收 2：R6 段穿透权威验证（点段内 label 文字穿透到段） ====================

    /**
     * R6 权威落地：点 segment[1] 内 label 文字几何中心，命中穿透到 segment[1]，
     * segment[1] 进 pressed → 切 pressed 背景；释放合成 CLICK 上抛 1。
     */
    @Test
    public void hitTestShouldPassThroughSegmentLabelToSegment() {
        doLayout();
        int[] c = absCenter(labelNode(1));
        int cx = c[0];
        int cy = c[1];

        routePointer(ScenePointerAction.BUTTON_DOWN, cx, cy);
        runtime.flush();
        doLayout();
        Assert.assertEquals("点 label[1] 穿透到 segment[1] → pressed 背景",
                SEG_UNSEL_PRESSED, segBackground(1));

        routePointer(ScenePointerAction.BUTTON_UP, cx, cy);
        runtime.flush();
        Assert.assertEquals("点 label[1] 释放应合成 CLICK 触发 onSelect", 1, selectCount.get());
        Assert.assertEquals("期望下标 1", Integer.valueOf(1), lastSelectValue);
    }

    // ==================== 验收 3：四态切换零重排（终极反证 R-D） ====================

    @Test
    public void interactionStateSwitchShouldOnlyPaintNotLayout() {
        LayoutResult result = doLayout();
        Assert.assertEquals("初始 segment[1] 默认背景", SEG_UNSEL_ENABLED, segBackground(1));

        // ① enabled → disabled：切灰，零重排
        enabledSignal.set(Boolean.FALSE);
        runtime.flush();
        result = doLayout();
        Assert.assertEquals("disabled segment[1] 背景", SEG_DISABLED, segBackground(1));
        Assert.assertEquals("R-D: enabled→disabled 零重排", 0, result.getRelayoutCount());

        // ② disabled → enabled：回默认，零重排
        enabledSignal.set(Boolean.TRUE);
        runtime.flush();
        result = doLayout();
        Assert.assertEquals("回 enabled segment[1] 背景", SEG_UNSEL_ENABLED, segBackground(1));
        Assert.assertEquals("R-D: disabled→enabled 零重排", 0, result.getRelayoutCount());

        // ③ pressed：route 真实 POINTER_DOWN 命中 segment[1] 几何中心
        result = doLayout();
        int[] sc = absCenter(segmentNode(1));
        int cx = sc[0];
        int cy = sc[1];
        routePointer(ScenePointerAction.BUTTON_DOWN, cx, cy);
        runtime.flush();
        result = doLayout();
        Assert.assertEquals("pressed segment[1] 背景", SEG_UNSEL_PRESSED, segBackground(1));
        Assert.assertEquals("R-D: pressed 零重排", 0, result.getRelayoutCount());

        routePointer(ScenePointerAction.BUTTON_UP, cx, cy);
        runtime.flush();
        result = doLayout();
        Assert.assertEquals("释放后回默认背景", SEG_UNSEL_ENABLED, segBackground(1));
        Assert.assertEquals("R-D: 释放 pressed 零重排", 0, result.getRelayoutCount());

        // ④ 外部 set 选中切换：纯 PAINT 级零重排
        selectedSignal.set(Integer.valueOf(2));
        runtime.flush();
        result = doLayout();
        Assert.assertEquals("选中切到 2：segment[2] 选中背景", SEG_SEL_ENABLED, segBackground(2));
        Assert.assertEquals("R-D: 选中切换零重排", 0, result.getRelayoutCount());
    }

    // ==================== 验收 4：键盘激活 + disabled 拦截 ====================

    @Test
    public void keyboardActivationRaisesOnSelectAndDisabledBlocks() {
        doLayout();
        runtime.requestFocus(segmentNode(1));

        int before = selectCount.get();
        routeKey(SceneKey.ENTER, SceneKeyAction.PRESSED);
        runtime.flush();
        Assert.assertEquals("Enter 应触发一次 onSelect", before + 1, selectCount.get());
        Assert.assertEquals("Enter 期望下标 1", Integer.valueOf(1), lastSelectValue);

        before = selectCount.get();
        routeKey(SceneKey.SPACE, SceneKeyAction.PRESSED);
        runtime.flush();
        Assert.assertEquals("Space 应触发一次 onSelect", before + 1, selectCount.get());

        enabledSignal.set(Boolean.FALSE);
        runtime.flush();
        before = selectCount.get();
        routeKey(SceneKey.ENTER, SceneKeyAction.PRESSED);
        runtime.flush();
        Assert.assertEquals("disabled 态 Enter 不触发", before, selectCount.get());

        doLayout();
        clickCenter(segmentNode(1));
        runtime.flush();
        Assert.assertEquals("disabled 态 CLICK 不触发", before, selectCount.get());
    }

    // ==================== 验收 6：段宽按标题文本自适应 ====================

    /**
     * 段宽应按其标题文本宽度自适应：短标题段窄、长标题段宽，
     * 段宽 = 文本宽（每字符 STUB_CHAR_WIDTH）+ 2*SEGMENT_PADDING（PAD_LG=10）。
     *
     * <p>构建期一次性测量固化进 preferredWidth，不引入每段脏标记瀑布（守 I7）。</p>
     */
    @Test
    public void segmentWidthShouldAdaptToTitleText() {
        // OPTIONS = ["Day"(3), "Week"(4), "Month"(5)]，charWidth=8，PAD_LG=10
        int pad = SceneChromeTokens.PAD_LG;
        Assert.assertEquals("段[0] 'Day' 宽 = 3*8 + 2*10 = 44",
                3 * STUB_CHAR_WIDTH + 2 * pad, segmentNode(0).getPreferredWidth());
        Assert.assertEquals("段[1] 'Week' 宽 = 4*8 + 2*10 = 52",
                4 * STUB_CHAR_WIDTH + 2 * pad, segmentNode(1).getPreferredWidth());
        Assert.assertEquals("段[2] 'Month' 宽 = 5*8 + 2*10 = 60",
                5 * STUB_CHAR_WIDTH + 2 * pad, segmentNode(2).getPreferredWidth());
        // 短标题段窄于长标题段，不留白
        Assert.assertTrue("短标题段窄于长标题段",
                segmentNode(0).getPreferredWidth() < segmentNode(2).getPreferredWidth());
    }

    // ==================== 验收 5：方向键导航（←/→ + 焦点移动） ====================

    @Test
    public void arrowKeyNavigationRaisesAdjacentIndexAndMovesFocus() {
        doLayout();
        runtime.requestFocus(segmentNode(0));

        // ① →：cur=0 → next=1
        routeKey(SceneKey.ARROW_RIGHT, SceneKeyAction.PRESSED);
        runtime.flush();
        Assert.assertEquals("→ 上抛相邻下标 1", Integer.valueOf(1), lastSelectValue);
        Assert.assertSame("→ 焦点移到 segment[1]", segmentNode(1), runtime.getFocusedNode());

        selectedSignal.set(Integer.valueOf(1));
        runtime.flush();
        routeKey(SceneKey.ARROW_RIGHT, SceneKeyAction.PRESSED);
        runtime.flush();
        Assert.assertEquals("→ 从 1 上抛 2", Integer.valueOf(2), lastSelectValue);
        Assert.assertSame("→ 焦点移到 segment[2]", segmentNode(2), runtime.getFocusedNode());

        // ② 边界裁剪：cur=2 再 → 仍 2
        selectedSignal.set(Integer.valueOf(2));
        runtime.flush();
        routeKey(SceneKey.ARROW_RIGHT, SceneKeyAction.PRESSED);
        runtime.flush();
        Assert.assertEquals("→ 末段边界裁剪仍 2", Integer.valueOf(2), lastSelectValue);

        // ③ ←：cur=2 → next=1
        routeKey(SceneKey.ARROW_LEFT, SceneKeyAction.PRESSED);
        runtime.flush();
        Assert.assertEquals("← 从 2 上抛 1", Integer.valueOf(1), lastSelectValue);
        Assert.assertSame("← 焦点移到 segment[1]", segmentNode(1), runtime.getFocusedNode());

        // ④ ← 边界裁剪：cur=0 再 ← 仍 0
        selectedSignal.set(Integer.valueOf(0));
        runtime.flush();
        routeKey(SceneKey.ARROW_LEFT, SceneKeyAction.PRESSED);
        runtime.flush();
        Assert.assertEquals("← 首段边界裁剪仍 0", Integer.valueOf(0), lastSelectValue);
    }
}
