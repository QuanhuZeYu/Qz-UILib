package club.heiqi.uilib.ui.scene.layout;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 子树节点数缓存（{@code cachedSubtreeNodeCount}）增量维护单元测试。
 *
 * <p>验证阶段 2 第一批步骤 2.1 的核心契约：</p>
 * <ul>
 *   <li>新建节点 count=1（叶子语义）。</li>
 *   <li>layout 后序遍历顺带重算，父节点 count=1+子 count 之和。</li>
 *   <li>结构变化入口（appendChild/removeChild/insertBefore/applyChildReconcile）
 *       冒泡 {@code subtreeCountDirty}，layout 后清 false。</li>
 *   <li>非结构变化（属性 setter）不触碰 count 标记，干净帧零开销（守 I7）。</li>
 *   <li>兄弟子树 count 独立隔离，互不影响。</li>
 * </ul>
 *
 * <p>装配复用 {@link SceneLayoutEngineTest} 模式：{@link FixedTextMeasurer} +
 * {@link SceneLayoutEngine} + {@link Constraints}。</p>
 */
public class SubtreeNodeCountTest {

    private final FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
    private final SceneLayoutEngine engine = new SceneLayoutEngine(measurer);

    // ============================================================
    // 用例 1：单节点 count=1
    // ============================================================

    /**
     * 新建叶子节点未 layout 时 count=1，subtreeCountDirty=false（初始正确）。
     */
    @Test
    public void singleLeafCountIsOne() {
        SceneNode leaf = new SceneNode();
        Assert.assertEquals("新建叶子 count=1", 1, leaf.__getCachedSubtreeNodeCount());
        Assert.assertFalse("新建叶子 subtreeCountDirty=false", leaf.__isSubtreeCountDirty());
    }

    // ============================================================
    // 用例 2：父子树 count 正确
    // ============================================================

    /**
     * root + 3 子，layout 后 root.count=4（含自身）。
     */
    @Test
    public void parentWithThreeChildrenCountIsFour() {
        SceneNode root = new SceneNode();
        SceneNode a = new SceneNode();
        SceneNode b = new SceneNode();
        SceneNode c = new SceneNode();
        root.appendChild(a);
        root.appendChild(b);
        root.appendChild(c);

        engine.layout(root, new Constraints(200));

        Assert.assertEquals("root count=4（含自身+3子）", 4, root.__getCachedSubtreeNodeCount());
        Assert.assertEquals("a count=1", 1, a.__getCachedSubtreeNodeCount());
        Assert.assertEquals("b count=1", 1, b.__getCachedSubtreeNodeCount());
        Assert.assertEquals("c count=1", 1, c.__getCachedSubtreeNodeCount());
    }

    // ============================================================
    // 用例 3：深层树 count 正确
    // ============================================================

    /**
     * root→child→grandchild 三层，layout 后 root.count=3。
     */
    @Test
    public void deepChainCountIsThree() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        SceneNode grandchild = new SceneNode();
        child.appendChild(grandchild);
        root.appendChild(child);

        engine.layout(root, new Constraints(200));

        Assert.assertEquals("grandchild count=1", 1, grandchild.__getCachedSubtreeNodeCount());
        Assert.assertEquals("child count=2（含自身+grandchild）", 2, child.__getCachedSubtreeNodeCount());
        Assert.assertEquals("root count=3（含自身+child 子树）", 3, root.__getCachedSubtreeNodeCount());
    }

    // ============================================================
    // 用例 4：appendChild 后父链 count 更新
    // ============================================================

    /**
     * 先 layout 使 count 稳定，再 appendChild 一个新子，再次 layout 后
     * root.count 增加 1，且 subtreeCountDirty 在 layout 后清 false。
     */
    @Test
    public void appendChildUpdatesParentChainCount() {
        SceneNode root = new SceneNode();
        SceneNode a = new SceneNode();
        root.appendChild(a);
        engine.layout(root, new Constraints(200));
        Assert.assertEquals("初次 layout root.count=2", 2, root.__getCachedSubtreeNodeCount());

        // 结构变化：appendChild 新子
        SceneNode b = new SceneNode();
        root.appendChild(b);
        // 结构变化后、layout 前：root subtreeCountDirty=true（冒泡点亮）
        Assert.assertTrue("appendChild 后 root subtreeCountDirty=true",
                root.__isSubtreeCountDirty());

        engine.layout(root, new Constraints(200));

        Assert.assertEquals("append 后 root.count=3", 3, root.__getCachedSubtreeNodeCount());
        Assert.assertFalse("layout 后 root subtreeCountDirty=false",
                root.__isSubtreeCountDirty());
    }

    // ============================================================
    // 用例 5：removeChild 后父链 count 更新
    // ============================================================

    /**
     * 先 layout 使 count 稳定，再 removeChild，再次 layout 后 root.count 减少 1。
     */
    @Test
    public void removeChildUpdatesParentChainCount() {
        SceneNode root = new SceneNode();
        SceneNode a = new SceneNode();
        SceneNode b = new SceneNode();
        root.appendChild(a);
        root.appendChild(b);
        engine.layout(root, new Constraints(200));
        Assert.assertEquals("初次 layout root.count=3", 3, root.__getCachedSubtreeNodeCount());

        root.removeChild(b);
        Assert.assertTrue("removeChild 后 root subtreeCountDirty=true",
                root.__isSubtreeCountDirty());

        engine.layout(root, new Constraints(200));

        Assert.assertEquals("remove 后 root.count=2", 2, root.__getCachedSubtreeNodeCount());
        Assert.assertFalse("layout 后 root subtreeCountDirty=false",
                root.__isSubtreeCountDirty());
    }

    // ============================================================
    // 用例 6：干净帧 count 不重算
    // ============================================================

    /**
     * 两次 layout 间无结构变化，第二次 layout 后 count 不变且 subtreeCountDirty 保持 false。
     * 验证非结构变化（属性 setter）不触碰 count 标记，守 I7 干净帧零开销。
     */
    @Test
    public void cleanFrameDoesNotRecomputeCount() {
        SceneNode root = new SceneNode();
        SceneNode a = new SceneNode();
        a.setText("A");
        root.appendChild(a);
        engine.layout(root, new Constraints(200));
        int countAfterFirst = root.__getCachedSubtreeNodeCount();
        Assert.assertEquals("初次 layout root.count=2", 2, countAfterFirst);
        Assert.assertFalse("初次 layout 后 root dirty=false", root.__isSubtreeCountDirty());

        // 非结构变化：只改属性（setText），不触碰 count 标记
        a.setText("AA");
        Assert.assertFalse("setText 后 root subtreeCountDirty 仍 false（非结构变化）",
                root.__isSubtreeCountDirty());

        engine.layout(root, new Constraints(200));

        Assert.assertEquals("第二次 layout root.count 不变", countAfterFirst,
                root.__getCachedSubtreeNodeCount());
        Assert.assertFalse("第二次 layout 后 root dirty 仍 false",
                root.__isSubtreeCountDirty());
    }

    // ============================================================
    // 用例 7：结构变化后 subtreeCountDirty 冒泡祖先链
    // ============================================================

    /**
     * 三层树 root→mid→leaf，对 mid appendChild 新子后，
     * mid 与 root 的 subtreeCountDirty 均点亮（冒泡），leaf 不受影响。
     * layout 后全部清 false。
     */
    @Test
    public void structuralChangeBubblesSubtreeCountDirty() {
        SceneNode root = new SceneNode();
        SceneNode mid = new SceneNode();
        SceneNode leaf = new SceneNode();
        mid.appendChild(leaf);
        root.appendChild(mid);
        engine.layout(root, new Constraints(200));
        Assert.assertFalse("初次 layout 后 root dirty=false", root.__isSubtreeCountDirty());
        Assert.assertFalse("初次 layout 后 mid dirty=false", mid.__isSubtreeCountDirty());

        // 结构变化：对 mid appendChild
        SceneNode newChild = new SceneNode();
        mid.appendChild(newChild);

        // 冒泡：mid 与 root 点亮，leaf 不受影响（leaf 是被加节点的兄弟，非祖先）
        Assert.assertTrue("mid subtreeCountDirty=true", mid.__isSubtreeCountDirty());
        Assert.assertTrue("root subtreeCountDirty=true（冒泡）", root.__isSubtreeCountDirty());
        Assert.assertFalse("leaf subtreeCountDirty 不受影响", leaf.__isSubtreeCountDirty());

        engine.layout(root, new Constraints(200));

        Assert.assertFalse("layout 后 mid dirty=false", mid.__isSubtreeCountDirty());
        Assert.assertFalse("layout 后 root dirty=false", root.__isSubtreeCountDirty());
        // count 正确：mid=3（mid+leaf+newChild），root=4（root+mid 子树）
        Assert.assertEquals("mid count=3", 3, mid.__getCachedSubtreeNodeCount());
        Assert.assertEquals("root count=4", 4, root.__getCachedSubtreeNodeCount());
    }

    // ============================================================
    // 用例 8：独立子树隔离
    // ============================================================

    /**
     * root 有两个子容器 A、B，A 有 2 子，B 有 3 子。
     * layout 后 A.count=3, B.count=4, root.count=8。
     * 对 A appendChild 1 子，再次 layout 后 A.count=4, B.count=4 不变, root.count=9。
     * 验证兄弟子树 count 独立，互不影响。
     */
    @Test
    public void siblingSubtreesAreIsolated() {
        SceneNode root = new SceneNode();
        SceneNode a = new SceneNode();
        SceneNode b = new SceneNode();
        root.appendChild(a);
        root.appendChild(b);

        SceneNode a1 = new SceneNode();
        SceneNode a2 = new SceneNode();
        a.appendChild(a1);
        a.appendChild(a2);

        SceneNode b1 = new SceneNode();
        SceneNode b2 = new SceneNode();
        SceneNode b3 = new SceneNode();
        b.appendChild(b1);
        b.appendChild(b2);
        b.appendChild(b3);

        engine.layout(root, new Constraints(200));

        Assert.assertEquals("A count=3（A+a1+a2）", 3, a.__getCachedSubtreeNodeCount());
        Assert.assertEquals("B count=4（B+b1+b2+b3）", 4, b.__getCachedSubtreeNodeCount());
        Assert.assertEquals("root count=8（root+A 子树3+B 子树4）", 8, root.__getCachedSubtreeNodeCount());

        // 对 A 加 1 子，B 不受影响
        SceneNode a3 = new SceneNode();
        a.appendChild(a3);
        // B 的 subtreeCountDirty 不应点亮（冒泡只沿 A 的祖先链：a→root，不碰 b）
        Assert.assertFalse("B subtreeCountDirty 不受 A 结构变化影响",
                b.__isSubtreeCountDirty());
        Assert.assertTrue("A subtreeCountDirty=true", a.__isSubtreeCountDirty());
        Assert.assertTrue("root subtreeCountDirty=true（冒泡）", root.__isSubtreeCountDirty());

        engine.layout(root, new Constraints(200));

        Assert.assertEquals("A count=4（加 a3）", 4, a.__getCachedSubtreeNodeCount());
        Assert.assertEquals("B count=4 不变", 4, b.__getCachedSubtreeNodeCount());
        Assert.assertEquals("root count=9（root+A 子树4+B 子树4）", 9, root.__getCachedSubtreeNodeCount());
    }

    // ============================================================
    // 用例 9：insertBefore 后 count 更新（含跨容器旧父）
    // ============================================================

    /**
     * root 有子 A，另一容器 otherParent 有子 B。
     * 先 layout 使双方 count 稳定（root.count=2, otherParent.count=2）。
     * 调 root.insertBefore(B, A) 把 B 从 otherParent 移到 root 的 A 之前，
     * 再次 layout 后 root.count=3（root+A+B），otherParent.count=1（仅自身，B 已迁出）。
     * 验证 insertBefore 冒泡 subtreeCountDirty，且旧父 count 同步更新。
     */
    @Test
    public void insertBeforeUpdatesCountOnNewAndOldParent() {
        SceneNode root = new SceneNode();
        SceneNode a = new SceneNode();
        root.appendChild(a);

        SceneNode otherParent = new SceneNode();
        SceneNode b = new SceneNode();
        otherParent.appendChild(b);

        engine.layout(root, new Constraints(200));
        engine.layout(otherParent, new Constraints(200));
        Assert.assertEquals("初次 layout root.count=2", 2, root.__getCachedSubtreeNodeCount());
        Assert.assertEquals("初次 layout otherParent.count=2", 2,
                otherParent.__getCachedSubtreeNodeCount());

        // 跨容器插入：B 从 otherParent 移到 root，插在 A 之前
        root.insertBefore(b, a);
        Assert.assertTrue("insertBefore 后 root subtreeCountDirty=true",
                root.__isSubtreeCountDirty());
        Assert.assertTrue("insertBefore 后 otherParent subtreeCountDirty=true（旧父）",
                otherParent.__isSubtreeCountDirty());

        engine.layout(root, new Constraints(200));
        engine.layout(otherParent, new Constraints(200));

        Assert.assertEquals("insertBefore 后 root.count=3（root+A+B）", 3,
                root.__getCachedSubtreeNodeCount());
        Assert.assertEquals("insertBefore 后 otherParent.count=1（B 已迁出）", 1,
                otherParent.__getCachedSubtreeNodeCount());
        Assert.assertFalse("layout 后 root dirty=false", root.__isSubtreeCountDirty());
        Assert.assertFalse("layout 后 otherParent dirty=false",
                otherParent.__isSubtreeCountDirty());
    }

    // ============================================================
    // 用例 10：applyChildReconcile 后 count 反映实际节点数
    // ============================================================

    /**
     * root 有子 [A, B]，layout 后 root.count=3。
     * 调 applyChildReconcile([A, C], {C})：移除 B、加入 C，最终子序列为 [A, C]。
     * 再次 layout 后 root.count=3（root+A+C），确认 count 反映 finalOrder 实际节点数
     * 而非旧值（旧值也是 3，但成员已换；同时验证 B 被摘除后 parent=null、count=1）。
     */
    @Test
    public void applyChildReconcileUpdatesCountToFinalOrder() {
        SceneNode root = new SceneNode();
        SceneNode a = new SceneNode();
        SceneNode b = new SceneNode();
        root.appendChild(a);
        root.appendChild(b);

        engine.layout(root, new Constraints(200));
        Assert.assertEquals("初次 layout root.count=3", 3, root.__getCachedSubtreeNodeCount());

        // 批量结构变更：替换为 [A, C]，移除 B 加入 C
        SceneNode c = new SceneNode();
        List<SceneNode> finalOrder = Arrays.asList(a, c);
        Set<SceneNode> insertedOrMoved = new HashSet<>(Collections.singletonList(c));
        root.applyChildReconcile(finalOrder, insertedOrMoved);

        Assert.assertTrue("applyChildReconcile 后 root subtreeCountDirty=true",
                root.__isSubtreeCountDirty());
        // B 被摘除，parent 应置 null
        Assert.assertNull("B 被摘除后 parent=null", b.__getParent());

        engine.layout(root, new Constraints(200));

        Assert.assertEquals("reconcile 后 root.count=3（root+A+C，反映 finalOrder）", 3,
                root.__getCachedSubtreeNodeCount());
        Assert.assertEquals("A count=1", 1, a.__getCachedSubtreeNodeCount());
        Assert.assertEquals("C count=1", 1, c.__getCachedSubtreeNodeCount());
        Assert.assertEquals("B 被摘除后 count=1（仅自身）", 1, b.__getCachedSubtreeNodeCount());
        Assert.assertFalse("layout 后 root dirty=false", root.__isSubtreeCountDirty());
    }
}
