package club.heiqi.qz_uilib.widget;

import club.heiqi.qz_uilib.Config;
import club.heiqi.qz_uilib.widget.layout.VerticalLayout;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

public class ListWidget extends Widget {

    public ListWidget() {
        super();
        // 设置为垂直布局
        this.layout = new VerticalLayout();
        this.setPerfectSize(-1,-1);  // 列表组件默认使用自适应策略
    }

    @Override
    public void draw() {
        debugDraw();
        // 所有内容都在裁切测试中绘制
        startScissor();

        for (Widget child : children) {
            child.draw();
        }

        endScissor();
    }

    @Override
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
        GL11.glColor4f(0.2f, 0.8f, 1.0f, 1);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex3f(x, y, 0);
        GL11.glVertex3f(x + width, y, 0);
        GL11.glVertex3f(x + width, y + height, 0);
        GL11.glVertex3f(x, y + height, 0);
        GL11.glEnd();

        GL11.glPopAttrib();
    }

    @Override
    public void onWheel(float x, float y, int dWheel) {
        super.onWheel(x, y, dWheel);
        int move = (int) (dWheel*Config.wheelCount);
        // 列表元素可能出现空的情况
        if (!children.isEmpty()) {
            Widget first = children.get(0);
            Widget last = children.get(children.size() - 1);
            if (first.y + first.height + move > height || last.y + move < 0) return;
            for (Widget child : children) {
                child.localY += move;
            }
        }
    }

    /**使用自身区域进行裁切*/
    @Override
    public void startScissor() {
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
        GL11.glScissor((int) x, (int) (Display.getHeight() - y - height), (int) width, (int) height);
    }

    @Override
    public void endScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        // 还原之前的
        GL11.glScissor(scissorX,scissorY,scissorW,scissorH);
    }
}
