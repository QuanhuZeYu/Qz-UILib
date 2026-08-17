package club.heiqi.uilib.ui.scene.integration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.control.SceneContextMenu;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
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
 * SceneContextMenu 独立单元测试：overlay 挂载/锚点、点击激活与关闭、
 * ESC/外部点击关闭、↑/↓/Enter 键盘导航（跳分隔线、disabled 不激活）、Handle 幂等关闭。
 *
 * <p>锚点定位由 SceneFramePipeline 的 layoutOverlays 在真机执行；测试无管线，
 * overlay root 手动全尺寸布局 + open(0,0) 沙箱（与 SceneSelectPrimitiveTest 同款假设）。</p>
 */
public class SceneContextMenuTest {

    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    private SceneInteractionHarness harness;

    private List<String> activationLog;

    private static final int CANVAS_WIDTH = 240;
    private static final int CANVAS_HEIGHT = 160;
    private static final int STUB_CHAR_WIDTH = 8;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(STUB_CHAR_WIDTH, 16);
        harness = SceneInteractionHarness.create(measurer);
        runtime = harness.getRuntime();
        layoutEngine = new SceneLayoutEngine(measurer);
        sceneRoot = new SceneNode();
        activationLog = new ArrayList<>();
        harness.mountRoot(sceneRoot, CANVAS_WIDTH, CANVAS_HEIGHT);
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== 辅助方法 ====================

    private List<SceneContextMenu.MenuItem> threeItems() {
        return Arrays.asList(
                SceneContextMenu.MenuItem.of("复制", () -> activationLog.add("copy")),
                SceneContextMenu.MenuItem.of("粘贴", () -> activationLog.add("paste")),
                SceneContextMenu.MenuItem.of("删除", () -> activationLog.add("delete")));
    }

    private SceneContextMenu.Handle openAt(int x, int y, List<SceneContextMenu.MenuItem> items) {
        SceneContextMenu.Handle handle = SceneContextMenu.open(runtime, x, y, items);
        runtime.flush();
        doLayout();
        return handle;
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

    /** 菜单直接子节点（item 行/分隔线，按 items 顺序）。 */
    private SceneNode menuChild(int index) {
        return overlayRoot().__getChildren().get(index);
    }

    private int overlaySize() {
        return runtime.getOverlayHost().size();
    }

    private void routeKeyAndFlush(SceneKey key) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofKey(key, SceneKeyAction.PRESSED,
                false, false, false, false, 0, 0, 1000L));
        runtime.route(sceneRoot, fb.drainFrame(), 0, 0);
        runtime.flush();
    }

    private void pressAt(int absX, int absY) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN, absX, absY,
                SceneMouseButton.LEFT, 0, 0, 0, false, false, false, false, 1000L));
        runtime.route(sceneRoot, fb.drainFrame(), 0, 0);
        runtime.flush();
    }

    /** DOWN + UP 合成 CLICK（菜单项激活走 CLICK 事件）。 */
    private void pressAndReleaseAt(int absX, int absY) {
        pressAt(absX, absY);
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_UP, absX, absY,
                SceneMouseButton.LEFT, 0, 0, 0, false, false, false, false, 1000L));
        runtime.route(sceneRoot, fb.drainFrame(), 0, 0);
        runtime.flush();
    }

    private int[] absCenter(SceneNode node) {
        LayoutBox b = (LayoutBox) node.getCachedLayout();
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
        return new int[] {ax + b.getWidth() / 2, ay + b.getHeight() / 2};
    }

    // ==================== 打开/关闭 ====================

    @Test
    public void openRegistersOverlayAndHandleState() {
        SceneContextMenu.Handle handle = openAt(0, 0, threeItems());
        Assert.assertTrue("打开后 isOpen", handle.isOpen());
        Assert.assertEquals("overlay 挂载 1 个", 1, overlaySize());
        Assert.assertEquals("菜单子节点 = 3 item 行", 3, overlayRoot().__getChildren().size());
    }

    @Test
    public void handleCloseIsIdempotentAndUnmountsOverlay() {
        SceneContextMenu.Handle handle = openAt(0, 0, threeItems());
        handle.close();
        runtime.flush();
        Assert.assertFalse("close 后 isOpen=false", handle.isOpen());
        Assert.assertEquals("close 后 overlay 卸载", 0, overlaySize());
        handle.close(); // 幂等无异常
        runtime.flush();
        Assert.assertEquals(0, overlaySize());
    }

    @Test
    public void clickItemFiresOnSelectAndCloses() {
        SceneContextMenu.Handle handle = openAt(0, 0, threeItems());
        SceneNode item = menuChild(1);
        int[] c = absCenter(item);
        pressAndReleaseAt(c[0], c[1]);
        Assert.assertEquals("激活一次 paste", Arrays.asList("paste"), activationLog);
        Assert.assertFalse("选择后关闭", handle.isOpen());
        Assert.assertEquals(0, overlaySize());
    }

    @Test
    public void clickOutsideClosesWithoutActivation() {
        SceneContextMenu.Handle handle = openAt(0, 0, threeItems());
        // 菜单锚点 (0,0)，点击远处空白
        pressAt(CANVAS_WIDTH - 2, CANVAS_HEIGHT - 2);
        runtime.flush();
        Assert.assertEquals("外部点击不激活", 0, activationLog.size());
        Assert.assertFalse("外部点击关闭", handle.isOpen());
        Assert.assertEquals(0, overlaySize());
    }

    @Test
    public void escapeClosesMenu() {
        SceneContextMenu.Handle handle = openAt(0, 0, threeItems());
        routeKeyAndFlush(SceneKey.ESCAPE);
        Assert.assertFalse("ESC 关闭", handle.isOpen());
        Assert.assertEquals(0, overlaySize());
    }

    // ==================== 键盘导航 ====================

    @Test
    public void arrowKeysNavigateAndEnterActivates() {
        SceneContextMenu.Handle handle = openAt(0, 0, threeItems());
        routeKeyAndFlush(SceneKey.ARROW_DOWN); // 无高亮 → 高亮 0（复制）
        routeKeyAndFlush(SceneKey.ARROW_DOWN); // → 粘贴
        routeKeyAndFlush(SceneKey.ARROW_DOWN); // → 删除
        routeKeyAndFlush(SceneKey.ARROW_UP);   // → 粘贴
        routeKeyAndFlush(SceneKey.ENTER);
        Assert.assertEquals("Enter 激活高亮项（粘贴）", Arrays.asList("paste"), activationLog);
        Assert.assertFalse("激活后关闭", handle.isOpen());
    }

    @Test
    public void arrowNavigationWrapsAround() {
        openAt(0, 0, threeItems());
        routeKeyAndFlush(SceneKey.ARROW_DOWN); // 0
        routeKeyAndFlush(SceneKey.ARROW_UP);   // wrap → 2（删除）
        routeKeyAndFlush(SceneKey.ENTER);
        Assert.assertEquals("上移环绕激活末项", Arrays.asList("delete"), activationLog);
    }

    @Test
    public void disabledItemNotActivatedButStillHighlighted() {
        List<SceneContextMenu.MenuItem> items = Arrays.asList(
                SceneContextMenu.MenuItem.of("复制", () -> activationLog.add("copy")),
                SceneContextMenu.MenuItem.of("粘贴", false, () -> activationLog.add("paste")),
                SceneContextMenu.MenuItem.of("删除", () -> activationLog.add("delete")));
        SceneContextMenu.Handle handle = openAt(0, 0, items);
        routeKeyAndFlush(SceneKey.ARROW_DOWN); // 高亮 0
        routeKeyAndFlush(SceneKey.ARROW_DOWN); // 高亮 1（disabled）
        routeKeyAndFlush(SceneKey.ENTER);
        Assert.assertEquals("disabled 项不激活", 0, activationLog.size());
        Assert.assertFalse("Enter 仍关闭菜单", handle.isOpen());
    }

    @Test
    public void separatorSkippedInNavigation() {
        List<SceneContextMenu.MenuItem> items = Arrays.asList(
                SceneContextMenu.MenuItem.of("复制", () -> activationLog.add("copy")),
                SceneContextMenu.MenuItem.divider(),
                SceneContextMenu.MenuItem.of("删除", () -> activationLog.add("delete")));
        openAt(0, 0, items);
        Assert.assertEquals("菜单子节点 = 2 item + 1 分隔线", 3, overlayRoot().__getChildren().size());
        routeKeyAndFlush(SceneKey.ARROW_DOWN); // 高亮 0（复制）
        routeKeyAndFlush(SceneKey.ARROW_DOWN); // 跳分隔线 → 删除
        routeKeyAndFlush(SceneKey.ENTER);
        Assert.assertEquals("导航跳过分隔线激活删除", Arrays.asList("delete"), activationLog);
    }

    @Test
    public void disabledItemClickDoesNotActivateButCloses() {
        List<SceneContextMenu.MenuItem> items = Arrays.asList(
                SceneContextMenu.MenuItem.of("复制", () -> activationLog.add("copy")),
                SceneContextMenu.MenuItem.of("粘贴", false, null));
        SceneContextMenu.Handle handle = openAt(0, 0, items);
        SceneNode item = menuChild(1);
        int[] c = absCenter(item);
        pressAndReleaseAt(c[0], c[1]);
        Assert.assertEquals("disabled 点击不激活", 0, activationLog.size());
        Assert.assertFalse("点击仍关闭", handle.isOpen());
    }
}
