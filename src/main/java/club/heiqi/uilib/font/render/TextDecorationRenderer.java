package club.heiqi.uilib.font.render;

/**
 * 文本装饰线收集门面。
 *
 * <p>装饰线实际由 {@link FontBatchRenderer} 走字体主 shader 与 VAO/VBO 管线提交，本类不再执行独立 GL flush。</p>
 */
public class TextDecorationRenderer {

    private final FontBatchRenderer batchRenderer;

    /**
     * 创建文本装饰线收集门面。
     *
     * @param batchRenderer 字体主批渲染器
     */
    public TextDecorationRenderer(FontBatchRenderer batchRenderer) {
        this.batchRenderer = batchRenderer;
    }

    /**
     * 收集一个装饰线四边形。
     *
     * @param x 起始 X
     * @param y 起始 Y
     * @param width 线条宽度
     * @param height 线条高度
     * @param color 文本颜色
     */
    public void collect(float x, float y, float width, float height, int color) {
        batchRenderer.collectDecoration(x, y, width, height, color);
    }

    /**
     * 保留旧调用点的空提交入口。
     *
     * <p>装饰线已经随 {@link FontBatchRenderer#flush(club.heiqi.uilib.font.shader.FontShaderProgram)}
     * 在字体主管线中提交，此处不再执行 OpenGL 操作。</p>
     */
    public void flush() {
    }

    /**
     * 清空已收集的装饰线。
     */
    public void clear() {
        batchRenderer.clearDecorationQuads();
    }
}
