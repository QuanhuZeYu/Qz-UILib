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
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceBinder;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;
import club.heiqi.uilib.util.UiNumbers;

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
 *
 * <p><b>外观归属（液态玻璃迁移）</b>：列表底座（viewport）取主题
 * {@link SceneTheme.Role#GROUP} 配方，是该节点 background/border/borderWidth/cornerRadius/
 * backdrop/surfaceElevation 的唯一写入者（{@link SceneSurfaceBinder#bind}）；行默认全透明，
 * 只在校验失败时写一次主题 {@code errorText} 系半透明轻量底色覆盖，行自身不装滤镜、不写边框
 * 与圆角。行内 key/value 输入框只读复用 {@link SceneTextInput} 已主题化的 INPUT 表面与前景，
 * 类型分段只读复用 {@link SceneSegmented} 的导航族配方；添加/删除按钮取
 * {@code BUTTON_STANDARD} / {@code BUTTON_DANGER} 角色配方并经 {@link SceneSurfaceBinder#bindForeground}
 * 绑文字前景，不再保留静态按钮底色与禁用实色文字。标题与表头文字分别取主题
 * {@code foreground}/{@code mutedForeground}。主题切换只重派生外观，不重建节点、不丢编辑草稿
 * 与校验状态，也不触碰数据模型、校验规则与增删提交语义。</p>
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
     * 按钮内边距（布局属性，取自 chrome token；外观尺寸常量不受主题接管，契约 §4.2）。
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
     * 恒真 enabled：列表底座自身没有禁用语义，表面绑定只走 idle/hovered/pressed 三档。
     */
    private static final ReadableSignal<Boolean> ALWAYS_ENABLED = () -> Boolean.TRUE;
    /**
     * 行默认背景：全透明，露出底座玻璃。
     */
    private static final int ROW_BG_TRANSPARENT = 0x00000000;
    /**
     * 校验失败行的弱提示底色 alpha：沿用旧 {@code DANGER_BG_SUBTLE} 的强度档，
     * RGB 换为主题 {@code errorText}（深色底上可读的错误语义色）。
     */
    private static final int ROW_ERROR_ALPHA = 0x22;

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
            // 控件内文字跟随 root 字号：标题、表头、行内文字与动作按钮都是 root 的后代，
            // 沿父链继承层 2 声明，不再需要把字号信号逐层传进辅助方法。
            SceneNode labelNode = new SceneNode();
            labelNode.setText(props.label());
            // 标题取主题正文前景（构造期捕获来源主题，主题切换只重派生不重建节点）。
            rt.bind(SceneThemes.foreground(rt), labelNode::setTextColor);
            rt.show(root, Computed.create(() -> !props.label().isEmpty()), () -> labelNode);

            root.appendChild(buildHeader(rt));

            SceneNode viewport = SceneNode.column();
            viewport.setScrollable(true);
            viewport.setClipChildren(true);
            viewport.setGap(ROW_GAP);
            viewport.setFillParentHeight(true);
            viewport.setFlexGrow(1);

            // 列表底座：GROUP 角色配方是 background/border/borderWidth/cornerRadius/backdrop/
            // surfaceElevation 的唯一写入者；enabled 恒真（底座无禁用语义）。
            // 时序契约：先声明关心 hovered/pressed/focused，Router 的写入才不会被 null 短路。
            SceneInteractionState viewportInteraction = rt.interactionState(viewport);
            viewportInteraction.hovered();
            viewportInteraction.pressed();
            viewportInteraction.focused();
            SceneSurfaceBinder.bind(rt, viewport, SceneThemes.surface(rt, SceneTheme.Role.GROUP),
                ALWAYS_ENABLED, viewportInteraction);

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
                "+ 添加", SceneTheme.Role.BUTTON_STANDARD, () -> addRow(props)));

            return root;
        };
    }

    /**
     * 构建表头行。
     *
     * @param rt 场景运行时
     * @return 表头节点
     */
    private static SceneNode buildHeader(SceneRuntime rt) {
        SceneNode header = SceneNode.row();
        header.setGap(CELL_GAP);
        header.setCrossAxisAlign(CrossAxisAlign.CENTER);
        appendHeaderCell(rt, header, "Key", INPUT_WIDTH);
        appendHeaderCell(rt, header, "Value", INPUT_WIDTH);
        appendHeaderCell(rt, header, "Type", 230);
        appendHeaderCell(rt, header, "操作", 48);
        return header;
    }

    /**
     * 追加表头单元格。
     *
     * @param rt     场景运行时
     * @param header 表头行
     * @param text   文本
     * @param width  宽度
     */
    private static void appendHeaderCell(SceneRuntime rt, SceneNode header, String text, int width) {
        SceneNode cell = new SceneNode();
        cell.setText(text);
        // 表头取主题次要前景，主题切换只重派生。
        rt.bind(SceneThemes.mutedForeground(rt), cell::setTextColor);
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
        // 行只做轻量底色覆盖：默认透明露出底座玻璃，校验失败行取主题 errorText 系半透明弱提示；
        // 只写 backgroundColor 一个属性，不装滤镜、不写边框/圆角，不与底座争属性槽。
        // 全部取值发生在 effect 体内（构造期不解引用未求值 Computed），主题切换自动重派生。
        // G19/P-02 收编：语义色经 SceneThemes.errorText 公共入口取，控件侧只保留弱提示 alpha 遮罩。
        ReadableSignal<Integer> errorText = SceneThemes.errorText(rt);
        rt.bindComputed(() -> validationStateSignal.get().invalidRowIds().contains(Long.valueOf(row.getRowId()))
                ? errorRowTint(errorText.get()) : ROW_BG_TRANSPARENT,
            rowNode::setBackgroundColor);

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
                current.getValue(), ValueType.values()[UiNumbers.clamp(next.intValue(), 0, ValueType.values().length - 1)])))));

        SceneNode actionButton = buildActionButton(rt,
            Computed.create(() -> SceneListOps.canRemove(props.rows().get(), props.minRows())),
            "删除", SceneTheme.Role.BUTTON_DANGER, () -> removeRow(props, row.getRowId()));
        actionButton.setPreferredHeight(INPUT_HEIGHT);
        rowNode.appendChild(actionButton);
        return rowNode;
    }

    /**
     * 构建动作按钮。
     *
     * <p>外观全部归主题：表面取 {@code role} 配方（添加=BUTTON_STANDARD、删除=BUTTON_DANGER），
     * background/border/borderWidth/cornerRadius/backdrop/surfaceElevation 由
     * {@link SceneSurfaceBinder#bind} 独占；文字前景取配方 foreground（回落库默认正文色），
     * 禁用反馈由绑定器的 disabled 档表达，不再叠加禁用实色文字绑定。padding/高度等布局属性
     * 仍由本控件自持。</p>
     *
     * @param rt      场景运行时
     * @param enabled 是否启用
     * @param text    文本
     * @param role    材质角色（添加=BUTTON_STANDARD、删除=BUTTON_DANGER）
     * @param action  动作回调
     * @return 按钮节点
     */
    private static SceneNode buildActionButton(SceneRuntime rt, Computed<Boolean> enabled, String text,
                                               SceneTheme.Role role, Runnable action) {
        SceneNode button = SceneNode.row();
        button.setMainAxisAlign(MainAxisAlign.CENTER);
        button.setCrossAxisAlign(CrossAxisAlign.CENTER);
        button.setPadding(BUTTON_PADDING);
        button.setWidthSizing(WidthSizing.SHRINK);

        SceneNode label = new SceneNode();
        label.setHitTestable(false);
        label.setText(text);
        button.appendChild(label);

        ReadableSignal<SceneSurfaceStyle> surface = SceneThemes.surface(rt, role);
        SceneInteractionState is = rt.interactionState(button);
        // 时序契约：构造期声明关心，Router 后续写入才会落到已创建的 signal。
        is.hovered();
        is.pressed();
        is.focused();
        SceneSurfaceBinder.bind(rt, button, surface, enabled, is);
        SceneSurfaceBinder.bindForeground(rt, label, surface, SceneThemes.DEFAULT.foreground());
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
     * 校验失败行的轻量底色：主题 errorText（经 {@link SceneThemes#errorText} 公共入口取得）
     * 保留 RGB、替换为弱提示 alpha。
     *
     * @param errorTextArgb 来源主题 errorText 语义色 ARGB
     * @return 错误行底色 ARGB
     */
    private static int errorRowTint(int errorTextArgb) {
        return (ROW_ERROR_ALPHA << 24) | (errorTextArgb & 0x00FFFFFF);
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
