package club.heiqi.uilib.ui.scene.motion;

import java.util.Objects;

/** 显式视口运动；普通滚轮仍只有宿主提供的位移，不额外合成惯性。 */
public final class ScrollMotionOptions {
    public final MotionSpec rebound, inertia;
    public final int maximumDisplacement;
    public ScrollMotionOptions(MotionSpec rebound) { this(rebound, null, 48); }
    private ScrollMotionOptions(MotionSpec rebound, MotionSpec inertia, int maximum) {
        this.rebound = Objects.requireNonNull(rebound, "边界回弹"); this.inertia = inertia;
        if (maximum < 1) throw new IllegalArgumentException("最大回弹位移必须为正");
        maximumDisplacement = maximum;
    }
    public ScrollMotionOptions inertia(MotionSpec decay) {
        if (decay.kind != MotionSpec.Kind.DECAY) throw new IllegalArgumentException("滚动惯性要求衰减策略");
        return new ScrollMotionOptions(rebound, decay, maximumDisplacement);
    }
    public ScrollMotionOptions maximumDisplacement(int pixels) { return new ScrollMotionOptions(rebound, inertia, pixels); }
}
