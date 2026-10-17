package club.heiqi.uilib.ui.scene.motion;

import java.util.Objects;

/** 显式进出场的有限保留策略；预算属于同一个拥有声明，普通结构不消费它。 */
public final class PresenceOptions {
    public final MotionSpec enter, exit;
    public final boolean initialEnter;
    public final int retainedLimit;
    public final long maximumExitNanos;
    public PresenceOptions(MotionSpec motion) { this(motion, motion, false, 32, 2_000_000_000L); }
    private PresenceOptions(MotionSpec enter, MotionSpec exit, boolean initialEnter, int limit, long maximum) {
        this.enter = Objects.requireNonNull(enter, "入场策略"); this.exit = Objects.requireNonNull(exit, "退场策略");
        if (limit < 1 || maximum <= 0) throw new IllegalArgumentException("退场预算和期限必须为正");
        this.initialEnter = initialEnter; this.retainedLimit = limit; this.maximumExitNanos = maximum;
    }
    public PresenceOptions initialEnter(boolean value) { return new PresenceOptions(enter, exit, value, retainedLimit, maximumExitNanos); }
    public PresenceOptions motions(MotionSpec enter, MotionSpec exit) { return new PresenceOptions(enter, exit, initialEnter, retainedLimit, maximumExitNanos); }
    public PresenceOptions budget(int count) { return new PresenceOptions(enter, exit, initialEnter, count, maximumExitNanos); }
    public PresenceOptions maximumExit(long nanos) { return new PresenceOptions(enter, exit, initialEnter, retainedLimit, nanos); }
}
