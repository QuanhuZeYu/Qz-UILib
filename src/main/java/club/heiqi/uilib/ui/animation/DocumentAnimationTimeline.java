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
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.style.cascade.ComputedStyle;

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

    private final Map<ElementNode, DocumentAnimationRuntimeState> states =
            new HashMap<ElementNode, DocumentAnimationRuntimeState>();
    private long nextImperativeAnimationId = 1L;
    private Runnable runtimeChangeCallback;

    /**
     * 设置命令式动画运行态变化回调。
     *
     * @param runtimeChangeCallback 变化回调；为 null 时清除
     * @return 当前时间线
     */
    public DocumentAnimationTimeline setRuntimeChangeCallback(Runnable runtimeChangeCallback) {
        this.runtimeChangeCallback = runtimeChangeCallback;
        return this;
    }

    /**
     * 单次清理过程中收集到的动画完成结果。
     */
    public static final class PruneResult {

        private final List<TransitionEndRecord> transitionEndRecords = new ArrayList<TransitionEndRecord>();
        private final List<TransitionStartRecord> transitionStartRecords = new ArrayList<TransitionStartRecord>();
        private final List<TransitionCancelRecord> transitionCancelRecords = new ArrayList<TransitionCancelRecord>();
        private final List<AnimationStartRecord> animationStartRecords = new ArrayList<AnimationStartRecord>();
        private final List<AnimationIterationRecord> animationIterationRecords =
                new ArrayList<AnimationIterationRecord>();
        private final List<AnimationEndRecord> animationEndRecords = new ArrayList<AnimationEndRecord>();
        private boolean changed;

        public boolean isChanged() {
            return changed;
        }

        public List<TransitionEndRecord> getTransitionEndRecords() {
            return transitionEndRecords;
        }

        public List<TransitionStartRecord> getTransitionStartRecords() {
            return transitionStartRecords;
        }

        public List<TransitionCancelRecord> getTransitionCancelRecords() {
            return transitionCancelRecords;
        }

        public List<AnimationStartRecord> getAnimationStartRecords() {
            return animationStartRecords;
        }

        public List<AnimationIterationRecord> getAnimationIterationRecords() {
            return animationIterationRecords;
        }

        public List<AnimationEndRecord> getAnimationEndRecords() {
            return animationEndRecords;
        }

        void markChanged() {
            changed = true;
        }
    }

    /**
     * 单个 transition 开始记录。
     */
    public static final class TransitionStartRecord {

        private final ElementNode element;
        private final DocumentAnimationProperty property;
        private final long elapsedTimeNanos;

        TransitionStartRecord(ElementNode element, DocumentAnimationProperty property, long elapsedTimeNanos) {
            this.element = Objects.requireNonNull(element, "element");
            this.property = Objects.requireNonNull(property, "property");
            this.elapsedTimeNanos = Math.max(0L, elapsedTimeNanos);
        }

        public ElementNode getElement() {
            return element;
        }

        public DocumentAnimationProperty getProperty() {
            return property;
        }

        public long getElapsedTimeNanos() {
            return elapsedTimeNanos;
        }
    }

    /**
     * 单个 transition 取消记录。
     */
    public static final class TransitionCancelRecord {

        private final ElementNode element;
        private final DocumentAnimationProperty property;
        private final long elapsedTimeNanos;

        TransitionCancelRecord(ElementNode element, DocumentAnimationProperty property, long elapsedTimeNanos) {
            this.element = Objects.requireNonNull(element, "element");
            this.property = Objects.requireNonNull(property, "property");
            this.elapsedTimeNanos = Math.max(0L, elapsedTimeNanos);
        }

        public ElementNode getElement() {
            return element;
        }

        public DocumentAnimationProperty getProperty() {
            return property;
        }

        public long getElapsedTimeNanos() {
            return elapsedTimeNanos;
        }
    }

    /**
     * 单个 transition 结束记录。
     */
    public static final class TransitionEndRecord {

        private final ElementNode element;
        private final DocumentAnimationProperty property;
        private final long elapsedTimeNanos;

        TransitionEndRecord(ElementNode element, DocumentAnimationProperty property, long elapsedTimeNanos) {
            this.element = Objects.requireNonNull(element, "element");
            this.property = Objects.requireNonNull(property, "property");
            this.elapsedTimeNanos = Math.max(0L, elapsedTimeNanos);
        }

        public ElementNode getElement() {
            return element;
        }

        public DocumentAnimationProperty getProperty() {
            return property;
        }

        public long getElapsedTimeNanos() {
            return elapsedTimeNanos;
        }
    }

    /**
     * 单个 animation 结束记录。
     */
    public static final class AnimationEndRecord {

        private final ElementNode element;
        private final String animationName;
        private final long elapsedTimeNanos;

        AnimationEndRecord(ElementNode element, String animationName, long elapsedTimeNanos) {
            this.element = Objects.requireNonNull(element, "element");
            this.animationName = Objects.requireNonNull(animationName, "animationName");
            this.elapsedTimeNanos = Math.max(0L, elapsedTimeNanos);
        }

        public ElementNode getElement() {
            return element;
        }

        public String getAnimationName() {
            return animationName;
        }

        public long getElapsedTimeNanos() {
            return elapsedTimeNanos;
        }
    }

    /**
     * 单个 animation 开始记录。
     */
    public static final class AnimationStartRecord {

        private final ElementNode element;
        private final String animationName;
        private final long elapsedTimeNanos;

        AnimationStartRecord(ElementNode element, String animationName, long elapsedTimeNanos) {
            this.element = Objects.requireNonNull(element, "element");
            this.animationName = Objects.requireNonNull(animationName, "animationName");
            this.elapsedTimeNanos = Math.max(0L, elapsedTimeNanos);
        }

        public ElementNode getElement() {
            return element;
        }

        public String getAnimationName() {
            return animationName;
        }

        public long getElapsedTimeNanos() {
            return elapsedTimeNanos;
        }
    }

    /**
     * 单个 animation 迭代记录。
     */
    public static final class AnimationIterationRecord {

        private final ElementNode element;
        private final String animationName;
        private final long elapsedTimeNanos;
        private final long iterationIndex;

        AnimationIterationRecord(ElementNode element, String animationName, long elapsedTimeNanos,
                long iterationIndex) {
            this.element = Objects.requireNonNull(element, "element");
            this.animationName = Objects.requireNonNull(animationName, "animationName");
            this.elapsedTimeNanos = Math.max(0L, elapsedTimeNanos);
            this.iterationIndex = Math.max(0L, iterationIndex);
        }

        public ElementNode getElement() {
            return element;
        }

        public String getAnimationName() {
            return animationName;
        }

        public long getElapsedTimeNanos() {
            return elapsedTimeNanos;
        }

        public long getIterationIndex() {
            return iterationIndex;
        }
    }

    /**
     * 根据最新布局盒树刷新 transition 状态。
     *
     * @param rootBox 根布局盒
     * @param currentTimeNanos 当前动画时间
     * @return 动画状态是否发生变化
     */
    public boolean updateFromLayout(DocumentLayoutBox rootBox, long currentTimeNanos) {
        return updateFromLayout(java.util.Collections.singletonList(rootBox), currentTimeNanos);
    }

    /**
     * 根据最新布局盒根列表刷新 transition 状态。
     *
     * @param rootBoxes 布局盒根列表
     * @param currentTimeNanos 当前动画时间
     * @return 动画状态是否发生变化
     */
    public boolean updateFromLayout(List<DocumentLayoutBox> rootBoxes, long currentTimeNanos) {
        Objects.requireNonNull(rootBoxes, "rootBoxes");
        if (rootBoxes.isEmpty()) {
            boolean changed = !states.isEmpty();
            states.clear();
            return changed;
        }
        boolean changed = false;
        Set<ElementNode> activeElements = new HashSet<ElementNode>();
        for (DocumentLayoutBox rootBox : rootBoxes) {
            if (rootBox == null) {
                continue;
            }
            changed |= updateFromBox(rootBox, currentTimeNanos, activeElements);
        }
        Iterator<Map.Entry<ElementNode, DocumentAnimationRuntimeState>> iterator = states.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ElementNode, DocumentAnimationRuntimeState> entry = iterator.next();
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
        DocumentAnimationRuntimeState state = states.get(element);
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
        DocumentAnimationRuntimeState state = states.get(element);
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
        DocumentAnimationRuntimeState state = getOrCreateState(element);
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
        DocumentAnimationRuntimeState state = getOrCreateState(element);
        state.setManualFloatKeyframeAnimation(property, fromValue, toValue, startNanos, durationNanos);
    }

    /**
     * 清除指定元素的所有 keyframe animation 覆盖。
     *
     * @param element 元素
     */
    public void clearKeyframeAnimations(ElementNode element) {
        Objects.requireNonNull(element, "element");
        DocumentAnimationRuntimeState state = states.get(element);
        if (state == null) {
            return;
        }
        state.clearKeyframeAnimations();
    }

    /**
     * 启动命令式 keyframe animation。
     *
     * @param box 目标元素当前布局盒
     * @param keyframes keyframes 定义
     * @param options 播放选项
     * @param currentTimeNanos 当前动画时间
     * @return 命令式动画句柄
     */
    public DocumentAnimation startKeyframeAnimation(DocumentLayoutBox box, DocumentKeyframes keyframes,
            DocumentAnimationOptions options, long currentTimeNanos) {
        Objects.requireNonNull(box, "box");
        final ElementNode element = box.getElement();
        final DocumentKeyframes resolvedKeyframes = Objects.requireNonNull(keyframes, "keyframes");
        final DocumentAnimationOptions resolvedOptions = options == null
                ? DocumentAnimationOptions.ofMillis(0L) : options;
        if (resolvedOptions.getDurationNanos() <= 0L || resolvedKeyframes.isEmpty()) {
            return DocumentAnimation.inactive(element, resolvedKeyframes.getName(), resolvedOptions);
        }
        final long animationId = nextImperativeAnimationId++;
        long startNanos = currentTimeNanos + resolvedOptions.getDelayNanos();
        DocumentAnimationRuntimeState state = getOrCreateState(element);
        state.startImperativeKeyframeAnimations(box, resolvedKeyframes, startNanos, resolvedOptions, animationId);
        notifyRuntimeChanged();
        return new DocumentAnimation(element, animationId, resolvedKeyframes.getName(), startNanos,
                resolvedOptions.getDurationNanos(), new DocumentAnimation.Controller() {
                    @Override
                    public boolean cancel(DocumentAnimation animation) {
                        return cancelImperativeAnimation(element, animationId);
                    }

                    @Override
                    public boolean isRunning(DocumentAnimation animation) {
                        DocumentAnimationRuntimeState currentState = states.get(element);
                        return currentState != null && currentState.hasKeyframeAnimationOwner(animationId);
                    }
                });
    }

    /**
     * 返回当前是否仍有动画工作需要下一帧刷新。
     *
     * @return 是否有动画工作
     */
    public boolean hasAnimationWork() {
        for (DocumentAnimationRuntimeState state : states.values()) {
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
        for (DocumentAnimationRuntimeState state : states.values()) {
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
        for (DocumentAnimationRuntimeState state : states.values()) {
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
        DocumentAnimationRuntimeState state = states.get(element);
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
        for (DocumentAnimationRuntimeState state : states.values()) {
            count += state.countActiveAnimations(currentTimeNanos);
        }
        return count;
    }

    /**
     * 返回当前动画运行态诊断快照。
     *
     * <p>诊断只读取当前时间线状态，不推进动画，也不清理已完成动画。</p>
     *
     * @param currentTimeNanos 当前动画时间
     * @return 运行态诊断快照
     */
    public DiagnosticsSnapshot getDiagnosticsSnapshot(long currentTimeNanos) {
        DiagnosticsSnapshot snapshot = new DiagnosticsSnapshot();
        for (DocumentAnimationRuntimeState state : states.values()) {
            state.collectDiagnostics(snapshot, currentTimeNanos);
        }
        return snapshot;
    }

    /**
     * 清理已完成动画。
     *
     * @param currentTimeNanos 当前动画时间
     * @return 是否发生清理
     */
    public boolean pruneFinishedAnimations(long currentTimeNanos) {
        return pruneFinishedAnimationsWithResult(currentTimeNanos).isChanged();
    }

    /**
     * 清理已完成动画，并返回完成事件记录。
     *
     * @param currentTimeNanos 当前动画时间
     * @return 清理结果与完成记录
     */
    public PruneResult pruneFinishedAnimationsWithResult(long currentTimeNanos) {
        PruneResult result = new PruneResult();
        for (Map.Entry<ElementNode, DocumentAnimationRuntimeState> entry : states.entrySet()) {
            entry.getValue().pruneFinishedAnimations(entry.getKey(), currentTimeNanos, result);
        }
        return result;
    }

    /**
     * 清空所有动画状态。
     */
    public void clear() {
        states.clear();
    }

    /**
     * 动画运行态诊断快照。
     */
    public static final class DiagnosticsSnapshot {

        private final EnumMap<DocumentAnimationImpact, int[]> countsByImpact =
                new EnumMap<DocumentAnimationImpact, int[]>(DocumentAnimationImpact.class);
        private int totalTransitionCount;
        private int totalKeyframeCount;
        private int totalForwardsFillCount;

        private DiagnosticsSnapshot() {
            for (DocumentAnimationImpact impact : DocumentAnimationImpact.values()) {
                countsByImpact.put(impact, new int[3]);
            }
        }

        /**
         * 返回空诊断快照。
         *
         * @return 空诊断快照
         */
        public static DiagnosticsSnapshot empty() {
            return new DiagnosticsSnapshot();
        }

        /**
         * 返回指定影响范围内未完成 transition 数量。
         *
         * @param impact 动画影响范围
         * @return transition 数量
         */
        public int getTransitionCount(DocumentAnimationImpact impact) {
            return getCounts(impact)[0];
        }

        /**
         * 返回指定影响范围内未完成 keyframe animation 数量。
         *
         * @param impact 动画影响范围
         * @return keyframe animation 数量
         */
        public int getKeyframeCount(DocumentAnimationImpact impact) {
            return getCounts(impact)[1];
        }

        /**
         * 返回指定影响范围内 forwards fill 运行值数量。
         *
         * @param impact 动画影响范围
         * @return forwards fill 运行值数量
         */
        public int getForwardsFillCount(DocumentAnimationImpact impact) {
            return getCounts(impact)[2];
        }

        /**
         * 返回所有影响范围内未完成 transition 总数。
         *
         * @return transition 总数
         */
        public int getTotalTransitionCount() {
            return totalTransitionCount;
        }

        /**
         * 返回所有影响范围内未完成 keyframe animation 总数。
         *
         * @return keyframe animation 总数
         */
        public int getTotalKeyframeCount() {
            return totalKeyframeCount;
        }

        /**
         * 返回所有影响范围内 forwards fill 运行值总数。
         *
         * @return forwards fill 总数
         */
        public int getTotalForwardsFillCount() {
            return totalForwardsFillCount;
        }

        /**
         * 返回当前未完成动画总数。
         *
         * @return transition 与 keyframe animation 的总数
         */
        public int getActiveAnimationCount() {
            return totalTransitionCount + totalKeyframeCount;
        }

        /**
         * 返回指定影响范围内是否存在运行态覆盖值。
         *
         * @param impact 动画影响范围
         * @return 是否存在运行态覆盖值
         */
        public boolean hasRuntimeValue(DocumentAnimationImpact impact) {
            int[] counts = getCounts(impact);
            return counts[0] > 0 || counts[1] > 0 || counts[2] > 0;
        }

        private int[] getCounts(DocumentAnimationImpact impact) {
            int[] counts = countsByImpact.get(Objects.requireNonNull(impact, "impact"));
            if (counts == null) {
                throw new IllegalArgumentException("unsupported animation impact: " + impact);
            }
            return counts;
        }

        void incrementTransition(DocumentAnimationImpact impact) {
            getCounts(impact)[0]++;
            totalTransitionCount++;
        }

        void incrementKeyframe(DocumentAnimationImpact impact) {
            getCounts(impact)[1]++;
            totalKeyframeCount++;
        }

        void incrementForwardsFill(DocumentAnimationImpact impact) {
            getCounts(impact)[2]++;
            totalForwardsFillCount++;
        }
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
        DocumentAnimationRuntimeState state = states.get(element);
        boolean changed = false;
        if (state == null) {
            state = getOrCreateState(element);
            changed = true;
        }
        changed |= updateDeclaredKeyframeAnimations(box, style, currentTimeNanos, state);
        for (DocumentAnimationProperty property : COLOR_PROPERTIES) {
            int baseColor = getBaseColor(style, property);
            Integer previousTarget = state.getTargetColor(property);
            boolean transitionAllowed = canTransition(style, property);
            if (!transitionAllowed && state.removeColorTransition(property, currentTimeNanos)) {
                changed = true;
            }
            if (previousTarget == null) {
                state.putTargetColor(property, baseColor);
                changed = true;
                continue;
            }
            if (previousTarget.intValue() == baseColor) {
                continue;
            }
            int fromColor = resolveColor(element, property, previousTarget.intValue(), currentTimeNanos);
            state.putTargetColor(property, baseColor);
            state.suppressDeclaredColorKeyframeProperty(property);
            if (transitionAllowed && fromColor != baseColor) {
                state.setColorTransition(property, fromColor, baseColor,
                        currentTimeNanos + style.getTransitionDelayNanos(property),
                        style.getTransitionDurationNanos(property), style.getTransitionTimingFunction(property),
                        currentTimeNanos);
            } else {
                state.clearColorTransition(property, currentTimeNanos);
            }
            changed = true;
        }
        for (DocumentAnimationProperty property : FLOAT_PROPERTIES) {
            float baseValue = getBaseFloat(box, property);
            Float previousTarget = state.getTargetFloat(property);
            boolean targetTransitionable = isFloatTransitionTargetAnimatable(box, property);
            Boolean previousTargetTransitionable = state.getTargetFloatTransitionable(property);
            boolean transitionAllowed = canTransition(style, property) && targetTransitionable
                    && Boolean.TRUE.equals(previousTargetTransitionable);
            if (!transitionAllowed && state.removeFloatTransition(property, currentTimeNanos)) {
                changed = true;
            }
            if (previousTarget == null) {
                state.putTargetFloat(property, baseValue);
                state.putTargetFloatTransitionable(property, targetTransitionable);
                changed = true;
                continue;
            }
            if (Float.compare(previousTarget.floatValue(), baseValue) == 0) {
                if (!Objects.equals(previousTargetTransitionable, Boolean.valueOf(targetTransitionable))) {
                    state.putTargetFloatTransitionable(property, targetTransitionable);
                    changed = true;
                }
                continue;
            }
            float fromValue = resolveFloat(element, property, previousTarget.floatValue(), currentTimeNanos);
            state.putTargetFloat(property, baseValue);
            state.putTargetFloatTransitionable(property, targetTransitionable);
            state.suppressDeclaredFloatKeyframeProperty(property);
            if (transitionAllowed && Float.compare(fromValue, baseValue) != 0) {
                state.setFloatTransition(property, fromValue, baseValue,
                        currentTimeNanos + style.getTransitionDelayNanos(property),
                        style.getTransitionDurationNanos(property), style.getTransitionTimingFunction(property),
                        currentTimeNanos);
            } else {
                state.clearFloatTransition(property, currentTimeNanos);
            }
            changed = true;
        }
        return changed;
    }

    private DocumentAnimationRuntimeState getOrCreateState(ElementNode element) {
        DocumentAnimationRuntimeState state = states.get(element);
        if (state == null) {
            state = new DocumentAnimationRuntimeState();
            states.put(element, state);
        }
        return state;
    }

    private static boolean canTransition(ComputedStyle style, DocumentAnimationProperty property) {
        return style.canTransition(property);
    }

    private boolean cancelImperativeAnimation(ElementNode element, long animationId) {
        DocumentAnimationRuntimeState state = states.get(element);
        boolean changed = state != null && state.cancelKeyframeAnimationsByOwner(animationId);
        if (changed) {
            notifyRuntimeChanged();
        }
        return changed;
    }

    private void notifyRuntimeChanged() {
        if (runtimeChangeCallback != null) {
            runtimeChangeCallback.run();
        }
    }

    private static boolean updateDeclaredKeyframeAnimations(DocumentLayoutBox box, ComputedStyle style,
            long currentTimeNanos, DocumentAnimationRuntimeState state) {
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

    private static int getBaseColor(ComputedStyle style, DocumentAnimationProperty property) {
        return PropertyRuntimeSemantics.forProperty(property).resolveBaseColor(style);
    }

    private static float getBaseFloat(DocumentLayoutBox box, DocumentAnimationProperty property) {
        return PropertyRuntimeSemantics.forProperty(property).resolveBaseFloat(box);
    }

    private static boolean isFloatTransitionTargetAnimatable(DocumentLayoutBox box, DocumentAnimationProperty property) {
        return PropertyRuntimeSemantics.forProperty(property).isFloatTransitionTargetAnimatable(box);
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
}
