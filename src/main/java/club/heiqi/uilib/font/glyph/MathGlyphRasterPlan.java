package club.heiqi.uilib.font.glyph;

import club.heiqi.uilib.font.latex.layout.MathGlyphMetrics;

/** 数学 bitmap 固定整数核心分片规则；worker 与 adapter 必须使用同一 settings/profile。 */
public final class MathGlyphRasterPlan {
    public static final int MAX_TILES = 4096;
    private final int left;
    private final int top;
    private final int width;
    private final int height;
    private final int coreWidth;
    private final int tileCount;
    private final int padding;

    public MathGlyphRasterPlan(MathGlyphMetrics metrics, int textureSize, int inkPadding) {
        if (metrics == null || textureSize <= 0 || inkPadding < 0 || inkPadding > 32) {
            throw new IllegalArgumentException("无效的数学分片设置");
        }
        padding = inkPadding;
        coreWidth = textureSize - 2 * padding;
        // 两像素覆盖 AWT 边缘覆盖率及字体 hinting 的整数取整；不分配整体位图。
        double l = Math.floor(metrics.getInkLeft()) - 2;
        double t = Math.floor(metrics.getInkTop()) - 2;
        double r = Math.ceil(metrics.getInkRight()) + 2;
        double b = Math.ceil(metrics.getInkBottom()) + 2;
        if (coreWidth <= 0 || l < Integer.MIN_VALUE || t < Integer.MIN_VALUE
                || r > Integer.MAX_VALUE || b > Integer.MAX_VALUE
                || r - l > Integer.MAX_VALUE || b - t > coreWidth) {
            throw new IllegalArgumentException("数学字形超出可生成分片尺寸");
        }
        left = (int) l;
        top = (int) t;
        width = (int) (r - l);
        height = (int) (b - t);
        long count = ((long) width + coreWidth - 1) / coreWidth;
        if (count <= 0 || count > MAX_TILES) {
            throw new IllegalArgumentException("数学字形分片数量超出预算上限");
        }
        tileCount = (int) count;
    }

    public static int tileCount(MathGlyphMetrics metrics, int textureSize, int inkPadding) {
        return new MathGlyphRasterPlan(metrics, textureSize, inkPadding).getTileCount();
    }

    public int getTileCount() { return tileCount; }
    public int getPadding() { return padding; }
    public int getTop() { return top; }
    public int getHeight() { return height; }
    public int getLeft(int tileIndex) {
        checkIndex(tileIndex);
        return left + tileIndex * coreWidth;
    }
    public int getWidth(int tileIndex) {
        checkIndex(tileIndex);
        return Math.min(coreWidth, width - tileIndex * coreWidth);
    }
    private void checkIndex(int tileIndex) {
        if (tileIndex < 0 || tileIndex >= tileCount) {
            throw new IllegalArgumentException("数学字形 tileIndex 越界");
        }
    }
}
