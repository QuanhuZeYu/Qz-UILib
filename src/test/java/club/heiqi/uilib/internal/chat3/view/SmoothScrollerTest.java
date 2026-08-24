package club.heiqi.uilib.internal.chat3.view;

import org.junit.Assert;
import org.junit.Test;

/**
 * SmoothScroller 单元测试(T5b):computeDisplay 插值纯函数端点/中点、setTarget 重启语义、
 * snapTo 直通(拖动接管)、目标不变不重启、isSettled 稳定判定。
 *
 * <p>数值依据:120ms 起点/中点/终点,easeOutQuad(0.5) = 1-(1-0.5)² = 0.75;</p>
 * <ul>
 *   <li>computeDisplay(2, 8, 60, 120) = 2 + (8-2)×0.75 = 6.5;</li>
 *   <li>重启语义:目标变化时以「当前显示」为起点(无瞬跳);目标未变不重启(按原起点推进);</li>
 *   <li>snapTo 直通:display 立即等于目标、取消平滑;直通中 setTarget 直接到位;
 *       releaseDrag 后目标变化恢复 120ms 平滑。</li>
 * </ul>
 */
public class SmoothScrollerTest {

    private static final long T0 = 10_000L;

    // ==================== computeDisplay 纯函数:端点/中点 ====================

    @Test
    public void computeDisplayEndpointsAndMidpoint() {
        // elapsed <= 0 → start(未起步)
        Assert.assertEquals("elapsed=0 返回起点", 2.0F, SmoothScroller.computeDisplay(2, 8, 0L, 120L), 0.0001F);
        Assert.assertEquals("elapsed<0 返回起点", 2.0F, SmoothScroller.computeDisplay(2, 8, -5L, 120L), 0.0001F);
        // elapsed >= duration → target(完成)
        Assert.assertEquals("elapsed=duration 返回目标", 8.0F, SmoothScroller.computeDisplay(2, 8, 120L, 120L), 0.0001F);
        Assert.assertEquals("elapsed>duration 返回目标", 8.0F, SmoothScroller.computeDisplay(2, 8, 500L, 120L), 0.0001F);
        // 中点:120ms 中点 60ms → easeOutQuad(0.5)=0.75 → 2 + 6×0.75 = 6.5
        Assert.assertEquals("120ms 中点 easeOut(0.5)=0.75", 6.5F,
                SmoothScroller.computeDisplay(2, 8, 60L, 120L), 0.0001F);
        // duration <= 0 = 瞬移语义
        Assert.assertEquals("duration=0 瞬移到目标", 8.0F, SmoothScroller.computeDisplay(2, 8, 30L, 0L), 0.0001F);
    }

    // ==================== setTarget:目标变化重启 / 目标未变不重启 ====================

    @Test
    public void setTargetRestartsFromCurrentDisplayOnChange() {
        SmoothScroller scroller = new SmoothScroller();
        scroller.setTarget(5, T0);
        // 起点 = 当前显示 0(初始贴底)
        Assert.assertEquals("重启起点 = 当前显示", 0.0F, scroller.displayLines(T0), 0.0001F);
        // 中点:easeOut(0.5)=0.75 → 5×0.75=3.75
        Assert.assertEquals("0→5 中点 3.75", 3.75F, scroller.displayLines(T0 + 60L), 0.0001F);

        // 目标变化 5→8:以当前显示 3.75 为起点重启
        scroller.setTarget(8, T0 + 60L);
        Assert.assertEquals("重启帧显示不变(无瞬跳)", 3.75F, scroller.displayLines(T0 + 60L), 0.0001F);
        // 重启后中点:3.75 + (8-3.75)×0.75 = 3.75 + 3.1875 = 6.9375
        Assert.assertEquals("重启后 60ms 中点", 6.9375F, scroller.displayLines(T0 + 120L), 0.0001F);
        // 完成
        Assert.assertEquals("重启后完成", 8.0F, scroller.displayLines(T0 + 180L), 0.0001F);
    }

    @Test
    public void setTargetSameTargetDoesNotRestart() {
        SmoothScroller scroller = new SmoothScroller();
        scroller.setTarget(5, T0);
        // 目标未变(仍 5):不重启,动画仍按 T0 起点推进
        scroller.setTarget(5, T0 + 30L);
        Assert.assertEquals("未重启:仍按原起点插值", 5.0F * 0.75F, scroller.displayLines(T0 + 60L), 0.0001F);
        Assert.assertEquals("原起点结束", 5.0F, scroller.displayLines(T0 + 120L), 0.0001F);
    }

    // ==================== snapTo 直通(拖动接管)与 releaseDrag ====================

    @Test
    public void snapToCancelsSmoothingAndPassesThrough() {
        SmoothScroller scroller = new SmoothScroller();
        scroller.setTarget(5, T0);
        // 动画中途:显示位于 easeOut(0.25)=0.4375 → 5×0.4375=2.1875
        Assert.assertEquals("动画中途显示", 2.1875F, scroller.displayLines(T0 + 30L), 0.0001F);

        // 拖动接管:snapTo 当前显示行 → 直通,取消进行中的平滑
        scroller.snapTo(2);
        Assert.assertTrue("直通即稳定", scroller.isSettled());
        Assert.assertEquals("snapTo 后显示=目标", 2.0F, scroller.displayLines(T0 + 60L), 0.0F);

        // 直通中 setTarget 直接到位(拖动 MOVE 跟手,无 120ms 延迟)
        scroller.setTarget(4, T0 + 90L);
        Assert.assertEquals("直通中目标变化直接到位", 4.0F, scroller.displayLines(T0 + 90L), 0.0F);
        Assert.assertEquals("直通中显示恒=目标", 4.0F, scroller.displayLines(T0 + 100L), 0.0F);

        // 滚轮/回底等非拖动来源:退出直通,恢复平滑语义
        scroller.releaseDrag();
        scroller.setTarget(1, T0 + 120L);
        Assert.assertEquals("退出直通后重启起点=当前显示", 4.0F, scroller.displayLines(T0 + 120L), 0.0001F);
        // 重启中点:4 + (1-4)×0.75 = 4 - 2.25 = 1.75
        Assert.assertEquals("退出直通后平滑中点", 1.75F, scroller.displayLines(T0 + 180L), 0.0001F);
        Assert.assertEquals("退出直通后完成", 1.0F, scroller.displayLines(T0 + 240L), 0.0001F);
    }

    @Test
    public void snapToWhenSettledKeepsDisplay() {
        SmoothScroller scroller = new SmoothScroller();
        scroller.setTarget(5, T0);
        scroller.displayLines(T0 + 120L); // 动画完成
        Assert.assertTrue("动画完成即稳定", scroller.isSettled());

        scroller.snapTo(5); // 已稳定处接管:显示保持 5
        Assert.assertEquals("已稳定接管显示不变", 5.0F, scroller.displayLines(T0 + 200L), 0.0F);
        scroller.setTarget(7, T0 + 200L); // 直通中拖动:直接到位
        Assert.assertEquals("直通拖动到位", 7.0F, scroller.displayLines(T0 + 210L), 0.0F);
    }

    // ==================== isSettled ====================

    @Test
    public void isSettledFollowsAnimationLifecycle() {
        SmoothScroller scroller = new SmoothScroller();
        Assert.assertTrue("初始稳定", scroller.isSettled());

        scroller.setTarget(5, T0);
        scroller.displayLines(T0); // 推进时钟
        Assert.assertFalse("动画进行中不稳定", scroller.isSettled());

        scroller.displayLines(T0 + 120L); // 时长耗尽帧
        Assert.assertTrue("时长耗尽即稳定", scroller.isSettled());
        // 后续保持稳定(目标未变)
        scroller.displayLines(T0 + 1000L);
        Assert.assertTrue("静止后持续稳定", scroller.isSettled());
    }
}
