package club.heiqi.uilib.ui.scene.integration;

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
import club.heiqi.uilib.ui.scene.control.SceneSelect;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
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
 * SceneSelect 端到端单元测试 —— R8 受控选择 + R11 signal→portal 浮层契约验收。
 *
 * <p>外观按主题化后语义断言：触发器消费 {@link SceneTheme.Role#INPUT INPUT 角色配方}与主题
 * {@code foreground}/{@code mutedForeground}/{@code disabledForeground}，弹出底座消费
 * {@link SceneTheme.Role#OVERLAY OVERLAY 角色配方}，候选行只做 hover/选中/键盘高亮的轻量
 * 半透明覆盖（默认透明、行不装滤镜）。portal、键盘导航、选中源、焦点返回与值保持语义不变。</p>
 */
public class SceneSelectTest {

    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    private ScenePaintEngine paintEngine;
    private Signal<Integer> selectedSignal;
    private Signal<Boolean> enabledSignal;
    private AtomicInteger selectCount;
    private Integer lastSelectValue;
    private MountHandle handle;
    private SceneNode trigger;
    /** 语义化交互注入 harness；其 runtime 即上方 runtime 字段。
     *  可用于主树 trigger 点击，以及 anchor=0 测试沙箱中的 overlay item click/moveTo/
     *  pressReleaseAcrossFrames 等行为回归；锚点定位精度测试仍不能用 harness，需调用方自取几何。 */
    private SceneInteractionHarness harness;

    private static final int CANVAS_WIDTH = 240;
    private static final int CANVAS_HEIGHT = 160;
    private static final int STUB_CHAR_WIDTH = 8;
    private static final float EPSILON = 0.0001F;

    /** 库默认主题的 INPUT 角色配方：触发器默认外观唯一来源。 */
    private static final SceneSurfaceStyle INPUT_SURFACE =
            SceneThemes.DEFAULT.surface(SceneTheme.Role.INPUT);
    /** 库默认主题的 OVERLAY 角色配方：弹出底座默认外观唯一来源。 */
    private static final SceneSurfaceStyle OVERLAY_SURFACE =
            SceneThemes.DEFAULT.surface(SceneTheme.Role.OVERLAY);
    private static final int TRIGGER_BG_IDLE = INPUT_SURFACE.getIdle().getTint();
    private static final int TRIGGER_BG_HOVER = INPUT_SURFACE.getHovered().getTint();
    private static final int TRIGGER_BG_PRESSED = INPUT_SURFACE.getPressed().getTint();
    private static final int TRIGGER_BG_DISABLED = INPUT_SURFACE.getDisabled().getTint();
    private static final int TRIGGER_BORDER_IDLE = INPUT_SURFACE.getIdle().getEdge();
    private static final int TRIGGER_BORDER_FOCUS = INPUT_SURFACE.getFocusEdge();
    private static final int OVERLAY_BG_IDLE = OVERLAY_SURFACE.getIdle().getTint();
    private static final int TEXT_NORMAL = SceneThemes.DEFAULT.foreground();
    private static final int TEXT_MUTED = SceneThemes.DEFAULT.mutedForeground();
    private static final int TEXT_DISABLED = SceneThemes.DEFAULT.disabledForeground();
    private static final int TEXT_ON_ACCENT = SceneThemes.DEFAULT.onAccentForeground();
    /** 候选行轻量覆盖强度（与实现常量同源；断言取派生色而非硬编码色号）。 */
    private static final int ITEM_HOVER_ALPHA = 0x1F;
    private static final int ITEM_SELECTED_ALPHA = 0x33;
    private static final int ITEM_SELECTED_HOVER_ALPHA = 0x4C;
    private static final int ITEM_HIGHLIGHT_ALPHA = 0x59;
    private static final int ITEM_BG_TRANSPARENT = 0x00000000;
    private static final int ITEM_BG_HOVER = tint(SceneThemes.DEFAULT.accent(), ITEM_HOVER_ALPHA);
    private static final int ITEM_BG_SELECTED = tint(SceneThemes.DEFAULT.accent(), ITEM_SELECTED_ALPHA);
    private static final int ITEM_BG_SELECTED_HOVER = tint(SceneThemes.DEFAULT.accent(), ITEM_SELECTED_HOVER_ALPHA);
    private static final int ITEM_BG_HIGHLIGHTED = tint(SceneThemes.DEFAULT.selectionBackground(), ITEM_HIGHLIGHT_ALPHA);
    private static final List<String> OPTIONS = Arrays.asList("Low", "Mid", "High");

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(STUB_CHAR_WIDTH, 16);
        harness = SceneInteractionHarness.create(measurer);
        runtime = harness.getRuntime();
        layoutEngine = new SceneLayoutEngine(measurer);
        paintEngine = new ScenePaintEngine(measurer);
        sceneRoot = new SceneNode();
        selectedSignal = Signal.create(Integer.valueOf(0));
        enabledSignal = Signal.create(Boolean.TRUE);
        selectCount = new AtomicInteger(0);
        lastSelectValue = null;

        SceneSelect.Props props = new SceneSelect.Props(selectedSignal, OPTIONS, enabledSignal, next -> {
            selectCount.incrementAndGet();
            lastSelectValue = next;
        });
        handle = runtime.mount(sceneRoot, SceneSelect.create(runtime, props));
        trigger = handle.getRoot();
        runtime.flush();
        // 挂载路由根并对齐 layout，供 harness.click(trigger) 取中心 + route
        harness.mountRoot(sceneRoot, CANVAS_WIDTH, CANVAS_HEIGHT);
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    /**
     * 受控零状态：外部 selectedIndex 是唯一真值，控件点击选项只上抛不自改。
     */
    @Test
    public void controlledSelectionShouldUsePropsAsSingleSourceOfTruth() {
        doLayout();
        Assert.assertEquals("Low", labelNode().getText());

        openByClick();
        clickOverlayItem(1);
        runtime.flush();

        Assert.assertEquals("点击 item[1] 应上抛 1", Integer.valueOf(1), lastSelectValue);
        Assert.assertEquals("控件不应自改 selectedIndex", Integer.valueOf(0), selectedSignal.get());
        Assert.assertEquals("外部未回写时文本仍为旧真值", "Low", labelNode().getText());

        selectedSignal.set(Integer.valueOf(1));
        runtime.flush();
        Assert.assertEquals("外部回写后文本更新", "Mid", labelNode().getText());
    }

    /**
     * 默认工厂路径：触发器表面来自 INPUT 配方，选中值/箭头前景来自主题语义色
     * （正文 foreground、次要 mutedForeground），不再是旧状态色查表。
     */
    @Test
    public void defaultTriggerSurfaceFollowsInputRecipeAndThemeForeground() {
        doLayout();
        Assert.assertEquals("默认 Select padding 使用统一 token",
                SceneChromeTokens.PAD_MD, trigger.getPaddingLeft());
        Assert.assertEquals("触发器背景 = INPUT 配方 idle 染色", TRIGGER_BG_IDLE, trigger.getBackgroundColor());
        Assert.assertEquals("触发器边框 = INPUT 配方 idle 缘色", TRIGGER_BORDER_IDLE, trigger.getBorderColor());
        Assert.assertEquals("触发器边框宽 = INPUT 配方", INPUT_SURFACE.getBorderWidth(), trigger.getBorderWidth());
        Assert.assertEquals("触发器圆角 = INPUT 配方", INPUT_SURFACE.getCornerRadius(), trigger.getCornerRadius());
        Assert.assertEquals("选中值前景 = 主题正文前景", TEXT_NORMAL, labelNode().getTextColor());
        Assert.assertEquals("箭头前景 = 主题次要前景", TEXT_MUTED, arrowNode().getTextColor());
    }

    /**
     * 点击 trigger 应通过 expanded signal 派生 overlay 挂载与卸载。
     */
    @Test
    public void triggerClickShouldTogglePortalOverlay() {
        doLayout();
        Assert.assertTrue(runtime.getOverlayHost().isEmpty());

        openByClick();
        Assert.assertEquals("展开后应挂载一个 overlay", 1, runtime.getOverlayHost().size());
        Assert.assertEquals("箭头应切为收起态", "▲", arrowNode().getText());

        harness.click(trigger);
        Assert.assertTrue("再次点击应卸载 overlay", runtime.getOverlayHost().isEmpty());
        Assert.assertEquals("箭头应切回展开态", "▼", arrowNode().getText());
    }

    /**
     * 点击选项应上抛 onSelect，并关闭 listbox overlay。
     */
    @Test
    public void optionClickShouldRaiseSelectAndCloseOverlay() {
        doLayout();
        openByClick();

        clickOverlayItem(2);
        runtime.flush();

        Assert.assertEquals(1, selectCount.get());
        Assert.assertEquals(Integer.valueOf(2), lastSelectValue);
        Assert.assertTrue("选项点击后应关闭 overlay", runtime.getOverlayHost().isEmpty());
    }

    /**
     * overlay item 真机点击是 DOWN/UP 跨帧；中间帧重排后仍应合成 CLICK 并完成选择关闭。
     */
    @Test
    public void optionClickAcrossFramesShouldRaiseSelectAndCloseOverlay() {
        doLayout();
        openByClick();

        harness.pressReleaseAcrossFrames(overlayItem(2), this::doLayout);

        Assert.assertEquals("跨帧 item 点击应上抛一次", 1, selectCount.get());
        Assert.assertEquals("跨帧 item 点击应上抛目标下标", Integer.valueOf(2), lastSelectValue);
        Assert.assertTrue("跨帧 item 点击后应关闭 overlay", runtime.getOverlayHost().isEmpty());
    }

    /**
     * 键盘导航：方向键移动 highlightedIndex，Enter 选择，ESC 关闭。
     */
    @Test
    public void keyboardShouldNavigateSelectAndClose() {
        doLayout();
        selectedSignal.set(Integer.valueOf(1));
        runtime.flush();
        runtime.requestFocus(trigger);

        routeKey(SceneKey.ARROW_DOWN);
        runtime.flush();
        doLayout();
        Assert.assertEquals("方向键应展开 overlay", 1, runtime.getOverlayHost().size());
        Assert.assertEquals("方向键展开应从当前选中项 item[1] 建立高亮锚点",
                ITEM_BG_HIGHLIGHTED, overlayItem(1).getBackgroundColor());
        Assert.assertEquals("选中项键盘高亮 = 主题选区背景半透明，文字取强调底前景",
                TEXT_ON_ACCENT, overlayItemLabel(1).getTextColor());

        routeKey(SceneKey.ARROW_DOWN);
        runtime.flush();
        doLayout();
        Assert.assertEquals("第二次 ↓ 应高亮 item[2]", ITEM_BG_HIGHLIGHTED, overlayItem(2).getBackgroundColor());
        Assert.assertEquals("未选中项键盘高亮仍用主题正文前景", TEXT_NORMAL, overlayItemLabel(2).getTextColor());
        Assert.assertEquals("失去高亮后的 selected-only 应恢复选中指示（主题 accent 半透明）",
                ITEM_BG_SELECTED, overlayItem(1).getBackgroundColor());
        Assert.assertEquals("失去高亮后的 selected-only 应恢复普通文本色",
                TEXT_NORMAL, overlayItemLabel(1).getTextColor());

        routeKey(SceneKey.ENTER);
        runtime.flush();
        Assert.assertEquals("Enter 应上抛高亮下标", Integer.valueOf(2), lastSelectValue);
        Assert.assertTrue("Enter 选择后应关闭", runtime.getOverlayHost().isEmpty());

        routeKey(SceneKey.SPACE);
        runtime.flush();
        Assert.assertEquals("Space 应重新展开", 1, runtime.getOverlayHost().size());
        routeKey(SceneKey.ESCAPE);
        runtime.flush();
        Assert.assertTrue("ESC 应关闭 overlay", runtime.getOverlayHost().isEmpty());
    }

    /**
     * listbox 必须经 portal 挂卸，主树 trigger 不直接持有选项节点。
     */
    @Test
    public void listboxShouldMountAndUnmountThroughPortal() {
        doLayout();
        Assert.assertEquals("主树 trigger 只应有 label 与 arrow", 2, trigger.__getChildren().size());

        openByClick();
        Assert.assertEquals(1, runtime.getOverlayHost().size());
        Assert.assertEquals("overlay listbox 应持有所有选项", OPTIONS.size(), overlayRoot().__getChildren().size());

        harness.click(trigger);
        Assert.assertTrue(runtime.getOverlayHost().isEmpty());
        Assert.assertEquals("关闭后主树结构仍不含 listbox", 2, trigger.__getChildren().size());
    }

    /**
     * 少量选项时 listbox 应按内容高度 shrink-to-fit，不占满可用高度。
     */
    @Test
    public void listboxShouldShrinkToFitContentHeight() {
        doLayout();
        openByClick();

        LayoutBox listboxBox = box(overlayRoot());
        int expectedHeight = OPTIONS.size() * (16 + 2 * SceneChromeTokens.PAD_MD);
        Assert.assertEquals("listbox 高度应等于所有 item 内容高", expectedHeight, listboxBox.getHeight());
        Assert.assertTrue("listbox 高度应小于 overlay maxHeight", listboxBox.getHeight() < CANVAS_HEIGHT);
    }

    /**
     * disabled 时点击、键盘均不展开、不选择。
     */
    @Test
    public void disabledShouldIgnorePointerAndKeyboard() {
        enabledSignal.set(Boolean.FALSE);
        runtime.flush();
        doLayout();

        harness.click(trigger);
        Assert.assertTrue("disabled 点击不展开", runtime.getOverlayHost().isEmpty());
        Assert.assertEquals("disabled 点击不上抛", 0, selectCount.get());

        runtime.requestFocus(trigger);
        routeKey(SceneKey.ARROW_DOWN);
        routeKey(SceneKey.ENTER);
        runtime.flush();
        Assert.assertTrue("disabled 键盘不展开", runtime.getOverlayHost().isEmpty());
        Assert.assertEquals("disabled 键盘不上抛", 0, selectCount.get());
    }

    /**
     * 鼠标展开不预高亮选中项；选中项以主题 accent 半透明作选中指示（默认态其它行透明露出玻璃底），
     * hover 选中项时强调覆盖加深并切强调底前景，hover 未选中项只做 accent 低强度覆盖。
     */
    @Test
    public void mouseOpenShouldNotPreHighlightSelectedItemAndSelectedOnlyUsesAccentOverlay() {
        doLayout();
        openByClick();
        runtime.flush();

        SceneNode item0 = overlayItem(0);
        Assert.assertEquals("鼠标展开未 hover/键盘导航时 selected-only item[0] 背景 = 主题 accent 半透明选中指示",
                ITEM_BG_SELECTED, item0.getBackgroundColor());
        for (int i = 1; i < OPTIONS.size(); i++) {
            Assert.assertEquals("鼠标展开未 hover/键盘导航时普通未选中 item[" + i + "] 背景应透明",
                ITEM_BG_TRANSPARENT, overlayItem(i).getBackgroundColor());
        }
        Assert.assertEquals("selected-only 半透明覆盖上仍用普通文本色",
                TEXT_NORMAL, overlayItemLabel(0).getTextColor());

        moveToOverlayItem(item0);

        Assert.assertEquals("hover selected item[0] 后应切到 selected+hover 加深 accent 覆盖",
                ITEM_BG_SELECTED_HOVER, item0.getBackgroundColor());
        Assert.assertEquals("selected+hover 覆盖上文字切主题强调底前景",
                TEXT_ON_ACCENT, overlayItemLabel(0).getTextColor());

        moveToOverlayItem(overlayItem(1));

        Assert.assertEquals("hover 未选中 item[1] = accent 低强度覆盖", ITEM_BG_HOVER,
                overlayItem(1).getBackgroundColor());
        Assert.assertEquals("hover 离开后 selected-only 恢复选中指示", ITEM_BG_SELECTED, item0.getBackgroundColor());
        Assert.assertEquals("未选中 hover 行仍用普通文本色", TEXT_NORMAL, overlayItemLabel(1).getTextColor());
    }

    // ==================== 主题化：默认配方路径 ====================

    /**
     * 默认工厂路径：触发器表面等于 {@link SceneThemes#DEFAULT} 的 INPUT 角色配方（含滤镜与实体高度），
     * 弹出底座等于 OVERLAY 角色配方；两侧各只采样一次滤镜，候选行不各自采样背景、默认透明露出玻璃。
     */
    @Test
    public void defaultFactoryPathUsesInputAndOverlayRecipesWithSingleBackdropSample() {
        doLayout();

        // 触发器 = INPUT 配方
        Assert.assertEquals("触发器背景 = INPUT 配方 idle 染色", TRIGGER_BG_IDLE, trigger.getBackgroundColor());
        Assert.assertEquals("触发器边框 = INPUT 配方 idle 缘色", TRIGGER_BORDER_IDLE, trigger.getBorderColor());
        Assert.assertEquals("触发器边框宽 = INPUT 配方", INPUT_SURFACE.getBorderWidth(), trigger.getBorderWidth());
        Assert.assertEquals("触发器圆角 = INPUT 配方", INPUT_SURFACE.getCornerRadius(), trigger.getCornerRadius());
        Assert.assertEquals("触发器实体高度 = INPUT 配方", INPUT_SURFACE.getIdle().getElevation(),
                trigger.__getSurfaceElevation(), EPSILON);
        UiBackdrop inputRecipeBackdrop = INPUT_SURFACE.getBackdrop();
        Assert.assertNotNull("INPUT 配方自带滤镜", inputRecipeBackdrop);
        Assert.assertNotNull("默认工厂路径触发器应写入液态滤镜", trigger.getBackdrop());
        Assert.assertEquals("触发器模糊半径 = 配方", inputRecipeBackdrop.getBlurRadius(),
                trigger.getBackdrop().getBlurRadius());
        Assert.assertEquals("触发器材质 = 配方", inputRecipeBackdrop.getEffect().getMaterial(),
                trigger.getBackdrop().getEffect().getMaterial());

        PaintPlan triggerPlan = doPaint(sceneRoot);
        Assert.assertEquals("触发器只采样一次滤镜", 1,
                countType(triggerPlan.getCommands(), PaintCommandType.BACKDROP));

        // 弹出底座 = OVERLAY 配方
        openByClick();
        SceneNode listbox = overlayRoot();
        Assert.assertEquals("浮层背景 = OVERLAY 配方 idle 染色", OVERLAY_BG_IDLE, listbox.getBackgroundColor());
        Assert.assertEquals("浮层边框 = OVERLAY 配方 idle 缘色", OVERLAY_SURFACE.getIdle().getEdge(),
                listbox.getBorderColor());
        Assert.assertEquals("浮层边框宽 = OVERLAY 配方", OVERLAY_SURFACE.getBorderWidth(), listbox.getBorderWidth());
        Assert.assertEquals("浮层圆角 = OVERLAY 配方", OVERLAY_SURFACE.getCornerRadius(), listbox.getCornerRadius());
        Assert.assertEquals("浮层实体高度 = OVERLAY 配方", OVERLAY_SURFACE.getIdle().getElevation(),
                listbox.__getSurfaceElevation(), EPSILON);
        Assert.assertNotNull("浮层应写入 OVERLAY 配方滤镜", listbox.getBackdrop());
        Assert.assertEquals("浮层材质 = OVERLAY 配方", OVERLAY_SURFACE.getBackdrop().getEffect().getMaterial(),
                listbox.getBackdrop().getEffect().getMaterial());

        List<PaintCommand> overlayCommands = doPaint(listbox).getCommands();
        Assert.assertEquals("一颗浮层表面只采样一次滤镜", 1,
                countType(overlayCommands, PaintCommandType.BACKDROP));
        int backgroundIndex = indexOfType(overlayCommands, PaintCommandType.BACKGROUND);
        Assert.assertTrue("浮层应有 BACKGROUND（半透明 tint 叠玻璃之上）", backgroundIndex >= 0);
        Assert.assertTrue("滤镜之上不得压不透明底盖",
                alpha(overlayCommands.get(backgroundIndex).getColor()) < 0xFF);

        // 候选行不各自采样背景：选中行只做 accent 半透明指示，未选中行默认透明露出玻璃
        for (int i = 0; i < OPTIONS.size(); i++) {
            Assert.assertNull("候选行 " + i + " 不得挂滤镜", overlayItem(i).getBackdrop());
        }
        Assert.assertEquals("选中候选行 = 主题 accent 半透明选中指示", ITEM_BG_SELECTED,
                overlayItem(0).getBackgroundColor());
        for (int i = 1; i < OPTIONS.size(); i++) {
            Assert.assertEquals("未选中候选行 " + i + " 默认背景透明（露出浮层玻璃）", ITEM_BG_TRANSPARENT,
                    overlayItem(i).getBackgroundColor());
        }
    }

    /**
     * 触发器表面四态来自 INPUT 配方：disabled &gt; pressed &gt; hovered &gt; idle；
     * focus 只覆盖非禁用态缘色、不改染色；禁用时选中值与箭头前景落主题 disabledForeground。
     */
    @Test
    public void triggerSurfaceFollowsInputRecipeStatesAndThemeSemanticColors() {
        doLayout();
        Assert.assertEquals("idle 染色来自 INPUT 配方", TRIGGER_BG_IDLE, trigger.getBackgroundColor());
        Assert.assertEquals("idle 缘色来自 INPUT 配方", TRIGGER_BORDER_IDLE, trigger.getBorderColor());

        harness.moveTo(trigger);
        Assert.assertEquals("hover 取配方 hovered 档", TRIGGER_BG_HOVER, trigger.getBackgroundColor());

        runtime.requestFocus(trigger);
        runtime.flush();
        Assert.assertEquals("focus 不改染色（只改缘色）", TRIGGER_BG_HOVER, trigger.getBackgroundColor());
        Assert.assertEquals("focus 缘色取配方 focusEdge", TRIGGER_BORDER_FOCUS, trigger.getBorderColor());

        harness.press(trigger);
        Assert.assertEquals("pressed 压过 hover/focus", TRIGGER_BG_PRESSED, trigger.getBackgroundColor());

        enabledSignal.set(Boolean.FALSE);
        runtime.flush();
        Assert.assertEquals("disabled 压过其余状态", TRIGGER_BG_DISABLED, trigger.getBackgroundColor());
        Assert.assertEquals("disabled 缘色取配方禁用档", INPUT_SURFACE.getDisabled().getEdge(),
                trigger.getBorderColor());
        Assert.assertEquals("disabled 选中值前景 = 主题禁用前景", TEXT_DISABLED, labelNode().getTextColor());
        Assert.assertEquals("disabled 箭头前景 = 主题禁用前景", TEXT_DISABLED, arrowNode().getTextColor());
    }

    // ==================== 主题化：打开中切主题 / 关闭重开 ====================

    /**
     * 打开中切主题：触发器与弹出底座随主题更新，选中值/焦点/键盘高亮不丢，节点身份不变，
     * effect 数不增长；切主题后键盘导航与确认仍工作。
     */
    @Test
    public void themeSwitchWhileOpenUpdatesTriggerAndListboxWithoutStateLoss() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        Assert.assertNotEquals("测试前提：深/浅 INPUT 配方必须不同",
                dark.surface(SceneTheme.Role.INPUT), light.surface(SceneTheme.Role.INPUT));
        Assert.assertNotEquals("测试前提：深/浅 OVERLAY 配方必须不同",
                dark.surface(SceneTheme.Role.OVERLAY), light.surface(SceneTheme.Role.OVERLAY));

        Signal<SceneTheme> pageTheme = Signal.create(dark);
        MountHandle themed = mountSelectWithTheme(pageTheme, OPTIONS);
        SceneNode themedTrigger = themed.getRoot();
        doLayout();

        // 键盘展开并锚定当前选中项 item[0]
        runtime.requestFocus(themedTrigger);
        runtime.flush();
        routeKey(SceneKey.ARROW_DOWN);
        runtime.flush();
        doLayout();

        Assert.assertEquals("键盘展开一个浮层", 1, runtime.getOverlayHost().size());
        SceneNode listbox = overlayRoot();
        SceneNode item0 = listbox.__getChildren().get(0);
        Assert.assertSame("焦点在触发器", themedTrigger, runtime.getFocusedNode());
        Assert.assertEquals("深色触发器 = 深色 INPUT idle 染色",
                dark.surface(SceneTheme.Role.INPUT).getIdle().getTint(), themedTrigger.getBackgroundColor());
        Assert.assertEquals("深色浮层 = 深色 OVERLAY idle 染色",
                dark.surface(SceneTheme.Role.OVERLAY).getIdle().getTint(), listbox.getBackgroundColor());
        Assert.assertEquals("深色键盘高亮 = 深色选区背景半透明",
                tint(dark.selectionBackground(), ITEM_HIGHLIGHT_ALPHA), item0.getBackgroundColor());

        int effectsBeforeSwitch = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(light);
        runtime.flush();
        doLayout();

        Assert.assertSame("主题切换不重建触发器", themedTrigger, themed.getRoot());
        Assert.assertEquals("仍展开（浮层显隐不丢）", 1, runtime.getOverlayHost().size());
        Assert.assertSame("主题切换不重建浮层根", listbox, overlayRoot());
        Assert.assertSame("主题切换不重建候选行", item0, overlayRoot().__getChildren().get(0));
        Assert.assertEquals("触发器随主题更新", light.surface(SceneTheme.Role.INPUT).getIdle().getTint(),
                themedTrigger.getBackgroundColor());
        Assert.assertEquals("触发器圆角随主题更新", light.surface(SceneTheme.Role.INPUT).getCornerRadius(),
                themedTrigger.getCornerRadius());
        Assert.assertEquals("浮层随主题更新", light.surface(SceneTheme.Role.OVERLAY).getIdle().getTint(),
                listbox.getBackgroundColor());
        Assert.assertEquals("浮层缘色随主题更新", light.surface(SceneTheme.Role.OVERLAY).getIdle().getEdge(),
                listbox.getBorderColor());
        Assert.assertEquals("键盘高亮随主题更新 = 浅色选区背景半透明",
                tint(light.selectionBackground(), ITEM_HIGHLIGHT_ALPHA), item0.getBackgroundColor());
        Assert.assertEquals("选中值不丢", "Low", themedTrigger.__getChildren().get(0).getText());
        Assert.assertEquals("受控值不丢", Integer.valueOf(0), selectedSignal.get());
        Assert.assertSame("焦点不丢", themedTrigger, runtime.getFocusedNode());
        Assert.assertEquals("切主题不新增订阅", effectsBeforeSwitch, ReactiveTestProbe.registeredEffectCount());

        // 切主题后键盘导航与确认仍工作
        routeKey(SceneKey.ARROW_DOWN);
        runtime.flush();
        doLayout();
        Assert.assertEquals("高亮移动到 item[1]（浅色选区背景半透明）",
                tint(light.selectionBackground(), ITEM_HIGHLIGHT_ALPHA),
                overlayRoot().__getChildren().get(1).getBackgroundColor());
        Assert.assertEquals("原高亮项回落到选中指示（浅色 accent 半透明）",
                tint(light.accent(), ITEM_SELECTED_ALPHA), item0.getBackgroundColor());
        routeKey(SceneKey.ENTER);
        runtime.flush();
        Assert.assertEquals("切主题后 Enter 仍上抛高亮下标", Integer.valueOf(1), lastSelectValue);
        Assert.assertTrue("切主题后 Enter 仍关闭浮层", runtime.getOverlayHost().isEmpty());
    }

    /**
     * 关闭重开一致：切主题后关闭再重开，新浮层消费当前主题 OVERLAY 配方，选中值/焦点保持；
     * 重开经 portal 重建（节点身份变化属预期），外观与键盘高亮语义不漂移。
     */
    @Test
    public void closeAndReopenAfterThemeSwitchShouldStayConsistent() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        Signal<SceneTheme> pageTheme = Signal.create(dark);
        MountHandle themed = mountSelectWithTheme(pageTheme, OPTIONS);
        SceneNode themedTrigger = themed.getRoot();
        doLayout();

        runtime.requestFocus(themedTrigger);
        runtime.flush();
        routeKey(SceneKey.ARROW_DOWN);
        runtime.flush();
        doLayout();
        SceneNode firstListbox = overlayRoot();

        pageTheme.set(light);
        runtime.flush();
        doLayout();

        routeKey(SceneKey.ESCAPE);
        runtime.flush();
        Assert.assertTrue("ESC 关闭浮层", runtime.getOverlayHost().isEmpty());

        routeKey(SceneKey.SPACE);
        runtime.flush();
        doLayout();
        Assert.assertEquals("Space 重开一个浮层", 1, runtime.getOverlayHost().size());
        SceneNode reopened = overlayRoot();
        Assert.assertNotSame("重开是 portal 重建", firstListbox, reopened);
        Assert.assertEquals("重开浮层 = 浅色 OVERLAY 配方",
                light.surface(SceneTheme.Role.OVERLAY).getIdle().getTint(), reopened.getBackgroundColor());
        Assert.assertEquals("重开浮层缘色 = 浅色 OVERLAY 配方",
                light.surface(SceneTheme.Role.OVERLAY).getIdle().getEdge(), reopened.getBorderColor());
        Assert.assertEquals("重开仍持有全部选项", OPTIONS.size(), reopened.__getChildren().size());
        Assert.assertEquals("重开键盘高亮 = 浅色选区背景半透明",
                tint(light.selectionBackground(), ITEM_HIGHLIGHT_ALPHA),
                reopened.__getChildren().get(0).getBackgroundColor());
        Assert.assertEquals("重开后选中值保持", "Low", themedTrigger.__getChildren().get(0).getText());
        Assert.assertSame("重开后焦点保持", themedTrigger, runtime.getFocusedNode());
    }

    // ==================== 主题化：卸载回收 / 选项增删 ====================

    /**
     * 卸载回收：额外挂载的 Select 展开浮层后 dispose + flush，绑定 effect 回到挂载前基线，浮层一并卸载。
     */
    @Test
    public void disposeShouldReclaimAllBindingsAndOverlay() {
        int baseline = ReactiveTestProbe.registeredEffectCount();
        MountHandle extra = mountSelectWithTheme(Signal.create(SceneTheme.liquidGlassDark()), OPTIONS);
        doLayout();
        runtime.requestFocus(extra.getRoot());
        runtime.flush();
        routeKey(SceneKey.ARROW_DOWN);
        runtime.flush();
        doLayout();
        Assert.assertEquals("额外 Select 已展开浮层", 1, runtime.getOverlayHost().size());
        Assert.assertTrue("挂载应注册响应式绑定",
                ReactiveTestProbe.registeredEffectCount() > baseline);

        extra.dispose();
        runtime.flush();
        Assert.assertTrue("卸载后浮层移除", runtime.getOverlayHost().isEmpty());
        Assert.assertEquals("卸载回收全部绑定", baseline, ReactiveTestProbe.registeredEffectCount());
    }

    /**
     * 选项增删：选项列表构建期固定，增删以重挂载表达——项数、选项文本、键盘导航与选择回调
     * 始终与当次选项一致，旧实例卸载后浮层与绑定一并回收。
     */
    @Test
    public void optionListChangesAcrossRemountShouldStayConsistent() {
        doLayout();
        openByClick();
        Assert.assertEquals("初始 3 项", OPTIONS.size(), overlayRoot().__getChildren().size());
        harness.click(trigger);
        runtime.flush();
        Assert.assertTrue("初始实例浮层关闭", runtime.getOverlayHost().isEmpty());

        // 增加到 5 项
        List<String> fiveOptions = Arrays.asList("Low", "Mid", "High", "Ultra", "Extreme");
        MountHandle five = mountSelectWithTheme(Signal.create(SceneTheme.liquidGlassDark()), fiveOptions);
        doLayout();
        runtime.requestFocus(five.getRoot());
        runtime.flush();
        routeKey(SceneKey.ARROW_DOWN);
        runtime.flush();
        doLayout();
        Assert.assertEquals("重挂载后持有 5 项", 5, overlayRoot().__getChildren().size());
        clickOverlayItem(4);
        runtime.flush();
        Assert.assertEquals("点击第 5 项上抛 4", Integer.valueOf(4), lastSelectValue);
        Assert.assertTrue("选择后浮层关闭", runtime.getOverlayHost().isEmpty());
        five.dispose();
        runtime.flush();

        // 减少到 1 项
        List<String> oneOption = Arrays.asList("Only");
        MountHandle one = mountSelectWithTheme(Signal.create(SceneTheme.liquidGlassDark()), oneOption);
        doLayout();
        runtime.requestFocus(one.getRoot());
        runtime.flush();
        routeKey(SceneKey.ARROW_DOWN);
        runtime.flush();
        doLayout();
        Assert.assertEquals("重挂载后持有 1 项", 1, overlayRoot().__getChildren().size());
        Assert.assertEquals("单选项文本保持", "Only", overlayItemLabel(0).getText());
        clickOverlayItem(0);
        runtime.flush();
        Assert.assertEquals("点击唯一项上抛 0", Integer.valueOf(0), lastSelectValue);
        Assert.assertTrue("选择后浮层关闭", runtime.getOverlayHost().isEmpty());
        one.dispose();
        runtime.flush();
    }

    private void doLayout() {
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        for (int i = 0; i < runtime.getOverlayHost().bottomFirst().size(); i++) {
            SceneNode overlay = runtime.getOverlayHost().bottomFirst().get(i).getRoot();
            layoutEngine.layout(overlay, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        }
    }

    private SceneNode labelNode() {
        return trigger.__getChildren().get(0);
    }

    private SceneNode arrowNode() {
        return trigger.__getChildren().get(1);
    }

    private SceneNode overlayRoot() {
        return runtime.getOverlayHost().bottomFirst().get(0).getRoot();
    }

    private SceneNode overlayItem(int index) {
        return overlayRoot().__getChildren().get(index);
    }

    private SceneNode overlayItemLabel(int index) {
        return overlayItem(index).__getChildren().get(0);
    }

    private void openByClick() {
        harness.click(trigger);
        doLayout();
    }

    private void clickOverlayItem(int index) {
        harness.click(overlayItem(index));
    }

    private LayoutBox box(SceneNode node) {
        return (LayoutBox) node.getCachedLayout();
    }

    /** 移动到 overlay item 中心；anchor=0 测试沙箱中的 hover 行为回归走 harness。 */
    private void moveToOverlayItem(SceneNode node) {
        harness.moveTo(node);
    }

    /** 键盘事件注入（PRESSED）。
     *  <p>白盒回退（overlay 树外路由 + 自定义 native code）：键盘导航属 overlay，且用 NATIVE_NONE，harness 固定 0,0 不适用。</p> */
    private void routeKey(SceneKey key) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofKey(key, SceneKeyAction.PRESSED,
                false, false, false, false, RawInputEvent.NATIVE_NONE, RawInputEvent.NATIVE_NONE, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(sceneRoot, frame, 0, 0);
    }

    /**
     * 在可切换的局部主题作用域内挂载 Select（{@link SceneThemes#withTheme}）：
     * 主题信号变化只重派生外观，不重建节点。
     *
     * @param theme   页面主题信号
     * @param options 构建期固定选项
     * @return 挂载句柄
     */
    private MountHandle mountSelectWithTheme(Signal<SceneTheme> theme, List<String> options) {
        SceneSelect.Props props = new SceneSelect.Props(selectedSignal, options, enabledSignal, next -> {
            selectCount.incrementAndGet();
            lastSelectValue = next;
        });
        MountHandle mounted = runtime.mount(sceneRoot, () -> {
            final SceneNode[] holder = new SceneNode[1];
            SceneThemes.withTheme(theme, () -> holder[0] = SceneSelect.create(runtime, props).get());
            return holder[0];
        });
        runtime.flush();
        return mounted;
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

    private static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    private static int tint(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }
}
