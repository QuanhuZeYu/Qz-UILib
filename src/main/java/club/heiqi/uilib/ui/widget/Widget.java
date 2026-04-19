package club.heiqi.uilib.ui.widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.ui.diagnostic.UiPerformanceMonitor;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.event.UiTextInputEvent;
import club.heiqi.uilib.ui.layout.UiConstraints;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiMeasureResult;
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
    private int layoutVersion;

    /**
     * 绘制当前组件与其子组件。
     *
     * @param context 渲染上下文
     */
    public void render(UiRenderContext context) {
        if (!visible) {
            return;
        }
        UiPerformanceMonitor performanceMonitor = UiPerformanceMonitor.getInstance();
        performanceMonitor.enterWidget(this);
        try {
            drawSelf(context);
            int clipDepth = pushVisualClips(context);
            try {
                for (Widget child : children) {
                    child.render(context);
                }
            } finally {
                popVisualClips(context, clipDepth);
            }
        } finally {
            performanceMonitor.exitWidget(this);
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

    protected final int pushChildVisualClip(UiRenderContext context) {
        if (!clipChildren) {
            return 0;
        }

        int[] clipRect = getChildClipRect();
        context.pushClip(clipRect[0], clipRect[1], clipRect[2], clipRect[3]);
        return 1;
    }

    protected final void popVisualClips(UiRenderContext context, int clipDepth) {
        while (clipDepth > 0) {
            context.popClip();
            clipDepth--;
        }
    }

    private int pushVisualClips(UiRenderContext context) {
        return pushChildVisualClip(context);
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
        UiPerformanceMonitor.getInstance().recordHitTestVisit();
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
        WidgetBuildAttachmentTransaction.recordDirectAttachment(this, child);
        requestLayout();
        return this;
    }

    /**
     * 递归清空当前节点的全部子树。
     *
     * <p>该方法仅用于显式销毁整棵子树，
     * 屏幕宿主在 build attempt 失败时应使用挂接事务按直接边回滚，避免误拆复合控件骨架。</p>
     */
    public final void clearChildren() {
        if (children.isEmpty()) {
            return;
        }

        for (Widget child : children) {
            child.clearChildren();
            child.parent = null;
        }
        children.clear();
        requestLayout();
    }

    /**
     * 仅移除当前节点与目标子节点之间的一条直接挂接边。
     *
     * <p>该方法不会递归清理子树，供 build attempt 回滚按最小影响面撤销挂接关系。</p>
     */
    final void detachDirectChild(Widget child) {
        if (child == null || children.isEmpty()) {
            return;
        }

        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i) != child) {
                continue;
            }
            children.remove(i);
            if (child.parent == this) {
                child.parent = null;
            }
            requestLayout();
            return;
        }
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

    public int getLayoutVersion() {
        return layoutVersion;
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
     * 获取组件在 Div 容器中可压缩到的最小内容宽度。
     *
     * @return 最小内容宽度
     */
    public int getMinContentWidth() {
        return 0;
    }

    /**
     * 获取组件在 Div 容器中可压缩到的最小内容高度。
     *
     * @param width 当前宽度
     * @return 最小内容高度
     */
    public int getMinContentHeightForWidth(int width) {
        return getPreferredHeightForWidth(width);
    }

    /**
     * 在给定约束下测量组件尺寸。
     *
     * @param constraints 父容器传入的约束
     * @return 测量结果
     */
    public UiMeasureResult measure(UiConstraints constraints) {
        UiConstraints effectiveConstraints = constraints == null ? UiConstraints.unbounded() : constraints;
        int measuredWidth = effectiveConstraints.constrainWidth(getPreferredWidth());
        int measuredHeight = effectiveConstraints.constrainHeight(getPreferredHeightForWidth(measuredWidth));
        return new UiMeasureResult(measuredWidth, measuredHeight);
    }

    /**
     * 应用布局引擎解析后的最终边界。
     *
     * <p>该方法仅供布局容器与屏幕宿主在内部写入最终布局结果，
     * 页面作者应通过 `UiLayoutSpec`、容器流式布局和文档壳组合声明尺寸与位置。</p>
     *
     * @param x 左上角 X
     * @param y 左上角 Y
     * @param width 宽度
     * @param height 高度
     */
    public final void applyLayoutBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public Widget setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public Widget setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * 应用子树裁剪开关。
     *
     * <p>该方法属于容器内部实现细节，页面作者应优先通过 overflow 语义表达裁剪行为。</p>
     */
    protected final void applyChildClipEnabled(boolean clipChildren) {
        this.clipChildren = clipChildren;
    }

    /**
     * 应用命中测试裁剪开关。
     *
     * <p>该方法属于容器内部实现细节，页面作者不应直接操作底层命中裁剪标志。</p>
     */
    protected final void applyHitTestClipEnabled(boolean clipHitTest) {
        this.clipHitTest = clipHitTest;
    }

    public Widget setLayoutSpec(UiLayoutSpec layoutSpec) {
        this.layoutSpec = layoutSpec;
        requestLayout();
        return this;
    }

    /**
     * 标记当前节点及其全部子树的布局缓存失效。
     *
     * <p>该方法用于字体重载这类“运行时全局测量条件变化”的场景，
     * 让整棵已存活 UI 树在下一帧重新测量与布局。</p>
     */
    public final void invalidateLayoutTree() {
        layoutVersion++;
        for (Widget child : children) {
            child.invalidateLayoutTree();
        }
    }

    /**
     * 标记当前节点的测量与布局结果失效，并向父链传播。
     *
     * <p>自定义控件只要有任何内部状态会影响 `getPreferredWidth()`、`getPreferredHeight()`、
     * `measure(...)` 或实际子节点布局结果，就必须在状态变化后调用本方法。</p>
     */
    protected void requestLayout() {
        layoutVersion++;
        if (parent != null) {
            parent.requestLayout();
        }
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
