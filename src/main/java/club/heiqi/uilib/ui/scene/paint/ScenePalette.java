package club.heiqi.uilib.ui.scene.paint;

/**
 * ScenePalette 集中维护 scene 控件共享配色常量。
 */
public final class ScenePalette {

    /** 偶数数据行斑马纹背景色。 */
    public static final int ROW_BG_EVEN = 0xFF1E293B;
    /** 奇数数据行斑马纹背景色。 */
    public static final int ROW_BG_ODD = 0xFF243B53;

    /** 纯静态调色板，禁止实例化。 */
    private ScenePalette() {
    }

    /**
     * 按行下标读取斑马纹背景色。
     *
     * @param rowIndex 行下标
     * @return 对应行背景色
     */
    public static int rowBg(int rowIndex) {
        return (rowIndex % 2 == 0) ? ROW_BG_EVEN : ROW_BG_ODD;
    }
}
