package club.heiqi.uilib.ui.scene.control;

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
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

/**
 * SceneCheckbox 端到端单元测试 —— Phase 4 批 1 受控双向控件验收。
 *
 * <p>构造 SceneRuntime + SceneLayoutEngine + ScenePaintEngine 三件套，端到端验证：
 * 受控双向闭环（点击只调 onChange 交还期望新值、控件零内部状态不自翻转）、
 * 命中穿透（点装饰子节点穿透到 root）、四态切换零重排（R-D 终极反证）、
 * 键盘激活（Enter/Space）。</p>
 *
 * <h3>测试沙箱 pipeline（对照 SceneButtonTest）</h3>
 * <pre>
 *   signal.set / route → runtime.flush() → layout → paint → 断言
 * </pre>
 */
public class SceneCheckboxTest {

    /** 场景根：checkbox 作为子节点 mount 到此（route/layout/paint 入口） */
    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    private ScenePaintEngine paintEngine;
    /** 语义化交互注入 harness（route 根 + click/moveTo/pressKey 入口）；其 runtime 即上方 runtime 字段 */
    private SceneInteractionHarness harness;

    /** checkbox 的 checked 受控源（可写，测试驱动） */
    private Signal<Boolean> checkedSignal;
    /** checkbox 的 label 文本 signal */
    private Signal<String> labelSignal;
    /** checkbox 的 enabled signal（驱动四态/禁用） */
    private Signal<Boolean> enabledSignal;
    /** onChange 触发计数器 */
    private AtomicInteger changeCount;
    /** onChange 最近一次收到的「期望新值」 */
    private Boolean lastChangeValue;

    private MountHandle handle;
    /** checkbox 根节点 */
    private SceneNode checkboxRoot;

    private static final int CANVAS_WIDTH = 200;
    private static final int CANVAS_HEIGHT = 100;

    private static final int BOX_UNCHECKED_ENABLED = SceneChromeTokens.BG_DEFAULT;
    private static final int BOX_UNCHECKED_HOVER = SceneChromeTokens.BG_HOVER;
    private static final int BOX_UNCHECKED_PRESSED = SceneChromeTokens.BG_PRESSED;
    private static final int BOX_CHECKED_ENABLED = SceneChromeTokens.ACCENT;
    private static final int BOX_DISABLED = SceneChromeTokens.BG_DISABLED;
    private static final int STUB_CHAR_WIDTH = 8;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        harness = SceneInteractionHarness.create();
        runtime = harness.getRuntime();
        FixedTextMeasurer measurer = new FixedTextMeasurer(STUB_CHAR_WIDTH, 16);
        layoutEngine = new SceneLayoutEngine(measurer);
        paintEngine = new ScenePaintEngine(measurer);
        sceneRoot = new SceneNode();

        checkedSignal = Signal.create(Boolean.FALSE);
        labelSignal = Signal.create("Sound");
        enabledSignal = Signal.create(Boolean.TRUE);
        changeCount = new AtomicInteger(0);
        lastChangeValue = null;

        SceneCheckbox.Props props = new SceneCheckbox.Props(
                checkedSignal, labelSignal, enabledSignal,
                next -> {
                    changeCount.incrementAndGet();
                    lastChangeValue = next;
                });
        handle = runtime.mount(sceneRoot, SceneCheckbox.create(runtime, props));
        checkboxRoot = handle.getRoot();

        // 首帧 flush：让所有 bind 的 effect 首次执行
        runtime.flush();
        // 挂载路由根并对齐 layout，供 harness.click/moveTo/pressKey 取中心 + route
        harness.mountRoot(sceneRoot, CANVAS_WIDTH, CANVAS_HEIGHT);
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== 辅助方法 ====================

    private void doLayout() {
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
    }

    private PaintPlan doPaint() {
        return paintEngine.paint(sceneRoot).getPlan();
    }

    /** box 子节点（root 第一个孩子） */
    private SceneNode boxNode() {
        return checkboxRoot.__getChildren().get(0);
    }

    /** label 子节点（root 第二个孩子） */
    private SceneNode labelNode() {
        return checkboxRoot.__getChildren().get(1);
    }

    /** box 当前背景色 */
    private int boxBackground() {
        return boxNode().getBackgroundColor();
    }

    // ==================== 验收 1：受控双向闭环（点击不自翻转，只上抛期望新值） ====================

    /**
     * 受控双向核心：初始 checked=false，点击命中 → onChange 收到期望新值 true，
     * 但控件视觉此时<b>未变</b>（受控：外部没 set 回则 box 仍未勾选态）；
     * 再外部 set checked=true → flush → box 切勾选态。
     */
    @Test
    public void controlledTwoWayClickShouldRaiseOnChangeWithoutSelfFlip() {
        doLayout();
        // 初始：box 未勾选默认背景
        Assert.assertEquals("初始 box 未勾选背景", BOX_UNCHECKED_ENABLED, boxBackground());

        // 点击 box 几何中心（装饰子节点命中穿透到 root）→ DOWN+UP 合成 CLICK
        harness.click(boxNode());

        // onChange 被调一次且收到期望新值 true
        Assert.assertEquals("CLICK 应触发一次 onChange", 1, changeCount.get());
        Assert.assertEquals("onChange 应收到期望新值 true", Boolean.TRUE, lastChangeValue);

        // 受控：外部未 set 回 → checked 仍 false → box 视觉未变（控件零内部状态不自翻转）
        Assert.assertEquals("受控：外部未回写时 checked 仍 false",
                Boolean.FALSE, checkedSignal.get());
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        Assert.assertEquals("受控：box 视觉未自翻转", BOX_UNCHECKED_ENABLED, boxBackground());

        // 外部 set checked=true → flush → box 切勾选态
        checkedSignal.set(Boolean.TRUE);
        runtime.flush();
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        Assert.assertEquals("外部回写后 box 切勾选背景", BOX_CHECKED_ENABLED, boxBackground());
    }

    // ==================== 验收 2：命中穿透（点 label 装饰子节点穿透到 root） ====================

    /**
     * 命中穿透：点 label 子节点几何中心，最深命中穿透到 root，root 进 pressed。
     */
    @Test
    public void hitTestShouldPassThroughDecorativeLabelToRoot() {
        doLayout();

        // 按下 label 中心 → 命中穿透到 root → root pressed → box 进 pressed 背景
        harness.press(labelNode());
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        Assert.assertEquals("点 label 穿透到 root → box 进 pressed 背景",
                BOX_UNCHECKED_PRESSED, boxBackground());

        // 释放 → 合成 CLICK → onChange 触发（验证点 label 也能激活）
        harness.release(labelNode());
        Assert.assertEquals("点 label 释放应合成 CLICK 触发 onChange", 1, changeCount.get());
        Assert.assertEquals("期望新值 true", Boolean.TRUE, lastChangeValue);
    }

    // ==================== 验收 3：四态切换 + 终极断言 R-D（零重排） ====================

    /**
     * 四态背景切换正确，且每次状态切换帧 {@code result.getRelayoutCount()==0}——
     * 「控件契约没把交互态误做成布局级」的终极证明（命门）。
     */
    @Test
    public void interactionStateSwitchShouldOnlyPaintNotLayout() {
        // 初始 enabled + 未勾选：box 默认背景
        doLayout();
        Assert.assertEquals("初始 box 默认背景", BOX_UNCHECKED_ENABLED, boxBackground());

        // ① enabled → disabled：box 切灰，零重排
        enabledSignal.set(Boolean.FALSE);
        runtime.flush();
        LayoutResult result = layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        Assert.assertEquals("disabled box 背景", BOX_DISABLED, boxBackground());
        Assert.assertEquals("R-D: enabled→disabled 零重排", 0, result.getRelayoutCount());

        // ② disabled → enabled：回默认背景，零重排
        enabledSignal.set(Boolean.TRUE);
        runtime.flush();
        result = layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        Assert.assertEquals("回 enabled box 背景", BOX_UNCHECKED_ENABLED, boxBackground());
        Assert.assertEquals("R-D: disabled→enabled 零重排", 0, result.getRelayoutCount());

        // ③ pressed：harness.press 命中 box 几何中心 → 命中穿透 root → pressed
        result = layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        harness.press(boxNode());
        result = layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        Assert.assertEquals("pressed box 背景", BOX_UNCHECKED_PRESSED, boxBackground());
        Assert.assertEquals("R-D: pressed 零重排", 0, result.getRelayoutCount());

        // ④ 释放 pressed：harness.release → pressed=false，回默认背景，零重排
        harness.release(boxNode());
        result = layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        Assert.assertEquals("释放后回默认背景", BOX_UNCHECKED_ENABLED, boxBackground());
        Assert.assertEquals("R-D: 释放 pressed 零重排", 0, result.getRelayoutCount());
    }

    // ==================== 验收 4：键盘激活（Enter/Space），disabled 不触发 ====================

    /**
     * Enter/Space 键盘激活调 onChange 交还期望新值；disabled 态键盘/点击均不触发。
     */
    @Test
    public void keyboardActivationRaisesOnChangeAndDisabledBlocks() {
        doLayout();
        runtime.requestFocus(checkboxRoot);

        // ① Enter 激活
        int before = changeCount.get();
        harness.pressKey(SceneKey.ENTER);
        Assert.assertEquals("Enter 应触发一次 onChange", before + 1, changeCount.get());
        Assert.assertEquals("Enter 期望新值 true", Boolean.TRUE, lastChangeValue);

        // ② Space 激活
        before = changeCount.get();
        harness.pressKey(SceneKey.SPACE);
        Assert.assertEquals("Space 应触发一次 onChange", before + 1, changeCount.get());

        // ③ disabled 态：Enter / CLICK 均不触发
        enabledSignal.set(Boolean.FALSE);
        runtime.flush();
        before = changeCount.get();
        harness.pressKey(SceneKey.ENTER);
        Assert.assertEquals("disabled 态 Enter 不触发", before, changeCount.get());

        harness.click(boxNode());
        Assert.assertEquals("disabled 态 CLICK 不触发", before, changeCount.get());
    }

    // ==================== 验收 5：hover 态切换正确 ====================

    /**
     * hover 进/出经 route POINTER_MOVE 驱动，box 背景在 hover 与默认间切换，且零重排。
     */
    @Test
    public void hoverStateShouldSwitchBoxBackgroundWithoutLayout() {
        doLayout();

        // hover 进 box（命中穿透 root）→ box hover 背景
        harness.moveTo(boxNode());
        LayoutResult result = layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        Assert.assertEquals("hover box 背景", BOX_UNCHECKED_HOVER, boxBackground());
        Assert.assertEquals("R-D: hover 进零重排", 0, result.getRelayoutCount());

        // hover 出（移到控件外）→ 回默认背景
        harness.moveAt(CANVAS_WIDTH - 1, CANVAS_HEIGHT - 1);
        result = layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        Assert.assertEquals("hover 出回默认背景", BOX_UNCHECKED_ENABLED, boxBackground());
        Assert.assertEquals("R-D: hover 出零重排", 0, result.getRelayoutCount());
    }

    // ==================== 验收 6：交互根 SHRINK，命中宽=内容宽，行尾空白不命中 ====================

    /**
     * 交互根默认 SHRINK：root 宽严格小于 canvas 宽，且等于 box+gap+label 内容宽；
     * 行尾空白（root 右缘外）命中链不含 checkboxRoot；内容区（box 中心）仍可点触发 onChange。
     *
     * <p>期望宽：BOX_SIZE(16) + GAP_MD(8) + "Sound"(5)×STUB_CHAR_WIDTH(8)=40 → 64。</p>
     */
    @Test
    public void rootHitWidthShouldShrinkToContentNotFillParent() {
        doLayout();

        LayoutBox rootBox = (LayoutBox) checkboxRoot.getCachedLayout();
        int expectedWidth = 16 + SceneChromeTokens.GAP_MD + ("Sound".length() * STUB_CHAR_WIDTH);
        Assert.assertTrue("root 宽应严格小于 canvas，证明非 FILL 吞全宽",
                rootBox.getWidth() < CANVAS_WIDTH);
        Assert.assertEquals("root 宽 = box + gap + label 内容宽",
                expectedWidth, rootBox.getWidth());

        // 行尾空白：root 右缘 +1（仍在 canvas 内）不应命中 checkboxRoot
        int missX = rootBox.getX() + rootBox.getWidth() + 1;
        int midY = rootBox.getY() + rootBox.getHeight() / 2;
        Assert.assertTrue("探测点应仍在 canvas 内", missX < CANVAS_WIDTH);
        List<SceneNode> missChain = new SceneHitTester().hitTest(sceneRoot, missX, midY, 0, 0);
        Assert.assertFalse("行尾空白命中链不应含 checkboxRoot",
                missChain.contains(checkboxRoot));

        // 内容区仍可点：box 中心触发 onChange
        int before = changeCount.get();
        harness.click(boxNode());
        Assert.assertEquals("内容区点击仍应触发 onChange", before + 1, changeCount.get());
        Assert.assertEquals("期望新值 true", Boolean.TRUE, lastChangeValue);
    }
}
