package club.heiqi.uilib.ui.control;

import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.cascade.UiStyleResolver;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * `DocumentTooltipOverlayControl` 的基础行为契约测试。
 */
public class DocumentTooltipOverlayControlTest {

    /**
     * 验证隐藏态会把 tooltip 放到离屏位置并保持 aria-hidden。
     */
    @Test
    public void shouldHideTooltipWhenNoVisibleRequest() {
        UiDocument document = UiDocument.create();
        DocumentTooltipOverlayControl control = new DocumentTooltipOverlayControl(document,
                new DeterministicTextMeasureService(), new FixedViewportPointerProvider(320, 240, 80, 96));

        control.setRequestedTooltip(false, Collections.singletonList("Tooltip")).refresh();

        ElementNode tooltip = control.getElement();
        Assert.assertEquals("tooltip", tooltip.getAttribute("role"));
        Assert.assertEquals("true", tooltip.getAttribute("aria-hidden"));
        Assert.assertEquals(UiBorderStyle.SOLID, tooltip.style().getBorderStyle());
        Assert.assertEquals(0.0F, tooltip.style().getWidth().getValue(), 0.001F);
        Assert.assertEquals(-10000.0F, tooltip.style().getLeft().getValue(), 0.001F);
    }

    /**
     * 验证显示态会生成段落子节点并按指针定位。
     */
    @Test
    public void shouldRenderLinesAndPlaceTooltipNearPointer() {
        UiDocument document = UiDocument.create();
        DocumentTooltipOverlayControl control = new DocumentTooltipOverlayControl(document,
                new DeterministicTextMeasureService(), new FixedViewportPointerProvider(320, 240, 80, 96));

        control.setRequestedTooltip(true, Collections.singletonList("Tooltip 0")).refresh();

        ElementNode tooltip = control.getElement();
        Assert.assertEquals("false", tooltip.getAttribute("aria-hidden"));
        Assert.assertEquals(UiDisplay.FLEX, UiStyleResolver.compute(tooltip).getDisplay());
        Assert.assertEquals(103.0F, tooltip.style().getLeft().getValue(), 0.001F);
        Assert.assertEquals(119.0F, tooltip.style().getTop().getValue(), 0.001F);
        Assert.assertEquals(1, tooltip.getChildCount());
        Assert.assertEquals("p", ((ElementNode) tooltip.getChildren().get(0)).getTagName());
    }

    /**
     * 验证抑制态会隐藏已请求显示的 tooltip。
     */
    @Test
    public void shouldHideTooltipWhenSuppressed() {
        UiDocument document = UiDocument.create();
        DocumentTooltipOverlayControl control = new DocumentTooltipOverlayControl(document,
                new DeterministicTextMeasureService(), new FixedViewportPointerProvider(320, 240, 80, 96));

        control.setRequestedTooltip(true, Collections.singletonList("Tooltip 0"))
                .setSuppressed(true)
                .refresh();

        ElementNode tooltip = control.getElement();
        Assert.assertEquals("true", tooltip.getAttribute("aria-hidden"));
        Assert.assertEquals(0.0F, tooltip.style().getWidth().getValue(), 0.001F);
    }

    /**
     * 验证视口变化后重新刷新会跟随新的指针位置。
     */
    @Test
    public void shouldRepositionTooltipOnRefresh() {
        UiDocument document = UiDocument.create();
        MutableViewportPointerProvider provider = new MutableViewportPointerProvider(320, 240, 80, 96);
        DocumentTooltipOverlayControl control = new DocumentTooltipOverlayControl(document,
                new DeterministicTextMeasureService(), provider);

        control.setRequestedTooltip(true, Collections.singletonList("Tooltip 0")).refresh();
        provider.pointerX = 140;
        provider.pointerY = 152;

        control.refresh();

        ElementNode tooltip = control.getElement();
        Assert.assertEquals(163.0F, tooltip.style().getLeft().getValue(), 0.001F);
        Assert.assertEquals(96.0F, tooltip.style().getTop().getValue(), 0.001F);
    }

    private static final class DeterministicTextMeasureService implements TextMeasureService {

        @Override
        public int getEpoch() {
            return 1;
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
            return text == null ? "" : text;
        }

        @Override
        public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            return Collections.singletonList(text == null ? "" : text);
        }
    }

    private static class FixedViewportPointerProvider implements DocumentTooltipOverlayControl.ViewportPointerProvider {

        private final int viewportWidth;
        private final int viewportHeight;
        private final int pointerX;
        private final int pointerY;

        private FixedViewportPointerProvider(int viewportWidth, int viewportHeight, int pointerX, int pointerY) {
            this.viewportWidth = viewportWidth;
            this.viewportHeight = viewportHeight;
            this.pointerX = pointerX;
            this.pointerY = pointerY;
        }

        @Override
        public int getViewportWidth() {
            return viewportWidth;
        }

        @Override
        public int getViewportHeight() {
            return viewportHeight;
        }

        @Override
        public int getPointerX() {
            return pointerX;
        }

        @Override
        public int getPointerY() {
            return pointerY;
        }
    }

    private static final class MutableViewportPointerProvider extends FixedViewportPointerProvider {

        private int pointerX;
        private int pointerY;

        private MutableViewportPointerProvider(int viewportWidth, int viewportHeight, int pointerX, int pointerY) {
            super(viewportWidth, viewportHeight, pointerX, pointerY);
            this.pointerX = pointerX;
            this.pointerY = pointerY;
        }

        @Override
        public int getPointerX() {
            return pointerX;
        }

        @Override
        public int getPointerY() {
            return pointerY;
        }
    }
}
