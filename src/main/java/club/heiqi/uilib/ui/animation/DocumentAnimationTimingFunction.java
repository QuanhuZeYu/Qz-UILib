package club.heiqi.uilib.ui.animation;

import java.util.Locale;

/**
 * HTML-like transition timing function。
 */
public interface DocumentAnimationTimingFunction {

    /**
     * 线性插值。
     */
    DocumentAnimationTimingFunction LINEAR = new LinearTimingFunction();

    /**
     * 标准 CSS ease 曲线：cubic-bezier(0.25, 0.1, 0.25, 1.0)。
     */
    DocumentAnimationTimingFunction EASE = cubicBezier(0.25F, 0.10F, 0.25F, 1.0F);

    /**
     * 标准 CSS ease-in 曲线：cubic-bezier(0.42, 0, 1.0, 1.0)。
     */
    DocumentAnimationTimingFunction EASE_IN = cubicBezier(0.42F, 0.0F, 1.0F, 1.0F);

    /**
     * 标准 CSS ease-out 曲线：cubic-bezier(0, 0, 0.58, 1.0)。
     */
    DocumentAnimationTimingFunction EASE_OUT = cubicBezier(0.0F, 0.0F, 0.58F, 1.0F);

    /**
     * 标准 CSS ease-in-out 曲线：cubic-bezier(0.42, 0, 0.58, 1.0)。
     */
    DocumentAnimationTimingFunction EASE_IN_OUT = cubicBezier(0.42F, 0.0F, 0.58F, 1.0F);

    /**
     * 创建标准 cubic-bezier timing function。
     *
     * @param x1 第一个控制点 X，必须位于 0..1
     * @param y1 第一个控制点 Y
     * @param x2 第二个控制点 X，必须位于 0..1
     * @param y2 第二个控制点 Y
     * @return cubic-bezier timing function
     */
    static DocumentAnimationTimingFunction cubicBezier(float x1, float y1, float x2, float y2) {
        return new CubicBezierTimingFunction(x1, y1, x2, y2);
    }

    /**
     * 创建 steps timing function，默认等同于 {@code steps(count, end)}。
     *
     * @param count 阶梯数量，必须大于 0
     * @return steps timing function
     */
    static DocumentAnimationTimingFunction steps(int count) {
        return steps(count, StepPosition.END);
    }

    /**
     * 创建 steps timing function。
     *
     * @param count 阶梯数量，必须大于 0
     * @param position 阶梯跳变位置
     * @return steps timing function
     */
    static DocumentAnimationTimingFunction steps(int count, StepPosition position) {
        return new StepsTimingFunction(count, position);
    }

    /**
     * 计算缓动后的进度。
     *
     * @param progress 原始 0..1 进度
     * @return 缓动后进度
     */
    float apply(float progress);

    /** 线性 timing function。 */
    final class LinearTimingFunction implements DocumentAnimationTimingFunction {

        private LinearTimingFunction() {}

        @Override
        public float apply(float progress) {
            return clampProgress(progress);
        }

        @Override
        public String toString() {
            return "linear";
        }
    }

    /** 标准 cubic-bezier timing function。 */
    final class CubicBezierTimingFunction implements DocumentAnimationTimingFunction {

        private static final int NEWTON_ITERATIONS = 8;
        private static final int BISECTION_ITERATIONS = 12;
        private static final float DERIVATIVE_EPSILON = 0.000001F;

        private final float x1;
        private final float y1;
        private final float x2;
        private final float y2;

        private CubicBezierTimingFunction(float x1, float y1, float x2, float y2) {
            if (Float.isNaN(x1) || Float.isNaN(y1) || Float.isNaN(x2) || Float.isNaN(y2)) {
                throw new IllegalArgumentException("cubic-bezier control points cannot be NaN");
            }
            if (x1 < 0.0F || x1 > 1.0F || x2 < 0.0F || x2 > 1.0F) {
                throw new IllegalArgumentException("cubic-bezier x control points must be in [0, 1]");
            }
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }

        @Override
        public float apply(float progress) {
            float clampedProgress = clampProgress(progress);
            if (clampedProgress <= 0.0F || clampedProgress >= 1.0F) {
                return clampedProgress;
            }
            float curveT = solveCurveT(clampedProgress);
            return sampleCurveY(curveT);
        }

        private float solveCurveT(float x) {
            float t = x;
            for (int iteration = 0; iteration < NEWTON_ITERATIONS; iteration++) {
                float sampledX = sampleCurveX(t) - x;
                if (Math.abs(sampledX) < DERIVATIVE_EPSILON) {
                    return t;
                }
                float derivative = sampleCurveDerivativeX(t);
                if (Math.abs(derivative) < DERIVATIVE_EPSILON) {
                    break;
                }
                t -= sampledX / derivative;
                if (t <= 0.0F || t >= 1.0F) {
                    t = Math.max(0.0F, Math.min(1.0F, t));
                    break;
                }
            }
            float lower = 0.0F;
            float upper = 1.0F;
            t = x;
            for (int iteration = 0; iteration < BISECTION_ITERATIONS; iteration++) {
                float sampledX = sampleCurveX(t);
                if (Math.abs(sampledX - x) < DERIVATIVE_EPSILON) {
                    return t;
                }
                if (sampledX < x) {
                    lower = t;
                } else {
                    upper = t;
                }
                t = (lower + upper) * 0.5F;
            }
            return t;
        }

        private float sampleCurveX(float t) {
            return sampleCubic(x1, x2, t);
        }

        private float sampleCurveY(float t) {
            return sampleCubic(y1, y2, t);
        }

        private float sampleCurveDerivativeX(float t) {
            float inverse = 1.0F - t;
            return 3.0F * inverse * inverse * x1
                    + 6.0F * inverse * t * (x2 - x1)
                    + 3.0F * t * t * (1.0F - x2);
        }

        private static float sampleCubic(float control1, float control2, float t) {
            float inverse = 1.0F - t;
            return 3.0F * inverse * inverse * t * control1
                    + 3.0F * inverse * t * t * control2
                    + t * t * t;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof CubicBezierTimingFunction)) {
                return false;
            }
            CubicBezierTimingFunction other = (CubicBezierTimingFunction) obj;
            return Float.compare(x1, other.x1) == 0
                    && Float.compare(y1, other.y1) == 0
                    && Float.compare(x2, other.x2) == 0
                    && Float.compare(y2, other.y2) == 0;
        }

        @Override
        public int hashCode() {
            int result = Float.floatToIntBits(x1);
            result = 31 * result + Float.floatToIntBits(y1);
            result = 31 * result + Float.floatToIntBits(x2);
            result = 31 * result + Float.floatToIntBits(y2);
            return result;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "cubic-bezier(%s, %s, %s, %s)",
                    Float.valueOf(x1), Float.valueOf(y1), Float.valueOf(x2), Float.valueOf(y2));
        }
    }

    /** steps timing function 的跳变位置。 */
    enum StepPosition {
        /** 在区间起点跳变，等同 CSS steps(..., start)。 */
        START,

        /** 在区间终点跳变，等同 CSS steps(..., end)。 */
        END
    }

    /** 离散阶梯 timing function。 */
    final class StepsTimingFunction implements DocumentAnimationTimingFunction {

        private final int count;
        private final StepPosition position;

        private StepsTimingFunction(int count, StepPosition position) {
            if (count <= 0) {
                throw new IllegalArgumentException("steps count must be positive");
            }
            this.count = count;
            this.position = position == null ? StepPosition.END : position;
        }

        @Override
        public float apply(float progress) {
            float clampedProgress = clampProgress(progress);
            if (clampedProgress >= 1.0F) {
                return 1.0F;
            }
            if (position == StepPosition.START) {
                return Math.min(1.0F, (float) Math.ceil(clampedProgress * count) / count);
            }
            if (clampedProgress <= 0.0F) {
                return 0.0F;
            }
            return (float) Math.floor(clampedProgress * count) / count;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof StepsTimingFunction)) {
                return false;
            }
            StepsTimingFunction other = (StepsTimingFunction) obj;
            return count == other.count && position == other.position;
        }

        @Override
        public int hashCode() {
            return 31 * count + position.hashCode();
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "steps(%d, %s)", Integer.valueOf(count),
                    position == StepPosition.START ? "start" : "end");
        }
    }

    /**
     * 将原始进度限制在 0..1。
     *
     * @param progress 原始进度
     * @return 限制后的进度
     */
    static float clampProgress(float progress) {
        if (Float.isNaN(progress)) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, progress));
    }
}
