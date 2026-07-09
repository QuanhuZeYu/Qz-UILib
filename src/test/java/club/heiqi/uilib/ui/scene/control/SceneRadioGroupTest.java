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
import club.heiqi.uilib.ui.scene.input.SceneHitTester;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.LayoutResult;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

/**
 * SceneRadioGroup 端到端单元测试 —— Phase 4 批 2 多选项单选受控控件（R8）验收。
 *
 * <p>构造 SceneRuntime + SceneLayoutEngine + ScenePaintEngine 三件套，端到端验证：
 * 受控闭环（点 option 只上抛期望下标、控件零状态不自改 selectedIndex）、
 * 命中穿透（点 circle/dot/label 装饰子节点穿透到所属 option）、四态切换零重排、
 * 键盘激活（Enter/Space）+ disabled 拦截、方向键导航（↑/↓ + 焦点移动）。</p>
 */
public class SceneRadioGroupTest {

    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    private ScenePaintEngine paintEngine;
    /** 语义化交互注入 harness（route 根 + click/pressKey 入口）；其 runtime 即上方 runtime 字段 */
    private SceneInteractionHarness harness;

    /** selectedIndex 受控源（可写，测试驱动） */
    private Signal<Integer> selectedSignal;
    private Signal<Boolean> enabledSignal;
    /** onSelect 触发计数器 */
    private AtomicInteger selectCount;
    /** onSelect 最近一次收到的「期望选中下标」 */
    private Integer lastSelectValue;

    private MountHandle handle;
    /** radio 根节点 */
    private SceneNode radioRoot;

    private static final int CANVAS_WIDTH = 200;
    private static final int CANVAS_HEIGHT = 200;
    private static final int STUB_CHAR_WIDTH = 8;

    // SceneRadioGroup chrome token 镜像
    private static final int CIRCLE_UNSEL_ENABLED = SceneChromeTokens.BG_DEFAULT;
    private static final int CIRCLE_UNSEL_PRESSED = SceneChromeTokens.BG_PRESSED;
    private static final int CIRCLE_SEL_ENABLED = SceneChromeTokens.ACCENT;
    private static final int CIRCLE_DISABLED = SceneChromeTokens.BG_DISABLED;
    private static final int DOT_COLOR = SceneChromeTokens.TEXT_ON_ACCENT;
    private static final int DOT_TRANSPARENT = 0x00000000;

    private static final List<String> OPTIONS = Arrays.asList("Low", "Mid", "High");

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        harness = SceneInteractionHarness.create();
        runtime = harness.getRuntime();
        FixedTextMeasurer measurer = new FixedTextMeasurer(STUB_CHAR_WIDTH, 16);
        layoutEngine = new SceneLayoutEngine(measurer);
        paintEngine = new ScenePaintEngine(measurer);
        sceneRoot = new SceneNode();

        selectedSignal = Signal.create(Integer.valueOf(0));
        enabledSignal = Signal.create(Boolean.TRUE);
        selectCount = new AtomicInteger(0);
        lastSelectValue = null;

        SceneRadioGroup.Props props = new SceneRadioGroup.Props(
                selectedSignal, OPTIONS, enabledSignal,
                next -> {
                    selectCount.incrementAndGet();
                    lastSelectValue = next;
                });
        handle = runtime.mount(sceneRoot, SceneRadioGroup.create(runtime, props));
        radioRoot = handle.getRoot();

        runtime.flush();
        // 挂载路由根并对齐 layout，供 harness.click/pressKey 取中心 + route
        harness.mountRoot(sceneRoot, CANVAS_WIDTH, CANVAS_HEIGHT);
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

    /** option[i] 节点（root 第 i 个孩子） */
    private SceneNode optionNode(int i) {
        return radioRoot.__getChildren().get(i);
    }

    /** option[i] 的 circle 子节点（option 第一个孩子） */
    private SceneNode circleNode(int i) {
        return optionNode(i).__getChildren().get(0);
    }

    /** option[i] 的 dot 节点（circle 第一个孩子） */
    private SceneNode dotNode(int i) {
        return circleNode(i).__getChildren().get(0);
    }

    /** option[i] 的 label 节点（option 第二个孩子） */
    private SceneNode labelNode(int i) {
        return optionNode(i).__getChildren().get(1);
    }

    // ==================== 验收 1：受控闭环（点 option[1] 上抛 1 且外部不变；外部 set 1 切选中） ====================

    /**
     * 受控核心：初始 selectedIndex=0，点 option[1] → onSelect 收到期望下标 1，
     * 但 selectedIndex 仍 0（控件零状态不自改）；外部 set 1 → flush → option[1] 切选中态。
     */
    @Test
    public void controlledClickShouldRaiseOnSelectWithoutSelfMutate() {
        doLayout();
        // 初始：option[0] 选中（circle 亮蓝 + dot 实心），option[1] 未选中
        Assert.assertEquals("初始 circle[0] 选中背景", CIRCLE_SEL_ENABLED, circleNode(0).getBackgroundColor());
        Assert.assertEquals("初始 dot[0] 实心", DOT_COLOR, dotNode(0).getBackgroundColor());
        Assert.assertEquals("初始 circle[1] 未选中背景", CIRCLE_UNSEL_ENABLED, circleNode(1).getBackgroundColor());
        Assert.assertEquals("初始 dot[1] 透明", DOT_TRANSPARENT, dotNode(1).getBackgroundColor());

        // 点 option[1] 几何中心
        harness.click(optionNode(1));

        // onSelect 被调一次且收到期望下标 1
        Assert.assertEquals("CLICK 应触发一次 onSelect", 1, selectCount.get());
        Assert.assertEquals("onSelect 应收到期望下标 1", Integer.valueOf(1), lastSelectValue);

        // 受控：外部未 set 回 → selectedIndex 仍 0 → 视觉未变（控件零状态不自改）
        Assert.assertEquals("受控：外部未回写时 selectedIndex 仍 0",
                Integer.valueOf(0), selectedSignal.get());
        doLayout();
        Assert.assertEquals("受控：circle[1] 视觉未自选中", CIRCLE_UNSEL_ENABLED, circleNode(1).getBackgroundColor());

        // 外部 set 1 → flush → option[1] 切选中态、option[0] 退选
        selectedSignal.set(Integer.valueOf(1));
        runtime.flush();
        doLayout();
        Assert.assertEquals("外部回写后 circle[1] 选中背景", CIRCLE_SEL_ENABLED, circleNode(1).getBackgroundColor());
        Assert.assertEquals("外部回写后 dot[1] 实心", DOT_COLOR, dotNode(1).getBackgroundColor());
        Assert.assertEquals("外部回写后 circle[0] 退选背景", CIRCLE_UNSEL_ENABLED, circleNode(0).getBackgroundColor());
        Assert.assertEquals("外部回写后 dot[0] 透明", DOT_TRANSPARENT, dotNode(0).getBackgroundColor());
    }

    // ==================== 验收 2：命中穿透（点 dot 装饰子节点穿透到所属 option） ====================

    /**
     * 命中穿透：点 option[1] 内最深装饰子节点 dot[1] 几何中心，命中穿透到 option[1]，
     * option[1] 进 pressed → circle[1] 切 pressed 背景。
     */
    @Test
    public void hitTestShouldPassThroughDecorativeDotToOption() {
        doLayout();

        // 按下 dot[1] 中心 → 穿透到 option[1] → option[1] pressed → circle[1] pressed 背景
        harness.press(dotNode(1));
        doLayout();
        Assert.assertEquals("点 dot[1] 穿透到 option[1] → circle[1] pressed 背景",
                CIRCLE_UNSEL_PRESSED, circleNode(1).getBackgroundColor());

        // 释放 → 合成 CLICK → onSelect 收到 1（验证点装饰也能激活）
        harness.release(dotNode(1));
        Assert.assertEquals("点 dot[1] 释放应合成 CLICK 触发 onSelect", 1, selectCount.get());
        Assert.assertEquals("期望下标 1", Integer.valueOf(1), lastSelectValue);
    }

    // ==================== 验收 3：四态切换零重排（终极反证 R-D） ====================

    /**
     * 交互态切换帧 {@code result.getRelayoutCount()==0}——交互态没被误做成布局级的终极证明。
     */
    @Test
    public void interactionStateSwitchShouldOnlyPaintNotLayout() {
        LayoutResult result = doLayout();
        Assert.assertEquals("初始 circle[1] 默认背景", CIRCLE_UNSEL_ENABLED, circleNode(1).getBackgroundColor());

        // ① enabled → disabled：circle 切灰，零重排
        enabledSignal.set(Boolean.FALSE);
        runtime.flush();
        result = doLayout();
        Assert.assertEquals("disabled circle[1] 背景", CIRCLE_DISABLED, circleNode(1).getBackgroundColor());
        Assert.assertEquals("R-D: enabled→disabled 零重排", 0, result.getRelayoutCount());

        // ② disabled → enabled：回默认背景，零重排
        enabledSignal.set(Boolean.TRUE);
        runtime.flush();
        result = doLayout();
        Assert.assertEquals("回 enabled circle[1] 背景", CIRCLE_UNSEL_ENABLED, circleNode(1).getBackgroundColor());
        Assert.assertEquals("R-D: disabled→enabled 零重排", 0, result.getRelayoutCount());

        // ③ pressed：harness.press 命中 option[1] 几何中心
        result = doLayout();
        harness.press(optionNode(1));
        result = doLayout();
        Assert.assertEquals("pressed circle[1] 背景", CIRCLE_UNSEL_PRESSED, circleNode(1).getBackgroundColor());
        Assert.assertEquals("R-D: pressed 零重排", 0, result.getRelayoutCount());

        // ④ 释放 pressed：回默认背景，零重排
        harness.release(optionNode(1));
        result = doLayout();
        Assert.assertEquals("释放后回默认背景", CIRCLE_UNSEL_ENABLED, circleNode(1).getBackgroundColor());
        Assert.assertEquals("R-D: 释放 pressed 零重排", 0, result.getRelayoutCount());

        // ⑤ 外部 set 选中切换：纯 PAINT 级零重排（dot 透明背景切换不重排）
        selectedSignal.set(Integer.valueOf(2));
        runtime.flush();
        result = doLayout();
        Assert.assertEquals("选中切到 2：dot[2] 实心", DOT_COLOR, dotNode(2).getBackgroundColor());
        Assert.assertEquals("选中切到 2：dot[0] 透明", DOT_TRANSPARENT, dotNode(0).getBackgroundColor());
        Assert.assertEquals("R-D: 选中切换零重排（dot 透明背景纯 PAINT）", 0, result.getRelayoutCount());
    }

    // ==================== 验收 4：键盘激活（Enter/Space），disabled 拦截 ====================

    /**
     * Enter/Space 键盘激活调 onSelect 上抛当前 option 下标；disabled 态键盘/点击均不触发。
     */
    @Test
    public void keyboardActivationRaisesOnSelectAndDisabledBlocks() {
        doLayout();
        runtime.requestFocus(optionNode(1));

        // ① Enter 激活 → 上抛 option[1] 下标 1
        int before = selectCount.get();
        harness.pressKey(SceneKey.ENTER);
        Assert.assertEquals("Enter 应触发一次 onSelect", before + 1, selectCount.get());
        Assert.assertEquals("Enter 期望下标 1", Integer.valueOf(1), lastSelectValue);

        // ② Space 激活
        before = selectCount.get();
        harness.pressKey(SceneKey.SPACE);
        Assert.assertEquals("Space 应触发一次 onSelect", before + 1, selectCount.get());

        // ③ disabled 态：Enter / CLICK 均不触发
        enabledSignal.set(Boolean.FALSE);
        runtime.flush();
        before = selectCount.get();
        harness.pressKey(SceneKey.ENTER);
        Assert.assertEquals("disabled 态 Enter 不触发", before, selectCount.get());

        doLayout();
        harness.click(optionNode(1));
        Assert.assertEquals("disabled 态 CLICK 不触发", before, selectCount.get());
    }

    // ==================== 验收 5：方向键导航（↑/↓ + 焦点移动） ====================

    /**
     * ↓ 从当前 selectedIndex 算相邻下标上抛 + 焦点移动到对应 option；↑ 反向；边界裁剪。
     */
    @Test
    public void arrowKeyNavigationRaisesAdjacentIndexAndMovesFocus() {
        doLayout();
        // 当前 selectedIndex=0，焦点先放 option[0]
        runtime.requestFocus(optionNode(0));

        // ① ↓：cur=0 → next=1，上抛 1 + 焦点移到 option[1]
        harness.pressKey(SceneKey.ARROW_DOWN);
        Assert.assertEquals("↓ 上抛相邻下标 1", Integer.valueOf(1), lastSelectValue);
        Assert.assertSame("↓ 焦点移到 option[1]", optionNode(1), runtime.getFocusedNode());

        // 外部回写 selectedIndex=1（受控闭环），再 ↓ → next=2
        selectedSignal.set(Integer.valueOf(1));
        runtime.flush();
        harness.pressKey(SceneKey.ARROW_DOWN);
        Assert.assertEquals("↓ 从 1 上抛 2", Integer.valueOf(2), lastSelectValue);
        Assert.assertSame("↓ 焦点移到 option[2]", optionNode(2), runtime.getFocusedNode());

        // ② 边界裁剪：cur=2（先回写）再 ↓ → 仍 2（裁剪到末项）
        selectedSignal.set(Integer.valueOf(2));
        runtime.flush();
        harness.pressKey(SceneKey.ARROW_DOWN);
        Assert.assertEquals("↓ 末项边界裁剪仍 2", Integer.valueOf(2), lastSelectValue);

        // ③ ↑：cur=2 → next=1
        harness.pressKey(SceneKey.ARROW_UP);
        Assert.assertEquals("↑ 从 2 上抛 1", Integer.valueOf(1), lastSelectValue);
        Assert.assertSame("↑ 焦点移到 option[1]", optionNode(1), runtime.getFocusedNode());

        // ④ ↑ 边界裁剪：cur=0（回写）再 ↑ → 仍 0
        selectedSignal.set(Integer.valueOf(0));
        runtime.flush();
        harness.pressKey(SceneKey.ARROW_UP);
        Assert.assertEquals("↑ 首项边界裁剪仍 0", Integer.valueOf(0), lastSelectValue);
    }

    // ==================== 验收 6：option 行 SHRINK，命中宽=内容宽，行尾空白不命中 ====================

    /**
     * option 默认 SHRINK：option[2]（"High"）宽严格小于 canvas，且等于 circle+gap+label+2*padding 内容宽；
     * 行尾空白（option 右缘外）命中链不含 optionNode(2)；内容区（circle 中心）仍可点触发 onSelect。
     *
     * <p>期望宽：CIRCLE_SIZE(16) + OPTION_GAP(GAP_SM=4) + "High"(4)×STUB_CHAR_WIDTH(8)=32
     * + 2×OPTION_PADDING(PAD_SM=2)=4 → 16+4+32+4=56。</p>
     */
    @Test
    public void optionHitWidthShouldShrinkToContentNotFillParent() {
        doLayout();

        LayoutBox optionBox = (LayoutBox) optionNode(2).getCachedLayout();
        // width = CIRCLE_SIZE + OPTION_GAP + labelW + 2*OPTION_PADDING = 16 + 4 + 32 + 4 = 56
        int expectedWidth = 16 + SceneChromeTokens.GAP_SM
                + ("High".length() * STUB_CHAR_WIDTH)
                + 2 * SceneChromeTokens.PAD_SM;
        Assert.assertTrue("option 宽应严格小于 canvas，证明非 FILL 吞全宽",
                optionBox.getWidth() < CANVAS_WIDTH);
        Assert.assertEquals("option 宽 = circle + gap + label + 2*padding 内容宽",
                expectedWidth, optionBox.getWidth());
        Assert.assertEquals("期望宽 56（High 标签）", 56, expectedWidth);

        // 行尾空白：option 右缘 +1（仍在 canvas 内）不应命中 optionNode(2)
        int missX = optionBox.getX() + optionBox.getWidth() + 1;
        int midY = optionBox.getY() + optionBox.getHeight() / 2;
        Assert.assertTrue("探测点应仍在 canvas 内", missX < CANVAS_WIDTH);
        List<SceneNode> missChain = new SceneHitTester().hitTest(sceneRoot, missX, midY, 0, 0);
        Assert.assertFalse("行尾空白命中链不应含 optionNode(2)",
                missChain.contains(optionNode(2)));

        // 内容区仍可点：circle[2] 中心触发 onSelect，期望下标 2
        int before = selectCount.get();
        harness.click(circleNode(2));
        Assert.assertEquals("内容区点击仍应触发 onSelect", before + 1, selectCount.get());
        Assert.assertEquals("期望下标 2", Integer.valueOf(2), lastSelectValue);
    }
}
