package club.heiqi.uilib.internal.chat3.viewmodel;

import org.junit.Assert;
import org.junit.Test;

/**
 * MessageLifecycle 契约测试:未进入返回全额预算 / markEntered 幂等 / 剩余递减并夹 0 /
 * isExpired / isFadeElapsed(含 fadeMillis≤0) / markDone。
 */
public class MessageLifecycleTest {

    private static final long BUDGET = 5000L;

    @Test
    public void shouldReturnFullBudgetBeforeEnter() {
        MessageLifecycle lifecycle = new MessageLifecycle(BUDGET);
        Assert.assertFalse(lifecycle.isEntered());
        Assert.assertEquals(-1L, lifecycle.getHudVisibleStartMillis());
        // 未进入时任意可见时钟都返回全额预算
        Assert.assertEquals(BUDGET, lifecycle.remainingMillis(0L));
        Assert.assertEquals(BUDGET, lifecycle.remainingMillis(99_999L));
        Assert.assertEquals(BUDGET, lifecycle.getBudgetMillis());
    }

    @Test
    public void shouldBeIdempotentOnMarkEntered() {
        MessageLifecycle lifecycle = new MessageLifecycle(BUDGET);
        lifecycle.markEntered(1000L);
        Assert.assertTrue(lifecycle.isEntered());
        Assert.assertEquals(1000L, lifecycle.getHudVisibleStartMillis());
        // 重复调用(重挂载/组增长重建)不重置起点
        lifecycle.markEntered(99_999L);
        Assert.assertEquals(1000L, lifecycle.getHudVisibleStartMillis());
        Assert.assertEquals(BUDGET - 1000L, lifecycle.remainingMillis(2000L));
    }

    @Test
    public void shouldDecreaseRemainingAndClampAtZero() {
        MessageLifecycle lifecycle = new MessageLifecycle(BUDGET);
        lifecycle.markEntered(100L);
        Assert.assertEquals(BUDGET - 100L, lifecycle.remainingMillis(200L));
        Assert.assertEquals(0L, lifecycle.remainingMillis(100L + BUDGET));
        // 超预算后夹 0,不回负
        Assert.assertEquals(0L, lifecycle.remainingMillis(100L + BUDGET + 99_999L));
    }

    @Test
    public void shouldReportExpiredAtBudgetBoundary() {
        MessageLifecycle lifecycle = new MessageLifecycle(BUDGET);
        Assert.assertFalse("未进入不视为过期", lifecycle.isExpired(0L));
        lifecycle.markEntered(0L);
        Assert.assertFalse(lifecycle.isExpired(BUDGET - 1L));
        Assert.assertTrue("预算恰耗尽的边界视为过期", lifecycle.isExpired(BUDGET));
        Assert.assertTrue(lifecycle.isExpired(BUDGET + 1L));
    }

    @Test
    public void shouldReportFadeElapsedWithFadeWindow() {
        MessageLifecycle lifecycle = new MessageLifecycle(BUDGET);
        Assert.assertFalse("未进入恒 false", lifecycle.isFadeElapsed(99_999L, 2000L));
        lifecycle.markEntered(0L);
        Assert.assertFalse("预算未耗尽", lifecycle.isFadeElapsed(BUDGET - 1L, 2000L));
        Assert.assertFalse("预算耗尽但淡出窗未结束", lifecycle.isFadeElapsed(BUDGET, 2000L));
        Assert.assertFalse(lifecycle.isFadeElapsed(BUDGET + 1999L, 2000L));
        Assert.assertTrue("淡出窗恰走完视为结束", lifecycle.isFadeElapsed(BUDGET + 2000L, 2000L));
        Assert.assertTrue(lifecycle.isFadeElapsed(BUDGET + 5000L, 2000L));
    }

    @Test
    public void shouldReportFadeElapsedImmediatelyWhenNoFadeWindow() {
        MessageLifecycle lifecycle = new MessageLifecycle(BUDGET);
        lifecycle.markEntered(10L);
        Assert.assertFalse(lifecycle.isFadeElapsed(10L + BUDGET - 1L, 0L));
        // fadeMillis <= 0:预算耗尽即视为淡出窗结束
        Assert.assertTrue(lifecycle.isFadeElapsed(10L + BUDGET, 0L));
        Assert.assertTrue(lifecycle.isFadeElapsed(10L + BUDGET, -1L));
    }

    @Test
    public void shouldTrackDoneFlag() {
        MessageLifecycle lifecycle = new MessageLifecycle(BUDGET);
        Assert.assertFalse(lifecycle.isDone());
        lifecycle.markDone();
        Assert.assertTrue(lifecycle.isDone());
        // 重复标记幂等
        lifecycle.markDone();
        Assert.assertTrue(lifecycle.isDone());
    }
}
