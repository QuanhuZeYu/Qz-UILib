package club.heiqi.uilib.ui.scene.layout;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

/**
 * {@link SceneParallelExecutor} 单元测试 —— 步骤 2.3 基础设施验证。
 *
 * <p>验证 pool 单例、并行度、命名、开关、可执行性。
 * <b>不跑任何 layout/paint 并行任务</b>（那是 2.4/2.5 的事）。</p>
 */
public class SceneParallelExecutorTest {

    @After
    public void restoreDefault() {
        // 恢复默认开关，避免污染其他测试
        SceneParallelExecutor.setParallelEnabled(false);
    }

    // ============================================================
    // 测试 1：getPool 返回非 null 单例
    // ============================================================

    /**
     * 两次 getPool 应返回同一实例（进程级单例）。
     */
    @Test
    public void getPoolReturnsSingletonInstance() {
        ForkJoinPool p1 = SceneParallelExecutor.getPool();
        ForkJoinPool p2 = SceneParallelExecutor.getPool();
        Assert.assertNotNull("pool 不应为 null", p1);
        Assert.assertSame("两次 getPool 应返回同一实例", p1, p2);
    }

    // ============================================================
    // 测试 2：并行度 = max(1, cores-1)
    // ============================================================

    /**
     * getPool().getParallelism() 应等于 max(1, availableProcessors() - 1)。
     */
    @Test
    public void parallelismIsCoresMinusOne() {
        ForkJoinPool pool = SceneParallelExecutor.getPool();
        int expected = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        Assert.assertEquals("并行度应为 max(1, cores-1)", expected, pool.getParallelism());
    }

    // ============================================================
    // 测试 3：PARALLEL_ENABLED 默认 false
    // ============================================================

    /**
     * 初始状态 isParallelEnabled() 应为 false。
     */
    @Test
    public void parallelEnabledDefaultsToFalse() {
        Assert.assertFalse("默认应关闭并行", SceneParallelExecutor.isParallelEnabled());
    }

    // ============================================================
    // 测试 4：setParallelEnabled 生效
    // ============================================================

    /**
     * set(true) 后 isParallelEnabled() == true，set(false) 恢复 false。
     */
    @Test
    public void setParallelEnabledTakesEffect() {
        SceneParallelExecutor.setParallelEnabled(true);
        Assert.assertTrue("set(true) 后应启用", SceneParallelExecutor.isParallelEnabled());
        SceneParallelExecutor.setParallelEnabled(false);
        Assert.assertFalse("set(false) 后应关闭", SceneParallelExecutor.isParallelEnabled());
    }

    // ============================================================
    // 测试 5：pool 能执行简单任务
    // ============================================================

    /**
     * submit 一个 Callable 返回 42，get 结果 == 42，验证 pool 真能跑任务。
     * 不跑 layout/paint 并行。
     */
    @Test
    public void poolCanExecuteSimpleTask() throws Exception {
        ForkJoinPool pool = SceneParallelExecutor.getPool();
        Callable<Integer> task = () -> 42;
        Future<Integer> future = pool.submit(task);
        Integer result = future.get();
        Assert.assertEquals("pool 应能执行简单任务并返回结果",
            Integer.valueOf(42), result);
    }

    // ============================================================
    // 测试 6：线程名以 scene-layout-worker 开头
    // ============================================================

    /**
     * submit 任务内读 Thread.currentThread().getName()，
     * 验证 worker 线程命名为 scene-layout-worker-N。
     */
    @Test
    public void workerThreadNamedSceneLayoutWorker() throws Exception {
        ForkJoinPool pool = SceneParallelExecutor.getPool();
        Callable<String> task = () -> Thread.currentThread().getName();
        Future<String> future = pool.submit(task);
        String name = future.get();
        Assert.assertNotNull("线程名不应为 null", name);
        Assert.assertTrue("线程名应以 scene-layout-worker 开头，实际：" + name,
            name.startsWith("scene-layout-worker"));
    }
}
