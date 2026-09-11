package club.heiqi.uilib.ui.scene.layout;

import java.util.HashSet;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.input.SceneHitTester;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

/**
 * 内容折叠声明（{@link SceneNode#setCollapsed(boolean)}）语义与守卫（U-P5-17 根除）。
 *
 * <h3>被测主张</h3>
 * <ol>
 *   <li><b>先验闸门根除</b>：有子容器声明折叠后先验尺寸恒可知 ⇒ COLUMN grow 分配不再整条放弃，
 *       视口高度有界、{@code maxScrollY > 0}（同构未声明场景保留旧陷阱行为，两用例互为变异检查）；</li>
 *   <li><b>零内容叶口径</b>：折叠节点自身盒按「无子无文本」计算（padding 计入、preferred 作下限）；</li>
 *   <li><b>子树退出四面</b>：不参与布局（无盒）、绘制（零命令）、命中（不可达）、焦点环；</li>
 *   <li><b>可逆</b>：解折叠后子树整体重算并恢复盒子（折叠期保持脏态，无第二通道）。</li>
 * </ol>
 *
 * <p>用 {@link FixedTextMeasurer}(8,16) 桩保证纯 JUnit 可断言；布局经
 * {@link SceneLayoutEngine#layout} 直驱（与 {@code GrowAllocationTableTest} 同层 L2）。</p>
 */
public class CollapsedContentTest {

    private SceneTextMeasurer measurer;
    private SceneLayoutEngine layoutEngine;

    @Before
    public void setUp() {
        measurer = new FixedTextMeasurer(8, 16);
        layoutEngine = new SceneLayoutEngine(measurer);
    }

    /** 折叠节点的子树：label（文本叶）+ button（有 preferredHeight 的叶），与 picker 撤销条同构。 */
    private static SceneNode collapsedRowFixture() {
        SceneNode row = SceneNode.row();
        SceneNode label = new SceneNode();
        label.setText("撤销删除");
        SceneNode button = new SceneNode();
        button.setPreferredHeight(20);
        row.appendChild(label);
        row.appendChild(button);
        return row;
    }

    /**
     * 同构装配：COLUMN 根（preferredHeight=200，自身高度可先验）
     * <pre>
     *   ├ row（折叠声明的常驻容器；未声明分支即 U-P5-17 陷阱现场）
     *   └ viewport（scrollable + fillParentHeight + flexGrow=1）→ content → cell(preferredHeight=300)
     * </pre>
     * 未声明时：row 先验高不可知 ⇒ grow 分配放弃 ⇒ viewport 回退 shrink（高=内容 300、maxScrollY=0）。
     * 已声明时：row 先验高 = 0（可知）⇒ viewport 高 = 分配值 200、maxScrollY = 100。
     */
    private static SceneNode guardFixture(SceneNode row, SceneNode[] outViewport) {
        SceneNode root = SceneNode.column();
        root.setPreferredHeight(200);
        root.appendChild(row);

        SceneNode viewport = SceneNode.column();
        viewport.setScrollable(true);
        viewport.setFillParentHeight(true);
        viewport.setFlexGrow(1);
        viewport.setClipChildren(true);
        SceneNode content = SceneNode.column();
        SceneNode cell = new SceneNode();
        cell.setPreferredHeight(300);
        content.appendChild(cell);
        viewport.appendChild(content);
        root.appendChild(viewport);
        outViewport[0] = viewport;
        return root;
    }

    /** 两趟布局并断言收敛（第二趟零重算），返回第二趟结果。 */
    private LayoutResult layoutTwice(SceneNode root, Constraints constraints) {
        layoutEngine.layout(root, constraints);
        LayoutResult second = layoutEngine.layout(root, constraints);
        Assert.assertEquals("第二趟应零重算（收敛）", 0, second.getRelayoutCount());
        return second;
    }

    private static void assertBox(SceneNode node, int x, int y, int w, int h, String what) {
        Object cached = node.getCachedLayout();
        Assert.assertTrue(what + " 应有布局盒", cached instanceof LayoutBox);
        LayoutBox box = (LayoutBox) cached;
        Assert.assertEquals(what + ".x", x, box.getX());
        Assert.assertEquals(what + ".y", y, box.getY());
        Assert.assertEquals(what + ".w", w, box.getWidth());
        Assert.assertEquals(what + ".h", h, box.getHeight());
    }

    // ==================== 1. 先验闸门根除（守卫 + 变异检查） ====================

    /**
     * 守卫：固定兄弟声明折叠 ⇒ grow 分配<b>不放弃</b>，视口高度有界、带内滚动可用。
     *
     * <p>变异检查：删掉 {@code row.setCollapsed(true)} 一行（等价于「去掉声明」），
     * 本用例当场红在 viewport 高 300≠200 / maxScrollY 0≠100 —— 见
     * {@link #undeclaredSiblingKeepsLegacyGateBehaviour()}（同构未声明场景的旧行为锚）。</p>
     */
    @Test
    public void collapsedFixedSiblingKeepsGrowAllocation() {
        SceneNode row = collapsedRowFixture();
        row.setCollapsed(true);
        SceneNode[] vp = new SceneNode[1];
        SceneNode root = guardFixture(row, vp);

        layoutTwice(root, new Constraints(400, 200));

        assertBox(row, 0, 0, 400, 0, "折叠行（零内容叶）");
        assertBox(vp[0], 0, 0, 400, 200, "视口（grow 分配生效）");
        Assert.assertEquals("折叠声明下 maxScrollY 应 > 0（带内滚动可用）",
                100, SceneGeometry.maxScrollY(vp[0]));
        Assert.assertNull("折叠子树不参与布局（子树无盒）",
                row.__getChildren().get(0).getCachedLayout());
        Assert.assertNull("折叠子树不参与布局（子树无盒）",
                row.__getChildren().get(1).getCachedLayout());
    }

    /**
     * 锚定既有行为（纯加法证明 + 变异检查的对照面）：<b>未声明</b>折叠的有子容器保持 U-P5-17 旧行为
     * ——先验高不可知 ⇒ grow 分配整条放弃 ⇒ 视口被内容撑大、{@code maxScrollY == 0}。
     *
     * <p>本用例是「声明是唯一变化点」的证据：两棵同构树只差一行 {@code setCollapsed(true)}，
     * 结果差异全部来自该声明。</p>
     */
    @Test
    public void undeclaredSiblingKeepsLegacyGateBehaviour() {
        SceneNode row = collapsedRowFixture();   // 不声明折叠
        SceneNode[] vp = new SceneNode[1];
        SceneNode root = guardFixture(row, vp);

        layoutTwice(root, new Constraints(400, 200));

        // 未声明：行按内容高 20 占位（label 16 / button 20 的交叉轴最大者），视口回退 shrink
        assertBox(row, 0, 0, 400, 20, "未声明：行按内容撑高");
        assertBox(vp[0], 0, 20, 400, 300, "未声明：视口回退 shrink（内容高）");
        Assert.assertEquals("未声明：视口被内容撑大 ⇒ maxScrollY == 0",
                0, SceneGeometry.maxScrollY(vp[0]));
    }

    /** 先验口径双轴：折叠节点先验尺寸恒可知；未折叠的有子容器仍 UNCONSTRAINED（既有行为不变）。 */
    @Test
    public void priorSizeIsKnownOnlyForCollapsedNodes() {
        ConstraintResolver resolver = new ConstraintResolver(
                new SizingCalculator(measurer, new HashSet<SceneNode>()));

        SceneNode container = SceneNode.column();
        container.appendChild(new SceneNode());
        Assert.assertEquals("未折叠的有子容器先验高仍不可知（既有行为）",
                Constraints.UNCONSTRAINED, resolver.priorKnownChildHeight(container));
        Assert.assertEquals("未折叠的有子容器先验宽仍不可知（既有行为）",
                Constraints.UNCONSTRAINED, resolver.priorKnownChildWidth(container));

        container.setCollapsed(true);
        Assert.assertEquals("折叠后先验高恒可知（= padding 0）",
                0, resolver.priorKnownChildHeight(container));
        Assert.assertEquals("折叠后先验宽恒可知（= padding 0）",
                0, resolver.priorKnownChildWidth(container));
    }

    // ==================== 2. 零内容叶口径 ====================

    /** padding 计入、preferred 作下限；自身文本同属内容一并折叠。 */
    @Test
    public void collapsedSizingFollowsZeroContentLeafRule() {
        SceneNode root = SceneNode.column();
        root.setPreferredHeight(100);

        SceneNode padded = SceneNode.column();
        padded.setPadding(4, 6, 8, 6);
        padded.setCollapsed(true);
        SceneNode padChild = new SceneNode();
        padChild.setPreferredHeight(50);
        padded.appendChild(padChild);
        root.appendChild(padded);

        // 折叠的文本叶：自身文本折叠为 0，preferred 仍作下限
        SceneNode textLeaf = new SceneNode();
        textLeaf.setText("一行文字");
        textLeaf.setCollapsed(true);
        root.appendChild(textLeaf);

        SceneNode bounded = SceneNode.column();
        bounded.setPreferredHeight(40);
        bounded.setCollapsed(true);
        bounded.appendChild(new SceneNode());
        root.appendChild(bounded);

        layoutTwice(root, new Constraints(200, 100));

        // padV = 4 + 8 = 12（padding 计入），子树内容 50 不参与
        assertBox(padded, 0, 0, 200, 12, "折叠 + padding（子树不参与）");
        // 文本折叠为 0：无 padding 无 preferred ⇒ 高 0
        assertBox(textLeaf, 0, 12, 200, 0, "折叠文本叶（文本折叠为 0）");
        // preferredHeight 仍作下限
        assertBox(bounded, 0, 12, 200, 40, "折叠 + preferredHeight（下限生效）");
    }

    // ==================== 3. 子树退出绘制 / 命中 ====================

    /** 折叠子树零绘制命令（同构未折叠场景命令数 > 0，两用例互为对照）。 */
    @Test
    public void collapsedSubtreeEmitsNoPaintCommands() {
        SceneNode root = new SceneNode();
        SceneNode row = SceneNode.row();
        SceneNode label = new SceneNode();
        label.setText("撤销删除");
        label.setBackgroundColor(0xFF00FF00);
        row.appendChild(label);
        root.appendChild(row);

        layoutEngine.layout(root, new Constraints(200));
        ScenePaintEngine paintEngine = new ScenePaintEngine(measurer);
        int visibleCommands = paintEngine.paint(root).getPlan().getCommands().size();
        Assert.assertTrue("未折叠：子树应有绘制命令（对照）", visibleCommands > 0);

        row.setCollapsed(true);
        layoutEngine.layout(root, new Constraints(200));
        PaintPlan plan = paintEngine.paint(root).getPlan();
        List<PaintCommand> commands = plan.getCommands();
        Assert.assertEquals("折叠：子树零命令", 0, commands.size());
    }

    /**
     * 折叠子树指针不可达：子树整体退出命中遍历；折叠节点自身仍按既有叶命中规则参与
     * （「内容折叠」而非「节点隐藏」——行有 preferredHeight 时自身仍是合法命中目标）。
     */
    @Test
    public void collapsedSubtreeIsNotHitTestable() {
        SceneNode root = SceneNode.column();
        SceneNode row = SceneNode.row();
        row.setPreferredHeight(20);
        SceneNode button = SceneNode.column();
        button.setPreferredHeight(20);
        row.appendChild(button);
        root.appendChild(row);

        layoutEngine.layout(root, new Constraints(200));
        SceneHitTester hitTester = new SceneHitTester();
        List<SceneNode> visibleChain = hitTester.hitTest(root, 10, 10, 0, 0);
        Assert.assertTrue("未折叠：按钮可达（对照）", visibleChain.contains(button));

        row.setCollapsed(true);
        layoutEngine.layout(root, new Constraints(200));
        List<SceneNode> collapsedChain = hitTester.hitTest(root, 10, 10, 0, 0);
        Assert.assertFalse("折叠：子树不可达", collapsedChain.contains(button));
        Assert.assertTrue("折叠：行自身仍为叶命中候选", collapsedChain.contains(row));
    }

    // ==================== 4. 可逆（解折叠整体重算） ====================

    /** 折叠 → 子树无盒；解折叠 → 子树整体重算并恢复盒子（含文本叶重新测量）。 */
    @Test
    public void expandRestoresCollapsedSubtree() {
        SceneNode root = SceneNode.column();
        root.setPreferredHeight(100);
        SceneNode row = SceneNode.row();
        SceneNode label = new SceneNode();
        label.setText("撤销删除");     // 4 字 × 8px = 32 宽，行高 16
        row.appendChild(label);
        root.appendChild(row);

        layoutEngine.layout(root, new Constraints(200, 100));
        assertBox(label, 0, 0, 32, 16, "折叠前 label");

        row.setCollapsed(true);
        layoutEngine.layout(root, new Constraints(200, 100));
        assertBox(row, 0, 0, 200, 0, "折叠后行（零内容叶）");
        Assert.assertNull("折叠后子树无盒", label.getCachedLayout());

        row.setCollapsed(false);
        LayoutResult result = layoutEngine.layout(root, new Constraints(200, 100));
        Assert.assertTrue("解折叠必须重算行自身", result.getRelayoutedNodes().contains(row));
        Assert.assertTrue("解折叠必须重算子树（折叠期保持脏态）",
                result.getRelayoutedNodes().contains(label));
        assertBox(row, 0, 0, 200, 16, "解折叠后行");
        assertBox(label, 0, 0, 32, 16, "解折叠后 label");

        layoutTwice(root, new Constraints(200, 100));
    }

    /** 幂等：重复声明同值不产生失效（信号重复发同值零成本）。 */
    @Test
    public void repeatedCollapseDeclarationIsIdempotent() {
        SceneNode root = SceneNode.column();
        SceneNode row = SceneNode.row();
        row.appendChild(new SceneNode());
        root.appendChild(row);

        layoutEngine.layout(root, new Constraints(200, 100));
        row.setCollapsed(true);
        layoutEngine.layout(root, new Constraints(200, 100));

        row.setCollapsed(true);
        LayoutResult result = layoutEngine.layout(root, new Constraints(200, 100));
        Assert.assertEquals("同值重复声明零重算", 0, result.getRelayoutCount());
        Assert.assertTrue("同值重复声明零约束重算", result.getConstraintRelayoutedNodes().isEmpty());
    }

    /** 折叠期子树零布局访问：多帧下盒子持续为空、且不出现在重算集合中。 */
    @Test
    public void collapsedSubtreeStaysBoxlessAcrossFrames() {
        SceneNode root = SceneNode.column();
        root.setPreferredHeight(100);
        SceneNode row = SceneNode.row();
        SceneNode label = new SceneNode();
        label.setText("撤销删除");
        row.appendChild(label);
        root.appendChild(row);

        layoutEngine.layout(root, new Constraints(200, 100));
        row.setCollapsed(true);
        for (int frame = 0; frame < 3; frame++) {
            LayoutResult result = layoutEngine.layout(root, new Constraints(200, 100));
            Assert.assertNull("折叠期子树持续无盒（frame=" + frame + "）", label.getCachedLayout());
            Assert.assertFalse("折叠期子树零布局访问（frame=" + frame + "）",
                    result.getRelayoutedNodes().contains(label));
        }
    }
}
