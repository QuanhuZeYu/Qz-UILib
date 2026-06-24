package club.heiqi.uilib.ui.scene.component;

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
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * Phase 2 三声明式基石（bindText / forEach / show）验收测试 —— Phase 2 终审判据。
 *
 * <p>核心是坐实 I7（稳定项零重算）：列表结构变化时，未变的稳定项既不进入 layout 引擎的
 * 重算集合（{@code __getRelayoutedNodes}），其 {@code cachedLayout} 引用也保持不变。</p>
 *
 * <h3>flush 时机约定</h3>
 * <p>{@link Signal#set} 不即时生效（帧末批处理），每次断言前必须 {@code runtime.flush()}。
 * 与 {@link SceneRuntimeTest} 同范式。</p>
 *
 * <h3>layout 探针语义（单帧）</h3>
 * <p>{@link SceneLayoutEngine#__getRelayoutCount()} 与 {@link SceneLayoutEngine#__getRelayoutedNodes()}
 * 是「最近一帧」语义：每次 {@code layout()} 调用刷新（见 SceneLayoutEngineTest「第二次 layout 重算次数=0」）。
 * 因此 C 组先 layout 一帧达稳态，再结构变化后 layout 一帧，探针只反映增量。</p>
 *
 * <h3>I7 断言不在约束变化帧做</h3>
 * <p>root 约束变化会 {@code markSelfLayout(root)} 污染重算集合，故 C 组全程保持 Constraints 不变跨帧。</p>
 */
public class SceneDeclarativePrimitivesTest {

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

    // ==================== A. bindText 行为 ====================

    /**
     * A1：验证 bindText 初值生效——signal 初值经 flush 后写入 node.getText()。
     */
    @Test
    public void bindTextShouldApplyInitialValue() {
        SceneNode node = new SceneNode();
        Signal<String> textSignal = Signal.create("hello");

        runtime.bindText(node, textSignal);
        runtime.flush();

        Assert.assertEquals("初值应写入 text", "hello", node.getText());
    }

    /**
     * A2：验证 bindText 响应更新——signal.set 新值经 flush 后 text 更新。
     */
    @Test
    public void bindTextShouldUpdateOnSet() {
        SceneNode node = new SceneNode();
        Signal<String> textSignal = Signal.create("hello");

        runtime.bindText(node, textSignal);
        runtime.flush();
        Assert.assertEquals("初值应为 hello", "hello", node.getText());

        textSignal.set("world");
        runtime.flush();
        Assert.assertEquals("set 后 text 应更新为 world", "world", node.getText());
    }

    /**
     * A3：验证 bindText 的 null 跳过语义——signal.set(null) 不以 null 覆盖既有文本。
     */
    @Test
    public void bindTextShouldSkipNullValue() {
        SceneNode node = new SceneNode();
        Signal<String> textSignal = Signal.create("keep");

        runtime.bindText(node, textSignal);
        runtime.flush();
        Assert.assertEquals("初值应为 keep", "keep", node.getText());

        // set(null)：bindText 内部 v != null 守卫应跳过 setText，旧值保持
        textSignal.set(null);
        runtime.flush();
        Assert.assertEquals("set(null) 后 text 应保持旧值 keep", "keep", node.getText());
    }

    // ==================== B. forEach 基础行为 ====================

    /**
     * B1：验证初始列表 [a,b,c] flush 后 container 有 3 个子节点，顺序与 key 对应。
     */
    @Test
    public void forEachShouldRenderInitialListInOrder() {
        SceneNode container = new SceneNode();
        Signal<List<String>> items = Signal.create(listOf("a", "b", "c"));

        // itemComponent 把 key 写入节点 text，便于按顺序断言
        runtime.forEach(container, items, k -> k, item -> {
            SceneNode n = new SceneNode();
            n.setText(item);
            return n;
        });
        runtime.flush();

        List<SceneNode> children = container.__getChildren();
        Assert.assertEquals("应有 3 个子节点", 3, children.size());
        Assert.assertEquals("第 1 项为 a", "a", children.get(0).getText());
        Assert.assertEquals("第 2 项为 b", "b", children.get(1).getText());
        Assert.assertEquals("第 3 项为 c", "c", children.get(2).getText());
    }

    /**
     * B2：验证追加一项 [a,b,c,d] flush 后 4 个子节点，前 3 个是同一对象引用（复用未重建）。
     */
    @Test
    public void forEachShouldReuseExistingNodesOnAppend() {
        SceneNode container = new SceneNode();
        Signal<List<String>> items = Signal.create(listOf("a", "b", "c"));

        runtime.forEach(container, items, k -> k, item -> {
            SceneNode n = new SceneNode();
            n.setText(item);
            return n;
        });
        runtime.flush();

        // 记录前 3 项的对象引用
        SceneNode a1 = container.__getChildren().get(0);
        SceneNode b1 = container.__getChildren().get(1);
        SceneNode c1 = container.__getChildren().get(2);

        // 追加 d
        items.set(listOf("a", "b", "c", "d"));
        runtime.flush();

        List<SceneNode> children = container.__getChildren();
        Assert.assertEquals("追加后应有 4 个子节点", 4, children.size());
        // 前 3 项必须是同一对象引用（复用，未重建）
        Assert.assertSame("a 应复用同一节点", a1, children.get(0));
        Assert.assertSame("b 应复用同一节点", b1, children.get(1));
        Assert.assertSame("c 应复用同一节点", c1, children.get(2));
        Assert.assertEquals("新项 d 文本正确", "d", children.get(3).getText());
    }

    /**
     * B3：验证删除中间项 [a,c] flush 后 2 个子节点，且被删项 b 的 item 作用域已回收
     * （通过 item 内 bind 的 effect 在删除后不再响应 signal 变化来验证）。
     */
    @Test
    public void forEachShouldDisposeRemovedItemScope() {
        SceneNode container = new SceneNode();
        Signal<List<String>> items = Signal.create(listOf("a", "b", "c"));

        // b 项内部 bind 一个独立 signal 到 text；删除 b 后该 effect 应退订，
        // 再 set bSignal 不应改变 b 节点 text（即作用域已回收）。
        Signal<String> bSignal = Signal.create("b0");
        SceneNode[] bNodeHolder = new SceneNode[1];

        runtime.forEach(container, items, k -> k, item -> {
            SceneNode n = new SceneNode();
            if ("b".equals(item)) {
                bNodeHolder[0] = n;
                runtime.bindText(n, bSignal); // 归属 b 的 item 作用域
            } else {
                n.setText(item);
            }
            return n;
        });
        runtime.flush();
        Assert.assertEquals("b 项初始 text 应为 b0", "b0", bNodeHolder[0].getText());

        // 删除中间项 b → [a, c]
        items.set(listOf("a", "c"));
        runtime.flush();

        List<SceneNode> children = container.__getChildren();
        Assert.assertEquals("删除后应有 2 个子节点", 2, children.size());
        Assert.assertEquals("第 1 项 a", "a", children.get(0).getText());
        Assert.assertEquals("第 2 项 c", "c", children.get(1).getText());

        // 核心：b 的 item 作用域已 dispose，其内部 effect 退订——
        // 再变更 bSignal 并 flush，b 节点 text 不应更新（仍为 b0）。
        bSignal.set("b-after-removal");
        runtime.flush();
        Assert.assertEquals("被删项作用域已回收，effect 不再响应", "b0", bNodeHolder[0].getText());
    }

    /**
     * B4：验证重排 [c,a,b] flush 后子节点顺序变为 c,a,b，且都是原对象引用（移动非重建）。
     */
    @Test
    public void forEachShouldReorderWithoutRebuild() {
        SceneNode container = new SceneNode();
        Signal<List<String>> items = Signal.create(listOf("a", "b", "c"));

        runtime.forEach(container, items, k -> k, item -> {
            SceneNode n = new SceneNode();
            n.setText(item);
            return n;
        });
        runtime.flush();

        SceneNode a1 = container.__getChildren().get(0);
        SceneNode b1 = container.__getChildren().get(1);
        SceneNode c1 = container.__getChildren().get(2);

        // 重排为 [c, a, b]
        items.set(listOf("c", "a", "b"));
        runtime.flush();

        List<SceneNode> children = container.__getChildren();
        Assert.assertEquals("重排后仍 3 个子节点", 3, children.size());
        // 顺序变为 c, a, b
        Assert.assertEquals("第 1 项为 c", "c", children.get(0).getText());
        Assert.assertEquals("第 2 项为 a", "a", children.get(1).getText());
        Assert.assertEquals("第 3 项为 b", "b", children.get(2).getText());
        // 都是原对象引用（移动，非重建）
        Assert.assertSame("c 为原对象", c1, children.get(0));
        Assert.assertSame("a 为原对象", a1, children.get(1));
        Assert.assertSame("b 为原对象", b1, children.get(2));
    }

    /**
     * B5：验证重复 key 抛 IllegalStateException。
     */
    @Test
    public void forEachShouldThrowOnDuplicateKey() {
        SceneNode container = new SceneNode();
        // 两项 key 相同（keyFn 直接返回字符串本身）
        Signal<List<String>> items = Signal.create(listOf("dup", "dup"));

        runtime.forEach(container, items, k -> k, item -> {
            SceneNode n = new SceneNode();
            n.setText(item);
            return n;
        });

        // reconcile 在 flush 时触发，重复 key 应抛 IllegalStateException
        try {
            runtime.flush();
            Assert.fail("重复 key 应抛 IllegalStateException");
        } catch (IllegalStateException e) {
            Assert.assertTrue("异常信息应提及重复 key",
                    e.getMessage() != null && e.getMessage().contains("key"));
        }
    }

    // ==================== C. forEach 的 I7 终审（最关键） ====================

    /**
     * C1：I7 终审——末尾插入一项后，稳定项 a/b/c 零重算且 cachedLayout 引用不变。
     *
     * <p>步骤：
     * <ol>
     *   <li>建列表 [a,b,c]，flush，layout 一帧达稳态（cachedLayout 就位、脏标记清）；</li>
     *   <li>末尾插入 d，flush，<b>用同一约束</b>再 layout 一帧（探针只反映增量）；</li>
     *   <li>断言 __getRelayoutedNodes() 不含 a/b/c；a/b/c 的 cachedLayout 引用 assertSame。</li>
     * </ol>
     * 全程 Constraints 不变（避免约束变化 markSelfLayout(root) 污染重算集合）。
     * container 因结构变化被重排、新项 d 首次 layout 均允许。</p>
     */
    @Test
    public void forEachStableItemsShouldNotRelayoutOnAppend() {
        SceneNode root = new SceneNode();
        SceneNode container = new SceneNode();
        root.appendChild(container);

        Signal<List<String>> items = Signal.create(listOf("a", "b", "c"));
        runtime.forEach(container, items, k -> k, item -> {
            SceneNode n = new SceneNode();
            n.setText(item);
            return n;
        });
        runtime.flush();

        // 同一 layout 引擎实例跨帧复用，探针才反映增量
        SceneLayoutEngine engine = new SceneLayoutEngine(new FixedTextMeasurer());
        Constraints constraints = new Constraints(200);

        // 第一帧：layout 到稳态
        engine.layout(root, constraints);

        // 记录稳定项 a/b/c 的节点引用与 cachedLayout 引用
        SceneNode nodeA = container.__getChildren().get(0);
        SceneNode nodeB = container.__getChildren().get(1);
        SceneNode nodeC = container.__getChildren().get(2);
        Object boxA1 = nodeA.getCachedLayout();
        Object boxB1 = nodeB.getCachedLayout();
        Object boxC1 = nodeC.getCachedLayout();
        Assert.assertNotNull("a 应有 cachedLayout", boxA1);
        Assert.assertNotNull("b 应有 cachedLayout", boxB1);
        Assert.assertNotNull("c 应有 cachedLayout", boxC1);

        // 结构变化：末尾插入 d
        items.set(listOf("a", "b", "c", "d"));
        runtime.flush();

        // 第二帧：同一约束再 layout（探针刷新为本帧增量）
        engine.layout(root, constraints);

        // === I7 铁证 1：稳定项不进入重算集合 ===
        Assert.assertFalse("稳定项 a 不应重算", engine.__getRelayoutedNodes().contains(nodeA));
        Assert.assertFalse("稳定项 b 不应重算", engine.__getRelayoutedNodes().contains(nodeB));
        Assert.assertFalse("稳定项 c 不应重算", engine.__getRelayoutedNodes().contains(nodeC));

        // === I7 铁证 2：稳定项 cachedLayout 引用不变（复用，未重算） ===
        Assert.assertSame("a 的 cachedLayout 应复用", boxA1, nodeA.getCachedLayout());
        Assert.assertSame("b 的 cachedLayout 应复用", boxB1, nodeB.getCachedLayout());
        Assert.assertSame("c 的 cachedLayout 应复用", boxC1, nodeC.getCachedLayout());

        // 旁证：新项 d 已挂载且有布局结果（允许首次 layout）
        SceneNode nodeD = container.__getChildren().get(3);
        Assert.assertEquals("第 4 项为 d", "d", nodeD.getText());
        Assert.assertNotNull("新项 d 应有 cachedLayout", nodeD.getCachedLayout());
    }

    /**
     * C2（加强）：中间插入一项时，插入点之前的稳定项零重算、cachedLayout 引用不变。
     *
     * <p>列表 [a,b,c] → 在 b 后插入 x 得 [a,b,x,c]。a、b 在插入点之前，应零重算。
     * c 位置后移，其几何可能变化（允许重算），不对 c 断言零重算。</p>
     */
    @Test
    public void forEachStableItemsBeforeInsertPointShouldNotRelayout() {
        SceneNode root = new SceneNode();
        SceneNode container = new SceneNode();
        root.appendChild(container);

        Signal<List<String>> items = Signal.create(listOf("a", "b", "c"));
        runtime.forEach(container, items, k -> k, item -> {
            SceneNode n = new SceneNode();
            n.setText(item);
            return n;
        });
        runtime.flush();

        SceneLayoutEngine engine = new SceneLayoutEngine(new FixedTextMeasurer());
        Constraints constraints = new Constraints(200);
        engine.layout(root, constraints);

        SceneNode nodeA = container.__getChildren().get(0);
        SceneNode nodeB = container.__getChildren().get(1);
        Object boxA1 = nodeA.getCachedLayout();
        Object boxB1 = nodeB.getCachedLayout();

        // 在 b 后插入 x → [a, b, x, c]
        items.set(listOf("a", "b", "x", "c"));
        runtime.flush();
        engine.layout(root, constraints);

        // 插入点之前的 a、b 零重算、cachedLayout 引用不变
        Assert.assertFalse("插入点前 a 不应重算", engine.__getRelayoutedNodes().contains(nodeA));
        Assert.assertFalse("插入点前 b 不应重算", engine.__getRelayoutedNodes().contains(nodeB));
        Assert.assertSame("a 的 cachedLayout 应复用", boxA1, nodeA.getCachedLayout());
        Assert.assertSame("b 的 cachedLayout 应复用", boxB1, nodeB.getCachedLayout());

        // 旁证：插入顺序正确
        Assert.assertEquals("第 3 项为 x", "x", container.__getChildren().get(2).getText());
    }

    // ==================== D. show 行为 ====================

    /**
     * D1：验证 condition=true 时 content 节点挂到 parent（在 anchor 之前）。
     */
    @Test
    public void showShouldMountContentWhenTrue() {
        SceneNode parent = new SceneNode();
        Signal<Boolean> cond = Signal.create(true);
        SceneNode[] contentHolder = new SceneNode[1];

        runtime.show(parent, cond, () -> {
            SceneNode c = new SceneNode();
            c.setText("content");
            contentHolder[0] = c;
            return c;
        });
        runtime.flush();

        // parent 应包含 content 节点（anchor 也在，故至少含 content）
        Assert.assertTrue("parent 应包含 content 节点",
                parent.__getChildren().contains(contentHolder[0]));
        // content 在 anchor 之前：content 的索引应小于末尾 anchor 的索引
        List<SceneNode> children = parent.__getChildren();
        int contentIdx = children.indexOf(contentHolder[0]);
        Assert.assertTrue("content 应在 anchor 之前（非末位）", contentIdx < children.size() - 1);
    }

    /**
     * D2：验证 condition.set(false) 时 content 卸载（parent 不再含 content），content effect 回收。
     */
    @Test
    public void showShouldUnmountContentWhenFalse() {
        SceneNode parent = new SceneNode();
        Signal<Boolean> cond = Signal.create(true);
        // content 内部 bind 一个 signal，卸载后该 effect 应退订
        Signal<String> innerSignal = Signal.create("v0");
        SceneNode[] contentHolder = new SceneNode[1];

        runtime.show(parent, cond, () -> {
            SceneNode c = new SceneNode();
            contentHolder[0] = c;
            runtime.bindText(c, innerSignal); // 归属 content 作用域
            return c;
        });
        runtime.flush();
        Assert.assertTrue("初始应挂载 content",
                parent.__getChildren().contains(contentHolder[0]));
        Assert.assertEquals("content 初始 text 为 v0", "v0", contentHolder[0].getText());

        // 切换为 false → 卸载
        cond.set(false);
        runtime.flush();
        Assert.assertFalse("卸载后 parent 不应含 content 节点",
                parent.__getChildren().contains(contentHolder[0]));

        // content 的 effect 应已回收：再变更 innerSignal 不改变已卸载节点 text
        innerSignal.set("v1");
        runtime.flush();
        Assert.assertEquals("卸载后 content effect 不再响应", "v0", contentHolder[0].getText());
    }

    /**
     * D3：验证 condition 连续两次 true（true→set(true)）时 content 是同一对象引用（未重建，守 I7）。
     */
    @Test
    public void showShouldNotRebuildContentOnConsecutiveTrue() {
        SceneNode parent = new SceneNode();
        Signal<Boolean> cond = Signal.create(true);
        AtomicInteger buildCount = new AtomicInteger(0);

        runtime.show(parent, cond, () -> {
            buildCount.incrementAndGet();
            SceneNode c = new SceneNode();
            c.setText("content");
            return c;
        });
        runtime.flush();
        Assert.assertEquals("首次应构建一次", 1, buildCount.get());

        // 记录首次挂载的 content 节点
        SceneNode contentBefore = parent.__getChildren().get(0);

        // 再次 set(true)：条件未跨真假边界，已挂载不应重建（守 I7）
        cond.set(true);
        runtime.flush();

        Assert.assertEquals("连续 true 不应重新构建", 1, buildCount.get());
        Assert.assertSame("content 应是同一对象引用（未重建）",
                contentBefore, parent.__getChildren().get(0));
    }

    /**
     * D4：验证 show 的 parent 含其它兄弟节点时，切换 condition 不影响兄弟（anchor 方案不误删兄弟）。
     */
    @Test
    public void showShouldNotAffectSiblingsWhenToggling() {
        SceneNode parent = new SceneNode();
        // 预置兄弟节点（不归 show 管）
        SceneNode siblingBefore = new SceneNode();
        siblingBefore.setText("before");
        SceneNode siblingAfter = new SceneNode();
        siblingAfter.setText("after");
        parent.appendChild(siblingBefore);

        Signal<Boolean> cond = Signal.create(true);
        runtime.show(parent, cond, () -> {
            SceneNode c = new SceneNode();
            c.setText("content");
            return c;
        });
        // show 的 anchor 已 append 到 parent；再加一个后置兄弟
        parent.appendChild(siblingAfter);
        runtime.flush();

        // 兄弟节点应始终在场
        Assert.assertTrue("前置兄弟应在场", parent.__getChildren().contains(siblingBefore));
        Assert.assertTrue("后置兄弟应在场", parent.__getChildren().contains(siblingAfter));

        // 切到 false：content 卸载，兄弟不受影响
        cond.set(false);
        runtime.flush();
        Assert.assertTrue("卸载后前置兄弟仍在场", parent.__getChildren().contains(siblingBefore));
        Assert.assertTrue("卸载后后置兄弟仍在场", parent.__getChildren().contains(siblingAfter));

        // 切回 true：content 重新挂载，兄弟仍不受影响
        cond.set(true);
        runtime.flush();
        Assert.assertTrue("重新挂载后前置兄弟仍在场", parent.__getChildren().contains(siblingBefore));
        Assert.assertTrue("重新挂载后后置兄弟仍在场", parent.__getChildren().contains(siblingAfter));
    }

    /**
     * D5：验证 SceneShowHandle.dispose() 后 anchor 与 content 都从 parent 摘除。
     */
    @Test
    public void showHandleDisposeShouldRemoveAnchorAndContent() {
        SceneNode parent = new SceneNode();
        Signal<Boolean> cond = Signal.create(true);
        SceneNode[] contentHolder = new SceneNode[1];

        SceneShowHandle handle = runtime.show(parent, cond, () -> {
            SceneNode c = new SceneNode();
            c.setText("content");
            contentHolder[0] = c;
            return c;
        });
        runtime.flush();
        // 挂载后 parent 含 content + anchor 共 2 个子节点
        Assert.assertEquals("挂载后 parent 应有 content + anchor 两个子节点",
                2, parent.__getChildren().size());

        // dispose：content 由内容作用域 onCleanup 摘除，anchor 由 condOwner onCleanup 摘除
        handle.dispose();
        Assert.assertTrue("dispose 后 handle 应标记已卸载", handle.isDisposed());
        Assert.assertFalse("dispose 后 parent 不应含 content 节点",
                parent.__getChildren().contains(contentHolder[0]));
        Assert.assertEquals("dispose 后 parent 应无任何子节点（anchor + content 均摘除）",
                0, parent.__getChildren().size());
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造一个可变 ArrayList（保证每次 set 传入独立列表实例，模拟真实数据替换）。
     *
     * @param values 列表元素
     * @return 含给定元素的 ArrayList
     */
    private static List<String> listOf(String... values) {
        return new ArrayList<>(Arrays.asList(values));
    }
}
