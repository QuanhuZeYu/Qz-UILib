package club.heiqi.uilib.ui.scene.motion;

import java.util.Objects;

/** 框架拥有的一维轨迹；解析采样实际经过时间，无固定步长或补帧。读取不推进。 */
public final class MotionValue {
    private MotionSpec spec = MotionSpec.instant();
    private double value, velocity, target, origin, initialVelocity, reversingStart;
    private double shortening = 1, eased = 1;
    private long started, sampled, duration;
    private boolean active;
    public double value() { return value; }
    public double velocity() { return velocity; }
    public double target() { return target; }
    public boolean active() { return active; }
    public void snap(double next, long now) {
        finite(next); value = target = origin = reversingStart = next;
        velocity = initialVelocity = 0; shortening = eased = 1;
        started = sampled = now; active = false; spec = MotionSpec.instant();
    }
    public void retarget(double next, MotionSpec strategy, long now) {
        finite(next); Objects.requireNonNull(strategy, "运动策略");
        advance(now);
        if (target == next && spec.equals(strategy)) return;
        double previousTarget = target;
        if (strategy.kind == MotionSpec.Kind.TWEEN && spec.equals(strategy) && next == reversingStart) {
            shortening = Math.abs(eased * shortening + 1 - shortening);
            reversingStart = previousTarget;
        } else { shortening = 1; reversingStart = value; }
        origin = value; initialVelocity = velocity; target = next; spec = strategy;
        started = sampled; duration = Math.round(strategy.durationNanos * shortening); eased = 0;
        active = origin != target || velocity != 0;
        if (strategy.kind == MotionSpec.Kind.INSTANT || strategy.kind == MotionSpec.Kind.TWEEN && duration == 0)
            snap(next, sampled);
    }
    /** 直接操纵交出通道时，位置和输入估计速度一并进入同一求解器。 */
    public void release(double position, double speed, double destination, MotionSpec strategy, long now) {
        finite(position); finite(speed); finite(destination);
        snap(position, now); velocity = speed;
        retarget(destination, strategy, now);
    }
    /** 布局基准改变时重基当前轨迹；速度不变，不形成第二段绝对坐标补偿。 */
    public void shift(double delta) {
        finite(delta); value += delta; target += delta; origin += delta; reversingStart += delta;
    }
    /** 自由惯性终点由速度和衰减率求得；范围限位由所属视口负责。 */
    public void decay(double position, double speed, MotionSpec strategy, long now) {
        if (strategy.kind != MotionSpec.Kind.DECAY) throw new IllegalArgumentException("自由惯性要求衰减策略");
        release(position, speed, position + speed / strategy.frequency, strategy, now);
    }
    public boolean advance(long now) {
        if (now - sampled <= 0) return active;
        sampled = now;
        if (!active) return false;
        double seconds = (now - started) / 1_000_000_000.0;
        switch (spec.kind) {
            case TWEEN:
                if (now - started >= duration) { finish(); break; }
                double progress = (double) (now - started) / duration;
                eased = spec.easing.applyAsDouble(progress);
                if (!Double.isFinite(eased) || eased < 0 || eased > 1)
                    throw new IllegalArgumentException("动效曲线必须返回0到1之间的有限值");
                value = origin + (target - origin) * eased;
                // 有限曲线只承诺值续接；其速度不参与反向连续性合同。
                velocity = 0;
                break;
            case SPRING: spring(seconds); break;
            case DECAY:
                double decay = Math.exp(-spec.frequency * seconds);
                value = target + (origin - target) * decay;
                velocity = -spec.frequency * (origin - target) * decay;
                settle(); break;
            case INSTANT: finish(); break;
            default: throw new AssertionError(spec.kind);
        }
        finite(value); finite(velocity);
        return active;
    }
    private void spring(double seconds) {
        double omega = spec.frequency, zeta = spec.damping, displacement = origin - target;
        double x, v;
        if (zeta < 1) {
            double rate = zeta * omega, damped = omega * Math.sqrt((1 - zeta) * (1 + zeta));
            double sine = Math.sin(damped * seconds), cosine = Math.cos(damped * seconds);
            double b = (initialVelocity + rate * displacement) / damped;
            double envelope = Math.exp(-rate * seconds);
            x = envelope * (displacement * cosine + b * sine);
            v = envelope * (initialVelocity * cosine - (rate * b + damped * displacement) * sine);
        } else if (zeta == 1) {
            double b = initialVelocity + omega * displacement, envelope = Math.exp(-omega * seconds);
            x = envelope * (displacement + b * seconds);
            v = envelope * (initialVelocity - omega * b * seconds);
        } else {
            // 慢特征根用倒数形式，避免大阻尼下相减消失。
            double root = Math.sqrt(zeta - 1) * Math.sqrt(zeta + 1);
            double fast = -omega * (zeta + root), slow = -omega / (zeta + root);
            double a = (initialVelocity - fast * displacement) / (slow - fast), b = displacement - a;
            double es = Math.exp(slow * seconds), ef = Math.exp(fast * seconds);
            x = a * es + b * ef; v = slow * a * es + fast * b * ef;
        }
        value = target + x; velocity = v; settle();
    }
    private void settle() {
        if (Math.abs(value - target) <= spec.positionPrecision && Math.abs(velocity) <= spec.velocityPrecision) finish();
    }
    private void finish() { value = target; velocity = 0; eased = 1; active = false; }
    private static void finite(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("运动值必须有限");
    }
}
