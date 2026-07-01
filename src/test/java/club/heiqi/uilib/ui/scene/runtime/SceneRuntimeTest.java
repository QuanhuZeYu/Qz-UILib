package club.heiqi.uilib.ui.scene.runtime;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.InputBinding;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * SceneRuntime + bind 骨架单元测试 —— Phase 0 T3 验收点。
 *
 * <p>验证：bind 对接属性槽自动打级（I4）、mount builder 只执行一次（I3）、
 * dispose 后退订 effect。全程不碰 layout/paint/旧栈。</p>
 */
public class SceneRuntimeTest {

    private SceneRuntime runtime;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        runtime = new SceneRuntime();
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== 测试 1：bind 属性槽自动打级（I4） ====================

    /**
     * 验证：bind Signal&lt;Integer&gt; 到 SceneNode 的 backgroundColor 后，
     * signal.set(新值) → flush → 节点值已更新、且打出正确的 paint 级标记。
     */
    @Test
    public void shouldBindSignalToBackgroundColorAndMarkPaintDirty() {
        SceneNode node = new SceneNode();
        Signal<Integer> colorSignal = Signal.create(0);

        // bind: impact 声明 PAINT，applier 写 setBackgroundColor（内部自动 markSelfPaint）
        Binding binding = runtime.bind(colorSignal, node::setBackgroundColor);

        // 首次 flush：effect 首跑，应把 signal 当前值(0)应用到节点
        runtime.flush();
        Assert.assertEquals("初始值应已应用", 0, node.getBackgroundColor());

        // 清除脏标记、再变更 signal
        node.clearDirtyFlags();
        colorSignal.set(0xFFFF0000); // 红色
        runtime.flush();

        // === I4 断言：effect 重跑后节点值已更新 ===
        Assert.assertEquals("节点 backgroundColor 应为新值", 0xFFFF0000, node.getBackgroundColor());

        // === I4 断言：仅打出 PAINT 级失效 ===
        Assert.assertTrue("节点应标 selfPaintDirty", node.__isSelfPaintDirty());
        Assert.assertFalse("不应标 selfLayoutDirty", node.__isSelfLayoutDirty());
        Assert.assertFalse("不应标 compositeDirty", node.__isCompositeDirty());

        binding.dispose();
    }

    /**
     * 验证：bind Signal&lt;String&gt; 到 text 属性，打出 LAYOUT + PAINT 级标记。
     * setText 同时影响布局尺寸和绘制输出，两个标记均需打出。
     */
    @Test
    public void shouldBindSignalToTextAndMarkLayoutDirty() {
        SceneNode node = new SceneNode();
        Signal<String> textSignal = Signal.create("initial");

        runtime.bind(textSignal, node::setText);
        runtime.flush();

        node.clearDirtyFlags();
        textSignal.set("changed");
        runtime.flush();

        Assert.assertEquals("节点 text 应为新值", "changed", node.getText());
        Assert.assertTrue("应标 selfLayoutDirty", node.__isSelfLayoutDirty());
        Assert.assertTrue("应标 selfPaintDirty", node.__isSelfPaintDirty());
    }

    /**
     * 验证：bind Signal&lt;Float&gt; 到 opacity 属性，打出 COMPOSITE 级标记。
     */
    @Test
    public void shouldBindSignalToOpacityAndMarkCompositeDirty() {
        SceneNode node = new SceneNode();
        Signal<Float> opacitySignal = Signal.create(1.0f);

        runtime.bind(opacitySignal, node::setOpacity);
        runtime.flush();

        node.clearDirtyFlags();
        opacitySignal.set(0.5f);
        runtime.flush();

        Assert.assertEquals("节点 opacity 应为新值", 0.5f, node.getOpacity(), 0.001f);
        Assert.assertTrue("应标 compositeDirty", node.__isCompositeDirty());
        Assert.assertFalse("不应标 selfLayoutDirty", node.__isSelfLayoutDirty());
        Assert.assertFalse("不应标 selfPaintDirty", node.__isSelfPaintDirty());
    }

    // ==================== 测试 2：mount builder 只执行一次（I3） ====================

    /**
     * 验证：mount 的 builder 只执行一次（I3）。
     * 多次修改 bind 的 signal 并 flush，builder 计数器仍为 1。
     */
    @Test
    public void mountBuilderShouldExecuteOnlyOnce() {
        SceneNode parent = new SceneNode();
        Signal<Integer> colorSignal = Signal.create(0xFFFFFFFF);
        AtomicInteger builderCallCount = new AtomicInteger(0);

        MountHandle handle = runtime.mount(parent, () -> {
            builderCallCount.incrementAndGet();
            SceneNode child = new SceneNode();
            // 在 builder 内部 bind——effect 随 mount 作用域自动管理
            runtime.bind(colorSignal, child::setBackgroundColor);
            return child;
        });

        // builder 应在 mount 调用期间执行一次
        Assert.assertEquals("builder 应在 mount 期间执行一次", 1, builderCallCount.get());

        // 多次修改 signal + flush，builder 不应再被调用
        colorSignal.set(0xFFFF0000);
        runtime.flush();
        Assert.assertEquals("第 1 次 signal 变更后 builder 仍为 1", 1, builderCallCount.get());

        colorSignal.set(0xFF00FF00);
        runtime.flush();
        Assert.assertEquals("第 2 次 signal 变更后 builder 仍为 1", 1, builderCallCount.get());

        // 验证 effect 正常工作：子节点颜色被正确更新
        SceneNode child = handle.getRoot();
        Assert.assertNotNull("挂载根节点不应为 null", child);
        Assert.assertEquals("子节点颜色应为最后一次 set 的值", 0xFF00FF00, child.getBackgroundColor());

        handle.dispose();
    }

    // ==================== 测试 3：dispose 后退订 effect ====================

    /**
     * 验证：dispose 后 signal 再变化不会触发 applier（effect 已退订）。
     */
    @Test
    public void disposedBindingShouldNotReactToSignalChange() {
        SceneNode node = new SceneNode();
        Signal<Integer> colorSignal = Signal.create(0);

        Binding binding = runtime.bind(colorSignal, node::setBackgroundColor);
        runtime.flush();
        Assert.assertEquals("初始颜色应为 0", 0, node.getBackgroundColor());

        // 退订
        binding.dispose();
        Assert.assertTrue("绑定应标记为已释放", binding.isDisposed());

        // signal 再变化
        colorSignal.set(0xFFFF0000);
        runtime.flush();

        // 节点值应保持初始值，未被更新
        Assert.assertEquals("退订后颜色不应变化", 0, node.getBackgroundColor());
    }

    /**
     * 验证：runtime.dispose() 整体清理后，关联的 effect 全部退订。
     */
    @Test
    public void runtimeDisposeShouldUnsubscribeAllEffects() {
        SceneNode node = new SceneNode();
        Signal<Integer> colorSignal = Signal.create(0);

        runtime.bind(colorSignal, node::setBackgroundColor);
        runtime.flush();
        Assert.assertEquals("初始颜色应为 0", 0, node.getBackgroundColor());

        // 整体销毁
        runtime.dispose();

        colorSignal.set(0xFFFF0000);
        ReactiveScheduler.get().flush(); // 直接用 scheduler flush（runtime 已 dispose）

        Assert.assertEquals("runtime dispose 后颜色不应变化", 0, node.getBackgroundColor());
    }

    // ==================== 测试 4：mount lifecycle（挂载/卸载） ====================

    /**
     * 验证：mount 后子节点正确挂在 parent 下，dispose 后自动移除。
     */
    @Test
    public void mountShouldAppendAndDisposeShouldRemove() {
        SceneNode parent = new SceneNode();

        MountHandle handle = runtime.mount(parent, () -> {
            return new SceneNode();
        });

        SceneNode child = handle.getRoot();
        Assert.assertNotNull("挂载根节点不应为 null", child);
        Assert.assertTrue("parent 应包含 child", parent.__getChildren().contains(child));
        Assert.assertSame("child 的 parent 应为 parent", parent, child.__getParent());

        // 卸载
        handle.dispose();
        Assert.assertFalse("卸载后 parent 不应包含 child", parent.__getChildren().contains(child));
        Assert.assertNull("卸载后 child 的 parent 应为 null", child.__getParent());
    }

    /**
     * 验证：在 mount builder 内部 bind 的 effect 随 mount dispose 一并退订。
     */
    @Test
    public void bindInsideMountShouldBeDisposedWithMount() {
        SceneNode parent = new SceneNode();
        Signal<Integer> colorSignal = Signal.create(0);

        MountHandle handle = runtime.mount(parent, () -> {
            SceneNode child = new SceneNode();
            runtime.bind(colorSignal, child::setBackgroundColor);
            return child;
        });

        SceneNode child = handle.getRoot();
        runtime.flush();
        Assert.assertEquals("初始颜色应为 0", 0, child.getBackgroundColor());

        // 卸载 mount 作用域（内部 effect 一并 dispose）
        handle.dispose();

        colorSignal.set(0xFFFF0000);
        runtime.flush();
        Assert.assertEquals("mount dispose 后绑定的颜色不应变化", 0, child.getBackgroundColor());
    }

    /**
     * 验证：在 mount builder 内部注册的输入 handler 随 mount dispose 一并退订。
     */
    @Test
    public void onInsideMountShouldBeDisposedWithMount() {
        SceneNode parent = new SceneNode();
        AtomicInteger calls = new AtomicInteger(0);
        final InputBinding[] bindingHolder = new InputBinding[1];

        MountHandle handle = runtime.mount(parent, () -> {
            SceneNode child = new SceneNode();
            child.setCachedLayout(new LayoutBox(0, 0, 100, 100));
            bindingHolder[0] = runtime.on(child, SceneEventType.SCROLL, (evt, ctx) -> calls.incrementAndGet());
            return child;
        });

        parent.setCachedLayout(new LayoutBox(0, 0, 100, 100));
        routeScroll(parent);
        Assert.assertEquals("mount 后 handler 应触发", 1, calls.get());

        handle.dispose();

        Assert.assertNotNull("handler 绑定应已创建", bindingHolder[0]);
        Assert.assertTrue("mount dispose 后 handler 绑定应退订", bindingHolder[0].isDisposed());
    }

    private void routeScroll(SceneNode root) {
        InputFrameBuilder builder = new InputFrameBuilder(50, 50);
        builder.push(RawInputEvent.ofPointer(ScenePointerAction.SCROLL, 50, 50,
                SceneMouseButton.NONE, -120, 0, 0,
                false, false, false, false, 1000L));
        SceneInputFrame frame = builder.drainFrame();
        runtime.route(root, frame, 0, 0);
    }

    // ==================== 测试 5：impact 参数语义 ====================

    /**
     * 验证：bind 的 impact 参数与属性槽实际打出的级别一致。
     * 如果声明 LAYOUT 但 applier 写 setBackgroundColor，实际打出 PAINT——
     * 这证实了"impact 是声明/校验用途，真正打级靠属性槽"的设计。
     */
    @Test
    public void impactParameterIsDeclarativeActualLevelDeterminedBySetter() {
        SceneNode node = new SceneNode();
        Signal<Integer> colorSignal = Signal.create(0);

        // 即使声明 LAYOUT，setBackgroundColor 内部仍只打 PAINT
        runtime.bind(colorSignal, node::setBackgroundColor);
        runtime.flush();

        node.clearDirtyFlags();
        colorSignal.set(0xFF0000FF);
        runtime.flush();

        // setBackgroundColor 只打 PAINT，不因 impact=LAYOUT 而误打
        Assert.assertTrue("实际仍打 selfPaintDirty", node.__isSelfPaintDirty());
        Assert.assertFalse("不应因声明 LAYOUT 而误打 selfLayoutDirty",
            node.__isSelfLayoutDirty());
    }

    // ==================== 测试 6：自引用场景树防回归 ====================

    /**
     * 验证：正确构建的场景树（root → child，无自引用）layout 正常返回。
     *
     * <p>这是 T6 真机崩溃的防回归锚点。修复前 SceneHostWidget 构造时误用
     * {@code runtime.mount(root, () -> root)} 导致 root 自引用，layout DFS 无限递归
     * 抛 StackOverflowError。修复后 root.children 只含 child，layout 正常。</p>
     */
    @Test
    public void shouldLayoutCorrectTreeWithoutStackOverflow() {
        // 构造等价于修复后 SceneHostWidget 的场景：root → child + bind
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        root.appendChild(child);

        runtime.bind(Signal.create(0xFF333333), root::setBackgroundColor);
        runtime.bind(Signal.create("Scene Demo: Hello"), child::setText);
        runtime.flush();

        // 验证：root 不包含自身
        Assert.assertEquals("root children 数应为 1", 1, root.__getChildren().size());
        Assert.assertSame("root 的唯一子节点应为 child", child, root.__getChildren().get(0));
        Assert.assertFalse("root 不应包含自身", root.__getChildren().contains(root));

        // 验证：layout 正常返回（不抛 StackOverflowError）
        SceneLayoutEngine layoutEngine = new SceneLayoutEngine(new FixedTextMeasurer());
        try {
            layoutEngine.layout(root, new Constraints(200));
        } catch (StackOverflowError e) {
            Assert.fail("正确场景树不应导致 layout 无限递归：" + e.getMessage());
        }
        // layout 后 child 应有缓存
        Assert.assertNotNull("layout 后 child 应有 cachedLayout", child.getCachedLayout());
    }

    /**
     * 验证：自引用场景树（root 是自身子节点）会导致布局爆栈，
     * 确认测试能捕获此类错误。
     */
    @Test(expected = StackOverflowError.class)
    public void shouldDetectSelfReferencingTreeInLayout() {
        SceneNode root = new SceneNode();
        // 刻意构造自引用：root 把自己当子节点
        root.appendChild(root);

        SceneLayoutEngine layoutEngine = new SceneLayoutEngine(new FixedTextMeasurer());
        // 期望抛 StackOverflowError（自引用导致无限递归）
        layoutEngine.layout(root, new Constraints(200));
    }
}
