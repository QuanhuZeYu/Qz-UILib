package club.heiqi.uilib.ui.scene.control;

import static club.heiqi.uilib.ui.scene.control.SceneTextUtils.nullSafe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.SceneNode.WidthSizing;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.SceneStateColors;

/**
 * SceneKeyValueMap —— scene 新栈动态键值对编辑器。
 *
 * <p>行列表由外部 {@code rows} signal 受控持有；控件只在输入、增删和类型切换时复制列表并写回
 * {@code rows.set(next)}，再通过回调通知字段引擎。列表渲染使用 keyed forEach，行 key 为
 * {@link KeyValueRow} 的稳定 id，编辑 key/value/type 不会改变列表身份。</p>
 *
 * <p><b>回调语义（先 set 再通知）</b>：控件在触发 {@code onRowsChanged} 之前，已将新行列表
 * 不可变副本 {@code rows.set(immutable)} 写入受控 signal。回调<b>仅供通知</b>，外部不应在
 * 回调里再次 {@code rows.set(...)}——重复 set 属于冗余写入，且若外部不持有 signal 引用，
 * 行为将以控件写入为准。如需在变更后追加副作用（持久化、校验、联动其他 signal），在回调里
 * 读取参数即可，无需回写受控 signal。{@code onRowsChanged} 可为 null，控件会跳过通知。</p>
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
     * 标题文本色，取自 chrome token。
     */
    private static final int LABEL_COLOR = SceneChromeTokens.TEXT_PRIMARY;
    /**
     * 表头文本色，取自 chrome token。
     */
    private static final int HEADER_COLOR = SceneChromeTokens.TEXT_SECONDARY;
    /**
     * 按钮文本色，取自 chrome token。
     */
    private static final int BUTTON_TEXT = SceneChromeTokens.TEXT_ON_ACCENT;
    /**
     * 按钮禁用文本色，取自 chrome token。
     */
    private static final int BUTTON_TEXT_DISABLED = SceneChromeTokens.TEXT_DISABLED;
    /**
     * 按钮圆角，取自 chrome token。
     */
    private static final int BUTTON_RADIUS = SceneChromeTokens.RADIUS_MD;
    /**
     * 按钮内边距，取自 chrome token。
     */
    private static final int BUTTON_PADDING = SceneChromeTokens.PAD_MD;
    /**
     * 输入框高度，取自 chrome token。
     */
    private static final int INPUT_HEIGHT = SceneChromeTokens.INPUT_HEIGHT;
    /**
     * key/value 输入宽度。
     */
    private static final int INPUT_WIDTH = 120;

    /**
     * 纯静态工厂，禁止实例化。
     */
    private SceneKeyValueMap() {
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
         * 行变更回调。控件在回调前已将新值写入 {@code rows} signal，回调仅供通知，无需再次 set。可为 null。
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
         * 控件级启用信号，控制 key/value TextInput 与 type Segmented 的 enabled；默认恒为 true。
         */
        private final ReadableSignal<Boolean> enabled;
        /**
         * 控件级只读信号，仅作用于 key/value TextInput；默认恒为 false。
         */
        private final ReadableSignal<Boolean> readOnly;
        /**
         * 是否在视口右侧叠加 {@link SceneScrollbar}。false 表示不建滚动条（向后兼容）；
         * true 时控件在视口右侧叠加滚动条，滑块几何由 runtime layoutDoneSignal 驱动重算。
         */
        private final boolean showScrollbar;

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
            this.enabled = builder.enabled == null ? Signal.create(Boolean.TRUE) : builder.enabled;
            this.readOnly = builder.readOnly == null ? Signal.create(Boolean.FALSE) : builder.readOnly;
            this.showScrollbar = builder.showScrollbar;
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
         * 获取行变更回调。控件在回调前已将新值写入 {@code rows} signal，回调仅供通知，无需再次 set。
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
         * 获取控件级启用信号。
         */
        public ReadableSignal<Boolean> enabled() {
            return enabled;
        }

        /**
         * 获取控件级只读信号（仅作用于 key/value TextInput）。
         */
        public ReadableSignal<Boolean> readOnly() {
            return readOnly;
        }

        /**
         * 获取是否建滚动条。
         *
         * @return 是否建滚动条
         */
        public boolean showScrollbar() {
            return showScrollbar;
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
             * 行变更回调。控件在回调前已将新值写入 {@code rows} signal，回调仅供通知，无需再次 set。
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
             * 控件级启用信号。
             */
            private ReadableSignal<Boolean> enabled;
            /**
             * 控件级只读信号（仅作用于 key/value TextInput）。
             */
            private ReadableSignal<Boolean> readOnly;
            /**
             * 是否建滚动条，false 表示不建。
             */
            private boolean showScrollbar;

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
             * 设置行变更回调。控件在回调前已将新值写入 {@code rows} signal，回调仅供通知，无需再次 set。
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
             * 设置控件级启用信号。
             *
             * @param enabled 启用信号，null 时 build 后默认恒为 true
             * @return 当前 Builder
             */
            public Builder enabled(ReadableSignal<Boolean> enabled) {
                this.enabled = enabled;
                return this;
            }

            /**
             * 设置控件级只读信号（仅作用于 key/value TextInput）。
             *
             * @param readOnly 只读信号，null 时 build 后默认恒为 false
             * @return 当前 Builder
             */
            public Builder readOnly(ReadableSignal<Boolean> readOnly) {
                this.readOnly = readOnly;
                return this;
            }

            /**
             * 设置是否建滚动条。
             *
             * @param showScrollbar 是否建滚动条，false 表示不建
             * @return 当前 Builder
             */
            public Builder showScrollbar(boolean showScrollbar) {
                this.showScrollbar = showScrollbar;
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
            SceneNode root = SceneNode.column();
            root.setGap(ROOT_GAP);

            SceneNode labelNode = new SceneNode();
            labelNode.setText(props.label());
            labelNode.setTextColor(LABEL_COLOR);
            rt.show(root, Computed.create(() -> !props.label().isEmpty()), () -> labelNode);

            root.appendChild(buildHeader());

            SceneNode viewport = SceneNode.column();
            viewport.setScrollable(true);
            viewport.setClipChildren(true);
            viewport.setGap(ROW_GAP);
            viewport.setFillParentHeight(true);
            viewport.setFlexGrow(1);

            // stackHost 承载 viewport 原 preferredHeight(VIEWPORT_HEIGHT_DEFAULT)，并可选挂滚动条 column。
            // header 与 addButton 保持 root 直接子，不进 stackHost。即使无滚动条也建 stackHost，统一结构路径。
            SceneNode stackHost = SceneNode.row();
            stackHost.setPreferredHeight(SceneChromeTokens.VIEWPORT_HEIGHT_DEFAULT);
            stackHost.appendChild(viewport);

            Signal<Integer> scrollSignal = SceneScrolls.attach(rt, viewport);

            // 可选滚动条：showScrollbar 为 true 时建 bar，挂到 stackHost 右侧
            if (props.showScrollbar()) {
                SceneScrollbar.Result sbResult = SceneScrollbar.createDefault(rt, viewport, scrollSignal);
                stackHost.appendChild(sbResult.column());
            }

            root.appendChild(stackHost);

            Computed<SceneKeyValueMapValidation.ValidationState> validationStateSignal = Computed.create(() -> SceneKeyValueMapValidation.validateRows(props.rows().get()));
            rt.bind(validationStateSignal, state -> notifyValidation(props, state));

            rt.forEach(viewport, props.rows(), KeyValueRow::getRowId,
                row -> buildRow(rt, props, row, validationStateSignal));

            root.appendChild(buildActionButton(rt,
                Computed.create(() -> SceneListOps.canAdd(props.rows().get(), props.maxRows())),
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
        SceneNode header = SceneNode.row();
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
                                      Computed<SceneKeyValueMapValidation.ValidationState> validationStateSignal) {
        SceneNode rowNode = SceneNode.row();
        rowNode.setCrossAxisAlign(CrossAxisAlign.CENTER);
        rowNode.setGap(CELL_GAP);
        rowNode.setPadding(SceneChromeTokens.PAD_SM);
        rowNode.setCornerRadius(SceneChromeTokens.RADIUS_MD);
        rt.bindComputed(() -> validationStateSignal.get().invalidRowIds().contains(Long.valueOf(row.getRowId())),
            invalid -> rowNode.setBackgroundColor(SceneStateColors.errorRowBackground(Boolean.TRUE.equals(invalid))));

        SceneNode keyMount = new SceneNode();
        keyMount.setPreferredWidth(INPUT_WIDTH);
        rowNode.appendChild(keyMount);
        SceneNode keyInput = rt.mount(keyMount, SceneTextInput.create(rt, new SceneTextInput.Props(
            Computed.create(() -> currentRow(props.rows().get(), row).getKey()),
            props.enabled(),
            props.readOnly(),
            props.keyPlaceholder(), Integer.MAX_VALUE, SceneInputType.TEXT,
            next -> updateRow(props, row.getRowId(), current -> current.copyWith(next,
                current.getValue(), current.getType()))))).getRoot();
        keyInput.setPreferredHeight(INPUT_HEIGHT);

        SceneNode valueMount = new SceneNode();
        valueMount.setPreferredWidth(INPUT_WIDTH);
        rowNode.appendChild(valueMount);
        SceneNode valueInput = rt.mount(valueMount, SceneTextInput.create(rt, new SceneTextInput.Props(
            Computed.create(() -> currentRow(props.rows().get(), row).getValue()),
            props.enabled(),
            props.readOnly(),
            props.valuePlaceholder(), Integer.MAX_VALUE, SceneInputType.TEXT,
            next -> updateRow(props, row.getRowId(), current -> current.copyWith(current.getKey(),
                next, current.getType()))))).getRoot();
        valueInput.setPreferredHeight(INPUT_HEIGHT);

        SceneNode typeMount = new SceneNode();
        typeMount.setPreferredWidth(230);
        rowNode.appendChild(typeMount);
        rt.mount(typeMount, SceneSegmented.create(rt, new SceneSegmented.Props(
            Computed.create(() -> currentRow(props.rows().get(), row).getType().ordinal()),
            TYPE_OPTIONS,
            props.enabled(),
            next -> updateRow(props, row.getRowId(), current -> current.copyWith(current.getKey(),
                current.getValue(), ValueType.values()[clamp(next.intValue(), 0, ValueType.values().length - 1)])))));

        SceneNode actionButton = buildActionButton(rt,
            Computed.create(() -> SceneListOps.canRemove(props.rows().get(), props.minRows())),
            "删除", () -> removeRow(props, row.getRowId()));
        actionButton.setPreferredHeight(INPUT_HEIGHT);
        rowNode.appendChild(actionButton);
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
        SceneNode button = SceneNode.row();
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
        rt.bindComputed(() -> SceneStateColors.standardBackground(
                Boolean.TRUE.equals(enabled.get()),
                Boolean.TRUE.equals(is.hovered().get()),
                Boolean.TRUE.equals(is.pressed().get())),
            button::setBackgroundColor);
        rt.bind(enabled,
            value -> label.setTextColor(Boolean.TRUE.equals(value) ? BUTTON_TEXT : BUTTON_TEXT_DISABLED));
        SceneControlChrome.bindCursor(rt, button, enabled, SceneCursor.POINTER, SceneCursor.NOT_ALLOWED);
        rt.on(button, SceneEventType.CLICK, (ev, ctx) -> {
            if (Boolean.TRUE.equals(enabled.get())) {
                action.run();
            }
            ctx.stopPropagation();
        });
        rt.focusable(button, enabled);
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
        if (!SceneListOps.canAdd(current, props.maxRows())) {
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
        if (!SceneListOps.canRemove(current, props.minRows())) {
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
        List<KeyValueRow> immutable = SceneListOps.immutableCopy(next);
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
    private static void notifyValidation(Props props, SceneKeyValueMapValidation.ValidationState state) {
        if (props.onValidationError() == null) {
            return;
        }
        props.onValidationError().accept(state.validationError());
    }

    /**
     * 计算首个校验错误。
     *
     * <p>薄委托至 {@link SceneKeyValueMapValidation#firstError}，保留为公共 API 入口。</p>
     *
     * @param rows 行列表
     * @return 首个校验错误或 none
     */
    public static ValidationError firstValidationError(List<KeyValueRow> rows) {
        return SceneKeyValueMapValidation.firstError(rows);
    }

    /**
     * 读取当前行快照。
     *
     * @param rows     行列表
     * @param fallback 兜底行
     * @return 当前行或兜底行
     */
    private static KeyValueRow currentRow(List<KeyValueRow> rows, KeyValueRow fallback) {
        return SceneListOps.current(rows, fallback, (row, current) -> row.getRowId() == current.getRowId());
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
     * 空字符串兜底。
     */
    private static String defaultIfEmpty(String value, String fallback) {
        String safe = nullSafe(value);
        return safe.isEmpty() ? fallback : safe;
    }

    /**
     * null 安全行列表。
     */
    static List<KeyValueRow> safeRows(List<KeyValueRow> rows) {
        return SceneListOps.safeList(rows);
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
}
