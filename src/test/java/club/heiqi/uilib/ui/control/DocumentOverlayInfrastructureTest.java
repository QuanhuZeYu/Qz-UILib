package club.heiqi.uilib.ui.control;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.layout.DocumentHitTestEngine;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 通用 overlay 基础设施契约测试。
 */
public class DocumentOverlayInfrastructureTest {

    /**
     * 验证 overlay host 本身保持零尺寸，但可以承载子 overlay 层。
     */
    @Test
    public void shouldKeepOverlayHostZeroSizedWhileContainingLayers() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(200))
                .setHeight(UiStyleLength.px(120));

        DocumentOverlayHostControl overlayHost = new DocumentOverlayHostControl(document);
        DocumentOverlayLayerControl overlayLayer = new DocumentOverlayLayerControl(document, "aside")
                .setOverlaySize(60, 24)
                .setOverlayPosition(20, 30)
                .setZIndex(10);
        overlayHost.appendOverlay(overlayLayer.getElement());
        root.append(overlayHost.getElement());

        ElementNode hostElement = overlayHost.getElement();
        Assert.assertEquals("true", hostElement.getAttribute("data-overlay-host"));
        Assert.assertEquals(0.0F, hostElement.style().getWidth().getValue(), 0.001F);
        Assert.assertEquals(0.0F, hostElement.style().getHeight().getValue(), 0.001F);
        Assert.assertEquals(1, hostElement.getChildCount());
        Assert.assertEquals("true", ((ElementNode) hostElement.getChildren().get(0)).getAttribute("data-overlay-layer"));
    }

    /**
     * 验证命中隐藏 overlay 层不会截获下层元素命中。
     */
    @Test
    public void shouldLetHitTestPassThroughHiddenOverlayLayer() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode target = document.div();
        target.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        root.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(80));
        root.append(target);

        DocumentOverlayHostControl overlayHost = new DocumentOverlayHostControl(document);
        DocumentOverlayLayerControl overlayLayer = new DocumentOverlayLayerControl(document, "div")
                .setHitTestHidden(true)
                .setOverlaySize(120, 80)
                .setOverlayPosition(0, 0)
                .setZIndex(1000);
        overlayHost.appendOverlay(overlayLayer.getElement());
        root.append(overlayHost.getElement());

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 120, 80);
        ElementNode hit = DocumentHitTestEngine.hitTest(rootBox, null, 10, 10);

        Assert.assertNotNull(hit);
        Assert.assertEquals(target.__getElementUid(), hit.__getElementUid());
    }

    /**
     * 验证 overlay 层默认使用 fixed 定位并支持离屏隐藏。
     */
    @Test
    public void shouldUseFixedPositionAndSupportOffscreenHiding() {
        UiDocument document = UiDocument.create();
        DocumentOverlayLayerControl overlayLayer = new DocumentOverlayLayerControl(document, "div")
                .setOverlaySize(48, 24)
                .setOverlayPosition(12, 18);

        ElementNode element = overlayLayer.getElement();
        Assert.assertEquals("true", element.getAttribute("data-overlay-layer"));
        Assert.assertEquals(UiPosition.FIXED, element.style().getPosition());
        Assert.assertEquals(12.0F, element.style().getLeft().getValue(), 0.001F);
        Assert.assertEquals(18.0F, element.style().getTop().getValue(), 0.001F);

        overlayLayer.collapseOffscreen();

        Assert.assertEquals(0.0F, element.style().getWidth().getValue(), 0.001F);
        Assert.assertEquals(0.0F, element.style().getHeight().getValue(), 0.001F);
        Assert.assertEquals(-10000.0F, element.style().getLeft().getValue(), 0.001F);
        Assert.assertEquals(-10000.0F, element.style().getTop().getValue(), 0.001F);
    }
}
