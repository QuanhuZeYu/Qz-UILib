package club.heiqi.uilib.internal.chat3.viewmodel;

import org.junit.Assert;
import org.junit.Test;

/**
 * HudVisibleClock 契约测试:不可见帧不累计 / delta 夹取 1s / 恢复可见无跳变 / reset。
 */
public class HudVisibleClockTest {

    @Test
    public void shouldAnchorOnFirstVisibleFrameWithoutAccumulating() {
        HudVisibleClock clock = new HudVisibleClock();
        Assert.assertEquals(0L, clock.tickFrame(5000L, true));
        Assert.assertEquals(0L, clock.visibleMillis());
    }

    @Test
    public void shouldNotAccumulateOnInvisibleFrames() {
        HudVisibleClock clock = new HudVisibleClock();
        clock.tickFrame(1000L, true);
        long before = clock.visibleMillis();
        clock.tickFrame(5000L, false);
        clock.tickFrame(6000L, false);
        Assert.assertEquals("不可见帧只更新壁钟不累计", before, clock.visibleMillis());
    }

    @Test
    public void shouldClampDeltaToOneSecond() {
        HudVisibleClock clock = new HudVisibleClock();
        clock.tickFrame(1000L, true);
        // 5s 间隔(渲染停滞/切出后恢复)只累计 1s 上限
        clock.tickFrame(6000L, true);
        Assert.assertEquals(1000L, clock.visibleMillis());
    }

    @Test
    public void shouldNotJumpOnVisibleRecovery() {
        HudVisibleClock clock = new HudVisibleClock();
        clock.tickFrame(1000L, true);
        clock.tickFrame(2000L, true);
        Assert.assertEquals(1000L, clock.visibleMillis());
        // 切出期:不可见帧持续走 tick,壁钟不断更新
        clock.tickFrame(3000L, false);
        clock.tickFrame(9000L, false);
        // 恢复可见:delta 以上一帧壁钟(9000)为准,仅 +500,无跳变
        clock.tickFrame(9500L, true);
        Assert.assertEquals(1000L + 500L, clock.visibleMillis());
    }

    @Test
    public void shouldClampNegativeDeltaToZero() {
        HudVisibleClock clock = new HudVisibleClock();
        clock.tickFrame(5000L, true);
        // 系统时钟回拨:delta 夹取下限 0,不累计
        clock.tickFrame(4000L, true);
        Assert.assertEquals(0L, clock.visibleMillis());
    }

    @Test
    public void shouldResetAndReanchor() {
        HudVisibleClock clock = new HudVisibleClock();
        clock.tickFrame(1000L, true);
        clock.tickFrame(3000L, true);
        Assert.assertEquals(1000L, clock.visibleMillis());
        clock.reset();
        Assert.assertEquals(0L, clock.visibleMillis());
        // 复位后首帧可见只定锚不累计
        clock.tickFrame(3000L, true);
        Assert.assertEquals(0L, clock.visibleMillis());
        clock.tickFrame(3200L, true);
        Assert.assertEquals(200L, clock.visibleMillis());
    }
}
