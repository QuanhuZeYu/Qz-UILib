package club.heiqi.uilib.ui.render;

/**
 * 最近一次 backdrop-filter 实际渲染路径。
 */
public enum BackdropFilterRenderPath {
    NONE("none"),
    SHADER("shader"),
    FIXED_PIPELINE("fixed-pipeline"),
    TINT_FALLBACK("tint-fallback");

    private final String label;

    BackdropFilterRenderPath(String label) {
        this.label = label;
    }

    /**
     * 返回适合显示的路径标签。
     *
     * @return 路径标签
     */
    public String getLabel() {
        return label;
    }
}
