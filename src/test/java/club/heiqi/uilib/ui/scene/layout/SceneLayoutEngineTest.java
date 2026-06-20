package club.heiqi.uilib.ui.scene.layout;

import org.junit.Test;
import org.junit.Assert;
import java.util.Set;

import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;

/**
 * SceneLayoutEngine 增量布局引擎单元测试。
 *
 * <p>核心验证 I7/I8 的双标记跳过机制：干净子树零重算。
 * 这是与旧栈 version 闸门全量重算的正面翻转证明。</p>
 */
public class SceneLayoutEngineTest {

    private final SceneLayoutEngine engine = new SceneLayoutEngine(new FixedTextMeasurer(8, 16));
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

    // ==================== Bug① 回归：preferredHeight 叶节点高度 ====================

    /**
     * 无文本叶节点设置 preferredHeight 后 layout 高度>0。
     * Bug 根因：btn 节点无文本无子节点 → computeContentHeight=0 → 0 高矩形不渲染、不命中。
     */
    @Test
    public void leafNodeWithPreferredHeightShouldHaveNonZeroLayoutHeight() {
        SceneNode root = new SceneNode();
        SceneNode leaf = new SceneNode();
        leaf.setPreferredHeight(30);
        root.appendChild(leaf);

        engine.layout(root, new Constraints(200));

        LayoutBox leafBox = (LayoutBox) leaf.getCachedLayout();
        Assert.assertNotNull("leaf 应有 cachedLayout", leafBox);
        Assert.assertEquals("leaf 高度应为 30", 30, leafBox.getHeight());
    }

    /**
     * 文本叶节点的 preferredHeight 与文本高度取 max。
     * 文本 1 行=16px，preferredHeight=30，应取 30。
     */
    @Test
    public void leafNodeWithTextAndPreferredHeightShouldTakeMax() {
        SceneNode root = new SceneNode();
        SceneNode leaf = new SceneNode();
        leaf.setText("Hi");     // 1 行 × 16px = 16
        leaf.setPreferredHeight(30);
        root.appendChild(leaf);

        engine.layout(root, new Constraints(200));

        LayoutBox leafBox = (LayoutBox) leaf.getCachedLayout();
        Assert.assertNotNull("leaf 应有 cachedLayout", leafBox);
        Assert.assertEquals("高度应为 max(16, 30)=30", 30, leafBox.getHeight());
    }

    /**
     * 文本高度 > preferredHeight 时取文本高度。
     */
    @Test
    public void leafNodeTextHeightWinsWhenLargerThanPreferred() {
        SceneNode root = new SceneNode();
        SceneNode leaf = new SceneNode();
        leaf.setText("Line1\nLine2\nLine3"); // 3 行 × 16px = 48
        leaf.setPreferredHeight(20);
        root.appendChild(leaf);

        engine.layout(root, new Constraints(200));

        LayoutBox leafBox = (LayoutBox) leaf.getCachedLayout();
        Assert.assertNotNull("leaf 应有 cachedLayout", leafBox);
        Assert.assertEquals("高度应为 max(48, 20)=48", 48, leafBox.getHeight());
    }

    /**
     * 默认 preferredHeight=0 不改变现有行为。
     */
    @Test
    public void leafNodeWithDefaultPreferredHeightZeroShouldHaveTextHeightOnly() {
        SceneNode root = new SceneNode();
        SceneNode leaf = new SceneNode();
        leaf.setText("X"); // 1 行 × 16px
        // preferredHeight 默认 0
        root.appendChild(leaf);

        engine.layout(root, new Constraints(200));

        LayoutBox leafBox = (LayoutBox) leaf.getCachedLayout();
        Assert.assertNotNull("leaf 应有 cachedLayout", leafBox);
        Assert.assertEquals("高度应为文本 16px", 16, leafBox.getHeight());
    }

    // ============================================================
    // 测试 8：padding 变化只重算容器、不重算干净子节点
    // ============================================================

    /**
     * root 加 3 个干净文本子节点跑干净后，仅改 root.setPadding(20)，
     * 再 layout：只 root 自身重算（padding 属容器属性），子节点不被重算。
     */
    @Test
    public void paddingChangeDoesNotRelayoutCleanChildren() {
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

        // 第一次 layout：全树干净
        engine.layout(root, new Constraints(200));

        // 仅改 root padding（只标 root.selfLayoutDirty）
        root.setPadding(20);

        // 第二次 layout
        engine.layout(root, new Constraints(200));

        Set<SceneNode> relayouted = engine.__getRelayoutedNodes();
        Assert.assertTrue("root 在重算集合", relayouted.contains(root));
        Assert.assertFalse("a 不在重算集合", relayouted.contains(a));
        Assert.assertFalse("b 不在重算集合", relayouted.contains(b));
        Assert.assertFalse("c 不在重算集合", relayouted.contains(c));
        Assert.assertEquals("重算次数=1（仅 root）", 1, engine.__getRelayoutCount());
    }

    // ============================================================
    // 测试 9：gap 变化只重算容器、不重算干净子节点
    // ============================================================

    /**
     * root 加 2 个干净文本子节点跑干净后，仅改 root.setGap(10)，
     * 再 layout：只 root 自身重算，子节点不被重算，B 的 y 顺移到 26（A 高 16 + gap 10）。
     */
    @Test
    public void gapChangeDoesNotRelayoutCleanChildren() {
        SceneNode root = new SceneNode();
        SceneNode a = new SceneNode();
        SceneNode b = new SceneNode();

        a.setText("A");
        b.setText("B");

        root.appendChild(a);
        root.appendChild(b);

        // 第一次 layout：全树干净
        engine.layout(root, new Constraints(200));

        // 仅改 root gap（只标 root.selfLayoutDirty）
        root.setGap(10);

        // 第二次 layout
        engine.layout(root, new Constraints(200));

        Set<SceneNode> relayouted = engine.__getRelayoutedNodes();
        Assert.assertFalse("a 不在重算集合", relayouted.contains(a));
        Assert.assertFalse("b 不在重算集合", relayouted.contains(b));
        Assert.assertEquals("重算次数=1（仅 root）", 1, engine.__getRelayoutCount());

        // B 的 y 顺移：A 高 16 + gap 10 = 26
        LayoutBox boxB = (LayoutBox) b.getCachedLayout();
        Assert.assertEquals("B y=26（A高16+gap10）", 26, boxB.getY());
    }

    // ============================================================
    // 测试 10：ROW 方向水平排布子节点
    // ============================================================

    /**
     * root.setFlexDirection(ROW) + setGap(5)，加 a/b 两个文本子节点，
     * 验证子节点沿水平主轴排布：boxB.x == boxA.width + 5，y 均为 0。
     *
     * <p>用相对关系断言规避叶节点绝对宽度依赖。</p>
     */
    @Test
    public void rowDirectionLaysOutChildrenHorizontally() {
        SceneNode root = new SceneNode();
        root.setFlexDirection(FlexDirection.ROW);
        root.setGap(5);
        SceneNode a = new SceneNode();
        SceneNode b = new SceneNode();

        a.setText("A");
        b.setText("B");

        root.appendChild(a);
        root.appendChild(b);

        engine.layout(root, new Constraints(300));

        LayoutBox boxA = (LayoutBox) a.getCachedLayout();
        LayoutBox boxB = (LayoutBox) b.getCachedLayout();

        Assert.assertEquals("A x=0", 0, boxA.getX());
        Assert.assertEquals("A y=0", 0, boxA.getY());
        Assert.assertEquals("B x = A宽 + gap5", boxA.getWidth() + 5, boxB.getX());
        Assert.assertEquals("B y=0", 0, boxB.getY());
    }

    // ============================================================
    // 测试 11：主轴 CENTER 在 fill 容器内居中
    // ============================================================

    /**
     * root.setFillParentHeight(true) + setMainAxisAlign(CENTER)，加 a（高 16），
     * 约束高 100，验证 a 在主轴（垂直）方向居中：y == (100-16)/2 == 42，root 高 100。
     */
    @Test
    public void mainAxisCenterCentersChildrenInFillContainer() {
        SceneNode root = new SceneNode();
        root.setFillParentHeight(true);
        root.setMainAxisAlign(MainAxisAlign.CENTER);
        SceneNode a = new SceneNode();
        a.setText("A"); // 单行 16

        root.appendChild(a);

        engine.layout(root, new Constraints(200, 100));

        LayoutBox boxA = (LayoutBox) a.getCachedLayout();
        LayoutBox rootBox = (LayoutBox) root.getCachedLayout();
        Assert.assertEquals("A y=(100-16)/2=42", 42, boxA.getY());
        Assert.assertEquals("root 高度=100", 100, rootBox.getHeight());
    }

    // ============================================================
    // 测试 12：shrink-to-fit 时主轴 CENTER 退化为 START
    // ============================================================

    /**
     * root.setMainAxisAlign(CENTER)，加 a，无高度约束（shrink-to-fit），
     * 此时 mainAvail == mainContentWithGap → offset 算出 0 → 退化为 START，a.y == 0。
     */
    @Test
    public void mainAxisCenterDegradesToStartWhenShrinkToFit() {
        SceneNode root = new SceneNode();
        root.setMainAxisAlign(MainAxisAlign.CENTER);
        SceneNode a = new SceneNode();
        a.setText("A");

        root.appendChild(a);

        engine.layout(root, new Constraints(200)); // 无高度约束

        LayoutBox boxA = (LayoutBox) a.getCachedLayout();
        Assert.assertEquals("shrink-to-fit 下 CENTER 退化 START，A y=0", 0, boxA.getY());
    }

    // ============================================================
    // 测试 13：ROW 下交叉轴 CENTER 对齐
    // ============================================================

    /**
     * root.setFlexDirection(ROW) + setCrossAxisAlign(CENTER)，加 tall（高 48）、
     * shortN（高 16），验证交叉轴（垂直）居中：tall.y==0、shortN.y==(48-16)/2==16。
     */
    @Test
    public void crossAxisCenterAlignsChildrenInRow() {
        SceneNode root = new SceneNode();
        root.setFlexDirection(FlexDirection.ROW);
        root.setCrossAxisAlign(CrossAxisAlign.CENTER);
        SceneNode tall = new SceneNode();
        SceneNode shortN = new SceneNode();

        tall.setText("A\nB\nC"); // 3 行 → 高 48
        shortN.setText("X");     // 1 行 → 高 16

        root.appendChild(tall);
        root.appendChild(shortN);

        engine.layout(root, new Constraints(300));

        LayoutBox boxTall = (LayoutBox) tall.getCachedLayout();
        LayoutBox boxShort = (LayoutBox) shortN.getCachedLayout();
        Assert.assertEquals("tall y=0", 0, boxTall.getY());
        Assert.assertEquals("short y=(48-16)/2=16", 16, boxShort.getY());
    }

    // ============================================================
    // 测试 14：默认行为等价旧的垂直堆叠
    // ============================================================

    /**
     * root 加 a/b/c 全默认（COLUMN/START/STRETCH/padding 0/gap 0），
     * 验证与旧引擎垂直堆叠完全一致：宽填满、高度累加、y 递增、子宽=父宽。
     */
    @Test
    public void defaultBehaviorMatchesLegacyVerticalStacking() {
        SceneNode root = new SceneNode();
        SceneNode a = new SceneNode();
        SceneNode b = new SceneNode();
        SceneNode c = new SceneNode();

        a.setText("A");
        b.setText("B");
        c.setText("CCC");

        root.appendChild(a);
        root.appendChild(b);
        root.appendChild(c);

        engine.layout(root, new Constraints(200));

        LayoutBox rootBox = (LayoutBox) root.getCachedLayout();
        Assert.assertEquals("root 宽=200", 200, rootBox.getWidth());
        Assert.assertEquals("root 高=48", 48, rootBox.getHeight());

        LayoutBox boxA = (LayoutBox) a.getCachedLayout();
        LayoutBox boxB = (LayoutBox) b.getCachedLayout();
        LayoutBox boxC = (LayoutBox) c.getCachedLayout();
        Assert.assertEquals("A y=0", 0, boxA.getY());
        Assert.assertEquals("B y=16", 16, boxB.getY());
        Assert.assertEquals("C y=32", 32, boxC.getY());
        Assert.assertEquals("A 宽=200", 200, boxA.getWidth());
        Assert.assertEquals("B 宽=200", 200, boxB.getWidth());
    }

    // ============================================================
    // 测试 15：偏离 1 解除 —— ROW + 主轴 CENTER 偏移非 0
    // ============================================================

    /**
     * ROW + 主轴 CENTER，单个文本叶（"AB" → shrink-to-fit 宽=2*8=16）放进宽 200 容器，
     * 验证主轴居中偏移非 0：叶 x = (200-16)/2 = 92。
     *
     * <p>这是偏离 1 解除的直接铁证：接入真实度量前，叶节点宽被 STRETCH 改写为撑满主轴可用宽，
     * mainContentWithGap==mainAvail → CENTER 偏移恒 0；接入 shrink-to-fit 后，叶 main 宽=内在宽
     * &lt; 可用宽，CENTER 偏移恢复非 0。</p>
     */
    @Test
    public void rowMainAxisCenterShouldProduceNonZeroOffset() {
        SceneNode root = new SceneNode();
        root.setFlexDirection(FlexDirection.ROW);
        root.setMainAxisAlign(MainAxisAlign.CENTER);
        SceneNode a = new SceneNode();
        a.setText("AB"); // 2 字符 → shrink-to-fit 宽 = 2 * 8 = 16

        root.appendChild(a);

        engine.layout(root, new Constraints(200));

        LayoutBox boxA = (LayoutBox) a.getCachedLayout();
        // 叶节点 shrink-to-fit 宽 = 16
        Assert.assertEquals("A 宽 = 2*8 = 16（shrink-to-fit）", 16, boxA.getWidth());
        // ROW 主轴 CENTER 偏移：(200-16)/2 = 92
        Assert.assertEquals("A x = (200-16)/2 = 92", 92, boxA.getX());
        Assert.assertTrue("ROW+CENTER 主轴偏移非 0（偏离 1 解除）", boxA.getX() > 0);
    }

    // ============================================================
    // 测试 16：epoch 失效链 —— 字体运行时变化驱动文本叶重测
    // ============================================================

    /**
     * 字体 epoch 变化时，上一帧测量过的文本叶被向上冒泡标脏并重测；
     * 同时干净的非文本子树不被向下标脏（I7 正向断言）。
     *
     * <p>步骤：用可控 epoch 的 stub 跑稳态 → bump epoch → 再 layout，
     * 断言文本叶在重算集合、root 不在重算集合（仅因 descendant 下沉，不计入）。</p>
     */
    @Test
    public void epochChangeShouldRelayoutTextLeavesOnly() {
        FixedTextMeasurer stub = new FixedTextMeasurer(8, 16);
        SceneLayoutEngine epochEngine = new SceneLayoutEngine(stub);

        SceneNode root = new SceneNode();
        SceneNode textLeaf = new SceneNode();
        textLeaf.setText("Hello");
        root.appendChild(textLeaf);

        // 第一帧：跑到稳态（textLeaf 被测量并登记进 measuredTextNodes）
        Constraints constraints = new Constraints(200);
        epochEngine.layout(root, constraints);
        Assert.assertNotNull("textLeaf 应有 cachedLayout", textLeaf.getCachedLayout());

        // 第二帧（不变 epoch、不变约束）：整棵干净跳过，零重算
        epochEngine.layout(root, constraints);
        Assert.assertEquals("epoch/约束不变第二帧零重算", 0, epochEngine.__getRelayoutCount());

        // bump epoch 模拟字体运行时变化 → 第三帧应使文本叶失效重测
        stub.bumpEpoch();
        epochEngine.layout(root, constraints);

        // 文本叶被重算（epoch 失效链向上冒泡标脏）
        Assert.assertTrue("epoch 变化后文本叶应被重算",
                epochEngine.__getRelayoutedNodes().contains(textLeaf));
        // root 仅因 descendant 下沉重定位，不计入重算集合（I7：未向下标脏 root 自身）
        Assert.assertFalse("root 不应被计入重算集合（未被向下标脏）",
                epochEngine.__getRelayoutedNodes().contains(root));
    }

    /**
     * epoch 失效链不波及无文本子树（I7 正向断言）：
     * 纯容器 + 无文本叶在 epoch 变化时不被标脏重算。
     */
    @Test
    public void epochChangeShouldNotRelayoutNonTextSubtree() {
        FixedTextMeasurer stub = new FixedTextMeasurer(8, 16);
        SceneLayoutEngine epochEngine = new SceneLayoutEngine(stub);

        SceneNode root = new SceneNode();
        SceneNode textLeaf = new SceneNode();
        textLeaf.setText("T");
        SceneNode emptyLeaf = new SceneNode();
        emptyLeaf.setPreferredHeight(20); // 无文本叶（不进 measuredTextNodes）
        root.appendChild(textLeaf);
        root.appendChild(emptyLeaf);

        Constraints constraints = new Constraints(200);
        epochEngine.layout(root, constraints);
        epochEngine.layout(root, constraints);
        Assert.assertEquals("稳态第二帧零重算", 0, epochEngine.__getRelayoutCount());

        // bump epoch → 仅文本叶应失效，无文本叶不应被标脏
        stub.bumpEpoch();
        epochEngine.layout(root, constraints);

        Assert.assertTrue("文本叶应被重算", epochEngine.__getRelayoutedNodes().contains(textLeaf));
        Assert.assertFalse("无文本叶不应被 epoch 失效链标脏",
                epochEngine.__getRelayoutedNodes().contains(emptyLeaf));
    }

    // ============================================================
    // preferredWidth 系列：显式固定宽 + STRETCH 豁免
    // ============================================================

    /**
     * T1 固定宽生效（叶）：无文本叶 setPreferredWidth(18)，
     * 断言 width==18，压过「无文本叶=outerWidth」决策，且 STRETCH 豁免使其不被拉满。
     */
    @Test
    public void preferredWidthShouldPinLeafWidth() {
        SceneNode root = new SceneNode();
        SceneNode leaf = new SceneNode();
        leaf.setPreferredWidth(18);   // 无文本叶，原本走 outerWidth=200
        root.appendChild(leaf);

        engine.layout(root, new Constraints(200));

        LayoutBox leafBox = (LayoutBox) leaf.getCachedLayout();
        Assert.assertNotNull("leaf 应有 cachedLayout", leafBox);
        Assert.assertEquals("leaf 宽度应钉死为 18（压过无文本叶 fill + STRETCH 豁免）",
                18, leafBox.getWidth());
    }

    /**
     * T2 固定宽生效（容器）：容器含一子节点并 setPreferredWidth(16)，
     * 断言 width==16，压过「容器 fill 铺满」决策（checkbox box 核心断言）。
     */
    @Test
    public void preferredWidthShouldPinContainerWidth() {
        SceneNode root = new SceneNode();
        SceneNode container = new SceneNode();
        SceneNode child = new SceneNode();
        child.setText("A");
        container.setPreferredWidth(16);   // 容器原本 fill=outerWidth=200
        container.appendChild(child);
        root.appendChild(container);

        engine.layout(root, new Constraints(200));

        LayoutBox containerBox = (LayoutBox) container.getCachedLayout();
        Assert.assertNotNull("container 应有 cachedLayout", containerBox);
        Assert.assertEquals("container 宽度应钉死为 16（压过容器 fill + STRETCH 豁免）",
                16, containerBox.getWidth());
    }

    /**
     * T3 固定宽高方块：setPreferredWidth(16)+setPreferredHeight(16)，断言 16×16。
     */
    @Test
    public void preferredWidthAndHeightShouldFormFixedSquare() {
        SceneNode root = new SceneNode();
        SceneNode leaf = new SceneNode();
        leaf.setPreferredWidth(16);
        leaf.setPreferredHeight(16);
        root.appendChild(leaf);

        engine.layout(root, new Constraints(200));

        LayoutBox leafBox = (LayoutBox) leaf.getCachedLayout();
        Assert.assertNotNull("leaf 应有 cachedLayout", leafBox);
        Assert.assertEquals("leaf 宽度=16", 16, leafBox.getWidth());
        Assert.assertEquals("leaf 高度=16", 16, leafBox.getHeight());
    }

    /**
     * T4 STRETCH 豁免（最关键，成对断言）：父 ROW + crossAxisAlign STRETCH（默认），
     * 一个高兄弟（3 行=48）把 crossAvail 抬到 48。
     * <ul>
     *   <li>exemptChild 设 preferredHeight(18)：豁免 STRETCH，height==18 不被改写。</li>
     *   <li>plainChild 不设 preferred：被 STRETCH 改写为 crossAvail==48。</li>
     * </ul>
     * 两断言成对，缺一不可——这是本能力正确性的唯一硬验收点。
     */
    @Test
    public void stretchShouldExemptChildWithPreferredCrossSize() {
        SceneNode root = new SceneNode();
        SceneNode parent = new SceneNode();
        parent.setFlexDirection(FlexDirection.ROW);   // cross=height；crossAxisAlign 默认 STRETCH

        SceneNode tallSibling = new SceneNode();
        tallSibling.setText("A\nB\nC");               // 3 行 × 16 = 48，抬高 crossMax

        SceneNode exemptChild = new SceneNode();
        exemptChild.setPreferredHeight(18);           // 显式 cross 尺寸 → 应豁免

        SceneNode plainChild = new SceneNode();       // 无 preferred → 应被 STRETCH 改写

        parent.appendChild(tallSibling);
        parent.appendChild(exemptChild);
        parent.appendChild(plainChild);
        root.appendChild(parent);

        engine.layout(root, new Constraints(300));

        LayoutBox exemptBox = (LayoutBox) exemptChild.getCachedLayout();
        LayoutBox plainBox = (LayoutBox) plainChild.getCachedLayout();

        // 断言①：有显式 preferredHeight 的子节点豁免 STRETCH，保持 18 不被拉满到 48
        Assert.assertEquals("exemptChild 高度应保持 18（STRETCH 豁免）", 18, exemptBox.getHeight());
        // 断言②（对照组）：无 preferred 的子节点被 STRETCH 改写为 crossAvail=48
        Assert.assertEquals("plainChild 高度应被 STRETCH 改写为 crossAvail=48", 48, plainBox.getHeight());
    }

    /**
     * T5 thumb 推位：track 容器 ROW + preferredWidth(48)+preferredHeight(24)，
     * thumb 子 preferredWidth(18)+preferredHeight(18)，mainAxisAlign END → thumb 落右侧
     * （thumb.x+18 == 48）；切 START → thumb.x==0 落左侧。
     */
    @Test
    public void thumbShouldBePushedByMainAxisAlignInFixedTrack() {
        SceneNode root = new SceneNode();
        SceneNode track = new SceneNode();
        track.setFlexDirection(FlexDirection.ROW);
        track.setPreferredWidth(48);
        track.setPreferredHeight(24);
        track.setMainAxisAlign(MainAxisAlign.END);

        SceneNode thumb = new SceneNode();
        thumb.setPreferredWidth(18);
        thumb.setPreferredHeight(18);

        track.appendChild(thumb);
        root.appendChild(track);

        engine.layout(root, new Constraints(200));

        LayoutBox trackBox = (LayoutBox) track.getCachedLayout();
        LayoutBox thumbBox = (LayoutBox) thumb.getCachedLayout();
        Assert.assertEquals("track 宽度=48", 48, trackBox.getWidth());
        Assert.assertEquals("END：thumb.x = 48-18 = 30", 30, thumbBox.getX());
        Assert.assertEquals("END：thumb 右缘贴 track 右缘（30+18=48）",
                48, thumbBox.getX() + thumbBox.getWidth());

        // 切 START：thumb 落左侧 x=0
        track.setMainAxisAlign(MainAxisAlign.START);
        engine.layout(root, new Constraints(200));
        LayoutBox thumbBox2 = (LayoutBox) thumb.getCachedLayout();
        Assert.assertEquals("START：thumb.x=0 落左侧", 0, thumbBox2.getX());
    }

    /**
     * T6 I7 零重排：稳定布局后改无关 PAINT 级属性（背景色），再 layout，
     * 断言 __getRelayoutCount()==0（PAINT 级变化不触发布局重排）；
     * 再断言 preferredWidth 不变的连续两帧第二帧 relayoutCount 仍为 0。
     */
    @Test
    public void paintLevelChangeShouldNotTriggerRelayoutWithPreferredWidth() {
        SceneNode root = new SceneNode();
        SceneNode leaf = new SceneNode();
        leaf.setText("X");
        leaf.setPreferredWidth(20);
        root.appendChild(leaf);

        Constraints c = new Constraints(200);
        engine.layout(root, c);   // 帧 1：首次布局
        engine.layout(root, c);   // 帧 2：稳定
        Assert.assertEquals("稳定后第二帧零重排", 0, engine.__getRelayoutCount());

        // 改无关 PAINT 级属性（背景色），不应触发布局重排
        leaf.setBackgroundColor(0xFFFF0000);
        engine.layout(root, c);
        Assert.assertEquals("PAINT 级背景色变化不触发布局重排（I7）", 0, engine.__getRelayoutCount());

        // preferredWidth 不变的连续两帧，第二帧零重排
        engine.layout(root, c);
        Assert.assertEquals("preferredWidth 不变连续帧第二帧零重排", 0, engine.__getRelayoutCount());
    }

    /**
     * T7 回退：不设 preferredWidth 时宽度走原决策，证零回归。
     * <ul>
     *   <li>容器（root 有子节点）：宽=outerWidth=200（容器 fill）。</li>
     *   <li>文本叶（ROW 父，shrink-to-fit 可观测）：宽=2*8=16。</li>
     * </ul>
     */
    @Test
    public void withoutPreferredWidthShouldFallBackToOriginalDecision() {
        // 场景①：容器 fill = outerWidth
        SceneNode containerRoot = new SceneNode();
        SceneNode child = new SceneNode();
        child.setText("A");
        containerRoot.appendChild(child);
        engine.layout(containerRoot, new Constraints(200));
        LayoutBox containerBox = (LayoutBox) containerRoot.getCachedLayout();
        Assert.assertEquals("无 preferredWidth 容器宽=outerWidth=200", 200, containerBox.getWidth());

        // 场景②：ROW 下文本叶 shrink-to-fit = 16（主轴宽不被 STRETCH 改写）
        SceneNode rowRoot = new SceneNode();
        rowRoot.setFlexDirection(FlexDirection.ROW);
        SceneNode textLeaf = new SceneNode();
        textLeaf.setText("AB");   // 2 字符 × 8 = 16
        rowRoot.appendChild(textLeaf);
        engine.layout(rowRoot, new Constraints(200));
        LayoutBox textLeafBox = (LayoutBox) textLeaf.getCachedLayout();
        Assert.assertEquals("无 preferredWidth 文本叶 shrink-to-fit 宽=16", 16, textLeafBox.getWidth());
    }

    /**
     * T8 几何闸门：固定尺寸的干净子节点被「脏父」重新定位时，若计算出的盒值不变，
     * 几何闸门 {@code newBox.equals(old)} 命中 → 子节点 LayoutBox 引用复用（assertSame）、
     * 不产生无谓的缓存替换与几何脏。
     *
     * <p>构造：ROW 父 + 固定 16×16 子（preferredWidth/Height 各 16），START 主轴对齐。
     * 稳定后只把<b>父</b>的 preferredWidth 由 0 扩到 100（只标父 selfLayoutDirty，子保持干净）。
     * 父重排时重新定位子：START 下子仍落 x=0、cross 维度因子有 preferredHeight 而 STRETCH 豁免、
     * 尺寸 16×16 不变 → 子盒值完全不变。验证子 LayoutBox 引用被复用、子不在重算集合、子无几何脏。
     * 这正是几何闸门「值不变即复用引用」的语义，且全程绝不向下递归标脏（I7）。</p>
     */
    @Test
    public void cleanFixedChildShouldReuseBoxWhenDirtyParentRelayouts() {
        SceneNode root = new SceneNode();
        SceneNode parent = new SceneNode();
        parent.setFlexDirection(FlexDirection.ROW);   // cross=height
        // 主轴默认 START → 子始终落 x=0，父扩宽不改变子位置
        SceneNode child = new SceneNode();
        child.setPreferredWidth(16);
        child.setPreferredHeight(16);
        parent.appendChild(child);
        root.appendChild(parent);

        Constraints c = new Constraints(200);
        engine.layout(root, c);
        engine.layout(root, c);   // 稳定
        LayoutBox childBox1 = (LayoutBox) child.getCachedLayout();
        Assert.assertNotNull("child 应有 cachedLayout", childBox1);
        Assert.assertEquals("child 宽=16", 16, childBox1.getWidth());
        Assert.assertEquals("child 高=16", 16, childBox1.getHeight());
        Assert.assertEquals("child x=0（START）", 0, childBox1.getX());

        // 只标脏父：preferredWidth 0→100（子保持干净）
        parent.setPreferredWidth(100);
        engine.layout(root, c);

        LayoutBox parentBox = (LayoutBox) parent.getCachedLayout();
        Assert.assertEquals("parent 宽应钉死为 100", 100, parentBox.getWidth());

        LayoutBox childBox2 = (LayoutBox) child.getCachedLayout();
        // 子盒值完全不变（仍 0,0,16,16）→ 几何闸门命中 → 引用复用
        Assert.assertSame("干净子盒值不变时 LayoutBox 引用应被复用（几何闸门）", childBox1, childBox2);
        // 子不在重算集合（只有父因自身脏被重算）
        Assert.assertFalse("child 不在重算集合（未被向下标脏）",
                engine.__getRelayoutedNodes().contains(child));
        Assert.assertTrue("parent 在重算集合", engine.__getRelayoutedNodes().contains(parent));
    }

    /**
     * T9 固定宽容器约束子节点不溢出（基准对齐回归）：固定宽容器 C（preferredWidth=48）
     * 的「依赖约束宽」子节点 D（无 preferredWidth、无文本，纯色块）应按父<b>解析盒内宽</b>（48）
     * 布局，而非父<b>裸约束宽</b>（200），否则 D 宽算成 200 溢出 48 盒右边界 152px。
     *
     * <p>缺陷锚点：layoutInternal 给子的 childConstraints 与 performLayout 排子的 innerWidth
     * 必须用同一盒宽基准 {@code computeWidth(node, constraints)}。两者分裂时本测试复现溢出。</p>
     */
    @Test
    public void fixedWidthContainerShouldConstrainChildWidthNoOverflow() {
        // C 作为 root 直接 layout：C 自身盒宽走 computeWidth=preferredWidth=48
        SceneNode c = new SceneNode();
        c.setFlexDirection(FlexDirection.ROW);
        c.setPreferredWidth(48);

        // D：无 preferredWidth、无文本（纯色块）→ computeWidth 返回约束宽；
        // 给 preferredHeight(10) 使其有可见高度（ROW 下 STRETCH 对 cross=高豁免，保 10）
        SceneNode d = new SceneNode();
        d.setPreferredHeight(10);
        c.appendChild(d);

        engine.layout(c, new Constraints(200));

        LayoutBox cBox = (LayoutBox) c.getCachedLayout();
        LayoutBox dBox = (LayoutBox) d.getCachedLayout();
        Assert.assertNotNull("C 应有 cachedLayout", cBox);
        Assert.assertNotNull("D 应有 cachedLayout", dBox);

        Assert.assertEquals("C 宽应钉死为 48", 48, cBox.getWidth());
        // 关键：D 宽=父盒内宽 48，而非父裸约束宽 200
        Assert.assertEquals("D 宽应为父盒内宽 48（非裸约束宽 200）", 48, dBox.getWidth());
        // D 不溢出父右边界
        Assert.assertTrue("D 右缘不溢出父盒（D.x+D.width <= C.width）",
                dBox.getX() + dBox.getWidth() <= cBox.getWidth());
    }

    // ============================================================
    // 深层约束下沉系列：让非 root 深层容器也能 fillParentHeight 拿到父可用高度
    // ============================================================

    /**
     * 深层 fill 子节点穿过干净中间层拿到父高（核心正例）。
     *
     * <p>树：root(ROW,fill) → panel(ROW,fill) → fillChild(ROW,fill)；
     * root 另挂 deco=叶 setText("X")（不 fill）。layout(root,(200,100))。
     * 断言 fillChild 高度==100（深层拿父高，改前因约束高不下传会 fail=16）；
     * deco 高度==16（装饰兄弟仍 shrink，未被污染）。</p>
     */
    @Test
    public void depthFillChildGetsParentHeightThroughCleanMiddle() {
        SceneNode root = new SceneNode();
        root.setFlexDirection(FlexDirection.ROW);
        root.setFillParentHeight(true);
        // 交叉轴 START，规避默认 STRETCH 把矮装饰兄弟拉满到高 panel 的混淆，
        // 使 deco 显示其内在 shrink 高度，纯净验证「约束下传不污染非 fill 兄弟内在高」。
        root.setCrossAxisAlign(CrossAxisAlign.START);

        SceneNode panel = new SceneNode();
        panel.setFlexDirection(FlexDirection.ROW);
        panel.setFillParentHeight(true);

        SceneNode fillChild = new SceneNode();
        fillChild.setFlexDirection(FlexDirection.ROW);
        fillChild.setFillParentHeight(true);

        panel.appendChild(fillChild);
        root.appendChild(panel);

        // 装饰兄弟：纯文本叶，不 fill
        SceneNode deco = new SceneNode();
        deco.setText("X");
        root.appendChild(deco);

        engine.layout(root, new Constraints(200, 100));

        LayoutBox fillChildBox = (LayoutBox) fillChild.getCachedLayout();
        LayoutBox decoBox = (LayoutBox) deco.getCachedLayout();
        Assert.assertNotNull("fillChild 应有 cachedLayout", fillChildBox);
        Assert.assertNotNull("deco 应有 cachedLayout", decoBox);
        Assert.assertEquals("深层 fillChild 高度应拿到父高 100", 100, fillChildBox.getHeight());
        Assert.assertEquals("装饰兄弟 deco 仍 shrink 高度=16", 16, decoBox.getHeight());
    }

    /**
     * 干净装饰兄弟在约束高变化时绝不被重算（反证 I7 红线）。
     *
     * <p>同上树先 layout(root,(200,100)) 跑干净，再 layout(root,(200,200))（仅改高）。
     * 断言 deco 自身未脏、不在 relayoutedNodes、不在 constraintRelayoutedNodes；
     * fillChild 在 constraintRelayoutedNodes（因约束高变化被迫重算）；fillChild 高度==200。</p>
     */
    @Test
    public void cleanDecoSiblingNeverRelayoutedOnConstraintChange() {
        SceneNode root = new SceneNode();
        root.setFlexDirection(FlexDirection.ROW);
        root.setFillParentHeight(true);

        SceneNode panel = new SceneNode();
        panel.setFlexDirection(FlexDirection.ROW);
        panel.setFillParentHeight(true);

        SceneNode fillChild = new SceneNode();
        fillChild.setFlexDirection(FlexDirection.ROW);
        fillChild.setFillParentHeight(true);

        panel.appendChild(fillChild);
        root.appendChild(panel);

        SceneNode deco = new SceneNode();
        deco.setText("X");
        root.appendChild(deco);

        // 第一次：跑干净
        engine.layout(root, new Constraints(200, 100));

        // 第二次：仅改高 100→200
        engine.layout(root, new Constraints(200, 200));

        Assert.assertFalse("deco 自身未脏", deco.__isSelfLayoutDirty());
        Assert.assertFalse("deco 不在 relayoutedNodes",
                engine.__getRelayoutedNodes().contains(deco));
        Assert.assertFalse("deco 不在 constraintRelayoutedNodes",
                engine.__getConstraintRelayoutedNodes().contains(deco));
        Assert.assertTrue("fillChild 在 constraintRelayoutedNodes（约束高变化被迫重算）",
                engine.__getConstraintRelayoutedNodes().contains(fillChild));

        LayoutBox fillChildBox = (LayoutBox) fillChild.getCachedLayout();
        Assert.assertEquals("约束变化后 fillChild 高度=200", 200, fillChildBox.getHeight());
    }

    /**
     * COLUMN 容器禁主轴 fill 下传（反证 COLUMN 高度恒 UNCONSTRAINED）。
     *
     * <p>树：root(COLUMN,fill) → a(COLUMN,fill)→leaf("A"), b(COLUMN,fill)→leaf("B")。
     * layout(root,(200,100))。断言 a 高度==16、b 高度==16（COLUMN 不向下传主轴高，
     * 子按内容 shrink），root 高度==100（root 自身 fill 仍吃约束高）。</p>
     */
    @Test
    public void columnFillChildrenDoNotOverflowParent() {
        SceneNode root = new SceneNode();
        root.setFlexDirection(FlexDirection.COLUMN);
        root.setFillParentHeight(true);

        SceneNode a = new SceneNode();
        a.setFlexDirection(FlexDirection.COLUMN);
        a.setFillParentHeight(true);
        SceneNode leafA = new SceneNode();
        leafA.setText("A");
        a.appendChild(leafA);

        SceneNode b = new SceneNode();
        b.setFlexDirection(FlexDirection.COLUMN);
        b.setFillParentHeight(true);
        SceneNode leafB = new SceneNode();
        leafB.setText("B");
        b.appendChild(leafB);

        root.appendChild(a);
        root.appendChild(b);

        engine.layout(root, new Constraints(200, 100));

        LayoutBox aBox = (LayoutBox) a.getCachedLayout();
        LayoutBox bBox = (LayoutBox) b.getCachedLayout();
        LayoutBox rootBox = (LayoutBox) root.getCachedLayout();
        Assert.assertEquals("COLUMN 子 a 不溢出，高度=16", 16, aBox.getHeight());
        Assert.assertEquals("COLUMN 子 b 不溢出，高度=16", 16, bBox.getHeight());
        Assert.assertEquals("root 自身 fill 高度=100", 100, rootBox.getHeight());
    }

    /**
     * 约束完全不变时仍整棵跳过（零回归反证）。
     *
     * <p>layout(root,(200,100)) 两次完全相同约束。断言第二次后 relayoutCount==0、
     * constraintRelayoutedNodes 为空（约束未变 → childConstraintsWouldChange/
     * selfConsumesConstraint 均短路 false → 整棵安全跳过）。</p>
     */
    @Test
    public void unchangedConstraintStillFullSkip() {
        SceneNode root = new SceneNode();
        root.setFlexDirection(FlexDirection.ROW);
        root.setFillParentHeight(true);

        SceneNode panel = new SceneNode();
        panel.setFlexDirection(FlexDirection.ROW);
        panel.setFillParentHeight(true);

        SceneNode fillChild = new SceneNode();
        fillChild.setFlexDirection(FlexDirection.ROW);
        fillChild.setFillParentHeight(true);

        panel.appendChild(fillChild);
        root.appendChild(panel);

        Constraints c = new Constraints(200, 100);

        // 第一次：跑干净
        engine.layout(root, c);

        // 第二次：完全相同约束
        engine.layout(root, c);

        Assert.assertEquals("相同约束第二次 relayoutCount=0", 0, engine.__getRelayoutCount());
        Assert.assertTrue("相同约束第二次 constraintRelayoutedNodes 为空",
                engine.__getConstraintRelayoutedNodes().isEmpty());
    }

    // ============================================================
    // 深层约束下沉系列（补充）：oracle 复审挖出的角落缺陷与边界固化
    // ============================================================

    /**
     * 覆盖缺陷 A：约束失去高度约束时深层 fill 叶节点不陈旧（回退 shrink）。
     *
     * <p>root(ROW,fill)→panel(ROW,fill)→fillChild(ROW,fill,无子无文本)。
     * 先 layout(root,(200,100)) 断言 fillChild 高=100；再 layout(root,(200))
     * （单参=高 UNCONSTRAINED）。fillChild selfConsumesConstraint 因上帧有高而触发重算，
     * 高度应回退到 shrink 自然高（无子无文本 → content=0），证明不陈旧停在 100。</p>
     */
    @Test
    public void deepFillChildFallsBackWhenConstraintLosesHeight() {
        SceneNode root = new SceneNode();
        root.setFlexDirection(FlexDirection.ROW);
        root.setFillParentHeight(true);

        SceneNode panel = new SceneNode();
        panel.setFlexDirection(FlexDirection.ROW);
        panel.setFillParentHeight(true);

        SceneNode fillChild = new SceneNode();
        fillChild.setFlexDirection(FlexDirection.ROW);
        fillChild.setFillParentHeight(true);

        panel.appendChild(fillChild);
        root.appendChild(panel);

        // 第一次：有高约束 → fillChild fill 到 100
        engine.layout(root, new Constraints(200, 100));
        LayoutBox box1 = (LayoutBox) fillChild.getCachedLayout();
        Assert.assertEquals("有高约束时 fillChild 高=100", 100, box1.getHeight());

        // 第二次：高度 UNCONSTRAINED → fillChild 应回退 shrink（无子无文本 → 0）
        engine.layout(root, new Constraints(200));
        LayoutBox box2 = (LayoutBox) fillChild.getCachedLayout();
        Assert.assertEquals("失高后 fillChild 应回退 shrink=0（不陈旧停在 100）", 0, box2.getHeight());
    }

    /**
     * 覆盖缺陷 B：fill + preferredHeight 大于约束高时，子 fill 拿到 max 后的父内高（无留白）。
     *
     * <p>root(ROW,fill,preferredHeight=300)→inner(ROW,fill)→leaf。layout(root,(200,100))，
     * padding 全 0。root 自身高取 max(content_含preferredHeight, 约束高)=300（preferredHeight 压过约束高）；
     * inner 下传时 priorKnownInnerHeight 取 max(约束高100, root.preferredHeight300)=300，
     * 故 inner fill 到 300，与 root 内高一致、无底部留白。</p>
     */
    @Test
    public void fillWithPreferredHeightChildFillsToMaxNoGap() {
        SceneNode root = new SceneNode();
        root.setFlexDirection(FlexDirection.ROW);
        root.setFillParentHeight(true);
        root.setPreferredHeight(300);

        SceneNode inner = new SceneNode();
        inner.setFlexDirection(FlexDirection.ROW);
        inner.setFillParentHeight(true);

        SceneNode leaf = new SceneNode();
        inner.appendChild(leaf);
        root.appendChild(inner);

        engine.layout(root, new Constraints(200, 100));

        LayoutBox rootBox = (LayoutBox) root.getCachedLayout();
        LayoutBox innerBox = (LayoutBox) inner.getCachedLayout();
        Assert.assertEquals("root 高=300（preferredHeight 压过约束高100）", 300, rootBox.getHeight());
        Assert.assertEquals("inner fill 到父内高 300（无留白，口径与 computeHeight 对齐）",
                300, innerBox.getHeight());
    }

    /**
     * 固化有意行为：COLUMN 中间层截断主轴 fill 高下传，深层 leaf 回退 shrink。
     *
     * <p>root(ROW,fill)→mid(COLUMN,fill)→leaf(ROW,fill,文本"X")。layout(root,(200,100))。
     * mid 是 COLUMN，buildChildConstraints 恒下传 UNCONSTRAINED（禁主轴 fill 下传），
     * leaf 拿不到父高 → 回退 shrink=16。这是 YAGNI 边界（COLUMN 主轴 fill 需 flex-grow
     * 求解器，本期不支持），是预期行为而非 bug。</p>
     */
    @Test
    public void columnMiddleLayerTruncatesFillDownpass() {
        SceneNode root = new SceneNode();
        root.setFlexDirection(FlexDirection.ROW);
        root.setFillParentHeight(true);

        SceneNode mid = new SceneNode();
        mid.setFlexDirection(FlexDirection.COLUMN);
        mid.setFillParentHeight(true);

        SceneNode leaf = new SceneNode();
        leaf.setFlexDirection(FlexDirection.ROW);
        leaf.setFillParentHeight(true);
        leaf.setText("X");

        mid.appendChild(leaf);
        root.appendChild(mid);

        engine.layout(root, new Constraints(200, 100));

        LayoutBox leafBox = (LayoutBox) leaf.getCachedLayout();
        Assert.assertEquals("COLUMN 中间层截断下传，leaf 回退 shrink=16", 16, leafBox.getHeight());
    }

    /**
     * 固化有意行为：非 fill 且无 preferredHeight 的中间层高不先验确定，截断下传。
     *
     * <p>root(ROW,fill)→panel(ROW,不fill,无preferredHeight)→fillChild(ROW,fill,文本"Y")。
     * layout(root,(200,100))。panel priorKnownInnerHeight 返回 UNCONSTRAINED（既非 fill
     * 也无 preferredHeight），不下传高 → fillChild 断链回退 shrink=16。</p>
     */
    @Test
    public void nonFillMiddleLayerTruncatesFillDownpass() {
        SceneNode root = new SceneNode();
        root.setFlexDirection(FlexDirection.ROW);
        root.setFillParentHeight(true);

        SceneNode panel = new SceneNode();
        panel.setFlexDirection(FlexDirection.ROW);
        // 不 fill、无 preferredHeight → 高不先验确定

        SceneNode fillChild = new SceneNode();
        fillChild.setFlexDirection(FlexDirection.ROW);
        fillChild.setFillParentHeight(true);
        fillChild.setText("Y");

        panel.appendChild(fillChild);
        root.appendChild(panel);

        engine.layout(root, new Constraints(200, 100));

        LayoutBox fillChildBox = (LayoutBox) fillChild.getCachedLayout();
        Assert.assertEquals("非fill中间层高不先验确定，fillChild 断链回退 shrink=16",
                16, fillChildBox.getHeight());
    }

    /**
     * 端到端：深层约束重算产出几何脏标记，供 paint 阶段感知。
     *
     * <p>root(ROW,fill)→panel(ROW,fill)→fillChild(ROW,fill)。先 layout(root,(200,100)) 跑干净；
     * 再 layout(root,(200,200))。fillChild 自身盒高 100→200，performLayout 步骤5 几何闸门
     * 命中（盒值变化）→ markGeometryDirty。layout 阶段不清几何脏（仅 paint 清），
     * 故此时 fillChild.__isSelfGeometryDirty()==true，且 LayoutBox 引用被替换、高度不同。</p>
     */
    @Test
    public void deepConstraintRelayoutProducesGeometryDirty() {
        SceneNode root = new SceneNode();
        root.setFlexDirection(FlexDirection.ROW);
        root.setFillParentHeight(true);

        SceneNode panel = new SceneNode();
        panel.setFlexDirection(FlexDirection.ROW);
        panel.setFillParentHeight(true);

        SceneNode fillChild = new SceneNode();
        fillChild.setFlexDirection(FlexDirection.ROW);
        fillChild.setFillParentHeight(true);

        panel.appendChild(fillChild);
        root.appendChild(panel);

        // 第一次：跑干净并经 paint 清掉几何脏（模拟真实 layout→paint 流程）
        engine.layout(root, new Constraints(200, 100));
        paintEngine.paint(root);
        LayoutBox box1 = (LayoutBox) fillChild.getCachedLayout();
        Assert.assertEquals("首次 fillChild 高=100", 100, box1.getHeight());
        Assert.assertFalse("paint 后 fillChild 几何脏已清", fillChild.__isSelfGeometryDirty());

        // 第二次：约束高 100→200，深层 fillChild 被迫重算
        engine.layout(root, new Constraints(200, 200));
        LayoutBox box2 = (LayoutBox) fillChild.getCachedLayout();

        Assert.assertEquals("重算后 fillChild 高=200", 200, box2.getHeight());
        Assert.assertNotSame("LayoutBox 引用被替换（盒值变化）", box1, box2);
        Assert.assertTrue("fillChild 几何脏=true（供 paint 阶段感知）",
                fillChild.__isSelfGeometryDirty());
    }
}
