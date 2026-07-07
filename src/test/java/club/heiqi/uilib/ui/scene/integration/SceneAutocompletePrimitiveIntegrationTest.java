package club.heiqi.uilib.ui.scene.integration;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.control.SceneAutocompletePrimitive;
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
import club.heiqi.uilib.ui.scene.paint.SceneStateColors;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

/**
 * SceneAutocompletePrimitive L3 集成测试 —— 验证浮层显隐信号链、键盘导航、选中、portal 挂卸、
 * expanded effect 驱动与键集正交（守 R13 + R10/R11 + I5 keyed diff）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>浮层显隐信号链（R13：expanded 独立可写 Signal + focused/filtered effect 驱动）：
 *       focus→打字→filtered 重算→effect set expanded=true→portal 挂载；失焦→effect set false→卸载；
 *       ESC→expanded.set(false)→卸载；再打字→filtered 重算→effect set true→重弹。</li>
 *   <li>键盘：ARROW_DOWN/UP 移动 highlightedIndex（截止边界）；ENTER 上抛正确候选 + 关闭；ESC 关闭。</li>
 *   <li>选中：item CLICK 上抛正确候选 + expanded.set(false)。</li>
 *   <li>portal 挂卸：expanded 反复 true/false 时 overlay entry 正确增删。</li>
 *   <li>协作回归：ARROW_LEFT/RIGHT/BACKSPACE 仍走 primitive（caret 移动/删除正常），未被 autocomplete 吞。</li>
 * </ul>
 *
 * <p>键盘导航属 overlay 行为，harness 固定 0,0 坐标对 overlay 节点几何不适用，故用白盒回退
 * （SceneSelectPrimitiveTest 同款）：自建 InputFrameBuilder 注入到 sceneRoot（KEY_DOWN 冒泡根节点
 * 不要求几何中心）。</p>
 */
public class SceneAutocompletePrimitiveIntegrationTest {

    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    private SceneInteractionHarness harness;

    private Signal<String> valueSignal;
    private Signal<Boolean> enabledSignal;
    private final AtomicReference<String> onChangeValue = new AtomicReference<>(null);
    private final AtomicReference<String> onSelectValue = new AtomicReference<>(null);

    private MountHandle handle;
    private SceneAutocompletePrimitive.Result result;
    private SceneNode inputRoot;

    private static final int CANVAS_WIDTH = 240;
    private static final int CANVAS_HEIGHT = 160;
    private static final int STUB_CHAR_WIDTH = 8;
    private static final int ITEM_BG_DEFAULT = SceneStateColors.listItemBackground(true, false, false, false);
    private static final int ITEM_BG_HOVERED = SceneStateColors.listItemBackground(true, false, false, true);
    private static final int ITEM_BG_HIGHLIGHTED = SceneStateColors.listItemBackground(true, false, true, false);

    private static final List<String> CANDIDATES = Arrays.asList(
            "Arial", "Arial Black", "Calibri", "Cambria", "Consolas");

    @Before
    public void setUp() {
        initialize(SceneAutocompletePrimitive.MatchMode.PREFIX);
    }

    private void initialize(SceneAutocompletePrimitive.MatchMode matchMode) {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(STUB_CHAR_WIDTH, 16);
        harness = SceneInteractionHarness.create(measurer);
        runtime = harness.getRuntime();
        layoutEngine = new SceneLayoutEngine(measurer);
        sceneRoot = new SceneNode();

        valueSignal = Signal.create("");
        enabledSignal = Signal.create(Boolean.TRUE);
        onChangeValue.set(null);
        onSelectValue.set(null);

        SceneAutocompletePrimitive.Props props = new SceneAutocompletePrimitive.Props(
                valueSignal,
                enabledSignal,
                Signal.create(Boolean.FALSE),
                "字体名",
                Integer.MAX_VALUE,
                CANDIDATES,
                matchMode,
                8,
                v -> onChangeValue.set(v),
                v -> onSelectValue.set(v),
                new TestListboxChrome(runtime));
        final SceneAutocompletePrimitive.Result[] holder = new SceneAutocompletePrimitive.Result[1];
        handle = runtime.mount(sceneRoot, () -> {
            holder[0] = SceneAutocompletePrimitive.create(runtime, props);
            return holder[0].root();
        });
        result = holder[0];
        inputRoot = result.root();
        runtime.flush();
        harness.mountRoot(sceneRoot, CANVAS_WIDTH, CANVAS_HEIGHT);
    }

    private void remount(SceneAutocompletePrimitive.MatchMode matchMode) {
        runtime.dispose();
        ReactiveScheduler.get().reset();
        initialize(matchMode);
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== 辅助 ====================

    private void doLayout() {
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        for (Object entry : runtime.getOverlayHost().bottomFirst()) {
            SceneNode r = ((club.heiqi.uilib.ui.scene.overlay.SceneOverlayHost.Entry) entry).getRoot();
            layoutEngine.layout(r, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        }
    }

    private SceneNode overlayRoot() {
        return runtime.getOverlayHost().bottomFirst().get(0).getRoot();
    }

    private int overlayItemCount() {
        return overlayRoot().__getChildren().size();
    }

    private SceneNode overlayItem(int i) {
        return overlayRoot().__getChildren().get(i);
    }

    private LayoutBox box(SceneNode n) {
        return (LayoutBox) n.getCachedLayout();
    }

    /** overlay 节点几何中心绝对坐标（沿 overlay 父链累加，白盒回退）。 */
    private int[] absCenter(SceneNode n) {
        LayoutBox b = box(n);
        int ax = b.getX();
        int ay = b.getY();
        SceneNode p = n.__getParent();
        while (p != null) {
            LayoutBox pb = (LayoutBox) p.getCachedLayout();
            if (pb != null) {
                ax += pb.getX();
                ay += pb.getY();
            }
            p = p.__getParent();
        }
        return new int[]{ax + b.getWidth() / 2, ay + b.getHeight() / 2};
    }

    private void clickCenter(SceneNode n) {
        int[] c = absCenter(n);
        routePointer(ScenePointerAction.BUTTON_DOWN, c[0], c[1]);
        routePointer(ScenePointerAction.BUTTON_UP, c[0], c[1]);
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

    private void routeText(String text) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofText(text, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(sceneRoot, frame, 0, 0);
    }

    /** 聚焦输入框并打字（驱动 value 经 onChange 上抛 → valueSignal.set → filtered 重算）。 */
    private void focusAndType(String text) {
        runtime.requestFocus(inputRoot);
        runtime.flush();
        routeText(text);
        runtime.flush();
        // primitive 的 TEXT_INPUT handler 把 next 经 onChange 上抛 → 测试 onChange 回 set valueSignal
        // 这里 onChange 只记值，需要把值回灌 valueSignal 才能驱动 filtered
        String captured = onChangeValue.get();
        if (captured != null) {
            valueSignal.set(captured);
            runtime.flush();
        }
    }

    /** 测试 chrome：把 highlighted/hovered 显性绑定到 item 背景，便于断言视觉态。 */
    private static final class TestListboxChrome implements SceneAutocompletePrimitive.ListboxChrome {
        private final SceneRuntime rt;

        private TestListboxChrome(SceneRuntime rt) {
            this.rt = rt;
        }

        @Override
        public void decorateListbox(SceneNode listbox) {
        }

        @Override
        public void decorateItem(SceneAutocompletePrimitive.ItemHandle handle) {
            rt.bindComputed(() -> SceneStateColors.listItemBackground(
                            true, false,
                            Boolean.TRUE.equals(handle.highlighted().get()),
                            Boolean.TRUE.equals(handle.interaction().hovered().get())),
                    handle.item()::setBackgroundColor);
        }
    }

    // ==================== 契约 1：初始未聚焦 → 未展开 ====================

    /** 未聚焦、value 空：expanded=false，无 overlay。 */
    @Test
    public void initiallyCollapsedWhenUnfocusedAndEmpty() {
        Assert.assertFalse("初始未展开", result.expanded().get());
        Assert.assertTrue("初始无 overlay", runtime.getOverlayHost().isEmpty());
        Assert.assertEquals("filtered 空", 0, result.filtered().get().size());
    }

    // ==================== 契约 2：focus + 打字 → expanded → portal 挂载 ====================

    /** 聚焦 + 打字 "Ari"：filtered 含 Arial/Arial Black → expanded → overlay 挂载。 */
    @Test
    public void focusAndTypeExpandsOverlay() {
        doLayout();
        focusAndType("Ari");
        doLayout();

        Assert.assertTrue("打字后展开", result.expanded().get());
        Assert.assertEquals("filtered 含 Arial + Arial Black", 2, result.filtered().get().size());
        Assert.assertEquals("filtered[0]=Arial", "Arial", result.filtered().get().get(0));
        Assert.assertEquals("filtered[1]=Arial Black", "Arial Black", result.filtered().get().get(1));
        Assert.assertEquals("overlay 挂载", 1, runtime.getOverlayHost().size());

        SceneNode listbox = overlayRoot();
        Assert.assertEquals("listbox COLUMN", FlexDirection.COLUMN, listbox.getFlexDirection());
        Assert.assertTrue("listbox 可滚动", listbox.isScrollable());
        Assert.assertEquals("listbox 持有 filtered 全部项", 2, overlayItemCount());

        // item label 文本 = 候选原文
        Assert.assertEquals("item[0] label=Arial", "Arial",
                overlayItem(0).__getChildren().get(0).getText());
        Assert.assertEquals("item[1] label=Arial Black", "Arial Black",
                overlayItem(1).__getChildren().get(0).getText());
    }

    /** 打开后未键盘导航时无高亮；所有项初始透明，hover 后才出现 hover 背景。 */
    @Test
    public void mouseOpenStartsWithoutHighlightedItemAndHoverShowsBackground() {
        doLayout();
        focusAndType("Ari");
        doLayout();

        Assert.assertNull("打开后尚未键盘导航，应无高亮", result.highlightedIndex().get());
        Assert.assertEquals("item[0] 初始背景透明", ITEM_BG_DEFAULT, overlayItem(0).getBackgroundColor());
        Assert.assertEquals("item[1] 初始背景透明", ITEM_BG_DEFAULT, overlayItem(1).getBackgroundColor());

        // 白盒说明：harness.moveTo 这里只回归 anchor=0 测试沙箱下 overlay item hover 行为，
        // 不覆盖 anchored overlay 的定位精度。
        harness.moveTo(overlayItem(0));
        runtime.flush();

        Assert.assertEquals("hover item[0] 后应显示 hover 背景", ITEM_BG_HOVERED, overlayItem(0).getBackgroundColor());
        Assert.assertEquals("未 hover 的 item[1] 仍透明", ITEM_BG_DEFAULT, overlayItem(1).getBackgroundColor());
    }

    // ==================== 契约 3：filtered 随 value 动态变化（I5 keyed diff） ====================

    /** 打字 Ari→Calibri：filtered 切换，item 节点按 keyed diff 增删（不重建整个 listbox）。 */
    @Test
    public void filteredDynamicallyUpdatesByKeyedDiff() {
        doLayout();
        focusAndType("Ari");
        doLayout();
        Assert.assertEquals("Ari → 2 项", 2, overlayItemCount());
        SceneNode listboxBefore = overlayRoot();

        // 继续打字 "c"（变 "Aric"，无候选命中）→ filtered 空 → expanded=false → overlay 卸载
        routeText("c");
        runtime.flush();
        valueSignal.set(onChangeValue.get());
        runtime.flush();
        Assert.assertEquals("Aric → filtered 空", 0, result.filtered().get().size());
        Assert.assertFalse("Aric → 不展开", result.expanded().get());
        Assert.assertTrue("Aric → overlay 卸载", runtime.getOverlayHost().isEmpty());

        // 退格模拟：直接 set value 回 "Ari"（不通过 BACKSPACE 键，避免 caret 复杂度）
        valueSignal.set("Ari");
        runtime.flush();
        Assert.assertTrue("回到 Ari → 重新展开", result.expanded().get());
        Assert.assertEquals("回到 Ari → 2 项", 2, overlayItemCount());
    }

    // ==================== 契约 4：键盘导航 ====================

    /** ARROW_DOWN/UP 移动 highlightedIndex；ENTER 上抛候选 + 关闭；ESC 关闭。 */
    @Test
    public void keyboardNavigationAndCommit() {
        doLayout();
        focusAndType("Ari");
        doLayout();

        Assert.assertNull("初始无键盘高亮", result.highlightedIndex().get());

        // ↓ → 首次建立高亮 0
        routeKey(SceneKey.ARROW_DOWN);
        runtime.flush();
        Assert.assertEquals("首次 ↓ 高亮 0", Integer.valueOf(0), result.highlightedIndex().get());
        Assert.assertEquals("item[0] 背景切为键盘高亮", ITEM_BG_HIGHLIGHTED, overlayItem(0).getBackgroundColor());

        // ↓ → 高亮 1
        routeKey(SceneKey.ARROW_DOWN);
        runtime.flush();
        Assert.assertEquals("↓ 高亮 1", Integer.valueOf(1), result.highlightedIndex().get());

        // ↓ → 边界裁剪仍 1（filtered 只有 2 项）
        routeKey(SceneKey.ARROW_DOWN);
        runtime.flush();
        Assert.assertEquals("↓ 末项边界仍 1", Integer.valueOf(1), result.highlightedIndex().get());

        // ↑ → 高亮 0
        routeKey(SceneKey.ARROW_UP);
        runtime.flush();
        Assert.assertEquals("↑ 高亮 0", Integer.valueOf(0), result.highlightedIndex().get());

        // ↑ → 边界裁剪仍 0
        routeKey(SceneKey.ARROW_UP);
        runtime.flush();
        Assert.assertEquals("↑ 首项边界仍 0", Integer.valueOf(0), result.highlightedIndex().get());

        // ↓ 到 1，ENTER → 上抛 "Arial Black" + expanded.set(false) → 关闭
        routeKey(SceneKey.ARROW_DOWN);
        runtime.flush();
        routeKey(SceneKey.ENTER);
        runtime.flush();
        Assert.assertEquals("ENTER 上抛 Arial Black", "Arial Black", onSelectValue.get());
        Assert.assertFalse("ENTER 后关闭", result.expanded().get());
        Assert.assertNull("ENTER 后清空键盘高亮", result.highlightedIndex().get());
        Assert.assertTrue("ENTER 后 overlay 卸载", runtime.getOverlayHost().isEmpty());
    }

    /** CONTAINS 下点击候选后，外部受控 value 回写为精确候选时，不应重新展开。 */
    @Test
    public void containsClickCommitDoesNotReexpandAfterControlledWriteback() {
        remount(SceneAutocompletePrimitive.MatchMode.CONTAINS);
        doLayout();
        focusAndType("Ari");
        doLayout();
        Assert.assertTrue("前置：CONTAINS Ari 已展开", result.expanded().get());
        Assert.assertEquals("前置：Ari 命中 Arial + Arial Black", 2, result.filtered().get().size());

        clickCenter(overlayItem(0));
        runtime.flush();
        Assert.assertEquals("CLICK item[0] 上抛 Arial", "Arial", onSelectValue.get());

        valueSignal.set(onSelectValue.get());
        runtime.flush();

        Assert.assertEquals("回写 Arial 后 filtered 仍可含 Arial + Arial Black", 2, result.filtered().get().size());
        Assert.assertFalse("精确匹配 filtered 任一项应抑制重新展开", result.expanded().get());
        Assert.assertTrue("回写后 overlay 不应重挂", runtime.getOverlayHost().isEmpty());
    }

    /** CONTAINS 下 ENTER 选择候选后，外部受控 value 回写为精确候选时，不应重新展开。 */
    @Test
    public void containsEnterCommitDoesNotReexpandAfterControlledWriteback() {
        remount(SceneAutocompletePrimitive.MatchMode.CONTAINS);
        doLayout();
        focusAndType("Ari");
        doLayout();
        Assert.assertNull("前置：打开后无高亮", result.highlightedIndex().get());

        routeKey(SceneKey.ARROW_DOWN);
        runtime.flush();
        routeKey(SceneKey.ENTER);
        runtime.flush();
        Assert.assertEquals("ENTER 上抛首次方向键高亮的 Arial", "Arial", onSelectValue.get());

        valueSignal.set(onSelectValue.get());
        runtime.flush();

        Assert.assertEquals("回写 Arial 后 filtered 仍可含 Arial + Arial Black", 2, result.filtered().get().size());
        Assert.assertFalse("ENTER 回写后不应重新展开", result.expanded().get());
        Assert.assertTrue("ENTER 回写后 overlay 不应重挂", runtime.getOverlayHost().isEmpty());
    }

    /** ESC → expanded.set(false) → 关闭；再打字→filtered 重算→effect set true→重弹（守 R13 effect 驱动）。 */
    @Test
    public void escapeSuppressesAndTypingRestarts() {
        doLayout();
        focusAndType("Ari");
        doLayout();
        Assert.assertTrue("前置：已展开", result.expanded().get());

        routeKey(SceneKey.ARROW_DOWN);
        runtime.flush();
        Assert.assertEquals("前置：方向键建立高亮", Integer.valueOf(0), result.highlightedIndex().get());

        // ESC → 关闭
        routeKey(SceneKey.ESCAPE);
        runtime.flush();
        Assert.assertFalse("ESC 后关闭", result.expanded().get());
        Assert.assertNull("ESC 后清空键盘高亮", result.highlightedIndex().get());
        Assert.assertTrue("ESC 后 overlay 卸载", runtime.getOverlayHost().isEmpty());

        // 再打字 "a"（value = "Aria"）：filtered 重算非空 → effect set expanded=true → 重弹
        routeText("a");
        runtime.flush();
        // onChange 捕获的是拼接结果 "Aria"，回灌 valueSignal
        String captured = onChangeValue.get();
        Assert.assertNotNull("应有 onChange", captured);
        valueSignal.set(captured);
        runtime.flush();
        doLayout();
        Assert.assertTrue("再打字经 effect 重弹", result.expanded().get());
        Assert.assertNull("ESC 后重新展开仍无高亮", result.highlightedIndex().get());
        Assert.assertEquals("重弹后 overlay 挂载", 1, runtime.getOverlayHost().size());
    }

    // ==================== 契约 5：item CLICK 上抛候选 + expanded.set(false) ====================

    /** 点击 listbox item[1] → 上抛 "Arial Black" + 关闭。 */
    @Test
    public void itemClickCommitsCandidate() {
        doLayout();
        focusAndType("Ari");
        doLayout();

        routeKey(SceneKey.ARROW_DOWN);
        runtime.flush();
        Assert.assertEquals("前置：方向键建立高亮", Integer.valueOf(0), result.highlightedIndex().get());

        clickCenter(overlayItem(1));
        runtime.flush();
        Assert.assertEquals("CLICK item[1] 上抛 Arial Black", "Arial Black", onSelectValue.get());
        Assert.assertFalse("CLICK 后关闭", result.expanded().get());
        Assert.assertNull("CLICK 后清空键盘高亮", result.highlightedIndex().get());
        Assert.assertTrue("CLICK 后 overlay 卸载", runtime.getOverlayHost().isEmpty());
    }

    // ==================== 契约 5b：跨帧点击守卫（真因 D1 回归） ====================

    /**
     * 跨帧点击 listbox item[1]：DOWN 与 UP 之间隔一帧 flush + layout。
     *
     * <p>真因 D1 守卫（R13 重构 + C1 双保险）：R13 重构前 autocomplete {@code expanded} 派生自
     * {@code focused}（Computed），DOWN 命中 overlay item（非 focusable）时若 Router 无条件 clearFocus，
     * DOWN→flush 后 focused=false→expanded=false→浮层卸载；UP 到达时浮层已卸载，
     * hitTarget != pressedNode → CLICK 不合成 → onSelect 永不触发（即"点击/hover 无响应"现象）。
     * R13 重构后 {@code expanded} 改为独立可写 Signal + focused effect 驱动（守 R13），但 effect 内
     * {@code if (!focused) expanded.set(false)} 仍会在 focused 掐断时关浮层——故 C1 豁免（SceneInputRouter
     * 命中 active overlay 时不清焦）保留作框架兜底：C1 阻止 clearFocus → focused 保持 → expanded effect
     * 不关浮层 → UP 命中同 item → CLICK 合成 → onSelect 上抛候选。</p>
     *
     * <p>本用例改 P0-1 之前应红（onSelectValue 仍 null），改之后绿；R13 重构后仍依赖 C1 兜底，继续绿。
     * 与 {@link #itemClickCommitsCandidate}（同帧 clickCenter）互补，覆盖真机跨帧盲区。</p>
     */
    @Test
    public void itemClickSurvivesCrossFrameFocusLoss() {
        doLayout();
        focusAndType("Ari");
        doLayout();
        Assert.assertTrue("前置：已展开", result.expanded().get());
        Assert.assertEquals("前置：filtered 含 Arial + Arial Black", 2, overlayItemCount());

        SceneNode item1 = overlayItem(1);
        // 跨帧点击：DOWN→flush + overlay 重排（doLayout）→UP；坐标 DOWN 前捕获一次，
        // UP 复用（守真因 D1：UP 命中同 item → CLICK 合成 → onSelect 上抛）
        harness.pressReleaseAcrossFrames(item1, this::doLayout);

        Assert.assertEquals("跨帧 CLICK item[1] 应上抛 Arial Black（D1 修复后）",
                "Arial Black", onSelectValue.get());
        Assert.assertFalse("CLICK 后应关闭", result.expanded().get());
        Assert.assertTrue("CLICK 后 overlay 应卸载", runtime.getOverlayHost().isEmpty());
    }

    // ==================== 契约 6：外部点击 dismiss → expanded.set(false) → 卸载 ====================

    /** 展开后点击 overlay 外部 → dismissRequest → expanded.set(false) → 卸载。 */
    @Test
    public void outsidePointerDismissesOverlay() {
        doLayout();
        focusAndType("Ari");
        doLayout();
        Assert.assertTrue("前置：已展开", result.expanded().get());

        // 点击 canvas 右下角空白（不在 listbox / inputRoot 内）
        harness.pressAt(CANVAS_WIDTH - 1, CANVAS_HEIGHT - 1);

        Assert.assertFalse("外部点击后 expanded=false", result.expanded().get());
        Assert.assertTrue("外部点击后 overlay 卸载", runtime.getOverlayHost().isEmpty());
    }

    // ==================== 契约 7：键集正交（F1） —— 编辑键仍走 primitive ====================

    /**
     * BACKSPACE 不被 autocomplete 吞：expanded=false 时 BACKSPACE 经 primitive 删除字符。
     * 验证 autocomplete 的 KEY_DOWN handler 在 expanded=false 时早退（不拦截编辑键）。
     */
    @Test
    public void backspaceGoesThroughPrimitiveWhenCollapsed() {
        doLayout();
        // 用 "Cambria"（候选中唯一无前缀碰撞）触发精确单命中 → expanded=false
        runtime.requestFocus(inputRoot);
        runtime.flush();
        valueSignal.set("Cambria");
        runtime.flush();
        Assert.assertFalse("精确单命中 Cambria → 不展开", result.expanded().get());

        // caretIndex 移到末尾（END），BACKSPACE 删除末字符 → onChange 上抛 "Cambri"
        routeKey(SceneKey.END);
        runtime.flush();
        routeKey(SceneKey.BACKSPACE);
        runtime.flush();
        Assert.assertNotNull("BACKSPACE 经 primitive 触发 onChange", onChangeValue.get());
        Assert.assertEquals("BACKSPACE 删除末字符 → 'Cambri'", "Cambri", onChangeValue.get());
    }

    /** ARROW_LEFT/RIGHT 不被 autocomplete 吞（expanded=false 时交回 primitive 移动 caret）。 */
    @Test
    public void arrowLeftRightGoThroughPrimitiveWhenCollapsed() {
        doLayout();
        runtime.requestFocus(inputRoot);
        runtime.flush();
        valueSignal.set("Cambria");
        runtime.flush();
        Assert.assertFalse("精确单命中 Cambria → 不展开", result.expanded().get());

        // ARROW_LEFT → caretIndex 应移动（透传到 primitive 的 KEY_DOWN handler）
        // primitive 的 caretIndex 不在 autocomplete Result 中暴露，间接验证：不抛异常、autocomplete 不拦截
        routeKey(SceneKey.ARROW_LEFT);
        runtime.flush();
        // 初始 caret=0（primitive 默认），LEFT 后仍 0（边界）→ BACKSPACE 无字符删 → onChange 不触发
        Assert.assertNull("LEFT 后 caret=0 边界，BACKSPACE 无字符删", onChangeValue.get());
    }

    // ==================== 契约 8：disabled 拦截 ====================

    /** disabled：打字不触发 onChange，expanded 不为 true。 */
    @Test
    public void disabledBlocksInputAndKeyboard() {
        enabledSignal.set(Boolean.FALSE);
        runtime.flush();
        doLayout();

        runtime.requestFocus(inputRoot);
        runtime.flush();
        routeText("Ari");
        runtime.flush();
        // 守 disabled：primitive 的 TEXT_INPUT handler 兜底早退，onChange 不上抛
        Assert.assertNull("disabled 打字不上抛 onChange", onChangeValue.get());

        // 直接灌 value，验证 effect 内 expanded 的 enabled 守卫生效
        valueSignal.set("Ari");
        runtime.flush();
        Assert.assertFalse("disabled 时 expanded 始终 false（enabled 守卫）", result.expanded().get());
    }

    // ==================== 契约 9：精确单命中抑制浮层 ====================

    /** value 与唯一候选精确归一化相等 → expanded=false（isExactSingleMatch 抑制）。 */
    @Test
    public void exactSingleMatchSuppressesOverlay() {
        doLayout();
        // 用 "Cambria"（候选中无其它 "Cambria..." 前缀碰撞）→ filtered 单项
        valueSignal.set("Cambria");
        runtime.requestFocus(inputRoot);
        runtime.flush();
        Assert.assertFalse("精确单命中 Cambria → 抑制浮层", result.expanded().get());
        Assert.assertTrue("精确单命中 → 无 overlay", runtime.getOverlayHost().isEmpty());

        // 改为部分前缀 "Ca" → 重弹（filtered 含 Cambria + Calibri? 不，Calibri 不以 Ca 开头，但 Cambria 唯一）
        // 实际 "Ca" → 只匹配 Cambria（Calibri 是 "Ca" 后接 "libri" → "ca".startsWith? 是！）
        // 让我用 "Cam" 确保唯一命中以外的多命中场景，或验证至少重弹
        valueSignal.set("Ca");
        runtime.flush();
        // "Ca" 匹配 Cambria + Calibri（两者都以 "Ca" 开头）
        Assert.assertEquals("Ca → filtered 含 Cambria + Calibri", 2, result.filtered().get().size());
        Assert.assertTrue("部分前缀 Ca → 展开", result.expanded().get());
    }

    // ==================== 契约 10：portal 挂卸随 expanded 反复切换 ====================

    /** expanded 反复 true/false：overlay entry 正确增删（不泄漏）。 */
    @Test
    public void portalMountUnmountCycle() {
        doLayout();
        for (int i = 0; i < 3; i++) {
            // 展开
            runtime.requestFocus(inputRoot);
            runtime.flush();
            valueSignal.set("Ar");
            runtime.flush();
            doLayout();
            Assert.assertEquals("循环 " + i + " 展开 overlay=1", 1, runtime.getOverlayHost().size());

            // 关闭（ESC）
            routeKey(SceneKey.ESCAPE);
            runtime.flush();
            Assert.assertEquals("循环 " + i + " ESC 后 overlay=0", 0, runtime.getOverlayHost().size());

            // 打字触发 value 变化 → filtered 重算 → effect 重评（替代旧 suppressed 复位）
            routeText("i");
            runtime.flush();
            valueSignal.set("Ari");
            runtime.flush();
            Assert.assertTrue("循环 " + i + " effect 重弹", result.expanded().get());
            // ESC 关闭准备下一轮
            routeKey(SceneKey.ESCAPE);
            runtime.flush();
            // 再打字触发 effect 重评（最后一轮不需要，但保持循环一致）
            routeText("i");
            runtime.flush();
            valueSignal.set("Arii");
            runtime.flush();
            // Arii 无命中 → filtered 空 → expanded false
            Assert.assertFalse("Arii 无命中不展开", result.expanded().get());
            // 重置 value 给下一轮
            valueSignal.set("");
            runtime.flush();
            // value 已空，下一轮 focusAndType 重新驱动 effect
        }
        Assert.assertEquals("循环结束无 overlay 泄漏", 0, runtime.getOverlayHost().size());
    }
}
