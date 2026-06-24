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
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;

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

    private static final int CANVAS_WIDTH = 240;
    private static final int CANVAS_HEIGHT = 160;
    private static final int STUB_CHAR_WIDTH = 8;
    private static final int TRIGGER_BG = 0xFF3A3A3A;
    private static final int ITEM_BG_HIGHLIGHTED = 0xFF3B4E68;
    private static final int ITEM_BG_SELECTED = 0xFF4A90D9;
    private static final List<String> OPTIONS = Arrays.asList("Low", "Mid", "High");

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        runtime = new SceneRuntime();
        layoutEngine = new SceneLayoutEngine(new FixedTextMeasurer(STUB_CHAR_WIDTH, 16));
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

    @Test
    public void flatAppearanceShouldRemoveTriggerChrome() {
        handle.dispose();
        sceneRoot = new SceneNode();
        SceneSelect.Props props = new SceneSelect.Props(selectedSignal, OPTIONS, enabledSignal, next -> {
            selectCount.incrementAndGet();
            lastSelectValue = next;
        }, true);
        handle = runtime.mount(sceneRoot, SceneSelect.create(runtime, props));
        trigger = handle.getRoot();
        runtime.flush();

        Assert.assertEquals("flat Select padding 应为 0", 0, trigger.getPaddingLeft());
        Assert.assertEquals("flat Select cornerRadius 应为 0", 0, trigger.getCornerRadius());
        Assert.assertEquals("flat Select 背景应透明", 0x00000000, trigger.getBackgroundColor());
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

        clickCenter(trigger);
        runtime.flush();
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
     * 键盘导航：方向键移动 highlightedIndex，Enter 选择，ESC 关闭。
     */
    @Test
    public void keyboardShouldNavigateSelectAndClose() {
        doLayout();
        runtime.requestFocus(trigger);

        routeKey(SceneKey.ARROW_DOWN);
        runtime.flush();
        doLayout();
        Assert.assertEquals("方向键应展开 overlay", 1, runtime.getOverlayHost().size());
        Assert.assertEquals("初始高亮同步当前选中项", ITEM_BG_SELECTED, overlayItem(0).getBackgroundColor());

        routeKey(SceneKey.ARROW_DOWN);
        runtime.flush();
        doLayout();
        Assert.assertEquals("第二次 ↓ 应高亮 item[1]", ITEM_BG_HIGHLIGHTED, overlayItem(1).getBackgroundColor());

        routeKey(SceneKey.ENTER);
        runtime.flush();
        Assert.assertEquals("Enter 应上抛高亮下标", Integer.valueOf(1), lastSelectValue);
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

        clickCenter(trigger);
        runtime.flush();
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

        clickCenter(trigger);
        runtime.flush();
        Assert.assertTrue("disabled 点击不展开", runtime.getOverlayHost().isEmpty());
        Assert.assertEquals("disabled 点击不上抛", 0, selectCount.get());

        runtime.requestFocus(trigger);
        routeKey(SceneKey.ARROW_DOWN);
        routeKey(SceneKey.ENTER);
        runtime.flush();
        Assert.assertTrue("disabled 键盘不展开", runtime.getOverlayHost().isEmpty());
        Assert.assertEquals("disabled 键盘不上抛", 0, selectCount.get());
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

    private void openByClick() {
        clickCenter(trigger);
        runtime.flush();
        doLayout();
    }

    private void clickOverlayItem(int index) {
        clickCenter(overlayItem(index));
    }

    private LayoutBox box(SceneNode node) {
        return (LayoutBox) node.getCachedLayout();
    }

    private int[] absCenter(SceneNode node) {
        LayoutBox b = box(node);
        int ax = b.getX();
        int ay = b.getY();
        SceneNode parent = node.__getParent();
        while (parent != null) {
            LayoutBox parentBox = (LayoutBox) parent.getCachedLayout();
            if (parentBox != null) {
                ax += parentBox.getX();
                ay += parentBox.getY();
            }
            parent = parent.__getParent();
        }
        return new int[]{ax + b.getWidth() / 2, ay + b.getHeight() / 2};
    }

    private void clickCenter(SceneNode node) {
        int[] center = absCenter(node);
        routePointer(ScenePointerAction.BUTTON_DOWN, center[0], center[1]);
        routePointer(ScenePointerAction.BUTTON_UP, center[0], center[1]);
    }

    private void routePointer(ScenePointerAction action, int x, int y) {
        InputFrameBuilder fb = new InputFrameBuilder(x, y);
        fb.push(RawInputEvent.ofPointer(action, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(sceneRoot, frame, 0, 0);
    }

    private void routeKey(SceneKey key) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofKey(key, SceneKeyAction.PRESSED,
                false, false, false, false, RawInputEvent.NATIVE_NONE, RawInputEvent.NATIVE_NONE, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(sceneRoot, frame, 0, 0);
    }
}
