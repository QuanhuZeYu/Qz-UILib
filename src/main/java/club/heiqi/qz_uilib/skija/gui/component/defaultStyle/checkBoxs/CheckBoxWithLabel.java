package club.heiqi.qz_uilib.skija.gui.component.defaultStyle.checkBoxs;

import club.heiqi.qz_uilib.skija.alignment.ComponentAlignUtils;
import club.heiqi.qz_uilib.skija.alignment.StringAlignUtils;
import club.heiqi.qz_uilib.skija.gui.component.UIComponent;
import club.heiqi.qz_uilib.skija.gui.component.defaultStyle.Label;
import club.heiqi.qz_uilib.skija.gui.component.defaultStyle.cardView.CardView;
import io.github.humbleui.skija.Canvas;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class CheckBoxWithLabel extends UIComponent {

    public String labelText = "";

    public CardView cardView;
    public CheckBox checkBox;
    public Label label;

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
    public CheckBoxWithLabel(float x, float y, float width, float height) {
        super(x, y, width, height);
        float min = Math.min(this.width, this.height);
        cardView = new CardView(x,y,width,height).setParent(this);
        checkBox = new CheckBox(x,y,min,min).setParent(cardView).setWidth(min).setHeight(min);
        ComponentAlignUtils.align(checkBox,cardView, ComponentAlignUtils.ComponentAlign.LEFT_LEFT);
        ComponentAlignUtils.align(checkBox,cardView, ComponentAlignUtils.ComponentAlign.VERTICAL_CENTER_ALIGN);
        label = new Label(x,y).setAlign(StringAlignUtils.Align.LEFT_CENTER_TO_TARGET).setParent(cardView);
        ComponentAlignUtils.align(label,checkBox, ComponentAlignUtils.ComponentAlign.LEFT_RIGHT);
        ComponentAlignUtils.align(label,checkBox, ComponentAlignUtils.ComponentAlign.VERTICAL_CENTER_ALIGN);
        label.x = label.x + 5;
        cardView.width += 8; cardView.height += 8;
        ComponentAlignUtils.align(cardView,checkBox, ComponentAlignUtils.ComponentAlign.VERTICAL_CENTER_ALIGN);
        ComponentAlignUtils.align(cardView,this, ComponentAlignUtils.ComponentAlign.HORIZON_CENTER_ALIGN);
    }

    public CheckBoxWithLabel setText(String text) {
        this.labelText = text; label.setText(text);
        return this;
    }

    public CheckBoxWithLabel setCheckBoxCallBack(Consumer<Boolean> consumer) {
        checkBox.setClickedCallBack(consumer); return this;
    }
    public CheckBoxWithLabel setCheckBoxStateHook(Supplier<Boolean> hook) {
        checkBox.setStateHook(hook); return this;
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
    }

    @Override
    public void onDragTick() {

    }
}
