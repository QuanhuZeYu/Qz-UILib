package club.heiqi.qz_uilib.widget;

import aurelienribon.tweenengine.Tween;
import aurelienribon.tweenengine.TweenManager;
import club.heiqi.qz_fontrender.fontsystem.impl.ReplaceFontRender;
import club.heiqi.qz_uilib.Config;
import club.heiqi.qz_uilib.widget.layout.DefaultLayout;
import club.heiqi.qz_uilib.widget.layout.RelativeCoordinateLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Set;

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
    public static FontRenderer fontRenderer;
    /**绝对坐标X*/
    public float x = 0, y = 0;
    /**相对坐标 相对坐标起始点在绝对坐标+内边距后的位置 即默认左上角*/
    public float localX = 0, localY = 0;

    /**当前大小*/
    public float width = 512;
    /**当前大小*/
    public float height = 512;
    /**最小大小*/
    public float miniumWidth = 1;
    /**最小大小*/
    public float miniumHeight = 1;
    /**期望大小，用于布局器选取合适大小; -1表示不限制可被随意拉伸*/
    public float perfectWidth = -1, perfectHeight = -1;

    /**外边距*/
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


    public Widget() {
        registerTween();
        if (fontRenderer == null) {
            Minecraft mc = Minecraft.getMinecraft();
            fontRenderer = new ReplaceFontRender(mc.gameSettings, mc.fontRenderer.locationFontTexture, mc.renderEngine, true);
            ((ReplaceFontRender)fontRenderer).setCharSize(18f);
        }
    }

    public Widget(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }


    public void draw() {
        layout.applyLayout(this);
        drawSelf();

        debugDraw();

        for (Widget child : children) child.draw();
    }

    public void drawSelf() {

    }

    public void debugDraw() {
        if (!Config.usDebug) return;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);

        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        // GL11.glDisable(GL11.GL_FOG);
        // GL11.glDisable(GL11.GL_LIGHTING);
        // GL11.glDisable(GL11.GL_ALPHA_TEST);
        // GL11.glDisable(GL11.GL_BLEND);

        GL11.glLineWidth(5.0f);
        GL11.glColor4f(1, 0, 0, 1);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex3f(x, y, 0);
        GL11.glVertex3f(x + width, y, 0);
        GL11.glVertex3f(x + width, y + height, 0);
        GL11.glVertex3f(x, y + height, 0);
        GL11.glEnd();

        GL11.glPopAttrib();
    }

    public boolean isMouseInBounds(float mouseX, float mouseY) {
        return (mouseX >= x && mouseX <= x + width) && (mouseY >= y && mouseY <= y + height);
    }

    public void onMouseMovingPrivate(float x, float y, Set<Integer> clicked, Set<Integer> hold) {
        onMouseMoving(x,y, clicked, hold);
        for (Widget child : children) {
            child.onMouseMovingPrivate(x,y, clicked, hold);
        }
    }

    public void onHoverPrivate(float x, float y) {
        onHover(x,y);
        for (Widget child : children) {
            if (child.isMouseInBounds(x, y))
                child.onHoverPrivate(x, y);
            else
                child.onLeavePrivate(x,y);
        }
    }

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


    public void onHover(float x, float y) {}

    public void onLeave(float x, float y) {}

    public void onPress(float x, float y, int buttonID) {}

    public void onRelease(float x, float y, int buttonID) {}

    public void onDragged(float newX, float newY, Set<Integer> clicked) {}

    public void onMouseMoving(float x, float y, Set<Integer> clicked, Set<Integer> hold) {}

    public void onWheel(float x, float y, int dWheel) {}

    public Vector2f getParentSize() {
        if (parent == null) return new Vector2f(Display.getWidth(), Display.getHeight());
        return new Vector2f(parent.width, parent.height);
    }

    public Widget addChild(Widget widget) {
        children.add(widget);
        widget.parent = this;
        return  this;
    }

    public Widget setLayout(DefaultLayout layout) {
        this.layout = layout;
        return  this;
    }

    public Widget setSize(float width, float height) {
        this.width = width;
        this.height = height;
        return this;
    }

    public Widget setPos(float x, float y) {
        this.x = x;
        this.y = y;
        return this;
    }

    public Widget setLocalPos(float localX, float localY) {
        this.localX = localX;
        this.localY = localY;
        return this;
    }

    /**使用-1代表不限制*/
    public Widget setPerfectSize(float width, float height) {
        this.perfectWidth = width;
        this.perfectHeight = height;
        return this;
    }

    public void registerTween() {}

    public static void updateTween() {
        long curTime = System.nanoTime();
        long diff = curTime - LAST_TIME;
        LAST_TIME = curTime;
        TWEEN_MANAGER.update((float) diff /1_000_000);
    }


    private static IntBuffer scissorBuffer = BufferUtils.createIntBuffer(16);
    private int scissorX, scissorY, scissorW, scissorH;
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
