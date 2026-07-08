package club.heiqi.uilib.ui.scene.integration;

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
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.SceneStateColors;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

/**
 * SceneSelect 端到端单元测试 —— R8 受控选择 + R11 signal→portal 浮层契约验收。
 */
public class SceneSelectTest {

    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
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
    /**
     * trigger 默认背景，走 {@link SceneStateColors#standardBackground} 查表，与控件同源。
     */
    private static final int TRIGGER_BG = SceneStateColors.standardBackground(true, false, false);
    /**
     * item 键盘高亮态背景，走 {@link SceneStateColors#listItemBackground} 查表，与控件同源。
     */
    private static final int ITEM_BG_HIGHLIGHTED = SceneStateColors.listItemBackground(true, false, true, false);
    /**
     * item 选中态背景，走 {@link SceneStateColors#listItemBackground} 查表，与控件同源。
     */
    private static final int ITEM_BG_SELECTED = SceneStateColors.listItemBackground(true, true, false, false);
    /**
     * item 选中且键盘高亮态背景，走 {@link SceneStateColors#listItemBackground} 查表，与控件同源。
     */
    private static final int ITEM_BG_SELECTED_HIGHLIGHTED = SceneStateColors.listItemBackground(true, true, true, false);
    /**
     * item 选中且悬停态背景，走 {@link SceneStateColors#listItemBackground} 查表，与控件同源。
     */
    private static final int ITEM_BG_SELECTED_HOVERED = SceneStateColors.listItemBackground(true, true, false, true);
    private static final List<String> OPTIONS = Arrays.asList("Low", "Mid", "High");

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(STUB_CHAR_WIDTH, 16);
        harness = SceneInteractionHarness.create(measurer);
        runtime = harness.getRuntime();
        layoutEngine = new SceneLayoutEngine(measurer);
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

    @Test
    public void defaultAppearanceShouldStayNonFlat() {
        Assert.assertEquals("默认 Select padding 保持原值", 6, trigger.getPaddingLeft());
        Assert.assertEquals("默认 Select cornerRadius 保持原值", 4, trigger.getCornerRadius());
        Assert.assertEquals("默认 Select 背景保持原值", TRIGGER_BG, trigger.getBackgroundColor());
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
                ITEM_BG_SELECTED_HIGHLIGHTED, overlayItem(1).getBackgroundColor());
        Assert.assertEquals("selected+highlight accent 背景应使用白字",
                SceneChromeTokens.TEXT_ON_ACCENT, overlayItemLabel(1).getTextColor());

        routeKey(SceneKey.ARROW_DOWN);
        runtime.flush();
        doLayout();
        Assert.assertEquals("第二次 ↓ 应高亮 item[2]", ITEM_BG_HIGHLIGHTED, overlayItem(2).getBackgroundColor());
        Assert.assertEquals("失去高亮后的 selected-only 应恢复 selected-only 背景 token（当前语义为透明）",
                ITEM_BG_SELECTED, overlayItem(1).getBackgroundColor());
        Assert.assertEquals("失去高亮后的 selected-only 应恢复普通文本色",
                SceneChromeTokens.TEXT_PRIMARY, overlayItemLabel(1).getTextColor());

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
        Assert.assertEquals("listbox 高度应等于 3 个 item 内容高", 84, listboxBox.getHeight());
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

    /** 鼠标展开不预高亮选中项；selected-only 透明且使用普通文本色，hover 后才切 accent 视觉。 */
    @Test
    public void mouseOpenShouldNotPreHighlightSelectedItemAndSelectedOnlyUsesPlainText() {
        doLayout();
        openByClick();
        runtime.flush();

        SceneNode item0 = overlayItem(0);
        Assert.assertEquals("鼠标展开未 hover/键盘导航时 selected-only item[0] 背景应走 selected-only token（当前语义为透明，非普通未选中项）",
                ITEM_BG_SELECTED, item0.getBackgroundColor());
        for (int i = 1; i < OPTIONS.size(); i++) {
            Assert.assertEquals("鼠标展开未 hover/键盘导航时普通未选中 item[" + i + "] 背景应透明",
                    0x00000000, overlayItem(i).getBackgroundColor());
        }
        Assert.assertEquals("selected-only 透明背景应使用普通文本色",
                SceneChromeTokens.TEXT_PRIMARY, overlayItemLabel(0).getTextColor());

        moveToOverlayItem(item0);

        Assert.assertEquals("hover selected item[0] 后应切到 selected+hover accent 背景",
                ITEM_BG_SELECTED_HOVERED, item0.getBackgroundColor());
        Assert.assertEquals("selected+hover accent 背景应使用白字",
                SceneChromeTokens.TEXT_ON_ACCENT, overlayItemLabel(0).getTextColor());
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
}
