package club.heiqi.uilib.ui.widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.ui.diagnostic.UiPerformanceMonitor;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.event.UiTextInputEvent;
import club.heiqi.uilib.ui.render.UiRenderContext;

/**
 * UI 组件基础节点。
 *
 * <p>当前主线仅支持相对布局：组件的 `x` / `y` 始终相对于父组件左上角。</p>
 */
public class Widget {

    /**
     * 子树命中测试继承的结构裁剪状态。
     */
    protected static final class StructuralHitClip {

        private final int[] clipRect;
        private final List<RoundedRectClipRegion> roundedClipRegions;

        private StructuralHitClip(int[] clipRect, List<RoundedRectClipRegion> roundedClipRegions) {
            this.clipRect = clipRect;
            this.roundedClipRegions = roundedClipRegions;
        }

        /**
         * 判断坐标是否仍位于当前结构裁剪内。
         *
         * @param mouseX 鼠标 X
         * @param mouseY 鼠标 Y
         * @return 是否位于裁剪区域内
         */
        private boolean contains(int mouseX, int mouseY) {
            if (clipRect != null && !containsInRect(mouseX, mouseY, clipRect)) {
                return false;
            }
            for (RoundedRectClipRegion roundedClipRegion : roundedClipRegions) {
                if (!roundedClipRegion.contains(mouseX, mouseY)) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * 仅用于命中测试的圆角矩形裁剪描述。
     */
    protected static final class RoundedRectClipRegion {

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final int cornerRadius;

        private RoundedRectClipRegion(int left, int top, int right, int bottom, int cornerRadius) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.cornerRadius = cornerRadius;
        }

        /**
         * 判断坐标是否落在圆角矩形内。
         *
         * @param mouseX 鼠标 X
         * @param mouseY 鼠标 Y
         * @return 是否位于圆角矩形内
         */
        private boolean contains(int mouseX, int mouseY) {
            return containsInRoundedRect(mouseX, mouseY, left, top, right, bottom, cornerRadius);
        }
    }

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
        context.pushClip(clipRect[0], clipRect[1], clipRect[2], clipRect[3], getChildClipCornerRadius());
        return 1;
    }

    /**
     * 在默认子树 clip 之前，允许容器追加额外的结构性视觉裁剪。
     *
     * <p>该钩子用于表达“外层 rounded shell + 内层矩形 viewport”这类复合结构裁剪，
     * 避免把多个几何层级硬挤进同一份 `rect + radius` 描述中。</p>
     *
     * @param context 渲染上下文
     * @return 实际压入的裁剪层数
     */
    protected int pushAdditionalChildVisualClips(UiRenderContext context) {
        return 0;
    }

    /**
     * 返回当前容器对子树结构裁剪的圆角半径。
     *
     * <p>默认返回 0，表示仅使用矩形裁剪；如需显式 rounded structural clip，
     * 应由专用结构容器子类覆盖或配置该钩子，而不是回退到 surface 外观驱动。</p>
     *
     * @return 子树结构裁剪圆角半径
     */
    protected int getChildClipCornerRadius() {
        return 0;
    }

    protected final void popVisualClips(UiRenderContext context, int clipDepth) {
        while (clipDepth > 0) {
            context.popClip();
            clipDepth--;
        }
    }

    private int pushVisualClips(UiRenderContext context) {
        int clipDepth = pushAdditionalChildVisualClips(context);
        clipDepth += pushChildVisualClip(context);
        return clipDepth;
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

    private Widget findWidgetAt(int mouseX, int mouseY, StructuralHitClip inheritedHitClip) {
        UiPerformanceMonitor.getInstance().recordHitTestVisit();
        if (inheritedHitClip != null && !inheritedHitClip.contains(mouseX, mouseY)) {
            return null;
        }
        if (!visible || !enabled || !contains(mouseX, mouseY)) {
            return null;
        }

        StructuralHitClip childHitClip = inheritedHitClip;
        if (clipHitTest) {
            childHitClip = appendAdditionalChildHitClips(inheritedHitClip);
            childHitClip = appendStructuralHitClip(childHitClip, getChildClipRect(), getChildClipCornerRadius());
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
     * 计算传递给子树的命中测试裁剪状态。
     *
     * <p>这里显式保留 rounded structural clip 语义，但只影响对子树的命中裁剪，
     * 不改变当前容器自身是否返回命中的既有规则。</p>
     *
     * @param inheritedHitClip 继承到当前节点的裁剪状态
     * @return 传递给子节点的裁剪状态
     */
    protected StructuralHitClip appendAdditionalChildHitClips(StructuralHitClip inheritedHitClip) {
        return inheritedHitClip;
    }

    /**
     * 在现有结构裁剪状态后追加一层新的矩形/圆角矩形裁剪。
     *
     * @param inheritedHitClip 继承到当前节点的裁剪状态
     * @param childClipRect 追加的裁剪矩形
     * @param cornerRadius 圆角半径；为 0 时表示纯矩形
     * @return 更新后的裁剪状态
     */
    protected final StructuralHitClip appendStructuralHitClip(StructuralHitClip inheritedHitClip, int[] childClipRect,
            int cornerRadius) {
        int[] inheritedClipRect = inheritedHitClip == null ? null : inheritedHitClip.clipRect;
        int[] resolvedClipRect = intersectRect(inheritedClipRect, childClipRect);

        List<RoundedRectClipRegion> roundedClipRegions = new ArrayList<RoundedRectClipRegion>();
        if (inheritedHitClip != null) {
            roundedClipRegions.addAll(inheritedHitClip.roundedClipRegions);
        }

        int resolvedCornerRadius = clampCornerRadius(resolvedClipRect, cornerRadius);
        if (resolvedCornerRadius > 0) {
            roundedClipRegions.add(new RoundedRectClipRegion(resolvedClipRect[0], resolvedClipRect[1], resolvedClipRect[2],
                    resolvedClipRect[3], resolvedCornerRadius));
        }
        return new StructuralHitClip(resolvedClipRect, roundedClipRegions);
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

    /**
     * 处理通过全局焦点遍历进入当前组件的时机。
     *
     * @param reverse 是否为反向遍历
     */
    public void onFocusTraversalEntered(boolean reverse) {}

    /**
     * 处理当前组件内部的焦点遍历请求。
     *
     * @param reverse 是否为反向遍历
     * @return 是否已在组件内部消费该焦点遍历请求
     */
    public boolean onFocusTraversal(boolean reverse) {
        return false;
    }

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

    public int getLayoutVersion() {
        return layoutVersion;
    }

    /**
     * 应用布局引擎解析后的最终边界。
     *
     * <p>该方法仅供屏幕宿主在内部写入最终布局结果，
     * 页面作者应通过文档壳组合声明尺寸与位置。</p>
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
     * <p>自定义控件只要有任何内部状态会影响实际布局结果，就必须在状态变化后调用本方法。</p>
     */
    protected void requestLayout() {
        layoutVersion++;
        if (parent != null) {
            parent.requestLayout();
        }
    }

    private static boolean containsInRect(int mouseX, int mouseY, int[] rect) {
        return mouseX >= rect[0] && mouseX < rect[2] && mouseY >= rect[1] && mouseY < rect[3];
    }

    private static int[] intersectRect(int[] first, int[] second) {
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

    /**
     * 以像素中心判断命中点是否位于圆角矩形内。
     */
    private static boolean containsInRoundedRect(int mouseX, int mouseY, int left, int top, int right, int bottom,
            int cornerRadius) {
        if (!containsInRect(mouseX, mouseY, new int[] { left, top, right, bottom })) {
            return false;
        }
        if (cornerRadius <= 0) {
            return true;
        }

        int innerLeft = left + cornerRadius;
        int innerRight = right - cornerRadius;
        int innerTop = top + cornerRadius;
        int innerBottom = bottom - cornerRadius;
        if (mouseX >= innerLeft && mouseX < innerRight) {
            return true;
        }
        if (mouseY >= innerTop && mouseY < innerBottom) {
            return true;
        }

        float pointX = mouseX + 0.5F;
        float pointY = mouseY + 0.5F;
        float centerX = pointX < innerLeft ? innerLeft : innerRight;
        float centerY = pointY < innerTop ? innerTop : innerBottom;
        float deltaX = pointX - centerX;
        float deltaY = pointY - centerY;
        return deltaX * deltaX + deltaY * deltaY <= cornerRadius * cornerRadius;
    }

    /**
     * 将圆角半径收敛到当前裁剪矩形可承受的范围内。
     */
    private static int clampCornerRadius(int[] clipRect, int cornerRadius) {
        if (clipRect == null) {
            return 0;
        }
        int width = Math.max(0, clipRect[2] - clipRect[0]);
        int height = Math.max(0, clipRect[3] - clipRect[1]);
        return Math.max(0, Math.min(cornerRadius, Math.min(width, height) / 2));
    }
}
