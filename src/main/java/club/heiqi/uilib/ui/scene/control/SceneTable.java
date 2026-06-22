package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.node.Invalidation;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * SceneTable —— scene 新栈最小版静态表格控件。
 *
 * <p>本期只提供固定列宽、固定行高、纵向滚动视口和静态全量建树。滚动遵循
 * signal-first：输入 handler 只写 {@link Signal}，由 bind 在 flush 阶段把值推给
 * {@link SceneNode#setScrollOffsetY(int)}，避免在 handler 内直接改几何状态。</p>
 */
public final class SceneTable {

    /** 默认列宽（像素），仅在传入列宽非正时兜底 */
    private static final int DEFAULT_COLUMN_WIDTH = 96;
    /** 默认行高（像素） */
    private static final int DEFAULT_ROW_HEIGHT = 28;
    /** 默认视口高（像素） */
    private static final int DEFAULT_VIEWPORT_HEIGHT = 160;
    /** 单元格内边距（像素） */
    private static final int CELL_PADDING = 4;

    /** 表头背景色 */
    private static final int HEADER_BG = 0xFF334155;
    /** 偶数数据行单元格背景色 */
    private static final int ROW_BG_EVEN = 0xFF1E293B;
    /** 奇数数据行单元格背景色 */
    private static final int ROW_BG_ODD = 0xFF243B53;
    /** 外层背景色 */
    private static final int VIEWPORT_BG = 0xFF0F172A;
    /** 单元格文字颜色 */
    private static final int TEXT_COLOR = 0xFFEAF1FF;

    /** 纯静态工厂，禁止实例化 */
    private SceneTable() {
    }

    /** Table 输入契约 —— 固定列、固定行高、静态行数据。 */
    public static final class Props {

        private final List<String> headers;
        private final List<List<String>> rows;
        private final List<Integer> columnWidths;
        private final int rowHeight;
        private final int viewportHeight;

        /**
         * 构造 Table 输入并做防御性复制与基础归一化。
         *
         * @param headers        表头文本，非空
         * @param rows           数据行文本，行不足列补空、超出列截断
         * @param columnWidths   每列固定宽度，数量必须与 headers 相同
         * @param rowHeight      固定行高，非正时使用默认值
         * @param viewportHeight 视口固定高度，非正时使用默认值
         * @throws IllegalArgumentException headers/columnWidths 为空或数量不一致时抛出
         */
        public Props(List<String> headers, List<List<String>> rows,
                     List<Integer> columnWidths, int rowHeight, int viewportHeight) {
            if (headers == null || headers.isEmpty()) {
                throw new IllegalArgumentException("headers must not be empty");
            }
            if (columnWidths == null || columnWidths.isEmpty()) {
                throw new IllegalArgumentException("columnWidths must not be empty");
            }
            if (headers.size() != columnWidths.size()) {
                throw new IllegalArgumentException("headers and columnWidths size must match");
            }

            List<String> safeHeaders = new ArrayList<>(headers.size());
            for (String header : headers) {
                safeHeaders.add(header == null ? "" : header);
            }

            List<Integer> safeWidths = new ArrayList<>(columnWidths.size());
            for (Integer width : columnWidths) {
                int normalized = width == null ? DEFAULT_COLUMN_WIDTH : width.intValue();
                safeWidths.add(Integer.valueOf(normalized <= 0 ? DEFAULT_COLUMN_WIDTH : normalized));
            }

            int columnCount = safeHeaders.size();
            List<List<String>> safeRows = new ArrayList<>();
            if (rows != null) {
                for (List<String> row : rows) {
                    safeRows.add(normalizeRow(row, columnCount));
                }
            }

            this.headers = Collections.unmodifiableList(safeHeaders);
            this.rows = Collections.unmodifiableList(safeRows);
            this.columnWidths = Collections.unmodifiableList(safeWidths);
            this.rowHeight = rowHeight <= 0 ? DEFAULT_ROW_HEIGHT : rowHeight;
            this.viewportHeight = viewportHeight <= 0 ? DEFAULT_VIEWPORT_HEIGHT : viewportHeight;
        }

        /** @return 表头文本列表 */
        public List<String> headers() {
            return headers;
        }

        /** @return 归一化后的数据行 */
        public List<List<String>> rows() {
            return rows;
        }

        /** @return 归一化后的固定列宽 */
        public List<Integer> columnWidths() {
            return columnWidths;
        }

        /** @return 固定行高 */
        public int rowHeight() {
            return rowHeight;
        }

        /** @return 固定视口高度 */
        public int viewportHeight() {
            return viewportHeight;
        }
    }

    /**
     * 工厂：构建 Table 组件函数。
     *
     * @param runtime 场景运行时
     * @param props   Table 输入契约
     * @return 组件函数，交 {@link SceneRuntime#mount} 挂载
     */
    public static Supplier<SceneNode> create(SceneRuntime runtime, Props props) {
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

            content.appendChild(createRow(props.headers(), props.columnWidths(), props.rowHeight(), HEADER_BG));
            for (int i = 0; i < props.rows().size(); i++) {
                int bg = (i % 2 == 0) ? ROW_BG_EVEN : ROW_BG_ODD;
                content.appendChild(createRow(props.rows().get(i), props.columnWidths(), props.rowHeight(), bg));
            }

            Signal<Integer> scrollSignal = Signal.create(Integer.valueOf(0));
            runtime.bind(Invalidation.COMPOSITE, scrollSignal,
                    v -> viewport.setScrollOffsetY(v.intValue()));

            runtime.on(viewport, SceneEventType.SCROLL, (ev, ctx) -> {
                LayoutBox viewportBox = (LayoutBox) viewport.getCachedLayout();
                LayoutBox contentBox = (LayoutBox) content.getCachedLayout();
                if (viewportBox == null || contentBox == null) {
                    return;
                }
                int maxScrollY = Math.max(0, contentBox.getHeight() - viewportBox.getHeight());
                int next = scrollSignal.get().intValue() - ev.getWheelDelta();
                int clamped = Math.max(0, Math.min(maxScrollY, next));
                scrollSignal.set(Integer.valueOf(clamped));
            });
            return root;
        };
    }

    /**
     * 创建一行节点。
     *
     * @param values       每列文本
     * @param columnWidths 每列固定宽度
     * @param rowHeight    固定行高
     * @param background   单元格背景色
     * @return 行节点
     */
    private static SceneNode createRow(List<String> values, List<Integer> columnWidths,
                                       int rowHeight, int background) {
        SceneNode row = new SceneNode();
        row.setFlexDirection(FlexDirection.ROW);
        row.setPreferredHeight(rowHeight);
        for (int i = 0; i < columnWidths.size(); i++) {
            row.appendChild(createCell(values.get(i), columnWidths.get(i).intValue(), rowHeight, background));
        }
        return row;
    }

    /**
     * 创建单元格节点，文本由 label 子节点承载。
     *
     * @param text       单元格文本
     * @param width      固定列宽
     * @param rowHeight  固定行高
     * @param background 背景色
     * @return 单元格节点
     */
    private static SceneNode createCell(String text, int width, int rowHeight, int background) {
        SceneNode cell = new SceneNode();
        cell.setFlexDirection(FlexDirection.ROW);
        cell.setPreferredWidth(width);
        cell.setPreferredHeight(rowHeight);
        cell.setPadding(CELL_PADDING);
        cell.setClipChildren(true);
        cell.setBackgroundColor(background);

        SceneNode label = new SceneNode();
        label.setText(text);
        label.setTextColor(TEXT_COLOR);
        label.setHitTestable(false);
        cell.appendChild(label);
        return cell;
    }

    /**
     * 将输入行归一化到固定列数。
     *
     * @param row         输入行，允许 null
     * @param columnCount 目标列数
     * @return 不可变归一化行
     */
    private static List<String> normalizeRow(List<String> row, int columnCount) {
        List<String> normalized = new ArrayList<>(columnCount);
        for (int i = 0; i < columnCount; i++) {
            String value = row != null && i < row.size() ? row.get(i) : "";
            normalized.add(value == null ? "" : value);
        }
        return Collections.unmodifiableList(normalized);
    }
}
