package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.scene.input.SceneKey;

/**
 * 虚拟网格纯函数导航与窗口数学（SceneVirtualGrid 的数值核心，零副作用）。
 *
 * <p>四向导航语义（测试锚定）：</p>
 * <ul>
 *   <li>上下移动一行：同行保持列位置；首行/末行越界时<b>夹取</b>（原地不动）；末行缺列时夹取到该行末项。</li>
 *   <li>左右移动一列：行边界自然<b>换行</b>（行主序 ±1 即跨行）；首项/末项越界时夹取。</li>
 *   <li>无高亮（current &lt; 0）时任意方向键进入第 0 项。</li>
 * </ul>
 */
final class SceneVirtualGridNav {

    private SceneVirtualGridNav() {
    }

    /** 数据项数换算总行数；空列表为 0 行。 */
    static int totalRows(int size, int columns) {
        int cols = Math.max(1, columns);
        return size <= 0 ? 0 : (size + cols - 1) / cols;
    }

    /** 由可用内宽推算列数：{@code max(1, (innerWidth + gapX) / (cellWidth + gapX))}。 */
    static int deriveColumns(int innerWidth, int cellWidth, int gapX) {
        if (innerWidth <= 0 || cellWidth <= 0) {
            return 1;
        }
        return Math.max(1, (innerWidth + gapX) / (cellWidth + gapX));
    }

    /** 是否为网格导航消费的方向键。 */
    static boolean isNavigationKey(SceneKey key) {
        return key == SceneKey.ARROW_UP || key == SceneKey.ARROW_DOWN
                || key == SceneKey.ARROW_LEFT || key == SceneKey.ARROW_RIGHT;
    }

    /**
     * 四向网格导航：返回下一步高亮下标。
     *
     * @param current 当前高亮下标，&lt;0 表示无高亮
     * @param key     方向键（非方向键返回 current 不变）
     * @param columns 列数（&lt;=0 按 1 处理）
     * @param size    数据项总数
     * @return 下一步下标；空列表返回 -1
     */
    static int navigate(int current, SceneKey key, int columns, int size) {
        if (!isNavigationKey(key)) {
            return current;
        }
        if (size <= 0) {
            return -1;
        }
        if (current < 0) {
            return 0;
        }
        int cols = Math.max(1, columns);
        switch (key) {
            case ARROW_RIGHT:
                return Math.min(current + 1, size - 1);
            case ARROW_LEFT:
                return Math.max(current - 1, 0);
            case ARROW_UP: {
                int row = current / cols;
                return row == 0 ? current : current - cols;
            }
            case ARROW_DOWN: {
                int row = current / cols;
                if (row >= totalRows(size, cols) - 1) {
                    return current;
                }
                return Math.min(current + cols, size - 1);
            }
            default:
                return current;
        }
    }

    /**
     * 滚动像素换算可见窗口首行（floor(scroll / stride) 夹取到 [0, maxStartRow]）。
     *
     * @param scroll      滚动偏移（像素，负值按 0 处理）
     * @param stride      行步长（cellHeight + gapY，&lt;=0 按 1 处理）
     * @param maxStartRow 最大首行（totalRows - visibleRows 夹取）
     * @return 可见窗口首行
     */
    static int windowStartRowForScroll(int scroll, int stride, int maxStartRow) {
        int s = Math.max(0, scroll);
        int st = Math.max(1, stride);
        int maxStart = Math.max(0, maxStartRow);
        return Math.max(0, Math.min(maxStart, s / st));
    }

    /**
     * 高亮行滚动进入视野的目标滚动偏移。
     *
     * @param row            目标行（高亮下标 / 列数）
     * @param windowStartRow 当前窗口首行
     * @param visibleRows    可见行数
     * @param totalRows      总行数
     * @param stride         行步长
     * @return 目标滚动偏移；目标行已在视野内时返回 -1（无需滚动）
     */
    static int scrollTargetForRow(int row, int windowStartRow, int visibleRows, int totalRows, int stride) {
        int st = Math.max(1, stride);
        if (row < windowStartRow) {
            return Math.max(0, row) * st;
        }
        if (row >= windowStartRow + visibleRows) {
            int maxStart = Math.max(0, totalRows - visibleRows);
            return Math.min(maxStart, row - visibleRows + 1) * st;
        }
        return -1;
    }
}
