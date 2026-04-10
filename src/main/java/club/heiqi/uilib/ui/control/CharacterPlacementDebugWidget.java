package club.heiqi.uilib.ui.control;

import club.heiqi.uilib.font.api.FontRendererAdapter;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 调试字符在相对布局容器中的实际摆放位置。
 */
public class CharacterPlacementDebugWidget extends Widget {

    private String topLeftText = "左上 A 文 😀";
    private String topRightText = "右上 Emoji 😀";
    private String bottomLeftText = "左下 边界 gjpqy";
    private String bottomRightText = "右下 混排 §l粗体§r 😎";
    private String centerText = "中心线: UI 字形边界 / 中文 / Emoji 😀✨";

    @Override
    protected void drawSelf(UiRenderContext context) {
        int absoluteX = getAbsoluteX();
        int absoluteY = getAbsoluteY();
        int right = absoluteX + getWidth();
        int bottom = absoluteY + getHeight();
        int centerX = absoluteX + (getWidth() / 2);
        int centerY = absoluteY + (getHeight() / 2);

        FontRendererAdapter fontRenderer = context.getFontRenderer();
        context.fillRect(absoluteX, absoluteY, right, bottom, 0xAA10151C);
        context.drawBorder(absoluteX, absoluteY, right, bottom, 0xFF89AFFF);
        context.fillRect(centerX, absoluteY + 1, centerX + 1, bottom - 1, 0x55FFFFFF);
        context.fillRect(absoluteX + 1, centerY, right - 1, centerY + 1, 0x55FFFFFF);

        drawAnchoredText(context, fontRenderer, topLeftText, absoluteX + 6, absoluteY + 6, Anchor.LEFT_TOP, 0xFFFFFFFF);
        drawAnchoredText(context, fontRenderer, topRightText, right - 6, absoluteY + 6, Anchor.RIGHT_TOP, 0xFFF6D78E);
        drawAnchoredText(context, fontRenderer, bottomLeftText, absoluteX + 6, bottom - 6, Anchor.LEFT_BOTTOM, 0xFFD7E3FF);
        drawAnchoredText(context, fontRenderer, bottomRightText, right - 6, bottom - 6, Anchor.RIGHT_BOTTOM, 0xFFC8F0C8);
        drawAnchoredText(context, fontRenderer, centerText, centerX, centerY, Anchor.CENTER, 0xFFFFD7FF);
    }

    private void drawAnchoredText(UiRenderContext context, FontRendererAdapter fontRenderer, String text, int anchorX,
            int anchorY, Anchor anchor, int color) {
        int textWidth = context.measureTextWidth(text);
        int textHeight = context.getTextLineHeight();
        int drawX = anchorX;
        int drawY = anchorY;

        switch (anchor) {
            case RIGHT_TOP:
                drawX = anchorX - textWidth;
                break;
            case LEFT_BOTTOM:
                drawY = anchorY - textHeight;
                break;
            case RIGHT_BOTTOM:
                drawX = anchorX - textWidth;
                drawY = anchorY - textHeight;
                break;
            case CENTER:
                drawX = anchorX - (textWidth / 2);
                drawY = anchorY - (textHeight / 2);
                break;
            default:
                break;
        }

        context.drawBorder(drawX - 1, drawY - 1, drawX + textWidth + 1, drawY + textHeight + 1, 0x66FFFFFF);
        context.drawText(text, drawX, drawY, color, true);
    }

    public CharacterPlacementDebugWidget setTopLeftText(String topLeftText) {
        this.topLeftText = topLeftText;
        return this;
    }

    public CharacterPlacementDebugWidget setTopRightText(String topRightText) {
        this.topRightText = topRightText;
        return this;
    }

    public CharacterPlacementDebugWidget setBottomLeftText(String bottomLeftText) {
        this.bottomLeftText = bottomLeftText;
        return this;
    }

    public CharacterPlacementDebugWidget setBottomRightText(String bottomRightText) {
        this.bottomRightText = bottomRightText;
        return this;
    }

    public CharacterPlacementDebugWidget setCenterText(String centerText) {
        this.centerText = centerText;
        return this;
    }

    private enum Anchor {
        LEFT_TOP,
        RIGHT_TOP,
        LEFT_BOTTOM,
        RIGHT_BOTTOM,
        CENTER
    }

    @Override
    public int getPreferredWidth() {
        return 680;
    }

    @Override
    public int getPreferredHeight() {
        return 360;
    }
}
