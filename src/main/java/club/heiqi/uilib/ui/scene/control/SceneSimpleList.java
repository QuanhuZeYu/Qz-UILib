package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.Invalidation;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.SceneNode.WidthSizing;

/**
 * SceneSimpleList —— scene 新栈动态字符串列表编辑器。
 *
 * <p>列表内容完全由外部 {@link Signal} 持有；控件只在输入事件中复制列表、替换条目并写回
 * {@code items} signal，然后通过 {@code onItemsChanged} 通知外层字段引擎。每行由 keyed
 * {@code forEach} 隔离，行内文本输入复用 {@link SceneTextInput} 的受控 value + onChange 契约。</p>
 */
public final class SceneSimpleList {

    /** 根节点纵向间距。 */
    private static final int ROOT_GAP = 6;
    /** 列表行间距。 */
    private static final int LIST_GAP = 4;
    /** 行内输入与按钮间距。 */
    private static final int ROW_GAP = 6;
    /** 按钮内边距。 */
    private static final int BUTTON_PADDING = 6;
    /** 圆角。 */
    private static final int RADIUS = 4;
    /** 删除按钮固定宽度。 */
    private static final int DELETE_BUTTON_WIDTH = 28;
    /** 行输入框默认宽度。 */
    private static final int INPUT_WIDTH = 240;
    /** 添加按钮固定高度。 */
    private static final int ADD_BUTTON_HEIGHT = 28;
    /** 按钮背景色。 */
    private static final int BUTTON_BG = 0xFF3A3A3A;
    /** 按钮禁用背景色。 */
    private static final int BUTTON_BG_DISABLED = 0xFF2F2F2F;
    /** 删除按钮背景色。 */
    private static final int DELETE_BG = 0xFF7F1D1D;
    /** 删除按钮禁用背景色。 */
    private static final int DELETE_BG_DISABLED = 0xFF3F2A2A;
    /** 文本颜色。 */
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    /** 禁用文本颜色。 */
    private static final int TEXT_DISABLED = 0xFF888888;
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
    public static class Props {
        /** 列表内容受控 signal。 */
        private final Signal<List<ListItem>> items;
        /** 控件标题，可为空。 */
        private final String label;
        /** 行输入占位文本，可为空。 */
        private final String placeholder;
        /** 列表变更回调。 */
        private final Consumer<List<ListItem>> onItemsChanged;
        /** 最大条目数，0 表示无限。 */
        private final int maxItems;
        /** 最小条目数，0 表示无限制。 */
        private final int minItems;

        /**
         * 创建输入契约。
         *
         * @param items          列表内容受控 signal
         * @param label          控件标题，可为 null
         * @param placeholder    行输入占位文本，可为 null
         * @param onItemsChanged 列表变更回调，可为 null
         * @param maxItems       最大条目数，0 表示无限
         * @param minItems       最小条目数，0 表示无限制
         */
        public Props(Signal<List<ListItem>> items,
                     String label,
                     String placeholder,
                     Consumer<List<ListItem>> onItemsChanged,
                     int maxItems,
                     int minItems) {
            this.items = Objects.requireNonNull(items, "items");
            this.label = label == null ? "" : label;
            this.placeholder = placeholder == null ? "" : placeholder;
            this.onItemsChanged = onItemsChanged == null ? ignored -> { } : onItemsChanged;
            this.maxItems = Math.max(0, maxItems);
            this.minItems = Math.max(0, minItems);
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

        /** @return 列表变更回调 */
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

        /** Props 构建器。 */
        public static final class Builder {
            /** 列表内容受控 signal。 */
            private final Signal<List<ListItem>> items;
            /** 控件标题。 */
            private String label = "";
            /** 行输入占位文本。 */
            private String placeholder = "";
            /** 列表变更回调。 */
            private Consumer<List<ListItem>> onItemsChanged;
            /** 最大条目数。 */
            private int maxItems;
            /** 最小条目数。 */
            private int minItems;

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
             * @param onItemsChanged 列表变更回调
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
             * 构建 Props。
             *
             * @return Props 实例
             */
            public Props build() {
                return new Props(items, label, placeholder, onItemsChanged, maxItems, minItems);
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

            Signal<Integer> scrollSignal = Signal.create(Integer.valueOf(0));
            rt.bind(Invalidation.COMPOSITE, scrollSignal,
                    v -> listViewport.setScrollOffsetY(v.intValue()));
            rt.on(listViewport, SceneEventType.SCROLL, (ev, ctx) -> {
                int maxScrollY = SceneGeometry.maxScrollY(listViewport);
                int current = scrollSignal.get().intValue();
                int next = current - ev.getWheelDelta();
                int clamped = clamp(next, 0, maxScrollY);
                if (clamped != current) {
                    scrollSignal.set(Integer.valueOf(clamped));
                    ctx.stopPropagation();
                }
            });

            rt.forEach(listViewport, rowItems, ListItem::getId,
                    row -> buildRow(rt, props, row));

            SceneNode addButton = createButton("添加", BUTTON_BG, 0);
            addButton.setPreferredHeight(ADD_BUTTON_HEIGHT);
            rt.bind(Invalidation.PAINT,
                    Computed.create(() -> canAdd(props.items().get(), props.maxItems())),
                    enabled -> applyButtonEnabled(addButton, BUTTON_BG, enabled));
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
                Signal.create(Boolean.TRUE),
                Signal.create(Boolean.FALSE),
                props.placeholder(),
                Integer.MAX_VALUE,
                SceneInputType.TEXT,
                next -> replaceItem(props, row.getId(), next));
        SceneNode input = SceneTextInput.create(rt, inputProps).get();
        input.setPreferredWidth(INPUT_WIDTH);
        line.appendChild(input);

        SceneNode deleteButton = createButton("×", DELETE_BG, DELETE_BUTTON_WIDTH);
        rt.bind(Invalidation.PAINT,
                Computed.create(() -> canDelete(props.items().get(), props.minItems())),
                enabled -> applyButtonEnabled(deleteButton, DELETE_BG, enabled));
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

    /**
     * 数值钳制到闭区间。
     *
     * @param value 原值
     * @param min 最小值
     * @param max 最大值
     * @return 钳制后的值
     */
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
