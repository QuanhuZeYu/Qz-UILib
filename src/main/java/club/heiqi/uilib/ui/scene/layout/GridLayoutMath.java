package club.heiqi.uilib.ui.scene.layout;

/**
 * 固定列 Grid 的无状态整数数学。
 *
 * <p>轨道先扣除列间 gap，再等分可用宽；余数从左到右每列补 1px。</p>
 */
final class GridLayoutMath {

    /** 工具类不可实例化。 */
    private GridLayoutMath() {
    }

    /** 计算扣除全部列间距后的可排布宽度。 */
    static int usableWidth(int innerWidth, int gap, int columns) {
        requireColumns(columns);
        return Math.max(0, innerWidth - gap * (columns - 1));
    }

    /** 计算指定列的轨道宽度。 */
    static int trackWidth(int innerWidth, int gap, int columns, int column) {
        requireColumn(columns, column);
        int usable = usableWidth(innerWidth, gap, columns);
        int base = usable / columns;
        int remainder = usable % columns;
        return base + (column < remainder ? 1 : 0);
    }

    /** 计算指定列相对 Grid 内区左边缘的起点。 */
    static int trackStart(int innerWidth, int gap, int columns, int column) {
        requireColumn(columns, column);
        int usable = usableWidth(innerWidth, gap, columns);
        int base = usable / columns;
        int remainder = usable % columns;
        return column * (base + gap) + Math.min(column, remainder);
    }

    /** 计算 child index 所在行。 */
    static int rowOf(int childIndex, int columns) {
        requireChildIndex(childIndex);
        requireColumns(columns);
        return childIndex / columns;
    }

    /** 计算 child index 所在列。 */
    static int columnOf(int childIndex, int columns) {
        requireChildIndex(childIndex);
        requireColumns(columns);
        return childIndex % columns;
    }

    /** 计算 childCount 实际占用的行数。 */
    static int rowCount(int childCount, int columns) {
        if (childCount < 0) {
            throw new IllegalArgumentException("childCount 不可为负数");
        }
        requireColumns(columns);
        return childCount == 0 ? 0 : (childCount - 1) / columns + 1;
    }

    private static void requireColumns(int columns) {
        if (columns < 1) {
            throw new IllegalArgumentException("columns 必须至少为 1");
        }
    }

    private static void requireColumn(int columns, int column) {
        requireColumns(columns);
        if (column < 0 || column >= columns) {
            throw new IllegalArgumentException("column 超出固定列范围");
        }
    }

    private static void requireChildIndex(int childIndex) {
        if (childIndex < 0) {
            throw new IllegalArgumentException("childIndex 不可为负数");
        }
    }
}
