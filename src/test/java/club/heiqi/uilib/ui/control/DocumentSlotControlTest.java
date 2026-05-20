package club.heiqi.uilib.ui.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.dom.DocumentElementActiveEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementHoverEvent;
import club.heiqi.uilib.ui.dom.DocumentElementKeyEvent;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.image.HostImageSource;
import club.heiqi.uilib.ui.slot.SlotContentSnapshot;
import club.heiqi.uilib.ui.style.UiDisplay;
import club.heiqi.uilib.ui.style.UiStyleResolver;

/**
 * `DocumentSlotControl` 的基础行为契约测试。
 */
public class DocumentSlotControlTest {

    /**
     * 验证空槽默认无图片且保留按钮语义。
     */
    @Test
    public void shouldKeepEmptySlotImageHiddenAndInteractive() {
        UiDocument document = UiDocument.create();
        DocumentSlotControl slotControl = new DocumentSlotControl(document);

        ElementNode slot = slotControl.getElement();
        ElementNode image = slotImageElement(slot);

        Assert.assertEquals("button", slot.getAttribute("role"));
        Assert.assertEquals("0", slot.getAttribute("tabindex"));
        Assert.assertEquals("false", slot.getAttribute("data-slot-occupied"));
        Assert.assertEquals(UiDisplay.NONE, UiStyleResolver.compute(image).getDisplay());
    }

    /**
     * 验证占用态会同步图片、数量和 aria 描述。
     */
    @Test
    public void shouldRenderOccupiedSlotFromGenericSnapshot() {
        UiDocument document = UiDocument.create();
        DocumentSlotControl slotControl = new DocumentSlotControl(document)
                .setSlotLabel("槽位 3")
                .setContent(SlotContentSnapshot.builder()
                        .setOccupied(true)
                        .setContentKind("item")
                        .setVisualSource(HostImageSource.textureRegion(
                                new net.minecraft.util.ResourceLocation("minecraft", "textures/gui/widgets.png"),
                                256, 256, 0, 0, 1, 1))
                        .setDisplayName("钻石")
                        .setPrimaryCount(4)
                        .build());

        ElementNode slot = slotControl.getElement();
        ElementNode image = slotImageElement(slot);

        Assert.assertEquals("true", slot.getAttribute("data-slot-occupied"));
        Assert.assertEquals("item", slot.getAttribute("data-slot-content-kind"));
        Assert.assertEquals("槽位 3，钻石，数量 4", slot.getAttribute("aria-label"));
        Assert.assertEquals(UiDisplay.BLOCK, UiStyleResolver.compute(image).getDisplay());
    }

    /**
     * 验证 hover 会更新高亮并通知 tooltip。
     */
    @Test
    public void shouldHighlightHoveredSlotAndNotifyTooltip() {
        UiDocument document = UiDocument.create();
        final List<String> tooltipEvents = new ArrayList<String>();
        DocumentSlotControl slotControl = new DocumentSlotControl(document)
                .setContent(SlotContentSnapshot.builder()
                        .setOccupied(true)
                        .setDisplayName("苹果")
                        .setTooltipLines(Collections.singletonList("Tooltip 1"))
                        .build())
                .setSlotHoverHandler(new DocumentSlotControl.SlotHoverHandler() {
                    @Override
                    public void onSlotHoverChanged(boolean hovered, List<String> tooltipLines, int documentX,
                            int documentY, long timeNanos) {
                        tooltipEvents.add(hovered + ":" + (tooltipLines.isEmpty() ? "empty" : tooltipLines.get(0)));
                    }
                });

        ElementNode slot = slotControl.getElement();
        slot.getHoverHandler().onHoverChanged(new DocumentElementHoverEvent(slot, slot, true, 6, 8, 1L));

        Assert.assertTrue(slotControl.isHovered());
        Assert.assertEquals(Integer.valueOf(0xDD263349), slot.style().getBackgroundColor());
        Assert.assertEquals(Collections.singletonList("true:Tooltip 1"), tooltipEvents);
    }

    /**
     * 验证携带内容时会抑制 tooltip。
     */
    @Test
    public void shouldSuppressTooltipWhenCarriedContentIsOccupied() {
        UiDocument document = UiDocument.create();
        final List<String> tooltipEvents = new ArrayList<String>();
        DocumentSlotControl slotControl = new DocumentSlotControl(document)
                .setContent(SlotContentSnapshot.builder()
                        .setOccupied(true)
                        .setDisplayName("苹果")
                        .setTooltipLines(Collections.singletonList("Tooltip 1"))
                        .build())
                .setCarriedContent(SlotContentSnapshot.occupied("item", null, "携带物"))
                .setSlotHoverHandler(new DocumentSlotControl.SlotHoverHandler() {
                    @Override
                    public void onSlotHoverChanged(boolean hovered, List<String> tooltipLines, int documentX,
                            int documentY, long timeNanos) {
                        tooltipEvents.add(hovered + ":" + (tooltipLines.isEmpty() ? "empty" : tooltipLines.get(0)));
                    }
                });

        ElementNode slot = slotControl.getElement();
        slot.getHoverHandler().onHoverChanged(new DocumentElementHoverEvent(slot, slot, true, 6, 8, 1L));

        Assert.assertEquals(Collections.singletonList("true:empty"), tooltipEvents);
    }

    /**
     * 验证点击和键盘激活会透传到处理器。
     */
    @Test
    public void shouldDispatchClickAndKeyboardActivation() {
        UiDocument document = UiDocument.create();
        final List<Integer> clickedButtons = new ArrayList<Integer>();
        DocumentSlotControl slotControl = new DocumentSlotControl(document)
                .setSlotClickHandler(new DocumentSlotControl.SlotClickHandler() {
                    @Override
                    public boolean onSlotClick(int button, long timeNanos) {
                        clickedButtons.add(Integer.valueOf(button));
                        return true;
                    }
                });

        ElementNode slot = slotControl.getElement();
        Assert.assertTrue(slot.getClickHandler().onClick(new DocumentElementClickEvent(slot, slot, 1, 1, 1, 2L)));
        Assert.assertTrue(slot.getKeyHandler().onKey(new DocumentElementKeyEvent(slot, slot,
                new UiKeyEvent(Keyboard.KEY_SPACE, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                        false, 4L))));
        Assert.assertEquals(1, clickedButtons.size());
        Assert.assertTrue(slot.getKeyHandler().onKey(new DocumentElementKeyEvent(slot, slot,
                new UiKeyEvent(Keyboard.KEY_SPACE, 0, 0, UiKeyEvent.Action.RELEASED, false, false, false,
                        false, 5L))));

        Assert.assertEquals(Integer.valueOf(1), clickedButtons.get(0));
        Assert.assertEquals(Integer.valueOf(0), clickedButtons.get(1));
    }

    /**
     * 验证选中态与按下态拥有独立高亮。
     */
    @Test
    public void shouldApplySelectedAndActiveHighlights() {
        UiDocument document = UiDocument.create();
        DocumentSlotControl slotControl = new DocumentSlotControl(document)
                .setSelected(true);
        ElementNode slot = slotControl.getElement();

        Assert.assertEquals(Integer.valueOf(0xDD273B20), slot.style().getBackgroundColor());
        Assert.assertEquals(Integer.valueOf(0xFFFFD166), slot.style().getBorderColor());

        slot.getActiveHandler().onActiveChanged(new DocumentElementActiveEvent(slot, slot, true, 0, 1L));

        Assert.assertEquals(Integer.valueOf(0xEE334155), slot.style().getBackgroundColor());
        Assert.assertEquals(Integer.valueOf(0xFFFFFFFF), slot.style().getBorderColor());
    }

    private static ElementNode slotImageElement(ElementNode slotElement) {
        for (club.heiqi.uilib.ui.dom.DocumentNode child : slotElement.getChildren()) {
            if (child instanceof ElementNode && "true".equals(((ElementNode) child).getAttribute("data-slot-image"))) {
                return (ElementNode) child;
            }
        }
        throw new AssertionError("slot image element not found");
    }
}
