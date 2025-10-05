package club.heiqi.qz_uilib.widget;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import java.util.Set;

public class DraggableWidget extends Widget {

    private float cacheX, cacheY;
    private boolean inDrag = false;
    @Override
    public void onDragPrivate(float newX, float newY, Set<Integer> clicked) {
        // 阻断向下传播 对于子组件不应感知到被拖拽 即相对父组件静止
        if (!inDrag) {
            inDrag = true;
            cacheX = newX;
            cacheY = newY;
        }
    }

    @Override
    public void onRelease(float x, float y, int buttonID) {
        super.onRelease(x, y, buttonID);
        inDrag = false;
    }

    @Override
    public void onMouseMoving(float x, float y, Set<Integer> clicked, Set<Integer> hold) {
        super.onMouseMoving(x, y, clicked, hold);
        if (hold.isEmpty()) inDrag = false;
    }

    private void drag(float newX, float newY) {
        if (inDrag) {
            // 如果超过1s没有收到拖拽事件自动取消
            float offsetX = newX - cacheX;
            float offsetY = newY - cacheY;
            this.cacheX = newX;
            this.cacheY = newY;
            // 位移组件的关键实现 该位移需要相对坐标布局器才可以使用
            this.localX = this.localX + offsetX;
            this.localY = this.localY + offsetY;

            // 钳制位置在父组件内部
            float parentWidth = parent==null ? Display.getWidth() : parent.width;
            float parentHeight = parent==null ? Display.getHeight() : parent.height;
            float parentInsideM = parent==null ? 0 : parent.insideMargins;
            localX = Math.min(Math.max(0, localX), parentWidth - parentInsideM - width);
            localY = Math.min(Math.max(0, localY), parentHeight - parentInsideM - height);
        }
    }

    @Override
    public void drawSelf() {
        drag(Mouse.getX(), Display.getHeight() - Mouse.getY());
        if (parent != null) {
            startScissor();

            GL11.glColor4f(1,1,1,1);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex3f(x,y,0);
            GL11.glVertex3f(x,y+height,0);
            GL11.glVertex3f(x+width,y+height,0);
            GL11.glVertex3f(x+width,y,0);
            GL11.glEnd();

            endScissor();
        }
    }
}
