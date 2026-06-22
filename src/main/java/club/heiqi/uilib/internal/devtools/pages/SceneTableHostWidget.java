package club.heiqi.uilib.internal.devtools.pages;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.scene.UiSurface;
import club.heiqi.uilib.ui.scene.component.MountHandle;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.control.SceneTable;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.paint.ScenePaintReplayer;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextMeasureServiceSceneAdapter;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 新栈 ui.scene Table demo 宿主 Widget。
 *
 * <p>该宿主只负责独立页面壳、标题说明和 render pipeline，表格结构、裁剪与滚动逻辑全部交给
 * {@link SceneTable} 组件本体，避免在 demo 层重复实现 table 内部能力。</p>
 */
public class SceneTableHostWidget extends Widget implements UiSurface {

    /** 表格视口固定高度（像素），用于真机验收纵向滚动与裁剪。 */
    private static final int VIEWPORT_HEIGHT = 168;
    /** 表格固定行高（像素）。 */
    private static final int ROW_HEIGHT = 28;
    /** 数据行数量，确保内容高度明显超过视口高度。 */
    private static final int ROW_COUNT = 14;
    /** 标题文字颜色。 */
    private static final int TITLE_TEXT_COLOR = 0xFFC9D8F8;

    private final SceneRuntime runtime;
    private final SceneLayoutEngine layoutEngine;
    private final ScenePaintEngine paintEngine;
    private final ScenePaintReplayer replayer;
    private final SceneNode root;
    private final SceneNode tableRoot;
    private final SceneNode viewport;
    private final SceneNode content;
    private final MountHandle tableHandle;
    private final PlatformInputSource inputSource;

    /**
     * 创建 Table demo 宿主 Widget，注入平台输入源。
     *
     * @param inputSource 平台输入源，可为 null（退化模式，无真机滚轮）
     */
    public SceneTableHostWidget(PlatformInputSource inputSource) {
        this.inputSource = inputSource;
        SceneTextMeasurer measurer = new TextMeasureServiceSceneAdapter(DefaultTextMeasureService.getInstance());
        this.runtime = new SceneRuntime(measurer);
        this.layoutEngine = new SceneLayoutEngine(measurer);
        this.paintEngine = new ScenePaintEngine();
        this.replayer = new ScenePaintReplayer();

        this.root = new SceneNode();
        root.setFillParentHeight(true);
        root.setFlexDirection(FlexDirection.COLUMN);
        root.setGap(12);
        root.setPadding(20);

        SceneNode title = new SceneNode();
        title.setText("Scene Table demo：固定列宽 / 固定行高 / 长文本裁剪 / 纵向滚动");
        title.setTextColor(TITLE_TEXT_COLOR);
        title.setHitTestable(false);
        root.appendChild(title);

        this.tableHandle = runtime.mount(root, SceneTable.create(runtime, createTableProps()));
        this.tableRoot = tableHandle.getRoot();
        this.viewport = tableRoot.__getChildren().get(0);
        this.content = viewport.__getChildren().get(0);

        if (inputSource instanceof LwjglInputSource) {
            runtime.bindCursor(new LwjglCursorBackend());
        }
        runtime.flush();
    }

    /**
     * 创建 Table demo 输入数据。
     *
     * @return Table 输入契约
     */
    private SceneTable.Props createTableProps() {
        List<List<String>> rows = new ArrayList<List<String>>();
        for (int i = 1; i <= ROW_COUNT; i++) {
            rows.add(Arrays.asList(
                    "#" + i,
                    "物品 " + i,
                    i == 3 ? "这是一段非常非常长的描述文本，用于真机观察固定列宽内裁剪效果" : "固定宽度描述 " + i,
                    String.valueOf(i * 7)
            ));
        }
        return new SceneTable.Props(
                Arrays.asList("序号", "名称", "描述", "数量"),
                rows,
                Arrays.asList(52, 88, 188, 64),
                ROW_HEIGHT,
                VIEWPORT_HEIGHT);
    }

    /**
     * 每帧绘制 —— 与 SceneScrollHostWidget 保持同一输入、布局、路由、刷新、绘制时序。
     *
     * @param ctx 渲染上下文
     */
    @Override
    protected void drawSelf(UiRenderContext ctx) {
        render(getWidth(), getHeight(), ctx, getAbsoluteX(), getAbsoluteY());
    }

    /**
     * 驱动完整 scene table demo pipeline。
     *
     * @param w 宿主宽度
     * @param h 宿主高度
     * @param ctx 渲染出口
     * @param absX 宿主绝对 X 偏移
     * @param absY 宿主绝对 Y 偏移
     */
    @Override
    public void render(int w, int h, UiRenderBackend ctx, int absX, int absY) {
        w = Math.max(0, w);
        h = Math.max(0, h);

        SceneInputFrame frame = inputSource != null ? inputSource.drainFrame() : SceneInputFrame.EMPTY;
        layoutEngine.layout(root, new Constraints(w, h));
        if (!frame.isEmpty()) {
            runtime.route(root, frame, absX, absY);
        }
        runtime.flush();
        layoutEngine.layout(root, new Constraints(w, h));
        PaintPlan plan = paintEngine.paint(root);
        replayer.replay(plan, ctx, absX, absY);
    }

    @Override
    public void onKeyTyped(char typedChar, int keyCode) {}

    @Override
    public void pushText(String text) {}

    @Override
    public void setExternalTextMode(boolean external) {}

    /** 回收资源：卸载 Table 组件并释放 runtime 作用域。 */
    @Override
    public void dispose() {
        tableHandle.dispose();
        runtime.dispose();
    }

    /** @return 内部场景运行时 */
    SceneRuntime __getRuntime() {
        return runtime;
    }

    /** @return 内部布局引擎 */
    SceneLayoutEngine __getLayoutEngine() {
        return layoutEngine;
    }

    /** @return 场景树根节点 */
    SceneNode __getRoot() {
        return root;
    }

    /** @return SceneTable 组件根节点 */
    SceneNode __getTableRoot() {
        return tableRoot;
    }

    /** @return Table 视口节点 */
    SceneNode __getViewport() {
        return viewport;
    }

    /** @return Table 内容容器节点 */
    SceneNode __getContent() {
        return content;
    }

    /** @return 视口固定高度常量 */
    static int __getViewportHeight() {
        return VIEWPORT_HEIGHT;
    }
}
