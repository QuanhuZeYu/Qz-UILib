package club.heiqi.uilib.font;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.shader.FontShaderProgram;

/**
 * `FontService` 轻量布局期入口冒烟。
 *
 * <p>该测试只覆盖 {@link FontService#ensureLayoutRuntimeReady()} 这条不触碰字符页/调度器/批渲染器/着色器
 * 的入口；reload 流程、fallback 选择与并发 reload 行为分别由
 * {@code FontReloadDebouncerTest}、{@code FontMatcherRuntimeVersionTest}、
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
}
