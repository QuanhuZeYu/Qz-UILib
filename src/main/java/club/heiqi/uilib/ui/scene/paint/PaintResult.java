package club.heiqi.uilib.ui.scene.paint;

/**
 * paint() 返回的不可变结果，携带产出的 {@link PaintPlan} 与测试探针。
 *
 * <p>本类是 Display List 契约线阶段 1 的产物：把原本散落在 {@link ScenePaintEngine}
 * 实例字段中的「本次 paint 重生成 fragment 计数」探针打包成 per-call 不可变交付物，
 * 使引擎逐步走向无状态化（守 NORTH_STAR 信条六/I6 并行强化方向）。</p>
 *
 * <h3>不可变契约</h3>
 * <ul>
 *   <li>{@link #plan} 是 paint 产出的自包含 Display List，可延迟 replay（守 I6）。</li>
 *   <li>{@link #regeneratedFragmentCount} 是本次 paint 调用中重新生成的 fragment 数量，
 *       仅供测试断言 I8 缓存复用行为，生产代码不应依赖。</li>
 * </ul>
 *
 * <p>阶段 2 子树并行化后，每个 worker 产出的 PaintResult 可独立合并，无共享可变状态。</p>
 */
public final class PaintResult {

    /** 本次 paint 产出的 Display List（自包含不可变交付物）。 */
    private final PaintPlan plan;

    /** 本次 paint 重新生成的 fragment 数量（测试探针，I8 断言用）。 */
    private final int regeneratedFragmentCount;

    /**
     * 创建 paint 结果。
     *
     * @param plan                     paint 产出的 Display List（非 null）
     * @param regeneratedFragmentCount 本次 paint 重新生成的 fragment 数量
     */
    public PaintResult(PaintPlan plan, int regeneratedFragmentCount) {
        if (plan == null) {
            throw new IllegalArgumentException("PaintPlan 不可为 null");
        }
        this.plan = plan;
        this.regeneratedFragmentCount = regeneratedFragmentCount;
    }

    /** @return paint 产出的 Display List（自包含，可延迟 replay） */
    public PaintPlan getPlan() {
        return plan;
    }

    /** @return 本次 paint 重新生成的 fragment 数量（测试探针） */
    public int getRegeneratedFragmentCount() {
        return regeneratedFragmentCount;
    }
}
