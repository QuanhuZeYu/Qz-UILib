package club.heiqi.qz_uilib.widget;

import club.heiqi.qz_uilib.widget.layout.DefaultLayout;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;

public class Widget {
    /**绝对坐标X*/
    public float globalX = 0;
    /**绝对坐标Y*/
    public float globalY = 0;

    /**当前大小*/
    public float width = 512;
    /**当前大小*/
    public float height = 512;
    /**期望大小*/
    public float targetWidth = 16;
    /**期望大小*/
    public float targetHeight = 16;
    /**最小大小*/
    public float miniumWidth = 1;
    /**最小大小*/
    public float miniumHeight = 1;

    /**伸缩权重*/
    public float stretchWeight = 1;
    /**外边距*/
    public float outMargins = 8;
    /**内边距*/
    public float insideMargins = 8;

    /**布局器*/
    public DefaultLayout layout = new DefaultLayout();
    /**所有子元素*/
    public ArrayList<Widget> children = new ArrayList<>();


    public void draw() {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        // 设置纯白色（RGBA）
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(globalX,globalY+height);//左下
            GL11.glVertex2f(globalX+width,globalY+height);//右下
            GL11.glVertex2f(globalX+width,globalY);//右上
            GL11.glVertex2f(globalX,globalY);//左上
        GL11.glEnd();

        GL11.glPopAttrib();

        for (Widget child : children) child.draw();
    }

    /**被点击事件*/
    public void onClicked(float x, float y, int buttonID) {

    }

    /**
     * 拖拽事件
     * @param newX 当前鼠标的绝对坐标
     * @param newY 当前鼠标的绝对坐标
     */
    public void onDrag(float newX, float newY) {

    }

    /**缩放事件*/
    public void onResize(float width, float height) {

    }
}
