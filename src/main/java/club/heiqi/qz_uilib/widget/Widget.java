package club.heiqi.qz_uilib.widget;

import aurelienribon.tweenengine.Tween;
import aurelienribon.tweenengine.TweenManager;
import club.heiqi.qz_uilib.Config;
import club.heiqi.qz_uilib.fontsystem.impl.ReplaceFontRender;
import club.heiqi.qz_uilib.widget.api.WidgetEvent;
import club.heiqi.qz_uilib.widget.layout.DefaultLayout;
import club.heiqi.qz_uilib.widget.layout.RelativeCoordinateLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector2f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 使用组件时可用的事件在WidgetEvent接口中可查看
 */
public class Widget implements WidgetEvent {
    /**动画插值管理器 单例模式*/
    public static final TweenManager TWEEN_MANAGER = new TweenManager();
    static {
        Tween.setCombinedAttributesLimit(4);
    }
    public static long LAST_TIME = System.nanoTime();
    public static Logger LOG = LogManager.getLogger();
    /**可自定义字符大小的字体渲染器*/
    public static ReplaceFontRender fontRenderer;
    /**绝对坐标X*/
    public float x = 0, y = 0;
    /**相对坐标 */
    public float localX = 0, localY = 0;

    /**当前大小*/
    public float width = 1, height = 1;
    /**期望大小，用于布局器选取合适大小; -1表示不限制可被随意拉伸*/
    public float perfectWidth = 64, perfectHeight = 64;

    /**外边距 暂时未用到*/
    public float outMargins = 4;
    /**内边距*/
    public float insideMargins = 4;

    /**布局器*/
    public DefaultLayout layout = new RelativeCoordinateLayout();
    /**父元素*/
    @Nullable
    public Widget parent = null;
    /**所有子元素*/
    public ArrayList<Widget> children = new ArrayList<>();


    /**提示文本框内容*/
    public String tooltip = "";
    /**字符串ID 如果设置可用于遍历查找元素位置*/
    public String identifierID = "MISSING";


    public Widget() {
        registerTween();
        if (fontRenderer == null) {
            Minecraft mc = Minecraft.getMinecraft();
            fontRenderer = new ReplaceFontRender(mc.gameSettings, new ResourceLocation("textures/font/ascii.png"), mc.renderEngine, true);
            fontRenderer.setCharSize(18f);
        }
    }

    public Widget(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**递归调用，把子集的应用完最后应用自身*/
    public void applyLayout() {
        // 第一遍用于组件大小整理
        for (Widget child : children) {
            child.applyLayout();
        }
        layout.applyLayout(this);
        for (Widget child : children) {
            child.applyLayout();
        }
        layout.applyLayout(this);
    }

    public void draw() {
        drawSelf();

        debugDraw();

        for (Widget child : children) child.draw();
    }

    public void drawSelf() {

    }

    public void debugDraw() {
        if (!Config.useDebug) return;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);

        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        // GL11.glDisable(GL11.GL_FOG);
        // GL11.glDisable(GL11.GL_LIGHTING);
        // GL11.glDisable(GL11.GL_ALPHA_TEST);
        // GL11.glDisable(GL11.GL_BLEND);

        GL11.glLineWidth(1.0f);
        GL11.glColor4f(1, 0, 0, 1);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex3f(x, y, 0);
        GL11.glVertex3f(x + width, y, 0);
        GL11.glVertex3f(x + width, y + height, 0);
        GL11.glVertex3f(x, y + height, 0);
        GL11.glEnd();

        // GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glPopAttrib();
    }

    public boolean isMouseInBounds(float mouseX, float mouseY) {
        return (mouseX >= x && mouseX <= x + width) && (mouseY >= y && mouseY <= y + height);
    }

    /**任意时刻只要鼠标在移动就触发*/
    public void onMouseMovingPrivate(float x, float y, Set<Integer> clicked, Set<Integer> hold) {
        onMouseMoving(x,y, clicked, hold);
        for (Widget child : children) {
            child.onMouseMovingPrivate(x,y, clicked, hold);
        }
    }

    /**在组件内触发*/
    public void onHoverPrivate(float x, float y) {
        onHover(x,y);
        for (Widget child : children) {
            if (child.isMouseInBounds(x, y))
                child.onHoverPrivate(x, y);
            else
                child.onLeavePrivate(x,y);
        }
    }

    /**不在组件内触发事件*/
    public void onLeavePrivate(float x, float y) {
        onLeave(x,y);
        for (Widget child : children) {
            if (!child.isMouseInBounds(x, y))
                child.onLeavePrivate(x, y);
        }
    }

    /**被按下事件*/
    public void onPressPrivate(float x, float y, int buttonID) {
        onPress(x, y, buttonID);
        for (Widget child : children) {
            if (child.isMouseInBounds(x, y))
                child.onPressPrivate(x, y, buttonID);
        }
    }

    /**鼠标按下但不在组件内*/
    public void onPressNotInBoundsPrivate(float x, float y, int buttonID) {
        for (Widget child : children) {
            child.onPressNotInBoundsPrivate(x, y, buttonID);
        }
        if (!isMouseInBounds(x,y)) {
            onPressNotInBounds(x,y,buttonID);
        }
    }

    /**在组件内释放鼠标按键*/
    public void onReleasePrivate(float x, float y, int buttonID) {
        onRelease(x,y,buttonID);
        for (Widget child : children) {
            if (child.isMouseInBounds(x, y))
                child.onReleasePrivate(x, y, buttonID);
        }
    }

    /**拖拽事件*/
    public void onDragPrivate(float newX, float newY, Set<Integer> clicked) {
        onDragged(newX, newY, clicked);
        for (Widget child : children) {
            if (child.isMouseInBounds(newX,newY)) {
                child.onDragPrivate(newX,newY,clicked);
            }
        }
    }

    /**滚轮事件*/
    public void onWheelPrivate(float x, float y, int dWheel) {
        onWheel(x,y,dWheel);
        for (Widget child : children) {
            if (child.isMouseInBounds(x,y)) {
                child.onWheelPrivate(x,y,dWheel);
            }
        }
    }


    /**键盘键入事件*/
    public void onTypePrivate(char typeChar, int code) {
        onType(typeChar, code);
        for (Widget child : children) {
            child.onTypePrivate(typeChar, code);
        }
    }


    public void onHover(float x, float y) {}

    public void onLeave(float x, float y) {}

    public void onPress(float x, float y, int buttonID) {}

    public void onPressNotInBounds(float x, float y, int buttonID) {}

    public void onRelease(float x, float y, int buttonID) {}

    public void onDragged(float newX, float newY, Set<Integer> clicked) {}

    public void onMouseMoving(float x, float y, Set<Integer> clicked, Set<Integer> hold) {}

    public void onWheel(float x, float y, int dWheel) {}

    public void onType(char typeChar, int code) {}

    public Consumer<Vector2f> resizeCallback = (vec2) -> {
        // 提供默认行为
        width = vec2.x;
        height = vec2.y;
    };
    public void onResize(float width, float height) {
        resizeCallback.accept(new Vector2f(width,height));
    }
    public <T extends Widget> T setResizeCallback(Consumer<Vector2f> resizeCallback) {
        this.resizeCallback = resizeCallback;
        return (T) this;
    }

    public Vector2f getParentSize() {
        if (parent == null) return new Vector2f(Display.getWidth(), Display.getHeight());
        return new Vector2f(parent.width, parent.height);
    }

    public <T extends Widget> T addChild(Widget widget) {
        children.add(widget);
        widget.parent = this;
        // 触发子组件回调 子组件自动调整一些设置
        widget.onAdd(this);
        return (T) this;
    }

    /**
     * 将子组件插入到 children 列表的指定索引位置。
     *
     * @param index 要插入的位置的索引。
     * @param widget 要添加的子组件。
     * @return 当前 Widget 实例 (this)，方便进行链式调用。
     */
    public  <T extends Widget> T addChild(int index, Widget widget) {
        // 确保索引有效
        if (index < 0 || index > children.size()) {
            throw new IndexOutOfBoundsException("Invalid index: " + index);
        }

        // 将子组件插入到指定位置
        children.add(index, widget);

        // 设置子组件的父组件
        widget.parent = this;

        // 触发子组件回调，子组件自动调整一些设置
        widget.onAdd(this);

        return (T) this;
    }

    /**当自己被添加到某个组件中时触发*/
    public void onAdd(Widget parent) {
        // 1.对齐坐标
        x = parent.x;
        y = parent.y;
        // 2.限制大小
        // width = Math.min(width, parent.width);
        // height = Math.min(height, parent.height);
    }

    public <T extends Widget> T setLayout(DefaultLayout layout) {
        this.layout = layout;
        return (T) this;
    }

    public <T extends Widget> T setSize(float width, float height) {
        this.width = width;
        this.height = height;
        return (T) this;
    }

    public <T extends Widget> T setPos(float x, float y) {
        this.x = x;
        this.y = y;
        return (T) this;
    }

    public <T extends Widget> T setLocalPos(float localX, float localY) {
        this.localX = localX;
        this.localY = localY;
        return (T) this;
    }

    public <T extends Widget> T setTooltip(String tooltip) {
        this.tooltip = tooltip != null ? tooltip : "";
        return (T) this;
    }

    /**使用-1代表不限制*/
    public <T extends Widget> T setPerfectSize(float width, float height) {
        this.perfectWidth = width;
        this.perfectHeight = height;
        return (T) this;
    }

    /**根据传入的组件最佳大小调整自身 多用于自适应子组件*/
    public <T extends Widget> T setPerfectHeight(Collection<Widget> widgets) {
        float maxHeight = 0;
        for (Widget widget : widgets) {
            maxHeight = Math.max(maxHeight, widget.perfectHeight);
        }
        perfectHeight = maxHeight + insideMargins*2;
        return (T) this;
    }

    public <T extends Widget> T setPerfectWidth(Collection<Widget> widgets) {
        float maxWidth = 0;
        for (Widget widget : widgets) {
            maxWidth = Math.max(maxWidth, widget.perfectWidth);
        }
        perfectWidth = maxWidth + insideMargins*2;
        return (T) this;
    }

    /**
     * @param x 鼠标坐标
     * @param y 鼠标坐标
     * */
    public void renderTooltip(float x, float y) {
        if (tooltip.isEmpty()) return;
        GL11.glPushMatrix();
        GL11.glTranslatef(0,0,100);
        // GL11.glDisable(GL11.GL_DEPTH_TEST);
        y = y + 10;

        // --- 字体测量和分行逻辑 ---

        // 获取文字宽度并设置字符大小
        ((ReplaceFontRender)fontRenderer).setCharSize(32);
        // float stringWidth = fontRenderer.getStringWidth(tooltip); // 原始代码中未使用

        // 计算可用宽度
        float displayWidth = Display.getWidth();
        float leftWidth = x;
        float rightWidth = displayWidth - x;

        // 选择在宽的一侧渲染。如果一样优先右侧。
        // 注意：原始代码的分行逻辑 (listFormattedStringToWidth) 似乎直接使用了 rightWidth 作为限制宽度，
        // 但注释说"选择在宽的一侧渲染"。为了保持功能，我们先计算出实际渲染的 X 坐标和限制宽度。

        float renderX;
        float maxWidth;

        if (rightWidth >= leftWidth) {
            // 优先右侧或右侧更宽
            renderX = x;
            maxWidth = rightWidth;
        } else {
            // 左侧更宽
            // 渲染时需要从 x 往左偏移，但 listFormattedStringToWidth 需要一个最大宽度限制
            maxWidth = leftWidth;
            renderX = x; // 暂定为 x，实际绘制时再偏移
        }

        // 钳制字体宽度分行
        // 注意：listFormattedStringToWidth 返回的是 List<String>
        // 在实际渲染时，我们需要考虑左侧渲染时需要调整 renderX 和背景框的起始位置
        List<String> strings = fontRenderer.listFormattedStringToWidth(tooltip, (int) maxWidth);

        if (strings.isEmpty()) {
            return; // 没有文本，直接返回
        }

        // 计算总高度和最长字符串的宽度
        float lineHeight = 32 + 4; // 假设 setCharSize(32) 设置了行高为 32
        float totalHeight = strings.size() * lineHeight;
        float maxStringWidth = 0;
        for (String string : strings) {
            float width = fontRenderer.getStringWidth(string);
            if (width > maxStringWidth) {
                maxStringWidth = width;
            }
        }

        // 考虑边距
        float padding = 4.0f;
        float boxWidth = maxStringWidth + padding * 2;
        float boxHeight = totalHeight + padding * 2;

        // 重新确定最终的渲染坐标 (renderX) 和背景框的左上角坐标 (boxLeft, boxTop)
        float boxLeft, boxTop = y - padding; // 背景框的 top 坐标
        float textX; // 文本绘制的 X 坐标

        // 检查是否需要向左渲染
        if (renderX + boxWidth > displayWidth) {
            // 右侧空间不足，向左渲染
            boxLeft = x - boxWidth;
            textX = boxLeft + padding;
        } else {
            // 向右渲染
            boxLeft = x;
            textX = x + padding;
        }


        // --- 绘制背景框 (半透明黑色) ---

        // 启用混合 (Alpha 混合)
        GL11.glEnable(GL11.GL_BLEND);
        // GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // 禁用纹理，以便绘制纯色
        GL11.glDisable(GL11.GL_TEXTURE_2D);

        // 设置颜色：半透明黑色 (RGBA: 0.0f, 0.0f, 0.0f, 0.75f - 0.9f 左右)
        // 假设使用 0xCC000000 的颜色值，Alpha 约为 0.8
        float alpha = 0.8f;
        GL11.glColor4f(0.0f, 0.0f, 0.0f, alpha);

        // 绘制矩形 (使用 GL_QUADS)
        GL11.glBegin(GL11.GL_QUADS);
        {
            // 左上角
            GL11.glVertex2f(boxLeft, boxTop);
            // 右上角
            GL11.glVertex2f(boxLeft + boxWidth, boxTop);
            // 右下角
            GL11.glVertex2f(boxLeft + boxWidth, boxTop + boxHeight);
            // 左下角
            GL11.glVertex2f(boxLeft, boxTop + boxHeight);
        }
        GL11.glEnd();

        // 重新启用纹理，为接下来的文字渲染做准备
        // GL11.glEnable(GL11.GL_TEXTURE_2D);

        // --- 绘制文字 ---

        float currentY = y + padding;
        int color = 0xffffffff; // 白色文本

        for (String string : strings) {
            fontRenderer.drawString(string, (int) textX, (int) currentY, color);
            currentY += lineHeight;
        }

        // 恢复状态 (可选，取决于您的渲染环境，但保持良好习惯)
        // GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glPopMatrix();
    }

    public void registerTween() {}

    public static void updateTween() {
        long curTime = System.nanoTime();
        long diff = curTime - LAST_TIME;
        LAST_TIME = curTime;
        TWEEN_MANAGER.update((float) diff /1_000_000);
    }


    public static IntBuffer scissorBuffer = BufferUtils.createIntBuffer(16);
    public int scissorX, scissorY, scissorW, scissorH;
    /**使用父组件区域进行裁切*/
    public void startScissor() {
        if (parent != null) {
            scissorBuffer.clear();
            // 备份前一个
            GL11.glGetInteger(GL11.GL_SCISSOR_BOX, scissorBuffer);
            scissorX = scissorBuffer.get(0);
            scissorY = scissorBuffer.get(1);
            scissorW = scissorBuffer.get(2);
            scissorH = scissorBuffer.get(3);
            // 开启裁切测试
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            // 设置裁切范围 设置为父组件区域
            GL11.glScissor((int) parent.x, (int) (Display.getHeight() - parent.y - parent.height), (int) parent.width, (int) parent.height);
        }
    }

    public void endScissor() {
        if (parent != null) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            // 还原之前的
            GL11.glScissor(scissorX,scissorY,scissorW,scissorH);
        }
    }
}
