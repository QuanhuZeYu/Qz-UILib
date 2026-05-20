package club.heiqi.uilib.ui.control;

import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.style.cascade.UiBorderRadiusResolver;
import club.heiqi.uilib.ui.style.props.UiFontStyle;
import club.heiqi.uilib.ui.style.props.UiFontWeight;
import club.heiqi.uilib.ui.text.TextContentMode;
import club.heiqi.uilib.ui.style.values.UiSurfaceStyle;

/**
 * 控件测试使用的确定性渲染上下文。
 */
final class ControlTestRenderContext extends UiRenderContext {

    ControlTestRenderContext(int screenWidth, int screenHeight) {
        super(screenWidth, screenHeight, 0, 0, 0.0F);
    }

    @Override
    public void fillRect(int left, int top, int right, int bottom, int color) {}

    @Override
    public void drawSurface(int left, int top, int right, int bottom, UiSurfaceStyle surfaceStyle) {}

    @Override
    public void drawText(String text, int x, int y, int color, boolean shadow, TextContentMode textContentMode,
            UiFontWeight fontWeight, UiFontStyle fontStyle) {}

    @Override
    public int measureTextWidth(String text, TextContentMode textContentMode) {
        return text == null ? 0 : text.length() * 6;
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
}
