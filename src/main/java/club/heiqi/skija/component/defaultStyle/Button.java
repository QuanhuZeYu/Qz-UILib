package club.heiqi.skija.component.defaultStyle;

import club.heiqi.skija.component.UIComponent;
import club.heiqi.skija.component.Utils;
import club.heiqi.skija.font.FontLoader;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.RRect;
import org.joml.Vector2f;
import org.lwjgl.opengl.Display;

/**
 * 默认基础样式Button
 */
public class Button extends UIComponent {
    public String text = "默认文本";
    // 默认空实现
    public Runnable onClick = new Runnable() {
        /**
         * When an object implementing interface {@code Runnable} is used
         * to create a thread, starting the thread causes the object's
         * {@code run} method to be called in that separately executing
         * thread.
         * <p>
         * The general contract of the method {@code run} is that it may
         * take any action whatsoever.
         *
         * @see Thread#run()
         */
        @Override
        public void run() {

        }
    };
    public Runnable onHover = onClick;
    public Runnable onPress = onClick;

    /**
     * 使用百分比单位创建
     * @param x 在左边 % 多少
     * @param y 在顶部 % 多少
     * @param width 占屏幕宽度 %
     * @param height 占屏幕高度 %
     * <br>-----<br>
     * 请勿忘记使用setText设置按钮文本，可链式调用
     */
    public Button(float x, float y, float width, float height) {
        super(x, y, width, height);
    }

    public Button setText(String text) {
        this.text = text; return this;
    }

    @Override
    public void draw(Canvas canvas) {
        Paint paint = new Paint().setColor(Color.makeARGB(130,11,21,31));
        Paint strokePaint = new Paint().setStroke(true).setColor(Color.makeARGB(255,199,246,96));
        Paint textPaint = new Paint().setColor(Color.makeARGB(255,199,246,96));
        // 绘制背景
        canvas.drawRRect(RRect.makeXYWH(x, y, width, height, 4), paint);
        // 绘制外边框
        canvas.drawRRect(RRect.makeXYWH(x-0.00125f, y-0.00125f, width+2, height+2, 12), strokePaint);
        Font font = FontLoader.fonts.get(0);
        Vector2f center = new Vector2f(x+width/2, y+height/2);
        Utils.drawStringCenter(canvas, text, font, center, textPaint);

        // 释放资源
        paint.close(); strokePaint.close(); textPaint.close();
    }

    @Override
    public void onClick() {
        onClick.run();
    }

    @Override
    public void onHover() {

    }

    @Override
    public void onPress() {

    }
}
