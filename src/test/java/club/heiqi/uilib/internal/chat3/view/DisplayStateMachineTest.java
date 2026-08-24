package club.heiqi.uilib.internal.chat3.view;

import org.junit.Assert;
import org.junit.Test;

/**
 * DisplayStateMachine 契约测试:开/关动画序列、时长推进、幂等与快速开关可重入。
 */
public class DisplayStateMachineTest {

    private static final long COLLAPSE = 150L;
    private static final long POP = 250L;
    /** 容器关闭动画时长(独立于 pop;设计稿 §4.1 closing 140)。 */
    private static final long CLOSE = 120L;

    @Test
    public void shouldTransitionThroughOpenSequence() {
        DisplayStateMachine machine = new DisplayStateMachine();
        Assert.assertEquals(DisplayStateMachine.Phase.HUD, machine.getPhase());

        machine.setTarget(true, 1000L);
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
        machine.setTarget(true, 1000L);
        machine.tick(1000L + COLLAPSE, COLLAPSE, POP, CLOSE); // POPPING
        machine.tick(1000L + COLLAPSE + POP, COLLAPSE, POP, CLOSE); // CONTAINER
        Assert.assertEquals(DisplayStateMachine.Phase.CONTAINER, machine.getPhase());

        machine.setTarget(false, 5000L);
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
        machine.setTarget(true, 1000L);
        machine.tick(1000L + COLLAPSE, COLLAPSE, POP, CLOSE); // POPPING
        machine.tick(1000L + COLLAPSE + POP, COLLAPSE, POP, CLOSE); // CONTAINER

        machine.setTarget(true, 9999L); // 同目标:稳定态幂等,不重启动画
        Assert.assertEquals(DisplayStateMachine.Phase.CONTAINER, machine.getPhase());
    }

    @Test
    public void shouldRestartOnRapidToggle() {
        DisplayStateMachine machine = new DisplayStateMachine();
        machine.setTarget(true, 1000L);
        machine.setTarget(false, 1100L); // 收起中反悔:以最新目标为准
        Assert.assertEquals(DisplayStateMachine.Phase.CLOSING, machine.getPhase());
        machine.tick(1100L + CLOSE, COLLAPSE, POP, CLOSE);
        Assert.assertEquals(DisplayStateMachine.Phase.HUD, machine.getPhase());
    }
}
