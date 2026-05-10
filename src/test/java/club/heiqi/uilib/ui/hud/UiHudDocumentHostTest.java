package club.heiqi.uilib.ui.hud;

import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.dom.control.DocumentButtonActionEvent;
import club.heiqi.uilib.ui.dom.control.DocumentButtonActionHandler;
import club.heiqi.uilib.ui.dom.control.DocumentButtonControl;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.input.UiInputFrame;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiStyleLength;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
import club.heiqi.uilib.ui.text.TextMeasureService;

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

    /**
     * 验证交互 HUD 在非菜单且鼠标已释放时都允许接通输入。
     */
    @Test
    public void shouldEnableInteractiveHudInputOnAnyOpenedScreenWhenMouseIsFree() {
        Assert.assertFalse(UiHudDocumentHost.isInteractiveInputEnabled(null, null, false));
        Assert.assertTrue(UiHudDocumentHost.isInteractiveInputEnabled(new Object(),
                "net.minecraft.client.gui.inventory.GuiChest", false));
        Assert.assertTrue(UiHudDocumentHost.isInteractiveInputEnabled(new Object(),
                "example.custom.Screen", false));
        Assert.assertTrue(UiHudDocumentHost.isInteractiveInputEnabled(new Object(),
                "net.minecraft.client.gui.GuiIngameMenu", false));
        Assert.assertFalse(UiHudDocumentHost.isInteractiveInputEnabled(null, null, true));
    }

    /**
     * 验证交互 HUD 在点击回调里立即注销自身时，不会破坏当前输入帧遍历。
     */
    @Test
    public void shouldAllowInteractiveHudToUnregisterDuringInputRouting() {
        UiHudDocumentHost host = UiHudDocumentHost.getInstance();
        final UiHudDocumentRegistration[] registrationHolder = new UiHudDocumentRegistration[1];
        final int[] actionCount = new int[1];
        registrationHolder[0] = host.register(UiHudLayerType.INTERACTIVE,
                new UiHudDocumentHost.UiHudDocumentContentBuilder() {
                    @Override
                    public void build(UiDocument document) {
                        ElementNode root = document.getRootElement();
                        root.style()
                                .setWidth(UiStyleLength.px(160))
                                .setHeight(UiStyleLength.px(80));

                        DocumentButtonControl buttonControl = new DocumentButtonControl(document, "Hide");
                        buttonControl.getElement().style()
                                .setWidth(UiStyleLength.px(120))
                                .setHeight(UiStyleLength.px(32));
                        buttonControl.setActionHandler(new DocumentButtonActionHandler() {
                            @Override
                            public void onAction(DocumentButtonActionEvent event) {
                                actionCount[0]++;
                                registrationHolder[0].unregister();
                            }
                        });
                        root.append(buttonControl.getElement());
                    }
        }, new DeterministicTextMeasureService(), UiRuntimeAdapters.empty());

        try {
            host.handleInputFrameForTest(mouseFrame(UiMouseEvent.Action.BUTTON_DOWN, 8, 8, 1L),
                    UiHudScreenCategory.CONTAINER, 160, 80);
            host.handleInputFrameForTest(mouseFrame(UiMouseEvent.Action.BUTTON_UP, 8, 8, 2L),
                    UiHudScreenCategory.CONTAINER, 160, 80);

            Assert.assertEquals(1, actionCount[0]);
        } finally {
            registrationHolder[0].unregister();
        }
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

    private static UiInputFrame mouseFrame(UiMouseEvent.Action action, int mouseX, int mouseY, long timeNanos) {
        return new UiInputFrame(mouseX, mouseY,
                Collections.singletonList(new UiMouseEvent(action, mouseX, mouseY, 0, 0, 0, 0, timeNanos)),
                Collections.emptyList(), Collections.emptyList());
    }

    private static final class DeterministicTextMeasureService implements TextMeasureService {

        @Override
        public int getEpoch() {
            return 0;
        }

        @Override
        public int getStringWidth(String text) {
            return text == null ? 0 : text.length() * 6;
        }

        @Override
        public int getLineHeight() {
            return 9;
        }

        @Override
        public String trimStringToWidth(String text, int targetWidth) {
            if (text == null || text.isEmpty() || targetWidth <= 0) {
                return "";
            }
            int maxChars = Math.max(0, targetWidth / 6);
            return text.length() <= maxChars ? text : text.substring(0, maxChars);
        }

        @Override
        public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            return Collections.singletonList(text == null ? "" : trimStringToWidth(text, wrapWidth));
        }
    }
}
