package club.heiqi.uilib.ui.control;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiTextInputEvent;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * `TextInputWidget` 的基础行为与样式契约测试。
 */
public class TextInputWidgetTest {

    /**
     * 验证文本输入框会按样式参数绘制顶部 accent 条。
     */
    @Test
    public void shouldRenderAccentStripUsingConfiguredInset() {
        TextInputWidget widget = new TextInputWidget(UiControlTheme.defaultTextInputStyle(),
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(10, 20, 140, 38);

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        widget.render(renderContext);

        UiControlTheme.TextInputStyle style = UiControlTheme.defaultTextInputStyle();
        UiControlTheme.BoxState expectedState = style.normalState;
        Assert.assertEquals(2, renderContext.fillRects.size());

        RecordedFillRect backgroundRect = renderContext.fillRects.get(0);
        Assert.assertEquals(10, backgroundRect.left);
        Assert.assertEquals(20, backgroundRect.top);
        Assert.assertEquals(150, backgroundRect.right);
        Assert.assertEquals(58, backgroundRect.bottom);
        Assert.assertEquals(expectedState.fillColor, backgroundRect.color);

        RecordedFillRect accentRect = renderContext.fillRects.get(1);
        Assert.assertEquals(11, accentRect.left);
        Assert.assertEquals(21, accentRect.top);
        Assert.assertEquals(149, accentRect.right);
        Assert.assertEquals(22, accentRect.bottom);
        Assert.assertEquals(expectedState.accentColor, accentRect.color);
    }

    /**
     * 验证 accent 高度为 0 时不会额外绘制顶部强调条。
     */
    @Test
    public void shouldSkipAccentStripWhenAccentHeightIsZero() {
        UiControlTheme.TextInputStyle baseStyle = UiControlTheme.defaultTextInputStyle();
        UiControlTheme.TextInputStyle styleWithoutAccent = new UiControlTheme.TextInputStyle(
                baseStyle.normalState,
                baseStyle.hoveredState,
                baseStyle.focusedState,
                baseStyle.textColor,
                baseStyle.placeholderColor,
                baseStyle.caretColor,
                baseStyle.preferredMinWidth,
                baseStyle.minContentWidthFloor,
                baseStyle.preferredExtraWidth,
                baseStyle.height,
                baseStyle.textHorizontalPadding,
                baseStyle.accentInsetTop,
                0,
                baseStyle.caretWidth,
                baseStyle.caretRightInset,
                baseStyle.caretVerticalInset);
        TextInputWidget widget = new TextInputWidget(styleWithoutAccent, new DeterministicTextMeasureService());
        widget.applyLayoutBounds(10, 20, 140, 38);

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        widget.render(renderContext);

        Assert.assertEquals(1, renderContext.fillRects.size());
        Assert.assertEquals(styleWithoutAccent.normalState.fillColor, renderContext.fillRects.get(0).color);
    }

    /**
     * 验证聚焦态输入会过滤控制字符、遵守最大长度，并支持退格删除。
     */
    @Test
    public void shouldAcceptPrintableCharactersRespectMaxLengthAndHandleBackspace() {
        TextInputWidget widget = new TextInputWidget(UiControlTheme.defaultTextInputStyle(),
                new DeterministicTextMeasureService());
        widget.setMaxLength(3);
        widget.onFocusChanged(true);

        widget.onTextInput(new UiTextInputEvent("A\nB\t中CD", 1L));
        Assert.assertEquals("AB中", widget.getText());

        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_BACK, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 2L));
        Assert.assertEquals("AB", widget.getText());
    }

    /**
     * 记录矩形填充调用的渲染上下文。
     */
    private static final class RecordingUiRenderContext extends UiRenderContext {

        private final List<RecordedFillRect> fillRects = new ArrayList<RecordedFillRect>();

        private RecordingUiRenderContext() {
            super(320, 240, 0, 0, 0.0F);
        }

        @Override
        public void fillRect(int left, int top, int right, int bottom, int color) {
            fillRects.add(new RecordedFillRect(left, top, right, bottom, color));
        }

        @Override
        public void drawBorder(int left, int top, int right, int bottom, int color) {}

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow) {}

        @Override
        public int measureTextWidth(String text) {
            return text == null ? 0 : text.length() * 12;
        }

        @Override
        public int getTextLineHeight() {
            return 18;
        }
    }

    private static final class RecordedFillRect {

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final int color;

        private RecordedFillRect(int left, int top, int right, int bottom, int color) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.color = color;
        }
    }

    /**
     * 供测试使用的确定性文本测量桩。
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
            if (text == null || text.isEmpty() || targetWidth <= 0) {
                return "";
            }
            int maxChars = Math.max(1, targetWidth / 6);
            return text.length() <= maxChars ? text : text.substring(0, maxChars);
        }

        @Override
        public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            return java.util.Collections.singletonList(text == null ? "" : text);
        }
    }
}
