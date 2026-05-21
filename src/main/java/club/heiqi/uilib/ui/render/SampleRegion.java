package club.heiqi.uilib.ui.render;

/**
 * Backdrop 采样区域。
 */
final class SampleRegion {

    private final int left;
    private final int top;
    private final int right;
    private final int bottom;

    SampleRegion(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    int getLeft() {
        return left;
    }

    int getTop() {
        return top;
    }

    int getRight() {
        return right;
    }

    int getBottom() {
        return bottom;
    }

    int getWidth() {
        return right - left;
    }

    int getHeight() {
        return bottom - top;
    }
}
