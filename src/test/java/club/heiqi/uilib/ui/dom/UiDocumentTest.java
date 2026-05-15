package club.heiqi.uilib.ui.dom;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.style.UiStyleLength;
import club.heiqi.uilib.ui.text.TextContentMode;

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
        Assert.assertTrue(root.__getElementUid() > 0L);
        Assert.assertTrue(panel.__getElementUid() > 0L);
        Assert.assertTrue(root.__getElementUid() != panel.__getElementUid());
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
        long firstElementUid = first.__getElementUid();

        root.append(first).append(second);
        second.append(first);

        Assert.assertEquals(1, root.getChildCount());
        Assert.assertSame(second, root.getFirstChild());
        Assert.assertEquals(1, second.getChildCount());
        Assert.assertSame(first, second.getFirstChild());
        Assert.assertSame(second, first.getParent());
        Assert.assertEquals(firstElementUid, first.__getElementUid());
    }

    /**
     * 验证内部元素唯一身份不会复用 HTML id 属性语义。
     */
    @Test
    public void shouldAssignInternalElementUidsSeparatelyFromHtmlIdAttribute() {
        UiDocument firstDocument = UiDocument.create();
        UiDocument secondDocument = UiDocument.create();
        ElementNode first = firstDocument.div().setAttribute("id", "same-html-id");
        ElementNode second = firstDocument.div().setAttribute("id", "same-html-id");
        ElementNode otherDocumentElement = secondDocument.div().setAttribute("id", "same-html-id");

        Assert.assertEquals("same-html-id", first.getAttribute("id"));
        Assert.assertEquals("same-html-id", second.getAttribute("id"));
        Assert.assertTrue(first.__getElementUid() != second.__getElementUid());
        Assert.assertTrue(first.__getElementUid() != otherDocumentElement.__getElementUid());
        Assert.assertFalse(first.hasAttribute("__elementUid"));
    }

    /**
     * 验证文档会区分 layout 失效与 paint-only 失效版本。
     */
    @Test
    public void shouldTrackLayoutAndPaintMutationVersionsSeparately() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        int initialMutationVersion = document.getMutationVersion();
        int initialLayoutVersion = document.getLayoutVersion();
        int initialPaintVersion = document.getPaintVersion();

        root.style().setBackgroundColor(0xFF223344);

        Assert.assertTrue(document.getMutationVersion() > initialMutationVersion);
        Assert.assertEquals(initialLayoutVersion, document.getLayoutVersion());
        Assert.assertTrue(document.getPaintVersion() > initialPaintVersion);

        int paintOnlyLayoutVersion = document.getLayoutVersion();
        int paintOnlyPaintVersion = document.getPaintVersion();
        root.style().setWidth(UiStyleLength.px(40));

        Assert.assertTrue(document.getLayoutVersion() > paintOnlyLayoutVersion);
        Assert.assertTrue(document.getPaintVersion() > paintOnlyPaintVersion);
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

    /**
     * 验证作者入口提供语义化的 raw / Minecraft 文本快捷方法。
     */
    @Test
    public void shouldProvideSemanticTextFactoriesAndAppenders() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();

        TextNode rawText = document.rawText("价格：§a100金币");
        TextNode minecraftText = document.minecraftText("§a100金币");
        TextNode appendedRawText = root.appendRawText("原样 §k 文本");
        TextNode appendedMinecraftText = root.appendMinecraftText("§a绿色文本");

        Assert.assertEquals(TextContentMode.UILIB_RAW, rawText.getTextContentMode());
        Assert.assertEquals(TextContentMode.MINECRAFT_FORMATTED, minecraftText.getTextContentMode());
        Assert.assertEquals(TextContentMode.UILIB_RAW, appendedRawText.getTextContentMode());
        Assert.assertEquals(TextContentMode.MINECRAFT_FORMATTED, appendedMinecraftText.getTextContentMode());
    }

    /**
     * 验证文档支持语义化切换后续文本节点的默认解析模式。
     */
    @Test
    public void shouldProvideSemanticDefaultTextModeSwitchers() {
        UiDocument document = UiDocument.create();

        document.useMinecraftTextByDefault();
        TextNode minecraftText = document.text("§a100金币");
        document.useRawTextByDefault();
        TextNode rawText = document.text("§a100金币");

        Assert.assertEquals(TextContentMode.MINECRAFT_FORMATTED, minecraftText.getTextContentMode());
        Assert.assertEquals(TextContentMode.UILIB_RAW, rawText.getTextContentMode());
    }
}
