package club.heiqi.uilib.ui.document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;

import club.heiqi.uilib.ui.animation.DocumentAnimationClock;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.event.UiTextInputEvent;
import club.heiqi.uilib.ui.image.HostImageSource;
import club.heiqi.uilib.ui.input.UiInputFrame;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.base.cascade.UiBorderRadiusResolver;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.base.props.UiFontStyle;
import club.heiqi.uilib.ui.base.props.UiFontWeight;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.base.values.UiSurfaceStyle;
import club.heiqi.uilib.ui.base.values.UiTransform;
import club.heiqi.uilib.ui.text.TextContentMode;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.text.TextMeasureStyle;

/**
 * `HtmlLikeDocumentWidget` 测试共享 fixture 和断言工具。
 */
final class HtmlLikeDocumentWidgetTestSupport {

    private HtmlLikeDocumentWidgetTestSupport() {}

    static void assertDrawCall(DrawCall drawCall, int left, int top, int right, int bottom, int fillColor,
            int borderColor, int cornerRadius) {
        Assert.assertEquals(left, drawCall.left);
        Assert.assertEquals(top, drawCall.top);
        Assert.assertEquals(right, drawCall.right);
        Assert.assertEquals(bottom, drawCall.bottom);
        Assert.assertEquals(fillColor, drawCall.surfaceStyle.fillColor);
        Assert.assertEquals(borderColor, drawCall.surfaceStyle.borderColor);
        Assert.assertEquals(cornerRadius, drawCall.surfaceStyle.cornerRadius);
    }

    static void assertContainsDrawCall(RecordingUiRenderContext renderContext, int left, int top, int right,
            int bottom, int fillColor, int borderColor, int cornerRadius) {
        for (DrawCall drawCall : renderContext.drawCalls) {
            if (drawCall.left == left && drawCall.top == top && drawCall.right == right && drawCall.bottom == bottom
                    && drawCall.surfaceStyle.fillColor == fillColor
                    && drawCall.surfaceStyle.borderColor == borderColor
                    && drawCall.surfaceStyle.cornerRadius == cornerRadius) {
                return;
            }
        }
        Assert.fail("未找到预期绘制调用");
    }

    static void assertTextCall(TextCall textCall, String text, int x, int y, int color, boolean shadow) {
        Assert.assertEquals(text, textCall.text);
        Assert.assertEquals(x, textCall.x);
        Assert.assertEquals(y, textCall.y);
        Assert.assertEquals(color, textCall.color);
        Assert.assertEquals(shadow, textCall.shadow);
    }

    static void assertElementUid(ElementNode expectedElement, ElementNode actualElement) {
        Assert.assertNotNull(actualElement);
        Assert.assertEquals(expectedElement.__getElementUid(), actualElement.__getElementUid());
    }

    static int[] findVisibleElementPoint(HtmlLikeDocumentWidget widget, ElementNode scrollHost, ElementNode target) {
        Assert.assertNotNull(target);
        for (int attempt = 0; attempt < 20; attempt++) {
            DocumentLayoutBox rootBox = widget.resolveLayoutBoxForTest();
            DocumentLayoutBox scrollHostBox = findLayoutBox(rootBox, scrollHost);
            DocumentLayoutBox targetBox = findLayoutBox(rootBox, target);
            Assert.assertNotNull(scrollHostBox);
            Assert.assertNotNull(targetBox);
            int scrollTop = widget.getScrollTop(scrollHost);
            int viewportLeft = widget.getAbsoluteX() + scrollHostBox.getContentLeft();
            int viewportTop = widget.getAbsoluteY() + scrollHostBox.getContentTop();
            int viewportRight = viewportLeft + scrollHostBox.getContentWidth();
            int viewportBottom = viewportTop + scrollHostBox.getContentHeight();
            int targetLeft = widget.getAbsoluteX() + targetBox.getContentLeft();
            int targetTop = widget.getAbsoluteY() + targetBox.getContentTop() - scrollTop;
            int targetRight = targetLeft + Math.max(1, targetBox.getContentWidth());
            int targetBottom = targetTop + Math.max(1, targetBox.getContentHeight());
            int left = Math.max(viewportLeft, targetLeft);
            int top = Math.max(viewportTop, targetTop);
            int right = Math.min(viewportRight, targetRight);
            int bottom = Math.min(viewportBottom, targetBottom);
            if (right > left && bottom > top) {
                return new int[] { left + Math.max(1, right - left) / 2, top + Math.max(1, bottom - top) / 2 };
            }
            int wheelDelta = targetTop >= viewportBottom ? -120 : 120;
            widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, viewportLeft + 4,
                    viewportTop + Math.max(1, scrollHostBox.getContentHeight()) / 2, -1, wheelDelta, 0, 0,
                    100L + attempt));
        }
        Assert.fail("目标元素未进入滚动视口");
        return new int[] { widget.getAbsoluteX(), widget.getAbsoluteY() };
    }

    static DocumentLayoutBox findLayoutBox(DocumentLayoutBox box, ElementNode element) {
        if (box.getElement() == element) {
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

    static ElementNode findElementContainingDirectText(HtmlLikeDocumentWidget widget, String expectedText) {
        return findElementContainingDirectText(widget.getDocument().getRootElement(), expectedText);
    }

    private static ElementNode findElementContainingDirectText(ElementNode element, String expectedText) {
        for (DocumentNode child : element.getChildren()) {
            if (child instanceof TextNode && expectedText.equals(((TextNode) child).getText())) {
                return element;
            }
            if (child instanceof ElementNode) {
                ElementNode found = findElementContainingDirectText((ElementNode) child, expectedText);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    static UiInputFrame mouseFrame(UiMouseEvent event) {
        return new UiInputFrame(event.getMouseX(), event.getMouseY(), Collections.singletonList(event),
                Collections.<UiKeyEvent>emptyList(), Collections.<UiTextInputEvent>emptyList());
    }

    static ElementNode createHudLikeCard(UiDocument document, int index) {
        ElementNode card = document.div();
        card.style()
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.percent(1.0F))
                .setPadding(UiStyleLength.px(8))
                .setMargin(UiStyleLength.px(6))
                .setBorderWidth(UiStyleLength.px(1));
        card.append(createTextBlock(document, "卡片 " + index + " 标题"));
        card.append(createTextBlock(document, "卡片 " + index + " 描述：用于构造 HUD 面板内部固定高度滚动区域。"));
        card.append(createTextBlock(document,
                "卡片 " + index + " 正文：这是一段较长的中文说明，用于确保在较窄 HUD 面板宽度下发生多行换行，"
                        + "并且总高度明显超过固定内容区视口。继续补充第二句说明，验证滚轮命中后代卡片正文时，祖先滚动宿主仍会移动。"));
        return card;
    }

    static ElementNode createTextBlock(UiDocument document, String text) {
        ElementNode block = document.div();
        block.style()
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.percent(1.0F));
        block.appendText(text);
        return block;
    }

    static ElementNode createAutoWidthTextBlock(UiDocument document, String text) {
        ElementNode block = document.div();
        block.style()
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.auto());
        block.appendText(text);
        return block;
    }

    static TextNode appendDynamicTextLine(UiDocument document, ElementNode parent, String text) {
        ElementNode line = document.div();
        line.style()
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.auto());
        TextNode textNode = line.appendText(text);
        parent.append(line);
        return textNode;
    }

    static void appendHudPanelWithTopCards(UiDocument document, ElementNode root, boolean disableShrink) {
        ElementNode panel = document.div();
        ElementNode heroCard = document.div();
        ElementNode controlCard = document.div();
        ElementNode scrollContent = document.div();

        panel.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(1648))
                .setTop(UiStyleLength.px(18))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setAlignItems(UiAlignItems.START)
                .setWidth(UiStyleLength.px(360))
                .setHeight(UiStyleLength.px(368))
                .setPadding(UiStyleLength.px(12))
                .setRowGap(UiStyleLength.px(8));
        heroCard.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setAlignItems(UiAlignItems.START)
                .setWidth(UiStyleLength.percent(1.0F))
                .setPadding(UiStyleLength.px(8))
                .setBorderWidth(UiStyleLength.px(1))
                .setRowGap(UiStyleLength.px(4));
        if (disableShrink) {
            heroCard.style().setFlexShrink(0.0F);
        }
        heroCard.append(createAutoWidthTextBlock(document, "INTERACTIVE HUD"));
        heroCard.append(createAutoWidthTextBlock(document,
                "把工具浮窗停在背包右上区域，用于核对 HUD 层可见性、输入接管与滚轮状态。继续补充第二句说明，确保顶部卡片出现明显换行。"));

        controlCard.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setAlignItems(UiAlignItems.START)
                .setWidth(UiStyleLength.percent(1.0F))
                .setPadding(UiStyleLength.px(8))
                .setBorderWidth(UiStyleLength.px(1))
                .setRowGap(UiStyleLength.px(6));
        if (disableShrink) {
            controlCard.style().setFlexShrink(0.0F);
        }
        controlCard.append(createAutoWidthTextBlock(document, "调试开关"));
        controlCard.append(createAutoWidthTextBlock(document, "底部提示标记：保留"));

        scrollContent.style()
                .setFlexGrow(1.0F)
                .setWidth(UiStyleLength.percent(1.0F))
                .setOverflowY(UiOverflow.AUTO);
        scrollContent.append(createTextBlock(document, "会话概览"));

        panel.append(heroCard).append(controlCard).append(scrollContent);
        root.append(panel);
    }

    /**
     * 记录 surface 绘制调用的渲染上下文。
     */
    static final class RecordingUiRenderContext extends UiRenderContext {

        final List<DrawCall> drawCalls = new ArrayList<DrawCall>();
        final List<TextCall> textCalls = new ArrayList<TextCall>();
        final List<HostImageCall> hostImageCalls = new ArrayList<HostImageCall>();
        final List<BackdropFilterCall> backdropFilterCalls = new ArrayList<BackdropFilterCall>();

        RecordingUiRenderContext() {
            super(320, 240, 0, 0, 0.0F);
        }

        @Override
        public void drawSurface(int left, int top, int right, int bottom, UiSurfaceStyle surfaceStyle) {
            drawCalls.add(new DrawCall(left, top, right, bottom, surfaceStyle));
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow) {
            drawText(text, x, y, color, shadow, TextContentMode.UILIB_RAW);
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow, TextContentMode textContentMode) {
            drawText(text, x, y, color, shadow, textContentMode, UiFontWeight.NORMAL, UiFontStyle.NORMAL);
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow, TextContentMode textContentMode,
                UiFontWeight fontWeight, UiFontStyle fontStyle) {
            textCalls.add(new TextCall(text, x, y, color, shadow, textContentMode, fontWeight, fontStyle,
                    TextMeasureStyle.DEFAULT_FONT_SIZE_PX));
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow, TextMeasureStyle textStyle) {
            TextMeasureStyle resolvedStyle = textStyle == null ? TextMeasureStyle.DEFAULT : textStyle;
            textCalls.add(new TextCall(text, x, y, color, shadow, resolvedStyle.getTextContentMode(),
                    resolvedStyle.getFontWeight(), resolvedStyle.getFontStyle(), resolvedStyle.getFontSizePx()));
        }

        @Override
        public int measureTextWidth(String text, TextContentMode textContentMode) {
            return text == null ? 0 : text.length() * 4;
        }

        @Override
        public int getTextLineHeight() {
            return 18;
        }

        @Override
        public void drawHostImage(HostImageSource source, int left, int top, int right, int bottom) {
            hostImageCalls.add(new HostImageCall(left, top, right, bottom));
        }

        @Override
        public boolean supportsDeferredTextBatching() {
            return false;
        }

        @Override
        public void drawBackdropFilter(int left, int top, int right, int bottom, int blurRadius, float saturation,
                int cornerRadius) {
            backdropFilterCalls.add(new BackdropFilterCall(left, top, right, bottom, blurRadius, saturation));
        }

        @Override
        public void drawBackdropFilter(int left, int top, int right, int bottom, int blurRadius, float saturation,
                UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii) {
            backdropFilterCalls.add(new BackdropFilterCall(left, top, right, bottom, blurRadius, saturation));
        }

        @Override
        public void pushClip(int left, int top, int right, int bottom, int cornerRadius) {}

        @Override
        public void pushClip(int left, int top, int right, int bottom,
                UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii) {}

        @Override
        public void popClip() {}

        // 录制上下文只记录逻辑坐标，不应用真实 GL 矩阵，故 transform 压栈/出栈在测试中为 no-op，
        // 避免 transform 命令回放触碰 LWJGL native（沙箱无 GL 上下文），使 widget 级 transform 端到端测试可运行。
        @Override
        public void pushTransform(UiTransform transform, int left, int top, int right, int bottom) {}

        @Override
        public void popTransform() {}
    }

    /**
     * 单次 surface 绘制记录。
     */
    static final class DrawCall {

        final int left;
        final int top;
        final int right;
        final int bottom;
        final UiSurfaceStyle surfaceStyle;

        DrawCall(int left, int top, int right, int bottom, UiSurfaceStyle surfaceStyle) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.surfaceStyle = surfaceStyle;
        }
    }

    /**
     * 单次文本绘制记录。
     */
    static final class TextCall {

        final String text;
        final int x;
        final int y;
        final int color;
        final boolean shadow;
        final TextContentMode textContentMode;
        final UiFontWeight fontWeight;
        final UiFontStyle fontStyle;
        final int fontSizePx;

        TextCall(String text, int x, int y, int color, boolean shadow, TextContentMode textContentMode,
                UiFontWeight fontWeight, UiFontStyle fontStyle, int fontSizePx) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
            this.shadow = shadow;
            this.textContentMode = textContentMode;
            this.fontWeight = fontWeight;
            this.fontStyle = fontStyle;
            this.fontSizePx = fontSizePx;
        }
    }

    static final class HostImageCall {

        final int left;
        final int top;
        final int right;
        final int bottom;

        HostImageCall(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }

    static final class BackdropFilterCall {

        final int left;
        final int top;
        final int right;
        final int bottom;
        final int blurRadius;
        final float saturation;

        BackdropFilterCall(int left, int top, int right, int bottom, int blurRadius, float saturation) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.blurRadius = blurRadius;
            this.saturation = saturation;
        }
    }

    /**
     * 供动画测试使用的手动时间源。
     */
    static final class ManualAnimationClock implements DocumentAnimationClock {

        private long currentTimeNanos;

        void setCurrentTimeNanos(long currentTimeNanos) {
            this.currentTimeNanos = currentTimeNanos;
        }

        @Override
        public long getCurrentTimeNanos() {
            return currentTimeNanos;
        }
    }

    /**
     * 记录测量次数的确定性文本测量服务。
     */
    static final class CountingTextMeasureService implements TextMeasureService {

        private int measureCount;
        private int epoch = 1;

        int getMeasureCount() {
            return measureCount;
        }

        void advanceEpoch() {
            epoch++;
        }

        @Override
        public int getEpoch() {
            return epoch;
        }

        @Override
        public int getStringWidth(String text) {
            measureCount++;
            return text == null ? 0 : text.length() * 4;
        }

        @Override
        public int getLineHeight() {
            return 9;
        }

        @Override
        public String trimStringToWidth(String text, int targetWidth) {
            measureCount++;
            if (text == null || text.isEmpty() || targetWidth <= 0) {
                return "";
            }
            int maxLength = Math.max(0, targetWidth / 4);
            return text.substring(0, Math.min(text.length(), maxLength));
        }

        @Override
        public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            measureCount++;
            if (text == null || text.isEmpty() || wrapWidth <= 0) {
                return Collections.emptyList();
            }
            List<String> lines = new ArrayList<String>();
            int maxCharsPerLine = Math.max(1, wrapWidth / 4);
            for (int index = 0; index < text.length(); index += maxCharsPerLine) {
                lines.add(text.substring(index, Math.min(text.length(), index + maxCharsPerLine)));
            }
            return lines;
        }
    }

    /**
     * 供 widget 测试使用的确定性文本测量服务。
     */
    static final class DeterministicTextMeasureService implements TextMeasureService {

        @Override
        public int getEpoch() {
            return 1;
        }

        @Override
        public int getStringWidth(String text) {
            return text == null ? 0 : text.length() * 4;
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
            int maxLength = Math.max(0, targetWidth / 4);
            return text.substring(0, Math.min(text.length(), maxLength));
        }

        @Override
        public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            if (text == null || text.isEmpty() || wrapWidth <= 0) {
                return Collections.emptyList();
            }
            List<String> lines = new ArrayList<String>();
            int maxCharsPerLine = Math.max(1, wrapWidth / 4);
            for (int index = 0; index < text.length(); index += maxCharsPerLine) {
                lines.add(text.substring(index, Math.min(text.length(), index + maxCharsPerLine)));
            }
            return lines;
        }
    }
}
