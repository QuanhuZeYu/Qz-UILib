package club.heiqi.uilib.internal.devtools.pages;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;
import club.heiqi.uilib.ui.scene.control.SceneObjectField;
import club.heiqi.uilib.ui.scene.control.SceneScrollbar;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 新栈 ui.scene 对象字段 demo 宿主 Widget。
 */
public class SceneObjectFieldHostWidget extends AbstractSceneHostWidget {

    private static final int STATUS_BG = 0xFF111C31;
    private static final int TITLE_BAR_HEIGHT = 44;
    private static final int STATUS_HEIGHT = 34;
    private static final int SCROLL_GAP = 3;

    private final SceneNode root;
    private final SceneNode viewport;
    private final SceneNode scrollContainer;
    private final SceneNode scrollbarColumn;
    private final SceneNode content;
    private final Signal<Integer> scrollSignal;
    private final Signal<Map<String, Object>> basicValue;
    private final Signal<Map<String, Object>> deepValue;
    private final Signal<Map<String, Object>> limitedValue;
    private final Signal<Map<String, Object>> emptyValue;
    private final Signal<Set<String>> expandedPaths;
    private final Signal<Integer> valueChangedCount;

    /**
     * 创建对象字段 demo 宿主 Widget。
     *
     * @param inputSource 平台输入源，可为 null（退化模式）
     */
    public SceneObjectFieldHostWidget(PlatformInputSource inputSource) {
        super(inputSource);
        this.basicValue = Signal.create(createBasicValue());
        this.deepValue = Signal.create(createDeepValue());
        this.limitedValue = Signal.create(createLimitedValue());
        this.emptyValue = Signal.create(Collections.<String, Object>emptyMap());
        this.expandedPaths = Signal.create(createInitialExpandedPaths());
        this.valueChangedCount = Signal.create(Integer.valueOf(0));

        this.root = createRoot();
        root.appendChild(createTitleBar());
        this.viewport = createViewport();
        this.content = createContent();
        viewport.appendChild(content);
        this.scrollContainer = createScrollContainer();
        scrollContainer.appendChild(viewport);
        root.appendChild(scrollContainer);
        root.appendChild(createStatusBar());

        content.appendChild(createObjectFieldCard("基础用法", "标量字段 + database 嵌套对象，默认展开。",
                basicValue, "服务器配置", SceneObjectField.MAX_DEPTH));
        content.appendChild(createObjectFieldCard("深层嵌套", "三层对象递归展开 / 折叠。",
                deepValue, "世界生成配置", SceneObjectField.MAX_DEPTH));
        content.appendChild(createObjectFieldCard("深度限制", "六层嵌套对象，maxDepth=5 时显示深度占位提示。",
                limitedValue, "深度限制配置", 5));
        content.appendChild(createObjectFieldCard("空对象", "空 Map 展示空对象提示。",
                emptyValue, "空对象配置", SceneObjectField.MAX_DEPTH));

        this.scrollSignal = SceneScrolls.attach(runtime, viewport);

        // 滚动条叠加在 viewport 右侧（scrollContainer ROW 内独立列），照 ConfigScreen 范式。
        SceneScrollbar.Props sbProps = new SceneScrollbar.Props(
                viewport, scrollSignal, scrollSignal::set,
                SceneScrollbar.DEFAULT_TRACK_COLOR, SceneScrollbar.DEFAULT_THUMB_COLOR,
                SceneScrollbar.DEFAULT_BAR_WIDTH, SceneScrollbar.DEFAULT_MIN_THUMB_HEIGHT);
        SceneScrollbar.Result sb = SceneScrollbar.create(runtime, sbProps);
        this.scrollbarColumn = sb.column();
        scrollContainer.appendChild(scrollbarColumn);

        runtime.flush();
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
        node.setBackgroundColor(SceneDemoTokens.ROOT_BG);
        return node;
    }

    /**
     * 创建标题条。
     *
     * @return 标题条节点
     */
    private SceneNode createTitleBar() {
        SceneNode titleBar = SceneNode.column();
        titleBar.setPreferredHeight(TITLE_BAR_HEIGHT);
        titleBar.setGap(4);
        titleBar.setHitTestable(false);
        titleBar.appendChild(text("SceneObjectField Demo", SceneDemoTokens.TITLE_COLOR));
        titleBar.appendChild(text("递归对象编辑 · 展开路径受控 · 深度限制与空对象提示", SceneDemoTokens.MUTED_COLOR));
        return titleBar;
    }

    /**
     * 创建滚动视口。
     *
     * @return 视口节点
     */
    private SceneNode createViewport() {
        SceneNode node = SceneNode.column();
        node.setFillParentHeight(true);
        node.setFlexGrow(1);
        node.setScrollable(true);
        node.setClipChildren(true);
        node.setPadding(14);
        node.setGap(14);
        node.setBackgroundColor(SceneDemoTokens.VIEWPORT_BG);
        node.setCornerRadius(10);
        return node;
    }

    /**
     * 创建滚动容器（ROW：viewport + scrollbar 列），照 ConfigScreen 范式。
     *
     * @return 滚动容器节点
     */
    private SceneNode createScrollContainer() {
        SceneNode node = SceneNode.row();
        node.setFillParentHeight(true);
        node.setGap(SCROLL_GAP);
        return node;
    }

    /**
     * 创建内容容器。
     *
     * @return 内容节点
     */
    private SceneNode createContent() {
        SceneNode node = SceneNode.column();
        node.setGap(14);
        return node;
    }

    /**
     * 创建对象字段卡片。
     *
     * @param title 卡片标题
     * @param helper 帮助说明
     * @param value 对象值 signal
     * @param label 控件标题
     * @param maxDepth 最大递归深度
     * @return 卡片节点
     */
    private SceneNode createObjectFieldCard(String title, String helper, Signal<Map<String, Object>> value,
            String label, int maxDepth) {
        SceneNode card = createCardShell(title, helper);
        SceneObjectField.Props props = SceneObjectField.Props.builder(value)
                .expandedPaths(expandedPaths)
                .label(label)
                .maxDepth(maxDepth)
                .onValueChanged(next -> valueChangedCount.set(Integer.valueOf(valueChangedCount.get().intValue() + 1)))
                .build();
        runtime.mount(card, SceneObjectField.create(runtime, props));
        return card;
    }

    /**
     * 创建卡片外壳。
     *
     * @param title 卡片标题
     * @param helper 帮助说明
     * @return 卡片节点
     */
    private SceneNode createCardShell(String title, String helper) {
        return SceneDemoCards.cardShell(title, helper);
    }

    /**
     * 创建底部状态条。
     *
     * @return 状态条节点
     */
    private SceneNode createStatusBar() {
        SceneNode row = SceneNode.row();
        row.setPreferredHeight(STATUS_HEIGHT);
        row.setPadding(8);
        row.setGap(12);
        row.setBackgroundColor(STATUS_BG);
        row.setCornerRadius(8);
        row.appendChild(boundText(Computed.create(() -> "基础对象根字段数: " + safeSize(basicValue.get())), SceneDemoTokens.TEXT_COLOR));
        row.appendChild(boundText(Computed.create(() -> "onValueChanged 次数: " + valueChangedCount.get()), SceneDemoTokens.TEXT_COLOR));
        return row;
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

    /**
     * 创建绑定文字节点。
     *
     * @param value 文本 signal
     * @param color 文本颜色
     * @return 绑定文字节点
     */
    private SceneNode boundText(Computed<String> value, int color) {
        SceneNode node = text("", color);
        runtime.bind(value, node::setText);
        return node;
    }

    /**
     * 创建基础示例对象。
     *
     * @return 基础示例对象
     */
    private static Map<String, Object> createBasicValue() {
        Map<String, Object> database = new LinkedHashMap<String, Object>();
        database.put("host", "localhost");
        database.put("port", Integer.valueOf(3306));
        database.put("ssl", Boolean.TRUE);

        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("serverName", "my-server");
        value.put("maxPlayers", Integer.valueOf(20));
        value.put("debug", Boolean.FALSE);
        value.put("database", database);
        return Collections.unmodifiableMap(value);
    }

    /**
     * 创建三层嵌套示例对象。
     *
     * @return 三层嵌套对象
     */
    private static Map<String, Object> createDeepValue() {
        Map<String, Object> ores = new LinkedHashMap<String, Object>();
        ores.put("enabled", Boolean.TRUE);
        ores.put("density", Integer.valueOf(12));

        Map<String, Object> caves = new LinkedHashMap<String, Object>();
        caves.put("size", Integer.valueOf(4));
        caves.put("ores", ores);

        Map<String, Object> overworld = new LinkedHashMap<String, Object>();
        overworld.put("seed", "demo-seed");
        overworld.put("caves", caves);

        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("world", overworld);
        value.put("dimension", "overworld");
        return Collections.unmodifiableMap(value);
    }

    /**
     * 创建超过显示深度的嵌套对象。
     *
     * @return 六层嵌套对象
     */
    private static Map<String, Object> createLimitedValue() {
        Map<String, Object> layer6 = new LinkedHashMap<String, Object>();
        layer6.put("leaf", "too-deep");
        Map<String, Object> layer5 = singletonObject("layer6", layer6);
        Map<String, Object> layer4 = singletonObject("layer5", layer5);
        Map<String, Object> layer3 = singletonObject("layer4", layer4);
        Map<String, Object> layer2 = singletonObject("layer3", layer3);
        Map<String, Object> layer1 = singletonObject("layer2", layer2);
        return Collections.unmodifiableMap(layer1);
    }

    /**
     * 创建单字段对象。
     *
     * @param key 字段名
     * @param value 字段值
     * @return 单字段对象
     */
    private static Map<String, Object> singletonObject(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put(key, value);
        return map;
    }

    /**
     * 创建默认展开路径集合。
     *
     * @return 默认展开路径集合
     */
    private static Set<String> createInitialExpandedPaths() {
        Set<String> paths = new LinkedHashSet<String>();
        paths.add("database");
        paths.add("world");
        paths.add("world.caves");
        paths.add("world.caves.ores");
        paths.add("layer2");
        paths.add("layer2.layer3");
        paths.add("layer2.layer3.layer4");
        paths.add("layer2.layer3.layer4.layer5");
        paths.add("layer2.layer3.layer4.layer5.layer6");
        return Collections.unmodifiableSet(paths);
    }

    /**
     * 获取 Map 安全长度。
     *
     * @param value 对象 Map
     * @return 字段数
     */
    private static int safeSize(Map<String, Object> value) {
        return value == null ? 0 : value.size();
    }

    @Override
    protected SceneNode getRoot() {
        return root;
    }

    /** @return 滚动容器节点（ROW：viewport + scrollbarColumn） */
    SceneNode __getScrollContainer() {
        return scrollContainer;
    }

    /** @return 滚动条列节点（scrollContainer 内 viewport 右侧独立列） */
    SceneNode __getScrollbarColumn() {
        return scrollbarColumn;
    }
}
