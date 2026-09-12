package club.heiqi.uilib.util;

import org.junit.Assert;
import org.junit.Test;

/**
 * {@link LogThrottle} 频率闸门语义测试（假时钟注入，纯 JVM）。
 *
 * <p>真机文本通道诊断的"正常路径不刷屏"承诺全靠本类：断言必须钉死
 * 「稳态上限 = burst 条 / 窗口」与「每条放行都带回被折叠次数」两条契约。</p>
 */
public class LogThrottleTest {

    private static final long MS = 1_000_000L;

    @Test
    public void allowsBurstThenSuppressesWithinWindow() {
        LogThrottle throttle = new LogThrottle(2, 2000);
        Assert.assertTrue(throttle.allow(0L));
        Assert.assertTrue(throttle.allow(1L));
        Assert.assertFalse("窗口内超出 burst 必须抑制", throttle.allow(2L));
        Assert.assertFalse(throttle.allow(500L * MS));
        Assert.assertEquals("累计调用计数含被抑制项", 4L, throttle.total());
    }

    @Test
    public void reopensBurstInNextWindow() {
        LogThrottle throttle = new LogThrottle(1, 2000);
        Assert.assertTrue(throttle.allow(0L));
        Assert.assertFalse(throttle.allow(1999L * MS));
        Assert.assertTrue("窗口到期后重新获得 burst", throttle.allow(2000L * MS));
        Assert.assertFalse(throttle.allow(2000L * MS));
        Assert.assertTrue(throttle.allow(5000L * MS));
    }

    @Test
    public void reportsSuppressedCountSnapshotAtEachAllowedLine() {
        LogThrottle throttle = new LogThrottle(1, 1000);
        Assert.assertTrue(throttle.allow(0L));
        Assert.assertEquals("首条无折叠", 0L, throttle.suppressedSinceLastLog());
        for (int i = 0; i < 5; i++) {
            Assert.assertFalse(throttle.allow(10L + i));
        }
        Assert.assertTrue(throttle.allow(1000L * MS));
        Assert.assertEquals("第二条应上报上窗口折叠的 5 次", 5L, throttle.suppressedSinceLastLog());
        Assert.assertEquals("快照读取幂等（getter 无副作用，不清零）", 5L, throttle.suppressedSinceLastLog());
        for (int i = 0; i < 3; i++) {
            Assert.assertFalse(throttle.allow(1001L * MS + i));
        }
        Assert.assertTrue(throttle.allow(2000L * MS));
        Assert.assertEquals("折叠计数按放行点清零，故本次上报的是上一条之后的 3 次", 3L,
                throttle.suppressedSinceLastLog());
    }

    /**
     * 稳态上限契约：任意事件速率下，放行条数 ≤ burst × 窗口数（含首个窗口）。
     * 用 1000 次等间隔事件压实（覆盖窗口边界恰好对齐与不对齐两种相位）。
     */
    @Test
    public void allowedLinesStayWithinBurstTimesWindows() {
        int burst = 2;
        long windowMillis = 500L;
        int events = 1000;
        long spanNanos = 10_000L * MS; // 10000ms ⇒ 20 个窗口
        LogThrottle throttle = new LogThrottle(burst, windowMillis);
        int allowed = 0;
        for (int i = 0; i < events; i++) {
            if (throttle.allow(spanNanos * i / events)) {
                allowed++;
            }
        }
        long windows = spanNanos / (windowMillis * MS);
        Assert.assertEquals(events, (int) throttle.total());
        Assert.assertTrue("放行条数必须落在窗口上限内: allowed=" + allowed + ", windows=" + windows,
                allowed <= burst * (windows + 1));
        Assert.assertTrue("窗口显著长于事件间隔时必须有抑制（限流确实生效）", allowed < events);
    }

    @Test
    public void rejectsNonPositiveArguments() {
        try {
            new LogThrottle(0, 1000);
            Assert.fail("burstPerWindow=0 应被拒绝");
        } catch (IllegalArgumentException expected) {
            Assert.assertNotNull(expected.getMessage());
        }
        try {
            new LogThrottle(1, 0);
            Assert.fail("windowMillis=0 应被拒绝");
        } catch (IllegalArgumentException expected) {
            Assert.assertNotNull(expected.getMessage());
        }
    }
}
