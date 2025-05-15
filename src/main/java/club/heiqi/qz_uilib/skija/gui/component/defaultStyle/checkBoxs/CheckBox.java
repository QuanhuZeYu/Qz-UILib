package club.heiqi.qz_uilib.skija.gui.component.defaultStyle.checkBoxs;

import club.heiqi.qz_uilib.skija.gui.component.UIComponent;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;
import io.github.humbleui.types.RRect;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class CheckBox extends UIComponent {
    /**圆角大小*/
    public float round = 5;
    public float strokeWidth = 2;
    public float pathWidth = 4;

    public int defaultFillColor = 0xFF0a59f7;
    public int defaultCheckPathColor = 0xFFFFFFFF;
    public int defaultStrokeColor = 0xFF959595;

    public int fillColor = defaultFillColor;
    public int checkPathColor = defaultCheckPathColor;
    public int strokeColor = defaultStrokeColor;

    public boolean curState = false;

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
    public CheckBox(float x, float y, float width, float height) {
        super(x, y, width, height);
    }

    @Override
    public void draw(Canvas canvas) {
        // 1.盒子外边框
        RRect rectStroke = RRect.makeXYWH(x,y,width,height,round);
        // 2.盒子内部填充颜色
        RRect rectFill = RRect.makeXYWH(x,y,width,height,round);
        // 3.勾
        float l = x, r = x+width;
        float t = y, b = y+height;
        float centerX = (l+r)/2, centerY = (t+b)/2;
        float pathL = (l+centerX)/2, pathR = (r+centerX)/2;
        float pathT = (t+centerY)/2, pathB = (b+centerY)/2;
        Path checkPath = new Path().moveTo(pathL,centerY)
            .lineTo(centerX,pathB)
            .lineTo(pathR,pathT);
        // 4.填充盒子的画笔
        Paint fillPaint = new Paint().setColor(fillColor).setAntiAlias(true);
        // 5.外边框画笔
        Paint strokePaint = new Paint().setColor(strokeColor).setAntiAlias(true).setStroke(true)
            .setStrokeWidth(strokeWidth);
        // 6.勾线画笔
        Paint pathPaint = new Paint().setColor(checkPathColor).setAntiAlias(true)
            .setStroke(true).setStrokeWidth(pathWidth);

        if (hook != null) {
            try {
                curState = hook.get();
            } catch (Exception ignored) {}
        }
        // 最底下是填充矩形
        if (curState) {
            canvas.drawRRect(rectFill,fillPaint);
        }
        // 绘制外框
        canvas.drawRRect(rectStroke,strokePaint);
        // 最后绘制勾
        if (curState) {
            canvas.drawPath(checkPath,pathPaint);
        }

        checkPath.closePath();
        fillPaint.close(); strokePaint.close(); pathPaint.close();
        super.draw(canvas);
    }

    public CheckBox setRound(float round) {
        this.round = round; return this;
    }

    /**
     * 可使用该设置行为覆盖初始创建的值
     */
    @Override
    public CheckBox setWidth(float width) {
        this.width = width; return this;
    }
    /**
     * 可使用该设置行为覆盖初始创建的值
     */
    @Override
    public CheckBox setHeight(float height) {
        this.height = height; return this;
    }

    public CheckBox setClickedCallBack(Consumer<Boolean> consumer) {
        this.onClickedTask = consumer; return this;
    }

    public Supplier<Boolean> hook;
    public CheckBox setStateHook(Supplier<Boolean> hook) {
        this.hook = hook; return this;
    }

    @Override
    public void onDragTick() {

    }

    public Consumer<Boolean> onClickedTask;
    @Override
    public boolean onRelease(boolean transmit) {
        super.onRelease(false);
        this.curState = !this.curState;
        if (onClickedTask != null) {
            onClickedTask.accept(this.curState);
        }
        return false;
    }
}
