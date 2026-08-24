package club.heiqi.uilib.internal.chat3.data;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.Test;

import net.minecraft.util.ChatComponentText;

/**
 * ChatHistory 契约测试:顺序/容量裁剪/删除/清空/滚动/快照/并发追加。
 */
public class ChatHistoryTest {

    @Test
    public void shouldAppendNewestFirst() {
        ChatHistory history = new ChatHistory(10);
        history.append(component("a"), 1);
        history.append(component("b"), 2);
        history.append(component("c"), 3);

        Assert.assertEquals("c", history.snapshot().get(0).getPlainText());
        Assert.assertEquals("b", history.snapshot().get(1).getPlainText());
        Assert.assertEquals("a", history.snapshot().get(2).getPlainText());
    }

    @Test
    public void shouldTrimOldestBeyondCapacity() {
        ChatHistory history = new ChatHistory(3);
        history.append(component("a"), 1);
        history.append(component("b"), 2);
        history.append(component("c"), 3);
        history.append(component("d"), 4);

        Assert.assertEquals(3, history.size());
        List<ChatLineRecord> lines = history.snapshot();
        Assert.assertEquals("d", lines.get(0).getPlainText());
        Assert.assertEquals("c", lines.get(1).getPlainText());
        Assert.assertEquals("b", lines.get(2).getPlainText());
        // "a" 最旧,已裁剪
    }

    @Test
    public void shouldAssignUniqueSequenceIds() {
        ChatHistory history = new ChatHistory(10);
        history.append(component("a"), 1);
        history.append(component("b"), 2);
        history.append(component("c"), 3);

        List<ChatLineRecord> lines = history.snapshot();
        Assert.assertNotEquals("序列号应唯一", lines.get(0).getSequenceId(), lines.get(1).getSequenceId());
        Assert.assertNotEquals(lines.get(1).getSequenceId(), lines.get(2).getSequenceId());
        Assert.assertTrue("入史后序列号非 0", lines.get(0).getSequenceId() > 0);
    }

    @Test
    public void shouldRejectInvalidCapacity() {
        try {
            new ChatHistory(0);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // 预期
        }
    }

    @Test
    public void shouldReplaceSameNonZeroMessageId() {
        ChatHistory history = new ChatHistory(10);
        history.append(component("a"), 1);
        history.append(component("b"), 1);
        Assert.assertEquals("非 0 id 同 id 替换(候选打印覆盖语义)", 1, history.size());
        Assert.assertEquals("b", history.snapshot().get(0).getPlainText());
        Assert.assertEquals(1, history.snapshot().get(0).getMessageId());
    }

    @Test
    public void shouldKeepAppendingZeroMessageId() {
        ChatHistory history = new ChatHistory(10);
        history.append(component("a"), 0);
        history.append(component("b"), 0);
        Assert.assertEquals("普通消息 id 恒 0:追加而非互相替换", 2, history.size());
        Assert.assertEquals("b", history.snapshot().get(0).getPlainText());
    }

    @Test
    public void shouldDeleteById() {
        ChatHistory history = new ChatHistory(10);
        history.append(component("a"), 1);
        history.append(component("b"), 2);
        history.append(component("c"), 3);

        Assert.assertTrue(history.deleteById(2));
        Assert.assertEquals(2, history.size());
        Assert.assertEquals("c", history.snapshot().get(0).getPlainText());
        Assert.assertEquals("a", history.snapshot().get(1).getPlainText());

        Assert.assertFalse("不存在的 ID 返回 false", history.deleteById(99));
        Assert.assertEquals(2, history.size());
    }

    @Test
    public void shouldClearAndResetScroll() {
        ChatHistory history = new ChatHistory(10);
        history.append(component("a"), 1);
        history.scrollBy(5);
        Assert.assertTrue(history.isScrolled());

        history.clear();
        Assert.assertEquals(0, history.size());
        Assert.assertEquals(0, history.getScroll());
        Assert.assertFalse(history.isScrolled());
    }

    @Test
    public void shouldScrollWithZeroFloor() {
        ChatHistory history = new ChatHistory(10);
        Assert.assertEquals(0, history.getScroll());
        Assert.assertFalse(history.isScrolled());

        history.scrollBy(7);
        Assert.assertEquals(7, history.getScroll());
        Assert.assertTrue(history.isScrolled());

        history.scrollBy(-3);
        Assert.assertEquals(4, history.getScroll());

        // 下限 0,不回卷成负
        history.scrollBy(-100);
        Assert.assertEquals(0, history.getScroll());
        Assert.assertFalse(history.isScrolled());
    }

    @Test
    public void shouldResetScroll() {
        ChatHistory history = new ChatHistory(10);
        history.scrollBy(9);
        history.resetScroll();
        Assert.assertEquals(0, history.getScroll());
        Assert.assertFalse(history.isScrolled());
    }

    @Test
    public void shouldReturnDetachedSnapshot() {
        ChatHistory history = new ChatHistory(10);
        history.append(component("a"), 1);

        List<ChatLineRecord> snapshot = history.snapshot();
        snapshot.clear(); // 篡改快照不影响内部

        Assert.assertEquals(1, history.size());
        Assert.assertEquals("a", history.snapshot().get(0).getPlainText());
    }

    @Test
    public void shouldSupportConcurrentAppends() throws InterruptedException {
        final ChatHistory history = new ChatHistory(100);
        final int perThread = 500;
        final CountDownLatch start = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

        List<Thread> threads = new ArrayList<Thread>();
        for (int t = 0; t < 4; t++) {
            final int threadId = t;
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        start.await();
                        for (int i = 0; i < perThread; i++) {
                            history.append(component("t" + threadId + "-" + i), threadId * perThread + i);
                        }
                    } catch (Throwable error) {
                        failure.compareAndSet(null, error);
                    }
                }
            });
            threads.add(thread);
            thread.start();
        }
        start.countDown();
        for (Thread thread : threads) {
            thread.join(20000);
        }

        Assert.assertNull("并发追加不应抛异常: " + failure.get(), failure.get());
        Assert.assertEquals("容量裁剪后应为 100 条", 100, history.size());
    }

    private static ChatComponentText component(String text) {
        return new ChatComponentText(text);
    }
}
