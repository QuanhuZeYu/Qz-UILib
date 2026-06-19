package club.heiqi.uilib.ui.scene.paint;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.scene.layout.Constraints;
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
 * {@link ScenePaintEngine#__getRegeneratedFragmentCount()} +
 * {@link SceneLayoutEngine#__getRelayoutCount()} 两个探针，纯沙箱可断言。</p>
 */
public class ScenePaintCompositeReplayTest {

    private final SceneLayoutEngine layoutEngine = new SceneLayoutEngine();
    private final ScenePaintEngine paintEngine = new ScenePaintEngine();

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
        layoutEngine.layout(root, new Constraints(100));
        paintEngine.paint(root);

        // ★ 铁律：零重排 + 零 fragment 重建
        Assert.assertEquals("纯 opacity 帧零重排", 0, layoutEngine.__getRelayoutCount());
        Assert.assertEquals("纯 opacity 帧零 fragment 重建", 0,
                paintEngine.__getRegeneratedFragmentCount());
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

        layoutEngine.layout(root, new Constraints(100));
        paintEngine.paint(root);

        Assert.assertEquals("纯 transform 帧零重排", 0, layoutEngine.__getRelayoutCount());
        Assert.assertEquals("纯 transform 帧零 fragment 重建", 0,
                paintEngine.__getRegeneratedFragmentCount());
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
            paintEngine.paint(root);

            // 每帧 fragment 引用不变 + 零重建
            Assert.assertSame("第 " + i + " 帧 fragment 引用不变",
                    frag0, child.getCachedPaint());
            Assert.assertEquals("第 " + i + " 帧零重建", 0,
                    paintEngine.__getRegeneratedFragmentCount());
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
        PaintPlan plan = paintEngine.paint(root);

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
        PaintPlan plan = paintEngine.paint(root);

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
        PaintPlan plan = paintEngine.paint(root);

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
        PaintPlan plan = paintEngine.paint(root);

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
        PaintPlan plan = paintEngine.paint(root);

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
        PaintPlan plan = paintEngine.paint(root);

        // 恰好 1 对 PUSH/POP（仅 translucent）
        int pushCount = countType(plan.getCommands(), PaintCommandType.PUSH_OPACITY);
        int popCount = countType(plan.getCommands(), PaintCommandType.POP_OPACITY);
        Assert.assertEquals("仅 1 个 PUSH_OPACITY", 1, pushCount);
        Assert.assertEquals("仅 1 个 POP_OPACITY", 1, popCount);
    }

    // ============================================================
    // D. transform offset 叠加（D2）
    // ============================================================

    /**
     * translate 命令绝对坐标 == 原坐标 + dx + dy，且 fragment 引用不变。
     */
    @Test
    public void transformShouldOffsetCommandCoordinatesReusingFragment() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        child.setBackgroundColor(0xFF336699);
        root.appendChild(child);

        layoutEngine.layout(root, new Constraints(100));
        PaintPlan plan1 = paintEngine.paint(root);
        PaintCommand bg1 = firstOfType(plan1.getCommands(), PaintCommandType.BACKGROUND);
        Assert.assertNotNull(bg1);
        int origLeft = bg1.getLeft();
        int origTop = bg1.getTop();
        PaintFragment frag1 = (PaintFragment) child.getCachedPaint();

        // 施加 transform translate(30, 40)
        child.setTransform(new Transform(30f, 40f));
        layoutEngine.layout(root, new Constraints(100));
        PaintPlan plan2 = paintEngine.paint(root);

        PaintCommand bg2 = firstOfType(plan2.getCommands(), PaintCommandType.BACKGROUND);
        Assert.assertNotNull(bg2);
        // 绝对坐标顺移 +30/+40
        Assert.assertEquals("transform 后 left 顺移 +30", origLeft + 30, bg2.getLeft());
        Assert.assertEquals("transform 后 top 顺移 +40", origTop + 40, bg2.getTop());

        // fragment 引用不变（transform 不重建）
        Assert.assertSame("transform 帧 fragment 引用不变",
                frag1, child.getCachedPaint());
    }

    /**
     * transform + geometry 共存：layout offset 与 transform translate 正确累加。
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
        leaf.setTransform(new Transform(0f, 100f)); // transform 再 +100

        container.appendChild(sibling);
        container.appendChild(leaf);
        root.appendChild(container);

        layoutEngine.layout(root, new Constraints(100));
        PaintPlan plan = paintEngine.paint(root);

        PaintCommand leafBg = findCommandByColor(plan, 0xFF336699);
        Assert.assertNotNull(leafBg);
        // layout y=16 + transform 100 = 116
        Assert.assertEquals("layout offset + transform 累加", 116, leafBg.getTop());
    }

    /**
     * 同节点 opacity + transform 共存：PUSH_OPACITY 区域坐标须用含 transform 偏移的绝对边界。
     * <p>验证 needGroup 分支里的 nodeAbsX/Y 已吸收 transform translate（push 区域不是裸 layout 坐标）。</p>
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
        PaintPlan plan = paintEngine.paint(root);

        PaintCommand push = firstOfType(plan.getCommands(), PaintCommandType.PUSH_OPACITY);
        Assert.assertNotNull("应有 PUSH_OPACITY", push);
        // 原始 layout 左上角为 (0,0)，叠加 transform(30,40) 后 PUSH 区域绝对左上角应为 (30,40)
        Assert.assertEquals("PUSH 区域 left 含 transform 偏移", 30, push.getLeft());
        Assert.assertEquals("PUSH 区域 top 含 transform 偏移", 40, push.getTop());
        Assert.assertEquals("PUSH 携带局部 opacity", 0.5f, push.getOpacity(), 1e-6f);

        // 背景命令坐标同样含 transform 偏移
        PaintCommand bg = firstOfType(plan.getCommands(), PaintCommandType.BACKGROUND);
        Assert.assertNotNull(bg);
        Assert.assertEquals("BACKGROUND left 含 transform 偏移", 30, bg.getLeft());
        Assert.assertEquals("BACKGROUND top 含 transform 偏移", 40, bg.getTop());
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
