package club.heiqi.uilib.ui.screen;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

/**
 * `UiScreenManager` 的延后任务测试。
 */
public class UiScreenManagerTest {

    /**
     * 验证延后任务会在冲刷时按顺序执行。
     */
    @Test
    public void shouldRunEnqueuedTasksWhenFlushed() {
        UiScreenManager manager = UiScreenManager.getInstance();
        AtomicInteger counter = new AtomicInteger(0);

        manager.enqueue(new Runnable() {
            @Override
            public void run() {
                counter.compareAndSet(0, 1);
            }
        });
        manager.enqueue(new Runnable() {
            @Override
            public void run() {
                counter.compareAndSet(1, 2);
            }
        });

        Assert.assertEquals(0, counter.get());

        manager.flushPendingTasks();

        Assert.assertEquals(2, counter.get());

        manager.flushPendingTasks();

        Assert.assertEquals(2, counter.get());
    }
}
