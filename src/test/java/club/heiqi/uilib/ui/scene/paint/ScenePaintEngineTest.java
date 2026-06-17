package club.heiqi.uilib.ui.scene.paint;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.Assert;

import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.layout.Constraints;

/**
 * ScenePaintEngine + ScenePaintReplayer 单元测试。
 *
 * <p>核心验证：I8 缓存复用（干净兄弟 fragment 零重生成）、命令生成正确性、
 * Replayer 翻译到 UiRenderContext 的映射正确性。</p>
 */
public class ScenePaintEngineTest {

    private final SceneLayoutEngine layoutEngine = new SceneLayoutEngine();
    private final ScenePaintEngine paintEngine = new ScenePaintEngine();
    private final ScenePaintReplayer replayer = new ScenePaintReplayer();

    // ============================================================
    // 测试 1：一棵小树 paint 后命令正确
    // ============================================================

    /**
     * 树结构：root(无属性) → container(背景色) → textNode(文本)
     * layout 后 paint，验证 PaintPlan 中命令数、类型、字段正确。
     */
    @Test
    public void shouldGenerateCorrectCommandsForTree() {
        // 构建树
        SceneNode root = new SceneNode();
        SceneNode container = new SceneNode();
        SceneNode textNode = new SceneNode();

        container.setBackgroundColor(0xFF336699);  // 深蓝背景
        textNode.setText("Hello Scene");            // 文本

        container.appendChild(textNode);
        root.appendChild(container);

        // 先 layout
        layoutEngine.layout(root, new Constraints(200));

        // 再 paint
        PaintPlan plan = paintEngine.paint(root);

        List<PaintCommand> commands = plan.getCommands();
        // 预期：container 的 BACKGROUND + textNode 的 TEXT = 2 条命令
        Assert.assertEquals("命令总数", 2, commands.size());

        // 第一条：BACKGROUND（先画背景）
        PaintCommand cmd0 = commands.get(0);
        Assert.assertEquals("cmd0 类型", PaintCommandType.BACKGROUND, cmd0.getType());
        Assert.assertEquals("cmd0 颜色", 0xFF336699, cmd0.getColor());
        // container 布局：x=0, y=0, w=200, h=16(文本行高)
        Assert.assertEquals("cmd0 left", 0, cmd0.getLeft());
        Assert.assertEquals("cmd0 top", 0, cmd0.getTop());
        Assert.assertEquals("cmd0 right", 200, cmd0.getRight());
        Assert.assertEquals("cmd0 bottom", 16, cmd0.getBottom());

        // 第二条：TEXT
        PaintCommand cmd1 = commands.get(1);
        Assert.assertEquals("cmd1 类型", PaintCommandType.TEXT, cmd1.getType());
        Assert.assertEquals("cmd1 文本", "Hello Scene", cmd1.getText());
        Assert.assertNotNull("cmd1 textStyle", cmd1.getTextStyle());
        // 文本节点在 container 内，局部坐标 (0,0)，绝对坐标 = container 的 (0,0)
        Assert.assertEquals("cmd1 left", 0, cmd1.getLeft());
        Assert.assertEquals("cmd1 top", 0, cmd1.getTop());
    }

    // ============================================================
    // 测试 2：I8 铁证 —— 干净兄弟 fragment 被复用
    // ============================================================

    /**
     * root → A(背景), B(背景), C(背景)。先 layout+paint 一次，
     * 改 B 的背景色使其 selfPaintDirty，再 paint，断言 A 和 C 的
     * cachedPaint（PaintFragment）引用不变（未被重新生成）。
     */
    @Test
    public void shouldSkipCleanSiblingFragmentOnRepaint() {
        SceneNode root = new SceneNode();
        SceneNode a = new SceneNode();
        SceneNode b = new SceneNode();
        SceneNode c = new SceneNode();

        a.setBackgroundColor(0xFFFF0000); // 红
        b.setBackgroundColor(0xFF00FF00); // 绿
        c.setBackgroundColor(0xFF0000FF); // 蓝

        root.appendChild(a);
        root.appendChild(b);
        root.appendChild(c);

        // 第一次 layout + paint
        layoutEngine.layout(root, new Constraints(100));
        paintEngine.paint(root);

        // 记录 A 和 C 的 fragment 引用
        PaintFragment fragA1 = (PaintFragment) a.getCachedPaint();
        PaintFragment fragC1 = (PaintFragment) c.getCachedPaint();
        Assert.assertNotNull("A 应有 fragment", fragA1);
        Assert.assertNotNull("C 应有 fragment", fragC1);

        // 修改 B（触发 B.selfPaintDirty + root.descendantPaintDirty）
        b.setBackgroundColor(0xFF888800); // 黄绿

        // 第二次 paint
        paintEngine.paint(root);

        // I8 铁证：A 和 C 的 fragment 引用不变（零重生成）
        PaintFragment fragA2 = (PaintFragment) a.getCachedPaint();
        PaintFragment fragC2 = (PaintFragment) c.getCachedPaint();

        Assert.assertSame("I8: A 的 fragment 应被复用（引用相同）", fragA1, fragA2);
        Assert.assertSame("I8: C 的 fragment 应被复用（引用相同）", fragC1, fragC2);

        // 验证只重新生成了 1 个 fragment（B）
        Assert.assertEquals("重新生成 fragment 数", 1, paintEngine.__getRegeneratedFragmentCount());
    }

    // ============================================================
    // 测试 3：全树 clean 时整棵跳过
    // ============================================================

    @Test
    public void shouldSkipEntireCleanTree() {
        SceneNode root = new SceneNode();
        SceneNode a = new SceneNode();
        a.setBackgroundColor(0xFF000000);
        root.appendChild(a);

        layoutEngine.layout(root, new Constraints(100));
        paintEngine.paint(root);
        Assert.assertTrue("首次 paint 有生成", paintEngine.__getRegeneratedFragmentCount() >= 1);

        // 第二次 paint：全树 clean，零重生成
        paintEngine.paint(root);
        Assert.assertEquals("第二次 paint 整棵跳过，零重生成", 0,
                paintEngine.__getRegeneratedFragmentCount());
    }

    // ============================================================
    // 测试 4：descendantPaintDirty 下沉但中间节点复用
    // ============================================================

    @Test
    public void shouldDescendantDirtySinkButReuseIntermediateNodeFragment() {
        SceneNode root = new SceneNode();
        SceneNode container = new SceneNode();
        SceneNode leaf = new SceneNode();

        container.setBackgroundColor(0xFFAAAAAA);
        leaf.setBackgroundColor(0xFFBBBBBB);

        container.appendChild(leaf);
        root.appendChild(container);

        // 第一次 layout + paint
        layoutEngine.layout(root, new Constraints(100));
        paintEngine.paint(root);

        PaintFragment containerFrag1 = (PaintFragment) container.getCachedPaint();
        Assert.assertNotNull(containerFrag1);

        // 修改 leaf（触发 leaf.selfPaintDirty + container/root.descendantPaintDirty）
        leaf.setBackgroundColor(0xFFCCCCCC);

        // 第二次 paint
        paintEngine.paint(root);

        // container 的 fragment 应复用（selfPaintDirty==false）
        PaintFragment containerFrag2 = (PaintFragment) container.getCachedPaint();
        Assert.assertSame("container fragment 引用不变", containerFrag1, containerFrag2);
        // 只 leaf 重新生成
        Assert.assertEquals("重新生成 fragment 数", 1, paintEngine.__getRegeneratedFragmentCount());
    }

    // ============================================================
    // 测试 5：无布局的节点被跳过
    // ============================================================

    @Test
    public void shouldSkipNodeWithoutLayout() {
        SceneNode root = new SceneNode();
        SceneNode noLayout = new SceneNode();
        noLayout.setBackgroundColor(0xFFFF0000);
        root.appendChild(noLayout);

        // 不 layout 直接 paint
        PaintPlan plan = paintEngine.paint(root);
        Assert.assertEquals("无布局节点被跳过，命令数为 0", 0, plan.size());
    }

    // ============================================================
    // 测试 6：replayer 映射正确性（尝试真实 UiRenderContext）
    // ============================================================

    /**
     * 如果 UiRenderContext 在测试环境可构造，验证 replay 命令 → fillRect/drawText 映射正确。
     * 如果构造失败（无 Minecraft 运行时），此测试静默跳过。
     */
    @Test
    public void shouldReplayCommandsToRenderContext() {
        TestRenderContext testCtx;
        try {
            testCtx = new TestRenderContext();
        } catch (Exception e) {
            // UiRenderContext 构造需要 Minecraft 运行时（FontRenderer 等），
            // 在纯 JUnit 环境不可用。跳过此测试并在控制台输出原因。
            System.out.println("[ScenePaintEngineTest] 跳过 replayer 测试："
                    + "UiRenderContext 构造失败 (" + e.getClass().getSimpleName() + ")");
            return;
        }

        // 手动构建 PaintPlan
        PaintPlan plan = new PaintPlan();
        plan.addCommand(PaintCommand.background(10, 20, 110, 70, 0xFF336699));
        PaintCommand textCmd = PaintCommand.text(5, 30, "Hello",
                new TextStyle(0xFFFFFFFF, 14));
        plan.addCommand(textCmd);

        // 执行 replay
        replayer.replay(plan, testCtx);

        // 验证调用序列
        List<String> calls = testCtx.getCalls();
        Assert.assertEquals("render 调用数", 2, calls.size());
        Assert.assertTrue("第 1 条应为 fillRect", calls.get(0).startsWith("fillRect"));
        Assert.assertTrue("第 1 条含坐标 10,20,110,70",
                calls.get(0).contains("10,20,110,70"));
        Assert.assertTrue("第 1 条含颜色 #ff336699",
                calls.get(0).contains("#ff336699"));
        Assert.assertTrue("第 2 条应为 drawText", calls.get(1).startsWith("drawText"));
        Assert.assertTrue("第 2 条含 'Hello'", calls.get(1).contains("Hello"));
    }

    // ============================================================
    // 测试 7：B1 回归 —— 位置变化时 fragment 复用但绝对坐标更新
    // ============================================================

    /**
     * 前序兄弟 layout 变高导致后续节点位置下移，验证：
     * ① B 的 fragment 引用不变（paint 干净，零重生成）
     * ② B 命令的绝对坐标 y 已顺移到新位置
     */
    @Test
    public void shouldUpdateAbsoluteOffsetWhenPositionChangesButPaintClean() {
        // root → container → A(文本+背景红), B(文本+背景蓝)
        SceneNode root = new SceneNode();
        SceneNode container = new SceneNode();
        SceneNode a = new SceneNode();
        SceneNode b = new SceneNode();

        a.setText("A");
        a.setBackgroundColor(0xFFFF0000); // 红 — 方便按颜色找命令
        b.setText("B");
        b.setBackgroundColor(0xFF0000FF); // 蓝

        container.appendChild(a);
        container.appendChild(b);
        root.appendChild(container);

        // 第一次 layout + paint
        layoutEngine.layout(root, new Constraints(100));
        PaintPlan plan1 = paintEngine.paint(root);

        // 记录 B 的 fragment 引用
        PaintFragment bFrag1 = (PaintFragment) b.getCachedPaint();
        Assert.assertNotNull("B 应有 fragment", bFrag1);

        // 记录 B 背景命令的初始绝对坐标（top 应 = 16，A 高度 16）
        PaintCommand bBgCmd1 = findCommandByColor(plan1, 0xFF0000FF);
        Assert.assertNotNull("应找到 B 的背景命令（蓝）", bBgCmd1);
        Assert.assertEquals("B 初始 top=16", 16, bBgCmd1.getTop());

        // A 变双行 → A 高度 16→32，layout 重排 → B.y 16→32
        a.setText("A\nX");

        // 重新 layout + paint
        layoutEngine.layout(root, new Constraints(100));
        PaintPlan plan2 = paintEngine.paint(root);

        // B1 铁证 1：B 的 fragment 引用不变（paint 属性没变 → 零重生成）
        PaintFragment bFrag2 = (PaintFragment) b.getCachedPaint();
        Assert.assertSame("B fragment 引用不变（零重生成）", bFrag1, bFrag2);

        // B1 铁证 2：B 命令的绝对坐标 top 已顺移 16→32
        PaintCommand bBgCmd2 = findCommandByColor(plan2, 0xFF0000FF);
        Assert.assertNotNull("应找到 B 的背景命令", bBgCmd2);
        Assert.assertEquals("B top 应顺移到 32", 32, bBgCmd2.getTop());

        // fragment 零重生成
        Assert.assertEquals("fragment 重生成数=0", 0, paintEngine.__getRegeneratedFragmentCount());
    }

    // ============================================================
    // 测试 8：geometryDirty 标记在 paint 遍历后被清除
    // ============================================================

    /**
     * layout 后位置变化节点被标记 geometryDirty，paint 遍历应清除之。
     */
    @Test
    public void shouldClearGeometryDirtyAfterPaint() {
        SceneNode root = new SceneNode();
        SceneNode a = new SceneNode();
        SceneNode b = new SceneNode();

        a.setText("A");
        a.setBackgroundColor(0xFFFF0000);
        b.setText("B");
        b.setBackgroundColor(0xFF0000FF);

        root.appendChild(a);
        root.appendChild(b);

        // 第一次 layout + paint：所有标记清空
        layoutEngine.layout(root, new Constraints(100));
        paintEngine.paint(root);
        Assert.assertFalse("初始 geometry 干净", b.__isSelfGeometryDirty());

        // A 变双行 → layout 标 geometry dirty
        a.setText("A\nX");
        layoutEngine.layout(root, new Constraints(100));
        Assert.assertTrue("layout 后 B geometryDirty=true",
                b.__isSelfGeometryDirty());

        // paint 遍历应清除 geometry dirty
        paintEngine.paint(root);
        Assert.assertFalse("paint 后 B selfGeometryDirty=false",
                b.__isSelfGeometryDirty());
        Assert.assertFalse("paint 后 root descendantGeometryDirty=false",
                root.__isDescendantGeometryDirty());
    }

    // ============================================================
    // 测试 9：BLOCK-1 回归锚点 —— 第二帧不改 signal 时 plan 完整且零重生成
    // ============================================================

    /**
     * 构建 root + container(背景+文本) + 子节点(背景+文本) 的树，
     * 首帧 layout+paint 后记录 plan.getCommands() 数量/坐标/颜色/顺序；
     * 第二帧不改任何 signal 直接再 paint，断言第二帧 plan 与首帧完全一致，
     * 且 __getRegeneratedFragmentCount()==0（零重生成但 plan 完整）。
     *
     * <p>该测试是 BLOCK-1 的永久锚点：修复前 paintNode 的"整棵跳过 return"
     * 导致第二帧所有子节点 fragment 丢失（plan 不完整），修复后 plan 完整且零重生成。</p>
     */
    @Test
    public void shouldProduceCompletePlanOnSecondFrameWithNoSignalChange() {
        // 构建树：root → container(带背景) → childA(背景+文本), childB(背景+文本)
        SceneNode root = new SceneNode();
        SceneNode container = new SceneNode();
        SceneNode childA = new SceneNode();
        SceneNode childB = new SceneNode();

        container.setBackgroundColor(0xFF223344);      // 深灰蓝
        container.setText("Container Label");
        childA.setBackgroundColor(0xFFFF0000);          // 红
        childA.setText("Child A");
        childB.setBackgroundColor(0xFF0000FF);          // 蓝
        childB.setText("Child B");

        container.appendChild(childA);
        container.appendChild(childB);
        root.appendChild(container);

        // 首帧：layout + paint
        layoutEngine.layout(root, new Constraints(200));
        PaintPlan plan1 = paintEngine.paint(root);
        List<PaintCommand> cmds1 = plan1.getCommands();

        // 记录首帧命令数量
        int count1 = cmds1.size();
        Assert.assertTrue("首帧应有命令", count1 > 0);

        // 第二帧：不改任何 signal，直接再 paint
        PaintPlan plan2 = paintEngine.paint(root);
        List<PaintCommand> cmds2 = plan2.getCommands();

        // BLOCK-1 锚点 1：第二帧命令数量与首帧相同
        Assert.assertEquals("第二帧计划命令数应与首帧一致", count1, cmds2.size());

        // BLOCK-1 锚点 2：每条命令坐标/颜色/文本/顺序一致
        for (int i = 0; i < count1; i++) {
            PaintCommand cmd1 = cmds1.get(i);
            PaintCommand cmd2 = cmds2.get(i);
            Assert.assertEquals("命令[" + i + "] 类型一致", cmd1.getType(), cmd2.getType());
            Assert.assertEquals("命令[" + i + "] left", cmd1.getLeft(), cmd2.getLeft());
            Assert.assertEquals("命令[" + i + "] top", cmd1.getTop(), cmd2.getTop());
            Assert.assertEquals("命令[" + i + "] right", cmd1.getRight(), cmd2.getRight());
            Assert.assertEquals("命令[" + i + "] bottom", cmd1.getBottom(), cmd2.getBottom());
            Assert.assertEquals("命令[" + i + "] color", cmd1.getColor(), cmd2.getColor());
            Assert.assertEquals("命令[" + i + "] text", cmd1.getText(), cmd2.getText());
        }

        // BLOCK-1 锚点 3：零重生成（I8 缓存命中，plan 完整）
        Assert.assertEquals("第二帧零重生成", 0, paintEngine.__getRegeneratedFragmentCount());
    }

    // ============================================================
    // 测试 10：replay offset 坐标叠加
    // ============================================================

    /**
     * 验证 ScenePaintReplayer.replay(plan, ctx, offsetX, offsetY) 在回放每条命令时
     * 将 offset 叠加到 BACKGROUND 和 TEXT 命令的坐标上。
     */
    @Test
    public void shouldApplyOffsetToReplayedCommands() {
        TestRenderContext testCtx;
        try {
            testCtx = new TestRenderContext();
        } catch (Exception e) {
            System.out.println("[ScenePaintEngineTest] 跳过 replay offset 测试："
                    + "UiRenderContext 构造失败 (" + e.getClass().getSimpleName() + ")");
            return;
        }

        // 手动构建 PaintPlan
        PaintPlan plan = new PaintPlan();
        plan.addCommand(PaintCommand.background(10, 20, 110, 70, 0xFF336699));
        PaintCommand textCmd = PaintCommand.text(5, 30, "Hello",
                new TextStyle(0xFFFFFFFF, 14));
        plan.addCommand(textCmd);

        // 带 offset 回放
        int offsetX = 100;
        int offsetY = 200;
        replayer.replay(plan, testCtx, offsetX, offsetY);

        List<String> calls = testCtx.getCalls();
        Assert.assertEquals("render 调用数", 2, calls.size());

        // 第 1 条 BACKGROUND：坐标应叠加 offset
        Assert.assertTrue("fillRect 应含 offsetX",
                calls.get(0).contains((10 + offsetX) + ","));
        Assert.assertTrue("fillRect 应含 offsetY",
                calls.get(0).contains("," + (20 + offsetY) + ","));

        // 第 2 条 TEXT：坐标应叠加 offset
        Assert.assertTrue("drawText 应含 offsetX",
                calls.get(1).contains((5 + offsetX) + ","));
        Assert.assertTrue("drawText 应含 offsetY",
                calls.get(1).contains("," + (30 + offsetY) + ","));
    }

    /**
     * 验证 replan(plan, ctx) 无参重载等价于 offset=(0,0)。
     */
    @Test
    public void shouldReplayWithZeroOffsetByDefault() {
        TestRenderContext testCtx;
        try {
            testCtx = new TestRenderContext();
        } catch (Exception e) {
            System.out.println("[ScenePaintEngineTest] 跳过 replay offset 默认测试："
                    + "UiRenderContext 构造失败 (" + e.getClass().getSimpleName() + ")");
            return;
        }

        PaintPlan plan = new PaintPlan();
        plan.addCommand(PaintCommand.background(10, 20, 50, 40, 0xFFFF0000));

        replayer.replay(plan, testCtx);
        List<String> calls = testCtx.getCalls();
        Assert.assertEquals("调用数=1", 1, calls.size());
        // 无参重载应等价于 offset=(0,0)，坐标不变
        Assert.assertTrue("坐标应保持不变", calls.get(0).contains("10,20,50,40"));
    }

    /**
     * 在 PaintPlan 中按颜色查找 BACKGROUND 命令。
     */
    private static PaintCommand findCommandByColor(PaintPlan plan, int color) {
        for (PaintCommand cmd : plan.getCommands()) {
            if (cmd.getType() == PaintCommandType.BACKGROUND && cmd.getColor() == color) {
                return cmd;
            }
        }
        return null;
    }

    // ============================================================
    // UiRenderContext 测试替身
    // ============================================================

    /**
     * UiRenderContext 的轻量测试替身。
     * 重写 fillRect / drawText，记录所有调用参数，不执行真实 OpenGL。
     */
    static class TestRenderContext extends UiRenderContext {

        private final List<String> calls = new ArrayList<>();

        TestRenderContext() {
            super(800, 600, 0, 0, 0f);
        }

        @Override
        public void fillRect(int left, int top, int right, int bottom, int color) {
            calls.add("fillRect(" + left + "," + top + "," + right + "," + bottom
                    + ",#" + Integer.toHexString(color) + ")");
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow) {
            calls.add("drawText(" + text + "," + x + "," + y
                    + ",#" + Integer.toHexString(color)
                    + (shadow ? ",shadow" : "") + ")");
        }

        List<String> getCalls() {
            return calls;
        }
    }
}
