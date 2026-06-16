package club.heiqi.uilib.ui.component;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.reactive.Effect;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.style.values.UiTransform;

/**
 * {@link UiComponentRuntime} 的 opacity/transform signal 绑定契约测试（信条二：signal → 节点属性绑定）。
 *
 * <p>验证「signal → effect → 命令式 setOpacity/setTransform → COMPOSITE 失效」端到端闭环：
 * signal 是唯一数据源（I1），写入经中央调度器批处理（I2/I9），动态行为落在 effect 里（I3），
 * opacity/transform 走 COMPOSITE 级而非 PAINT（I4）。</p>
 *
 * <p>运行时不依赖任何渲染后端：直接 {@code new UiComponentRuntime(document)} 即可纯 JVM 测试，
 * 手动 {@link ReactiveScheduler#flush()} 驱动。composite-only 就地回放的渲染侧验证由引擎/widget 测试覆盖。</p>
 */
public class UiComponentRuntimeBindingTest {

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        ReactiveScheduler.get().reset();
    }

    // ── opacity 绑定 ────────────────────────────────────────────────────────────

    @Test
    public void bindOpacityWritesInitialValueOnFirstFlush() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        Signal<Float> opacity = Signal.create(0.5F);
        runtime.bindOpacity(root, opacity);

        Assert.assertNull("flush 前不应写入 opacity", root.style().getOpacity());

        ReactiveScheduler.get().flush();
        Assert.assertEquals(0.5F, root.style().getOpacity().floatValue(), 1.0e-6F);
    }

    @Test
    public void bindOpacityReappliesWhenSignalChanges() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        Signal<Float> opacity = Signal.create(0.8F);
        runtime.bindOpacity(root, opacity);
        ReactiveScheduler.get().flush();
        Assert.assertEquals(0.8F, root.style().getOpacity().floatValue(), 1.0e-6F);

        opacity.set(0.3F);
        ReactiveScheduler.get().flush();
        Assert.assertEquals(0.3F, root.style().getOpacity().floatValue(), 1.0e-6F);
    }

    @Test
    public void bindOpacityChangeBumpsCompositeVersionNotPaintVersion() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        Signal<Float> opacity = Signal.create(0.9F);
        runtime.bindOpacity(root, opacity);
        ReactiveScheduler.get().flush();

        int paintBefore = document.getPaintVersion();
        int compositeBefore = document.getCompositeVersion();

        // opacity 0.9 → 0.4 都 < 0.999，保持 paint context，走 composite-only 路径
        opacity.set(0.4F);
        ReactiveScheduler.get().flush();

        Assert.assertEquals("opacity 变化不应触发 PAINT 失效", paintBefore, document.getPaintVersion());
        Assert.assertTrue("opacity 变化应触发 COMPOSITE 失效",
                document.getCompositeVersion() > compositeBefore);
    }

    @Test
    public void bindOpacityBatchesMultipleSetsIntoOneApplication() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        Signal<Float> opacity = Signal.create(0.5F);
        runtime.bindOpacity(root, opacity);
        ReactiveScheduler.get().flush();

        int compositeBefore = document.getCompositeVersion();
        opacity.set(0.6F);
        opacity.set(0.7F);
        opacity.set(0.2F);
        ReactiveScheduler.get().flush();

        Assert.assertEquals(0.2F, root.style().getOpacity().floatValue(), 1.0e-6F);
        // 每次 effect 重跑产生 2 次 COMPOSITE bump（setOpacity 内部 listener + createEffect 体后显式 markCompositeDirty）；
        // 3 次 set 一次 flush 仅 +2（非 +6），反证 effect 只重跑一次（I9 批处理生效）。
        Assert.assertEquals("同帧多次 set 应合并为一次重跑",
                compositeBefore + 2, document.getCompositeVersion());
    }

    @Test
    public void disposedRuntimeStopsApplyingOpacitySignal() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        Signal<Float> opacity = Signal.create(0.5F);
        runtime.bindOpacity(root, opacity);
        ReactiveScheduler.get().flush();
        Assert.assertEquals(0.5F, root.style().getOpacity().floatValue(), 1.0e-6F);

        runtime.dispose();
        opacity.set(0.1F);
        ReactiveScheduler.get().flush();
        Assert.assertEquals(0.5F, root.style().getOpacity().floatValue(), 1.0e-6F);
    }

    // ── transform 绑定 ──────────────────────────────────────────────────────────

    @Test
    public void bindTransformWritesInitialValueOnFirstFlush() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        UiTransform translate = UiTransform.translate(4.0F, 8.0F);
        Signal<UiTransform> transform = Signal.create(translate);
        runtime.bindTransform(root, transform);

        Assert.assertNull("flush 前不应写入 transform", root.style().getTransform());

        ReactiveScheduler.get().flush();
        Assert.assertEquals(translate, root.style().getTransform());
    }

    @Test
    public void bindTransformReappliesWhenSignalChanges() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        Signal<UiTransform> transform = Signal.create(UiTransform.translate(2.0F, 2.0F));
        runtime.bindTransform(root, transform);
        ReactiveScheduler.get().flush();

        UiTransform next = UiTransform.translate(10.0F, 20.0F);
        transform.set(next);
        ReactiveScheduler.get().flush();
        Assert.assertEquals(next, root.style().getTransform());
    }

    @Test
    public void bindTransformChangeBumpsCompositeVersionNotPaintVersion() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        Signal<UiTransform> transform = Signal.create(UiTransform.translate(1.0F, 1.0F));
        runtime.bindTransform(root, transform);
        ReactiveScheduler.get().flush();

        int paintBefore = document.getPaintVersion();
        int compositeBefore = document.getCompositeVersion();

        // 非 identity → 非 identity，不翻转 stacking，走 composite-only 路径
        transform.set(UiTransform.translate(5.0F, 6.0F));
        ReactiveScheduler.get().flush();

        Assert.assertEquals("transform 变化不应触发 PAINT 失效", paintBefore, document.getPaintVersion());
        Assert.assertTrue("transform 变化应触发 COMPOSITE 失效",
                document.getCompositeVersion() > compositeBefore);
    }

    @Test
    public void bindReturnsLiveEffectThatCanBeDisposedIndividually() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        Signal<Float> opacity = Signal.create(0.5F);
        Effect effect = runtime.bindOpacity(root, opacity);
        ReactiveScheduler.get().flush();
        Assert.assertFalse(effect.isDisposed());

        effect.dispose();
        Assert.assertTrue(effect.isDisposed());

        opacity.set(0.2F);
        ReactiveScheduler.get().flush();
        Assert.assertEquals("单独 dispose 后不再写入", 0.5F,
                root.style().getOpacity().floatValue(), 1.0e-6F);
    }
}
