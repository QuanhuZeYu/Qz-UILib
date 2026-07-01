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

    private final FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
    private final SceneLayoutEngine engine = new SceneLayoutEngine(measurer);
    private final ScenePaintEngine paintEngine = new ScenePaintEngine(measurer);

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
        LayoutResult result = engine.layout(root, new Constraints(100));

        // I7 铁证：A 和 C 的 LayoutBox 引用应不变（未被重算，即未进入 performLayout）
        LayoutBox boxA2 = (LayoutBox) a.getCachedLayout();
        LayoutBox boxC2 = (LayoutBox) c.getCachedLayout();

        Assert.assertSame("干净兄弟 A 的 box 应被复用（引用相同）", boxA1, boxA2);
        Assert.assertSame("干净兄弟 C 的 box 应被复用（引用相同）", boxC1, boxC2);

        // 只重算了 1 个节点（B）
        Assert.assertEquals("重算次数应为 1", 1, result.getRelayoutCount());
        Set<SceneNode> relayouted = result.getRelayoutedNodes();
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
        LayoutResult result = engine.layout(root, new Constraints(100));
        Assert.assertTrue("首次 layout 重算次数≥1", result.getRelayoutCount() >= 1);

        // 第二次 layout：全树干净，应零重算
        result = engine.layout(root, new Constraints(100));
        Assert.assertEquals("第二次 layout 整棵跳过，重算次数=0", 0, result.getRelayoutCount());
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
        LayoutResult result = engine.layout(root, new Constraints(200));

        // root 和 container 的 selfLayoutDirty 为 false，
        // 它们的 cachedLayout 应被复用（引用不变）
        LayoutBox rootBox2 = (LayoutBox) root.getCachedLayout();
        LayoutBox containerBox2 = (LayoutBox) container.getCachedLayout();

        Assert.assertSame("root box 应复用（引用相同）", rootBox1, rootBox2);
        Assert.assertSame("container box 应复用（引用相同）", containerBox1, containerBox2);

        // leaf 应被重算
        Assert.assertEquals("重算次数=1", 1, result.getRelayoutCount());
        Assert.assertTrue("leaf 在重算集合中", result.getRelayoutedNodes().contains(leaf));
        Assert.assertFalse("root 不在重算集合中", result.getRelayoutedNodes().contains(root));
        Assert.assertFalse("container 不在重算集合中", result.getRelayoutedNodes().contains(container));
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

    /**
     * 显式空文本叶（setText("")）应按文本语义 shrink 到自身 padding 宽，
     * 不能回退成无文本装饰叶的 fill 满宽，否则 TextInput 空 prefix 会把 caret 推到右侧。
     */
    @Test
    public void emptyTextLeafShouldShrinkToPaddingWidth() {
        SceneNode root = SceneNode.row();
        SceneNode emptyText = new SceneNode();
        emptyText.setText("");
        emptyText.setPadding(0, 3, 0, 5);
        root.appendChild(emptyText);

        engine.layout(root, new Constraints(100));

        LayoutBox emptyBox = (LayoutBox) emptyText.getCachedLayout();
        Assert.assertNotNull("空文本叶应有 cachedLayout", emptyBox);
        Assert.assertEquals("空文本叶宽度=paddingH=8", 8, emptyBox.getWidth());
    }

    /**
     * 未设置文本的叶节点（text == null）保留装饰/矩形语义，仍按可用宽 fill，
     * 防止空文本修复破坏 checkbox box、slider track 等纯装饰节点。
     */
    @Test
    public void nullTextLeafShouldStillFillAvailableWidth() {
        SceneNode root = SceneNode.row();
        SceneNode deco = new SceneNode();
        root.appendChild(deco);

        engine.layout(root, new Constraints(100));

        LayoutBox decoBox = (LayoutBox) deco.getCachedLayout();
        Assert.assertNotNull("无文本装饰叶应有 cachedLayout", decoBox);
        Assert.assertEquals("无文本装饰叶宽度保持 fill=100", 100, decoBox.getWidth());
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
        LayoutResult result = engine.layout(root, new Constraints(200));

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
        Assert.assertEquals("重算次数=1（仅 leaf 自身）", 1, result.getRelayoutCount());
        Assert.assertTrue("leaf 在重算集合", result.getRelayoutedNodes().contains(leaf));
        Assert.assertFalse("container 不在重算集合", result.getRelayoutedNodes().contains(container));
        Assert.assertFalse("root 不在重算集合", result.getRelayoutedNodes().contains(root));
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
     * scrollable 节点未设置 preferredHeight/fillParentHeight 时，内容超过高度约束应按约束 cap。
     */
    @Test
    public void scrollableWithoutFillOrPreferredShouldCapToConstraintWhenContentExceeds() {
        SceneNode root = new SceneNode();
        root.setScrollable(true);
        SceneNode child = new SceneNode();
        child.setPreferredHeight(200);
        root.appendChild(child);

        engine.layout(root, new Constraints(200, 100));

        LayoutBox rootBox = (LayoutBox) root.getCachedLayout();
        Assert.assertEquals("scrollable 内容超出时高度应被约束 cap 到 100", 100, rootBox.getHeight());
    }

    /**
     * scrollable 节点未设置 preferredHeight/fillParentHeight 时，内容低于高度约束应包住内容。
     */
    @Test
    public void scrollableWithoutFillOrPreferredShouldReturnContentWhenBelowConstraint() {
        SceneNode root = new SceneNode();
        root.setScrollable(true);
        SceneNode child = new SceneNode();
        child.setPreferredHeight(50);
        root.appendChild(child);

        engine.layout(root, new Constraints(200, 100));

        LayoutBox rootBox = (LayoutBox) root.getCachedLayout();
        Assert.assertEquals("scrollable 内容低于约束时高度应为内容高 50", 50, rootBox.getHeight());
    }

    /**
     * scrollable 节点未设置 preferredHeight/fillParentHeight 且无高度约束时，应返回内容高度。
     */
    @Test
    public void scrollableWithoutFillOrPreferredShouldReturnContentWhenUnconstrained() {
        SceneNode root = new SceneNode();
        root.setScrollable(true);
        SceneNode child = new SceneNode();
        child.setPreferredHeight(50);
        root.appendChild(child);

        engine.layout(root, new Constraints(200));

        LayoutBox rootBox = (LayoutBox) root.getCachedLayout();
        Assert.assertEquals("scrollable 无高度约束时高度应为内容高 50", 50, rootBox.getHeight());
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
        LayoutResult result = engine.layout(root, new Constraints(200, 200));

        LayoutBox rootBox2 = (LayoutBox) root.getCachedLayout();
        Assert.assertEquals("约束变化后 root 高度=200", 200, rootBox2.getHeight());
        // root 因为被约束变化感知标脏，应出现在重算集合中
        Assert.assertTrue("约束变化后应有重算", result.getRelayoutCount() >= 1);
        Assert.assertTrue("root 在重算集合中", result.getRelayoutedNodes().contains(root));
    }

    /**
     * scrollable 回退分支3在约束高度变化时必须重算自身，避免复用陈旧 cap 高度。
     */
    @Test
    public void scrollableBranch3ShouldRecomputeOnConstraintHeightChange() {
        SceneNode root = new SceneNode();
        root.setScrollable(true);
        SceneNode child = new SceneNode();
        child.setPreferredHeight(50);
        root.appendChild(child);

        // 第一次 layout：内容高 50，小于约束高 100，cap 不触发。
        engine.layout(root, new Constraints(200, 100));
        LayoutBox rootBox1 = (LayoutBox) root.getCachedLayout();
        Assert.assertEquals("首次 scrollable 高度=内容高 50", 50, rootBox1.getHeight());
        Assert.assertFalse("首次后 root selfLayoutDirty=false", root.__isSelfLayoutDirty());

        // 第二次 layout：同一干净节点收到约束高 30，应突破跳过并按 cap 重算为 30。
        engine.layout(root, new Constraints(200, 30));

        LayoutBox rootBox2 = (LayoutBox) root.getCachedLayout();
        Assert.assertEquals("约束降低后 scrollable 高度应 cap 到 30", 30, rootBox2.getHeight());
    }

    /**
     * 约束不变不过度失效：连续两次相同 Constraints(W,100) 的 fill root，
     * 第二次 result.getRelayoutCount() 应为 0（I7 整棵跳过）。
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
        LayoutResult result = engine.layout(root, c);

        Assert.assertEquals("相同约束第二次重算=0", 0, result.getRelayoutCount());
    }

    /**
     * I7 兄弟跳过保持：fill root 下挂干净非 fill 兄弟，约束不变时
     * 它们 LayoutBox assertSame 复用、不在 result.getRelayoutedNodes() 中。
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
        LayoutResult result = engine.layout(root, c200x100);

        // 兄弟节点 LayoutBox 应复用
        LayoutBox boxA2 = (LayoutBox) a.getCachedLayout();
        LayoutBox boxB2 = (LayoutBox) b.getCachedLayout();
        LayoutBox boxC2 = (LayoutBox) c.getCachedLayout();

        Assert.assertSame("干净兄弟 A 的 box 应复用", boxA1, boxA2);
        Assert.assertSame("干净兄弟 B 的 box 应复用", boxB1, boxB2);
        Assert.assertSame("干净兄弟 C 的 box 应复用", boxC1, boxC2);

        // 不在重算集合中
        Set<SceneNode> relayouted = result.getRelayoutedNodes();
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
        LayoutResult result = engine.layout(root, new Constraints(200));

        Set<SceneNode> relayouted = result.getRelayoutedNodes();
        Assert.assertTrue("root 在重算集合", relayouted.contains(root));
        Assert.assertFalse("a 不在重算集合", relayouted.contains(a));
        Assert.assertFalse("b 不在重算集合", relayouted.contains(b));
        Assert.assertFalse("c 不在重算集合", relayouted.contains(c));
        Assert.assertEquals("重算次数=1（仅 root）", 1, result.getRelayoutCount());
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
        LayoutResult result = engine.layout(root, new Constraints(200));

        Set<SceneNode> relayouted = result.getRelayoutedNodes();
        Assert.assertFalse("a 不在重算集合", relayouted.contains(a));
        Assert.assertFalse("b 不在重算集合", relayouted.contains(b));
        Assert.assertEquals("重算次数=1（仅 root）", 1, result.getRelayoutCount());

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
        SceneNode root = SceneNode.row();
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
        SceneNode root = SceneNode.row();
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
        SceneNode root = SceneNode.row();
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
        LayoutResult result = epochEngine.layout(root, constraints);
        Assert.assertEquals("epoch/约束不变第二帧零重算", 0, result.getRelayoutCount());

        // bump epoch 模拟字体运行时变化 → 第三帧应使文本叶失效重测
        stub.bumpEpoch();
        result = epochEngine.layout(root, constraints);

        // 文本叶被重算（epoch 失效链向上冒泡标脏）
        Assert.assertTrue("epoch 变化后文本叶应被重算",
                result.getRelayoutedNodes().contains(textLeaf));
        // root 仅因 descendant 下沉重定位，不计入重算集合（I7：未向下标脏 root 自身）
        Assert.assertFalse("root 不应被计入重算集合（未被向下标脏）",
                result.getRelayoutedNodes().contains(root));
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
        LayoutResult result = epochEngine.layout(root, constraints);
        Assert.assertEquals("稳态第二帧零重算", 0, result.getRelayoutCount());

        // bump epoch → 仅文本叶应失效，无文本叶不应被标脏
        stub.bumpEpoch();
        result = epochEngine.layout(root, constraints);

        Assert.assertTrue("文本叶应被重算", result.getRelayoutedNodes().contains(textLeaf));
        Assert.assertFalse("无文本叶不应被 epoch 失效链标脏",
                result.getRelayoutedNodes().contains(emptyLeaf));
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
        SceneNode parent = SceneNode.row();   // cross=height；crossAxisAlign 默认 STRETCH

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
     * COLUMN 父容器默认 STRETCH 时，SHRINK 子容器应保持自身内容宽，
     * 不被父容器交叉轴拉满。
     */
    @Test
    public void stretchShouldExemptChildWithShrinkWidthSizing() {
        SceneNode root = new SceneNode();
        SceneNode parent = new SceneNode();

        SceneNode wideSibling = new SceneNode();
        wideSibling.setPreferredWidth(32);            // 显式宽度抬高 parent crossAvail

        SceneNode shrinkChild = new SceneNode();
        shrinkChild.setWidthSizing(SceneNode.WidthSizing.SHRINK);
        SceneNode label = new SceneNode();
        label.setText("A");                          // shrinkChild 内容宽 8px
        shrinkChild.appendChild(label);

        parent.appendChild(wideSibling);
        parent.appendChild(shrinkChild);
        root.appendChild(parent);

        engine.layout(root, new Constraints(200));

        LayoutBox wideBox = (LayoutBox) wideSibling.getCachedLayout();
        LayoutBox shrinkBox = (LayoutBox) shrinkChild.getCachedLayout();

        Assert.assertEquals("wideSibling 宽度=32", 32, wideBox.getWidth());
        Assert.assertEquals("SHRINK 子容器保持内容宽 8，不被 STRETCH 拉到 32", 8, shrinkBox.getWidth());
    }

    /**
     * T5 thumb 推位：track 容器 ROW + preferredWidth(48)+preferredHeight(24)，
     * thumb 子 preferredWidth(18)+preferredHeight(18)，mainAxisAlign END → thumb 落右侧
     * （thumb.x+18 == 48）；切 START → thumb.x==0 落左侧。
     */
    @Test
    public void thumbShouldBePushedByMainAxisAlignInFixedTrack() {
        SceneNode root = new SceneNode();
        SceneNode track = SceneNode.row();
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
     * 断言 result.getRelayoutCount()==0（PAINT 级变化不触发布局重排）；
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
        LayoutResult result = engine.layout(root, c);   // 帧 2：稳定
        Assert.assertEquals("稳定后第二帧零重排", 0, result.getRelayoutCount());

        // 改无关 PAINT 级属性（背景色），不应触发布局重排
        leaf.setBackgroundColor(0xFFFF0000);
        result = engine.layout(root, c);
        Assert.assertEquals("PAINT 级背景色变化不触发布局重排（I7）", 0, result.getRelayoutCount());

        // preferredWidth 不变的连续两帧，第二帧零重排
        result = engine.layout(root, c);
        Assert.assertEquals("preferredWidth 不变连续帧第二帧零重排", 0, result.getRelayoutCount());
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
        SceneNode rowRoot = SceneNode.row();
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
        SceneNode parent = SceneNode.row();   // cross=height
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
        LayoutResult result = engine.layout(root, c);

        LayoutBox parentBox = (LayoutBox) parent.getCachedLayout();
        Assert.assertEquals("parent 宽应钉死为 100", 100, parentBox.getWidth());

        LayoutBox childBox2 = (LayoutBox) child.getCachedLayout();
        // 子盒值完全不变（仍 0,0,16,16）→ 几何闸门命中 → 引用复用
        Assert.assertSame("干净子盒值不变时 LayoutBox 引用应被复用（几何闸门）", childBox1, childBox2);
        // 子不在重算集合（只有父因自身脏被重算）
        Assert.assertFalse("child 不在重算集合（未被向下标脏）",
                result.getRelayoutedNodes().contains(child));
        Assert.assertTrue("parent 在重算集合", result.getRelayoutedNodes().contains(parent));
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
        SceneNode c = SceneNode.row();
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
        SceneNode root = SceneNode.row();
        root.setFillParentHeight(true);
        // 交叉轴 START，规避默认 STRETCH 把矮装饰兄弟拉满到高 panel 的混淆，
        // 使 deco 显示其内在 shrink 高度，纯净验证「约束下传不污染非 fill 兄弟内在高」。
        root.setCrossAxisAlign(CrossAxisAlign.START);

        SceneNode panel = SceneNode.row();
        panel.setFillParentHeight(true);

        SceneNode fillChild = SceneNode.row();
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
        SceneNode root = SceneNode.row();
        root.setFillParentHeight(true);

        SceneNode panel = SceneNode.row();
        panel.setFillParentHeight(true);

        SceneNode fillChild = SceneNode.row();
        fillChild.setFillParentHeight(true);

        panel.appendChild(fillChild);
        root.appendChild(panel);

        SceneNode deco = new SceneNode();
        deco.setText("X");
        root.appendChild(deco);

        // 第一次：跑干净
        engine.layout(root, new Constraints(200, 100));

        // 第二次：仅改高 100→200
        LayoutResult result = engine.layout(root, new Constraints(200, 200));

        Assert.assertFalse("deco 自身未脏", deco.__isSelfLayoutDirty());
        Assert.assertFalse("deco 不在 relayoutedNodes",
                result.getRelayoutedNodes().contains(deco));
        Assert.assertFalse("deco 不在 constraintRelayoutedNodes",
                result.getConstraintRelayoutedNodes().contains(deco));
        Assert.assertTrue("fillChild 在 constraintRelayoutedNodes（约束高变化被迫重算）",
                result.getConstraintRelayoutedNodes().contains(fillChild));

        LayoutBox fillChildBox = (LayoutBox) fillChild.getCachedLayout();
        Assert.assertEquals("约束变化后 fillChild 高度=200", 200, fillChildBox.getHeight());
    }

    /**
     * COLUMN 多 grow 子按 effectiveGrow 等权分配父内高（还偏离 2026-06-20 的债）。
     *
     * <p>树：root(COLUMN,fill) → a(COLUMN,fill)→leaf("A"), b(COLUMN,fill)→leaf("B")。
     * layout(root,(200,100))。a/b 各 effectiveGrow=1（fill 隐式），Σw=2，freeH=100，
     * 等权分得各 50。root 仍 fill 到 100，a+b 高度之和=100 不溢出父内高。</p>
     */
    @Test
    public void columnMultipleGrowChildrenSplitInnerHeightEvenly() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode a = SceneNode.column();
        a.setFillParentHeight(true);
        SceneNode leafA = new SceneNode();
        leafA.setText("A");
        a.appendChild(leafA);

        SceneNode b = SceneNode.column();
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
        Assert.assertEquals("COLUMN 多 grow 子 a 等权分得 50", 50, aBox.getHeight());
        Assert.assertEquals("COLUMN 多 grow 子 b 等权分得 50", 50, bBox.getHeight());
        Assert.assertEquals("root 自身 fill 高度=100", 100, rootBox.getHeight());
        int rootInnerHeight = rootBox.getHeight() - root.getPaddingTop() - root.getPaddingBottom();
        Assert.assertTrue("a+b 高度之和不超过父内高",
                aBox.getHeight() + bBox.getHeight() <= rootInnerHeight);
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
        SceneNode root = SceneNode.row();
        root.setFillParentHeight(true);

        SceneNode panel = SceneNode.row();
        panel.setFillParentHeight(true);

        SceneNode fillChild = SceneNode.row();
        fillChild.setFillParentHeight(true);

        panel.appendChild(fillChild);
        root.appendChild(panel);

        Constraints c = new Constraints(200, 100);

        // 第一次：跑干净
        engine.layout(root, c);

        // 第二次：完全相同约束
        LayoutResult result = engine.layout(root, c);

        Assert.assertEquals("相同约束第二次 relayoutCount=0", 0, result.getRelayoutCount());
        Assert.assertTrue("相同约束第二次 constraintRelayoutedNodes 为空",
                result.getConstraintRelayoutedNodes().isEmpty());
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
        SceneNode root = SceneNode.row();
        root.setFillParentHeight(true);

        SceneNode panel = SceneNode.row();
        panel.setFillParentHeight(true);

        SceneNode fillChild = SceneNode.row();
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
        SceneNode root = SceneNode.row();
        root.setFillParentHeight(true);
        root.setPreferredHeight(300);

        SceneNode inner = SceneNode.row();
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
     * COLUMN 中间层单个 fill 子可穿透拿到中间层剩余高度。
     *
     * <p>root(ROW,fill)→mid(COLUMN,fill)→leaf(ROW,fill,文本"X")。layout(root,(200,100))。
     * mid 是 COLUMN 且只有一个 fill 子，剩余高度无分配冲突，leaf 应拿到 mid 的完整内高 100。</p>
     */
    @Test
    public void columnMiddleLayerPassesRemainingHeightToSingleFillChild() {
        SceneNode root = SceneNode.row();
        root.setFillParentHeight(true);

        SceneNode mid = SceneNode.column();
        mid.setFillParentHeight(true);

        SceneNode leaf = SceneNode.row();
        leaf.setFillParentHeight(true);
        leaf.setText("X");

        mid.appendChild(leaf);
        root.appendChild(mid);

        engine.layout(root, new Constraints(200, 100));

        LayoutBox leafBox = (LayoutBox) leaf.getCachedLayout();
        Assert.assertEquals("COLUMN 唯一 fill 子拿到中间层剩余高 100", 100, leafBox.getHeight());
    }

    /**
     * 固定标题 + fill viewport：唯一 fill 子吃掉扣除标题后的剩余高度。
     */
    @Test
    public void columnFillViewportEatsRemainingHeightAfterFixedTitle() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode title = new SceneNode();
        title.setPreferredHeight(20);

        SceneNode viewport = new SceneNode();
        viewport.setFillParentHeight(true);
        viewport.setText("content");

        root.appendChild(title);
        root.appendChild(viewport);

        engine.layout(root, new Constraints(200, 100));

        LayoutBox titleBox = (LayoutBox) title.getCachedLayout();
        LayoutBox viewportBox = (LayoutBox) viewport.getCachedLayout();
        Assert.assertEquals("固定标题高度=20", 20, titleBox.getHeight());
        Assert.assertEquals("viewport 吃剩余高 80", 80, viewportBox.getHeight());
        Assert.assertEquals("viewport y 紧随标题", 20, viewportBox.getY());
    }

    /**
     * scrollable + COLUMN 唯一 fill 子使用下传剩余高度作为 viewport 高。
     *
     * <p>root(COLUMN,fill)→title(20), viewport(scrollable,fill,内容 3 行=48)。
     * layout(root,(200,100))。viewport 自身是可滚动视口，应钉死为剩余高 80，
     * 不能被滚动内容撑到 48 或其它高度口径。</p>
     */
    @Test
    public void scrollableColumnFillViewportUsesRemainingHeight() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode title = new SceneNode();
        title.setPreferredHeight(20);

        SceneNode viewport = new SceneNode();
        viewport.setScrollable(true);
        viewport.setFillParentHeight(true);
        viewport.setText("A\nB\nC");

        root.appendChild(title);
        root.appendChild(viewport);

        engine.layout(root, new Constraints(200, 100));

        LayoutBox viewportBox = (LayoutBox) viewport.getCachedLayout();
        Assert.assertEquals("scrollable fill viewport 高度=剩余高 80", 80, viewportBox.getHeight());
        Assert.assertEquals("scrollable viewport y 紧随标题", 20, viewportBox.getY());
    }

    /**
     * scrollable fill 子设置 preferredHeight 时，preferredHeight 优先于下传剩余高度。
     *
     * <p>root(COLUMN,fill)→title(20), viewport(scrollable,fill,preferredHeight=120)。
     * layout(root,(200,100)) 下传剩余高为 80，但 preferredHeight 是显式高度，
     * viewport 应取 120，证明 preferredHeight 压过剩余高度。</p>
     */
    @Test
    public void scrollableFillViewportPreferredHeightWinsOverRemainingHeight() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode title = new SceneNode();
        title.setPreferredHeight(20);

        SceneNode viewport = new SceneNode();
        viewport.setScrollable(true);
        viewport.setFillParentHeight(true);
        viewport.setPreferredHeight(120);
        viewport.setText("body");

        root.appendChild(title);
        root.appendChild(viewport);

        engine.layout(root, new Constraints(200, 100));

        LayoutBox viewportBox = (LayoutBox) viewport.getCachedLayout();
        Assert.assertEquals("scrollable fill viewport 高度取 preferredHeight=120", 120,
                viewportBox.getHeight());
        Assert.assertEquals("scrollable viewport y 紧随标题", 20, viewportBox.getY());
    }

    /**
     * 单个 COLUMN fill 子在无固定兄弟时吃完整父内高。
     */
    @Test
    public void singleColumnFillChildEatsWholeInnerHeight() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);
        root.setPadding(4, 0, 6, 0);

        SceneNode fillChild = new SceneNode();
        fillChild.setFillParentHeight(true);
        fillChild.setText("X");
        root.appendChild(fillChild);

        engine.layout(root, new Constraints(200, 100));

        LayoutBox fillBox = (LayoutBox) fillChild.getCachedLayout();
        Assert.assertEquals("唯一 fill 子吃父内高 90", 90, fillBox.getHeight());
        Assert.assertEquals("fill 子 y 从 paddingTop 开始", 4, fillBox.getY());
    }

    /**
     * padding/gap/固定兄弟共同参与剩余高度计算。
     */
    @Test
    public void columnFillRemainingHeightAccountsForPaddingAndGap() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);
        root.setPadding(5, 0, 7, 0);
        root.setGap(3);

        SceneNode header = new SceneNode();
        header.setPreferredHeight(20);

        SceneNode viewport = new SceneNode();
        viewport.setFillParentHeight(true);
        viewport.setText("body");

        SceneNode footer = new SceneNode();
        footer.setText("F");

        root.appendChild(header);
        root.appendChild(viewport);
        root.appendChild(footer);

        engine.layout(root, new Constraints(200, 120));

        LayoutBox viewportBox = (LayoutBox) viewport.getCachedLayout();
        Assert.assertEquals("剩余高=120-padding12-header20-footer16-gap6=66", 66, viewportBox.getHeight());
        Assert.assertEquals("viewport y=paddingTop5+header20+gap3=28", 28, viewportBox.getY());
    }

    /**
     * COLUMN 剩余高约束变化时唯一 fill 子重算，固定兄弟保持跳过。
     */
    @Test
    public void columnFillChildRelayoutsOnConstraintHeightChangeButFixedSiblingSkips() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode title = new SceneNode();
        title.setPreferredHeight(20);

        SceneNode viewport = new SceneNode();
        viewport.setFillParentHeight(true);
        viewport.setText("content");

        root.appendChild(title);
        root.appendChild(viewport);

        engine.layout(root, new Constraints(200, 100));
        LayoutBox titleBox1 = (LayoutBox) title.getCachedLayout();
        Assert.assertEquals("首次 viewport 高=80", 80,
                ((LayoutBox) viewport.getCachedLayout()).getHeight());

        LayoutResult result = engine.layout(root, new Constraints(200, 160));

        LayoutBox titleBox2 = (LayoutBox) title.getCachedLayout();
        LayoutBox viewportBox2 = (LayoutBox) viewport.getCachedLayout();
        Assert.assertSame("固定标题盒值不变，应复用 LayoutBox", titleBox1, titleBox2);
        Assert.assertFalse("固定标题不在重算集合", result.getRelayoutedNodes().contains(title));
        Assert.assertTrue("fill viewport 因约束变化重算",
                result.getConstraintRelayoutedNodes().contains(viewport));
        Assert.assertEquals("约束高变化后 viewport 高=140", 140, viewportBox2.getHeight());
    }

    /**
     * 无高度约束时 COLUMN 唯一 fill 子回退 shrink-to-fit。
     */
    @Test
    public void columnFillChildFallsBackWhenHeightUnconstrained() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode fillChild = new SceneNode();
        fillChild.setFillParentHeight(true);
        fillChild.setText("X");
        root.appendChild(fillChild);

        engine.layout(root, new Constraints(200));

        LayoutBox fillBox = (LayoutBox) fillChild.getCachedLayout();
        Assert.assertEquals("无高约束时 fill 子回退文本 shrink=16", 16, fillBox.getHeight());
    }

    /**
     * 固定兄弟高度不可先验时 COLUMN 唯一 fill 子回退 shrink-to-fit。
     */
    @Test
    public void columnFillChildFallsBackWhenFixedSiblingHeightUnknown() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode unknownFixed = new SceneNode();
        SceneNode unknownLeaf = new SceneNode();
        unknownLeaf.setText("fixed");
        unknownFixed.appendChild(unknownLeaf);

        SceneNode fillChild = new SceneNode();
        fillChild.setFillParentHeight(true);
        fillChild.setText("X");

        root.appendChild(unknownFixed);
        root.appendChild(fillChild);

        engine.layout(root, new Constraints(200, 100));

        LayoutBox fillBox = (LayoutBox) fillChild.getCachedLayout();
        Assert.assertEquals("固定兄弟不可先验时 fill 子回退 shrink=16", 16, fillBox.getHeight());
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
        SceneNode root = SceneNode.row();
        root.setFillParentHeight(true);

        SceneNode panel = SceneNode.row();
        // 不 fill、无 preferredHeight → 高不先验确定

        SceneNode fillChild = SceneNode.row();
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
        SceneNode root = SceneNode.row();
        root.setFillParentHeight(true);

        SceneNode panel = SceneNode.row();
        panel.setFillParentHeight(true);

        SceneNode fillChild = SceneNode.row();
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

    /**
     * ROW 容器启用 SHRINK 时，宽度按子宽之和 + gap + 水平 padding 回收。
     */
    @Test
    public void rowContainerWithShrinkWidthShouldFitChildren() {
        SceneNode root = new SceneNode();
        SceneNode row = SceneNode.row();
        row.setWidthSizing(SceneNode.WidthSizing.SHRINK);
        row.setGap(4);
        row.setPadding(0, 3, 0, 5);

        SceneNode a = new SceneNode();
        a.setText("AB");
        SceneNode b = new SceneNode();
        b.setText("CDE");

        row.appendChild(a);
        row.appendChild(b);
        root.appendChild(row);

        engine.layout(root, new Constraints(200));

        LayoutBox rowBox = (LayoutBox) row.getCachedLayout();
        Assert.assertEquals("ROW shrink 宽度=16+24+4+8", 52, rowBox.getWidth());
    }

    /**
     * COLUMN 容器启用 SHRINK 时，宽度按子最大宽 + 水平 padding 回收。
     */
    @Test
    public void columnContainerWithShrinkWidthShouldFitMaxChild() {
        SceneNode root = new SceneNode();
        SceneNode column = new SceneNode();
        column.setWidthSizing(SceneNode.WidthSizing.SHRINK);
        column.setPadding(0, 3, 0, 5);

        SceneNode a = new SceneNode();
        a.setText("A");
        SceneNode b = new SceneNode();
        b.setText("WIDE");

        column.appendChild(a);
        column.appendChild(b);
        root.appendChild(column);

        engine.layout(root, new Constraints(200));

        LayoutBox columnBox = (LayoutBox) column.getCachedLayout();
        Assert.assertEquals("COLUMN shrink 宽度=max(8,32)+8", 40, columnBox.getWidth());
    }

    /**
     * SHRINK 容器宽度不得超过父级下传的 available outerWidth。
     */
    @Test
    public void shrinkContainerWidthShouldClampToAvailableWidth() {
        SceneNode root = new SceneNode();
        SceneNode row = SceneNode.row();
        row.setWidthSizing(SceneNode.WidthSizing.SHRINK);

        SceneNode child = new SceneNode();
        child.setText("ABCDEFGHIJ"); // 80px
        row.appendChild(child);
        root.appendChild(row);

        engine.layout(root, new Constraints(50));

        LayoutBox rowBox = (LayoutBox) row.getCachedLayout();
        Assert.assertEquals("SHRINK 容器宽度 clamp 到 available=50", 50, rowBox.getWidth());
    }

    /**
     * 默认 FILL 行为保持不变：有子容器仍填满父级可用宽度。
     */
    @Test
    public void defaultWidthSizingShouldStillFillContainerWidth() {
        SceneNode root = new SceneNode();
        SceneNode container = new SceneNode();
        SceneNode child = new SceneNode();
        child.setText("A");
        container.appendChild(child);
        root.appendChild(container);

        engine.layout(root, new Constraints(200));

        LayoutBox containerBox = (LayoutBox) container.getCachedLayout();
        Assert.assertEquals("默认 FILL 容器宽度仍为 available", 200, containerBox.getWidth());
        Assert.assertEquals("默认 WidthSizing 为 FILL", SceneNode.WidthSizing.FILL, container.getWidthSizing());
    }

    /**
     * preferredWidth 仍最高优先级，压过 SHRINK 宽度策略。
     */
    @Test
    public void preferredWidthShouldOverrideShrinkWidthSizing() {
        SceneNode root = new SceneNode();
        SceneNode row = SceneNode.row();
        row.setWidthSizing(SceneNode.WidthSizing.SHRINK);
        row.setPreferredWidth(120);

        SceneNode child = new SceneNode();
        child.setText("A");
        row.appendChild(child);
        root.appendChild(row);

        engine.layout(root, new Constraints(200));

        LayoutBox rowBox = (LayoutBox) row.getCachedLayout();
        Assert.assertEquals("preferredWidth 最高优先级", 120, rowBox.getWidth());
    }

    /**
     * 无文本装饰叶宽度依赖约束宽，约束宽变化时应突破 I7 跳过并重算。
     */
    @Test
    public void fillDecorativeLeafShouldRecomputeOnConstraintWidthChange() {
        SceneNode leaf = new SceneNode();

        engine.layout(leaf, new Constraints(200));
        Assert.assertEquals("首次装饰叶宽度=200", 200,
                ((LayoutBox) leaf.getCachedLayout()).getWidth());

        engine.layout(leaf, new Constraints(150));

        Assert.assertEquals("约束宽变小后装饰叶宽度应重算为 150", 150,
                ((LayoutBox) leaf.getCachedLayout()).getWidth());
    }

    /**
     * 长文本叶宽度被约束宽 clamp，约束宽降低时应按新约束收缩。
     */
    @Test
    public void clampedTextLeafShouldShrinkOnConstraintWidthDecrease() {
        SceneNode leaf = new SceneNode();
        leaf.setText("ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMN");

        engine.layout(leaf, new Constraints(300));
        Assert.assertEquals("首次长文本叶被 300 clamp", 300,
                ((LayoutBox) leaf.getCachedLayout()).getWidth());

        engine.layout(leaf, new Constraints(100));

        Assert.assertEquals("约束宽降低后长文本叶应 clamp 到 100", 100,
                ((LayoutBox) leaf.getCachedLayout()).getWidth());
    }

    /**
     * 显式 preferredWidth 叶宽固定，父宽变化不应触发叶节点缓存替换。
     */
    @Test
    public void preferredWidthLeafShouldSkipOnConstraintWidthChange() {
        SceneNode root = new SceneNode();
        SceneNode leaf = new SceneNode();
        leaf.setPreferredWidth(180);
        root.appendChild(leaf);

        engine.layout(root, new Constraints(300));
        LayoutBox firstBox = (LayoutBox) leaf.getCachedLayout();
        Assert.assertEquals("首次 preferredWidth 叶宽=180", 180, firstBox.getWidth());

        engine.layout(root, new Constraints(100));
        LayoutBox secondBox = (LayoutBox) leaf.getCachedLayout();

        Assert.assertEquals("约束宽变化后 preferredWidth 叶宽仍为 180", 180, secondBox.getWidth());
        Assert.assertSame("preferredWidth 叶不消费约束宽，LayoutBox 引用应稳定", firstBox, secondBox);
    }

    /**
     * 非 clamp 短文本叶只遇到高度约束变化时，应保持 I7 跳过与缓存引用稳定。
     */
    @Test
    public void nonClampedTextLeafShouldSkipOnHeightOnlyConstraintChange() {
        SceneNode root = SceneNode.row();
        SceneNode leaf = new SceneNode();
        leaf.setText("短");
        root.appendChild(leaf);

        engine.layout(root, new Constraints(300));
        LayoutBox firstBox = (LayoutBox) leaf.getCachedLayout();
        Assert.assertTrue("短文本叶内在宽应小于约束宽", firstBox.getWidth() < 300);

        engine.layout(root, new Constraints(300, 200));
        LayoutBox secondBox = (LayoutBox) leaf.getCachedLayout();

        Assert.assertEquals("仅高度约束变化时短文本叶宽不变", firstBox.getWidth(), secondBox.getWidth());
        Assert.assertSame("短文本叶不消费高度约束，LayoutBox 引用应稳定", firstBox, secondBox);
    }

    /**
     * 叶节点宽度修复只影响消费宽度的叶，不污染同父固定宽干净兄弟。
     */
    @Test
    public void cleanPreferredSiblingShouldNotRelayoutOnLeafWidthChange() {
        SceneNode root = new SceneNode();
        SceneNode decorativeLeaf = new SceneNode();
        SceneNode fixedLeaf = new SceneNode();
        fixedLeaf.setPreferredWidth(100);

        root.appendChild(decorativeLeaf);
        root.appendChild(fixedLeaf);

        engine.layout(root, new Constraints(300));
        LayoutBox fixedFirstBox = (LayoutBox) fixedLeaf.getCachedLayout();
        Assert.assertEquals("首次装饰叶宽度=300", 300,
                ((LayoutBox) decorativeLeaf.getCachedLayout()).getWidth());
        Assert.assertEquals("首次固定兄弟宽度=100", 100, fixedFirstBox.getWidth());

        engine.layout(root, new Constraints(200));
        LayoutBox decorativeSecondBox = (LayoutBox) decorativeLeaf.getCachedLayout();
        LayoutBox fixedSecondBox = (LayoutBox) fixedLeaf.getCachedLayout();

        Assert.assertEquals("约束宽变化后装饰叶宽度=200", 200, decorativeSecondBox.getWidth());
        Assert.assertEquals("固定兄弟宽度仍为 100", 100, fixedSecondBox.getWidth());
        Assert.assertSame("固定兄弟不应因叶宽修复被替换缓存", fixedFirstBox, fixedSecondBox);
    }

    // ============================================================
    // ROW 交叉轴垂直对齐回归：固定高容器 CENTER/END 必须用容器内高作基准
    // ============================================================

    /**
     * 用例1：固定高 ROW + 交叉轴 CENTER，子节点应垂直居中。
     *
     * <p>root(ROW, mainAxis CENTER, crossAxis CENTER, preferredHeight=40, padding 上下 6)
     * → child(文本 "X", 行高 16, 无 padding)。innerHeight = 40-12 = 28，
     * child.y = padTop + (innerHeight - childHeight)/2 = 6 + (28-16)/2 = 12。
     * 旧 bug 用 crossMax(=16) 作基准 → child.y = 6 + (16-16)/2 = 6（贴顶），新代码应得 12。</p>
     */
    @Test
    public void rowFixedHeightCrossCenterShouldVerticallyCenterChild() {
        SceneNode root = SceneNode.row();
        root.setMainAxisAlign(MainAxisAlign.CENTER);
        root.setCrossAxisAlign(CrossAxisAlign.CENTER);
        root.setPreferredHeight(40);
        root.setPadding(6, 0, 6, 0);

        SceneNode child = new SceneNode();
        child.setText("X"); // 1 行 × 16 = 16
        root.appendChild(child);

        engine.layout(root, new Constraints(200));

        LayoutBox childBox = (LayoutBox) child.getCachedLayout();
        Assert.assertEquals("child 高度=16", 16, childBox.getHeight());
        Assert.assertEquals("固定高 ROW+CENTER：child.y = 6 + (28-16)/2 = 12", 12, childBox.getY());
    }

    /**
     * 用例2：固定高 ROW + 交叉轴 END，子节点应贴底。
     *
     * <p>同用例1结构但 crossAxis END。child.y = padTop + (innerHeight - childHeight)
     * = 6 + (28-16) = 18。旧 bug 得 y=6（贴顶），新代码应得 18。</p>
     */
    @Test
    public void rowFixedHeightCrossEndShouldStickChildToBottom() {
        SceneNode root = SceneNode.row();
        root.setMainAxisAlign(MainAxisAlign.CENTER);
        root.setCrossAxisAlign(CrossAxisAlign.END);
        root.setPreferredHeight(40);
        root.setPadding(6, 0, 6, 0);

        SceneNode child = new SceneNode();
        child.setText("X");
        root.appendChild(child);

        engine.layout(root, new Constraints(200));

        LayoutBox childBox = (LayoutBox) child.getCachedLayout();
        Assert.assertEquals("固定高 ROW+END：child.y = 6 + (28-16) = 18", 18, childBox.getY());
    }

    /**
     * 用例3：自适应高 ROW + CENTER（回归保护，确保不破坏已正常场景）。
     *
     * <p>不设 preferredHeight。root 高度自适应 = childHeight + padV = 16+12 = 28。
     * innerHeight = 28-12 = 16 = childHeight，居中偏移 0，child.y = padTop = 6。
     * 保证 shrink-to-fit 场景行为不变。</p>
     */
    @Test
    public void rowShrinkToFitCrossCenterShouldKeepChildAtPaddingTop() {
        SceneNode root = SceneNode.row();
        root.setMainAxisAlign(MainAxisAlign.CENTER);
        root.setCrossAxisAlign(CrossAxisAlign.CENTER);
        root.setPadding(6, 0, 6, 0);

        SceneNode child = new SceneNode();
        child.setText("X");
        root.appendChild(child);

        engine.layout(root, new Constraints(200));

        LayoutBox rootBox = (LayoutBox) root.getCachedLayout();
        Assert.assertEquals("自适应 root 高度 = 16+12 = 28", 28, rootBox.getHeight());
        LayoutBox childBox = (LayoutBox) child.getCachedLayout();
        Assert.assertEquals("shrink-to-fit ROW+CENTER：child.y = 6（innerHeight==childHeight）",
                6, childBox.getY());
    }

    /**
     * 用例4：固定高 ROW + 多个子节点不同高 + CENTER，各自按 innerHeight 居中。
     *
     * <p>root preferredHeight=40，padding 上下 6 → innerHeight=28。两子：
     * childA 文本 "X" 高 16、childB 无文本 setPreferredHeight(10) 高 10。
     * childA.y = 6 + (28-16)/2 = 12；childB.y = 6 + (28-10)/2 = 15。
     * 旧 bug 用 crossMax(=16) 作基准 → 两者都 y=6（贴顶），新代码应得 12 与 15。</p>
     */
    @Test
    public void rowFixedHeightCrossCenterShouldCenterEachChildByInnerHeight() {
        SceneNode root = SceneNode.row();
        root.setMainAxisAlign(MainAxisAlign.CENTER);
        root.setCrossAxisAlign(CrossAxisAlign.CENTER);
        root.setPreferredHeight(40);
        root.setPadding(6, 0, 6, 0);

        SceneNode childA = new SceneNode();
        childA.setText("X"); // 高 16
        SceneNode childB = new SceneNode();
        childB.setPreferredHeight(10); // 无文本叶，高 10
        root.appendChild(childA);
        root.appendChild(childB);

        engine.layout(root, new Constraints(200));

        LayoutBox boxA = (LayoutBox) childA.getCachedLayout();
        LayoutBox boxB = (LayoutBox) childB.getCachedLayout();
        Assert.assertEquals("childA 高度=16", 16, boxA.getHeight());
        Assert.assertEquals("childB 高度=10", 10, boxB.getHeight());
        Assert.assertEquals("childA.y = 6 + (28-16)/2 = 12", 12, boxA.getY());
        Assert.assertEquals("childB.y = 6 + (28-10)/2 = 15", 15, boxB.getY());
    }

    /**
     * 固定高 ROW + 默认 STRETCH + 无 preferredHeight 子节点 → 子节点高度被拉满到容器内高。
     * <p>crossAvail 改用容器内高后，STRETCH 子节点从拉到 crossMax 变为拉到容器内高
     * （flexbox 语义更正确）。本用例锁定新行为，防未来误改回 crossMax。</p>
     */
    @Test
    public void rowFixedHeightDefaultStretchShouldFillChildToInnerHeight() {
        SceneNode root = SceneNode.row();
        root.setMainAxisAlign(MainAxisAlign.CENTER);
        // 不显式设 crossAxisAlign，默认 STRETCH
        root.setPreferredHeight(40);
        root.setPadding(6, 0, 6, 0);

        SceneNode child = new SceneNode();
        child.setText("X"); // 自然高 16，无 preferredHeight
        root.appendChild(child);

        engine.layout(root, new Constraints(200));

        LayoutBox box = (LayoutBox) child.getCachedLayout();
        // 容器内高 = 40 - 12 = 28，STRETCH 拉满
        Assert.assertEquals("STRETCH 子节点高度=容器内高 28", 28, box.getHeight());
        Assert.assertEquals("STRETCH 子节点 y=padTop 6", 6, box.getY());
    }

    /**
     * 嵌套固定高 ROW + CENTER：每层用各自容器内高做基准，不串味。
     * <p>外层 ROW preferredHeight=60（撑高于内容）→ 内层 ROW preferredHeight=20（小于自然高28，
     * 故内层高=28）→ 叶子文本高 16。
     * 外层内高=60-12=48，内层高28 → 内层 y=6+(48-28)/2=16。
     * 旧 bug 用 crossMax=28 当外层基准 → crossAvail=28，内层 y=6+(28-28)/2=6，贴顶不居中。
     * 叶子在内层内高16里居中：y=6+(16-16)/2=6（内层内高=28-12=16=叶子高，居中偏移0）。</p>
     */
    @Test
    public void nestedFixedHeightRowCenterShouldUseEachContainerInnerHeight() {
        SceneNode outer = SceneNode.row();
        outer.setMainAxisAlign(MainAxisAlign.CENTER);
        outer.setCrossAxisAlign(CrossAxisAlign.CENTER);
        outer.setPreferredHeight(60);
        outer.setPadding(6, 0, 6, 0);

        SceneNode inner = SceneNode.row();
        inner.setMainAxisAlign(MainAxisAlign.CENTER);
        inner.setCrossAxisAlign(CrossAxisAlign.CENTER);
        inner.setPreferredHeight(20); // < natural 28，故内层高=28
        inner.setPadding(6, 0, 6, 0);
        outer.appendChild(inner);

        SceneNode leaf = new SceneNode();
        leaf.setText("X"); // 高 16
        inner.appendChild(leaf);

        engine.layout(outer, new Constraints(200));

        LayoutBox innerBox = (LayoutBox) inner.getCachedLayout();
        LayoutBox leafBox = (LayoutBox) leaf.getCachedLayout();
        // 外层内高 48，内层高 28 → 内层 y=6+(48-28)/2=16（旧 bug 得 6）
        Assert.assertEquals("内层 ROW y=外层内高居中 16", 16, innerBox.getY());
        // 内层内高 16，叶子高 16 → 叶子 y=6+0=6
        Assert.assertEquals("叶子在内层内高居中 6", 6, leafBox.getY());
    }

    /**
     * fill 撑高的 ROW + CENTER 子节点：crossAvail 用 fill 撑起的高度而非 crossMax。
     * <p>root(ROW, fill, crossAxis CENTER) 收约束高 100，子节点自然高 16。
     * crossAvail 应=100-0=100（无 padding），子 y=(100-16)/2=42。
     * 旧 bug 用 crossMax=16 → y=0，贴顶不居中。</p>
     */
    @Test
    public void fillRowCrossCenterShouldUseFillHeightNotCrossMax() {
        SceneNode root = SceneNode.row();
        root.setMainAxisAlign(MainAxisAlign.CENTER);
        root.setCrossAxisAlign(CrossAxisAlign.CENTER);
        root.setFillParentHeight(true);
        // 无 padding，padV=0

        SceneNode child = new SceneNode();
        child.setText("X"); // 自然高 16
        root.appendChild(child);

        engine.layout(root, new Constraints(200, 100));

        LayoutBox childBox = (LayoutBox) child.getCachedLayout();
        // fill 撑高到 100，crossAvail=100，子 y=(100-16)/2=42
        Assert.assertEquals("fill ROW 子节点 y=撑起高居中 42", 42, childBox.getY());
    }

    // ============================================================
    // 阶段 1.5 回归锚点：epoch 失效链外置（SceneNode 自持 lastMeasuredEpoch）
    // ============================================================

    /**
     * 锚点 1.1：epoch 变化只重测文本叶，非文本兄弟（固定宽高装饰盒）不重算。
     *
     * <p>构造 root(COLUMN) → [textLeaf("hello"), decoBox(固定宽高无文本)]。
     * 稳态后 bumpEpoch 再 layout，断言 textLeaf 在重算集合、decoBox 不在，
     * 且 root 的 relayoutCount 只含 textLeaf 重算（非全树）。</p>
     */
    @Test
    public void epochChangeRemeasuresTextLeafOnly() {
        FixedTextMeasurer stub = new FixedTextMeasurer(8, 16);
        SceneLayoutEngine epochEngine = new SceneLayoutEngine(stub);

        SceneNode root = SceneNode.column();
        SceneNode textLeaf = new SceneNode();
        textLeaf.setText("hello");
        SceneNode decoBox = new SceneNode();
        decoBox.setPreferredWidth(40);
        decoBox.setPreferredHeight(30);
        root.appendChild(textLeaf);
        root.appendChild(decoBox);

        Constraints constraints = new Constraints(200);
        // 稳态：跑两帧确保 cachedLayout 建好且全树干净
        epochEngine.layout(root, constraints);
        LayoutResult steady = epochEngine.layout(root, constraints);
        Assert.assertEquals("稳态第二帧零重算", 0, steady.getRelayoutCount());

        // bump epoch 模拟字体运行时变化
        stub.bumpEpoch();
        LayoutResult result = epochEngine.layout(root, constraints);

        Assert.assertTrue("epoch 变化后文本叶应被重算",
                result.getRelayoutedNodes().contains(textLeaf));
        Assert.assertFalse("固定宽高装饰盒不应被 epoch 失效链标脏",
                result.getRelayoutedNodes().contains(decoBox));
        // root 仅因 descendant 下沉重定位，不计入重算集合
        Assert.assertFalse("root 不应被计入重算集合",
                result.getRelayoutedNodes().contains(root));
        // relayoutCount 只含 textLeaf 重算（1 次），非全树
        Assert.assertEquals("relayoutCount 只含 textLeaf 重算", 1, result.getRelayoutCount());
    }

    /**
     * 锚点 1.2（P0 命门正证）：epoch 变化时，干净中间层下的文本叶仍被冒泡触达。
     *
     * <p>构造 root → cleanMiddleContainer(固定宽高无文本，稳态后干净) → textLeaf("deep")。
     * 稳态后 bumpEpoch 再 layout，断言：</p>
     * <ul>
     *   <li>textLeaf 在 relayoutedNodes 中（被入口遍历前冒泡触达，P0 命门）</li>
     *   <li>cleanMiddleContainer 被下沉访问（因 descendantLayoutDirty 被冒泡点亮）
     *       但自身未重算（selfLayoutDirty 仍 false，复用 cachedLayout）</li>
     * </ul>
     * <p><b>此测试若失败 = P0 命门失守 = 入口遍历前冒泡被删 = 实现错误。</b>
     * 若删掉入口冒泡只靠遍历时自查，干净子树（双 false）会在 layoutInternal 入口
     * 被整棵跳过，永远到不了文本叶的自查点，导致字体 reload 后干净子树文本不更新。</p>
     */
    @Test
    public void epochChangeBubblesThroughCleanMiddle() {
        FixedTextMeasurer stub = new FixedTextMeasurer(8, 16);
        SceneLayoutEngine epochEngine = new SceneLayoutEngine(stub);

        SceneNode root = new SceneNode();
        SceneNode cleanMiddle = new SceneNode();
        cleanMiddle.setPreferredWidth(100);
        cleanMiddle.setPreferredHeight(50);
        SceneNode textLeaf = new SceneNode();
        textLeaf.setText("deep");
        root.appendChild(cleanMiddle);
        cleanMiddle.appendChild(textLeaf);

        Constraints constraints = new Constraints(200);
        // 稳态：跑两帧确保 cleanMiddle 的 cachedLayout 建好且 cleanSelf=true
        epochEngine.layout(root, constraints);
        LayoutResult steady = epochEngine.layout(root, constraints);
        Assert.assertEquals("稳态第二帧零重算", 0, steady.getRelayoutCount());
        // 确认 cleanMiddle 稳态干净
        Assert.assertFalse("cleanMiddle 稳态应 selfLayoutDirty=false",
                cleanMiddle.__isSelfLayoutDirty());
        Assert.assertFalse("cleanMiddle 稳态应 descendantLayoutDirty=false",
                cleanMiddle.__isDescendantLayoutDirty());
        LayoutBox cleanMiddleBoxSteady = (LayoutBox) cleanMiddle.getCachedLayout();
        Assert.assertNotNull("cleanMiddle 应有 cachedLayout", cleanMiddleBoxSteady);

        // bump epoch 模拟字体运行时变化
        stub.bumpEpoch();
        LayoutResult result = epochEngine.layout(root, constraints);

        // P0 命门正证：textLeaf 被入口遍历前冒泡触达，进入重算集合
        Assert.assertTrue("P0 命门：干净中间层下的文本叶应被冒泡触达重算",
                result.getRelayoutedNodes().contains(textLeaf));
        // cleanMiddle 被下沉访问（descendantLayoutDirty 被冒泡点亮）但自身未重算
        Assert.assertFalse("cleanMiddle 自身不应被重算（selfLayoutDirty 仍 false，复用 cachedLayout）",
                result.getRelayoutedNodes().contains(cleanMiddle));
        // cleanMiddle 的 cachedLayout 引用应不变（复用，未重算）
        LayoutBox cleanMiddleBoxAfter = (LayoutBox) cleanMiddle.getCachedLayout();
        Assert.assertSame("cleanMiddle 复用 cachedLayout（未被重算）",
                cleanMiddleBoxSteady, cleanMiddleBoxAfter);
        // root 仅因 descendant 下沉，不计入重算集合
        Assert.assertFalse("root 不应被计入重算集合",
                result.getRelayoutedNodes().contains(root));
    }

    /**
     * 锚点 1.3：主树与 overlay 的 epoch 失效链 per-tree 隔离。
     *
     * <p>主树含 textLeaf，overlay 含独立 textLeaf。bumpEpoch 后主树 layout + overlay layout
     * 各自独立，断言 overlay 的 relayoutedNodes 只含 overlay 自己的 textLeaf，不含主树节点；
     * 主树的 relayoutedNodes 只含主树自己的 textLeaf，不含 overlay 节点。</p>
     *
     * <p>注：epoch 是全局的（FontService.textMeasureEpoch），bumpEpoch 会同时影响两个 engine
     * 的 measurer。但 measuredTextNodes 是 per-engine 实例字段，故失效链遍历范围隔离。</p>
     */
    @Test
    public void overlayTextNotDirtiedByMainTreeEpoch() {
        FixedTextMeasurer mainStub = new FixedTextMeasurer(8, 16);
        FixedTextMeasurer overlayStub = new FixedTextMeasurer(8, 16);
        SceneLayoutEngine mainEngine = new SceneLayoutEngine(mainStub);
        SceneLayoutEngine overlayEngine = new SceneLayoutEngine(overlayStub);

        // 主树：root → mainText
        SceneNode mainRoot = new SceneNode();
        SceneNode mainText = new SceneNode();
        mainText.setText("main");
        mainRoot.appendChild(mainText);

        // overlay：overlayRoot → overlayText
        SceneNode overlayRoot = new SceneNode();
        SceneNode overlayText = new SceneNode();
        overlayText.setText("overlay");
        overlayRoot.appendChild(overlayText);

        Constraints constraints = new Constraints(200);
        // 稳态
        mainEngine.layout(mainRoot, constraints);
        overlayEngine.layout(overlayRoot, constraints);
        mainEngine.layout(mainRoot, constraints);
        overlayEngine.layout(overlayRoot, constraints);

        // bump epoch（两个 stub 各自 bump，模拟全局 epoch 变化同时影响两者）
        mainStub.bumpEpoch();
        overlayStub.bumpEpoch();

        // 主树 layout + overlay layout 各自独立
        LayoutResult mainResult = mainEngine.layout(mainRoot, constraints);
        LayoutResult overlayResult = overlayEngine.layout(overlayRoot, constraints);

        // 主树 relayoutedNodes 只含主树 textLeaf，不含 overlay 节点
        Assert.assertTrue("主树 textLeaf 应被重算",
                mainResult.getRelayoutedNodes().contains(mainText));
        Assert.assertFalse("主树 relayoutedNodes 不应含 overlay textLeaf",
                mainResult.getRelayoutedNodes().contains(overlayText));
        // overlay relayoutedNodes 只含 overlay textLeaf，不含主树节点
        Assert.assertTrue("overlay textLeaf 应被重算",
                overlayResult.getRelayoutedNodes().contains(overlayText));
        Assert.assertFalse("overlay relayoutedNodes 不应含主树 textLeaf",
                overlayResult.getRelayoutedNodes().contains(mainText));
    }

    /**
     * 锚点 1.4：一帧两调幂等——同帧第二次 layout 不应重复标脏文本叶。
     *
     * <p>构造 root → textLeaf("hello")。稳态后 bumpEpoch，模拟 host 一帧两调
     * （第一次 layout → 第二次 layout，中间无 flush）。断言：</p>
     * <ul>
     *   <li>第一次 layout 的 relayoutCount 含 textLeaf 重算（epoch 失效链触发）</li>
     *   <li>第二次 layout 的 relayoutCount 不含 textLeaf（第一次已更新 lastMeasuredEpoch，
     *       第二次 epoch 比对成立不标脏）</li>
     * </ul>
     * <p>此测试守护「节点级 epoch 比对」的幂等性：同帧重复 layout 不会因 epoch 未变
     * 而重复触发文本叶重测。</p>
     */
    @Test
    public void sameFrameDoubleLayoutNoDoubleDirty() {
        FixedTextMeasurer stub = new FixedTextMeasurer(8, 16);
        SceneLayoutEngine epochEngine = new SceneLayoutEngine(stub);

        SceneNode root = new SceneNode();
        SceneNode textLeaf = new SceneNode();
        textLeaf.setText("hello");
        root.appendChild(textLeaf);

        Constraints constraints = new Constraints(200);
        // 稳态
        epochEngine.layout(root, constraints);
        LayoutResult steady = epochEngine.layout(root, constraints);
        Assert.assertEquals("稳态第二帧零重算", 0, steady.getRelayoutCount());

        // bump epoch 模拟字体运行时变化
        stub.bumpEpoch();

        // 模拟一帧两调：第一次 layout
        LayoutResult first = epochEngine.layout(root, constraints);
        Assert.assertTrue("第一次 layout 文本叶应被重算（epoch 失效链触发）",
                first.getRelayoutedNodes().contains(textLeaf));
        Assert.assertEquals("第一次 layout relayoutCount 含 textLeaf 重算",
                1, first.getRelayoutCount());

        // 模拟一帧两调：第二次 layout（同帧）——第一次 layout 已更新 textLeaf.lastMeasuredEpoch，
        // 第二次 epoch 比对成立不标脏，relayoutCount 应为 0（节点级 epoch 比对幂等）
        LayoutResult second = epochEngine.layout(root, constraints);
        Assert.assertEquals("同帧第二次 layout 不重复标脏 textLeaf",
                0, second.getRelayoutCount());
    }

    // ============================================================
    // flexGrow 权重分配系列（阶段 3：还 NORTH_STAR §偏离 2026-06-20 的债）
    // ============================================================

    /**
     * T1：按权重非等分。
     *
     * <p>root(COLUMN,fill) 高 100 无 pad/gap，a(flexGrow=1)、b(flexGrow=3) 无固定兄弟。
     * Σw=4，freeH=100。a=100*1/4=25，b=末位补余=100-25=75。</p>
     */
    @Test
    public void columnGrowChildrenSplitByWeight() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode a = new SceneNode();
        a.setFlexGrow(1);
        SceneNode b = new SceneNode();
        b.setFlexGrow(3);

        root.appendChild(a);
        root.appendChild(b);

        engine.layout(root, new Constraints(200, 100));

        LayoutBox aBox = (LayoutBox) a.getCachedLayout();
        LayoutBox bBox = (LayoutBox) b.getCachedLayout();
        Assert.assertEquals("a 按权重 1/4 分得 25", 25, aBox.getHeight());
        Assert.assertEquals("b 末位补余得 75", 75, bBox.getHeight());
    }

    /**
     * T2：余数补末位，Σalloc 精确吃满 freeH。
     *
     * <p>root 高 100，a/b/c 各 flexGrow=1（Σw=3）。100/3=33 余 1，末位 c 补余得 34。
     * a+b+c=33+33+34=100 精确吃满 freeH。</p>
     */
    @Test
    public void columnGrowRemainderGoesToLastChild() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode a = new SceneNode();
        a.setFlexGrow(1);
        SceneNode b = new SceneNode();
        b.setFlexGrow(1);
        SceneNode c = new SceneNode();
        c.setFlexGrow(1);

        root.appendChild(a);
        root.appendChild(b);
        root.appendChild(c);

        engine.layout(root, new Constraints(200, 100));

        LayoutBox aBox = (LayoutBox) a.getCachedLayout();
        LayoutBox bBox = (LayoutBox) b.getCachedLayout();
        LayoutBox cBox = (LayoutBox) c.getCachedLayout();
        Assert.assertEquals("a=33", 33, aBox.getHeight());
        Assert.assertEquals("b=33", 33, bBox.getHeight());
        Assert.assertEquals("c 末位补余=34", 34, cBox.getHeight());
        Assert.assertEquals("Σalloc 精确吃满 freeH=100",
                100, aBox.getHeight() + bBox.getHeight() + cBox.getHeight());
    }

    /**
     * T3：grow 子 + 固定兄弟混合，gap 参与剩余计算。
     *
     * <p>root 高 120、gap=3，header(preferredHeight=20 固定)、a(grow=1)、b(grow=1)。
     * freeH=120-20-(3*2)=94，a/b 各 47。</p>
     */
    @Test
    public void columnGrowWithFixedSiblingAndGap() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);
        root.setGap(3);

        SceneNode header = new SceneNode();
        header.setPreferredHeight(20);

        SceneNode a = new SceneNode();
        a.setFlexGrow(1);
        SceneNode b = new SceneNode();
        b.setFlexGrow(1);

        root.appendChild(header);
        root.appendChild(a);
        root.appendChild(b);

        engine.layout(root, new Constraints(200, 120));

        LayoutBox headerBox = (LayoutBox) header.getCachedLayout();
        LayoutBox aBox = (LayoutBox) a.getCachedLayout();
        LayoutBox bBox = (LayoutBox) b.getCachedLayout();
        Assert.assertEquals("固定 header=20", 20, headerBox.getHeight());
        Assert.assertEquals("a=47", 47, aBox.getHeight());
        Assert.assertEquals("b 末位补余=47", 47, bBox.getHeight());
    }

    /**
     * T4：显式 flexGrow 压过 fill 隐式权重。
     *
     * <p>root 高 100，a(fill 隐式 1)、b(flexGrow=3)。Σw=4，a=25、b=75。
     * 证明 effectiveGrow 优先取显式 flexGrow，b 取 3 不取 1。</p>
     */
    @Test
    public void columnExplicitFlexGrowWinsOverFillImplicit() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode a = new SceneNode();
        a.setFillParentHeight(true);
        SceneNode b = new SceneNode();
        b.setFlexGrow(3);

        root.appendChild(a);
        root.appendChild(b);

        engine.layout(root, new Constraints(200, 100));

        LayoutBox aBox = (LayoutBox) a.getCachedLayout();
        LayoutBox bBox = (LayoutBox) b.getCachedLayout();
        Assert.assertEquals("a 隐式权重 1 分得 25", 25, aBox.getHeight());
        Assert.assertEquals("b 显式 flexGrow=3 分得 75", 75, bBox.getHeight());
    }

    /**
     * T5 ★I7 核心反证：多 grow 子约束变化时干净兄弟不被重算。
     *
     * <p>树 = root(COLUMN,fill)，header(preferredHeight=20)、a(grow=1)、b(grow=1)。
     * 先 layout(root,(200,100))；再 layout(root,(200,160))。断言：
     * ① header assertSame LayoutBox 复用、② !result.getRelayoutedNodes().contains(header)、
     * ③ a/b 都在 getConstraintRelayoutedNodes()、④ a/b 新高按新 freeH 重分
     * （freeH=160-20=140 → 各 70）。</p>
     */
    @Test
    public void columnMultipleGrowRelayoutsOnConstraintChangeButFixedSiblingSkips() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode header = new SceneNode();
        header.setPreferredHeight(20);

        SceneNode a = new SceneNode();
        a.setFlexGrow(1);
        SceneNode b = new SceneNode();
        b.setFlexGrow(1);

        root.appendChild(header);
        root.appendChild(a);
        root.appendChild(b);

        engine.layout(root, new Constraints(200, 100));
        LayoutBox headerBox1 = (LayoutBox) header.getCachedLayout();
        Assert.assertEquals("首次 a=40", 40, ((LayoutBox) a.getCachedLayout()).getHeight());
        Assert.assertEquals("首次 b=40", 40, ((LayoutBox) b.getCachedLayout()).getHeight());

        LayoutResult result = engine.layout(root, new Constraints(200, 160));

        LayoutBox headerBox2 = (LayoutBox) header.getCachedLayout();
        LayoutBox aBox2 = (LayoutBox) a.getCachedLayout();
        LayoutBox bBox2 = (LayoutBox) b.getCachedLayout();
        Assert.assertSame("固定 header 盒值不变，应复用 LayoutBox", headerBox1, headerBox2);
        Assert.assertFalse("固定 header 不在重算集合", result.getRelayoutedNodes().contains(header));
        Assert.assertTrue("a 因约束变化重算", result.getConstraintRelayoutedNodes().contains(a));
        Assert.assertTrue("b 因约束变化重算", result.getConstraintRelayoutedNodes().contains(b));
        Assert.assertEquals("约束高变化后 a=70", 70, aBox2.getHeight());
        Assert.assertEquals("约束高变化后 b=70", 70, bBox2.getHeight());
    }

    /**
     * T6：多 grow 子不溢出 + 失高回退。
     *
     * <p>复用 T1 树（a flexGrow=1、b flexGrow=3）。先 layout(root,(200,100)) 验 a=25 b=75；
     * 再 layout(root,(200)) 失高，断言 a/b 都回退 shrink（各自内容高，无子无文本 → 0），
     * 不陈旧停在分配值。</p>
     */
    @Test
    public void columnMultipleGrowFallsBackWhenConstraintLosesHeight() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode a = new SceneNode();
        a.setFlexGrow(1);
        SceneNode b = new SceneNode();
        b.setFlexGrow(3);

        root.appendChild(a);
        root.appendChild(b);

        // 第一次：有高约束 → a=25 b=75
        engine.layout(root, new Constraints(200, 100));
        LayoutBox aBox1 = (LayoutBox) a.getCachedLayout();
        LayoutBox bBox1 = (LayoutBox) b.getCachedLayout();
        Assert.assertEquals("有高约束时 a=25", 25, aBox1.getHeight());
        Assert.assertEquals("有高约束时 b=75", 75, bBox1.getHeight());

        // 第二次：高度 UNCONSTRAINED → a/b 应回退 shrink（无子无文本 → 0）
        engine.layout(root, new Constraints(200));
        LayoutBox aBox2 = (LayoutBox) a.getCachedLayout();
        LayoutBox bBox2 = (LayoutBox) b.getCachedLayout();
        Assert.assertEquals("失高后 a 回退 shrink=0（不陈旧停在 25）", 0, aBox2.getHeight());
        Assert.assertEquals("失高后 b 回退 shrink=0（不陈旧停在 75）", 0, bBox2.getHeight());
    }

    // ============================================================
    // min/max 高度 clamp + freeze do-while 撞顶重分配系列
    // （一期：maxHeight 上界 clamp + preferredHeight 下界 freeze）
    // ============================================================

    /**
     * M1：grow 子撞 maxHeight 上界，冻结后剩余空间回流未冻结兄弟。
     *
     * <p>root(COLUMN,fill) 高 300 无 pad/gap，a/b/c 各 flexGrow=1（Σw=3），
     * 中间 b 设 maxHeight=50。第一轮 tentative=300/3=100 &gt; 50 → b 冻结到 50，
     * remainingFree=250、remainingW=2、active=[a,c]。第二轮 tentative=250/2=125，
     * a/c 无上界不冻结，退出。active 末位 c 补余：a=125、c=125。Σalloc=50+125+125=300。</p>
     */
    @Test
    public void columnGrowChildClampedByMaxHeight() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode a = new SceneNode();
        a.setFlexGrow(1);
        SceneNode b = new SceneNode();
        b.setFlexGrow(1);
        b.setMaxHeight(50);
        SceneNode c = new SceneNode();
        c.setFlexGrow(1);

        root.appendChild(a);
        root.appendChild(b);
        root.appendChild(c);

        engine.layout(root, new Constraints(200, 300));

        LayoutBox aBox = (LayoutBox) a.getCachedLayout();
        LayoutBox bBox = (LayoutBox) b.getCachedLayout();
        LayoutBox cBox = (LayoutBox) c.getCachedLayout();
        Assert.assertEquals("b 撞顶冻结到 maxHeight=50", 50, bBox.getHeight());
        Assert.assertEquals("a 回流后分得 125", 125, aBox.getHeight());
        Assert.assertEquals("c 末位补余=125", 125, cBox.getHeight());
    }

    /**
     * M2：撞顶子释放空间精确回流，Σalloc == freeH 不变式验证。
     *
     * <p>复用 M1 场景，断言 Σalloc 精确吃满 freeH=300（有 active 子时不变式成立）。</p>
     */
    @Test
    public void growChildMaxHeightSurplusRedistributed() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode a = new SceneNode();
        a.setFlexGrow(1);
        SceneNode b = new SceneNode();
        b.setFlexGrow(1);
        b.setMaxHeight(50);
        SceneNode c = new SceneNode();
        c.setFlexGrow(1);

        root.appendChild(a);
        root.appendChild(b);
        root.appendChild(c);

        engine.layout(root, new Constraints(200, 300));

        LayoutBox aBox = (LayoutBox) a.getCachedLayout();
        LayoutBox bBox = (LayoutBox) b.getCachedLayout();
        LayoutBox cBox = (LayoutBox) c.getCachedLayout();
        int sum = aBox.getHeight() + bBox.getHeight() + cBox.getHeight();
        Assert.assertEquals("Σalloc == freeH=300（有 active 子时不变式）",
                300, sum);
    }

    /**
     * M3：所有 grow 子都撞顶时剩余空间留空，do-while 正常退出不死循环。
     *
     * <p>root 高 300，a/b/c 各 flexGrow=1 且都 maxHeight=50。第一轮 tentative=100 &gt; 50
     * → 全冻结到 50，remainingFree=150、remainingW=0、active=[]。三重退出条件命中
     * （active 空），退出。active 分配为空，剩余 150 留空。Σalloc=150 &lt; freeH=300
     * （所有子撞顶时无法分配多余空间，不变式退化为 Σalloc ≤ freeH）。</p>
     */
    @Test
    public void allGrowChildrenClampedLeavesSurplus() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode a = new SceneNode();
        a.setFlexGrow(1);
        a.setMaxHeight(50);
        SceneNode b = new SceneNode();
        b.setFlexGrow(1);
        b.setMaxHeight(50);
        SceneNode c = new SceneNode();
        c.setFlexGrow(1);
        c.setMaxHeight(50);

        root.appendChild(a);
        root.appendChild(b);
        root.appendChild(c);

        // 若 do-while 死循环，本调用会挂住或栈溢出；正常退出即证明三重退出条件生效
        engine.layout(root, new Constraints(200, 300));

        LayoutBox aBox = (LayoutBox) a.getCachedLayout();
        LayoutBox bBox = (LayoutBox) b.getCachedLayout();
        LayoutBox cBox = (LayoutBox) c.getCachedLayout();
        Assert.assertEquals("a 撞顶=50", 50, aBox.getHeight());
        Assert.assertEquals("b 撞顶=50", 50, bBox.getHeight());
        Assert.assertEquals("c 撞顶=50", 50, cBox.getHeight());
        // 剩余 150 留空，Σalloc=150 < freeH=300（所有子撞顶，多余空间无法分配）
        Assert.assertEquals("Σalloc=150（剩余 150 留空）",
                150, aBox.getHeight() + bBox.getHeight() + cBox.getHeight());
    }

    /**
     * M4：文本叶撞 maxHeight，computeHeight 出口 clamp 生效。
     *
     * <p>root(COLUMN,fill) 高 200，子是文本叶 5 行（"a\\nb\\nc\\nd\\ne"，行高 16 → 自然高 80），
     * 设 maxHeight=50。子非 grow/fill，computeHeight 走兜底分支 return clampHeight(80)
     * = max(pref=0, min(80, 50)) = 50。断言子高=50。</p>
     */
    @Test
    public void leafMaxHeightClampsContentHeight() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode leaf = new SceneNode();
        leaf.setText("a\nb\nc\nd\ne");
        leaf.setMaxHeight(50);

        root.appendChild(leaf);

        engine.layout(root, new Constraints(200, 200));

        LayoutBox leafBox = (LayoutBox) leaf.getCachedLayout();
        Assert.assertEquals("文本叶自然高 80 被 maxHeight clamp 到 50",
                50, leafBox.getHeight());
    }

    /**
     * M5 ★I7 反证：撞顶重分配后干净装饰兄弟零重算。
     *
     * <p>树 = root(COLUMN,fill)，header(preferredHeight=20 装饰固定)、a(grow=1, maxHeight=50)、
     * b(grow=1)。先 layout(root,(200,300))：freeH=280，第一轮 tentative=140，a 撞顶冻结到 50，
     * b 回流得 230。再 layout(root,(200,300)) 相同约束，断言：① relayoutCount=0、
     * ② header assertSame LayoutBox 复用、③ header 不在 relayoutedNodes。
     * 证明 freeze do-while 撞顶重分配不破坏 I7 干净帧短路。</p>
     */
    @Test
    public void maxHeightCleanSiblingNotRelayouted() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode header = new SceneNode();
        header.setPreferredHeight(20);

        SceneNode a = new SceneNode();
        a.setFlexGrow(1);
        a.setMaxHeight(50);
        SceneNode b = new SceneNode();
        b.setFlexGrow(1);

        root.appendChild(header);
        root.appendChild(a);
        root.appendChild(b);

        // 第一帧：建立 cache，触发撞顶重分配
        engine.layout(root, new Constraints(200, 300));
        LayoutBox headerBox1 = (LayoutBox) header.getCachedLayout();
        Assert.assertEquals("首次 a 撞顶冻结到 50", 50, ((LayoutBox) a.getCachedLayout()).getHeight());
        Assert.assertEquals("首次 b 回流得 230", 230, ((LayoutBox) b.getCachedLayout()).getHeight());

        // 第二帧：相同约束，干净帧短路
        LayoutResult result = engine.layout(root, new Constraints(200, 300));

        LayoutBox headerBox2 = (LayoutBox) header.getCachedLayout();
        Assert.assertEquals("相同约束第二次 relayoutCount=0", 0, result.getRelayoutCount());
        Assert.assertSame("固定 header 盒值不变，应复用 LayoutBox", headerBox1, headerBox2);
        Assert.assertFalse("固定 header 不在重算集合",
                result.getRelayoutedNodes().contains(header));
    }

    /**
     * M6：preferredHeight=80 &gt; maxHeight=50 矛盾配置，下限优先（CSS min-height 赢 max-height）。
     *
     * <p>root(COLUMN,fill) 高 200，子是文本叶 5 行（自然高 80）+ preferredHeight=80 + maxHeight=50。
     * computeContentHeight = max(natural=80, preferredHeight=80) = 80。clampHeight
     * = max(preferredHeight=80, min(80, maxHeight=50)) = max(80, 50) = 80。下限优先，返回 80。</p>
     */
    @Test
    public void maxHeightVsPreferredHeightConflict() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode leaf = new SceneNode();
        leaf.setText("a\nb\nc\nd\ne");
        leaf.setPreferredHeight(80);
        leaf.setMaxHeight(50);

        root.appendChild(leaf);

        engine.layout(root, new Constraints(200, 200));

        LayoutBox leafBox = (LayoutBox) leaf.getCachedLayout();
        Assert.assertEquals("preferredHeight=80 > maxHeight=50 矛盾时下限优先，返回 80",
                80, leafBox.getHeight());
    }

    /**
     * M7：grow 子分得高 &lt; preferredHeight 时撑回 preferredHeight（下界 freeze）。
     *
     * <p>root(COLUMN,fill) 高 100，a(grow=1, preferredHeight=80)、b(grow=1)。freeH=100、Σw=2。
     * 第一轮 tentative=100/2=50 &lt; a.preferredHeight=80 → a 撞底冻结到 80，remainingFree=20、
     * remainingW=1、active=[b]。第二轮 tentative=20/1=20，b 无下界不冻结，退出。active 末位 b=20。
     * Σalloc=80+20=100 精确吃满 freeH。</p>
     */
    @Test
    public void growChildClampedByPreferredHeightFloor() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode a = new SceneNode();
        a.setFlexGrow(1);
        a.setPreferredHeight(80);
        SceneNode b = new SceneNode();
        b.setFlexGrow(1);

        root.appendChild(a);
        root.appendChild(b);

        engine.layout(root, new Constraints(200, 100));

        LayoutBox aBox = (LayoutBox) a.getCachedLayout();
        LayoutBox bBox = (LayoutBox) b.getCachedLayout();
        Assert.assertEquals("a 撞底冻结到 preferredHeight=80", 80, aBox.getHeight());
        Assert.assertEquals("b 回流得 20", 20, bBox.getHeight());
        Assert.assertEquals("Σalloc=100 精确吃满 freeH",
                100, aBox.getHeight() + bBox.getHeight());
    }

    /**
     * M8：maxWidth 对 computeWidth 的 clamp 生效，preferredWidth 显式钉死时不 clamp。
     *
     * <p>用 ROW 容器（主轴=宽，不被 STRETCH 改写；COLUMN 的 cross=宽会被 STRETCH 改写覆盖，
     * FlexLayouter 一期不改）。root(ROW,fill) 宽 200 高 200，a 无文本叶 maxWidth=100，
     * b 无文本叶 preferredWidth=150 + maxWidth=100。
     * a: computeWidth 走无文本叶分支 return clampWidth(200) = min(200, 100) = 100。
     * b: preferredWidth=150 &gt; 0 最高优先级直接 return 150，不 clamp（preferredWidth 优先级高于 maxWidth）。</p>
     */
    @Test
    public void maxWidthClampsComputeWidth() {
        SceneNode root = SceneNode.row();
        root.setFillParentHeight(true);

        SceneNode a = new SceneNode();
        a.setMaxWidth(100);
        SceneNode b = new SceneNode();
        b.setPreferredWidth(150);
        b.setMaxWidth(100);

        root.appendChild(a);
        root.appendChild(b);

        engine.layout(root, new Constraints(200, 200));

        LayoutBox aBox = (LayoutBox) a.getCachedLayout();
        LayoutBox bBox = (LayoutBox) b.getCachedLayout();
        Assert.assertEquals("a 无 preferredWidth，maxWidth clamp 到 100",
                100, aBox.getWidth());
        Assert.assertEquals("b preferredWidth=150 优先级高于 maxWidth=100，不 clamp",
                150, bBox.getWidth());
    }

    // ============================================================
    // freeze do-while 多轮冻结 + 余数补末位 + 混合上下界 + 不等权重
    // （一期补充：reviewer 建议 5，M9-M12）
    // ============================================================

    /**
     * M9：do-while 多轮逐轮冻结（3+ 轮）。
     *
     * <p>root(COLUMN,fill) 高 300 无 pad/gap，a(grow=1,maxHeight=50)、b(grow=1,maxHeight=80)、
     * c(grow=1,maxHeight=100)、d(grow=1)。Σw=4，freeH=300。
     * 第一轮：tentative=300/4=75。a 75&gt;50 撞顶冻 50；b 75&lt;80 不冻；c 75&lt;100 不冻；d 不冻。
     * 第二轮（remainingFree=250, remainingW=3）：tentative=250/3=83。b 83&gt;80 撞顶冻 80；
     * c 83&lt;100 不冻；d 不冻。
     * 第三轮（remainingFree=170, remainingW=2）：tentative=170/2=85。c 85&lt;100 不冻；d 不冻。
     * 无新冻结退出。active=[c,d] 末位补余：c=85、d=85。Σ=300。</p>
     */
    @Test
    public void columnGrowMultiRoundFreeze() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode a = new SceneNode();
        a.setFlexGrow(1);
        a.setMaxHeight(50);
        SceneNode b = new SceneNode();
        b.setFlexGrow(1);
        b.setMaxHeight(80);
        SceneNode c = new SceneNode();
        c.setFlexGrow(1);
        c.setMaxHeight(100);
        SceneNode d = new SceneNode();
        d.setFlexGrow(1);

        root.appendChild(a);
        root.appendChild(b);
        root.appendChild(c);
        root.appendChild(d);

        engine.layout(root, new Constraints(200, 300));

        LayoutBox aBox = (LayoutBox) a.getCachedLayout();
        LayoutBox bBox = (LayoutBox) b.getCachedLayout();
        LayoutBox cBox = (LayoutBox) c.getCachedLayout();
        LayoutBox dBox = (LayoutBox) d.getCachedLayout();
        Assert.assertEquals("a 第一轮撞顶冻结到 maxHeight=50", 50, aBox.getHeight());
        Assert.assertEquals("b 第二轮撞顶冻结到 maxHeight=80", 80, bBox.getHeight());
        Assert.assertEquals("c 第三轮不冻，末位补余得 85", 85, cBox.getHeight());
        Assert.assertEquals("d 第三轮不冻，末位补余得 85", 85, dBox.getHeight());
        Assert.assertEquals("Σalloc 精确吃满 freeH=300",
                300, aBox.getHeight() + bBox.getHeight() + cBox.getHeight() + dBox.getHeight());
    }

    /**
     * M10：freeze 后余数不整除的末位补余。
     *
     * <p>root(COLUMN,fill) 高 101，a(grow=1,maxHeight=30)、b(grow=1)、c(grow=1)。Σw=3，freeH=101。
     * 第一轮：tentative=101/3=33。a 33&gt;30 撞顶冻 30；b/c 不冻。
     * 第二轮（remainingFree=71, remainingW=2）：tentative=71/2=35。b/c 不冻，退出。
     * active=[b,c] 末位补余：b=71*1/2=35、c=71-35=36。Σ=101。</p>
     */
    @Test
    public void freezeThenRemainderNotDivisible() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode a = new SceneNode();
        a.setFlexGrow(1);
        a.setMaxHeight(30);
        SceneNode b = new SceneNode();
        b.setFlexGrow(1);
        SceneNode c = new SceneNode();
        c.setFlexGrow(1);

        root.appendChild(a);
        root.appendChild(b);
        root.appendChild(c);

        engine.layout(root, new Constraints(200, 101));

        LayoutBox aBox = (LayoutBox) a.getCachedLayout();
        LayoutBox bBox = (LayoutBox) b.getCachedLayout();
        LayoutBox cBox = (LayoutBox) c.getCachedLayout();
        Assert.assertEquals("a 撞顶冻结到 maxHeight=30", 30, aBox.getHeight());
        Assert.assertEquals("b 按比例分得 35", 35, bBox.getHeight());
        Assert.assertEquals("c 末位补余得 36", 36, cBox.getHeight());
        Assert.assertEquals("Σalloc 精确吃满 freeH=101",
                101, aBox.getHeight() + bBox.getHeight() + cBox.getHeight());
    }

    /**
     * M11：上界+下界混合冻结。
     *
     * <p>root(COLUMN,fill) 高 200，a(grow=1,maxHeight=30) 撞顶、b(grow=1,preferredHeight=80) 撞底、
     * c(grow=1) 正常。Σw=3，freeH=200。
     * 第一轮：tentative=200/3=66。a 66&gt;30 撞顶冻 30；b 66&lt;80 撞底冻 80；c 不冻。
     * 第二轮（remainingFree=200-30-80=90, remainingW=1）：c tentative=90 不冻，退出。
     * active=[c] 末位补余：c=90。Σ=200。</p>
     */
    @Test
    public void mixedMaxAndMinFreeze() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode a = new SceneNode();
        a.setFlexGrow(1);
        a.setMaxHeight(30);
        SceneNode b = new SceneNode();
        b.setFlexGrow(1);
        b.setPreferredHeight(80);
        SceneNode c = new SceneNode();
        c.setFlexGrow(1);

        root.appendChild(a);
        root.appendChild(b);
        root.appendChild(c);

        engine.layout(root, new Constraints(200, 200));

        LayoutBox aBox = (LayoutBox) a.getCachedLayout();
        LayoutBox bBox = (LayoutBox) b.getCachedLayout();
        LayoutBox cBox = (LayoutBox) c.getCachedLayout();
        Assert.assertEquals("a 撞顶冻结到 maxHeight=30", 30, aBox.getHeight());
        Assert.assertEquals("b 撞底冻结到 preferredHeight=80", 80, bBox.getHeight());
        Assert.assertEquals("c 回流后末位补余得 90", 90, cBox.getHeight());
        Assert.assertEquals("Σalloc 精确吃满 freeH=200",
                200, aBox.getHeight() + bBox.getHeight() + cBox.getHeight());
    }

    /**
     * M12：不等权重 grow 的 freeze。
     *
     * <p>root(COLUMN,fill) 高 300，a(grow=2,maxHeight=80)、b(grow=1)、c(grow=1)。Σw=4，freeH=300。
     * 第一轮：a tentative=300*2/4=150&gt;80 撞顶冻 80；b=300*1/4=75 不冻；c=75 不冻。
     * 第二轮（remainingFree=220, remainingW=2）：b=220*1/2=110 不冻；c=110 不冻，退出。
     * active=[b,c] 末位补余：b=110、c=110。Σ=300。</p>
     */
    @Test
    public void unequalWeightGrowFreeze() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode a = new SceneNode();
        a.setFlexGrow(2);
        a.setMaxHeight(80);
        SceneNode b = new SceneNode();
        b.setFlexGrow(1);
        SceneNode c = new SceneNode();
        c.setFlexGrow(1);

        root.appendChild(a);
        root.appendChild(b);
        root.appendChild(c);

        engine.layout(root, new Constraints(200, 300));

        LayoutBox aBox = (LayoutBox) a.getCachedLayout();
        LayoutBox bBox = (LayoutBox) b.getCachedLayout();
        LayoutBox cBox = (LayoutBox) c.getCachedLayout();
        Assert.assertEquals("a 撞顶冻结到 maxHeight=80", 80, aBox.getHeight());
        Assert.assertEquals("b 按比例分得 110", 110, bBox.getHeight());
        Assert.assertEquals("c 末位补余得 110", 110, cBox.getHeight());
        Assert.assertEquals("Σalloc 精确吃满 freeH=300",
                300, aBox.getHeight() + bBox.getHeight() + cBox.getHeight());
    }

    // ============================================================
    // align-self（二期）+ 回填一期边界 2（STRETCH 尊重 maxWidth）
    // A1-A5
    // ============================================================

    /**
     * A1：alignSelf 覆盖父级 crossAxisAlign。
     *
     * <p>root(ROW, preferredHeight=100, crossAxisAlign=START)→child(宽20高20, alignSelf=CENTER)。
     * 父级 START 应让子贴顶 y=0，但子 alignSelf=CENTER 覆盖 → 子居中
     * y = (100-20)/2 = 40。验证 effectiveCrossAlign 非 AUTO 时覆盖父级。</p>
     */
    @Test
    public void alignSelfOverridesParentCrossAlign() {
        SceneNode root = SceneNode.row();
        root.setCrossAxisAlign(CrossAxisAlign.START);
        root.setPreferredHeight(100); // innerH=100，无 padding

        SceneNode child = new SceneNode();
        child.setPreferredWidth(20);
        child.setPreferredHeight(20);
        child.setAlignSelf(AlignSelf.CENTER);
        root.appendChild(child);

        engine.layout(root, new Constraints(200));

        LayoutBox childBox = (LayoutBox) child.getCachedLayout();
        Assert.assertEquals("child 宽=20", 20, childBox.getWidth());
        Assert.assertEquals("child 高=20", 20, childBox.getHeight());
        Assert.assertEquals("alignSelf=CENTER 覆盖父级 START：child.y=(100-20)/2=40",
                40, childBox.getY());
    }

    /**
     * A2：alignSelf=AUTO 回退父级 crossAxisAlign。
     *
     * <p>root(ROW, crossAxisAlign=END, innerH=100)→child(宽20高20, alignSelf=AUTO)。
     * AUTO 回退父级 END → 子贴底 y = 100-20 = 80。验证 AUTO 继承父级（零回归）。</p>
     */
    @Test
    public void alignSelfAutoInheritsParent() {
        SceneNode root = SceneNode.row();
        root.setCrossAxisAlign(CrossAxisAlign.END);
        root.setPreferredHeight(100);

        SceneNode child = new SceneNode();
        child.setPreferredWidth(20);
        child.setPreferredHeight(20);
        child.setAlignSelf(AlignSelf.AUTO); // 默认即 AUTO，显式设以明意
        root.appendChild(child);

        engine.layout(root, new Constraints(200));

        LayoutBox childBox = (LayoutBox) child.getCachedLayout();
        Assert.assertEquals("alignSelf=AUTO 回退父级 END：child.y=100-20=80",
                80, childBox.getY());
    }

    /**
     * A3：alignSelf=STRETCH 覆盖父级 START，子被拉满。
     *
     * <p>root(ROW, crossAxisAlign=START, innerH=100)→child(宽20, 无preferredHeight, alignSelf=STRETCH)。
     * 父级 START 不拉满，但子 alignSelf=STRETCH 覆盖 → 子高被拉满到 crossAvail=100。
     * 验证 STRETCH 覆盖父级非 STRETCH 设置。</p>
     */
    @Test
    public void alignSelfStretchVsParentStart() {
        SceneNode root = SceneNode.row();
        root.setCrossAxisAlign(CrossAxisAlign.START);
        root.setPreferredHeight(100);

        SceneNode child = new SceneNode();
        child.setPreferredWidth(20); // 宽20
        // 不设 preferredHeight，让 STRETCH 拉满
        child.setAlignSelf(AlignSelf.STRETCH);
        root.appendChild(child);

        engine.layout(root, new Constraints(200));

        LayoutBox childBox = (LayoutBox) child.getCachedLayout();
        Assert.assertEquals("child 宽=20", 20, childBox.getWidth());
        Assert.assertEquals("alignSelf=STRETCH 覆盖父级 START：child 高拉满到 100",
                100, childBox.getHeight());
    }

    /**
     * A4：alignSelf 改变只重算自身，干净兄弟零重算（I7 反证）。
     *
     * <p>root(ROW, crossAxisAlign=START, innerH=100)→a(宽20高20, alignSelf=CENTER),
     * b(宽20高20, 默认 AUTO=START)。
     * 第一帧：a 居中 y=40、b START y=0。
     * 第二帧：只改 a 的 alignSelf（标 a selfLayoutDirty，b 保持干净）。
     * 断言 b 零重算（不在 relayoutedNodes，LayoutBox 引用复用），a 重算（在 relayoutedNodes）。</p>
     */
    @Test
    public void alignSelfCleanSiblingNotRelayouted() {
        SceneNode root = SceneNode.row();
        root.setCrossAxisAlign(CrossAxisAlign.START);
        root.setPreferredHeight(100);

        SceneNode a = new SceneNode();
        a.setPreferredWidth(20);
        a.setPreferredHeight(20);
        a.setAlignSelf(AlignSelf.CENTER);
        SceneNode b = new SceneNode();
        b.setPreferredWidth(20);
        b.setPreferredHeight(20);
        // b 不设 alignSelf，默认 AUTO → 继承父级 START
        root.appendChild(a);
        root.appendChild(b);

        // 第一帧
        engine.layout(root, new Constraints(200));
        LayoutBox aBox1 = (LayoutBox) a.getCachedLayout();
        LayoutBox bBox1 = (LayoutBox) b.getCachedLayout();
        Assert.assertEquals("第一帧 a 居中 y=40", 40, aBox1.getY());
        Assert.assertEquals("第一帧 b START y=0", 0, bBox1.getY());

        // 第二帧：只改 a 的 alignSelf（标 a selfLayoutDirty，b 保持干净）
        a.setAlignSelf(AlignSelf.END);
        LayoutResult result = engine.layout(root, new Constraints(200));

        LayoutBox aBox2 = (LayoutBox) a.getCachedLayout();
        LayoutBox bBox2 = (LayoutBox) b.getCachedLayout();
        Assert.assertEquals("第二帧 a END y=80", 80, aBox2.getY());
        // I7 反证：b 零重算
        Assert.assertFalse("b 不在重算集合中（零重算）",
                result.getRelayoutedNodes().contains(b));
        Assert.assertTrue("a 在重算集合中（自身脏重算）",
                result.getRelayoutedNodes().contains(a));
        Assert.assertSame("b 的 LayoutBox 引用复用（未被重算）", bBox1, bBox2);
    }

    /**
     * A5：COLUMN 容器 STRETCH 尊重 maxWidth（回填一期边界 2）。
     *
     * <p>root(COLUMN, innerW=200, crossAxisAlign=STRETCH)→child1(无preferredWidth, maxWidth=100),
     * child2(无preferredWidth, 无maxWidth 对照)。
     * child1：computeWidth 已 clamp 到 100，但 STRETCH 改写会拉到 crossAvail=200 覆盖 clamp；
     * 回填后 STRETCH 分支尊重 maxWidth → child1 宽=100（不拉超过 maxWidth）。
     * child2 对照：无 maxWidth → STRETCH 拉满到 200。</p>
     */
    @Test
    public void columnMaxWidthRespectedUnderStretch() {
        SceneNode root = SceneNode.column();
        root.setCrossAxisAlign(CrossAxisAlign.STRETCH); // 显式 STRETCH（也是默认）

        SceneNode child1 = new SceneNode();
        child1.setMaxWidth(100); // 无 preferredWidth，有 maxWidth
        SceneNode child2 = new SceneNode();
        // child2 无 preferredWidth 无 maxWidth，对照 STRETCH 拉满
        root.appendChild(child1);
        root.appendChild(child2);

        engine.layout(root, new Constraints(200));

        LayoutBox c1Box = (LayoutBox) child1.getCachedLayout();
        LayoutBox c2Box = (LayoutBox) child2.getCachedLayout();
        Assert.assertEquals("child1 STRETCH 尊重 maxWidth=100，不拉满到 200",
                100, c1Box.getWidth());
        Assert.assertEquals("child2 对照：无 maxWidth，STRETCH 拉满到 200",
                200, c2Box.getWidth());
    }

    /**
     * A6：COLUMN 下 alignSelf=CENTER 水平居中。
     *
     * <p>root(COLUMN, innerW=200, crossAxisAlign=START)→child(宽20高20, alignSelf=CENTER)。
     * 父级 START 应让子贴左 x=0，但子 alignSelf=CENTER 覆盖 → 子水平居中
     * x = (200-20)/2 = 90。验证 COLUMN 容器下 alignSelf 对 cross=宽的对齐覆盖。</p>
     */
    @Test
    public void columnAlignSelfCenter() {
        SceneNode root = SceneNode.column();
        root.setCrossAxisAlign(CrossAxisAlign.START); // innerW=200，无 padding

        SceneNode child = new SceneNode();
        child.setPreferredWidth(20);
        child.setPreferredHeight(20);
        child.setAlignSelf(AlignSelf.CENTER);
        root.appendChild(child);

        engine.layout(root, new Constraints(200));

        LayoutBox childBox = (LayoutBox) child.getCachedLayout();
        Assert.assertEquals("child 宽=20", 20, childBox.getWidth());
        Assert.assertEquals("child 高=20", 20, childBox.getHeight());
        Assert.assertEquals("COLUMN+alignSelf=CENTER 覆盖父级 START：child.x=(200-20)/2=90",
                90, childBox.getX());
    }

    /**
     * A7：嵌套 align-self 不串味（alignSelf 只影响自身在父内的对齐，不向下传递）。
     *
     * <p>root(ROW, crossAxisAlign=START, innerH=100)→mid(ROW, crossAxisAlign=END, innerH=80,
     * alignSelf=CENTER)→leaf(宽20高20)。
     * <ul>
     *   <li>mid 自身在 root 内 y = (100-80)/2 = 40（alignSelf=CENTER 覆盖 root 的 START）</li>
     *   <li>leaf 在 mid 内 y = 80-20 = 60（leaf 无 alignSelf 继承 mid 的 crossAxisAlign=END）</li>
     *   <li>leaf 不受 root 的 START 或 mid 的 alignSelf=CENTER 影响</li>
     * </ul></p>
     */
    @Test
    public void nestedAlignSelfNoBleed() {
        SceneNode root = SceneNode.row();
        root.setCrossAxisAlign(CrossAxisAlign.START);
        root.setPreferredHeight(100); // innerH=100，无 padding

        SceneNode mid = SceneNode.row();
        mid.setCrossAxisAlign(CrossAxisAlign.END);
        mid.setPreferredHeight(80); // innerH=80
        mid.setAlignSelf(AlignSelf.CENTER); // 覆盖 root 的 START

        SceneNode leaf = new SceneNode();
        leaf.setPreferredWidth(20);
        leaf.setPreferredHeight(20);
        // leaf 不设 alignSelf，默认 AUTO → 继承 mid 的 crossAxisAlign=END
        mid.appendChild(leaf);
        root.appendChild(mid);

        engine.layout(root, new Constraints(200));

        LayoutBox midBox = (LayoutBox) mid.getCachedLayout();
        LayoutBox leafBox = (LayoutBox) leaf.getCachedLayout();
        // mid 自身在 root 内：alignSelf=CENTER 覆盖 root 的 START → y=(100-80)/2=10
        Assert.assertEquals("mid 自身 y=(100-80)/2=10（alignSelf=CENTER 覆盖 root START）",
                10, midBox.getY());
        Assert.assertEquals("mid 高=80", 80, midBox.getHeight());
        // leaf 在 mid 内：无 alignSelf 继承 mid 的 END → y=80-20=60
        // 注意 leaf.y 是相对 mid 的局部坐标，mid 的 crossPos=END → leaf.y=60
        Assert.assertEquals("leaf 在 mid 内 y=80-20=60（继承 mid 的 END，不受 root START 或 mid alignSelf 影响）",
                60, leafBox.getY());
    }

    /**
     * A8：ROW+STRETCH+maxWidth 不影响高（反证 maxWidth 只作用于主轴宽上界）。
     *
     * <p>root(ROW, crossAxisAlign=STRETCH, innerH=100)→child(宽20无preferredHeight, maxWidth=50)。
     * ROW 容器 cross=高，STRETCH 拉满高 → 子高=100。maxWidth 是主轴宽上界，
     * 不影响 cross=高；且子宽=20（computeWidth 自然宽）&lt; maxWidth=50，maxWidth 不触发 clamp。</p>
     */
    @Test
    public void rowStretchMaxWidthDoesNotAffectHeight() {
        SceneNode root = SceneNode.row();
        root.setCrossAxisAlign(CrossAxisAlign.STRETCH); // 显式 STRETCH（也是默认）
        root.setPreferredHeight(100); // innerH=100，无 padding

        SceneNode child = new SceneNode();
        child.setPreferredWidth(20); // 宽 20
        // 不设 preferredHeight，让 STRETCH 拉满高
        child.setMaxWidth(50); // 主轴宽上界 50，但子宽 20<50 不触发
        root.appendChild(child);

        engine.layout(root, new Constraints(200));

        LayoutBox childBox = (LayoutBox) child.getCachedLayout();
        // ROW cross=高，STRETCH 拉满到 innerH=100，maxWidth 不削高
        Assert.assertEquals("ROW+STRETCH 子高拉满到 100（maxWidth 不影响 cross=高）",
                100, childBox.getHeight());
        // 子宽=20（computeWidth 自然宽），maxWidth=50 不触发因 20<50
        Assert.assertEquals("子宽=20（computeWidth 自然宽，maxWidth=50 不触发）",
                20, childBox.getWidth());
    }

    /**
     * A9：preferredWidth==crossAvail 且 maxWidth&lt;preferredWidth 时 preferredWidth 赢
     * （守卫修复反证：stretched boolean 精确区分「被 STRETCH 改写」与「豁免子内在尺寸等于 crossAvail」）。
     *
     * <p>root(COLUMN, innerW=200, crossAxisAlign=STRETCH)→child(preferredWidth=200, maxWidth=100,
     * 自适应高)。preferredWidth=200&gt;0 → 豁免 STRETCH 改写，保 childCrossSize=200。
     * 旧守卫 {@code finalCrossSize == crossAvail} 在 preferredWidth==crossAvail==200 时误触发，
     * 把豁免子 clamp 到 maxWidth=100；新守卫 {@code stretched=false}（因 preferredWidth&gt;0 豁免）
     * 不 clamp，preferredWidth=200 赢。此测试在守卫修复前会失败（误 clamp 到 100），修复后通过。</p>
     */
    @Test
    public void preferredWidthBeatsMaxWidthUnderStretch() {
        SceneNode root = SceneNode.column();
        root.setCrossAxisAlign(CrossAxisAlign.STRETCH); // 显式 STRETCH（也是默认）

        SceneNode child = new SceneNode();
        child.setPreferredWidth(200); // == crossAvail=innerW=200，豁免 STRETCH
        child.setMaxWidth(100); // < preferredWidth，旧守卫会误 clamp
        // 自适应高：无文本无子 → 高 0
        root.appendChild(child);

        engine.layout(root, new Constraints(200));

        LayoutBox childBox = (LayoutBox) child.getCachedLayout();
        // preferredWidth=200 优先级最高，不被 maxWidth=100 clamp 压低
        Assert.assertEquals("preferredWidth=200 赢，不被 maxWidth=100 误 clamp（守卫 stretched=false）",
                200, childBox.getWidth());
    }

    // ============================================================
    // margin（三期）：四向外边距在主轴/交叉轴的占用与偏移
    // ============================================================

    /**
     * G1：COLUMN 子 marginV 计入主轴占用。
     *
     * <p>root(COLUMN)→a(高20, marginTop=10, marginBottom=10), b(高20)。
     * a.y=10（marginTop 偏移），b.y=a.y+20+marginBottom(10)=40（a 占用=20+10+10=40，b 紧跟）。
     * root 内容高=（20+20）+（20+0）=60（含 a.marginV=20）。</p>
     */
    @Test
    public void columnChildMarginVAddsToMainAxis() {
        SceneNode root = SceneNode.column();

        SceneNode a = new SceneNode();
        a.setPreferredHeight(20);
        a.setMargin(10, 0, 10, 0);   // marginV=20

        SceneNode b = new SceneNode();
        b.setPreferredHeight(20);

        root.appendChild(a);
        root.appendChild(b);

        engine.layout(root, new Constraints(200, 100));

        LayoutBox boxA = (LayoutBox) a.getCachedLayout();
        LayoutBox boxB = (LayoutBox) b.getCachedLayout();
        LayoutBox rootBox = (LayoutBox) root.getCachedLayout();

        Assert.assertEquals("a.y=10（marginTop 偏移）", 10, boxA.getY());
        Assert.assertEquals("b.y=40（a 占用 20+10+10=40，b 紧跟）", 40, boxB.getY());
        Assert.assertEquals("root 内容高=60（含 a.marginV=20）", 60, rootBox.getHeight());
    }

    /**
     * G2：margin 扣减 freeH（grow 分配）。
     *
     * <p>root(COLUMN, fill, 高=100)→fixed(高20, marginTop=10, marginBottom=0), grow(grow=1, marginTop=10, marginBottom=0)。
     * freeH = 100 - (20+10) - 10 - 0 = 60（fixed 含 margin=30, grow.marginV=10）。
     * grow 高=60（不含自身 marginV）。fixed.y=10（marginTop），grow.y=40（fixed 占用 20+10=30 + grow.marginTop 10）。</p>
     */
    @Test
    public void marginAffectsGrowFreeHeight() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode fixed = new SceneNode();
        fixed.setPreferredHeight(20);
        fixed.setMargin(10, 0, 0, 0);   // marginV=10

        SceneNode grow = new SceneNode();
        grow.setFlexGrow(1);
        grow.setMargin(10, 0, 0, 0);    // marginV=10

        root.appendChild(fixed);
        root.appendChild(grow);

        engine.layout(root, new Constraints(200, 100));

        LayoutBox fixedBox = (LayoutBox) fixed.getCachedLayout();
        LayoutBox growBox = (LayoutBox) grow.getCachedLayout();

        Assert.assertEquals("grow 高=60（freeH 不含自身 marginV）", 60, growBox.getHeight());
        Assert.assertEquals("fixed.y=10（marginTop 偏移）", 10, fixedBox.getY());
        Assert.assertEquals("grow.y=40（fixed 占用 30 + grow.marginTop 10）", 40, growBox.getY());
    }

    /**
     * G3：margin + cross 居中不错位。
     *
     * <p>root(ROW, crossAxisAlign=CENTER, innerH=100)→child(宽20高20, marginTop=10, marginBottom=10)。
     * 子占位高=20+20=40。crossPos=(100-40)/2=30。child.y=padTop+30+marginTop=40。</p>
     */
    @Test
    public void marginWithCenterCrossAlign() {
        SceneNode root = SceneNode.row();
        root.setCrossAxisAlign(CrossAxisAlign.CENTER);
        root.setFillParentHeight(true);

        SceneNode child = new SceneNode();
        child.setPreferredWidth(20);
        child.setPreferredHeight(20);
        child.setMargin(10, 0, 10, 0);   // marginV=20

        root.appendChild(child);

        engine.layout(root, new Constraints(200, 100));

        LayoutBox childBox = (LayoutBox) child.getCachedLayout();
        Assert.assertEquals("child.y=40（居中 crossPos=30 + marginTop=10）", 40, childBox.getY());
    }

    /**
     * G4：margin 改变只重算自身，干净兄弟零重算（I7 反证）。
     *
     * <p>root(ROW, innerH=100)→a(宽20高20, marginLeft=10), b(宽20高20)。
     * 第一帧 a.x=10（marginLeft 偏移），b.x=30（a 占用 20+10+0=30，b 紧跟）。
     * 第二帧只改 a.marginLeft=20（标 a selfLayoutDirty，b 干净），断言 b 零重算、a 重算。</p>
     */
    @Test
    public void marginCleanSiblingNotRelayouted() {
        SceneNode root = SceneNode.row();
        root.setFillParentHeight(true);

        SceneNode a = new SceneNode();
        a.setPreferredWidth(20);
        a.setPreferredHeight(20);
        a.setMargin(0, 0, 0, 10);   // marginLeft=10

        SceneNode b = new SceneNode();
        b.setPreferredWidth(20);
        b.setPreferredHeight(20);

        root.appendChild(a);
        root.appendChild(b);

        // 第一帧
        engine.layout(root, new Constraints(200, 100));
        LayoutBox boxA1 = (LayoutBox) a.getCachedLayout();
        LayoutBox boxB1 = (LayoutBox) b.getCachedLayout();
        Assert.assertEquals("第一帧 a.x=10（marginLeft 偏移）", 10, boxA1.getX());
        Assert.assertEquals("第一帧 b.x=30（a 占用 30，b 紧跟）", 30, boxB1.getX());

        // 第二帧：只改 a.marginLeft 10→20（标 a selfLayoutDirty，b 保持干净）
        a.setMargin(0, 0, 0, 20);
        LayoutResult result = engine.layout(root, new Constraints(200, 100));

        Assert.assertTrue("a 在重算集合（自身 margin 变）",
                result.getRelayoutedNodes().contains(a));
        Assert.assertFalse("b 不在重算集合（I7 干净兄弟零重算）",
                result.getRelayoutedNodes().contains(b));
    }

    /**
     * G5：ROW 主轴 margin 累加。
     *
     * <p>root(ROW, innerW=200)→a(宽30, marginLeft=10, marginRight=5), b(宽40)。
     * a.x=10（marginLeft），b.x=a.x+30+5+0=45（a 占用=30+10+5=45，b 紧跟）。</p>
     */
    @Test
    public void rowMarginMainAxis() {
        SceneNode root = SceneNode.row();

        SceneNode a = new SceneNode();
        a.setPreferredWidth(30);
        a.setPreferredHeight(20);
        a.setMargin(0, 5, 0, 10);   // marginLeft=10, marginRight=5

        SceneNode b = new SceneNode();
        b.setPreferredWidth(40);
        b.setPreferredHeight(20);

        root.appendChild(a);
        root.appendChild(b);

        engine.layout(root, new Constraints(200));

        LayoutBox boxA = (LayoutBox) a.getCachedLayout();
        LayoutBox boxB = (LayoutBox) b.getCachedLayout();
        Assert.assertEquals("a.x=10（marginLeft 偏移）", 10, boxA.getX());
        Assert.assertEquals("b.x=45（a 占用 30+10+5=45，b 紧跟）", 45, boxB.getX());
    }

    /**
     * G6：COLUMN+STRETCH+marginH，子内容宽=可用-marginH。
     *
     * <p>root(COLUMN, innerW=200, crossAxisAlign=STRETCH)→child(无preferredWidth, marginLeft=20, marginRight=20)。
     * STRETCH finalCrossSize = 200 - 40 = 160。child.x = padLeft + 0 + marginLeft = 20。child.width=160。</p>
     */
    @Test
    public void columnMarginCrossStretchRespected() {
        SceneNode root = SceneNode.column();
        root.setCrossAxisAlign(CrossAxisAlign.STRETCH);   // 显式 STRETCH（也是默认）

        SceneNode child = new SceneNode();
        child.setPreferredHeight(20);   // 给个高让子有可见盒
        child.setMargin(0, 20, 0, 20);  // marginH=40

        root.appendChild(child);

        engine.layout(root, new Constraints(200));

        LayoutBox childBox = (LayoutBox) child.getCachedLayout();
        Assert.assertEquals("child.x=20（crossPos=0 + marginLeft=20）", 20, childBox.getX());
        Assert.assertEquals("child.width=160（crossAvail=200 - marginH=40）", 160, childBox.getWidth());
    }

    /**
     * G7：grow 子 maxHeight + marginV，freeze 撞顶后 margin 正确处理。
     *
     * <p>root(COLUMN, fill, 高=200)→a(grow=1, maxHeight=50, marginTop=10, marginBottom=10), b(grow=1)。
     * freeH = 200 - (a.marginV=20) - (b.marginV=0) - 0 = 180。
     * freeze：a tentative=180*1/2=90 > 50 撞顶冻 50。b = 180-50=130。
     * a.y=10（marginTop），b.y=a.y+50+10+0=70（a 占用=50+10+10=70）。</p>
     */
    @Test
    public void marginPlusFreezeDoWhile() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode a = new SceneNode();
        a.setFlexGrow(1);
        a.setMaxHeight(50);
        a.setMargin(10, 0, 10, 0);   // marginV=20

        SceneNode b = new SceneNode();
        b.setFlexGrow(1);

        root.appendChild(a);
        root.appendChild(b);

        engine.layout(root, new Constraints(200, 200));

        LayoutBox boxA = (LayoutBox) a.getCachedLayout();
        LayoutBox boxB = (LayoutBox) b.getCachedLayout();

        Assert.assertEquals("a.height=50（撞顶冻结到 maxHeight）", 50, boxA.getHeight());
        Assert.assertEquals("b.height=130（freeH=180 - a 冻结 50）", 130, boxB.getHeight());
        Assert.assertEquals("a.y=10（marginTop 偏移）", 10, boxA.getY());
        Assert.assertEquals("b.y=70（a 占用 50+10+10=70，b 紧跟）", 70, boxB.getY());
    }

    /**
     * G8：ROW+STRETCH+marginV，子内容高 = 可用高 - marginV。
     *
     * <p>root(ROW, crossAxisAlign=STRETCH, innerH=100)→child(宽20无preferredHeight, marginTop=10, marginBottom=10)。
     * STRETCH finalCrossSize = 100 - 20 = 80。child.y = padTop + crossPos(0) + marginTop = 10。child.height=80。</p>
     */
    @Test
    public void rowStretchMarginVSubtractsFromHeight() {
        SceneNode root = SceneNode.row();
        root.setCrossAxisAlign(CrossAxisAlign.STRETCH);
        root.setFillParentHeight(true);

        SceneNode child = new SceneNode();
        child.setPreferredWidth(20);
        child.setMargin(10, 0, 10, 0);   // marginV=20

        root.appendChild(child);

        engine.layout(root, new Constraints(200, 100));

        LayoutBox childBox = (LayoutBox) child.getCachedLayout();
        Assert.assertEquals("child.y=10（crossPos=0 + marginTop=10）", 10, childBox.getY());
        Assert.assertEquals("child.height=80（crossAvail=100 - marginV=20）", 80, childBox.getHeight());
    }

    /**
     * G9：SHRINK 容器包住子 marginH（reviewer 问题 1 修复验证）。
     *
     * <p>root(COLUMN, widthSizing=SHRINK, innerW=300)→child(宽50, marginLeft=20, marginRight=20)。
     * SHRINK 容器宽 = 50 + 40 + padH = 90（含子 marginH=40，padH=0）。</p>
     */
    @Test
    public void shrinkContainerIncludesMarginH() {
        SceneNode root = SceneNode.column();
        root.setWidthSizing(SceneNode.WidthSizing.SHRINK);

        SceneNode child = new SceneNode();
        child.setPreferredWidth(50);
        child.setPreferredHeight(20);   // 给个高让子有可见盒
        child.setMargin(0, 20, 0, 20);  // marginH=40

        root.appendChild(child);

        engine.layout(root, new Constraints(300));

        LayoutBox rootBox = (LayoutBox) root.getCachedLayout();
        Assert.assertEquals("root.width=90（SHRINK 包住子占位 50+marginH 40+padH 0）",
                90, rootBox.getWidth());
    }

    /**
     * G10：scrollable + margin，maxScrollY 含 marginBottom（reviewer 问题 3 修复验证）。
     *
     * <p>root(COLUMN, scrollable=true, preferredHeight=80)→a(高50), b(高30, marginBottom=20)。
     * 内容总高 = 50 + 30 + 20 = 100（含 b.marginBottom）。maxScrollY = 100 - 80 = 20。</p>
     */
    @Test
    public void scrollableMaxScrollYIncludesMarginBottom() {
        SceneNode root = SceneNode.column();
        root.setScrollable(true);
        root.setPreferredHeight(80);

        SceneNode a = new SceneNode();
        a.setPreferredHeight(50);

        SceneNode b = new SceneNode();
        b.setPreferredHeight(30);
        b.setMargin(0, 0, 20, 0);   // marginBottom=20

        root.appendChild(a);
        root.appendChild(b);

        engine.layout(root, new Constraints(200));

        int maxScroll = SceneGeometry.maxScrollY(root);
        Assert.assertEquals("maxScrollY=20（滚动范围覆盖到 b.marginBottom 底边）",
                20, maxScroll);
    }

    /**
     * G11：G4 第二帧位置断言 —— margin 改变后干净兄弟位置正确更新。
     *
     * <p>root(ROW, innerW=200)→a(宽20, marginLeft=10), b(宽20)。
     * 第一帧：a.x=10, b.x=30。第二帧改 a.marginLeft=20（标 a selfLayoutDirty，b 干净）。
     * 断言第二帧 a.x=20, b.x=40（a 占用=20+20+0=40，b 紧跟），b 零重算。</p>
     */
    @Test
    public void marginChangeUpdatesSiblingPosition() {
        SceneNode root = SceneNode.row();
        root.setFillParentHeight(true);

        SceneNode a = new SceneNode();
        a.setPreferredWidth(20);
        a.setPreferredHeight(20);
        a.setMargin(0, 0, 0, 10);   // marginLeft=10

        SceneNode b = new SceneNode();
        b.setPreferredWidth(20);
        b.setPreferredHeight(20);

        root.appendChild(a);
        root.appendChild(b);

        // 第一帧
        engine.layout(root, new Constraints(200, 100));
        LayoutBox boxA1 = (LayoutBox) a.getCachedLayout();
        LayoutBox boxB1 = (LayoutBox) b.getCachedLayout();
        Assert.assertEquals("第一帧 a.x=10（marginLeft 偏移）", 10, boxA1.getX());
        Assert.assertEquals("第一帧 b.x=30（a 占用 30，b 紧跟）", 30, boxB1.getX());

        // 第二帧：只改 a.marginLeft 10→20（标 a selfLayoutDirty，b 保持干净）
        a.setMargin(0, 0, 0, 20);
        LayoutResult result = engine.layout(root, new Constraints(200, 100));

        LayoutBox boxA2 = (LayoutBox) a.getCachedLayout();
        LayoutBox boxB2 = (LayoutBox) b.getCachedLayout();
        Assert.assertEquals("第二帧 a.x=20（marginLeft 偏移）", 20, boxA2.getX());
        Assert.assertEquals("第二帧 b.x=40（a 占用 40，b 紧跟）", 40, boxB2.getX());
        Assert.assertTrue("a 在重算集合（自身 margin 变）",
                result.getRelayoutedNodes().contains(a));
        Assert.assertFalse("b 不在重算集合（I7 干净兄弟零重算）",
                result.getRelayoutedNodes().contains(b));
        Assert.assertEquals("b 零重算（relayoutCount 仅含 a）",
                1, result.getRelayoutCount());
    }

    // ============================================================
    // percent（四期）：百分比高度/宽度
    // P1-P7
    // ============================================================

    /**
     * P1：percentHeight 相对父先验内高。
     *
     * <p>root(COLUMN, fill, 高=200)→child(percentHeight=50, 无 flexGrow/fill)。
     * 父先验内高=200，child 高=200*50/100=100。percent 子作固定子，下传 tight 高=100，
     * child 隐式 fill 返回 max(contentHeight=0, 100)=100。</p>
     */
    @Test
    public void percentHeightRelativeToParentInner() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode child = new SceneNode();
        child.setPercentHeight(50);
        root.appendChild(child);

        engine.layout(root, new Constraints(200, 200));

        LayoutBox childBox = (LayoutBox) child.getCachedLayout();
        Assert.assertNotNull("child 应有 cachedLayout", childBox);
        Assert.assertEquals("percentHeight=50 相对父内高 200 → child 高=100",
                100, childBox.getHeight());
    }

    /**
     * P2：父高不可先验时 percentHeight 失效回退 shrink。
     *
     * <p>root(COLUMN, 不 fill, 无 preferredHeight, 无高约束)→child(percentHeight=50, 文本"X" 自然高16)。
     * root 收 Constraints(200)（高 UNCONSTRAINED）→ priorKnownInnerHeight 返回 UNCONSTRAINED
     * → computeColumnGrowHeights 早退空 Map → child 下传高 UNCONSTRAINED
     * → child computeHeight: percentHeight>0 但 hasHeightConstraint=false → 走兜底 shrink=16。
     * 断言 child 高=16（fallback shrink，不是 0 或报错）。</p>
     */
    @Test
    public void percentFallbackToShrinkWhenNoConstraint() {
        SceneNode root = SceneNode.column();
        // 不 fill、无 preferredHeight → 父高不先验

        SceneNode child = new SceneNode();
        child.setPercentHeight(50);
        child.setText("X"); // 自然高 16
        root.appendChild(child);

        engine.layout(root, new Constraints(200)); // 无高约束

        LayoutBox childBox = (LayoutBox) child.getCachedLayout();
        Assert.assertNotNull("child 应有 cachedLayout", childBox);
        Assert.assertEquals("父高不可先验时 percentHeight 失效回退 shrink=16",
                16, childBox.getHeight());
    }

    /**
     * P3：grow 优先，percentHeight 被忽略。
     *
     * <p>root(COLUMN, fill, 高=200)→a(flexGrow=1, percentHeight=50)。
     * effectiveGrow(a)=1>0 → 走 grow 分支，percent 忽略。a 是唯一 grow 子，吃满 freeH=200。
     * 断言 a 高=200（grow 分配），不是 percentHeight=100。</p>
     */
    @Test
    public void percentIgnoredWhenGrowSet() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode a = new SceneNode();
        a.setFlexGrow(1);
        a.setPercentHeight(50);
        root.appendChild(a);

        engine.layout(root, new Constraints(200, 200));

        LayoutBox aBox = (LayoutBox) a.getCachedLayout();
        Assert.assertEquals("grow 优先：a 高=200（grow 分配），不是 percentHeight=100",
                200, aBox.getHeight());
    }

    /**
     * P4：percentWidth 相对父内宽。
     *
     * <p>root(COLUMN, innerW=200)→child(percentWidth=30, 无 preferredWidth)。
     * child 下传宽=200（父内宽），computeWidth: percentWidth=30>0 且 availableWidth=200≠UNCONSTRAINED
     * → pctW=200*30/100=60。断言 child 宽=60。</p>
     */
    @Test
    public void percentWidthRelativeToParentInner() {
        SceneNode root = SceneNode.column();

        SceneNode child = new SceneNode();
        child.setPercentWidth(30);
        child.setPreferredHeight(20); // 给个高让子有可见盒
        root.appendChild(child);

        engine.layout(root, new Constraints(200));

        LayoutBox childBox = (LayoutBox) child.getCachedLayout();
        Assert.assertNotNull("child 应有 cachedLayout", childBox);
        Assert.assertEquals("percentWidth=30 相对父内宽 200 → child 宽=60",
                60, childBox.getWidth());
    }

    /**
     * P5：percentWidth 无宽约束时回退 shrink。
     *
     * <p>直接 layout 文本叶 percentWidth=30，Constraints(UNCONSTRAINED, 100)（宽无约束）。
     * computeWidth: percentWidth=30>0 但 availableWidth==UNCONSTRAINED → 跳过 percent 分支，
     * 进入文本 shrink 分支。percent 未生效（width ≠ pctW）。
     * 断言 percent 未生效（width != 60），验证 fallback 路径不触发 percent 分支。</p>
     *
     * <p><b>边界说明</b>：现有 computeWidth 文本 shrink 分支 {@code min(outerWidth, intrinsic)}
     * 在 outerWidth=UNCONSTRAINED(-1) 时返回 -1（现有边界行为，非 percent 引入）。
     * 本测试断言"percent 未生效"语义，不断言精确自然宽，以避免与现有 UNCONSTRAINED 边界冲突。</p>
     */
    @Test
    public void percentWidthFallbackToShrink() {
        SceneNode leaf = new SceneNode();
        leaf.setText("XXXXX"); // 5 字符 × 8 = 40 自然宽
        leaf.setPercentWidth(30);

        // 宽无约束（UNCONSTRAINED），高 100
        engine.layout(leaf, new Constraints(Constraints.UNCONSTRAINED, 100));

        LayoutBox leafBox = (LayoutBox) leaf.getCachedLayout();
        Assert.assertNotNull("leaf 应有 cachedLayout", leafBox);
        // percent 未生效：若有宽约束 pctW 会是 60；无宽约束时 percent 分支跳过，width ≠ 60
        Assert.assertNotEquals("无宽约束时 percentWidth 不生效（fallback shrink，非 pctW=60）",
                60, leafBox.getWidth());
    }

    /**
     * P6：percent 子作固定子，与 grow 子共存。
     *
     * <p>root(COLUMN, fill, 高=200)→a(grow=1), b(percentHeight=30, 无 grow)。
     * b 作固定子=200*30/100=60，占用 fixedH=60。freeH=200-60=140。a 是唯一 grow 子吃满 140。
     * 断言 a 高=140, b 高=60。</p>
     */
    @Test
    public void percentHeightAsFixedChildInGrowContainer() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode a = new SceneNode();
        a.setFlexGrow(1);
        SceneNode b = new SceneNode();
        b.setPercentHeight(30);

        root.appendChild(a);
        root.appendChild(b);

        engine.layout(root, new Constraints(200, 200));

        LayoutBox aBox = (LayoutBox) a.getCachedLayout();
        LayoutBox bBox = (LayoutBox) b.getCachedLayout();
        Assert.assertEquals("b percent 子作固定子=200*30/100=60", 60, bBox.getHeight());
        Assert.assertEquals("a grow 子吃剩余 freeH=200-60=140", 140, aBox.getHeight());
    }

    /**
     * P7：percent 子改变只重算自身，干净兄弟零重算（I7 反证）。
     *
     * <p>root(COLUMN, fill, 高=200)→a(percentHeight=50), b(文本"X" 自然高16)。
     * 第一帧：a=100（percent 固定子），b=16（文本 shrink，不在 alloc，下传 UNCONSTRAINED）。
     * 第二帧：只改 a.percentHeight=60（标 a selfLayoutDirty，b 保持干净）。
     * 断言：b 零重算（relayoutCount=0 对 b，不在 relayoutedNodes），a 重算（a=120）。</p>
     */
    @Test
    public void percentHeightCleanSiblingNotRelayouted() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode a = new SceneNode();
        a.setPercentHeight(50);
        SceneNode b = new SceneNode();
        b.setText("X"); // 自然高 16

        root.appendChild(a);
        root.appendChild(b);

        // 第一帧
        engine.layout(root, new Constraints(200, 200));
        LayoutBox aBox1 = (LayoutBox) a.getCachedLayout();
        LayoutBox bBox1 = (LayoutBox) b.getCachedLayout();
        Assert.assertEquals("第一帧 a=100（percentHeight=50）", 100, aBox1.getHeight());
        Assert.assertEquals("第一帧 b=16（文本 shrink）", 16, bBox1.getHeight());

        // 第二帧：只改 a.percentHeight 50→60（标 a selfLayoutDirty，b 保持干净）
        a.setPercentHeight(60);
        LayoutResult result = engine.layout(root, new Constraints(200, 200));

        LayoutBox aBox2 = (LayoutBox) a.getCachedLayout();
        LayoutBox bBox2 = (LayoutBox) b.getCachedLayout();
        Assert.assertEquals("第二帧 a=120（percentHeight=60）", 120, aBox2.getHeight());
        Assert.assertEquals("第二帧 b 高不变=16", 16, bBox2.getHeight());
        // I7 反证：a 重算，b 零重算
        Assert.assertTrue("a 在重算集合（自身 percent 变）",
                result.getRelayoutedNodes().contains(a));
        Assert.assertFalse("b 不在重算集合（I7 干净兄弟零重算）",
                result.getRelayoutedNodes().contains(b));
        Assert.assertEquals("relayoutCount 仅含 a（b 零重算）",
                1, result.getRelayoutCount());
    }

    /**
     * P8：percent 子有 maxHeight &lt; pctH 时 clamp（reviewer 问题 1 修复验证）。
     *
     * <p>root(COLUMN, fill, 高=200)→a(grow=1), b(percentHeight=50, maxHeight=80)。
     * b pctH=200*50/100=100，clamp 到 maxHeight=80。fixedH=80，freeH=200-80=120，
     * a 是唯一 grow 子吃满 120。b 下传 tight=80，computeHeight fill 分支 max(content=0, 80)=80。
     * 断言 a.height=120, b.height=80。
     * 若未 clamp（旧 bug）：fixedH=100, freeH=100, a=100, 但 b 实际高=clampHeight(100)=80，
     * 留白 20——本测试守护此回归。</p>
     */
    @Test
    public void percentPlusMaxHeightClamp() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode a = new SceneNode();
        a.setFlexGrow(1);
        SceneNode b = new SceneNode();
        b.setPercentHeight(50);
        b.setMaxHeight(80);

        root.appendChild(a);
        root.appendChild(b);

        engine.layout(root, new Constraints(200, 200));

        LayoutBox aBox = (LayoutBox) a.getCachedLayout();
        LayoutBox bBox = (LayoutBox) b.getCachedLayout();
        Assert.assertEquals("b pctH=100 clamp 到 maxHeight=80", 80, bBox.getHeight());
        Assert.assertEquals("a grow 子吃 freeH=200-80=120（clamp 后无留白）",
                120, aBox.getHeight());
    }

    /**
     * P9：percent 子内容高 &gt; pctH 时 fixedH 用内容高（reviewer 问题 2 修复验证）。
     *
     * <p>root(COLUMN, fill, 高=200)→a(grow=1), b(percentHeight=10, 文本 6 行自然高 96)。
     * b pctH=200*10/100=20，priorKnownChildHeight(b)=6*16+0=96，
     * effectiveFixedH=max(20, 96)=96。fixedH=96，freeH=200-96=104，a 吃满 104。
     * b 下传 tight=96，computeHeight fill 分支 max(96, 96)=96。
     * 断言 a.height=104, b.height=96。
     * 若用 pctH（旧 bug）：fixedH=20, freeH=180, a=180, 但 b 实际高=max(96, 20)=96，
     * 溢出 84——本测试守护此回归。</p>
     */
    @Test
    public void percentChildContentExceedsPctH() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode a = new SceneNode();
        a.setFlexGrow(1);
        SceneNode b = new SceneNode();
        b.setPercentHeight(10);
        // 6 行文本：5 个 '\n' 切出 6 行，自然高 = 6 * 16 = 96（padV=0）
        b.setText("A\nB\nC\nD\nE\nF");

        root.appendChild(a);
        root.appendChild(b);

        engine.layout(root, new Constraints(200, 200));

        LayoutBox aBox = (LayoutBox) a.getCachedLayout();
        LayoutBox bBox = (LayoutBox) b.getCachedLayout();
        Assert.assertEquals("b 内容高 96 > pctH 20 → effectiveFixedH=max(20,96)=96",
                96, bBox.getHeight());
        Assert.assertEquals("a grow 子吃 freeH=200-96=104（内容撑大下界保护，无溢出）",
                104, aBox.getHeight());
    }

    /**
     * P10：percent 子有 marginV，验证 marginV 计入 fixedH。
     *
     * <p>root(COLUMN, fill, 高=200)→a(grow=1), b(percentHeight=50, marginTop=10, marginBottom=10)。
     * b pctH=200*50/100=100，fixedH=100+20(marginV)=120，freeH=200-120=80，a 吃满 80。
     * b 下传 tight=100，高=100。b.y = a.height(80) + b.marginTop(10) = 90。
     * 断言 a.height=80, b.height=100, b.y=90。
     * 若 marginV 未计入 fixedH（潜在 bug）：fixedH=100, freeH=100, a=100——本测试守护此回归。</p>
     */
    @Test
    public void percentPlusMargin() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode a = new SceneNode();
        a.setFlexGrow(1);
        SceneNode b = new SceneNode();
        b.setPercentHeight(50);
        b.setMargin(10, 0, 10, 0); // marginTop=10, marginBottom=10

        root.appendChild(a);
        root.appendChild(b);

        engine.layout(root, new Constraints(200, 200));

        LayoutBox aBox = (LayoutBox) a.getCachedLayout();
        LayoutBox bBox = (LayoutBox) b.getCachedLayout();
        Assert.assertEquals("b percent 子高=pctH=100（marginV 不影响子自身高）",
                100, bBox.getHeight());
        Assert.assertEquals("a grow 子吃 freeH=200-(100+20)=80（marginV 计入 fixedH）",
                80, aBox.getHeight());
        Assert.assertEquals("b.y = a.height(80) + b.marginTop(10) = 90",
                90, bBox.getY());
    }

    /**
     * P11：ROW 下 percentHeight 不生效（reviewer 问题 6 验证）。
     *
     * <p>root(ROW, fillParentHeight, 高=100, crossAxisAlign=STRETCH)
     * →child(percentHeight=50, 无 preferredHeight)。
     * ROW 主轴=宽，高是交叉轴；percentHeight 只在 COLUMN 主轴 grow 求解器里识别，
     * ROW 下 percentHeight 字段被忽略，child 走 fill/STRETCH，高=父内高-marginV=100-0=100。
     * 断言 child.height=100（不是 50，percentHeight 在 ROW 下被忽略）。</p>
     */
    @Test
    public void rowPercentHeightNotEffective() {
        SceneNode root = SceneNode.row();
        root.setFillParentHeight(true);
        root.setCrossAxisAlign(CrossAxisAlign.STRETCH);

        SceneNode child = new SceneNode();
        child.setPercentHeight(50);
        // 无 preferredHeight，确保不被 STRETCH 豁免

        root.appendChild(child);

        engine.layout(root, new Constraints(200, 100));

        LayoutBox childBox = (LayoutBox) child.getCachedLayout();
        Assert.assertNotNull("child 应有 cachedLayout", childBox);
        Assert.assertEquals("ROW 下 percentHeight 不生效，child 走 STRETCH 拉满父内高=100",
                100, childBox.getHeight());
    }

    /**
     * P12：fillParentHeight + percentHeight → fill 隐式 grow 优先（P3 只覆盖显式 flexGrow）。
     *
     * <p>root(COLUMN, fill, 高=200)→a(fillParentHeight=true, percentHeight=50)。
     * a 无显式 flexGrow，但 fillParentHeight → effectiveGrow=1（隐式 grow）。
     * grow 优先分支生效，percent 被忽略。a 是唯一 grow 子，吃满 freeH=200。
     * 断言 a.height=200（grow 分配，不是 percentHeight=200*50/100=100）。
     * 与 P3（显式 flexGrow 优先）对称，覆盖隐式 fill 优先路径。</p>
     */
    @Test
    public void percentPlusFillParentHeightImplicitGrow() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode a = new SceneNode();
        a.setFillParentHeight(true);
        a.setPercentHeight(50);

        root.appendChild(a);

        engine.layout(root, new Constraints(200, 200));

        LayoutBox aBox = (LayoutBox) a.getCachedLayout();
        Assert.assertEquals("fillParentHeight 隐式 effectiveGrow=1 优先于 percentHeight，"
                + "a 吃满 freeH=200（不是 percentHeight=100）",
                200, aBox.getHeight());
    }

    // ============================================================
    // L1 回归：嵌套 grow 子容器场景的 definite 高下传
    // （Oracle 裁决修复后补的 6 个回归测试）
    // ============================================================

    /**
     * L1-1 主修复：嵌套 grow 子容器内 grow 子吃满父高，不回退 shrink。
     *
     * <p>root(COLUMN,fill,高200) → X(grow=1,COLUMN容器,非fill,无preferredHeight)
     * → 孙子(grow=1,文本节点)。L1 修复前 X 拿到确定高后不下传给自己的 grow 子，
     * 孙子回退 shrink=文本自然高16；修复后 X 的确定内高下传，孙子吃满 200。
     * 这是 L1 的直接反证测试。</p>
     */
    @Test
    public void nestedGrowChildFillsInnerHeightFromGrowParent() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode x = SceneNode.column();
        x.setFlexGrow(1);
        // X 非 fill、无 preferredHeight，靠 grow 拿到确定高

        SceneNode grandchild = new SceneNode();
        grandchild.setFlexGrow(1);
        grandchild.setText("X"); // 自然高 16

        x.appendChild(grandchild);
        root.appendChild(x);

        engine.layout(root, new Constraints(200, 200));

        LayoutBox grandchildBox = (LayoutBox) grandchild.getCachedLayout();
        Assert.assertNotNull("孙子应有 cachedLayout", grandchildBox);
        Assert.assertEquals("L1 修复：嵌套 grow 子吃满 X 内高 200（不回退 shrink=16）",
                200, grandchildBox.getHeight());
    }

    /**
     * L1-2 percent 变体：嵌套 percent 子容器内 grow 子吃满。
     *
     * <p>root(COLUMN,fill,高200) → X(percentHeight=100,COLUMN容器,非fill)
     * → 孙子(grow=1,文本节点)。percentHeight=100 意味着 X 吃满父高 200，
     * X 的确定内高下传给孙子，孙子吃满 200。</p>
     */
    @Test
    public void nestedGrowChildFillsInnerHeightFromPercentParent() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode x = SceneNode.column();
        x.setPercentHeight(100); // X 非 fill，靠 percent 吃满父高 200

        SceneNode grandchild = new SceneNode();
        grandchild.setFlexGrow(1);
        grandchild.setText("X"); // 自然高 16

        x.appendChild(grandchild);
        root.appendChild(x);

        engine.layout(root, new Constraints(200, 200));

        LayoutBox grandchildBox = (LayoutBox) grandchild.getCachedLayout();
        Assert.assertNotNull("孙子应有 cachedLayout", grandchildBox);
        Assert.assertEquals("percent 变体：嵌套 grow 子吃满 X 内高 200",
                200, grandchildBox.getHeight());
    }

    /**
     * L1-3 两层嵌套 grow：definite 沿树下传不断链。
     *
     * <p>root(COLUMN,fill,高200) → X(grow=1,COLUMN) → Y(grow=1,COLUMN)
     * → leaf(grow=1,文本节点)。逐层吃满，确认 definite 沿树三层下传不断链，
     * leaf 高度=200。</p>
     */
    @Test
    public void nestedTwoLevelGrowDefinitePropagatesDown() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode x = SceneNode.column();
        x.setFlexGrow(1);

        SceneNode y = SceneNode.column();
        y.setFlexGrow(1);

        SceneNode leaf = new SceneNode();
        leaf.setFlexGrow(1);
        leaf.setText("X"); // 自然高 16

        y.appendChild(leaf);
        x.appendChild(y);
        root.appendChild(x);

        engine.layout(root, new Constraints(200, 200));

        LayoutBox leafBox = (LayoutBox) leaf.getCachedLayout();
        Assert.assertNotNull("leaf 应有 cachedLayout", leafBox);
        Assert.assertEquals("两层嵌套 grow：definite 沿树下传不断链，leaf 吃满 200",
                200, leafBox.getHeight());
    }

    /**
     * L1-4 scrollable 排除反证：scrollable+grow 容器不按 grow 下传先验。
     *
     * <p>root(COLUMN,fill,高200) → X(scrollable=true,grow=1) → 内容子(高300,不grow)。
     * X 自身是 viewport 语义（grow 吃满父高 200 作视口），内容子按内容高 300 可超视口。
     * 验证 {@code !isScrollable()} 排除生效：scrollable 容器即使有 flexGrow，
     * 其内高也不作 grow 分配先验下传给 grow 子。</p>
     */
    @Test
    public void scrollableGrowContainerDoesNotPassInnerAsGrowPrior() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode x = new SceneNode();
        x.setScrollable(true);
        x.setFlexGrow(1);

        // 用 grow 子而非固定子：若 !isScrollable() 排除失效，
        // priorKnownInnerHeight(X) 会返回 200，grow 子会被分配 200 而非回退 shrink
        SceneNode content = new SceneNode();
        content.setFlexGrow(1);
        content.setText("X");

        x.appendChild(content);
        root.appendChild(x);

        engine.layout(root, new Constraints(200, 200));

        LayoutBox xBox = (LayoutBox) x.getCachedLayout();
        LayoutBox contentBox = (LayoutBox) content.getCachedLayout();
        Assert.assertNotNull("X 应有 cachedLayout", xBox);
        Assert.assertNotNull("grow 子应有 cachedLayout", contentBox);
        // X 作为 scrollable viewport，自身高由 viewportHeight 决定：
        // 非 fill 无 preferredHeight → min(内容高 16, 约束高 200) = 16
        Assert.assertEquals("X scrollable viewport 高度=16（viewportHeight min(内容高,约束高)）",
                16, xBox.getHeight());
        // grow 子回退 shrink（文本自然高 16），而非被分配 200——
        // 证明 !isScrollable() 排除生效：scrollable 内高不作 grow 分配先验下传。
        // 若去掉排除，priorKnownInnerHeight(X) 返回 200，grow 子会被分配 200
        Assert.assertEquals("grow 子回退 shrink=16（scrollable 内高不作 grow 先验下传）",
                16, contentBox.getHeight());
    }

    /**
     * L1-5 I7 干净帧：嵌套 grow 树二次 layout 同约束全树 skip。
     *
     * <p>root(COLUMN,fill,高200) → X(grow=1,COLUMN) → 孙子(grow=1,文本节点)。
     * 同约束 layout 两次，断言第二次全树 skip（relayoutCount=0，孙子 LayoutBox 引用复用），
     * 守 I7 干净帧短路。参考现有 I7 干净帧测试写法（assertSame + getRelayoutCount）。</p>
     */
    @Test
    public void nestedGrowTreeCleanFrameFullSkipOnSameConstraints() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode x = SceneNode.column();
        x.setFlexGrow(1);

        SceneNode grandchild = new SceneNode();
        grandchild.setFlexGrow(1);
        grandchild.setText("X");

        x.appendChild(grandchild);
        root.appendChild(x);

        Constraints c = new Constraints(200, 200);
        // 第一次 layout：建立 cache，孙子吃满 200
        engine.layout(root, c);
        LayoutBox grandchildBox1 = (LayoutBox) grandchild.getCachedLayout();
        Assert.assertNotNull("首次孙子应有 cachedLayout", grandchildBox1);
        Assert.assertEquals("首次孙子吃满 200", 200, grandchildBox1.getHeight());

        // 第二次 layout：同约束，全树 skip
        LayoutResult result = engine.layout(root, c);
        LayoutBox grandchildBox2 = (LayoutBox) grandchild.getCachedLayout();

        Assert.assertEquals("同约束第二次 relayoutCount=0（全树 skip）",
                0, result.getRelayoutCount());
        Assert.assertSame("孙子 LayoutBox 引用复用（I7 干净帧）",
                grandchildBox1, grandchildBox2);
    }

    /**
     * L1-6 grow+maxHeight 撞顶：嵌套 grow 子容器内 grow 子撞 maxHeight。
     *
     * <p>root(COLUMN,fill,高200) → X(grow=1,COLUMN) → 孙子(grow=1,maxHeight=50)。
     * X 拿到确定 innerH=200 后下传，孙子 grow 分配 200 但撞 maxHeight=50 冻结。
     * 确认 X 拿到确定 innerH 后 freeze do-while 正常撞顶。</p>
     */
    @Test
    public void nestedGrowChildClampedByMaxHeightInGrowParent() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode x = SceneNode.column();
        x.setFlexGrow(1);

        SceneNode grandchild = new SceneNode();
        grandchild.setFlexGrow(1);
        grandchild.setMaxHeight(50);

        x.appendChild(grandchild);
        root.appendChild(x);

        engine.layout(root, new Constraints(200, 200));

        LayoutBox grandchildBox = (LayoutBox) grandchild.getCachedLayout();
        Assert.assertNotNull("孙子应有 cachedLayout", grandchildBox);
        Assert.assertEquals("嵌套 grow 子撞 maxHeight=50 冻结（X 确定内高下传后 freeze 撞顶）",
                50, grandchildBox.getHeight());
    }

    /**
     * L1-T1 grow 容器首次进入 padV 扣减分支，孙子高度应为父约束高减父 padding。
     *
     * <p>root(COLUMN,fill,高200) → X(grow=1,COLUMN容器,padding上下各10)
     * → 孙子(grow=1,文本节点)。X 的约束高 200，扣 padding 上下各 10，
     * 内高 180 下传给孙子，孙子吃满 180。锁 {@code priorKnownInnerHeight} 的
     * {@code max(约束高, preferredHeight) - padV} padV 扣减路径。
     * 这是修复新激活的代码路径——旧代码 grow 容器根本不进此分支，padV 扣减无覆盖。</p>
     */
    @Test
    public void nestedGrowChildRespectsParentPadding() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode x = SceneNode.column();
        x.setFlexGrow(1);
        // padding 上下各 10，左右 0
        x.setPadding(10, 0, 10, 0);

        SceneNode grandchild = new SceneNode();
        grandchild.setFlexGrow(1);
        grandchild.setText("X"); // 自然高 16

        x.appendChild(grandchild);
        root.appendChild(x);

        engine.layout(root, new Constraints(200, 200));

        LayoutBox grandchildBox = (LayoutBox) grandchild.getCachedLayout();
        Assert.assertNotNull("孙子应有 cachedLayout", grandchildBox);
        Assert.assertEquals("grow 容器扣 padding 后内高 180 下传，孙子吃满 180",
                180, grandchildBox.getHeight());
    }

    /**
     * L1-T2 grow 容器带大 preferredHeight 时，下传内高取 max(约束高, preferredHeight)。
     *
     * <p>root(COLUMN,fill,高100) → X(grow=1,preferredHeight=150,COLUMN容器)
     * → 孙子(grow=1,文本节点)。X 的 {@code max(约束高100, preferredHeight150) = 150}，
     * 下传内高 150 给孙子，孙子吃满 150。锁 {@code priorKnownInnerHeight} 的
     * {@code max(约束高, preferredHeight)} 下界下传。
     * Javadoc 显式声明的语义"否则 fill+大 preferredHeight 时子只 fill 到约束高、父底留白"
     * ——对 grow 父此前零测试。</p>
     */
    @Test
    public void nestedGrowChildUsesPreferredHeightWhenLargerThanConstraint() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode x = SceneNode.column();
        x.setFlexGrow(1);
        x.setPreferredHeight(150); // 大于约束高 100

        SceneNode grandchild = new SceneNode();
        grandchild.setFlexGrow(1);
        grandchild.setText("X"); // 自然高 16

        x.appendChild(grandchild);
        root.appendChild(x);

        engine.layout(root, new Constraints(100, 100));

        LayoutBox grandchildBox = (LayoutBox) grandchild.getCachedLayout();
        Assert.assertNotNull("孙子应有 cachedLayout", grandchildBox);
        Assert.assertEquals("grow 容器带大 preferredHeight，下传内高取 max(100,150)=150",
                150, grandchildBox.getHeight());
    }

    /**
     * L1-T3 ROW 容器作为 COLUMN 父的 grow 子时，修复后能向 ROW 子下传 cross 高。
     *
     * <p>root(COLUMN,fill,高200) → X(grow=1,ROW容器) → 子(fillParentHeight=true,文本节点)。
     * X 作为 COLUMN 父的 grow 子分到 200，X 是 ROW 容器，通过
     * {@code buildChildConstraints} ROW 分支调 {@code priorKnownInnerHeight(X)} 返回 200，
     * 下传给 ROW 子作为交叉轴高，子 fill 交叉轴吃到 200。锁修复后激活的 cross 高下传路径。</p>
     */
    @Test
    public void nestedRowGrowContainerPassesCrossHeightToChildren() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode x = SceneNode.row();
        x.setFlexGrow(1);

        // ROW 子：fill 交叉轴（即 fillParentHeight=true）
        SceneNode child = new SceneNode();
        child.setFillParentHeight(true);
        child.setText("X"); // 自然高 16

        x.appendChild(child);
        root.appendChild(x);

        engine.layout(root, new Constraints(200, 200));

        LayoutBox childBox = (LayoutBox) child.getCachedLayout();
        Assert.assertNotNull("ROW 子应有 cachedLayout", childBox);
        Assert.assertEquals("ROW grow 容器向子下传 cross 高 200，子 fill 交叉轴吃满 200",
                200, childBox.getHeight());
    }

    /**
     * L1-T4 percent 容器下传 definite innerH 后，percent 孙子按 pctH = innerH * pct / 100 分配。
     *
     * <p>root(COLUMN,fill,高200) → X(percentHeight=100,COLUMN容器)
     * → 孙子(percentHeight=50,文本节点)。X 吃满父高 200，下传 innerH=200，
     * 孙子走 {@code computeColumnGrowHeights} percent 固定子路径
     * {@code pctH = innerH * pct / 100 = 200 * 50 / 100 = 100}。
     * 现有 L1-2 是 percent 父 + grow 孙子，percent 父 + percent 孙子此前无覆盖。</p>
     */
    @Test
    public void nestedPercentParentPassesDefiniteToPercentGrandchild() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode x = SceneNode.column();
        x.setPercentHeight(100); // X 吃满父高 200

        SceneNode grandchild = new SceneNode();
        grandchild.setPercentHeight(50); // 50% → 200 * 50 / 100 = 100
        grandchild.setText("X"); // 自然高 16

        x.appendChild(grandchild);
        root.appendChild(x);

        engine.layout(root, new Constraints(200, 200));

        LayoutBox grandchildBox = (LayoutBox) grandchild.getCachedLayout();
        Assert.assertNotNull("孙子应有 cachedLayout", grandchildBox);
        Assert.assertEquals("percent 孙子按 pctH = innerH * pct / 100 = 200 * 50 / 100 = 100",
                100, grandchildBox.getHeight());
    }

    /**
     * L1-T5 scrollable+fill 容器修复后被 !isScrollable() 排除，不下传 definite 内高给 fill 子，fill 子回退 shrink。
     *
     * <p>root(COLUMN,fill,高200) → X(scrollable=true,fillParentHeight=true,COLUMN容器)
     * → 子(fillParentHeight=true,文本节点)。X 自身 viewport 高 = 200
     * （scrollable+fill → viewportHeight 返回 availableHeight 200）。
     * 但 X 被 {@code !isScrollable()} 排除，priorKnownInnerHeight 不下传 definite，
     * fill 子回退 shrink = 16（文本自然高），不是 fill 到 200。
     * L1-4 用 grow 变体验证了排除逻辑，本测试补 fill 变体的直接回归锁。</p>
     */
    @Test
    public void scrollableFillContainerDoesNotPassInnerAsFillPrior() {
        SceneNode root = SceneNode.column();
        root.setFillParentHeight(true);

        SceneNode x = SceneNode.column();
        x.setScrollable(true);
        x.setFillParentHeight(true);

        SceneNode child = new SceneNode();
        child.setFillParentHeight(true);
        child.setText("X"); // 自然高 16

        x.appendChild(child);
        root.appendChild(x);

        engine.layout(root, new Constraints(200, 200));

        LayoutBox xBox = (LayoutBox) x.getCachedLayout();
        LayoutBox childBox = (LayoutBox) child.getCachedLayout();
        Assert.assertNotNull("X 应有 cachedLayout", xBox);
        Assert.assertNotNull("fill 子应有 cachedLayout", childBox);
        // X scrollable+fill → viewportHeight 返回 availableHeight 200
        Assert.assertEquals("X scrollable+fill viewport 高度=200（viewportHeight 返回 availableHeight）",
                200, xBox.getHeight());
        // fill 子回退 shrink=16（文本自然高），不是 fill 到 200——
        // 证明 !isScrollable() 排除对 fill 容器也生效：scrollable+fill 内高不作 fill 先验下传。
        Assert.assertEquals("fill 子回退 shrink=16（scrollable+fill 内高不作 fill 先验下传）",
                16, childBox.getHeight());
    }
}
