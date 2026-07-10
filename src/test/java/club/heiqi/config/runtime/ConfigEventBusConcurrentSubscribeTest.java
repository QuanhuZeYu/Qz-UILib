package club.heiqi.config.runtime;

import club.heiqi.config.ConfigChangeListener;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link ConfigEventBus#subscribe} 并发 addIfAbsent 去重。
 */
public class ConfigEventBusConcurrentSubscribeTest {

    @Test
    public void concurrentDuplicateSubscribe_registersOnce() throws Exception {
        ConfigEventBus bus = new ConfigEventBus();
        ConfigChangeListener listener = event -> { };
        int threads = 16;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start.await(5, TimeUnit.SECONDS);
                    bus.subscribe(listener);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }, "bus-sub-" + i).start();
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertEquals(1, bus.listenerCount());

        AtomicInteger hits = new AtomicInteger();
        bus.subscribe(e -> hits.incrementAndGet());
        // 再 publish 一次验证原 listener 只有一个
        bus.publish(new club.heiqi.config.ConfigChangeEvent(
                "", null, null, club.heiqi.config.ConfigChangeEvent.ChangeType.BATCH_SAVE));
        assertEquals(1, hits.get());
        assertEquals(2, bus.listenerCount());
    }
}
