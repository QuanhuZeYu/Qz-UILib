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
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.SceneOverlayHost;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

/**
 * SceneSelectPrimitive 独立单元测试 —— 验证无样式单选下拉行为核心契约。
 *
 * <p>primitive 用 NOOP_CHROME（兼容构造不传 chrome），只验证行为与结构：
 * trigger 常驻主树、listbox 经 portalAnchored 提升为 overlay root、
 * expanded/highlightedIndex signal、键盘导航、overlay dismiss 语义、
 * AnchorProvider + 保护节点。</p>
 */
public class SceneSelectPrimitiveTest {

    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;

    private Signal<Integer> selectedSignal;
    private Signal<Boolean> enabledSignal;
    private AtomicInteger selectCount;
    private Integer lastSelectValue;

    private MountHandle handle;
    private SceneSelectPrimitive.Result result;
    private SceneNode trigger;
    /** 语义化交互注入 harness（trigger click / 外部 dismiss clickAt 入口）；其 runtime 即上方 runtime 字段。
     *  仅用于 trigger 开合点击与 overlay 外部 dismiss；overlay item 点击 + 键盘导航走白盒回退（overlay 树外路由）
     *  （overlay 不在 sceneRoot 子树，harness.centerOf 对 overlay 节点坐标不适用）。判据见 §7.1。 */
    private SceneInteractionHarness harness;

    private static final int CANVAS_WIDTH = 240;
    private static final int CANVAS_HEIGHT = 160;
    private static final int STUB_CHAR_WIDTH = 8;

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

        // 用兼容构造（不传 chrome → 默认 NOOP_CHROME）
        SceneSelectPrimitive.Props props = new SceneSelectPrimitive.Props(
                selectedSignal, OPTIONS, enabledSignal,
                next -> {
                    selectCount.incrementAndGet();
                    lastSelectValue = next;
                });
        final SceneSelectPrimitive.Result[] holder = new SceneSelectPrimitive.Result[1];
        handle = runtime.mount(sceneRoot, () -> {
            holder[0] = SceneSelectPrimitive.create(runtime, props);
            return holder[0].trigger();
        });
        result = holder[0];
        trigger = result.trigger();
        runtime.flush();
        // 挂载路由根并对齐 layout，供 harness.click(trigger)/clickAt 取中心 + route
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
        for (SceneOverlayHost.Entry entry : runtime.getOverlayHost().bottomFirst()) {
            layoutEngine.layout(entry.getRoot(), new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
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

    private LayoutBox box(SceneNode node) {
        return (LayoutBox) node.getCachedLayout();
    }

    /** 计算 overlay 节点几何中心绝对坐标（沿 overlay 父链累加）。
     *  <p>白盒回退（overlay 树外路由）：overlay item 不在 sceneRoot 子树，harness.centerOf 对 overlay 节点坐标不适用。判据见 §7.1。</p> */
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

    /** 点击 overlay item 中心（DOWN+UP 合成 CLICK）。白盒回退（overlay 树外路由）：clickCenter 命中 overlay item，harness 不接管 overlay 路由。 */
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

    /** 键盘事件注入（PRESSED）。白盒回退（overlay 树外路由 + 自定义 native code）：键盘导航属 overlay，且用 NATIVE_NONE，harness 固定 0,0 不适用。 */
    private void routeKey(SceneKey key) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofKey(key, SceneKeyAction.PRESSED,
                false, false, false, false, RawInputEvent.NATIVE_NONE, RawInputEvent.NATIVE_NONE, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(sceneRoot, frame, 0, 0);
    }

    private void openByClick() {
        harness.click(trigger);
        doLayout();
    }

    // ==================== 契约 1：trigger 常驻主树 ====================

    /** trigger 挂在 sceneRoot 下，展开/关闭都不移动，主树结构始终只有 label + arrow。 */
    @Test
    public void triggerStaysInMainTreeAcrossExpandCollapse() {
        doLayout();
        Assert.assertSame("trigger 是 sceneRoot 唯一子节点", trigger, sceneRoot.__getChildren().get(0));
        Assert.assertEquals("主树 trigger 只含 label + arrow", 2, trigger.__getChildren().size());

        // 展开
        openByClick();
        Assert.assertSame("展开后 trigger 仍在 sceneRoot 下", trigger, sceneRoot.__getChildren().get(0));
        Assert.assertEquals("展开后主树 trigger 仍只含 label + arrow", 2, trigger.__getChildren().size());

        // 关闭
        harness.click(trigger);
        Assert.assertSame("关闭后 trigger 仍在 sceneRoot 下", trigger, sceneRoot.__getChildren().get(0));
        Assert.assertEquals("关闭后主树结构不变", 2, trigger.__getChildren().size());
    }

    // ==================== 契约 2：listbox 经 portalAnchored 提升为 overlay root ====================

    /** 展开时 overlayHost 挂载 1 个 overlay，其 root 是 listbox（COLUMN），不是 trigger。 */
    @Test
    public void listboxMountsAsOverlayRootViaPortal() {
        doLayout();
        Assert.assertTrue("初始无 overlay", runtime.getOverlayHost().isEmpty());

        openByClick();
        Assert.assertEquals("展开后挂载 1 个 overlay", 1, runtime.getOverlayHost().size());
        SceneNode listbox = overlayRoot();
        Assert.assertNotSame("overlay root 不是 trigger", trigger, listbox);
        Assert.assertEquals("listbox 为 COLUMN 布局", FlexDirection.COLUMN, listbox.getFlexDirection());
        Assert.assertTrue("listbox 可滚动", listbox.isScrollable());
        Assert.assertTrue("listbox 裁剪子节点", listbox.isClipChildren());
        Assert.assertEquals("listbox 持有所有选项", OPTIONS.size(), listbox.__getChildren().size());

        // 关闭后 overlay 卸载
        harness.click(trigger);
        Assert.assertTrue("关闭后 overlay 卸载", runtime.getOverlayHost().isEmpty());
    }

    // ==================== 契约 3：expanded/highlightedIndex signal ====================

    /** 点击 trigger 切 expanded；展开时箭头文本切 ▲；highlightedIndex 同步当前选中项。 */
    @Test
    public void expandedAndHighlightedSignalsDeriveCorrectly() {
        doLayout();
        Assert.assertFalse("初始未展开", result.expanded().get());
        Assert.assertEquals("初始箭头 ▼", "▼", arrowNode().getText());

        // 点击展开
        openByClick();
        Assert.assertTrue("点击后展开", result.expanded().get());
        Assert.assertEquals("展开后箭头 ▲", "▲", arrowNode().getText());
        Assert.assertEquals("展开时 highlightedIndex 同步当前选中项 0",
                Integer.valueOf(0), result.highlightedIndex().get());

        // 外部切 selectedIndex=2，重新展开 → highlightedIndex 同步 2
        clickCenter(trigger);
        runtime.flush();
        selectedSignal.set(Integer.valueOf(2));
        runtime.flush();
        openByClick();
        Assert.assertEquals("重新展开时 highlightedIndex 同步新选中项 2",
                Integer.valueOf(2), result.highlightedIndex().get());
    }

    // ==================== 契约 4：键盘导航（方向键/Enter/Escape） ====================

    /** ARROW_DOWN 展开+高亮同步选中项；再次 ARROW_DOWN 高亮+1；Enter 选择高亮+关闭；Space 展开；Esc 关闭。 */
    @Test
    public void keyboardNavigatesHighlightsSelectsAndCloses() {
        doLayout();
        runtime.requestFocus(trigger);

        // ↓ 未展开 → 展开 + 高亮同步选中项 0
        routeKey(SceneKey.ARROW_DOWN);
        runtime.flush();
        doLayout();
        Assert.assertTrue("↓ 应展开", result.expanded().get());
        Assert.assertEquals("↓ 展开后高亮 0", Integer.valueOf(0), result.highlightedIndex().get());

        // 再次 ↓ → 高亮 +1 = 1
        routeKey(SceneKey.ARROW_DOWN);
        runtime.flush();
        Assert.assertEquals("第二次 ↓ 高亮 1", Integer.valueOf(1), result.highlightedIndex().get());

        // 再次 ↓ → 高亮 +1 = 2
        routeKey(SceneKey.ARROW_DOWN);
        runtime.flush();
        Assert.assertEquals("第三次 ↓ 高亮 2", Integer.valueOf(2), result.highlightedIndex().get());

        // 再次 ↓ → 边界裁剪仍 2
        routeKey(SceneKey.ARROW_DOWN);
        runtime.flush();
        Assert.assertEquals("↓ 末项边界裁剪仍 2", Integer.valueOf(2), result.highlightedIndex().get());

        // ↑ → 高亮 -1 = 1
        routeKey(SceneKey.ARROW_UP);
        runtime.flush();
        Assert.assertEquals("↑ 高亮回 1", Integer.valueOf(1), result.highlightedIndex().get());

        // Enter → 选择高亮 1 + 关闭
        routeKey(SceneKey.ENTER);
        runtime.flush();
        Assert.assertEquals("Enter 上抛高亮 1", Integer.valueOf(1), lastSelectValue);
        Assert.assertFalse("Enter 后关闭", result.expanded().get());
        Assert.assertTrue("Enter 后 overlay 卸载", runtime.getOverlayHost().isEmpty());

        // Space → 重新展开
        routeKey(SceneKey.SPACE);
        runtime.flush();
        Assert.assertTrue("Space 重新展开", result.expanded().get());

        // Escape → 关闭
        routeKey(SceneKey.ESCAPE);
        runtime.flush();
        Assert.assertFalse("Escape 关闭", result.expanded().get());
        Assert.assertTrue("Escape 后 overlay 卸载", runtime.getOverlayHost().isEmpty());
    }

    // ==================== 契约 5：HOME/END 不被 primitive 处理（契约边界） ====================

    /** primitive 的 handleKeyDown 不处理 HOME/END：展开态下 HOME/END 不改变 highlightedIndex。 */
    @Test
    public void homeAndEndAreNotHandledByPrimitive() {
        doLayout();
        runtime.requestFocus(trigger);
        // 先展开，高亮 0
        routeKey(SceneKey.ARROW_DOWN);
        runtime.flush();
        Assert.assertEquals("展开后高亮 0", Integer.valueOf(0), result.highlightedIndex().get());

        // HOME 不被处理 → 高亮不变
        routeKey(SceneKey.HOME);
        runtime.flush();
        Assert.assertEquals("HOME 不改变 highlightedIndex", Integer.valueOf(0), result.highlightedIndex().get());

        // END 不被处理 → 高亮不变
        routeKey(SceneKey.END);
        runtime.flush();
        Assert.assertEquals("END 不改变 highlightedIndex", Integer.valueOf(0), result.highlightedIndex().get());
    }

    // ==================== 契约 6：overlay dismiss 语义（外部点击关闭） ====================

    /** 展开后点击 overlay 外部空白 → dismissRequest 触发 → expanded=false → overlay 卸载。 */
    @Test
    public void outsidePointerDismissesOverlay() {
        doLayout();
        openByClick();
        Assert.assertTrue("前置：已展开", result.expanded().get());
        Assert.assertEquals("前置：有 1 个 overlay", 1, runtime.getOverlayHost().size());

        // 点击 canvas 右下角空白（不在 listbox 内，不在 trigger 内）——单 DOWN 即触发 dismiss
        harness.pressAt(CANVAS_WIDTH - 1, CANVAS_HEIGHT - 1);

        Assert.assertFalse("外部点击后 expanded=false", result.expanded().get());
        Assert.assertTrue("外部点击后 overlay 卸载", runtime.getOverlayHost().isEmpty());
    }

    // ==================== 契约 7：AnchorProvider + 保护节点 ====================

    /** overlay entry 的 anchorProvider 指向 trigger；protectedNodes 包含 trigger。
     *  <p>注：测试沙箱中 overlay anchor 默认 0（无布局系统定位），listbox 与 trigger 重叠，
     *  故不点击 trigger 验证 dismiss 豁免（那会命中重叠的 listbox item），只验证结构契约。</p> */
    @Test
    public void overlayEntryAnchorAndProtectedNodesReferenceTrigger() {
        doLayout();
        openByClick();

        SceneOverlayHost.Entry entry = runtime.getOverlayHost().bottomFirst().get(0);
        Assert.assertNotNull("entry 应有 anchorProvider", entry.getAnchorProvider());
        Assert.assertSame("anchorProvider 节点 = trigger", trigger, entry.getAnchorProvider().getNode());
        Assert.assertTrue("protectedNodes 应包含 trigger",
                entry.getProtectedNodes().contains(trigger));
        Assert.assertEquals("dismissPolicy 为 DEFAULT",
                club.heiqi.uilib.ui.scene.overlay.OverlayDismissPolicy.DEFAULT,
                entry.getDismissPolicy());
    }

    // ==================== 契约 8：NOOP_CHROME 路径（无样式 listbox 仍可工作） ====================

    /** 用兼容构造（NOOP_CHROME）：listbox/item 无背景色，但点击 item 仍上抛 onSelect + 关闭。 */
    @Test
    public void noopChromeListboxWorksWithoutVisualStyle() {
        doLayout();
        openByClick();

        SceneNode listbox = overlayRoot();
        Assert.assertEquals("NOOP_CHROME: listbox 无背景色", 0, listbox.getBackgroundColor());
        Assert.assertEquals("NOOP_CHROME: listbox 无圆角", 0, listbox.getCornerRadius());

        for (int i = 0; i < OPTIONS.size(); i++) {
            SceneNode item = overlayItem(i);
            Assert.assertEquals("NOOP_CHROME: item[" + i + "] 无背景色", 0, item.getBackgroundColor());
            // item label 文本 = options[i]
            SceneNode itemLabel = item.__getChildren().get(0);
            Assert.assertEquals("item[" + i + "] label 文本", OPTIONS.get(i), itemLabel.getText());
        }

        // 点击 item[2] → 上抛 2 + 关闭
        clickCenter(overlayItem(2));
        runtime.flush();
        Assert.assertEquals("NOOP_CHROME 点击 item 上抛 2", Integer.valueOf(2), lastSelectValue);
        Assert.assertTrue("NOOP_CHROME 点击 item 后关闭", runtime.getOverlayHost().isEmpty());
    }

    // ==================== 契约 9：trigger label 派生当前选中文本 ====================

    /** trigger label 文本随 selectedIndex 派生；越界时为空串。 */
    @Test
    public void triggerLabelDerivesSelectedText() {
        runtime.flush();
        Assert.assertEquals("selectedIndex=0 → label=Low", "Low", labelNode().getText());

        selectedSignal.set(Integer.valueOf(1));
        runtime.flush();
        Assert.assertEquals("selectedIndex=1 → label=Mid", "Mid", labelNode().getText());

        selectedSignal.set(Integer.valueOf(2));
        runtime.flush();
        Assert.assertEquals("selectedIndex=2 → label=High", "High", labelNode().getText());

        // 越界 → 空串
        selectedSignal.set(Integer.valueOf(99));
        runtime.flush();
        Assert.assertEquals("越界 selectedIndex → label 空串", "", labelNode().getText());

        // null → 空串
        selectedSignal.set(null);
        runtime.flush();
        Assert.assertEquals("null selectedIndex → label 空串", "", labelNode().getText());
    }

    // ==================== 契约 10：disabled 拦截展开与选择 ====================

    /** disabled 态：点击 trigger 不展开，键盘不展开，不上抛。 */
    @Test
    public void disabledBlocksExpandAndSelect() {
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
}
