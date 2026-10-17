package club.heiqi.uilib.ui.scene.motion;

import java.util.Objects;
import java.util.function.DoubleUnaryOperator;

/** 目标跟随的不可变策略。弹簧以响应周期（秒）和阻尼比表达，不伪装成 easing。 */
public final class MotionSpec {
    public enum Kind { INSTANT, TWEEN, SPRING, DECAY }
    public static final DoubleUnaryOperator LINEAR = value -> value;
    public static final DoubleUnaryOperator SMOOTH = value -> value * value * (3 - 2 * value);
    private static final MotionSpec INSTANT = new MotionSpec(Kind.INSTANT, 0, LINEAR, 0, 0, .001, .001);
    public final Kind kind;
    public final long durationNanos;
    public final DoubleUnaryOperator easing;
    public final double frequency, damping, positionPrecision, velocityPrecision;

    private MotionSpec(Kind kind, long duration, DoubleUnaryOperator easing, double frequency,
            double damping, double positionPrecision, double velocityPrecision) {
        this.kind = kind; this.durationNanos = duration; this.easing = easing;
        this.frequency = frequency; this.damping = damping;
        this.positionPrecision = positionPrecision; this.velocityPrecision = velocityPrecision;
    }
    public static MotionSpec instant() { return INSTANT; }
    public static MotionSpec tween(long durationNanos, DoubleUnaryOperator easing) {
        if (durationNanos < 0) throw new IllegalArgumentException("动效时长不能为负");
        return new MotionSpec(Kind.TWEEN, durationNanos, Objects.requireNonNull(easing, "动效曲线"), 0, 0, .001, .001);
    }
    public static MotionSpec spring(double responseSeconds, double dampingRatio) {
        positive(responseSeconds, "弹簧响应周期"); positive(dampingRatio, "弹簧阻尼比");
        double frequency = 2 * Math.PI / responseSeconds;
        positive(frequency, "弹簧频率"); positive(frequency * dampingRatio * 2, "弹簧衰减率");
        return new MotionSpec(Kind.SPRING, 0, LINEAR, frequency, dampingRatio, .001, .001);
    }
    /** 仅用于显式手势释放；衰减率的单位是每秒。 */
    public static MotionSpec decay(double rate) {
        positive(rate, "惯性衰减率");
        return new MotionSpec(Kind.DECAY, 0, LINEAR, rate, 0, .001, .001);
    }
    public MotionSpec precision(double position, double velocity) {
        positive(position, "位置精度"); positive(velocity, "速度精度");
        return new MotionSpec(kind, durationNanos, easing, frequency, damping, position, velocity);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MotionSpec)) return false;
        MotionSpec value = (MotionSpec) other;
        return kind == value.kind && durationNanos == value.durationNanos && easing == value.easing
                && frequency == value.frequency && damping == value.damping
                && positionPrecision == value.positionPrecision && velocityPrecision == value.velocityPrecision;
    }
    @Override public int hashCode() {
        return Objects.hash(kind, durationNanos, easing, frequency, damping, positionPrecision, velocityPrecision);
    }
    private static void positive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException(name + "必须是有限正数");
    }
}
