package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.Invalidation;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.SceneNode.WidthSizing;

/**
 * SceneKeyValueMap —— scene 新栈动态键值对编辑器。
 *
 * <p>行列表由外部 {@code rows} signal 受控持有；控件只在输入、增删和类型切换时复制列表并写回
 * {@code rows.set(next)}，再通过回调通知字段引擎。列表渲染使用 keyed forEach，行 key 为
 * {@link KeyValueRow} 的稳定 id，编辑 key/value/type 不会改变列表身份。</p>
 */
public final class SceneKeyValueMap {

    /**
     * 类型段选项文本。
     */
    private static final List<String> TYPE_OPTIONS = Collections.unmodifiableList(
        Arrays.asList("String", "Number", "Boolean"));
    /**
     * 默认 key 占位符。
     */
    private static final String DEFAULT_KEY_PLACEHOLDER = "key";
    /**
     * 默认 value 占位符。
     */
    private static final String DEFAULT_VALUE_PLACEHOLDER = "value";
    /**
     * 行间距。
     */
    private static final int ROW_GAP = 6;
    /**
     * 根节点间距。
     */
    private static final int ROOT_GAP = 8;
    /**
     * 行内间距。
     */
    private static final int CELL_GAP = 6;
    /**
     * 普通行背景。
     */
    private static final int ROW_BG = 0x00000000;
    /**
     * 校验错误行背景。
     */
    private static final int ROW_ERROR_BG = 0x22EF4444;
    /**
     * 标题文本色。
     */
    private static final int LABEL_COLOR = 0xFFE2E8F0;
    /**
     * 表头文本色。
     */
    private static final int HEADER_COLOR = 0xFF94A3B8;
    /**
     * 按钮背景色。
     */
    private static final int BUTTON_BG = 0xFF334155;
    /**
     * 按钮悬停背景色。
     */
    private static final int BUTTON_BG_HOVER = 0xFF475569;
    /**
     * 按钮按压背景色。
     */
    private static final int BUTTON_BG_PRESSED = 0xFF1E293B;
    /**
     * 按钮禁用背景色。
     */
    private static final int BUTTON_BG_DISABLED = 0xFF1F2937;
    /**
     * 按钮文本色。
     */
    private static final int BUTTON_TEXT = 0xFFFFFFFF;
    /**
     * 按钮禁用文本色。
     */
    private static final int BUTTON_TEXT_DISABLED = 0xFF64748B;
    /**
     * 按钮圆角。
     */
    private static final int BUTTON_RADIUS = 4;
    /**
     * 按钮内边距。
     */
    private static final int BUTTON_PADDING = 6;
    /**
     * 输入框高度。
     */
    private static final int INPUT_HEIGHT = 30;
    /**
     * 滚动视口默认高度。
     */
    private static final int VIEWPORT_HEIGHT = 160;
    /**
     * key/value 输入宽度。
     */
    private static final int INPUT_WIDTH = 120;

    /**
     * 行 id 分配器，用于 keyed 列表稳定身份。
     */
    private static final AtomicLong NEXT_ROW_ID = new AtomicLong(1L);

    /**
     * 纯静态工厂，禁止实例化。
     */
    private SceneKeyValueMap() {
    }

    /**
     * value 类型。
     */
    public enum ValueType {
        /**
         * 字符串。
         */
        STRING,
        /**
         * 数字。
         */
        NUMBER,
        /**
         * 布尔。
         */
        BOOLEAN
    }

    /**
     * 校验错误类型。
     */
    public enum ValidationErrorType {
        /**
         * key 为空。
         */
        EMPTY_KEY,
        /**
         * key 含点号。
         */
        KEY_CONTAINS_DOT,
        /**
         * key 重复。
         */
        DUPLICATE_KEY,
        /**
         * 校验通过。
         */
        NONE
    }

    /**
     * 单行键值对数据。
     *
     * <p>公开字段模型保持 key/value/type 三元组语义，同时携带稳定 rowId 供 forEach keyed 使用。
     * 复制更新时保留 rowId；业务也可用三参构造器创建新行。</p>
     */
    public static class KeyValueRow {
        /**
         * 稳定行 id。
         */
        private final long rowId;
        /**
         * key 文本。
         */
        private String key;
        /**
         * value 文本。
         */
        private String value;
        /**
         * value 类型。
         */
        private ValueType type;

        /**
         * 创建空字符串类型行。
         */
        public KeyValueRow() {
            this("", "", ValueType.STRING);
        }

        /**
         * 创建一行键值对。
         *
         * @param key   key 文本
         * @param value value 文本
         * @param type  value 类型
         */
        public KeyValueRow(String key, String value, ValueType type) {
            this(NEXT_ROW_ID.getAndIncrement(), key, value, type);
        }

        /**
         * 创建带稳定 id 的一行键值对。
         *
         * @param rowId 稳定行 id
         * @param key   key 文本
         * @param value value 文本
         * @param type  value 类型
         */
        private KeyValueRow(long rowId, String key, String value, ValueType type) {
            this.rowId = rowId;
            this.key = nullSafe(key);
            this.value = nullSafe(value);
            this.type = type == null ? ValueType.STRING : type;
        }

        /**
         * 获取稳定行 id。
         *
         * @return 稳定行 id
         */
        public long getRowId() {
            return rowId;
        }

        /**
         * 获取 key 文本。
         *
         * @return key 文本
         */
        public String getKey() {
            return key;
        }

        /**
         * 设置 key 文本。
         *
         * @param key key 文本
         */
        public void setKey(String key) {
            this.key = nullSafe(key);
        }

        /**
         * 获取 value 文本。
         *
         * @return value 文本
         */
        public String getValue() {
            return value;
        }

        /**
         * 设置 value 文本。
         *
         * @param value value 文本
         */
        public void setValue(String value) {
            this.value = nullSafe(value);
        }

        /**
         * 获取 value 类型。
         *
         * @return value 类型
         */
        public ValueType getType() {
            return type;
        }

        /**
         * 设置 value 类型。
         *
         * @param type value 类型
         */
        public void setType(ValueType type) {
            this.type = type == null ? ValueType.STRING : type;
        }

        /**
         * 复制为同 id 的新行。
         *
         * @param key   新 key
         * @param value 新 value
         * @param type  新类型
         * @return 新行对象
         */
        public KeyValueRow copyWith(String key, String value, ValueType type) {
            return new KeyValueRow(rowId, key, value, type);
        }
    }

    /**
     * 校验错误回调载荷。
     */
    public static final class ValidationError {
        /**
         * 错误类型。
         */
        private final ValidationErrorType type;
        /**
         * 错误行下标；无错误时为 -1。
         */
        private final int rowIndex;
        /**
         * 错误 key；无错误时为空串。
         */
        private final String key;

        /**
         * 创建校验错误。
         *
         * @param type     错误类型
         * @param rowIndex 错误行下标
         * @param key      错误 key
         */
        public ValidationError(ValidationErrorType type, int rowIndex, String key) {
            this.type = type == null ? ValidationErrorType.NONE : type;
            this.rowIndex = rowIndex;
            this.key = nullSafe(key);
        }

        /**
         * 创建校验通过载荷。
         *
         * @return 校验通过载荷
         */
        public static ValidationError none() {
            return new ValidationError(ValidationErrorType.NONE, -1, "");
        }

        /**
         * 获取错误类型。
         *
         * @return 错误类型
         */
        public ValidationErrorType getType() {
            return type;
        }

        /**
         * 获取错误行下标。
         *
         * @return 错误行下标
         */
        public int getRowIndex() {
            return rowIndex;
        }

        /**
         * 获取错误 key。
         *
         * @return 错误 key
         */
        public String getKey() {
            return key;
        }
    }

    /**
     * KeyValueMap 输入契约。
     */
    public static final class Props {
        /**
         * 受控行列表。
         */
        private final Signal<List<KeyValueRow>> rows;
        /**
         * 可选标题。
         */
        private final String label;
        /**
         * key 占位符。
         */
        private final String keyPlaceholder;
        /**
         * value 占位符。
         */
        private final String valuePlaceholder;
        /**
         * 行变更回调。
         */
        private final Consumer<List<KeyValueRow>> onRowsChanged;
        /**
         * 校验回调。
         */
        private final Consumer<ValidationError> onValidationError;
        /**
         * 最大行数；0 表示无限。
         */
        private final int maxRows;
        /**
         * 最小行数；0 表示无限制。
         */
        private final int minRows;

        /**
         * 通过 Builder 创建输入契约。
         *
         * @param builder Builder
         */
        private Props(Builder builder) {
            this.rows = Objects.requireNonNull(builder.rows, "rows");
            this.label = nullSafe(builder.label);
            this.keyPlaceholder = defaultIfEmpty(builder.keyPlaceholder, DEFAULT_KEY_PLACEHOLDER);
            this.valuePlaceholder = defaultIfEmpty(builder.valuePlaceholder, DEFAULT_VALUE_PLACEHOLDER);
            this.onRowsChanged = builder.onRowsChanged;
            this.onValidationError = builder.onValidationError;
            this.maxRows = Math.max(0, builder.maxRows);
            this.minRows = Math.max(0, builder.minRows);
        }

        /**
         * 创建 Builder。
         *
         * @param rows 受控行列表 signal
         * @return Builder
         */
        public static Builder builder(Signal<List<KeyValueRow>> rows) {
            return new Builder(rows);
        }

        /**
         * 获取受控行列表。
         */
        public Signal<List<KeyValueRow>> rows() {
            return rows;
        }

        /**
         * 获取标题。
         */
        public String label() {
            return label;
        }

        /**
         * 获取 key 占位符。
         */
        public String keyPlaceholder() {
            return keyPlaceholder;
        }

        /**
         * 获取 value 占位符。
         */
        public String valuePlaceholder() {
            return valuePlaceholder;
        }

        /**
         * 获取行变更回调。
         */
        public Consumer<List<KeyValueRow>> onRowsChanged() {
            return onRowsChanged;
        }

        /**
         * 获取校验回调。
         */
        public Consumer<ValidationError> onValidationError() {
            return onValidationError;
        }

        /**
         * 获取最大行数。
         */
        public int maxRows() {
            return maxRows;
        }

        /**
         * 获取最小行数。
         */
        public int minRows() {
            return minRows;
        }

        /**
         * Props Builder。
         */
        public static final class Builder {
            /**
             * 受控行列表。
             */
            private final Signal<List<KeyValueRow>> rows;
            /**
             * 可选标题。
             */
            private String label;
            /**
             * key 占位符。
             */
            private String keyPlaceholder;
            /**
             * value 占位符。
             */
            private String valuePlaceholder;
            /**
             * 行变更回调。
             */
            private Consumer<List<KeyValueRow>> onRowsChanged;
            /**
             * 校验回调。
             */
            private Consumer<ValidationError> onValidationError;
            /**
             * 最大行数。
             */
            private int maxRows;
            /**
             * 最小行数。
             */
            private int minRows;

            /**
             * 创建 Builder。
             *
             * @param rows 受控行列表 signal
             */
            private Builder(Signal<List<KeyValueRow>> rows) {
                this.rows = rows;
            }

            /**
             * 设置标题。
             */
            public Builder label(String label) {
                this.label = label;
                return this;
            }

            /**
             * 设置 key 占位符。
             */
            public Builder keyPlaceholder(String keyPlaceholder) {
                this.keyPlaceholder = keyPlaceholder;
                return this;
            }

            /**
             * 设置 value 占位符。
             */
            public Builder valuePlaceholder(String valuePlaceholder) {
                this.valuePlaceholder = valuePlaceholder;
                return this;
            }

            /**
             * 设置行变更回调。
             */
            public Builder onRowsChanged(Consumer<List<KeyValueRow>> onRowsChanged) {
                this.onRowsChanged = onRowsChanged;
                return this;
            }

            /**
             * 设置校验回调。
             */
            public Builder onValidationError(Consumer<ValidationError> onValidationError) {
                this.onValidationError = onValidationError;
                return this;
            }

            /**
             * 设置最大行数。
             */
            public Builder maxRows(int maxRows) {
                this.maxRows = maxRows;
                return this;
            }

            /**
             * 设置最小行数。
             */
            public Builder minRows(int minRows) {
                this.minRows = minRows;
                return this;
            }

            /**
             * 构建 Props。
             *
             * @return Props
             */
            public Props build() {
                return new Props(this);
            }
        }
    }

    /**
     * 工厂：构建 KeyValueMap 组件函数。
     *
     * @param rt    场景运行时
     * @param props 输入契约
     * @return 组件函数，交 {@link SceneRuntime#mount} 挂载
     */
    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        Objects.requireNonNull(rt, "rt");
        Objects.requireNonNull(props, "props");
        return () -> {
            SceneNode root = new SceneNode();
            root.setFlexDirection(FlexDirection.COLUMN);
            root.setGap(ROOT_GAP);

            SceneNode labelNode = new SceneNode();
            labelNode.setText(props.label());
            labelNode.setTextColor(LABEL_COLOR);
            rt.show(root, Computed.create(() -> !props.label().isEmpty()), () -> labelNode);

            root.appendChild(buildHeader());

            SceneNode viewport = new SceneNode();
            viewport.setFlexDirection(FlexDirection.COLUMN);
            viewport.setScrollable(true);
            viewport.setClipChildren(true);
            viewport.setPreferredHeight(VIEWPORT_HEIGHT);
            viewport.setGap(ROW_GAP);
            root.appendChild(viewport);

            Signal<Integer> scrollSignal = Signal.create(Integer.valueOf(0));
            rt.bind(Invalidation.COMPOSITE, scrollSignal, v -> viewport.setScrollOffsetY(v.intValue()));
            rt.on(viewport, SceneEventType.SCROLL, (ev, ctx) -> {
                int maxScrollY = SceneGeometry.maxScrollY(viewport);
                int current = scrollSignal.get().intValue();
                int next = current - ev.getWheelDelta();
                int clamped = clamp(next, 0, maxScrollY);
                if (clamped != current) {
                    scrollSignal.set(Integer.valueOf(clamped));
                    ctx.stopPropagation();
                }
            });

            Computed<ValidationState> validationStateSignal = Computed.create(() -> validateRows(props.rows().get()));
            rt.bind(Invalidation.PAINT, validationStateSignal, state -> notifyValidation(props, state));

            rt.forEach(viewport, props.rows(), KeyValueRow::getRowId,
                row -> buildRow(rt, props, row, validationStateSignal));

            root.appendChild(buildActionButton(rt,
                Computed.create(() -> canAdd(props.rows().get(), props.maxRows())),
                "+ 添加", () -> addRow(props)));

            return root;
        };
    }

    /**
     * 构建表头行。
     *
     * @return 表头节点
     */
    private static SceneNode buildHeader() {
        SceneNode header = new SceneNode();
        header.setFlexDirection(FlexDirection.ROW);
        header.setGap(CELL_GAP);
        header.setCrossAxisAlign(CrossAxisAlign.CENTER);
        appendHeaderCell(header, "Key", INPUT_WIDTH);
        appendHeaderCell(header, "Value", INPUT_WIDTH);
        appendHeaderCell(header, "Type", 230);
        appendHeaderCell(header, "操作", 48);
        return header;
    }

    /**
     * 追加表头单元格。
     *
     * @param header 表头行
     * @param text   文本
     * @param width  宽度
     */
    private static void appendHeaderCell(SceneNode header, String text, int width) {
        SceneNode cell = new SceneNode();
        cell.setText(text);
        cell.setTextColor(HEADER_COLOR);
        cell.setPreferredWidth(width);
        header.appendChild(cell);
    }

    /**
     * 构建键值行。
     *
     * @param rt    场景运行时
     * @param props 输入契约
     * @param row   当前行快照
     * @return 行节点
     */
    private static SceneNode buildRow(SceneRuntime rt, Props props, KeyValueRow row,
                                      Computed<ValidationState> validationStateSignal) {
        SceneNode rowNode = new SceneNode();
        rowNode.setFlexDirection(FlexDirection.ROW);
        rowNode.setCrossAxisAlign(CrossAxisAlign.CENTER);
        rowNode.setGap(CELL_GAP);
        rowNode.setPadding(2);
        rowNode.setCornerRadius(4);
        rt.bind(Invalidation.PAINT,
            Computed.create(() -> validationStateSignal.get().invalidRowIds().contains(Long.valueOf(row.getRowId()))),
            invalid -> rowNode.setBackgroundColor(Boolean.TRUE.equals(invalid) ? ROW_ERROR_BG : ROW_BG));

        SceneNode keyMount = new SceneNode();
        keyMount.setPreferredWidth(INPUT_WIDTH);
        rowNode.appendChild(keyMount);
        rt.mount(keyMount, SceneTextInput.create(rt, new SceneTextInput.Props(
            Computed.create(() -> currentRow(props.rows().get(), row).getKey()),
            Signal.create(Boolean.TRUE),
            Signal.create(Boolean.FALSE),
            props.keyPlaceholder(), Integer.MAX_VALUE, SceneInputType.TEXT,
            next -> updateRow(props, row.getRowId(), current -> current.copyWith(next,
                current.getValue(), current.getType())))));
        keyMount.__getChildren().get(0).setPreferredHeight(INPUT_HEIGHT);

        SceneNode valueMount = new SceneNode();
        valueMount.setPreferredWidth(INPUT_WIDTH);
        rowNode.appendChild(valueMount);
        rt.mount(valueMount, SceneTextInput.create(rt, new SceneTextInput.Props(
            Computed.create(() -> currentRow(props.rows().get(), row).getValue()),
            Signal.create(Boolean.TRUE),
            Signal.create(Boolean.FALSE),
            props.valuePlaceholder(), Integer.MAX_VALUE, SceneInputType.TEXT,
            next -> updateRow(props, row.getRowId(), current -> current.copyWith(current.getKey(),
                next, current.getType())))));
        valueMount.__getChildren().get(0).setPreferredHeight(INPUT_HEIGHT);

        SceneNode typeMount = new SceneNode();
        typeMount.setPreferredWidth(230);
        rowNode.appendChild(typeMount);
        rt.mount(typeMount, SceneSegmented.create(rt, new SceneSegmented.Props(
            Computed.create(() -> currentRow(props.rows().get(), row).getType().ordinal()),
            TYPE_OPTIONS,
            Signal.create(Boolean.TRUE),
            next -> updateRow(props, row.getRowId(), current -> current.copyWith(current.getKey(),
                current.getValue(), ValueType.values()[clamp(next.intValue(), 0, ValueType.values().length - 1)])))));

        rowNode.appendChild(buildActionButton(rt,
            Computed.create(() -> canRemove(props.rows().get(), props.minRows())),
            "删除", () -> removeRow(props, row.getRowId())));
        rowNode.__getChildren().get(3).setPreferredHeight(INPUT_HEIGHT);
        return rowNode;
    }

    /**
     * 构建动作按钮。
     *
     * @param rt      场景运行时
     * @param enabled 是否启用
     * @param text    文本
     * @param action  动作回调
     * @return 按钮节点
     */
    private static SceneNode buildActionButton(SceneRuntime rt, Computed<Boolean> enabled, String text, Runnable action) {
        SceneNode button = new SceneNode();
        button.setFlexDirection(FlexDirection.ROW);
        button.setMainAxisAlign(MainAxisAlign.CENTER);
        button.setCrossAxisAlign(CrossAxisAlign.CENTER);
        button.setPadding(BUTTON_PADDING);
        button.setCornerRadius(BUTTON_RADIUS);
        button.setWidthSizing(WidthSizing.SHRINK);

        SceneNode label = new SceneNode();
        label.setHitTestable(false);
        label.setText(text);
        button.appendChild(label);

        SceneInteractionState is = rt.interactionState(button);
        rt.bind(Invalidation.PAINT,
            Computed.create(() -> buttonBackground(enabled.get(), is.pressed().get(), is.hovered().get())),
            button::setBackgroundColor);
        rt.bind(Invalidation.PAINT, enabled,
            value -> label.setTextColor(Boolean.TRUE.equals(value) ? BUTTON_TEXT : BUTTON_TEXT_DISABLED));
        rt.bind(Invalidation.PAINT, enabled,
            value -> button.setCursor(Boolean.TRUE.equals(value) ? SceneCursor.POINTER : SceneCursor.NOT_ALLOWED));
        rt.on(button, SceneEventType.CLICK, (ev, ctx) -> {
            if (Boolean.TRUE.equals(enabled.get())) {
                action.run();
            }
            ctx.stopPropagation();
        });
        rt.focusable(button);
        return button;
    }

    /**
     * 更新指定行。
     *
     * @param props   输入契约
     * @param rowId   行 id
     * @param updater 行更新器
     */
    private static void updateRow(Props props, long rowId, RowUpdater updater) {
        List<KeyValueRow> current = safeRows(props.rows().get());
        List<KeyValueRow> next = new ArrayList<KeyValueRow>(current.size());
        boolean changed = false;
        for (KeyValueRow row : current) {
            if (row.getRowId() == rowId) {
                next.add(updater.update(row));
                changed = true;
            } else {
                next.add(row);
            }
        }
        if (changed) {
            publishRows(props, next);
        }
    }

    /**
     * 添加空行。
     *
     * @param props 输入契约
     */
    private static void addRow(Props props) {
        List<KeyValueRow> current = safeRows(props.rows().get());
        if (!canAdd(current, props.maxRows())) {
            return;
        }
        List<KeyValueRow> next = new ArrayList<KeyValueRow>(current);
        next.add(new KeyValueRow("", "", ValueType.STRING));
        publishRows(props, next);
    }

    /**
     * 删除指定行。
     *
     * @param props 输入契约
     * @param rowId 行 id
     */
    private static void removeRow(Props props, long rowId) {
        List<KeyValueRow> current = safeRows(props.rows().get());
        if (!canRemove(current, props.minRows())) {
            return;
        }
        List<KeyValueRow> next = new ArrayList<KeyValueRow>(current.size());
        for (KeyValueRow row : current) {
            if (row.getRowId() != rowId) {
                next.add(row);
            }
        }
        publishRows(props, next);
    }

    /**
     * 发布新行列表并触发回调。
     *
     * @param props 输入契约
     * @param next  新行列表
     */
    private static void publishRows(Props props, List<KeyValueRow> next) {
        List<KeyValueRow> immutable = Collections.unmodifiableList(new ArrayList<KeyValueRow>(next));
        props.rows().set(immutable);
        if (props.onRowsChanged() != null) {
            props.onRowsChanged().accept(immutable);
        }
    }

    /**
     * 通知首个校验结果。
     *
     * @param props 输入契约
     * @param state 当前校验状态
     */
    private static void notifyValidation(Props props, ValidationState state) {
        if (props.onValidationError() == null) {
            return;
        }
        props.onValidationError().accept(state.validationError());
    }

    /**
     * 计算首个校验错误。
     *
     * @param rows 行列表
     * @return 首个校验错误或 none
     */
    public static ValidationError firstValidationError(List<KeyValueRow> rows) {
        return validateRows(rows).validationError();
    }

    /**
     * 计算校验状态。
     *
     * @param rows 行列表
     * @return 校验状态
     */
    private static ValidationState validateRows(List<KeyValueRow> rows) {
        List<KeyValueRow> safe = safeRows(rows);
        Map<String, Integer> counts = new HashMap<String, Integer>();
        for (KeyValueRow row : safe) {
            String key = nullSafe(row.getKey());
            counts.put(key, Integer.valueOf(counts.containsKey(key) ? counts.get(key).intValue() + 1 : 1));
        }
        Set<Long> ids = new HashSet<Long>();
        ValidationError firstError = ValidationError.none();
        for (int i = 0; i < safe.size(); i++) {
            KeyValueRow row = safe.get(i);
            String key = nullSafe(row.getKey());
            if (key.trim().isEmpty()) {
                ids.add(Long.valueOf(row.getRowId()));
                if (firstError.getType() == ValidationErrorType.NONE) {
                    firstError = new ValidationError(ValidationErrorType.EMPTY_KEY, i, key);
                }
            } else if (key.indexOf('.') >= 0) {
                ids.add(Long.valueOf(row.getRowId()));
                if (firstError.getType() == ValidationErrorType.NONE) {
                    firstError = new ValidationError(ValidationErrorType.KEY_CONTAINS_DOT, i, key);
                }
            } else if (counts.get(key).intValue() > 1) {
                ids.add(Long.valueOf(row.getRowId()));
                if (firstError.getType() == ValidationErrorType.NONE) {
                    firstError = new ValidationError(ValidationErrorType.DUPLICATE_KEY, i, key);
                }
            }
        }
        return new ValidationState(firstError, ids);
    }

    /**
     * 读取当前行快照。
     *
     * @param rows     行列表
     * @param fallback 兜底行
     * @return 当前行或兜底行
     */
    private static KeyValueRow currentRow(List<KeyValueRow> rows, KeyValueRow fallback) {
        for (KeyValueRow row : safeRows(rows)) {
            if (row.getRowId() == fallback.getRowId()) {
                return row;
            }
        }
        return fallback;
    }

    /**
     * 判断是否可添加。
     */
    private static boolean canAdd(List<KeyValueRow> rows, int maxRows) {
        return maxRows <= 0 || safeRows(rows).size() < maxRows;
    }

    /**
     * 判断是否可删除。
     */
    private static boolean canRemove(List<KeyValueRow> rows, int minRows) {
        return minRows <= 0 || safeRows(rows).size() > minRows;
    }

    /**
     * 解析按钮背景色。
     */
    private static int buttonBackground(Boolean enabled, Boolean pressed, Boolean hovered) {
        if (!Boolean.TRUE.equals(enabled)) {
            return BUTTON_BG_DISABLED;
        }
        if (Boolean.TRUE.equals(pressed)) {
            return BUTTON_BG_PRESSED;
        }
        if (Boolean.TRUE.equals(hovered)) {
            return BUTTON_BG_HOVER;
        }
        return BUTTON_BG;
    }

    /**
     * 裁剪整数到闭区间。
     */
    private static int clamp(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    /**
     * null 安全字符串。
     */
    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    /**
     * 空字符串兜底。
     */
    private static String defaultIfEmpty(String value, String fallback) {
        String safe = nullSafe(value);
        return safe.isEmpty() ? fallback : safe;
    }

    /**
     * null 安全行列表。
     */
    private static List<KeyValueRow> safeRows(List<KeyValueRow> rows) {
        return rows == null ? Collections.<KeyValueRow>emptyList() : rows;
    }

    /**
     * 行更新器。
     */
    private interface RowUpdater {
        /**
         * 更新行。
         *
         * @param row 当前行
         * @return 新行
         */
        KeyValueRow update(KeyValueRow row);
    }

    /**
     * 校验派生状态。
     */
    private static final class ValidationState {
        /** 首个校验错误。 */
        private final ValidationError validationError;
        /** 错误行 id 集合。 */
        private final Set<Long> invalidRowIds;

        /**
         * 创建校验派生状态。
         *
         * @param validationError 首个校验错误
         * @param invalidRowIds   错误行 id 集合
         */
        private ValidationState(ValidationError validationError, Set<Long> invalidRowIds) {
            this.validationError = validationError == null ? ValidationError.none() : validationError;
            this.invalidRowIds = Collections.unmodifiableSet(new HashSet<Long>(invalidRowIds));
        }

        /**
         * 获取首个校验错误。
         *
         * @return 首个校验错误
         */
        private ValidationError validationError() {
            return validationError;
        }

        /**
         * 获取错误行 id 集合。
         *
         * @return 错误行 id 集合
         */
        private Set<Long> invalidRowIds() {
            return invalidRowIds;
        }
    }
}
