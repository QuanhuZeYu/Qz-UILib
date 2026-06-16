package club.heiqi.uilib.ui.document;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.DeterministicTextMeasureService;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.style.UiStyleChangeImpact;
import club.heiqi.uilib.ui.style.props.UiVisibility;

/**
 * `HtmlLikeDocumentWidget` 的 bind 推广契约测试（深化甲：通用 bind + computed + 便捷封装）。
 *
 * <p>验证：通用 {@code bind(impact, source, applier)} 覆盖任意级别/属性；{@link Computed} 派生值可直接
 * 喂入 bind；便捷封装（backgroundColor/textColor/visibility）走 PAINT 级；LAYOUT 级绑定走重排路径。</p>
 *
 * <p>纯数据层路径（手动 {@link ReactiveScheduler#flush()}），不调 {@code render}（依赖 LWJGL native）。</p>
 */
public class HtmlLikeDocumentWidgetStyleBindExpandTest {

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

    // ── 通用 bind ───────────────────────────────────────────────────────────────

    @Test
    public void genericBindWritesPaintPropertyAndBumpsPaintVersion() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        HtmlLikeDocumentWidget widget = newWidget(document);

        Signal<Integer> bg = Signal.create(0xFF112233);
        widget.bind(UiStyleChangeImpact.PAINT, bg, v -> root.style().setBackgroundColor(v.intValue()));
        ReactiveScheduler.get().flush();
        Assert.assertEquals(Integer.valueOf(0xFF112233), root.style().getBackgroundColor());

        int paintBefore = document.getPaintVersion();
        int compositeBefore = document.getCompositeVersion();
        bg.set(0xFF445566);
        ReactiveScheduler.get().flush();

        Assert.assertEquals(Integer.valueOf(0xFF445566), root.style().getBackgroundColor());
        Assert.assertTrue("PAINT 级 bind 应 bump paintVersion", document.getPaintVersion() > paintBefore);
        Assert.assertEquals("PAINT 级 bind 不应 bump compositeVersion",
                compositeBefore, document.getCompositeVersion());
    }

    @Test
    public void genericBindSupportsLayoutLevelProperty() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        HtmlLikeDocumentWidget widget = newWidget(document);

        Signal<Integer> zIndex = Signal.create(1);
        widget.bind(UiStyleChangeImpact.LAYOUT, zIndex, v -> root.style().setZIndex(v.intValue()));
        ReactiveScheduler.get().flush();
        Assert.assertEquals(Integer.valueOf(1), root.style().getZIndex());

        int layoutBefore = document.getLayoutVersion();
        zIndex.set(5);
        ReactiveScheduler.get().flush();

        Assert.assertEquals(Integer.valueOf(5), root.style().getZIndex());
        Assert.assertTrue("LAYOUT 级 bind 应 bump layoutVersion", document.getLayoutVersion() > layoutBefore);
    }

    // ── computed 喂入 bind ──────────────────────────────────────────────────────

    @Test
    public void computedDerivedValueCanFeedBind() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        HtmlLikeDocumentWidget widget = newWidget(document);

        Signal<Integer> red = Signal.create(0x10);
        // computed: 由 red 派生完整 ARGB
        Computed<Integer> color = Computed.create(() -> 0xFF000000 | (red.get().intValue() << 16));
        widget.bindBackgroundColor(root, color);
        ReactiveScheduler.get().flush();
        Assert.assertEquals(Integer.valueOf(0xFF100000), root.style().getBackgroundColor());

        // 改上游 signal → computed 重算 → bind effect 重跑
        red.set(0x80);
        ReactiveScheduler.get().flush();
        Assert.assertEquals(Integer.valueOf(0xFF800000), root.style().getBackgroundColor());
    }

    // ── 便捷封装 ────────────────────────────────────────────────────────────────

    @Test
    public void bindBackgroundColorReappliesOnChange() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        HtmlLikeDocumentWidget widget = newWidget(document);

        Signal<Integer> bg = Signal.create(0xFFAABBCC);
        widget.bindBackgroundColor(root, bg);
        ReactiveScheduler.get().flush();
        Assert.assertEquals(Integer.valueOf(0xFFAABBCC), root.style().getBackgroundColor());

        bg.set(0xFF010203);
        ReactiveScheduler.get().flush();
        Assert.assertEquals(Integer.valueOf(0xFF010203), root.style().getBackgroundColor());
    }

    @Test
    public void bindTextColorReappliesOnChange() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        HtmlLikeDocumentWidget widget = newWidget(document);

        Signal<Integer> fg = Signal.create(0xFF111111);
        widget.bindTextColor(root, fg);
        ReactiveScheduler.get().flush();
        Assert.assertEquals(Integer.valueOf(0xFF111111), root.style().getTextColor());

        fg.set(0xFFEEEEEE);
        ReactiveScheduler.get().flush();
        Assert.assertEquals(Integer.valueOf(0xFFEEEEEE), root.style().getTextColor());
    }

    @Test
    public void bindVisibilityReappliesOnChange() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        HtmlLikeDocumentWidget widget = newWidget(document);

        Signal<UiVisibility> visibility = Signal.create(UiVisibility.VISIBLE);
        widget.bindVisibility(root, visibility);
        ReactiveScheduler.get().flush();
        Assert.assertEquals(UiVisibility.VISIBLE, root.style().getVisibility());

        visibility.set(UiVisibility.HIDDEN);
        ReactiveScheduler.get().flush();
        Assert.assertEquals(UiVisibility.HIDDEN, root.style().getVisibility());
    }

    @Test
    public void closedWidgetStopsApplyingAllBindings() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        HtmlLikeDocumentWidget widget = newWidget(document);

        Signal<Integer> bg = Signal.create(0xFFAABBCC);
        widget.bindBackgroundColor(root, bg);
        ReactiveScheduler.get().flush();
        Assert.assertEquals(Integer.valueOf(0xFFAABBCC), root.style().getBackgroundColor());

        widget.close();
        bg.set(0xFF000000);
        ReactiveScheduler.get().flush();
        Assert.assertEquals("close 后不再写入", Integer.valueOf(0xFFAABBCC), root.style().getBackgroundColor());
    }
}
