package club.heiqi.uilib.ui.layout;

/**
 * 组件在给定约束下的测量结果。
 */
public class UiMeasureResult {

    private final int width;
    private final int height;

    public UiMeasureResult(int width, int height) {
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
