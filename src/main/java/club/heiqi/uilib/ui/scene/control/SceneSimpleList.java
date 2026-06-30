package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
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
 * SceneSimpleList —— scene 新栈动态字符串列表编辑器。
 *
 * <p>列表内容完全由外部 {@link Signal} 持有；控件只在输入事件中复制列表、替换条目并写回
 * {@code items} signal，然后通过 {@code onItemsChanged} 通知外层字段引擎。每行由 keyed
 * {@code forEach} 隔离，行内文本输入复用 {@link SceneTextInput} 的受控 value + onChange 契约。</p>
 *
 * <p><b>回调语义（先 set 再通知）</b>：控件在触发 {@code onItemsChanged} 之前，已将新列表
 * 不可变副本 {@code items.set(immutable)} 写入受控 signal。回调<b>仅供通知</b>，外部不应在
 * 回调里再次 {@code items.set(...)}——重复 set 属于冗余写入，且若外部不持有 signal 引用，
 * 行为将以控件写入为准。如需在变更后追加副作用（持久化、校验、联动其他 signal），在回调里
 * 读取参数即可，无需回写受控 signal。</p>
 */
public final class SceneSimpleList {

    /** 根节点纵向间距。 */
    private static final int ROOT_GAP = 6;
    /** 列表行间距。 */
    private static final int LIST_GAP = 4;
    /** 行内输入与按钮间距。 */
    private static final int ROW_GAP = 6;
    /** 按钮内边距。 */
    private static final int BUTTON_PADDING = SceneChromeTokens.PAD_MD;
    /** 圆角。 */
    private static final int RADIUS = SceneChromeTokens.RADIUS_MD;
    /** 删除按钮固定宽度。 */
    private static final int DELETE_BUTTON_WIDTH = 28;
    /** 行输入框默认宽度。 */
    private static final int INPUT_WIDTH = 240;
    /** 添加按钮固定高度，取自 chrome token。 */
    private static final int ADD_BUTTON_HEIGHT = SceneChromeTokens.BUTTON_HEIGHT;
    /** 按钮背景色。 */
    private static final int BUTTON_BG = SceneChromeTokens.BG_DEFAULT;
    /** 按钮禁用背景色。 */
    private static final int BUTTON_BG_DISABLED = SceneChromeTokens.BG_DISABLED;
    /** 删除按钮背景色，取自 chrome token。 */
    private static final int DELETE_BG = SceneChromeTokens.DANGER_BG;
    /** 删除按钮禁用背景色，取自 chrome token。 */
    private static final int DELETE_BG_DISABLED = SceneChromeTokens.DANGER_BG_DISABLED;
    /** 文本颜色。 */
    private static final int TEXT_COLOR = SceneChromeTokens.TEXT_ON_ACCENT;
    /** 禁用文本颜色。 */
    private static final int TEXT_DISABLED = SceneChromeTokens.TEXT_DISABLED;
    /** 行 id 分配器，用于 keyed 列表稳定身份。 */
    private static final AtomicLong NEXT_ITEM_ID = new AtomicLong(1L);

    /** 纯静态工厂，禁止实例化。 */
    private SceneSimpleList() {
    }

    /**
     * SimpleList 单行数据。
     *
     * <p>每行携带稳定 id 供 keyed 列表复用节点；编辑 value 时通过 {@link #copyWith(String)}
     * 生成同 id 新对象，避免文本变化导致行重建。</p>
     */
    public static final class ListItem {
        /** 稳定行 id。 */
        private final long id;
        /** 行文本。 */
        private final String value;

        /**
         * 创建空文本行。
         */
        public ListItem() {
            this("");
        }

        /**
         * 创建一行文本。
         *
         * @param value 行文本
         */
        public ListItem(String value) {
            this(NEXT_ITEM_ID.getAndIncrement(), value);
        }

        /**
         * 创建带稳定 id 的行。
         *
         * @param id    稳定行 id
         * @param value 行文本
         */
        private ListItem(long id, String value) {
            this.id = id;
            this.value = nullSafe(value);
        }

        /** @return 稳定行 id */
        public long getId() {
            return id;
        }

        /** @return 行文本 */
        public String getValue() {
            return value;
        }

        /**
         * 复制为同 id 的新行。
         *
         * @param value 新文本
         * @return 新行对象
         */
        public ListItem copyWith(String value) {
            return new ListItem(id, value);
        }
    }

    /**
     * SimpleList 输入契约 —— 受控字符串列表与字段引擎回调接入点。
     */
    public static final class Props {
        /** 列表内容受控 signal。 */
        private final Signal<List<ListItem>> items;
        /** 控件标题，可为空。 */
        private final String label;
        /** 行输入占位文本，可为空。 */
        private final String placeholder;
        /** 列表变更回调。控件在回调前已将新值写入 {@code items} signal，回调仅供通知，无需再次 set。 */
        private final Consumer<List<ListItem>> onItemsChanged;
        /** 最大条目数，0 表示无限。 */
        private final int maxItems;
        /** 最小条目数，0 表示无限制。 */
        private final int minItems;
        /** 控件级启用信号，控制行内 TextInput 的 enabled；默认恒为 true。 */
        private final ReadableSignal<Boolean> enabled;
        /** 控件级只读信号，控制行内 TextInput 的 readOnly；默认恒为 false。 */
        private final ReadableSignal<Boolean> readOnly;

        /**
         * 创建输入契约。
         *
         * @param items          列表内容受控 signal
         * @param label          控件标题，可为 null
         * @param placeholder    行输入占位文本，可为 null
         * @param onItemsChanged 列表变更回调，可为 null。控件在回调前已将新值写入 {@code items} signal，回调仅供通知，无需再次 set
         * @param maxItems       最大条目数，0 表示无限
         * @param minItems       最小条目数，0 表示无限制
         */
        public Props(Signal<List<ListItem>> items,
                     String label,
                     String placeholder,
                     Consumer<List<ListItem>> onItemsChanged,
                     int maxItems,
                     int minItems) {
            this(items, label, placeholder, onItemsChanged, maxItems, minItems, null, null);
        }

        /**
         * 创建输入契约并注入控件级 enabled/readOnly 信号。
         *
         * @param items          列表内容受控 signal
         * @param label          控件标题，可为 null
         * @param placeholder    行输入占位文本，可为 null
         * @param onItemsChanged 列表变更回调，可为 null。控件在回调前已将新值写入 {@code items} signal，回调仅供通知，无需再次 set
         * @param maxItems       最大条目数，0 表示无限
         * @param minItems       最小条目数，0 表示无限制
         * @param enabled        控件级启用信号，null 时默认恒为 true
         * @param readOnly       控件级只读信号，null 时默认恒为 false
         */
        public Props(Signal<List<ListItem>> items,
                     String label,
                     String placeholder,
                     Consumer<List<ListItem>> onItemsChanged,
                     int maxItems,
                     int minItems,
                     ReadableSignal<Boolean> enabled,
                     ReadableSignal<Boolean> readOnly) {
            this.items = Objects.requireNonNull(items, "items");
            this.label = label == null ? "" : label;
            this.placeholder = placeholder == null ? "" : placeholder;
            this.onItemsChanged = onItemsChanged == null ? ignored -> { } : onItemsChanged;
            this.maxItems = Math.max(0, maxItems);
            this.minItems = Math.max(0, minItems);
            this.enabled = enabled == null ? Signal.create(Boolean.TRUE) : enabled;
            this.readOnly = readOnly == null ? Signal.create(Boolean.FALSE) : readOnly;
        }

        /**
         * 创建 Props builder。
         *
         * @param items 列表内容受控 signal
         * @return builder 实例
         */
        public static Builder builder(Signal<List<ListItem>> items) {
            return new Builder(items);
        }

        /** @return 列表内容受控 signal */
        public Signal<List<ListItem>> items() {
            return items;
        }

        /** @return 控件标题 */
        public String label() {
            return label;
        }

        /** @return 行输入占位文本 */
        public String placeholder() {
            return placeholder;
        }

        /** @return 列表变更回调。控件在回调前已将新值写入 {@code items} signal，回调仅供通知，无需再次 set */
        public Consumer<List<ListItem>> onItemsChanged() {
            return onItemsChanged;
        }

        /** @return 最大条目数，0 表示无限 */
        public int maxItems() {
            return maxItems;
        }

        /** @return 最小条目数，0 表示无限制 */
        public int minItems() {
            return minItems;
        }

        /** @return 控件级启用信号，缺省时恒为 true */
        public ReadableSignal<Boolean> enabled() {
            return enabled;
        }

        /** @return 控件级只读信号，缺省时恒为 false */
        public ReadableSignal<Boolean> readOnly() {
            return readOnly;
        }

        /** Props 构建器。 */
        public static final class Builder {
            /** 列表内容受控 signal。 */
            private final Signal<List<ListItem>> items;
            /** 控件标题。 */
            private String label = "";
            /** 行输入占位文本。 */
            private String placeholder = "";
            /** 列表变更回调。控件在回调前已将新值写入 {@code items} signal，回调仅供通知，无需再次 set。 */
            private Consumer<List<ListItem>> onItemsChanged;
            /** 最大条目数。 */
            private int maxItems;
            /** 最小条目数。 */
            private int minItems;
            /** 控件级启用信号。 */
            private ReadableSignal<Boolean> enabled;
            /** 控件级只读信号。 */
            private ReadableSignal<Boolean> readOnly;

            /**
             * 创建构建器。
             *
             * @param items 列表内容受控 signal
             */
            private Builder(Signal<List<ListItem>> items) {
                this.items = Objects.requireNonNull(items, "items");
            }

            /**
             * 设置控件标题。
             *
             * @param label 控件标题
             * @return 当前 builder
             */
            public Builder label(String label) {
                this.label = label;
                return this;
            }

            /**
             * 设置行输入占位文本。
             *
             * @param placeholder 行输入占位文本
             * @return 当前 builder
             */
            public Builder placeholder(String placeholder) {
                this.placeholder = placeholder;
                return this;
            }

            /**
             * 设置列表变更回调。
             *
             * @param onItemsChanged 列表变更回调。控件在回调前已将新值写入 {@code items} signal，回调仅供通知，无需再次 set
             * @return 当前 builder
             */
            public Builder onItemsChanged(Consumer<List<ListItem>> onItemsChanged) {
                this.onItemsChanged = onItemsChanged;
                return this;
            }

            /**
             * 设置最大条目数。
             *
             * @param maxItems 最大条目数，0 表示无限
             * @return 当前 builder
             */
            public Builder maxItems(int maxItems) {
                this.maxItems = maxItems;
                return this;
            }

            /**
             * 设置最小条目数。
             *
             * @param minItems 最小条目数，0 表示无限制
             * @return 当前 builder
             */
            public Builder minItems(int minItems) {
                this.minItems = minItems;
                return this;
            }

            /**
             * 设置控件级启用信号。
             *
             * @param enabled 启用信号，null 时 build 后默认恒为 true
             * @return 当前 builder
             */
            public Builder enabled(ReadableSignal<Boolean> enabled) {
                this.enabled = enabled;
                return this;
            }

            /**
             * 设置控件级只读信号。
             *
             * @param readOnly 只读信号，null 时 build 后默认恒为 false
             * @return 当前 builder
             */
            public Builder readOnly(ReadableSignal<Boolean> readOnly) {
                this.readOnly = readOnly;
                return this;
            }

            /**
             * 构建 Props。
             *
             * @return Props 实例
             */
            public Props build() {
                return new Props(items, label, placeholder, onItemsChanged, maxItems, minItems, enabled, readOnly);
            }
        }
    }

    /**
     * 工厂：构建 SimpleList 组件函数。
     *
     * @param rt    场景运行时
     * @param props SimpleList 输入契约
     * @return 组件函数，交 {@link SceneRuntime#mount} 挂载
     */
    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        Objects.requireNonNull(rt, "rt");
        Objects.requireNonNull(props, "props");
        return () -> {
            Computed<List<ListItem>> rowItems = Computed.create(() -> safeItems(props.items().get()));

            SceneNode root = new SceneNode();
            root.setFlexDirection(FlexDirection.COLUMN);
            root.setGap(ROOT_GAP);

            SceneNode labelNode = new SceneNode();
            labelNode.setHitTestable(false);
            labelNode.setText(props.label());
            labelNode.setTextColor(TEXT_COLOR);
            rt.show(root, Computed.create(() -> !props.label().isEmpty()), () -> labelNode);

            SceneNode listViewport = new SceneNode();
            listViewport.setFlexDirection(FlexDirection.COLUMN);
            listViewport.setGap(LIST_GAP);
            listViewport.setScrollable(true);
            listViewport.setClipChildren(true);
            listViewport.setFillParentHeight(true);
            root.appendChild(listViewport);

            Signal<Integer> scrollSignal = SceneScrolls.attach(rt, listViewport);

            rt.forEach(listViewport, rowItems, ListItem::getId,
                    row -> buildRow(rt, props, row));

            SceneNode addButton = createButton("添加", BUTTON_BG, 0);
            addButton.setPreferredHeight(ADD_BUTTON_HEIGHT);
            Computed<Boolean> addEnabled = Computed.create(() -> canAdd(props.items().get(), props.maxItems()));
            rt.bind(Invalidation.PAINT,
                    addEnabled,
                    enabled -> applyButtonEnabled(addButton, BUTTON_BG, enabled));
            // 与 SceneKeyValueMap 行为对齐：操作按钮进 Tab 焦点环，disabled 时自动退出
            rt.focusable(addButton, addEnabled);
            rt.on(addButton, SceneEventType.CLICK, (ev, ctx) -> {
                if (canAdd(props.items().get(), props.maxItems())) {
                    List<ListItem> next = mutableItems(props.items().get());
                    next.add(new ListItem(""));
                    commit(props, next);
                }
                ctx.stopPropagation();
            });
            root.appendChild(addButton);

            return root;
        };
    }

    /**
     * 构建单行编辑节点。
     *
     * @param rt       场景运行时
     * @param props    SimpleList 输入契约
     * @param row      行数据
     * @return 行根节点
     */
    private static SceneNode buildRow(SceneRuntime rt, Props props, ListItem row) {
        SceneNode line = new SceneNode();
        line.setFlexDirection(FlexDirection.ROW);
        line.setCrossAxisAlign(CrossAxisAlign.CENTER);
        line.setGap(ROW_GAP);

        SceneTextInput.Props inputProps = new SceneTextInput.Props(
                Computed.create(() -> currentItem(props.items().get(), row).getValue()),
                props.enabled(),
                props.readOnly(),
                props.placeholder(),
                Integer.MAX_VALUE,
                SceneInputType.TEXT,
                next -> replaceItem(props, row.getId(), next));
        SceneNode input = SceneTextInput.create(rt, inputProps).get();
        input.setPreferredWidth(INPUT_WIDTH);
        line.appendChild(input);

        SceneNode deleteButton = createButton("×", DELETE_BG, DELETE_BUTTON_WIDTH);
        Computed<Boolean> deleteEnabled = Computed.create(() -> canDelete(props.items().get(), props.minItems()));
        rt.bind(Invalidation.PAINT,
                deleteEnabled,
                enabled -> applyButtonEnabled(deleteButton, DELETE_BG, enabled));
        // 与 SceneKeyValueMap 行为对齐：行内删除按钮进 Tab 焦点环，disabled 时自动退出
        rt.focusable(deleteButton, deleteEnabled);
        rt.on(deleteButton, SceneEventType.CLICK, (ev, ctx) -> {
            if (canDelete(props.items().get(), props.minItems())) {
                removeItem(props, row.getId());
            }
            ctx.stopPropagation();
        });
        line.appendChild(deleteButton);

        return line;
    }

    /**
     * 创建文本按钮节点。
     *
     * @param text           按钮文本
     * @param background     背景色
     * @param preferredWidth 固定宽度，0 表示不设置
     * @return 按钮节点
     */
    private static SceneNode createButton(String text, int background, int preferredWidth) {
        SceneNode button = new SceneNode();
        button.setFlexDirection(FlexDirection.ROW);
        button.setMainAxisAlign(MainAxisAlign.CENTER);
        button.setCrossAxisAlign(CrossAxisAlign.CENTER);
        button.setPadding(BUTTON_PADDING);
        button.setCornerRadius(RADIUS);
        button.setBackgroundColor(background);
        button.setCursor(SceneCursor.POINTER);
        if (preferredWidth > 0) {
            button.setPreferredWidth(preferredWidth);
        } else {
            button.setWidthSizing(WidthSizing.SHRINK);
        }

        SceneNode label = new SceneNode();
        label.setHitTestable(false);
        label.setText(text);
        label.setTextColor(TEXT_COLOR);
        button.appendChild(label);
        return button;
    }

    /**
     * 应用按钮启用态外观。
     *
     * @param button     按钮节点
     * @param enabledBg  启用背景色
     * @param enabledObj 是否启用
     */
    private static void applyButtonEnabled(SceneNode button, int enabledBg, Boolean enabledObj) {
        boolean enabled = Boolean.TRUE.equals(enabledObj);
        button.setBackgroundColor(enabled ? enabledBg
                : (enabledBg == DELETE_BG ? DELETE_BG_DISABLED : BUTTON_BG_DISABLED));
        button.setCursor(enabled ? SceneCursor.POINTER : SceneCursor.NOT_ALLOWED);
        if (!button.__getChildren().isEmpty()) {
            button.__getChildren().get(0).setTextColor(enabled ? TEXT_COLOR : TEXT_DISABLED);
        }
    }

    /**
     * 替换指定行文本。
     *
     * @param props SimpleList 输入契约
     * @param itemId 目标行 id
     * @param value 新文本
     */
    private static void replaceItem(Props props, long itemId, String value) {
        List<ListItem> current = safeItems(props.items().get());
        List<ListItem> next = new ArrayList<>(current.size());
        boolean changed = false;
        for (ListItem item : current) {
            if (item.getId() == itemId) {
                next.add(item.copyWith(value));
                changed = true;
            } else {
                next.add(item);
            }
        }
        if (changed) {
            commit(props, next);
        }
    }

    /**
     * 删除指定行。
     *
     * @param props SimpleList 输入契约
     * @param itemId 目标行 id
     */
    private static void removeItem(Props props, long itemId) {
        List<ListItem> current = safeItems(props.items().get());
        List<ListItem> next = new ArrayList<>(current.size());
        for (ListItem item : current) {
            if (item.getId() != itemId) {
                next.add(item);
            }
        }
        commit(props, next);
    }

    /**
     * 提交列表变更。
     *
     * @param props SimpleList 输入契约
     * @param next  下一版列表
     */
    private static void commit(Props props, List<ListItem> next) {
        List<ListItem> immutable = Collections.unmodifiableList(new ArrayList<>(next));
        props.items().set(immutable);
        props.onItemsChanged().accept(immutable);
    }

    /**
     * 判断是否允许添加。
     *
     * @param items    当前列表
     * @param maxItems 最大条目数
     * @return true 表示允许添加
     */
    private static boolean canAdd(List<ListItem> items, int maxItems) {
        return maxItems <= 0 || safeSize(items) < maxItems;
    }

    /**
     * 判断是否允许删除。
     *
     * @param items    当前列表
     * @param minItems 最小条目数
     * @return true 表示允许删除
     */
    private static boolean canDelete(List<ListItem> items, int minItems) {
        return minItems <= 0 || safeSize(items) > minItems;
    }

    /**
     * 获取列表安全长度。
     *
     * @param items 列表，可为 null
     * @return 列表长度
     */
    private static int safeSize(List<ListItem> items) {
        return items == null ? 0 : items.size();
    }

    /**
     * 复制列表为可变列表。
     *
     * @param items 原列表，可为 null
     * @return 可变副本
     */
    private static List<ListItem> mutableItems(List<ListItem> items) {
        return items == null ? new ArrayList<>() : new ArrayList<>(items);
    }

    /**
     * null 安全行列表。
     *
     * @param items 行列表
     * @return 安全行列表
     */
    private static List<ListItem> safeItems(List<ListItem> items) {
        return items == null ? Collections.emptyList() : items;
    }

    /**
     * 读取当前行快照。
     *
     * @param items    行列表
     * @param fallback 兜底行
     * @return 当前行或兜底行
     */
    private static ListItem currentItem(List<ListItem> items, ListItem fallback) {
        for (ListItem item : safeItems(items)) {
            if (item.getId() == fallback.getId()) {
                return item;
            }
        }
        return fallback;
    }

    /**
     * null 安全字符串。
     *
     * @param value 原文本
     * @return 非 null 文本
     */
    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

}
