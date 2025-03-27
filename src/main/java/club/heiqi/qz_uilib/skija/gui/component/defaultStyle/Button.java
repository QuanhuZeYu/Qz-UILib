package club.heiqi.qz_uilib.skija.gui.component.defaultStyle;

import club.heiqi.qz_uilib.skija.gui.component.UIComponent;
import club.heiqi.qz_uilib.skija.gui.component.Utils;
import club.heiqi.qz_uilib.skija.font.FontLoader;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.RRect;
import org.joml.Vector2f;

/**
 * 默认基础样式Button
 */
public class Button extends UIComponent {
    public String text = "默认文本";

    public final int defaultFillColor = 0xFF3998DB;
    public final int defaultStrokeColor = 0x00000000;
    public final int defaultTextColor = 0xFFFFFFFF;

    public int fillColor = defaultFillColor;
    public int strokeColor = defaultStrokeColor;
    public int textColor = defaultTextColor;

    public Button(float x, float y, float width, float height) {
        super(x, y, width, height);
    }

    public Button setText(String text) {
        this.text = text; return this;
    }

    public Button setFillColor(int fillColor) {this.fillColor = fillColor; return this;}
    public Button setStrokeColor(int strokeColor) {this.strokeColor = strokeColor; return this;}

    @Override
    public void draw(Canvas canvas) {
        Paint fillPaint = new Paint().setColor(fillColor);
        Paint strokePaint = new Paint().setStroke(true).setColor(strokeColor);
        Paint textPaint = new Paint().setColor(textColor);
        // 绘制背景
        canvas.drawRRect(RRect.makeXYWH(x, y, width, height, 20), fillPaint);
        // 绘制外边框
        canvas.drawRRect(RRect.makeXYWH(x, y, width, height, 20), strokePaint);
        Font font = FontLoader.getDefaultFont();
        Vector2f center = new Vector2f(x+width/2, y+height/2);
        Utils.drawStringCenter(canvas, text, font, center, textPaint);

        // 释放资源
        fillPaint.close(); strokePaint.close(); textPaint.close();
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

    }
}
