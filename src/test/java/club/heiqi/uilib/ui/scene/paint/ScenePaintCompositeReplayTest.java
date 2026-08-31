package club.heiqi.uilib.ui.scene.paint;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.LayoutResult;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.Transform;

/**
 * Phase 3B 合成级失效通路单元测试（opacity group 栈 + transform offset）。
 *
 * <p>验证 compositeDirty 不再是白标——opacity 经 PUSH_OPACITY/POP_OPACITY 边界命令传导、
 * transform-translate 经命令绝对坐标传导，且<b>纯 composite 变化帧零重排零 fragment 重建</b>
 * （信条五铁律）。</p>
 *
 * <h3>断言策略</h3>
 * <p>group 栈配对正确性全部在 <b>PaintPlan 命令流层面</b>断言（不依赖 UiRenderContext——
 * 后者构造需 Minecraft 运行时，纯 JUnit 不可用）。铁律锚点走
 * {@link PaintResult#getRegeneratedFragmentCount()} +
 * {@link LayoutResult#getRelayoutCount()} 两个探针，纯沙箱可断言。</p>
 */
public class ScenePaintCompositeReplayTest {

    private final FixedTextMeasurer measurer = new FixedTextMeasurer();
    private final SceneLayoutEngine layoutEngine = new SceneLayoutEngine(measurer);
    private final ScenePaintEngine paintEngine = new ScenePaintEngine(measurer);

    // ============================================================
    // A. 信条五铁律锚点（最高优先，★）
    // ============================================================

    /**
     * ★ 铁律 1：纯 opacity 变化帧 —— relayoutCount==0 && regeneratedFragmentCount==0。
     * <p>opacity 改动只打 compositeDirty，绝不触发布局重排或 fragment 重建。</p>
     */
    @Test
    public void pureOpacityFrameShouldNotRelayoutNorRegenerate() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        child.setBackgroundColor(0xFF336699);
        root.appendChild(child);

        // 首帧：layout + paint（建立缓存）
        layoutEngine.layout(root, new Constraints(100));
        paintEngine.paint(root);

        // 改 opacity（只打 compositeDirty）
        child.setOpacity(0.5f);

        // 第二帧：layout + paint
        LayoutResult layoutResult = layoutEngine.layout(root, new Constraints(100));
        PaintResult result = paintEngine.paint(root);

        // ★ 铁律：零重排 + 零 fragment 重建
        Assert.assertEquals("纯 opacity 帧零重排", 0, layoutResult.getRelayoutCount());
        Assert.assertEquals("纯 opacity 帧零 fragment 重建", 0,
                result.getRegeneratedFragmentCount());
    }

    /**
     * ★ 铁律 2：纯 transform(translate) 变化帧 —— 同样零重排零重建。
     */
    @Test
    public void pureTransformFrameShouldNotRelayoutNorRegenerate() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        child.setBackgroundColor(0xFF336699);
        root.appendChild(child);

        layoutEngine.layout(root, new Constraints(100));
        paintEngine.paint(root);

        // 改 transform（只打 compositeDirty）
        child.setTransform(new Transform(10f, 20f));

        LayoutResult layoutResult = layoutEngine.layout(root, new Constraints(100));
        PaintResult result = paintEngine.paint(root);

        Assert.assertEquals("纯 transform 帧零重排", 0, layoutResult.getRelayoutCount());
        Assert.assertEquals("纯 transform 帧零 fragment 重建", 0,
                result.getRegeneratedFragmentCount());
    }

    /**
     * ★ 铁律 3：多帧推进 opacity（模拟动画）—— 每帧 fragment 引用不变、零重建。
     */
    @Test
    public void animatingOpacityShouldReuseFragmentEveryFrame() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        child.setBackgroundColor(0xFF336699);
        root.appendChild(child);

        layoutEngine.layout(root, new Constraints(100));
        paintEngine.paint(root);
        PaintFragment frag0 = (PaintFragment) child.getCachedPaint();
        Assert.assertNotNull("首帧应有 fragment", frag0);

        // 模拟动画：连续 10 帧推进 opacity
        for (int i = 1; i <= 10; i++) {
            child.setOpacity(1.0f - i * 0.05f);
            layoutEngine.layout(root, new Constraints(100));
            PaintResult result = paintEngine.paint(root);

            // 每帧 fragment 引用不变 + 零重建
            Assert.assertSame("第 " + i + " 帧 fragment 引用不变",
                    frag0, child.getCachedPaint());
            Assert.assertEquals("第 " + i + " 帧零重建", 0,
                    result.getRegeneratedFragmentCount());
        }
    }

    // ============================================================
    // B. opacity group 栈正确性（D1）
    // ============================================================

    /**
     * 单节点 opacity<1：plan 命令流中该 fragment 外层正确出现 PUSH_OPACITY...POP_OPACITY 配对。
     */
    @Test
    public void opacityBelowOneShouldEmitPushPopPair() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        child.setBackgroundColor(0xFF336699);
        child.setOpacity(0.5f);
        root.appendChild(child);

        layoutEngine.layout(root, new Constraints(100));
        PaintPlan plan = paintEngine.paint(root).getPlan();

        List<PaintCommand> cmds = plan.getCommands();
        // 预期序列：PUSH_OPACITY、BACKGROUND、POP_OPACITY
        int pushIdx = indexOfType(cmds, PaintCommandType.PUSH_OPACITY);
        int popIdx = indexOfType(cmds, PaintCommandType.POP_OPACITY);
        int bgIdx = indexOfType(cmds, PaintCommandType.BACKGROUND);

        Assert.assertTrue("应含 PUSH_OPACITY", pushIdx >= 0);
        Assert.assertTrue("应含 POP_OPACITY", popIdx >= 0);
        Assert.assertTrue("PUSH 在 BACKGROUND 之前", pushIdx < bgIdx);
        Assert.assertTrue("POP 在 BACKGROUND 之后", popIdx > bgIdx);

        // PUSH 携带局部 opacity
        Assert.assertEquals("PUSH 携带局部 opacity", 0.5f,
                cmds.get(pushIdx).getOpacity(), 1e-6f);
    }

    /**
     * 嵌套 opacity：父 0.5、子 0.5，断言各传局部值（不传 0.25），push/pop 嵌套深度正确。
     * <p>验证 replayer 不自算累计——相乘交给渲染层离屏层栈。</p>
     */
    @Test
    public void nestedOpacityShouldEmitLocalValuesNotMultiplied() {
        SceneNode root = new SceneNode();
        SceneNode parent = new SceneNode();
        SceneNode child = new SceneNode();
        parent.setBackgroundColor(0xFFAA0000);
        parent.setOpacity(0.5f);
        child.setBackgroundColor(0xFF00AA00);
        child.setOpacity(0.5f);
        parent.appendChild(child);
        root.appendChild(parent);

        layoutEngine.layout(root, new Constraints(100));
        PaintPlan plan = paintEngine.paint(root).getPlan();

        List<PaintCommand> cmds = plan.getCommands();
        // 预期嵌套序列：PUSH(0.5) parentBg PUSH(0.5) childBg POP POP
        // 收集所有 PUSH_OPACITY 的 opacity 值
        float[] pushOpacities = new float[2];
        int pushCount = 0;
        int depth = 0;
        int maxDepth = 0;
        for (PaintCommand cmd : cmds) {
            if (cmd.getType() == PaintCommandType.PUSH_OPACITY) {
                if (pushCount < pushOpacities.length) {
                    pushOpacities[pushCount] = cmd.getOpacity();
                }
                pushCount++;
                depth++;
                maxDepth = Math.max(maxDepth, depth);
            } else if (cmd.getType() == PaintCommandType.POP_OPACITY) {
                depth--;
            }
        }

        Assert.assertEquals("两层 PUSH_OPACITY", 2, pushCount);
        // ★ 各传局部值 0.5，绝不传累计 0.25
        Assert.assertEquals("父 PUSH 局部 opacity", 0.5f, pushOpacities[0], 1e-6f);
        Assert.assertEquals("子 PUSH 局部 opacity", 0.5f, pushOpacities[1], 1e-6f);
        // 嵌套深度达到 2（父子嵌套）
        Assert.assertEquals("最大嵌套深度", 2, maxDepth);
        // push/pop 配对：结束时深度归零
        Assert.assertEquals("push/pop 配对深度归零", 0, depth);
    }

    /**
     * 复杂多层嵌套树：所有 PUSH_OPACITY/POP_OPACITY 严格配对，遍历后深度归零（防泄漏）。
     */
    @Test
    public void complexNestedTreeShouldHaveBalancedPushPop() {
        // root → (a[0.8] → a1[0.5], a2), b[0.3] → b1[0.6]
        SceneNode root = new SceneNode();
        SceneNode a = new SceneNode();
        SceneNode a1 = new SceneNode();
        SceneNode a2 = new SceneNode();
        SceneNode b = new SceneNode();
        SceneNode b1 = new SceneNode();

        a.setBackgroundColor(0xFF111111);
        a.setOpacity(0.8f);
        a1.setBackgroundColor(0xFF222222);
        a1.setOpacity(0.5f);
        a2.setBackgroundColor(0xFF333333);
        b.setBackgroundColor(0xFF444444);
        b.setOpacity(0.3f);
        b1.setBackgroundColor(0xFF555555);
        b1.setOpacity(0.6f);

        a.appendChild(a1);
        a.appendChild(a2);
        b.appendChild(b1);
        root.appendChild(a);
        root.appendChild(b);

        layoutEngine.layout(root, new Constraints(100));
        PaintPlan plan = paintEngine.paint(root).getPlan();

        // 验证整条命令流 push/pop 严格配对、深度全程非负、结束归零
        int depth = 0;
        int pushTotal = 0;
        int popTotal = 0;
        for (PaintCommand cmd : plan.getCommands()) {
            if (cmd.getType() == PaintCommandType.PUSH_OPACITY) {
                depth++;
                pushTotal++;
            } else if (cmd.getType() == PaintCommandType.POP_OPACITY) {
                depth--;
                popTotal++;
                Assert.assertTrue("深度全程非负（POP 不早于 PUSH）", depth >= 0);
            }
        }
        // 4 个 opacity<1 节点（a/a1/b/b1）→ 4 对 PUSH/POP
        Assert.assertEquals("PUSH 总数", 4, pushTotal);
        Assert.assertEquals("POP 总数", 4, popTotal);
        Assert.assertEquals("遍历后深度归零（无泄漏）", 0, depth);
    }

    /**
     * 带 offset 深层节点：PUSH_OPACITY 区域坐标 == 局部区域叠加累计 offset 后的绝对坐标。
     */
    @Test
    public void pushOpacityRegionShouldBeAbsoluteCoordinates() {
        // root → container → leaf[opacity], leaf 在 container 内有非零 y 偏移
        SceneNode root = new SceneNode();
        SceneNode container = new SceneNode();
        SceneNode sibling = new SceneNode();
        SceneNode leaf = new SceneNode();

        sibling.setBackgroundColor(0xFF111111);
        sibling.setText("S"); // 撑高 sibling，使 leaf 下移
        leaf.setBackgroundColor(0xFF336699);
        leaf.setText("L");
        leaf.setOpacity(0.5f);

        container.appendChild(sibling);
        container.appendChild(leaf);
        root.appendChild(container);

        layoutEngine.layout(root, new Constraints(100));
        PaintPlan plan = paintEngine.paint(root).getPlan();

        // leaf 在 sibling(高 16) 之后，绝对 top 应为 16
        PaintCommand push = firstOfType(plan.getCommands(), PaintCommandType.PUSH_OPACITY);
        Assert.assertNotNull("应有 PUSH_OPACITY", push);
        Assert.assertEquals("PUSH 区域 top == 绝对坐标 16", 16, push.getTop());
        Assert.assertEquals("PUSH 区域 left == 0", 0, push.getLeft());
    }

    // ============================================================
    // C. opacity==1 快速路径
    // ============================================================

    /**
     * 全不透明树：plan 命令流零 PUSH_OPACITY/POP_OPACITY（快速路径生效）。
     */
    @Test
    public void fullyOpaqueTreeShouldEmitNoGroupBoundary() {
        SceneNode root = new SceneNode();
        SceneNode a = new SceneNode();
        SceneNode b = new SceneNode();
        a.setBackgroundColor(0xFFFF0000);
        b.setBackgroundColor(0xFF0000FF);
        root.appendChild(a);
        root.appendChild(b);

        layoutEngine.layout(root, new Constraints(100));
        PaintPlan plan = paintEngine.paint(root).getPlan();

        for (PaintCommand cmd : plan.getCommands()) {
            Assert.assertNotEquals("全不透明树零 PUSH_OPACITY",
                    PaintCommandType.PUSH_OPACITY, cmd.getType());
            Assert.assertNotEquals("全不透明树零 POP_OPACITY",
                    PaintCommandType.POP_OPACITY, cmd.getType());
        }
    }

    /**
     * 混合：只 opacity<1 节点产 PUSH/POP，其余走快速路径。
     */
    @Test
    public void onlyTranslucentNodesShouldEmitBoundary() {
        SceneNode root = new SceneNode();
        SceneNode opaque = new SceneNode();
        SceneNode translucent = new SceneNode();
        opaque.setBackgroundColor(0xFFFF0000);          // 不透明
        translucent.setBackgroundColor(0xFF0000FF);
        translucent.setOpacity(0.4f);                   // 半透明
        root.appendChild(opaque);
        root.appendChild(translucent);

        layoutEngine.layout(root, new Constraints(100));
        PaintPlan plan = paintEngine.paint(root).getPlan();

        // 恰好 1 对 PUSH/POP（仅 translucent）
        int pushCount = countType(plan.getCommands(), PaintCommandType.PUSH_OPACITY);
        int popCount = countType(plan.getCommands(), PaintCommandType.POP_OPACITY);
        Assert.assertEquals("仅 1 个 PUSH_OPACITY", 1, pushCount);
        Assert.assertEquals("仅 1 个 POP_OPACITY", 1, popCount);
    }

    // ============================================================
    // D. transform 完整矩阵通路（方案甲，Phase 4C）
    // ============================================================

    /**
     * 方案甲：translate 走 PUSH_TRANSFORM GL 矩阵，绝不进命令坐标。
     * <p>施加 translate(30,40) 后 BACKGROUND 坐标不变（fragment 引用不变），
     * 但 PaintPlan 中应产出 PUSH_TRANSFORM 命令携带正确分量。</p>
     */
    @Test
    public void transformShouldOffsetCommandCoordinatesReusingFragment() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        child.setBackgroundColor(0xFF336699);
        root.appendChild(child);

        layoutEngine.layout(root, new Constraints(100));
        PaintPlan plan1 = paintEngine.paint(root).getPlan();
        PaintCommand bg1 = firstOfType(plan1.getCommands(), PaintCommandType.BACKGROUND);
        Assert.assertNotNull(bg1);
        int origLeft = bg1.getLeft();
        int origTop = bg1.getTop();
        PaintFragment frag1 = (PaintFragment) child.getCachedPaint();

        // 施加 transform translate(30, 40)
        child.setTransform(new Transform(30f, 40f));
        layoutEngine.layout(root, new Constraints(100));
        PaintPlan plan2 = paintEngine.paint(root).getPlan();

        PaintCommand bg2 = firstOfType(plan2.getCommands(), PaintCommandType.BACKGROUND);
        Assert.assertNotNull(bg2);
        // 方案甲：translate 走 GL 矩阵，命令坐标不变
        Assert.assertEquals("方案甲 translate 不进命令坐标：left 不变", origLeft, bg2.getLeft());
        Assert.assertEquals("方案甲 translate 不进命令坐标：top 不变", origTop, bg2.getTop());

        // PUSH_TRANSFORM 携带正确的 translate 分量
        PaintCommand pushTr = firstOfType(plan2.getCommands(), PaintCommandType.PUSH_TRANSFORM);
        Assert.assertNotNull("方案甲应产出 PUSH_TRANSFORM", pushTr);
        Assert.assertEquals("PUSH_TRANSFORM translateX==30f", 30f, pushTr.getTranslateX(), 1e-6f);
        Assert.assertEquals("PUSH_TRANSFORM translateY==40f", 40f, pushTr.getTranslateY(), 1e-6f);

        // fragment 引用不变（transform 不重建）
        Assert.assertSame("transform 帧 fragment 引用不变",
                frag1, child.getCachedPaint());
    }

    /**
     * 方案甲：translate 不进命令坐标，layout offset 仍正确反映几何位置。
     * <p>leaf layout y=16（sibling 撑高），transform +100 走 PUSH_TRANSFORM，
     * leafBg.getTop()==16（纯 layout offset），PUSH_TRANSFORM.translateY==100。</p>
     */
    @Test
    public void transformAndGeometryOffsetShouldAccumulate() {
        SceneNode root = new SceneNode();
        SceneNode container = new SceneNode();
        SceneNode sibling = new SceneNode();
        SceneNode leaf = new SceneNode();

        sibling.setBackgroundColor(0xFF111111);
        sibling.setText("S"); // 撑高 16，使 leaf 的 layout y=16
        leaf.setBackgroundColor(0xFF336699);
        leaf.setTransform(new Transform(0f, 100f)); // transform translateY=100，走 GL 矩阵

        container.appendChild(sibling);
        container.appendChild(leaf);
        root.appendChild(container);

        layoutEngine.layout(root, new Constraints(100));
        PaintPlan plan = paintEngine.paint(root).getPlan();

        PaintCommand leafBg = findCommandByColor(plan, 0xFF336699);
        Assert.assertNotNull(leafBg);
        // 方案甲：命令坐标只含 layout offset（y=16），translate 不进坐标
        Assert.assertEquals("方案甲：命令坐标只含 layout offset，top==16", 16, leafBg.getTop());

        // PUSH_TRANSFORM 应携带 translateY=100
        PaintCommand pushTr = firstOfType(plan.getCommands(), PaintCommandType.PUSH_TRANSFORM);
        Assert.assertNotNull("leaf 应产出 PUSH_TRANSFORM", pushTr);
        Assert.assertEquals("PUSH_TRANSFORM translateY==100f", 100f, pushTr.getTranslateY(), 1e-6f);
    }

    /**
     * 方案甲：同节点 opacity + transform 共存，PUSH_OPACITY 区域使用裸 layout 坐标（不含 translate）。
     * <p>translate 由外层 PUSH_TRANSFORM 承载，PUSH_OPACITY 用的是 nodeAbsX/Y（纯 layout）。
     * 命令流顺序：PUSH_TRANSFORM → PUSH_OPACITY → BACKGROUND → POP_OPACITY → POP_TRANSFORM。</p>
     */
    @Test
    public void opacityAndTransformOnSameNodeShouldOffsetPushRegion() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        child.setBackgroundColor(0xFF336699);
        child.setOpacity(0.5f);                       // 触发 group
        child.setTransform(new Transform(30f, 40f));  // 同节点再施 translate
        root.appendChild(child);

        layoutEngine.layout(root, new Constraints(100));
        PaintPlan plan = paintEngine.paint(root).getPlan();

        // 方案甲：PUSH_OPACITY 区域用裸 layout 坐标（0,0），translate 由外层 PUSH_TRANSFORM 承载
        PaintCommand push = firstOfType(plan.getCommands(), PaintCommandType.PUSH_OPACITY);
        Assert.assertNotNull("应有 PUSH_OPACITY", push);
        Assert.assertEquals("PUSH_OPACITY 区域 left==0（裸 layout 坐标）", 0, push.getLeft());
        Assert.assertEquals("PUSH_OPACITY 区域 top==0（裸 layout 坐标）", 0, push.getTop());
        Assert.assertEquals("PUSH 携带局部 opacity", 0.5f, push.getOpacity(), 1e-6f);

        // PUSH_TRANSFORM 携带正确 translate 分量
        PaintCommand pushTr = firstOfType(plan.getCommands(), PaintCommandType.PUSH_TRANSFORM);
        Assert.assertNotNull("应有 PUSH_TRANSFORM", pushTr);
        Assert.assertEquals("PUSH_TRANSFORM translateX==30f", 30f, pushTr.getTranslateX(), 1e-6f);
        Assert.assertEquals("PUSH_TRANSFORM translateY==40f", 40f, pushTr.getTranslateY(), 1e-6f);

        // 命令流顺序：PUSH_TRANSFORM 在 PUSH_OPACITY 之前（transform 是最外层作用域）
        List<PaintCommand> cmds = plan.getCommands();
        int trIdx = indexOfType(cmds, PaintCommandType.PUSH_TRANSFORM);
        int opIdx = indexOfType(cmds, PaintCommandType.PUSH_OPACITY);
        Assert.assertTrue("PUSH_TRANSFORM 在 PUSH_OPACITY 之前", trIdx < opIdx);
    }

    // ============================================================
    // E. clearCompositeDirty 消费（帧循环消费步骤）
    // ============================================================

    /**
     * setOpacity → paint 一帧后，compositeDirty 被 clearCompositeDirty 清除。
     */
    @Test
    public void compositeDirtyShouldBeClearedAfterPaint() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        child.setBackgroundColor(0xFF336699);
        root.appendChild(child);

        layoutEngine.layout(root, new Constraints(100));
        paintEngine.paint(root);

        child.setOpacity(0.5f);
        Assert.assertTrue("setOpacity 后 composite 脏", child.__isCompositeDirty());

        layoutEngine.layout(root, new Constraints(100));
        paintEngine.paint(root);

        Assert.assertFalse("paint 后 composite 已清", child.__isCompositeDirty());
        Assert.assertFalse("paint 后 root descendantComposite 已清",
                root.__isDescendantCompositeDirty());
    }

    /**
     * composite 与 paint/geometry 清除隔离：setOpacity（只 composite 脏）后 paint，
     * paint/geometry 标记不被误置（与 3A 解耦呼应）。
     */
    @Test
    public void compositeClearShouldNotAffectPaintGeometryAfterFrame() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        child.setBackgroundColor(0xFF336699);
        root.appendChild(child);

        layoutEngine.layout(root, new Constraints(100));
        paintEngine.paint(root);

        // 只改 opacity
        child.setOpacity(0.5f);
        Assert.assertFalse("opacity 改动不污染 selfPaint", child.__isSelfPaintDirty());

        layoutEngine.layout(root, new Constraints(100));
        paintEngine.paint(root);

        // 帧后全干净
        Assert.assertFalse("composite 已清", child.__isCompositeDirty());
        Assert.assertFalse("paint 全程干净", child.__isSelfPaintDirty());
        Assert.assertFalse("geometry 全程干净", child.__isSelfGeometryDirty());
    }

    // ============================================================
    // F. P10/P10b：opacity / transform layer 区域用子树内容包围盒
    // ============================================================

    /**
     * P10：父节点 opacity=0.5 + 溢出子（preferredWidth=150 &gt; 父钉死宽 100）。
     * <p>PUSH_OPACITY 区域必须覆盖溢出后代的布局盒：离屏层全屏分配，回贴窗口钉节点盒
     * 会把溢出内容在 opacity&lt;1 期间隐式硬裁。</p>
     */
    @Test
    public void opacityPushRegionShouldCoverOverflowingDescendant() {
        SceneNode root = new SceneNode();
        SceneNode parent = new SceneNode();
        parent.setOpacity(0.5f);
        parent.setPreferredWidth(100);
        parent.setPreferredHeight(20);
        parent.setBackgroundColor(0xFFAA0000);
        SceneNode child = new SceneNode();
        child.setPreferredWidth(150);
        child.setPreferredHeight(20);
        child.setBackgroundColor(0xFF00AA00);
        parent.appendChild(child);
        root.appendChild(parent);

        layoutEngine.layout(root, new Constraints(100, 100));
        PaintPlan plan = paintEngine.paint(root).getPlan();

        PaintCommand push = firstOfType(plan.getCommands(), PaintCommandType.PUSH_OPACITY);
        Assert.assertNotNull("应有 PUSH_OPACITY", push);
        AnchorRect childBox = SceneGeometry.absoluteBox(child, 0, 0);
        Assert.assertTrue("PUSH right 覆盖溢出后代右边界",
                push.getRight() >= childBox.getX() + childBox.getWidth());
        Assert.assertTrue("PUSH bottom 覆盖溢出后代底边界",
                push.getBottom() >= childBox.getY() + childBox.getHeight());
        Assert.assertEquals("PUSH 携带局部 opacity", 0.5f, push.getOpacity(), 1e-6f);
    }

    /**
     * P10：嵌套 group opacity（父 0.5 / 子 0.5）+ 溢出孙（preferredWidth=150）。
     * <p>外层区域必须同样覆盖孙盒：外层离屏层贴回时孙的溢出内容仍在窗口内。</p>
     */
    @Test
    public void nestedOpacityGroupsShouldInflateOuterRegion() {
        SceneNode root = new SceneNode();
        SceneNode parent = new SceneNode();
        parent.setOpacity(0.5f);
        parent.setPreferredWidth(100);
        parent.setPreferredHeight(20);
        SceneNode child = new SceneNode();
        child.setOpacity(0.5f);
        SceneNode grandchild = new SceneNode();
        grandchild.setPreferredWidth(150);
        grandchild.setPreferredHeight(20);
        grandchild.setBackgroundColor(0xFF00AA00);
        child.appendChild(grandchild);
        parent.appendChild(child);
        root.appendChild(parent);

        layoutEngine.layout(root, new Constraints(100, 100));
        List<PaintCommand> cmds = paintEngine.paint(root).getPlan().getCommands();

        int firstPush = indexOfType(cmds, PaintCommandType.PUSH_OPACITY);
        Assert.assertTrue("应有外层 PUSH_OPACITY", firstPush >= 0);
        int secondPush = -1;
        for (int i = firstPush + 1; i < cmds.size(); i++) {
            if (cmds.get(i).getType() == PaintCommandType.PUSH_OPACITY) {
                secondPush = i;
                break;
            }
        }
        Assert.assertTrue("应有内层 PUSH_OPACITY", secondPush >= 0);

        AnchorRect grandBox = SceneGeometry.absoluteBox(grandchild, 0, 0);
        PaintCommand outer = cmds.get(firstPush);
        PaintCommand inner = cmds.get(secondPush);
        Assert.assertTrue("外层区域覆盖孙盒右边界",
                outer.getRight() >= grandBox.getX() + grandBox.getWidth());
        Assert.assertTrue("外层区域覆盖孙盒底边界",
                outer.getBottom() >= grandBox.getY() + grandBox.getHeight());
        Assert.assertTrue("内层区域同样覆盖孙盒",
                inner.getRight() >= grandBox.getX() + grandBox.getWidth());
        Assert.assertEquals("外层 opacity 局部值", 0.5f, outer.getOpacity(), 1e-6f);
        Assert.assertEquals("内层 opacity 局部值", 0.5f, inner.getOpacity(), 1e-6f);
    }

    /**
     * P10：opacity==0 子树被剪枝（零命令），包围盒不为其增长。
     * <p>预遍历跳过剪枝子树与主遍历同口径：区域恒等于父自身盒（而非 150 宽）。</p>
     */
    @Test
    public void opacityRegionShouldNotGrowForPrunedZeroOpacitySubtree() {
        SceneNode root = new SceneNode();
        SceneNode parent = new SceneNode();
        parent.setOpacity(0.5f);
        parent.setPreferredWidth(100);
        parent.setPreferredHeight(20);
        parent.setBackgroundColor(0xFFAA0000);
        SceneNode hidden = new SceneNode();
        hidden.setOpacity(0.0F);
        hidden.setPreferredWidth(150);
        hidden.setPreferredHeight(20);
        hidden.setBackgroundColor(0xFF00FF00);
        parent.appendChild(hidden);
        root.appendChild(parent);

        layoutEngine.layout(root, new Constraints(100, 100));
        PaintPlan plan = paintEngine.paint(root).getPlan();

        Assert.assertEquals("剪枝子树零命令：仅 1 对 PUSH/POP_OPACITY", 1,
                countType(plan.getCommands(), PaintCommandType.PUSH_OPACITY));
        PaintCommand push = firstOfType(plan.getCommands(), PaintCommandType.PUSH_OPACITY);
        Assert.assertNotNull("应有 PUSH_OPACITY", push);
        AnchorRect parentBox = SceneGeometry.absoluteBox(parent, 0, 0);
        Assert.assertEquals("区域不随剪枝子树增长：right == 父自身盒右边界",
                parentBox.getX() + parentBox.getWidth(), push.getRight());
        Assert.assertEquals("区域不随剪枝子树增长：bottom == 父自身盒底边界",
                parentBox.getY() + parentBox.getHeight(), push.getBottom());
        Assert.assertNull("剪枝子树不产出 BACKGROUND", findCommandByColor(plan, 0xFF00FF00));
        Assert.assertNotNull("父自身 BACKGROUND 仍在", findCommandByColor(plan, 0xFFAA0000));
    }

    /**
     * P10：scrollable 视口滚动注入口径 —— opacity 区域用注入后的绝对坐标，
     * 且与 CLIP 叠加不冲突（CLIP 框固定不含注入，opacity 区域跟随内容平移并可覆盖溢出内容）。
     */
    @Test
    public void opacityRegionInsideScrollableMatchesInjectedOffset() {
        SceneNode root = SceneNode.column();
        SceneNode viewport = SceneNode.column();
        viewport.setScrollable(true);
        viewport.setPreferredHeight(50);
        SceneNode shell = SceneNode.column();
        shell.setOpacity(0.75f);
        SceneNode item = new SceneNode();
        item.setPreferredWidth(150);
        item.setPreferredHeight(20);
        item.setBackgroundColor(0xFF123456);
        shell.appendChild(item);
        viewport.appendChild(shell);
        root.appendChild(viewport);

        layoutEngine.layout(root, new Constraints(100, 100));
        viewport.setScrollOffsetY(5);
        PaintPlan plan = paintEngine.paint(root).getPlan();

        PaintCommand push = firstOfType(plan.getCommands(), PaintCommandType.PUSH_OPACITY);
        Assert.assertNotNull("应有 PUSH_OPACITY", push);
        // 注入口径：shell 绝对 y = 0 - scrollOffsetY(5) = -5；区域覆盖溢出内容宽 150
        Assert.assertEquals("opacity 区域 top 注入滚动偏移", -5, push.getTop());
        Assert.assertEquals("opacity 区域 bottom = top + 内容高", 15, push.getBottom());
        Assert.assertEquals("opacity 区域覆盖溢出内容右边界", 150, push.getRight());

        // CLIP 叠加不冲突：viewport 裁剪框固定不动（不含滚动注入），仍裁 (0,0,100,50)
        PaintCommand clip = firstOfType(plan.getCommands(), PaintCommandType.CLIP_PUSH);
        Assert.assertNotNull("应有 CLIP_PUSH", clip);
        Assert.assertEquals("CLIP top 固定 0（不含注入）", 0, clip.getTop());
        Assert.assertEquals("CLIP bottom 固定视口高 50", 50, clip.getBottom());
        Assert.assertEquals("CLIP 仅 1 对（scrollable 自动裁剪）", 1,
                countType(plan.getCommands(), PaintCommandType.CLIP_PUSH));
    }

    /**
     * P10b：preferTransformLayer 无 clip 场景（HUD 根同源缺陷）——PUSH_TRANSFORM_LAYER
     * 回贴窗口同样必须覆盖溢出后代，否则溢出内容在图层贴回时被隐式硬裁。
     */
    @Test
    public void transformLayerRegionShouldCoverOverflowingDescendant() {
        SceneNode root = new SceneNode();
        SceneNode parent = new SceneNode();
        parent.setPreferTransformLayer(true);
        parent.setTransform(new Transform(5f, 5f));
        parent.setPreferredWidth(100);
        parent.setPreferredHeight(20);
        parent.setBackgroundColor(0xFFAA0000);
        SceneNode child = new SceneNode();
        child.setPreferredWidth(150);
        child.setPreferredHeight(20);
        child.setBackgroundColor(0xFF00AA00);
        parent.appendChild(child);
        root.appendChild(parent);

        layoutEngine.layout(root, new Constraints(100, 100));
        PaintPlan plan = paintEngine.paint(root).getPlan();

        Assert.assertEquals("无 clip 且 preferTransformLayer 走图层路径：零 PUSH_TRANSFORM", 0,
                countType(plan.getCommands(), PaintCommandType.PUSH_TRANSFORM));
        Assert.assertEquals("无 clip 场景零 CLIP", 0,
                countType(plan.getCommands(), PaintCommandType.CLIP_PUSH));
        PaintCommand layer = firstOfType(plan.getCommands(), PaintCommandType.PUSH_TRANSFORM_LAYER);
        Assert.assertNotNull("应有 PUSH_TRANSFORM_LAYER", layer);

        AnchorRect childBox = SceneGeometry.absoluteBox(child, 0, 0);
        Assert.assertTrue("图层回贴窗口覆盖溢出后代右边界",
                layer.getRight() >= childBox.getX() + childBox.getWidth());
        Assert.assertTrue("图层回贴窗口覆盖溢出后代底边界",
                layer.getBottom() >= childBox.getY() + childBox.getHeight());
        Assert.assertEquals("transform translate 分量原样保留", 5f, layer.getTranslateX(), 1e-6f);

        // origin 仍锚定节点自身盒中心（box 归一化语义）：折算后绝对原点恒为节点盒中心 (50,10)
        float originAbsX = layer.getLeft() + layer.getOriginXRatio() * (layer.getRight() - layer.getLeft());
        float originAbsY = layer.getTop() + layer.getOriginYRatio() * (layer.getBottom() - layer.getTop());
        Assert.assertEquals("transform origin 仍锚定节点盒中心 x=50", 50f, originAbsX, 1e-3f);
        Assert.assertEquals("transform origin 仍锚定节点盒中心 y=10", 10f, originAbsY, 1e-3f);
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private static int indexOfType(List<PaintCommand> cmds, PaintCommandType type) {
        for (int i = 0; i < cmds.size(); i++) {
            if (cmds.get(i).getType() == type) {
                return i;
            }
        }
        return -1;
    }

    private static PaintCommand firstOfType(List<PaintCommand> cmds, PaintCommandType type) {
        for (PaintCommand cmd : cmds) {
            if (cmd.getType() == type) {
                return cmd;
            }
        }
        return null;
    }

    private static int countType(List<PaintCommand> cmds, PaintCommandType type) {
        int count = 0;
        for (PaintCommand cmd : cmds) {
            if (cmd.getType() == type) {
                count++;
            }
        }
        return count;
    }

    private static PaintCommand findCommandByColor(PaintPlan plan, int color) {
        for (PaintCommand cmd : plan.getCommands()) {
            if (cmd.getType() == PaintCommandType.BACKGROUND && cmd.getColor() == color) {
                return cmd;
            }
        }
        return null;
    }
}
