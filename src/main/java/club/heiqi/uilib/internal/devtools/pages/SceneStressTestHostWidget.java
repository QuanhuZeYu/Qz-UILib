package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.scene.host.AbstractSceneHostWidget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneKeyValueMap;
import club.heiqi.uilib.ui.scene.control.SceneObjectField;
import club.heiqi.uilib.ui.scene.control.SceneSimpleList;
import club.heiqi.uilib.ui.scene.control.SceneTab;
import club.heiqi.uilib.ui.scene.control.SceneTextArea;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 新栈 ui.scene 大数据压力测试宿主 Widget。
 *
 * <p>本页同时挂载 {@link SceneSimpleList}、{@link SceneKeyValueMap}、{@link SceneObjectField} 与 {@link SceneTextArea} 的压力场景，
 * 通过受控 signal 批量替换数据源，并用 {@link club.heiqi.uilib.ui.scene.layout.LayoutResult#getRelayoutCount()}
 * 观察最近一帧布局重算次数。</p>
 */
public class SceneStressTestHostWidget extends AbstractSceneHostWidget {

    private static final int INITIAL_ROW_COUNT = 200;
    private static final int INITIAL_TEXTAREA_LINE_COUNT = 500;
    private static final int ADD_BATCH_COUNT = 100;
    private static final int DELETE_BATCH_COUNT = 50;
    private static final int ROOT_BG = 0xFF08111F;
    private static final int MONITOR_BG = 0xFF111C31;
    private static final int TITLE_BAR_HEIGHT = 44;
    private static final int MONITOR_BAR_HEIGHT = 36;
    private static final int ACTION_BAR_HEIGHT = 46;

    private final SceneNode root;
    private final Signal<Integer> activeTabSignal;
    private final Signal<Integer> relayoutCountSignal;
    private final Signal<List<SceneSimpleList.ListItem>> simpleListItems;
    private final Signal<List<SceneKeyValueMap.KeyValueRow>> keyValueRows;
    private final Signal<Map<String, Object>> objectFieldValue;
    private final Signal<Set<String>> objectFieldExpandedPaths;
    private final Signal<String> textAreaValue;

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
        this.objectFieldValue = Signal.create(createObjectFieldValue(INITIAL_ROW_COUNT, 0));
        this.objectFieldExpandedPaths = Signal.create(createObjectFieldExpandedPaths(INITIAL_ROW_COUNT));
        this.textAreaValue = Signal.create(createTextAreaLines(INITIAL_TEXTAREA_LINE_COUNT, 0));

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
        relayoutCountSignal.set(Integer.valueOf(lastLayoutResult != null ? lastLayoutResult.getRelayoutCount() : 0));
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
        SceneNode titleBar = SceneNode.column();
        titleBar.setPreferredHeight(TITLE_BAR_HEIGHT);
        titleBar.setGap(4);
        titleBar.setHitTestable(false);
        titleBar.appendChild(SceneDemoCards.text("Scene 压力测试", SceneDemoTokens.TEXT_COLOR));
        titleBar.appendChild(SceneDemoCards.text("大量行数据下验证 SceneSimpleList / SceneKeyValueMap / SceneObjectField 的协调与布局成本", SceneDemoTokens.MUTED_COLOR));
        return titleBar;
    }

    /**
     * 创建性能监测条。
     *
     * @return 性能监测条节点
     */
    private SceneNode createMonitorBar() {
        SceneNode row = SceneNode.row();
        row.setPreferredHeight(MONITOR_BAR_HEIGHT);
        row.setPadding(8);
        row.setGap(12);
        row.setBackgroundColor(MONITOR_BG);
        row.setCornerRadius(8);
        row.appendChild(boundText(Computed.create(() -> "relayoutCount: " + relayoutCountSignal.get()), SceneDemoTokens.TEXT_COLOR));
        row.appendChild(boundText(Computed.create(() -> "SimpleList 行数: " + safeSize(simpleListItems.get())), SceneDemoTokens.TEXT_COLOR));
        row.appendChild(boundText(Computed.create(() -> "KeyValueMap 行数: " + safeSize(keyValueRows.get())), SceneDemoTokens.TEXT_COLOR));
        row.appendChild(boundText(Computed.create(() -> "ObjectField 字段数: " + safeSize(objectFieldValue.get())), SceneDemoTokens.TEXT_COLOR));
        row.appendChild(boundText(Computed.create(() -> "TextArea 行数: " + countTextAreaLines(textAreaValue.get())), SceneDemoTokens.TEXT_COLOR));
        return row;
    }

    /**
     * 创建 Tab 切换区和内容区。
     *
     * @return Tab 根节点
     */
    private SceneNode createTabArea() {
        List<String> labels = Arrays.asList("SimpleList 压力", "KeyValueMap 压力", "ObjectField 压力", "TextArea 压力");
        List<Supplier<SceneNode>> panels = Arrays.asList(
                this::createSimpleListPanel,
                this::createKeyValueMapPanel,
                this::createObjectFieldPanel,
                this::createTextAreaPanel);
        SceneTab.Props props = new SceneTab.Props(
                activeTabSignal,
                labels,
                panels,
                Signal.create(Boolean.TRUE),
                next -> activeTabSignal.set(next),
                true);  // fillContentPanel=true：内容区填满 holder（各页 createContentPanel 已 setFillParentHeight）
        SceneNode holder = SceneNode.column();
        holder.setFillParentHeight(true);
        holder.setBackgroundColor(SceneDemoTokens.CARD_BG);
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
     * 创建 ObjectField 压力内容页。
     *
     * @return ObjectField 内容节点
     */
    private SceneNode createObjectFieldPanel() {
        SceneNode panel = createContentPanel();
        SceneObjectField.Props props = SceneObjectField.Props.builder(objectFieldValue)
                .label("ObjectField 受控对象字段")
                .expandedPaths(objectFieldExpandedPaths)
                .onValueChanged(next -> objectFieldValue.set(next))
                .build();
        runtime.mount(panel, SceneObjectField.create(runtime, props));
        return panel;
    }

    /**
     * 创建 TextArea 压力内容页。
     *
     * <p>受控 value 为含 {@code \n} 的多行文本，初始 500 行。TextArea 自带 scrollable viewport，
     * 可滚动浏览；可点击编辑任意行，验证大量行下编辑时帧率与重排成本。</p>
     *
     * @return TextArea 内容节点
     */
    private SceneNode createTextAreaPanel() {
        SceneNode panel = createContentPanel();
        SceneTextArea.Props props = SceneTextArea.Props.builder(textAreaValue)
                .placeholder("压力测试文本区，可编辑任意行...")
                .maxLength(Integer.MAX_VALUE)
                .viewportHeight(600)
                .onChange(next -> textAreaValue.set(next))
                .build();
        runtime.mount(panel, SceneTextArea.create(runtime, props));
        return panel;
    }

    /**
     * 创建内容页容器。
     *
     * @return 内容页容器
     */
    private SceneNode createContentPanel() {
        // 四个内嵌控件（SimpleList/KeyValueMap/ObjectField/TextArea）均自带 scrollable viewport + SceneScrolls.attach，
        // 外层 panel 无需再设 scrollable/clip，否则会与内层视口形成嵌套滚动冲突。
        SceneNode panel = SceneNode.column();
        panel.setFillParentHeight(true);
        panel.setPadding(10);
        panel.setBackgroundColor(SceneDemoTokens.VIEWPORT_BG);
        panel.setCornerRadius(8);
        return panel;
    }

    /**
     * 创建底部批量操作区。
     *
     * @return 操作区节点
     */
    private SceneNode createActionBar() {
        SceneNode row = SceneNode.row();
        row.setPreferredHeight(ACTION_BAR_HEIGHT);
        row.setGap(10);
        mountButton(row, "批量添加 100 行", this::addRowsToActiveTab);
        mountButton(row, "批量删除 50 行", this::deleteRowsFromActiveTab);
        mountButton(row, "清空", this::clearActiveTab);
        mountButton(row, "重置初始行数", this::resetActiveTab);
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
        if (isTextAreaTabActive()) {
            int start = countTextAreaLines(safeTextAreaValue());
            String current = safeTextAreaValue();
            String next = current.isEmpty() ? createTextAreaLines(ADD_BATCH_COUNT, start)
                    : current + "\n" + createTextAreaLines(ADD_BATCH_COUNT, start);
            textAreaValue.set(next);
            return;
        }
        if (isObjectFieldTabActive()) {
            int start = safeSize(objectFieldValue.get());
            Map<String, Object> next = new LinkedHashMap<String, Object>(safeObjectFieldValue());
            next.putAll(createObjectFieldValue(ADD_BATCH_COUNT, start));
            objectFieldValue.set(Collections.unmodifiableMap(next));
            return;
        }
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
        if (isTextAreaTabActive()) {
            textAreaValue.set(trimTextAreaTail(safeTextAreaValue(), DELETE_BATCH_COUNT));
        } else if (isObjectFieldTabActive()) {
            objectFieldValue.set(trimObjectFields(safeObjectFieldValue(), DELETE_BATCH_COUNT));
        } else if (isKeyValueTabActive()) {
            keyValueRows.set(trimTail(safeKeyValueRows(), DELETE_BATCH_COUNT));
        } else {
            simpleListItems.set(trimTail(safeListItems(), DELETE_BATCH_COUNT));
        }
    }

    /** 清空当前 Tab 的行数据。 */
    private void clearActiveTab() {
        if (isTextAreaTabActive()) {
            textAreaValue.set("");
        } else if (isObjectFieldTabActive()) {
            objectFieldValue.set(Collections.<String, Object>emptyMap());
        } else if (isKeyValueTabActive()) {
            keyValueRows.set(Collections.<SceneKeyValueMap.KeyValueRow>emptyList());
        } else {
            simpleListItems.set(Collections.<SceneSimpleList.ListItem>emptyList());
        }
    }

    /** 重置当前 Tab 为初始行数。 */
    private void resetActiveTab() {
        if (isTextAreaTabActive()) {
            textAreaValue.set(createTextAreaLines(INITIAL_TEXTAREA_LINE_COUNT, 0));
        } else if (isObjectFieldTabActive()) {
            objectFieldValue.set(createObjectFieldValue(INITIAL_ROW_COUNT, 0));
            objectFieldExpandedPaths.set(createObjectFieldExpandedPaths(INITIAL_ROW_COUNT));
        } else if (isKeyValueTabActive()) {
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
     * 判断 ObjectField Tab 是否激活。
     *
     * @return true 表示当前为 ObjectField Tab
     */
    private boolean isObjectFieldTabActive() {
        return activeTabSignal.get() != null && activeTabSignal.get().intValue() == 2;
    }

    /**
     * 判断 TextArea Tab 是否激活。
     *
     * @return true 表示当前为 TextArea Tab
     */
    private boolean isTextAreaTabActive() {
        return activeTabSignal.get() != null && activeTabSignal.get().intValue() == 3;
    }

    /**
     * 创建绑定文字节点。
     *
     * @param value 文本 signal
     * @param color 文本颜色
     * @return 绑定文字节点
     */
    private SceneNode boundText(Computed<String> value, int color) {
        SceneNode node = SceneDemoCards.text("", color);
        runtime.bind(value, node::setText);
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
     * 创建 ObjectField 测试数据。
     *
     * @param count 字段数
     * @param startIndex 起始下标
     * @return 不可变字段 Map
     */
    private static Map<String, Object> createObjectFieldValue(int count, int startIndex) {
        Map<String, Object> fields = new LinkedHashMap<String, Object>(count);
        for (int i = 0; i < count; i++) {
            int index = startIndex + i;
            if (index % 10 == 0) {
                fields.put("objectField" + index, createNestedObject(index));
            } else if (index % 3 == 0) {
                fields.put("booleanField" + index, Boolean.valueOf(index % 2 == 0));
            } else if (index % 3 == 1) {
                fields.put("numberField" + index, Integer.valueOf(index));
            } else {
                fields.put("stringField" + index, "value-" + index);
            }
        }
        return Collections.unmodifiableMap(fields);
    }

    /**
     * 创建 ObjectField 嵌套对象字段。
     *
     * @param index 字段序号
     * @return 嵌套对象
     */
    private static Map<String, Object> createNestedObject(int index) {
        Map<String, Object> child = new LinkedHashMap<String, Object>();
        child.put("enabled", Boolean.valueOf(index % 2 == 0));
        child.put("limit", Integer.valueOf(index));

        Map<String, Object> parent = new LinkedHashMap<String, Object>();
        parent.put("name", "nested-" + index);
        parent.put("child", child);
        return parent;
    }

    /**
     * 创建 ObjectField 默认展开路径。
     *
     * @param count 初始字段数
     * @return 不可变展开路径集合
     */
    private static Set<String> createObjectFieldExpandedPaths(int count) {
        Set<String> paths = new LinkedHashSet<String>();
        for (int i = 0; i < count; i += 10) {
            paths.add("objectField" + i);
            paths.add("objectField" + i + ".child");
        }
        return Collections.unmodifiableSet(paths);
    }

    /**
     * 创建 TextArea 压力测试多行文本。
     *
     * <p>每行约 50-80 字符的中英文混合文本，行间以 {@code \n} 分隔。
     * 用于验证 TextArea 文本几何从 O(N²) 优化到 O(N) 后的帧率收益。</p>
     *
     * @param count 行数
     * @param startIndex 起始行号
     * @return 含换行符的多行文本
     */
    private static String createTextAreaLines(int count, int startIndex) {
        if (count <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(count * 80);
        for (int i = 0; i < count; i++) {
            int index = startIndex + i;
            if (i > 0) {
                sb.append('\n');
            }
            sb.append("第 ").append(index).append(" 行：这是一段测试文本用于验证 TextArea 在大量行下的渲染性能，")
                    .append("包含中英文 mixed content 与数字 ").append(index * 7)
                    .append("，用于压力测试。");
        }
        return sb.toString();
    }

    /**
     * 删除 TextArea 多行文本末尾指定行数。
     *
     * @param value 原文本（含 {@code \n}）
     * @param count 删除行数
     * @return 删除后的文本
     */
    private static String trimTextAreaTail(String value, int count) {
        String t = value == null ? "" : value;
        if (t.isEmpty() || count <= 0) {
            return t;
        }
        String[] lines = t.split("\n", -1);
        int nextSize = Math.max(0, lines.length - count);
        if (nextSize == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(t.length());
        for (int i = 0; i < nextSize; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    /**
     * 统计 TextArea 多行文本行数。
     *
     * <p>空串记为 0 行；非空文本按 {@code \n} 计数，末尾换行视为最后一空行。</p>
     *
     * @param value 文本
     * @return 行数
     */
    private static int countTextAreaLines(String value) {
        String t = value == null ? "" : value;
        if (t.isEmpty()) {
            return 0;
        }
        int lines = 1;
        for (int i = 0; i < t.length(); i++) {
            if (t.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
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
     * 删除 Map 尾部指定数量字段。
     *
     * @param fields 原字段 Map
     * @param count 删除数量
     * @return 删除后的不可变 Map
     */
    private static Map<String, Object> trimObjectFields(Map<String, Object> fields, int count) {
        Map<String, Object> next = new LinkedHashMap<String, Object>(fields);
        List<String> keys = new ArrayList<String>(next.keySet());
        int deleteCount = Math.min(count, keys.size());
        for (int i = 0; i < deleteCount; i++) {
            next.remove(keys.get(keys.size() - 1 - i));
        }
        return Collections.unmodifiableMap(next);
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
     * null 安全读取 ObjectField 字段。
     *
     * @return ObjectField 字段 Map
     */
    private Map<String, Object> safeObjectFieldValue() {
        Map<String, Object> fields = objectFieldValue.get();
        return fields == null ? Collections.<String, Object>emptyMap() : fields;
    }

    /**
     * null 安全读取 TextArea 文本。
     *
     * @return TextArea 文本
     */
    private String safeTextAreaValue() {
        String value = textAreaValue.get();
        return value == null ? "" : value;
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

    /**
     * 获取 Map 安全长度。
     *
     * @param value 字段 Map
     * @return 字段数
     */
    private static int safeSize(Map<String, Object> value) {
        return value == null ? 0 : value.size();
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

    /** @return ObjectField 字段数据源 */
    Signal<Map<String, Object>> __getObjectFieldValue() {
        return objectFieldValue;
    }

    /** @return TextArea 文本数据源 */
    Signal<String> __getTextAreaValue() {
        return textAreaValue;
    }

    /** @return relayoutCount 显示源 */
    Signal<Integer> __getRelayoutCountSignal() {
        return relayoutCountSignal;
    }
}
