package club.heiqi.uilib.ui.scene.layout;

/** 内容测量约束，单位为 logical px。 */
public final class Constraints {
    public final int availableWidth, availableHeight;

    public Constraints(int width, int height) {
        if (width < 0 || height < 0) throw new IllegalArgumentException("可用空间不能为负");
        availableWidth = width;
        availableHeight = height;
    }
}
