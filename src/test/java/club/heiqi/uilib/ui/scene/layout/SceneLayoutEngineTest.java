package club.heiqi.uilib.ui.scene.layout;

import org.junit.Test;
import org.junit.Assert;
import java.util.Set;

import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;

/**
 * SceneLayoutEngine 增量布局引擎单元测试。
 *
 * <p>核心验证 I7/I8 的双标记跳过机制：干净子树零重算。
 * 这是与旧栈 version 闸门全量重算的正面翻转证明。</p>
 */
public class SceneLayoutEngineTest {

    private final SceneLayoutEngine engine = new SceneLayoutEngine();
    private final ScenePaintEngine paintEngine = new ScenePaintEngine();

    // ============================================================
    // 测试 1：基本块级垂直堆叠正确
    // ============================================================

    /**
     * root 含 3 个文本子节点，layout 后验证各子节点 y 坐标按垂直堆叠递增、宽度=约束宽。
     */
    @Test
    public void shouldLayoutBlockVerticalStack() {
        // 构建树：root → childA, childB, childC（三个文本叶节点）
        SceneNode root = new SceneNode();
        SceneNode childA = new SceneNode();
        SceneNode childB = new SceneNode();
        SceneNode childC = new SceneNode();

        childA.setText("A");
        childB.setText("BB");   // 2 字符，但行高固定，不影响高度
        childC.setText("CCC");

        root.appendChild(childA);
        root.appendChild(childB);
        root.appendChild(childC);

        Constraints constraints = new Constraints(200);
        engine.layout(root, constraints);

        // 验证 root 的布局结果
        LayoutBox rootBox = (LayoutBox) root.getCachedLayout();
        Assert.assertNotNull("root 应有 cachedLayout", rootBox);
        Assert.assertEquals("root x", 0, rootBox.getX());
        Assert.assertEquals("root y", 0, rootBox.getY());
        Assert.assertEquals("root 宽度=约束宽", 200, rootBox.getWidth());
        // 三个文本子节点各占 16px 行高 → root 高度 = 48
        Assert.assertEquals("root 高度 = 3 × 16 = 48", 48, rootBox.getHeight());

        // childA: y=0
        LayoutBox boxA = (LayoutBox) childA.getCachedLayout();
        Assert.assertNotNull("childA cachedLayout", boxA);
        Assert.assertEquals("childA x", 0, boxA.getX());
        Assert.assertEquals("childA y=0", 0, boxA.getY());
        Assert.assertEquals("childA 宽度=200", 200, boxA.getWidth());
        Assert.assertEquals("childA 高度=16", 16, boxA.getHeight());

        // childB: y=16
        LayoutBox boxB = (LayoutBox) childB.getCachedLayout();
        Assert.assertEquals("childB y=16", 16, boxB.getY());
        Assert.assertEquals("childB 高度=16", 16, boxB.getHeight());

        // childC: y=32
        LayoutBox boxC = (LayoutBox) childC.getCachedLayout();
        Assert.assertEquals("childC y=32", 32, boxC.getY());
        Assert.assertEquals("childC 高度=16", 16, boxC.getHeight());

        // 所有脏标记应已清除
        Assert.assertFalse("root layout 脏", root.__isSelfLayoutDirty());
        Assert.assertFalse("root descendant 脏", root.__isDescendantLayoutDirty());
    }

    // ============================================================
    // 测试 2：I7 核心铁证 —— 干净兄弟被跳过、不重算
    // ============================================================

    /**
     * 先 layout 整棵树使其干净。然后只改一个兄弟的 text（只标它自己脏），
     * 再次 layout，断言其余干净兄弟的 LayoutBox 引用不变（未被重算）。
     *
     * <p>这是与旧栈 version 闸门全量重算的正面翻转。旧栈改一个节点→整棵重算；
     * 新栈只重算脏节点链，干净兄弟零开销。</p>
     */
    @Test
    public void shouldSkipCleanSiblingWhenDirtyNodeRelayouts() {
        // 构建树：root → A(文本), B(文本), C(文本)
        SceneNode root = new SceneNode();
        SceneNode a = new SceneNode();
        SceneNode b = new SceneNode();
        SceneNode c = new SceneNode();

        a.setText("A");
        b.setText("B");
        c.setText("C");

        root.appendChild(a);
        root.appendChild(b);
        root.appendChild(c);

        // 第一次 layout：全部变干净
        engine.layout(root, new Constraints(100));
        LayoutBox boxA1 = (LayoutBox) a.getCachedLayout();
        LayoutBox boxC1 = (LayoutBox) c.getCachedLayout();

        Assert.assertNotNull("A 应有 box", boxA1);
        Assert.assertNotNull("C 应有 box", boxC1);

        // 修改 B（设新 text → B.selfLayoutDirty=true, root.descendantLayoutDirty=true）
        // A 和 C 的标记未被触碰
        b.setText("BBBB");

        // 第二次 layout
        engine.layout(root, new Constraints(100));

        // I7 铁证：A 和 C 的 LayoutBox 引用应不变（未被重算，即未进入 performLayout）
        LayoutBox boxA2 = (LayoutBox) a.getCachedLayout();
        LayoutBox boxC2 = (LayoutBox) c.getCachedLayout();

        Assert.assertSame("干净兄弟 A 的 box 应被复用（引用相同）", boxA1, boxA2);
        Assert.assertSame("干净兄弟 C 的 box 应被复用（引用相同）", boxC1, boxC2);

        // 只重算了 1 个节点（B）
        Assert.assertEquals("重算次数应为 1", 1, engine.__getRelayoutCount());
        Set<SceneNode> relayouted = engine.__getRelayoutedNodes();
        Assert.assertTrue("B 在重算集合中", relayouted.contains(b));
        Assert.assertFalse("A 不在重算集合中", relayouted.contains(a));
        Assert.assertFalse("C 不在重算集合中", relayouted.contains(c));
    }

    // ============================================================
    // 测试 3：整棵干净树跳过
    // ============================================================

    /**
     * 全树已 layout 干净后，再次 layout 应零重算。
     */
    @Test
    public void shouldSkipEntireCleanTree() {
        SceneNode root = new SceneNode();
        SceneNode a = new SceneNode();
        a.setText("Hello");
        root.appendChild(a);

        // 第一次 layout（root + a 均被标脏，重算 2 次）
        engine.layout(root, new Constraints(100));
        Assert.assertTrue("首次 layout 重算次数≥1", engine.__getRelayoutCount() >= 1);

        // 第二次 layout：全树干净，应零重算
        engine.layout(root, new Constraints(100));
        Assert.assertEquals("第二次 layout 整棵跳过，重算次数=0", 0, engine.__getRelayoutCount());
    }

    // ============================================================
    // 测试 4：descendantLayoutDirty 下沉但跳过中间干净节点
    // ============================================================

    /**
     * 深层节点脏时，中间干净节点复用（selfLayoutDirty=false 不重算），
     * 只有脏节点链上的节点被重算。
     *
     * <p>树结构：root → container → leaf
     * 修改 leaf.text → leaf selfLayoutDirty + container/root descendantLayoutDirty
     * layout 后：leaf 重算，container 和 root 的 LayoutBox 引用不变。</p>
     */
    @Test
    public void shouldDescendantDirtySinkButSkipCleanIntermediateNodes() {
        SceneNode root = new SceneNode();
        SceneNode container = new SceneNode();
        SceneNode leaf = new SceneNode();

        leaf.setText("initial");
        container.appendChild(leaf);
        root.appendChild(container);

        // 第一次 layout：全部干净
        engine.layout(root, new Constraints(200));

        LayoutBox rootBox1 = (LayoutBox) root.getCachedLayout();
        LayoutBox containerBox1 = (LayoutBox) container.getCachedLayout();
        LayoutBox leafBox1 = (LayoutBox) leaf.getCachedLayout();
        Assert.assertNotNull(rootBox1);
        Assert.assertNotNull(containerBox1);
        Assert.assertNotNull(leafBox1);

        // 修改 leaf（只标 leaf.selfLayoutDirty + 向上冒泡 descendantLayoutDirty）
        leaf.setText("changed");

        // 第二次 layout
        engine.layout(root, new Constraints(200));

        // root 和 container 的 selfLayoutDirty 为 false，
        // 它们的 cachedLayout 应被复用（引用不变）
        LayoutBox rootBox2 = (LayoutBox) root.getCachedLayout();
        LayoutBox containerBox2 = (LayoutBox) container.getCachedLayout();

        Assert.assertSame("root box 应复用（引用相同）", rootBox1, rootBox2);
        Assert.assertSame("container box 应复用（引用相同）", containerBox1, containerBox2);

        // leaf 应被重算
        Assert.assertEquals("重算次数=1", 1, engine.__getRelayoutCount());
        Assert.assertTrue("leaf 在重算集合中", engine.__getRelayoutedNodes().contains(leaf));
        Assert.assertFalse("root 不在重算集合中", engine.__getRelayoutedNodes().contains(root));
        Assert.assertFalse("container 不在重算集合中", engine.__getRelayoutedNodes().contains(container));
    }

    // ============================================================
    // 附加：容器嵌套子节点的垂直堆叠验证
    // ============================================================

    /**
     * 验证容器节点高度 = 子节点高度之和。
     */
    @Test
    public void shouldComputeContainerHeightFromChildren() {
        SceneNode root = new SceneNode();
        SceneNode container = new SceneNode();
        SceneNode a = new SceneNode();
        SceneNode b = new SceneNode();

        a.setText("A");
        b.setText("B");
        container.appendChild(a);
        container.appendChild(b);
        root.appendChild(container);

        engine.layout(root, new Constraints(300));

        // container 高度 = 2 × 16 = 32
        LayoutBox containerBox = (LayoutBox) container.getCachedLayout();
        Assert.assertEquals("container 高度=32", 32, containerBox.getHeight());

        // root 高度 = container 高度 = 32
        LayoutBox rootBox = (LayoutBox) root.getCachedLayout();
        Assert.assertEquals("root 高度=32", 32, rootBox.getHeight());
    }

    // ============================================================
    // 附加：无文本无子节点的叶子高度为 0
    // ============================================================

    /**
     * 空叶子节点（无文本、无子节点）高度应为 0。
     */
    @Test
    public void shouldHaveZeroHeightForEmptyLeaf() {
        SceneNode root = new SceneNode();
        SceneNode empty = new SceneNode();
        root.appendChild(empty);

        engine.layout(root, new Constraints(100));

        LayoutBox emptyBox = (LayoutBox) empty.getCachedLayout();
        Assert.assertNotNull("空节点也应有 cachedLayout", emptyBox);
        Assert.assertEquals("空叶子高度=0", 0, emptyBox.getHeight());

        LayoutBox rootBox = (LayoutBox) root.getCachedLayout();
        Assert.assertEquals("root 高度=0", 0, rootBox.getHeight());
    }

    // ============================================================
    // 新增：几何变化上传祖先 —— ora-2 修复验证
    // ============================================================

    /**
     * leaf 高度从 16→32（单行→双行），验证 container 和 root 的
     * cachedLayout 高度同步更新。此前因 descendantLayoutDirty 分支
     * 跳过 performLayout，祖先高度不更新——这是 ora-2 发现的阻断缺陷。
     */
    @Test
    public void shouldUploadLeafGeometryChangeToAncestors() {
        // root → container → leaf（初始单行文本）
        SceneNode root = new SceneNode();
        SceneNode container = new SceneNode();
        SceneNode leaf = new SceneNode();

        leaf.setText("A");               // 单行 → 高度 16
        container.appendChild(leaf);
        root.appendChild(container);

        // 第一次 layout
        engine.layout(root, new Constraints(200));

        LayoutBox leafBox1 = (LayoutBox) leaf.getCachedLayout();
        LayoutBox containerBox1 = (LayoutBox) container.getCachedLayout();
        LayoutBox rootBox1 = (LayoutBox) root.getCachedLayout();
        Assert.assertEquals("leaf 初始高度=16", 16, leafBox1.getHeight());
        Assert.assertEquals("container 初始高度=16", 16, containerBox1.getHeight());
        Assert.assertEquals("root 初始高度=16", 16, rootBox1.getHeight());

        // leaf 变双行：高度 16→32
        // 触发 leaf.selfLayoutDirty，container/root 仅 descendantLayoutDirty
        leaf.setText("A\nB");

        // 第二次 layout
        engine.layout(root, new Constraints(200));

        // 验证 leaf 高度更新
        LayoutBox leafBox2 = (LayoutBox) leaf.getCachedLayout();
        Assert.assertEquals("leaf 高度应为 32", 32, leafBox2.getHeight());

        // 核心断言：祖先高度同步更新（ora-2 修复前为 16）
        LayoutBox containerBox2 = (LayoutBox) container.getCachedLayout();
        LayoutBox rootBox2 = (LayoutBox) root.getCachedLayout();
        Assert.assertEquals("container 高度应同步为 32", 32, containerBox2.getHeight());
        Assert.assertEquals("root 高度应同步为 32", 32, rootBox2.getHeight());

        // leaf 被重算（selfLayoutDirty），但 container/root 仅因几何上传,
        // 不计入 relayoutCount（保持 I7 语义）
        Assert.assertEquals("重算次数=1（仅 leaf 自身）", 1, engine.__getRelayoutCount());
        Assert.assertTrue("leaf 在重算集合", engine.__getRelayoutedNodes().contains(leaf));
        Assert.assertFalse("container 不在重算集合", engine.__getRelayoutedNodes().contains(container));
        Assert.assertFalse("root 不在重算集合", engine.__getRelayoutedNodes().contains(root));
    }

    /**
     * 三兄弟 A→B→C，A 高度从 16→32 后，B 和 C 的 y 坐标应顺移。
     */
    @Test
    public void shouldShiftSiblingYAfterPrecedingNodeHeightChange() {
        SceneNode root = new SceneNode();
        SceneNode a = new SceneNode();
        SceneNode b = new SceneNode();
        SceneNode c = new SceneNode();

        a.setText("A");    // 单行 16
        b.setText("B");    // 单行 16
        c.setText("C");    // 单行 16

        root.appendChild(a);
        root.appendChild(b);
        root.appendChild(c);

        // 第一次 layout
        engine.layout(root, new Constraints(100));

        // 记录 B 和 C 的初始 y
        LayoutBox boxB1 = (LayoutBox) b.getCachedLayout();
        LayoutBox boxC1 = (LayoutBox) c.getCachedLayout();
        Assert.assertEquals("B 初始 y=16", 16, boxB1.getY());
        Assert.assertEquals("C 初始 y=32", 32, boxC1.getY());

        // A 变双行：高度 16→32
        a.setText("A\nX");

        // 第二次 layout
        engine.layout(root, new Constraints(100));

        // B 和 C 的 y 坐标应顺移 16px
        LayoutBox boxB2 = (LayoutBox) b.getCachedLayout();
        LayoutBox boxC2 = (LayoutBox) c.getCachedLayout();
        Assert.assertEquals("B y 应顺移到 32", 32, boxB2.getY());
        Assert.assertEquals("C y 应顺移到 48", 48, boxC2.getY());

        // root 高度从 48→64
        LayoutBox rootBox = (LayoutBox) root.getCachedLayout();
        Assert.assertEquals("root 高度=64", 64, rootBox.getHeight());
    }

    // ============================================================
    // 新增：geometryDirty 标记语义纯净（ora-2 复审要求）
    // ============================================================

    /**
     * A 变高导致 B 位置下移，layout 后 B 应有 geometryDirty 标记，
     * 但 compositeDirty 必须保持 false（位置变化不污染合成标记）。
     * geometryDirty 冒泡使祖先 descendantGeometryDirty 被点亮。
     */
    @Test
    public void shouldSetGeometryDirtyNotCompositeDirtyOnPositionChange() {
        SceneNode root = new SceneNode();
        SceneNode container = new SceneNode();
        SceneNode a = new SceneNode();
        SceneNode b = new SceneNode();

        a.setText("A");
        a.setBackgroundColor(0xFFFF0000);
        b.setText("B");
        b.setBackgroundColor(0xFF0000FF);

        container.appendChild(a);
        container.appendChild(b);
        root.appendChild(container);

        // 第一次 layout + paint：所有节点首次 layout → geometryDirty 被标
        engine.layout(root, new Constraints(100));
        // paint 遍历清除 geometry 标记（模拟真实流程 layout→paint）
        paintEngine.paint(root);
        Assert.assertFalse("首次 paint 后 B geometryDirty=false", b.__isSelfGeometryDirty());

        // A 变双行 → 触发 layout 重排，B 位置 16→32
        a.setText("A\nX");
        engine.layout(root, new Constraints(100));

        // B 的 compositeDirty 未被污染
        Assert.assertFalse("B compositeDirty 仍为 false",
                b.__isCompositeDirty());
        // B 的 selfGeometryDirty 被标记
        Assert.assertTrue("B selfGeometryDirty=true",
                b.__isSelfGeometryDirty());
        // root 的 descendantGeometryDirty 冒泡点亮
        Assert.assertTrue("root descendantGeometryDirty=true",
                root.__isDescendantGeometryDirty());
    }

    // ============================================================
    // 新增：Constraints 高度约束 + fillParentHeight 功能测试
    // ============================================================

    /**
     * Constraints 双参构造器、getAvailableHeight、hasHeightConstraint、
     * equals/hashCode 含 availableHeight 的基本单元断言。
     */
    @Test
    public void shouldConstraintsIncludeHeightDimension() {
        // 单参构造器（向后兼容）：高度为 UNCONSTRAINED
        Constraints c1 = new Constraints(200);
        Assert.assertEquals("单参宽度=200", 200, c1.getAvailableWidth());
        Assert.assertEquals("单参高度=UNCONSTRAINED(-1)", -1, c1.getAvailableHeight());
        Assert.assertFalse("单参 hasHeightConstraint=false", c1.hasHeightConstraint());

        // 双参构造器
        Constraints c2 = new Constraints(200, 100);
        Assert.assertEquals("双参宽度=200", 200, c2.getAvailableWidth());
        Assert.assertEquals("双参高度=100", 100, c2.getAvailableHeight());
        Assert.assertTrue("双参 hasHeightConstraint=true", c2.hasHeightConstraint());

        // equals 纳入 availableHeight
        Constraints c2Copy = new Constraints(200, 100);
        Assert.assertEquals("同宽高应 equals", c2, c2Copy);
        Assert.assertNotEquals("不同高度应不等", c1, c2);

        // hashCode 纳入 availableHeight
        Assert.assertEquals("同宽高 hashCode 相等", c2.hashCode(), c2Copy.hashCode());

        // toString 包含 availableHeight
        String s = c2.toString();
        Assert.assertTrue("toString 含 availableHeight", s.contains("availableHeight"));
    }

    /**
     * fill 生效：root 设置 fillParentHeight，内容仅 16px，约束高 100，
     * 断言 root 高度取 max(16, 100) = 100。
     */
    @Test
    public void shouldFillParentHeightWhenFlagSet() {
        SceneNode root = new SceneNode();
        root.setFillParentHeight(true);
        SceneNode child = new SceneNode();
        child.setText("A"); // 单行 16px
        root.appendChild(child);

        // 约束宽 200、高 100 → root 应取 max(16, 100) = 100
        engine.layout(root, new Constraints(200, 100));

        LayoutBox rootBox = (LayoutBox) root.getCachedLayout();
        Assert.assertNotNull("root 应有 cachedLayout", rootBox);
        Assert.assertEquals("root 高度=100（max(16,100)）", 100, rootBox.getHeight());
        Assert.assertEquals("root 宽度=200", 200, rootBox.getWidth());

        // child 自身高度仍是 16px（不受 fill 影响）
        LayoutBox childBox = (LayoutBox) child.getCachedLayout();
        Assert.assertEquals("child 高度=16", 16, childBox.getHeight());
    }

    /**
     * fill 不影响默认行为：非 fill 节点在 Constraints(W, 100) 下
     * 仍 shrink-to-fit，高度=内容高（回归保护）。
     */
    @Test
    public void shouldShrinkToFitWhenFillParentHeightNotSet() {
        SceneNode root = new SceneNode();
        // 未设置 fillParentHeight（默认 false）
        SceneNode child = new SceneNode();
        child.setText("A"); // 单行 16px
        root.appendChild(child);

        // 约束宽 200、高 100，但 fill=false → root 高度=内容高 16
        engine.layout(root, new Constraints(200, 100));

        LayoutBox rootBox = (LayoutBox) root.getCachedLayout();
        Assert.assertEquals("非 fill root 高度=16（shrink-to-fit）", 16, rootBox.getHeight());
        Assert.assertEquals("非 fill root 宽度=200", 200, rootBox.getWidth());

        // 验证 UNCONSTRAINED 高度也不影响 shrink-to-fit
        engine.layout(root, new Constraints(200)); // 单参=UNCONSTRAINED
        LayoutBox rootBox2 = (LayoutBox) root.getCachedLayout();
        Assert.assertEquals("UNCONSTRAINED 下 root 高度仍=16", 16, rootBox2.getHeight());
    }

    /**
     * 约束高度变化突破双 false 跳过（I7 关键正例）。
     *
     * <p>先 layout(root, (W,100)) 跑干净，再 layout(root, (W,200))，
     * 约束变化应驱动 root 标脏 → 突破 I7 双 false → root 被重算、height==200。</p>
     */
    @Test
    public void shouldBreakI7SkipOnConstraintHeightChange() {
        SceneNode root = new SceneNode();
        root.setFillParentHeight(true);
        SceneNode child = new SceneNode();
        child.setText("A");
        root.appendChild(child);

        // 第一次 layout：约束高 100，root 高度=100
        engine.layout(root, new Constraints(200, 100));
        LayoutBox rootBox1 = (LayoutBox) root.getCachedLayout();
        Assert.assertEquals("首次 root 高度=100", 100, rootBox1.getHeight());
        Assert.assertFalse("首次后 root selfLayoutDirty=false", root.__isSelfLayoutDirty());

        // 第二次 layout：约束高变为 200，应突破双 false 跳过
        engine.layout(root, new Constraints(200, 200));

        LayoutBox rootBox2 = (LayoutBox) root.getCachedLayout();
        Assert.assertEquals("约束变化后 root 高度=200", 200, rootBox2.getHeight());
        // root 因为被约束变化感知标脏，应出现在重算集合中
        Assert.assertTrue("约束变化后应有重算", engine.__getRelayoutCount() >= 1);
        Assert.assertTrue("root 在重算集合中", engine.__getRelayoutedNodes().contains(root));
    }

    /**
     * 约束不变不过度失效：连续两次相同 Constraints(W,100) 的 fill root，
     * 第二次 __getRelayoutCount() 应为 0（I7 整棵跳过）。
     */
    @Test
    public void shouldNotRelayoutOnSameConstraints() {
        SceneNode root = new SceneNode();
        root.setFillParentHeight(true);
        SceneNode child = new SceneNode();
        child.setText("A");
        root.appendChild(child);

        Constraints c = new Constraints(200, 100);

        // 第一次 layout
        engine.layout(root, c);

        // 第二次 layout：约束完全相同，应整棵跳过
        engine.layout(root, c);

        Assert.assertEquals("相同约束第二次重算=0", 0, engine.__getRelayoutCount());
    }

    /**
     * I7 兄弟跳过保持：fill root 下挂干净非 fill 兄弟，约束不变时
     * 它们 LayoutBox assertSame 复用、不在 __getRelayoutedNodes() 中。
     */
    @Test
    public void shouldSkipCleanSiblingsUnderFillRootOnSameConstraints() {
        SceneNode root = new SceneNode();
        root.setFillParentHeight(true);
        SceneNode a = new SceneNode();
        SceneNode b = new SceneNode();
        SceneNode c = new SceneNode();

        a.setText("A");
        b.setText("B");
        c.setText("C");

        root.appendChild(a);
        root.appendChild(b);
        root.appendChild(c);

        Constraints c200x100 = new Constraints(200, 100);

        // 第一次 layout
        engine.layout(root, c200x100);

        LayoutBox boxA1 = (LayoutBox) a.getCachedLayout();
        LayoutBox boxB1 = (LayoutBox) b.getCachedLayout();
        LayoutBox boxC1 = (LayoutBox) c.getCachedLayout();
        Assert.assertNotNull("A 应有 box", boxA1);
        Assert.assertNotNull("B 应有 box", boxB1);
        Assert.assertNotNull("C 应有 box", boxC1);

        // 第二次 layout：约束不变，fill root 第一次被标脏但第二次干净（约束不变不标脏）
        engine.layout(root, c200x100);

        // 兄弟节点 LayoutBox 应复用
        LayoutBox boxA2 = (LayoutBox) a.getCachedLayout();
        LayoutBox boxB2 = (LayoutBox) b.getCachedLayout();
        LayoutBox boxC2 = (LayoutBox) c.getCachedLayout();

        Assert.assertSame("干净兄弟 A 的 box 应复用", boxA1, boxA2);
        Assert.assertSame("干净兄弟 B 的 box 应复用", boxB1, boxB2);
        Assert.assertSame("干净兄弟 C 的 box 应复用", boxC1, boxC2);

        // 不在重算集合中
        Set<SceneNode> relayouted = engine.__getRelayoutedNodes();
        Assert.assertFalse("A 不在重算集合", relayouted.contains(a));
        Assert.assertFalse("B 不在重算集合", relayouted.contains(b));
        Assert.assertFalse("C 不在重算集合", relayouted.contains(c));
    }
}
