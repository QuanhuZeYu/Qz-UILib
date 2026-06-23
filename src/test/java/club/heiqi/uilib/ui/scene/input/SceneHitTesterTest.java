package club.heiqi.uilib.ui.scene.input;

import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * SceneHitTester 命中测试单元测试。
 *
 * <p>覆盖：命中链结构、坐标语义、子节点优先、cachedLayout=null 跳过、
 * 无命中返回空、绝对坐标临时累加不回写。</p>
 */
public class SceneHitTesterTest {

    private SceneHitTester tester;

    @Before
    public void setUp() {
        tester = new SceneHitTester();
    }

    // ===== T1：单节点命中 =====

    /**
     * 单节点树，指针在 bounds 内返回 [node]。
     */
    @Test
    public void shouldHitSingleNode() {
        SceneNode root = new SceneNode();
        root.setCachedLayout(new LayoutBox(0, 0, 100, 80));

        List<SceneNode> chain = tester.hitTest(root, 50, 40, 0, 0);
        Assert.assertEquals("命中链长度", 1, chain.size());
        Assert.assertSame("命中节点为 root", root, chain.get(0));
    }

    // ===== T2：未命中返回空 =====

    @Test
    public void shouldReturnEmptyOnMiss() {
        SceneNode root = new SceneNode();
        root.setCachedLayout(new LayoutBox(0, 0, 100, 80));

        List<SceneNode> chain = tester.hitTest(root, 150, 40, 0, 0);
        Assert.assertTrue("指针在 bounds 外应返回空", chain.isEmpty());
    }

    // ===== T3：父子命中链 =====

    /**
     * 父子两层树，指针命中子节点，返回 [root, child]。
     * 注意：appendChild 会调用 markSelfLayout() 将父节点 cachedLayout 置 null，
     * 因此必须在所有树操作完成后统一设置 LayoutBox。
     */
    @Test
    public void shouldReturnRootToTargetChain() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        root.appendChild(child);

        // 树构造完成后设置 LayoutBox（避免 appendChild 的 markSelfLayout 清掉缓存）
        root.setCachedLayout(new LayoutBox(0, 0, 100, 100));
        child.setCachedLayout(new LayoutBox(10, 10, 50, 50));

        // 指针在 child 区域内：20, 20 (绝对 = rootAbs + 20 = 20)
        List<SceneNode> chain = tester.hitTest(root, 20, 20, 0, 0);
        Assert.assertEquals("命中链长度", 2, chain.size());
        Assert.assertSame("索引 0 为 root", root, chain.get(0));
        Assert.assertSame("索引 1 为 child", child, chain.get(1));
    }

    // ===== T4：cachedLayout=null 跳过 =====

    @Test
    public void shouldSkipNodeWithNullLayout() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        root.appendChild(child);

        // root 有 layout，child 故意不设
        root.setCachedLayout(new LayoutBox(0, 0, 100, 100));
        // child cachedLayout 保持 null

        // 指针在 child 应有区域但 child 无 layout
        List<SceneNode> chain = tester.hitTest(root, 20, 20, 0, 0);
        Assert.assertEquals("child 无 layout 时应命中 root", 1, chain.size());
        Assert.assertSame("命中 root", root, chain.get(0));
    }

    // ===== T5：rootAbsX/Y 平移 =====

    /**
     * rootAbsX=100, rootAbsY=50，root 自身 layout(0,0,100,80)。
     * root 绝对位置 = (100,50)。指针 (150,70) = root 绝对 (100+50, 50+20) = 在 bounds 内。
     */
    @Test
    public void shouldAccountForRootAbsOffset() {
        SceneNode root = new SceneNode();
        root.setCachedLayout(new LayoutBox(0, 0, 100, 80));

        // root 绝对区域 [100, 200) × [50, 130)，指针 (150, 70) 命中
        List<SceneNode> chain = tester.hitTest(root, 150, 70, 100, 50);
        Assert.assertEquals("命中 chain 长度", 1, chain.size());
        Assert.assertSame("命中 root", root, chain.get(0));

        // 指针在 root 外 (99, 49 在 root 绝对区域的左/上)
        List<SceneNode> missChain = tester.hitTest(root, 99, 49, 100, 50);
        Assert.assertTrue("应在 bounds 外", missChain.isEmpty());
    }

    // ===== T6：子节点优先 z-order（后添加优先） =====

    @Test
    public void shouldPrioritizeLaterChildren() {
        SceneNode root = new SceneNode();
        SceneNode child1 = new SceneNode();
        SceneNode child2 = new SceneNode();
        root.appendChild(child1);
        root.appendChild(child2);

        // 树构造完成后设置 LayoutBox
        root.setCachedLayout(new LayoutBox(0, 0, 200, 200));
        // child1 先添加，占据 10-60
        child1.setCachedLayout(new LayoutBox(10, 10, 50, 50));
        // child2 后添加（z-order 更高），占据 20-70（与 child1 重叠）
        child2.setCachedLayout(new LayoutBox(20, 20, 50, 50));

        // 指针在重叠区域 (30, 30)，应命中后添加的 child2
        List<SceneNode> chain = tester.hitTest(root, 30, 30, 0, 0);
        Assert.assertEquals("命中链长度", 2, chain.size());
        Assert.assertSame("索引 0 root", root, chain.get(0));
        Assert.assertSame("索引 1 应为后添加的 child2", child2, chain.get(1));
    }

    // ===== T7：左闭右开边界 =====

    @Test
    public void shouldUseLeftClosedRightOpenBounds() {
        SceneNode root = new SceneNode();
        root.setCachedLayout(new LayoutBox(0, 0, 100, 80));

        // 左上角 (0,0) 命中
        Assert.assertFalse("左上角应命中",
                tester.hitTest(root, 0, 0, 0, 0).isEmpty());

        // 右下边界 (99, 79) 命中（右开，100 不命中）
        Assert.assertFalse("右下边界内应命中",
                tester.hitTest(root, 99, 79, 0, 0).isEmpty());

        // 右边界外 (100, 0) 不命中
        Assert.assertTrue("右边界外不应命中",
                tester.hitTest(root, 100, 0, 0, 0).isEmpty());

        // 下边界外 (0, 80) 不命中
        Assert.assertTrue("下边界外不应命中",
                tester.hitTest(root, 0, 80, 0, 0).isEmpty());
    }

    // ===== T8：深层嵌套命中 =====

    @Test
    public void shouldHandleDeepNesting() {
        SceneNode root = new SceneNode();
        SceneNode a = new SceneNode();
        SceneNode b = new SceneNode();
        SceneNode c = new SceneNode();

        root.appendChild(a);
        a.appendChild(b);
        b.appendChild(c);

        // 树构造完成后统一设置 LayoutBox
        root.setCachedLayout(new LayoutBox(0, 0, 300, 300));
        a.setCachedLayout(new LayoutBox(10, 10, 200, 200));
        b.setCachedLayout(new LayoutBox(10, 10, 100, 100));
        c.setCachedLayout(new LayoutBox(5, 5, 40, 40));

        // 指针在 c 区域：rootAbs + a(10,10) + b(10,10) + c(5,5) = (25,25)
        List<SceneNode> chain = tester.hitTest(root, 35, 35, 0, 0);
        Assert.assertEquals("深度 4 命中链", 4, chain.size());
        Assert.assertSame(root, chain.get(0));
        Assert.assertSame(a, chain.get(1));
        Assert.assertSame(b, chain.get(2));
        Assert.assertSame(c, chain.get(3));
    }

    // ===== T9：hitTestable=false 叶节点命中穿透（方案 B 偏离 2 修复） =====

    /**
     * child 是叶节点且 setHitTestable(false)：命中 child 区域时命中穿透到 parent。
     * 断言命中链末尾是 parent 不是 child（pointer-events:none 语义）。
     */
    @Test
    public void skipsNodeWithHitTestableFalse() {
        SceneNode parent = new SceneNode();
        SceneNode child = new SceneNode();
        parent.appendChild(child);

        // 树构造完成后设置 LayoutBox（避免 appendChild 的 markSelfLayout 清缓存）
        parent.setCachedLayout(new LayoutBox(0, 0, 100, 100));
        child.setCachedLayout(new LayoutBox(10, 10, 50, 50));

        // child 命中透明：装饰子节点退出叶命中候选
        child.setHitTestable(false);

        // 指针落在 child 区域 (20,20)：child 退出候选，命中穿透到 parent
        List<SceneNode> chain = tester.hitTest(parent, 20, 20, 0, 0);
        Assert.assertEquals("命中穿透后链长度为 1（仅 parent）", 1, chain.size());
        Assert.assertSame("命中目标穿透到 parent 而非 child", parent, chain.get(0));
        Assert.assertFalse("命中链不应含 hitTestable=false 的叶 child", chain.contains(child));
    }

    // ===== T10：hitTestable=false 中间节点仍作为锚点出现在链中 =====

    /**
     * parent→mid→leaf 三层，mid.setHitTestable(false) 但有子节点 leaf 命中：
     * 命中 leaf 区域时，mid 虽退出「叶命中目标」候选，但子节点 leaf 命中后
     * mid 仍作为坐标锚点出现在命中链中间（已拍板语义：仅剔除叶命中资格，不剔除链路径）。
     */
    @Test
    public void hitTestableFalseMidNodeStillInChainWhenChildHit() {
        SceneNode parent = new SceneNode();
        SceneNode mid = new SceneNode();
        SceneNode leaf = new SceneNode();
        parent.appendChild(mid);
        mid.appendChild(leaf);

        parent.setCachedLayout(new LayoutBox(0, 0, 200, 200));
        mid.setCachedLayout(new LayoutBox(10, 10, 100, 100));
        leaf.setCachedLayout(new LayoutBox(10, 10, 40, 40));

        // mid 命中透明，但它仍是 leaf 的坐标锚点
        mid.setHitTestable(false);

        // 指针落在 leaf 区域：parentAbs + mid(10,10) + leaf(10,10) = (20,20)
        List<SceneNode> chain = tester.hitTest(parent, 30, 30, 0, 0);
        Assert.assertEquals("命中链深度 3（parent→mid→leaf）", 3, chain.size());
        Assert.assertSame("索引 0 为 parent", parent, chain.get(0));
        Assert.assertSame("索引 1 为 mid（hitTestable=false 仍在链中间作锚点）", mid, chain.get(1));
        Assert.assertSame("索引 2 为 leaf（实际命中目标）", leaf, chain.get(2));
        Assert.assertTrue("mid 必须出现在命中链中（中间锚点语义）", chain.contains(mid));
    }

    // ===== T11：默认 hitTestable=true 零回归 =====

    /**
     * 不显式设置 hitTestable 的节点（默认 true）正常命中，与现状完全一致。
     */
    @Test
    public void defaultHitTestableTrueZeroRegression() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        root.appendChild(child);

        root.setCachedLayout(new LayoutBox(0, 0, 100, 100));
        child.setCachedLayout(new LayoutBox(10, 10, 50, 50));

        // 不调用 setHitTestable：默认 true，叶节点正常命中
        Assert.assertTrue("默认 hitTestable 应为 true", child.isHitTestable());

        List<SceneNode> chain = tester.hitTest(root, 20, 20, 0, 0);
        Assert.assertEquals("默认命中链长度 2", 2, chain.size());
        Assert.assertSame("索引 0 为 root", root, chain.get(0));
        Assert.assertSame("索引 1 为 child（默认正常命中）", child, chain.get(1));
    }

    /**
     * scrollable 节点自己的 scrollOffsetY 不移动自己，只移动其子内容。
     */
    @Test
    public void scrollOffsetShouldNotMoveScrollableNodeItself() {
        SceneNode root = new SceneNode();
        SceneNode viewport = new SceneNode();
        root.appendChild(viewport);

        viewport.setScrollable(true);
        viewport.setScrollOffsetY(40);
        root.setCachedLayout(new LayoutBox(0, 0, 200, 200));
        viewport.setCachedLayout(new LayoutBox(10, 20, 100, 80));

        List<SceneNode> chain = tester.hitTest(root, 20, 30, 0, 0);
        Assert.assertEquals("viewport 自己仍应在原始 LayoutBox 位置命中", 2, chain.size());
        Assert.assertSame("命中 viewport 自己", viewport, chain.get(1));
    }

    /**
     * scrollable 父节点滚动后，子节点命中区域跟随 paint 偏移整体上移。
     */
    @Test
    public void scrollOffsetShouldMoveChildHitArea() {
        SceneNode root = new SceneNode();
        SceneNode viewport = new SceneNode();
        SceneNode child = new SceneNode();
        root.appendChild(viewport);
        viewport.appendChild(child);

        viewport.setScrollable(true);
        root.setCachedLayout(new LayoutBox(0, 0, 200, 200));
        viewport.setCachedLayout(new LayoutBox(0, 0, 120, 80));
        child.setCachedLayout(new LayoutBox(0, 60, 100, 30));

        List<SceneNode> before = tester.hitTest(root, 10, 70, 0, 0);
        Assert.assertEquals("滚动前 y=70 命中 child", 3, before.size());
        Assert.assertSame(child, before.get(2));

        viewport.setScrollOffsetY(40);
        viewport.setCachedLayout(new LayoutBox(0, 0, 120, 80));

        List<SceneNode> afterNewPosition = tester.hitTest(root, 10, 30, 0, 0);
        Assert.assertEquals("滚动后 child 命中区域应上移到 y=20..50", 3, afterNewPosition.size());
        Assert.assertSame(child, afterNewPosition.get(2));

        List<SceneNode> afterOldPosition = tester.hitTest(root, 10, 70, 0, 0);
        Assert.assertEquals("旧位置应回落命中 viewport 自己", 2, afterOldPosition.size());
        Assert.assertSame(viewport, afterOldPosition.get(1));
    }
}
