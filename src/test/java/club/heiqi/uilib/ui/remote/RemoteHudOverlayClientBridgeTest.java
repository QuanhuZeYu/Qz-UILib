package club.heiqi.uilib.ui.remote;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;

/**
 * 远程 HUD 客户端文档构建测试。
 */
public class RemoteHudOverlayClientBridgeTest {

    @Test
    public void shouldBuildDialogDocumentWithParsedHtmlAndCloseButton() {
        RemoteHudOverlays.OpenOffer offer = new RemoteHudOverlays.OpenOffer();
        offer.sessionId = "session";
        offer.overlayId = "overlay";
        offer.pageId = "page";
        offer.title = "HUD 标题";
        offer.mode = RemoteHudOverlayMode.DIALOG.name();
        offer.resourcePolicy = RemoteDocumentResourcePolicy.LOCAL_RESOURCES_ONLY.name();
        offer.defaultCloseButtonVisible = true;
        offer.closeButtonLabel = "关闭";
        String html = "<form id=\"hud-form\" action=\"save\">"
                + "<select name=\"choice\"><option>A</option><option selected>B</option></select>"
                + "<button type=\"submit\" name=\"submitter\" value=\"提交\"></button></form>";

        UiDocument document = UiDocument.create();
        RemoteHudOverlayClientBridge.OverlayDocumentParts parts =
                RemoteHudOverlayClientBridge.buildOverlayDocument(document, offer, html);

        Assert.assertEquals(RemoteHudOverlayMode.DIALOG, parts.mode);
        Assert.assertNotNull(document.getElementById("hud-form"));
        String text = collectText(document.getRootElement());
        Assert.assertTrue(text.contains("关闭"));
        Assert.assertTrue(text.contains("提交"));
        Assert.assertTrue(text.contains("B"));
    }

    @Test
    public void shouldBuildToastAsHitTestHiddenHudLayer() {
        RemoteHudOverlays.OpenOffer offer = new RemoteHudOverlays.OpenOffer();
        offer.sessionId = "session";
        offer.overlayId = "toast";
        offer.pageId = "page";
        offer.title = "提示";
        offer.mode = RemoteHudOverlayMode.TOAST.name();
        offer.resourcePolicy = RemoteDocumentResourcePolicy.LOCAL_RESOURCES_ONLY.name();

        UiDocument document = UiDocument.create();
        RemoteHudOverlayClientBridge.buildOverlayDocument(document, offer, "<p>toast-ok</p>");

        Assert.assertEquals("true", document.getRootElement().getAttribute("data-hit-test-hidden"));
        Assert.assertTrue(collectText(document.getRootElement()).contains("toast-ok"));
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
