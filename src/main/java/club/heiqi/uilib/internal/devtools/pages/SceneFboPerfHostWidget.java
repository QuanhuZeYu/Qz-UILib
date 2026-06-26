package club.heiqi.uilib.internal.devtools.pages;

import java.util.ArrayList;
import java.util.List;

import club.heiqi.uilib.ui.diagnostic.UiPerformanceMonitor;
import club.heiqi.uilib.ui.diagnostic.UiRuntimeStats;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneSlider;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.Transform;

/**
 * B6 FBO 性能基线实测宿主 Widget。
 *
 * <p>本页用于量化 transform+clip 叠加（FBO 离屏图层路径）相对纯 transform / 纯 clip 的
 * fillrate 掉帧拐点，为「是否值得做 FBO 纹理脏标记跨帧缓存」提供数据依据。</p>
 *
 * <p>三种模式对照：</p>
 * <ul>
 *   <li>mode 0（FBO）：测试节点同时 {@code setTransform} + {@code setClipChildren(true)}，
 *       触发 {@code ScenePaintEngine} 的 {@code addPushTransformLayer} FBO 离屏图层路径</li>
 *   <li>mode 1（纯 transform）：仅 {@code setTransform}，走 GL 矩阵 {@code addPushTransform}</li>
 *   <li>mode 2（纯 clip）：仅 {@code setClipChildren(true)}，走 scissor {@code addClipPush}</li>
 * </ul>
 *
 * <p>触发 FBO 的关键：测试节点须<b>自身同时</b> setTransform + setClipChildren。
 * 若 transform 在子节点、clip 在父节点，子节点单独 transform 走 GL，不进 FBO。</p>
 */
public class SceneFboPerfHostWidget extends AbstractSceneHostWidget {

    /** 根背景色。 */
    private static final int ROOT_BG = 0xFF08111F;
    /** 标题文本色。 */
    private static final int TITLE_COLOR = 0xFFEAF1FF;
    /** 次要说明文本色。 */
    private static final int MUTED_COLOR = 0xFF8AA0C8;
    /** 监测条背景色。 */
    private static final int MONITOR_BG = 0xFF111C31;
    /** 内容区背景色。 */
    private static final int CONTENT_BG = 0xFF0D1728;
    /** 测试节点背景色（蓝）。 */
    private static final int NODE_BG = 0xFF2F6FB0;
    /** 测试节点溢出子节点背景色（青，比 clip 框大，确保有内容被裁）。 */
    private static final int OVERFLOW_BG = 0xFF39B7C9;
    /** 嵌套层容器背景色（低透明，便于肉眼确认层叠）。 */
    private static final int NEST_BG = 0x22FFFFFF;
    /** 标题条固定高度。 */
    private static final int TITLE_BAR_HEIGHT = 44;
    /** 监测条固定高度。 */
    private static final int MONITOR_BAR_HEIGHT = 36;
    /** 底部操作条固定高度。 */
    private static final int ACTION_BAR_HEIGHT = 52;
    /** 测试节点固定宽。 */
    private static final int NODE_W = 40;
    /** 测试节点固定高。 */
    private static final int NODE_H = 40;
    /** 溢出子节点宽（大于 clip 框，确保裁剪生效）。 */
    private static final int OVERFLOW_W = 64;
    /** 溢出子节点高。 */
    private static final int OVERFLOW_H = 64;
    /** 每行排列的测试节点数。 */
    private static final int NODES_PER_ROW = 10;
    /** 行间距/列间距。 */
    private static final int CELL_GAP = 8;
    /** FBO 旋转角度。 */
    private static final float ROTATE_DEG = 15f;

    /** mode 0：FBO（transform+clip）。 */
    private static final int MODE_FBO = 0;
    /** mode 1：纯 transform。 */
    private static final int MODE_TRANSFORM = 1;
    /** mode 2：纯 clip。 */
    private static final int MODE_CLIP = 2;

    /** 根节点。 */
    private final SceneNode root;
    /** 内容区容器（rebuild 时清空并重建测试节点）。 */
    private final SceneNode content;
    /** fps 文本节点（每帧直接 setText，不走 bind）。 */
    private final SceneNode fpsText;
    /** 统计文本节点（每帧直接 setText，不走 bind）。 */
    private final SceneNode statsText;

    /** 测试节点数量 signal（初始 100，范围 0-2000，step 50）。 */
    private final Signal<Integer> nodeCountSignal;
    /** 嵌套深度 signal（初始 1，范围 1-5，step 1）。 */
    private final Signal<Integer> depthSignal;
    /** 模式 signal（0=FBO，1=纯transform，2=纯clip；初始 0）。 */
    private final Signal<Integer> modeSignal;

    /**
     * 创建 FBO 性能基线实测宿主 Widget。
     *
     * @param inputSource 平台输入源，可为 null（退化模式）
     */
    public SceneFboPerfHostWidget(PlatformInputSource inputSource) {
        super(inputSource);
        this.nodeCountSignal = Signal.create(Integer.valueOf(100));
        this.depthSignal = Signal.create(Integer.valueOf(1));
        this.modeSignal = Signal.create(Integer.valueOf(MODE_FBO));

        this.root = createRoot();
        root.appendChild(createTitleBar());

        SceneNode monitorBar = createMonitorBar();
        root.appendChild(monitorBar);
        this.fpsText = (SceneNode) monitorBar.__getChildren().get(0);
        this.statsText = (SceneNode) monitorBar.__getChildren().get(1);

        this.content = createContent();
        root.appendChild(content);

        root.appendChild(createActionBar());

        rebuild();
        runtime.flush();
    }

    /**
     * 每帧渲染后采样 fps 与帧耗时，直接 setText 到监测条文本节点。
     *
     * <p>不走 bind/boundText，避免 invalidation 刷新时序问题；fps 文本展示在 render 末尾直接写入。</p>
     *
     * @param w 宿主宽度
     * @param h 宿主高度
     * @param ctx 渲染出口
     * @param absX 宿主绝对 X 偏移
     * @param absY 宿主绝对 Y 偏移
     */
    @Override
    public void render(int w, int h, UiRenderBackend ctx, int absX, int absY) {
        super.render(w, h, ctx, absX, absY);
        UiRuntimeStats s = UiPerformanceMonitor.getInstance().getRuntimeStats();
        String fps = String.format("fps=%.1f", s.getAverageFps());
        String stats = String.format("frame=%.2fms  max=%.2fms  slow=%d/%d",
                s.getFrameTimeMs(), s.getMaxFrameTimeMs(),
                s.getSlowFrameCount(), s.getSampledFrameCount());
        fpsText.setText(fps);
        statsText.setText(stats);
    }

    /**
     * 创建根容器。
     *
     * @return 根节点
     */
    private SceneNode createRoot() {
        SceneNode node = new SceneNode();
        node.setFillParentHeight(true);
        node.setFlexDirection(FlexDirection.COLUMN);
        node.setPadding(20);
        node.setGap(12);
        node.setBackgroundColor(ROOT_BG);
        return node;
    }

    /**
     * 创建标题条。
     *
     * @return 标题条节点
     */
    private SceneNode createTitleBar() {
        SceneNode titleBar = new SceneNode();
        titleBar.setFlexDirection(FlexDirection.COLUMN);
        titleBar.setPreferredHeight(TITLE_BAR_HEIGHT);
        titleBar.setGap(4);
        titleBar.setHitTestable(false);
        titleBar.appendChild(text("Scene FBO 性能基线实测", TITLE_COLOR));
        titleBar.appendChild(text("B6 FBO(transform+clip) vs 纯transform vs 纯clip · fillrate 掉帧拐点量化", MUTED_COLOR));
        return titleBar;
    }

    /**
     * 创建性能监测条（fps + 统计文本，文本节点引用保存为字段供每帧 setText）。
     *
     * @return 监测条节点
     */
    private SceneNode createMonitorBar() {
        SceneNode row = new SceneNode();
        row.setFlexDirection(FlexDirection.ROW);
        row.setPreferredHeight(MONITOR_BAR_HEIGHT);
        row.setPadding(8);
        row.setGap(24);
        row.setBackgroundColor(MONITOR_BG);
        row.setCornerRadius(8);
        row.appendChild(text("fps=--", TITLE_COLOR));
        row.appendChild(text("frame=--  max=--  slow=--/--", TITLE_COLOR));
        return row;
    }

    /**
     * 创建内容区容器（可滚动 + 裁剪，承载测试节点）。
     *
     * @return 内容区节点
     */
    private SceneNode createContent() {
        SceneNode node = new SceneNode();
        node.setFlexDirection(FlexDirection.COLUMN);
        node.setFillParentHeight(true);
        node.setScrollable(true);
        node.setClipChildren(true);
        node.setPadding(10);
        node.setGap(CELL_GAP);
        node.setBackgroundColor(CONTENT_BG);
        node.setCornerRadius(10);
        return node;
    }

    /**
     * 创建底部调参操作条。
     *
     * @return 操作条节点
     */
    private SceneNode createActionBar() {
        SceneNode row = new SceneNode();
        row.setFlexDirection(FlexDirection.ROW);
        row.setPreferredHeight(ACTION_BAR_HEIGHT);
        row.setGap(10);
        mountModeButton(row, "FBO", MODE_FBO);
        mountModeButton(row, "纯T", MODE_TRANSFORM);
        mountModeButton(row, "纯Clip", MODE_CLIP);
        mountNodeCountSlider(row);
        mountDepthSlider(row);
        mountButton(row, "重置采样", this::resetSampling);
        return row;
    }

    /**
     * 挂载模式切换按钮。
     *
     * @param parent 父节点
     * @param label 按钮文案
     * @param mode 目标模式
     */
    private void mountModeButton(SceneNode parent, String label, int mode) {
        SceneButton.Props props = new SceneButton.Props(
                Signal.create(label),
                Signal.create(Boolean.TRUE),
                () -> {
                    modeSignal.set(Integer.valueOf(mode));
                    rebuild();
                });
        SceneNode button = runtime.mount(parent, SceneButton.create(runtime, props)).getRoot();
        button.setPreferredWidth(80);
        button.setPreferredHeight(36);
    }

    /**
     * 挂载节点数 slider（0-2000，step 50，committing 时 rebuild）。
     *
     * @param parent 父节点
     */
    private void mountNodeCountSlider(SceneNode parent) {
        Signal<Double> value = Signal.create(Double.valueOf(nodeCountSignal.get()));
        SceneSlider.Props props = SceneSlider.Props.builder(value)
                .min(0)
                .max(2000)
                .step(50)
                .onChange((v, committing) -> {
                    value.set(Double.valueOf(v));
                    if (committing) {
                        nodeCountSignal.set(Integer.valueOf((int) Math.round(v)));
                        rebuild();
                    }
                })
                .build();
        runtime.mount(parent, SceneSlider.create(runtime, props));
    }

    /**
     * 挂载嵌套深度 slider（1-5，step 1，committing 时 rebuild）。
     *
     * @param parent 父节点
     */
    private void mountDepthSlider(SceneNode parent) {
        Signal<Double> value = Signal.create(Double.valueOf(depthSignal.get()));
        SceneSlider.Props props = SceneSlider.Props.builder(value)
                .min(1)
                .max(5)
                .step(1)
                .onChange((v, committing) -> {
                    value.set(Double.valueOf(v));
                    if (committing) {
                        depthSignal.set(Integer.valueOf((int) Math.round(v)));
                        rebuild();
                    }
                })
                .build();
        runtime.mount(parent, SceneSlider.create(runtime, props));
    }

    /**
     * 挂载普通操作按钮。
     *
     * @param parent 父节点
     * @param label 按钮文案
     * @param onClick 点击回调
     */
    private void mountButton(SceneNode parent, String label, Runnable onClick) {
        SceneButton.Props props = new SceneButton.Props(Signal.create(label), Signal.create(Boolean.TRUE), onClick);
        SceneNode button = runtime.mount(parent, SceneButton.create(runtime, props)).getRoot();
        button.setPreferredWidth(100);
        button.setPreferredHeight(36);
    }

    /** 重置 fps 采样历史。 */
    private void resetSampling() {
        UiPerformanceMonitor.getInstance().resetHistory("fbo-perf");
    }

    /**
     * 按当前 nodeCount/depth/mode 清空内容区并重建测试节点。
     *
     * <p>每个测试节点是一个带背景色的小矩形（便于肉眼确认渲染），按行排列。
     * 嵌套深度 depth>1 时，外层包 depth-1 层 transform+clip 容器，制造层叠 FBO。</p>
     */
    private void rebuild() {
        // mode/参数切换时重置 fps 采样历史，避免 120 帧滚动窗口内新旧数据混合影响测量准确性。
        // 构造期调用同样安全：resetHistory 只清历史，无副作用。
        UiPerformanceMonitor.getInstance().resetHistory("fbo-perf");

        // 清空旧测试节点（复制 children 列表避免并发修改）
        List<SceneNode> old = new ArrayList<SceneNode>(content.__getChildren());
        for (SceneNode child : old) {
            content.removeChild(child);
        }

        int count = nodeCountSignal.get();
        int depth = depthSignal.get();
        int mode = modeSignal.get();

        SceneNode currentRow = null;
        for (int i = 0; i < count; i++) {
            if (i % NODES_PER_ROW == 0) {
                currentRow = new SceneNode();
                currentRow.setFlexDirection(FlexDirection.ROW);
                currentRow.setGap(CELL_GAP);
                currentRow.setHitTestable(false);
                content.appendChild(currentRow);
            }
            SceneNode testNode = buildTestNode(mode);
            SceneNode nested = wrapNesting(testNode, depth);
            currentRow.appendChild(nested);
        }
        runtime.flush();
    }

    /**
     * 构造单个测试节点（按 mode 决定 transform/clip 组合）。
     *
     * @param mode 模式
     * @return 测试节点
     */
    private SceneNode buildTestNode(int mode) {
        SceneNode node = new SceneNode();
        node.setFlexDirection(FlexDirection.COLUMN);
        node.setPreferredWidth(NODE_W);
        node.setPreferredHeight(NODE_H);
        node.setBackgroundColor(NODE_BG);
        node.setHitTestable(false);

        if (mode == MODE_FBO) {
            // FBO：自身同时 transform + clipChildren
            node.setTransform(Transform.rotate(ROTATE_DEG));
            node.setClipChildren(true);
            node.appendChild(overflowChild());
        } else if (mode == MODE_TRANSFORM) {
            // 纯 transform：仅 setTransform，不 clip
            node.setTransform(Transform.rotate(ROTATE_DEG));
            node.appendChild(overflowChild());
        } else {
            // 纯 clip：仅 clipChildren + 固定宽高 + 溢出子节点，不 transform
            node.setClipChildren(true);
            node.appendChild(overflowChild());
        }
        return node;
    }

    /**
     * 构造比 clip 框略大的溢出子节点（确保有内容被裁，证明 FBO/scissor 在工作）。
     *
     * @return 溢出子节点
     */
    private SceneNode overflowChild() {
        SceneNode child = new SceneNode();
        child.setPreferredWidth(OVERFLOW_W);
        child.setPreferredHeight(OVERFLOW_H);
        child.setBackgroundColor(OVERFLOW_BG);
        child.setHitTestable(false);
        return child;
    }

    /**
     * 把测试节点外层包 depth-1 层 transform+clip 容器，制造层叠 FBO。
     *
     * <p>对照公平性说明：depth>1 时三档（FBO/纯transform/纯clip）均叠加 (depth-1) 层
     * 外层 FBO wrapper（wrapper 固定 transform+clip），最内层路径差异被外层 FBO 开销稀释，
     * 三档数值会趋于接近。建议 depth=1 做纯路径对比（凸显 transform/clip/FBO 单层差异），
     * depth>1 做层叠 FBO 累积开销测量（观察 FBO 层数对 fillrate 的累积影响）。</p>
     *
     * @param inner 最内层测试节点
     * @param depth 嵌套深度（含最内层）
     * @return 包装后的节点
     */
    private SceneNode wrapNesting(SceneNode inner, int depth) {
        SceneNode current = inner;
        for (int layer = 1; layer < depth; layer++) {
            SceneNode wrapper = new SceneNode();
            wrapper.setFlexDirection(FlexDirection.COLUMN);
            wrapper.setPreferredWidth(NODE_W + layer * 6);
            wrapper.setPreferredHeight(NODE_H + layer * 6);
            wrapper.setPadding(3);
            wrapper.setBackgroundColor(NEST_BG);
            wrapper.setTransform(Transform.rotate(ROTATE_DEG));
            wrapper.setClipChildren(true);
            wrapper.setHitTestable(false);
            wrapper.appendChild(current);
            current = wrapper;
        }
        return current;
    }

    /**
     * 创建静态文字节点。
     *
     * @param value 文本
     * @param color 文本颜色
     * @return 文字节点
     */
    private SceneNode text(String value, int color) {
        SceneNode node = new SceneNode();
        node.setText(value);
        node.setTextColor(color);
        node.setHitTestable(false);
        return node;
    }

    @Override
    protected SceneNode getRoot() {
        return root;
    }

    /** @return 节点数 signal */
    Signal<Integer> __getNodeCountSignal() {
        return nodeCountSignal;
    }

    /** @return 深度 signal */
    Signal<Integer> __getDepthSignal() {
        return depthSignal;
    }

    /** @return 模式 signal */
    Signal<Integer> __getModeSignal() {
        return modeSignal;
    }
}
