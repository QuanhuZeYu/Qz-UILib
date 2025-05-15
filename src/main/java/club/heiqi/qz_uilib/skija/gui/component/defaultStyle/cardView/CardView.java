package club.heiqi.qz_uilib.skija.gui.component.defaultStyle.cardView;

import club.heiqi.qz_uilib.skija.gui.component.UIComponent;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.RRect;

public class CardView extends UIComponent {

    public float round = 5;
    public float strokeRectWidth = 0.5f;

    public int defaultFillColor = 0x0DFFFFFF; // 5%透明度的白色
    public int defaultStrokeRectColor = 0xFFefefef;

    public int fillColor = defaultFillColor;
    public int strokeRectColor = defaultStrokeRectColor;

    /**
     * 使用百分比单位创建
     *
     * @param x      在左边 % 多少
     * @param y      在顶部 % 多少
     * @param width  占屏幕宽度 %
     * @param height 占屏幕高度 %
     *               <br>--------------------<br>
     *               其继承类中大多含有{@code set_xxx}的链式调用函数可用, 请多留意<p/>
     *               如有每帧逻辑可以重写{@code onTick}逻辑<br>
     *               初始化后坐标变为绝对值坐标而不是百分比坐标了<br>
     *               当窗口缩放后，MC的GUI会自动重新创建
     */
    public CardView(float x, float y, float width, float height) {
        super(x, y, width, height);
    }

    @Override
    public void draw(Canvas canvas) {
        // 底色
        RRect fillRect = RRect.makeXYWH(x,y,width,height,round);
        Paint fillPaint = new Paint().setColor(fillColor).setAntiAlias(true);
        // 边框
        RRect strokeRect = RRect.makeXYWH(x,y,width,height,round);
        Paint strokePaint = new Paint().setColor(strokeRectColor).setAntiAlias(true)
            .setStroke(true).setStrokeWidth(strokeRectWidth);

        canvas.drawRRect(fillRect,fillPaint);
        canvas.drawRRect(strokeRect,strokePaint);

        fillPaint.close(); strokePaint.close();
        super.draw(canvas);
    }

    public CardView setRound(float round) {
        this.round = round; return this;
    }

    @Override
    public void onDragTick() {

    }
}
