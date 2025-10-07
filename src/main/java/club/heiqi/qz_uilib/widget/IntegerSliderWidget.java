package club.heiqi.qz_uilib.widget;

import club.heiqi.qz_uilib.widget.drawUtil.RenderTool;
import club.heiqi.qz_uilib.widget.drawUtil.RoundedRectangle;
import org.joml.Vector2d;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import java.util.Set;
import java.util.function.Consumer;

import static org.lwjgl.opengl.Display.getHeight;

public class IntegerSliderWidget extends Widget {
    public RoundedRectangle rectangleInside = new RoundedRectangle();
    public RoundedRectangle rectangleOutside = new RoundedRectangle();

    public float sliderX = 0, sliderY = 0, sliderW = 12;
    public int sliderMin = -64, sliderMax = 64;
    public int sliderColor = 0xffddddff;
    public int textColor = 0xfff0f0f0;
    public boolean dragged = false;
    public void setSliderX(float x) {
        float sliderXCopy = sliderX;
        sliderX = x;
        if (sliderX != sliderXCopy) {
            callback.accept(getIntValue());
        }
    }

    public IntegerSliderWidget() {
        setPerfectSize(-1, sliderW);
    }

    // === 防止设置大小小于游标大小 === tip:仍然可能被自动布局组件设置到过小
    public IntegerSliderWidget setSize(float width, float height) {
        width = Math.max(sliderW, width);
        height = Math.max(this.height, height);
        return super.setSize(width, height);
    }
    public IntegerSliderWidget setPerfectSize(float width, float height) {
        width = Math.max(sliderW, width);
        height = Math.max(this.height, height);
        return super.setPerfectSize(width, height);
    }

    public Consumer<Integer> callback = (integer -> {});
    public IntegerSliderWidget setSliderChangeCallBack(Consumer<Integer> callBack) {
        this.callback = callBack;
        return this;
    }

    public IntegerSliderWidget setRange(int min, int max) {
        sliderMin = min;
        sliderMax = max;
        return this;
    }

    public IntegerSliderWidget setTextColor(int color) {
        this.textColor = color;
        return this;
    }

    public int getIntValue() {
        return (int) ((sliderX/width)*(sliderMax-sliderMin));
    }

    @Override
    public void drawSelf() {
        super.drawSelf();
        drag();

        rectangleOutside.gen(width,6,3,1,new Vector2d(x,y+(height-6)/2), 0xfff0f0f0);
        rectangleInside.gen(width-2,6-2,3,1,new Vector2d(x+1,y+((height-5))/2), 0xff484848);
        // 绘制中心线条
        RenderTool.getInstance().render(
                rectangleOutside.getVertexArray(),
                rectangleOutside.getTexCoordArray(),
                rectangleOutside.getColorArray(),
                rectangleOutside.getIndexArray()
        );
        RenderTool.getInstance().render(
                rectangleInside.getVertexArray(),
                rectangleInside.getTexCoordArray(),
                rectangleInside.getColorArray(),
                rectangleInside.getIndexArray()
        );

        // 绘制当前指示的值
        fontRenderer.setCharSize(height);
        int stringWidth = fontRenderer.getStringWidth(String.valueOf(getIntValue()));
        fontRenderer.drawString(String.valueOf(getIntValue()), (int) (x+(width-stringWidth)/2), (int) y, textColor);

        // 绘制滑块
        float sl = this.x + sliderX-5;
        float sr = this.x + sliderX+5;
        float st = this.y;
        float sb = this.y + height;
        float alpha = (float) ((sliderColor >> 24) & 255) /255;
        float red = (float) ((sliderColor >> 16) & 255) /255;
        float green = (float) ((sliderColor >> 8) & 255) /255;
        float blue = (float) ((sliderColor) & 255) /255;
        GL11.glColor4f(alpha,red,green,blue);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3f(sl,st,0);
        GL11.glVertex3f(sl,sb,0);
        GL11.glVertex3f(sr,sb,0);
        GL11.glVertex3f(sr,st,0);
        GL11.glEnd();
    }

    @Override
    public void onDragged(float newX, float newY, Set<Integer> clicked) {
        super.onDragged(newX, newY, clicked);
        dragged = true;
    }

    public void drag() {
        if (dragged) {
            float x = Mouse.getX();
            float y = Display.getHeight() - Mouse.getY();

            // 判断值是否发生变化
            float sliderX = Math.min(width, Math.max(0, x - this.x));
            setSliderX(sliderX);
        }
    }

    @Override
    public void onMouseMoving(float x, float y, Set<Integer> clicked, Set<Integer> hold) {
        super.onMouseMoving(x, y, clicked, hold);
        if (hold.isEmpty()) dragged = false;
    }
}
