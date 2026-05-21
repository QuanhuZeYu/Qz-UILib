package club.heiqi.uilib.ui.animation;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.style.cascade.ComputedStyle;

/**
 * 单元素动画运行态。
 *
 * <p>承载 transition、keyframe animation 与 forwards fill 的具体状态机，
 * 让 {@link DocumentAnimationTimeline} 只负责按元素组织时间线。</p>
 */
final class DocumentAnimationRuntimeState {

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

    int resolveColor(DocumentAnimationProperty property, int baseColor, long currentTimeNanos) {
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

    float resolveFloat(DocumentAnimationProperty property, float baseValue, long currentTimeNanos) {
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

    void setManualColorKeyframeAnimation(DocumentAnimationProperty property, int fromColor, int toColor,
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

    void setManualFloatKeyframeAnimation(DocumentAnimationProperty property, float fromValue,
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

    void clearKeyframeAnimations() {
        colorKeyframeAnimations.clear();
        floatKeyframeAnimations.clear();
        filledColors.clear();
        filledFloats.clear();
        clearDeclaredKeyframeSignature();
    }

    void suppressDeclaredColorKeyframeProperty(DocumentAnimationProperty property) {
        suppressDeclaredKeyframeProperty(declaredColorKeyframeProperties, colorKeyframeAnimations, filledColors,
                property);
    }

    void suppressDeclaredFloatKeyframeProperty(DocumentAnimationProperty property) {
        suppressDeclaredKeyframeProperty(declaredFloatKeyframeProperties, floatKeyframeAnimations, filledFloats,
                property);
    }

    void startDeclaredKeyframeAnimations(DocumentLayoutBox box, ComputedStyle style, String animationName,
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

    boolean refreshDeclaredKeyframeGeometry(DocumentLayoutBox box) {
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
            DocumentKeyframes.FloatTrack normalizedTrack = normalizeDeclaredKeyframeFloatTrack(box, property, track);
            if (floatKeyframeAnimations.containsKey(property)) {
                floatKeyframeAnimations.put(property, new FloatKeyframeAnimation(normalizedTrack, declaredStartNanos,
                        declaredDurationNanos, declaredIterationCount, declaredFillMode, declaredTimingFunction));
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

    boolean clearDeclaredKeyframeAnimations() {
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

    boolean hasAnimationWork() {
        return !colorTransitions.isEmpty() || !floatTransitions.isEmpty()
                || !colorKeyframeAnimations.isEmpty() || !floatKeyframeAnimations.isEmpty();
    }

    boolean hasAnimationWork(DocumentAnimationImpact impact) {
        return containsPropertyWithImpact(colorTransitions, impact)
                || containsPropertyWithImpact(floatTransitions, impact)
                || containsPropertyWithImpact(colorKeyframeAnimations, impact)
                || containsPropertyWithImpact(floatKeyframeAnimations, impact);
    }

    boolean hasRuntimeValue(DocumentAnimationImpact impact) {
        return hasAnimationWork(impact) || containsPropertyWithImpact(filledColors, impact)
                || containsPropertyWithImpact(filledFloats, impact);
    }

    boolean hasRunningTransition(DocumentAnimationProperty property) {
        return colorTransitions.containsKey(property) || floatTransitions.containsKey(property);
    }

    int countActiveAnimations(long currentTimeNanos) {
        int count = 0;
        count += countActiveTransitions(colorTransitions, currentTimeNanos);
        count += countActiveTransitions(floatTransitions, currentTimeNanos);
        count += countActiveKeyframeAnimations(colorKeyframeAnimations, currentTimeNanos);
        count += countActiveKeyframeAnimations(floatKeyframeAnimations, currentTimeNanos);
        return count;
    }

    void collectDiagnostics(DocumentAnimationTimeline.DiagnosticsSnapshot snapshot, long currentTimeNanos) {
        collectTransitionDiagnostics(snapshot, colorTransitions, currentTimeNanos);
        collectTransitionDiagnostics(snapshot, floatTransitions, currentTimeNanos);
        collectKeyframeDiagnostics(snapshot, colorKeyframeAnimations, currentTimeNanos);
        collectKeyframeDiagnostics(snapshot, floatKeyframeAnimations, currentTimeNanos);
        collectFillDiagnostics(snapshot, filledColors);
        collectFillDiagnostics(snapshot, filledFloats);
    }

    void pruneFinishedAnimations(ElementNode element, long currentTimeNanos,
            DocumentAnimationTimeline.PruneResult result) {
        pruneFinishedTransitions(element, colorTransitions, currentTimeNanos, result);
        pruneFinishedTransitions(element, floatTransitions, currentTimeNanos, result);
        boolean colorKeyframeFinished = pruneFinishedKeyframeAnimations(colorKeyframeAnimations, filledColors,
                currentTimeNanos, result);
        boolean floatKeyframeFinished = pruneFinishedKeyframeAnimations(floatKeyframeAnimations, filledFloats,
                currentTimeNanos, result);
        if ((colorKeyframeFinished || floatKeyframeFinished) && declaredAnimationName != null) {
            result.getAnimationEndRecords().add(new DocumentAnimationTimeline.AnimationEndRecord(element,
                    declaredAnimationName, declaredDurationNanos * Math.max(1, declaredIterationCount)));
            result.markChanged();
        }
    }

    boolean matchesDeclaredKeyframeSignature(String animationName, DocumentKeyframes keyframes, ComputedStyle style) {
        return Objects.equals(declaredAnimationName, animationName) && declaredKeyframes == keyframes
                && declaredDurationNanos == style.getAnimationDurationNanos()
                && declaredDelayNanos == style.getAnimationDelayNanos()
                && declaredIterationCount == style.getAnimationIterationCount()
                && declaredFillMode == style.getAnimationFillMode()
                && declaredTimingFunction == style.getAnimationTimingFunction();
    }

    void setColorTransition(DocumentAnimationProperty property, int fromColor, int toColor, long startNanos,
            long durationNanos, DocumentAnimationTimingFunction timingFunction) {
        colorTransitions.put(property, new ColorTransition(fromColor, toColor, startNanos, durationNanos,
                timingFunction));
    }

    void clearColorTransition(DocumentAnimationProperty property) {
        colorTransitions.remove(property);
    }

    boolean removeColorTransition(DocumentAnimationProperty property) {
        return colorTransitions.remove(property) != null;
    }

    Integer getTargetColor(DocumentAnimationProperty property) {
        return targetColors.get(property);
    }

    void putTargetColor(DocumentAnimationProperty property, int color) {
        targetColors.put(property, Integer.valueOf(color));
    }

    void setFloatTransition(DocumentAnimationProperty property, float fromValue, float toValue, long startNanos,
            long durationNanos, DocumentAnimationTimingFunction timingFunction) {
        floatTransitions.put(property, new FloatTransition(fromValue, toValue, startNanos, durationNanos,
                timingFunction));
    }

    void clearFloatTransition(DocumentAnimationProperty property) {
        floatTransitions.remove(property);
    }

    boolean removeFloatTransition(DocumentAnimationProperty property) {
        return floatTransitions.remove(property) != null;
    }

    Float getTargetFloat(DocumentAnimationProperty property) {
        return targetFloats.get(property);
    }

    void putTargetFloat(DocumentAnimationProperty property, float value) {
        targetFloats.put(property, Float.valueOf(value));
    }

    Boolean getTargetFloatTransitionable(DocumentAnimationProperty property) {
        return targetFloatTransitionable.get(property);
    }

    void putTargetFloatTransitionable(DocumentAnimationProperty property, boolean transitionable) {
        targetFloatTransitionable.put(property, Boolean.valueOf(transitionable));
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

    private static <T extends TransitionState> void collectTransitionDiagnostics(
            DocumentAnimationTimeline.DiagnosticsSnapshot snapshot, Map<DocumentAnimationProperty, T> values,
            long currentTimeNanos) {
        for (Map.Entry<DocumentAnimationProperty, T> entry : values.entrySet()) {
            if (!entry.getValue().isFinished(currentTimeNanos)) {
                snapshot.incrementTransition(entry.getKey().getImpact());
            }
        }
    }

    private static <T extends KeyframeAnimationState> void collectKeyframeDiagnostics(
            DocumentAnimationTimeline.DiagnosticsSnapshot snapshot, Map<DocumentAnimationProperty, T> values,
            long currentTimeNanos) {
        for (Map.Entry<DocumentAnimationProperty, T> entry : values.entrySet()) {
            if (!entry.getValue().isFinished(currentTimeNanos)) {
                snapshot.incrementKeyframe(entry.getKey().getImpact());
            }
        }
    }

    private static void collectFillDiagnostics(DocumentAnimationTimeline.DiagnosticsSnapshot snapshot,
            Map<DocumentAnimationProperty, ?> values) {
        for (DocumentAnimationProperty property : values.keySet()) {
            snapshot.incrementForwardsFill(property.getImpact());
        }
    }

    private static <T extends TransitionState> void pruneFinishedTransitions(ElementNode element,
            Map<DocumentAnimationProperty, T> values, long currentTimeNanos,
            DocumentAnimationTimeline.PruneResult result) {
        Iterator<Map.Entry<DocumentAnimationProperty, T>> iterator = values.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<DocumentAnimationProperty, T> entry = iterator.next();
            if (entry.getValue().isFinished(currentTimeNanos)) {
                result.getTransitionEndRecords().add(new DocumentAnimationTimeline.TransitionEndRecord(element,
                        entry.getKey(), entry.getValue().getDurationNanos()));
                iterator.remove();
                result.markChanged();
            }
        }
    }

    private static <T, A extends FillingKeyframeAnimationState<T>> boolean pruneFinishedKeyframeAnimations(
            Map<DocumentAnimationProperty, A> animations, Map<DocumentAnimationProperty, T> filledValues,
            long currentTimeNanos, DocumentAnimationTimeline.PruneResult result) {
        boolean finished = false;
        Iterator<Map.Entry<DocumentAnimationProperty, A>> iterator = animations.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<DocumentAnimationProperty, A> entry = iterator.next();
            A animation = entry.getValue();
            if (animation.isFinished(currentTimeNanos)) {
                if (animation.fillsForwards()) {
                    filledValues.put(entry.getKey(), animation.getFilledRuntimeValue());
                }
                iterator.remove();
                finished = true;
                result.markChanged();
            }
        }
        return finished;
    }

    private static <T, U> void suppressDeclaredKeyframeProperty(Set<DocumentAnimationProperty> declaredProperties,
            Map<DocumentAnimationProperty, T> keyframeAnimations, Map<DocumentAnimationProperty, U> filledValues,
            DocumentAnimationProperty property) {
        if (!declaredProperties.contains(property)) {
            return;
        }
        keyframeAnimations.remove(property);
        filledValues.remove(property);
    }

    private static <T, U> boolean clearDeclaredKeyframeProperties(Set<DocumentAnimationProperty> declaredProperties,
            Map<DocumentAnimationProperty, T> keyframeAnimations, Map<DocumentAnimationProperty, U> filledValues) {
        boolean changed = false;
        for (DocumentAnimationProperty property : declaredProperties) {
            changed |= keyframeAnimations.remove(property) != null;
            changed |= filledValues.remove(property) != null;
        }
        return changed;
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
        java.util.List<DocumentKeyframes.ColorStop> stops = track.getStops();
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
        java.util.List<DocumentKeyframes.FloatStop> stops = track.getStops();
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

    /** 支持统一查询完成状态的 transition 运行对象。 */
    private interface TransitionState {
        boolean isFinished(long currentTimeNanos);

        long getDurationNanos();
    }

    /** 支持统一查询完成状态的 keyframe animation 运行对象。 */
    private interface KeyframeAnimationState {
        boolean isFinished(long currentTimeNanos);
    }

    /** 支持 forwards fill 写回运行值的 keyframe animation 运行对象。 */
    private interface FillingKeyframeAnimationState<T> extends KeyframeAnimationState {
        boolean fillsForwards();

        T getFilledRuntimeValue();
    }

    /** 单个颜色 transition。 */
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

        @Override
        public long getDurationNanos() {
            return durationNanos;
        }
    }

    /** 单个数值 transition。 */
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

        @Override
        public long getDurationNanos() {
            return durationNanos;
        }
    }

    /** 单个颜色 keyframe animation。 */
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

    /** 单个数值 keyframe animation。 */
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
