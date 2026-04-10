package club.heiqi.uilib.ui.control;

import java.util.List;

import club.heiqi.uilib.font.api.DefaultFontRendererAdapter;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 最小标签控件。
 */
public class LabelWidget extends Widget {

    private String text;
    private int color = 0xFFFFFFFF;
    private boolean shadow = true;
    private boolean wrap;
    private boolean ellipsis;
    private int maxLines = Integer.MAX_VALUE;

    /**
     * 使用文本创建标签。
     *
     * @param text 文本内容
     */
    public LabelWidget(String text) {
        this.text = text;
    }

    @Override
    protected void drawSelf(UiRenderContext context) {
        if (text == null || text.isEmpty()) {
            return;
        }

        int absoluteX = getAbsoluteX();
        int absoluteY = getAbsoluteY();
        int availableWidth = Math.max(1, getWidth());
        int lineHeight = context.getTextLineHeight();

        if (wrap) {
            List<String> lines = wrapToUiWidth(availableWidth);
            int lineCount = Math.min(lines.size(), maxLines);
            for (int i = 0; i < lineCount; i++) {
                String line = lines.get(i);
                if (ellipsis && i == lineCount - 1 && lines.size() > maxLines) {
                    line = trimToUiWidth(line, availableWidth, true);
                }
                context.drawText(line, absoluteX, absoluteY + i * lineHeight, color, shadow);
            }
            return;
        }

        String displayText = trimToUiWidth(text, availableWidth, ellipsis);
        context.drawText(displayText, absoluteX, absoluteY, color, shadow);
    }

    @Override
    public int getPreferredWidth() {
        return text == null ? 0 : DefaultFontRendererAdapter.getInstance().getStringWidth(text) * 2;
    }

    @Override
    public int getPreferredHeight() {
        return Math.max(20, ((int) Math.ceil(FontConfig.charSize) + 2) * 2);
    }

    @Override
    public int getPreferredHeightForWidth(int width) {
        if (!wrap || text == null || text.isEmpty()) {
            return getPreferredHeight();
        }
        int lineCount = Math.max(1, Math.min(wrapToUiWidth(Math.max(1, width)).size(), maxLines));
        return lineCount * getPreferredHeight();
    }

    @Override
    public int getMinContentWidth() {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        if (!wrap) {
            return getPreferredWidth();
        }

        DefaultFontRendererAdapter adapter = DefaultFontRendererAdapter.getInstance();
        int widest = 0;
        StringBuilder segmentBuilder = new StringBuilder();
        for (int index = 0; index < text.length();) {
            int codepoint = text.codePointAt(index);
            if (Character.isWhitespace(codepoint)) {
                widest = Math.max(widest, adapter.getStringWidth(segmentBuilder.toString()) * 2);
                segmentBuilder.setLength(0);
            } else {
                segmentBuilder.appendCodePoint(codepoint);
            }
            index += Character.charCount(codepoint);
        }
        widest = Math.max(widest, adapter.getStringWidth(segmentBuilder.toString()) * 2);
        return widest;
    }

    public String getText() {
        return text;
    }

    public LabelWidget setText(String text) {
        this.text = text;
        return this;
    }

    public LabelWidget setColor(int color) {
        this.color = color;
        return this;
    }

    public LabelWidget setShadow(boolean shadow) {
        this.shadow = shadow;
        return this;
    }

    public LabelWidget setWrap(boolean wrap) {
        this.wrap = wrap;
        return this;
    }

    public LabelWidget setEllipsis(boolean ellipsis) {
        this.ellipsis = ellipsis;
        return this;
    }

    public LabelWidget setMaxLines(int maxLines) {
        this.maxLines = Math.max(1, maxLines);
        return this;
    }

    private List<String> wrapToUiWidth(int uiWidth) {
        int rawWidth = toRawTextWidth(uiWidth);
        return DefaultFontRendererAdapter.getInstance().listFormattedStringToWidth(text, rawWidth);
    }

    private String trimToUiWidth(String source, int uiWidth, boolean appendEllipsis) {
        if (source == null || source.isEmpty()) {
            return "";
        }
        DefaultFontRendererAdapter adapter = DefaultFontRendererAdapter.getInstance();
        int rawWidth = toRawTextWidth(uiWidth);
        if (adapter.getStringWidth(source) <= rawWidth) {
            return source;
        }
        if (!appendEllipsis) {
            return adapter.trimStringToWidth(source, rawWidth);
        }
        int ellipsisWidth = adapter.getStringWidth("...");
        String trimmed = adapter.trimStringToWidth(source, Math.max(0, rawWidth - ellipsisWidth));
        return trimmed.isEmpty() ? "..." : trimmed + "...";
    }

    private int toRawTextWidth(int uiWidth) {
        return Math.max(1, Math.round(uiWidth / 2.0F));
    }
}
