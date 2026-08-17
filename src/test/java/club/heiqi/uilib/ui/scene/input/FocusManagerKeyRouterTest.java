package club.heiqi.uilib.ui.scene.input;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FocusManager + 键盘/文本路由 + 焦点信号闭环 单元测试（I4a 全部验收单测）。
 *
 * <p>覆盖：A焦点基础 / B Tab遍历 / C键盘分发 / D文本分发 /
 * E requestFocus via ctx / F focused signal接通 / G零标脏回归 / H回收/隔离。</p>
 */
public class FocusManagerKeyRouterTest {

    private SceneInputRouter router;
    private InputFrameBuilder frameBuilder;
    private SceneRuntime runtime;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        runtime = new SceneRuntime();
        router = runtime.getInputRouter();
        frameBuilder = new InputFrameBuilder(0, 0);
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== 辅助方法 ====================

    /** 构建单指针事件帧 */
    private SceneInputFrame buildPointerFrame(ScenePointerAction action, int x, int y, SceneMouseButton button) {
        frameBuilder.push(RawInputEvent.ofPointer(action, x, y,
                button, 0, 0, 0,
                false, false, false, false, 1000L));
        return frameBuilder.drainFrame();
    }

    /** 构建单键盘事件帧 */
    private SceneInputFrame buildKeyFrame(SceneKey key, SceneKeyAction action,
                                          boolean ctrl, boolean shift) {
        frameBuilder.push(RawInputEvent.ofKey(key, action,
                ctrl, shift, false, false,
                RawInputEvent.NATIVE_NONE, RawInputEvent.NATIVE_NONE, 1000L));
        return frameBuilder.drainFrame();
    }

    /** 构建文本事件帧 */
    private SceneInputFrame buildTextFrame(String text) {
        frameBuilder.push(RawInputEvent.ofText(text, 1000L));
        return frameBuilder.drainFrame();
    }

    /** 构建两层树并设置 LayoutBox */
    private SceneNode buildTwoLayerTree() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        root.appendChild(child);

        root.setCachedLayout(new LayoutBox(0, 0, 200, 200));
        child.setCachedLayout(new LayoutBox(20, 20, 80, 60));
        return root;
    }

    /** 构建三层嵌套树 */
    private SceneNode buildThreeLayerTree() {
        SceneNode root = new SceneNode();
        SceneNode a = new SceneNode();
        SceneNode b = new SceneNode();
        root.appendChild(a);
        a.appendChild(b);

        root.setCachedLayout(new LayoutBox(0, 0, 300, 300));
        a.setCachedLayout(new LayoutBox(10, 10, 200, 200));
        b.setCachedLayout(new LayoutBox(10, 10, 100, 100));
        return root;
    }

    /** DFS 清所有脏标记 */
    private void clearAllDirtyRecursive(SceneNode node) {
        if (node == null) return;
        node.clearDirtyFlags();
        node.clearGeometryDirty();
        for (SceneNode child : node.__getChildren()) {
            clearAllDirtyRecursive(child);
        }
    }

    /** DFS 收集所有节点的 7 个脏探针 */
    private Map<SceneNode, boolean[]> collectDirtyProbes(SceneNode root) {
        Map<SceneNode, boolean[]> result = new HashMap<SceneNode, boolean[]>();
        collectDirtyProbesRecursive(root, result);
        return result;
    }

    private void collectDirtyProbesRecursive(SceneNode node, Map<SceneNode, boolean[]> result) {
        if (node == null) return;
        boolean[] probes = new boolean[7];
        probes[0] = node.__isSelfLayoutDirty();
        probes[1] = node.__isDescendantLayoutDirty();
        probes[2] = node.__isSelfPaintDirty();
        probes[3] = node.__isDescendantPaintDirty();
        probes[4] = node.__isCompositeDirty();
        probes[5] = node.__isSelfGeometryDirty();
        probes[6] = node.__isDescendantGeometryDirty();
        result.put(node, probes);
        for (SceneNode child : node.__getChildren()) {
            collectDirtyProbesRecursive(child, result);
        }
    }

    /** 断言两个探针 Map 中所有节点 7 探针全等 */
    private void assertProbesEqual(Map<SceneNode, boolean[]> before, Map<SceneNode, boolean[]> after, String label) {
        String[] names = {"selfLayout", "descLayout", "selfPaint", "descPaint",
                "composite", "selfGeom", "descGeom"};
        for (Map.Entry<SceneNode, boolean[]> entry : before.entrySet()) {
            SceneNode node = entry.getKey();
            boolean[] bf = entry.getValue();
            boolean[] af = after.get(node);
            Assert.assertNotNull(label + " after 中应存在节点", af);
            for (int i = 0; i < 7; i++) {
                Assert.assertEquals(label + " 节点 " + System.identityHashCode(node) + " 标记 " + names[i],
                        bf[i], af[i]);
            }
        }
    }

    // ==================== A 焦点基础 ====================

    // A1：requestFocus 切换焦点 → focusedNode 变更
    @Test
    public void shouldSwitchFocusedNodeOnRequestFocus() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        Assert.assertNull("初始焦点为 null", router.__getFocusedNode());

        router.requestFocus(child);
        Assert.assertSame("requestFocus 后焦点应为 child", child, router.__getFocusedNode());
    }

    // A2：焦点切换 → 旧焦点 writeFocused(false) + 新焦点 writeFocused(true)
    @Test
    public void shouldWriteFocusedSignalsOnFocusSwitch() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        // 声明关心 focus
        ReadableSignal<Boolean> childFocus = router.interactionState(child).focused();
        router.requestFocus(child);
        runtime.flush();
        Assert.assertEquals("flush 后 child focused=true", Boolean.TRUE, childFocus.get());

        // 切到 root
        ReadableSignal<Boolean> rootFocus = router.interactionState(root).focused();
        router.requestFocus(root);
        runtime.flush();
        Assert.assertEquals("切后 child focused=false", Boolean.FALSE, childFocus.get());
        Assert.assertEquals("切后 root focused=true", Boolean.TRUE, rootFocus.get());
    }

    // A3：clearFocus → writeFocused(false) + focusedNode=null
    @Test
    public void shouldClearFocusAndWriteFalse() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        ReadableSignal<Boolean> childFocus = router.interactionState(child).focused();
        router.requestFocus(child);
        runtime.flush();
        Assert.assertEquals("聚焦后 focused=true", Boolean.TRUE, childFocus.get());

        router.__getFocusManager().clearFocus();
        Assert.assertNull("clearFocus 后 focusedNode 应为 null", router.__getFocusedNode());
        runtime.flush();
        Assert.assertEquals("clearFocus+flush 后 focused=false", Boolean.FALSE, childFocus.get());
    }

    // A4：requestFocus 未声明 interactionState 的节点 → writeFocused 短路不报错
    @Test
    public void shouldNotFailWhenRequestFocusOnNodeWithoutInteractionState() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        // 不声明 interactionState，直接 requestFocus
        router.requestFocus(child);
        Assert.assertSame("焦点应为 child", child, router.__getFocusedNode());

        // 切换应无异常
        router.requestFocus(root);
        Assert.assertSame("焦点应为 root", root, router.__getFocusedNode());
    }

    // A5：requestFocus(null) 安全短路
    @Test
    public void shouldNoOpOnRequestFocusNull() {
        router.requestFocus(null);
        Assert.assertNull("焦点仍为 null", router.__getFocusedNode());

        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);
        router.requestFocus(child);
        Assert.assertSame("焦点为 child", child, router.__getFocusedNode());

        router.requestFocus(null);
        // requestFocus(null) 不应 clear —— 需要显式 clearFocus
        // 当前实现 requestFocus(null) 返回 false 不做任何事
    }

    // A6：同步焦点事件先于 focused signal flush 派发
    @Test
    public void shouldDispatchSynchronousFocusEvents() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);
        List<String> events = new ArrayList<String>();
        runtime.on(child, SceneEventType.FOCUS_GAINED, (event, context) -> events.add("gained"));
        runtime.on(child, SceneEventType.FOCUS_LOST, (event, context) -> events.add("lost"));
        ReadableSignal<Boolean> focused = router.interactionState(child).focused();

        router.requestFocus(child);
        Assert.assertEquals("focus gained 必须同步可见", Arrays.asList("gained"), events);
        Assert.assertEquals("focused signal 仍保持延迟语义", Boolean.FALSE, focused.get());

        router.requestFocus(root);
        Assert.assertEquals("切换时 lost 必须在返回前派发", Arrays.asList("gained", "lost"), events);
        runtime.flush();
        Assert.assertEquals(Boolean.FALSE, focused.get());
    }

    // A7：LOST handler 重入改回旧焦点时，不得继续派发过期 GAINED
    @Test
    public void shouldSkipStaleFocusGainedAfterReentrantLostHandler() {
        SceneNode root = new SceneNode();
        SceneNode a = new SceneNode();
        SceneNode b = new SceneNode();
        root.appendChild(a);
        root.appendChild(b);
        List<String> events = new ArrayList<String>();
        runtime.on(a, SceneEventType.FOCUS_LOST, (event, context) -> {
            events.add("a-lost");
            context.requestFocus();
        });
        runtime.on(a, SceneEventType.FOCUS_GAINED, (event, context) -> events.add("a-gained"));
        runtime.on(b, SceneEventType.FOCUS_LOST, (event, context) -> events.add("b-lost"));
        runtime.on(b, SceneEventType.FOCUS_GAINED, (event, context) -> events.add("b-gained"));
        router.requestFocus(a);
        events.clear();

        router.requestFocus(b);

        Assert.assertSame("LOST handler 重入后最终 authority 应回到 A", a, router.__getFocusedNode());
        Assert.assertEquals(Arrays.asList("a-lost", "b-lost", "a-gained"), events);
    }

    @Test
    public void shouldGainNewFocusBeforeRethrowingLostRuntimeException() {
        SceneNode a = new SceneNode();
        SceneNode b = new SceneNode();
        RuntimeException lostFailure = new RuntimeException("lost");
        List<String> events = new ArrayList<String>();
        runtime.on(a, SceneEventType.FOCUS_LOST, (event, context) -> {
            throw lostFailure;
        });
        runtime.on(b, SceneEventType.FOCUS_GAINED, (event, context) -> events.add("b-gained"));
        router.requestFocus(a);

        try {
            router.requestFocus(b);
            Assert.fail("LOST failure 应在 GAINED 收口后原样重抛");
        } catch (RuntimeException actual) {
            Assert.assertSame(lostFailure, actual);
        }

        Assert.assertSame("异常后 authority 仍应指向新焦点", b, router.__getFocusedNode());
        Assert.assertEquals(Arrays.asList("b-gained"), events);
    }

    @Test
    public void shouldPreserveLostErrorWhenGainedAlsoFails() {
        SceneNode a = new SceneNode();
        SceneNode b = new SceneNode();
        AssertionError lostFailure = new AssertionError("lost");
        RuntimeException gainedFailure = new RuntimeException("gained");
        runtime.on(a, SceneEventType.FOCUS_LOST, (event, context) -> {
            throw lostFailure;
        });
        runtime.on(b, SceneEventType.FOCUS_GAINED, (event, context) -> {
            throw gainedFailure;
        });
        router.requestFocus(a);

        try {
            router.requestFocus(b);
            Assert.fail("首个 LOST Error 应在 GAINED 尝试后原样重抛");
        } catch (AssertionError actual) {
            Assert.assertSame(lostFailure, actual);
            Assert.assertArrayEquals(new Throwable[]{gainedFailure}, actual.getSuppressed());
        }

        Assert.assertSame("双异常后 authority 仍应指向新焦点", b, router.__getFocusedNode());
    }

    @Test
    public void shouldRejectReentrantFocusAfterFocusableIsUnregistered() {
        SceneNode node = new SceneNode();
        List<String> events = new ArrayList<String>();
        ReadableSignal<Boolean> focused = router.interactionState(node).focused();
        runtime.on(node, SceneEventType.FOCUS_LOST, (event, context) -> {
            events.add("lost");
            context.requestFocus();
        });
        runtime.on(node, SceneEventType.FOCUS_GAINED, (event, context) -> events.add("gained"));
        router.registerFocusable(node);
        router.requestFocus(node);
        runtime.flush();
        events.clear();

        router.unregisterFocusable(node);
        runtime.flush();

        Assert.assertNull("unregister 的 LOST 重入不得恢复 authority", router.__getFocusedNode());
        Assert.assertEquals(Boolean.FALSE, focused.get());
        Assert.assertEquals(Arrays.asList("lost"), events);
        Assert.assertFalse("disabled/已卸载节点后续显式请求也应被拒绝", router.requestFocus(node));

        router.registerFocusable(node);
        Assert.assertTrue("重新注册后应恢复可聚焦", router.requestFocus(node));
        Assert.assertSame(node, router.__getFocusedNode());
    }

    // ==================== B Tab 遍历 ====================

    // B1：注册 3 个 focusable，focusNext 按 DOM 前序循环
    @Test
    public void shouldFocusNextInDomPreOrder() {
        SceneNode root = new SceneNode();
        SceneNode a = new SceneNode();
        SceneNode b = new SceneNode();
        SceneNode c = new SceneNode();
        root.appendChild(a);
        root.appendChild(b);
        a.appendChild(c);

        router.registerFocusable(a);
        router.registerFocusable(b);
        router.registerFocusable(c);

        // DOM 前序：root(非focusable) → a → c(子) → b
        // 排序后：[a, c, b]

        FocusManager fm = router.__getFocusManager();
        fm.setRoot(root);

        // 无焦点时首次 focusNext → 首个
        fm.focusNext();
        Assert.assertSame("无焦点时聚焦首个 focusable", a, router.__getFocusedNode());

        fm.focusNext();
        Assert.assertSame("下一个应为 c", c, router.__getFocusedNode());

        fm.focusNext();
        Assert.assertSame("下一个应为 b", b, router.__getFocusedNode());

        fm.focusNext();
        Assert.assertSame("循环回首个 a", a, router.__getFocusedNode());
    }

    // B2：focusPrevious 反向遍历
    @Test
    public void shouldFocusPreviousInReverseDomPreOrder() {
        SceneNode root = new SceneNode();
        SceneNode a = new SceneNode();
        SceneNode b = new SceneNode();
        SceneNode c = new SceneNode();
        root.appendChild(a);
        root.appendChild(b);
        a.appendChild(c);

        router.registerFocusable(a);
        router.registerFocusable(b);
        router.registerFocusable(c);
        // 排序：[a, c, b]

        FocusManager fm2 = router.__getFocusManager();
        fm2.setRoot(root);

        // 无焦点时 focusPrevious → 最后一个
        fm2.focusPrevious();
        Assert.assertSame("无焦点 Shift+Tab 聚焦最后一个", b, router.__getFocusedNode());

        fm2.focusPrevious();
        Assert.assertSame("上一个应为 c", c, router.__getFocusedNode());

        fm2.focusPrevious();
        Assert.assertSame("上一个应为 a", a, router.__getFocusedNode());

        fm2.focusPrevious();
        Assert.assertSame("循环回最后一个 b", b, router.__getFocusedNode());
    }

    // B3：focusables 为空时 focusNext/focusPrevious 无副作用
    @Test
    public void shouldNoOpWhenNoFocusables() {
        SceneNode root = buildTwoLayerTree();
        FocusManager fm3 = router.__getFocusManager();
        fm3.setRoot(root);
        fm3.focusNext();
        Assert.assertNull("无 focusable 时焦点仍 null", router.__getFocusedNode());

        fm3.focusPrevious();
        Assert.assertNull("无 focusable 时焦点仍 null", router.__getFocusedNode());
    }

    // B4：临时关闭 presentation subtree 输入时，Tab 不得进入视觉位移中的真实控件
    @Test
    public void shouldSkipInputGatedSubtreeDuringTabTraversal() {
        SceneNode root = new SceneNode();
        SceneNode presentationShell = new SceneNode();
        SceneNode gatedControl = new SceneNode();
        SceneNode stableControl = new SceneNode();
        root.appendChild(presentationShell);
        root.appendChild(stableControl);
        presentationShell.appendChild(gatedControl);
        presentationShell.__setHitTestSubtreeEnabled(false);
        router.registerFocusable(gatedControl);
        router.registerFocusable(stableControl);

        FocusManager focusManager = router.__getFocusManager();
        focusManager.setRoot(root);
        focusManager.focusNext();
        Assert.assertSame("Tab 应跳过视觉位置与命中盒尚未重合的子树",
                stableControl, router.__getFocusedNode());

        focusManager.clearFocus();
        presentationShell.__setHitTestSubtreeEnabled(true);
        focusManager.focusNext();
        Assert.assertSame("门禁恢复后重新进入原 DOM 顺序", gatedControl, router.__getFocusedNode());
    }

    // ==================== C 键盘分发 ====================

    // C1：KEY_DOWN 事件投给 focusedNode + bubble 到祖先
    @Test
    public void shouldDispatchKeyDownToFocusedNodeAndBubble() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        router.registerFocusable(child);
        router.requestFocus(child);

        List<String> log = new ArrayList<String>();
        router.on(child, SceneEventType.KEY_DOWN, (evt, ctx) -> log.add("child:" + evt.getKey()));
        router.on(root, SceneEventType.KEY_DOWN, (evt, ctx) -> log.add("root:" + evt.getKey()));

        SceneInputFrame frame = buildKeyFrame(SceneKey.ENTER, SceneKeyAction.PRESSED, false, false);
        runtime.route(root, frame, 0, 0);

        Assert.assertEquals("应有两个 handler 被调用", 2, log.size());
        Assert.assertEquals("child 先收到", "child:ENTER", log.get(0));
        Assert.assertEquals("root 后收到（bubble）", "root:ENTER", log.get(1));
    }

    // C2：无焦点时 key 事件不投递
    @Test
    public void shouldNotDispatchKeyWhenNoFocus() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        List<String> log = new ArrayList<String>();
        router.on(child, SceneEventType.KEY_DOWN, (evt, ctx) -> log.add("child:" + evt.getKey()));

        SceneInputFrame frame = buildKeyFrame(SceneKey.ENTER, SceneKeyAction.PRESSED, false, false);
        runtime.route(root, frame, 0, 0);

        Assert.assertEquals("无焦点时应无 handler 被调用", 0, log.size());
    }

    // C3：无焦点但 Tab 键仍触发遍历
    @Test
    public void shouldTriggerTabTraversalEvenWithoutFocus() {
        SceneNode root = new SceneNode();
        SceneNode a = new SceneNode();
        root.appendChild(a);

        router.registerFocusable(a);
        Assert.assertNull("初始焦点为 null", router.__getFocusedNode());

        SceneInputFrame frame = buildKeyFrame(SceneKey.TAB, SceneKeyAction.PRESSED, false, false);
        runtime.route(root, frame, 0, 0);

        Assert.assertSame("Tab 后焦点应为 a", a, router.__getFocusedNode());
    }

    // C4：handler stopPropagation 拦截 Tab 默认遍历
    @Test
    public void shouldAllowHandlerToPreventTabTraversal() {
        SceneNode root = new SceneNode();
        SceneNode a = new SceneNode();
        SceneNode b = new SceneNode();
        root.appendChild(a);
        root.appendChild(b);

        router.registerFocusable(a);
        router.registerFocusable(b);
        router.requestFocus(a);

        // handler 内调 stopPropagation 阻止 Tab 切换
        router.on(a, SceneEventType.KEY_DOWN, (evt, ctx) -> {
            if (evt.getKey() == SceneKey.TAB) {
                ctx.stopPropagation();
            }
        });

        SceneInputFrame frame = buildKeyFrame(SceneKey.TAB, SceneKeyAction.PRESSED, false, false);
        runtime.route(root, frame, 0, 0);

        // Tab 遍历被拦截，焦点应仍为 a
        Assert.assertSame("stopPropagation 后焦点仍为 a", a, router.__getFocusedNode());
    }

    // C5：前一 key 事件 handler 内 requestFocus 改焦点，同帧后续 key 投给新焦点
    @Test
    public void shouldDispatchToNewFocusAfterRequestFocusInSameFrame() {
        SceneNode root = new SceneNode();
        SceneNode a = new SceneNode();
        SceneNode b = new SceneNode();
        root.appendChild(a);
        root.appendChild(b);

        router.registerFocusable(a);
        router.registerFocusable(b);
        router.requestFocus(a);

        List<String> log = new ArrayList<String>();
        // 第一个 KEY 事件 handler 内切换焦点到 b
        router.on(a, SceneEventType.KEY_DOWN, (evt, ctx) -> {
            log.add("a");
            ctx.requestFocus(); // 聚焦 target=a（实际 a 已经是焦点，无变化）
            router.requestFocus(b); // 手动切换焦点到 b
        });
        router.on(b, SceneEventType.KEY_DOWN, (evt, ctx) -> log.add("b"));

        // 同帧推两个 key 事件
        frameBuilder.push(RawInputEvent.ofKey(SceneKey.KEY_A, SceneKeyAction.PRESSED,
                false, false, false, false,
                RawInputEvent.NATIVE_NONE, RawInputEvent.NATIVE_NONE, 1000L));
        frameBuilder.push(RawInputEvent.ofKey(SceneKey.KEY_B, SceneKeyAction.PRESSED,
                false, false, false, false,
                RawInputEvent.NATIVE_NONE, RawInputEvent.NATIVE_NONE, 2000L));
        SceneInputFrame frame = frameBuilder.drainFrame();

        runtime.route(root, frame, 0, 0);

        // 第一个 key(A) 投给 a；第二个 key(B) 投给 b（焦点已在第一个事件中被切换）
        Assert.assertEquals("应收到两个事件", 2, log.size());
        Assert.assertEquals("第一个事件投给 a", "a", log.get(0));
        Assert.assertEquals("第二个事件投给 b", "b", log.get(1));
    }

    // C6：KEY_UP 事件正常投递
    @Test
    public void shouldDispatchKeyUpToFocusedNode() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        router.registerFocusable(child);
        router.requestFocus(child);

        List<String> log = new ArrayList<String>();
        router.on(child, SceneEventType.KEY_UP, (evt, ctx) -> log.add("KEY_UP:" + evt.getKey()));

        SceneInputFrame frame = buildKeyFrame(SceneKey.ENTER, SceneKeyAction.RELEASED, false, false);
        runtime.route(root, frame, 0, 0);

        Assert.assertEquals("KEY_UP 应被投递", 1, log.size());
        Assert.assertEquals("KEY_UP:ENTER", log.get(0));
    }

    // ==================== D 文本分发 ====================

    // D1：TEXT_INPUT 投给 focusedNode
    @Test
    public void shouldDispatchTextInputToFocusedNode() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        router.registerFocusable(child);
        router.requestFocus(child);

        List<String> log = new ArrayList<String>();
        router.on(child, SceneEventType.TEXT_INPUT, (evt, ctx) -> log.add("text:" + evt.getText()));

        SceneInputFrame frame = buildTextFrame("hello");
        runtime.route(root, frame, 0, 0);

        Assert.assertEquals("TEXT_INPUT 应被投递", 1, log.size());
        Assert.assertEquals("text:hello", log.get(0));
    }

    // D2：无焦点时 TEXT_INPUT 丢弃
    @Test
    public void shouldDiscardTextInputWhenNoFocus() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        List<String> log = new ArrayList<String>();
        router.on(child, SceneEventType.TEXT_INPUT, (evt, ctx) -> log.add("text:" + evt.getText()));

        SceneInputFrame frame = buildTextFrame("hello");
        runtime.route(root, frame, 0, 0);

        Assert.assertEquals("无焦点时文本应丢弃", 0, log.size());
    }

    // ==================== E requestFocus via ctx ====================

    // E1：handler 内 ctx.requestFocus() → focusedNode 变更
    @Test
    public void shouldChangeFocusViaCtxRequestFocus() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        // 先聚焦 root，然后在 root 的 key handler 中通过 ctx 聚焦 child
        router.registerFocusable(child);
        router.requestFocus(root);
        Assert.assertSame("初始焦点为 root", root, router.__getFocusedNode());

        // 给 root 注册 KEY handler，内部通过 ctx 聚焦 child
        // 但 ctx.requestFocus() 聚焦的是 event.target（即 root 自己）
        // 要聚焦 child，需要用 router.requestFocus(child)
        // 这里测试 ctx.requestFocus() 的正确行为
        router.on(root, SceneEventType.KEY_DOWN, (evt, ctx) -> {
            ctx.requestFocus(); // 聚焦 event.target = root（本来就是 root，无变化）
        });

        SceneInputFrame frame = buildKeyFrame(SceneKey.ENTER, SceneKeyAction.PRESSED, false, false);
        runtime.route(root, frame, 0, 0);
        Assert.assertSame("ctx.requestFocus 焦点仍为 root", root, router.__getFocusedNode());
    }

    // E1b：bubble 场景下 ctx.requestFocus 聚焦 event.target 而非 currentNode
    // ★ 关键约束：ctx 构造时注入的 target 是 event.target（最深焦点节点），不是 bubble 游标 currentNode。
    // bubble 到祖先(root)时 handler 调 ctx.requestFocus()，仍应聚焦原 target(child)，而非 currentNode(root)。
    @Test
    public void shouldFocusEventTargetNotBubbleCurrentNodeWhenRequestFocusInBubble() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        // 先建 child 的 interactionState 容器，再 requestFocus（G2 教训：容器必须先于写入存在）
        router.registerFocusable(child);
        ReadableSignal<Boolean> childFocus = router.interactionState(child).focused();
        router.requestFocus(child);
        runtime.flush();
        Assert.assertSame("初始焦点为 child", child, router.__getFocusedNode());

        // bubble 场景：handler 注册在 root 上（bubble 阶段 currentNode=root），
        // handler 内调 ctx.requestFocus()。若实现错误地聚焦 currentNode(root)，焦点会变成 root。
        List<String> log = new ArrayList<String>();
        router.on(root, SceneEventType.KEY_DOWN, (evt, ctx) -> {
            log.add("bubbleRoot:currentNode=" + (ctx.getCurrentNode() == root)
                    + ",target=" + (evt.getTarget() == child));
            ctx.requestFocus();
        });

        // route 一个 KEY_DOWN 事件，目标为 child（当前焦点），bubble 到 root
        SceneInputFrame frame = buildKeyFrame(SceneKey.ENTER, SceneKeyAction.PRESSED, false, false);
        runtime.route(root, frame, 0, 0);
        runtime.flush();

        // 关键断言：焦点仍是 child（event.target），未因 bubble 中的 ctx.requestFocus 变成 root
        Assert.assertEquals("root 的 handler 应在 bubble 阶段被调 1 次", 1, log.size());
        Assert.assertTrue("handler 中 currentNode 应为 root", log.get(0).contains("currentNode=true"));
        Assert.assertTrue("handler 中 evt.getTarget() 应为 child", log.get(0).contains("target=true"));
        Assert.assertSame("ctx.requestFocus 聚焦 event.target=child，焦点不变", child, router.__getFocusedNode());
        Assert.assertEquals("child focused signal 仍为 true", Boolean.TRUE, childFocus.get());
    }

    // E2：handler 内 router.requestFocus → focused signal 翻转
    @Test
    public void shouldFlipFocusedSignalViaCtxRequestFocus() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        ReadableSignal<Boolean> rootFocus = router.interactionState(root).focused();
        ReadableSignal<Boolean> childFocus = router.interactionState(child).focused();

        router.registerFocusable(child);
        router.requestFocus(root);
        runtime.flush();
        Assert.assertEquals("root focused=true", Boolean.TRUE, rootFocus.get());

        // handler 内切换焦点到 child
        router.on(root, SceneEventType.KEY_DOWN, (evt, ctx) -> {
            router.requestFocus(child);
        });

        SceneInputFrame frame = buildKeyFrame(SceneKey.ENTER, SceneKeyAction.PRESSED, false, false);
        runtime.route(root, frame, 0, 0);
        runtime.flush();

        Assert.assertEquals("root focused=false", Boolean.FALSE, rootFocus.get());
        Assert.assertEquals("child focused=true", Boolean.TRUE, childFocus.get());
    }

    // ==================== F focused signal 接通 ====================

    // F1：interactionState(node).focused() + bind(PAINT, focused) → requestFocus → flush → PAINT 脏
    @Test
    public void shouldWireFocusedSignalToPaintDirty() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        ReadableSignal<Boolean> focusedSig = router.interactionState(child).focused();
        int focusedColor = 0xFFFF0000;
        int unfocusedColor = 0xFF0000FF;

        runtime.bind(focusedSig, (focused) -> {
            child.setBackgroundColor(focused ? focusedColor : unfocusedColor);
        });

        runtime.flush();
        Assert.assertEquals("初始 unfocused 背景色", unfocusedColor, child.getBackgroundColor());
        clearAllDirtyRecursive(root);

        // requestFocus → flush
        router.requestFocus(child);
        runtime.flush();

        Assert.assertEquals("flush 后背景色应为 focusedColor", focusedColor, child.getBackgroundColor());
        Assert.assertTrue("节点应被打 PAINT 脏", child.__isSelfPaintDirty());
    }

    // F2：writeFocused 对未声明关心的节点短路
    @Test
    public void shouldShortCircuitWriteFocusedForUninterestedNode() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        // 创建 interactionState 但不调 .focused()
        SceneInteractionState state = router.interactionState(child);
        Assert.assertFalse("focused signal 未创建", state.__hasFocusedSignal());

        router.requestFocus(child);
        runtime.flush();

        // focused signal 仍未被创建（writeFocused 短路）
        Assert.assertFalse("flush 后 focused signal 仍不应被创建", state.__hasFocusedSignal());
    }

    // ==================== G 零标脏回归（最高优先） ====================

    // G1：route 含 key/text 事件 → route 前后 7 脏探针全等（route 自身零标脏）
    @Test
    public void shouldNotDirtyAnyNodeDuringKeyTextRoute() {
        SceneNode root = new SceneNode();
        SceneNode a = new SceneNode();
        SceneNode b = new SceneNode();
        SceneNode c = new SceneNode();
        root.appendChild(a);
        a.appendChild(b);
        b.appendChild(c);

        root.setCachedLayout(new LayoutBox(0, 0, 300, 300));
        a.setCachedLayout(new LayoutBox(10, 10, 200, 200));
        b.setCachedLayout(new LayoutBox(10, 10, 100, 100));
        c.setCachedLayout(new LayoutBox(5, 5, 40, 40));

        router.registerFocusable(c);
        router.requestFocus(c);

        clearAllDirtyRecursive(root);
        Map<SceneNode, boolean[]> before = collectDirtyProbes(root);

        // route：KEY_DOWN + TEXT_INPUT，无 handler，无 interactionState
        frameBuilder.push(RawInputEvent.ofKey(SceneKey.ENTER, SceneKeyAction.PRESSED,
                false, false, false, false,
                RawInputEvent.NATIVE_NONE, RawInputEvent.NATIVE_NONE, 1000L));
        frameBuilder.push(RawInputEvent.ofText("hello", 2000L));
        SceneInputFrame frame = frameBuilder.drainFrame();

        runtime.route(root, frame, 0, 0);

        Map<SceneNode, boolean[]> after = collectDirtyProbes(root);
        assertProbesEqual(before, after, "G1");
    }

    // G2：声明 interactionState + bind 后 route（flush 前）7 探针仍全等
    @Test
    public void shouldNotDirtyBeforeFlushEvenWithKeyTextRoute() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        router.registerFocusable(child);
        // ★ 必须先建 interactionState 容器，再 requestFocus，否则 writeFocused 因容器不存在而短路
        ReadableSignal<Boolean> focusedSig = router.interactionState(child).focused();
        router.requestFocus(child);
        runtime.bind(focusedSig, (focused) -> {
            child.setBackgroundColor(focused ? 0xFFFF0000 : 0xFF0000FF);
        });

        runtime.flush();
        clearAllDirtyRecursive(root);

        Map<SceneNode, boolean[]> before = collectDirtyProbes(root);

        // route：KEY_DOWN 事件（无 focus 变化，但确保 route 调用）
        SceneInputFrame frame = buildKeyFrame(SceneKey.ENTER, SceneKeyAction.PRESSED, false, false);
        runtime.route(root, frame, 0, 0);

        // ★ route 返回后、flush 之前采集
        Map<SceneNode, boolean[]> afterBeforeFlush = collectDirtyProbes(root);
        assertProbesEqual(before, afterBeforeFlush, "G2");

        // 同时验证 signal get 返回旧值
        Assert.assertEquals("flush 前 focused get 返回旧值", Boolean.TRUE, focusedSig.get());
    }

    // G3：紧接 G2 调 flush → 此时才出现 PAINT 脏（如 focus 有变化）
    @Test
    public void shouldDirtyAfterFlushForFocusChange() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        router.registerFocusable(child);
        // 初始聚焦 root
        router.requestFocus(root);
        ReadableSignal<Boolean> childFocus = router.interactionState(child).focused();
        runtime.bind(childFocus, (focused) -> {
            child.setBackgroundColor(focused ? 0xFFFF0000 : 0xFF0000FF);
        });

        runtime.flush();
        Assert.assertEquals("初始 unfocused 背景色", 0xFF0000FF, child.getBackgroundColor());
        clearAllDirtyRecursive(root);

        // route 包含 Tab 事件，会触发 focusNext 切换焦点到 child
        frameBuilder.push(RawInputEvent.ofKey(SceneKey.TAB, SceneKeyAction.PRESSED,
                false, false, false, false,
                RawInputEvent.NATIVE_NONE, RawInputEvent.NATIVE_NONE, 1000L));
        SceneInputFrame frame = frameBuilder.drainFrame();
        runtime.route(root, frame, 0, 0);

        // flush 之前不应有脏
        Assert.assertFalse("flush 前 selfPaint 应为 false", child.__isSelfPaintDirty());

        // flush
        runtime.flush();

        Assert.assertTrue("flush 后 selfPaint 应为 true", child.__isSelfPaintDirty());
        Assert.assertEquals("flush 后 focused get 返回新值", Boolean.TRUE, childFocus.get());
        Assert.assertEquals("背景色应为 focusedColor", 0xFFFF0000, child.getBackgroundColor());
    }

    // ==================== H 回收/隔离 ====================

    // H1：mount 内 focusable 声明 → dispose 子 Owner → focusables 移除该 node
    @Test
    public void shouldRemoveFocusableOnOwnerDispose() {
        SceneNode root = new SceneNode();

        MountHandle handle = runtime.mount(root, () -> {
            SceneNode btn = new SceneNode();
            runtime.focusable(btn); // 在 Owner 作用域内注册
            return btn;
        });

        SceneNode btn = handle.getRoot();
        Assert.assertNotNull("挂载的节点不应为 null", btn);
        Assert.assertTrue("btn 应在 focusables 中", router.__isFocusable(btn));

        // dispose 子 Owner
        handle.dispose();

        Assert.assertFalse("dispose 后 btn 应从 focusables 中移除", router.__isFocusable(btn));
    }

    // H2：ScenePackageIsolationTest 仍零平台 import —— 在 ScenePackageIsolationTest 中验证

    // ==================== 综合场景 ====================

    // Z1：Tab 遍历 + 键盘分发 + focus signal 全流程
    @Test
    public void shouldIntegrateTabTraversalAndKeyDispatch() {
        SceneNode root = new SceneNode();
        SceneNode a = new SceneNode();
        SceneNode b = new SceneNode();
        root.appendChild(a);
        root.appendChild(b);

        router.registerFocusable(a);
        router.registerFocusable(b);

        ReadableSignal<Boolean> aFocus = router.interactionState(a).focused();
        ReadableSignal<Boolean> bFocus = router.interactionState(b).focused();

        List<String> log = new ArrayList<String>();
        router.on(a, SceneEventType.KEY_DOWN, (evt, ctx) -> log.add("a:" + evt.getKey()));
        router.on(b, SceneEventType.KEY_DOWN, (evt, ctx) -> log.add("b:" + evt.getKey()));

        // 初始无焦点 → Tab → 聚焦 a
        SceneInputFrame tabFrame = buildKeyFrame(SceneKey.TAB, SceneKeyAction.PRESSED, false, false);
        runtime.route(root, tabFrame, 0, 0);
        runtime.flush();
        Assert.assertSame("Tab 后焦点为 a", a, router.__getFocusedNode());
        Assert.assertEquals("a focused=true", Boolean.TRUE, aFocus.get());

        // 再 Tab → 聚焦 b
        runtime.route(root, buildKeyFrame(SceneKey.TAB, SceneKeyAction.PRESSED, false, false), 0, 0);
        runtime.flush();
        Assert.assertSame("Tab 后焦点为 b", b, router.__getFocusedNode());
        Assert.assertEquals("a focused=false", Boolean.FALSE, aFocus.get());
        Assert.assertEquals("b focused=true", Boolean.TRUE, bFocus.get());

        // Shift+Tab → 回到 a
        runtime.route(root, buildKeyFrame(SceneKey.TAB, SceneKeyAction.PRESSED, false, true), 0, 0);
        runtime.flush();
        Assert.assertSame("Shift+Tab 后焦点为 a", a, router.__getFocusedNode());
        Assert.assertEquals("a focused=true", Boolean.TRUE, aFocus.get());
        Assert.assertEquals("b focused=false", Boolean.FALSE, bFocus.get());

        // 投递 ENTER 给当前焦点 a
        // ★ 注意：此前第2次Tab和第3次Shift+Tab已在dispatch阶段被a/b的KEY_DOWN handler收到，
        // 因为Tab先dispatch后遍历是D2-A契约（handler可拦截Tab）。log实际序列：
        // ["a:TAB"(第2次Tab), "b:TAB"(第3次Shift+Tab), "a:ENTER"(本次)]
        runtime.route(root, buildKeyFrame(SceneKey.ENTER, SceneKeyAction.PRESSED, false, false), 0, 0);
        Assert.assertEquals("Tab+Shift+Tab+ENTER → log应含3条", 3, log.size());
        Assert.assertEquals("末项应为ENTER投给当前焦点a", "a:ENTER", log.get(log.size() - 1));
    }
}
