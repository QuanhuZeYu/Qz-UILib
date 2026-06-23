package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.scene.UiSurface;
import club.heiqi.uilib.ui.scene.component.MountHandle;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.control.SceneButton;
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
 * 新栈 qzui test 首页宿主 Widget。
 *
 * <p>本页只负责展示 scene 新栈导航入口：交互处理只写 {@link #requestedDestinationSignal}，
 * 具体打开 MC {@code GuiScreen} 的平台动作由 {@link SceneTestHubScreen} 适配层消费。</p>
 */
final class SceneTestHubHostWidget extends Widget implements UiSurface {

    /** 没有待处理导航请求。 */
    private static final String DESTINATION_NONE = "";
    /** Scene 基础 demo 请求。 */
    private static final String DESTINATION_SCENE = "scene";
    /** Controls demo 请求。 */
    private static final String DESTINATION_CONTROLS = "controls";
    /** Scroll demo 请求。 */
    private static final String DESTINATION_SCROLL = "scroll";
    /** Table demo 请求。 */
    private static final String DESTINATION_TABLE = "table";
    /** Layout demo 请求。 */
    private static final String DESTINATION_LAYOUT = "layout";

    private final SceneRuntime runtime;
    private final SceneLayoutEngine layoutEngine;
    private final ScenePaintEngine paintEngine;
    private final ScenePaintReplayer replayer;
    private final SceneNode root;
    private final PlatformInputSource inputSource;
    private final Signal<String> requestedDestinationSignal;

    /**
     * 创建新栈 test 首页宿主。
     *
     * @param inputSource 平台输入源，可为 null（退化模式）
     */
    SceneTestHubHostWidget(PlatformInputSource inputSource) {
        this.inputSource = inputSource;
        SceneTextMeasurer measurer = new TextMeasureServiceSceneAdapter(DefaultTextMeasureService.getInstance());
        this.runtime = new SceneRuntime(measurer);
        this.layoutEngine = new SceneLayoutEngine(measurer);
        this.paintEngine = new ScenePaintEngine();
        this.replayer = new ScenePaintReplayer();
        this.root = new SceneNode();
        this.requestedDestinationSignal = Signal.create(DESTINATION_NONE);

        root.setFillParentHeight(true);
        root.setFlexDirection(FlexDirection.COLUMN);
        root.setGap(12);
        root.setPadding(24);
        root.setBackgroundColor(0xFF20242B);

        mountTitle("Qz UILib Scene Test Hub", 0xFFFFFFFF, 28);
        mountTitle("第一批新栈入口：保留旧 /qzuilib test，独立打开现有 scene demos。", 0xFFB8C2CC, 18);
        mountButton("Scene demo", DESTINATION_SCENE);
        mountButton("Controls demo", DESTINATION_CONTROLS);
        mountButton("Scroll demo", DESTINATION_SCROLL);
        mountButton("Table demo", DESTINATION_TABLE);
        mountButton("Layout demo", DESTINATION_LAYOUT);

        if (inputSource instanceof LwjglInputSource) {
            runtime.bindCursor(new LwjglCursorBackend());
        }
        runtime.flush();
    }

    /**
     * 消费导航请求，供 screen 适配层在渲染帧后打开目标页面。
     *
     * @return 目标标识；没有请求时返回 null
     */
    String consumeNavigationRequest() {
        String destination = requestedDestinationSignal.get();
        if (destination == null || DESTINATION_NONE.equals(destination)) {
            return null;
        }
        requestedDestinationSignal.set(DESTINATION_NONE);
        // 立即提交清零，避免切屏请求在下一帧被重复消费。
        runtime.flush();
        return destination;
    }

    /**
     * 判断目标是否为 Scene 基础 demo。
     *
     * @param destination 目标标识
     * @return true 表示 Scene 基础 demo
     */
    static boolean isSceneDestination(String destination) {
        return DESTINATION_SCENE.equals(destination);
    }

    /**
     * 判断目标是否为 Controls demo。
     *
     * @param destination 目标标识
     * @return true 表示 Controls demo
     */
    static boolean isControlsDestination(String destination) {
        return DESTINATION_CONTROLS.equals(destination);
    }

    /**
     * 判断目标是否为 Scroll demo。
     *
     * @param destination 目标标识
     * @return true 表示 Scroll demo
     */
    static boolean isScrollDestination(String destination) {
        return DESTINATION_SCROLL.equals(destination);
    }

    /**
     * 判断目标是否为 Table demo。
     *
     * @param destination 目标标识
     * @return true 表示 Table demo
     */
    static boolean isTableDestination(String destination) {
        return DESTINATION_TABLE.equals(destination);
    }

    /**
     * 判断目标是否为 Layout demo。
     *
     * @param destination 目标标识
     * @return true 表示 Layout demo
     */
    static boolean isLayoutDestination(String destination) {
        return DESTINATION_LAYOUT.equals(destination);
    }

    @Override
    protected void drawSelf(UiRenderContext ctx) {
        render(getWidth(), getHeight(), ctx, getAbsoluteX(), getAbsoluteY());
    }

    /**
     * 驱动首页完整 scene pipeline。
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

        SceneInputFrame frame = (inputSource != null) ? inputSource.drainFrame() : SceneInputFrame.EMPTY;
        layoutEngine.layout(root, new Constraints(w, h));
        if (!frame.isEmpty()) {
            runtime.route(root, frame, absX, absY);
        }
        runtime.flush();
        layoutEngine.layout(root, new Constraints(w, h));
        PaintPlan plan = paintEngine.paint(root);
        replayer.replay(plan, ctx, absX, absY);
    }

    /**
     * 转发键盘事件到输入适配器。
     *
     * @param typedChar 输入字符
     * @param keyCode 原生键码
     */
    @Override
    public void onKeyTyped(char typedChar, int keyCode) {
        if (inputSource instanceof LwjglInputSource) {
            ((LwjglInputSource) inputSource).pushKeyTyped(typedChar, keyCode, System.nanoTime());
        }
    }

    /**
     * 首页无文本输入旁路需求，保留 UiSurface 契约入口。
     *
     * @param text 文本内容
     */
    @Override
    public void pushText(String text) {
    }

    /**
     * 首页无文本输入旁路需求，保留 UiSurface 契约入口。
     *
     * @param external true 表示外部文本事件接管输入
     */
    @Override
    public void setExternalTextMode(boolean external) {
    }

    /**
     * 释放首页 runtime 资源。
     */
    @Override
    public void dispose() {
        runtime.dispose();
    }

    /**
     * 挂载标题文本节点。
     *
     * @param text 文本内容
     * @param color 文本颜色
     * @param height 节点高度
     */
    private void mountTitle(String text, int color, int height) {
        SceneNode title = new SceneNode();
        title.setText(text);
        title.setTextColor(color);
        title.setPreferredHeight(height);
        title.setHitTestable(false);
        root.appendChild(title);
    }

    /**
     * 挂载导航按钮，点击时只写导航 signal。
     *
     * @param label 按钮文案
     * @param destination 导航目标标识
     */
    private void mountButton(String label, String destination) {
        SceneButton.Props props = new SceneButton.Props(
                Signal.create(label),
                Signal.create(Boolean.TRUE),
                () -> requestedDestinationSignal.set(destination));
        MountHandle handle = runtime.mount(root, SceneButton.create(runtime, props));
        SceneNode row = handle.getRoot();
        row.setPreferredWidth(220);
        row.setPreferredHeight(40);
    }
}
