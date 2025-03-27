package club.heiqi.qz_uilib.skija.gui.component.defaultStyle;

import club.heiqi.qz_uilib.skija.gui.component.UIComponent;
import club.heiqi.qz_uilib.skija.gui.component.Utils;
import com.google.common.util.concurrent.AtomicDouble;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.types.Point;
import io.github.humbleui.types.RRect;

import java.awt.*;

public class BackGround extends UIComponent {
    public static final int DURATION = 60; // 完整循环周期（秒）
    public int blurAnimTime = 1_000;
    public long startTime;

    public int fillColor = 0xCC0A1212, strokeColor = 0xFF63FFC1;
    public int[] colors = {0xFF2B8CFF, 0xFF29B6F6, 0xFF00E5FF};


    public BackGround(float x, float y, float width, float height) {
        super(x, y, width, height);
        startTime = System.currentTimeMillis();
    }

    public BackGround setFillColor(int color) {
        fillColor = color;
        return this;
    }

    public BackGround setStrokeColor(int color) {
        strokeColor = color;
        return this;
    }

    @Override
    public void draw(Canvas canvas) {
        // 制造动画背景模糊
        long diffTime = System.currentTimeMillis() - startTime;
        AtomicDouble blur = new AtomicDouble(15f);
        if (diffTime < blurAnimTime) {
            float per = (float) diffTime / blurAnimTime;
            blur.set(blur.get() * per);
        }
        Utils.drawBlurMCBackground(canvas, (float) blur.get());
        // 1.创建圆角矩形
        RRect rect = RRect.makeXYWH(x, y, width, height, 20);
        RRect rectStroke = RRect.makeXYWH(x, y, width, height, 20);
        // 2.创建线性渐变画笔
        Point start = new Point(x, y); // 左上
        Point end = new Point(x + width, y + height);
        Shader shader = Shader.makeLinearGradient(start, end, colors);
        Paint paint = new Paint().setAntiAlias(true).setColor(fillColor);
        Paint paintStroke = new Paint().setShader(shader).setAntiAlias(true).setStroke(true);
        canvas.drawRRect(rect, paint);
        canvas.drawRRect(rectStroke, paintStroke);
        shader.close();
        paint.close();
        paintStroke.close();
    }

    @Override
    public void onClick() {

    }

    @Override
    public void onHover() {

    }

    @Override
    public void onPress() {

    }

    @Override
    public void onTick() {
        // 更新每个颜色值
        for (int i = 0; i < colors.length; i++) {
            int color = colors[i];
            int alpha = (color >> 24) & 0xFF;
            int red = (color >> 16) & 0xFF;
            int green = (color >> 8) & 0xFF;
            int blue = color & 0xFF;
            // RGBToHSV/HSVToColor
            float[] hsb = Color.RGBtoHSB(red, green, blue, null);
            hsb[0] = (hsb[0] + (5 / 360f)) % 1.0f; // 将步长转换为0-1范围
            int newRGB = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
            colors[i] = (alpha << 24) | (newRGB & 0x00FFFFFF);
        }
    }
}
