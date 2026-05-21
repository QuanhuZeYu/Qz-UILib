package club.heiqi.uilib.ui.render;

/**
 * Backdrop 采样区域对应的 tile 网格范围。
 */
final class TileRegion {

    private final int tileLeft;
    private final int tileTop;
    private final int tileRight;
    private final int tileBottom;

    TileRegion(int tileLeft, int tileTop, int tileRight, int tileBottom) {
        this.tileLeft = tileLeft;
        this.tileTop = tileTop;
        this.tileRight = tileRight;
        this.tileBottom = tileBottom;
    }

    int getTileLeft() {
        return tileLeft;
    }

    int getTileTop() {
        return tileTop;
    }

    int getTileRight() {
        return tileRight;
    }

    int getTileBottom() {
        return tileBottom;
    }

    int getTileWidth() {
        return Math.max(0, tileRight - tileLeft);
    }

    int getTileHeight() {
        return Math.max(0, tileBottom - tileTop);
    }

    int getTileCount() {
        return getTileWidth() * getTileHeight();
    }
}
