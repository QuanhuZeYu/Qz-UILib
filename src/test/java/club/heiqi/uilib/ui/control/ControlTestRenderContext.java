package club.heiqi.uilib.ui.control;

import java.util.ArrayList;
import java.util.List;

import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.style.cascade.UiBorderRadiusResolver;
import club.heiqi.uilib.ui.style.props.UiFontStyle;
import club.heiqi.uilib.ui.style.props.UiFontWeight;
import club.heiqi.uilib.ui.text.TextContentMode;
import club.heiqi.uilib.ui.text.TextMeasureStyle;
import club.heiqi.uilib.ui.style.values.UiSurfaceStyle;

/**
 * 控件测试使用的确定性渲染上下文。
 */
final class ControlTestRenderContext extends UiRenderContext {

    final List<TextCall> textCalls = new ArrayList<TextCall>();
    final List<FillRectCall> fillRectCalls = new ArrayList<FillRectCall>();

    ControlTestRenderContext(int screenWidth, int screenHeight) {
        super(screenWidth, screenHeight, 0, 0, 0.0F);
    }

    @Override
    public void fillRect(int left, int top, int right, int bottom, int color) {
        fillRectCalls.add(new FillRectCall(left, top, right, bottom, color));
    }

    @Override
    public void drawSurface(int left, int top, int right, int bottom, UiSurfaceStyle surfaceStyle) {}

    @Override
    public void drawText(String text, int x, int y, int color, boolean shadow, TextContentMode textContentMode,
            UiFontWeight fontWeight, UiFontStyle fontStyle) {
        textCalls.add(new TextCall(text, x, y, color, shadow, textContentMode, fontWeight, fontStyle));
    }

    @Override
    protected void drawTextResolved(String text, int x, int y, int color, boolean shadow,
            TextMeasureStyle resolvedStyle) {
        TextMeasureStyle safeStyle = resolvedStyle == null ? TextMeasureStyle.DEFAULT : resolvedStyle;
        textCalls.add(new TextCall(text, x, y, color, shadow, safeStyle.getTextContentMode(),
                safeStyle.getFontWeight(), safeStyle.getFontStyle()));
    }

    @Override
    protected void drawTextResolved(String text, int x, int y, int color, boolean shadow,
            TextContentMode textContentMode, UiFontWeight resolvedFontWeight, UiFontStyle resolvedFontStyle) {
        textCalls.add(new TextCall(text, x, y, color, shadow, textContentMode, resolvedFontWeight, resolvedFontStyle));
    }

    @Override
    public int measureTextWidth(String text, TextContentMode textContentMode) {
        return text == null ? 0 : text.length() * 12;
    }

    @Override
    public int getTextLineHeight() {
        return 9;
    }

    @Override
    public boolean supportsDeferredTextBatching() {
        return false;
    }

    @Override
    public void drawBackdropFilter(int left, int top, int right, int bottom, int blurRadius, float saturation,
            int cornerRadius) {}

    @Override
    public void drawBackdropFilter(int left, int top, int right, int bottom, int blurRadius, float saturation,
            UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii) {}

    @Override
    public void pushClip(int left, int top, int right, int bottom, int cornerRadius) {}

    @Override
    public void pushClip(int left, int top, int right, int bottom,
            UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii) {}

    @Override
    public void popClip() {}

    static final class TextCall {

        final String text;
        final int x;
        final int y;
        final int color;
        final boolean shadow;
        final TextContentMode textContentMode;
        final UiFontWeight fontWeight;
        final UiFontStyle fontStyle;

        private TextCall(String text, int x, int y, int color, boolean shadow, TextContentMode textContentMode,
                UiFontWeight fontWeight, UiFontStyle fontStyle) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
            this.shadow = shadow;
            this.textContentMode = textContentMode;
            this.fontWeight = fontWeight;
            this.fontStyle = fontStyle;
        }
    }

    static final class FillRectCall {

        final int left;
        final int top;
        final int right;
        final int bottom;
        final int color;

        private FillRectCall(int left, int top, int right, int bottom, int color) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.color = color;
        }
    }
}
