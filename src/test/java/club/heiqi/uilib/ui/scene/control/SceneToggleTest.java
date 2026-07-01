package club.heiqi.uilib.ui.scene.control;

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
 * SceneToggle 端到端单元测试 —— Phase 4 批 1 受控双向开关控件验收。
 *
 * <p>构造 SceneRuntime + SceneLayoutEngine + ScenePaintEngine 三件套，端到端验证：
 * 受控双向闭环（点击只调 onChange 交还期望新值、控件零内部状态不自翻转）、
 * 命中穿透（点 track/label 装饰子节点穿透到 root）、四态切换零重排（R-D 终极反证）、
 * 键盘激活（Enter/Space）、on/off 两态 thumb 位置不同（静态非动画）。</p>
 *
 * <h3>测试沙箱 pipeline（对照 SceneButtonTest）</h3>
 * <pre>
 *   signal.set / route → runtime.flush() → layout → paint → 断言
 * </pre>
 */
public class SceneToggleTest {

    /** 场景根：toggle 作为子节点 mount 到此（route/layout/paint 入口） */
    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    private ScenePaintEngine paintEngine;
    /** 语义化交互注入 harness（route 根 + click/pressKey 入口）；其 runtime 即上方 runtime 字段 */
    private SceneInteractionHarness harness;

    /** toggle 的 on 受控源（可写，测试驱动） */
    private Signal<Boolean> onSignal;
    /** toggle 的 label 文本 signal */
    private Signal<String> labelSignal;
    /** toggle 的 enabled signal（驱动四态/禁用） */
    private Signal<Boolean> enabledSignal;
    /** onChange 触发计数器 */
    private AtomicInteger changeCount;
    /** onChange 最近一次收到的「期望新值」 */
    private Boolean lastChangeValue;

    private MountHandle handle;
    /** toggle 根节点 */
    private SceneNode toggleRoot;

    private static final int CANVAS_WIDTH = 200;
    private static final int CANVAS_HEIGHT = 100;

    private static final int TRACK_OFF_ENABLED = SceneChromeTokens.BG_DEFAULT;
    private static final int TRACK_OFF_HOVER = SceneChromeTokens.BG_HOVER;
    private static final int TRACK_OFF_PRESSED = SceneChromeTokens.BG_PRESSED;
    private static final int TRACK_ON_ENABLED = SceneChromeTokens.ACCENT;
    private static final int TRACK_DISABLED = SceneChromeTokens.BG_DISABLED;
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

        onSignal = Signal.create(Boolean.FALSE);
        labelSignal = Signal.create("Night");
        enabledSignal = Signal.create(Boolean.TRUE);
        changeCount = new AtomicInteger(0);
        lastChangeValue = null;

        SceneToggle.Props props = new SceneToggle.Props(
                onSignal, labelSignal, enabledSignal,
                next -> {
                    changeCount.incrementAndGet();
                    lastChangeValue = next;
                });
        handle = runtime.mount(sceneRoot, SceneToggle.create(runtime, props));
        toggleRoot = handle.getRoot();

        // 首帧 flush：让所有 bind 的 effect 首次执行
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

    private void doLayout() {
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
    }

    /** track 子节点（root 第一个孩子） */
    private SceneNode trackNode() {
        return toggleRoot.__getChildren().get(0);
    }

    /** thumb 子节点（track 第一个孩子） */
    private SceneNode thumbNode() {
        return trackNode().__getChildren().get(0);
    }

    /** label 子节点（root 第二个孩子） */
    private SceneNode labelNode() {
        return toggleRoot.__getChildren().get(1);
    }

    private LayoutBox thumbBox() {
        return (LayoutBox) thumbNode().getCachedLayout();
    }

    /** track 当前背景色 */
    private int trackBackground() {
        return trackNode().getBackgroundColor();
    }

    // ==================== 验收 1：受控双向闭环（点击不自翻转，只上抛期望新值） ====================

    /**
     * 受控双向核心：初始 on=false，点击命中 → onChange 收到期望新值 true，
     * 但控件视觉此时<b>未变</b>（受控：外部没 set 回则 track 仍 off 态）；
     * 再外部 set on=true → flush → track 切 on 态。
     */
    @Test
    public void controlledTwoWayClickShouldRaiseOnChangeWithoutSelfFlip() {
        doLayout();
        Assert.assertEquals("初始 track off 背景", TRACK_OFF_ENABLED, trackBackground());

        // 点击 track 几何中心（装饰子节点命中穿透到 root）→ DOWN+UP 合成 CLICK
        harness.click(trackNode());

        Assert.assertEquals("CLICK 应触发一次 onChange", 1, changeCount.get());
        Assert.assertEquals("onChange 应收到期望新值 true", Boolean.TRUE, lastChangeValue);

        // 受控：外部未 set 回 → on 仍 false → track 视觉未变（控件零内部状态不自翻转）
        Assert.assertEquals("受控：外部未回写时 on 仍 false", Boolean.FALSE, onSignal.get());
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        Assert.assertEquals("受控：track 视觉未自翻转", TRACK_OFF_ENABLED, trackBackground());

        // 外部 set on=true → flush → track 切 on 态
        onSignal.set(Boolean.TRUE);
        runtime.flush();
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        Assert.assertEquals("外部回写后 track 切 on 背景", TRACK_ON_ENABLED, trackBackground());
    }

    // ==================== 验收 2：命中穿透（点 label 装饰子节点穿透到 root） ====================

    /**
     * 命中穿透：点 label 子节点几何中心，最深命中穿透到 root，root 进 pressed，
     * 释放合成 CLICK 触发 onChange。
     */
    @Test
    public void hitTestShouldPassThroughDecorativeLabelToRoot() {
        doLayout();

        harness.press(labelNode());
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        Assert.assertEquals("点 label 穿透到 root → track 进 pressed 背景",
                TRACK_OFF_PRESSED, trackBackground());

        harness.release(labelNode());
        Assert.assertEquals("点 label 释放应合成 CLICK 触发 onChange", 1, changeCount.get());
        Assert.assertEquals("期望新值 true", Boolean.TRUE, lastChangeValue);
    }

    // ==================== 验收 3：四态切换 + 终极断言 R-D（零重排） ====================

    /**
     * 四态 track 背景切换正确，且每次状态切换帧 {@code result.getRelayoutCount()==0}——
     * 「控件契约没把交互态误做成布局级」的终极证明（命门）。
     *
     * <p>注意：交互态切换（enabled/pressed/hover）全 PAINT 级零重排；
     * 而 on/off 切换涉及 thumb 位置（LAYOUT 级），由验收 5 单独验证，不混入本试金石。</p>
     */
    @Test
    public void interactionStateSwitchShouldOnlyPaintNotLayout() {
        doLayout();
        Assert.assertEquals("初始 track off 默认背景", TRACK_OFF_ENABLED, trackBackground());

        // ① enabled → disabled：track 切灰，零重排
        enabledSignal.set(Boolean.FALSE);
        runtime.flush();
        LayoutResult result = layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        Assert.assertEquals("disabled track 背景", TRACK_DISABLED, trackBackground());
        Assert.assertEquals("R-D: enabled→disabled 零重排", 0, result.getRelayoutCount());

        // ② disabled → enabled：回默认背景，零重排
        enabledSignal.set(Boolean.TRUE);
        runtime.flush();
        result = layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        Assert.assertEquals("回 enabled track 背景", TRACK_OFF_ENABLED, trackBackground());
        Assert.assertEquals("R-D: disabled→enabled 零重排", 0, result.getRelayoutCount());

        // ③ pressed：harness.press 命中 track 几何中心 → 命中穿透 root → pressed
        result = layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        harness.press(trackNode());
        result = layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        Assert.assertEquals("pressed track 背景", TRACK_OFF_PRESSED, trackBackground());
        Assert.assertEquals("R-D: pressed 零重排", 0, result.getRelayoutCount());

        // ④ 释放 pressed：harness.release → pressed=false，回默认背景，零重排
        harness.release(trackNode());
        result = layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        Assert.assertEquals("释放后回默认背景", TRACK_OFF_ENABLED, trackBackground());
        Assert.assertEquals("R-D: 释放 pressed 零重排", 0, result.getRelayoutCount());
    }

    // ==================== 验收 4：键盘激活（Enter/Space），disabled 不触发 ====================

    /**
     * Enter/Space 键盘激活调 onChange 交还期望新值；disabled 态键盘/点击均不触发。
     */
    @Test
    public void keyboardActivationRaisesOnChangeAndDisabledBlocks() {
        doLayout();
        runtime.requestFocus(toggleRoot);

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

        harness.click(trackNode());
        Assert.assertEquals("disabled 态 CLICK 不触发", before, changeCount.get());
    }

    // ==================== 验收 5：on/off 两态 thumb 位置不同（静态非动画） ====================

    /**
     * on/off 两态 thumb 在 track 内左右位置不同：off→靠左、on→靠右。
     * 断言 thumb LayoutBox.x 在两态间有差异（on 态更靠右），证明静态位置表达开关态。
     */
    @Test
    public void thumbPositionShouldDifferBetweenOnAndOff() {
        // off 态：thumb 靠左
        doLayout();
        int offThumbX = thumbBox().getX();

        // 外部 set on=true → flush → layout → thumb 靠右
        onSignal.set(Boolean.TRUE);
        runtime.flush();
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        int onThumbX = thumbBox().getX();

        Assert.assertTrue("on 态 thumb 应比 off 态更靠右（位置差异表达开关态）",
                onThumbX > offThumbX);
    }
}
