package club.heiqi.uilib.ui.scene.control;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
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
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * SceneToggle 端到端单元测试 —— Phase 4 批 1 受控双向开关控件验收。
 *
 * <p>构造 SceneRuntime + SceneLayoutEngine + ScenePaintEngine 三件套，端到端验证：
 * 受控双向闭环（点击只调 onChange 交还期望新值、控件零内部状态不自翻转）、
 * 命中穿透（点 track/label 装饰子节点穿透到 root）、四态切换零重排（R-D 终极反证）、
 * 键盘激活（Enter/Space）、on/off thumb transform Motion 且零重排，
 * 以及主题化外观（track 走 INDICATOR 选中配方、thumb 走强调色、label 走主题前景、
 * 主题切换不重建节点、禁用取禁用档、卸载回收绑定）。</p>
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

    /**
     * 库默认主题的 INDICATOR 角色配方：track 默认外观唯一来源。
     * 断言取配方值而不是硬编码色号，主题集中调参时本类自动跟随。
     */
    private static final SceneSurfaceStyle TRACK_SURFACE =
            SceneThemes.DEFAULT.surface(SceneTheme.Role.INDICATOR);
    private static final int TRACK_OFF_ENABLED = TRACK_SURFACE.getIdle().getTint();
    private static final int TRACK_OFF_HOVER = TRACK_SURFACE.getHovered().getTint();
    private static final int TRACK_OFF_PRESSED = TRACK_SURFACE.getPressed().getTint();
    private static final int TRACK_DISABLED = TRACK_SURFACE.getDisabled().getTint();
    /**
     * 选中档：{@code SceneThemes.selectableSurface} 把配方 tint 的 RGB 换成主题强调色、
     * 保留原 alpha（故选中是色彩语义，不是仅透明度）。
     */
    private static final int TRACK_ON_ENABLED = selectedTint(TRACK_OFF_ENABLED, SceneThemes.DEFAULT.accent());
    /** thumb 启用色 = 主题强调色；禁用色 = 主题禁用前景色。 */
    private static final int THUMB_ENABLED = SceneThemes.DEFAULT.accent();
    private static final int THUMB_DISABLED = SceneThemes.DEFAULT.disabledForeground();
    /** label 前景：主题正文色 / 主题禁用前景色。 */
    private static final int LABEL_ENABLED = SceneThemes.DEFAULT.foreground();
    private static final int LABEL_DISABLED = SceneThemes.DEFAULT.disabledForeground();
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

    /** 选中配方语义：保留配方 tint 的 alpha，RGB 换成强调色。 */
    private static int selectedTint(int baseTint, int accent) {
        return (baseTint & 0xFF000000) | (accent & 0x00FFFFFF);
    }

    private static int alphaOf(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    private static int rgbOf(int argb) {
        return argb & 0x00FFFFFF;
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
     * 再外部 set on=true → flush → track 切 on 态（选中配方 = tint 换强调色）。
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
     * <p>四态值全部来自主题 INDICATOR 配方（disabled/hovered/pressed/idle），不再取
     * {@code SceneStateColors} 实色。注意：交互态切换（enabled/pressed/hover）全 PAINT 级零重排；
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

        // ②b hover：moveTo 命中 track 中心（穿透到 root）→ track 取配方 hovered 档；移开回静止档
        harness.moveTo(trackNode());
        result = layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        Assert.assertEquals("hover track 背景取配方 hovered 档", TRACK_OFF_HOVER, trackBackground());
        Assert.assertEquals("R-D: hover 零重排", 0, result.getRelayoutCount());
        harness.moveAt(CANVAS_WIDTH - 20, CANVAS_HEIGHT - 10); // toggle 外坐标
        result = layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        Assert.assertEquals("移开回静止背景", TRACK_OFF_ENABLED, trackBackground());
        Assert.assertEquals("R-D: hover 退出零重排", 0, result.getRelayoutCount());

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

    // ==================== 验收 5：on/off thumb 只走 composite transform ====================

    /**
     * on/off 两态不改 thumb LayoutBox，只通过 translateX 表达视觉位置，避免移动 hit/layout root。
     */
    @Test
    public void thumbPositionShouldDifferBetweenOnAndOff() {
        doLayout();
        int offThumbX = thumbBox().getX();

        // 默认 runtime 未启用 Motion，仍立即到端点，但布局盒必须不动。
        onSignal.set(Boolean.TRUE);
        runtime.flush();
        LayoutResult result = layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        int onThumbX = thumbBox().getX();

        Assert.assertEquals("thumb LayoutBox 不移动", offThumbX, onThumbX);
        Assert.assertEquals("on 端点 translateX=24", 24.0f, thumbNode().getTransform().translateX, 0.0001f);
        Assert.assertEquals("on/off 不触发布局", 0, result.getRelayoutCount());
    }

    @Test
    public void enabledMotionShouldInterpolateThumbWithoutLayout() {
        runtime.__enableMotion();
        doLayout();
        thumbNode().clearDirtyFlags();

        onSignal.set(Boolean.TRUE);
        runtime.flush();
        runtime.__sampleMotion(1_000_000L);
        runtime.__sampleMotion(81_000_000L);

        Assert.assertEquals("standard 半程 translateX=12", 12.0f,
                thumbNode().getTransform().translateX, 0.0001f);
        Assert.assertTrue(thumbNode().__isCompositeDirty());
        Assert.assertFalse(thumbNode().__isSelfLayoutDirty());
        LayoutResult result = layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        Assert.assertEquals("Motion sample 零重排", 0, result.getRelayoutCount());

        runtime.__sampleMotion(161_000_000L);
        Assert.assertEquals(24.0f, thumbNode().getTransform().translateX, 0.0001f);
    }

    // ==================== 验收 6：交互根 SHRINK，命中宽=内容宽，行尾空白不命中 ====================

    /**
     * 交互根默认 SHRINK：root 宽严格小于 canvas 宽，且等于 track+gap+label 内容宽；
     * 行尾空白（root 右缘外）命中链不含 toggleRoot；内容区（track 中心）仍可点触发 onChange。
     *
     * <p>期望宽：TRACK_WIDTH(48) + GAP_MD(8) + "Night"(5)×STUB_CHAR_WIDTH(8)=40 → 96。</p>
     */
    @Test
    public void rootHitWidthShouldShrinkToContentNotFillParent() {
        doLayout();

        LayoutBox rootBox = (LayoutBox) toggleRoot.getCachedLayout();
        int expectedWidth = 48 + SceneChromeTokens.GAP_MD + ("Night".length() * STUB_CHAR_WIDTH);
        Assert.assertTrue("root 宽应严格小于 canvas，证明非 FILL 吞全宽",
                rootBox.getWidth() < CANVAS_WIDTH);
        Assert.assertEquals("root 宽 = track + gap + label 内容宽",
                expectedWidth, rootBox.getWidth());

        // 行尾空白：root 右缘 +1（仍在 canvas 内）不应命中 toggleRoot
        int missX = rootBox.getX() + rootBox.getWidth() + 1;
        int midY = rootBox.getY() + rootBox.getHeight() / 2;
        Assert.assertTrue("探测点应仍在 canvas 内", missX < CANVAS_WIDTH);
        List<SceneNode> missChain = new SceneHitTester().hitTest(sceneRoot, missX, midY, 0, 0);
        Assert.assertFalse("行尾空白命中链不应含 toggleRoot",
                missChain.contains(toggleRoot));

        // 内容区仍可点：track 中心触发 onChange
        int before = changeCount.get();
        harness.click(trackNode());
        Assert.assertEquals("内容区点击仍应触发 onChange", before + 1, changeCount.get());
        Assert.assertEquals("期望新值 true", Boolean.TRUE, lastChangeValue);
    }

    // ==================== 验收 7：默认工厂路径消费主题（配方 + 语义色） ====================

    /**
     * 默认工厂路径（不传任何样式参数）：track 的边框宽/圆角/染色/滤镜/实体高度全部等于
     * {@code SceneThemes.DEFAULT.surface(Role.INDICATOR)} 的对应值；thumb 取主题强调色、
     * label 取主题正文色；选中时 tint 的 RGB 换成强调色且保留配方 alpha（选中不只靠透明度）。
     */
    @Test
    public void defaultFactoryShouldConsumeIndicatorRecipeAndThemeForeground() {
        doLayout();

        Assert.assertEquals("track 圆角来自 INDICATOR 配方（不再是控件胶囊常量）",
                TRACK_SURFACE.getCornerRadius(), trackNode().getCornerRadius());
        Assert.assertEquals("track 边框宽来自配方", TRACK_SURFACE.getBorderWidth(), trackNode().getBorderWidth());
        Assert.assertNotNull("track 默认带液态玻璃滤镜（INDICATOR 配方）", trackNode().getBackdrop());
        Assert.assertEquals("backdrop 模糊半径来自配方", TRACK_SURFACE.getBackdrop().getBlurRadius(),
                trackNode().getBackdrop().getBlurRadius());
        Assert.assertEquals("实体高度来自配方 idle 档", TRACK_SURFACE.getIdle().getElevation(),
                trackNode().__getSurfaceElevation(), 0.0001F);
        Assert.assertEquals("track 未选中染色 = 配方 idle tint", TRACK_OFF_ENABLED, trackBackground());
        Assert.assertEquals("thumb 底色 = 主题强调色", THUMB_ENABLED, thumbNode().getBackgroundColor());
        Assert.assertEquals("label 前景 = 主题正文色", LABEL_ENABLED, labelNode().getTextColor());

        onSignal.set(Boolean.TRUE);
        runtime.flush();
        doLayout();

        Assert.assertEquals("选中 track 染色 = 配方 tint 换强调色", TRACK_ON_ENABLED, trackBackground());
        Assert.assertEquals("选中 tint 为强调色系", rgbOf(SceneThemes.DEFAULT.accent()), rgbOf(trackBackground()));
        Assert.assertEquals("选中保留配方 alpha（同 alpha 不同色，非仅透明度）",
                alphaOf(TRACK_OFF_ENABLED), alphaOf(trackBackground()));
        Assert.assertNotEquals("选中与未选中必须可区分", TRACK_OFF_ENABLED, trackBackground());
        Assert.assertEquals("thumb 在选中态保持强调色", THUMB_ENABLED, thumbNode().getBackgroundColor());
        Assert.assertEquals("选中不改变 label 前景", LABEL_ENABLED, labelNode().getTextColor());
    }

    // ==================== 验收 8：禁用态取禁用档与禁用前景 ====================

    /**
     * 禁用态：track 取角色配方禁用档，thumb 与 label 取主题禁用前景色；
     * 「选中 + 禁用」仍走禁用档（选中配方不覆盖 disabled 分支），受控值不受影响。
     */
    @Test
    public void disabledShouldUseDisabledRecipeAndDisabledForeground() {
        doLayout();

        enabledSignal.set(Boolean.FALSE);
        runtime.flush();
        doLayout();
        Assert.assertEquals("禁用 track 取配方禁用档", TRACK_DISABLED, trackBackground());
        Assert.assertEquals("禁用 thumb 取主题禁用前景色", THUMB_DISABLED, thumbNode().getBackgroundColor());
        Assert.assertEquals("禁用 label 取主题禁用前景色", LABEL_DISABLED, labelNode().getTextColor());

        // 选中 + 禁用：disabled 优先级最高，仍取角色禁用档
        onSignal.set(Boolean.TRUE);
        runtime.flush();
        doLayout();
        Assert.assertEquals("选中且禁用仍取禁用档", TRACK_DISABLED, trackBackground());
        Assert.assertEquals("禁用态不改变受控值", Boolean.TRUE, onSignal.get());

        // 恢复启用：回到选中档
        enabledSignal.set(Boolean.TRUE);
        runtime.flush();
        doLayout();
        Assert.assertEquals("恢复启用回到选中档", TRACK_ON_ENABLED, trackBackground());
        Assert.assertEquals("恢复启用 thumb 回强调色", THUMB_ENABLED, thumbNode().getBackgroundColor());
        Assert.assertEquals("恢复启用 label 回正文色", LABEL_ENABLED, labelNode().getTextColor());
    }

    // ==================== 验收 9：主题切换更新外观且不重建节点 / 不增订阅 ====================

    /**
     * 页面主题信号更新 + flush 后：track/thumb/label 外观随新主题更新，
     * 节点身份不变、effect 数不增长；卸载后绑定全部回收。
     */
    @Test
    public void themeSwitchShouldUpdateAppearanceWithoutRebuild() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        Assert.assertNotEquals("测试前提：深/浅 INDICATOR 配方必须不同",
                dark.surface(SceneTheme.Role.INDICATOR), light.surface(SceneTheme.Role.INDICATOR));

        int baseline = ReactiveTestProbe.registeredEffectCount();
        Signal<SceneTheme> pageTheme = Signal.create(dark);
        SceneToggle.Props props = new SceneToggle.Props(
                onSignal, labelSignal, enabledSignal, next -> lastChangeValue = next);

        SceneNode host = new SceneNode();
        MountHandle themed = runtime.mount(host, () -> {
            final SceneNode[] holder = new SceneNode[1];
            SceneThemes.withTheme(pageTheme, () -> holder[0] = SceneToggle.create(runtime, props).get());
            return holder[0];
        });
        runtime.flush();

        SceneNode themedRoot = themed.getRoot();
        SceneNode themedTrack = themedRoot.__getChildren().get(0);
        SceneNode themedThumb = themedTrack.__getChildren().get(0);
        SceneNode themedLabel = themedRoot.__getChildren().get(1);

        Assert.assertEquals("初始 track 取深色配方",
                dark.surface(SceneTheme.Role.INDICATOR).getIdle().getTint(), themedTrack.getBackgroundColor());
        Assert.assertEquals("初始 thumb 取深色强调色", dark.accent(), themedThumb.getBackgroundColor());
        Assert.assertEquals("初始 label 取深色正文色", dark.foreground(), themedLabel.getTextColor());

        int effectsBeforeSwitch = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(light);
        runtime.flush();

        Assert.assertSame("主题切换不重建 track 节点", themedTrack, themedRoot.__getChildren().get(0));
        Assert.assertSame("主题切换不重建 thumb 节点", themedThumb, themedTrack.__getChildren().get(0));
        Assert.assertSame("主题切换不重建 label 节点", themedLabel, themedRoot.__getChildren().get(1));
        Assert.assertEquals("track 随主题更新",
                light.surface(SceneTheme.Role.INDICATOR).getIdle().getTint(), themedTrack.getBackgroundColor());
        Assert.assertEquals("track 圆角随主题更新",
                light.surface(SceneTheme.Role.INDICATOR).getCornerRadius(), themedTrack.getCornerRadius());
        Assert.assertEquals("thumb 随主题更新", light.accent(), themedThumb.getBackgroundColor());
        Assert.assertEquals("label 随主题更新", light.foreground(), themedLabel.getTextColor());
        Assert.assertEquals("主题切换不新增订阅", effectsBeforeSwitch, ReactiveTestProbe.registeredEffectCount());

        themed.dispose();
        Assert.assertEquals("卸载后回收该实例全部绑定", baseline, ReactiveTestProbe.registeredEffectCount());
    }

    // ==================== 验收 10：卸载回收绑定（effect 探针） ====================

    /**
     * 挂载注册响应式绑定、卸载全部回收：{@code ReactiveTestProbe.registeredEffectCount()}
     * 回到挂载前基线（守「卸载即回收」纪律）。
     */
    @Test
    public void disposeShouldReclaimAllBindings() {
        int baseline = ReactiveTestProbe.registeredEffectCount();

        SceneToggle.Props props = new SceneToggle.Props(
                onSignal, labelSignal, enabledSignal, next -> lastChangeValue = next);
        MountHandle extra = runtime.mount(sceneRoot, SceneToggle.create(runtime, props));
        runtime.flush();

        int mounted = ReactiveTestProbe.registeredEffectCount();
        Assert.assertTrue("挂载应注册响应式绑定，baseline=" + baseline + ", mounted=" + mounted,
                mounted > baseline);

        extra.dispose();
        Assert.assertEquals("卸载回收全部绑定", baseline, ReactiveTestProbe.registeredEffectCount());
    }
}
