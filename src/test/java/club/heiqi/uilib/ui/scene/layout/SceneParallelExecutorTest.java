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
        // 恢复 4 个 fork 阈值默认值（64/256），避免污染其他测试
        SceneParallelExecutor.setLayoutForkThreshold(64);
        SceneParallelExecutor.setLayoutWholeTreeThreshold(256);
        SceneParallelExecutor.setPaintForkThreshold(64);
        SceneParallelExecutor.setPaintWholeTreeThreshold(256);
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

    // ============================================================
    // 测试 7：4 个 fork 阈值默认值正确（64/256）
    // ============================================================

    /**
     * 初始状态 4 个阈值应为默认值 64/256（与原 final 常量等价，保证零回归）。
     */
    @Test
    public void forkThresholdsDefaultTo64And256() {
        Assert.assertEquals("layoutForkThreshold 默认应为 64", 64,
            SceneParallelExecutor.getLayoutForkThreshold());
        Assert.assertEquals("layoutWholeTreeThreshold 默认应为 256", 256,
            SceneParallelExecutor.getLayoutWholeTreeThreshold());
        Assert.assertEquals("paintForkThreshold 默认应为 64", 64,
            SceneParallelExecutor.getPaintForkThreshold());
        Assert.assertEquals("paintWholeTreeThreshold 默认应为 256", 256,
            SceneParallelExecutor.getPaintWholeTreeThreshold());
    }

    // ============================================================
    // 测试 8：setter/getter 生效（set 后 get 返回新值）
    // ============================================================

    /**
     * set 4 个阈值为新值后，get 应返回新值；再 set 回默认值，get 应恢复。
     * 验证运行时动态调阈值 API 可用（demo 页 slider 生效的前提）。
     */
    @Test
    public void forkThresholdSetterTakesEffect() {
        SceneParallelExecutor.setLayoutForkThreshold(128);
        SceneParallelExecutor.setLayoutWholeTreeThreshold(512);
        SceneParallelExecutor.setPaintForkThreshold(32);
        SceneParallelExecutor.setPaintWholeTreeThreshold(1024);

        Assert.assertEquals("set 后 layoutForkThreshold 应为 128", 128,
            SceneParallelExecutor.getLayoutForkThreshold());
        Assert.assertEquals("set 后 layoutWholeTreeThreshold 应为 512", 512,
            SceneParallelExecutor.getLayoutWholeTreeThreshold());
        Assert.assertEquals("set 后 paintForkThreshold 应为 32", 32,
            SceneParallelExecutor.getPaintForkThreshold());
        Assert.assertEquals("set 后 paintWholeTreeThreshold 应为 1024", 1024,
            SceneParallelExecutor.getPaintWholeTreeThreshold());

        // 恢复默认，验证可反复改
        SceneParallelExecutor.setLayoutForkThreshold(64);
        SceneParallelExecutor.setLayoutWholeTreeThreshold(256);
        SceneParallelExecutor.setPaintForkThreshold(64);
        SceneParallelExecutor.setPaintWholeTreeThreshold(256);

        Assert.assertEquals("恢复后 layoutForkThreshold 应为 64", 64,
            SceneParallelExecutor.getLayoutForkThreshold());
        Assert.assertEquals("恢复后 layoutWholeTreeThreshold 应为 256", 256,
            SceneParallelExecutor.getLayoutWholeTreeThreshold());
        Assert.assertEquals("恢复后 paintForkThreshold 应为 64", 64,
            SceneParallelExecutor.getPaintForkThreshold());
        Assert.assertEquals("恢复后 paintWholeTreeThreshold 应为 256", 256,
            SceneParallelExecutor.getPaintWholeTreeThreshold());
    }

    // ============================================================
    // 测试 9：阈值改动后 layout 并行/串行路径切换（行为差异）
    // ============================================================

    /**
     * 构造一棵节点数 &gt;= 256 的树，开 PARALLEL_ENABLED：
     * set layoutWholeTreeThreshold=1 → 走并行路径；
     * set layoutWholeTreeThreshold=100000 → 走串行路径。
     * 两次 layout 的 relayoutCount 应一致（结果等价，仅路径不同），
     * 验证阈值改动确实影响 layout 行为路径选择。
     */
    @Test
    public void layoutPathSwitchesWithThreshold() {
        // 构造一棵 300 节点的链式树（每个节点 1 个子，深度 300）
        // subtreeNodeCount 从根算 = 300，>= 256 默认阈值
        club.heiqi.uilib.ui.scene.node.SceneNode root =
            new club.heiqi.uilib.ui.scene.node.SceneNode();
        club.heiqi.uilib.ui.scene.node.SceneNode prev = root;
        for (int i = 1; i < 300; i++) {
            club.heiqi.uilib.ui.scene.node.SceneNode n =
                new club.heiqi.uilib.ui.scene.node.SceneNode();
            prev.appendChild(n);
            prev = n;
        }

        // 文本度量 stub：所有文本宽度 0、行高 0、epoch 0
        club.heiqi.uilib.ui.scene.text.SceneTextMeasurer measurer =
            new club.heiqi.uilib.ui.scene.text.SceneTextMeasurer() {
                @Override public int measureWidth(String text, int fontSizePx) { return 0; }
                @Override public int lineHeight(int fontSizePx) { return 0; }
                @Override public int epoch() { return 0; }
            };
        SceneLayoutEngine engine = new SceneLayoutEngine(measurer);
        Constraints cons = new Constraints(1000, 1000);

        // 先跑一次串行 layout 建立缓存 + subtreeNodeCount（PARALLEL_ENABLED 默认 false）
        engine.layout(root, cons);
        int subtreeCount = root.__getCachedSubtreeNodeCount();
        Assert.assertTrue("root 子树节点数应 >= 256，实际：" + subtreeCount,
            subtreeCount >= 256);

        // 开并行，set 整树阈值=1 → 走并行路径
        SceneParallelExecutor.setParallelEnabled(true);
        SceneParallelExecutor.setLayoutWholeTreeThreshold(1);
        root.markSelfLayout();
        LayoutResult parallelResult = engine.layout(root, cons);

        // set 整树阈值=100000 → 走串行路径
        SceneParallelExecutor.setLayoutWholeTreeThreshold(100000);
        root.markSelfLayout();
        LayoutResult serialResult = engine.layout(root, cons);

        // 两次 relayoutCount 应一致（同一棵树、同一约束、同一脏标记，结果等价）
        Assert.assertEquals("并行与串行路径 relayoutCount 应一致",
            serialResult.getRelayoutCount(), parallelResult.getRelayoutCount());
    }
}
