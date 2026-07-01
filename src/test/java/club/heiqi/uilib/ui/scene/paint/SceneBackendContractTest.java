package club.heiqi.uilib.ui.scene.paint;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.Transform;
import club.heiqi.uilib.ui.scene.testkit.ScenePaintCapture;

/**
 * scene 渲染出口契约端到端测试 —— 锁定「{@link UiRenderBackend} 契约层是视觉意图原语，
 * 跨后端通用」的承诺（宪章信条六）。
 *
 * <p>用 {@link ScenePaintEngine} 产 PaintPlan → {@link ScenePaintReplayer#replay} 回放到
 * {@link RecordingRenderBackend}（零 GL 能力的纯 mock）→ 断言调用序列。若有人偷偷在
 * replayer 里依赖 {@code UiRenderContext} 特有方法或 GL 副作用，本测试无法通过——
 * mock backend 没有任何 GL 能力，replayer 只能纯靠接口方法工作。</p>
 *
 * <h3>测试锚点价值</h3>
 * <ul>
 *   <li>现有 {@code ScenePaintCompositeReplayTest} 只在 PaintPlan 命令流层面断言（不跑
 *       replayer）；本测试把链路延伸到 replayer → UiRenderBackend，覆盖「命令→接口调用」
 *       翻译这一环。</li>
 *   <li>现有 {@code SceneOverlayPipelineTest.RecordingBackend} 是 private inner class、
 *       只记调用名不记参数；本测试用公共 {@link RecordingRenderBackend} 记完整参数，
 *       可断言 pushGroupOpacity 的 opacity、pushTransform 的 translate 分量等。</li>
 * </ul>
 */
public class SceneBackendContractTest {

    /** UiRenderBackend 接口方法名白名单（场景 7 契约断言用） */
    private static final Set<String> INTERFACE_METHODS = new HashSet<String>(Arrays.asList(
            "fillRect", "drawSurface", "drawBorder",
            "pushClip", "popClip",
            "drawText",
            "pushGroupOpacity", "popGroupOpacity",
            "pushTransform", "popTransform",
            "pushTransformLayer", "popTransformLayer"));

    // ============================================================
    // 场景 1：纯背景节点
    // ============================================================

    /**
     * 树：root → child（背景色 0xFF336699）。
     * 断言：calls 含 fillRect(0,0,100,50,0xFF336699)；不含 pushGroupOpacity/pushTransform/pushClip。
     */
    @Test
    public void plainBackgroundNodeShouldEmitOnlyFillRect() {
        SceneNode root = newNode(100, 50);
        SceneNode child = newNode(100, 50);
        child.setBackgroundColor(0xFF336699);
        root.appendChild(child);

        RecordingRenderBackend backend = paintAndReplay(root);

        // 含一条 fillRect 且颜色正确、坐标从 (0,0) 起
        RecordingRenderBackend.RenderCall fill = findCall(backend, "fillRect");
        Assert.assertNotNull("应含 fillRect 调用", fill);
        Assert.assertEquals("fillRect left==0", 0, fill.getInt(0));
        Assert.assertEquals("fillRect top==0", 0, fill.getInt(1));
        Assert.assertEquals("fillRect right==100", 100, fill.getInt(2));
        Assert.assertEquals("fillRect bottom==50", 50, fill.getInt(3));
        Assert.assertEquals("fillRect color==0xFF336699", 0xFF336699, fill.getInt(4));

        // 不含任何合成/裁剪边界
        assertNoCalls(backend, "pushGroupOpacity", "popGroupOpacity",
                "pushTransform", "popTransform", "pushTransformLayer", "popTransformLayer",
                "pushClip", "popClip");
    }

    // ============================================================
    // 场景 2：opacity 节点
    // ============================================================

    /**
     * 树：root → child（背景色 + opacity=0.5）。
     * 断言：calls 序列 == [pushGroupOpacity, fillRect, popGroupOpacity]；
     * pushGroupOpacity 的 opacity 参数 == 0.5。
     */
    @Test
    public void opacityNodeShouldEmitGroupBoundary() {
        SceneNode root = newNode(100, 50);
        SceneNode child = newNode(100, 50);
        child.setBackgroundColor(0xFF336699);
        child.setOpacity(0.5f);
        root.appendChild(child);

        RecordingRenderBackend backend = paintAndReplay(root);

        List<String> names = backend.getMethodNames();
        Assert.assertEquals("调用序列应为 [pushGroupOpacity, fillRect, popGroupOpacity]",
                Arrays.asList("pushGroupOpacity", "fillRect", "popGroupOpacity"), names);

        // pushGroupOpacity 第 5 个参数（index 4）== opacity
        RecordingRenderBackend.RenderCall push = backend.getCall(0);
        Assert.assertEquals("pushGroupOpacity opacity==0.5", 0.5f, push.getFloat(4), 1e-6f);
    }

    // ============================================================
    // 场景 3：transform 节点
    // ============================================================

    /**
     * 树：root → child（背景色 + Transform(30f, 40f)）。
     * 断言：calls 序列 == [pushTransform, fillRect, popTransform]；
     * pushTransform 的 translateX/Y == 30f/40f。
     */
    @Test
    public void transformNodeShouldEmitTransformBoundary() {
        SceneNode root = newNode(100, 50);
        SceneNode child = newNode(100, 50);
        child.setBackgroundColor(0xFF336699);
        child.setTransform(new Transform(30f, 40f));
        root.appendChild(child);

        RecordingRenderBackend backend = paintAndReplay(root);

        List<String> names = backend.getMethodNames();
        Assert.assertEquals("调用序列应为 [pushTransform, fillRect, popTransform]",
                Arrays.asList("pushTransform", "fillRect", "popTransform"), names);

        // pushTransform 参数顺序：translateX(0), translateY(1), ...
        RecordingRenderBackend.RenderCall push = backend.getCall(0);
        Assert.assertEquals("pushTransform translateX==30f", 30f, push.getFloat(0), 1e-6f);
        Assert.assertEquals("pushTransform translateY==40f", 40f, push.getFloat(1), 1e-6f);
    }

    // ============================================================
    // 场景 4：clip 节点
    // ============================================================

    /**
     * 树：root → container（clipChildren=true，背景色）→ child（背景色）。
     * 断言：calls 序列含 pushClip → fillRect(container) → fillRect(child) → popClip；
     * pushClip 的 cornerRadius == 0。
     */
    @Test
    public void clipNodeShouldEmitClipBoundaryAroundSubtree() {
        SceneNode root = newNode(100, 50);
        SceneNode container = newNode(100, 50);
        container.setBackgroundColor(0xFFAA0000);
        container.setClipChildren(true);
        SceneNode child = newNode(100, 50);
        child.setBackgroundColor(0xFF00AA00);
        container.appendChild(child);
        root.appendChild(container);

        RecordingRenderBackend backend = paintAndReplay(root);

        List<String> names = backend.getMethodNames();
        // 期望顺序：pushClip, fillRect(container), fillRect(child), popClip
        Assert.assertEquals("clip 子树调用序列",
                Arrays.asList("pushClip", "fillRect", "fillRect", "popClip"), names);

        // pushClip 第 5 个参数（index 4）== cornerRadius
        RecordingRenderBackend.RenderCall clip = backend.getCall(0);
        Assert.assertEquals("pushClip cornerRadius==0", 0, clip.getInt(4));

        // 两条 fillRect 颜色分别为 container / child 背景色
        Assert.assertEquals("container fillRect color", 0xFFAA0000, backend.getCall(1).getInt(4));
        Assert.assertEquals("child fillRect color", 0xFF00AA00, backend.getCall(2).getInt(4));
    }

    // ============================================================
    // 场景 5：嵌套 opacity + transform 同节点
    // ============================================================

    /**
     * 树：root → child（背景色 + opacity=0.5 + Transform(30f,40f)）。
     * 断言调用顺序：pushTransform → pushGroupOpacity → fillRect → popGroupOpacity → popTransform。
     * 这是 ScenePaintEngine.java:200-213 的闭合顺序（CLIP_POP → POP_OPACITY → POP_TRANSFORM）。
     */
    @Test
    public void opacityAndTransformOnSameNodeShouldEmitNestedBoundariesInOrder() {
        SceneNode root = newNode(100, 50);
        SceneNode child = newNode(100, 50);
        child.setBackgroundColor(0xFF336699);
        child.setOpacity(0.5f);
        child.setTransform(new Transform(30f, 40f));
        root.appendChild(child);

        RecordingRenderBackend backend = paintAndReplay(root);

        List<String> names = backend.getMethodNames();
        Assert.assertEquals("同节点 opacity+transform 调用顺序",
                Arrays.asList("pushTransform", "pushGroupOpacity", "fillRect",
                        "popGroupOpacity", "popTransform"), names);

        // transform 是最外层作用域，其 translate 分量正确
        Assert.assertEquals("pushTransform translateX==30f",
                30f, backend.getCall(0).getFloat(0), 1e-6f);
        Assert.assertEquals("pushTransform translateY==40f",
                40f, backend.getCall(0).getFloat(1), 1e-6f);
        // opacity 传局部值 0.5
        Assert.assertEquals("pushGroupOpacity opacity==0.5",
                0.5f, backend.getCall(1).getFloat(4), 1e-6f);
    }

    // ============================================================
    // 场景 6：全不透明快速路径
    // ============================================================

    /**
     * 树：root → a（背景色）→ b（背景色），全不透明。
     * 断言：calls 不含 pushGroupOpacity / popGroupOpacity。
     */
    @Test
    public void fullyOpaqueTreeShouldEmitNoGroupOpacity() {
        SceneNode root = newNode(100, 50);
        SceneNode a = newNode(100, 50);
        a.setBackgroundColor(0xFFFF0000);
        SceneNode b = newNode(100, 50);
        b.setBackgroundColor(0xFF0000FF);
        root.appendChild(a);
        root.appendChild(b);

        RecordingRenderBackend backend = paintAndReplay(root);

        // 两条 fillRect（a、b），无 group opacity 边界
        Assert.assertEquals("应恰好 2 条 fillRect", 2,
                countCalls(backend, "fillRect"));
        assertNoCalls(backend, "pushGroupOpacity", "popGroupOpacity");
    }

    // ============================================================
    // 场景 7：transform + clip 同节点（B6 FBO 方案门控）
    // ============================================================

    /**
     * 树：root → child（背景色 + Transform(30f,40f) + clipChildren=true）。
     * 断言：calls 序列 == [pushTransformLayer, pushClip, fillRect, popClip, popTransformLayer]。
     * transform+clip 叠加走 FBO 离屏图层（pushTransformLayer），clip 在 layer 段内（pushClip 在 pushTransformLayer 之后）。
     * pushTransformLayer 的 translateX/Y == 30f/40f。
     */
    @Test
    public void transformAndClipOnSameNodeShouldEmitTransformLayerBoundary() {
        SceneNode root = newNode(100, 50);
        SceneNode child = newNode(100, 50);
        child.setBackgroundColor(0xFF336699);
        child.setTransform(new Transform(30f, 40f));
        child.setClipChildren(true);
        root.appendChild(child);

        RecordingRenderBackend backend = paintAndReplay(root);

        List<String> names = backend.getMethodNames();
        Assert.assertEquals("transform+clip 同节点调用序列应为 [pushTransformLayer, pushClip, fillRect, popClip, popTransformLayer]",
                Arrays.asList("pushTransformLayer", "pushClip", "fillRect", "popClip", "popTransformLayer"), names);

        // pushTransformLayer 参数顺序：translateX(0), translateY(1), ...
        RecordingRenderBackend.RenderCall push = backend.getCall(0);
        Assert.assertEquals("pushTransformLayer translateX==30f", 30f, push.getFloat(0), 1e-6f);
        Assert.assertEquals("pushTransformLayer translateY==40f", 40f, push.getFloat(1), 1e-6f);

        // 不含 pushTransform/popTransform（走 layer 路径而非 GL 矩阵路径）
        assertNoCalls(backend, "pushTransform", "popTransform");
    }

    // ============================================================
    // 场景 7b：transform + clip + opacity 三层同节点嵌套顺序
    // ============================================================

    /**
     * 树：root → child（背景色 + Transform(30f,40f) + clipChildren=true + opacity=0.5）。
     * 断言调用顺序：pushTransformLayer → pushGroupOpacity → pushClip → fillRect → popClip → popGroupOpacity → popTransformLayer。
     * 验证 ScenePaintEngine 闭合顺序（CLIP_POP → POP_OPACITY → POP_TRANSFORM_LAYER）。
     */
    @Test
    public void transformClipOpacityTripleShouldEmitNestedBoundariesInOrder() {
        SceneNode root = newNode(100, 50);
        SceneNode child = newNode(100, 50);
        child.setBackgroundColor(0xFF336699);
        child.setTransform(new Transform(30f, 40f));
        child.setClipChildren(true);
        child.setOpacity(0.5f);
        root.appendChild(child);

        RecordingRenderBackend backend = paintAndReplay(root);

        List<String> names = backend.getMethodNames();
        Assert.assertEquals("transform+clip+opacity 三层嵌套调用顺序",
                Arrays.asList("pushTransformLayer", "pushGroupOpacity", "pushClip", "fillRect",
                        "popClip", "popGroupOpacity", "popTransformLayer"), names);
    }

    // ============================================================
    // 场景 7c：嵌套 transform+clip（父子均 transform+clip）
    // ============================================================

    /**
     * 树：root → parent（Transform + clipChildren）→ child（Transform + clipChildren + 背景色）。
     * 断言：两层 pushTransformLayer/popTransformLayer 严格配对，且嵌套顺序正确（parent 在外、child 在内）。
     */
    @Test
    public void nestedTransformClipShouldEmitPairedLayerBoundaries() {
        SceneNode root = newNode(200, 100);
        SceneNode parent = newNode(200, 100);
        parent.setTransform(new Transform(5f, 5f));
        parent.setClipChildren(true);
        SceneNode child = newNode(100, 50);
        child.setTransform(new Transform(10f, 10f));
        child.setClipChildren(true);
        child.setBackgroundColor(0xFF336699);
        parent.appendChild(child);
        root.appendChild(parent);

        RecordingRenderBackend backend = paintAndReplay(root);

        List<String> names = backend.getMethodNames();
        // 期望序列：pushTransformLayer(parent) → pushClip(parent) → pushTransformLayer(child) → pushClip(child)
        //          → fillRect → popClip(child) → popTransformLayer(child) → popClip(parent) → popTransformLayer(parent)
        Assert.assertEquals("嵌套 transform+clip 完整调用序列",
                Arrays.asList("pushTransformLayer", "pushClip", "pushTransformLayer", "pushClip",
                        "fillRect", "popClip", "popTransformLayer", "popClip", "popTransformLayer"), names);
        Assert.assertEquals("嵌套 transform+clip 层数", 2, countCalls(backend, "pushTransformLayer"));
        Assert.assertEquals("嵌套 transform+clip pop 配对", 2, countCalls(backend, "popTransformLayer"));
        // 不含 pushTransform/popTransform（均走 layer 路径）
        assertNoCalls(backend, "pushTransform", "popTransform");
    }

    // ============================================================
    // 场景 8：replayer 纯靠接口工作（核心契约断言）
    // ============================================================

    /**
     * 构造 3 层中等复杂树（含 opacity + transform + clip），跑 paint → replay 到
     * RecordingRenderBackend，断言所有调用都是 UiRenderBackend 接口方法。
     *
     * <p>这是「换后端零改动」的测试锚点：mock backend 没有任何 GL 能力，若 replayer
     * 偷偷依赖了 GL 副作用或 UiRenderContext 特有方法，这里无法工作。</p>
     */
    @Test
    public void replayerShouldOnlyUseInterfaceMethods() {
        // root → mid（opacity=0.6 + transform）→ clipper（clipChildren + 背景色）→ leaf（背景色）
        SceneNode root = newNode(120, 80);
        SceneNode mid = newNode(120, 80);
        mid.setOpacity(0.6f);
        mid.setTransform(new Transform(5f, 10f));
        SceneNode clipper = newNode(120, 80);
        clipper.setBackgroundColor(0xFF112233);
        clipper.setClipChildren(true);
        SceneNode leaf = newNode(120, 80);
        leaf.setBackgroundColor(0xFF445566);
        clipper.appendChild(leaf);
        mid.appendChild(clipper);
        root.appendChild(mid);

        RecordingRenderBackend backend = paintAndReplay(root);

        // 至少有调用产出（非空链路）
        Assert.assertTrue("应产出渲染调用", backend.getCallCount() > 0);

        // 每条调用的方法名都必须在接口白名单内
        for (RecordingRenderBackend.RenderCall call : backend.getCalls()) {
            Assert.assertTrue("调用 " + call.methodName() + " 必须是 UiRenderBackend 接口方法",
                    INTERFACE_METHODS.contains(call.methodName()));
        }

        // 含 transform / opacity / clip 三类边界（证明链路完整跑通）
        Assert.assertTrue("应含 pushTransform", countCalls(backend, "pushTransform") >= 1);
        Assert.assertTrue("应含 pushGroupOpacity", countCalls(backend, "pushGroupOpacity") >= 1);
        Assert.assertTrue("应含 pushClip", countCalls(backend, "pushClip") >= 1);

        // 边界严格配对
        Assert.assertEquals("push/popTransform 配对",
                countCalls(backend, "pushTransform"), countCalls(backend, "popTransform"));
        Assert.assertEquals("push/popGroupOpacity 配对",
                countCalls(backend, "pushGroupOpacity"), countCalls(backend, "popGroupOpacity"));
        Assert.assertEquals("push/popClip 配对",
                countCalls(backend, "pushClip"), countCalls(backend, "popClip"));
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    /** 创建带固定首选尺寸的节点（便于断言 fillRect 坐标） */
    private static SceneNode newNode(int width, int height) {
        SceneNode n = new SceneNode();
        n.setPreferredWidth(width);
        n.setPreferredHeight(height);
        return n;
    }

    /** layout + paint + replay 到全新 RecordingRenderBackend，返回该 backend。
     *  委托 {@link ScenePaintCapture#paintAndCapture}（提公共 helper 后零行为变化）。 */
    private RecordingRenderBackend paintAndReplay(SceneNode root) {
        return ScenePaintCapture.paintAndCapture(root, 200, Constraints.UNCONSTRAINED);
    }

    /** 返回第一条指定方法名的调用，无则 null */
    private static RecordingRenderBackend.RenderCall findCall(
            RecordingRenderBackend backend, String methodName) {
        for (RecordingRenderBackend.RenderCall c : backend.getCalls()) {
            if (c.methodName().equals(methodName)) {
                return c;
            }
        }
        return null;
    }

    /** 统计指定方法名的调用数 */
    private static int countCalls(RecordingRenderBackend backend, String methodName) {
        int count = 0;
        for (RecordingRenderBackend.RenderCall c : backend.getCalls()) {
            if (c.methodName().equals(methodName)) {
                count++;
            }
        }
        return count;
    }

    /** 断言 backend 中不含任何指定方法名的调用 */
    private static void assertNoCalls(RecordingRenderBackend backend, String... forbidden) {
        for (String name : forbidden) {
            Assert.assertEquals("不应含 " + name + " 调用", 0, countCalls(backend, name));
        }
    }
}
