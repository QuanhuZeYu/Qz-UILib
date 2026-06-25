package club.heiqi.uilib.ui.scene.paint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.Transform;

/**
 * C 任务反证测试 T1-T6 —— transform 完整矩阵 + 合成级动画（方案甲）命门验证。
 *
 * <h3>测试目标</h3>
 * <ul>
 *   <li>T1-T3：纯 transform 变化帧零重排 + 零 fragment 重建（信条五铁律）</li>
 *   <li>T4：阳性对照 —— 真实 paint/layout 脏变化时计数 &gt;0（防假阴性）</li>
 *   <li>T5：I6 静态守线 —— ScenePaintReplayer.java 源文件不含 Transform/UiTransform/SceneNode import</li>
 *   <li>T6：命令序列 —— 非恒等 transform 节点产出 PUSH_TRANSFORM/POP_TRANSFORM 配对且携带正确分量</li>
 * </ul>
 */
public class SceneTransformCompositeTest {

    private final FixedTextMeasurer measurer = new FixedTextMeasurer();
    private final SceneLayoutEngine layoutEngine = new SceneLayoutEngine(measurer);
    private final ScenePaintEngine paintEngine = new ScenePaintEngine(measurer);

    // ================================================================
    // T1：纯 rotate 变化帧 —— 零重排 + 零 fragment 重建
    // ================================================================

    /**
     * T1：setTransform(rotate) 只打 compositeDirty，绝不触发布局重排或 fragment 重建。
     */
    @Test
    public void t1_pureRotateShouldNotRelayoutNorRegenerate() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        child.setBackgroundColor(0xFF336699);
        root.appendChild(child);

        // 首帧：layout + paint 建立缓存
        layoutEngine.layout(root, new Constraints(100));
        paintEngine.paint(root);
        PaintFragment cachedFrag = (PaintFragment) child.getCachedPaint();
        Assert.assertNotNull("首帧应有缓存 fragment", cachedFrag);

        // 仅改 transform（只打 compositeDirty）
        child.setTransform(Transform.rotate(45f));

        layoutEngine.layout(root, new Constraints(100));
        paintEngine.paint(root);

        Assert.assertEquals("T1 纯 rotate 帧零重排", 0, layoutEngine.__getRelayoutCount());
        Assert.assertEquals("T1 纯 rotate 帧零 fragment 重建", 0,
                paintEngine.__getRegeneratedFragmentCount());
        Assert.assertSame("T1 fragment 引用不变（assertSame）", cachedFrag, child.getCachedPaint());
    }

    // ================================================================
    // T2：纯 scale 变化帧 —— 零重排 + 零 fragment 重建
    // ================================================================

    /**
     * T2：setTransform(scale) 只打 compositeDirty，绝不触发布局重排或 fragment 重建。
     */
    @Test
    public void t2_pureScaleShouldNotRelayoutNorRegenerate() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        child.setBackgroundColor(0xFFAA0033);
        root.appendChild(child);

        layoutEngine.layout(root, new Constraints(100));
        paintEngine.paint(root);
        PaintFragment cachedFrag = (PaintFragment) child.getCachedPaint();

        child.setTransform(Transform.scale(2f, 2f));

        layoutEngine.layout(root, new Constraints(100));
        paintEngine.paint(root);

        Assert.assertEquals("T2 纯 scale 帧零重排", 0, layoutEngine.__getRelayoutCount());
        Assert.assertEquals("T2 纯 scale 帧零 fragment 重建", 0,
                paintEngine.__getRegeneratedFragmentCount());
        Assert.assertSame("T2 fragment 引用不变", cachedFrag, child.getCachedPaint());
    }

    // ================================================================
    // T3：连续 60 帧不同 rotate 角度 —— 累计零重排 + 零重建
    // ================================================================

    /**
     * T3：模拟 60 帧旋转动画，每帧均无重排无 fragment 重建（信条五铁律大规模验证）。
     */
    @Test
    public void t3_sixty_framesRotateAnimationShouldNeverRegenerate() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        child.setBackgroundColor(0xFF00AA66);
        root.appendChild(child);

        layoutEngine.layout(root, new Constraints(100));
        paintEngine.paint(root);

        for (int i = 1; i <= 60; i++) {
            child.setTransform(Transform.rotate(i * 6f)); // 每帧转 6 度，60 帧一圈
            layoutEngine.layout(root, new Constraints(100));
            paintEngine.paint(root);

            Assert.assertEquals("T3 第 " + i + " 帧零重排", 0, layoutEngine.__getRelayoutCount());
            Assert.assertEquals("T3 第 " + i + " 帧零 fragment 重建", 0,
                    paintEngine.__getRegeneratedFragmentCount());
        }
    }

    // ================================================================
    // T4：阳性对照 —— 真实 paint/layout 脏变化时计数 > 0（防假阴性）
    // ================================================================

    /**
     * T4a：改 backgroundColor → regeneratedFragmentCount &gt;= 1（fragment 必须重建）。
     */
    @Test
    public void t4a_backgroundColorChangeShouldRegenerateFragment() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        child.setBackgroundColor(0xFF336699);
        root.appendChild(child);

        layoutEngine.layout(root, new Constraints(100));
        paintEngine.paint(root);

        // 改背景色 → 打 selfPaintDirty
        child.setBackgroundColor(0xFFFF0000);

        layoutEngine.layout(root, new Constraints(100));
        paintEngine.paint(root);

        Assert.assertTrue("T4a 改背景色 regeneratedFragmentCount>=1",
                paintEngine.__getRegeneratedFragmentCount() >= 1);
    }

    /**
     * T4b：改 width（setPreferredWidth）→ relayoutCount &gt;= 1（布局必须重排）。
     */
    @Test
    public void t4b_widthChangeShouldRelayout() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        child.setBackgroundColor(0xFF336699);
        root.appendChild(child);

        layoutEngine.layout(root, new Constraints(100));
        paintEngine.paint(root);

        // 改首选宽度 → 打 selfLayoutDirty
        child.setPreferredWidth(50);

        layoutEngine.layout(root, new Constraints(100));
        paintEngine.paint(root);

        Assert.assertTrue("T4b 改 width relayoutCount>=1",
                layoutEngine.__getRelayoutCount() >= 1);
    }

    // ================================================================
    // T5：I6 静态守线 —— ScenePaintReplayer.java 无 Transform import
    // ================================================================

    /**
     * T5：读取 ScenePaintReplayer.java 源文件，断言不含 Transform/UiTransform/SceneNode import 行。
     *
     * <p>这是 I6「replayer 零 scene/DOM 认知」的静态守线。精确匹配 import 语句行（行首 import ），
     * 避免注释中合法提及这些类名时误判。</p>
     */
    @Test
    public void t5_replayerShouldNotImportTransformOrSceneNode() throws IOException {
        String userDir = System.getProperty("user.dir");
        Path src = Paths.get(userDir, "src", "main", "java",
                "club", "heiqi", "uilib", "ui", "scene", "paint", "ScenePaintReplayer.java");
        List<String> lines = Files.readAllLines(src);

        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("import ")) {
                continue; // 只检查 import 行，注释/正文不检查
            }
            Assert.assertFalse("T5 replayer import 行不得含 Transform（scene.node）: " + trimmed,
                    trimmed.contains("Transform"));
            Assert.assertFalse("T5 replayer import 行不得含 UiTransform: " + trimmed,
                    trimmed.contains("UiTransform"));
            Assert.assertFalse("T5 replayer import 行不得含 SceneNode: " + trimmed,
                    trimmed.contains("SceneNode"));
        }
    }

    // ================================================================
    // T6：命令序列验证 —— PUSH_TRANSFORM/POP_TRANSFORM 正确产出
    // ================================================================

    /**
     * T6：非恒等 transform 节点 → PaintPlan 中应有 PUSH_TRANSFORM + POP_TRANSFORM，
     * 数量相等，PUSH 在 POP 前，且 PUSH 携带正确分量值。
     */
    @Test
    public void t6_nonIdentityTransformShouldEmitPushPopPair() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        child.setBackgroundColor(0xFF336699);
        child.setTransform(Transform.rotate(30f)); // 非恒等：rotate=30，scaleX=1，scaleY=1
        root.appendChild(child);

        layoutEngine.layout(root, new Constraints(100));
        PaintPlan plan = paintEngine.paint(root);
        List<PaintCommand> cmds = plan.getCommands();

        // 统计 PUSH/POP_TRANSFORM 个数及索引
        int pushCount = 0;
        int popCount = 0;
        int firstPushIdx = -1;
        int lastPopIdx = -1;
        PaintCommand pushCmd = null;
        for (int i = 0; i < cmds.size(); i++) {
            PaintCommand cmd = cmds.get(i);
            if (cmd.getType() == PaintCommandType.PUSH_TRANSFORM) {
                pushCount++;
                if (firstPushIdx < 0) {
                    firstPushIdx = i;
                    pushCmd = cmd;
                }
            } else if (cmd.getType() == PaintCommandType.POP_TRANSFORM) {
                popCount++;
                lastPopIdx = i;
            }
        }

        Assert.assertTrue("T6 应含 PUSH_TRANSFORM", pushCount > 0);
        Assert.assertTrue("T6 应含 POP_TRANSFORM", popCount > 0);
        Assert.assertEquals("T6 PUSH/POP 数量相等（配对）", pushCount, popCount);
        Assert.assertTrue("T6 PUSH 在 POP 之前", firstPushIdx < lastPopIdx);

        // PUSH 携带正确分量
        Assert.assertNotNull("T6 pushCmd 不为 null", pushCmd);
        Assert.assertEquals("T6 PUSH rotateDegrees==30f", 30f, pushCmd.getRotateDegrees(), 1e-6f);
        Assert.assertEquals("T6 PUSH scaleX==1f（默认恒等）", 1f, pushCmd.getScaleX(), 1e-6f);
        Assert.assertEquals("T6 PUSH scaleY==1f（默认恒等）", 1f, pushCmd.getScaleY(), 1e-6f);
        Assert.assertEquals("T6 PUSH translateX==0f", 0f, pushCmd.getTranslateX(), 1e-6f);
        Assert.assertEquals("T6 PUSH translateY==0f", 0f, pushCmd.getTranslateY(), 1e-6f);
    }

    /**
     * T6b：恒等 transform（isIdentity==true）→ 不产出 PUSH_TRANSFORM/POP_TRANSFORM。
     * <p>与 T6 互为镜像：恒等走快速路径，零矩阵压栈开销。</p>
     */
    @Test
    public void t6b_identityTransformShouldNotEmitPushPop() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        child.setBackgroundColor(0xFF336699);
        // 恒等变换（rotate=0, scale=1, translate=0）
        child.setTransform(new Transform(0f, 0f, 0f, 1f, 1f, 0.5f, 0.5f));
        root.appendChild(child);

        layoutEngine.layout(root, new Constraints(100));
        PaintPlan plan = paintEngine.paint(root);

        boolean hasPush = plan.getCommands().stream()
                .anyMatch(c -> c.getType() == PaintCommandType.PUSH_TRANSFORM);
        Assert.assertFalse("T6b 恒等 transform 不应产出 PUSH_TRANSFORM", hasPush);
    }

}
