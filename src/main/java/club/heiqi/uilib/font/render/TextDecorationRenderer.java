package club.heiqi.uilib.font.render;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

/**
 * 文本装饰线渲染器。
 */
public class TextDecorationRenderer {

    private final List<TextLineQuad> lineQuads = new ArrayList<TextLineQuad>();
    private final FontRenderStateGuard stateGuard = new FontRenderStateGuard();

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
        float alpha = (float) (color >> 24 & 255) / 255.0F;
        float red = (float) (color >> 16 & 255) / 255.0F;
        float green = (float) (color >> 8 & 255) / 255.0F;
        float blue = (float) (color & 255) / 255.0F;

        float[] vertex = new float[] {
                x + width, y + height, 0.0F,
                x + width, y, 0.0F,
                x, y, 0.0F,
                x, y + height, 0.0F
        };
        float[] vertexColor = new float[] {
                red, green, blue, alpha,
                red, green, blue, alpha,
                red, green, blue, alpha,
                red, green, blue, alpha
        };
        lineQuads.add(new TextLineQuad(vertex, vertexColor));
    }

    /**
     * 绘制并清空已收集的装饰线。
     */
    public void flush() {
        if (lineQuads.isEmpty()) {
            return;
        }

        stateGuard.run(new Runnable() {
            @Override
            public void run() {
                FontRenderStateSupport.prepareTextRenderState();
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glBegin(GL11.GL_QUADS);
                for (TextLineQuad quad : lineQuads) {
                    float[] color = quad.getColor();
                    float[] vertex = quad.getVertex();
                    for (int i = 0; i < 4; i++) {
                        int colorOffset = i * 4;
                        int vertexOffset = i * 3;
                        GL11.glColor4f(color[colorOffset], color[colorOffset + 1], color[colorOffset + 2], color[colorOffset + 3]);
                        GL11.glVertex3f(vertex[vertexOffset], vertex[vertexOffset + 1], vertex[vertexOffset + 2]);
                    }
                }
                GL11.glEnd();
            }
        });
        lineQuads.clear();
    }

    /**
     * 清空已收集的装饰线。
     */
    public void clear() {
        lineQuads.clear();
    }
}
