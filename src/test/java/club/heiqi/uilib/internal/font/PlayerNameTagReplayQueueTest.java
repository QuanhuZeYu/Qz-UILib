package club.heiqi.uilib.internal.font;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.internal.font.PlayerNameTagReplayQueue.ReplayTarget;

/** 玩家名称标签帧队列的 FIFO、递归旁路与失败清理测试。 */
public class PlayerNameTagReplayQueueTest {

    @Before
    public void setUp() {
        PlayerNameTagReplayQueue.clear();
    }

    @After
    public void tearDown() {
        PlayerNameTagReplayQueue.clear();
        assertFalse(PlayerNameTagReplayQueue.isReplaying());
    }

    /** 队列按入队顺序把 target 与全部原参数各回放一次。 */
    @Test
    public void drainsInFifoOrderWithCompleteArgumentsAtMostOnce() {
        List<String> calls = new ArrayList<String>();
        ReplayTarget firstTarget = (entity, text, x, y, z, maxDistance) -> {
            assertNull(entity);
            assertEquals("first", text);
            assertEquals(1.25D, x, 0.0D);
            assertEquals(2.5D, y, 0.0D);
            assertEquals(3.75D, z, 0.0D);
            assertEquals(32, maxDistance);
            calls.add("first-target");
        };
        ReplayTarget secondTarget = (entity, text, x, y, z, maxDistance) -> {
            assertNull(entity);
            assertEquals("second", text);
            assertEquals(-4.0D, x, 0.0D);
            assertEquals(5.0D, y, 0.0D);
            assertEquals(-6.0D, z, 0.0D);
            assertEquals(64, maxDistance);
            calls.add("second-target");
        };

        assertTrue(PlayerNameTagReplayQueue.defer(firstTarget, null, "first", 1.25D, 2.5D, 3.75D, 32));
        assertTrue(PlayerNameTagReplayQueue.defer(secondTarget, null, "second", -4.0D, 5.0D, -6.0D, 64));

        PlayerNameTagReplayQueue.drain();
        PlayerNameTagReplayQueue.drain();

        assertEquals(Arrays.asList("first-target", "second-target"), calls);
    }

    /** 回放期间的新捕获被拒绝，使 Invoker 的递归入口直接执行原方法。 */
    @Test
    public void replayGuardRejectsRecursiveDeferral() {
        List<String> calls = new ArrayList<String>();
        AtomicBoolean recursiveAccepted = new AtomicBoolean(true);
        ReplayTarget recursiveTarget = (entity, text, x, y, z, maxDistance) -> {
            assertTrue(PlayerNameTagReplayQueue.isReplaying());
            calls.add(text);
            recursiveAccepted.set(PlayerNameTagReplayQueue.defer(
                    (nestedEntity, nestedText, nestedX, nestedY, nestedZ, nestedDistance) -> calls.add(nestedText),
                    null, "recursive", 0.0D, 0.0D, 0.0D, 64));
        };

        PlayerNameTagReplayQueue.defer(recursiveTarget, null, "outer", 0.0D, 0.0D, 0.0D, 64);
        PlayerNameTagReplayQueue.drain();
        PlayerNameTagReplayQueue.drain();

        assertFalse(recursiveAccepted.get());
        assertEquals(Arrays.asList("outer"), calls);
    }

    /** 失败会复位 guard，且同批未回放尾项不会重新泄回共享队列。 */
    @Test
    public void failureResetsGuardAndDiscardsUnreplayedTail() {
        RuntimeException failure = new RuntimeException("expected");
        List<String> calls = new ArrayList<String>();
        PlayerNameTagReplayQueue.defer((entity, text, x, y, z, maxDistance) -> {
            calls.add("failed");
            throw failure;
        }, null, "first", 0.0D, 0.0D, 0.0D, 64);
        PlayerNameTagReplayQueue.defer((entity, text, x, y, z, maxDistance) -> calls.add("stale-tail"),
                null, "second", 0.0D, 0.0D, 0.0D, 64);

        try {
            PlayerNameTagReplayQueue.drain();
            fail("应传播原回放异常");
        } catch (RuntimeException actual) {
            assertSame(failure, actual);
        }

        assertFalse(PlayerNameTagReplayQueue.isReplaying());
        PlayerNameTagReplayQueue.drain();
        assertEquals(Arrays.asList("failed"), calls);

        assertTrue(PlayerNameTagReplayQueue.defer(
                (entity, text, x, y, z, maxDistance) -> calls.add("recovered"),
                null, "recovered", 0.0D, 0.0D, 0.0D, 64));
        PlayerNameTagReplayQueue.drain();
        assertEquals(Arrays.asList("failed", "recovered"), calls);
    }

    /** 帧边界清理后，旧批次不会在后续 world-last 中回放。 */
    @Test
    public void clearDropsCrossFrameRemainder() {
        List<String> calls = new ArrayList<String>();
        PlayerNameTagReplayQueue.defer((entity, text, x, y, z, maxDistance) -> calls.add(text),
                null, "stale", 0.0D, 0.0D, 0.0D, 64);

        PlayerNameTagReplayQueue.clear();
        PlayerNameTagReplayQueue.drain();

        assertTrue(calls.isEmpty());
    }
}
