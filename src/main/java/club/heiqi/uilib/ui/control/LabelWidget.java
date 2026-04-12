package club.heiqi.uilib.ui.control;

import java.util.List;

import club.heiqi.uilib.font.api.DefaultFontRendererAdapter;
import club.heiqi.uilib.font.FontService;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 最小标签控件。
 */
public class LabelWidget extends Widget {

    private String text;
    private int color = UiControlTheme.defaultLabelStyle().textColor;
    private boolean shadow = UiControlTheme.defaultLabelStyle().shadow;
    private boolean wrap;
    private boolean ellipsis;
    private int maxLines = Integer.MAX_VALUE;
    private int cachedPreferredWidth = -1;
    private int cachedMinContentWidth = -1;
    private int cachedWrapWidth = -1;
    private String cachedWrapText;
    private List<String> cachedWrappedLines;
    private int cachedPreferredWidthFontRuntimeVersion = -1;
    private int cachedMinContentWidthFontRuntimeVersion = -1;
    private int cachedWrapFontRuntimeVersion = -1;

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
            List<String> lines = getWrappedLines(availableWidth);
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
        if (text == null || text.isEmpty()) {
            return 0;
        }
        if (cachedPreferredWidth < 0
                || cachedPreferredWidthFontRuntimeVersion != FontService.getInstance().getRuntimeVersion()) {
            cachedPreferredWidth = DefaultFontRendererAdapter.getInstance().getStringWidth(text) * 2;
            cachedPreferredWidthFontRuntimeVersion = FontService.getInstance().getRuntimeVersion();
        }
        return cachedPreferredWidth;
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
        int lineCount = Math.max(1, Math.min(getWrappedLines(Math.max(1, width)).size(), maxLines));
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

        // 当前文本布局服务按字符粒度换行，中文与大多数符号都可以在单字符处断行，
        // 因此最小内容宽度不应再按空白分词，否则中文整段会被误判为不可压缩长词。
        if (cachedMinContentWidth < 0
                || cachedMinContentWidthFontRuntimeVersion != FontService.getInstance().getRuntimeVersion()) {
            cachedMinContentWidth = resolveWrapMinWidth();
            cachedMinContentWidthFontRuntimeVersion = FontService.getInstance().getRuntimeVersion();
        }
        return cachedMinContentWidth;
    }

    public String getText() {
        return text;
    }

    public LabelWidget setText(String text) {
        if (this.text == text || this.text != null && this.text.equals(text)) {
            return this;
        }

        int currentWidth = getWidth();
        int oldPreferredWidth = getPreferredWidth();
        int oldMinContentWidth = getMinContentWidth();
        int oldHeightAtCurrentWidth = currentWidth > 0 ? getPreferredHeightForWidth(currentWidth) : -1;

        this.text = text;
        invalidateTextCache();

        int newPreferredWidth = getPreferredWidth();
        int newMinContentWidth = getMinContentWidth();
        int newHeightAtCurrentWidth = currentWidth > 0 ? getPreferredHeightForWidth(currentWidth) : -1;
        if (currentWidth <= 0
                || oldPreferredWidth != newPreferredWidth
                || oldMinContentWidth != newMinContentWidth
                || oldHeightAtCurrentWidth != newHeightAtCurrentWidth) {
            requestLayout();
        }
        return this;
    }

    public LabelWidget setColor(int color) {
        this.color = color;
        return this;
    }

    /**
     * 设置标签样式。
     *
     * @param style 标签样式；为空时恢复默认样式
     * @return 当前标签
     */
    public LabelWidget setStyle(UiControlTheme.LabelStyle style) {
        UiControlTheme.LabelStyle effectiveStyle = style == null ? UiControlTheme.defaultLabelStyle() : style;
        color = effectiveStyle.textColor;
        shadow = effectiveStyle.shadow;
        return this;
    }

    public LabelWidget setShadow(boolean shadow) {
        this.shadow = shadow;
        return this;
    }

    public LabelWidget setWrap(boolean wrap) {
        this.wrap = wrap;
        requestLayout();
        return this;
    }

    public LabelWidget setEllipsis(boolean ellipsis) {
        this.ellipsis = ellipsis;
        return this;
    }

    public LabelWidget setMaxLines(int maxLines) {
        this.maxLines = Math.max(1, maxLines);
        requestLayout();
        return this;
    }

    private List<String> getWrappedLines(int uiWidth) {
        if (cachedWrappedLines != null && cachedWrapWidth == uiWidth
                && cachedWrapFontRuntimeVersion == FontService.getInstance().getRuntimeVersion()
                && (cachedWrapText == text || cachedWrapText != null && cachedWrapText.equals(text))) {
            return cachedWrappedLines;
        }

        int rawWidth = toRawTextWidth(uiWidth);
        cachedWrapWidth = uiWidth;
        cachedWrapText = text;
        cachedWrappedLines = DefaultFontRendererAdapter.getInstance().listFormattedStringToWidth(text, rawWidth);
        cachedWrapFontRuntimeVersion = FontService.getInstance().getRuntimeVersion();
        return cachedWrappedLines;
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

    private int resolveWrapMinWidth() {
        DefaultFontRendererAdapter adapter = DefaultFontRendererAdapter.getInstance();
        int widest = 0;
        for (int index = 0; index < text.length();) {
            int codepoint = text.codePointAt(index);
            if (codepoint == '§' && index < text.length() - 1) {
                index += 2;
                continue;
            }
            if (!Character.isWhitespace(codepoint)) {
                widest = Math.max(widest, adapter.getStringWidth(new String(Character.toChars(codepoint))) * 2);
            }
            index += Character.charCount(codepoint);
        }
        return Math.max(widest, adapter.getStringWidth("字") * 2);
    }

    private void invalidateTextCache() {
        cachedPreferredWidth = -1;
        cachedPreferredWidthFontRuntimeVersion = -1;
        cachedMinContentWidth = -1;
        cachedMinContentWidthFontRuntimeVersion = -1;
        cachedWrapWidth = -1;
        cachedWrapText = null;
        cachedWrappedLines = null;
        cachedWrapFontRuntimeVersion = -1;
    }
}
