package club.heiqi.qz_uilib.skija.gui.component;

import aurelienribon.tweenengine.TweenManager;
import club.heiqi.qz_uilib.skija.gui.BaseGUI;
import io.github.humbleui.skija.Canvas;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.Display;

/**
 * 基础组件类, 内部含有自动更新的动画管理器{@code TweenManager}, 你可以使用它来创建自己的动画效果, 动画使用方式请参考 Universal Tween Engine
 */
public abstract class UIComponent {
    public static Logger LOG = LogManager.getLogger();
    // 动画管理器
    public TweenManager manager = new TweenManager();
    long lastTime ;
    // 属性
    public float x, y, width, height; // 组件坐标和宽高 单位百分比

    /**
     * 使用百分比单位创建
     * @param x 在左边 % 多少
     * @param y 在顶部 % 多少
     * @param width 占屏幕宽度 %
     * @param height 占屏幕高度 %
     * <br>--------------------<br>
     * 其继承类中大多含有{@code set_xxx}的链式调用函数可用, 请多留意<p/>
     * 如有每帧逻辑可以重写{@code onTick}逻辑
     */
    public UIComponent(float x, float y, float width, float height) {
        int sw = Display.getWidth(); int sh = Display.getHeight();
        x = sw*x; y = sh*y; width = sw*width; height = sh*height; // 将百分比转为绝对值坐标
        this.x = x; this.y = y; this.width = width; this.height = height;
        lastTime = System.currentTimeMillis();
    }

    public abstract void draw(Canvas canvas);

    /**
     * 由外部自动触发
     * 当点击坐标在组件的矩形边框内时自动被调用
     * 处理被点击事件
     */
    public abstract void onClick();

    public abstract void onHover(BaseGUI.MouseInfo mouseInfo);

    public abstract void onMouseOut(BaseGUI.MouseInfo mouseInfo);

    /**
     * 被按住后会每帧调用，还请留意不要将瞬时逻辑放到此处
     */
    public abstract void onPress();

    /**
     * 每个渲染帧更新</p>
     * 务必使用super调用父类方法，否则动画可能会失效
     */
    public void onTick() {
        long diff = System.currentTimeMillis() - lastTime;
        manager.update((float) diff / 1000);
        lastTime = System.currentTimeMillis();
    }

    /**
     * 检查是否在父组件内部
     * @param px 父组件左上角x
     * @param py 父组件左上角y
     * @return true 在父组件内部 false 不在父组件内部
     */
    public boolean contains(float px, float py) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    public void onResize() {
        width = Display.getWidth();
        height = Display.getHeight();
    }
}
