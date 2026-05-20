package club.heiqi.uilib.ui.control;

import net.minecraft.util.ResourceLocation;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.image.HostImageSource;
import club.heiqi.uilib.ui.style.UiDisplay;
import club.heiqi.uilib.ui.style.UiStyleResolver;

/**
 * `DocumentCursorOverlayControl` 的基础行为契约测试。
 */
public class DocumentCursorOverlayControlTest {

    /**
     * 验证无图片源时会保持隐藏并离屏。
     */
    @Test
    public void shouldStayHiddenWhenSourceIsMissing() {
        UiDocument document = UiDocument.create();
        MutablePointerProvider pointerProvider = new MutablePointerProvider(80, 96);
        DocumentCursorOverlayControl control = new DocumentCursorOverlayControl(document, pointerProvider,
                placeholderSource())
                .setSize(24)
                .setAnchorOffset(12);

        control.setSource(null).refresh();

        ElementNode element = control.getElement();
        Assert.assertEquals("img", element.getTagName());
        Assert.assertEquals("true", element.getAttribute("data-hit-test-hidden"));
        Assert.assertEquals(UiDisplay.NONE, UiStyleResolver.compute(element).getDisplay());
        Assert.assertEquals(-10000.0F, element.style().getLeft().getValue(), 0.001F);
        Assert.assertEquals(-10000.0F, element.style().getTop().getValue(), 0.001F);
    }

    /**
     * 验证图片源存在时会显示并跟随指针偏移。
     */
    @Test
    public void shouldShowAndFollowPointerWhenSourceExists() {
        UiDocument document = UiDocument.create();
        MutablePointerProvider pointerProvider = new MutablePointerProvider(80, 96);
        DocumentCursorOverlayControl control = new DocumentCursorOverlayControl(document, pointerProvider,
                placeholderSource())
                .setSize(24)
                .setAnchorOffset(12);

        control.setSource(HostImageSource.texture(new ResourceLocation("qz_uilib", "textures/test/icon.png"),
                16, 16)).refresh();

        ElementNode element = control.getElement();
        Assert.assertEquals(UiDisplay.BLOCK, UiStyleResolver.compute(element).getDisplay());
        Assert.assertEquals(68.0F, element.style().getLeft().getValue(), 0.001F);
        Assert.assertEquals(84.0F, element.style().getTop().getValue(), 0.001F);

        pointerProvider.pointerX = 140;
        pointerProvider.pointerY = 152;
        control.refresh();

        Assert.assertEquals(128.0F, element.style().getLeft().getValue(), 0.001F);
        Assert.assertEquals(140.0F, element.style().getTop().getValue(), 0.001F);
    }

    private static HostImageSource placeholderSource() {
        return HostImageSource.textureRegion(new ResourceLocation("minecraft", "textures/gui/widgets.png"),
                256, 256, 0, 0, 1, 1);
    }

    private static final class MutablePointerProvider implements DocumentCursorOverlayControl.PointerProvider {

        private int pointerX;
        private int pointerY;

        private MutablePointerProvider(int pointerX, int pointerY) {
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
