package club.heiqi.uilib.ui.scene.layout;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 坐标系契约不变量表测试 —— 锚定 {@code NORTH_STAR.md} 的 I12（两层坐标：raw 局部 vs absolute
 * 绝对）与 §4.5（坐标系契约）的<b>布局侧数学不变量</b>。
 *
 * <p>本类只验证 {@link SceneGeometry} 的只读几何工具在 layout 产出 LayoutBox 后返回的绝对坐标
 * 是否严格满足父链累加 + scrollOffsetY 注入 + rootAbs 偏移三条数学规则，不触发 paint/composite，
 * 属纯 JVM 数学断言层。</p>
 *
 * <h3>三条核心规则（来自 SceneGeometry 源码）</h3>
 * <ul>
 *   <li><b>父链累加</b>：{@code absoluteBox(node, rx, ry)} 沿 parent 链累加每层 LayoutBox.x/y，
 *       初值为 (rx, ry)。</li>
 *   <li><b>scrollOffsetY 注入（减）</b>：每攀升到一层 parent 时调
 *       {@link SceneGeometry#childYBase}；若 parent 是 scrollable，则
 *       {@code y = parentAbsY - parent.getScrollOffsetY()}（注意是<b>减</b>）。
 *       语义：滚动向上，子内容绝对坐标随滚动量减小。</li>
 *   <li><b>rootAbs 偏移</b>：{@code rootAbsX/rootAbsY} 是初值偏移，传 0 即 host 局部坐标；
 *       GUI 居中场景 rootAbs≠0 时绝对坐标整体偏移——这正是 I12「raw 与 absoluteBox(node,0,0)
 *       禁止混比」的根因。</li>
 * </ul>
 *
 * <p>装配复用 scene 测试标准模式：{@link FixedTextMeasurer}(8,16) 确定性度量 +
 * {@link SceneLayoutEngine} + {@link Constraints}。本类置于 layout 包以访问
 * package-private 的 {@link LayoutBox} / {@link Constraints}。</p>
 */
public class CoordinateInvariantTest {

    /** 确定性文本度量替身：每字符宽 8px，行高 16px。 */
    private final FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
    /** 被测布局引擎。 */
    private final SceneLayoutEngine engine = new SceneLayoutEngine(measurer);

    // ============================================================
    // 场景 1：root 在原点（§4.5 基础契约）
    // ============================================================

    /**
     * 锚定 §4.5 基础坐标契约：fillParent 的 root 在 (0,0) 约束下 LayoutBox 恒 (0,0,W,H)，
     * absoluteBox(root, 0, 0) 应原样返回 (0,0,W,H)。
     *
     * <p>验证 absoluteBox 的初值 (0,0) + root 自身 LayoutBox (0,0,200,200) 累加后仍为原点，
     * 且 width/height 取自 root 自身 LayoutBox——这是所有后续嵌套累加的基准锚点。</p>
     */
    @Test
    public void rootAtOrigin_returnsExactLayoutBox() {
        SceneNode root = SceneNode.column()
                .setFillParentHeight(true)
                .setFillParentWidth(true);

        engine.layout(root, new Constraints(200, 200));

        // root LayoutBox 恒 (0,0,200,200)
        LayoutAssertions.assertLocalBox(root, 0, 0, 200, 200);

        // absoluteBox(root, 0, 0) 应 = (0,0,200,200)，全字段核对
        AnchorRect abs = SceneGeometry.absoluteBox(root, 0, 0);
        LayoutAssertions.assertAbsoluteBox(root, 0, 0, 0, 0);
        Assert.assertEquals("root absolute width 应为 200", 200, abs.getWidth());
        Assert.assertEquals("root absolute height 应为 200", 200, abs.getHeight());
    }

    // ============================================================
    // 场景 2：多层嵌套坐标累加（I12 核心）
    // ============================================================

    /**
     * 锚定 I12 两层坐标核心：absoluteBox 沿 parent 链累加 LayoutBox.x/y。
     *
     * <p>树形：root(pad10) → child COLUMN(pad5, fillParentWidth) → grandchild(50x30)。
     * layout 后 grandchild 局部 (5,5)、child 局部 (10,10)、root 局部 (0,0)，
     * 故 absoluteBox(grandchild, 0, 0) 应 = 0+10+5 = 15 (x 与 y 同)。</p>
     *
     * <p>注：child 只 fillParentWidth、未 fillParentHeight，故其高度走 shrink-to-fit =
     * grandchild 高 30 + child padV(5+5) = 40（非填满 root innerH 180）。但 absoluteBox
     * 只累加 x/y，child.height 不参与绝对坐标计算，故绝对坐标 (15,15) 不受影响。</p>
     *
     * <p>这条断言把 raw 局部坐标 (5,5) 与 absoluteBox 绝对坐标 (15,15) 显式区分开——
     * 正是 I12「raw 与 absoluteBox 禁止混比」的反例锚定。</p>
     */
    @Test
    public void nestedChain_accumulatesLayoutBoxOffsets() {
        SceneNode root = SceneNode.column()
                .setFillParentHeight(true)
                .setFillParentWidth(true)
                .setPadding(10, 10, 10, 10);
        SceneNode child = SceneNode.column()
                .setPadding(5, 5, 5, 5)
                .setFillParentWidth(true);
        SceneNode grandchild = new SceneNode()
                .setPreferredWidth(50)
                .setPreferredHeight(30);
        root.appendChild(child);
        child.appendChild(grandchild);

        engine.layout(root, new Constraints(200, 200));

        // 三层局部坐标核对：root(0,0,200,200) / child(10,10,180,40 shrink-to-fit) / grandchild(5,5,50,30)
        LayoutAssertions.assertLocalBox(root, 0, 0, 200, 200);
        LayoutAssertions.assertLocalBox(child, 10, 10, 180, 40);
        LayoutAssertions.assertLocalBox(grandchild, 5, 5, 50, 30);

        // 绝对坐标 = 0(root) + 10(child) + 5(grandchild) = 15
        LayoutAssertions.assertAbsoluteBox(grandchild, 0, 0, 15, 15);
    }

    // ============================================================
    // 场景 3：rootAbs 偏移（I12 GUI 居中场景）
    // ============================================================

    /**
     * 锚定 I12 GUI 居中场景：rootAbs≠0 时绝对坐标整体偏移。
     *
     * <p>同场景 2 的树，但 absoluteBox(grandchild, 100, 50) 应 = 100+10+5=115 (x)、
     * 50+10+5=65 (y)。这锚定 I12「raw 与 absoluteBox(node,0,0) 禁止混比」——
     * rootAbs 是初值偏移，不同 rootAbs 下同一节点的绝对坐标不同，但 raw 局部坐标不变。</p>
     */
    @Test
    public void rootAbsOffset_shiftsAbsoluteCoordinates() {
        SceneNode root = SceneNode.column()
                .setFillParentHeight(true)
                .setFillParentWidth(true)
                .setPadding(10, 10, 10, 10);
        SceneNode child = SceneNode.column()
                .setPadding(5, 5, 5, 5)
                .setFillParentWidth(true);
        SceneNode grandchild = new SceneNode()
                .setPreferredWidth(50)
                .setPreferredHeight(30);
        root.appendChild(child);
        child.appendChild(grandchild);

        engine.layout(root, new Constraints(200, 200));

        // rootAbs(100,50) + 父链(10+5) = (115, 65)
        LayoutAssertions.assertAbsoluteBox(grandchild, 100, 50, 115, 65);
    }

    // ============================================================
    // 场景 4：scrollOffsetY 注入（§4.5 scrollable 几何）
    // ============================================================

    /**
     * 锚定 §4.5 scrollable 几何：scrollable 祖先向子内容注入 -scrollOffsetY。
     *
     * <p>树形：root(fillParent 200x200) → container(COLUMN, scrollable, preferredHeight=100,
     * fillParentWidth) → content(200x300 装饰叶，内容超出视口)。</p>
     *
     * <p>读 {@link SceneGeometry#childYBase} 源码确认注入方向为<b>减</b>
     * ({@code parentAbsY - scrollOffsetY})。故：</p>
     * <ul>
     *   <li>scrollOffsetY=0：absoluteBox(content, 0, 0).y = 0（内容顶对齐视口顶）</li>
     *   <li>scrollOffsetY=50：absoluteBox(content, 0, 0).y = 0 - 50 = -50（滚动向上 50px，
     *       内容绝对坐标减小 50）</li>
     * </ul>
     * <p>两值之差恰好 = scrollOffsetY 之差（50），且方向为减——这是滚动几何的核心不变量。</p>
     *
     * <p>注：{@link SceneNode#setScrollOffsetY(int)} 只标 markGeometryDirty，不清 cachedLayout，
     * 故 layout 一次后切换 scrollOffsetY 直接调 absoluteBox 即可读到正确值，无需重新 layout。</p>
     */
    @Test
    public void scrollOffsetY_injectsAsSubtractionIntoChildY() {
        SceneNode root = SceneNode.column()
                .setFillParentHeight(true)
                .setFillParentWidth(true);
        SceneNode container = SceneNode.column()
                .setScrollable(true)
                .setPreferredHeight(100)
                .setFillParentWidth(true);
        SceneNode content = new SceneNode()
                .setPreferredWidth(200)
                .setPreferredHeight(300);
        root.appendChild(container);
        container.appendChild(content);

        engine.layout(root, new Constraints(200, 200));

        // 几何前置：container 钉死视口高 100，content 高 300 超出视口
        LayoutAssertions.assertLocalBox(container, 0, 0, 200, 100);
        LayoutAssertions.assertLocalBox(content, 0, 0, 200, 300);

        // scrollOffsetY=0：content 绝对 y = 0（视口顶对齐）
        int yAtZero = SceneGeometry.absoluteBox(content, 0, 0).getY();
        Assert.assertEquals("scrollOffsetY=0 时 content absolute y 应为 0", 0, yAtZero);

        // 切到 scrollOffsetY=50：只标 geometry，cachedLayout 仍在，直接调 absoluteBox
        container.setScrollOffsetY(50);
        int yAtFifty = SceneGeometry.absoluteBox(content, 0, 0).getY();
        Assert.assertEquals("scrollOffsetY=50 时 content absolute y 应为 -50（注入方向为减）",
                -50, yAtFifty);

        // 核心不变量：两值之差恰好 = scrollOffsetY 之差，且为减
        int yDelta = yAtZero - yAtFifty;
        Assert.assertEquals("scrollOffsetY 增量应完全映射为 absolute y 的减少量",
                50, yDelta);
    }

    // ============================================================
    // 场景 5：maxScrollY（最大滚动量）
    // ============================================================

    /**
     * 锚定 {@link SceneGeometry#maxScrollY} 闭式公式：
     * {@code maxChildBottom + paddingBottom - boxHeight}，其中
     * {@code maxChildBottom = max(child.y + child.height + child.marginBottom)}。
     *
     * <p>同场景 4 的 scrollable 容器：content 高 300、marginBottom=0、container 视口高 100、
     * paddingBottom=0，故 maxScrollY = (0+300+0) + 0 - 100 = 200。</p>
     */
    @Test
    public void maxScrollY_equalsContentHeightMinusViewportHeight() {
        SceneNode root = SceneNode.column()
                .setFillParentHeight(true)
                .setFillParentWidth(true);
        SceneNode container = SceneNode.column()
                .setScrollable(true)
                .setPreferredHeight(100)
                .setFillParentWidth(true);
        SceneNode content = new SceneNode()
                .setPreferredWidth(200)
                .setPreferredHeight(300);
        root.appendChild(container);
        container.appendChild(content);

        engine.layout(root, new Constraints(200, 200));

        // maxScrollY = 内容高 300 - 视口高 100 = 200
        int maxScroll = SceneGeometry.maxScrollY(container);
        Assert.assertEquals("maxScrollY 应为 内容高 300 - 视口高 100 = 200",
                200, maxScroll);
    }

    // ============================================================
    // 场景 6：兄弟节点坐标独立性（ROW 主轴排列）
    // ============================================================

    /**
     * 锚定 ROW 主轴累加：兄弟节点在 ROW 容器内沿 X 轴依次排列，
     * absoluteBox 应反映主轴累加结果。
     *
     * <p>树形：root(ROW, fillParent 100x100) → a(30x50) + b(40x50)。
     * layout 后 a.x=0、b.x=30（a 宽度累加），absoluteBox(a,0,0).x=0、
     * absoluteBox(b,0,0).x=30。这验证绝对坐标正确反映主轴排列，兄弟互不干扰。</p>
     */
    @Test
    public void rowSiblings_layOutAlongMainAxisIndependently() {
        SceneNode root = SceneNode.row()
                .setFillParentHeight(true)
                .setFillParentWidth(true);
        SceneNode a = new SceneNode().setPreferredWidth(30).setPreferredHeight(50);
        SceneNode b = new SceneNode().setPreferredWidth(40).setPreferredHeight(50);
        root.appendChild(a);
        root.appendChild(b);

        engine.layout(root, new Constraints(100, 100));

        // ROW 主轴累加：a.x=0, b.x=a.x+a.width=30
        LayoutAssertions.assertLocalBox(a, 0, 0, 30, 50);
        LayoutAssertions.assertLocalBox(b, 30, 0, 40, 50);

        // 绝对坐标反映主轴排列
        Assert.assertEquals("a absolute x 应为 0（主轴起点）",
                0, SceneGeometry.absoluteBox(a, 0, 0).getX());
        Assert.assertEquals("b absolute x 应为 30（a 宽度累加）",
                30, SceneGeometry.absoluteBox(b, 0, 0).getX());
    }

    // ============================================================
    // 场景 7：visibleBoxWithinScrollableAncestors 裁剪
    // ============================================================

    /**
     * 锚定 {@link SceneGeometry#visibleBoxWithinScrollableAncestors}：节点绝对盒与所有
     * scrollable 祖先视口框取交集，超出视口的部分被裁掉。
     *
     * <p>同场景 4 的树，scrollOffsetY=50：</p>
     * <ul>
     *   <li>content 绝对盒 = (0, -50, 200, 300)（顶部 50px 滚出视口上方）</li>
     *   <li>container 视口框 = absoluteBox(container) = (0, 0, 200, 100)
     *       （视口框用祖先自身绝对 LayoutBox，不含其 scrollOffsetY）</li>
     *   <li>交集 = (0, max(-50,0)=0, 200, min(250,100)=100) = (0, 0, 200, 100)</li>
     * </ul>
     * <p>可见盒恰好等于视口框——内容完全覆盖视口，故裁剪后就是视口本身，且严格落在视口范围内。</p>
     */
    @Test
    public void visibleBox_clippedToScrollableAncestorViewport() {
        SceneNode root = SceneNode.column()
                .setFillParentHeight(true)
                .setFillParentWidth(true);
        SceneNode container = SceneNode.column()
                .setScrollable(true)
                .setPreferredHeight(100)
                .setFillParentWidth(true);
        SceneNode content = new SceneNode()
                .setPreferredWidth(200)
                .setPreferredHeight(300);
        root.appendChild(container);
        container.appendChild(content);

        engine.layout(root, new Constraints(200, 200));
        container.setScrollOffsetY(50);

        AnchorRect visible = SceneGeometry.visibleBoxWithinScrollableAncestors(content, 0, 0);

        // 交集结果应严格落在视口框 (0,0,200,100) 内
        Assert.assertEquals("visible x 应为视口左 0", 0, visible.getX());
        Assert.assertEquals("visible y 应被裁到视口顶 0", 0, visible.getY());
        Assert.assertEquals("visible width 应为视口宽 200", 200, visible.getWidth());
        Assert.assertEquals("visible height 应被裁到视口高 100", 100, visible.getHeight());

        // 通用不变量：可见盒不得超出视口框任何一边
        AnchorRect viewport = SceneGeometry.absoluteBox(container, 0, 0);
        Assert.assertTrue("visible 左边不得小于视口左边",
                visible.getX() >= viewport.getX());
        Assert.assertTrue("visible 顶边不得小于视口顶边",
                visible.getY() >= viewport.getY());
        Assert.assertTrue("visible 右边不得大于视口右边",
                visible.getX() + visible.getWidth() <= viewport.getX() + viewport.getWidth());
        Assert.assertTrue("visible 底边不得大于视口底边",
                visible.getY() + visible.getHeight() <= viewport.getY() + viewport.getHeight());
    }
}
