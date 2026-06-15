package club.heiqi.uilib.ui.document;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.DeterministicTextMeasureService;
import club.heiqi.uilib.ui.dom.DocumentElementBounds;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * `HtmlLikeDocumentWidget` 渲染期元素定位索引缓存回归测试。
 *
 * <p>自定义渲染器（文本控件的选区/光标层）每帧多次经 {@code getDocumentBounds()} 定位视口/内容/图层元素，
 * 旧实现每次都从根重建整棵视觉场景并走树查找，稳态退化为 O(N×K)。引入按场景签名缓存的元素定位索引后，
 * 同帧多次定位摊销为单趟遍历 + O(1) 查表，且稳态跨帧复用。本测试锁定缓存对外可见行为与直接定位完全等价，
 * 并在布局/滚动变化后正确失效。</p>
 */
public class HtmlLikeDocumentWidgetBoundsIndexCacheTest {

    /**
     * 验证同一布局下对多个元素反复取边界，结果稳定且符合布局几何。
     */
    @Test
    public void shouldReturnStableBoundsForRepeatedLookupsWithinSameLayout() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode viewport = document.div();
        ElementNode content = document.div();
        root.style().setWidth(UiStyleLength.px(200)).setHeight(UiStyleLength.px(120));
        viewport.style()
                .setWidth(UiStyleLength.px(160))
                .setHeight(UiStyleLength.px(80))
                .setPadding(UiStyleLength.px(8));
        content.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(40));
        viewport.append(content);
        root.append(viewport);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 200, 120,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 200, 120);
        widget.resolveLayoutBoxForTest();

        DocumentElementBounds viewportFirst = viewport.getDocumentBounds();
        DocumentElementBounds contentFirst = content.getDocumentBounds();
        DocumentElementBounds viewportSecond = viewport.getDocumentBounds();
        DocumentElementBounds contentSecond = content.getDocumentBounds();

        Assert.assertTrue(viewportFirst.isAvailable());
        Assert.assertTrue(contentFirst.isAvailable());
        assertSameBounds(viewportFirst, viewportSecond);
        assertSameBounds(contentFirst, contentSecond);
        // viewport content 区因 8px padding 内缩
        Assert.assertEquals(8, viewportFirst.getContentLeft());
        Assert.assertEquals(8, viewportFirst.getContentTop());
        Assert.assertEquals(160, viewportFirst.getContentWidth());
        // content 落在 viewport content 区左上
        Assert.assertEquals(8, contentFirst.getContentLeft());
        Assert.assertEquals(8, contentFirst.getContentTop());
        Assert.assertEquals(120, contentFirst.getContentWidth());
    }

    /**
     * 验证布局变化后元素边界缓存失效，返回新几何。
     */
    @Test
    public void shouldRefreshBoundsAfterLayoutChange() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode first = document.div();
        ElementNode second = document.div();
        first.style().setHeight(UiStyleLength.px(20));
        second.style().setHeight(UiStyleLength.px(20));
        root.append(first).append(second);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 120,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 120);
        widget.resolveLayoutBoxForTest();

        DocumentElementBounds secondBefore = second.getDocumentBounds();
        Assert.assertEquals(20, secondBefore.getTop());

        first.style().setHeight(UiStyleLength.px(50));
        widget.resolveLayoutBoxForTest();
        DocumentElementBounds secondAfter = second.getDocumentBounds();

        Assert.assertEquals(50, secondAfter.getTop());
    }

    /**
     * 验证滚动后元素边界缓存失效，返回滚动后的视觉位置。
     */
    @Test
    public void shouldRefreshBoundsAfterScrollChange() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode tall = document.div();
        ElementNode marker = document.div();
        root.style()
                .setWidth(UiStyleLength.px(100))
                .setHeight(UiStyleLength.px(60))
                .setOverflowY(UiOverflow.AUTO);
        tall.style().setHeight(UiStyleLength.px(40));
        marker.style().setHeight(UiStyleLength.px(160));
        root.append(tall).append(marker);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 100, 60,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 100, 60);
        widget.resolveLayoutBoxForTest();

        DocumentElementBounds markerBefore = marker.getDocumentBounds();
        Assert.assertTrue(widget.requestScrollTo(root, 0, 30));
        widget.resolveLayoutBoxForTest();
        DocumentElementBounds markerAfter = marker.getDocumentBounds();

        Assert.assertTrue(markerBefore.isAvailable());
        Assert.assertTrue(markerAfter.isAvailable());
        // 向下滚动 30px 后，marker 视觉 top 相对滚动前上移 30px
        Assert.assertEquals(markerBefore.getTop() - 30, markerAfter.getTop());
    }

    private static void assertSameBounds(DocumentElementBounds expected, DocumentElementBounds actual) {
        Assert.assertEquals(expected.isAvailable(), actual.isAvailable());
        Assert.assertEquals(expected.getLeft(), actual.getLeft());
        Assert.assertEquals(expected.getTop(), actual.getTop());
        Assert.assertEquals(expected.getWidth(), actual.getWidth());
        Assert.assertEquals(expected.getHeight(), actual.getHeight());
        Assert.assertEquals(expected.getContentLeft(), actual.getContentLeft());
        Assert.assertEquals(expected.getContentTop(), actual.getContentTop());
        Assert.assertEquals(expected.getContentWidth(), actual.getContentWidth());
        Assert.assertEquals(expected.getContentHeight(), actual.getContentHeight());
    }
}
