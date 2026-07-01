package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
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
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

/**
 * SceneSingleSelectPrimitive 独立单元测试 —— 验证无样式 N 选 1 受控行为核心契约。
 *
 * <p>primitive 不挂任何 chrome，只验证行为与结构：
 * 选项构建期不可变、方向键导航 + 边界裁剪、HOME/END 跳首尾、
 * requestFocus 的 roving tabindex 语义、selected signal 派生、无 chrome 路径可工作。</p>
 */
public class SceneSingleSelectPrimitiveTest {

    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    /** 语义化交互注入 harness（route 根 + click/pressKey 入口）；其 runtime 即上方 runtime 字段 */
    private SceneInteractionHarness harness;

    private Signal<Integer> selectedSignal;
    private Signal<Boolean> enabledSignal;
    private AtomicInteger selectCount;
    private Integer lastSelectValue;

    private MountHandle handle;
    private SceneSingleSelectPrimitive.Result result;
    private SceneNode primitiveRoot;

    private static final int CANVAS_WIDTH = 200;
    private static final int CANVAS_HEIGHT = 200;
    private static final int STUB_CHAR_WIDTH = 8;

    private static final List<String> OPTIONS = Arrays.asList("Low", "Mid", "High");

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        harness = SceneInteractionHarness.create();
        runtime = harness.getRuntime();
        FixedTextMeasurer measurer = new FixedTextMeasurer(STUB_CHAR_WIDTH, 16);
        layoutEngine = new SceneLayoutEngine(measurer);
        sceneRoot = new SceneNode();

        selectedSignal = Signal.create(Integer.valueOf(0));
        enabledSignal = Signal.create(Boolean.TRUE);
        selectCount = new AtomicInteger(0);
        lastSelectValue = null;

        mountPrimitive(SceneSingleSelectPrimitive.Orientation.VERTICAL, OPTIONS);
        // 挂载路由根并对齐 layout，供 harness.click/pressKey 取中心 + route
        harness.mountRoot(sceneRoot, CANVAS_WIDTH, CANVAS_HEIGHT);
    }

    /** 挂载 primitive（在 mount builder 作用域内调用 create，让 effect 归属 owner） */
    private void mountPrimitive(SceneSingleSelectPrimitive.Orientation orientation, List<String> options) {
        SceneSingleSelectPrimitive.Props props = new SceneSingleSelectPrimitive.Props(
                selectedSignal, options, enabledSignal,
                next -> {
                    selectCount.incrementAndGet();
                    lastSelectValue = next;
                },
                orientation);
        final SceneSingleSelectPrimitive.Result[] holder = new SceneSingleSelectPrimitive.Result[1];
        handle = runtime.mount(sceneRoot, () -> {
            holder[0] = SceneSingleSelectPrimitive.create(runtime, props);
            return holder[0].root();
        });
        result = holder[0];
        primitiveRoot = result.root();
        runtime.flush();
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

    /** item[i] 节点（root 第 i 个孩子） */
    private SceneNode itemNode(int i) {
        return primitiveRoot.__getChildren().get(i);
    }

    /** item[i] 的 label 节点（经 ItemHandle 暴露，primitive 不把 label 挂到 item 子节点） */
    private SceneNode labelNode(int i) {
        return result.items().get(i).label();
    }

    private LayoutBox box(SceneNode n) {
        return (LayoutBox) n.getCachedLayout();
    }

    // ==================== 契约 1：选项构建期固定不可变 ====================

    /** Props 构造时防御性复制为不可变 list，外部修改原 list 不影响 props.options()。 */
    @Test
    public void propsOptionsShouldBeDefensiveCopyAndUnmodifiable() {
        List<String> mutable = new ArrayList<>(Arrays.asList("A", "B", "C"));
        SceneSingleSelectPrimitive.Props props = new SceneSingleSelectPrimitive.Props(
                selectedSignal, mutable, enabledSignal, next -> {}, SceneSingleSelectPrimitive.Orientation.VERTICAL);

        // 修改原 list 不影响 props.options()
        mutable.add("D");
        mutable.set(0, "X");
        Assert.assertEquals("props.options 不受原 list 修改影响", 3, props.options().size());
        Assert.assertEquals("props.options[0] 保持原值", "A", props.options().get(0));

        // props.options() 自身不可变
        try {
            props.options().add("Z");
            Assert.fail("props.options 应不可变，add 应抛 UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // 期望
        }
    }

    /** Result.items 也是不可变 list。 */
    @Test
    public void resultItemsShouldBeUnmodifiable() {
        Assert.assertEquals(OPTIONS.size(), result.items().size());
        try {
            result.items().add(null);
            Assert.fail("result.items 应不可变，add 应抛 UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // 期望
        }
    }

    // ==================== 契约 2：方向键导航 + 边界裁剪（VERTICAL） ====================

    /** VERTICAL：↓ 上抛相邻下标 + 焦点移动；↑ 反向；边界裁剪到 [0,size-1]。 */
    @Test
    public void verticalArrowNavigationRaisesAdjacentIndexAndMovesFocus() {
        doLayout();
        runtime.requestFocus(itemNode(0));
        Assert.assertSame(itemNode(0), runtime.getFocusedNode());

        // selectedSignal=0，↓ → next=1
        harness.pressKey(SceneKey.ARROW_DOWN);
        Assert.assertEquals("↓ 上抛相邻下标 1", Integer.valueOf(1), lastSelectValue);
        Assert.assertSame("↓ 焦点移到 item[1]", itemNode(1), runtime.getFocusedNode());

        // 外部回写 selectedIndex=1（受控闭环），再 ↓ → next=2
        selectedSignal.set(Integer.valueOf(1));
        runtime.flush();
        harness.pressKey(SceneKey.ARROW_DOWN);
        Assert.assertEquals("↓ 从 1 上抛 2", Integer.valueOf(2), lastSelectValue);
        Assert.assertSame(itemNode(2), runtime.getFocusedNode());

        // 边界裁剪：cur=2 再 ↓ → 仍 2
        selectedSignal.set(Integer.valueOf(2));
        runtime.flush();
        harness.pressKey(SceneKey.ARROW_DOWN);
        Assert.assertEquals("↓ 末项边界裁剪仍 2", Integer.valueOf(2), lastSelectValue);

        // ↑：cur=2 → next=1
        harness.pressKey(SceneKey.ARROW_UP);
        Assert.assertEquals("↑ 从 2 上抛 1", Integer.valueOf(1), lastSelectValue);
        Assert.assertSame(itemNode(1), runtime.getFocusedNode());

        // ↑ 边界裁剪：cur=0 再 ↑ → 仍 0
        selectedSignal.set(Integer.valueOf(0));
        runtime.flush();
        harness.pressKey(SceneKey.ARROW_UP);
        Assert.assertEquals("↑ 首项边界裁剪仍 0", Integer.valueOf(0), lastSelectValue);
    }

    // ==================== 契约 3：方向键导航（HORIZONTAL） ====================

    /** HORIZONTAL：←→ 工作，↑↓ 不触发上抛。 */
    @Test
    public void horizontalArrowNavigationUsesLeftRightOnly() {
        // 用独立 primitive 实例（HORIZONTAL），挂到同一 sceneRoot 会冲突，用独立 runtime
        ReactiveScheduler.get().reset();
        SceneRuntime rt2 = new SceneRuntime();
        SceneLayoutEngine le2 = new SceneLayoutEngine(new FixedTextMeasurer(STUB_CHAR_WIDTH, 16));
        SceneNode root2 = new SceneNode();
        Signal<Integer> sel2 = Signal.create(Integer.valueOf(0));
        Signal<Boolean> en2 = Signal.create(Boolean.TRUE);
        AtomicInteger cnt2 = new AtomicInteger(0);
        Integer[] last2 = new Integer[1];
        SceneSingleSelectPrimitive.Props props = new SceneSingleSelectPrimitive.Props(
                sel2, OPTIONS, en2, next -> { cnt2.incrementAndGet(); last2[0] = next; },
                SceneSingleSelectPrimitive.Orientation.HORIZONTAL);
        final SceneSingleSelectPrimitive.Result[] holder = new SceneSingleSelectPrimitive.Result[1];
        MountHandle h = rt2.mount(root2, () -> {
            holder[0] = SceneSingleSelectPrimitive.create(rt2, props);
            return holder[0].root();
        });
        SceneSingleSelectPrimitive.Result r = holder[0];
        rt2.flush();
        le2.layout(root2, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));

        SceneNode item0 = r.root().__getChildren().get(0);
        rt2.requestFocus(item0);

        // →：cur=0 → next=1
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofKey(SceneKey.ARROW_RIGHT, SceneKeyAction.PRESSED,
                false, false, false, false, RawInputEvent.NATIVE_NONE, RawInputEvent.NATIVE_NONE, 1000L));
        rt2.route(root2, fb.drainFrame(), 0, 0);
        rt2.flush();
        Assert.assertEquals("→ 上抛 1", Integer.valueOf(1), last2[0]);
        Assert.assertSame(r.root().__getChildren().get(1), rt2.getFocusedNode());

        // ←：cur=1（回写）→ next=0
        sel2.set(Integer.valueOf(1));
        rt2.flush();
        fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofKey(SceneKey.ARROW_LEFT, SceneKeyAction.PRESSED,
                false, false, false, false, RawInputEvent.NATIVE_NONE, RawInputEvent.NATIVE_NONE, 1000L));
        rt2.route(root2, fb.drainFrame(), 0, 0);
        rt2.flush();
        Assert.assertEquals("← 上抛 0", Integer.valueOf(0), last2[0]);

        // ↑：HORIZONTAL 不处理，不上抛
        int before = cnt2.get();
        fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofKey(SceneKey.ARROW_UP, SceneKeyAction.PRESSED,
                false, false, false, false, RawInputEvent.NATIVE_NONE, RawInputEvent.NATIVE_NONE, 1000L));
        rt2.route(root2, fb.drainFrame(), 0, 0);
        rt2.flush();
        Assert.assertEquals("HORIZONTAL 下 ↑ 不触发上抛", before, cnt2.get());

        h.dispose();
        rt2.dispose();
    }

    // ==================== 契约 4：HOME/END 跳首尾 ====================

    /** HOME 跳首项，END 跳末项，均上抛期望下标 + 焦点移动。 */
    @Test
    public void homeAndEndJumpToFirstAndLast() {
        doLayout();
        runtime.requestFocus(itemNode(1));
        selectedSignal.set(Integer.valueOf(1));
        runtime.flush();

        // END → 末项 size-1=2
        harness.pressKey(SceneKey.END);
        Assert.assertEquals("END 上抛末项 2", Integer.valueOf(2), lastSelectValue);
        Assert.assertSame("END 焦点移到 item[2]", itemNode(2), runtime.getFocusedNode());

        // HOME → 首项 0
        harness.pressKey(SceneKey.HOME);
        Assert.assertEquals("HOME 上抛首项 0", Integer.valueOf(0), lastSelectValue);
        Assert.assertSame("HOME 焦点移到 item[0]", itemNode(0), runtime.getFocusedNode());
    }

    // ==================== 契约 5：requestFocus 的 roving tabindex 语义 ====================

    /** 每个 item 都是独立 focusable 单元，requestFocus 可聚焦任意 item（roving tabindex）。 */
    @Test
    public void eachItemIsIndependentlyFocusable() {
        doLayout();
        Assert.assertNull("初始无焦点", runtime.getFocusedNode());

        // 聚焦 item[0]
        Assert.assertTrue("requestFocus(item[0]) 应成功", runtime.requestFocus(itemNode(0)));
        Assert.assertSame(itemNode(0), runtime.getFocusedNode());

        // 聚焦 item[1]
        Assert.assertTrue("requestFocus(item[1]) 应成功", runtime.requestFocus(itemNode(1)));
        Assert.assertSame(itemNode(1), runtime.getFocusedNode());

        // 聚焦 item[2]
        Assert.assertTrue("requestFocus(item[2]) 应成功", runtime.requestFocus(itemNode(2)));
        Assert.assertSame(itemNode(2), runtime.getFocusedNode());
    }

    // ==================== 契约 6：selected signal 派生 ====================

    /** ItemHandle.selected 是 Computed，随 selectedIndex 变化派生当前 item 是否选中。 */
    @Test
    public void selectedSignalDerivesFromSelectedIndex() {
        // 初始 selectedIndex=0 → item[0] 选中，item[1]/item[2] 未选中
        runtime.flush();
        Assert.assertTrue("初始 item[0] selected", result.items().get(0).selected().get());
        Assert.assertFalse("初始 item[1] 未 selected", result.items().get(1).selected().get());
        Assert.assertFalse("初始 item[2] 未 selected", result.items().get(2).selected().get());

        // 切到 1
        selectedSignal.set(Integer.valueOf(1));
        runtime.flush();
        Assert.assertFalse("切 1 后 item[0] 退选", result.items().get(0).selected().get());
        Assert.assertTrue("切 1 后 item[1] selected", result.items().get(1).selected().get());
        Assert.assertFalse("切 1 后 item[2] 未 selected", result.items().get(2).selected().get());

        // 切到 2
        selectedSignal.set(Integer.valueOf(2));
        runtime.flush();
        Assert.assertTrue("切 2 后 item[2] selected", result.items().get(2).selected().get());
        Assert.assertFalse("切 2 后 item[1] 退选", result.items().get(1).selected().get());

        // 越界下标归一化：selectedIndex=99 → 归一化到 size-1=2
        selectedSignal.set(Integer.valueOf(99));
        runtime.flush();
        Assert.assertTrue("越界 99 归一化到末项 2 selected", result.items().get(2).selected().get());

        // 负下标归一化到 0
        selectedSignal.set(Integer.valueOf(-5));
        runtime.flush();
        Assert.assertTrue("负下标 -5 归一化到首项 0 selected", result.items().get(0).selected().get());
    }

    // ==================== 契约 7：NOOP_CHROME 路径（无样式仍可工作） ====================

    /** primitive 不挂任何 chrome：节点无背景色/圆角/padding（默认 0），但点击/键盘仍正常触发 onSelect。
     *  <p>注：primitive 不把 label 挂到 item 子节点（由 wrapper 决定），item 无内容故无尺寸；
     *  本测试给 item 设置 preferredWidth/Height 提供命中区（布局尺寸非 chrome），验证点击行为契约。</p> */
    @Test
    public void noChromePathWorksWithoutAnyVisualStyle() {
        // 给 item 提供布局尺寸（非视觉 chrome），让点击可命中
        for (int i = 0; i < OPTIONS.size(); i++) {
            itemNode(i).setPreferredWidth(40);
            itemNode(i).setPreferredHeight(16);
        }
        doLayout();
        // 结构节点无样式：backgroundColor/cornerRadius/padding 均为默认 0
        Assert.assertEquals("root 无背景色", 0, primitiveRoot.getBackgroundColor());
        Assert.assertEquals("root 无圆角", 0, primitiveRoot.getCornerRadius());
        for (int i = 0; i < OPTIONS.size(); i++) {
            Assert.assertEquals("item[" + i + "] 无背景色", 0, itemNode(i).getBackgroundColor());
            Assert.assertEquals("item[" + i + "] 无圆角", 0, itemNode(i).getCornerRadius());
            Assert.assertEquals("item[" + i + "] 无 padding", 0, itemNode(i).getPaddingLeft());
        }

        // label 文本经 bindText 绑定到 options[i]（label 经 ItemHandle 暴露，不在 item 子节点列表）
        Assert.assertEquals("label[0] 文本", "Low", labelNode(0).getText());
        Assert.assertEquals("label[1] 文本", "Mid", labelNode(1).getText());
        Assert.assertEquals("label[2] 文本", "High", labelNode(2).getText());

        // 点击 item[2] 仍触发 onSelect（无 chrome 不影响行为）
        harness.click(itemNode(2));
        Assert.assertEquals("无 chrome 路径点击仍触发 onSelect", 1, selectCount.get());
        Assert.assertEquals("点击 item[2] 上抛 2", Integer.valueOf(2), lastSelectValue);

        // 键盘 Enter 仍触发
        runtime.requestFocus(itemNode(1));
        harness.pressKey(SceneKey.ENTER);
        Assert.assertEquals("无 chrome 路径 Enter 仍触发 onSelect", 2, selectCount.get());
        Assert.assertEquals("Enter 上抛 1", Integer.valueOf(1), lastSelectValue);
    }

    // ==================== 契约 8：root 方向随 orientation ====================

    /** VERTICAL → root 为 COLUMN；HORIZONTAL → root 为 ROW。 */
    @Test
    public void rootFlexDirectionFollowsOrientation() {
        Assert.assertEquals("VERTICAL → root COLUMN",
                FlexDirection.COLUMN, primitiveRoot.getFlexDirection());

        // HORIZONTAL 单独验证（mountPrimitive 已在 setUp 用 VERTICAL）
        ReactiveScheduler.get().reset();
        SceneRuntime rt2 = new SceneRuntime();
        SceneNode root2 = new SceneNode();
        Signal<Integer> sel2 = Signal.create(Integer.valueOf(0));
        Signal<Boolean> en2 = Signal.create(Boolean.TRUE);
        SceneSingleSelectPrimitive.Props props = new SceneSingleSelectPrimitive.Props(
                sel2, OPTIONS, en2, next -> {}, SceneSingleSelectPrimitive.Orientation.HORIZONTAL);
        final SceneSingleSelectPrimitive.Result[] holder = new SceneSingleSelectPrimitive.Result[1];
        MountHandle h = rt2.mount(root2, () -> {
            holder[0] = SceneSingleSelectPrimitive.create(rt2, props);
            return holder[0].root();
        });
        rt2.flush();
        Assert.assertEquals("HORIZONTAL → root ROW",
                FlexDirection.ROW, holder[0].root().getFlexDirection());
        h.dispose();
        rt2.dispose();
    }

    // ==================== 契约 9：disabled 拦截点击与键盘 ====================

    /** disabled 态：点击与 Enter/方向键均不触发 onSelect。 */
    @Test
    public void disabledBlocksClickAndKeyboard() {
        // 给 item 提供布局尺寸（primitive item 无内容故无尺寸），让 harness.click 可命中
        for (int i = 0; i < OPTIONS.size(); i++) {
            itemNode(i).setPreferredWidth(40);
            itemNode(i).setPreferredHeight(16);
        }
        doLayout();
        enabledSignal.set(Boolean.FALSE);
        runtime.flush();
        doLayout();

        int before = selectCount.get();
        harness.click(itemNode(1));
        Assert.assertEquals("disabled 点击不触发", before, selectCount.get());

        runtime.requestFocus(itemNode(1));
        harness.pressKey(SceneKey.ENTER);
        Assert.assertEquals("disabled Enter 不触发", before, selectCount.get());

        harness.pressKey(SceneKey.ARROW_DOWN);
        Assert.assertEquals("disabled 方向键不触发", before, selectCount.get());
    }
}
