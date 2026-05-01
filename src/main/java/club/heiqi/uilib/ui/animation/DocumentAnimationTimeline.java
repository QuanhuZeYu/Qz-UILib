package club.heiqi.uilib.ui.animation;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.layout.DocumentEffectChain;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.style.ComputedStyle;

/**
 * HTML-like 文档级动画时间线。
 *
 * <p>当前负责 paint/effect transition 与首期 keyframe 运行覆盖，不直接修改作者侧 inline style。</p>
 */
public final class DocumentAnimationTimeline {

    private static final DocumentAnimationProperty[] PAINT_COLOR_PROPERTIES = new DocumentAnimationProperty[] {
            DocumentAnimationProperty.BACKGROUND_COLOR,
            DocumentAnimationProperty.BORDER_COLOR,
            DocumentAnimationProperty.TEXT_COLOR
    };
    private static final DocumentAnimationProperty[] PAINT_FLOAT_PROPERTIES = new DocumentAnimationProperty[] {
            DocumentAnimationProperty.OPACITY,
            DocumentAnimationProperty.BORDER_RADIUS,
            DocumentAnimationProperty.BACKDROP_BLUR_RADIUS
    };

    private final Map<ElementNode, ElementAnimationState> states = new HashMap<ElementNode, ElementAnimationState>();

    /**
     * 根据最新布局盒树刷新 transition 状态。
     *
     * @param rootBox 根布局盒
     * @param currentTimeNanos 当前动画时间
     * @return 动画状态是否发生变化
     */
    public boolean updateFromLayout(DocumentLayoutBox rootBox, long currentTimeNanos) {
        Objects.requireNonNull(rootBox, "rootBox");
        boolean changed = false;
        Set<ElementNode> activeElements = new HashSet<ElementNode>();
        changed |= updateFromBox(rootBox, currentTimeNanos, activeElements);
        Iterator<Map.Entry<ElementNode, ElementAnimationState>> iterator = states.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ElementNode, ElementAnimationState> entry = iterator.next();
            if (activeElements.contains(entry.getKey())) {
                continue;
            }
            iterator.remove();
            changed = true;
        }
        return changed;
    }

    /**
     * 返回指定颜色属性当前动画后的表现值。
     *
     * @param element 元素
     * @param property 动画属性
     * @param baseColor 当前 computed style 基准色
     * @param currentTimeNanos 当前动画时间
     * @return 动画后的颜色
     */
    public int resolveColor(ElementNode element, DocumentAnimationProperty property, int baseColor,
            long currentTimeNanos) {
        ElementAnimationState state = states.get(element);
        if (state == null) {
            return baseColor;
        }
        ColorTransition transition = state.colorTransitions.get(property);
        if (transition != null) {
            return transition.resolve(currentTimeNanos);
        }
        ColorKeyframeAnimation keyframeAnimation = state.colorKeyframeAnimations.get(property);
        if (keyframeAnimation != null) {
            return keyframeAnimation.resolve(baseColor, currentTimeNanos);
        }
        Integer filledColor = state.filledColors.get(property);
        return filledColor == null ? baseColor : filledColor.intValue();
    }

    /**
     * 返回指定数值属性当前动画后的表现值。
     *
     * @param element 元素
     * @param property 动画属性
     * @param baseValue 当前 computed style 基准值
     * @param currentTimeNanos 当前动画时间
     * @return 动画后的数值
     */
    public float resolveFloat(ElementNode element, DocumentAnimationProperty property, float baseValue,
            long currentTimeNanos) {
        ElementAnimationState state = states.get(element);
        if (state == null) {
            return baseValue;
        }
        FloatTransition transition = state.floatTransitions.get(property);
        if (transition != null) {
            return transition.resolve(currentTimeNanos);
        }
        FloatKeyframeAnimation keyframeAnimation = state.floatKeyframeAnimations.get(property);
        if (keyframeAnimation != null) {
            return keyframeAnimation.resolve(baseValue, currentTimeNanos);
        }
        Float filledValue = state.filledFloats.get(property);
        return filledValue == null ? baseValue : filledValue.floatValue();
    }

    /**
     * 设置单个颜色 keyframe animation 覆盖。
     *
     * @param element 元素
     * @param property 动画属性
     * @param fromColor 起始颜色
     * @param toColor 结束颜色
     * @param startNanos 动画起始时间
     * @param durationNanos 动画持续时间
     */
    public void setColorKeyframeAnimation(ElementNode element, DocumentAnimationProperty property, int fromColor,
            int toColor, long startNanos, long durationNanos) {
        Objects.requireNonNull(element, "element");
        Objects.requireNonNull(property, "property");
        ElementAnimationState state = getOrCreateState(element);
        state.declaredColorKeyframeProperties.remove(property);
        state.filledColors.remove(property);
        state.colorKeyframeAnimations.put(property, new ColorKeyframeAnimation(fromColor, toColor, startNanos,
                durationNanos, 1, DocumentAnimationFillMode.NONE, DocumentAnimationTimingFunction.LINEAR));
    }

    /**
     * 设置单个数值 keyframe animation 覆盖。
     *
     * @param element 元素
     * @param property 动画属性
     * @param fromValue 起始值
     * @param toValue 结束值
     * @param startNanos 动画起始时间
     * @param durationNanos 动画持续时间
     */
    public void setFloatKeyframeAnimation(ElementNode element, DocumentAnimationProperty property, float fromValue,
            float toValue, long startNanos, long durationNanos) {
        Objects.requireNonNull(element, "element");
        Objects.requireNonNull(property, "property");
        ElementAnimationState state = getOrCreateState(element);
        state.declaredFloatKeyframeProperties.remove(property);
        state.filledFloats.remove(property);
        state.floatKeyframeAnimations.put(property, new FloatKeyframeAnimation(fromValue, toValue, startNanos,
                durationNanos, 1, DocumentAnimationFillMode.NONE, DocumentAnimationTimingFunction.LINEAR));
    }

    /**
     * 清除指定元素的所有 keyframe animation 覆盖。
     *
     * @param element 元素
     */
    public void clearKeyframeAnimations(ElementNode element) {
        Objects.requireNonNull(element, "element");
        ElementAnimationState state = states.get(element);
        if (state == null) {
            return;
        }
        state.colorKeyframeAnimations.clear();
        state.floatKeyframeAnimations.clear();
        state.filledColors.clear();
        state.filledFloats.clear();
        state.clearDeclaredKeyframeSignature();
    }

    /**
     * 返回当前是否仍有动画工作需要下一帧刷新。
     *
     * @return 是否有动画工作
     */
    public boolean hasAnimationWork() {
        for (ElementAnimationState state : states.values()) {
            if (!state.colorTransitions.isEmpty() || !state.floatTransitions.isEmpty()
                    || !state.colorKeyframeAnimations.isEmpty() || !state.floatKeyframeAnimations.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 返回当前是否有指定影响范围的动画工作。
     *
     * @param impact 动画影响范围
     * @return 是否存在对应动画
     */
    public boolean hasAnimationWork(DocumentAnimationImpact impact) {
        Objects.requireNonNull(impact, "impact");
        for (ElementAnimationState state : states.values()) {
            for (DocumentAnimationProperty property : state.colorTransitions.keySet()) {
                if (property.getImpact() == impact) {
                    return true;
                }
            }
            for (DocumentAnimationProperty property : state.floatTransitions.keySet()) {
                if (property.getImpact() == impact) {
                    return true;
                }
            }
            for (DocumentAnimationProperty property : state.colorKeyframeAnimations.keySet()) {
                if (property.getImpact() == impact) {
                    return true;
                }
            }
            for (DocumentAnimationProperty property : state.floatKeyframeAnimations.keySet()) {
                if (property.getImpact() == impact) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 返回指定元素属性当前是否仍存在运行态 transition 覆盖。
     *
     * @param element 元素
     * @param property 动画属性
     * @return 是否存在运行态 transition
     */
    public boolean hasRunningTransition(ElementNode element, DocumentAnimationProperty property) {
        Objects.requireNonNull(element, "element");
        Objects.requireNonNull(property, "property");
        ElementAnimationState state = states.get(element);
        if (state == null) {
            return false;
        }
        return state.colorTransitions.containsKey(property) || state.floatTransitions.containsKey(property);
    }

    /**
     * 返回当前仍未完成的动画数量。
     *
     * @param currentTimeNanos 当前动画时间
     * @return 活跃动画数量
     */
    public int getActiveAnimationCount(long currentTimeNanos) {
        int count = 0;
        for (ElementAnimationState state : states.values()) {
            for (ColorTransition transition : state.colorTransitions.values()) {
                if (!transition.isFinished(currentTimeNanos)) {
                    count++;
                }
            }
            for (FloatTransition transition : state.floatTransitions.values()) {
                if (!transition.isFinished(currentTimeNanos)) {
                    count++;
                }
            }
            for (ColorKeyframeAnimation keyframeAnimation : state.colorKeyframeAnimations.values()) {
                if (!keyframeAnimation.isFinished(currentTimeNanos)) {
                    count++;
                }
            }
            for (FloatKeyframeAnimation keyframeAnimation : state.floatKeyframeAnimations.values()) {
                if (!keyframeAnimation.isFinished(currentTimeNanos)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * 清理已完成动画。
     *
     * @param currentTimeNanos 当前动画时间
     * @return 是否发生清理
     */
    public boolean pruneFinishedAnimations(long currentTimeNanos) {
        boolean changed = false;
        for (ElementAnimationState state : states.values()) {
            Iterator<Map.Entry<DocumentAnimationProperty, ColorTransition>> colorIterator = state.colorTransitions.entrySet()
                    .iterator();
            while (colorIterator.hasNext()) {
                Map.Entry<DocumentAnimationProperty, ColorTransition> entry = colorIterator.next();
                if (entry.getValue().isFinished(currentTimeNanos)) {
                    colorIterator.remove();
                    changed = true;
                }
            }
            Iterator<Map.Entry<DocumentAnimationProperty, FloatTransition>> floatIterator = state.floatTransitions.entrySet()
                    .iterator();
            while (floatIterator.hasNext()) {
                Map.Entry<DocumentAnimationProperty, FloatTransition> entry = floatIterator.next();
                if (entry.getValue().isFinished(currentTimeNanos)) {
                    floatIterator.remove();
                    changed = true;
                }
            }
        }
        for (ElementAnimationState state : states.values()) {
            Iterator<Map.Entry<DocumentAnimationProperty, ColorKeyframeAnimation>> colorIterator =
                    state.colorKeyframeAnimations.entrySet().iterator();
            while (colorIterator.hasNext()) {
                Map.Entry<DocumentAnimationProperty, ColorKeyframeAnimation> entry = colorIterator.next();
                if (entry.getValue().isFinished(currentTimeNanos)) {
                    if (entry.getValue().fillsForwards()) {
                        state.filledColors.put(entry.getKey(), Integer.valueOf(entry.getValue().getFilledColor()));
                    }
                    colorIterator.remove();
                    changed = true;
                }
            }
            Iterator<Map.Entry<DocumentAnimationProperty, FloatKeyframeAnimation>> floatIterator =
                    state.floatKeyframeAnimations.entrySet().iterator();
            while (floatIterator.hasNext()) {
                Map.Entry<DocumentAnimationProperty, FloatKeyframeAnimation> entry = floatIterator.next();
                if (entry.getValue().isFinished(currentTimeNanos)) {
                    if (entry.getValue().fillsForwards()) {
                        state.filledFloats.put(entry.getKey(), Float.valueOf(entry.getValue().getFilledValue()));
                    }
                    floatIterator.remove();
                    changed = true;
                }
            }
        }
        return changed;
    }

    /**
     * 清空所有动画状态。
     */
    public void clear() {
        states.clear();
    }

    private boolean updateFromBox(DocumentLayoutBox box, long currentTimeNanos, Set<ElementNode> activeElements) {
        ElementNode element = box.getElement();
        activeElements.add(element);
        boolean changed = updateElementState(box, currentTimeNanos);
        for (DocumentLayoutBox child : box.getChildren()) {
            changed |= updateFromBox(child, currentTimeNanos, activeElements);
        }
        return changed;
    }

    private boolean updateElementState(DocumentLayoutBox box, long currentTimeNanos) {
        ElementNode element = box.getElement();
        ComputedStyle style = box.getComputedStyle();
        ElementAnimationState state = states.get(element);
        boolean changed = false;
        if (state == null) {
            state = getOrCreateState(element);
            changed = true;
        }
        changed |= updateDeclaredKeyframeAnimations(box, style, currentTimeNanos, state);
        for (DocumentAnimationProperty property : PAINT_COLOR_PROPERTIES) {
            int baseColor = getBaseColor(style, property);
            Integer previousTarget = state.targetColors.get(property);
            boolean transitionAllowed = canTransition(style, property);
            if (!transitionAllowed && state.colorTransitions.remove(property) != null) {
                changed = true;
            }
            if (previousTarget == null) {
                state.targetColors.put(property, Integer.valueOf(baseColor));
                changed = true;
                continue;
            }
            if (previousTarget.intValue() == baseColor) {
                continue;
            }
            int fromColor = resolveColor(element, property, previousTarget.intValue(), currentTimeNanos);
            state.targetColors.put(property, Integer.valueOf(baseColor));
            if (transitionAllowed && fromColor != baseColor) {
                state.colorTransitions.put(property, new ColorTransition(fromColor, baseColor,
                        currentTimeNanos + style.getTransitionDelayNanos(), style.getTransitionDurationNanos(),
                        style.getTransitionTimingFunction()));
            } else {
                state.colorTransitions.remove(property);
            }
            changed = true;
        }
        for (DocumentAnimationProperty property : PAINT_FLOAT_PROPERTIES) {
            float baseValue = getBaseFloat(box, property);
            Float previousTarget = state.targetFloats.get(property);
            boolean transitionAllowed = canTransition(style, property);
            if (!transitionAllowed && state.floatTransitions.remove(property) != null) {
                changed = true;
            }
            if (previousTarget == null) {
                state.targetFloats.put(property, Float.valueOf(baseValue));
                changed = true;
                continue;
            }
            if (Float.compare(previousTarget.floatValue(), baseValue) == 0) {
                continue;
            }
            float fromValue = resolveFloat(element, property, previousTarget.floatValue(), currentTimeNanos);
            state.targetFloats.put(property, Float.valueOf(baseValue));
            if (transitionAllowed && Float.compare(fromValue, baseValue) != 0) {
                state.floatTransitions.put(property, new FloatTransition(fromValue, baseValue,
                        currentTimeNanos + style.getTransitionDelayNanos(), style.getTransitionDurationNanos(),
                        style.getTransitionTimingFunction()));
            } else {
                state.floatTransitions.remove(property);
            }
            changed = true;
        }
        return changed;
    }

    private ElementAnimationState getOrCreateState(ElementNode element) {
        ElementAnimationState state = states.get(element);
        if (state == null) {
            state = new ElementAnimationState();
            states.put(element, state);
        }
        return state;
    }

    private static boolean canTransition(ComputedStyle style, DocumentAnimationProperty property) {
        return style.getTransitionDurationNanos() > 0L && style.getTransitionProperties().contains(property);
    }

    private static boolean updateDeclaredKeyframeAnimations(DocumentLayoutBox box, ComputedStyle style,
            long currentTimeNanos, ElementAnimationState state) {
        String animationName = style.getAnimationName();
        DocumentKeyframes keyframes = animationName == null ? null
                : box.getElement().getOwnerDocument().getKeyframes(animationName);
        if (animationName == null || style.getAnimationDurationNanos() <= 0L || keyframes == null
                || keyframes.isEmpty()) {
            return clearDeclaredKeyframeAnimations(state);
        }
        if (state.matchesDeclaredKeyframeSignature(animationName, keyframes, box, style)) {
            return false;
        }

        clearDeclaredKeyframeAnimations(state);
        state.declaredAnimationName = animationName;
        state.declaredKeyframes = keyframes;
        state.declaredDurationNanos = style.getAnimationDurationNanos();
        state.declaredDelayNanos = style.getAnimationDelayNanos();
        state.declaredIterationCount = style.getAnimationIterationCount();
        state.declaredFillMode = style.getAnimationFillMode();
        state.declaredTimingFunction = style.getAnimationTimingFunction();
        state.declaredBoxWidth = box.getWidth();
        state.declaredBoxHeight = box.getHeight();
        long startNanos = currentTimeNanos + style.getAnimationDelayNanos();
        for (Map.Entry<DocumentAnimationProperty, DocumentKeyframes.ColorRange> entry : keyframes.getColorRanges()
                .entrySet()) {
            DocumentAnimationProperty property = entry.getKey();
            DocumentKeyframes.ColorRange range = entry.getValue();
            state.colorKeyframeAnimations.put(property, new ColorKeyframeAnimation(range.getFromColor(),
                    range.getToColor(), startNanos, style.getAnimationDurationNanos(),
                    style.getAnimationIterationCount(), style.getAnimationFillMode(),
                    style.getAnimationTimingFunction()));
            state.declaredColorKeyframeProperties.add(property);
            state.filledColors.remove(property);
        }
        for (Map.Entry<DocumentAnimationProperty, DocumentKeyframes.FloatRange> entry : keyframes.getFloatRanges()
                .entrySet()) {
            DocumentAnimationProperty property = entry.getKey();
            DocumentKeyframes.FloatRange range = entry.getValue();
            state.floatKeyframeAnimations.put(property, new FloatKeyframeAnimation(normalizeDeclaredKeyframeFloat(box,
                    property, range.getFromValue()), normalizeDeclaredKeyframeFloat(box, property, range.getToValue()),
                    startNanos, style.getAnimationDurationNanos(),
                    style.getAnimationIterationCount(), style.getAnimationFillMode(),
                    style.getAnimationTimingFunction()));
            state.declaredFloatKeyframeProperties.add(property);
            state.filledFloats.remove(property);
        }
        return true;
    }

    private static boolean clearDeclaredKeyframeAnimations(ElementAnimationState state) {
        boolean changed = false;
        for (DocumentAnimationProperty property : state.declaredColorKeyframeProperties) {
            changed |= state.colorKeyframeAnimations.remove(property) != null;
            changed |= state.filledColors.remove(property) != null;
        }
        for (DocumentAnimationProperty property : state.declaredFloatKeyframeProperties) {
            changed |= state.floatKeyframeAnimations.remove(property) != null;
            changed |= state.filledFloats.remove(property) != null;
        }
        if (state.declaredAnimationName != null || state.declaredKeyframes != null) {
            changed = true;
        }
        state.clearDeclaredKeyframeSignature();
        return changed;
    }

    private static float normalizeDeclaredKeyframeFloat(DocumentLayoutBox box, DocumentAnimationProperty property,
            float value) {
        if (property == DocumentAnimationProperty.OPACITY) {
            return Math.max(0.0F, Math.min(1.0F, value));
        }
        if (property == DocumentAnimationProperty.BORDER_RADIUS) {
            int limit = Math.min(box.getWidth(), box.getHeight());
            return Math.max(0.0F, Math.min(value, limit / 2.0F));
        }
        if (property == DocumentAnimationProperty.BACKDROP_BLUR_RADIUS) {
            return Math.max(0.0F, Math.min(value, DocumentEffectChain.MAX_BACKDROP_BLUR_RADIUS));
        }
        return value;
    }

    private static int getBaseColor(ComputedStyle style, DocumentAnimationProperty property) {
        if (property == DocumentAnimationProperty.BORDER_COLOR) {
            return style.getBorderColor();
        }
        if (property == DocumentAnimationProperty.TEXT_COLOR) {
            return style.getTextColor();
        }
        return style.getBackgroundColor();
    }

    private static float getBaseFloat(DocumentLayoutBox box, DocumentAnimationProperty property) {
        if (property == DocumentAnimationProperty.OPACITY) {
            return box.getComputedStyle().getOpacity();
        }
        if (property == DocumentAnimationProperty.BORDER_RADIUS) {
            return resolveBorderRadius(box);
        }
        if (property == DocumentAnimationProperty.BACKDROP_BLUR_RADIUS) {
            return resolveBackdropBlurRadius(box);
        }
        return 0.0F;
    }

    private static int resolveBorderRadius(DocumentLayoutBox box) {
        int limit = Math.min(box.getWidth(), box.getHeight());
        int radius = box.getComputedStyle().getBorderRadius().resolve(limit, 0);
        return Math.max(0, Math.min(radius, limit / 2));
    }

    private static int resolveBackdropBlurRadius(DocumentLayoutBox box) {
        int availableSpace = Math.max(box.getWidth(), box.getHeight());
        int radius = box.getComputedStyle().getBackdropBlurRadius().resolve(availableSpace, 0);
        return Math.max(0, Math.min(radius, DocumentEffectChain.MAX_BACKDROP_BLUR_RADIUS));
    }

    private static int interpolateColor(int fromColor, int toColor, float progress) {
        int fromA = (fromColor >>> 24) & 0xFF;
        int fromR = (fromColor >>> 16) & 0xFF;
        int fromG = (fromColor >>> 8) & 0xFF;
        int fromB = fromColor & 0xFF;
        int toA = (toColor >>> 24) & 0xFF;
        int toR = (toColor >>> 16) & 0xFF;
        int toG = (toColor >>> 8) & 0xFF;
        int toB = toColor & 0xFF;
        int a = interpolateChannel(fromA, toA, progress);
        int r = interpolateChannel(fromR, toR, progress);
        int g = interpolateChannel(fromG, toG, progress);
        int b = interpolateChannel(fromB, toB, progress);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int interpolateChannel(int from, int to, float progress) {
        return Math.max(0, Math.min(255, Math.round(from + (to - from) * progress)));
    }

    /**
     * 单元素动画状态。
     */
    private static final class ElementAnimationState {

        private final EnumMap<DocumentAnimationProperty, Integer> targetColors =
                new EnumMap<DocumentAnimationProperty, Integer>(DocumentAnimationProperty.class);
        private final EnumMap<DocumentAnimationProperty, ColorTransition> colorTransitions =
                new EnumMap<DocumentAnimationProperty, ColorTransition>(DocumentAnimationProperty.class);
        private final EnumMap<DocumentAnimationProperty, Float> targetFloats =
                new EnumMap<DocumentAnimationProperty, Float>(DocumentAnimationProperty.class);
        private final EnumMap<DocumentAnimationProperty, FloatTransition> floatTransitions =
                new EnumMap<DocumentAnimationProperty, FloatTransition>(DocumentAnimationProperty.class);
        private final EnumMap<DocumentAnimationProperty, ColorKeyframeAnimation> colorKeyframeAnimations =
                new EnumMap<DocumentAnimationProperty, ColorKeyframeAnimation>(DocumentAnimationProperty.class);
        private final EnumMap<DocumentAnimationProperty, FloatKeyframeAnimation> floatKeyframeAnimations =
                new EnumMap<DocumentAnimationProperty, FloatKeyframeAnimation>(DocumentAnimationProperty.class);
        private final EnumMap<DocumentAnimationProperty, Integer> filledColors =
                new EnumMap<DocumentAnimationProperty, Integer>(DocumentAnimationProperty.class);
        private final EnumMap<DocumentAnimationProperty, Float> filledFloats =
                new EnumMap<DocumentAnimationProperty, Float>(DocumentAnimationProperty.class);
        private final Set<DocumentAnimationProperty> declaredColorKeyframeProperties =
                new HashSet<DocumentAnimationProperty>();
        private final Set<DocumentAnimationProperty> declaredFloatKeyframeProperties =
                new HashSet<DocumentAnimationProperty>();
        private String declaredAnimationName;
        private DocumentKeyframes declaredKeyframes;
        private long declaredDurationNanos;
        private long declaredDelayNanos;
        private int declaredIterationCount;
        private DocumentAnimationFillMode declaredFillMode;
        private DocumentAnimationTimingFunction declaredTimingFunction;
        private int declaredBoxWidth;
        private int declaredBoxHeight;

        private boolean matchesDeclaredKeyframeSignature(String animationName, DocumentKeyframes keyframes,
                DocumentLayoutBox box, ComputedStyle style) {
            return Objects.equals(declaredAnimationName, animationName) && declaredKeyframes == keyframes
                    && declaredDurationNanos == style.getAnimationDurationNanos()
                    && declaredDelayNanos == style.getAnimationDelayNanos()
                    && declaredIterationCount == style.getAnimationIterationCount()
                    && declaredFillMode == style.getAnimationFillMode()
                    && declaredTimingFunction == style.getAnimationTimingFunction()
                    && declaredBoxWidth == box.getWidth()
                    && declaredBoxHeight == box.getHeight();
        }

        private void clearDeclaredKeyframeSignature() {
            declaredColorKeyframeProperties.clear();
            declaredFloatKeyframeProperties.clear();
            declaredAnimationName = null;
            declaredKeyframes = null;
            declaredDurationNanos = 0L;
            declaredDelayNanos = 0L;
            declaredIterationCount = 0;
            declaredFillMode = null;
            declaredTimingFunction = null;
            declaredBoxWidth = 0;
            declaredBoxHeight = 0;
        }
    }

    /**
     * 单个颜色 transition。
     */
    private static final class ColorTransition {

        private final int fromColor;
        private final int toColor;
        private final long startNanos;
        private final long durationNanos;
        private final DocumentAnimationTimingFunction timingFunction;

        private ColorTransition(int fromColor, int toColor, long startNanos, long durationNanos,
                DocumentAnimationTimingFunction timingFunction) {
            this.fromColor = fromColor;
            this.toColor = toColor;
            this.startNanos = startNanos;
            this.durationNanos = Math.max(1L, durationNanos);
            this.timingFunction = timingFunction == null ? DocumentAnimationTimingFunction.LINEAR : timingFunction;
        }

        private int resolve(long currentTimeNanos) {
            if (currentTimeNanos <= startNanos) {
                return fromColor;
            }
            long elapsedNanos = currentTimeNanos - startNanos;
            if (elapsedNanos >= durationNanos) {
                return toColor;
            }
            float progress = elapsedNanos / (float) durationNanos;
            return interpolateColor(fromColor, toColor, timingFunction.apply(progress));
        }

        private boolean isFinished(long currentTimeNanos) {
            return currentTimeNanos >= startNanos + durationNanos;
        }
    }

    /**
     * 单个数值 transition。
     */
    private static final class FloatTransition {

        private final float fromValue;
        private final float toValue;
        private final long startNanos;
        private final long durationNanos;
        private final DocumentAnimationTimingFunction timingFunction;

        private FloatTransition(float fromValue, float toValue, long startNanos, long durationNanos,
                DocumentAnimationTimingFunction timingFunction) {
            this.fromValue = fromValue;
            this.toValue = toValue;
            this.startNanos = startNanos;
            this.durationNanos = Math.max(1L, durationNanos);
            this.timingFunction = timingFunction == null ? DocumentAnimationTimingFunction.LINEAR : timingFunction;
        }

        private float resolve(long currentTimeNanos) {
            if (currentTimeNanos <= startNanos) {
                return fromValue;
            }
            long elapsedNanos = currentTimeNanos - startNanos;
            if (elapsedNanos >= durationNanos) {
                return toValue;
            }
            float progress = elapsedNanos / (float) durationNanos;
            return fromValue + (toValue - fromValue) * timingFunction.apply(progress);
        }

        private boolean isFinished(long currentTimeNanos) {
            return currentTimeNanos >= startNanos + durationNanos;
        }
    }

    /**
     * 单个颜色 keyframe animation。
     */
    private static final class ColorKeyframeAnimation {

        private final int fromColor;
        private final int toColor;
        private final long startNanos;
        private final long durationNanos;
        private final int iterationCount;
        private final DocumentAnimationFillMode fillMode;
        private final DocumentAnimationTimingFunction timingFunction;

        private ColorKeyframeAnimation(int fromColor, int toColor, long startNanos, long durationNanos,
                int iterationCount, DocumentAnimationFillMode fillMode, DocumentAnimationTimingFunction timingFunction) {
            this.fromColor = fromColor;
            this.toColor = toColor;
            this.startNanos = startNanos;
            this.durationNanos = Math.max(1L, durationNanos);
            this.iterationCount = Math.max(1, iterationCount);
            this.fillMode = fillMode == null ? DocumentAnimationFillMode.NONE : fillMode;
            this.timingFunction = timingFunction == null ? DocumentAnimationTimingFunction.LINEAR : timingFunction;
        }

        private int resolve(int baseColor, long currentTimeNanos) {
            if (currentTimeNanos <= startNanos) {
                return fillsBackwards() ? fromColor : baseColor;
            }
            long activeDurationNanos = getActiveDurationNanos();
            long elapsedNanos = currentTimeNanos - startNanos;
            if (elapsedNanos >= activeDurationNanos) {
                return fillsForwards() ? toColor : baseColor;
            }
            long iterationElapsedNanos = elapsedNanos % durationNanos;
            return interpolateColor(fromColor, toColor, timingFunction.apply(iterationElapsedNanos / (float) durationNanos));
        }

        private boolean isFinished(long currentTimeNanos) {
            return currentTimeNanos >= startNanos + getActiveDurationNanos();
        }

        private boolean fillsBackwards() {
            return fillMode == DocumentAnimationFillMode.BACKWARDS || fillMode == DocumentAnimationFillMode.BOTH;
        }

        private boolean fillsForwards() {
            return fillMode == DocumentAnimationFillMode.FORWARDS || fillMode == DocumentAnimationFillMode.BOTH;
        }

        private int getFilledColor() {
            return toColor;
        }

        private long getActiveDurationNanos() {
            return durationNanos * iterationCount;
        }
    }

    /**
     * 单个数值 keyframe animation。
     */
    private static final class FloatKeyframeAnimation {

        private final float fromValue;
        private final float toValue;
        private final long startNanos;
        private final long durationNanos;
        private final int iterationCount;
        private final DocumentAnimationFillMode fillMode;
        private final DocumentAnimationTimingFunction timingFunction;

        private FloatKeyframeAnimation(float fromValue, float toValue, long startNanos, long durationNanos,
                int iterationCount, DocumentAnimationFillMode fillMode, DocumentAnimationTimingFunction timingFunction) {
            this.fromValue = fromValue;
            this.toValue = toValue;
            this.startNanos = startNanos;
            this.durationNanos = Math.max(1L, durationNanos);
            this.iterationCount = Math.max(1, iterationCount);
            this.fillMode = fillMode == null ? DocumentAnimationFillMode.NONE : fillMode;
            this.timingFunction = timingFunction == null ? DocumentAnimationTimingFunction.LINEAR : timingFunction;
        }

        private float resolve(float baseValue, long currentTimeNanos) {
            if (currentTimeNanos <= startNanos) {
                return fillsBackwards() ? fromValue : baseValue;
            }
            long activeDurationNanos = getActiveDurationNanos();
            long elapsedNanos = currentTimeNanos - startNanos;
            if (elapsedNanos >= activeDurationNanos) {
                return fillsForwards() ? toValue : baseValue;
            }
            long iterationElapsedNanos = elapsedNanos % durationNanos;
            return fromValue + (toValue - fromValue) * timingFunction.apply(iterationElapsedNanos / (float) durationNanos);
        }

        private boolean isFinished(long currentTimeNanos) {
            return currentTimeNanos >= startNanos + getActiveDurationNanos();
        }

        private boolean fillsBackwards() {
            return fillMode == DocumentAnimationFillMode.BACKWARDS || fillMode == DocumentAnimationFillMode.BOTH;
        }

        private boolean fillsForwards() {
            return fillMode == DocumentAnimationFillMode.FORWARDS || fillMode == DocumentAnimationFillMode.BOTH;
        }

        private float getFilledValue() {
            return toValue;
        }

        private long getActiveDurationNanos() {
            return durationNanos * iterationCount;
        }
    }
}
