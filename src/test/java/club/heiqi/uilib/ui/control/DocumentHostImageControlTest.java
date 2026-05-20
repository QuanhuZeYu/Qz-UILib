package club.heiqi.uilib.ui.control;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.util.ResourceLocation;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.image.DocumentRemoteImageCache;
import club.heiqi.uilib.ui.image.HostImageRenderer;
import club.heiqi.uilib.ui.image.HostImageSource;
import club.heiqi.uilib.ui.layout.DocumentHitTestEngine;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine;
import club.heiqi.uilib.ui.paint.DocumentPaintCommand;
import club.heiqi.uilib.ui.paint.DocumentPaintCommandType;
import club.heiqi.uilib.ui.paint.DocumentPaintEngine;
import club.heiqi.uilib.ui.render.UiMainLayerSnapshotService;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;
import club.heiqi.uilib.ui.style.cascade.UiBorderRadiusResolver;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.props.UiObjectFit;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * `DocumentHostImageControl` 与背景贴图辅助的契约测试。
 */
public class DocumentHostImageControlTest {

    /**
     * 验证宿主图片控件会通过渲染上下文触发隔离贴图绘制。
     */
    @Test
    public void shouldRenderHostImageThroughRenderContext() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(160))
                .setHeight(UiStyleLength.px(120));
        DocumentHostImageControl imageControl = new DocumentHostImageControl(document,
                HostImageSource.texture(new ResourceLocation("qz_uilib", "textures/test/icon.png"), 32, 32))
                        .setSize(24);
        root.append(imageControl.getElement());

        RecordingHostImageRenderer hostImageRenderer = new RecordingHostImageRenderer();
        RecordingUiRenderContext renderContext = new RecordingUiRenderContext(160, 120,
                UiRuntimeAdapters.empty().withHostImageRenderer(hostImageRenderer));
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 160, 120,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 160, 120);

        widget.render(renderContext);

        Assert.assertEquals(1, hostImageRenderer.calls.size());
        Assert.assertTrue(hostImageRenderer.calls.get(0).contains("0,0,24,24"));
        List<DocumentPaintCommand> commands = DocumentPaintEngine.buildPaintCommands(
                DocumentLayoutEngine.layout(root, 160, 120, new DeterministicTextMeasureService()));
        Assert.assertEquals(1, countCommands(commands, DocumentPaintCommandType.CUSTOM));
    }

    /**
     * 验证背景贴图装饰默认不会截获前景元素命中。
     */
    @Test
    public void shouldKeepBackgroundDecorationHitTestHidden() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style().setWidth(UiStyleLength.px(180));
        ElementNode card = document.div();
        card.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(80));
        root.append(card);

        ElementNode background = DocumentHostImageDecorations.attachBackground(card,
                HostImageSource.texture(new ResourceLocation("qz_uilib", "textures/test/card.png"), 64, 64));

        Assert.assertEquals("true", background.getAttribute("data-hit-test-hidden"));
        Assert.assertEquals(UiPosition.ABSOLUTE, background.style().getPosition());

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 180, 100, new DeterministicTextMeasureService());
        ElementNode hit = DocumentHitTestEngine.hitTest(rootBox, null, 10, 10);

        Assert.assertNotNull(hit);
        Assert.assertEquals(card.__getElementUid(), hit.__getElementUid());
    }

    /**
     * 验证宿主图片控件默认走纯装饰语义，不声明为可交互元素。
     */
    @Test
    public void shouldMarkHostImageAsDecorativePresentation() {
        UiDocument document = UiDocument.create();
        DocumentHostImageControl imageControl = new DocumentHostImageControl(document,
                HostImageSource.texture(new ResourceLocation("qz_uilib", "textures/test/icon.png"), 16, 16));

        ElementNode element = imageControl.getElement();

        Assert.assertEquals("img", element.getTagName());
        Assert.assertEquals("presentation", element.getAttribute("role"));
        Assert.assertEquals("true", element.getAttribute("aria-hidden"));
        Assert.assertEquals("true", element.getAttribute("data-hit-test-hidden"));
    }

    /**
     * 验证普通 img 元素可以通过 src 属性触发宿主位图绘制。
     */
    @Test
    public void shouldRenderImageElementFromSrcAttribute() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(160))
                .setHeight(UiStyleLength.px(120));
        ElementNode image = document.img();
        image.setAttribute("src", "qz_uilib:textures/test/icon.png");
        image.style()
                .setWidth(UiStyleLength.px(32))
                .setHeight(UiStyleLength.px(24));
        root.append(image);

        RecordingHostImageRenderer hostImageRenderer = new RecordingHostImageRenderer();
        RecordingUiRenderContext renderContext = new RecordingUiRenderContext(160, 120,
                UiRuntimeAdapters.empty().withHostImageRenderer(hostImageRenderer));
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 160, 120,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 160, 120);

        widget.render(renderContext);

        Assert.assertEquals("img", image.getTagName());
        Assert.assertEquals(1, hostImageRenderer.calls.size());
        Assert.assertTrue(hostImageRenderer.calls.get(0).contains("0,0,32,24"));
    }

    /**
     * 验证远程 URL 图片在缓存命中后可作为 img 源绘制。
     */
    @Test
    public void shouldRenderRemoteImageElementWhenLoaded() {
        DocumentRemoteImageCache.getInstance().clearForTesting();
        DocumentRemoteImageCache.getInstance().putForTesting("https://example.test/icon.png",
                new BufferedImage(40, 20, BufferedImage.TYPE_INT_ARGB));
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(160))
                .setHeight(UiStyleLength.px(120));
        ElementNode image = document.img();
        image.setAttribute("src", "https://example.test/icon.png");
        root.append(image);

        RecordingHostImageRenderer hostImageRenderer = new RecordingHostImageRenderer();
        RecordingUiRenderContext renderContext = new RecordingUiRenderContext(160, 120,
                UiRuntimeAdapters.empty().withHostImageRenderer(hostImageRenderer));
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 160, 120,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 160, 120);

        widget.render(renderContext);

        Assert.assertEquals(1, hostImageRenderer.calls.size());
        Assert.assertTrue(hostImageRenderer.calls.get(0).startsWith("BUFFERED_IMAGE@"));
        Assert.assertTrue(hostImageRenderer.calls.get(0).contains("0,0,40,20"));
    }

    /**
     * 验证 `img` 元素的 `object-fit: contain` 会按固有比例在内容区内居中绘制。
     */
    @Test
    public void shouldApplyObjectFitContainWhenRenderingImageElement() {
        DocumentRemoteImageCache.getInstance().clearForTesting();
        DocumentRemoteImageCache.getInstance().putForTesting("https://example.test/contain.png",
                new BufferedImage(40, 20, BufferedImage.TYPE_INT_ARGB));
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(160))
                .setHeight(UiStyleLength.px(120));
        ElementNode image = document.img();
        image.setAttribute("src", "https://example.test/contain.png");
        image.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(80))
                .setObjectFit(UiObjectFit.CONTAIN);
        root.append(image);

        RecordingHostImageRenderer hostImageRenderer = new RecordingHostImageRenderer();
        RecordingUiRenderContext renderContext = new RecordingUiRenderContext(160, 120,
                UiRuntimeAdapters.empty().withHostImageRenderer(hostImageRenderer));
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 160, 120,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 160, 120);

        widget.render(renderContext);

        Assert.assertEquals(1, hostImageRenderer.calls.size());
        Assert.assertTrue(hostImageRenderer.calls.get(0).startsWith("BUFFERED_IMAGE@"));
        Assert.assertTrue(hostImageRenderer.calls.get(0).contains("0,20,80,60"));
    }

    private static int countCommands(List<DocumentPaintCommand> commands, DocumentPaintCommandType type) {
        int count = 0;
        for (DocumentPaintCommand command : commands) {
            if (command.getType() == type) {
                count++;
            }
        }
        return count;
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

    private static final class RecordingHostImageRenderer implements HostImageRenderer {

        private final List<String> calls = new ArrayList<String>();

        @Override
        public void render(HostImageSource source, int left, int top, int right, int bottom) {
            calls.add(source.getKind().name() + "@" + left + "," + top + "," + right + "," + bottom);
        }
    }

    private static final class RecordingUiRenderContext extends UiRenderContext {

        private RecordingUiRenderContext(int width, int height, UiRuntimeAdapters runtimeAdapters) {
            super(width, height, 0, 0, 1.0F, new PaintContextCompositor(), new UiMainLayerSnapshotService(),
                    runtimeAdapters);
        }

        @Override
        public void drawSurface(int left, int top, int right, int bottom,
                club.heiqi.uilib.ui.style.values.UiSurfaceStyle surfaceStyle) {}

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow) {}

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow,
                club.heiqi.uilib.ui.text.TextContentMode textContentMode) {}

        @Override
        public boolean supportsDeferredTextBatching() {
            return false;
        }

        @Override
        public void pushClip(int left, int top, int right, int bottom, int cornerRadius) {}

        @Override
        public void pushClip(int left, int top, int right, int bottom,
                UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii) {}

        @Override
        public void popClip() {}

        @Override
        public void drawHostImage(HostImageSource source, int left, int top, int right, int bottom) {
            HostImageRenderer renderer = getRuntimeAdapters().getHostImageRenderer();
            if (renderer != null) {
                renderer.render(source, left, top, right, bottom);
            }
        }
    }
}
