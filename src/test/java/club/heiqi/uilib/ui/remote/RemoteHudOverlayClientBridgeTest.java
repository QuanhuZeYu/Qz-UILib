package club.heiqi.uilib.ui.remote;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.DocumentElementBounds;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.hud.UiHudDocumentRegistration;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine;
import club.heiqi.uilib.ui.paint.DocumentPaintCommand;
import club.heiqi.uilib.ui.paint.DocumentPaintCommandType;
import club.heiqi.uilib.ui.paint.DocumentPaintEngine;
import club.heiqi.uilib.ui.base.props.UiCursor;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.props.UiVisibility;
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
        Assert.assertEquals("DIALOG 根层不应依赖 flex 居中驱动 shell 位置",
                UiDisplay.BLOCK, document.getRootElement().style().getDisplay());
        Assert.assertEquals("DIALOG shell 初始即应是 fixed 浮窗，避免首拖切换定位模型",
                UiPosition.FIXED, parts.movingElement.style().getPosition());
        Assert.assertEquals("DIALOG shell 放置前保持隐藏，避免首帧在左上角闪现",
                UiVisibility.HIDDEN, parts.movingElement.style().getVisibility());
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
    public void shouldCenterDialogBeforeFirstDragAndMoveByMouseDelta() {
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
                RemoteHudOverlayClientBridge.buildOverlayDocument(document, offer,
                        "<section style=\"height:120px;background:#111827;\">"
                                + "<div data-qz-hud-drag-handle=\"true\" style=\"height:40px;cursor:move;\">拖动</div>"
                                + "<p>正文</p></section>");
        DeterministicTextMeasureService textMeasureService = new DeterministicTextMeasureService();

        parts.updateDialogPlacementForTest(1024, 768, textMeasureService);
        float initialLeft = parts.movingElement.style().getLeft().getValue();
        float initialTop = parts.movingElement.style().getTop().getValue();

        Assert.assertEquals(UiVisibility.VISIBLE, parts.movingElement.style().getVisibility());
        Assert.assertEquals(UiPosition.FIXED, parts.movingElement.style().getPosition());
        Assert.assertTrue("初始放置不应停留在左上角 X", initialLeft > 100.0F);
        Assert.assertTrue("初始放置不应停留在左上角 Y", initialTop > 100.0F);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 320, 180, textMeasureService);
        widget.applyLayoutBounds(0, 0, 1024, 768);
        int mouseX = Math.round(initialLeft) + 24;
        int mouseY = Math.round(initialTop) + 16;
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, mouseX, mouseY, 0, 0, 0, 0, 1L));
        widget.onMouseMove(new UiMouseEvent(UiMouseEvent.Action.MOVE, mouseX + 30, mouseY + 20, -1, 0, 30, 20, 2L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, mouseX + 30, mouseY + 20, 0, 0, 0, 0, 3L));

        Assert.assertEquals(initialLeft + 30.0F, parts.movingElement.style().getLeft().getValue(), 0.001F);
        Assert.assertEquals(initialTop + 20.0F, parts.movingElement.style().getTop().getValue(), 0.001F);

        parts.updateDialogPlacementForTest(1024, 768, textMeasureService);
        Assert.assertEquals("用户拖拽后，相同视口刷新不应重新居中",
                initialLeft + 30.0F, parts.movingElement.style().getLeft().getValue(), 0.001F);
        Assert.assertEquals("用户拖拽后，相同视口刷新不应重新居中",
                initialTop + 20.0F, parts.movingElement.style().getTop().getValue(), 0.001F);
    }

    @Test
    public void shouldRecenterDialogWhenViewportBecomesRealBeforeUserDrag() {
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
                RemoteHudOverlayClientBridge.buildOverlayDocument(document, offer,
                        "<section style=\"height:80px;\"><div data-qz-hud-drag-handle=\"true\">拖动</div></section>");
        DeterministicTextMeasureService textMeasureService = new DeterministicTextMeasureService();

        parts.updateDialogPlacementForTest(320, 180, textMeasureService);
        float temporaryLeft = parts.movingElement.style().getLeft().getValue();
        parts.updateDialogPlacementForTest(1024, 768, textMeasureService);

        Assert.assertTrue("真实视口到达且尚未拖拽时，应从临时小视口重新居中",
                parts.movingElement.style().getLeft().getValue() > temporaryLeft + 100.0F);
        Assert.assertEquals(UiVisibility.VISIBLE, parts.movingElement.style().getVisibility());
    }

    @Test
    public void shouldHitDialogSelectPopupAfterShellMovesAndEscapeOverflow() {
        RemoteHudOverlays.OpenOffer offer = new RemoteHudOverlays.OpenOffer();
        offer.sessionId = "session";
        offer.overlayId = "overlay";
        offer.pageId = "page";
        offer.title = "HUD 标题";
        offer.mode = RemoteHudOverlayMode.DIALOG.name();
        offer.resourcePolicy = RemoteDocumentResourcePolicy.LOCAL_RESOURCES_ONLY.name();
        offer.defaultCloseButtonVisible = false;
        String html = "<section style=\"width:100%;background:#111827;\">"
                + "<div data-qz-hud-drag-handle=\"true\" style=\"height:28px;\">拖动</div>"
                + "<form id=\"hud-form\" action=\"save\">"
                + "<select id=\"moving-select\" name=\"moving\" style=\"width:160px;\">"
                + "<option value=\"wrong\">错误</option><option value=\"moving-ok\">移动后通过</option></select>"
                + "<div style=\"height:40px;overflow:hidden;margin-top:8px;\">"
                + "<select id=\"clipped-select\" name=\"clipped\" style=\"width:160px;\">"
                + "<option value=\"wrong\">错误</option><option value=\"clipped-ok\">裁剪通过</option></select>"
                + "</div></form></section>";
        UiDocument document = UiDocument.create();
        RemoteHudOverlayClientBridge.OverlayDocumentParts parts =
                RemoteHudOverlayClientBridge.buildOverlayDocument(document, offer, html);
        DeterministicTextMeasureService textMeasureService = new DeterministicTextMeasureService();
        parts.updateDialogPlacementForTest(640, 480, textMeasureService);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 640, 480, textMeasureService);
        widget.applyLayoutBounds(0, 0, 640, 480);
        ElementNode movingSelect = document.getElementById("moving-select");

        click(widget, pointInsideX(movingSelect), pointInsideY(movingSelect), 1L);
        Assert.assertEquals("true", movingSelect.getAttribute("aria-expanded"));
        parts.movingElement.style()
                .setLeft(UiStyleLength.px(parts.movingElement.style().getLeft().getValue() + 48.0F))
                .setTop(UiStyleLength.px(parts.movingElement.style().getTop().getValue() + 24.0F));
        click(widget, pointInsideX(movingSelect), optionY(movingSelect, 1), 3L);

        Assert.assertEquals("移动后通过", movingSelect.getAttribute("value"));

        ElementNode clippedSelect = document.getElementById("clipped-select");
        click(widget, pointInsideX(clippedSelect), pointInsideY(clippedSelect), 5L);
        ElementNode popup = findListboxElement(clippedSelect);
        Assert.assertTrue("裁剪容器中的 popup 应保持 select 逻辑 DOM 归属",
                popup != null && popup.getParent() == clippedSelect);
        Assert.assertTrue("裁剪容器中的 popup 应提升到 top-layer",
                document.__isTopLayerElement(popup));
        click(widget, pointInsideX(clippedSelect), optionY(clippedSelect, 1), 7L);

        Assert.assertEquals("裁剪通过", clippedSelect.getAttribute("value"));
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

    @Test
    public void shouldIgnoreSessionScopedDismissFromOlderHudOverlay() {
        RemoteHudOverlayClientBridge bridge = RemoteHudOverlayClientBridge.getInstance();
        bridge.clearAll();
        final AtomicBoolean unregistered = new AtomicBoolean(false);
        RemoteHudOverlays.OpenOffer currentOffer = new RemoteHudOverlays.OpenOffer();
        currentOffer.sessionId = "S2";
        currentOffer.overlayId = "shared-overlay";
        currentOffer.pageId = "page";
        currentOffer.mode = RemoteHudOverlayMode.DIALOG.name();
        bridge.addActiveOverlayForTest(currentOffer, new UiHudDocumentRegistration() {
            @Override
            public void unregister() {
                unregistered.set(true);
            }
        });

        RemoteHudOverlays.DismissPayload oldDismiss = new RemoteHudOverlays.DismissPayload();
        oldDismiss.sessionId = "S1";
        oldDismiss.surfaceType = RemoteUiProtocol.SurfaceType.HUD.name();
        oldDismiss.surfaceId = "shared-overlay";
        oldDismiss.contentRevision = 1L;
        oldDismiss.closeScope = RemoteUiProtocol.CloseScope.SESSION.name();
        oldDismiss.overlayId = "shared-overlay";
        oldDismiss.reason = "server-dismiss";
        RemoteHudOverlayClientBridge.receiveDismiss(RemoteJson.toJson(oldDismiss));

        Assert.assertTrue("旧 session 的 dismiss 不应关闭同 overlayId 的新 HUD",
                bridge.hasActiveOverlayForTest("shared-overlay", "S2"));
        Assert.assertFalse(unregistered.get());

        RemoteHudOverlays.DismissPayload forcedDismiss = new RemoteHudOverlays.DismissPayload();
        forcedDismiss.sessionId = "";
        forcedDismiss.surfaceType = RemoteUiProtocol.SurfaceType.HUD.name();
        forcedDismiss.surfaceId = "shared-overlay";
        forcedDismiss.closeScope = RemoteUiProtocol.CloseScope.SURFACE.name();
        forcedDismiss.overlayId = "shared-overlay";
        forcedDismiss.reason = "server-force-dismiss";
        RemoteHudOverlayClientBridge.receiveDismiss(RemoteJson.toJson(forcedDismiss));

        Assert.assertFalse(bridge.hasActiveOverlayForTest("shared-overlay", "S2"));
        Assert.assertTrue("无 session 的强制关闭仍应按 overlayId 回退", unregistered.get());
        bridge.clearAll();
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

    private static ElementNode findListboxElement(DocumentNode node) {
        if (node instanceof ElementNode) {
            ElementNode element = (ElementNode) node;
            if ("listbox".equals(element.getAttribute("role"))) {
                return element;
            }
        }
        for (DocumentNode child : node.getChildren()) {
            ElementNode found = findListboxElement(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static int pointInsideX(ElementNode element) {
        DocumentElementBounds bounds = element.getDocumentBounds();
        Assert.assertTrue(bounds.isAvailable());
        return bounds.getLeft() + 8;
    }

    private static int pointInsideY(ElementNode element) {
        DocumentElementBounds bounds = element.getDocumentBounds();
        Assert.assertTrue(bounds.isAvailable());
        return bounds.getTop() + 8;
    }

    private static int optionY(ElementNode select, int optionIndex) {
        DocumentElementBounds bounds = select.getDocumentBounds();
        Assert.assertTrue(bounds.isAvailable());
        return bounds.getTop() + bounds.getHeight() + optionIndex * 28 + 8;
    }

    private static void click(HtmlLikeDocumentWidget widget, int x, int y, long timeNanos) {
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, x, y, 0, 0, 0, 0, timeNanos));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, x, y, 0, 0, 0, 0, timeNanos + 1L));
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
