package club.heiqi.uilib.ui.hud;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiStyleLength;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
/**
 * `UiHudDocumentHost` 的稳定契约测试。
 */
public class UiHudDocumentHostTest {

    /**
     * 验证 HUD 屏幕分类会把纯游戏、容器和菜单页区分开。
     */
    @Test
    public void shouldClassifyHudScreenCategories() {
        Assert.assertEquals(UiHudScreenCategory.INGAME, UiHudDocumentHost.classifyScreen(null, null));
        Assert.assertEquals(UiHudScreenCategory.CONTAINER,
                UiHudDocumentHost.classifyScreen(new Object(), "net.minecraft.client.gui.inventory.GuiChest"));
        Assert.assertEquals(UiHudScreenCategory.CONTAINER,
                UiHudDocumentHost.classifyScreen(new Object(), "net.minecraft.client.gui.GuiChat"));
        Assert.assertEquals(UiHudScreenCategory.MENU,
                UiHudDocumentHost.classifyScreen(new Object(), "net.minecraft.client.gui.GuiIngameMenu"));
        Assert.assertEquals(UiHudScreenCategory.MENU,
                UiHudDocumentHost.classifyScreen(new Object(), "example.custom.Screen"));
    }

    /**
     * 验证被动 HUD 根节点默认命中隐藏且使用全视口可见容器契约。
     */
    @Test
    public void shouldApplyPassiveHudRootContract() {
        UiDocument document = captureRegisteredDocument(UiHudLayerType.PASSIVE);
        ElementNode root = document.getRootElement();

        Assert.assertEquals(UiStyleLength.percent(1.0F), root.style().getWidth());
        Assert.assertEquals(UiStyleLength.percent(1.0F), root.style().getHeight());
        Assert.assertEquals(UiOverflow.VISIBLE, root.style().getOverflowX());
        Assert.assertEquals(UiOverflow.VISIBLE, root.style().getOverflowY());
        Assert.assertEquals("passive", root.getAttribute("data-hud-layer"));
        Assert.assertEquals("true", root.getAttribute("data-hit-test-hidden"));
    }

    /**
     * 验证交互 HUD 根节点沿用全视口可见容器契约，但不会被默认标记为命中隐藏。
     */
    @Test
    public void shouldApplyInteractiveHudRootContract() {
        UiDocument document = captureRegisteredDocument(UiHudLayerType.INTERACTIVE);
        ElementNode root = document.getRootElement();

        Assert.assertEquals(UiStyleLength.percent(1.0F), root.style().getWidth());
        Assert.assertEquals(UiStyleLength.percent(1.0F), root.style().getHeight());
        Assert.assertEquals(UiOverflow.VISIBLE, root.style().getOverflowX());
        Assert.assertEquals(UiOverflow.VISIBLE, root.style().getOverflowY());
        Assert.assertEquals("interactive", root.getAttribute("data-hud-layer"));
        Assert.assertNull(root.getAttribute("data-hit-test-hidden"));
    }

    private static UiDocument captureRegisteredDocument(UiHudLayerType layerType) {
        final UiDocument[] holder = new UiDocument[1];
        UiHudDocumentRegistration registration = UiHudDocumentHost.getInstance().register(layerType,
                new UiHudDocumentHost.UiHudDocumentContentBuilder() {
                    @Override
                    public void build(UiDocument document) {
                        holder[0] = document;
                    }
                }, DefaultTextMeasureService.getInstance(), UiRuntimeAdapters.empty());
        try {
            return holder[0];
        } finally {
            registration.unregister();
        }
    }
}
