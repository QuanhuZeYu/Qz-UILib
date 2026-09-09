package club.heiqi.uilib.ui.scene.control;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.UiBackdrop;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.SceneOverlayHost;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * {@link SceneAutocomplete} 成品壳测试：默认输入表面（INPUT 配方）+ 候选浮层（OVERLAY 配方）+
 * 候选行轻量状态覆盖，以及补全/键盘选择/延迟打开/关闭行为保持。
 *
 * <p>覆盖：
 * <ul>
 *   <li>默认工厂路径：输入区 = {@link SceneThemes#DEFAULT} 的 INPUT 角色配方、候选浮层 = OVERLAY
 *       角色配方，各只采样一次滤镜，候选行不各自采样背景。</li>
 *   <li>输入表面四态（disabled &gt; pressed &gt; hovered &gt; idle；focus 只覆盖缘色）与主题语义前景。</li>
 *   <li>禁用态可读：表面落配方禁用档、前景落 disabledForeground 且不透明、文本仍进 PaintPlan。</li>
 *   <li>主题切换：输入区与浮层随主题更新，候选列表/键盘高亮/选区不丢，节点身份不变，effect 数不增长。</li>
 *   <li>卸载回收：handle.dispose + flush 后 effect 回到基线、浮层卸载。</li>
 *   <li>行为保持：聚焦未输入不展开、打字展开、键盘 ARROW/ENTER 提交、ESC 关闭、再次打字重弹。</li>
 * </ul>
 */
public class SceneAutocompleteTest {

    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    private ScenePaintEngine paintEngine;
    private SceneInteractionHarness harness;

    private Signal<String> valueSignal;
    private Signal<Boolean> enabledSignal;
    private Signal<Boolean> readOnlySignal;
    private AtomicInteger changeCount;
    private String lastChange;
    private String lastSelect;

    private MountHandle handle;
    private SceneNode inputRoot;

    private static final int CANVAS_WIDTH = 400;
    private static final int CANVAS_HEIGHT = 160;
    private static final int STUB_CHAR_WIDTH = 8;
    private static final int LINE_HEIGHT = 16;
    private static final int PADDING = SceneChromeTokens.PAD_MD;
    private static final float EPSILON = 0.0001F;

    private static final List<String> CANDIDATES = Arrays.asList(
            "Arial", "Arial Black", "Calibri", "Cambria", "Consolas");

    /** 库默认主题的 INPUT 角色配方：输入区默认外观唯一来源。 */
    private static final SceneSurfaceStyle INPUT_SURFACE =
            SceneThemes.DEFAULT.surface(SceneTheme.Role.INPUT);
    /** 库默认主题的 OVERLAY 角色配方：候选浮层默认外观唯一来源。 */
    private static final SceneSurfaceStyle OVERLAY_SURFACE =
            SceneThemes.DEFAULT.surface(SceneTheme.Role.OVERLAY);
    private static final int INPUT_BG_IDLE = INPUT_SURFACE.getIdle().getTint();
    private static final int INPUT_BG_HOVER = INPUT_SURFACE.getHovered().getTint();
    private static final int INPUT_BG_DISABLED = INPUT_SURFACE.getDisabled().getTint();
    private static final int INPUT_BORDER_IDLE = INPUT_SURFACE.getIdle().getEdge();
    /** 聚焦缘色来自配方 focusEdge（表面绑定器覆盖缘色），与 caret 的主题聚焦色是两条通道。 */
    private static final int INPUT_BORDER_FOCUS = INPUT_SURFACE.getFocusEdge();
    private static final int OVERLAY_BG_IDLE = OVERLAY_SURFACE.getIdle().getTint();
    private static final int TEXT_NORMAL = SceneThemes.DEFAULT.foreground();
    private static final int TEXT_MUTED = SceneThemes.DEFAULT.mutedForeground();
    private static final int TEXT_DISABLED = SceneThemes.DEFAULT.disabledForeground();
    private static final int SELECTION_BG = SceneThemes.DEFAULT.selectionBackground();
    private static final int SELECTION_TEXT = SceneThemes.DEFAULT.selectionForeground();
    private static final int CARET_COLOR = SceneThemes.DEFAULT.borderFocus();

    /** 候选行轻量覆盖强度（与实现常量同源；断言取派生色而非硬编码色号）。 */
    private static final int ITEM_HOVER_ALPHA = 0x1F;
    private static final int ITEM_HIGHLIGHT_ALPHA = 0x59;
    private static final int ITEM_BG_TRANSPARENT = 0x00000000;
    private static final int ITEM_BG_HOVER = tint(SceneThemes.DEFAULT.accent(), ITEM_HOVER_ALPHA);
    private static final int ITEM_BG_HIGHLIGHT = tint(SELECTION_BG, ITEM_HIGHLIGHT_ALPHA);

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(STUB_CHAR_WIDTH, LINE_HEIGHT);
        harness = SceneInteractionHarness.create(measurer);
        runtime = harness.getRuntime();
        layoutEngine = new SceneLayoutEngine(measurer);
        paintEngine = new ScenePaintEngine(measurer);
        sceneRoot = new SceneNode();
        harness.mountRoot(sceneRoot, CANVAS_WIDTH, CANVAS_HEIGHT);
    }

    @After
    public void tearDown() {
        if (runtime != null) {
            runtime.dispose();
        }
        ReactiveScheduler.get().reset();
    }

    // ==================== 挂载辅助 ====================

    private SceneAutocomplete.Props buildProps() {
        return new SceneAutocomplete.Props(
                valueSignal, enabledSignal, readOnlySignal,
                "字体名", 64, CANDIDATES,
                SceneAutocompletePrimitive.MatchMode.PREFIX, 8,
                next -> {
                    changeCount.incrementAndGet();
                    lastChange = next;
                },
                sel -> {
                    lastSelect = sel;
                    // 受控消费者语义：选中候选即回写受控 value
                    valueSignal.set(sel);
                });
    }

    private void resetSignals() {
        valueSignal = Signal.create("");
        enabledSignal = Signal.create(Boolean.TRUE);
        readOnlySignal = Signal.create(Boolean.FALSE);
        changeCount = new AtomicInteger(0);
        lastChange = null;
        lastSelect = null;
    }

    private void mountAutocomplete() {
        resetSignals();
        handle = runtime.mount(sceneRoot, SceneAutocomplete.create(runtime, buildProps()));
        inputRoot = handle.getRoot();
        runtime.flush();
    }

    /** 在可切换的局部主题作用域内挂载（{@link SceneThemes#withTheme}）：主题变化驱动外观派生，不重建节点。 */
    private void mountAutocompleteWithTheme(Signal<SceneTheme> theme) {
        resetSignals();
        final SceneNode[] holder = new SceneNode[1];
        handle = runtime.mount(sceneRoot, () -> {
            SceneThemes.withTheme(theme, () -> holder[0] = SceneAutocomplete.create(runtime, buildProps()).get());
            return holder[0];
        });
        inputRoot = handle.getRoot();
        runtime.flush();
    }

    // ==================== 行为/几何辅助 ====================

    private void doLayout() {
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        for (SceneOverlayHost.Entry entry : runtime.getOverlayHost().bottomFirst()) {
            layoutEngine.layout(entry.getRoot(), new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        }
    }

    private SceneNode prefixNode() {
        return inputRoot.__getChildren().get(0);
    }

    private SceneNode caretNode() {
        return inputRoot.__getChildren().get(1);
    }

    private SceneNode highlightNode() {
        return inputRoot.__getChildren().get(2);
    }

    private SceneNode caretAfterNode() {
        return inputRoot.__getChildren().get(3);
    }

    private SceneNode suffixNode() {
        return inputRoot.__getChildren().get(4);
    }

    private SceneNode overlayRoot() {
        return runtime.getOverlayHost().bottomFirst().get(0).getRoot();
    }

    private int overlayItemCount() {
        return overlayRoot().__getChildren().size();
    }

    private SceneNode overlayItem(int index) {
        return overlayRoot().__getChildren().get(index);
    }

    private SceneNode itemLabel(int index) {
        return overlayItem(index).__getChildren().get(0);
    }

    private void routeKey(SceneKey key) {
        harness.pressKey(key);
    }

    /** Ctrl+A 全选注入（harness 不覆盖修饰键组合，白盒回退）。 */
    private void ctrlSelectAll() {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofKey(SceneKey.KEY_A, SceneKeyAction.PRESSED,
                true, false, false, false, 0, 0, 1000L));
        runtime.route(sceneRoot, fb.drainFrame(), 0, 0);
        runtime.flush();
    }

    /** 打字并把 onChange 上抛值回灌受控 value（补全浮层由 value→filtered→effect 驱动）。 */
    private void typeAndWriteBack(String text) {
        harness.typeText(text);
        if (lastChange != null) {
            valueSignal.set(lastChange);
            runtime.flush();
        }
    }

    /** 聚焦并打字展开候选浮层。 */
    private void openOverlay() {
        runtime.requestFocus(inputRoot);
        runtime.flush();
        typeAndWriteBack("Ari");
        doLayout();
    }

    private PaintPlan doPaint(SceneNode root) {
        return paintEngine.paint(root).getPlan();
    }

    private static int countType(List<PaintCommand> cmds, PaintCommandType type) {
        int count = 0;
        for (PaintCommand cmd : cmds) {
            if (cmd.getType() == type) {
                count++;
            }
        }
        return count;
    }

    private static int indexOfType(List<PaintCommand> cmds, PaintCommandType type) {
        for (int i = 0; i < cmds.size(); i++) {
            if (cmds.get(i).getType() == type) {
                return i;
            }
        }
        return -1;
    }

    private static PaintCommand firstOfType(List<PaintCommand> cmds, PaintCommandType type) {
        int index = indexOfType(cmds, type);
        return index < 0 ? null : cmds.get(index);
    }

    private static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    private static int tint(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    // ==================== 1. 默认工厂路径 ====================

    /**
     * create 返回的 Supplier 可执行并产出带 padding/主题边框的根节点（外观断言按新语义更新）。
     */
    @Test
    public void createMountsRootWithChrome() {
        mountAutocomplete();
        Assert.assertNotNull("根节点不应为 null", inputRoot);
        Assert.assertEquals("应设 padding（chrome）", PADDING, inputRoot.getPaddingLeft());
        Assert.assertTrue("应设边框宽度", inputRoot.getBorderWidth() > 0);
        Assert.assertEquals("边框宽 = INPUT 配方", INPUT_SURFACE.getBorderWidth(), inputRoot.getBorderWidth());
        Assert.assertEquals("圆角 = INPUT 配方", INPUT_SURFACE.getCornerRadius(), inputRoot.getCornerRadius());
    }

    /**
     * 默认工厂路径：输入区表面等于 {@link SceneThemes#DEFAULT} 的 INPUT 角色配方（含滤镜与实体高度），
     * 候选浮层等于 OVERLAY 角色配方；两侧各只采样一次滤镜，候选行不各自采样背景。
     */
    @Test
    public void defaultFactoryPathUsesInputAndOverlayRecipesWithSingleBackdropSample() {
        mountAutocomplete();
        doLayout();

        // 输入区 = INPUT 配方
        Assert.assertEquals("输入区背景 = INPUT 配方 idle 染色", INPUT_BG_IDLE, inputRoot.getBackgroundColor());
        Assert.assertEquals("输入区边框 = INPUT 配方 idle 缘色", INPUT_BORDER_IDLE, inputRoot.getBorderColor());
        Assert.assertEquals("输入区边框宽 = INPUT 配方", INPUT_SURFACE.getBorderWidth(), inputRoot.getBorderWidth());
        Assert.assertEquals("输入区圆角 = INPUT 配方", INPUT_SURFACE.getCornerRadius(), inputRoot.getCornerRadius());
        Assert.assertEquals("输入区实体高度 = INPUT 配方", INPUT_SURFACE.getIdle().getElevation(),
                inputRoot.__getSurfaceElevation(), EPSILON);
        UiBackdrop inputRecipeBackdrop = INPUT_SURFACE.getBackdrop();
        Assert.assertNotNull("INPUT 配方自带滤镜", inputRecipeBackdrop);
        Assert.assertNotNull("默认工厂路径输入区应写入液态滤镜", inputRoot.getBackdrop());
        Assert.assertEquals("输入区模糊半径 = 配方", inputRecipeBackdrop.getBlurRadius(),
                inputRoot.getBackdrop().getBlurRadius());
        Assert.assertEquals("输入区材质 = 配方", inputRecipeBackdrop.getEffect().getMaterial(),
                inputRoot.getBackdrop().getEffect().getMaterial());

        PaintPlan inputPlan = doPaint(sceneRoot);
        Assert.assertEquals("输入区只采样一次滤镜", 1,
                countType(inputPlan.getCommands(), PaintCommandType.BACKDROP));

        // 候选浮层 = OVERLAY 配方
        openOverlay();
        Assert.assertEquals("打字后展开浮层", 1, runtime.getOverlayHost().size());
        Assert.assertEquals("候选 = Arial + Arial Black", 2, overlayItemCount());

        SceneNode listbox = overlayRoot();
        Assert.assertEquals("浮层背景 = OVERLAY 配方 idle 染色", OVERLAY_BG_IDLE, listbox.getBackgroundColor());
        Assert.assertEquals("浮层边框 = OVERLAY 配方 idle 缘色", OVERLAY_SURFACE.getIdle().getEdge(),
                listbox.getBorderColor());
        Assert.assertEquals("浮层边框宽 = OVERLAY 配方", OVERLAY_SURFACE.getBorderWidth(),
                listbox.getBorderWidth());
        Assert.assertEquals("浮层圆角 = OVERLAY 配方", OVERLAY_SURFACE.getCornerRadius(),
                listbox.getCornerRadius());
        Assert.assertEquals("浮层实体高度 = OVERLAY 配方", OVERLAY_SURFACE.getIdle().getElevation(),
                listbox.__getSurfaceElevation(), EPSILON);
        Assert.assertNotNull("浮层应写入 OVERLAY 配方滤镜", listbox.getBackdrop());
        Assert.assertEquals("浮层材质 = OVERLAY 配方", OVERLAY_SURFACE.getBackdrop().getEffect().getMaterial(),
                listbox.getBackdrop().getEffect().getMaterial());

        PaintPlan overlayPlan = doPaint(listbox);
        List<PaintCommand> overlayCommands = overlayPlan.getCommands();
        Assert.assertEquals("一颗浮层表面只采样一次滤镜", 1,
                countType(overlayCommands, PaintCommandType.BACKDROP));
        int backgroundIndex = indexOfType(overlayCommands, PaintCommandType.BACKGROUND);
        Assert.assertTrue("浮层应有 BACKGROUND（半透明 tint 叠玻璃之上）", backgroundIndex >= 0);
        Assert.assertTrue("滤镜之上不得压不透明底盖",
                alpha(overlayCommands.get(backgroundIndex).getColor()) < 0xFF);

        // 候选行不各自采样背景
        for (int i = 0; i < overlayItemCount(); i++) {
            Assert.assertNull("候选行 " + i + " 不得挂滤镜", overlayItem(i).getBackdrop());
            Assert.assertEquals("候选行 " + i + " 默认背景透明（露出浮层玻璃）",
                    ITEM_BG_TRANSPARENT, overlayItem(i).getBackgroundColor());
            Assert.assertEquals("候选行 " + i + " label 前景 = 主题正文前景", TEXT_NORMAL,
                    itemLabel(i).getTextColor());
        }
        Assert.assertEquals("候选文本保持", "Arial", itemLabel(0).getText());
        Assert.assertEquals("候选文本保持", "Arial Black", itemLabel(1).getText());
    }

    /**
     * 输入表面四态来自 INPUT 配方：disabled &gt; pressed &gt; hovered &gt; idle；
     * focus 只覆盖非禁用态缘色，不改染色。前景/caret/选区取主题语义色。
     */
    @Test
    public void inputSurfaceFollowsInputRecipeStatesAndThemeSemanticColors() {
        mountAutocomplete();
        doLayout();
        Assert.assertEquals("idle 染色来自 INPUT 配方", INPUT_BG_IDLE, inputRoot.getBackgroundColor());
        Assert.assertEquals("idle 缘色来自 INPUT 配方", INPUT_BORDER_IDLE, inputRoot.getBorderColor());
        Assert.assertEquals("占位前景 = 主题 mutedForeground", TEXT_MUTED, prefixNode().getTextColor());

        harness.moveTo(inputRoot);
        Assert.assertEquals("hover 取配方 hovered 档", INPUT_BG_HOVER, inputRoot.getBackgroundColor());

        runtime.requestFocus(inputRoot);
        runtime.flush();
        Assert.assertEquals("focus 不改染色（只改缘色）", INPUT_BG_HOVER, inputRoot.getBackgroundColor());
        Assert.assertEquals("focus 缘色取配方 focusEdge", INPUT_BORDER_FOCUS, inputRoot.getBorderColor());
        Assert.assertEquals("caret 取主题聚焦色", CARET_COLOR, caretNode().getBackgroundColor());

        enabledSignal.set(Boolean.FALSE);
        runtime.flush();
        Assert.assertEquals("disabled 压过 hover/focus", INPUT_BG_DISABLED, inputRoot.getBackgroundColor());
        Assert.assertEquals("disabled 缘色取配方禁用档",
                INPUT_SURFACE.getDisabled().getEdge(), inputRoot.getBorderColor());
    }

    /**
     * 禁用态仍可读：表面落配方禁用档，正文落主题 disabledForeground（不透明），文本照常进 PaintPlan；
     * 禁用不展开候选浮层（行为语义不变）。
     */
    @Test
    public void disabledAutocompleteStaysReadableAndNeverExpands() {
        mountAutocomplete();
        valueSignal.set("Ari");
        enabledSignal.set(Boolean.FALSE);
        runtime.flush();
        doLayout();

        Assert.assertEquals("禁用表面取配方禁用档", INPUT_BG_DISABLED, inputRoot.getBackgroundColor());
        Assert.assertEquals("禁用正文取主题禁用前景", TEXT_DISABLED, prefixNode().getTextColor());
        Assert.assertEquals("禁用前景不透明可读", 0xFF, alpha(TEXT_DISABLED));

        PaintPlan plan = doPaint(sceneRoot);
        PaintCommand text = firstOfType(plan.getCommands(), PaintCommandType.TEXT);
        Assert.assertNotNull("禁用态仍绘制文本", text);
        Assert.assertEquals("PaintPlan 文本色 = 主题禁用前景", TEXT_DISABLED, text.getTextStyle().getColor());

        runtime.requestFocus(inputRoot);
        runtime.flush();
        Assert.assertTrue("禁用不展开候选浮层", runtime.getOverlayHost().isEmpty());
    }

    // ==================== 2. 候选行轻量状态覆盖 ====================

    /**
     * 候选行 hover/键盘高亮 = 主题语义色半透明轻量覆盖：hover 取 accent 低透明度、
     * 键盘高亮取选区背景更高透明度（明显强于 hover），默认透明露出浮层玻璃；行内不装滤镜。
     */
    @Test
    public void candidateRowHoverAndKeyboardHighlightUseThemeDerivedOverlays() {
        mountAutocomplete();
        doLayout();
        openOverlay();

        Assert.assertEquals("默认候选行透明", ITEM_BG_TRANSPARENT, overlayItem(0).getBackgroundColor());

        harness.moveTo(overlayItem(0));
        Assert.assertEquals("hover 覆盖 = 主题 accent 低透明度", ITEM_BG_HOVER,
                overlayItem(0).getBackgroundColor());
        Assert.assertEquals("未 hover 的行仍透明", ITEM_BG_TRANSPARENT, overlayItem(1).getBackgroundColor());

        routeKey(SceneKey.ARROW_DOWN);
        Assert.assertEquals("键盘高亮覆盖 = 主题选区背景半透明", ITEM_BG_HIGHLIGHT,
                overlayItem(0).getBackgroundColor());
        Assert.assertTrue("高亮强度高于 hover", alpha(ITEM_BG_HIGHLIGHT) > alpha(ITEM_BG_HOVER));
        Assert.assertTrue("行覆盖为半透明（玻璃可见）", alpha(ITEM_BG_HIGHLIGHT) < 0xFF);

        routeKey(SceneKey.ARROW_DOWN);
        Assert.assertEquals("高亮移动到第二项", ITEM_BG_HIGHLIGHT, overlayItem(1).getBackgroundColor());
        Assert.assertEquals("第一项回落到透明/hover 覆盖", ITEM_BG_HOVER, overlayItem(0).getBackgroundColor());
    }

    // ==================== 3. 主题切换 ====================

    /**
     * 主题切换（深色 → 浅色）：输入区与候选浮层表面随主题更新，候选列表与键盘高亮、选区、
     * 受控值不丢，节点身份不变，effect 数不增长，且不触发 onChange。
     */
    @Test
    public void themeSwitchUpdatesInputAndListboxWithoutRebuildOrStateLoss() {
        Signal<SceneTheme> pageTheme = Signal.create(SceneTheme.liquidGlassDark());
        mountAutocompleteWithTheme(pageTheme);
        doLayout();

        // 聚焦 + 打字展开浮层 + 键盘高亮第一项
        runtime.requestFocus(inputRoot);
        runtime.flush();
        typeAndWriteBack("Ari");
        doLayout();
        routeKey(SceneKey.ARROW_DOWN);
        doLayout();

        SceneNode inputIdentity = inputRoot;
        SceneNode listboxIdentity = overlayRoot();
        SceneNode itemIdentity = overlayItem(0);
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        SceneSurfaceStyle lightInput = light.surface(SceneTheme.Role.INPUT);
        SceneSurfaceStyle lightOverlay = light.surface(SceneTheme.Role.OVERLAY);

        Assert.assertEquals("深色档输入区染色", dark.surface(SceneTheme.Role.INPUT).getIdle().getTint(),
                inputRoot.getBackgroundColor());
        Assert.assertEquals("深色档聚焦缘色 = 深色配方 focusEdge",
                dark.surface(SceneTheme.Role.INPUT).getFocusEdge(), inputRoot.getBorderColor());
        Assert.assertEquals("深色档浮层染色", dark.surface(SceneTheme.Role.OVERLAY).getIdle().getTint(),
                listboxIdentity.getBackgroundColor());
        Assert.assertEquals("深色档候选行键盘高亮 = 深色选区背景半透明",
                tint(dark.selectionBackground(), ITEM_HIGHLIGHT_ALPHA), itemIdentity.getBackgroundColor());
        Assert.assertEquals("深色档候选 label 前景 = 深色正文", dark.foreground(), itemLabel(0).getTextColor());
        Assert.assertEquals("深色档正文前景 = 主题 foreground", TEXT_NORMAL, prefixNode().getTextColor());

        int effectsBeforeSwitch = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(light);
        runtime.flush();

        Assert.assertSame("主题切换不重建输入根", inputIdentity, inputRoot);
        Assert.assertSame("主题切换不重建候选浮层", listboxIdentity, overlayRoot());
        Assert.assertSame("主题切换不重建候选行", itemIdentity, overlayItem(0));

        Assert.assertEquals("输入区染色随主题更新", lightInput.getIdle().getTint(), inputRoot.getBackgroundColor());
        Assert.assertEquals("聚焦缘色随主题更新（取配方 focusEdge）", lightInput.getFocusEdge(),
                inputRoot.getBorderColor());
        Assert.assertEquals("输入区圆角随主题更新", lightInput.getCornerRadius(), inputRoot.getCornerRadius());
        Assert.assertEquals("浮层染色随主题更新", lightOverlay.getIdle().getTint(),
                listboxIdentity.getBackgroundColor());
        Assert.assertEquals("浮层缘色随主题更新", lightOverlay.getIdle().getEdge(),
                listboxIdentity.getBorderColor());
        Assert.assertEquals("候选行键盘高亮随主题更新 = 浅色选区背景半透明",
                tint(light.selectionBackground(), ITEM_HIGHLIGHT_ALPHA), itemIdentity.getBackgroundColor());
        Assert.assertEquals("候选 label 前景随主题更新", light.foreground(), itemLabel(0).getTextColor());
        Assert.assertEquals("正文前景随主题更新", light.foreground(), prefixNode().getTextColor());

        Assert.assertEquals("候选列表不丢", 2, overlayItemCount());
        Assert.assertEquals("候选文本不丢", "Arial", itemLabel(0).getText());
        Assert.assertEquals("候选文本不丢", "Arial Black", itemLabel(1).getText());
        Assert.assertEquals("受控值不丢", "Ari", valueSignal.get());
        Assert.assertEquals("切主题不触发 onChange", 1, changeCount.get());
        Assert.assertEquals("切主题不新增 effect 订阅", effectsBeforeSwitch,
                ReactiveTestProbe.registeredEffectCount());
    }

    /**
     * 选区在主题切换中保留并随主题更新（输入中更新主题保留文本/选区）。
     */
    @Test
    public void selectionSurvivesThemeSwitchAndFollowsThemeColors() {
        Signal<SceneTheme> pageTheme = Signal.create(SceneTheme.liquidGlassDark());
        mountAutocompleteWithTheme(pageTheme);
        doLayout();
        runtime.requestFocus(inputRoot);
        runtime.flush();
        typeAndWriteBack("Ari");
        doLayout();

        ctrlSelectAll();
        doLayout();

        Assert.assertEquals("全选后选中段文本", "Ari", highlightNode().getText());
        Assert.assertEquals("深色档选区背景 = 主题 selectionBackground", SELECTION_BG,
                highlightNode().getBackgroundColor());
        Assert.assertEquals("深色档选区前景 = 主题 selectionForeground", SELECTION_TEXT,
                highlightNode().getTextColor());

        SceneTheme light = SceneTheme.liquidGlassLight();
        pageTheme.set(light);
        runtime.flush();

        Assert.assertEquals("选区文本不丢", "Ari", highlightNode().getText());
        Assert.assertEquals("选区背景随主题更新", light.selectionBackground(), highlightNode().getBackgroundColor());
        Assert.assertEquals("选区前景随主题更新", light.selectionForeground(), highlightNode().getTextColor());
    }

    // ==================== 4. 卸载回收 ====================

    /**
     * 卸载回收：handle.dispose + flush 后，绑定注册的 effect 回到基线，浮层一并卸载。
     */
    @Test
    public void disposeReleasesAutocompleteBindingsAndOverlay() {
        int baseline = ReactiveTestProbe.registeredEffectCount();
        mountAutocomplete();
        doLayout();
        openOverlay();
        Assert.assertEquals("浮层已展开", 1, runtime.getOverlayHost().size());
        Assert.assertTrue("绑定应新增订阅", ReactiveTestProbe.registeredEffectCount() > baseline);

        handle.dispose();
        runtime.flush();
        Assert.assertTrue("卸载后浮层移除", runtime.getOverlayHost().isEmpty());
        Assert.assertEquals("卸载回收全部绑定", baseline, ReactiveTestProbe.registeredEffectCount());
    }

    // ==================== 5. 行为保持 ====================

    /**
     * 补全/键盘选择/延迟打开/关闭行为不变：聚焦未输入不展开；打字展开；ARROW_DOWN + ENTER 提交
     * 高亮候选并关闭；再次打字重弹；ESC 关闭。
     */
    @Test
    public void keyboardCommitAndDelayedReopenSemanticsUnchanged() {
        mountAutocomplete();
        doLayout();

        runtime.requestFocus(inputRoot);
        runtime.flush();
        Assert.assertTrue("聚焦但未输入不展开", runtime.getOverlayHost().isEmpty());

        typeAndWriteBack("Ari");
        doLayout();
        Assert.assertEquals("打字后展开一个浮层", 1, runtime.getOverlayHost().size());
        Assert.assertEquals("候选 = Arial + Arial Black", 2, overlayItemCount());

        routeKey(SceneKey.ARROW_DOWN);
        routeKey(SceneKey.ENTER);
        Assert.assertEquals("ENTER 提交键盘高亮候选", "Arial", lastSelect);
        Assert.assertEquals("提交后受控值回写候选", "Arial", valueSignal.get());
        Assert.assertTrue("提交后浮层关闭", runtime.getOverlayHost().isEmpty());

        // 清空后再次打字 → 重弹（延迟打开语义不变）
        valueSignal.set("");
        runtime.flush();
        Assert.assertTrue("清空后无候选浮层", runtime.getOverlayHost().isEmpty());
        typeAndWriteBack("Cal");
        doLayout();
        Assert.assertEquals("再次打字重弹", 1, runtime.getOverlayHost().size());
        Assert.assertEquals("候选 = Calibri", "Calibri", itemLabel(0).getText());

        routeKey(SceneKey.ESCAPE);
        Assert.assertTrue("ESC 关闭浮层", runtime.getOverlayHost().isEmpty());
        Assert.assertEquals("ESC 不改文本", "Cal", valueSignal.get());
    }
}
