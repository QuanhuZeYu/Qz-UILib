package club.heiqi.uilib.ui.animation;

import java.util.Objects;

import club.heiqi.uilib.ui.style.props.UiAnimationDirection;

/**
 * 命令式 keyframe animation 播放选项。
 */
public final class DocumentAnimationOptions {

    private final long durationNanos;
    private final long delayNanos;
    private final int iterationCount;
    private final DocumentAnimationFillMode fillMode;
    private final DocumentAnimationTimingFunction timingFunction;
    private final UiAnimationDirection direction;

    private DocumentAnimationOptions(Builder builder) {
        this.durationNanos = Math.max(0L, builder.durationNanos);
        this.delayNanos = Math.max(0L, builder.delayNanos);
        this.iterationCount = Math.max(0, builder.iterationCount);
        this.fillMode = builder.fillMode == null ? DocumentAnimationFillMode.NONE : builder.fillMode;
        this.timingFunction = builder.timingFunction == null ? DocumentAnimationTimingFunction.LINEAR
                : builder.timingFunction;
        this.direction = builder.direction == null ? UiAnimationDirection.NORMAL : builder.direction;
    }

    /**
     * 创建默认选项 builder。
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 创建指定持续时间的默认选项。
     *
     * @param durationMillis 持续时间，单位毫秒
     * @return 动画选项
     */
    public static DocumentAnimationOptions ofMillis(long durationMillis) {
        return builder().setDurationMillis(durationMillis).build();
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
     * 返回迭代次数；0 表示无限迭代。
     *
     * @return 迭代次数
     */
    public int getIterationCount() {
        return iterationCount;
    }

    /**
     * 返回填充模式。
     *
     * @return 填充模式
     */
    public DocumentAnimationFillMode getFillMode() {
        return fillMode;
    }

    /**
     * 返回缓动函数。
     *
     * @return 缓动函数
     */
    public DocumentAnimationTimingFunction getTimingFunction() {
        return timingFunction;
    }

    /**
     * 返回播放方向。
     *
     * @return 播放方向
     */
    public UiAnimationDirection getDirection() {
        return direction;
    }

    /**
     * 命令式动画选项 builder。
     */
    public static final class Builder {

        private long durationNanos;
        private long delayNanos;
        private int iterationCount = 1;
        private DocumentAnimationFillMode fillMode = DocumentAnimationFillMode.NONE;
        private DocumentAnimationTimingFunction timingFunction = DocumentAnimationTimingFunction.LINEAR;
        private UiAnimationDirection direction = UiAnimationDirection.NORMAL;

        private Builder() {}

        /**
         * 设置持续时间。
         *
         * @param durationMillis 持续时间，单位毫秒
         * @return 当前 builder
         */
        public Builder setDurationMillis(long durationMillis) {
            return setDurationNanos(durationMillis * 1_000_000L);
        }

        /**
         * 设置持续时间。
         *
         * @param durationNanos 持续时间，单位纳秒
         * @return 当前 builder
         */
        public Builder setDurationNanos(long durationNanos) {
            this.durationNanos = Math.max(0L, durationNanos);
            return this;
        }

        /**
         * 设置延迟时间。
         *
         * @param delayMillis 延迟时间，单位毫秒
         * @return 当前 builder
         */
        public Builder setDelayMillis(long delayMillis) {
            return setDelayNanos(delayMillis * 1_000_000L);
        }

        /**
         * 设置延迟时间。
         *
         * @param delayNanos 延迟时间，单位纳秒
         * @return 当前 builder
         */
        public Builder setDelayNanos(long delayNanos) {
            this.delayNanos = Math.max(0L, delayNanos);
            return this;
        }

        /**
         * 设置迭代次数；0 表示无限迭代。
         *
         * @param iterationCount 迭代次数
         * @return 当前 builder
         */
        public Builder setIterationCount(int iterationCount) {
            this.iterationCount = Math.max(0, iterationCount);
            return this;
        }

        /**
         * 设置填充模式。
         *
         * @param fillMode 填充模式
         * @return 当前 builder
         */
        public Builder setFillMode(DocumentAnimationFillMode fillMode) {
            this.fillMode = Objects.requireNonNull(fillMode, "fillMode");
            return this;
        }

        /**
         * 设置缓动函数。
         *
         * @param timingFunction 缓动函数
         * @return 当前 builder
         */
        public Builder setTimingFunction(DocumentAnimationTimingFunction timingFunction) {
            this.timingFunction = Objects.requireNonNull(timingFunction, "timingFunction");
            return this;
        }

        /**
         * 设置播放方向。
         *
         * @param direction 播放方向
         * @return 当前 builder
         */
        public Builder setDirection(UiAnimationDirection direction) {
            this.direction = Objects.requireNonNull(direction, "direction");
            return this;
        }

        /**
         * 创建不可变选项。
         *
         * @return 动画选项
         */
        public DocumentAnimationOptions build() {
            return new DocumentAnimationOptions(this);
        }
    }
}
