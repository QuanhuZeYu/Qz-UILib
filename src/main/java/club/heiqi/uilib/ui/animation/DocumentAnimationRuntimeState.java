package club.heiqi.uilib.ui.animation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.style.cascade.ComputedStyle;
import club.heiqi.uilib.ui.style.props.UiAnimationDirection;

/**
 * 单元素动画运行态。
 *
 * <p>承载 transition、keyframe animation 与 forwards fill 的具体状态机，
 * 让 {@link DocumentAnimationTimeline} 只负责按元素组织时间线。</p>
 */
final class DocumentAnimationRuntimeState {

    private static final long DECLARED_KEYFRAME_OWNER_ID = -1L;
    private static final long MANUAL_KEYFRAME_OWNER_ID = 0L;

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
    private final List<TransitionCancelSnapshot> pendingTransitionCancelSnapshots =
            new ArrayList<TransitionCancelSnapshot>();
    private String declaredAnimationName;
    private DocumentKeyframes declaredKeyframes;
    private long declaredDurationNanos;
    private long declaredDelayNanos;
    private int declaredIterationCount;
    private DocumentAnimationFillMode declaredFillMode;
    private DocumentAnimationTimingFunction declaredTimingFunction;
    private UiAnimationDirection declaredAnimationDirection;
    private long declaredStartNanos;
    private boolean declaredStartEventDispatched;
    private long declaredLastIterationEventIndex;
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
                durationNanos, 1, DocumentAnimationFillMode.NONE, DocumentAnimationTimingFunction.LINEAR,
                UiAnimationDirection.NORMAL, MANUAL_KEYFRAME_OWNER_ID, "manual-color"));
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
                durationNanos, 1, DocumentAnimationFillMode.NONE, DocumentAnimationTimingFunction.LINEAR,
                UiAnimationDirection.NORMAL, MANUAL_KEYFRAME_OWNER_ID, "manual-float"));
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
        declaredAnimationDirection = style.getAnimationDirection();
        declaredBoxWidth = box.getWidth();
        declaredBoxHeight = box.getHeight();
        long startNanos = currentTimeNanos + style.getAnimationDelayNanos();
        declaredStartNanos = startNanos;
        declaredStartEventDispatched = false;
        declaredLastIterationEventIndex = 0L;
        for (Map.Entry<DocumentAnimationProperty, DocumentKeyframes.ColorTrack> entry : keyframes.getColorTracks()
                .entrySet()) {
            DocumentAnimationProperty property = entry.getKey();
            DocumentKeyframes.ColorTrack track = entry.getValue();
            colorKeyframeAnimations.put(property, new ColorKeyframeAnimation(track, startNanos,
                    style.getAnimationDurationNanos(), style.getAnimationIterationCount(),
                    style.getAnimationFillMode(), style.getAnimationTimingFunction(), style.getAnimationDirection(),
                    DECLARED_KEYFRAME_OWNER_ID, animationName));
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
                    style.getAnimationTimingFunction(), style.getAnimationDirection(), DECLARED_KEYFRAME_OWNER_ID,
                    animationName));
            declaredFloatKeyframeProperties.add(property);
            filledFloats.remove(property);
        }
    }

    void startImperativeKeyframeAnimations(DocumentLayoutBox box, DocumentKeyframes keyframes, long startNanos,
            DocumentAnimationOptions options, long ownerId) {
        Objects.requireNonNull(box, "box");
        Objects.requireNonNull(keyframes, "keyframes");
        Objects.requireNonNull(options, "options");
        for (Map.Entry<DocumentAnimationProperty, DocumentKeyframes.ColorTrack> entry : keyframes.getColorTracks()
                .entrySet()) {
            DocumentAnimationProperty property = entry.getKey();
            declaredColorKeyframeProperties.remove(property);
            filledColors.remove(property);
            colorKeyframeAnimations.put(property, new ColorKeyframeAnimation(entry.getValue(), startNanos,
                    options.getDurationNanos(), options.getIterationCount(), options.getFillMode(),
                    options.getTimingFunction(), options.getDirection(), ownerId, keyframes.getName()));
        }
        for (Map.Entry<DocumentAnimationProperty, DocumentKeyframes.FloatTrack> entry : keyframes.getFloatTracks()
                .entrySet()) {
            DocumentAnimationProperty property = entry.getKey();
            declaredFloatKeyframeProperties.remove(property);
            filledFloats.remove(property);
            floatKeyframeAnimations.put(property, new FloatKeyframeAnimation(normalizeDeclaredKeyframeFloatTrack(box,
                    property, entry.getValue()), startNanos, options.getDurationNanos(), options.getIterationCount(),
                    options.getFillMode(), options.getTimingFunction(), options.getDirection(), ownerId,
                    keyframes.getName()));
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
                        declaredDurationNanos, declaredIterationCount, declaredFillMode, declaredTimingFunction,
                        declaredAnimationDirection, DECLARED_KEYFRAME_OWNER_ID, declaredAnimationName));
                changed = true;
            }
            if (filledFloats.containsKey(property)) {
                float nextFilledValue = resolveFilledFloatValue(normalizedTrack, declaredIterationCount,
                        declaredTimingFunction, declaredAnimationDirection);
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

    boolean hasAnimationWork(DocumentAnimationProperty property) {
        return colorTransitions.containsKey(property)
                || floatTransitions.containsKey(property)
                || colorKeyframeAnimations.containsKey(property)
                || floatKeyframeAnimations.containsKey(property);
    }

    boolean hasRuntimeValue(DocumentAnimationImpact impact) {
        return hasAnimationWork(impact) || containsPropertyWithImpact(filledColors, impact)
                || containsPropertyWithImpact(filledFloats, impact);
    }

    boolean hasRuntimeValue(DocumentAnimationProperty property) {
        return hasAnimationWork(property) || filledColors.containsKey(property) || filledFloats.containsKey(property);
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
        collectLifecycleEvents(element, currentTimeNanos, result);
        boolean declaredHadKeyframeWork = hasDeclaredKeyframeAnimationWork();
        pruneFinishedTransitions(element, colorTransitions, currentTimeNanos, result);
        pruneFinishedTransitions(element, floatTransitions, currentTimeNanos, result);
        pruneFinishedKeyframeAnimations(colorKeyframeAnimations, filledColors, currentTimeNanos, result);
        pruneFinishedKeyframeAnimations(floatKeyframeAnimations, filledFloats, currentTimeNanos, result);
        if (declaredHadKeyframeWork && !hasDeclaredKeyframeAnimationWork() && declaredAnimationName != null) {
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
                && Objects.equals(declaredTimingFunction, style.getAnimationTimingFunction())
                && declaredAnimationDirection == style.getAnimationDirection();
    }

    void setColorTransition(DocumentAnimationProperty property, int fromColor, int toColor, long startNanos,
            long durationNanos, DocumentAnimationTimingFunction timingFunction, long currentTimeNanos) {
        cancelTransitionIfRunning(property, colorTransitions.remove(property), currentTimeNanos);
        colorTransitions.put(property, new ColorTransition(fromColor, toColor, startNanos, durationNanos,
                timingFunction));
    }

    void clearColorTransition(DocumentAnimationProperty property, long currentTimeNanos) {
        cancelTransitionIfRunning(property, colorTransitions.remove(property), currentTimeNanos);
    }

    boolean removeColorTransition(DocumentAnimationProperty property, long currentTimeNanos) {
        ColorTransition transition = colorTransitions.remove(property);
        cancelTransitionIfRunning(property, transition, currentTimeNanos);
        return transition != null;
    }

    Integer getTargetColor(DocumentAnimationProperty property) {
        return targetColors.get(property);
    }

    void putTargetColor(DocumentAnimationProperty property, int color) {
        targetColors.put(property, Integer.valueOf(color));
    }

    void setFloatTransition(DocumentAnimationProperty property, float fromValue, float toValue, long startNanos,
            long durationNanos, DocumentAnimationTimingFunction timingFunction, long currentTimeNanos) {
        cancelTransitionIfRunning(property, floatTransitions.remove(property), currentTimeNanos);
        floatTransitions.put(property, new FloatTransition(fromValue, toValue, startNanos, durationNanos,
                timingFunction));
    }

    void clearFloatTransition(DocumentAnimationProperty property, long currentTimeNanos) {
        cancelTransitionIfRunning(property, floatTransitions.remove(property), currentTimeNanos);
    }

    boolean removeFloatTransition(DocumentAnimationProperty property, long currentTimeNanos) {
        FloatTransition transition = floatTransitions.remove(property);
        cancelTransitionIfRunning(property, transition, currentTimeNanos);
        return transition != null;
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

    boolean cancelKeyframeAnimationsByOwner(long ownerId) {
        boolean changed = removeKeyframeAnimationsByOwner(colorKeyframeAnimations, ownerId);
        changed |= removeKeyframeAnimationsByOwner(floatKeyframeAnimations, ownerId);
        return changed;
    }

    boolean hasKeyframeAnimationOwner(long ownerId) {
        return containsKeyframeAnimationOwner(colorKeyframeAnimations, ownerId)
                || containsKeyframeAnimationOwner(floatKeyframeAnimations, ownerId);
    }

    void collectRunningTransitionCancelRecords(ElementNode element, long currentTimeNanos,
            List<DocumentAnimationTimeline.TransitionCancelRecord> records) {
        drainPendingTransitionCancelRecords(element, records);
        collectRunningTransitionCancelRecords(element, colorTransitions, currentTimeNanos, records);
        collectRunningTransitionCancelRecords(element, floatTransitions, currentTimeNanos, records);
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
        declaredAnimationDirection = null;
        declaredStartNanos = 0L;
        declaredStartEventDispatched = false;
        declaredLastIterationEventIndex = 0L;
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

    private void collectLifecycleEvents(ElementNode element, long currentTimeNanos,
            DocumentAnimationTimeline.PruneResult result) {
        drainPendingTransitionCancelRecords(element, result);
        collectTransitionStartRecords(element, colorTransitions, currentTimeNanos, result);
        collectTransitionStartRecords(element, floatTransitions, currentTimeNanos, result);
        collectDeclaredAnimationLifecycleRecords(element, currentTimeNanos, result);
    }

    private void collectDeclaredAnimationLifecycleRecords(ElementNode element, long currentTimeNanos,
            DocumentAnimationTimeline.PruneResult result) {
        if (declaredAnimationName == null || !hasDeclaredKeyframeAnimationWork() || currentTimeNanos < declaredStartNanos) {
            return;
        }
        if (!declaredStartEventDispatched) {
            result.getAnimationStartRecords().add(new DocumentAnimationTimeline.AnimationStartRecord(element,
                    declaredAnimationName, 0L));
            declaredStartEventDispatched = true;
        }
        long elapsedNanos = currentTimeNanos - declaredStartNanos;
        long durationNanos = Math.max(1L, declaredDurationNanos);
        long completedIterations = elapsedNanos / durationNanos;
        long maxIterationEventIndex = completedIterations;
        if (declaredIterationCount > 0 && completedIterations >= declaredIterationCount) {
            maxIterationEventIndex = Math.max(0L, declaredIterationCount - 1L);
        }
        for (long iterationIndex = declaredLastIterationEventIndex + 1L;
                iterationIndex <= maxIterationEventIndex; iterationIndex++) {
            if (iterationIndex <= 0L) {
                continue;
            }
            result.getAnimationIterationRecords().add(new DocumentAnimationTimeline.AnimationIterationRecord(element,
                    declaredAnimationName, durationNanos * iterationIndex, iterationIndex));
            declaredLastIterationEventIndex = iterationIndex;
        }
    }

    private boolean hasDeclaredKeyframeAnimationWork() {
        return containsAnyKey(colorKeyframeAnimations, declaredColorKeyframeProperties)
                || containsAnyKey(floatKeyframeAnimations, declaredFloatKeyframeProperties);
    }

    private void drainPendingTransitionCancelRecords(ElementNode element,
            DocumentAnimationTimeline.PruneResult result) {
        if (pendingTransitionCancelSnapshots.isEmpty()) {
            return;
        }
        drainPendingTransitionCancelRecords(element, result.getTransitionCancelRecords());
    }

    private void drainPendingTransitionCancelRecords(ElementNode element,
            List<DocumentAnimationTimeline.TransitionCancelRecord> records) {
        for (TransitionCancelSnapshot snapshot : pendingTransitionCancelSnapshots) {
            records.add(new DocumentAnimationTimeline.TransitionCancelRecord(element, snapshot.property,
                    snapshot.elapsedTimeNanos));
        }
        pendingTransitionCancelSnapshots.clear();
    }

    private void cancelTransitionIfRunning(DocumentAnimationProperty property, TransitionState transition,
            long currentTimeNanos) {
        if (transition == null || transition.isFinished(currentTimeNanos)) {
            return;
        }
        pendingTransitionCancelSnapshots.add(new TransitionCancelSnapshot(property,
                transition.getElapsedTimeNanos(currentTimeNanos)));
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

    private static boolean containsAnyKey(Map<DocumentAnimationProperty, ?> values,
            Set<DocumentAnimationProperty> keys) {
        for (DocumentAnimationProperty property : keys) {
            if (values.containsKey(property)) {
                return true;
            }
        }
        return false;
    }

    private static <T extends KeyframeAnimationState> boolean containsKeyframeAnimationOwner(
            Map<DocumentAnimationProperty, T> values, long ownerId) {
        for (T animation : values.values()) {
            if (animation.getOwnerId() == ownerId) {
                return true;
            }
        }
        return false;
    }

    private static <T extends KeyframeAnimationState> boolean removeKeyframeAnimationsByOwner(
            Map<DocumentAnimationProperty, T> values, long ownerId) {
        boolean changed = false;
        Iterator<Map.Entry<DocumentAnimationProperty, T>> iterator = values.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().getOwnerId() == ownerId) {
                iterator.remove();
                changed = true;
            }
        }
        return changed;
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

    private static <T extends TransitionState> void collectTransitionStartRecords(ElementNode element,
            Map<DocumentAnimationProperty, T> values, long currentTimeNanos,
            DocumentAnimationTimeline.PruneResult result) {
        for (Map.Entry<DocumentAnimationProperty, T> entry : values.entrySet()) {
            T transition = entry.getValue();
            if (transition.shouldDispatchStart(currentTimeNanos)) {
                result.getTransitionStartRecords().add(new DocumentAnimationTimeline.TransitionStartRecord(element,
                        entry.getKey(), transition.getElapsedTimeNanos(currentTimeNanos)));
                transition.markStartEventDispatched();
            }
        }
    }

    private static <T extends TransitionState> void collectRunningTransitionCancelRecords(ElementNode element,
            Map<DocumentAnimationProperty, T> values, long currentTimeNanos,
            List<DocumentAnimationTimeline.TransitionCancelRecord> records) {
        for (Map.Entry<DocumentAnimationProperty, T> entry : values.entrySet()) {
            T transition = entry.getValue();
            if (!transition.isFinished(currentTimeNanos)) {
                records.add(new DocumentAnimationTimeline.TransitionCancelRecord(element, entry.getKey(),
                        transition.getElapsedTimeNanos(currentTimeNanos)));
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

    private static boolean isReverseIteration(UiAnimationDirection direction, long iterationIndex) {
        if (direction == null) {
            return false;
        }
        switch (direction) {
            case REVERSE: return true;
            case ALTERNATE: return (iterationIndex & 1L) == 1L;
            case ALTERNATE_REVERSE: return (iterationIndex & 1L) == 0L;
            default: return false;
        }
    }

    private static float resolveDirectionalProgress(UiAnimationDirection direction, long iterationIndex,
            float iterationProgress) {
        float clampedProgress = Math.max(0.0F, Math.min(1.0F, iterationProgress));
        return isReverseIteration(direction, iterationIndex) ? 1.0F - clampedProgress : clampedProgress;
    }

    private static float resolveFilledFloatValue(DocumentKeyframes.FloatTrack track, int iterationCount,
            DocumentAnimationTimingFunction timingFunction, UiAnimationDirection direction) {
        long finalIterationIndex = Math.max(1, iterationCount) - 1L;
        float progress = resolveDirectionalProgress(direction, finalIterationIndex, 1.0F);
        return resolveFloatTrack(track, progress,
                timingFunction == null ? DocumentAnimationTimingFunction.LINEAR : timingFunction);
    }

    private static long resolveActiveDurationNanos(long durationNanos, int iterationCount) {
        if (iterationCount <= 0) {
            return Long.MAX_VALUE;
        }
        long safeDuration = Math.max(1L, durationNanos);
        long maxIterations = Long.MAX_VALUE / safeDuration;
        if (iterationCount > maxIterations) {
            return Long.MAX_VALUE;
        }
        return safeDuration * iterationCount;
    }

    /** 已取消 transition 的事件快照。 */
    private static final class TransitionCancelSnapshot {

        private final DocumentAnimationProperty property;
        private final long elapsedTimeNanos;

        private TransitionCancelSnapshot(DocumentAnimationProperty property, long elapsedTimeNanos) {
            this.property = Objects.requireNonNull(property, "property");
            this.elapsedTimeNanos = Math.max(0L, elapsedTimeNanos);
        }
    }

    /** 支持统一查询完成状态的 transition 运行对象。 */
    private interface TransitionState {
        boolean isFinished(long currentTimeNanos);

        long getDurationNanos();

        long getElapsedTimeNanos(long currentTimeNanos);

        boolean shouldDispatchStart(long currentTimeNanos);

        void markStartEventDispatched();
    }

    /** 支持统一查询完成状态的 keyframe animation 运行对象。 */
    private interface KeyframeAnimationState {
        boolean isFinished(long currentTimeNanos);

        long getOwnerId();
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
        private boolean startEventDispatched;

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

        @Override
        public long getElapsedTimeNanos(long currentTimeNanos) {
            if (currentTimeNanos <= startNanos) {
                return 0L;
            }
            return Math.min(durationNanos, currentTimeNanos - startNanos);
        }

        @Override
        public boolean shouldDispatchStart(long currentTimeNanos) {
            return !startEventDispatched && currentTimeNanos >= startNanos;
        }

        @Override
        public void markStartEventDispatched() {
            startEventDispatched = true;
        }
    }

    /** 单个数值 transition。 */
    private static final class FloatTransition implements TransitionState {

        private final float fromValue;
        private final float toValue;
        private final long startNanos;
        private final long durationNanos;
        private final DocumentAnimationTimingFunction timingFunction;
        private boolean startEventDispatched;

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

        @Override
        public long getElapsedTimeNanos(long currentTimeNanos) {
            if (currentTimeNanos <= startNanos) {
                return 0L;
            }
            return Math.min(durationNanos, currentTimeNanos - startNanos);
        }

        @Override
        public boolean shouldDispatchStart(long currentTimeNanos) {
            return !startEventDispatched && currentTimeNanos >= startNanos;
        }

        @Override
        public void markStartEventDispatched() {
            startEventDispatched = true;
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
        private final UiAnimationDirection animationDirection;
        private final boolean infiniteIteration;
        private final long ownerId;
        private final String animationName;

        private ColorKeyframeAnimation(DocumentKeyframes.ColorTrack track, long startNanos, long durationNanos,
                int iterationCount, DocumentAnimationFillMode fillMode, DocumentAnimationTimingFunction timingFunction,
                UiAnimationDirection animationDirection, long ownerId, String animationName) {
            this.track = Objects.requireNonNull(track, "track");
            this.startNanos = startNanos;
            this.durationNanos = Math.max(1L, durationNanos);
            this.infiniteIteration = iterationCount <= 0;
            this.iterationCount = this.infiniteIteration ? 0 : Math.max(1, iterationCount);
            this.fillMode = fillMode == null ? DocumentAnimationFillMode.NONE : fillMode;
            this.timingFunction = timingFunction == null ? DocumentAnimationTimingFunction.LINEAR : timingFunction;
            this.animationDirection = animationDirection == null ? UiAnimationDirection.NORMAL : animationDirection;
            this.ownerId = ownerId;
            this.animationName = animationName == null ? "animation" : animationName;
        }

        private int resolve(int baseColor, long currentTimeNanos) {
            if (currentTimeNanos < startNanos) {
                return fillsBackwards() ? resolveAtIterationBoundary(0L, 0.0F) : baseColor;
            }
            long elapsedNanos = currentTimeNanos - startNanos;
            if (!infiniteIteration) {
                long activeDurationNanos = getActiveDurationNanos();
                if (elapsedNanos >= activeDurationNanos) {
                    return fillsForwards() ? resolveAtIterationBoundary(iterationCount - 1L, 1.0F) : baseColor;
                }
            }
            long iterationElapsedNanos = elapsedNanos % durationNanos;
            long iterationIndex = elapsedNanos / durationNanos;
            float progress = resolveDirectionalProgress(animationDirection, iterationIndex,
                    iterationElapsedNanos / (float) durationNanos);
            return resolveColorTrack(track, progress, timingFunction);
        }

        @Override
        public boolean isFinished(long currentTimeNanos) {
            if (infiniteIteration || currentTimeNanos < startNanos) {
                return false;
            }
            return currentTimeNanos - startNanos >= getActiveDurationNanos();
        }

        @Override
        public long getOwnerId() {
            return ownerId;
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
            return Integer.valueOf(resolveAtIterationBoundary(iterationCount - 1L, 1.0F));
        }

        private long getActiveDurationNanos() {
            return resolveActiveDurationNanos(durationNanos, iterationCount);
        }

        private int resolveAtIterationBoundary(long iterationIndex, float iterationProgress) {
            float progress = resolveDirectionalProgress(animationDirection, iterationIndex, iterationProgress);
            return resolveColorTrack(track, progress, timingFunction);
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
        private final UiAnimationDirection animationDirection;
        private final boolean infiniteIteration;
        private final long ownerId;
        private final String animationName;

        private FloatKeyframeAnimation(DocumentKeyframes.FloatTrack track, long startNanos, long durationNanos,
                int iterationCount, DocumentAnimationFillMode fillMode, DocumentAnimationTimingFunction timingFunction,
                UiAnimationDirection animationDirection, long ownerId, String animationName) {
            this.track = Objects.requireNonNull(track, "track");
            this.startNanos = startNanos;
            this.durationNanos = Math.max(1L, durationNanos);
            this.infiniteIteration = iterationCount <= 0;
            this.iterationCount = this.infiniteIteration ? 0 : Math.max(1, iterationCount);
            this.fillMode = fillMode == null ? DocumentAnimationFillMode.NONE : fillMode;
            this.timingFunction = timingFunction == null ? DocumentAnimationTimingFunction.LINEAR : timingFunction;
            this.animationDirection = animationDirection == null ? UiAnimationDirection.NORMAL : animationDirection;
            this.ownerId = ownerId;
            this.animationName = animationName == null ? "animation" : animationName;
        }

        private float resolve(float baseValue, long currentTimeNanos) {
            if (currentTimeNanos < startNanos) {
                return fillsBackwards() ? resolveAtIterationBoundary(0L, 0.0F) : baseValue;
            }
            long elapsedNanos = currentTimeNanos - startNanos;
            if (!infiniteIteration) {
                long activeDurationNanos = getActiveDurationNanos();
                if (elapsedNanos >= activeDurationNanos) {
                    return fillsForwards() ? resolveAtIterationBoundary(iterationCount - 1L, 1.0F) : baseValue;
                }
            }
            long iterationElapsedNanos = elapsedNanos % durationNanos;
            long iterationIndex = elapsedNanos / durationNanos;
            float progress = resolveDirectionalProgress(animationDirection, iterationIndex,
                    iterationElapsedNanos / (float) durationNanos);
            return resolveFloatTrack(track, progress, timingFunction);
        }

        @Override
        public boolean isFinished(long currentTimeNanos) {
            if (infiniteIteration || currentTimeNanos < startNanos) {
                return false;
            }
            return currentTimeNanos - startNanos >= getActiveDurationNanos();
        }

        @Override
        public long getOwnerId() {
            return ownerId;
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
            return Float.valueOf(resolveAtIterationBoundary(iterationCount - 1L, 1.0F));
        }

        private long getActiveDurationNanos() {
            return resolveActiveDurationNanos(durationNanos, iterationCount);
        }

        private float resolveAtIterationBoundary(long iterationIndex, float iterationProgress) {
            float progress = resolveDirectionalProgress(animationDirection, iterationIndex, iterationProgress);
            return resolveFloatTrack(track, progress, timingFunction);
        }
    }
}
