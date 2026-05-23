package club.heiqi.uilib.ui.remote;

import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine;
import club.heiqi.uilib.ui.paint.DocumentPaintCommand;
import club.heiqi.uilib.ui.paint.DocumentPaintCommandType;
import club.heiqi.uilib.ui.paint.DocumentPaintEngine;
import club.heiqi.uilib.ui.style.props.UiCursor;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.text.TextMeasureService;

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
        String html = "<section><h1 data-qz-hud-drag-handle=\"true\">作者标题</h1>"
                + "<form id=\"hud-form\" action=\"save\">"
                + "<select name=\"choice\"><option>A</option><option selected>B</option></select>"
                + "<button type=\"submit\" name=\"submitter\" value=\"提交\"></button></form></section>";

        UiDocument document = UiDocument.create();
        RemoteHudOverlayClientBridge.OverlayDocumentParts parts =
                RemoteHudOverlayClientBridge.buildOverlayDocument(document, offer, html);

        Assert.assertEquals(RemoteHudOverlayMode.DIALOG, parts.mode);
        Assert.assertNotNull(document.getElementById("hud-form"));
        String text = collectText(document.getRootElement());
        Assert.assertTrue(text.contains("关闭"));
        Assert.assertTrue(text.contains("提交"));
        Assert.assertTrue(text.contains("B"));
        Assert.assertTrue(text.contains("作者标题"));
        Assert.assertFalse("DIALOG 不应再生成宿主标题栏文本", text.contains("HUD 标题"));
        Assert.assertNull("DIALOG 根层不应额外绘制全屏暗色父容器",
                document.getRootElement().style().getBackgroundColor());
        Assert.assertNull("DIALOG shell 应只承担定位和拖拽，不额外绘制背景",
                parts.movingElement.style().getBackgroundColor());
        Assert.assertNull("DIALOG shell 应只承担定位和拖拽，不额外绘制边框",
                parts.movingElement.style().getBorderWidth());
        Assert.assertSame("DIALOG shell 的第一项应是解析出的远程 HTML 内容，不应插入宿主标题栏",
                parts.contentElement, parts.movingElement.getChildren().get(0));
        ElementNode dragHandle = findElementByAttribute(document.getRootElement(), "data-qz-hud-drag-handle",
                "true");
        Assert.assertNotNull(dragHandle);
        Assert.assertNotNull(dragHandle.getDragHandler());
        Assert.assertEquals(UiCursor.MOVE, dragHandle.style().getCursor());
        ElementNode closeButton = findElementByAttribute(document.getRootElement(), "data-qz-hud-close-button",
                "true");
        Assert.assertNotNull(closeButton);
        Assert.assertSame(parts.movingElement, closeButton.getParent());
        Assert.assertEquals(UiPosition.ABSOLUTE, closeButton.style().getPosition());
        Assert.assertEquals("默认关闭按钮应保持紧凑，不应按 flex/block 语义横向铺满内容",
                UiDisplay.FLEX, closeButton.style().getDisplay());
        Assert.assertEquals(UiStyleLength.px(44), closeButton.style().getWidth());
        Assert.assertEquals(UiStyleLength.px(24), closeButton.style().getHeight());
    }

    @Test
    public void shouldFallbackDialogDragToParsedContentWhenNoAuthorHandleExists() {
        RemoteHudOverlays.OpenOffer offer = new RemoteHudOverlays.OpenOffer();
        offer.sessionId = "session";
        offer.overlayId = "overlay";
        offer.pageId = "page";
        offer.title = "HUD 标题";
        offer.mode = RemoteHudOverlayMode.DIALOG.name();
        offer.resourcePolicy = RemoteDocumentResourcePolicy.LOCAL_RESOURCES_ONLY.name();
        offer.defaultCloseButtonVisible = false;

        UiDocument document = UiDocument.create();
        RemoteHudOverlayClientBridge.OverlayDocumentParts parts =
                RemoteHudOverlayClientBridge.buildOverlayDocument(document, offer, "<div>无显式拖拽把手</div>");

        Assert.assertNotNull("没有作者拖拽把手时，DIALOG 内容本身作为兜底拖拽区域",
                parts.contentElement.getDragHandler());
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

    @Test
    public void shouldBuildDanmakuAsShrinkWrappedDrawableText() {
        RemoteHudOverlays.OpenOffer offer = new RemoteHudOverlays.OpenOffer();
        offer.sessionId = "session";
        offer.overlayId = "danmaku";
        offer.pageId = "page";
        offer.title = "弹幕";
        offer.mode = RemoteHudOverlayMode.DANMAKU.name();
        offer.resourcePolicy = RemoteDocumentResourcePolicy.LOCAL_RESOURCES_ONLY.name();

        UiDocument document = UiDocument.create();
        RemoteHudOverlayClientBridge.OverlayDocumentParts parts =
                RemoteHudOverlayClientBridge.buildOverlayDocument(document, offer,
                        "<div style=\"padding:4px 8px;\">HUD 弹幕文字</div>");
        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(document.getRootElement(), 320, 180,
                new DeterministicTextMeasureService());
        List<DocumentPaintCommand> commands = DocumentPaintEngine.buildPaintCommands(rootBox);
        DocumentLayoutBox movingBox = findLayoutBox(rootBox, parts.movingElement);

        Assert.assertNotNull(movingBox);
        Assert.assertTrue("弹幕 shell 应按内容收缩，而不是撑成整屏", movingBox.getWidth() < 220);
        Assert.assertTrue(containsTextCommand(commands, "HUD 弹幕文字"));
        Assert.assertFalse("弹幕移动应使用布局坐标，避免 deferred text batch 脱离 transform",
                containsCommand(commands, DocumentPaintCommandType.TRANSFORM_START));
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

    private static ElementNode findElementByAttribute(DocumentNode node, String name, String value) {
        if (node instanceof ElementNode) {
            ElementNode element = (ElementNode) node;
            if (value.equals(element.getAttribute(name))) {
                return element;
            }
        }
        for (DocumentNode child : node.getChildren()) {
            ElementNode found = findElementByAttribute(child, name, value);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static DocumentLayoutBox findLayoutBox(DocumentLayoutBox box, ElementNode element) {
        if (box.getElement().__getElementUid() == element.__getElementUid()) {
            return box;
        }
        for (DocumentLayoutBox child : box.getChildren()) {
            DocumentLayoutBox found = findLayoutBox(child, element);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static boolean containsTextCommand(List<DocumentPaintCommand> commands, String expectedText) {
        for (DocumentPaintCommand command : commands) {
            if (command.getType() == DocumentPaintCommandType.TEXT
                    && command.getText() != null
                    && command.getText().contains(expectedText)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsCommand(List<DocumentPaintCommand> commands, DocumentPaintCommandType type) {
        for (DocumentPaintCommand command : commands) {
            if (command.getType() == type) {
                return true;
            }
        }
        return false;
    }

    private static final class DeterministicTextMeasureService implements TextMeasureService {

        @Override
        public int getEpoch() {
            return 0;
        }

        @Override
        public int getStringWidth(String text) {
            return text == null ? 0 : text.length() * 8;
        }

        @Override
        public int getLineHeight() {
            return 18;
        }

        @Override
        public String trimStringToWidth(String text, int targetWidth) {
            if (text == null) {
                return "";
            }
            int maxChars = Math.max(0, targetWidth / 8);
            return text.length() <= maxChars ? text : text.substring(0, maxChars);
        }

        @Override
        public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            return Collections.singletonList(text == null ? "" : text);
        }
    }
}
