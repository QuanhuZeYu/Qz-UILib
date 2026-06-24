package club.heiqi.uilib.internal.devtools.pages;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneKeyValueMap;
import club.heiqi.uilib.ui.scene.control.SceneSimpleList;
import club.heiqi.uilib.ui.scene.control.SceneTab;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.node.Invalidation;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 新栈 ui.scene 大数据压力测试宿主 Widget。
 *
 * <p>本页同时挂载 {@link SceneSimpleList} 与 {@link SceneKeyValueMap} 的压力场景，
 * 通过受控 signal 批量替换数据源，并用 {@link club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine#__getRelayoutCount()}
 * 观察最近一帧布局重算次数。</p>
 */
public class SceneStressTestHostWidget extends AbstractSceneHostWidget {

    private static final int INITIAL_ROW_COUNT = 200;
    private static final int ADD_BATCH_COUNT = 100;
    private static final int DELETE_BATCH_COUNT = 50;
    private static final int ROOT_BG = 0xFF08111F;
    private static final int TITLE_COLOR = 0xFFEAF1FF;
    private static final int MUTED_COLOR = 0xFF8AA0C8;
    private static final int PANEL_BG = 0xFF0D1728;
    private static final int MONITOR_BG = 0xFF111C31;
    private static final int TEXT_COLOR = 0xFFEAF1FF;
    private static final int TITLE_BAR_HEIGHT = 44;
    private static final int MONITOR_BAR_HEIGHT = 36;
    private static final int ACTION_BAR_HEIGHT = 46;

    private final SceneNode root;
    private final Signal<Integer> activeTabSignal;
    private final Signal<Integer> relayoutCountSignal;
    private final Signal<List<SceneSimpleList.ListItem>> simpleListItems;
    private final Signal<List<SceneKeyValueMap.KeyValueRow>> keyValueRows;

    /**
     * 创建压力测试宿主 Widget。
     *
     * @param inputSource 平台输入源，可为 null（退化模式）
     */
    public SceneStressTestHostWidget(PlatformInputSource inputSource) {
        super(inputSource);
        this.activeTabSignal = Signal.create(Integer.valueOf(0));
        this.relayoutCountSignal = Signal.create(Integer.valueOf(0));
        this.simpleListItems = Signal.create(createListItems(INITIAL_ROW_COUNT, 0));
        this.keyValueRows = Signal.create(createKeyValueRows(INITIAL_ROW_COUNT, 0));

        this.root = createRoot();
        root.appendChild(createTitleBar());
        root.appendChild(createMonitorBar());
        root.appendChild(createTabArea());
        root.appendChild(createActionBar());

        runtime.flush();
    }

    /**
     * 每帧渲染后更新布局重算探针。
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
        relayoutCountSignal.set(Integer.valueOf(getLayoutEngine().__getRelayoutCount()));
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
        titleBar.appendChild(text("Scene 压力测试", TITLE_COLOR));
        titleBar.appendChild(text("大量行数据下验证 SceneSimpleList / SceneKeyValueMap 的协调与布局成本", MUTED_COLOR));
        return titleBar;
    }

    /**
     * 创建性能监测条。
     *
     * @return 性能监测条节点
     */
    private SceneNode createMonitorBar() {
        SceneNode row = new SceneNode();
        row.setFlexDirection(FlexDirection.ROW);
        row.setPreferredHeight(MONITOR_BAR_HEIGHT);
        row.setPadding(8);
        row.setGap(12);
        row.setBackgroundColor(MONITOR_BG);
        row.setCornerRadius(8);
        row.appendChild(boundText(Computed.create(() -> "relayoutCount: " + relayoutCountSignal.get()), TEXT_COLOR));
        row.appendChild(boundText(Computed.create(() -> "SimpleList 行数: " + safeSize(simpleListItems.get())), TEXT_COLOR));
        row.appendChild(boundText(Computed.create(() -> "KeyValueMap 行数: " + safeSize(keyValueRows.get())), TEXT_COLOR));
        return row;
    }

    /**
     * 创建 Tab 切换区和内容区。
     *
     * @return Tab 根节点
     */
    private SceneNode createTabArea() {
        List<String> labels = Arrays.asList("SimpleList 压力", "KeyValueMap 压力");
        List<Supplier<SceneNode>> panels = Arrays.asList(
                this::createSimpleListPanel,
                this::createKeyValueMapPanel);
        SceneTab.Props props = new SceneTab.Props(
                activeTabSignal,
                labels,
                panels,
                Signal.create(Boolean.TRUE),
                next -> activeTabSignal.set(next));
        SceneNode holder = new SceneNode();
        holder.setFlexDirection(FlexDirection.COLUMN);
        holder.setFillParentHeight(true);
        holder.setBackgroundColor(PANEL_BG);
        holder.setCornerRadius(10);
        holder.setPadding(12);
        runtime.mount(holder, SceneTab.create(runtime, props));
        return holder;
    }

    /**
     * 创建 SimpleList 压力内容页。
     *
     * @return SimpleList 内容节点
     */
    private SceneNode createSimpleListPanel() {
        SceneNode panel = createContentPanel();
        SceneSimpleList.Props props = SceneSimpleList.Props.builder(simpleListItems)
                .label("SimpleList 受控行列表")
                .placeholder("列表项文本")
                .onItemsChanged(next -> simpleListItems.set(next))
                .build();
        runtime.mount(panel, SceneSimpleList.create(runtime, props));
        return panel;
    }

    /**
     * 创建 KeyValueMap 压力内容页。
     *
     * @return KeyValueMap 内容节点
     */
    private SceneNode createKeyValueMapPanel() {
        SceneNode panel = createContentPanel();
        SceneKeyValueMap.Props props = SceneKeyValueMap.Props.builder(keyValueRows)
                .label("KeyValueMap 受控行列表")
                .keyPlaceholder("key")
                .valuePlaceholder("value")
                .onRowsChanged(next -> keyValueRows.set(next))
                .build();
        runtime.mount(panel, SceneKeyValueMap.create(runtime, props));
        return panel;
    }

    /**
     * 创建内容页容器。
     *
     * @return 内容页容器
     */
    private SceneNode createContentPanel() {
        SceneNode panel = new SceneNode();
        panel.setFlexDirection(FlexDirection.COLUMN);
        panel.setFillParentHeight(true);
        panel.setScrollable(true);
        panel.setClipChildren(true);
        panel.setPadding(10);
        panel.setBackgroundColor(0xFF081120);
        panel.setCornerRadius(8);
        return panel;
    }

    /**
     * 创建底部批量操作区。
     *
     * @return 操作区节点
     */
    private SceneNode createActionBar() {
        SceneNode row = new SceneNode();
        row.setFlexDirection(FlexDirection.ROW);
        row.setPreferredHeight(ACTION_BAR_HEIGHT);
        row.setGap(10);
        mountButton(row, "批量添加 100 行", this::addRowsToActiveTab);
        mountButton(row, "批量删除 50 行", this::deleteRowsFromActiveTab);
        mountButton(row, "清空", this::clearActiveTab);
        mountButton(row, "重置为 200 行", this::resetActiveTab);
        return row;
    }

    /**
     * 挂载操作按钮。
     *
     * @param parent 父节点
     * @param label 按钮文案
     * @param onClick 点击回调
     */
    private void mountButton(SceneNode parent, String label, Runnable onClick) {
        SceneButton.Props props = new SceneButton.Props(Signal.create(label), Signal.create(Boolean.TRUE), onClick);
        SceneNode button = runtime.mount(parent, SceneButton.create(runtime, props)).getRoot();
        button.setPreferredWidth(132);
        button.setPreferredHeight(36);
    }

    /** 批量添加当前 Tab 的行数据。 */
    private void addRowsToActiveTab() {
        if (isKeyValueTabActive()) {
            int start = safeSize(keyValueRows.get());
            List<SceneKeyValueMap.KeyValueRow> next = new ArrayList<SceneKeyValueMap.KeyValueRow>(safeKeyValueRows());
            next.addAll(createKeyValueRows(ADD_BATCH_COUNT, start));
            keyValueRows.set(Collections.unmodifiableList(next));
            return;
        }
        int start = safeSize(simpleListItems.get());
        List<SceneSimpleList.ListItem> next = new ArrayList<SceneSimpleList.ListItem>(safeListItems());
        next.addAll(createListItems(ADD_BATCH_COUNT, start));
        simpleListItems.set(Collections.unmodifiableList(next));
    }

    /** 批量删除当前 Tab 末尾行数据。 */
    private void deleteRowsFromActiveTab() {
        if (isKeyValueTabActive()) {
            keyValueRows.set(trimTail(safeKeyValueRows(), DELETE_BATCH_COUNT));
        } else {
            simpleListItems.set(trimTail(safeListItems(), DELETE_BATCH_COUNT));
        }
    }

    /** 清空当前 Tab 的行数据。 */
    private void clearActiveTab() {
        if (isKeyValueTabActive()) {
            keyValueRows.set(Collections.<SceneKeyValueMap.KeyValueRow>emptyList());
        } else {
            simpleListItems.set(Collections.<SceneSimpleList.ListItem>emptyList());
        }
    }

    /** 重置当前 Tab 为初始 200 行。 */
    private void resetActiveTab() {
        if (isKeyValueTabActive()) {
            keyValueRows.set(createKeyValueRows(INITIAL_ROW_COUNT, 0));
        } else {
            simpleListItems.set(createListItems(INITIAL_ROW_COUNT, 0));
        }
    }

    /**
     * 判断 KeyValueMap Tab 是否激活。
     *
     * @return true 表示当前为 KeyValueMap Tab
     */
    private boolean isKeyValueTabActive() {
        return activeTabSignal.get() != null && activeTabSignal.get().intValue() == 1;
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
        runtime.bind(Invalidation.LAYOUT, value, node::setText);
        return node;
    }

    /**
     * 创建 SimpleList 测试数据。
     *
     * @param count 行数
     * @param startIndex 起始下标
     * @return 不可变行列表
     */
    private static List<SceneSimpleList.ListItem> createListItems(int count, int startIndex) {
        List<SceneSimpleList.ListItem> rows = new ArrayList<SceneSimpleList.ListItem>(count);
        for (int i = 0; i < count; i++) {
            int index = startIndex + i;
            rows.add(new SceneSimpleList.ListItem("List Item " + index + " / 压力测试文本"));
        }
        return Collections.unmodifiableList(rows);
    }

    /**
     * 创建 KeyValueMap 测试数据。
     *
     * @param count 行数
     * @param startIndex 起始下标
     * @return 不可变行列表
     */
    private static List<SceneKeyValueMap.KeyValueRow> createKeyValueRows(int count, int startIndex) {
        List<SceneKeyValueMap.KeyValueRow> rows = new ArrayList<SceneKeyValueMap.KeyValueRow>(count);
        for (int i = 0; i < count; i++) {
            int index = startIndex + i;
            rows.add(new SceneKeyValueMap.KeyValueRow(
                    "stress.key." + index,
                    "value-" + index,
                    SceneKeyValueMap.ValueType.STRING));
        }
        return Collections.unmodifiableList(rows);
    }

    /**
     * 删除列表尾部指定数量元素。
     *
     * @param rows 原列表
     * @param count 删除数量
     * @param <T> 行类型
     * @return 删除后的不可变列表
     */
    private static <T> List<T> trimTail(List<T> rows, int count) {
        int nextSize = Math.max(0, rows.size() - count);
        return Collections.unmodifiableList(new ArrayList<T>(rows.subList(0, nextSize)));
    }

    /**
     * null 安全读取 SimpleList 行。
     *
     * @return SimpleList 行列表
     */
    private List<SceneSimpleList.ListItem> safeListItems() {
        List<SceneSimpleList.ListItem> rows = simpleListItems.get();
        return rows == null ? Collections.<SceneSimpleList.ListItem>emptyList() : rows;
    }

    /**
     * null 安全读取 KeyValueMap 行。
     *
     * @return KeyValueMap 行列表
     */
    private List<SceneKeyValueMap.KeyValueRow> safeKeyValueRows() {
        List<SceneKeyValueMap.KeyValueRow> rows = keyValueRows.get();
        return rows == null ? Collections.<SceneKeyValueMap.KeyValueRow>emptyList() : rows;
    }

    /**
     * 获取列表安全长度。
     *
     * @param rows 行列表
     * @return 行数
     */
    private static int safeSize(List<?> rows) {
        return rows == null ? 0 : rows.size();
    }

    @Override
    protected SceneNode getRoot() {
        return root;
    }

    /** @return SimpleList 行数据源 */
    Signal<List<SceneSimpleList.ListItem>> __getSimpleListItems() {
        return simpleListItems;
    }

    /** @return KeyValueMap 行数据源 */
    Signal<List<SceneKeyValueMap.KeyValueRow>> __getKeyValueRows() {
        return keyValueRows;
    }

    /** @return relayoutCount 显示源 */
    Signal<Integer> __getRelayoutCountSignal() {
        return relayoutCountSignal;
    }
}
