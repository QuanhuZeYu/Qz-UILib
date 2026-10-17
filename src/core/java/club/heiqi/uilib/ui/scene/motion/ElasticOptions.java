package club.heiqi.uilib.ui.scene.motion;

import java.util.Objects;

/** 显式弹性边界：合法值不越界，越界距离仅产生逐渐加重的阻力和有限呈现形变。 */
public final class ElasticOptions {
    public final double maximumDisplacement;
    public final MotionSpec releaseMotion;

    public ElasticOptions(double maximumDisplacement, MotionSpec releaseMotion) {
        if (!Double.isFinite(maximumDisplacement) || maximumDisplacement <= 0)
            throw new IllegalArgumentException("弹性位移上限必须是有限正数");
        this.releaseMotion = Objects.requireNonNull(releaseMotion, "弹性释放策略");
        if (releaseMotion.kind == MotionSpec.Kind.DECAY) throw new IllegalArgumentException("弹性释放需要回到合法目标");
        this.maximumDisplacement = maximumDisplacement;
    }

    /** 输入为距最近合法边缘的有符号距离；反向输入直接作用于当前指针，不积存越界量。 */
    public double displacement(double overshoot, MotionPolicy policy) {
        if (!Double.isFinite(overshoot)) throw new IllegalArgumentException("弹性输入必须有限");
        if (policy == MotionPolicy.OFF) return 0;
        double limit = maximumDisplacement * (policy == MotionPolicy.REDUCED ? .25 : 1);
        return Math.copySign(limit * (1 - 1 / (1 + Math.abs(overshoot) * .55 / limit)), overshoot);
    }
}
