package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.scene.host.AbstractSceneHostWidget;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 新栈 qzui test 首页宿主 Widget。
 *
 * <p>本页只负责展示 scene 新栈导航入口：交互处理只写 {@link #requestedDestinationSignal}，
 * 具体打开 MC {@code GuiScreen} 的平台动作由 {@link SceneTestHubScreen} 适配层消费。</p>
 */
final class SceneTestHubHostWidget extends AbstractSceneHostWidget {

    /** 没有待处理导航请求。 */
    private static final String DESTINATION_NONE = "";
    /** Controls demo 请求。 */
    private static final String DESTINATION_CONTROLS = "controls";
    /** SceneScrollContainer.attach demo 请求。 */
    private static final String DESTINATION_SCROLL_CONTAINER = "scrollContainer";
    /** DataTable demo 请求。 */
    private static final String DESTINATION_DATA_TABLE = "dataTable";
    /** Layout demo 请求。 */
    private static final String DESTINATION_LAYOUT = "layout";
    /** Form demo 请求。 */
    private static final String DESTINATION_FORM = "form";
    /** Select demo 请求。 */
    private static final String DESTINATION_SELECT = "select";
    /** SimpleList demo 请求。 */
    private static final String DESTINATION_SIMPLE_LIST = "simpleList";
    /** KeyValueMap demo 请求。 */
    private static final String DESTINATION_KEY_VALUE_MAP = "keyValueMap";
    /** 压力测试 demo 请求。 */
    private static final String DESTINATION_STRESS_TEST = "stressTest";
    /** ObjectField demo 请求。 */
    private static final String DESTINATION_OBJECT_FIELD = "objectField";

    private static final String DESTINATION_TEXT_AREA = "textArea";
    /** Transform+Clip demo 请求。 */
    private static final String DESTINATION_TRANSFORM = "transform";
    /** FBO 性能基线实测页请求。 */
    private static final String DESTINATION_PERF = "perf";

    private final SceneNode root;
    private final Signal<String> requestedDestinationSignal;

    /**
     * 创建新栈 test 首页宿主。
     *
     * @param inputSource 平台输入源，可为 null（退化模式）
     */
    SceneTestHubHostWidget(PlatformInputSource inputSource) {
        super(inputSource);
        this.root = new SceneNode();
        this.requestedDestinationSignal = Signal.create(DESTINATION_NONE);

        root.setFillParentHeight(true);
        root.setFlexDirection(FlexDirection.COLUMN);
        root.setGap(12);
        root.setPadding(24);
        // 半透明背景：保留原深色基调，alpha 降到 0xCC 让背后 MC 场景透出
        root.setBackgroundColor(0xCC20242B);
        // 整页做成纵向滚动视口：按钮条目多时内容超出视口可滚轮滚动
        root.setScrollable(true);
        SceneScrolls.attach(runtime, root);

        mountTitle("Qz UILib Scene Test Hub", 0xFFFFFFFF, 28);
        mountTitle("第一批新栈入口：保留旧 /qzuilib test，独立打开现有 scene demos。", 0xFFB8C2CC, 18);

        // 两列布局：ROW 容器内放左右两个 COLUMN，13 个入口分 7+6
        SceneNode row = SceneNode.row();
        row.setGap(12);
        row.setHitTestable(false);
        root.appendChild(row);
        SceneNode leftColumn = SceneNode.column();
        leftColumn.setGap(12);
        leftColumn.setHitTestable(false);
        row.appendChild(leftColumn);
        SceneNode rightColumn = SceneNode.column();
        rightColumn.setGap(12);
        rightColumn.setHitTestable(false);
        row.appendChild(rightColumn);

        // 左列 7 个
        mountButton(leftColumn, "Controls demo", DESTINATION_CONTROLS);
        mountButton(leftColumn, "ScrollContainer demo", DESTINATION_SCROLL_CONTAINER);
        mountButton(leftColumn, "DataTable demo", DESTINATION_DATA_TABLE);
        mountButton(leftColumn, "Layout demo", DESTINATION_LAYOUT);
        mountButton(leftColumn, "Form demo", DESTINATION_FORM);
        mountButton(leftColumn, "Select demo", DESTINATION_SELECT);
        mountButton(leftColumn, "SimpleList demo", DESTINATION_SIMPLE_LIST);
        // 右列 6 个
        mountButton(rightColumn, "KeyValueMap demo", DESTINATION_KEY_VALUE_MAP);
        mountButton(rightColumn, "Stress Test", DESTINATION_STRESS_TEST);
        mountButton(rightColumn, "ObjectField demo", DESTINATION_OBJECT_FIELD);
        mountButton(rightColumn, "TextArea demo", DESTINATION_TEXT_AREA);
        mountButton(rightColumn, "Transform+Clip demo", DESTINATION_TRANSFORM);
        mountButton(rightColumn, "FBO Perf Baseline", DESTINATION_PERF);

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
     * 判断目标是否为 Controls demo。
     *
     * @param destination 目标标识
     * @return true 表示 Controls demo
     */
    static boolean isControlsDestination(String destination) {
        return DESTINATION_CONTROLS.equals(destination);
    }

    /**
     * 判断目标是否为 SceneScrollContainer demo。
     *
     * @param destination 目标标识
     * @return true 表示 SceneScrollContainer demo
     */
    static boolean isScrollContainerDestination(String destination) {
        return DESTINATION_SCROLL_CONTAINER.equals(destination);
    }

    /**
     * 判断目标是否为 DataTable demo。
     *
     * @param destination 目标标识
     * @return true 表示 DataTable demo
     */
    static boolean isDataTableDestination(String destination) {
        return DESTINATION_DATA_TABLE.equals(destination);
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

    /**
     * 判断目标是否为 Form demo。
     *
     * @param destination 目标标识
     * @return true 表示 Form demo
     */
    static boolean isFormDestination(String destination) {
        return DESTINATION_FORM.equals(destination);
    }

    /**
     * 判断目标是否为 Select demo。
     *
     * @param destination 目标标识
     * @return true 表示 Select demo
     */
    static boolean isSelectDestination(String destination) {
        return DESTINATION_SELECT.equals(destination);
    }

    /**
     * 判断目标是否为 SimpleList demo。
     *
     * @param destination 目标标识
     * @return true 表示 SimpleList demo
     */
    static boolean isSimpleListDestination(String destination) {
        return DESTINATION_SIMPLE_LIST.equals(destination);
    }

    /**
     * 判断目标是否为 KeyValueMap demo。
     *
     * @param destination 目标标识
     * @return true 表示 KeyValueMap demo
     */
    static boolean isKeyValueMapDestination(String destination) {
        return DESTINATION_KEY_VALUE_MAP.equals(destination);
    }

    /**
     * 判断目标是否为压力测试 demo。
     *
     * @param destination 目标标识
     * @return true 表示压力测试 demo
     */
    static boolean isStressTestDestination(String destination) {
        return DESTINATION_STRESS_TEST.equals(destination);
    }

    /**
     * 判断目标是否为 ObjectField demo。
     *
     * @param destination 目标标识
     * @return true 表示 ObjectField demo
     */
    static boolean isObjectFieldDestination(String destination) {
        return DESTINATION_OBJECT_FIELD.equals(destination);
    }

    /**
     * 判断目标是否为 TextArea demo。
     *
     * @param destination 目标标识
     * @return true 表示 TextArea demo
     */
    static boolean isTextAreaDestination(String destination) {
        return DESTINATION_TEXT_AREA.equals(destination);
    }

    /**
     * 判断目标是否为 Transform+Clip demo。
     *
     * @param destination 目标标识
     * @return true 表示 Transform+Clip demo
     */
    static boolean isTransformDestination(String destination) {
        return DESTINATION_TRANSFORM.equals(destination);
    }

    /**
     * 判断目标是否为 FBO 性能基线实测页。
     *
     * @param destination 目标标识
     * @return true 表示 FBO 性能基线实测页
     */
    static boolean isPerfDestination(String destination) {
        return DESTINATION_PERF.equals(destination);
    }

    @Override
    protected SceneNode getRoot() {
        return root;
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
     * @param parent 按钮挂载的父容器
     * @param label 按钮文案
     * @param destination 导航目标标识
     */
    private void mountButton(SceneNode parent, String label, String destination) {
        SceneButton.Props props = new SceneButton.Props(
                Signal.create(label),
                Signal.create(Boolean.TRUE),
                () -> requestedDestinationSignal.set(destination));
        MountHandle handle = runtime.mount(parent, SceneButton.create(runtime, props));
        SceneNode row = handle.getRoot();
        row.setPreferredWidth(220);
        row.setPreferredHeight(40);
    }
}
