package club.heiqi.uilib.internal.chat3.view;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.scene.node.Transform;

/**
 * ChatSurfaceAnimator 开合动画状态机契约测试(headless,时间精确注入):
 *
 * <p>覆盖 TB2 关闭动画可见性要求的核心纯逻辑:开始(弹入/请求关闭/§4.2 折算起点)、
 * 进度(140ms 淡出+下滑)、完成回调(单次触发)、超时兜底(500ms 强制完成)、重入幂等
 * (同请求同令牌、起点不重置),以及 render 阶段切换的输出(transform/opacity 与
 * ChatSceneController §4.1 同参数同曲线)。</p>
 */
public class ChatSurfaceAnimatorTest {

    /** 生产参数镜像(与 ChatMarkdownSettings 默认一致:p op 240 / closing 140 / 超时 500)。 */
    private static final long POP = 240L;
    private static final long CLOSING = 140L;
    private static final long TIMEOUT = ChatSurfaceAnimator.DEFAULT_CLOSE_TIMEOUT_MILLIS;

    /** 完成回调触发计数(JUnit 每测试方法新建实例,互不污染)。 */
    private final AtomicInteger fired = new AtomicInteger();

    /** 走完整触发链路:推进状态机 → 取走回调 → 执行(与 ChatInputSurface.tickCloseState 同序)。 */
    private void tickAndRunCallbacks(ChatSurfaceAnimator animator, long nowMillis) {
        animator.tick(nowMillis);
        Runnable callback = animator.takeCloseCallback();
        if (callback != null) {
            callback.run();
        }
    }

    @Test
    public void openPopAnimatesFromBottomLeftAndSettles() {
        ChatSurfaceAnimator animator = new ChatSurfaceAnimator(POP, CLOSING, TIMEOUT);
        animator.startOpen(0L);
        Assert.assertEquals("构造后即弹入阶段", ChatSurfaceAnimator.Phase.OPEN, animator.phase());
        Assert.assertEquals("弹出起点进度 0", 0.0F, animator.progress(0L), 0.0001F);
        Assert.assertEquals("弹出半程进度 0.5", 0.5F, animator.progress(POP / 2), 0.0001F);
        Assert.assertEquals("弹出结束进度 1", 1.0F, animator.progress(POP), 0.0001F);
        Assert.assertEquals("稳定后进度保持 1", 1.0F, animator.progress(1000L), 0.0001F);
        Assert.assertEquals("起点透明度 0", 0.0F, animator.opacity(0L), 0.0001F);
        // p=0.3: easeOutBack(0.3)=0.80988 → opacity 同值,translateY 24×(1−eased)≈4.563,scale 0.9924
        Assert.assertEquals("弹出 72ms 透明度随 easeOutBack", 0.80988F, animator.opacity(72L), 0.001F);
        Transform mid = animator.transform(72L);
        Assert.assertEquals("弹出 72ms 下滑量 +24→0 途中", 4.56288F, mid.translateY, 0.001F);
        Assert.assertEquals("弹出 72ms 缩放 0.96→1 途中", 0.9924F, mid.scaleX, 0.001F);
        Assert.assertEquals("弹出 72ms 双轴同缩放", mid.scaleX, mid.scaleY, 0.0001F);
        Transform end = animator.transform(POP);
        Assert.assertEquals("弹出结束平移归零", 0.0F, end.translateY, 0.001F);
        Assert.assertEquals("弹出结束缩放归一", 1.0F, end.scaleX, 0.001F);
        Assert.assertEquals("弹出结束透明度 1", 1.0F, animator.opacity(POP), 0.0001F);
    }

    @Test
    public void openPopKeepsTransformOvershootButClampsOpacity() {
        // p=0.5: easeOutBack = 1.005(overshoot)>1 → opacity clamp01 到 1,transform 保留微超调
        ChatSurfaceAnimator animator = new ChatSurfaceAnimator(POP, CLOSING, TIMEOUT);
        animator.startOpen(0L);
        Assert.assertEquals("opacity 通道 clamp01 不超 1", 1.0F, animator.opacity(POP / 2), 0.0001F);
        Assert.assertEquals("transform 通道保留 overshoot 微量上弹", -0.12F, animator.transform(POP / 2).translateY, 0.001F);
    }

    @Test
    public void closeAfterSettledPlaysFullFadeOutAndSlideDown() {
        ChatSurfaceAnimator animator = new ChatSurfaceAnimator(POP, CLOSING, TIMEOUT);
        animator.startOpen(0L);
        animator.tick(1000L); // 稳定后(弹出已完成)
        ChatSurfaceAnimator.CloseRequest request = animator.requestClose(() -> fired.incrementAndGet(), 1000L);
        Assert.assertNotNull("关闭请求令牌非空", request);
        Assert.assertEquals("进入关闭阶段", ChatSurfaceAnimator.Phase.CLOSING, animator.phase());
        Assert.assertTrue("关闭进行中", animator.isClosing());
        Assert.assertEquals("稳定后关闭从 0 起播", 0.0F, animator.progress(1000L), 0.0001F);
        Assert.assertEquals("关闭半程 70ms = 0.5", 0.5F, animator.progress(1070L), 0.0001F);
        // p=0.5: easeOut=0.75 → opacity 1−0.75=0.25,translateY 12×0.75=9(scale 不参与)
        Assert.assertEquals("关闭半程透明度 0.25", 0.25F, animator.opacity(1070L), 0.001F);
        Transform mid = animator.transform(1070L);
        Assert.assertEquals("关闭半程下滑 9px", 9.0F, mid.translateY, 0.001F);
        Assert.assertEquals("关闭不参与缩放", 1.0F, mid.scaleX, 0.001F);
        tickAndRunCallbacks(animator, 1139L);
        Assert.assertEquals("139/140 未完成:仍在关闭", ChatSurfaceAnimator.Phase.CLOSING, animator.phase());
        Assert.assertEquals("未完成不触发回调", 0, fired.get());
        tickAndRunCallbacks(animator, 1140L);
        Assert.assertEquals("140ms 完成 → 关闭终态", ChatSurfaceAnimator.Phase.CLOSED, animator.phase());
        Assert.assertTrue("已关闭", animator.isClosed());
        Assert.assertEquals("完成回调恰好触发一次", 1, fired.get());
        Assert.assertEquals("终态进度 1", 1.0F, animator.progress(2000L), 0.0001F);
        tickAndRunCallbacks(animator, 2000L);
        Assert.assertEquals("重复推进不重复触发回调", 1, fired.get());
    }

    @Test
    public void closedPhaseOutputsInvisibleState() {
        ChatSurfaceAnimator animator = new ChatSurfaceAnimator(POP, CLOSING, TIMEOUT);
        animator.startOpen(0L);
        animator.requestClose(null, 1000L); // 不注册回调,只验证视觉终态
        tickAndRunCallbacks(animator, 1140L);
        Assert.assertEquals(ChatSurfaceAnimator.Phase.CLOSED, animator.phase());
        Assert.assertEquals("关闭完成透明", 0.0F, animator.opacity(5000L), 0.0001F);
        Transform closed = animator.transform(5000L);
        Assert.assertEquals("关闭完成下滑到底 +12", 12.0F, closed.translateY, 0.001F);
        Assert.assertEquals("关闭完成缩放归一", 1.0F, closed.scaleX, 0.001F);
    }

    @Test
    public void closeDuringPopStartsFromReversedProgress() {
        // §4.2 折算:弹出一半(p=0.5)时关闭从 1−p=0.5 起播,比从头播提前完成
        ChatSurfaceAnimator animator = new ChatSurfaceAnimator(POP, CLOSING, TIMEOUT);
        animator.startOpen(0L);
        animator.requestClose(() -> fired.incrementAndGet(), 120L);
        Assert.assertEquals("折算起点 1−0.5", 0.5F, animator.progress(120L), 0.0001F);
        Assert.assertEquals("起点前 elapsed 负值:0.5 − 2/140 仍在起点附近", 0.48571F, animator.progress(118L), 0.001F);
        tickAndRunCallbacks(animator, 190L); // 120 + 70ms:0.5 + 0.5 = 1
        Assert.assertEquals("折算路径提前完成(140ms 全播需到 260ms)", ChatSurfaceAnimator.Phase.CLOSED, animator.phase());
        Assert.assertEquals("折算路径回调触发", 1, fired.get());
    }

    @Test
    public void closeWhileNotYetVisibleCompletesImmediately() {
        // p=0 时 1−p=1:容器尚未弹出(不可见),关闭无可播内容 → 下一推进即完成(与
        // DisplayStateMachine POPPING p=0 → CLOSING 从 1 起播同语义)
        ChatSurfaceAnimator animator = new ChatSurfaceAnimator(POP, CLOSING, TIMEOUT);
        animator.startOpen(0L);
        animator.requestClose(() -> fired.incrementAndGet(), 0L);
        Assert.assertEquals("未弹出折算起点 1", 1.0F, animator.progress(0L), 0.0001F);
        tickAndRunCallbacks(animator, 0L);
        Assert.assertEquals(ChatSurfaceAnimator.Phase.CLOSED, animator.phase());
        Assert.assertEquals(1, fired.get());
    }

    @Test
    public void reentryReturnsSameRequestAndKeepsTimeline() {
        ChatSurfaceAnimator animator = new ChatSurfaceAnimator(POP, 10000L, 60000L);
        animator.startOpen(0L);
        animator.tick(1000L);
        ChatSurfaceAnimator.CloseRequest first = animator.requestClose(() -> fired.incrementAndGet(), 1000L);
        ChatSurfaceAnimator.CloseRequest second = animator.requestClose(() -> fired.incrementAndGet(), 2000L);
        Assert.assertSame("动画期间重复请求返回同一令牌", first, second);
        Assert.assertEquals("重复请求不重置起点:进度仍按首次请求计时", 0.1F, animator.progress(2000L), 0.001F);
        tickAndRunCallbacks(animator, 2000L);
        Assert.assertEquals("未完成不触发回调", 0, fired.get());
        Assert.assertEquals("仍在关闭阶段", ChatSurfaceAnimator.Phase.CLOSING, animator.phase());
    }

    @Test
    public void hangingCloseForcesCompletionByTimeout() {
        // closing 10000ms ≫ 500ms 兜底:推进到 500ms 仍未完成进度 1 → 超时强制完成(不卡死屏幕)
        ChatSurfaceAnimator animator = new ChatSurfaceAnimator(POP, 10000L, 500L);
        animator.startOpen(0L);
        animator.tick(1000L);
        animator.requestClose(() -> fired.incrementAndGet(), 1000L);
        tickAndRunCallbacks(animator, 1499L);
        Assert.assertEquals("499ms 未超时:仍在关闭", ChatSurfaceAnimator.Phase.CLOSING, animator.phase());
        Assert.assertEquals("499ms 未触发回调", 0, fired.get());
        tickAndRunCallbacks(animator, 1500L);
        Assert.assertEquals("500ms 超时强制完成", ChatSurfaceAnimator.Phase.CLOSED, animator.phase());
        Assert.assertEquals("超时兜底触发回调", 1, fired.get());
    }

    @Test
    public void reentryDoesNotExtendTimeout() {
        // 重入请求不重置起点 → 超时仍按首次请求计时(1500ms 强制完成,不因重复按键延期)
        ChatSurfaceAnimator animator = new ChatSurfaceAnimator(POP, 10000L, 500L);
        animator.startOpen(0L);
        animator.tick(1000L);
        animator.requestClose(() -> fired.incrementAndGet(), 1000L);
        animator.requestClose(() -> fired.incrementAndGet(), 1400L); // 重入:同令牌
        tickAndRunCallbacks(animator, 1499L);
        Assert.assertEquals(0, fired.get());
        tickAndRunCallbacks(animator, 1500L);
        Assert.assertEquals("重入不延期超时", 1, fired.get());
    }

    @Test
    public void zeroDurationCloseCompletesOnNextTick() {
        // closing ≤0 = 瞬完语义(与 SmoothScroller 瞬移语义一致)
        ChatSurfaceAnimator animator = new ChatSurfaceAnimator(1L, 0L, 500L);
        animator.startOpen(0L);
        animator.requestClose(() -> fired.incrementAndGet(), 100L);
        tickAndRunCallbacks(animator, 100L);
        Assert.assertEquals(ChatSurfaceAnimator.Phase.CLOSED, animator.phase());
        Assert.assertEquals(1, fired.get());
    }

    /**
     * 超时联动(2026-08-29 用户高层语义「关闭动画必须完整,时长可配置至秒级」):
     * 装配超时 ≥ closing + 500 —— 任何配置时长下动画完整播放,超时只兜底渲染挂起;
     * 缩短 closing(瞬完 0)仍保底 500。
     */
    @Test
    public void closeTimeoutFollowsClosingMillis() {
        Assert.assertEquals("closing=140 → 640(完整播放 + 500 缓冲)", 640L,
                ChatSurfaceAnimator.closeTimeoutFor(140L));
        Assert.assertEquals("closing=5000 → 5500(5s 动画不被截断)", 5500L,
                ChatSurfaceAnimator.closeTimeoutFor(5000L));
        Assert.assertEquals("closing=0(瞬完)保底 500", 500L,
                ChatSurfaceAnimator.closeTimeoutFor(0L));
        Assert.assertEquals("负 closing 保底 500", 500L,
                ChatSurfaceAnimator.closeTimeoutFor(-100L));

        // 配 5s 关闭动画:超时(5500)后动画才完成,期间不被截断
        ChatSurfaceAnimator animator = new ChatSurfaceAnimator(POP, 5000L,
                ChatSurfaceAnimator.closeTimeoutFor(5000L));
        animator.startOpen(0L);
        animator.requestClose(null, 1000L);
        animator.tick(4000L); // 动画 3s 处:仍 CLOSING(未被 500 截断)
        Assert.assertEquals("5s 动画 3s 处仍播放", ChatSurfaceAnimator.Phase.CLOSING, animator.phase());
        animator.tick(6000L); // 动画完成(5s)
        Assert.assertEquals("5s 动画完整播放后完成", ChatSurfaceAnimator.Phase.CLOSED, animator.phase());
    }

    @Test
    public void requestBeforeOpenIsTreatedAsClosedIdempotent() {
        // 构造后未 startOpen(初始 CLOSED):请求关闭幂等返回非空令牌,不进入 CLOSING
        ChatSurfaceAnimator animator = new ChatSurfaceAnimator(POP, CLOSING, TIMEOUT);
        ChatSurfaceAnimator.CloseRequest request = animator.requestClose(null, 0L);
        Assert.assertNotNull(request);
        Assert.assertEquals(ChatSurfaceAnimator.Phase.CLOSED, animator.phase());
        tickAndRunCallbacks(animator, 1000L);
        Assert.assertEquals("未打开不可进入关闭", ChatSurfaceAnimator.Phase.CLOSED, animator.phase());
    }
}
