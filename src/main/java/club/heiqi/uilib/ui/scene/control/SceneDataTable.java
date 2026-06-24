package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.component.SceneScrolls;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.node.SceneNode;
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
    /** 默认行高（像素）。 */
    private static final int DEFAULT_ROW_HEIGHT = 28;
    /** 默认视口高（像素）。 */
    private static final int DEFAULT_VIEWPORT_HEIGHT = 160;
    /** 单元格内边距（像素）。 */
    private static final int CELL_PADDING = 4;
    /** 行 id 分配器，用于 keyed 列表稳定身份。 */
    private static final AtomicLong NEXT_ROW_ID = new AtomicLong(1L);
    /** 表头背景色。 */
    private static final int HEADER_BG = 0xFF334155;
    /** 外层背景色。 */
    private static final int VIEWPORT_BG = 0xFF0F172A;
    /** 单元格文字颜色。 */
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

        /**
         * 构造 DataTable 输入并做基础归一化。
         *
         * @param rows           受控行数据源
         * @param columns        列定义列表
         * @param rowHeight      固定行高，非正时使用默认值
         * @param viewportHeight 视口固定高度，非正时使用默认值
         */
        public Props(Signal<List<Row>> rows, List<Column> columns, int rowHeight, int viewportHeight) {
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
                SceneNode input = SceneTextInput.create(rt, new SceneTextInput.Props(
                    ctx.value(),
                    Signal.create(Boolean.TRUE),
                    Signal.create(Boolean.FALSE),
                    "",
                    Integer.MAX_VALUE,
                    SceneInputType.TEXT,
                    ctx.onChange())).get();
                input.setPreferredHeight(ctx.contentHeight());
                return input;
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
                SceneNode select = SceneSelect.create(rt, new SceneSelect.Props(
                    Computed.create(() -> Integer.valueOf(safeOptions.indexOf(ctx.value().get()))),
                    safeOptions,
                    Signal.create(Boolean.TRUE),
                    next -> ctx.onChange().accept(optionValue(safeOptions, next)))).get();
                select.setPreferredHeight(ctx.contentHeight());
                return select;
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

        /**
         * 创建单元格上下文。
         *
         * @param value         当前单元格值
         * @param onChange      提交回调
         * @param editable      是否可编辑
         * @param contentHeight 单元格内容可用高度
         */
        public CellContext(ReadableSignal<String> value, Consumer<String> onChange, boolean editable, int contentHeight) {
            if (value == null || onChange == null) {
                throw new IllegalArgumentException("value/onChange must not be null");
            }
            this.value = value;
            this.onChange = onChange;
            this.editable = editable;
            this.contentHeight = Math.max(0, contentHeight);
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
    }

    /**
     * 工厂：构建 DataTable 组件函数。
     *
     * @param runtime 场景运行时
     * @param props   DataTable 输入契约
     * @return 组件函数，交 {@link SceneRuntime#mount(SceneNode, Supplier)} 挂载
     */
    public static Supplier<SceneNode> create(SceneRuntime runtime, Props props) {
        if (runtime == null || props == null) {
            throw new IllegalArgumentException("runtime/props must not be null");
        }
        return () -> {
            SceneNode root = new SceneNode();
            root.setFlexDirection(FlexDirection.COLUMN);

            SceneNode viewport = new SceneNode();
            viewport.setScrollable(true);
            viewport.setClipChildren(true);
            viewport.setPreferredHeight(props.viewportHeight());
            viewport.setBackgroundColor(VIEWPORT_BG);
            root.appendChild(viewport);

            SceneNode content = new SceneNode();
            content.setFlexDirection(FlexDirection.COLUMN);
            viewport.appendChild(content);

            content.appendChild(buildHeaderRow(props));
            SceneNode dataContainer = new SceneNode();
            dataContainer.setFlexDirection(FlexDirection.COLUMN);
            content.appendChild(dataContainer);
            runtime.forEach(dataContainer, props.rows(), Row::getRowId, row -> buildRow(runtime, props, row));
            SceneScrolls.attach(runtime, viewport);
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
        SceneNode row = new SceneNode();
        row.setFlexDirection(FlexDirection.ROW);
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
        SceneNode cell = new SceneNode();
        cell.setFlexDirection(FlexDirection.ROW);
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
     * @param rt    场景运行时
     * @param props DataTable 输入契约
     * @param row   当前行快照
     * @return 数据行节点
     */
    private static SceneNode buildRow(SceneRuntime rt, Props props, Row row) {
        SceneNode rowNode = new SceneNode();
        rowNode.setFlexDirection(FlexDirection.ROW);
        rowNode.setPreferredHeight(props.rowHeight());
        int rowIndex = rowIndex(props.rows().get(), row.getRowId());
        for (int col = 0; col < props.columns().size(); col++) {
            rowNode.appendChild(buildCell(rt, props, row, col, rowIndex));
        }
        return rowNode;
    }

    /**
     * 构建数据单元格。
     *
     * @param rt       场景运行时
     * @param props    DataTable 输入契约
     * @param row      当前行快照
     * @param col      列下标
     * @param rowIndex 初始行下标
     * @return 数据单元格节点
     */
    private static SceneNode buildCell(SceneRuntime rt, Props props, Row row, int col, int rowIndex) {
        Column column = props.columns().get(col);
        SceneNode cell = new SceneNode();
        cell.setFlexDirection(FlexDirection.ROW);
        cell.setCrossAxisAlign(CrossAxisAlign.CENTER);
        cell.setPreferredWidth(column.width());
        cell.setPreferredHeight(props.rowHeight());
        cell.setPadding(CELL_PADDING);
        cell.setClipChildren(true);
        cell.setBackgroundColor(ScenePalette.rowBg(rowIndex));

        ReadableSignal<String> value = Computed.create(() -> currentRow(props.rows().get(), row).cellValue(col));
        CellContext ctx = new CellContext(value, next -> {
            Row updated = currentRow(props.rows().get(), row).withCell(col, next);
            List<Row> newRows = updateRowInList(props.rows().get(), row.getRowId(), updated);
            props.rows().set(newRows);
        }, column.editable(), props.rowHeight() - 2 * CELL_PADDING);
        SceneNode child = column.renderer().render(rt, ctx);
        if (child != null) {
            cell.appendChild(child);
        }
        return cell;
    }

    /**
     * 从当前列表中按 rowId 查找最新行。
     *
     * @param rows 当前行列表
     * @param row  当前行快照
     * @return 最新行，找不到时返回当前快照
     */
    private static Row currentRow(List<Row> rows, Row row) {
        if (rows != null) {
            for (Row candidate : rows) {
                if (candidate != null && candidate.getRowId() == row.getRowId()) {
                    return candidate;
                }
            }
        }
        return row;
    }

    /**
     * 获取行初始下标。
     *
     * @param rows  当前行列表
     * @param rowId 稳定行 id
     * @return 初始下标，找不到时返回 0
     */
    private static int rowIndex(List<Row> rows, long rowId) {
        if (rows != null) {
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                if (row != null && row.getRowId() == rowId) {
                    return i;
                }
            }
        }
        return 0;
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
