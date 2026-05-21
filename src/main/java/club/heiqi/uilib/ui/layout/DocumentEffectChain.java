package club.heiqi.uilib.ui.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.style.cascade.ComputedStyle;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.values.UiTransform;

/**
 * 单个 HTML-like 布局盒的显式 clip / effect chain。
 *
 * <p>该模型集中描述 overflow clip、backdrop-filter、paint context 与 stacking boundary 的关系，
 * 让 paint command、hit-test 和滚动命中复用同一套判定，避免多个模块各自维护隐式顺序。</p>
 */
public final class DocumentEffectChain {

    /**
     * 元素级 backdrop blur 半径上限。
     */
    public static final int MAX_BACKDROP_BLUR_RADIUS = 48;
    private static final int UNBOUNDED_MIN = Integer.MIN_VALUE / 4;
    private static final int UNBOUNDED_MAX = Integer.MAX_VALUE / 4;

    private final DocumentLayoutBox box;
    private final boolean overflowClipsX;
    private final boolean overflowClipsY;
    private final boolean clipsChildren;
    private final boolean positionedStackingContext;
    private final boolean opacityStackingContext;
    private final boolean transformStackingContext;
    private final int backdropBlurRadius;
    private final float backdropSaturation;
    private final List<DocumentEffectType> staticEffects;

    private DocumentEffectChain(DocumentLayoutBox box, boolean overflowClipsX, boolean overflowClipsY,
            boolean clipsChildren, boolean positionedStackingContext, boolean opacityStackingContext,
            boolean transformStackingContext, int backdropBlurRadius, float backdropSaturation,
            List<DocumentEffectType> staticEffects) {
        this.box = Objects.requireNonNull(box, "box");
        this.overflowClipsX = overflowClipsX;
        this.overflowClipsY = overflowClipsY;
        this.clipsChildren = clipsChildren;
        this.positionedStackingContext = positionedStackingContext;
        this.opacityStackingContext = opacityStackingContext;
        this.transformStackingContext = transformStackingContext;
        this.backdropBlurRadius = Math.max(0, backdropBlurRadius);
        this.backdropSaturation = Math.max(0.0F, backdropSaturation);
        this.staticEffects = Collections.unmodifiableList(new ArrayList<DocumentEffectType>(staticEffects));
    }

    /**
     * 基于布局盒的当前 computed style 解析效果链。
     *
     * @param box 布局盒
     * @return 效果链
     */
    public static DocumentEffectChain resolve(DocumentLayoutBox box) {
        Objects.requireNonNull(box, "box");
        ComputedStyle style = box.getComputedStyle();
        boolean overflowClipsX = style.getOverflowX() != UiOverflow.VISIBLE;
        boolean overflowClipsY = style.getOverflowY() != UiOverflow.VISIBLE;
        boolean hasOverflowClip = overflowClipsX || overflowClipsY;
        boolean clipsChildren = hasOverflowClip && hasClipSensitiveContent(box);
        boolean positionedStackingContext = style.getPosition() == UiPosition.STICKY
                || style.getPosition() != UiPosition.STATIC && style.getZIndex() != null;
        boolean opacityStackingContext = style.getOpacity() < 0.999F;
        UiTransform transform = style.getTransform();
        boolean transformStackingContext = transform != null && !transform.isIdentity();
        int backdropBlurRadius = resolveBackdropBlurRadius(box, style);
        float backdropSaturation = style.getBackdropSaturation();

        List<DocumentEffectType> staticEffects = new ArrayList<DocumentEffectType>();
        if (hasBackdropFilter(backdropBlurRadius, backdropSaturation)) {
            staticEffects.add(DocumentEffectType.BACKDROP_FILTER);
        }
        if (clipsChildren) {
            staticEffects.add(DocumentEffectType.OVERFLOW_CLIP);
        }
        return new DocumentEffectChain(box, overflowClipsX, overflowClipsY, clipsChildren,
                positionedStackingContext, opacityStackingContext, transformStackingContext,
                backdropBlurRadius, backdropSaturation,
                staticEffects);
    }

    /**
     * 返回不依赖动画运行值的静态效果顺序。
     *
     * @return 静态效果顺序
     */
    public List<DocumentEffectType> getStaticEffects() {
        return staticEffects;
    }

    /**
     * 返回绘制阶段应按顺序开启的效果。
     *
     * @param rootBox 当前盒是否为根绘制盒
     * @param localOpacity 当前盒的运行态局部 opacity
     * @return 绘制效果顺序
     */
    public List<DocumentEffectType> getPaintEffects(boolean rootBox, float localOpacity) {
        List<DocumentEffectType> paintEffects = new ArrayList<DocumentEffectType>();
        if (createsPaintContext(rootBox, localOpacity)) {
            paintEffects.add(DocumentEffectType.PAINT_CONTEXT);
        }
        paintEffects.addAll(staticEffects);
        return Collections.unmodifiableList(paintEffects);
    }

    /**
     * 判断当前盒是否建立 CSS-like stacking context。
     *
     * @return 是否建立 stacking context
     */
    public boolean createsStackingContext() {
        return opacityStackingContext || positionedStackingContext || transformStackingContext || hasBackdropFilter();
    }

    /**
     * 判断当前盒是否应作为命中/滚动/绘制的局部排序边界。
     *
     * @return 是否为局部排序边界
     */
    public boolean isStackingBoundary() {
        return createsStackingContext() || clipsChildren;
    }

    /**
     * 判断绘制阶段是否需要开启独立 paint context。
     *
     * @param rootBox 当前盒是否为根绘制盒
     * @param localOpacity 当前盒的运行态局部 opacity
     * @return 是否需要 paint context
     */
    public boolean createsPaintContext(boolean rootBox, float localOpacity) {
        if (rootBox || box.getWidth() <= 0 || box.getHeight() <= 0) {
            return false;
        }
        if (localOpacity < 0.999F) {
            return true;
        }
        return positionedStackingContext || hasBackdropFilter();
    }

    /**
     * 判断当前盒是否有任一方向的 overflow clip。
     *
     * @return 是否声明 overflow clip
     */
    public boolean hasOverflowClip() {
        return overflowClipsX || overflowClipsY;
    }

    /**
     * 判断当前盒是否需要裁剪子内容。
     *
     * @return 是否裁剪子内容
     */
    public boolean clipsChildren() {
        return clipsChildren;
    }

    /**
     * 判断当前盒是否启用 backdrop-filter。
     *
     * @return 是否启用 backdrop-filter
     */
    public boolean hasBackdropFilter() {
        return hasBackdropFilter(backdropBlurRadius, backdropSaturation);
    }

    /**
     * 返回已限制上限的 backdrop blur 半径。
     *
     * @return blur 半径
     */
    public int getBackdropBlurRadius() {
        return backdropBlurRadius;
    }

    /**
     * 返回 backdrop saturation 倍率。
     *
     * @return saturation 倍率
     */
    public float getBackdropSaturation() {
        return backdropSaturation;
    }

    /**
     * 解析子内容裁剪边界。
     *
     * @param offsetX 当前盒视觉 X 偏移
     * @param offsetY 当前盒视觉 Y 偏移
     * @return 子内容裁剪边界
     */
    public ClipBounds resolveChildClipBounds(int offsetX, int offsetY) {
        int left = overflowClipsX ? box.getLeft() + box.getBorder().getLeft() + offsetX : UNBOUNDED_MIN;
        int right = overflowClipsX ? box.getRight() - box.getBorder().getRight() + offsetX : UNBOUNDED_MAX;
        int top = overflowClipsY ? box.getTop() + box.getBorder().getTop() + offsetY : UNBOUNDED_MIN;
        int bottom = overflowClipsY ? box.getBottom() - box.getBorder().getBottom() + offsetY : UNBOUNDED_MAX;
        return new ClipBounds(left, top, right, bottom);
    }

    /**
     * 判断指定坐标是否可进入当前盒的子内容区域。
     *
     * @param x 文档局部 X
     * @param y 文档局部 Y
     * @param offsetX 当前盒视觉 X 偏移
     * @param offsetY 当前盒视觉 Y 偏移
     * @return 是否可继续访问子内容
     */
    public boolean canReachChildrenAt(float x, float y, int offsetX, int offsetY) {
        if (!hasOverflowClip()) {
            return true;
        }
        return resolveChildClipBounds(offsetX, offsetY).contains(x, y);
    }

    private static boolean hasClipSensitiveContent(DocumentLayoutBox box) {
        return !box.getChildren().isEmpty() || !box.getTextRuns().isEmpty()
                || box.getElement().getCustomRenderer() != null;
    }

    private static int resolveBackdropBlurRadius(DocumentLayoutBox box, ComputedStyle style) {
        int availableSpace = Math.max(box.getWidth(), box.getHeight());
        int radius = style.getBackdropBlurRadius().resolve(availableSpace, 0);
        return Math.max(0, Math.min(radius, MAX_BACKDROP_BLUR_RADIUS));
    }

    private static boolean hasBackdropFilter(int blurRadius, float saturation) {
        return blurRadius > 0 || Float.compare(saturation, 1.0F) != 0;
    }

    /**
     * 子内容裁剪边界。
     */
    public static final class ClipBounds {

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;

        private ClipBounds(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = Math.max(left, right);
            this.bottom = Math.max(top, bottom);
        }

        /**
         * 判断指定点是否位于边界内。
         *
         * @param x X 坐标
         * @param y Y 坐标
         * @return 是否命中边界
         */
        public boolean contains(float x, float y) {
            return x >= left && x < right && y >= top && y < bottom;
        }

        /**
         * 返回左边界。
         *
         * @return 左边界
         */
        public int getLeft() {
            return left;
        }

        /**
         * 返回上边界。
         *
         * @return 上边界
         */
        public int getTop() {
            return top;
        }

        /**
         * 返回右边界。
         *
         * @return 右边界
         */
        public int getRight() {
            return right;
        }

        /**
         * 返回下边界。
         *
         * @return 下边界
         */
        public int getBottom() {
            return bottom;
        }
    }
}
