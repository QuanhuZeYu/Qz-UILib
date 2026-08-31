package club.heiqi.uilib.internal.chat3.view;

import org.junit.Assert;
import org.junit.Test;

/**
 * DisplayStateMachine 契约测试:开/关动画序列、时长推进、幂等与 §4.2 重入规则
 * (COLLAPSING 反悔反向播放 / 反向中再展开正向续播 / CLOSING 不可打断 + pendingOpen /
 * POPPING 中关闭从半途折算反向起点)。
 */
public class DisplayStateMachineTest {

    private static final long COLLAPSE = 150L;
    private static final long POP = 250L;
    /** 容器关闭动画时长(独立于 pop;设计稿 §4.1 closing 140)。 */
    private static final long CLOSE = 120L;

    private static void setTarget(DisplayStateMachine machine, boolean open, long nowMillis) {
        machine.setTarget(open, nowMillis, COLLAPSE, POP, CLOSE);
    }

    @Test
    public void shouldTransitionThroughOpenSequence() {
        DisplayStateMachine machine = new DisplayStateMachine();
        Assert.assertEquals(DisplayStateMachine.Phase.HUD, machine.getPhase());

        setTarget(machine, true, 1000L);
        Assert.assertEquals(DisplayStateMachine.Phase.COLLAPSING, machine.getPhase());

        // 收起未结束
        machine.tick(1000L + 100L, COLLAPSE, POP, CLOSE);
        Assert.assertEquals(DisplayStateMachine.Phase.COLLAPSING, machine.getPhase());
        Assert.assertEquals(0.6666F, machine.progress(1000L + 100L, COLLAPSE), 0.001F);

        // 收起结束 → 弹出
        machine.tick(1000L + COLLAPSE, COLLAPSE, POP, CLOSE);
        Assert.assertEquals(DisplayStateMachine.Phase.POPPING, machine.getPhase());
        Assert.assertEquals(0.0F, machine.progress(1000L + COLLAPSE, POP), 0.001F);

        // 弹出结束 → 稳定
        machine.tick(1000L + COLLAPSE + POP, COLLAPSE, POP, CLOSE);
        Assert.assertEquals(DisplayStateMachine.Phase.CONTAINER, machine.getPhase());
        Assert.assertEquals(1.0F, machine.progress(1000L + COLLAPSE + POP, POP), 0.001F);
    }

    @Test
    public void shouldTransitionThroughCloseSequence() {
        DisplayStateMachine machine = new DisplayStateMachine();
        setTarget(machine, true, 1000L);
        machine.tick(1000L + COLLAPSE, COLLAPSE, POP, CLOSE); // POPPING
        machine.tick(1000L + COLLAPSE + POP, COLLAPSE, POP, CLOSE); // CONTAINER
        Assert.assertEquals(DisplayStateMachine.Phase.CONTAINER, machine.getPhase());

        setTarget(machine, false, 5000L);
        Assert.assertEquals(DisplayStateMachine.Phase.CLOSING, machine.getPhase());

        // CLOSING 用独立 closing 时长推进(未到时长仍 CLOSING)
        machine.tick(5000L + CLOSE - 1L, COLLAPSE, POP, CLOSE);
        Assert.assertEquals(DisplayStateMachine.Phase.CLOSING, machine.getPhase());
        machine.tick(5000L + CLOSE, COLLAPSE, POP, CLOSE);
        Assert.assertEquals(DisplayStateMachine.Phase.HUD, machine.getPhase());
    }

    @Test
    public void shouldBeIdempotentForSameTarget() {
        DisplayStateMachine machine = new DisplayStateMachine();
        setTarget(machine, true, 1000L);
        machine.tick(1000L + COLLAPSE, COLLAPSE, POP, CLOSE); // POPPING
        machine.tick(1000L + COLLAPSE + POP, COLLAPSE, POP, CLOSE); // CONTAINER

        setTarget(machine, true, 9999L); // 同目标:稳定态幂等,不重启动画
        Assert.assertEquals(DisplayStateMachine.Phase.CONTAINER, machine.getPhase());
    }

    // ==================== §4.2 重入:COLLAPSING 反悔反向播放 ====================

    @Test
    public void shouldReverseCollapseWhenRepentingMidCollapse() {
        DisplayStateMachine machine = new DisplayStateMachine();
        setTarget(machine, true, 1000L);
        // 收起 100ms(总 150)处反悔:p = 100/150 = 0.6667
        setTarget(machine, false, 1100L);
        Assert.assertEquals("反悔后仍在 COLLAPSING(反向播放,不硬切 CLOSING)",
                DisplayStateMachine.Phase.COLLAPSING, machine.getPhase());
        // 进度连续:反悔时刻 p 保持 0.6667,随后递减(不跳变)
        Assert.assertEquals(0.6667F, machine.progress(1100L, COLLAPSE), 0.001F);
        machine.tick(1150L, COLLAPSE, POP, CLOSE); // 反向 50ms → p = 0.6667 − 1/3
        Assert.assertEquals(0.3333F, machine.progress(1150L, COLLAPSE), 0.001F);
        // 反向播完(p 归零)→ HUD
        machine.tick(1200L, COLLAPSE, POP, CLOSE);
        Assert.assertEquals(DisplayStateMachine.Phase.HUD, machine.getPhase());
    }

    @Test
    public void shouldResumeForwardFromCurrentProgressAfterUnReverse() {
        DisplayStateMachine machine = new DisplayStateMachine();
        setTarget(machine, true, 1000L);
        machine.tick(1060L, COLLAPSE, POP, CLOSE); // p = 60/150 = 0.4
        setTarget(machine, false, 1060L); // 反悔:反向锚定 p=0.4
        machine.tick(1090L, COLLAPSE, POP, CLOSE); // 反向 30ms → p = 0.4 − 0.2 = 0.2
        Assert.assertEquals(0.2F, machine.progress(1090L, COLLAPSE), 0.001F);
        // 再展开:从当前 p=0.2 正向续播(不硬切到 0)
        setTarget(machine, true, 1090L);
        Assert.assertEquals("再展开仍 COLLAPSING", DisplayStateMachine.Phase.COLLAPSING,
                machine.getPhase());
        machine.tick(1135L, COLLAPSE, POP, CLOSE); // 正向 45ms → p = 0.2 + 0.3 = 0.5
        Assert.assertEquals(0.5F, machine.progress(1135L, COLLAPSE), 0.001F);
    }

    // ==================== §4.2 重入:CLOSING 不可打断 + pendingOpen ====================

    @Test
    public void closingIsNotInterruptibleAndPendingOpenFiresAfterCompletion() {
        DisplayStateMachine machine = new DisplayStateMachine();
        setTarget(machine, true, 1000L);
        machine.tick(1000L + COLLAPSE, COLLAPSE, POP, CLOSE); // POPPING
        machine.tick(1000L + COLLAPSE + POP, COLLAPSE, POP, CLOSE); // CONTAINER
        setTarget(machine, false, 5000L); // CLOSING

        setTarget(machine, true, 5030L); // CLOSING 中请求打开 → 不可打断,挂起
        Assert.assertEquals("CLOSING 中不可被打断为 COLLAPSING/POPPING",
                DisplayStateMachine.Phase.CLOSING, machine.getPhase());
        machine.tick(5000L + CLOSE - 1L, COLLAPSE, POP, CLOSE);
        Assert.assertEquals("未完成仍 CLOSING", DisplayStateMachine.Phase.CLOSING,
                machine.getPhase());

        // 关闭动画完成 → 兑现挂起的打开,自动进入 COLLAPSING(从头)
        machine.tick(5000L + CLOSE, COLLAPSE, POP, CLOSE);
        Assert.assertEquals("完成后兑现 pendingOpen 进 COLLAPSING",
                DisplayStateMachine.Phase.COLLAPSING, machine.getPhase());
        Assert.assertEquals(0.0F, machine.progress(5000L + CLOSE, COLLAPSE), 0.001F);
        // 随后正常走完打开序列
        machine.tick(5000L + CLOSE + COLLAPSE, COLLAPSE, POP, CLOSE);
        Assert.assertEquals(DisplayStateMachine.Phase.POPPING, machine.getPhase());
    }

    @Test
    public void pendingOpenIsCancelledByClosingAgain() {
        DisplayStateMachine machine = new DisplayStateMachine();
        setTarget(machine, true, 1000L);
        machine.tick(1000L + COLLAPSE, COLLAPSE, POP, CLOSE);
        machine.tick(1000L + COLLAPSE + POP, COLLAPSE, POP, CLOSE);
        setTarget(machine, false, 5000L);
        setTarget(machine, true, 5030L); // pendingOpen
        setTarget(machine, false, 5050L); // 关闭中再关闭:取消挂起
        machine.tick(5000L + CLOSE, COLLAPSE, POP, CLOSE);
        Assert.assertEquals("挂起已取消:完成后回 HUD 而非 COLLAPSING",
                DisplayStateMachine.Phase.HUD, machine.getPhase());
    }

    // ==================== §4.2 重入:POPPING 中关闭折算反向起点 ====================

    @Test
    public void closingMidPopStartsFromConvertedReverseProgress() {
        DisplayStateMachine machine = new DisplayStateMachine();
        setTarget(machine, true, 1000L);
        machine.tick(1000L + COLLAPSE, COLLAPSE, POP, CLOSE); // POPPING,now=1150
        // pop p = 62/250 = 0.248 → closing 折算起点 = 1 − p = 0.752
        setTarget(machine, false, 1150L + 62L);
        Assert.assertEquals(DisplayStateMachine.Phase.CLOSING, machine.getPhase());
        Assert.assertEquals("closing 从 1−p 折算起点开始", 0.752F,
                machine.progress(1150L + 62L, CLOSE), 0.001F);
        // 折算后按 closing 时长继续推进(未完成仍在 CLOSING)
        machine.tick(1150L + 62L + 10L, COLLAPSE, POP, CLOSE);
        Assert.assertEquals(DisplayStateMachine.Phase.CLOSING, machine.getPhase());
        Assert.assertEquals("折算起点 + 10/120", 0.752F + 0.08333F,
                machine.progress(1150L + 62L + 10L, CLOSE), 0.001F);
        // 剩余推进走完(closing 起点非 0,总时长按剩余进度缩短)
        machine.tick(1150L + 62L + 30L, COLLAPSE, POP, CLOSE);
        Assert.assertEquals("折算后 30ms 完成关闭回 HUD", DisplayStateMachine.Phase.HUD,
                machine.getPhase());
    }

    @Test
    public void closingMidPopNearEndStartsClosingFromBeginning() {
        DisplayStateMachine machine = new DisplayStateMachine();
        setTarget(machine, true, 1000L);
        machine.tick(1000L + COLLAPSE, COLLAPSE, POP, CLOSE); // POPPING
        // pop 接近完成(p→1)→ closing 从起点 0 开始(几乎满显,关闭动画完整播放)
        setTarget(machine, false, 1150L + POP);
        Assert.assertEquals(DisplayStateMachine.Phase.CLOSING, machine.getPhase());
        Assert.assertEquals(0.0F, machine.progress(1150L + POP, CLOSE), 0.001F);
    }

    // ==================== forceHud:容器收回动画由输入屏播完后直落 HUD ====================

    /** forceHud 跳过 CLOSING 空窗:CLOSING 中直接落 HUD(锚点归零,目标关闭)。 */
    @Test
    public void forceHudSkipsClosingStraightToHud() {
        DisplayStateMachine machine = new DisplayStateMachine();
        setTarget(machine, true, 1000L);
        machine.tick(1000L + COLLAPSE, COLLAPSE, POP, CLOSE);
        machine.tick(1000L + COLLAPSE + POP, COLLAPSE, POP, CLOSE); // CONTAINER
        setTarget(machine, false, 5000L); // CLOSING(容器收回空窗)
        Assert.assertEquals(DisplayStateMachine.Phase.CLOSING, machine.getPhase());

        machine.forceHud(5010L);
        Assert.assertEquals("forceHud 跳过 CLOSING 直接落 HUD", DisplayStateMachine.Phase.HUD,
                machine.getPhase());
        Assert.assertEquals("HUD 稳定 progress=0", 0.0F, machine.progress(5010L, COLLAPSE), 0.001F);
    }

    /** forceHud 时若 CLOSING 期间挂起了打开请求(pendingOpen),先兑现进 COLLAPSING(§4.2)。 */
    @Test
    public void forceHudHonorsPendingOpenByEnteringCollapsing() {
        DisplayStateMachine machine = new DisplayStateMachine();
        setTarget(machine, true, 1000L);
        machine.tick(1000L + COLLAPSE, COLLAPSE, POP, CLOSE);
        machine.tick(1000L + COLLAPSE + POP, COLLAPSE, POP, CLOSE); // CONTAINER
        setTarget(machine, false, 5000L); // CLOSING
        setTarget(machine, true, 5030L); // CLOSING 中请求打开 → pendingOpen

        machine.forceHud(5050L);
        Assert.assertEquals("forceHud 兑现挂起打开进 COLLAPSING",
                DisplayStateMachine.Phase.COLLAPSING, machine.getPhase());
        Assert.assertEquals("COLLAPSING 从头播放", 0.0F, machine.progress(5050L, COLLAPSE), 0.001F);
    }

    /** forceHud 幂等:HUD 稳定态重复调用无变化(阶段/目标/进度不变)。 */
    @Test
    public void forceHudIsIdempotentInHudStableState() {
        DisplayStateMachine machine = new DisplayStateMachine();
        machine.forceHud(1000L);
        Assert.assertEquals(DisplayStateMachine.Phase.HUD, machine.getPhase());
        machine.forceHud(2000L);
        Assert.assertEquals("重复 forceHud 仍 HUD", DisplayStateMachine.Phase.HUD,
                machine.getPhase());
        Assert.assertEquals("HUD 稳定 progress 恒 0", 0.0F, machine.progress(2000L, COLLAPSE), 0.001F);
    }

    // ==================== S2:pendingOpen 兑现标志(rebuildTree 权威信号) ====================

    /**
     * S2:tick 路径兑现置位——CLOSING 中挂起打开,CLOSING 完成后 tick 兑现进 COLLAPSING,
     * 同帧标志可见(true);consume 读并清位,一次性语义(再次 peek false)。
     */
    @Test
    public void redemptionFlagSetByTickAndConsumedOnce() {
        DisplayStateMachine machine = new DisplayStateMachine();
        setTarget(machine, true, 1000L);
        machine.tick(1000L + COLLAPSE, COLLAPSE, POP, CLOSE); // POPPING
        machine.tick(1000L + COLLAPSE + POP, COLLAPSE, POP, CLOSE); // CONTAINER
        setTarget(machine, false, 5000L); // CLOSING
        setTarget(machine, true, 5030L); // CLOSING 中请求打开 → pendingOpen 挂起
        Assert.assertFalse("挂起期兑现标志未置位", machine.isPendingOpenRedeemed());

        machine.tick(5000L + CLOSE, COLLAPSE, POP, CLOSE); // CLOSING 完成 → 兑现进 COLLAPSING
        Assert.assertEquals("兑现进 COLLAPSING", DisplayStateMachine.Phase.COLLAPSING,
                machine.getPhase());
        Assert.assertTrue("兑现帧标志可见", machine.isPendingOpenRedeemed());
        Assert.assertTrue("consume 读并清位", machine.consumePendingOpenRedeemed());
        Assert.assertFalse("消费后 peek 为 false(一次性)", machine.isPendingOpenRedeemed());
        Assert.assertFalse("重复 consume 返回 false", machine.consumePendingOpenRedeemed());
    }

    /**
     * S2:forceHud 路径兑现置位;非兑现 forceHud 分支清位(卫生)——先造 CLOSING 兑现,
     * 再关再开再 forceHud 兑现,标志重新置位;随后 plain forceHud(无挂起)清位。
     */
    @Test
    public void redemptionFlagSetByForceHudAndClearedByNonPendingForceHud() {
        DisplayStateMachine machine = new DisplayStateMachine();
        setTarget(machine, true, 1000L);
        machine.tick(1000L + COLLAPSE, COLLAPSE, POP, CLOSE); // POPPING
        machine.tick(1000L + COLLAPSE + POP, COLLAPSE, POP, CLOSE); // CONTAINER
        setTarget(machine, false, 5000L); // CLOSING
        setTarget(machine, true, 5030L); // pendingOpen 挂起

        machine.forceHud(5050L);
        Assert.assertEquals("forceHud 兑现进 COLLAPSING", DisplayStateMachine.Phase.COLLAPSING,
                machine.getPhase());
        Assert.assertTrue("forceHud 兑现帧标志置位", machine.isPendingOpenRedeemed());
        Assert.assertTrue("consume 消费一次", machine.consumePendingOpenRedeemed());

        // 普通关闭路径再走一遍:非兑现 forceHud 清位
        machine.tick(6000L + COLLAPSE, COLLAPSE, POP, CLOSE); // POPPING
        machine.tick(6000L + COLLAPSE + POP, COLLAPSE, POP, CLOSE); // CONTAINER
        setTarget(machine, false, 7000L); // CLOSING(无挂起)
        machine.forceHud(7050L);
        Assert.assertEquals("plain forceHud 落 HUD", DisplayStateMachine.Phase.HUD,
                machine.getPhase());
        Assert.assertFalse("非兑现 forceHud 清位", machine.isPendingOpenRedeemed());
    }

    /** S2:普通关闭(无挂起)CLOSING 完成落 HUD 不置位;消费后同样不置位。 */
    @Test
    public void plainCloseToHudDoesNotSetFlag() {
        DisplayStateMachine machine = new DisplayStateMachine();
        setTarget(machine, true, 1000L);
        machine.tick(1000L + COLLAPSE, COLLAPSE, POP, CLOSE); // POPPING
        machine.tick(1000L + COLLAPSE + POP, COLLAPSE, POP, CLOSE); // CONTAINER
        setTarget(machine, false, 5000L);
        machine.tick(5000L + CLOSE, COLLAPSE, POP, CLOSE); // CLOSING 完成 → HUD
        Assert.assertEquals("plain close 落 HUD", DisplayStateMachine.Phase.HUD,
                machine.getPhase());
        Assert.assertFalse("plain close 不置位", machine.isPendingOpenRedeemed());
        Assert.assertFalse("消费仍 false", machine.consumePendingOpenRedeemed());
    }
}
