package club.heiqi.uilib.ui.scene.paint;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.Assert;

import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.TextHorizontalAlign;
import club.heiqi.uilib.ui.scene.node.TextVerticalAlign;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.testkit.ScenePaintCapture;

/**
 * ScenePaintEngine + ScenePaintReplayer 单元测试。
 *
 * <p>核心验证：I8 缓存复用（干净兄弟 fragment 零重生成）、命令生成正确性、
 * Replayer 翻译到 UiRenderContext 的映射正确性。</p>
 */
public class ScenePaintEngineTest {

    private final FixedTextMeasurer measurer = new FixedTextMeasurer();
    private final SceneLayoutEngine layoutEngine = new SceneLayoutEngine(measurer);
    private final ScenePaintEngine paintEngine = new ScenePaintEngine(measurer);
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
        PaintPlan plan = paintEngine.paint(root).getPlan();

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
        // 文本节点高度等于行高，默认 CENTER 钳到 paddingTop=0，绝对坐标 = container 的 (0,0)
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
        PaintResult result = paintEngine.paint(root);

        // I8 铁证：A 和 C 的 fragment 引用不变（零重生成）
        PaintFragment fragA2 = (PaintFragment) a.getCachedPaint();
        PaintFragment fragC2 = (PaintFragment) c.getCachedPaint();

        Assert.assertSame("I8: A 的 fragment 应被复用（引用相同）", fragA1, fragA2);
        Assert.assertSame("I8: C 的 fragment 应被复用（引用相同）", fragC1, fragC2);

        // 验证只重新生成了 1 个 fragment（B）
        Assert.assertEquals("重新生成 fragment 数", 1, result.getRegeneratedFragmentCount());
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
        PaintResult result = paintEngine.paint(root);
        Assert.assertTrue("首次 paint 有生成", result.getRegeneratedFragmentCount() >= 1);

        // 第二次 paint：全树 clean，零重生成
        result = paintEngine.paint(root);
        Assert.assertEquals("第二次 paint 整棵跳过，零重生成", 0,
                result.getRegeneratedFragmentCount());
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
        PaintResult result = paintEngine.paint(root);

        // container 的 fragment 应复用（selfPaintDirty==false）
        PaintFragment containerFrag2 = (PaintFragment) container.getCachedPaint();
        Assert.assertSame("container fragment 引用不变", containerFrag1, containerFrag2);
        // 只 leaf 重新生成
        Assert.assertEquals("重新生成 fragment 数", 1, result.getRegeneratedFragmentCount());
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
        PaintPlan plan = paintEngine.paint(root).getPlan();
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
        Assert.assertTrue("第 2 条透传 TextStyle 字号", calls.get(1).contains("fontSize=14"));
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
        PaintPlan plan1 = paintEngine.paint(root).getPlan();

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
        PaintResult result = paintEngine.paint(root);
        PaintPlan plan2 = result.getPlan();

        // B1 铁证 1：B 的 fragment 引用不变（paint 属性没变 → 零重生成）
        PaintFragment bFrag2 = (PaintFragment) b.getCachedPaint();
        Assert.assertSame("B fragment 引用不变（零重生成）", bFrag1, bFrag2);

        // B1 铁证 2：B 命令的绝对坐标 top 已顺移 16→32
        PaintCommand bBgCmd2 = findCommandByColor(plan2, 0xFF0000FF);
        Assert.assertNotNull("应找到 B 的背景命令", bBgCmd2);
        Assert.assertEquals("B top 应顺移到 32", 32, bBgCmd2.getTop());

        // A 因 setText 标 selfPaint；container 和 root 因尺寸变化（layout 几何闸门）标 selfPaint
        // → A + container + root 共 3 次重生成（B 零重生成，container/root 虽重生成但为空 fragment）
        Assert.assertEquals("A + container + root 因尺寸变化重生成（B 零重生成）",
                3, result.getRegeneratedFragmentCount());
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

    @Test
    public void presentationOffsetMovesSubtreeCommandsInsideFixedViewportWithoutRepaint() {
        SceneNode root = SceneNode.column();
        SceneNode viewport = SceneNode.column();
        viewport.setScrollable(true);
        viewport.setPreferredHeight(100);
        SceneNode shell = SceneNode.column();
        shell.setOpacity(0.75f);
        SceneNode input = new SceneNode();
        input.setPreferredHeight(20);
        input.setBackgroundColor(0xFF123456);
        input.setClipChildren(true);
        shell.appendChild(input);
        viewport.appendChild(shell);
        root.appendChild(viewport);

        layoutEngine.layout(root, new Constraints(100, 100));
        viewport.setScrollOffsetY(5);
        paintEngine.paint(root);
        Object shellLayout = shell.getCachedLayout();
        Object inputLayout = input.getCachedLayout();
        Object inputFragment = input.getCachedPaint();
        Assert.assertEquals("输入框终态几何含 viewport scroll", -5,
                SceneGeometry.absoluteBox(input, 0, 0).getY());

        shell.__setPresentationOffsetY(-12);
        PaintResult moved = paintEngine.paint(root);
        PaintCommand inputBackground = findCommandByColor(moved.getPlan(), 0xFF123456);
        Assert.assertNotNull(inputBackground);
        Assert.assertEquals("输入框 fragment 与 presentation offset 同步移动", -17,
                inputBackground.getTop());

        List<PaintCommand> clips = new ArrayList<PaintCommand>();
        for (PaintCommand command : moved.getPlan().getCommands()) {
            if (command.getType() == PaintCommandType.CLIP_PUSH) {
                clips.add(command);
            }
        }
        Assert.assertEquals("viewport 与 input 各一个 clip", 2, clips.size());
        Assert.assertEquals("父 viewport clip 保持固定", 0, clips.get(0).getTop());
        Assert.assertEquals("后代 input clip 与 shell 一起移动", -17, clips.get(1).getTop());
        PaintCommand opacity = firstOfType(moved.getPlan().getCommands(), PaintCommandType.PUSH_OPACITY);
        Assert.assertNotNull(opacity);
        Assert.assertEquals("opacity bounds 使用移动后的绝对坐标", -17, opacity.getTop());
        Assert.assertEquals(0, countType(moved.getPlan().getCommands(), PaintCommandType.PUSH_TRANSFORM));
        Assert.assertEquals(0, countType(moved.getPlan().getCommands(), PaintCommandType.PUSH_TRANSFORM_LAYER));

        Assert.assertEquals("presentation offset 不重生成 fragment", 0,
                moved.getRegeneratedFragmentCount());
        Assert.assertSame("shell LayoutBox 保持终态", shellLayout, shell.getCachedLayout());
        Assert.assertSame("input LayoutBox 保持终态", inputLayout, input.getCachedLayout());
        Assert.assertSame("input fragment 继续复用", inputFragment, input.getCachedPaint());
        Assert.assertEquals("输入几何不读取 presentation offset", -5,
                SceneGeometry.absoluteBox(input, 0, 0).getY());
    }

    @Test
    public void presentationOffsetReachesRenderBackendCoordinates() {
        SceneNode root = SceneNode.column();
        SceneNode shell = SceneNode.column();
        SceneNode input = new SceneNode();
        input.setPreferredWidth(40);
        input.setPreferredHeight(20);
        input.setBackgroundColor(0xFF123456);
        input.setClipChildren(true);
        shell.appendChild(input);
        root.appendChild(shell);
        shell.__setPresentationOffsetY(-12);

        RecordingRenderBackend backend = ScenePaintCapture.paintAndCapture(root, 100, 100);
        RecordingRenderBackend.RenderCall fill = ScenePaintCapture.firstFill(backend);
        Assert.assertNotNull(fill);
        Assert.assertEquals("replay 出口顶点应包含 presentation offset", -12, fill.getInt(1));
        Assert.assertEquals(8, fill.getInt(3));
        RecordingRenderBackend.RenderCall clip = null;
        for (RecordingRenderBackend.RenderCall call : backend.getCalls()) {
            if ("pushClip".equals(call.methodName())) {
                clip = call;
                break;
            }
        }
        Assert.assertNotNull(clip);
        Assert.assertEquals("replay 出口 clip 应与内容同步移动", -12, clip.getInt(1));
        Assert.assertEquals(8, clip.getInt(3));
        Assert.assertFalse(backend.getMethodNames().contains("pushTransform"));
        Assert.assertFalse(backend.getMethodNames().contains("pushTransformLayer"));
    }

    // ============================================================
    // 测试 9：BLOCK-1 回归锚点 —— 第二帧不改 signal 时 plan 完整且零重生成
    // ============================================================

    /**
     * 构建 root + container(背景+文本) + 子节点(背景+文本) 的树，
     * 首帧 layout+paint 后记录 plan.getCommands() 数量/坐标/颜色/顺序；
     * 第二帧不改任何 signal 直接再 paint，断言第二帧 plan 与首帧完全一致，
     * 且 result.getRegeneratedFragmentCount()==0（零重生成但 plan 完整）。
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
        PaintPlan plan1 = paintEngine.paint(root).getPlan();
        List<PaintCommand> cmds1 = plan1.getCommands();

        // 记录首帧命令数量
        int count1 = cmds1.size();
        Assert.assertTrue("首帧应有命令", count1 > 0);

        // 第二帧：不改任何 signal，直接再 paint
        PaintResult result = paintEngine.paint(root);
        PaintPlan plan2 = result.getPlan();
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
        Assert.assertEquals("第二帧零重生成", 0, result.getRegeneratedFragmentCount());
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

    // ============================================================
    // Phase 4 任务 B+C：textColor 接入 + BORDER/CLIP 命令编排
    // ============================================================

    /**
     * 任务 C：TEXT 命令的 TextStyle.color 应读 node.getTextColor()（非写死白）。
     */
    @Test
    public void textCommandShouldUseNodeTextColor() {
        SceneNode root = new SceneNode();
        SceneNode textNode = new SceneNode();
        textNode.setText("Colored");
        textNode.setTextColor(0xFFFF8800); // 橙色
        root.appendChild(textNode);

        layoutEngine.layout(root, new Constraints(100));
        PaintPlan plan = paintEngine.paint(root).getPlan();

        PaintCommand textCmd = firstOfType(plan.getCommands(), PaintCommandType.TEXT);
        Assert.assertNotNull("应有 TEXT 命令", textCmd);
        Assert.assertEquals("TextStyle.color 应等于 node.getTextColor()",
                0xFFFF8800, textCmd.getTextStyle().getColor());
    }

    /**
     * 任务 C 零回归：未设 textColor 时默认白（0xFFFFFFFF）。
     */
    @Test
    public void textCommandShouldDefaultToWhiteColor() {
        SceneNode root = new SceneNode();
        SceneNode textNode = new SceneNode();
        textNode.setText("Default");
        root.appendChild(textNode);

        layoutEngine.layout(root, new Constraints(100));
        PaintPlan plan = paintEngine.paint(root).getPlan();

        PaintCommand textCmd = firstOfType(plan.getCommands(), PaintCommandType.TEXT);
        Assert.assertNotNull("应有 TEXT 命令", textCmd);
        Assert.assertEquals("默认文字色应为白", 0xFFFFFFFF, textCmd.getTextStyle().getColor());
    }

    /**
     * 偏离 1 解除：TEXT 命令 fontSize 读 node.getFontSize()，不再等于 box.height。
     *
     * <p>stub 行高固定 16（与字号无关），节点显式设 fontSize=14。修复前 paint 用
     * box.height（=16）当 fontSize，修复后应读 node.getFontSize()（=14），二者不再耦合。</p>
     */
    @Test
    public void textCommandFontSizeShouldReadNodeFontSizeNotBoxHeight() {
        SceneNode root = new SceneNode();
        SceneNode textNode = new SceneNode();
        textNode.setText("Hi");
        textNode.setFontSize(14);
        root.appendChild(textNode);

        layoutEngine.layout(root, new Constraints(100));
        PaintPlan plan = paintEngine.paint(root).getPlan();

        PaintCommand textCmd = firstOfType(plan.getCommands(), PaintCommandType.TEXT);
        Assert.assertNotNull("应有 TEXT 命令", textCmd);
        Assert.assertEquals("fontSize 应读 node.getFontSize()=14（非 box.height）",
                14, textCmd.getTextStyle().getFontSize());
        // box.height 在 stub 下恒为行高 16，证明 fontSize 与 box.height 已解耦
        LayoutBox box = (LayoutBox) textNode.getCachedLayout();
        Assert.assertEquals("box 高度=行高 16", 16, box.getHeight());
    }

    /**
     * 偏离 1 零回归：未显式设 fontSize 时默认 16。
     */
    @Test
    public void textCommandFontSizeShouldDefaultToSixteen() {
        SceneNode root = new SceneNode();
        SceneNode textNode = new SceneNode();
        textNode.setText("Hi");
        root.appendChild(textNode);

        layoutEngine.layout(root, new Constraints(100));
        PaintPlan plan = paintEngine.paint(root).getPlan();

        PaintCommand textCmd = firstOfType(plan.getCommands(), PaintCommandType.TEXT);
        Assert.assertNotNull("应有 TEXT 命令", textCmd);
        Assert.assertEquals("默认 fontSize=16", 16, textCmd.getTextStyle().getFontSize());
    }

    /**
     * 任务 B：borderWidth>0 时 fragment 含 BORDER 命令（用节点边框色/宽度/圆角）。
     */
    @Test
    public void borderWidthShouldEmitBorderCommandInFragment() {
        SceneNode root = new SceneNode();
        SceneNode node = new SceneNode();
        node.setBackgroundColor(0xFF112233);
        node.setBorderColor(0xFF00FF00);
        node.setBorderWidth(2);
        node.setCornerRadius(4);
        root.appendChild(node);

        layoutEngine.layout(root, new Constraints(100));
        PaintPlan plan = paintEngine.paint(root).getPlan();

        PaintCommand borderCmd = firstOfType(plan.getCommands(), PaintCommandType.BORDER);
        Assert.assertNotNull("borderWidth>0 应产出 BORDER 命令", borderCmd);
        Assert.assertEquals("BORDER 颜色", 0xFF00FF00, borderCmd.getColor());
        Assert.assertEquals("BORDER 宽度", 2, borderCmd.getBorderWidth());
        Assert.assertEquals("BORDER 圆角", 4, borderCmd.getCornerRadius());
    }

    /**
     * 任务 B 零回归：borderWidth==0 时不产出 BORDER 命令。
     */
    @Test
    public void zeroBorderWidthShouldNotEmitBorderCommand() {
        SceneNode root = new SceneNode();
        SceneNode node = new SceneNode();
        node.setBackgroundColor(0xFF112233);
        root.appendChild(node);

        layoutEngine.layout(root, new Constraints(100));
        PaintPlan plan = paintEngine.paint(root).getPlan();

        Assert.assertNull("borderWidth==0 不应产出 BORDER 命令",
                firstOfType(plan.getCommands(), PaintCommandType.BORDER));
    }

    /**
     * 任务 B：背景圆角>0 时 BACKGROUND 命令携带 cornerRadius。
     */
    @Test
    public void backgroundShouldCarryCornerRadius() {
        SceneNode root = new SceneNode();
        SceneNode node = new SceneNode();
        node.setBackgroundColor(0xFF112233);
        node.setCornerRadius(8);
        root.appendChild(node);

        layoutEngine.layout(root, new Constraints(100));
        PaintPlan plan = paintEngine.paint(root).getPlan();

        PaintCommand bg = firstOfType(plan.getCommands(), PaintCommandType.BACKGROUND);
        Assert.assertNotNull("应有 BACKGROUND 命令", bg);
        Assert.assertEquals("BACKGROUND 应携带圆角", 8, bg.getCornerRadius());
    }

    /**
     * 任务 B：clipChildren=true 时 paint plan 含 CLIP_PUSH/CLIP_POP 包裹子树，
     * 且严格嵌套——CLIP_PUSH 在子命令之前、CLIP_POP 在子命令之后。
     */
    @Test
    public void clipChildrenShouldWrapSubtreeWithClipPushPop() {
        SceneNode root = new SceneNode();
        SceneNode clipper = new SceneNode();
        SceneNode child = new SceneNode();

        clipper.setBackgroundColor(0xFF112233);
        clipper.setClipChildren(true);
        clipper.setCornerRadius(6);
        child.setBackgroundColor(0xFFAABBCC);

        clipper.appendChild(child);
        root.appendChild(clipper);

        layoutEngine.layout(root, new Constraints(100));
        PaintPlan plan = paintEngine.paint(root).getPlan();

        List<PaintCommand> cmds = plan.getCommands();
        int pushIdx = indexOfType(cmds, PaintCommandType.CLIP_PUSH);
        int popIdx = indexOfType(cmds, PaintCommandType.CLIP_POP);
        int childBgIdx = lastIndexOfBgColor(cmds, 0xFFAABBCC);

        Assert.assertTrue("应含 CLIP_PUSH", pushIdx >= 0);
        Assert.assertTrue("应含 CLIP_POP", popIdx >= 0);
        Assert.assertTrue("CLIP_PUSH 在子命令之前", pushIdx < childBgIdx);
        Assert.assertTrue("CLIP_POP 在子命令之后", popIdx > childBgIdx);
        // CLIP_PUSH 携带节点圆角
        Assert.assertEquals("CLIP_PUSH 携带圆角", 6, cmds.get(pushIdx).getCornerRadius());
    }

    /**
     * 任务 B 零回归：clipChildren=false 时不产出任何 CLIP 命令。
     */
    @Test
    public void noClipWhenClipChildrenFalse() {
        SceneNode root = new SceneNode();
        SceneNode node = new SceneNode();
        node.setBackgroundColor(0xFF112233);
        root.appendChild(node);

        layoutEngine.layout(root, new Constraints(100));
        PaintPlan plan = paintEngine.paint(root).getPlan();

        Assert.assertEquals("不应有 CLIP_PUSH", 0, countType(plan.getCommands(), PaintCommandType.CLIP_PUSH));
        Assert.assertEquals("不应有 CLIP_POP", 0, countType(plan.getCommands(), PaintCommandType.CLIP_POP));
    }

    /**
     * 任务 B：纯 paint 变化（改背景色）时 fragment 复用，BORDER/圆角随 fragment 复用不重建。
     */
    @Test
    public void borderAndCornerShouldReuseFragmentOnPureGeometryFrame() {
        SceneNode root = new SceneNode();
        SceneNode node = new SceneNode();
        node.setBackgroundColor(0xFF112233);
        node.setBorderColor(0xFF00FF00);
        node.setBorderWidth(2);
        node.setCornerRadius(4);
        SceneNode sibling = new SceneNode();
        sibling.setBackgroundColor(0xFF999999);
        root.appendChild(node);
        root.appendChild(sibling);

        layoutEngine.layout(root, new Constraints(100));
        paintEngine.paint(root);
        PaintFragment frag1 = (PaintFragment) node.getCachedPaint();
        Assert.assertNotNull("node 应有 fragment", frag1);

        // 改 sibling 背景色（node 保持 paint 干净）
        sibling.setBackgroundColor(0xFF777777);
        layoutEngine.layout(root, new Constraints(100));
        paintEngine.paint(root);

        // node（含 BORDER/圆角）fragment 引用不变
        Assert.assertSame("含 border/圆角的 fragment 应复用不重建",
                frag1, node.getCachedPaint());
    }

    // ============================================================
    // Phase 4 任务 B+C：Replayer BORDER/CLIP 翻译正确性
    // ============================================================

    /**
     * 任务 B：BORDER 命令 cornerRadius==0 → 走 drawBorder。
     */
    @Test
    public void replayBorderWithoutCornerShouldCallDrawBorder() {
        TestRenderContext testCtx;
        try {
            testCtx = new TestRenderContext();
        } catch (Exception e) {
            System.out.println("[ScenePaintEngineTest] 跳过 replay border 测试：" + e.getClass().getSimpleName());
            return;
        }

        PaintPlan plan = new PaintPlan();
        plan.addCommand(PaintCommand.border(0, 0, 50, 30, 0xFF00FF00, 2, 0));
        replayer.replay(plan, testCtx);

        List<String> calls = testCtx.getCalls();
        Assert.assertEquals("调用数=1", 1, calls.size());
        Assert.assertTrue("应调 drawBorder", calls.get(0).startsWith("drawBorder"));
        Assert.assertTrue("含边框色", calls.get(0).contains("#ff00ff00"));
    }

    /**
     * 任务 B 关键边界：BORDER 命令 cornerRadius>0 → 走 drawSurface，且 fillColor 传 0 只画边框不填充。
     */
    @Test
    public void replayRoundedBorderShouldCallDrawSurfaceWithZeroFill() {
        TestRenderContext testCtx;
        try {
            testCtx = new TestRenderContext();
        } catch (Exception e) {
            System.out.println("[ScenePaintEngineTest] 跳过 replay 圆角边框测试：" + e.getClass().getSimpleName());
            return;
        }

        PaintPlan plan = new PaintPlan();
        plan.addCommand(PaintCommand.border(0, 0, 50, 30, 0xFF00FF00, 2, 6));
        replayer.replay(plan, testCtx);

        List<String> calls = testCtx.getCalls();
        Assert.assertEquals("调用数=1", 1, calls.size());
        Assert.assertTrue("圆角边框应走 drawSurface", calls.get(0).startsWith("drawSurface"));
        // 关键：fillColor=0（只画边框不填充），borderColor=边框色
        Assert.assertTrue("drawSurface fillColor 应为 0", calls.get(0).contains("fill=#0"));
        Assert.assertTrue("drawSurface borderColor 应为边框色", calls.get(0).contains("border=#ff00ff00"));
    }

    /**
     * 任务 B：BACKGROUND 命令 cornerRadius>0 → 走 drawSurface 填充。
     */
    @Test
    public void replayRoundedBackgroundShouldCallDrawSurfaceWithFill() {
        TestRenderContext testCtx;
        try {
            testCtx = new TestRenderContext();
        } catch (Exception e) {
            System.out.println("[ScenePaintEngineTest] 跳过 replay 圆角背景测试：" + e.getClass().getSimpleName());
            return;
        }

        PaintPlan plan = new PaintPlan();
        plan.addCommand(PaintCommand.background(0, 0, 50, 30, 0xFF112233, 8));
        replayer.replay(plan, testCtx);

        List<String> calls = testCtx.getCalls();
        Assert.assertEquals("调用数=1", 1, calls.size());
        Assert.assertTrue("圆角背景应走 drawSurface", calls.get(0).startsWith("drawSurface"));
        Assert.assertTrue("drawSurface fillColor 应为背景色", calls.get(0).contains("fill=#ff112233"));
        Assert.assertTrue("drawSurface borderColor 应为 0", calls.get(0).contains("border=#0"));
    }

    /**
     * 任务 B 零回归：BACKGROUND cornerRadius==0 → 仍走 fillRect。
     */
    @Test
    public void replaySquareBackgroundShouldCallFillRect() {
        TestRenderContext testCtx;
        try {
            testCtx = new TestRenderContext();
        } catch (Exception e) {
            System.out.println("[ScenePaintEngineTest] 跳过 replay 直角背景测试：" + e.getClass().getSimpleName());
            return;
        }

        PaintPlan plan = new PaintPlan();
        plan.addCommand(PaintCommand.background(0, 0, 50, 30, 0xFF112233));
        replayer.replay(plan, testCtx);

        List<String> calls = testCtx.getCalls();
        Assert.assertEquals("调用数=1", 1, calls.size());
        Assert.assertTrue("直角背景应走 fillRect", calls.get(0).startsWith("fillRect"));
    }

    /**
     * 任务 B：CLIP_PUSH → pushClip(叠加 offset)，CLIP_POP → popClip。
     */
    @Test
    public void replayClipCommandsShouldCallPushPopClip() {
        TestRenderContext testCtx;
        try {
            testCtx = new TestRenderContext();
        } catch (Exception e) {
            System.out.println("[ScenePaintEngineTest] 跳过 replay clip 测试：" + e.getClass().getSimpleName());
            return;
        }

        PaintPlan plan = new PaintPlan();
        plan.addClipPush(10, 20, 60, 50, 4);
        plan.addCommand(PaintCommand.background(10, 20, 60, 50, 0xFF112233));
        plan.addClipPop();
        replayer.replay(plan, testCtx);

        List<String> calls = testCtx.getCalls();
        Assert.assertEquals("调用数=3", 3, calls.size());
        Assert.assertTrue("第 1 条应为 pushClip", calls.get(0).startsWith("pushClip"));
        Assert.assertTrue("pushClip 含圆角 r=4", calls.get(0).contains("r=4"));
        Assert.assertTrue("第 3 条应为 popClip", calls.get(2).startsWith("popClip"));
    }

    /**
     * 任务 B：CLIP_PUSH 回放时坐标叠加 offset。
     */
    @Test
    public void replayClipPushShouldApplyOffset() {
        TestRenderContext testCtx;
        try {
            testCtx = new TestRenderContext();
        } catch (Exception e) {
            System.out.println("[ScenePaintEngineTest] 跳过 replay clip offset 测试：" + e.getClass().getSimpleName());
            return;
        }

        PaintPlan plan = new PaintPlan();
        plan.addClipPush(10, 20, 60, 50, 0);
        replayer.replay(plan, testCtx, 100, 200);

        List<String> calls = testCtx.getCalls();
        Assert.assertEquals("调用数=1", 1, calls.size());
        Assert.assertTrue("pushClip 坐标应叠加 offset",
                calls.get(0).contains("pushClip(110,220,160,250"));
    }

    /**
     * B6：paint 下沉式与 absoluteBox 回溯式独立计算，但单层滚动注入原子保持一致。
     */
    @Test
    public void paintAndAbsoluteBoxShouldUseSameScrollOffsetAtom() {
        SceneNode root = new SceneNode();
        SceneNode viewport = new SceneNode();
        SceneNode content = new SceneNode();
        content.setBackgroundColor(0xFFAA5500);
        root.appendChild(viewport);
        viewport.appendChild(content);

        root.setCachedLayout(new LayoutBox(0, 0, 200, 200));
        viewport.setCachedLayout(new LayoutBox(5, 10, 100, 80));
        content.setCachedLayout(new LayoutBox(0, 50, 60, 20));
        viewport.setScrollable(true);
        viewport.setScrollOffsetY(35);

        PaintPlan plan = paintEngine.paint(root).getPlan();
        PaintCommand background = findCommandByColor(plan, 0xFFAA5500);
        AnchorRect absolute = SceneGeometry.absoluteBox(content, 0, 0);

        Assert.assertNotNull("content 应产出背景命令", background);
        Assert.assertEquals("paint 下沉式 Y 应等于 absoluteBox 回溯式 Y",
                absolute.getY(), background.getTop());
        Assert.assertEquals("单层原子应减父 scrollOffsetY", 25, absolute.getY());
    }

    // ============================================================
    // 文本垂直对齐（PAINT 级）
    // ============================================================

    /**
     * 文本垂直对齐 TOP：em-box 顶贴 paddingTop（不留 leading）。
     * <p>fontSize=20 显式 ≠ measurer.lineHeight(16)，使 em-box 模型（用 fontSize）
     * 与旧 half-leading 模型（用 lineHeight）期望值分叉，避免坐标系基准差被测试桩掩盖。</p>
     */
    @Test
    public void textVerticalAlignTopShouldUsePaddingTop() {
        PaintCommand textCmd = paintTextWithAlign(TextVerticalAlign.TOP, 20, 40, 4, 6);

        Assert.assertEquals("TOP textTop=paddingTop", 4, textCmd.getTop());
    }

    /**
     * 文本垂直对齐 CENTER：em-box（高=fontSize）在内高内居中。
     * <p>inner=30、emHeight=20 → 4+(30-20)/2=9。旧 half-leading 模型在 emHeight≠lineHeight
     * 时会得 11，本用例据此区分新旧模型。</p>
     */
    @Test
    public void textVerticalAlignCenterShouldCenterInInnerHeight() {
        PaintCommand textCmd = paintTextWithAlign(TextVerticalAlign.CENTER, 20, 40, 4, 6);

        Assert.assertEquals("CENTER textTop=paddingTop+(innerHeight-emHeight)/2", 9, textCmd.getTop());
    }

    /**
     * 文本垂直对齐 BOTTOM：em-box 底贴内高底部。
     * <p>inner=30、emHeight=20 → 4+(30-20)=14。旧模型得 18，本用例据此区分新旧模型。</p>
     */
    @Test
    public void textVerticalAlignBottomShouldUseInnerBottom() {
        PaintCommand textCmd = paintTextWithAlign(TextVerticalAlign.BOTTOM, 20, 40, 4, 6);

        Assert.assertEquals("BOTTOM textTop=paddingTop+(innerHeight-emHeight)", 14, textCmd.getTop());
    }

    /**
     * 盒高小于 em-box 高时，em-box 居中模型允许向上溢出（CSS overflow:visible 合法行为），
     * 不做下边界钳制：CENTER/BOTTOM 可得负偏移，TOP 仍贴 paddingTop。
     */
    @Test
    public void textVerticalAlignShouldAllowOverflowWhenEmBoxOverflows() {
        Assert.assertEquals("TOP 仍贴 paddingTop", 5,
                paintTextWithAlign(TextVerticalAlign.TOP, 20, 20, 5, 3).getTop());
        Assert.assertEquals("CENTER 向上溢出", 1,
                paintTextWithAlign(TextVerticalAlign.CENTER, 20, 20, 5, 3).getTop());
        Assert.assertEquals("BOTTOM 向上溢出", -3,
                paintTextWithAlign(TextVerticalAlign.BOTTOM, 20, 20, 5, 3).getTop());
    }

    /**
     * 默认文本垂直对齐为 CENTER，不显式设置 align 时按 em-box 自动居中。
     */
    @Test
    public void textVerticalAlignShouldDefaultToCenter() {
        SceneNode node = new SceneNode();
        node.setText("Default Center");
        node.setFontSize(20);
        node.setPadding(4, 0, 6, 0);
        node.setCachedLayout(new LayoutBox(0, 0, 100, 40));

        PaintPlan plan = paintEngine.paint(node).getPlan();
        PaintCommand textCmd = firstOfType(plan.getCommands(), PaintCommandType.TEXT);

        Assert.assertNotNull("应有 TEXT 命令", textCmd);
        Assert.assertEquals("默认 CENTER", TextVerticalAlign.CENTER, node.getTextVerticalAlign());
        Assert.assertEquals("默认 CENTER textTop", 9, textCmd.getTop());
    }

    // ============================================================
    // 文本水平对齐（PAINT 级）
    // ============================================================

    /**
     * 文本水平对齐 LEFT：盒宽大于文本宽时，文本贴 paddingLeft。
     */
    @Test
    public void textHorizontalAlignLeftShouldUsePaddingLeft() {
        PaintCommand textCmd = paintTextWithHorizontalAlign(TextHorizontalAlign.LEFT, 100, 4, 6, "Align");

        Assert.assertEquals("LEFT textLeft=paddingLeft", 4, textCmd.getLeft());
    }

    /**
     * 文本水平对齐 CENTER：盒宽大于文本宽时，文本在内宽内居中。
     */
    @Test
    public void textHorizontalAlignCenterShouldCenterInInnerWidth() {
        PaintCommand textCmd = paintTextWithHorizontalAlign(TextHorizontalAlign.CENTER, 100, 4, 6, "Align");

        Assert.assertEquals("CENTER textLeft=paddingLeft+(innerWidth-textWidth)/2", 29, textCmd.getLeft());
    }

    /**
     * 文本水平对齐 RIGHT：盒宽大于文本宽时，文本贴内宽右侧。
     */
    @Test
    public void textHorizontalAlignRightShouldUseInnerRight() {
        PaintCommand textCmd = paintTextWithHorizontalAlign(TextHorizontalAlign.RIGHT, 100, 4, 6, "Align");

        Assert.assertEquals("RIGHT textLeft=paddingLeft+innerWidth-textWidth", 54, textCmd.getLeft());
    }

    /**
     * 文本宽大于等于内宽时，三种对齐都钳到 paddingLeft，避免向左溢出。
     */
    @Test
    public void textHorizontalAlignShouldClampToPaddingLeftWhenTextWidthOverflows() {
        Assert.assertEquals("LEFT 钳到 paddingLeft", 5,
                paintTextWithHorizontalAlign(TextHorizontalAlign.LEFT, 40, 5, 3, "Overflow").getLeft());
        Assert.assertEquals("CENTER 钳到 paddingLeft", 5,
                paintTextWithHorizontalAlign(TextHorizontalAlign.CENTER, 40, 5, 3, "Overflow").getLeft());
        Assert.assertEquals("RIGHT 钳到 paddingLeft", 5,
                paintTextWithHorizontalAlign(TextHorizontalAlign.RIGHT, 40, 5, 3, "Overflow").getLeft());
    }

    /**
     * 默认文本水平对齐为 LEFT，不显式设置 align 时保持贴左。
     */
    @Test
    public void textHorizontalAlignShouldDefaultToLeft() {
        SceneNode node = new SceneNode();
        node.setText("Default Left");
        node.setPadding(0, 6, 0, 4);
        node.setCachedLayout(new LayoutBox(0, 0, 100, 16));

        PaintPlan plan = paintEngine.paint(node).getPlan();
        PaintCommand textCmd = firstOfType(plan.getCommands(), PaintCommandType.TEXT);

        Assert.assertNotNull("应有 TEXT 命令", textCmd);
        Assert.assertEquals("默认 LEFT", TextHorizontalAlign.LEFT, node.getTextHorizontalAlign());
        Assert.assertEquals("默认 LEFT textLeft", 4, textCmd.getLeft());
    }

    // ============================================================
    // 辅助方法（Phase 4 新增）
    // ============================================================

    private static PaintCommand firstOfType(List<PaintCommand> cmds, PaintCommandType type) {
        for (PaintCommand cmd : cmds) {
            if (cmd.getType() == type) {
                return cmd;
            }
        }
        return null;
    }

    /**
     * 构造单文本节点并返回 TEXT 命令，用于文本垂直对齐断言。
     */
    private PaintCommand paintTextWithAlign(TextVerticalAlign align, int fontSize, int boxHeight, int paddingTop,
            int paddingBottom) {
        SceneNode node = new SceneNode();
        node.setText("Align");
        node.setFontSize(fontSize);
        node.setTextVerticalAlign(align);
        node.setPadding(paddingTop, 0, paddingBottom, 0);
        node.setCachedLayout(new LayoutBox(0, 0, 100, boxHeight));

        PaintPlan plan = paintEngine.paint(node).getPlan();
        PaintCommand textCmd = firstOfType(plan.getCommands(), PaintCommandType.TEXT);
        Assert.assertNotNull("应有 TEXT 命令", textCmd);
        return textCmd;
    }

    /**
     * 构造单文本节点并返回 TEXT 命令，用于文本水平对齐断言。
     */
    private PaintCommand paintTextWithHorizontalAlign(TextHorizontalAlign align, int boxWidth, int paddingLeft,
            int paddingRight, String text) {
        SceneNode node = new SceneNode();
        node.setText(text);
        node.setTextHorizontalAlign(align);
        node.setPadding(0, paddingRight, 0, paddingLeft);
        node.setCachedLayout(new LayoutBox(0, 0, boxWidth, 16));

        PaintPlan plan = paintEngine.paint(node).getPlan();
        PaintCommand textCmd = firstOfType(plan.getCommands(), PaintCommandType.TEXT);
        Assert.assertNotNull("应有 TEXT 命令", textCmd);
        return textCmd;
    }

    private static int indexOfType(List<PaintCommand> cmds, PaintCommandType type) {
        for (int i = 0; i < cmds.size(); i++) {
            if (cmds.get(i).getType() == type) {
                return i;
            }
        }
        return -1;
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

    private static int lastIndexOfBgColor(List<PaintCommand> cmds, int color) {
        for (int i = cmds.size() - 1; i >= 0; i--) {
            if (cmds.get(i).getType() == PaintCommandType.BACKGROUND && cmds.get(i).getColor() == color) {
                return i;
            }
        }
        return -1;
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
        public void publishTextDemand(List<String> texts) {
            // 该 fake 只验证 replay 绘制命令；字体 demand 顺序由独立 backend 合同测试覆盖。
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

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow, int fontSizePx) {
            calls.add("drawText(" + text + "," + x + "," + y
                    + ",#" + Integer.toHexString(color)
                    + (shadow ? ",shadow" : "")
                    + ",fontSize=" + fontSizePx + ")");
        }

        @Override
        public void drawBorder(int left, int top, int right, int bottom, int color) {
            calls.add("drawBorder(" + left + "," + top + "," + right + "," + bottom
                    + ",#" + Integer.toHexString(color) + ")");
        }

        @Override
        public void drawSurface(int left, int top, int right, int bottom, int fillColor, int borderColor,
                int cornerRadius) {
            calls.add("drawSurface(" + left + "," + top + "," + right + "," + bottom
                    + ",fill=#" + Integer.toHexString(fillColor)
                    + ",border=#" + Integer.toHexString(borderColor) + ")");
        }

        @Override
        public void pushClip(int left, int top, int right, int bottom, int cornerRadius) {
            calls.add("pushClip(" + left + "," + top + "," + right + "," + bottom
                    + ",r=" + cornerRadius + ")");
        }

        @Override
        public void popClip() {
            calls.add("popClip()");
        }

        List<String> getCalls() {
            return calls;
        }
    }
}
