package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;
import club.heiqi.uilib.ui.scene.node.Invalidation;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.SceneNode.WidthSizing;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;

/**
 * SceneObjectField —— scene 新栈递归复合对象字段编辑器。
 *
 * <p>控件以单根 {@link Signal} 持有完整对象 Map，嵌套层通过 {@link Computed} 派生当前子树。
 * 标量编辑写回根 signal 时只复制命中路径上的 Map，未命中的兄弟子树原样透传引用，避免兄弟 computed
 * 因无关编辑失效。嵌套对象使用外部 {@code expandedPaths} signal 控制展开状态。</p>
 *
 * <p><b>回调语义（先 set 再通知）</b>：控件在触发 {@code onValueChanged} 之前，已将新对象
 * 不可变 Map {@code value.set(immutable)} 写入受控 signal。回调<b>仅供通知</b>，外部不应在
 * 回调里再次 {@code value.set(...)}——重复 set 属于冗余写入，且若外部不持有 signal 引用，
 * 行为将以控件写入为准。如需在变更后追加副作用（持久化、校验、联动其他 signal），在回调里
 * 读取参数即可，无需回写受控 signal。</p>
 */
public final class SceneObjectField {

    /** 默认最大递归深度。 */
    public static final int MAX_DEPTH = 5;
    /** 根节点间距。 */
    private static final int ROOT_GAP = 8;
    /** 字段行间距。 */
    private static final int ROW_GAP = 6;
    /** 行内间距。 */
    private static final int CELL_GAP = 6;
    /** 缩进宽度。 */
    private static final int INDENT = 14;
    /** 标签宽度。 */
    private static final int LABEL_WIDTH = 132;
    /** 输入宽度。 */
    private static final int INPUT_WIDTH = 220;
    /** 输入高度，取自 chrome token。 */
    private static final int INPUT_HEIGHT = SceneChromeTokens.INPUT_HEIGHT;
    /** 视口默认高度。 */
    private static final int VIEWPORT_HEIGHT = 220;
    /** 按钮内边距。 */
    private static final int BUTTON_PADDING = 5;
    /** 按钮圆角，取自 chrome token。 */
    private static final int BUTTON_RADIUS = SceneChromeTokens.RADIUS_MD;
    /** 标题文本色，取自 chrome token。 */
    private static final int LABEL_COLOR = SceneChromeTokens.TEXT_PRIMARY;
    /** 次级文本色，取自 chrome token。 */
    private static final int MUTED_COLOR = SceneChromeTokens.TEXT_SECONDARY;
    /** 占位提示色，取自 chrome token。 */
    private static final int NOTICE_COLOR = SceneChromeTokens.WARNING_TEXT;
    /** 按钮背景色，取自 chrome token（静态背景，无 hover/pressed 态）。 */
    private static final int BUTTON_BG = SceneChromeTokens.BG_DEFAULT;
    /** 文本颜色，取自 chrome token。 */
    private static final int TEXT_COLOR = SceneChromeTokens.TEXT_ON_ACCENT;

    /** 纯静态工厂，禁止实例化。 */
    private SceneObjectField() {
    }

    /** 字段类型。 */
    public enum FieldType {
        /** 字符串。 */
        STRING,
        /** 数字。 */
        NUMBER,
        /** 布尔。 */
        BOOLEAN,
        /** 对象。 */
        OBJECT,
        /** 列表。 */
        LIST,
        /** 空值。 */
        NULL
    }

    /**
     * 对象字段条目。
     */
    public static final class FieldEntry {
        /** 字段名。 */
        private final String key;
        /** 字段值。 */
        private final Object value;
        /** 字段类型。 */
        private final FieldType type;
        /** 对象字段展开状态。 */
        private final boolean expanded;

        /**
         * 创建字段条目。
         *
         * @param key      字段名
         * @param value    字段值
         * @param type     字段类型
         * @param expanded 是否展开
         */
        public FieldEntry(String key, Object value, FieldType type, boolean expanded) {
            this.key = nullSafe(key);
            this.value = value;
            this.type = type == null ? FieldType.NULL : type;
            this.expanded = expanded;
        }

        /** @return 字段名 */
        public String getKey() {
            return key;
        }

        /** @return 字段值 */
        public Object getValue() {
            return value;
        }

        /** @return 字段类型 */
        public FieldType getType() {
            return type;
        }

        /** @return 是否展开 */
        public boolean isExpanded() {
            return expanded;
        }
    }

    /**
     * ObjectField 输入契约。
     */
    public static final class Props {
        /** 对象完整字段映射。 */
        private final Signal<Map<String, Object>> value;
        /** 外部受控展开路径集合。 */
        private final Signal<Set<String>> expandedPaths;
        /** 值变更回调。控件在回调前已将新值写入 {@code value} signal，回调仅供通知，无需再次 set。 */
        private final Consumer<Map<String, Object>> onValueChanged;
        /** 控件标题。 */
        private final String label;
        /** 最大递归深度。 */
        private final int maxDepth;
        /** 控件级启用信号，控制标量行 TextInput 的 enabled；默认恒为 true。 */
        private final ReadableSignal<Boolean> enabled;
        /** 控件级只读信号，控制标量行 TextInput 的 readOnly；默认恒为 false。 */
        private final ReadableSignal<Boolean> readOnly;
        /**
         * 滚动条内容变更信号。null 表示不建滚动条（向后兼容）；非 null 时控件在视口右侧叠加
         * {@link SceneScrollbar}，并以此 signal 作为 contentChangedSignal 驱动滑块几何重算。
         */
        private final ReadableSignal<?> scrollbarContentSignal;

        /**
         * 通过 Builder 创建输入契约。
         *
         * @param builder Builder
         */
        private Props(Builder builder) {
            this.value = Objects.requireNonNull(builder.value, "value");
            this.expandedPaths = builder.expandedPaths == null
                    ? Signal.create(Collections.<String>emptySet()) : builder.expandedPaths;
            this.onValueChanged = builder.onValueChanged == null ? ignored -> { } : builder.onValueChanged;
            this.label = nullSafe(builder.label);
            this.maxDepth = builder.maxDepth <= 0 ? MAX_DEPTH : builder.maxDepth;
            this.enabled = builder.enabled == null ? Signal.create(Boolean.TRUE) : builder.enabled;
            this.readOnly = builder.readOnly == null ? Signal.create(Boolean.FALSE) : builder.readOnly;
            this.scrollbarContentSignal = builder.scrollbarContentSignal;
        }

        /**
         * 创建 Builder。
         *
         * @param value 对象完整字段映射 signal
         * @return Builder
         */
        public static Builder builder(Signal<Map<String, Object>> value) {
            return new Builder(value);
        }

        /** @return 对象完整字段映射 signal */
        public Signal<Map<String, Object>> value() {
            return value;
        }

        /** @return 外部受控展开路径集合 */
        public Signal<Set<String>> expandedPaths() {
            return expandedPaths;
        }

        /** @return 值变更回调。控件在回调前已将新值写入 {@code value} signal，回调仅供通知，无需再次 set */
        public Consumer<Map<String, Object>> onValueChanged() {
            return onValueChanged;
        }

        /** @return 控件标题 */
        public String label() {
            return label;
        }

        /** @return 最大递归深度 */
        public int maxDepth() {
            return maxDepth;
        }

        /** @return 控件级启用信号，缺省时恒为 true */
        public ReadableSignal<Boolean> enabled() {
            return enabled;
        }

        /** @return 控件级只读信号，缺省时恒为 false */
        public ReadableSignal<Boolean> readOnly() {
            return readOnly;
        }

        /** @return 滚动条内容变更信号，null 表示不建滚动条 */
        public ReadableSignal<?> scrollbarContentSignal() {
            return scrollbarContentSignal;
        }

        /** Props Builder。 */
        public static final class Builder {
            /** 对象完整字段映射。 */
            private final Signal<Map<String, Object>> value;
            /** 外部受控展开路径集合。 */
            private Signal<Set<String>> expandedPaths;
            /** 值变更回调。控件在回调前已将新值写入 {@code value} signal，回调仅供通知，无需再次 set。 */
            private Consumer<Map<String, Object>> onValueChanged;
            /** 控件标题。 */
            private String label;
            /** 最大递归深度。 */
            private int maxDepth = MAX_DEPTH;
            /** 控件级启用信号。 */
            private ReadableSignal<Boolean> enabled;
            /** 控件级只读信号。 */
            private ReadableSignal<Boolean> readOnly;
            /** 滚动条内容变更信号，null 表示不建滚动条。 */
            private ReadableSignal<?> scrollbarContentSignal;

            /**
             * 创建 Builder。
             *
             * @param value 对象完整字段映射 signal
             */
            private Builder(Signal<Map<String, Object>> value) {
                this.value = Objects.requireNonNull(value, "value");
            }

            /**
             * 设置展开路径集合 signal。
             *
             * @param expandedPaths 展开路径集合 signal
             * @return 当前 Builder
             */
            public Builder expandedPaths(Signal<Set<String>> expandedPaths) {
                this.expandedPaths = expandedPaths;
                return this;
            }

            /**
             * 设置值变更回调。
             *
             * @param onValueChanged 值变更回调。控件在回调前已将新值写入 {@code value} signal，回调仅供通知，无需再次 set
             * @return 当前 Builder
             */
            public Builder onValueChanged(Consumer<Map<String, Object>> onValueChanged) {
                this.onValueChanged = onValueChanged;
                return this;
            }

            /**
             * 设置控件标题。
             *
             * @param label 控件标题
             * @return 当前 Builder
             */
            public Builder label(String label) {
                this.label = label;
                return this;
            }

            /**
             * 设置最大递归深度。
             *
             * @param maxDepth 最大递归深度
             * @return 当前 Builder
             */
            public Builder maxDepth(int maxDepth) {
                this.maxDepth = maxDepth;
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
             * 设置控件级只读信号。
             *
             * @param readOnly 只读信号，null 时 build 后默认恒为 false
             * @return 当前 Builder
             */
            public Builder readOnly(ReadableSignal<Boolean> readOnly) {
                this.readOnly = readOnly;
                return this;
            }

            /**
             * 设置滚动条内容变更信号。
             *
             * @param scrollbarContentSignal 滚动条内容变更信号，null 表示不建滚动条
             * @return 当前 Builder
             */
            public Builder scrollbarContentSignal(ReadableSignal<?> scrollbarContentSignal) {
                this.scrollbarContentSignal = scrollbarContentSignal;
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
     * 工厂：构建 ObjectField 组件函数。
     *
     * @param rt    场景运行时
     * @param props 输入契约
     * @return 组件函数
     */
    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        Objects.requireNonNull(rt, "rt");
        Objects.requireNonNull(props, "props");
        return () -> {
            SceneNode root = new SceneNode();
            root.setFlexDirection(FlexDirection.COLUMN);
            root.setGap(ROOT_GAP);

            SceneNode labelNode = textNode(props.label(), LABEL_COLOR);
            rt.show(root, Computed.create(() -> !props.label().isEmpty()), () -> labelNode);

            SceneNode viewport = new SceneNode();
            viewport.setFlexDirection(FlexDirection.COLUMN);
            viewport.setScrollable(true);
            viewport.setClipChildren(true);
            viewport.setFillParentHeight(true);
            viewport.setFlexGrow(1);

            // stackHost 承载 viewport 原 preferredHeight(VIEWPORT_HEIGHT)，并可选挂滚动条 column。
            // 即使无滚动条也建 stackHost，统一结构路径。
            SceneNode stackHost = new SceneNode();
            stackHost.setFlexDirection(FlexDirection.ROW);
            stackHost.setPreferredHeight(VIEWPORT_HEIGHT);
            stackHost.appendChild(viewport);

            Signal<Integer> scrollSignal = SceneScrolls.attach(rt, viewport);

            // 可选滚动条：scrollbarContentSignal 非 null 时建 bar，挂到 stackHost 右侧
            if (props.scrollbarContentSignal() != null) {
                SceneScrollbar.Props sbProps = new SceneScrollbar.Props(
                        viewport, scrollSignal, scrollSignal::set,
                        props.scrollbarContentSignal(),
                        SceneScrollbar.DEFAULT_TRACK_COLOR, SceneScrollbar.DEFAULT_THUMB_COLOR,
                        SceneScrollbar.DEFAULT_BAR_WIDTH, SceneScrollbar.DEFAULT_MIN_THUMB_HEIGHT);
                SceneScrollbar.Result sbResult = SceneScrollbar.create(rt, sbProps);
                stackHost.appendChild(sbResult.column());
            }

            root.appendChild(stackHost);

            SceneNode editor = buildObjectEditor(rt, props, "", 0);
            viewport.appendChild(editor);
            return root;
        };
    }

    /**
     * 递归构建对象编辑器。
     *
     * @param rt        场景运行时
     * @param props     输入契约
     * @param basePath  当前路径
     * @param depth     当前深度
     * @return 对象编辑器节点
     */
    private static SceneNode buildObjectEditor(SceneRuntime rt, Props props,
                                               String basePath, int depth) {
        SceneNode container = new SceneNode();
        container.setFlexDirection(FlexDirection.COLUMN);
        container.setGap(ROW_GAP);
        if (depth > 0) {
            container.setPadding(0, 0, 0, INDENT);
        }
        appendObjectEditorChildren(rt, props, container, basePath, depth);
        return container;
    }

    /**
     * 向容器追加对象编辑器的直接子节点。
     *
     * @param rt            场景运行时
     * @param props         输入契约
     * @param container     目标容器
     * @param basePath      当前路径
     * @param depth         当前深度
     */
    private static void appendObjectEditorChildren(SceneRuntime rt, Props props,
                                                   SceneNode container, String basePath, int depth) {

        if (depth >= props.maxDepth()) {
            container.appendChild(textNode("嵌套层级超出显示深度，请通过配置文件编辑此字段", NOTICE_COLOR));
            return;
        }

        Map<String, Object> current = safeMap((Map<String, Object>) navigate(props.value().get(), basePath));
        if (current.isEmpty()) {
            container.appendChild(textNode("空对象", MUTED_COLOR));
            return;
        }

        List<String> keys = new ArrayList<String>(current.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            Object fieldValue = current.get(key);
            FieldType fieldType = inferType(fieldValue);
            String path = joinPath(basePath, key);
            if (fieldType == FieldType.OBJECT) {
                container.appendChild(buildNestedObjectRow(rt, props, key, path, depth));
            } else if (fieldType == FieldType.LIST) {
                container.appendChild(buildPlaceholderRow(key, "列表编辑暂未实现"));
            } else {
                container.appendChild(buildScalarRow(rt, props, key, path, fieldType));
            }
        }
    }

    /**
     * 构建嵌套对象行。
     *
     * @param rt            场景运行时
     * @param props         输入契约
     * @param key           字段名
     * @param path          字段路径
     * @param depth         当前深度
     * @return 嵌套对象行节点
     */
    private static SceneNode buildNestedObjectRow(SceneRuntime rt, Props props,
                                                  String key, String path, int depth) {
        SceneNode row = new SceneNode();
        row.setFlexDirection(FlexDirection.COLUMN);
        row.setGap(ROW_GAP);

        SceneNode header = new SceneNode();
        header.setFlexDirection(FlexDirection.ROW);
        header.setCrossAxisAlign(CrossAxisAlign.CENTER);
        header.setGap(CELL_GAP);
        row.appendChild(header);

        SceneNode toggle = buttonNode("");
        rt.bind(Invalidation.LAYOUT, Computed.create(() -> isExpanded(props, path) ? "▾" : "▸"),
                text -> toggle.__getChildren().get(0).setText(text));
        rt.on(toggle, SceneEventType.CLICK, (ev, ctx) -> {
            toggleExpanded(props, path);
            ctx.stopPropagation();
        });
        header.appendChild(toggle);

        SceneNode label = textNode(key, LABEL_COLOR);
        label.setPreferredWidth(LABEL_WIDTH);
        header.appendChild(label);
        header.appendChild(textNode("对象", MUTED_COLOR));

        rt.show(row, Computed.create(() -> isExpanded(props, path)),
                () -> buildObjectEditor(rt, props, path, depth + 1));
        return row;
    }

    /**
     * 构建标量字段行。
     *
     * @param rt          场景运行时
     * @param props       输入契约
     * @param key         字段名
     * @param path        字段路径
     * @param fieldType   字段类型
     * @return 标量字段行节点
     */
    private static SceneNode buildScalarRow(SceneRuntime rt, Props props,
                                            String key, String path, FieldType fieldType) {
        SceneNode row = new SceneNode();
        row.setFlexDirection(FlexDirection.ROW);
        row.setCrossAxisAlign(CrossAxisAlign.CENTER);
        row.setGap(CELL_GAP);

        SceneNode label = textNode(key, LABEL_COLOR);
        label.setPreferredWidth(LABEL_WIDTH);
        row.appendChild(label);

        SceneInputType inputType = fieldType == FieldType.NUMBER ? SceneInputType.NUMBER : SceneInputType.TEXT;
        SceneTextInput.Props inputProps = new SceneTextInput.Props(
                Computed.create(() -> displayValue(navigate(safeMap(props.value().get()), path))),
                props.enabled(),
                props.readOnly(),
                "",
                Integer.MAX_VALUE,
                inputType,
                next -> updateField(props, path,
                        convertValue(navigate(safeMap(props.value().get()), path), next)));
        SceneNode input = SceneTextInput.create(rt, inputProps).get();
        input.setPreferredWidth(INPUT_WIDTH);
        input.setPreferredHeight(INPUT_HEIGHT);
        row.appendChild(input);
        return row;
    }

    /**
     * 构建暂不支持类型的占位行。
     *
     * @param key  字段名
     * @param text 占位文本
     * @return 占位行节点
     */
    private static SceneNode buildPlaceholderRow(String key, String text) {
        SceneNode row = new SceneNode();
        row.setFlexDirection(FlexDirection.ROW);
        row.setCrossAxisAlign(CrossAxisAlign.CENTER);
        row.setGap(CELL_GAP);
        SceneNode label = textNode(key, LABEL_COLOR);
        label.setPreferredWidth(LABEL_WIDTH);
        row.appendChild(label);
        row.appendChild(textNode(text, NOTICE_COLOR));
        return row;
    }

    /**
     * 写回字段值。
     *
     * @param props    输入契约
     * @param path     字段路径
     * @param newValue 新值
     */
    private static void updateField(Props props, String path, Object newValue) {
        String[] parts = path.split("\\.");
        Map<String, Object> next = updateFieldRecursive(safeMap(props.value().get()), parts, 0, newValue);
        Map<String, Object> immutable = Collections.unmodifiableMap(next);
        props.value().set(immutable);
        props.onValueChanged().accept(immutable);
    }

    /**
     * 递归复制命中路径并写入新值。
     *
     * @param current  当前 Map
     * @param parts    路径片段
     * @param index    当前片段下标
     * @param newValue 新值
     * @return 写入后的新 Map
     */
    private static Map<String, Object> updateFieldRecursive(Map<String, Object> current,
                                                            String[] parts, int index, Object newValue) {
        Map<String, Object> next = new LinkedHashMap<String, Object>();
        String targetKey = parts[index];
        for (Map.Entry<String, Object> entry : current.entrySet()) {
            if (entry.getKey().equals(targetKey)) {
                if (index == parts.length - 1) {
                    next.put(entry.getKey(), newValue);
                } else {
                    next.put(entry.getKey(), updateFieldRecursive(objectValue(entry.getValue()), parts, index + 1, newValue));
                }
            } else {
                next.put(entry.getKey(), entry.getValue());
            }
        }
        return next;
    }

    /**
     * 推断字段类型。
     *
     * @param value 字段值
     * @return 字段类型
     */
    public static FieldType inferType(Object value) {
        if (value == null) {
            return FieldType.NULL;
        }
        if (value instanceof String) {
            return FieldType.STRING;
        }
        if (value instanceof Number) {
            return FieldType.NUMBER;
        }
        if (value instanceof Boolean) {
            return FieldType.BOOLEAN;
        }
        if (value instanceof Map) {
            return FieldType.OBJECT;
        }
        if (value instanceof Collection) {
            return FieldType.LIST;
        }
        return FieldType.STRING;
    }

    /**
     * 构建字段条目列表。
     *
     * @param value         对象字段映射
     * @param expandedPaths 展开路径集合
     * @param basePath      当前路径
     * @return 字段条目列表
     */
    public static List<FieldEntry> entries(Map<String, Object> value, Set<String> expandedPaths, String basePath) {
        Map<String, Object> safe = safeMap(value);
        Set<String> expanded = expandedPaths == null ? Collections.<String>emptySet() : expandedPaths;
        List<String> keys = new ArrayList<String>(safe.keySet());
        Collections.sort(keys);
        List<FieldEntry> result = new ArrayList<FieldEntry>(keys.size());
        for (String key : keys) {
            Object fieldValue = safe.get(key);
            String path = joinPath(basePath, key);
            result.add(new FieldEntry(key, fieldValue, inferType(fieldValue), expanded.contains(path)));
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * 切换展开路径。
     *
     * @param props 输入契约
     * @param path  字段路径
     */
    private static void toggleExpanded(Props props, String path) {
        Set<String> next = new LinkedHashSet<String>();
        Set<String> current = props.expandedPaths().get();
        if (current != null) {
            next.addAll(current);
        }
        if (next.contains(path)) {
            next.remove(path);
        } else {
            next.add(path);
        }
        props.expandedPaths().set(Collections.unmodifiableSet(next));
    }

    /**
     * 判断路径是否展开。
     *
     * @param props 输入契约
     * @param path  字段路径
     * @return true 表示展开
     */
    private static boolean isExpanded(Props props, String path) {
        Set<String> expanded = props.expandedPaths().get();
        return expanded != null && expanded.contains(path);
    }

    /**
     * 转换输入文本为原字段类型。
     *
     * @param original 原始值
     * @param text     输入文本
     * @return 转换后的值
     */
    private static Object convertValue(Object original, String text) {
        if (original instanceof Boolean) {
            return Boolean.valueOf(text);
        }
        if (original instanceof Integer) {
            return parseInteger(text);
        }
        if (original instanceof Long) {
            return parseLong(text);
        }
        if (original instanceof Float) {
            return parseFloat(text);
        }
        if (original instanceof Double) {
            return parseDouble(text);
        }
        if (original instanceof Number) {
            return parseDouble(text);
        }
        return text;
    }

    /** @return 整数或原文本 */
    private static Object parseInteger(String text) {
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException ex) {
            return text;
        }
    }

    /** @return 长整数或原文本 */
    private static Object parseLong(String text) {
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException ex) {
            return text;
        }
    }

    /** @return 单精度数或原文本 */
    private static Object parseFloat(String text) {
        try {
            return Float.valueOf(text);
        } catch (NumberFormatException ex) {
            return text;
        }
    }

    /** @return 双精度数或原文本 */
    private static Object parseDouble(String text) {
        try {
            return Double.valueOf(text);
        } catch (NumberFormatException ex) {
            return text;
        }
    }

    /**
     * 转为展示文本。
     *
     * @param value 字段值
     * @return 展示文本
     */
    private static String displayValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * null 安全 Map。
     *
     * @param value 原 Map
     * @return 非 null Map
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? Collections.<String, Object>emptyMap() : value;
    }

    /**
     * 对象值兜底。
     *
     * @param value 原值
     * @return Map 值或空 Map
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectValue(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return Collections.emptyMap();
    }

    /**
     * 按路径从根 Map 导航取子值。
     *
     * @param root 根 Map
     * @param path 点号分隔路径，空字符串表示根
     * @return 路径对应的值，不存在返回 null
     */
    private static Object navigate(Map<String, Object> root, String path) {
        if (path == null || path.isEmpty()) {
            return root;
        }
        Object current = root;
        for (String part : path.split("\\.")) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    /**
     * 创建文本节点。
     *
     * @param text  文本
     * @param color 文本色
     * @return 文本节点
     */
    private static SceneNode textNode(String text, int color) {
        SceneNode node = new SceneNode();
        node.setHitTestable(false);
        node.setText(nullSafe(text));
        node.setTextColor(color);
        return node;
    }

    /**
     * 创建按钮节点。
     *
     * @param text 按钮文本
     * @return 按钮节点
     */
    private static SceneNode buttonNode(String text) {
        SceneNode button = new SceneNode();
        button.setFlexDirection(FlexDirection.ROW);
        button.setMainAxisAlign(MainAxisAlign.CENTER);
        button.setCrossAxisAlign(CrossAxisAlign.CENTER);
        button.setPadding(BUTTON_PADDING);
        button.setCornerRadius(BUTTON_RADIUS);
        button.setBackgroundColor(BUTTON_BG);
        button.setCursor(SceneCursor.POINTER);
        button.setWidthSizing(WidthSizing.SHRINK);

        SceneNode label = textNode(text, TEXT_COLOR);
        button.appendChild(label);
        return button;
    }

    /**
     * 拼接路径。
     *
     * @param basePath 父路径
     * @param key      字段名
     * @return 字段路径
     */
    private static String joinPath(String basePath, String key) {
        String safeKey = nullSafe(key);
        return nullSafe(basePath).isEmpty() ? safeKey : basePath + "." + safeKey;
    }

    /**
     * null 安全文本。
     *
     * @param value 原文本
     * @return 非 null 文本
     */
    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

}
