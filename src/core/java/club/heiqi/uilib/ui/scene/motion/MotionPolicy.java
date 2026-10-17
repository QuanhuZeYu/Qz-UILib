package club.heiqi.uilib.ui.scene.motion;

/** 用户限制优先于局部与主题策略；不改变业务状态或输入资格。 */
public enum MotionPolicy {
    NORMAL, REDUCED, OFF;
    private static final MotionSpec FEEDBACK = MotionSpec.tween(120_000_000L, MotionSpec.SMOOTH);
    public MotionSpec resolve(MotionSpec requested, boolean spatial) {
        if (this == OFF || this == REDUCED && spatial) return MotionSpec.instant();
        if (this == REDUCED && (requested.kind == MotionSpec.Kind.SPRING || requested.kind == MotionSpec.Kind.DECAY)) return FEEDBACK;
        return requested;
    }
}
