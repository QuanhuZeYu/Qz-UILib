package club.heiqi.uilib.ui.widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.event.UiTextInputEvent;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.render.UiRenderContext;

/**
 * UI 组件基础节点。
 *
 * <p>当前主线仅支持相对布局：组件的 `x` / `y` 始终相对于父组件左上角。</p>
 */
public class Widget {

    private final List<Widget> children = new ArrayList<Widget>();

    private Widget parent;
    private int x;
    private int y;
    private int width;
    private int height;
    private boolean visible = true;
    private boolean enabled = true;
    private boolean clipChildren;
    private boolean clipHitTest;
    private UiLayoutSpec layoutSpec;

    /**
     * 绘制当前组件与其子组件。
     *
     * @param context 渲染上下文
     */
    public void render(UiRenderContext context) {
        if (!visible) {
            return;
        }
        drawSelf(context);
        boolean clipping = clipChildren;
        if (clipping) {
            int[] clipRect = getChildClipRect();
            context.pushClip(clipRect[0], clipRect[1], clipRect[2], clipRect[3]);
        }
        try {
            for (Widget child : children) {
                child.render(context);
            }
        } finally {
            if (clipping) {
                context.popClip();
            }
        }
    }

    /**
     * 绘制当前组件自身。
     *
     * @param context 渲染上下文
     */
    protected void drawSelf(UiRenderContext context) {}

    protected int[] getChildClipRect() {
        return new int[] { getAbsoluteX(), getAbsoluteY(), getAbsoluteX() + getWidth(), getAbsoluteY() + getHeight() };
    }

    /**
     * 递归查找命中的最深层组件。
     *
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     * @return 命中的组件，未命中返回 null
     */
    public Widget findWidgetAt(int mouseX, int mouseY) {
        return findWidgetAt(mouseX, mouseY, null);
    }

    private Widget findWidgetAt(int mouseX, int mouseY, int[] inheritedHitClip) {
        if (inheritedHitClip != null && !containsInRect(mouseX, mouseY, inheritedHitClip)) {
            return null;
        }
        if (!visible || !enabled || !contains(mouseX, mouseY)) {
            return null;
        }

        int[] childHitClip = inheritedHitClip;
        if (clipHitTest) {
            childHitClip = intersectRect(inheritedHitClip, getChildClipRect());
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            Widget child = children.get(i);
            Widget hit = child.findWidgetAt(mouseX, mouseY, childHitClip);
            if (hit != null) {
                return hit;
            }
        }
        return this;
    }

    /**
     * 添加子组件。
     *
     * @param child 子组件
     * @return 当前组件
     */
    public Widget addChild(Widget child) {
        if (child == null) {
            return this;
        }
        child.parent = this;
        children.add(child);
        return this;
    }

    /**
     * 判断坐标是否位于组件范围内。
     *
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     * @return 是否命中
     */
    public boolean contains(int mouseX, int mouseY) {
        int absoluteX = getAbsoluteX();
        int absoluteY = getAbsoluteY();
        return mouseX >= absoluteX && mouseX < absoluteX + width && mouseY >= absoluteY && mouseY < absoluteY + height;
    }

    public void onMouseEnter() {}

    public void onMouseLeave() {}

    public void onMouseMove(UiMouseEvent event) {}

    public void onMouseDown(UiMouseEvent event) {}

    public void onMouseUp(UiMouseEvent event) {}

    /**
     * 处理滚轮事件。
     *
     * @param event 鼠标滚轮事件
     * @return 是否已消费该事件
     */
    public boolean onMouseScroll(UiMouseEvent event) {
        return false;
    }

    public void onKeyEvent(UiKeyEvent event) {}

    public void onTextInput(UiTextInputEvent event) {}

    public void onFocusChanged(boolean focused) {}

    public boolean isFocusable() {
        return false;
    }

    public List<Widget> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public Widget getParent() {
        return parent;
    }

    public int getX() {
        return x;
    }

    /**
     * 获取组件在屏幕中的绝对 X。
     *
     * @return 绝对 X
     */
    public int getAbsoluteX() {
        return parent == null ? x : parent.getAbsoluteX() + x;
    }

    public int getY() {
        return y;
    }

    /**
     * 获取组件在屏幕中的绝对 Y。
     *
     * @return 绝对 Y
     */
    public int getAbsoluteY() {
        return parent == null ? y : parent.getAbsoluteY() + y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isClipChildren() {
        return clipChildren;
    }

    public boolean isClipHitTest() {
        return clipHitTest;
    }

    public UiLayoutSpec getLayoutSpec() {
        return layoutSpec;
    }

    /**
     * 获取组件期望宽度。
     *
     * @return 期望宽度
     */
    public int getPreferredWidth() {
        return width;
    }

    /**
     * 获取组件期望高度。
     *
     * @return 期望高度
     */
    public int getPreferredHeight() {
        return height;
    }

    /**
     * 在给定宽度约束下获取期望高度。
     *
     * @param width 可用宽度
     * @return 期望高度
     */
    public int getPreferredHeightForWidth(int width) {
        return getPreferredHeight();
    }

    /**
     * 设置组件位置与尺寸。
     *
     * @param x 左上角 X
     * @param y 左上角 Y
     * @param width 宽度
     * @param height 高度
     * @return 当前组件
     */
    public Widget setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        return this;
    }

    /**
     * 仅调整组件位置。
     *
     * @param x 相对父组件的 X
     * @param y 相对父组件的 Y
     * @return 当前组件
     */
    public Widget setPosition(int x, int y) {
        this.x = x;
        this.y = y;
        return this;
    }

    /**
     * 仅调整组件尺寸。
     *
     * @param width 宽度
     * @param height 高度
     * @return 当前组件
     */
    public Widget setSize(int width, int height) {
        this.width = width;
        this.height = height;
        return this;
    }

    public Widget setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public Widget setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public Widget setClipChildren(boolean clipChildren) {
        this.clipChildren = clipChildren;
        return this;
    }

    public Widget setClipHitTest(boolean clipHitTest) {
        this.clipHitTest = clipHitTest;
        return this;
    }

    public Widget setLayoutSpec(UiLayoutSpec layoutSpec) {
        this.layoutSpec = layoutSpec;
        return this;
    }

    private boolean containsInRect(int mouseX, int mouseY, int[] rect) {
        return mouseX >= rect[0] && mouseX < rect[2] && mouseY >= rect[1] && mouseY < rect[3];
    }

    private int[] intersectRect(int[] first, int[] second) {
        if (second == null) {
            return first;
        }
        if (first == null) {
            return new int[] { second[0], second[1], second[2], second[3] };
        }
        int left = Math.max(first[0], second[0]);
        int top = Math.max(first[1], second[1]);
        int right = Math.min(first[2], second[2]);
        int bottom = Math.min(first[3], second[3]);
        if (right < left) {
            right = left;
        }
        if (bottom < top) {
            bottom = top;
        }
        return new int[] { left, top, right, bottom };
    }
}
