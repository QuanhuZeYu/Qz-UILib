package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.ScenePalette;

/**
 * SceneDataTable —— scene 新栈响应式数据表格控件。
 *
 * <p>本阶段只提供 keyed 行复用、固定列宽、固定行高、纵向滚动视口和只读文本列。
 * 后续编辑器列复用相同 {@link CellRenderer} 常驻渲染扩展点接入。</p>
 */
public final class SceneDataTable {

    /** 默认列宽（像素）。 */
    private static final int DEFAULT_COLUMN_WIDTH = 96;
    /** 默认行高（像素），取自 chrome token。 */
    private static final int DEFAULT_ROW_HEIGHT = SceneChromeTokens.ROW_HEIGHT_TABLE;
    /** 默认视口高（像素）。 */
    private static final int DEFAULT_VIEWPORT_HEIGHT = 160;
    /** 单元格内边距（像素）。 */
    private static final int CELL_PADDING = 4;
    /** 行 id 分配器，用于 keyed 列表稳定身份。 */
    private static final AtomicLong NEXT_ROW_ID = new AtomicLong(1L);
    /** 表头背景色。 */
    private static final int HEADER_BG = SceneChromeTokens.BG_DEFAULT;
    /** 外层背景色（无 chrome token 对应，暂保留：比 BG_PRESSED 更深的 Slate-900）。 */
    private static final int VIEWPORT_BG = 0xFF0F172A;
    /** 单元格文字颜色（无 chrome token 对应，暂保留：嵌入式深色槽专用文本色，比 TEXT_PRIMARY 更亮，不强行统一）。 */
    private static final int TEXT_COLOR = 0xFFEAF1FF;

    /** 纯静态工厂，禁止实例化。 */
    private SceneDataTable() {
    }

    /** DataTable 输入契约 —— 受控行数据、列定义和固定布局参数。 */
    public static final class Props {

        /** 受控行数据源。 */
        private final Signal<List<Row>> rows;
        /** 列定义列表。 */
        private final List<Column> columns;
        /** 固定行高。 */
        private final int rowHeight;
        /** 视口固定高度。 */
        private final int viewportHeight;
        /** 控件级启用信号，控制所有编辑列的 enabled；默认恒为 true。 */
        private final ReadableSignal<Boolean> enabled;
        /** 控件级只读信号，仅作用于 TextInput 列；默认恒为 false。 */
        private final ReadableSignal<Boolean> readOnly;
        /**
         * 是否在视口右侧叠加 {@link SceneScrollbar}。false 表示不建滚动条（向后兼容）；
         * true 时控件在视口右侧叠加滚动条，滑块几何由 runtime layoutDoneSignal 驱动重算。
         */
        private final boolean showScrollbar;

        /**
         * 构造 DataTable 输入并做基础归一化。
         *
         * <p>enabled/readOnly 缺省时控件全启用、可编辑，保持原有行为。</p>
         *
         * @param rows           受控行数据源
         * @param columns        列定义列表
         * @param rowHeight      固定行高，非正时使用默认值
         * @param viewportHeight 视口固定高度，非正时使用默认值
         */
        public Props(Signal<List<Row>> rows, List<Column> columns, int rowHeight, int viewportHeight) {
             this(rows, columns, rowHeight, viewportHeight, null, null, false);
        }

        /**
         * 构造 DataTable 输入并注入控件级 enabled/readOnly 信号。
         *
         * @param rows           受控行数据源
         * @param columns        列定义列表
         * @param rowHeight      固定行高，非正时使用默认值
         * @param viewportHeight 视口固定高度，非正时使用默认值
         * @param enabled        控件级启用信号，null 时默认恒为 true
         * @param readOnly       控件级只读信号，null 时默认恒为 false
         */
        public Props(Signal<List<Row>> rows, List<Column> columns, int rowHeight, int viewportHeight,
                     ReadableSignal<Boolean> enabled, ReadableSignal<Boolean> readOnly) {
             this(rows, columns, rowHeight, viewportHeight, enabled, readOnly, false);
        }

        /**
         * 构造 DataTable 输入并注入控件级 enabled/readOnly 信号与可选滚动条内容信号。
         *
         * @param rows                   受控行数据源
         * @param columns                列定义列表
         * @param rowHeight              固定行高，非正时使用默认值
         * @param viewportHeight         视口固定高度，非正时使用默认值
         * @param enabled                控件级启用信号，null 时默认恒为 true
         * @param readOnly               控件级只读信号，null 时默认恒为 false
         * @param showScrollbar          是否建滚动条，false 表示不建
         */
        public Props(Signal<List<Row>> rows, List<Column> columns, int rowHeight, int viewportHeight,
                     ReadableSignal<Boolean> enabled, ReadableSignal<Boolean> readOnly,
                     boolean showScrollbar) {
            if (rows == null) {
                throw new IllegalArgumentException("rows must not be null");
            }
            if (columns == null || columns.isEmpty()) {
                throw new IllegalArgumentException("columns must not be empty");
            }
            this.rows = rows;
            this.columns = Collections.unmodifiableList(new ArrayList<>(columns));
            this.rowHeight = rowHeight <= 0 ? DEFAULT_ROW_HEIGHT : rowHeight;
            this.viewportHeight = viewportHeight <= 0 ? DEFAULT_VIEWPORT_HEIGHT : viewportHeight;
            this.enabled = enabled == null ? Signal.create(Boolean.TRUE) : enabled;
            this.readOnly = readOnly == null ? Signal.create(Boolean.FALSE) : readOnly;
            this.showScrollbar = showScrollbar;
        }

        /**
         * 获取受控行数据源。
         *
         * @return 受控行数据源
         */
        public Signal<List<Row>> rows() {
            return rows;
        }

        /**
         * 获取列定义列表。
         *
         * @return 不可变列定义列表
         */
        public List<Column> columns() {
            return columns;
        }

        /**
         * 获取固定行高。
         *
         * @return 固定行高
         */
        public int rowHeight() {
            return rowHeight;
        }

        /**
         * 获取视口固定高度。
         *
         * @return 视口固定高度
         */
        public int viewportHeight() {
            return viewportHeight;
        }

        /**
         * 获取控件级启用信号。
         *
         * @return 启用信号，缺省时恒为 true
         */
        public ReadableSignal<Boolean> enabled() {
            return enabled;
        }

        /**
         * 获取控件级只读信号（仅作用于 TextInput 列）。
         *
         * @return 只读信号，缺省时恒为 false
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
         * 创建 Props builder。
         *
         * <p>必填 rows；columns 虽以可选 setter 形式暴露，但 {@link #build()} 走紧凑构造器校验，
         * 未设置或设为空时构造器抛 {@link IllegalArgumentException}，实际为必填。</p>
         *
         * @param rows 受控行数据源
         * @return builder 实例
         */
        public static Builder builder(Signal<List<Row>> rows) {
            return new Builder(rows);
        }

        /** Props 构建器。 */
        public static final class Builder {
            /** 受控行数据源。 */
            private final Signal<List<Row>> rows;
            /** 列定义列表，默认空列表（build 时由紧凑构造器校验拒绝，需显式设置）。 */
            private List<Column> columns = Collections.emptyList();
            /** 固定行高，0 表示走构造器默认值归一化。 */
            private int rowHeight;
            /** 视口固定高度，0 表示走构造器默认值归一化。 */
            private int viewportHeight;
            /** 控件级启用信号，null 时构造器归一化为恒 true。 */
            private ReadableSignal<Boolean> enabled;
            /** 控件级只读信号，null 时构造器归一化为恒 false。 */
            private ReadableSignal<Boolean> readOnly;
            /** 是否建滚动条，false 表示不建。 */
            private boolean showScrollbar;

            /**
             * 创建构建器。
             *
             * @param rows 受控行数据源
             */
            private Builder(Signal<List<Row>> rows) {
                this.rows = rows;
            }

            /**
             * 设置列定义列表。
             *
             * @param columns 列定义列表，不可为 null 或空
             * @return 当前 builder
             */
            public Builder columns(List<Column> columns) {
                this.columns = columns;
                return this;
            }

            /**
             * 设置固定行高。
             *
             * @param rowHeight 固定行高，非正时使用默认值
             * @return 当前 builder
             */
            public Builder rowHeight(int rowHeight) {
                this.rowHeight = rowHeight;
                return this;
            }

            /**
             * 设置视口固定高度。
             *
             * @param viewportHeight 视口固定高度，非正时使用默认值
             * @return 当前 builder
             */
            public Builder viewportHeight(int viewportHeight) {
                this.viewportHeight = viewportHeight;
                return this;
            }

            /**
             * 设置控件级启用信号。
             *
             * @param enabled 启用信号，null 时默认恒为 true
             * @return 当前 builder
             */
            public Builder enabled(ReadableSignal<Boolean> enabled) {
                this.enabled = enabled;
                return this;
            }

            /**
             * 设置控件级只读信号。
             *
             * @param readOnly 只读信号，null 时默认恒为 false
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
             * 构建 Props。
             *
             * @return Props 实例
             */
            public Props build() {
                return new Props(rows, columns, rowHeight, viewportHeight, enabled, readOnly, showScrollbar);
            }
        }
    }

    /** 单行数据模型，携带稳定 rowId 供 keyed 列表复用。 */
    public static final class Row {

        /** 稳定行 id。 */
        private final long rowId;
        /** 每列文本值。 */
        private final List<String> cells;

        /**
         * 创建一行数据。
         *
         * @param cells 每列文本值，允许 null 元素
         */
        public Row(List<String> cells) {
            this(NEXT_ROW_ID.getAndIncrement(), cells);
        }

        /**
         * 创建带稳定 id 的一行数据。
         *
         * @param rowId 稳定行 id
         * @param cells 每列文本值，允许 null 元素
         */
        private Row(long rowId, List<String> cells) {
            this.rowId = rowId;
            this.cells = Collections.unmodifiableList(normalizeCells(cells));
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
         * 获取每列文本值。
         *
         * @return 不可变文本值列表
         */
        public List<String> cells() {
            return cells;
        }

        /**
         * 复制为同 id 的新行并替换指定列值。
         *
         * @param col   列下标
         * @param value 新文本值
         * @return 同 rowId 的新行
         */
        public Row withCell(int col, String value) {
            if (col < 0 || col >= cells.size()) {
                throw new IndexOutOfBoundsException("col out of bounds: " + col);
            }
            List<String> next = new ArrayList<>(cells);
            next.set(col, nullSafe(value));
            return new Row(rowId, next);
        }

        /**
         * 获取安全单元格值。
         *
         * @param col 列下标
         * @return 单元格文本，越界时返回空串
         */
        private String cellValue(int col) {
            if (col < 0 || col >= cells.size()) {
                return "";
            }
            return cells.get(col);
        }
    }

    /** 列定义，包含表头、固定宽度、可编辑标记和单元格渲染器。 */
    public static final class Column {

        /** 表头文本。 */
        private final String header;
        /** 固定列宽。 */
        private final int width;
        /** 是否可编辑。 */
        private final boolean editable;
        /** 单元格渲染器。 */
        private final CellRenderer renderer;

        /**
         * 创建列定义。
         *
         * @param header   表头文本
         * @param width    固定列宽，非正时使用默认值
         * @param editable 是否可编辑
         * @param renderer 单元格渲染器
         */
        public Column(String header, int width, boolean editable, CellRenderer renderer) {
            if (renderer == null) {
                throw new IllegalArgumentException("renderer must not be null");
            }
            this.header = nullSafe(header);
            this.width = width <= 0 ? DEFAULT_COLUMN_WIDTH : width;
            this.editable = editable;
            this.renderer = renderer;
        }

        /**
         * 创建只读文本列。
         *
         * @param header 表头文本
         * @param width  固定列宽
         * @return 只读文本列定义
         */
        public static Column text(String header, int width) {
            return new Column(header, width, false, (rt, ctx) -> {
                SceneNode label = new SceneNode();
                label.setTextColor(TEXT_COLOR);
                label.setPreferredHeight(ctx.contentHeight());
                label.setHitTestable(false);
                rt.bindText(label, ctx.value());
                return label;
            });
        }

        /**
         * 创建 TextInput 可编辑文本列。
         *
         * @param header 表头文本
         * @param width  固定列宽
         * @return 可编辑文本输入列定义
         */
        public static Column textInput(String header, int width) {
            return new Column(header, width, true, (rt, ctx) -> {
                SceneTextInputPrimitive.Result result = SceneTextInputPrimitive.create(rt, new SceneTextInputPrimitive.Props(
                        ctx.value(),
                        ctx.enabled(),
                        ctx.readOnly(),
                        "",
                        Integer.MAX_VALUE,
                        SceneInputType.TEXT,
                        ctx.onChange()));
                SceneDataTableEditorChrome.decorateTextInputEditor(rt, result, ctx.contentHeight(), ctx.enabled());
                return result.root();
            });
        }

        /**
         * 创建 Select 可编辑选择列。
         *
         * @param header  表头文本
         * @param width   固定列宽
         * @param options 选项文本列表
         * @return 可编辑选择列定义
         */
        public static Column select(String header, int width, List<String> options) {
            List<String> safeOptions = Collections.unmodifiableList(new ArrayList<>(options == null ? Collections.<String>emptyList() : options));
            return new Column(header, width, true, (rt, ctx) -> {
                SceneSelectPrimitive.Result result = SceneSelectPrimitive.create(rt, new SceneSelectPrimitive.Props(
                        Computed.create(() -> Integer.valueOf(safeOptions.indexOf(ctx.value().get()))),
                        safeOptions,
                        ctx.enabled(),
                        next -> ctx.onChange().accept(optionValue(safeOptions, next)),
                        SceneDataTableEditorChrome.createListboxChrome(rt)));
                SceneDataTableEditorChrome.decorateSelectEditor(rt, result, ctx.contentHeight(), ctx.enabled());
                return result.trigger();
            });
        }

        /**
         * 获取表头文本。
         *
         * @return 表头文本
         */
        public String header() {
            return header;
        }

        /**
         * 获取固定列宽。
         *
         * @return 固定列宽
         */
        public int width() {
            return width;
        }

        /**
         * 判断列是否可编辑。
         *
         * @return 可编辑返回 true
         */
        public boolean editable() {
            return editable;
        }

        /**
         * 获取单元格渲染器。
         *
         * @return 单元格渲染器
         */
        public CellRenderer renderer() {
            return renderer;
        }
    }

    /** 函数式单元格渲染器。 */
    @FunctionalInterface
    public interface CellRenderer {

        /**
         * 渲染单元格内容节点。
         *
         * @param rt  场景运行时
         * @param ctx 单元格上下文
         * @return 单元格内容节点
         */
        SceneNode render(SceneRuntime rt, CellContext ctx);
    }

    /** 单元格渲染上下文。 */
    public static final class CellContext {

        /** 当前单元格值。 */
        private final ReadableSignal<String> value;
        /** 提交回调。 */
        private final Consumer<String> onChange;
        /** 是否可编辑。 */
        private final boolean editable;
        /** 单元格内容可用高度。 */
        private final int contentHeight;
        /** 单元格启用信号（来自控件级 enabled），控制编辑器 enabled 态。 */
        private final ReadableSignal<Boolean> enabled;
        /** 单元格只读信号（来自控件级 readOnly，仅 TextInput 列使用）。 */
        private final ReadableSignal<Boolean> readOnly;

        /**
         * 创建单元格上下文（兼容旧签名，enabled 默认 true、readOnly 默认 false）。
         *
         * @param value         当前单元格值
         * @param onChange      提交回调
         * @param editable      是否可编辑
         * @param contentHeight 单元格内容可用高度
         */
        public CellContext(ReadableSignal<String> value, Consumer<String> onChange, boolean editable, int contentHeight) {
            this(value, onChange, editable, contentHeight, null, null);
        }

        /**
         * 创建单元格上下文并注入控件级 enabled/readOnly 信号。
         *
         * @param value         当前单元格值
         * @param onChange      提交回调
         * @param editable      是否可编辑
         * @param contentHeight 单元格内容可用高度
         * @param enabled       单元格启用信号，null 时默认恒为 true
         * @param readOnly      单元格只读信号，null 时默认恒为 false
         */
        public CellContext(ReadableSignal<String> value, Consumer<String> onChange, boolean editable,
                           int contentHeight, ReadableSignal<Boolean> enabled, ReadableSignal<Boolean> readOnly) {
            if (value == null || onChange == null) {
                throw new IllegalArgumentException("value/onChange must not be null");
            }
            this.value = value;
            this.onChange = onChange;
            this.editable = editable;
            this.contentHeight = Math.max(0, contentHeight);
            this.enabled = enabled == null ? Signal.create(Boolean.TRUE) : enabled;
            this.readOnly = readOnly == null ? Signal.create(Boolean.FALSE) : readOnly;
        }

        /**
         * 获取当前单元格值。
         *
         * @return 当前单元格值信号
         */
        public ReadableSignal<String> value() {
            return value;
        }

        /**
         * 获取提交回调。
         *
         * @return 提交回调
         */
        public Consumer<String> onChange() {
            return onChange;
        }

        /**
         * 判断单元格是否可编辑。
         *
         * @return 可编辑返回 true
         */
        public boolean editable() {
            return editable;
        }

        /**
         * 获取单元格内容可用高度。
         *
         * @return 内容可用高度
         */
        public int contentHeight() {
            return contentHeight;
        }

        /**
         * 获取单元格启用信号。
         *
         * @return 启用信号，缺省时恒为 true
         */
        public ReadableSignal<Boolean> enabled() {
            return enabled;
        }

        /**
         * 获取单元格只读信号（仅 TextInput 列使用）。
         *
         * @return 只读信号，缺省时恒为 false
         */
        public ReadableSignal<Boolean> readOnly() {
            return readOnly;
        }
    }

    /**
     * 工厂：构建 DataTable 组件函数。
     *
     * @param rt 场景运行时
     * @param props   DataTable 输入契约
     * @return 组件函数，交 {@link SceneRuntime#mount(SceneNode, Supplier)} 挂载
     */
    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        if (rt == null || props == null) {
            throw new IllegalArgumentException("rt/props must not be null");
        }
        return () -> {
            SceneNode root = SceneNode.column();

            SceneNode viewport = new SceneNode();
            viewport.setScrollable(true);
            viewport.setClipChildren(true);
            viewport.setBackgroundColor(VIEWPORT_BG);
            viewport.setFillParentHeight(true);
            viewport.setFlexGrow(1);

            // stackHost 承载 viewport 原 preferredHeight(props.viewportHeight())，并可选挂滚动条 column。
            // 即使无滚动条也建 stackHost，统一结构路径。content 两层（header+dataContainer）保持在 viewport 内。
            SceneNode stackHost = SceneNode.row();
            stackHost.setPreferredHeight(props.viewportHeight());
            stackHost.appendChild(viewport);

            Signal<Integer> scrollSignal = SceneScrolls.attach(rt, viewport);

            // 可选滚动条：showScrollbar 为 true 时建 bar，挂到 stackHost 右侧
            if (props.showScrollbar()) {
                SceneScrollbar.Result sbResult = SceneScrollbar.createDefault(rt, viewport, scrollSignal);
                stackHost.appendChild(sbResult.column());
            }

            root.appendChild(stackHost);

            SceneNode content = SceneNode.column();
            viewport.appendChild(content);

            content.appendChild(buildHeaderRow(props));
            SceneNode dataContainer = SceneNode.column();
            content.appendChild(dataContainer);
            // 行号/行对象索引缓存：随 rows signal 替换的列表实例失效重建，
            // 把单元格 Computed 内的行查找从 O(n) 线性扫描降到 O(1) 查表（大表防 O(n²)）。
            RowIndexCache indexCache = new RowIndexCache();
            rt.forEach(dataContainer, props.rows(), Row::getRowId, row -> buildRow(rt, props, row, indexCache));
            return root;
        };
    }

    /**
     * 构建表头行。
     *
     * @param props DataTable 输入契约
     * @return 表头行节点
     */
    private static SceneNode buildHeaderRow(Props props) {
        SceneNode row = SceneNode.row();
        row.setPreferredHeight(props.rowHeight());
        for (Column column : props.columns()) {
            row.appendChild(buildHeaderCell(column, props.rowHeight()));
        }
        return row;
    }

    /**
     * 构建表头单元格。
     *
     * @param column    列定义
     * @param rowHeight 固定行高
     * @return 表头单元格节点
     */
    private static SceneNode buildHeaderCell(Column column, int rowHeight) {
        SceneNode cell = SceneNode.row();
        cell.setPreferredWidth(column.width());
        cell.setPreferredHeight(rowHeight);
        cell.setPadding(CELL_PADDING);
        cell.setClipChildren(true);
        cell.setBackgroundColor(HEADER_BG);

        SceneNode label = new SceneNode();
        label.setText(column.header());
        label.setTextColor(TEXT_COLOR);
        label.setHitTestable(false);
        cell.appendChild(label);
        return cell;
    }

    /**
     * 构建数据行。
     *
     * @param rt         场景运行时
     * @param props      DataTable 输入契约
     * @param row        当前行快照
     * @param indexCache 行号/行对象索引缓存（随 rows 列表实例失效重建）
     * @return 数据行节点
     */
    private static SceneNode buildRow(SceneRuntime rt, Props props, Row row, RowIndexCache indexCache) {
        SceneNode rowNode = SceneNode.row();
        rowNode.setPreferredHeight(props.rowHeight());
        int rowIndex = indexCache.rowIndex(props.rows().get(), row.getRowId());
        for (int col = 0; col < props.columns().size(); col++) {
            rowNode.appendChild(buildCell(rt, props, row, col, rowIndex, indexCache));
        }
        return rowNode;
    }

    /**
     * 构建数据单元格。
     *
     * @param rt         场景运行时
     * @param props      DataTable 输入契约
     * @param row        当前行快照
     * @param col        列下标
     * @param rowIndex   初始行下标
     * @param indexCache 行号/行对象索引缓存（随 rows 列表实例失效重建）
     * @return 数据单元格节点
     */
    private static SceneNode buildCell(SceneRuntime rt, Props props, Row row, int col, int rowIndex,
                                       RowIndexCache indexCache) {
        Column column = props.columns().get(col);
        SceneNode cell = SceneNode.row();
        cell.setCrossAxisAlign(CrossAxisAlign.CENTER);
        cell.setPreferredWidth(column.width());
        cell.setPreferredHeight(props.rowHeight());
        cell.setPadding(CELL_PADDING);
        cell.setClipChildren(true);
        cell.setBackgroundColor(ScenePalette.rowBg(rowIndex));

        ReadableSignal<String> value = Computed.create(() -> indexCache.currentRow(props.rows().get(), row).cellValue(col));
        CellContext ctx = new CellContext(value, next -> {
            Row updated = indexCache.currentRow(props.rows().get(), row).withCell(col, next);
            List<Row> newRows = updateRowInList(props.rows().get(), row.getRowId(), updated);
            props.rows().set(newRows);
        }, column.editable(), props.rowHeight() - 2 * CELL_PADDING, props.enabled(), props.readOnly());
        SceneNode child = column.renderer().render(rt, ctx);
        if (child != null) {
            cell.appendChild(child);
        }
        return cell;
    }

    /**
     * 行号/行对象索引缓存。
     *
     * <p>把按 rowId 的行查找从 O(n) 线性扫描降为 O(1) 查表。缓存以当前 rows 列表实例
     * （{@code props.rows().get()} 返回值）为键：列表实例不变时复用索引，列表实例替换
     * （{@code props.rows().set(...)} 触发）时整表重建一次索引。单次 rows 变更的查找总成本
     * 由 O(n²·m)（n 行 × m 列 × O(n) 扫描）降到 O(n) + O(n·m)。</p>
     *
     * <p>缓存生命周期绑定到 {@link #create(SceneRuntime, Props)} 调用闭包，每个 DataTable
     * 实例独占一份，不作为静态全局状态；列表实例被 signal 释放后无残留引用。</p>
     */
    private static final class RowIndexCache {

        /** 上次建索引的列表实例，用身份比较判断是否需要重建。 */
        private List<Row> indexedRows;
        /** rowId → 行下标索引。 */
        private Map<Long, Integer> rowIndexById;
        /** rowId → 行对象索引。 */
        private Map<Long, Row> rowById;

        /**
         * 获取行初始下标。
         *
         * @param rows  当前行列表
         * @param rowId 稳定行 id
         * @return 初始下标，找不到时返回 0（与原线性扫描行为一致）
         */
        int rowIndex(List<Row> rows, long rowId) {
            if (rows == null) {
                return 0;
            }
            ensureIndex(rows);
            Integer idx = rowIndexById.get(rowId);
            return idx == null ? 0 : idx;
        }

        /**
         * 从当前列表中按 rowId 查找最新行。
         *
         * @param rows 当前行列表
         * @param row  当前行快照（用于读取 rowId 与回退）
         * @return 最新行，找不到时返回当前快照（与原线性扫描行为一致）
         */
        Row currentRow(List<Row> rows, Row row) {
            if (rows == null) {
                return row;
            }
            ensureIndex(rows);
            Row found = rowById.get(row.getRowId());
            return found == null ? row : found;
        }

        /**
         * 按列表实例身份判断是否需要重建索引；需要时整表扫一次建好两张 Map。
         *
         * @param rows 当前行列表
         */
        private void ensureIndex(List<Row> rows) {
            if (rows == indexedRows) {
                return;
            }
            Map<Long, Integer> idx = new HashMap<>(rows.size() * 2 + 1);
            Map<Long, Row> rm = new HashMap<>(rows.size() * 2 + 1);
            for (int i = 0; i < rows.size(); i++) {
                Row r = rows.get(i);
                if (r != null) {
                    // 重复 rowId 保留首次出现位置，与原线性扫描「从头匹配」语义一致。
                    idx.putIfAbsent(r.getRowId(), i);
                    rm.putIfAbsent(r.getRowId(), r);
                }
            }
            this.indexedRows = rows;
            this.rowIndexById = idx;
            this.rowById = rm;
        }
    }

    /**
     * 替换列表中指定 rowId 的行。
     *
     * @param rows    当前行列表
     * @param rowId   稳定行 id
     * @param updated 更新后的行
     * @return 替换后的不可变列表，找不到时返回当前列表副本
     */
    private static List<Row> updateRowInList(List<Row> rows, long rowId, Row updated) {
        List<Row> current = rows == null ? Collections.<Row>emptyList() : rows;
        List<Row> next = new ArrayList<>(current.size());
        for (Row row : current) {
            next.add(row != null && row.getRowId() == rowId ? updated : row);
        }
        return Collections.unmodifiableList(next);
    }

    /**
     * 归一化单元格列表。
     *
     * @param cells 输入单元格列表
     * @return 可变归一化副本
     */
    private static List<String> normalizeCells(List<String> cells) {
        List<String> normalized = new ArrayList<>();
        if (cells != null) {
            for (String cell : cells) {
                normalized.add(nullSafe(cell));
            }
        }
        return normalized;
    }

    /**
     * 按选项下标读取文本。
     *
     * @param options 选项列表
     * @param index   选项下标
     * @return 合法选项文本，越界时为空串
     */
    private static String optionValue(List<String> options, Integer index) {
        if (options == null || index == null) {
            return "";
        }
        int i = index.intValue();
        if (i < 0 || i >= options.size()) {
            return "";
        }
        return nullSafe(options.get(i));
    }

    /**
     * 将 null 文本归一为空串。
     *
     * @param value 输入文本
     * @return 非 null 文本
     */
    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
