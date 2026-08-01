package club.heiqi.uilib.font;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.event.FontReloadRequest;
import club.heiqi.uilib.font.shader.FontShaderProgram;

/**
 * `FontService` 轻量布局期入口冒烟。
 *
 * <p>该测试只覆盖 {@link FontService#ensureLayoutRuntimeReady()} 这条不触碰字符页/调度器/批渲染器/着色器
 * 的入口；reload signal、fallback 选择与并发 reload 行为分别由
 * {@code FontReloadSignalTest}、{@code FontMatcherRuntimeVersionTest}、
 * {@code GlyphGenerationDispatcherReloadBarrierTest}、
 * {@code GlyphRuntimeVersionIsolationTest} 在更接近代际隔离边界的层级覆盖。</p>
 *
 * <p>本测试默认不会运行 {@code FontService#initialize()}，避免触发 GL 资源初始化。LTS 期间若需要补 reload
 * 全链路冷测，应在能够提供 GL 上下文的集成测试中进行，而不是在 JVM 单元测试里 mock 整个调度链。</p>
 */
public class FontServiceLayoutRuntimeSmokeTest {

    /**
     * 多次调用 {@link FontService#ensureLayoutRuntimeReady} 应当幂等，不会让 measure epoch 异常翻倍。
     */
    @Test
    public void shouldKeepLayoutRuntimeIdempotentAcrossRepeatedEnsureCalls() {
        FontService service = FontService.getInstance();

        service.ensureLayoutRuntimeReady();
        int firstEpoch = service.getTextMeasureEpoch();
        service.ensureLayoutRuntimeReady();
        service.ensureLayoutRuntimeReady();
        int laterEpoch = service.getTextMeasureEpoch();

        Assert.assertTrue("epoch 应当至少在首次 ensure 后非零", firstEpoch >= 0);
        Assert.assertEquals("再次 ensure 不应让 epoch 推进", firstEpoch, laterEpoch);
    }

    /**
     * 在未调用 {@link FontService#initialize} 时，{@link FontService#isInitialized} 仍可能为 true（取决于运行顺序），
     * 但 {@link FontService#getRuntimeVersion} 应单调非递减。
     */
    @Test
    public void shouldExposeMonotonicRuntimeVersionAcrossLayoutRuntimeWarmup() {
        FontService service = FontService.getInstance();

        int versionBeforeWarmup = service.getRuntimeVersion();
        service.ensureLayoutRuntimeReady();
        int versionAfterWarmup = service.getRuntimeVersion();

        Assert.assertTrue("runtime version 应在 warmup 后非递减",
                versionAfterWarmup >= versionBeforeWarmup);
        Assert.assertTrue("runtime version 一定不为负", versionAfterWarmup >= 0);
    }

    /**
     * JVM 退出钩子等非渲染线程不应直接释放 GL 资源，避免关闭游戏时触发 native 崩溃。
     */
    @Test
    public void shouldSkipGlResourceReleaseFromNonRenderThreadShutdown() throws Exception {
        FontService service = newFontService();
        AtomicBoolean initialized = getAtomicBooleanField(service, "initialized");
        TrackingFontShaderProgram shaderProgram = new TrackingFontShaderProgram();

        initialized.set(true);
        setField(service, "shaderProgram", shaderProgram);
        setField(service, "renderThread", new Thread("Client thread"));

        service.shutdown();

        Assert.assertFalse("非渲染线程不应释放 shader", shaderProgram.closed.get());
        Assert.assertFalse("shutdown 后字体系统应标记为未初始化", initialized.get());
    }

    /** worker 只发布 reload signal，draw-stage 不得推进 reconcile，shutdown 清除旧 lifecycle。 */
    @Test
    public void shouldKeepReloadSignalOutOfWorkerAndDrawExecutionPaths() throws Exception {
        AtomicLong now = new AtomicLong(0L);
        FontReloadSignal signal = new FontReloadSignal(100L, 50L, 200L, now::get);
        FontService service = new FontService(signal);
        int initialRuntimeVersion = service.getRuntimeVersion();

        Thread worker = new Thread(
                () -> service.reload(new FontReloadRequest("worker-signal")), "font-reload-test-worker");
        worker.setDaemon(true);
        worker.start();
        worker.join(5000L);

        Assert.assertFalse("reload signal worker 应及时结束", worker.isAlive());
        Assert.assertFalse("signal 不应初始化字体运行时", service.isInitialized());
        Assert.assertEquals("signal 不应同步推进 runtime version", initialRuntimeVersion,
                service.getRuntimeVersion());
        Assert.assertEquals(1, signal.getPendingCount());
        Assert.assertFalse(signal.isInFlight());

        getAtomicBooleanField(service, "initialized").set(true);
        setField(service, "renderThread", Thread.currentThread());
        now.set(1000L);
        service.tickDrawStage(1);
        Assert.assertEquals("draw-stage 只允许 upload，不得 reconcile", 1, signal.getPendingCount());
        Assert.assertFalse(signal.isInFlight());

        service.shutdown();
        Assert.assertEquals("shutdown 必须清除旧 lifecycle signal", 0, signal.getPendingCount());
    }

    /** 非客户端线程不能抢占 owner；shutdown 后新 lifecycle 仍须由明确 Client thread 绑定。 */
    @Test
    public void shouldBindOnlyExplicitClientRenderThreadAcrossLifecycles() throws Exception {
        AtomicLong now = new AtomicLong(0L);
        FontReloadSignal signal = new FontReloadSignal(0L, 0L, 0L, now::get);
        FontService service = new FontService(signal);
        getAtomicBooleanField(service, "initialized").set(true);

        service.tickMainThread(0);
        Assert.assertNull("普通测试线程不得抢占 render owner", getField(service, "renderThread"));

        Thread firstOwner = runRenderTick(service, "Client thread-font-owner-1");
        Assert.assertSame(firstOwner, getField(service, "renderThread"));
        service.shutdown();
        Assert.assertNull(getField(service, "renderThread"));

        service.initialize();
        Thread wrong = runRenderTick(service, "font-owner-impostor");
        Assert.assertNull("错误线程不得在新 lifecycle 抢占 owner", getField(service, "renderThread"));
        Assert.assertFalse(wrong.isAlive());

        Thread secondOwner = runRenderTick(service, "Client thread-font-owner-2");
        Assert.assertSame(secondOwner, getField(service, "renderThread"));
        service.shutdown();
    }

    /** commit 前 RuntimeException/Error 都必须释放 ticket；只有 RuntimeException 在 owner 边界转入重试。 */
    @Test
    public void shouldReleaseReloadTicketWhenCommitFails() throws Exception {
        AtomicLong now = new AtomicLong(0L);
        FontReloadSignal signal = new FontReloadSignal(0L, 10L, 20L, now::get);
        FontService service = new FontService(signal);
        getAtomicBooleanField(service, "initialized").set(true);
        setField(service, "renderThread", Thread.currentThread());
        setField(service, "shaderProgram", new FailingFontShaderProgram(false));

        service.reload(new FontReloadRequest("runtime-failure"));
        service.tickMainThread(0);
        Assert.assertEquals(1, signal.getPendingCount());
        Assert.assertEquals(0L, signal.getAppliedSequence());
        Assert.assertEquals(1, signal.getConsecutiveFailures());
        Assert.assertFalse(signal.isInFlight());

        now.set(10L);
        setField(service, "shaderProgram", new FailingFontShaderProgram(true));
        try {
            service.tickMainThread(0);
            Assert.fail("AssertionError 必须在释放 ticket 后继续传播");
        } catch (AssertionError expected) {
            Assert.assertEquals("font reload error", expected.getMessage());
        }
        Assert.assertEquals(1, signal.getPendingCount());
        Assert.assertEquals(0L, signal.getAppliedSequence());
        Assert.assertEquals(2, signal.getConsecutiveFailures());
        Assert.assertFalse(signal.isInFlight());

        setField(service, "shaderProgram", null);
        service.shutdown();
    }

    private Thread runRenderTick(FontService service, String threadName) throws Exception {
        Thread thread = new Thread(() -> service.tickMainThread(0), threadName);
        thread.setDaemon(true);
        thread.start();
        thread.join(5000L);
        Assert.assertFalse("render tick 测试线程应及时结束", thread.isAlive());
        return thread;
    }

    private FontService newFontService() throws Exception {
        Constructor<FontService> constructor = FontService.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private AtomicBoolean getAtomicBooleanField(FontService service, String fieldName) throws Exception {
        return (AtomicBoolean) getField(service, fieldName);
    }

    private Object getField(FontService service, String fieldName) throws Exception {
        Field field = FontService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(service);
    }

    private void setField(FontService service, String fieldName, Object value) throws Exception {
        Field field = FontService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(service, value);
    }

    private static class TrackingFontShaderProgram extends FontShaderProgram {

        private final AtomicBoolean closed = new AtomicBoolean(false);

        @Override
        public void close() {
            closed.set(true);
            throw new AssertionError("close 不应在 shutdown hook 线程执行");
        }
    }

    private static final class FailingFontShaderProgram extends FontShaderProgram {

        private final boolean throwError;

        private FailingFontShaderProgram(boolean throwError) {
            this.throwError = throwError;
        }

        @Override
        public void close() {
            if (throwError) {
                throw new AssertionError("font reload error");
            }
            throw new IllegalStateException("font reload runtime failure");
        }
    }
}
