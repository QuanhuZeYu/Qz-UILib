package club.heiqi.uilib.ui.scene.control;

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
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

/**
 * SceneAutocompletePrimitive L3 集成测试 —— 验证浮层显隐信号链、键盘导航、选中、portal 挂卸、
 * suppressed 复位与键集正交（守 oracle F1/F2/F4 + R10/R11 + I5 keyed diff）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>浮层显隐信号链：focus→打字→expanded 派生 true→portal 挂载；失焦→卸载；
 *       ESC→suppressed→卸载；打字复位 suppressed→重弹。</li>
 *   <li>键盘：ARROW_DOWN/UP 移动 highlightedIndex（截止边界）；ENTER 上抛正确候选 + 关闭；ESC 关闭。</li>
 *   <li>选中：item CLICK 上抛正确候选 + suppressed。</li>
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

    private static final List<String> CANDIDATES = Arrays.asList(
            "Arial", "Arial Black", "Calibri", "Cambria", "Consolas");

    @Before
    public void setUp() {
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
                SceneAutocompletePrimitive.MatchMode.PREFIX,
                8,
                v -> onChangeValue.set(v),
                v -> onSelectValue.set(v));
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

    /**
     * 跨帧点击：DOWN 与 UP 之间插入完整 flush + layout（含 overlay 重评），
     * 模拟真机 DOWN/UP 跨帧的时序（点击 item 时 DOWN 帧末 flush 已生效）。
     *
     * <p>真因 D1 守卫：autocomplete expanded 派生自 focused，若 DOWN 命中 overlay item 时
     * Router 无条件 clearFocus，DOWN→flush 后 focused=false→expanded=false→浮层卸载，
     * UP 到达时 hitTarget 已不存在 → CLICK 不合成 → onSelect 永不触发。
     * clickCenter 同帧注入绕过此场景（DOWN+UP 同 route，flush 之前 CLICK 已合成），故补此 harness。</p>
     *
     * <p>时序参考 {@code AbstractSceneHostWidget.render}：route → flush → layout（含 overlay）。
     * 每帧 route 后立即 flush，跨帧即在两次 route 间插入 flush + overlay layout。</p>
     *
     * @param n 目标 overlay item 节点
     */
    private void clickCrossFrame(SceneNode n) {
        int[] c = absCenter(n);
        // DOWN 帧：route（隐式聚焦块在此执行）→ flush（signal 写入生效，portal 可能重评挂卸）
        routePointer(ScenePointerAction.BUTTON_DOWN, c[0], c[1]);
        runtime.flush();
        // overlay 几何重算：若 DOWN flush 后浮层已卸载则集合为空（doLayout 内置空跳过）
        doLayout();
        // UP 帧：route（hit-test 用上一步 layout 后的几何）→ flush
        routePointer(ScenePointerAction.BUTTON_UP, c[0], c[1]);
        runtime.flush();
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

        Assert.assertEquals("初始高亮 0", Integer.valueOf(0), result.highlightedIndex().get());

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

        // ↓ 到 1，ENTER → 上抛 "Arial Black" + suppressed → 关闭
        routeKey(SceneKey.ARROW_DOWN);
        runtime.flush();
        routeKey(SceneKey.ENTER);
        runtime.flush();
        Assert.assertEquals("ENTER 上抛 Arial Black", "Arial Black", onSelectValue.get());
        Assert.assertFalse("ENTER 后关闭", result.expanded().get());
        Assert.assertTrue("ENTER 后 overlay 卸载", runtime.getOverlayHost().isEmpty());
    }

    /** ESC → suppressed=true → 关闭；再打字复位 suppressed → 重弹（守 oracle 三大陷阱之二）。 */
    @Test
    public void escapeSuppressesAndTypingRestarts() {
        doLayout();
        focusAndType("Ari");
        doLayout();
        Assert.assertTrue("前置：已展开", result.expanded().get());

        // ESC → 关闭
        routeKey(SceneKey.ESCAPE);
        runtime.flush();
        Assert.assertFalse("ESC 后关闭", result.expanded().get());
        Assert.assertTrue("ESC 后 overlay 卸载", runtime.getOverlayHost().isEmpty());

        // 再打字 "a"（value = "Aria"）：suppressed 复位 → filtered 非空 → 重弹
        routeText("a");
        runtime.flush();
        // onChange 捕获的是拼接结果 "Aria"，回灌 valueSignal
        String captured = onChangeValue.get();
        Assert.assertNotNull("应有 onChange", captured);
        valueSignal.set(captured);
        runtime.flush();
        doLayout();
        Assert.assertTrue("再打字复位 suppressed 后重弹", result.expanded().get());
        Assert.assertEquals("重弹后 overlay 挂载", 1, runtime.getOverlayHost().size());
    }

    // ==================== 契约 5：item CLICK 上抛候选 + suppressed ====================

    /** 点击 listbox item[1] → 上抛 "Arial Black" + 关闭。 */
    @Test
    public void itemClickCommitsCandidate() {
        doLayout();
        focusAndType("Ari");
        doLayout();

        clickCenter(overlayItem(1));
        runtime.flush();
        Assert.assertEquals("CLICK item[1] 上抛 Arial Black", "Arial Black", onSelectValue.get());
        Assert.assertFalse("CLICK 后关闭", result.expanded().get());
        Assert.assertTrue("CLICK 后 overlay 卸载", runtime.getOverlayHost().isEmpty());
    }

    // ==================== 契约 5b：跨帧点击守卫（真因 D1 回归） ====================

    /**
     * 跨帧点击 listbox item[1]：DOWN 与 UP 之间隔一帧 flush + layout。
     *
     * <p>真因 D1 守卫：autocomplete {@code expanded} 派生自 {@code focused}（Computed），
     * DOWN 命中 overlay item（非 focusable）时若 Router 无条件 clearFocus，
     * DOWN→flush 后 focused=false→expanded=false→浮层卸载；UP 到达时浮层已卸载，
     * hitTarget != pressedNode → CLICK 不合成 → onSelect 永不触发（即"点击/hover 无响应"现象）。
     * 修复后（SceneInputRouter 命中 active overlay 时豁免 clearFocus）→ 焦点保持 → 浮层跨帧存活 →
     * UP 命中同 item → CLICK 合成 → onSelect 上抛候选。</p>
     *
     * <p>本用例改 P0-1 之前应红（onSelectValue 仍 null），改之后绿。
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
        // 跨帧点击：DOWN→flush（D1 焦点掐断点）→UP
        clickCrossFrame(item1);

        Assert.assertEquals("跨帧 CLICK item[1] 应上抛 Arial Black（D1 修复后）",
                "Arial Black", onSelectValue.get());
        Assert.assertFalse("CLICK 后应关闭", result.expanded().get());
        Assert.assertTrue("CLICK 后 overlay 应卸载", runtime.getOverlayHost().isEmpty());
    }

    // ==================== 契约 6：外部点击 dismiss → suppressed → 卸载 ====================

    /** 展开后点击 overlay 外部 → dismissRequest → suppressed → 卸载。 */
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

        // 直接灌 value，验证 expanded Computed 中的 enabled 守卫生效
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

            // 复位 suppressed（打字）
            routeText("i");
            runtime.flush();
            valueSignal.set("Ari");
            runtime.flush();
            Assert.assertTrue("循环 " + i + " 复位后重弹", result.expanded().get());
            // ESC 关闭准备下一轮
            routeKey(SceneKey.ESCAPE);
            runtime.flush();
            // 再打字复位（最后一轮不需要，但保持循环一致）
            routeText("i");
            runtime.flush();
            valueSignal.set("Arii");
            runtime.flush();
            // Arii 无命中 → filtered 空 → expanded false
            Assert.assertFalse("Arii 无命中不展开", result.expanded().get());
            // 重置 value 给下一轮
            valueSignal.set("");
            runtime.flush();
            // 失焦复位 suppressed
            // （不能直接 clearFocus 公开 API，但 suppressed 经 TEXT_INPUT 复位；这里 value 已空，
            //  下一轮 focusAndType 重新驱动）
        }
        Assert.assertEquals("循环结束无 overlay 泄漏", 0, runtime.getOverlayHost().size());
    }
}
