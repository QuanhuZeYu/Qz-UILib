package club.heiqi.uilib.ui.scene.control;

import static club.heiqi.uilib.ui.scene.control.SceneTextUtils.nullSafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;
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
    /** SimpleList 行 id 读取器。 */
    private static final ToLongFunction<ListItem> LIST_ITEM_ID = item -> item.getId();

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
         * 是否在视口右侧叠加 {@link SceneScrollbar}。false 表示不建滚动条（向后兼容）；
         * true 时控件在视口右侧叠加滚动条，滑块几何由 runtime layoutDoneSignal 驱动重算。
         */
        private final boolean showScrollbar;
        /**
         * 是否启用行拖拽排序。false（默认）表示不渲染拖拽把手、不响应拖拽（向后兼容）；
         * true 时每行行首渲染拖拽把手，按档 A 越界跳变语义重排（守硬约束§5：拖拽瞬态只存 handler
         * 局部闭包变量，不 signal 化；重排经 {@code items.set → keyed diff} 平移节点，I5）。
         */
        private final boolean draggable;

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
            this(items, label, placeholder, onItemsChanged, maxItems, minItems, null, null, false);
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
             this(items, label, placeholder, onItemsChanged, maxItems, minItems, enabled, readOnly, false, false);
        }

        /**
         * 创建输入契约并注入控件级 enabled/readOnly 信号与可选滚动条内容信号。
         *
         * @param items                  列表内容受控 signal
         * @param label                  控件标题，可为 null
         * @param placeholder            行输入占位文本，可为 null
         * @param onItemsChanged         列表变更回调，可为 null。控件在回调前已将新值写入 {@code items} signal，回调仅供通知，无需再次 set
         * @param maxItems               最大条目数，0 表示无限
         * @param minItems               最小条目数，0 表示无限制
         * @param enabled                控件级启用信号，null 时默认恒为 true
         * @param readOnly               控件级只读信号，null 时默认恒为 false
         * @param showScrollbar          是否建滚动条，false 表示不建
         */
        public Props(Signal<List<ListItem>> items,
                     String label,
                     String placeholder,
                     Consumer<List<ListItem>> onItemsChanged,
                     int maxItems,
                     int minItems,
                     ReadableSignal<Boolean> enabled,
                     ReadableSignal<Boolean> readOnly,
                     boolean showScrollbar) {
            this(items, label, placeholder, onItemsChanged, maxItems, minItems, enabled, readOnly, showScrollbar, false);
        }

        /**
         * 创建输入契约并注入控件级 enabled/readOnly 信号、可选滚动条与拖拽排序开关。
         *
         * @param items                  列表内容受控 signal
         * @param label                  控件标题，可为 null
         * @param placeholder            行输入占位文本，可为 null
         * @param onItemsChanged         列表变更回调，可为 null。控件在回调前已将新值写入 {@code items} signal，回调仅供通知，无需再次 set
         * @param maxItems               最大条目数，0 表示无限
         * @param minItems               最小条目数，0 表示无限制
         * @param enabled                控件级启用信号，null 时默认恒为 true
         * @param readOnly               控件级只读信号，null 时默认恒为 false
         * @param showScrollbar          是否建滚动条，false 表示不建
         * @param draggable              是否启用行拖拽排序，false 表示不建把手、不响应拖拽（向后兼容）
         */
        public Props(Signal<List<ListItem>> items,
                     String label,
                     String placeholder,
                     Consumer<List<ListItem>> onItemsChanged,
                     int maxItems,
                     int minItems,
                     ReadableSignal<Boolean> enabled,
                     ReadableSignal<Boolean> readOnly,
                     boolean showScrollbar,
                     boolean draggable) {
            this.items = Objects.requireNonNull(items, "items");
            this.label = label == null ? "" : label;
            this.placeholder = placeholder == null ? "" : placeholder;
            this.onItemsChanged = onItemsChanged == null ? ignored -> { } : onItemsChanged;
            this.maxItems = Math.max(0, maxItems);
            this.minItems = Math.max(0, minItems);
            this.enabled = enabled == null ? Signal.create(Boolean.TRUE) : enabled;
            this.readOnly = readOnly == null ? Signal.create(Boolean.FALSE) : readOnly;
            this.showScrollbar = showScrollbar;
            this.draggable = draggable;
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

        /** @return 是否建滚动条 */
        public boolean showScrollbar() {
            return showScrollbar;
        }

        /** @return 是否启用行拖拽排序 */
        public boolean draggable() {
            return draggable;
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
            /** 是否建滚动条，false 表示不建。 */
            private boolean showScrollbar;
            /** 是否启用行拖拽排序，false 表示不建把手、不响应拖拽（向后兼容）。 */
            private boolean draggable;

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
             * 设置是否建滚动条。
             *
             * @param showScrollbar 是否建滚动条，false 表示不建
             * @return 当前 builder
             */
            public Builder showScrollbar(boolean showScrollbar) {
                this.showScrollbar = showScrollbar;
                return this;
            }

            /**
             * 设置是否启用行拖拽排序。
             *
             * @param draggable 是否启用拖拽排序，false 表示不建把手、不响应拖拽（向后兼容）
             * @return 当前 builder
             */
            public Builder draggable(boolean draggable) {
                this.draggable = draggable;
                return this;
            }

            /**
             * 构建 Props。
             *
             * @return Props 实例
             */
            public Props build() {
                return new Props(items, label, placeholder, onItemsChanged, maxItems, minItems, enabled, readOnly, showScrollbar, draggable);
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
            Computed<List<ListItem>> rowItems = Computed.create(() -> SceneListOps.safeList(props.items().get()));

            SceneNode root = SceneNode.column();
            root.setGap(ROOT_GAP);

            SceneNode labelNode = new SceneNode();
            labelNode.setHitTestable(false);
            labelNode.setText(props.label());
            labelNode.setTextColor(TEXT_COLOR);
            rt.show(root, Computed.create(() -> !props.label().isEmpty()), () -> labelNode);

            SceneNode listViewport = SceneNode.column();
            listViewport.setGap(LIST_GAP);
            listViewport.setScrollable(true);
            listViewport.setClipChildren(true);
            listViewport.setFillParentHeight(true);
            listViewport.setFlexGrow(1);

            // stackHost 承载 viewport 原 fillParentHeight 模式，并在 showScrollbar 为 true 时
            // 叠加 SceneScrollbar column。即使无滚动条也建 stackHost，统一结构路径。
            SceneNode stackHost = SceneNode.row();
            stackHost.setFillParentHeight(true);
            stackHost.appendChild(listViewport);

            Signal<Integer> scrollSignal = SceneScrolls.attach(rt, listViewport);

            // 可选滚动条：showScrollbar 为 true 时建 bar，挂到 stackHost 右侧
            if (props.showScrollbar()) {
                SceneScrollbar.Result sbResult = SceneScrollbar.createDefault(rt, listViewport, scrollSignal);
                stackHost.appendChild(sbResult.column());
            }

            root.appendChild(stackHost);

            rt.forEach(listViewport, rowItems, ListItem::getId,
                    row -> buildRow(rt, props, listViewport, row));

            Computed<Boolean> addEnabled = Computed.create(() -> SceneListOps.canAdd(props.items().get(), props.maxItems()));
            SceneNode addButton = createButton(rt, "添加", BUTTON_BG, 0, addEnabled);
            addButton.setPreferredHeight(ADD_BUTTON_HEIGHT);
            // 与 SceneKeyValueMap 行为对齐：操作按钮进 Tab 焦点环，disabled 时自动退出
            rt.focusable(addButton, addEnabled);
            rt.on(addButton, SceneEventType.CLICK, (ev, ctx) -> {
                if (SceneListOps.canAdd(props.items().get(), props.maxItems())) {
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
     * <p>draggable=true 时行首追加拖拽把手（独立交互单元，承接 POINTER_DOWN 启动拖拽）；
     * 把手内图标 hitTestable=false 穿透到把手（R6）。</p>
     *
     * @param rt       场景运行时
     * @param props    SimpleList 输入契约
     * @param viewport 列表视口（拖拽 MOVE 时按其子节点 box 定位目标行）
     * @param row      行数据
     * @return 行根节点
     */
    private static SceneNode buildRow(SceneRuntime rt, Props props, SceneNode viewport, ListItem row) {
        SceneNode line = SceneNode.row();
        line.setCrossAxisAlign(CrossAxisAlign.CENTER);
        line.setGap(ROW_GAP);

        // draggable=true 时行首渲染拖拽把手（档 A 越界跳变基建）
        if (props.draggable()) {
            line.appendChild(buildDragHandle(rt, props, viewport, row));
        }

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

        Computed<Boolean> deleteEnabled = Computed.create(() -> SceneListOps.canRemove(props.items().get(), props.minItems()));
        SceneNode deleteButton = createButton(rt, "×", DELETE_BG, DELETE_BUTTON_WIDTH, deleteEnabled);
        // 与 SceneKeyValueMap 行为对齐：行内删除按钮进 Tab 焦点环，disabled 时自动退出
        rt.focusable(deleteButton, deleteEnabled);
        rt.on(deleteButton, SceneEventType.CLICK, (ev, ctx) -> {
            if (SceneListOps.canRemove(props.items().get(), props.minItems())) {
                removeItem(props, row.getId());
            }
            ctx.stopPropagation();
        });
        line.appendChild(deleteButton);

        return line;
    }

    /**
     * 构建拖拽把手节点并注册四段式拖拽 handler（复刻 SceneScrollbar 范式）。
     *
     * <p><b>档 A 越界跳变</b>：拖拽瞬态（起点 id、dragging 标志）存 handler 局部闭包 final 容器，
     * <b>不 signal 化</b>（守硬约束§5、R1：不加实例字段）。POINTER_DOWN 记起点 + requestPointerCapture；
     * MOVE 按指针 Y 落点行算目标 index，与 dragId 当前 index 不同则 moveItem 重排（id 保留，引用移动），
     * 重排经 {@code items.set → keyed diff} 平移节点（守 R4：不改节点 setXxx；I5：id 不变复用节点）；
     * UP/CANCEL 清 dragging 释放 capture。</p>
     *
     * <p>坐标系（I12 两层）：treeRootAbsY 不可直接读，由 {@code (rawY - handleLocalY) - absBox(handle,0,0).y}
     * 反推（handle 为 currentNode 时 {@code localY = rawY - absBox(handle,treeRootAbs).y}，二者配合消去 rootAbs）。
     * 再据此把各行子节点 layout Y 平移到屏幕系与 rawPointerY 同系比对，生产（rootAbs≠0）与测试（rootAbs=0）通用。</p>
     *
     * @param rt       场景运行时
     * @param props    SimpleList 输入契约
     * @param viewport 列表视口（拖拽 MOVE 时遍历其子节点 box 定位目标行）
     * @param row      行数据（id 在闭包内捕获为 final，恒定）
     * @return 把手节点
     */
    private static SceneNode buildDragHandle(SceneRuntime rt, Props props, SceneNode viewport, ListItem row) {
        // dragId 不可变：row.getId() 在 keyed diff 复用期间恒定（buildRow 仅建一次）
        final long dragId = row.getId();
        return SceneDragReorder.buildHandle(rt, viewport, null, dragId, props.items(), LIST_ITEM_ID,
                next -> commit(props, next), ignored -> { }, () -> { });
    }

    /**
     * 创建文本按钮节点。
     *
     * @param rt             场景运行时
     * @param text           按钮文本
     * @param enabledBg      启用背景色
     * @param preferredWidth 固定宽度，0 表示不设置
     * @param enabled        是否启用
     * @return 按钮节点
     */
    private static SceneNode createButton(SceneRuntime rt, String text, int enabledBg, int preferredWidth,
                                          ReadableSignal<Boolean> enabled) {
        SceneNode button = SceneNode.row();
        button.setMainAxisAlign(MainAxisAlign.CENTER);
        button.setCrossAxisAlign(CrossAxisAlign.CENTER);
        button.setPadding(BUTTON_PADDING);
        button.setCornerRadius(RADIUS);
        if (preferredWidth > 0) {
            button.setPreferredWidth(preferredWidth);
        } else {
            button.setWidthSizing(WidthSizing.SHRINK);
        }

        SceneNode label = new SceneNode();
        label.setHitTestable(false);
        label.setText(text);
        button.appendChild(label);

        rt.bind(enabled, value -> button.setBackgroundColor(Boolean.TRUE.equals(value)
                ? enabledBg
                : disabledButtonBackground(enabledBg)));
        rt.bind(enabled, value -> label.setTextColor(Boolean.TRUE.equals(value) ? TEXT_COLOR : TEXT_DISABLED));
        SceneControlChrome.bindCursor(rt, button, enabled, SceneCursor.POINTER, SceneCursor.NOT_ALLOWED);
        return button;
    }

    /**
     * 计算按钮禁用背景色。
     *
     * @param enabledBg 启用背景色
     * @return 禁用背景色
     */
    private static int disabledButtonBackground(int enabledBg) {
        return enabledBg == DELETE_BG ? DELETE_BG_DISABLED : BUTTON_BG_DISABLED;
    }

    /**
     * 替换指定行文本。
     *
     * @param props SimpleList 输入契约
     * @param itemId 目标行 id
     * @param value 新文本
     */
    private static void replaceItem(Props props, long itemId, String value) {
        List<ListItem> current = SceneListOps.safeList(props.items().get());
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
        List<ListItem> current = SceneListOps.safeList(props.items().get());
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
        List<ListItem> immutable = SceneListOps.immutableCopy(next);
        props.items().set(immutable);
        props.onItemsChanged().accept(immutable);
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
     * 读取当前行快照。
     *
     * @param items    行列表
     * @param fallback 兜底行
     * @return 当前行或兜底行
     */
    private static ListItem currentItem(List<ListItem> items, ListItem fallback) {
        return SceneListOps.current(items, fallback, (item, current) -> item.getId() == current.getId());
    }

}
