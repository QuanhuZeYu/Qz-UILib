package club.heiqi.uilib.ui.remote;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.cascade.ComputedStyle;
import club.heiqi.uilib.ui.style.cascade.UiStyleResolver;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 远程 HTML 安全子集解析测试。
 */
public class RemoteHtmlDocumentParserTest {

    @Test
    public void shouldParseNestedElementsEntitiesAndSkipDangerousContent() {
        String html = "<html><head><title>远程 &amp; 页面</title></head>"
                + "<body><div id=\"root\" class=\"card\"><p>Hello &lt;Qz&gt; "
                + "<span>World</span><!-- ignored --><script>bad()</script></p></div></body></html>";

        RemoteHtmlDocumentParser.Result result = RemoteHtmlDocumentParser.parse(html,
                RemoteHtmlDocumentParser.Options.defaults());
        UiDocument document = result.getDocument();
        ElementNode root = document.getElementById("root");

        Assert.assertEquals("远程 & 页面", result.getTitle());
        Assert.assertNotNull(root);
        Assert.assertEquals("card", root.getClassName());
        Assert.assertEquals("Hello <Qz> World", collectText(root).trim());
        Assert.assertNull(document.querySelector("script"));
        Assert.assertTrue(result.getWarnings().get(0).contains("危险标签"));
    }

    @Test
    public void shouldApplyStyleElementAndInlineStyleWithWhitelist() {
        String html = "<style>.card { color: #abc !important; width: calc(100% - 16px); unknown: nope; }"
                + "#panel { background-color: rgba(255, 0, 0, 0.5); }</style>"
                + "<div id=\"panel\" class=\"card\" style=\"margin: 1px 2px\"></div>";

        UiDocument document = RemoteHtmlDocumentParser.parse(html,
                RemoteHtmlDocumentParser.Options.defaults()).getDocument();
        ElementNode panel = document.getElementById("panel");
        ComputedStyle style = UiStyleResolver.compute(panel);

        Assert.assertEquals(0xFFAABBCC, style.getTextColor());
        Assert.assertEquals(UiStyleLength.calc(1.0F, -16.0F), style.getWidth());
        Assert.assertEquals(0x80FF0000, style.getBackgroundColor());
        Assert.assertEquals(UiStyleLength.px(1), panel.style().getMargin().getTop());
        Assert.assertEquals(UiStyleLength.px(2), panel.style().getMargin().getRight());
    }

    @Test
    public void shouldApplyRemoteResourcePolicyToLinksAndImages() {
        String html = "<a id=\"unsafe\" href=\"javascript:alert(1)\">x</a>"
                + "<a id=\"external\" href=\"https://example.test\">ok</a>"
                + "<img id=\"remote-img\" src=\"https://example.test/a.png\" alt=\"a\">"
                + "<img id=\"local-img\" src=\"qz_uilib:textures/gui/a.png\">";

        UiDocument localOnly = RemoteHtmlDocumentParser.parse(html,
                RemoteHtmlDocumentParser.Options.of("s", "p", RemoteDocumentResourcePolicy.LOCAL_RESOURCES_ONLY))
                .getDocument();
        Assert.assertNull(localOnly.getElementById("unsafe").getAttribute("href"));
        Assert.assertNull(localOnly.getElementById("external").getAttribute("href"));
        Assert.assertNull(localOnly.getElementById("remote-img").getAttribute("src"));
        Assert.assertEquals("qz_uilib:textures/gui/a.png", localOnly.getElementById("local-img").getAttribute("src"));

        UiDocument full = RemoteHtmlDocumentParser.parse(html,
                RemoteHtmlDocumentParser.Options.of("s", "p", RemoteDocumentResourcePolicy.FULL_EXTERNAL_LINKS))
                .getDocument();
        Assert.assertEquals("https://example.test", full.getElementById("external").getAttribute("href"));
        Assert.assertEquals("https://example.test/a.png", full.getElementById("remote-img").getAttribute("src"));
    }

    @Test
    public void shouldParseIntoProvidedContainerWithoutReplacingRootWrapper() {
        UiDocument document = UiDocument.create();
        ElementNode shell = document.div();
        ElementNode content = document.div();
        shell.append(content);
        document.getRootElement().append(shell);

        RemoteHtmlDocumentParser.parseInto(document, content,
                "<div id=\"parsed\"><select><option>A</option><option selected>B</option></select></div>",
                RemoteHtmlDocumentParser.Options.defaults().withDocumentDefaults(false));

        ElementNode parsed = document.getElementById("parsed");
        Assert.assertNotNull(parsed);
        Assert.assertSame(content, parsed.getParent());
        Assert.assertEquals(1, document.getRootElement().getChildCount());
        Assert.assertTrue(collectText(content).contains("B"));
    }

    private static String collectText(DocumentNode node) {
        if (node instanceof TextNode) {
            return ((TextNode) node).getText();
        }
        StringBuilder builder = new StringBuilder();
        List<DocumentNode> children = node.getChildren();
        for (DocumentNode child : children) {
            builder.append(collectText(child));
        }
        return builder.toString();
    }
}
