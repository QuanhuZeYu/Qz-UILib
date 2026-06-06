package club.heiqi.uilib.ui.document;

import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.assertElementUid;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.DeterministicTextMeasureService;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * `HtmlLikeDocumentWidget` 的基础命中测试。
 */
public class HtmlLikeDocumentWidgetHitTest {

    /**
     * 验证 HTML-like 组件可以命中屏幕坐标下的最深元素。
     */
    @Test
    public void shouldFindDeepestElementAtScreenPoint() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        ElementNode grandChild = document.div();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        child.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20));
        grandChild.style()
                .setWidth(UiStyleLength.px(16))
                .setHeight(UiStyleLength.px(10));
        child.append(grandChild);
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(5, 7, 80, 40);

        assertElementUid(grandChild, widget.findElementAt(10, 12));
        Assert.assertNull(widget.findElementAt(120, 12));
    }
}
