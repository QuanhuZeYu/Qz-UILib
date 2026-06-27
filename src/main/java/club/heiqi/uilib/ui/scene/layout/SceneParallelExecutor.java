package club.heiqi.uilib.ui.scene.layout;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;

/**
 * 场景布局/绘制并行执行器 —— 阶段 2 第二批引入的专用常驻 {@link ForkJoinPool} 单例基础设施。
 *
 * <p>本类只提供 pool 与全局回退开关，<b>不在此步实际跑任何 layout/paint 并行任务</b>
 * （步骤 2.4/2.5 才接入）。此步只建基础设施，确保后续可一键回退串行。</p>
 *
 * <h3>设计要点（Oracle 裁决 + 行业调研背书）</h3>
 * <ul>
 *   <li><b>进程级单例</b>：{@code static} 懒初始化的 {@link ForkJoinPool}，全 JVM 共享一份。
 *       与 {@code DefaultTextMeasureService.getInstance()} 单例模式一致。</li>
 *   <li><b>并行度 cores-1</b>：{@code max(1, availableProcessors() - 1)}。
 *       Unity/Unreal/Naughty Dog 游戏引擎强共识：留一核给主线程跑 MC 主循环 + GL replay，
 *       避免 worker 与主线程争抢 CPU 导致帧抖动。</li>
 *   <li><b>专用 pool</b>：不使用 {@link ForkJoinPool#commonPool()}（JVM 全局共享，
 *       会与 MC/Forge 生态其他并行任务争抢），独立建池隔离。</li>
 *   <li><b>线程命名</b>：{@code scene-layout-worker-N}，便于 profiling 定位
 *       （Servo/Rayon 惯例）。</li>
 *   <li><b>常驻不关</b>：pool 常驻整个 JVM 生命周期，不随 Host dispose 关闭，
 *       JVM 退出自然回收。避免反复建池的线程创建开销。</li>
 * </ul>
 *
 * <h3>全局回退开关</h3>
 * <p>{@link #PARALLEL_ENABLED} 默认 {@code false}。阶段 2.4/2.5 完成且真机验证通过后
 * 才设 {@code true}。开关关时，2.4/2.5 的并行入口必须退化串行执行——一键回退保险。
 * 出现任何并行相关回归（帧抖动/数据竞争/死锁），{@code setParallelEnabled(false)} 即可
 * 全局关闭并行路径，无需改代码。</p>
 *
 * <h3>worker render-scoped 不变量（NORTH_STAR 已登记，2.4 实际使用时守）</h3>
 * <p>pool 常驻但 worker 任务必须 <b>render-scoped</b>：每帧 fork 的任务必须在帧内 join 完成，
 * 不跨帧存活、不跨帧缓存任务对象。pool 本身只是线程池常驻，任务生命周期严格限定在单次
 * render 调用内。违反此不变量会导致跨帧数据依赖、任务泄漏与不可重入。</p>
 *
 * <h3>使用边界</h3>
 * <p>此 pool 专供 scene layout/paint 并行使用，不暴露给其他模块。
 * 2.4 将基于此 pool 实现 {@code RecursiveTask} 拆分布局子树。</p>
 */
public final class SceneParallelExecutor {

    private SceneParallelExecutor() {
        // 工具类，禁止实例化
    }

    /** 专用常驻 ForkJoinPool，懒初始化。 */
    private static volatile ForkJoinPool POOL;

    /**
     * 全局并行回退开关。默认 {@code false}，2.4/2.5 完成且真机验证后才设 {@code true}。
     * 关闭时所有并行入口退化串行。
     */
    private static volatile boolean PARALLEL_ENABLED = false;

    /**
     * 获取专用常驻 {@link ForkJoinPool}（懒初始化，双重检查锁定）。
     *
     * <p>并行度 {@code max(1, availableProcessors() - 1)}，worker 线程命名
     * {@code scene-layout-worker-N}。</p>
     *
     * @return 专用 ForkJoinPool 实例，全 JVM 单例
     */
    public static ForkJoinPool getPool() {
        ForkJoinPool pool = POOL;
        if (pool == null) {
            synchronized (SceneParallelExecutor.class) {
                pool = POOL;
                if (pool == null) {
                    int parallelism = Math.max(1,
                        Runtime.getRuntime().availableProcessors() - 1);
                    ForkJoinPool.ForkJoinWorkerThreadFactory factory = p -> {
                        ForkJoinWorkerThread t = ForkJoinPool.defaultForkJoinWorkerThreadFactory
                            .newThread(p);
                        t.setName("scene-layout-worker-" + t.getPoolIndex());
                        return t;
                    };
                    pool = new ForkJoinPool(parallelism, factory, null, false);
                    POOL = pool;
                }
            }
        }
        return pool;
    }

    /**
     * 查询全局并行开关状态。
     *
     * @return {@code true} 表示并行路径启用，{@code false} 表示退化串行
     */
    public static boolean isParallelEnabled() {
        return PARALLEL_ENABLED;
    }

    /**
     * 设置全局并行开关。出问题时 {@code setParallelEnabled(false)} 一键回退串行。
     *
     * @param enabled {@code true} 启用并行路径，{@code false} 退化串行
     */
    public static void setParallelEnabled(boolean enabled) {
        PARALLEL_ENABLED = enabled;
    }
}
