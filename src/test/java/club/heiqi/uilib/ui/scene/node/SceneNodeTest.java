package club.heiqi.uilib.ui.scene.node;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * SceneNode 双标记机制单元测试 —— Phase 0 叫停关口核验点。
 *
 * <p>本测试类的每条用例都是 I7 根除的"铁证"，验证脏标记只向上冒泡（O(深度)）、
 * 绝不向下递归（O(子树)）的核心设计。</p>
 */
public class SceneNodeTest {

    /**
     * 辅助方法：递归清除整棵树的所有脏标记。
     */
    private void flushAll(SceneNode node) {
        if (node == null) return;
        node.clearDirtyFlags();
        for (SceneNode child : node.__getChildren()) {
            flushAll(child);
        }
    }

    /**
     * 辅助方法：断言整棵树没有任何脏标记。
     */
    private void assertAllClean(SceneNode root) {
        Assert.assertFalse("root selfLayout", root.__isSelfLayoutDirty());
        Assert.assertFalse("root descendantLayout", root.__isDescendantLayoutDirty());
        Assert.assertFalse("root selfPaint", root.__isSelfPaintDirty());
        Assert.assertFalse("root descendantPaint", root.__isDescendantPaintDirty());
        Assert.assertFalse("root composite", root.__isCompositeDirty());
        for (SceneNode child : root.__getChildren()) {
            assertAllClean(child);
        }
    }

    @Before
    public void setUp() {
        // 每个测试前无需特殊初始化
    }

    // ==================== 测试：行距属性解析 ====================

    @Test
    public void shouldResolveExplicitLineHeight() {
        SceneNode node = new SceneNode();

        // 未设置：自动行高直通
        Assert.assertEquals(16, node.resolveLineHeight(16));

        // 倍数：ceil(16 × 1.5) = 24
        node.setLineHeightMultiplier(1.5D);
        Assert.assertEquals(24, node.resolveLineHeight(16));

        // 绝对行高（倍数清 0 后生效），可压缩
        node.setLineHeightMultiplier(0.0D);
        node.setLineHeightPx(12);
        Assert.assertEquals(12, node.resolveLineHeight(16));

        // 倍数优先于绝对行高
        node.setLineHeightMultiplier(2.0D);
        Assert.assertEquals(32, node.resolveLineHeight(16));
    }

    @Test
    public void shouldNormalizeNegativeLineHeightInputs() {
        SceneNode node = new SceneNode();

        node.setLineHeightPx(-5);
        node.setLineHeightMultiplier(-1.5D);

        Assert.assertEquals(0, node.getLineHeightPx());
        Assert.assertEquals(0.0D, node.getLineHeightMultiplier(), 0.001D);
        Assert.assertEquals(16, node.resolveLineHeight(16));
    }

    @Test
    public void shouldMarkLayoutAndPaintOnLineHeightChange() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        root.appendChild(child);
        flushAll(root);

        child.setLineHeightMultiplier(1.5D);

        Assert.assertTrue("child selfLayout", child.__isSelfLayoutDirty());
        Assert.assertTrue("child selfPaint", child.__isSelfPaintDirty());
        Assert.assertTrue("root descendantLayout", root.__isDescendantLayoutDirty());
        Assert.assertTrue("root descendantPaint", root.__isDescendantPaintDirty());
        Assert.assertFalse("root selfLayout", root.__isSelfLayoutDirty());
    }

    // ==================== 测试 1：属性变化只标自身和祖先路径（I7 根除铁证之一） ====================

    /**
     * 验证：对叶子节点设置文本后，只有该节点自身和祖先链被标记，
     * 兄弟节点及其子树完全不受影响。
     *
     * <p>树结构：
     * <pre>
     *       root
     *        └── containerA
     *             ├── child1
     *             │    └── grandchild
     *             └── child2  ← 对此节点 setText
     * </pre>
     */
    @Test
    public void shouldMarkOnlySelfAndAncestorPathOnPropertyChange() {
        // 构造树
        SceneNode root = new SceneNode();
        SceneNode containerA = new SceneNode();
        SceneNode child1 = new SceneNode();
        SceneNode grandchild = new SceneNode();
        SceneNode child2 = new SceneNode();

        root.appendChild(containerA);
        containerA.appendChild(child1);
        containerA.appendChild(child2);
        child1.appendChild(grandchild);

        // 清除初始构造产生的脏标记（appendChild 会标脏）
        flushAll(root);
        assertAllClean(root);

        // 对 child2 设置文本
        child2.setText("x");

        // === 断言 child2 自身 ===
        Assert.assertTrue("child2 自身应标 layout 脏", child2.__isSelfLayoutDirty());
        Assert.assertTrue("child2 setText 应标 paint 脏", child2.__isSelfPaintDirty());

        // === 断言祖先链路标 ===
        Assert.assertTrue("containerA 应点亮 descendantLayout 路标",
            containerA.__isDescendantLayoutDirty());
        Assert.assertTrue("root 应点亮 descendantLayout 路标",
            root.__isDescendantLayoutDirty());
        Assert.assertTrue("containerA 应点亮 descendantPaint 路标",
            containerA.__isDescendantPaintDirty());
        Assert.assertTrue("root 应点亮 descendantPaint 路标",
            root.__isDescendantPaintDirty());

        // === 断言祖先不自标 self ===
        Assert.assertFalse("containerA 自身不应标 selfLayout 脏",
            containerA.__isSelfLayoutDirty());
        Assert.assertFalse("root 自身不应标 selfLayout 脏",
            root.__isSelfLayoutDirty());

        // === 断言兄弟及其子树零标脏（I7 铁证） ===
        Assert.assertFalse("兄弟 child1 selfLayout 应为 false",
            child1.__isSelfLayoutDirty());
        Assert.assertFalse("兄弟 child1 descendantLayout 应为 false",
            child1.__isDescendantLayoutDirty());
        Assert.assertFalse("grandchild selfLayout 应为 false",
            grandchild.__isSelfLayoutDirty());
        Assert.assertFalse("grandchild descendantLayout 应为 false",
            grandchild.__isDescendantLayoutDirty());

        // paint 标记：setText 同时标 selfPaint → 冒泡 descendantPaint 到祖先
        Assert.assertTrue("root descendantPaint 应被设置", root.__isDescendantPaintDirty());
        Assert.assertTrue("containerA descendantPaint 应被设置",
            containerA.__isDescendantPaintDirty());
    }

    // ==================== 测试 2：applyChildReconcile 稳定兄弟零标脏（I7 根除铁证之二） ====================

    /**
     * 验证：容器的 applyChildReconcile 对稳定复用子节点零标脏。
     *
     * <p>这是与旧 DocumentNode.markSubtreeLayoutMutation 递归全标行为的正面翻转。
     * 旧模型中，任何容器增删都会导致全部后代被递归标脏。</p>
     */
    @Test
    public void shouldNotDirtyStableSiblingsOnReconcileInsert() {
        // 构造容器 + 3 个稳定子节点
        SceneNode container = new SceneNode();
        SceneNode child1 = new SceneNode();
        SceneNode child2 = new SceneNode();
        SceneNode child3 = new SceneNode();

        container.appendChild(child1);
        container.appendChild(child2);
        container.appendChild(child3);

        // 清除标记
        flushAll(container);
        assertAllClean(container);

        // 新建一个子节点
        SceneNode newChild = new SceneNode();
        Set<SceneNode> insertedOrMoved = new HashSet<>();
        insertedOrMoved.add(newChild);

        // 执行 reconcile：在 child1 后插入 newChild
        List<SceneNode> finalOrder = Arrays.asList(child1, newChild, child2, child3);
        container.applyChildReconcile(finalOrder, insertedOrMoved);

        // === 断言容器自身标脏 ===
        Assert.assertTrue("容器自身应标 selfLayout 脏", container.__isSelfLayoutDirty());

        // === 断言稳定兄弟零标脏（I7 铁证） ===
        Assert.assertFalse("稳定兄弟 child1 selfLayout 应为 false",
            child1.__isSelfLayoutDirty());
        Assert.assertFalse("稳定兄弟 child2 selfLayout 应为 false",
            child2.__isSelfLayoutDirty());
        Assert.assertFalse("稳定兄弟 child3 selfLayout 应为 false",
            child3.__isSelfLayoutDirty());

        // === 断言 children 顺序正确 ===
        List<SceneNode> children = container.__getChildren();
        Assert.assertEquals("子节点数量应为 4", 4, children.size());
        Assert.assertSame("第 0 位应为 child1", child1, children.get(0));
        Assert.assertSame("第 1 位应为 newChild", newChild, children.get(1));
        Assert.assertSame("第 2 位应为 child2", child2, children.get(2));
        Assert.assertSame("第 3 位应为 child3", child3, children.get(3));

        // === 断言 newChild 的 parent 正确 ===
        Assert.assertSame("newChild 的 parent 应为 container", container, newChild.__getParent());
    }

    // ==================== 测试 2b：reconcile 移除节点 ====================

    /**
     * 验证：reconcile 中移除的节点 parent 被正确置 null。
     */
    @Test
    public void shouldSetParentNullForRemovedNodesInReconcile() {
        SceneNode container = new SceneNode();
        SceneNode child1 = new SceneNode();
        SceneNode child2 = new SceneNode();
        container.appendChild(child1);
        container.appendChild(child2);
        flushAll(container);

        // 从 finalOrder 中移除 child2
        List<SceneNode> finalOrder = Collections.singletonList(child1);
        container.applyChildReconcile(finalOrder, Collections.<SceneNode>emptySet());

        Assert.assertSame("child1 parent 仍应为 container", container, child1.__getParent());
        Assert.assertNull("child2 parent 应为 null", child2.__getParent());
        Assert.assertEquals("子节点数应为 1", 1, container.__getChildren().size());
    }

    // ==================== 测试 3：冒泡在已点亮祖先处停止 ====================

    /**
     * 验证：脏标记冒泡在遇到已点亮路标的祖先时立即停止，
     * 防止 O(深度) 退化成重复上溯。
     */
    @Test
    public void shouldStopBubblingAtAlreadyMarkedAncestor() {
        // 构造深树：root → a → b → c → d（叶子）
        SceneNode root = new SceneNode();
        SceneNode a = new SceneNode();
        SceneNode b = new SceneNode();
        SceneNode c = new SceneNode();
        SceneNode d = new SceneNode();

        root.appendChild(a);
        a.appendChild(b);
        b.appendChild(c);
        c.appendChild(d);
        flushAll(root);

        // 先标 d：点亮 root→a→b→c 的 descendantLayout 路标
        d.markSelfLayout();

        Assert.assertTrue("d selfLayout", d.__isSelfLayoutDirty());
        Assert.assertTrue("c descendantLayout 应被点亮", c.__isDescendantLayoutDirty());
        Assert.assertTrue("b descendantLayout 应被点亮", b.__isDescendantLayoutDirty());
        Assert.assertTrue("a descendantLayout 应被点亮", a.__isDescendantLayoutDirty());
        Assert.assertTrue("root descendantLayout 应被点亮", root.__isDescendantLayoutDirty());

        // 清除 c 的 self（模拟 C 已经处理完自己），但路标保留
        // 注意：不能用 clearLayoutDirty()（它同时清 self + descendant），这里直接操作字段
        c.selfLayoutDirty = false;
        Assert.assertFalse("c selfLayout 已清", c.__isSelfLayoutDirty());
        Assert.assertTrue("c descendantLayout 路标仍在", c.__isDescendantLayoutDirty());

        // 现在标另一个节点 b（b 在 c 下方）
        // b.markSelfLayout() 应向上冒泡，但遇到 c.descendantLayoutDirty==true 立即停止
        b.markSelfLayout();

        // 验证：b 自身和祖先链状态正确（不会因重复上溯抛异常）
        Assert.assertTrue("b selfLayout 应为 true", b.__isSelfLayoutDirty());
        // a 和 root 的路标应仍然为 true
        Assert.assertTrue("a descendantLayout 仍为 true", a.__isDescendantLayoutDirty());
        Assert.assertTrue("root descendantLayout 仍为 true", root.__isDescendantLayoutDirty());

        // 兄弟和后代不应受影响
        Assert.assertFalse("a selfLayout 不应被标", a.__isSelfLayoutDirty());
    }

    // ==================== 测试 4：各 setter 打出正确的失效级别（I4） ====================

    /**
     * 验证：不同 setter 只打出对应级别的失效标记，不打多余级别。
     */
    @Test
    public void shouldPropagateCorrectInvalidationLevelPerSetter() {
        SceneNode node = new SceneNode();

        // --- setBackgroundColor → PAINT ---
        flushAll(node);
        node.setBackgroundColor(0xFFFF0000);
        Assert.assertTrue("setBackgroundColor 后应标 selfPaint",
            node.__isSelfPaintDirty());
        Assert.assertFalse("setBackgroundColor 不应标 selfLayout",
            node.__isSelfLayoutDirty());
        Assert.assertFalse("setBackgroundColor 不应标 composite",
            node.__isCompositeDirty());

        // --- setOpacity → COMPOSITE ---
        flushAll(node);
        node.setOpacity(0.5f);
        Assert.assertTrue("setOpacity 后应标 composite",
            node.__isCompositeDirty());
        Assert.assertFalse("setOpacity 不应标 selfLayout",
            node.__isSelfLayoutDirty());
        Assert.assertFalse("setOpacity 不应标 selfPaint",
            node.__isSelfPaintDirty());

        // --- setText → LAYOUT + PAINT ---
        flushAll(node);
        node.setText("hello");
        Assert.assertTrue("setText 后应标 selfLayout",
            node.__isSelfLayoutDirty());
        Assert.assertTrue("setText 后应标 selfPaint",
            node.__isSelfPaintDirty());
        Assert.assertFalse("setText 不应标 composite",
            node.__isCompositeDirty());
    }

    /**
     * 验证：setTransform 打出 composite 级失效。
     */
    @Test
    public void shouldMarkCompositeOnTransformChange() {
        SceneNode node = new SceneNode();
        flushAll(node);

        Transform t = new Transform(10, 20);
        node.setTransform(t);
        Assert.assertTrue("setTransform 后应标 composite", node.__isCompositeDirty());
        Assert.assertFalse("setTransform 不应标 selfLayout", node.__isSelfLayoutDirty());
        Assert.assertFalse("setTransform 不应标 selfPaint", node.__isSelfPaintDirty());
    }

    // ==================== 测试 5：同值不标脏（去重铁律） ====================

    /**
     * 验证：setter 在值不变时跳过标记，避免无谓标脏。
     */
    @Test
    public void shouldSkipMarkingWhenValueUnchanged() {
        SceneNode node = new SceneNode();

        // setBackgroundColor 同值
        node.setBackgroundColor(0xFFFF0000);
        flushAll(node);
        node.setBackgroundColor(0xFFFF0000); // 同值
        Assert.assertFalse("同值 setBackgroundColor 不应标脏", node.__isSelfPaintDirty());

        // setOpacity 同值（注意默认值是 1.0f）
        node.setOpacity(0.5f);
        flushAll(node);
        node.setOpacity(0.5f); // 同值
        Assert.assertFalse("同值 setOpacity 不应标脏", node.__isCompositeDirty());

        // setText 同值
        node.setText("hello");
        flushAll(node);
        node.setText("hello"); // 同值
        Assert.assertFalse("同值 setText 不应标脏", node.__isSelfLayoutDirty());

        // setTransform 同值
        Transform t = new Transform(5, 10);
        node.setTransform(t);
        flushAll(node);
        node.setTransform(t); // 同对象
        Assert.assertFalse("同值 setTransform 不应标脏", node.__isCompositeDirty());

        // setTransform 同值但不同对象
        node.setTransform(new Transform(5, 10)); // equals 返回 true
        Assert.assertFalse("同值不同对象 setTransform 不应标脏", node.__isCompositeDirty());
    }

    // ==================== 测试 6：树操作只标容器自身 ====================

    /**
     * 验证：appendChild 只标容器自身的 layout 脏，子节点自身不标脏。
     */
    @Test
    public void appendChildShouldOnlyMarkContainer() {
        SceneNode container = new SceneNode();
        flushAll(container);

        SceneNode child = new SceneNode();
        container.appendChild(child);

        Assert.assertTrue("容器 selfLayout 应标脏", container.__isSelfLayoutDirty());
        Assert.assertFalse("子节点 selfLayout 不应标脏", child.__isSelfLayoutDirty());
    }

    /**
     * 验证：removeChild 只标容器自身的 layout 脏。
     */
    @Test
    public void removeChildShouldOnlyMarkContainer() {
        SceneNode container = new SceneNode();
        SceneNode child = new SceneNode();
        container.appendChild(child);
        flushAll(container);

        container.removeChild(child);
        Assert.assertTrue("容器 selfLayout 应标脏", container.__isSelfLayoutDirty());
        Assert.assertFalse("被移除的子节点不应标脏", child.__isSelfLayoutDirty());
        Assert.assertNull("被移除的子节点 parent 应为 null", child.__getParent());
    }

    // ==================== 测试 7：冒泡链覆盖 ====================

    /**
     * 验证：paint 脏标记沿祖先链正确冒泡。
     */
    @Test
    public void shouldBubblePaintDirtyUpAncestorChain() {
        SceneNode root = new SceneNode();
        SceneNode a = new SceneNode();
        SceneNode b = new SceneNode();
        root.appendChild(a);
        a.appendChild(b);
        flushAll(root);

        b.setBackgroundColor(0xFF0000FF);

        Assert.assertTrue("b selfPaint", b.__isSelfPaintDirty());
        Assert.assertTrue("a descendantPaint", a.__isDescendantPaintDirty());
        Assert.assertTrue("root descendantPaint", root.__isDescendantPaintDirty());
        Assert.assertFalse("root selfPaint 不应被标", root.__isSelfPaintDirty());
    }

    /**
     * 验证（Phase 3A 解耦后）：composite 脏标记走独立的 descendantComposite 路标冒泡，
     * 不再借道 paint 路标，与 paint/layout/geometry 失效语义正交（守 I4）。
     */
    @Test
    public void shouldBubbleCompositeViaIndependentPathway() {
        SceneNode root = new SceneNode();
        SceneNode a = new SceneNode();
        root.appendChild(a);
        flushAll(root);

        a.setOpacity(0.3f);

        Assert.assertTrue("a composite", a.__isCompositeDirty());
        Assert.assertTrue("root descendantComposite 路标应被点亮", root.__isDescendantCompositeDirty());
        Assert.assertFalse("root descendantPaint 路标不应被污染（3A 解耦）", root.__isDescendantPaintDirty());
        Assert.assertFalse("root 自身不应标 selfPaint", root.__isSelfPaintDirty());
    }

    // ==================== 测试 8：markSelfLayout 去重自身 ====================

    /**
     * 验证：连续调用 markSelfLayout 不会重复冒泡（selfLayoutDirty 已是 true 时跳过）。
     */
    @Test
    public void shouldNotBubbleAgainWhenAlreadySelfLayoutDirty() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        root.appendChild(child);
        flushAll(root);

        // 第一次标脏：冒泡到 root
        child.markSelfLayout();
        Assert.assertTrue("child selfLayout", child.__isSelfLayoutDirty());
        Assert.assertTrue("root descendantLayout", root.__isDescendantLayoutDirty());

        // 清除 root 的路标，模拟某种中间状态
        root.descendantLayoutDirty = false;

        // 第二次标脏：child 自身已是 true，不应再次冒泡
        child.markSelfLayout();
        Assert.assertFalse("root descendantLayout 不应被再次点亮（因为 child 已标过，markSelfLayout 应跳过）",
            root.__isDescendantLayoutDirty());
    }

    // ==================== 测试 9：clearDirtyFlags 全清验证 ====================

    @Test
    public void shouldClearAllDirtyFlags() {
        SceneNode node = new SceneNode();
        node.setText("dirty");
        node.setBackgroundColor(0xFF000000);
        node.setOpacity(0.5f);
        // 此时应有 layout / paint / composite 三种标记

        node.clearDirtyFlags();

        Assert.assertFalse(node.__isSelfLayoutDirty());
        Assert.assertFalse(node.__isDescendantLayoutDirty());
        Assert.assertFalse(node.__isSelfPaintDirty());
        Assert.assertFalse(node.__isDescendantPaintDirty());
        Assert.assertFalse(node.__isCompositeDirty());
    }

    // ==================== 测试 10：复合 reconcile（增删移混合，Phase 2 主场景） ====================

    /**
     * 验证：同一次 reconcile 同时包含删除、插入、移动三种操作时，
     * 稳定项零标脏，容器自身标脏，被删节点 parent 置 null。
     *
     * <p>初始：容器 [a, b, c, d]
     * reconcile：finalOrder=[c, a, e]（删 b/d、移动 c 到首、保留 a、新增 e）
     * insertedOrMoved={c, e}</p>
     */
    @Test
    public void shouldHandleCompositeReconcileWithInsertRemoveAndMove() {
        SceneNode container = new SceneNode();
        SceneNode a = new SceneNode();
        SceneNode b = new SceneNode();
        SceneNode c = new SceneNode();
        SceneNode d = new SceneNode();

        container.appendChild(a);
        container.appendChild(b);
        container.appendChild(c);
        container.appendChild(d);
        flushAll(container);
        assertAllClean(container);

        SceneNode e = new SceneNode();
        Set<SceneNode> insertedOrMoved = new HashSet<>();
        insertedOrMoved.add(c);   // c 被移动
        insertedOrMoved.add(e);   // e 新增

        List<SceneNode> finalOrder = Arrays.asList(c, a, e);
        container.applyChildReconcile(finalOrder, insertedOrMoved);

        // ① 容器 children 顺序正确
        List<SceneNode> children = container.__getChildren();
        Assert.assertEquals("子节点数应为 3", 3, children.size());
        Assert.assertSame("[0] 应为 c", c, children.get(0));
        Assert.assertSame("[1] 应为 a", a, children.get(1));
        Assert.assertSame("[2] 应为 e", e, children.get(2));

        // ② 被删的 b/d parent==null
        Assert.assertNull("b parent 应为 null", b.__getParent());
        Assert.assertNull("d parent 应为 null", d.__getParent());

        // ③ 稳定项 a（既不在 insertedOrMoved 中，也不是被删或被移动）零标脏 —— I7 铁证
        Assert.assertFalse("稳定项 a selfLayout 应为 false", a.__isSelfLayoutDirty());
        Assert.assertFalse("稳定项 a descendantLayout 应为 false", a.__isDescendantLayoutDirty());

        // ④ 容器自身标脏
        Assert.assertTrue("容器 selfLayout 应为 true", container.__isSelfLayoutDirty());

        // ⑤ c/a/e 的 parent 都指向容器
        Assert.assertSame("c parent", container, c.__getParent());
        Assert.assertSame("a parent", container, a.__getParent());
        Assert.assertSame("e parent", container, e.__getParent());
    }

    // ==================== 测试 11：appendChild 迁移时标记旧父脏（修复 1 回归） ====================

    /**
     * 验证：appendChild 将节点从旧父迁移到新父时，旧父的子节点集合变了，
     * 必须调用旧父的 markSelfLayout() 标脏，避免旧父布局缓存陈旧导致显示残留。
     */
    @Test
    public void appendChildShouldMarkOldParentDirtyOnReparent() {
        SceneNode parentA = new SceneNode();
        SceneNode parentB = new SceneNode();
        SceneNode child = new SceneNode();

        // child 先挂到 parentA
        parentA.appendChild(child);
        flushAll(parentA);
        flushAll(parentB);
        Assert.assertTrue("初始 parentA children 含 child",
            parentA.__getChildren().contains(child));

        // 迁移 child 到 parentB
        parentB.appendChild(child);

        // parentA（旧父）应被标脏
        Assert.assertTrue("旧父 parentA selfLayout 应为 true", parentA.__isSelfLayoutDirty());
        // parentB（新父）应被标脏
        Assert.assertTrue("新父 parentB selfLayout 应为 true", parentB.__isSelfLayoutDirty());
        // child.parent 指向新父
        Assert.assertSame("child parent 应为 parentB", parentB, child.__getParent());
        // parentA 的 children 不含 child
        Assert.assertFalse("parentA children 不应含 child",
            parentA.__getChildren().contains(child));
        // parentB 的 children 含 child
        Assert.assertTrue("parentB children 应含 child",
            parentB.__getChildren().contains(child));
    }

    /**
     * 验证：insertBefore 将节点从旧父迁移到新父时，旧父被正确标脏。
     */
    @Test
    public void insertBeforeShouldMarkOldParentDirtyOnReparent() {
        SceneNode parentA = new SceneNode();
        SceneNode parentB = new SceneNode();
        SceneNode child = new SceneNode();
        SceneNode anchor = new SceneNode();

        // child 先挂到 parentA
        parentA.appendChild(child);
        parentB.appendChild(anchor);
        flushAll(parentA);
        flushAll(parentB);
        Assert.assertTrue("初始 parentA children 含 child",
            parentA.__getChildren().contains(child));

        // 迁移 child 到 parentB，插在 anchor 之前
        parentB.insertBefore(child, anchor);

        // parentA（旧父）应被标脏
        Assert.assertTrue("旧父 parentA selfLayout 应为 true", parentA.__isSelfLayoutDirty());
        // parentB（新父）应被标脏
        Assert.assertTrue("新父 parentB selfLayout 应为 true", parentB.__isSelfLayoutDirty());
        // child.parent 指向新父
        Assert.assertSame("child parent 应为 parentB", parentB, child.__getParent());
        // parentA 的 children 不含 child
        Assert.assertFalse("parentA children 不应含 child",
            parentA.__getChildren().contains(child));
        // parentB 的 children 含 child，且 child 在 anchor 之前
        List<SceneNode> pbChildren = parentB.__getChildren();
        Assert.assertTrue("parentB children 应含 child", pbChildren.contains(child));
        Assert.assertTrue("child 应在 anchor 之前",
            pbChildren.indexOf(child) < pbChildren.indexOf(anchor));
    }

    // ==================== Bug 修复回归测试 ====================

    /**
     * Bug② 回归：setText 必须同时标 LAYOUT + PAINT 脏。
     * 文本既影响布局盒尺寸，又影响绘制输出字符串。
     */
    @Test
    public void setTextShouldMarkBothLayoutAndPaintDirty() {
        SceneNode node = new SceneNode();
        node.setText("hello");
        // 首次设置初始值后清脏
        flushAll(node);
        assertAllClean(node);

        node.setText("world");
        Assert.assertTrue("setText 后 selfLayout 应为 true", node.__isSelfLayoutDirty());
        Assert.assertTrue("setText 后 selfPaint 应为 true", node.__isSelfPaintDirty());
        Assert.assertFalse("setText 不应标 composite", node.__isCompositeDirty());
    }

    /**
     * setPreferredHeight 属性槽 setter/getter 基本行为。
     */
    @Test
    public void setPreferredHeightShouldUpdateValueAndMarkLayoutDirty() {
        SceneNode node = new SceneNode();
        Assert.assertEquals("默认 preferredHeight=0", 0, node.getPreferredHeight());

        node.setPreferredHeight(30);
        Assert.assertEquals("设置后 preferredHeight=30", 30, node.getPreferredHeight());
        Assert.assertTrue("setPreferredHeight 应标 selfLayout", node.__isSelfLayoutDirty());
        Assert.assertFalse("setPreferredHeight 不应标 selfPaint", node.__isSelfPaintDirty());
    }

    /**
     * setPreferredHeight 值去重：相同值不重复标脏。
     */
    @Test
    public void setPreferredHeightSameValueShouldNotMarkDirty() {
        SceneNode node = new SceneNode();
        node.setPreferredHeight(30);
        flushAll(node);
        assertAllClean(node);

        node.setPreferredHeight(30); // 相同值
        Assert.assertFalse("相同值不应标 selfLayout", node.__isSelfLayoutDirty());
    }

    // ==================== Phase 4 任务 0：新增布局/绘制属性槽失效级别断言 ====================

    /**
     * 验证：setTextColor 只标 PAINT，绝不标 LAYOUT。
     * 文本颜色变化不改文字尺寸，故只触发绘制失效。
     */
    @Test
    public void setTextColorShouldMarkOnlyPaintNotLayout() {
        SceneNode node = new SceneNode();
        flushAll(node);

        node.setTextColor(0xFFFF0000);
        Assert.assertTrue("setTextColor 后应标 selfPaint", node.__isSelfPaintDirty());
        Assert.assertFalse("setTextColor 绝不应标 selfLayout", node.__isSelfLayoutDirty());
        Assert.assertFalse("setTextColor 不应标 composite", node.__isCompositeDirty());
    }

    /**
     * 验证：setTextColor 值去重——同值再设不重复标脏。
     */
    @Test
    public void setTextColorSameValueShouldNotMarkDirty() {
        SceneNode node = new SceneNode();
        node.setTextColor(0xFFFF0000);
        flushAll(node);
        assertAllClean(node);

        node.setTextColor(0xFFFF0000); // 同值
        Assert.assertFalse("同值 setTextColor 不应标 selfPaint", node.__isSelfPaintDirty());
    }

    /**
     * 验证：setPadding 标 LAYOUT（内边距改变盒模型可用空间）。
     */
    @Test
    public void setPaddingShouldMarkLayout() {
        SceneNode node = new SceneNode();
        flushAll(node);

        node.setPadding(4, 8, 4, 8);
        Assert.assertTrue("setPadding 后应标 selfLayout", node.__isSelfLayoutDirty());
        Assert.assertFalse("setPadding 不应标 selfPaint", node.__isSelfPaintDirty());
        Assert.assertFalse("setPadding 不应标 composite", node.__isCompositeDirty());
        Assert.assertEquals("paddingTop=4", 4, node.getPaddingTop());
        Assert.assertEquals("paddingRight=8", 8, node.getPaddingRight());
        Assert.assertEquals("paddingBottom=4", 4, node.getPaddingBottom());
        Assert.assertEquals("paddingLeft=8", 8, node.getPaddingLeft());
    }

    /**
     * 验证：setPadding 值去重——四边全相等时不重复标脏。
     */
    @Test
    public void setPaddingSameValueShouldNotMarkDirty() {
        SceneNode node = new SceneNode();
        node.setPadding(4, 8, 4, 8);
        flushAll(node);
        assertAllClean(node);

        node.setPadding(4, 8, 4, 8); // 同值
        Assert.assertFalse("同值 setPadding 不应标 selfLayout", node.__isSelfLayoutDirty());
    }

    /**
     * 验证：setPadding(int all) 便捷重载四边统一赋值。
     */
    @Test
    public void setPaddingAllShouldSetFourSidesEqually() {
        SceneNode node = new SceneNode();
        node.setPadding(6);
        Assert.assertEquals("paddingTop=6", 6, node.getPaddingTop());
        Assert.assertEquals("paddingRight=6", 6, node.getPaddingRight());
        Assert.assertEquals("paddingBottom=6", 6, node.getPaddingBottom());
        Assert.assertEquals("paddingLeft=6", 6, node.getPaddingLeft());
        Assert.assertTrue("setPadding(all) 后应标 selfLayout", node.__isSelfLayoutDirty());
    }

    /**
     * 验证：setCornerRadius 只标 PAINT，绝不标 LAYOUT（圆角不改盒模型尺寸）。
     */
    @Test
    public void setCornerRadiusShouldMarkOnlyPaintNotLayout() {
        SceneNode node = new SceneNode();
        flushAll(node);

        node.setCornerRadius(8);
        Assert.assertTrue("setCornerRadius 后应标 selfPaint", node.__isSelfPaintDirty());
        Assert.assertFalse("setCornerRadius 绝不应标 selfLayout", node.__isSelfLayoutDirty());
        Assert.assertFalse("setCornerRadius 不应标 composite", node.__isCompositeDirty());
        Assert.assertEquals("cornerRadius=8", 8, node.getCornerRadius());
    }

    /**
     * 验证：setCornerRadius 值去重。
     */
    @Test
    public void setCornerRadiusSameValueShouldNotMarkDirty() {
        SceneNode node = new SceneNode();
        node.setCornerRadius(8);
        flushAll(node);
        assertAllClean(node);

        node.setCornerRadius(8); // 同值
        Assert.assertFalse("同值 setCornerRadius 不应标 selfPaint", node.__isSelfPaintDirty());
    }

    /**
     * 验证：setBorderWidth 只标 PAINT（第 0 段裁决：边框不占布局空间）。
     */
    @Test
    public void setBorderWidthShouldMarkOnlyPaintNotLayout() {
        SceneNode node = new SceneNode();
        flushAll(node);

        node.setBorderWidth(2);
        Assert.assertTrue("setBorderWidth 后应标 selfPaint", node.__isSelfPaintDirty());
        Assert.assertFalse("setBorderWidth 绝不应标 selfLayout", node.__isSelfLayoutDirty());
    }

    /**
     * 验证：setBorderColor 只标 PAINT。
     */
    @Test
    public void setBorderColorShouldMarkOnlyPaint() {
        SceneNode node = new SceneNode();
        flushAll(node);

        node.setBorderColor(0xFF00FF00);
        Assert.assertTrue("setBorderColor 后应标 selfPaint", node.__isSelfPaintDirty());
        Assert.assertFalse("setBorderColor 不应标 selfLayout", node.__isSelfLayoutDirty());
    }

    /**
     * 验证：setClipChildren 只标 PAINT。
     */
    @Test
    public void setClipChildrenShouldMarkOnlyPaint() {
        SceneNode node = new SceneNode();
        flushAll(node);

        node.setClipChildren(true);
        Assert.assertTrue("setClipChildren 后应标 selfPaint", node.__isSelfPaintDirty());
        Assert.assertFalse("setClipChildren 不应标 selfLayout", node.__isSelfLayoutDirty());
        Assert.assertTrue("clipChildren=true", node.isClipChildren());
    }

    /**
     * 验证：setFlexDirection 标 LAYOUT，且默认值为 COLUMN（零回归）。
     */
    @Test
    public void setFlexDirectionShouldMarkLayout() {
        SceneNode node = new SceneNode();
        Assert.assertEquals("默认 flexDirection=COLUMN",
            club.heiqi.uilib.ui.scene.layout.FlexDirection.COLUMN, node.getFlexDirection());
        flushAll(node);

        node.setFlexDirection(club.heiqi.uilib.ui.scene.layout.FlexDirection.ROW);
        Assert.assertTrue("setFlexDirection 后应标 selfLayout", node.__isSelfLayoutDirty());
        Assert.assertFalse("setFlexDirection 不应标 selfPaint", node.__isSelfPaintDirty());
    }

    /**
     * 验证：setGap 标 LAYOUT，并支持值去重。
     */
    @Test
    public void setGapShouldMarkLayoutAndDedup() {
        SceneNode node = new SceneNode();
        Assert.assertEquals("默认 gap=0", 0, node.getGap());
        flushAll(node);

        node.setGap(10);
        Assert.assertTrue("setGap 后应标 selfLayout", node.__isSelfLayoutDirty());
        Assert.assertFalse("setGap 不应标 selfPaint", node.__isSelfPaintDirty());

        flushAll(node);
        node.setGap(10); // 同值
        Assert.assertFalse("同值 setGap 不应标 selfLayout", node.__isSelfLayoutDirty());
    }

    /**
     * 验证：setMainAxisAlign 标 LAYOUT，默认值为 START（零回归）。
     */
    @Test
    public void setMainAxisAlignShouldMarkLayout() {
        SceneNode node = new SceneNode();
        Assert.assertEquals("默认 mainAxisAlign=START",
            club.heiqi.uilib.ui.scene.layout.MainAxisAlign.START, node.getMainAxisAlign());
        flushAll(node);

        node.setMainAxisAlign(club.heiqi.uilib.ui.scene.layout.MainAxisAlign.CENTER);
        Assert.assertTrue("setMainAxisAlign 后应标 selfLayout", node.__isSelfLayoutDirty());
        Assert.assertFalse("setMainAxisAlign 不应标 selfPaint", node.__isSelfPaintDirty());
    }

    /**
     * 验证：setCrossAxisAlign 标 LAYOUT，默认值为 STRETCH（零回归：子节点宽度填满父宽）。
     */
    @Test
    public void setCrossAxisAlignShouldMarkLayout() {
        SceneNode node = new SceneNode();
        Assert.assertEquals("默认 crossAxisAlign=STRETCH",
            club.heiqi.uilib.ui.scene.layout.CrossAxisAlign.STRETCH, node.getCrossAxisAlign());
        flushAll(node);

        node.setCrossAxisAlign(club.heiqi.uilib.ui.scene.layout.CrossAxisAlign.CENTER);
        Assert.assertTrue("setCrossAxisAlign 后应标 selfLayout", node.__isSelfLayoutDirty());
        Assert.assertFalse("setCrossAxisAlign 不应标 selfPaint", node.__isSelfPaintDirty());
    }

    /**
     * 验证：setHitTestable 不标任何脏标记（与 setCursor 同为纯交互投影例外）。
     *
     * <p>hitTestable 只影响 hit-test 命中候选，绝不影响 layout/paint/composite
     * 任何渲染阶段。setHitTestable 内部不走任何 markXxx，也不点亮祖先路标。
     * 另验证默认值 true + 值去重。</p>
     */
    @Test
    public void setHitTestableDoesNotMarkDirty() {
        SceneNode node = new SceneNode();
        // 默认值 true（零行为漂移）
        Assert.assertTrue("默认 hitTestable=true", node.isHitTestable());
        flushAll(node);
        assertAllClean(node);

        // 设为 false：值变化但绝不标任何脏
        node.setHitTestable(false);
        Assert.assertFalse("setHitTestable 后值应为 false", node.isHitTestable());
        Assert.assertFalse("setHitTestable 不应标 selfLayout", node.__isSelfLayoutDirty());
        Assert.assertFalse("setHitTestable 不应标 descendantLayout", node.__isDescendantLayoutDirty());
        Assert.assertFalse("setHitTestable 不应标 selfPaint", node.__isSelfPaintDirty());
        Assert.assertFalse("setHitTestable 不应标 descendantPaint", node.__isDescendantPaintDirty());
        Assert.assertFalse("setHitTestable 不应标 composite", node.__isCompositeDirty());
        Assert.assertFalse("setHitTestable 不应标 descendantComposite", node.__isDescendantCompositeDirty());
        Assert.assertFalse("setHitTestable 不应标 selfGeometry", node.__isSelfGeometryDirty());
        Assert.assertFalse("setHitTestable 不应标 descendantGeometry", node.__isDescendantGeometryDirty());

        // 值去重：同值再设不变更
        node.setHitTestable(false); // 同值
        Assert.assertFalse("同值 setHitTestable 仍为 false", node.isHitTestable());
    }

    // ==================== isClipWindow 谓词（B3/I7 口径统一） ====================

    /**
     * isClipWindow() = isClipChildren() || isScrollable()，供 paint 与 hit-test 共用，
     * 消除两处独立判断的口径分裂温床。
     */
    @Test
    public void isClipWindowPredicateSemantics() {
        SceneNode none = new SceneNode();
        Assert.assertFalse("默认非裁剪窗口", none.isClipWindow());

        SceneNode clipOnly = new SceneNode();
        clipOnly.setClipChildren(true);
        Assert.assertTrue("clipChildren=true 是裁剪窗口", clipOnly.isClipWindow());

        SceneNode scrollOnly = new SceneNode();
        scrollOnly.setScrollable(true);
        Assert.assertTrue("scrollable=true 是裁剪窗口", scrollOnly.isClipWindow());

        SceneNode both = new SceneNode();
        both.setClipChildren(true);
        both.setScrollable(true);
        Assert.assertTrue("clipChildren+scrollable 是裁剪窗口", both.isClipWindow());
    }
}
