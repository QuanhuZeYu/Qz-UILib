package club.heiqi.uilib.ui.animation;

import java.util.Objects;

/**
 * 单个 transition 条目描述。
 *
 * <p>用于表达类似 CSS transition 四元组的 per-property 配置：属性、持续时间、延迟和缓动函数。</p>
 */
public final class DocumentTransitionSpec {

    private final DocumentAnimationProperty property;
    private final long durationNanos;
    private final long delayNanos;
    private final DocumentAnimationTimingFunction timingFunction;

    private DocumentTransitionSpec(DocumentAnimationProperty property, long durationNanos, long delayNanos,
            DocumentAnimationTimingFunction timingFunction) {
        this.property = Objects.requireNonNull(property, "property");
        this.durationNanos = Math.max(0L, durationNanos);
        this.delayNanos = Math.max(0L, delayNanos);
        this.timingFunction = timingFunction == null ? DocumentAnimationTimingFunction.LINEAR : timingFunction;
    }

    /**
     * 创建毫秒单位 transition 条目。
     *
     * @param property 动画属性
     * @param durationMillis 持续时间，单位毫秒
     * @return transition 条目
     */
    public static DocumentTransitionSpec ofMillis(DocumentAnimationProperty property, long durationMillis) {
        return ofMillis(property, durationMillis, 0L, DocumentAnimationTimingFunction.LINEAR);
    }

    /**
     * 创建毫秒单位 transition 条目。
     *
     * @param property 动画属性
     * @param durationMillis 持续时间，单位毫秒
     * @param delayMillis 延迟时间，单位毫秒
     * @param timingFunction 缓动函数；为 null 时使用 linear
     * @return transition 条目
     */
    public static DocumentTransitionSpec ofMillis(DocumentAnimationProperty property, long durationMillis,
            long delayMillis, DocumentAnimationTimingFunction timingFunction) {
        return ofNanos(property, durationMillis * 1_000_000L, delayMillis * 1_000_000L, timingFunction);
    }

    /**
     * 创建纳秒单位 transition 条目。
     *
     * @param property 动画属性
     * @param durationNanos 持续时间，单位纳秒
     * @param delayNanos 延迟时间，单位纳秒
     * @param timingFunction 缓动函数；为 null 时使用 linear
     * @return transition 条目
     */
    public static DocumentTransitionSpec ofNanos(DocumentAnimationProperty property, long durationNanos,
            long delayNanos, DocumentAnimationTimingFunction timingFunction) {
        return new DocumentTransitionSpec(property, durationNanos, delayNanos, timingFunction);
    }

    /**
     * 返回 transition 属性。
     *
     * @return 动画属性
     */
    public DocumentAnimationProperty getProperty() {
        return property;
    }

    /**
     * 返回持续时间。
     *
     * @return 持续时间，单位纳秒
     */
    public long getDurationNanos() {
        return durationNanos;
    }

    /**
     * 返回延迟时间。
     *
     * @return 延迟时间，单位纳秒
     */
    public long getDelayNanos() {
        return delayNanos;
    }

    /**
     * 返回缓动函数。
     *
     * @return 缓动函数
     */
    public DocumentAnimationTimingFunction getTimingFunction() {
        return timingFunction;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DocumentTransitionSpec)) {
            return false;
        }
        DocumentTransitionSpec other = (DocumentTransitionSpec) obj;
        return property == other.property
                && durationNanos == other.durationNanos
                && delayNanos == other.delayNanos
                && Objects.equals(timingFunction, other.timingFunction);
    }

    @Override
    public int hashCode() {
        int result = property.hashCode();
        result = 31 * result + (int) (durationNanos ^ (durationNanos >>> 32));
        result = 31 * result + (int) (delayNanos ^ (delayNanos >>> 32));
        result = 31 * result + timingFunction.hashCode();
        return result;
    }
}
