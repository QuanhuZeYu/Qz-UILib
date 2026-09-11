package club.heiqi.uilib.ui.scene.paint;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.Transform;

/**
 * P1-4 L3 守卫：裁剪窗口子树剔除（保守粒度：只剔除以 clip window 为根的整棵子树）。
 *
 * <p>剔除判据（三条同时成立）：① 本节点 {@code isClipWindow()}；② 无 transform；
 * ③ 本窗口绝对盒与「有效祖先裁剪矩形」无交集。本类把三条各自钉死，并给出两类反向守卫
 * （有 transform 不剔、回可见区必须重发射），避免「过度剔除 = 掉画面」。</p>
 *
 * <p>夹具口径：视口 = 高 40 的 clipChildren 容器；块 = clipChildren 容器 + 一个带识别色的
 * 后代（块本身就是 L3 的剔除单位）。块的绝对 y 由 COLUMN 顺序堆叠决定，视口外位置用 spacer 顶出。
 * 全部断言只用公开 {@code PaintPlan.getCommands()}，零新探针、不依赖 GL。</p>
 */
public class ScenePaintCullTest {

    /** 视口高（逻辑像素）。 */
    private static final int VIEWPORT_HEIGHT = 40;

    /** 块高（逻辑像素）。 */
    private static final int BLOCK_HEIGHT = 10;

    private static final int GREEN = 0xFF00FF00;
    private static final int BLUE = 0xFF0000FF;
    private static final int RED = 0xFFFF0000;

    private final FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
    private final SceneLayoutEngine layoutEngine = new SceneLayoutEngine(measurer);
    private final ScenePaintEngine paintEngine = new ScenePaintEngine(measurer);

    /** 布局 + 绘制一帧（paint 消费 cachedLayout，故夹具必须先布局）。 */
    private PaintResult paint(SceneNode root) {
        layoutEngine.layout(root, new Constraints(200, 100));
        return paintEngine.paint(root);
    }

    /** ① 视口外的 clip window 子树（含其后代全部命令）零发射；视口内的照常发射。 */
    @Test
    public void clipWindowSubtreeOutsideViewportEmitsNoCommands() {
        SceneNode root = new SceneNode();
        SceneNode viewport = viewport(root);
        block(viewport, GREEN);
        spacer(viewport, 200);
        SceneNode outside = block(viewport, RED);

        PaintPlan plan = paint(root).getPlan();

        Assert.assertNotEquals("视口内的裁剪块必须在计划内", -1, indexOfColor(plan, GREEN));
        Assert.assertEquals("视口外的裁剪块（含后代）必须零命令", -1, indexOfColor(plan, RED));
        Assert.assertNotNull("剔除不得影响布局结果", outside.getCachedLayout());
    }

    /** ① 反向：视口外块数 20 → 200，计划命令数不得增长（剔除的结构性收益）。 */
    @Test
    public void commandCountDoesNotGrowWithOffscreenClipBlocks() {
        PaintPlan small = paint(viewportWithOffscreenBlocks(20)).getPlan();
        PaintPlan large = paint(viewportWithOffscreenBlocks(200)).getPlan();

        Assert.assertEquals("视口外 clip 块规模不得影响命令数（剔除生效）",
                small.size(), large.size());
        Assert.assertTrue("计划必须仍有内容（可见块未被误剔）", small.size() > 0);
    }

    /** ② 反向：视口外子树带非恒等 transform 时不得剔除（图层回贴可能把它移回可见区）。 */
    @Test
    public void transformedOffscreenSubtreeIsNotCulled() {
        SceneNode root = new SceneNode();
        SceneNode viewport = viewport(root);
        spacer(viewport, 200);
        SceneNode offscreen = block(viewport, RED);
        offscreen.setTransform(Transform.translate(-300.0F, 0.0F));

        PaintResult result = paint(root);

        Assert.assertNotEquals("带 transform 的视口外子树必须继续下降（保守不剔除，宁多画不漏画）",
                -1, indexOfColor(result.getPlan(), RED));
        Assert.assertTrue("其片段必须真的生成过", result.getRegeneratedFragmentCount() > 0);
    }

    /** ③ CLIP_PUSH / CLIP_POP 严格配对，且被剔除的块不产生裁剪作用域。 */
    @Test
    public void clipPushPopStayPairedWithCulling() {
        SceneNode root = new SceneNode();
        SceneNode viewport = viewport(root);
        block(viewport, GREEN);
        spacer(viewport, 200);
        block(viewport, RED);
        spacer(viewport, 200);
        block(viewport, RED);

        List<PaintCommand> commands = paint(root).getPlan().getCommands();

        Assert.assertEquals("CLIP_PUSH 与 CLIP_POP 必须严格配对",
                countType(commands, PaintCommandType.CLIP_PUSH),
                countType(commands, PaintCommandType.CLIP_POP));
        Assert.assertEquals("只应有「视口 + 唯一可见块」两层裁剪", 2,
                countType(commands, PaintCommandType.CLIP_PUSH));
    }

    /** ④ 滚动改变可见集：滚出视口的块被剔除，滚入的块被发射。 */
    @Test
    public void scrollChangesCulledSet() {
        SceneNode root = new SceneNode();
        SceneNode viewport = viewport(root);
        block(viewport, GREEN);
        block(viewport, BLUE);

        PaintPlan before = paint(root).getPlan();
        Assert.assertNotEquals("第一块初始可见", -1, indexOfColor(before, GREEN));
        Assert.assertNotEquals("第二块初始可见", -1, indexOfColor(before, BLUE));

        viewport.setScrollOffsetY(BLOCK_HEIGHT);
        PaintPlan after = paint(root).getPlan();

        Assert.assertNotEquals("第二块滚入视口必须发射", -1, indexOfColor(after, BLUE));
        Assert.assertEquals("第一块滚出视口必须被剔除", -1, indexOfColor(after, GREEN));
        Assert.assertNotEquals("可见集变化必须改变命令集",
                before.getCommands().toString(), after.getCommands().toString());
    }

    /** ⑤ 被剔除期间仍脏的子树：回到可见区必须按新属性正确重发射并清脏。 */
    @Test
    public void culledDirtySubtreeEmitsCorrectCommandsWhenVisibleAgain() {
        SceneNode root = new SceneNode();
        SceneNode viewport = viewport(root);
        spacer(viewport, 200);
        SceneNode offscreen = block(viewport, RED);
        SceneNode inner = offscreen.__getChildren().get(0);

        PaintPlan culled = paint(root).getPlan();
        Assert.assertEquals("视口外子树零命令", -1, indexOfColor(culled, RED));
        Assert.assertTrue("剔除期间不得清脏（保守策略：回可见区才按脏标记重发射）",
                inner.__isSelfPaintDirty());

        inner.setBackgroundColor(0xFF11FF22);
        Assert.assertTrue("视口外修改属性后保持脏", inner.__isSelfPaintDirty());

        viewport.setScrollOffsetY(200);
        PaintPlan after = paint(root).getPlan();

        Assert.assertNotEquals("回到可见区必须按新属性重发射",
                -1, indexOfColor(after, 0xFF11FF22));
        Assert.assertFalse("重发射后脏标记必须被清除", inner.__isSelfPaintDirty());
    }

    /** 建带裁剪切口的视口（高 {@link #VIEWPORT_HEIGHT}）。 */
    private static SceneNode viewport(SceneNode root) {
        SceneNode viewport = SceneNode.column();
        viewport.setPreferredHeight(VIEWPORT_HEIGHT);
        viewport.setScrollable(true);        // 视口：滚动偏移生效，且 isClipWindow() 为真
        viewport.setClipChildren(true);
        root.appendChild(viewport);
        return viewport;
    }

    /** 视口内追加一个裁剪块（自身是 clip window + 一个识别色后代）。 */
    private static SceneNode block(SceneNode viewport, int color) {
        SceneNode block = SceneNode.column();
        block.setPreferredHeight(BLOCK_HEIGHT);
        block.setClipChildren(true);
        SceneNode inner = new SceneNode();
        inner.setPreferredHeight(BLOCK_HEIGHT);
        inner.setBackgroundColor(color);
        block.appendChild(inner);
        viewport.appendChild(block);
        return block;
    }

    /** 追加一个把后续内容顶出视口的占位块。 */
    private static void spacer(SceneNode viewport, int height) {
        SceneNode spacer = new SceneNode();
        spacer.setPreferredHeight(height);
        viewport.appendChild(spacer);
    }

    /** N 个视口外 clip 块 + 1 个可见块的对照夹具。 */
    private static SceneNode viewportWithOffscreenBlocks(int offscreenBlocks) {
        SceneNode root = new SceneNode();
        SceneNode viewport = viewport(root);
        block(viewport, GREEN);
        for (int i = 0; i < offscreenBlocks; i++) {
            spacer(viewport, 200);
            block(viewport, RED);
        }
        return root;
    }

    private static int countType(List<PaintCommand> commands, PaintCommandType type) {
        int count = 0;
        for (int i = 0; i < commands.size(); i++) {
            if (commands.get(i).getType() == type) {
                count++;
            }
        }
        return count;
    }

    private static int indexOfColor(PaintPlan plan, int color) {
        List<PaintCommand> commands = plan.getCommands();
        for (int i = 0; i < commands.size(); i++) {
            if (commands.get(i).getColor() == color) {
                return i;
            }
        }
        return -1;
    }
}
