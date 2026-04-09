package club.heiqi.uilib.font.render;

/**
 * 文本装饰线四边形。
 */
public class TextLineQuad {

    private final float[] vertex;
    private final float[] color;

    /**
     * 创建文本装饰线四边形。
     *
     * @param vertex 顶点坐标
     * @param color 顶点颜色
     */
    public TextLineQuad(float[] vertex, float[] color) {
        this.vertex = vertex;
        this.color = color;
    }

    public float[] getVertex() {
        return vertex;
    }

    public float[] getColor() {
        return color;
    }
}
