package club.heiqi.uilib.ui.scene.integration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.control.SceneDialog;
import club.heiqi.uilib.ui.scene.runtime.ScenePortalHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.SceneOverlayHost;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

/**
 * SceneDialog 独立单元测试：遮罩模态拦截、卡片结构、按钮点击/关闭语义、
 * ESC 关闭、打开焦点落首按钮、Tab 环限定在对话框内、Enter 激活焦点按钮。
 *
 * <p>overlay 布局在测试内手动执行（无管线，与 SceneContextMenuTest 同款假设）。</p>
 */
public class SceneDialogTest {

    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    private SceneInteractionHarness harness;

    private Signal<Boolean> visible;
    private AtomicBoolean dismissed;
    private List<String> log;
    private ScenePortalHandle handle;

    private static final int CANVAS_WIDTH = 480;
    private static final int CANVAS_HEIGHT = 240;
    private static final int STUB_CHAR_WIDTH = 8;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(STUB_CHAR_WIDTH, 16);
        harness = SceneInteractionHarness.create(measurer);
        runtime = harness.getRuntime();
        layoutEngine = new SceneLayoutEngine(measurer);
        sceneRoot = new SceneNode();
        harness.mountRoot(sceneRoot, CANVAS_WIDTH, CANVAS_HEIGHT);
        visible = Signal.create(Boolean.TRUE);
        dismissed = new AtomicBoolean(false);
        log = new ArrayList<>();
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== 辅助方法 ====================

    private void openDialog(List<SceneDialog.Button> buttons) {
        SceneDialog.Props props = new SceneDialog.Props(visible, "确认操作", "确定继续吗？", buttons,
                () -> {
                    dismissed.set(true);
                    visible.set(Boolean.FALSE);
                });
        handle = SceneDialog.create(runtime, props);
        runtime.flush();
        doLayout();
    }

    private void doLayout() {
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        for (SceneOverlayHost.Entry entry : runtime.getOverlayHost().bottomFirst()) {
            layoutEngine.layout(entry.getRoot(), new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        }
    }

    private SceneNode overlayRoot() {
        return runtime.getOverlayHost().bottomFirst().get(0).getRoot();
    }

    /** 遮罩 → 卡片。 */
    private SceneNode cardNode() {
        return overlayRoot().__getChildren().get(0);
    }

    /** 卡片 → 第 index 个按钮（卡片子节点：标题 0、正文 1、按钮行 2）。 */
    private SceneNode buttonNode(int index) {
        SceneNode buttonRow = cardNode().__getChildren().get(2);
        return buttonRow.__getChildren().get(index);
    }

    private int overlaySize() {
        return runtime.getOverlayHost().size();
    }

    private int[] absCenter(SceneNode node) {
        LayoutBox b = (LayoutBox) node.getCachedLayout();
        int ax = b.getX();
        int ay = b.getY();
        SceneNode parent = node.__getParent();
        while (parent != null) {
            LayoutBox pb = (LayoutBox) parent.getCachedLayout();
            if (pb != null) {
                ax += pb.getX();
                ay += pb.getY();
            }
            parent = parent.__getParent();
        }
        return new int[] {ax + b.getWidth() / 2, ay + b.getHeight() / 2};
    }

    private void pressAndReleaseAt(int absX, int absY) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN, absX, absY,
                SceneMouseButton.LEFT, 0, 0, 0, false, false, false, false, 1000L));
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_UP, absX, absY,
                SceneMouseButton.LEFT, 0, 0, 0, false, false, false, false, 1001L));
        runtime.route(sceneRoot, fb.drainFrame(), 0, 0);
        runtime.flush();
    }

    private void routeKeyAndFlush(SceneKey key) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofKey(key, SceneKeyAction.PRESSED,
                false, false, false, false, 0, 0, 1000L));
        runtime.route(sceneRoot, fb.drainFrame(), 0, 0);
        runtime.flush();
    }

    // ==================== 挂载与结构 ====================

    @Test
    public void visibleTrueMountsScrimCardAndButtons() {
        openDialog(Arrays.asList(
                SceneDialog.Button.of("取消", () -> log.add("cancel")),
                new SceneDialog.Button("确定", SceneDialog.ButtonKind.PRIMARY, true, () -> log.add("confirm"))));
        Assert.assertEquals("对话框 overlay 挂载", 1, overlaySize());
        Assert.assertEquals("卡片 3 子节点（标题/正文/按钮行）", 3, cardNode().__getChildren().size());
        Assert.assertEquals("按钮行 2 按钮", 2, cardNode().__getChildren().get(2).__getChildren().size());
        Assert.assertEquals("打开聚焦首按钮", true, runtime.interactionState(buttonNode(0)).focused().get());
    }

    @Test
    public void visibleFalseUnmountsOverlay() {
        openDialog(Arrays.asList(SceneDialog.Button.of("关闭", null)));
        visible.set(Boolean.FALSE);
        runtime.flush();
        Assert.assertEquals("visible=false 卸载", 0, overlaySize());
    }

    // ==================== 按钮与关闭语义 ====================

    @Test
    public void buttonClickRunsOnClickAndDismisses() {
        openDialog(Arrays.asList(
                SceneDialog.Button.of("取消", () -> log.add("cancel")),
                new SceneDialog.Button("确定", SceneDialog.ButtonKind.PRIMARY, true, () -> log.add("confirm"))));
        int[] c = absCenter(buttonNode(1));
        pressAndReleaseAt(c[0], c[1]);
        Assert.assertEquals("onClick 执行", Arrays.asList("confirm"), log);
        Assert.assertTrue("点击后请求关闭", dismissed.get());
        Assert.assertEquals("overlay 卸载", 0, overlaySize());
    }

    @Test
    public void nonClosingButtonRunsOnClickOnly() {
        openDialog(Arrays.asList(
                new SceneDialog.Button("帮助", SceneDialog.ButtonKind.NORMAL, false, () -> log.add("help")),
                SceneDialog.Button.of("关闭", () -> log.add("close"))));
        int[] c = absCenter(buttonNode(0));
        pressAndReleaseAt(c[0], c[1]);
        Assert.assertEquals("onClick 执行", Arrays.asList("help"), log);
        Assert.assertFalse("closesDialog=false 不关闭", dismissed.get());
        Assert.assertEquals("overlay 保持", 1, overlaySize());
    }

    @Test
    public void escapeRequestsDismiss() {
        openDialog(Arrays.asList(SceneDialog.Button.of("关闭", null)));
        routeKeyAndFlush(SceneKey.ESCAPE);
        Assert.assertTrue("ESC 请求关闭", dismissed.get());
        Assert.assertEquals("overlay 卸载", 0, overlaySize());
    }

    // ==================== 模态与焦点陷阱 ====================

    @Test
    public void scrimBlocksPointerToMainTree() {
        // 主树命中探针：覆盖 (10,10) 的 hitTestable 节点
        SceneNode probe = new SceneNode();
        probe.setPreferredWidth(30);
        probe.setPreferredHeight(30);
        sceneRoot.appendChild(probe);
        runtime.on(probe, SceneEventType.POINTER_DOWN, (ev, ctx) -> log.add("main-hit"));
        openDialog(Arrays.asList(SceneDialog.Button.of("关闭", null)));

        // 点击遮罩区域（卡片外）：应被遮罩拦截，主树探针不命中
        pressAndReleaseAt(10, 10);
        Assert.assertEquals("遮罩拦截主树点击", 0, log.size());
        Assert.assertFalse("遮罩点击不触发关闭", dismissed.get());
    }

    @Test
    public void tabCycleStaysInsideDialog() {
        openDialog(Arrays.asList(
                SceneDialog.Button.of("取消", null),
                new SceneDialog.Button("确定", SceneDialog.ButtonKind.PRIMARY, true, null)));
        // Tab 从首按钮移到次按钮（Tab 环限定在 active overlay 内）
        routeKeyAndFlush(SceneKey.TAB);
        Assert.assertEquals("Tab 后焦点在次按钮", true, runtime.interactionState(buttonNode(1)).focused().get());
        // 再 Tab 循环回首按钮
        routeKeyAndFlush(SceneKey.TAB);
        Assert.assertEquals("Tab 循环回首按钮", true, runtime.interactionState(buttonNode(0)).focused().get());
    }

    @Test
    public void enterActivatesFocusedButton() {
        openDialog(Arrays.asList(
                SceneDialog.Button.of("取消", () -> log.add("cancel")),
                new SceneDialog.Button("确定", SceneDialog.ButtonKind.PRIMARY, true, () -> log.add("confirm"))));
        // 焦点在首按钮（取消）；Enter 激活它
        routeKeyAndFlush(SceneKey.ENTER);
        Assert.assertEquals("Enter 激活焦点按钮", Arrays.asList("cancel"), log);
        Assert.assertTrue("激活后关闭", dismissed.get());
    }
}
