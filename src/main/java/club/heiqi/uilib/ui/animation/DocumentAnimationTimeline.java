package club.heiqi.uilib.ui.animation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.layout.DocumentEffectChain;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.style.ComputedStyle;
import club.heiqi.uilib.ui.style.UiStyleLength;

/**
 * HTML-like 文档级动画时间线。
 *
 * <p>当前负责 paint/effect transition 与首期 keyframe 运行覆盖，不直接修改作者侧 inline style。</p>
 */
public final class DocumentAnimationTimeline {

    private static final DocumentAnimationProperty[] COLOR_PROPERTIES = filterPropertiesByValueType(
            DocumentAnimationProperty.ValueType.COLOR);
    private static final DocumentAnimationProperty[] FLOAT_PROPERTIES = filterPropertiesByValueType(
            DocumentAnimationProperty.ValueType.FLOAT);

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
        return state.resolveColor(property, baseColor, currentTimeNanos);
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
        return state.resolveFloat(property, baseValue, currentTimeNanos);
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
        state.setManualColorKeyframeAnimation(property, fromColor, toColor, startNanos, durationNanos);
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
        state.setManualFloatKeyframeAnimation(property, fromValue, toValue, startNanos, durationNanos);
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
        state.clearKeyframeAnimations();
    }

    /**
     * 返回当前是否仍有动画工作需要下一帧刷新。
     *
     * @return 是否有动画工作
     */
    public boolean hasAnimationWork() {
        for (ElementAnimationState state : states.values()) {
            if (state.hasAnimationWork()) {
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
            if (state.hasAnimationWork(impact)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 返回当前是否有指定影响范围的运行态覆盖值。
     *
     * <p>与 `hasAnimationWork(...)` 不同，forwards fill 这类已完成但仍覆盖 computed style 的运行值也会返回 true。</p>
     *
     * @param impact 动画影响范围
     * @return 是否存在对应运行态覆盖值
     */
    public boolean hasRuntimeValue(DocumentAnimationImpact impact) {
        Objects.requireNonNull(impact, "impact");
        for (ElementAnimationState state : states.values()) {
            if (state.hasRuntimeValue(impact)) {
                return true;
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
        return state.hasRunningTransition(property);
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
            count += state.countActiveAnimations(currentTimeNanos);
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
            changed |= state.pruneFinishedAnimations(currentTimeNanos);
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
        for (DocumentAnimationProperty property : COLOR_PROPERTIES) {
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
            state.suppressDeclaredColorKeyframeProperty(property);
            if (transitionAllowed && fromColor != baseColor) {
                state.colorTransitions.put(property, new ColorTransition(fromColor, baseColor,
                        currentTimeNanos + style.getTransitionDelayNanos(), style.getTransitionDurationNanos(),
                        style.getTransitionTimingFunction()));
            } else {
                state.colorTransitions.remove(property);
            }
            changed = true;
        }
        for (DocumentAnimationProperty property : FLOAT_PROPERTIES) {
            float baseValue = getBaseFloat(box, property);
            Float previousTarget = state.targetFloats.get(property);
            boolean targetTransitionable = isFloatTransitionTargetAnimatable(box, property);
            Boolean previousTargetTransitionable = state.targetFloatTransitionable.get(property);
            boolean transitionAllowed = canTransition(style, property) && targetTransitionable
                    && Boolean.TRUE.equals(previousTargetTransitionable);
            if (!transitionAllowed && state.floatTransitions.remove(property) != null) {
                changed = true;
            }
            if (previousTarget == null) {
                state.targetFloats.put(property, Float.valueOf(baseValue));
                state.targetFloatTransitionable.put(property, Boolean.valueOf(targetTransitionable));
                changed = true;
                continue;
            }
            if (Float.compare(previousTarget.floatValue(), baseValue) == 0) {
                if (!Objects.equals(previousTargetTransitionable, Boolean.valueOf(targetTransitionable))) {
                    state.targetFloatTransitionable.put(property, Boolean.valueOf(targetTransitionable));
                    changed = true;
                }
                continue;
            }
            float fromValue = resolveFloat(element, property, previousTarget.floatValue(), currentTimeNanos);
            state.targetFloats.put(property, Float.valueOf(baseValue));
            state.targetFloatTransitionable.put(property, Boolean.valueOf(targetTransitionable));
            state.suppressDeclaredFloatKeyframeProperty(property);
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
            return state.clearDeclaredKeyframeAnimations();
        }
        if (state.matchesDeclaredKeyframeSignature(animationName, keyframes, style)) {
            return state.refreshDeclaredKeyframeGeometry(box);
        }

        state.startDeclaredKeyframeAnimations(box, style, animationName, keyframes, currentTimeNanos);
        return true;
    }

    private static float normalizeDeclaredKeyframeFloat(DocumentLayoutBox box, DocumentAnimationProperty property,
            float value) {
        return PropertyRuntimeSemantics.forProperty(property).normalizeDeclaredKeyframeFloat(box, value);
    }

    private static DocumentKeyframes.FloatTrack normalizeDeclaredKeyframeFloatTrack(DocumentLayoutBox box,
            DocumentAnimationProperty property, DocumentKeyframes.FloatTrack track) {
        DocumentKeyframes.Builder builder = DocumentKeyframes.named("normalized-float-track");
        for (DocumentKeyframes.FloatStop stop : track.getStops()) {
            builder.setFloatStop(property, stop.getOffset(), normalizeDeclaredKeyframeFloat(box, property,
                    stop.getValue()));
        }
        return builder.build().getFloatTracks().get(property);
    }

    private static int getBaseColor(ComputedStyle style, DocumentAnimationProperty property) {
        return PropertyRuntimeSemantics.forProperty(property).resolveBaseColor(style);
    }

    private static float getBaseFloat(DocumentLayoutBox box, DocumentAnimationProperty property) {
        return PropertyRuntimeSemantics.forProperty(property).resolveBaseFloat(box);
    }

    private static boolean isFloatTransitionTargetAnimatable(DocumentLayoutBox box, DocumentAnimationProperty property) {
        return PropertyRuntimeSemantics.forProperty(property).isFloatTransitionTargetAnimatable(box);
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

    private static DocumentAnimationProperty[] filterPropertiesByValueType(
            DocumentAnimationProperty.ValueType valueType) {
        List<DocumentAnimationProperty> properties = new ArrayList<DocumentAnimationProperty>();
        for (DocumentAnimationProperty property : DocumentAnimationProperty.values()) {
            if (property.getValueType() == valueType) {
                properties.add(property);
            }
        }
        return properties.toArray(new DocumentAnimationProperty[properties.size()]);
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

    private static int resolveColorTrack(DocumentKeyframes.ColorTrack track, float progress,
            DocumentAnimationTimingFunction timingFunction) {
        List<DocumentKeyframes.ColorStop> stops = track.getStops();
        if (progress <= stops.get(0).getOffset()) {
            return stops.get(0).getColor();
        }
        int lastIndex = stops.size() - 1;
        if (progress >= stops.get(lastIndex).getOffset()) {
            return stops.get(lastIndex).getColor();
        }
        for (int index = 0; index < lastIndex; index++) {
            DocumentKeyframes.ColorStop fromStop = stops.get(index);
            DocumentKeyframes.ColorStop toStop = stops.get(index + 1);
            if (progress > toStop.getOffset()) {
                continue;
            }
            float localProgress = resolveLocalProgress(fromStop.getOffset(), toStop.getOffset(), progress,
                    timingFunction);
            return interpolateColor(fromStop.getColor(), toStop.getColor(), localProgress);
        }
        return stops.get(lastIndex).getColor();
    }

    private static float resolveFloatTrack(DocumentKeyframes.FloatTrack track, float progress,
            DocumentAnimationTimingFunction timingFunction) {
        List<DocumentKeyframes.FloatStop> stops = track.getStops();
        if (progress <= stops.get(0).getOffset()) {
            return stops.get(0).getValue();
        }
        int lastIndex = stops.size() - 1;
        if (progress >= stops.get(lastIndex).getOffset()) {
            return stops.get(lastIndex).getValue();
        }
        for (int index = 0; index < lastIndex; index++) {
            DocumentKeyframes.FloatStop fromStop = stops.get(index);
            DocumentKeyframes.FloatStop toStop = stops.get(index + 1);
            if (progress > toStop.getOffset()) {
                continue;
            }
            float localProgress = resolveLocalProgress(fromStop.getOffset(), toStop.getOffset(), progress,
                    timingFunction);
            return fromStop.getValue() + (toStop.getValue() - fromStop.getValue()) * localProgress;
        }
        return stops.get(lastIndex).getValue();
    }

    private static float resolveLocalProgress(float fromOffset, float toOffset, float progress,
            DocumentAnimationTimingFunction timingFunction) {
        float span = toOffset - fromOffset;
        if (span <= 0.0F) {
            return 1.0F;
        }
        float localProgress = (progress - fromOffset) / span;
        return timingFunction.apply(Math.max(0.0F, Math.min(1.0F, localProgress)));
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
        private final EnumMap<DocumentAnimationProperty, Boolean> targetFloatTransitionable =
                new EnumMap<DocumentAnimationProperty, Boolean>(DocumentAnimationProperty.class);
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
        private long declaredStartNanos;
        private int declaredBoxWidth;
        private int declaredBoxHeight;

        private int resolveColor(DocumentAnimationProperty property, int baseColor, long currentTimeNanos) {
            ColorTransition transition = colorTransitions.get(property);
            if (transition != null) {
                return transition.resolve(currentTimeNanos);
            }
            ColorKeyframeAnimation keyframeAnimation = colorKeyframeAnimations.get(property);
            if (keyframeAnimation != null) {
                return keyframeAnimation.resolve(baseColor, currentTimeNanos);
            }
            Integer filledColor = filledColors.get(property);
            return filledColor == null ? baseColor : filledColor.intValue();
        }

        private float resolveFloat(DocumentAnimationProperty property, float baseValue, long currentTimeNanos) {
            FloatTransition transition = floatTransitions.get(property);
            if (transition != null) {
                return transition.resolve(currentTimeNanos);
            }
            FloatKeyframeAnimation keyframeAnimation = floatKeyframeAnimations.get(property);
            if (keyframeAnimation != null) {
                return keyframeAnimation.resolve(baseValue, currentTimeNanos);
            }
            Float filledValue = filledFloats.get(property);
            return filledValue == null ? baseValue : filledValue.floatValue();
        }

        private void setManualColorKeyframeAnimation(DocumentAnimationProperty property, int fromColor, int toColor,
                long startNanos, long durationNanos) {
            declaredColorKeyframeProperties.remove(property);
            filledColors.remove(property);
            colorKeyframeAnimations.put(property, new ColorKeyframeAnimation(
                    DocumentKeyframes.named("manual-color")
                            .setColor(property, fromColor, toColor)
                            .build()
                            .getColorTracks()
                            .get(property), startNanos,
                    durationNanos, 1, DocumentAnimationFillMode.NONE, DocumentAnimationTimingFunction.LINEAR));
        }

        private void setManualFloatKeyframeAnimation(DocumentAnimationProperty property, float fromValue,
                float toValue, long startNanos, long durationNanos) {
            declaredFloatKeyframeProperties.remove(property);
            filledFloats.remove(property);
            floatKeyframeAnimations.put(property, new FloatKeyframeAnimation(
                    DocumentKeyframes.named("manual-float")
                            .setFloat(property, fromValue, toValue)
                            .build()
                            .getFloatTracks()
                            .get(property), startNanos,
                    durationNanos, 1, DocumentAnimationFillMode.NONE, DocumentAnimationTimingFunction.LINEAR));
        }

        private void clearKeyframeAnimations() {
            colorKeyframeAnimations.clear();
            floatKeyframeAnimations.clear();
            filledColors.clear();
            filledFloats.clear();
            clearDeclaredKeyframeSignature();
        }

        private void suppressDeclaredColorKeyframeProperty(DocumentAnimationProperty property) {
            suppressDeclaredKeyframeProperty(declaredColorKeyframeProperties, colorKeyframeAnimations, filledColors,
                    property);
        }

        private void suppressDeclaredFloatKeyframeProperty(DocumentAnimationProperty property) {
            suppressDeclaredKeyframeProperty(declaredFloatKeyframeProperties, floatKeyframeAnimations, filledFloats,
                    property);
        }

        private void startDeclaredKeyframeAnimations(DocumentLayoutBox box, ComputedStyle style, String animationName,
                DocumentKeyframes keyframes, long currentTimeNanos) {
            clearDeclaredKeyframeAnimations();
            declaredAnimationName = animationName;
            declaredKeyframes = keyframes;
            declaredDurationNanos = style.getAnimationDurationNanos();
            declaredDelayNanos = style.getAnimationDelayNanos();
            declaredIterationCount = style.getAnimationIterationCount();
            declaredFillMode = style.getAnimationFillMode();
            declaredTimingFunction = style.getAnimationTimingFunction();
            declaredBoxWidth = box.getWidth();
            declaredBoxHeight = box.getHeight();
            long startNanos = currentTimeNanos + style.getAnimationDelayNanos();
            declaredStartNanos = startNanos;
            for (Map.Entry<DocumentAnimationProperty, DocumentKeyframes.ColorTrack> entry : keyframes.getColorTracks()
                    .entrySet()) {
                DocumentAnimationProperty property = entry.getKey();
                DocumentKeyframes.ColorTrack track = entry.getValue();
                colorKeyframeAnimations.put(property, new ColorKeyframeAnimation(track, startNanos,
                        style.getAnimationDurationNanos(), style.getAnimationIterationCount(),
                        style.getAnimationFillMode(), style.getAnimationTimingFunction()));
                declaredColorKeyframeProperties.add(property);
                filledColors.remove(property);
            }
            for (Map.Entry<DocumentAnimationProperty, DocumentKeyframes.FloatTrack> entry : keyframes.getFloatTracks()
                    .entrySet()) {
                DocumentAnimationProperty property = entry.getKey();
                DocumentKeyframes.FloatTrack track = entry.getValue();
                floatKeyframeAnimations.put(property, new FloatKeyframeAnimation(normalizeDeclaredKeyframeFloatTrack(box,
                        property, track), startNanos, style.getAnimationDurationNanos(),
                        style.getAnimationIterationCount(), style.getAnimationFillMode(),
                        style.getAnimationTimingFunction()));
                declaredFloatKeyframeProperties.add(property);
                filledFloats.remove(property);
            }
        }

        private boolean refreshDeclaredKeyframeGeometry(DocumentLayoutBox box) {
            if (declaredBoxWidth == box.getWidth() && declaredBoxHeight == box.getHeight()) {
                return false;
            }
            declaredBoxWidth = box.getWidth();
            declaredBoxHeight = box.getHeight();
            boolean changed = false;
            for (DocumentAnimationProperty property : declaredFloatKeyframeProperties) {
                DocumentKeyframes.FloatTrack track = declaredKeyframes.getFloatTracks().get(property);
                if (track == null) {
                    continue;
                }
                DocumentKeyframes.FloatTrack normalizedTrack = normalizeDeclaredKeyframeFloatTrack(box, property,
                        track);
                if (floatKeyframeAnimations.containsKey(property)) {
                    floatKeyframeAnimations.put(property, new FloatKeyframeAnimation(normalizedTrack,
                            declaredStartNanos, declaredDurationNanos, declaredIterationCount, declaredFillMode,
                            declaredTimingFunction));
                    changed = true;
                }
                if (filledFloats.containsKey(property)) {
                    float nextFilledValue = normalizedTrack.getLastValue();
                    if (Float.compare(filledFloats.get(property).floatValue(), nextFilledValue) != 0) {
                        filledFloats.put(property, Float.valueOf(nextFilledValue));
                        changed = true;
                    }
                }
            }
            return changed;
        }

        private boolean clearDeclaredKeyframeAnimations() {
            boolean changed = false;
            changed |= clearDeclaredKeyframeProperties(declaredColorKeyframeProperties, colorKeyframeAnimations,
                    filledColors);
            changed |= clearDeclaredKeyframeProperties(declaredFloatKeyframeProperties, floatKeyframeAnimations,
                    filledFloats);
            if (declaredAnimationName != null || declaredKeyframes != null) {
                changed = true;
            }
            clearDeclaredKeyframeSignature();
            return changed;
        }

        private boolean hasAnimationWork() {
            return !colorTransitions.isEmpty() || !floatTransitions.isEmpty()
                    || !colorKeyframeAnimations.isEmpty() || !floatKeyframeAnimations.isEmpty();
        }

        private boolean hasAnimationWork(DocumentAnimationImpact impact) {
            return containsPropertyWithImpact(colorTransitions, impact)
                    || containsPropertyWithImpact(floatTransitions, impact)
                    || containsPropertyWithImpact(colorKeyframeAnimations, impact)
                    || containsPropertyWithImpact(floatKeyframeAnimations, impact);
        }

        private boolean hasRuntimeValue(DocumentAnimationImpact impact) {
            return hasAnimationWork(impact) || containsPropertyWithImpact(filledColors, impact)
                    || containsPropertyWithImpact(filledFloats, impact);
        }

        private boolean hasRunningTransition(DocumentAnimationProperty property) {
            return colorTransitions.containsKey(property) || floatTransitions.containsKey(property);
        }

        private int countActiveAnimations(long currentTimeNanos) {
            int count = 0;
            count += countActiveTransitions(colorTransitions, currentTimeNanos);
            count += countActiveTransitions(floatTransitions, currentTimeNanos);
            count += countActiveKeyframeAnimations(colorKeyframeAnimations, currentTimeNanos);
            count += countActiveKeyframeAnimations(floatKeyframeAnimations, currentTimeNanos);
            return count;
        }

        private boolean pruneFinishedAnimations(long currentTimeNanos) {
            boolean changed = false;
            changed |= pruneFinishedTransitions(colorTransitions, currentTimeNanos);
            changed |= pruneFinishedTransitions(floatTransitions, currentTimeNanos);
            changed |= pruneFinishedKeyframeAnimations(colorKeyframeAnimations, filledColors, currentTimeNanos);
            changed |= pruneFinishedKeyframeAnimations(floatKeyframeAnimations, filledFloats, currentTimeNanos);
            return changed;
        }

        private boolean matchesDeclaredKeyframeSignature(String animationName, DocumentKeyframes keyframes,
                ComputedStyle style) {
            return Objects.equals(declaredAnimationName, animationName) && declaredKeyframes == keyframes
                    && declaredDurationNanos == style.getAnimationDurationNanos()
                    && declaredDelayNanos == style.getAnimationDelayNanos()
                    && declaredIterationCount == style.getAnimationIterationCount()
                    && declaredFillMode == style.getAnimationFillMode()
                    && declaredTimingFunction == style.getAnimationTimingFunction();
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
            declaredStartNanos = 0L;
            declaredBoxWidth = 0;
            declaredBoxHeight = 0;
        }

        private static boolean containsPropertyWithImpact(Map<DocumentAnimationProperty, ?> values,
                DocumentAnimationImpact impact) {
            for (DocumentAnimationProperty property : values.keySet()) {
                if (property.getImpact() == impact) {
                    return true;
                }
            }
            return false;
        }

        private static <T extends TransitionState> int countActiveTransitions(Map<DocumentAnimationProperty, T> values,
                long currentTimeNanos) {
            int count = 0;
            for (T transition : values.values()) {
                if (!transition.isFinished(currentTimeNanos)) {
                    count++;
                }
            }
            return count;
        }

        private static <T extends KeyframeAnimationState> int countActiveKeyframeAnimations(
                Map<DocumentAnimationProperty, T> values, long currentTimeNanos) {
            int count = 0;
            for (T animation : values.values()) {
                if (!animation.isFinished(currentTimeNanos)) {
                    count++;
                }
            }
            return count;
        }

        private static <T extends TransitionState> boolean pruneFinishedTransitions(
                Map<DocumentAnimationProperty, T> values, long currentTimeNanos) {
            boolean changed = false;
            Iterator<Map.Entry<DocumentAnimationProperty, T>> iterator = values.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<DocumentAnimationProperty, T> entry = iterator.next();
                if (entry.getValue().isFinished(currentTimeNanos)) {
                    iterator.remove();
                    changed = true;
                }
            }
            return changed;
        }

        private static <T, A extends FillingKeyframeAnimationState<T>> boolean pruneFinishedKeyframeAnimations(
                Map<DocumentAnimationProperty, A> animations,
                Map<DocumentAnimationProperty, T> filledValues,
                long currentTimeNanos) {
            boolean changed = false;
            Iterator<Map.Entry<DocumentAnimationProperty, A>> iterator = animations.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<DocumentAnimationProperty, A> entry = iterator.next();
                A animation = entry.getValue();
                if (animation.isFinished(currentTimeNanos)) {
                    if (animation.fillsForwards()) {
                        filledValues.put(entry.getKey(), animation.getFilledRuntimeValue());
                    }
                    iterator.remove();
                    changed = true;
                }
            }
            return changed;
        }

        private static <T, U> void suppressDeclaredKeyframeProperty(
                Set<DocumentAnimationProperty> declaredProperties,
                Map<DocumentAnimationProperty, T> keyframeAnimations,
                Map<DocumentAnimationProperty, U> filledValues,
                DocumentAnimationProperty property) {
            if (!declaredProperties.contains(property)) {
                return;
            }
            keyframeAnimations.remove(property);
            filledValues.remove(property);
        }

        private static <T, U> boolean clearDeclaredKeyframeProperties(
                Set<DocumentAnimationProperty> declaredProperties,
                Map<DocumentAnimationProperty, T> keyframeAnimations,
                Map<DocumentAnimationProperty, U> filledValues) {
            boolean changed = false;
            for (DocumentAnimationProperty property : declaredProperties) {
                changed |= keyframeAnimations.remove(property) != null;
                changed |= filledValues.remove(property) != null;
            }
            return changed;
        }
    }

    /**
     * 支持统一查询完成状态的 transition 运行对象。
     */
    private interface TransitionState {

        /**
         * 返回 transition 是否已完成。
         *
         * @param currentTimeNanos 当前动画时间
         * @return 是否已完成
         */
        boolean isFinished(long currentTimeNanos);
    }

    /**
     * 支持统一查询完成状态的 keyframe animation 运行对象。
     */
    private interface KeyframeAnimationState {

        /**
         * 返回 keyframe animation 是否已完成。
         *
         * @param currentTimeNanos 当前动画时间
         * @return 是否已完成
         */
        boolean isFinished(long currentTimeNanos);
    }

    /**
     * 支持 forwards fill 写回运行值的 keyframe animation 运行对象。
     *
     * @param <T> fill 运行值类型
     */
    private interface FillingKeyframeAnimationState<T> extends KeyframeAnimationState {

        /**
         * 返回是否应在结束后保留末帧运行值。
         *
         * @return 是否启用 forwards fill
         */
        boolean fillsForwards();

        /**
         * 返回结束后需要保留的运行值。
         *
         * @return fill 运行值
         */
        T getFilledRuntimeValue();
    }

    /**
     * 单个动画属性的运行时取值与 transition 限制规则。
     */
    private enum PropertyRuntimeSemantics {
        BACKGROUND_COLOR(DocumentAnimationProperty.BACKGROUND_COLOR) {
            @Override
            int resolveBaseColor(ComputedStyle style) {
                return style.getBackgroundColor();
            }
        },
        BORDER_COLOR(DocumentAnimationProperty.BORDER_COLOR) {
            @Override
            int resolveBaseColor(ComputedStyle style) {
                return style.getBorderColor();
            }
        },
        TEXT_COLOR(DocumentAnimationProperty.TEXT_COLOR) {
            @Override
            int resolveBaseColor(ComputedStyle style) {
                return style.getTextColor();
            }
        },
        OPACITY(DocumentAnimationProperty.OPACITY) {
            @Override
            float resolveBaseFloat(DocumentLayoutBox box) {
                return box.getComputedStyle().getOpacity();
            }

            @Override
            float normalizeDeclaredKeyframeFloat(DocumentLayoutBox box, float value) {
                return Math.max(0.0F, Math.min(1.0F, value));
            }
        },
        BORDER_RADIUS(DocumentAnimationProperty.BORDER_RADIUS) {
            @Override
            float resolveBaseFloat(DocumentLayoutBox box) {
                return resolveBorderRadius(box);
            }

            @Override
            float normalizeDeclaredKeyframeFloat(DocumentLayoutBox box, float value) {
                int limit = Math.min(box.getWidth(), box.getHeight());
                return Math.max(0.0F, Math.min(value, limit / 2.0F));
            }
        },
        BACKDROP_BLUR_RADIUS(DocumentAnimationProperty.BACKDROP_BLUR_RADIUS) {
            @Override
            float resolveBaseFloat(DocumentLayoutBox box) {
                return resolveBackdropBlurRadius(box);
            }

            @Override
            float normalizeDeclaredKeyframeFloat(DocumentLayoutBox box, float value) {
                return Math.max(0.0F, Math.min(value, DocumentEffectChain.MAX_BACKDROP_BLUR_RADIUS));
            }
        },
        WIDTH(DocumentAnimationProperty.WIDTH) {
            @Override
            float resolveBaseFloat(DocumentLayoutBox box) {
                return box.getContentWidth();
            }

            @Override
            boolean isFloatTransitionTargetAnimatable(DocumentLayoutBox box) {
                return isPixelLength(box.getComputedStyle().getWidth());
            }
        },
        HEIGHT(DocumentAnimationProperty.HEIGHT) {
            @Override
            float resolveBaseFloat(DocumentLayoutBox box) {
                return box.getContentHeight();
            }

            @Override
            boolean isFloatTransitionTargetAnimatable(DocumentLayoutBox box) {
                return isPixelLength(box.getComputedStyle().getHeight());
            }
        },
        MARGIN_LEFT(DocumentAnimationProperty.MARGIN_LEFT) {
            @Override
            float resolveBaseFloat(DocumentLayoutBox box) {
                return box.getMargin().getLeft();
            }

            @Override
            boolean isFloatTransitionTargetAnimatable(DocumentLayoutBox box) {
                return isPixelLength(box.getComputedStyle().getMargin().getLeft());
            }
        },
        MARGIN_RIGHT(DocumentAnimationProperty.MARGIN_RIGHT) {
            @Override
            float resolveBaseFloat(DocumentLayoutBox box) {
                return box.getMargin().getRight();
            }

            @Override
            boolean isFloatTransitionTargetAnimatable(DocumentLayoutBox box) {
                return isPixelLength(box.getComputedStyle().getMargin().getRight());
            }
        };

        private static final EnumMap<DocumentAnimationProperty, PropertyRuntimeSemantics> BY_PROPERTY =
                createLookup();

        private final DocumentAnimationProperty property;

        private PropertyRuntimeSemantics(DocumentAnimationProperty property) {
            this.property = property;
        }

        private static PropertyRuntimeSemantics forProperty(DocumentAnimationProperty property) {
            PropertyRuntimeSemantics semantics = BY_PROPERTY.get(Objects.requireNonNull(property, "property"));
            if (semantics == null) {
                throw new IllegalArgumentException("unsupported animation property: " + property);
            }
            return semantics;
        }

        private static EnumMap<DocumentAnimationProperty, PropertyRuntimeSemantics> createLookup() {
            EnumMap<DocumentAnimationProperty, PropertyRuntimeSemantics> lookup =
                    new EnumMap<DocumentAnimationProperty, PropertyRuntimeSemantics>(DocumentAnimationProperty.class);
            for (PropertyRuntimeSemantics semantics : values()) {
                lookup.put(semantics.property, semantics);
            }
            return lookup;
        }

        private static boolean isPixelLength(UiStyleLength length) {
            return length.getType() == UiStyleLength.Type.PIXEL;
        }

        int resolveBaseColor(ComputedStyle style) {
            throw new IllegalArgumentException("color value is not supported for: " + property);
        }

        float resolveBaseFloat(DocumentLayoutBox box) {
            throw new IllegalArgumentException("float value is not supported for: " + property);
        }

        boolean isFloatTransitionTargetAnimatable(DocumentLayoutBox box) {
            return true;
        }

        float normalizeDeclaredKeyframeFloat(DocumentLayoutBox box, float value) {
            return value;
        }
    }

    /**
     * 单个颜色 transition。
     */
    private static final class ColorTransition implements TransitionState {

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

        @Override
        public boolean isFinished(long currentTimeNanos) {
            return currentTimeNanos >= startNanos + durationNanos;
        }
    }

    /**
     * 单个数值 transition。
     */
    private static final class FloatTransition implements TransitionState {

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

        @Override
        public boolean isFinished(long currentTimeNanos) {
            return currentTimeNanos >= startNanos + durationNanos;
        }
    }

    /**
     * 单个颜色 keyframe animation。
     */
    private static final class ColorKeyframeAnimation implements FillingKeyframeAnimationState<Integer> {

        private final DocumentKeyframes.ColorTrack track;
        private final long startNanos;
        private final long durationNanos;
        private final int iterationCount;
        private final DocumentAnimationFillMode fillMode;
        private final DocumentAnimationTimingFunction timingFunction;

        private ColorKeyframeAnimation(DocumentKeyframes.ColorTrack track, long startNanos, long durationNanos,
                int iterationCount, DocumentAnimationFillMode fillMode, DocumentAnimationTimingFunction timingFunction) {
            this.track = Objects.requireNonNull(track, "track");
            this.startNanos = startNanos;
            this.durationNanos = Math.max(1L, durationNanos);
            this.iterationCount = Math.max(1, iterationCount);
            this.fillMode = fillMode == null ? DocumentAnimationFillMode.NONE : fillMode;
            this.timingFunction = timingFunction == null ? DocumentAnimationTimingFunction.LINEAR : timingFunction;
        }

        private int resolve(int baseColor, long currentTimeNanos) {
            if (currentTimeNanos <= startNanos) {
                return fillsBackwards() ? track.getFirstColor() : baseColor;
            }
            long activeDurationNanos = getActiveDurationNanos();
            long elapsedNanos = currentTimeNanos - startNanos;
            if (elapsedNanos >= activeDurationNanos) {
                return fillsForwards() ? track.getLastColor() : baseColor;
            }
            long iterationElapsedNanos = elapsedNanos % durationNanos;
            return resolveColorTrack(track, iterationElapsedNanos / (float) durationNanos, timingFunction);
        }

        @Override
        public boolean isFinished(long currentTimeNanos) {
            return currentTimeNanos >= startNanos + getActiveDurationNanos();
        }

        private boolean fillsBackwards() {
            return fillMode == DocumentAnimationFillMode.BACKWARDS || fillMode == DocumentAnimationFillMode.BOTH;
        }

        @Override
        public boolean fillsForwards() {
            return fillMode == DocumentAnimationFillMode.FORWARDS || fillMode == DocumentAnimationFillMode.BOTH;
        }

        @Override
        public Integer getFilledRuntimeValue() {
            return Integer.valueOf(track.getLastColor());
        }

        private long getActiveDurationNanos() {
            return durationNanos * iterationCount;
        }
    }

    /**
     * 单个数值 keyframe animation。
     */
    private static final class FloatKeyframeAnimation implements FillingKeyframeAnimationState<Float> {

        private final DocumentKeyframes.FloatTrack track;
        private final long startNanos;
        private final long durationNanos;
        private final int iterationCount;
        private final DocumentAnimationFillMode fillMode;
        private final DocumentAnimationTimingFunction timingFunction;

        private FloatKeyframeAnimation(DocumentKeyframes.FloatTrack track, long startNanos, long durationNanos,
                int iterationCount, DocumentAnimationFillMode fillMode, DocumentAnimationTimingFunction timingFunction) {
            this.track = Objects.requireNonNull(track, "track");
            this.startNanos = startNanos;
            this.durationNanos = Math.max(1L, durationNanos);
            this.iterationCount = Math.max(1, iterationCount);
            this.fillMode = fillMode == null ? DocumentAnimationFillMode.NONE : fillMode;
            this.timingFunction = timingFunction == null ? DocumentAnimationTimingFunction.LINEAR : timingFunction;
        }

        private float resolve(float baseValue, long currentTimeNanos) {
            if (currentTimeNanos <= startNanos) {
                return fillsBackwards() ? track.getFirstValue() : baseValue;
            }
            long activeDurationNanos = getActiveDurationNanos();
            long elapsedNanos = currentTimeNanos - startNanos;
            if (elapsedNanos >= activeDurationNanos) {
                return fillsForwards() ? track.getLastValue() : baseValue;
            }
            long iterationElapsedNanos = elapsedNanos % durationNanos;
            return resolveFloatTrack(track, iterationElapsedNanos / (float) durationNanos, timingFunction);
        }

        @Override
        public boolean isFinished(long currentTimeNanos) {
            return currentTimeNanos >= startNanos + getActiveDurationNanos();
        }

        private boolean fillsBackwards() {
            return fillMode == DocumentAnimationFillMode.BACKWARDS || fillMode == DocumentAnimationFillMode.BOTH;
        }

        @Override
        public boolean fillsForwards() {
            return fillMode == DocumentAnimationFillMode.FORWARDS || fillMode == DocumentAnimationFillMode.BOTH;
        }

        @Override
        public Float getFilledRuntimeValue() {
            return Float.valueOf(track.getLastValue());
        }

        private long getActiveDurationNanos() {
            return durationNanos * iterationCount;
        }
    }
}
