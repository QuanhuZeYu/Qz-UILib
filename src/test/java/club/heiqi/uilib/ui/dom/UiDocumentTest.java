package club.heiqi.uilib.ui.dom;

import org.junit.Assert;
import org.junit.Test;

/**
 * `UiDocument` 文档树基础契约测试。
 */
public class UiDocumentTest {

    /**
     * 验证作者入口可以创建稳定元素树与文本节点。
     */
    @Test
    public void shouldCreateElementTreeThroughAuthoringApi() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode panel = document.div()
                .setAttribute("ID", "main-panel")
                .setAttribute("role", "page");
        TextNode title = document.text("标题");

        int initialVersion = document.getMutationVersion();
        root.append(panel);
        panel.append(title);

        Assert.assertEquals("document", root.getTagName());
        Assert.assertEquals(DocumentNodeType.ELEMENT, panel.getNodeType());
        Assert.assertEquals("div", panel.getTagName());
        Assert.assertEquals("main-panel", panel.getAttribute("id"));
        Assert.assertEquals("page", panel.getAttribute("role"));
        Assert.assertSame(root, panel.getParent());
        Assert.assertSame(panel, title.getParent());
        Assert.assertEquals(DocumentNodeType.TEXT, title.getNodeType());
        Assert.assertEquals("标题", title.getText());
        Assert.assertTrue(document.getMutationVersion() > initialVersion);
    }

    /**
     * 验证 append 语义会在同一文档内移动已有子节点。
     */
    @Test
    public void shouldMoveExistingChildWhenAppendingAgain() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode first = document.div().setAttribute("id", "first");
        ElementNode second = document.div().setAttribute("id", "second");

        root.append(first).append(second);
        second.append(first);

        Assert.assertEquals(1, root.getChildCount());
        Assert.assertSame(second, root.getFirstChild());
        Assert.assertEquals(1, second.getChildCount());
        Assert.assertSame(first, second.getFirstChild());
        Assert.assertSame(second, first.getParent());
    }

    /**
     * 验证文档树会拒绝跨文档挂接与循环挂接。
     */
    @Test
    public void shouldRejectCrossDocumentNodesAndCycles() {
        UiDocument firstDocument = UiDocument.create();
        UiDocument secondDocument = UiDocument.create();
        ElementNode root = firstDocument.getRootElement();
        ElementNode child = firstDocument.div();
        ElementNode grandChild = firstDocument.div();

        root.append(child);
        child.append(grandChild);

        try {
            root.append(secondDocument.div());
            Assert.fail("跨文档节点不应允许挂接");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("another UiDocument"));
        }

        try {
            grandChild.append(child);
            Assert.fail("祖先节点不应允许挂到后代下方");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("ancestor"));
        }

        try {
            firstDocument.div().append(root);
            Assert.fail("文档根元素不应允许被挂到孤立节点下方");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("root element"));
        }
    }

    /**
     * 验证文本节点保持叶子节点语义，并能记录文本变更。
     */
    @Test
    public void shouldKeepTextNodeLeafAndRecordTextMutation() {
        UiDocument document = UiDocument.create();
        TextNode textNode = document.text(null);
        int initialVersion = document.getMutationVersion();

        Assert.assertEquals("", textNode.getText());
        textNode.setText("正文");

        Assert.assertEquals("正文", textNode.getText());
        Assert.assertTrue(document.getMutationVersion() > initialVersion);

        try {
            textNode.appendChild(document.span());
            Assert.fail("文本节点不应允许子节点");
        } catch (UnsupportedOperationException expected) {
            Assert.assertTrue(expected.getMessage().contains("cannot contain children"));
        }
    }
}
