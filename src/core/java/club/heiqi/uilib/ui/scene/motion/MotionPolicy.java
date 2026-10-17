package club.heiqi.uilib.ui.scene.motion;

/** 用户限制优先于局部与主题策略；不改变业务状态或输入资格。 */
public enum MotionPolicy {
    NORMAL, REDUCED, OFF;
    private static final MotionSpec FEEDBACK = MotionSpec.tween(120_000_000L, MotionSpec.SMOOTH);
    private static final MotionSpec SPATIAL = MotionSpec.tween(160_000_000L, MotionSpec.SMOOTH);
    public MotionSpec resolve(MotionSpec requested, boolean spatial) {
        if (this == OFF) return MotionSpec.instant();
        if (this == NORMAL) return requested;
        if (requested.kind == MotionSpec.Kind.INSTANT || requested.kind == MotionSpec.Kind.DECAY) return MotionSpec.instant();
        if (spatial) return SPATIAL;
        if (requested.kind == MotionSpec.Kind.SPRING || requested.durationNanos >= FEEDBACK.durationNanos) return FEEDBACK;
        // 短自定义曲线也可能往返；减少模式保留其短时长，统一使用单调反馈曲线。
        return MotionSpec.tween(requested.durationNanos, MotionSpec.SMOOTH);
    }
}
