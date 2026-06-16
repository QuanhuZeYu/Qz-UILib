package club.heiqi.uilib.ui.document;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.DeterministicTextMeasureService;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.reactive.Effect;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.style.values.UiTransform;

/**
 * `HtmlLikeDocumentWidget` 的 signal → style 细粒度绑定契约测试（方向甲，岗路 B：effect 桥接）。
 *
 * <p>验证「signal → effect → 命令式 setOpacity/setTransform → COMPOSITE 失效」端到端闭环：
 * signal 是唯一数据源（I1），写入经中央调度器批处理（I2/I9），动态行为落在 effect 里（I3），
 * opacity/transform 走 COMPOSITE 级而非 PAINT（I4）。</p>
 *
 * <p>本测试走纯数据层路径（手动 {@link ReactiveScheduler#flush()}），不调 {@code render}，
 * 因后者依赖 LWJGL native，沙箱缺失。composite-only 就地回放的渲染侧验证由第 16 次的
 * 引擎/widget 测试覆盖；真机帧率由人工 runClient 实测。</p>
 */
public class HtmlLikeDocumentWidgetStyleSignalBindingTest {

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        ReactiveScheduler.get().reset();
    }

    private HtmlLikeDocumentWidget newWidget(UiDocument document) {
        return new HtmlLikeDocumentWidget(document, 80, 40, new DeterministicTextMeasureService());
    }

    // ── opacity 绑定 ────────────────────────────────────────────────────────────

    @Test
    public void bindOpacityWritesInitialValueOnFirstFlush() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        HtmlLikeDocumentWidget widget = newWidget(document);

        Signal<Float> opacity = Signal.create(0.5F);
        widget.bindOpacity(root, opacity);

        // flush 前 effect 未跑，style 未被写入
        Assert.assertNull("flush 前不应写入 opacity", root.style().getOpacity());

        ReactiveScheduler.get().flush();
        Assert.assertEquals(0.5F, root.style().getOpacity().floatValue(), 1.0e-6F);
    }

    @Test
    public void bindOpacityReappliesWhenSignalChanges() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        HtmlLikeDocumentWidget widget = newWidget(document);

        Signal<Float> opacity = Signal.create(0.8F);
        widget.bindOpacity(root, opacity);
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
        HtmlLikeDocumentWidget widget = newWidget(document);

        Signal<Float> opacity = Signal.create(0.9F);
        widget.bindOpacity(root, opacity);
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
        HtmlLikeDocumentWidget widget = newWidget(document);

        Signal<Float> opacity = Signal.create(0.5F);
        widget.bindOpacity(root, opacity);
        ReactiveScheduler.get().flush();

        int compositeBefore = document.getCompositeVersion();
        // 同帧多次 set，flush 合并为一次重跑（I9）；最终值取最后一次
        opacity.set(0.6F);
        opacity.set(0.7F);
        opacity.set(0.2F);
        ReactiveScheduler.get().flush();

        Assert.assertEquals(0.2F, root.style().getOpacity().floatValue(), 1.0e-6F);
        // 关键：一次 flush 内 effect 只重跑一次（I9 批处理）。每次 effect 重跑产生 2 次 COMPOSITE bump：
        //   ① setOpacity() 经 style change listener → markCompositeMutated
        //   ② createEffect 按声明 impact 跑完 body 后显式 markCompositeDirty
        // 两者皆 COMPOSITE 级、对正确性无害。delta 恰为 2 证明只重跑一次（未批处理则 3 次 set = 6 bump）。
        Assert.assertEquals("同帧多次 set 应合并为一次 effect 重跑（2 次 COMPOSITE bump）",
                compositeBefore + 2, document.getCompositeVersion());
    }

    @Test
    public void closedWidgetStopsApplyingOpacitySignal() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        HtmlLikeDocumentWidget widget = newWidget(document);

        Signal<Float> opacity = Signal.create(0.5F);
        widget.bindOpacity(root, opacity);
        ReactiveScheduler.get().flush();
        Assert.assertEquals(0.5F, root.style().getOpacity().floatValue(), 1.0e-6F);

        widget.close();
        opacity.set(0.1F);
        ReactiveScheduler.get().flush();

        // dispose 后 effect 不再重跑，style 保持关闭前的值
        Assert.assertEquals(0.5F, root.style().getOpacity().floatValue(), 1.0e-6F);
    }

    // ── transform 绑定 ──────────────────────────────────────────────────────────

    @Test
    public void bindTransformWritesInitialValueOnFirstFlush() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        HtmlLikeDocumentWidget widget = newWidget(document);

        UiTransform translate = UiTransform.translate(4.0F, 8.0F);
        Signal<UiTransform> transform = Signal.create(translate);
        widget.bindTransform(root, transform);

        Assert.assertNull("flush 前不应写入 transform", root.style().getTransform());

        ReactiveScheduler.get().flush();
        Assert.assertEquals(translate, root.style().getTransform());
    }

    @Test
    public void bindTransformReappliesWhenSignalChanges() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        HtmlLikeDocumentWidget widget = newWidget(document);

        Signal<UiTransform> transform = Signal.create(UiTransform.translate(2.0F, 2.0F));
        widget.bindTransform(root, transform);
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
        HtmlLikeDocumentWidget widget = newWidget(document);

        Signal<UiTransform> transform = Signal.create(UiTransform.translate(1.0F, 1.0F));
        widget.bindTransform(root, transform);
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
        HtmlLikeDocumentWidget widget = newWidget(document);

        Signal<Float> opacity = Signal.create(0.5F);
        Effect effect = widget.bindOpacity(root, opacity);
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
