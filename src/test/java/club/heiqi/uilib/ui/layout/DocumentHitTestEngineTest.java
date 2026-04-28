package club.heiqi.uilib.ui.layout;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.UiPosition;
import club.heiqi.uilib.ui.style.UiStyleLength;

/**
 * `DocumentHitTestEngine` 的 HTML-like 命中测试契约。
 */
public class DocumentHitTestEngineTest {

    /**
     * 验证 z-index 更高的 relative 子元素在视觉重叠区域优先命中。
     */
    @Test
    public void shouldHitRaisedRelativeChildFirst() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode raised = document.div();
        ElementNode normal = document.div();

        root.style().setWidth(UiStyleLength.px(100));
        raised.style()
                .setWidth(UiStyleLength.px(50))
                .setHeight(UiStyleLength.px(20))
                .setPosition(UiPosition.RELATIVE)
                .setTop(UiStyleLength.px(16))
                .setZIndex(2);
        normal.style()
                .setWidth(UiStyleLength.px(50))
                .setHeight(UiStyleLength.px(20));
        root.append(raised).append(normal);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 120, 0);

        Assert.assertSame(raised, DocumentHitTestEngine.hitTest(rootBox, null, 10, 22));
    }
}
