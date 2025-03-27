package club.heiqi.qz_uilib.skija.component;

import io.github.humbleui.skija.Canvas;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.Display;

public abstract class UIComponent {
    public static Logger LOG = LogManager.getLogger();
    public float x, y, width, height; // 组件坐标和宽高 单位百分比

    /**
     * 使用百分比单位创建
     * @param x 在左边 % 多少
     * @param y 在顶部 % 多少
     * @param width 占屏幕宽度 %
     * @param height 占屏幕高度 %
     * <br>-----<br>
     * 请勿忘记使用setText设置按钮文本，可链式调用
     */
    public UIComponent(float x, float y, float width, float height) {
        int sw = Display.getWidth(); int sh = Display.getHeight();
        x = sw*x; y = sh*y; width = sw*width; height = sh*height; // 将百分比转为绝对值坐标
        this.x = x; this.y = y; this.width = width; this.height = height;
    }

    public abstract void draw(Canvas canvas);

    /**
     * 由外部自动触发
     * 当点击坐标在组件的矩形边框内时自动被调用
     * 处理被点击事件
     */
    public abstract void onClick();

    public abstract void onHover();

    public abstract void onPress();

    /**
     * 每个渲染帧更新
     */
    public abstract void onTick();

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
