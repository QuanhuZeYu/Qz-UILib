package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.screen.page.DirectDocumentPageAuthoringSurface;

import club.heiqi.uilib.ui.screen.page.DocumentUiScope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.DocumentNodeType;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;
import club.heiqi.uilib.ui.style.cascade.UiBorderRadiusResolver;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.style.values.UiSurfaceStyle;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * `HtmlLikeGlassDocumentPageController` 的页面集成契约测试。
 */
public class HtmlLikeGlassDocumentPageControllerTest {

    /**
     * 验证大面积磨玻璃测试页会挂接独立 HTML-like 文档。
     */
    @Test
    public void shouldBuildLargeGlassDocumentTree() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();

        List<Widget> blocks = fixture.pageSurface.getBlocks();
        Assert.assertEquals(1, blocks.size());
        Assert.assertTrue(blocks.get(0) instanceof HtmlLikeDocumentWidget);
        Assert.assertSame(blocks.get(0), fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(fixture.controller.getHtmlLikeDocumentWidget().isViewportRootScrollingEnabled());
        Assert.assertEquals(3, fixture.controller.getHtmlLikeDocumentWidget().getDocument()
                .getRootElement().getChildren().size());

        HtmlLikeDocumentWidget widget = fixture.controller.getHtmlLikeDocumentWidget();
        List<String> texts = collectDocumentTexts(widget);
        Assert.assertTrue(containsText(texts, "HTML-like Glass Lab"));
        Assert.assertTrue(containsText(texts, "Backdrop path: pending"));
        Assert.assertTrue(containsText(texts, "UI layer sampling field"));
        Assert.assertTrue(containsText(texts, "Large backdrop slab: blur 36px / saturate 125%"));
        Assert.assertTrue(containsText(texts, "Nested glass stack / 6 additional backdrop blocks"));
        Assert.assertTrue(containsText(texts, "Outer glass shell"));
        Assert.assertTrue(containsText(texts, "Middle glass shell"));
        Assert.assertTrue(containsText(texts, "Inner glass shell"));
        Assert.assertTrue(containsText(texts, "Scene level glass"));
        Assert.assertTrue(containsText(texts, "Tile atlas probe / block128 tile diagnostics"));
        Assert.assertTrue(containsText(texts, "Atlas target inside source"));
        Assert.assertTrue(containsText(texts, "Tile count target"));
        Assert.assertTrue(containsText(texts, "Tile probe Backdrop path: pending"));
        Assert.assertTrue(containsText(texts, "visual regressions are easy to see"));

        ElementNode glassSlab = findElementContainingDirectText(widget.getDocument().getRootElement(),
                "Large backdrop slab: blur 36px / saturate 125%");
        Assert.assertNotNull(glassSlab);
        Assert.assertEquals(UiPosition.ABSOLUTE, glassSlab.style().getPosition());

        ElementNode outerGlass = findElementContainingDirectText(widget.getDocument().getRootElement(),
                "Outer glass shell");
        Assert.assertNotNull(outerGlass);
        Assert.assertEquals(UiPosition.ABSOLUTE, outerGlass.style().getPosition());

        ElementNode sceneLevelGlass = findElementContainingDirectText(widget.getDocument().getRootElement(),
                "Scene level glass");
        Assert.assertNotNull(sceneLevelGlass);
        Assert.assertEquals(UiPosition.ABSOLUTE, sceneLevelGlass.style().getPosition());

        ElementNode sourceGlass = findElementContainingDirectText(widget.getDocument().getRootElement(),
                "Atlas source slab");
        Assert.assertNotNull(sourceGlass);
        Assert.assertEquals(UiPosition.ABSOLUTE, sourceGlass.style().getPosition());
    }

    /**
     * 验证页面每帧会刷新 backdrop 实际渲染路径诊断文本。
     */
    @Test
    public void shouldExposeBackdropRenderPathText() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        fixture.controller.beforeDocumentFrame();

        List<String> texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "Backdrop path:"));
        Assert.assertTrue(containsText(texts, "Tile probe Backdrop path:"));
    }

    /**
     * 验证测试页会生成覆盖面积明显更大的 backdrop-filter 调用。
     */
    @Test
    public void shouldRenderLargeBackdropSlab() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        HtmlLikeDocumentWidget widget = fixture.controller.getHtmlLikeDocumentWidget();
        widget.applyLayoutBounds(17, 29, 900, 620);
        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();

        widget.render(renderContext);

        Assert.assertFalse(renderContext.drawCalls.isEmpty());
        Assert.assertEquals(10, renderContext.backdropCalls.size());
        BackdropCall backdropCall = renderContext.backdropCalls.get(0);
        Assert.assertEquals(36, backdropCall.blurRadius);
        Assert.assertEquals(1.25F, backdropCall.saturation, 0.001F);
        Assert.assertEquals(20, backdropCall.cornerRadius);
        assertBackdropCall(renderContext.backdropCalls.get(1), 18, 1.20F);
        assertBackdropCall(renderContext.backdropCalls.get(2), 12, 1.16F);
        assertBackdropCall(renderContext.backdropCalls.get(3), 12, 1.14F);
        assertBackdropCall(renderContext.backdropCalls.get(4), 10, 1.12F);
        assertBackdropCall(renderContext.backdropCalls.get(5), 10, 1.12F);
        assertBackdropCall(renderContext.backdropCalls.get(6), 12, 1.14F);
        assertBackdropCall(renderContext.backdropCalls.get(7), 18, 1.20F);
        assertBackdropCall(renderContext.backdropCalls.get(8), 18, 1.20F);
        assertBackdropCall(renderContext.backdropCalls.get(9), 36, 1.25F);
        Assert.assertTrue(backdropCall.right - backdropCall.left >= 680);
        Assert.assertTrue(backdropCall.bottom - backdropCall.top >= 280);
        Assert.assertTrue(containsTextCall(renderContext.textCalls, "Large backdrop slab"));
        Assert.assertTrue(containsTextCall(renderContext.textCalls, "Nested level 3"));
        Assert.assertTrue(containsTextCall(renderContext.textCalls, "Direct canvas sibling"));
        Assert.assertTrue(containsTextCall(renderContext.textCalls, "Tile atlas probe"));
        Assert.assertTrue(containsTextCall(renderContext.textCalls, "tiles=N covered=M missing=K reused=R copied=C"));
        Assert.assertTrue(containsTextCall(renderContext.textCalls, "Tile probe Backdrop path"));
        Assert.assertTrue(containsTextCall(renderContext.textCalls, "Local path line updates with tiles="));

        ElementNode glassSlab = findElementContainingDirectText(widget.getDocument().getRootElement(),
                "Large backdrop slab: blur 36px / saturate 125%");
        Assert.assertNotNull(glassSlab);
        Assert.assertEquals(UiPosition.ABSOLUTE, glassSlab.style().getPosition());
    }

    private static List<String> collectDocumentTexts(HtmlLikeDocumentWidget widget) {
        List<String> texts = new ArrayList<String>();
        if (widget == null || widget.getDocument() == null) {
            return texts;
        }
        collectTextsFromNode(widget.getDocument().getRootElement(), texts);
        return texts;
    }

    private static void collectTextsFromNode(DocumentNode node, List<String> texts) {
        if (node.getNodeType() == DocumentNodeType.TEXT) {
            String text = ((TextNode) node).getText();
            if (text != null && !text.isEmpty()) {
                texts.add(text);
            }
        }
        if (node.getNodeType() == DocumentNodeType.ELEMENT) {
            ElementNode element = (ElementNode) node;
            for (DocumentNode child : element.getChildren()) {
                collectTextsFromNode(child, texts);
            }
        }
    }

    private static boolean containsText(List<String> texts, String expectedSnippet) {
        for (String text : texts) {
            if (text != null && text.contains(expectedSnippet)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsTextCall(List<TextCall> textCalls, String expectedSnippet) {
        for (TextCall textCall : textCalls) {
            if (textCall.text != null && textCall.text.contains(expectedSnippet)) {
                return true;
            }
        }
        return false;
    }

    private static ElementNode findElementContainingDirectText(ElementNode element, String expectedText) {
        if (element == null) {
            return null;
        }
        for (DocumentNode child : element.getChildren()) {
            if (child.getNodeType() == DocumentNodeType.TEXT) {
                String text = ((TextNode) child).getText();
                if (text != null && text.contains(expectedText)) {
                    return element;
                }
            } else if (child.getNodeType() == DocumentNodeType.ELEMENT) {
                ElementNode found = findElementContainingDirectText((ElementNode) child, expectedText);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void assertBackdropCall(BackdropCall backdropCall, int blurRadius, float saturation) {
        Assert.assertEquals(blurRadius, backdropCall.blurRadius);
        Assert.assertEquals(saturation, backdropCall.saturation, 0.001F);
    }

    /**
     * 页面控制器测试夹具。
     */
    private static final class TestFixture {

        private final TextMeasureService textMeasureService = new DeterministicTextMeasureService();
        private final DocumentUiScope documentUi = new DocumentUiScope(textMeasureService, UiRuntimeAdapters.empty());
        private final DirectDocumentPageAuthoringSurface pageSurface = new DirectDocumentPageAuthoringSurface();
        private final HtmlLikeGlassDocumentPageController controller = new HtmlLikeGlassDocumentPageController(
                documentUi, pageSurface, textMeasureService);
    }

    /**
     * 记录绘制调用的渲染上下文。
     */
    private static final class RecordingUiRenderContext extends UiRenderContext {

        private final List<DrawCall> drawCalls = new ArrayList<DrawCall>();
        private final List<TextCall> textCalls = new ArrayList<TextCall>();
        private final List<BackdropCall> backdropCalls = new ArrayList<BackdropCall>();

        private RecordingUiRenderContext() {
            super(1280, 720, 0, 0, 0.0F);
        }

        @Override
        public void drawSurface(int left, int top, int right, int bottom, UiSurfaceStyle surfaceStyle) {
            drawCalls.add(new DrawCall(left, top, right, bottom, surfaceStyle));
        }

        @Override
        public void drawBackdropFilter(int left, int top, int right, int bottom, int blurRadius, float saturation,
                int cornerRadius) {
            backdropCalls.add(new BackdropCall(left, top, right, bottom, blurRadius, saturation, cornerRadius));
        }

        @Override
        public void drawBackdropFilter(int left, int top, int right, int bottom, int blurRadius, float saturation,
                UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii) {
            int cornerRadius = cornerRadii == null ? 0 : cornerRadii.getUniformRadius();
            backdropCalls.add(new BackdropCall(left, top, right, bottom, blurRadius, saturation, cornerRadius));
        }

        @Override
        public void pushClip(int left, int top, int right, int bottom, int cornerRadius) {}

        @Override
        public void pushClip(int left, int top, int right, int bottom,
                UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii) {}

        @Override
        public void popClip() {}

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow) {
            textCalls.add(new TextCall(text, x, y, color, shadow));
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow,
                club.heiqi.uilib.ui.text.TextContentMode textContentMode) {
            textCalls.add(new TextCall(text, x, y, color, shadow));
        }

        @Override
        public boolean supportsDeferredTextBatching() {
            return false;
        }

        @Override
        public void fillRect(int left, int top, int right, int bottom, int color) {}

        @Override
        public int measureTextWidth(String text) {
            return text == null ? 0 : text.length() * 12;
        }

        @Override
        public int getTextLineHeight() {
            return 18;
        }

        @Override
        public void pushPaintContext(int left, int top, int right, int bottom, float opacity) {}

        @Override
        public boolean isCurrentPaintContextLayerActive() {
            return false;
        }

        @Override
        public void popPaintContext() {}
    }

    /**
     * 单次 surface 绘制记录。
     */
    private static final class DrawCall {

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final UiSurfaceStyle surfaceStyle;

        private DrawCall(int left, int top, int right, int bottom, UiSurfaceStyle surfaceStyle) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.surfaceStyle = surfaceStyle;
        }
    }

    /**
     * 单次 HTML-like 文本绘制记录。
     */
    private static final class TextCall {

        private final String text;
        private final int x;
        private final int y;
        private final int color;
        private final boolean shadow;

        private TextCall(String text, int x, int y, int color, boolean shadow) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
            this.shadow = shadow;
        }
    }

    /**
     * 单次 backdrop filter 投影记录。
     */
    private static final class BackdropCall {

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final int blurRadius;
        private final float saturation;
        private final int cornerRadius;

        private BackdropCall(int left, int top, int right, int bottom, int blurRadius, float saturation,
                int cornerRadius) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.blurRadius = blurRadius;
            this.saturation = saturation;
            this.cornerRadius = cornerRadius;
        }
    }

    /**
     * 供测试使用的确定性文本测量服务。
     */
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
}
