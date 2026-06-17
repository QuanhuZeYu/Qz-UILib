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
}
