package club.heiqi.uilib.ui.scene.testkit;

import org.junit.Assert;

import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintResult;
import club.heiqi.uilib.ui.scene.paint.RecordingRenderBackend;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.paint.ScenePaintReplayer;

/**
 * 场景渲染捕获 helper —— 渲染出口（layout → paint → replay）的统一捕获入口。
 *
 * <p>把 {@code SceneBackendContractTest.paintAndReplay} 私有方法提为 public static，
 * 供跨包测试复用：构造 layout/paint/replayer 链路 → 跑完 → 返回
 * {@link RecordingRenderBackend}（含全部 draw call 记录），调用方对其断言。</p>
 *
 * <h3>断言口径（与 hit-test 同）</h3>
 * <ul>
 *   <li>断「变换前 box + transform 分量分离」：box 顶点（{@code fillRect} 的 left/top/right/bottom）
 *       反映 layout 出来的几何，transform 仅出现在 {@code pushTransform} 的 7 个浮点分量里，
 *       二者分离不叠加。这是 scene 渲染管线的固有结构（见 ScenePaintEngine 方案甲）。</li>
 *   <li>不做「变换后顶点」断言：变换后的最终像素位置属 GPU 顶点层，由 GL 矩阵栈在 pushTransform
 *       作用域内实时算出，纯 JUnit mock backend 不可观测。该边界登记为偏离
 *       （见 docs/架构/测试体系约定.md §7 与偏离登记 2026-06-26-hit-test）。</li>
 * </ul>
 *
 * <h3>定位</h3>
 * <p>本类属 {@code testkit} 跨包搭台设施（无状态 static 工具）。区别于
 * {@code paint/} 包内的契约测试用例，本类只提供「跑通链路拿到记录」的能力，
 * 不含任何具体断言场景；断言由调用方按需做。</p>
 *
 * @see RecordingRenderBackend
 * @see ScenePaintEngine
 * @see ScenePaintReplayer
 */
public final class ScenePaintCapture {

    /** 工具类禁实例化 */
    private ScenePaintCapture() {
    }

    /**
     * 捕获渲染出口（正方形视口版）。
     *
     * <p>等价于 {@code paintAndCapture(root, viewportWidth, viewportWidth)}。
     * 正方形视口是测试最常见的场景（如 200x200、100x100）。</p>
     *
     * @param root          场景树根节点
     * @param viewportWidth 视口宽 == 高（像素）
     * @return 含全部 draw call 记录的 RecordingRenderBackend
     */
    public static RecordingRenderBackend paintAndCapture(SceneNode root, int viewportWidth) {
        return paintAndCapture(root, viewportWidth, viewportWidth);
    }

    /**
     * 捕获渲染出口：layout → paint → replay → 返回 RecordingRenderBackend。
     *
     * <p>内部用 {@link FixedTextMeasurer}（charWidth=8, lineHeight=16）构造 layout/paint 引擎，
     * 与 {@code SceneBackendContractTest} 等价。高度约束传 {@link Constraints#UNCONSTRAINED}
     * 时不限高度（与原 paintAndReplay 的 {@code new Constraints(200)} 行为一致）。</p>
     *
     * @param root           场景树根节点
     * @param viewportWidth  视口宽（像素），或 {@link Constraints#UNCONSTRAINED} 表示不限
     * @param viewportHeight 视口高（像素），或 {@link Constraints#UNCONSTRAINED} 表示不限
     * @return 含全部 draw call 记录的 RecordingRenderBackend
     */
    public static RecordingRenderBackend paintAndCapture(SceneNode root, int viewportWidth, int viewportHeight) {
        FixedTextMeasurer measurer = new FixedTextMeasurer();
        SceneLayoutEngine layoutEngine = new SceneLayoutEngine(measurer);
        ScenePaintEngine paintEngine = new ScenePaintEngine(measurer);
        ScenePaintReplayer replayer = new ScenePaintReplayer();

        layoutEngine.layout(root, new Constraints(viewportWidth, viewportHeight));
        PaintResult result = paintEngine.paint(root);
        PaintPlan plan = result.getPlan();
        RecordingRenderBackend backend = new RecordingRenderBackend();
        replayer.replay(plan, backend);
        return backend;
    }

    /**
     * 取首条 {@code fillRect} 调用记录，无则返回 null。
     *
     * @param backend 渲染记录
     * @return 首条 fillRect 调用，或 null
     */
    public static RecordingRenderBackend.RenderCall firstFill(RecordingRenderBackend backend) {
        for (RecordingRenderBackend.RenderCall c : backend.getCalls()) {
            if ("fillRect".equals(c.methodName())) {
                return c;
            }
        }
        return null;
    }

    /**
     * 断言 fillRect 调用的 4 个顶点坐标（left/top/right/bottom）。
     *
     * <p>颜色（第 5 参数）不在本断言内，由调用方按需用 {@code call.getInt(4)} 自行断言。</p>
     *
     * @param c      fillRect 调用记录
     * @param left   期望 left
     * @param top    期望 top
     * @param right  期望 right
     * @param bottom 期望 bottom
     */
    public static void assertFillBox(RecordingRenderBackend.RenderCall c,
            int left, int top, int right, int bottom) {
        Assert.assertNotNull("fillRect 调用不应为 null", c);
        Assert.assertEquals("fillRect left", left, c.getInt(0));
        Assert.assertEquals("fillRect top", top, c.getInt(1));
        Assert.assertEquals("fillRect right", right, c.getInt(2));
        Assert.assertEquals("fillRect bottom", bottom, c.getInt(3));
    }
}
