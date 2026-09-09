package club.heiqi.uilib.ui.scene.control;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
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
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * SceneCheckbox 端到端单元测试 —— Phase 4 批 1 受控双向控件验收。
 *
 * <p>构造 SceneRuntime + SceneLayoutEngine + ScenePaintEngine 三件套，端到端验证：
 * 受控双向闭环（点击只调 onChange 交还期望新值、控件零内部状态不自翻转）、
 * 命中穿透（点装饰子节点穿透到 root）、四态切换零重排（R-D 终极反证）、
 * 键盘激活（Enter/Space）。</p>
 *
 * <p><b>外观语义（G05 默认选中配方与主题前景）</b>：box 背景/边框/圆角/滤镜/浮雕高度来自
 * {@link SceneThemes#selectableSurface} 的 {@link SceneTheme.Role#INDICATOR} 角色配方
 * （未选中取角色配方、选中态换主题强调色 tint），勾选标记取
 * {@link SceneThemes#onAccentForeground}、label 前景取 {@link SceneThemes#foreground}/
 * {@link SceneThemes#disabledForeground}。外观断言一律取配方/主题值，集中调参时本类自动跟随。</p>
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

    /** 库默认主题的 INDICATOR 角色配方：默认工厂路径的外观唯一来源。 */
    private static final SceneSurfaceStyle INDICATOR_SURFACE =
            SceneThemes.DEFAULT.surface(SceneTheme.Role.INDICATOR);
    private static final int BOX_UNCHECKED_ENABLED = INDICATOR_SURFACE.getIdle().getTint();
    private static final int BOX_UNCHECKED_HOVER = INDICATOR_SURFACE.getHovered().getTint();
    private static final int BOX_UNCHECKED_PRESSED = INDICATOR_SURFACE.getPressed().getTint();
    private static final int BOX_DISABLED = INDICATOR_SURFACE.getDisabled().getTint();
    /** 选中态 tint = 角色配方 tint 的 alpha + 主题强调色 RGB（{@code selectableSurface} 的换色语义）。 */
    private static final int BOX_CHECKED_ENABLED =
            accentTinted(BOX_UNCHECKED_ENABLED, SceneThemes.DEFAULT.accent());
    private static final int CHECK_MARK_TRANSPARENT = 0x00000000;
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

    /** 勾选标记子节点（box 唯一孩子，常驻透明节点） */
    private SceneNode checkMarkNode() {
        return boxNode().__getChildren().get(0);
    }

    /** box 当前背景色 */
    private int boxBackground() {
        return boxNode().getBackgroundColor();
    }

    /** 角色配方 tint 换强调色 RGB、保留原 alpha —— 与 {@code SceneThemes.selectableSurface} 同语义的期望值算法。 */
    private static int accentTinted(int tint, int accent) {
        return (tint & 0xFF000000) | (accent & 0x00FFFFFF);
    }

    /** 在 PaintPlan 中按文本内容找 TEXT 命令 */
    private static PaintCommand textCommand(PaintPlan plan, String text) {
        for (PaintCommand command : plan.getCommands()) {
            if (command.getType() == PaintCommandType.TEXT && text.equals(command.getText())) {
                return command;
            }
        }
        return null;
    }

    /** 在 PaintPlan 中找首个指定类型命令 */
    private static PaintCommand firstOfType(PaintPlan plan, PaintCommandType type) {
        for (PaintCommand command : plan.getCommands()) {
            if (command.getType() == type) {
                return command;
            }
        }
        return null;
    }

    /** 统计指定类型命令数 */
    private static int countType(PaintPlan plan, PaintCommandType type) {
        int count = 0;
        for (PaintCommand command : plan.getCommands()) {
            if (command.getType() == type) {
                count++;
            }
        }
        return count;
    }

    /** 在局部主题作用域内重建 checkbox：验证来源主题继承与主题信号切换（重建 runtime 隔离默认主题）。 */
    private void remountInTheme(ReadableSignal<SceneTheme> theme) {
        runtime.dispose();
        ReactiveScheduler.get().reset();
        runtime = new SceneRuntime();
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
        handle = runtime.mount(sceneRoot, () -> {
            SceneNode page = new SceneNode();
            SceneThemes.withTheme(theme, () ->
                    checkboxRoot = runtime.mount(page, SceneCheckbox.create(runtime, props)).getRoot());
            return page;
        });
        runtime.flush();
    }

    @Test
    public void configMotionInterpolatesSelectableBackground() {
        runtime.__enableMotion();

        checkedSignal.set(Boolean.TRUE);
        runtime.flush();
        Assert.assertEquals("retarget 帧保持未选中起点", BOX_UNCHECKED_ENABLED, boxBackground());

        runtime.__sampleMotion(1_000_000L);
        runtime.__sampleMotion(81_000_000L);
        int midpoint = boxBackground();
        Assert.assertNotEquals("standard Motion 半程不得停在起点", BOX_UNCHECKED_ENABLED, midpoint);
        Assert.assertNotEquals("standard Motion 半程不得提前到终点", BOX_CHECKED_ENABLED, midpoint);

        runtime.__sampleMotion(INDICATOR_SURFACE.getTransitionMillis() * 1_000_000L + 1_000_000L);
        Assert.assertEquals("配方过渡时长到达选中背景", BOX_CHECKED_ENABLED, boxBackground());
    }

    // ==================== 验收 0：默认工厂路径 + 选中配方 + 主题前景 ====================

    /**
     * 默认工厂路径（不传任何样式参数）：box 属性等于 {@code SceneThemes.DEFAULT.surface(INDICATOR)}
     * 的对应值；label 取主题正文色；未选中勾号透明。
     */
    @Test
    public void defaultFactoryBoxFollowsIndicatorRecipeAndLabelFollowsThemeForeground() {
        doLayout();

        Assert.assertEquals("box 背景取 INDICATOR 配方 idle tint",
                INDICATOR_SURFACE.getIdle().getTint(), boxBackground());
        Assert.assertEquals("box 圆角取配方 cornerRadius（与原 RADIUS_SM 同值）",
                INDICATOR_SURFACE.getCornerRadius(), boxNode().getCornerRadius());
        Assert.assertEquals("box 圆角与原常量一致", SceneChromeTokens.RADIUS_SM, boxNode().getCornerRadius());
        Assert.assertEquals("box 边框宽取配方 borderWidth",
                INDICATOR_SURFACE.getBorderWidth(), boxNode().getBorderWidth());
        Assert.assertEquals("box 边框色取配方 idle edge",
                INDICATOR_SURFACE.getIdle().getEdge(), boxNode().getBorderColor());
        // 滤镜声明 + 动画样本合成：材质/模糊取配方，透镜强度 = 配方 lens × 当前状态 lensFactor。
        Assert.assertNotNull("box 滤镜跟随配方（非关闭滤镜）", boxNode().getBackdrop());
        Assert.assertEquals("box 滤镜模糊半径取配方",
                INDICATOR_SURFACE.getBackdrop().getBlurRadius(), boxNode().getBackdrop().getBlurRadius());
        Assert.assertEquals("box 滤镜材质取配方",
                INDICATOR_SURFACE.getBackdrop().getEffect().getMaterial(),
                boxNode().getBackdrop().getEffect().getMaterial());
        Assert.assertEquals("box 透镜强度 = 配方 lens × idle lensFactor",
                INDICATOR_SURFACE.getBackdrop().getEffect().getLensStrength()
                        * INDICATOR_SURFACE.getIdle().getLensFactor(),
                boxNode().getBackdrop().getEffect().getLensStrength(), 0.0001F);
        Assert.assertEquals("未选中勾号透明", CHECK_MARK_TRANSPARENT, checkMarkNode().getTextColor());
        Assert.assertEquals("label 前景取主题正文色",
                SceneThemes.DEFAULT.foreground(), labelNode().getTextColor());

        PaintPlan plan = doPaint();
        // 配方 idle elevation > 0 → 走浮雕路径：无独立 BORDER 命令，边框由方向性 ROUNDED_BAND 表达。
        // 16px 小盒的面短边仅 12px，浮雕圆角 = 配方圆角内缩 1px 后再按面短边一半夹取（6px）。
        PaintCommand boxBackground = firstOfType(plan, PaintCommandType.BACKGROUND);
        Assert.assertNotNull("box 应产出 BACKGROUND 命令", boxBackground);
        Assert.assertTrue("浮雕面圆角为配方圆角内缩 1px 后按面短边一半夹取",
                boxBackground.getCornerRadiusTopLeft() > 0
                        && boxBackground.getCornerRadiusTopLeft()
                                <= INDICATOR_SURFACE.getCornerRadius() - 1);
        Assert.assertEquals("实体浮雕路径不再单独发 BORDER 命令", 0,
                countType(plan, PaintCommandType.BORDER));
        Assert.assertTrue("边框由方向性倒角表达",
                countType(plan, PaintCommandType.ROUNDED_BAND) > 0);
        PaintCommand markText = textCommand(plan, "✓");
        Assert.assertNotNull("勾号节点应产出 TEXT 命令", markText);
        Assert.assertEquals("未选中勾号在绘制计划里透明", CHECK_MARK_TRANSPARENT,
                markText.getTextStyle().getColor());
        PaintCommand labelText = textCommand(plan, "Sound");
        Assert.assertNotNull("label 应产出 TEXT 命令", labelText);
        Assert.assertEquals("绘制计划 label 取主题正文色",
                SceneThemes.DEFAULT.foreground(), labelText.getTextStyle().getColor());
    }

    /**
     * 选中态：tint 换主题强调色 RGB、保留配方 alpha（选中不能只靠透明度区分），
     * 勾选标记取强调底前景；圆角/边框宽/滤镜等非染色分量保持角色配方。
     */
    @Test
    public void checkedBoxUsesAccentTintAndAccentForegroundMark() {
        doLayout();
        checkedSignal.set(Boolean.TRUE);
        runtime.flush();

        Assert.assertEquals("选中 tint = 配方 alpha + 主题强调色 RGB",
                BOX_CHECKED_ENABLED, boxBackground());
        Assert.assertEquals("选中 tint RGB 走主题强调色",
                SceneThemes.DEFAULT.accent() & 0x00FFFFFF, boxBackground() & 0x00FFFFFF);
        Assert.assertEquals("选中保留配方 alpha（不靠透明度区分）",
                INDICATOR_SURFACE.getIdle().getTint() & 0xFF000000, boxBackground() & 0xFF000000);
        Assert.assertNotEquals("选中与未选中必须换色而非只换透明度",
                INDICATOR_SURFACE.getIdle().getTint() & 0x00FFFFFF, boxBackground() & 0x00FFFFFF);

        Assert.assertEquals("选中不换角色配方圆角",
                INDICATOR_SURFACE.getCornerRadius(), boxNode().getCornerRadius());
        Assert.assertEquals("选中不换角色配方边框宽",
                INDICATOR_SURFACE.getBorderWidth(), boxNode().getBorderWidth());
        Assert.assertEquals("选中不换角色配方滤镜材质",
                INDICATOR_SURFACE.getBackdrop().getEffect().getMaterial(),
                boxNode().getBackdrop().getEffect().getMaterial());
        Assert.assertEquals("选中不换角色配方透镜强度",
                INDICATOR_SURFACE.getBackdrop().getEffect().getLensStrength()
                        * INDICATOR_SURFACE.getIdle().getLensFactor(),
                boxNode().getBackdrop().getEffect().getLensStrength(), 0.0001F);

        Assert.assertEquals("选中勾号取主题强调底前景",
                SceneThemes.DEFAULT.onAccentForeground(), checkMarkNode().getTextColor());
        PaintPlan plan = doPaint();
        PaintCommand markText = textCommand(plan, "✓");
        Assert.assertNotNull(markText);
        Assert.assertEquals("绘制计划勾号取主题强调底前景",
                SceneThemes.DEFAULT.onAccentForeground(), markText.getTextStyle().getColor());
    }

    /**
     * 禁用态：box 走 INDICATOR 配方禁用档（背景/边框），label 取主题禁用前景；恢复启用后回主题正文色。
     */
    @Test
    public void disabledUsesRecipeDisabledTierAndDisabledForeground() {
        doLayout();
        enabledSignal.set(Boolean.FALSE);
        runtime.flush();

        Assert.assertEquals("禁用 box 背景取配方禁用档",
                INDICATOR_SURFACE.getDisabled().getTint(), boxBackground());
        Assert.assertEquals("禁用 box 边框取配方禁用档",
                INDICATOR_SURFACE.getDisabled().getEdge(), boxNode().getBorderColor());
        Assert.assertEquals("禁用 label 取主题禁用前景",
                SceneThemes.DEFAULT.disabledForeground(), labelNode().getTextColor());

        PaintPlan plan = doPaint();
        PaintCommand labelText = textCommand(plan, "Sound");
        Assert.assertNotNull(labelText);
        Assert.assertEquals("绘制计划 label 取主题禁用前景",
                SceneThemes.DEFAULT.disabledForeground(), labelText.getTextStyle().getColor());

        enabledSignal.set(Boolean.TRUE);
        runtime.flush();
        Assert.assertEquals("恢复启用后 box 回角色配方 idle 档",
                INDICATOR_SURFACE.getIdle().getTint(), boxBackground());
        Assert.assertEquals("恢复启用后 label 回主题正文色",
                SceneThemes.DEFAULT.foreground(), labelNode().getTextColor());
    }

    /**
     * 主题切换：{@code withTheme} 来源主题信号变化 + flush 后 box/勾号/label 外观更新，
     * 节点身份不变、effect 数不增长（外观重算不重建节点、不重复订阅）。
     */
    @Test
    public void themeSwitchUpdatesBoxMarkAndLabelWithoutRebuildingNodes() {
        Signal<SceneTheme> pageTheme = Signal.create(SceneTheme.liquidGlassDark());
        remountInTheme(pageTheme);
        checkedSignal.set(Boolean.TRUE);
        runtime.flush();

        SceneNode box = boxNode();
        SceneNode mark = checkMarkNode();
        SceneNode label = labelNode();

        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        SceneSurfaceStyle darkIndicator = dark.surface(SceneTheme.Role.INDICATOR);
        SceneSurfaceStyle lightIndicator = light.surface(SceneTheme.Role.INDICATOR);
        Assert.assertNotEquals("两个主题的 INDICATOR 配方必须不同，否则切换不传播",
                darkIndicator.getIdle(), lightIndicator.getIdle());

        Assert.assertEquals("深色档选中 box tint",
                accentTinted(darkIndicator.getIdle().getTint(), dark.accent()), box.getBackgroundColor());
        Assert.assertEquals("深色档勾号取强调底前景", dark.onAccentForeground(), mark.getTextColor());
        Assert.assertEquals("深色档 label 前景", dark.foreground(), label.getTextColor());

        int effectsBefore = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(light);
        runtime.flush();

        Assert.assertEquals("切浅色档 box tint 更新",
                accentTinted(lightIndicator.getIdle().getTint(), light.accent()), box.getBackgroundColor());
        Assert.assertEquals("切浅色档 box 边框更新", lightIndicator.getIdle().getEdge(), box.getBorderColor());
        Assert.assertEquals("切浅色档勾号更新", light.onAccentForeground(), mark.getTextColor());
        Assert.assertEquals("切浅色档 label 前景更新", light.foreground(), label.getTextColor());

        Assert.assertSame("主题切换不重建 box", box, checkboxRoot.__getChildren().get(0));
        Assert.assertSame("主题切换不重建勾号", mark, boxNode().__getChildren().get(0));
        Assert.assertSame("主题切换不重建 label", label, checkboxRoot.__getChildren().get(1));
        Assert.assertEquals("主题切换不新增 effect", effectsBefore, ReactiveTestProbe.registeredEffectCount());

        doLayout();
        PaintPlan plan = doPaint();
        PaintCommand markText = textCommand(plan, "✓");
        PaintCommand labelText = textCommand(plan, "Sound");
        Assert.assertNotNull(markText);
        Assert.assertNotNull(labelText);
        Assert.assertEquals("绘制计划勾号跟随新主题", light.onAccentForeground(), markText.getTextStyle().getColor());
        Assert.assertEquals("绘制计划 label 跟随新主题", light.foreground(), labelText.getTextStyle().getColor());
    }

    /** 卸载后表面/前景/勾号 effect 全部回收，checked/enabled 更新不再写入旧节点。 */
    @Test
    public void unmountReleasesSurfaceAndForegroundBindings() {
        int before = ReactiveTestProbe.registeredEffectCount();
        Signal<Boolean> extraChecked = Signal.create(Boolean.FALSE);
        Signal<Boolean> extraEnabled = Signal.create(Boolean.TRUE);
        MountHandle extra = runtime.mount(sceneRoot, SceneCheckbox.create(runtime, new SceneCheckbox.Props(
                extraChecked, Signal.create("Extra"), extraEnabled, next -> { })));
        runtime.flush();
        Assert.assertTrue("Checkbox 默认路径应注册响应式外观绑定",
                ReactiveTestProbe.registeredEffectCount() > before);

        SceneNode extraBox = extra.getRoot().__getChildren().get(0);
        int colorBeforeDispose = extraBox.getBackgroundColor();
        extra.dispose();
        Assert.assertEquals("卸载后外观绑定 effect 应回收",
                before, ReactiveTestProbe.registeredEffectCount());

        extraChecked.set(Boolean.TRUE);
        extraEnabled.set(Boolean.FALSE);
        runtime.flush();
        Assert.assertEquals("卸载后 checked/enabled 更新不再写入旧 box",
                colorBeforeDispose, extraBox.getBackgroundColor());
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
