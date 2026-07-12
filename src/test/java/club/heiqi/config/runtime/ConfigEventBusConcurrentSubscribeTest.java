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
        AtomicInteger originalHits = new AtomicInteger();
        ConfigChangeListener listener = event -> originalHits.incrementAndGet();
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

        // 同一 listener 计数：publish 恰一次
        bus.publish(new club.heiqi.config.ConfigChangeEvent(
                "", null, null, club.heiqi.config.ConfigChangeEvent.ChangeType.BATCH_SAVE));
        assertEquals("同一 listener 实例 publish 应恰一次", 1, originalHits.get());

        AtomicInteger secondHits = new AtomicInteger();
        bus.subscribe(e -> secondHits.incrementAndGet());
        bus.publish(new club.heiqi.config.ConfigChangeEvent(
                "", null, null, club.heiqi.config.ConfigChangeEvent.ChangeType.RELOAD));
        assertEquals(2, originalHits.get());
        assertEquals(1, secondHits.get());
        assertEquals(2, bus.listenerCount());
    }
}
