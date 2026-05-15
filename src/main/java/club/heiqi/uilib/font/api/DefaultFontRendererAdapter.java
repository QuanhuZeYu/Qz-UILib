package club.heiqi.uilib.font.api;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import club.heiqi.uilib.font.FontService;
import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.glyph.GlyphGenerationPriority;
import club.heiqi.uilib.font.glyph.GlyphGenerationTask;
import club.heiqi.uilib.font.glyph.GlyphInfo;
import club.heiqi.uilib.font.layout.TextLayoutService;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.font.page.GlyphPage;
import club.heiqi.uilib.font.page.GlyphPageManager;
import club.heiqi.uilib.font.page.GlyphPageSlot;
import club.heiqi.uilib.font.render.FontRenderFlushCoordinator;
import club.heiqi.uilib.font.render.FontRenderStateGuard;
import club.heiqi.uilib.ui.text.TextContentMode;

/**
 * 默认字体适配器。
 */
public class DefaultFontRendererAdapter implements FontRendererAdapter {

    private static final DefaultFontRendererAdapter INSTANCE = new DefaultFontRendererAdapter();
    private static final String RANDOM_SAMPLE = "ÀÁÂÈÊËÍÓÔÕÚßãõğİıŒœŞşŴŵžȇ!\"#$%&'()*+,-./0123456789:;<=>?"
            + "@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~"
            + "ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜø£Ø×ƒáíóúñÑªº¿®¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀"
            + "αβΓπΣσμτΦΘΩδ∞∅∈∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■";
    private final FontRenderStateGuard renderStateGuard = new FontRenderStateGuard();
    private final FontRenderFlushCoordinator flushCoordinator = new FontRenderFlushCoordinator();

    private DefaultFontRendererAdapter() {}

    /**
     * 获取默认字体适配器。
     *
     * @return 适配器实例
     */
    public static DefaultFontRendererAdapter getInstance() {
        return INSTANCE;
    }

    @Override
    public int drawString(String text, int x, int y, int color, boolean dropShadow) {
        return drawString(text, x, y, color, dropShadow, TextContentMode.MINECRAFT_FORMATTED);
    }

    /**
     * 使用指定文本模式绘制字符串。
     *
     * @param text 文本
     * @param x 横坐标
     * @param y 纵坐标
     * @param color 颜色
     * @param dropShadow 是否启用阴影
     * @param textContentMode 文本内容解析模式
     * @return 绘制结束后的光标位置
     */
    public int drawString(String text, int x, int y, int color, boolean dropShadow, TextContentMode textContentMode) {
        if (text == null || text.isEmpty()) {
            return x;
        }

        FontService fontService = FontService.getInstance();
        synchronized (fontService) {
            fontService.initialize();
            fontService.tickDrawStage(FontConfig.drawStageUploadBatchSize);

            int result = x;
            if (dropShadow) {
                result = drawInternal(fontService, text, x, y, normalizeColor(color), true, textContentMode);
            }
            result = drawInternal(fontService, text, x, y, normalizeColor(color), false, textContentMode);
            return result;
        }
    }

    @Override
    public int getStringWidth(String text) {
        return getStringWidth(text, TextContentMode.MINECRAFT_FORMATTED);
    }

    /**
     * 使用指定文本模式测量字符串宽度。
     *
     * @param text 文本
     * @param textContentMode 文本内容解析模式
     * @return 宽度
     */
    public int getStringWidth(String text, TextContentMode textContentMode) {
        FontService fontService = FontService.getInstance();
        synchronized (fontService) {
            fontService.initialize();
            return fontService.getTextLayoutService().getStringWidth(text, textContentMode);
        }
    }

    @Override
    public int getLineHeight() {
        FontService fontService = FontService.getInstance();
        synchronized (fontService) {
            fontService.initialize();
            return fontService.getTextLayoutService().getLineHeight();
        }
    }

    @Override
    public String trimStringToWidth(String text, int targetWidth) {
        return trimStringToWidth(text, targetWidth, TextContentMode.MINECRAFT_FORMATTED);
    }

    /**
     * 使用指定文本模式按宽度裁剪字符串。
     *
     * @param text 文本
     * @param targetWidth 目标宽度
     * @param textContentMode 文本内容解析模式
     * @return 裁剪结果
     */
    public String trimStringToWidth(String text, int targetWidth, TextContentMode textContentMode) {
        FontService fontService = FontService.getInstance();
        synchronized (fontService) {
            fontService.initialize();
            return fontService.getTextLayoutService().trimStringToWidth(text, targetWidth, textContentMode);
        }
    }

    public String trimStringToWidth(String text, int targetWidth, boolean reverse) {
        return trimStringToWidth(text, targetWidth, reverse, TextContentMode.MINECRAFT_FORMATTED);
    }

    /**
     * 使用指定文本模式按宽度裁剪字符串，可选从尾部保留。
     *
     * @param text 文本
     * @param targetWidth 目标宽度
     * @param reverse 是否从尾部保留
     * @param textContentMode 文本内容解析模式
     * @return 裁剪结果
     */
    public String trimStringToWidth(String text, int targetWidth, boolean reverse,
            TextContentMode textContentMode) {
        FontService fontService = FontService.getInstance();
        synchronized (fontService) {
            fontService.initialize();
            return fontService.getTextLayoutService().trimStringToWidth(text, targetWidth, reverse, textContentMode);
        }
    }

    @Override
    public String wrapFormattedStringToWidth(String text, int wrapWidth) {
        return wrapFormattedStringToWidth(text, wrapWidth, TextContentMode.MINECRAFT_FORMATTED);
    }

    /**
     * 使用指定文本模式按宽度插入换行。
     *
     * @param text 文本
     * @param wrapWidth 最大宽度
     * @param textContentMode 文本内容解析模式
     * @return 包含换行的新文本
     */
    public String wrapFormattedStringToWidth(String text, int wrapWidth, TextContentMode textContentMode) {
        FontService fontService = FontService.getInstance();
        synchronized (fontService) {
            fontService.initialize();
            return fontService.getTextLayoutService().wrapFormattedStringToWidth(text, wrapWidth, textContentMode);
        }
    }

    @Override
    public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
        return listFormattedStringToWidth(text, wrapWidth, TextContentMode.MINECRAFT_FORMATTED);
    }

    /**
     * 使用指定文本模式按宽度拆分文本。
     *
     * @param text 文本
     * @param wrapWidth 最大宽度
     * @param textContentMode 文本内容解析模式
     * @return 拆分结果
     */
    public List<String> listFormattedStringToWidth(String text, int wrapWidth, TextContentMode textContentMode) {
        FontService fontService = FontService.getInstance();
        synchronized (fontService) {
            fontService.initialize();
            return fontService.getTextLayoutService().listFormattedStringToWidth(text, wrapWidth, textContentMode);
        }
    }

    @Override
    public int splitStringWidth(String text, int wrapWidth) {
        return splitStringWidth(text, wrapWidth, TextContentMode.MINECRAFT_FORMATTED);
    }

    /**
     * 使用指定文本模式计算拆行高度。
     *
     * @param text 文本
     * @param wrapWidth 最大宽度
     * @param textContentMode 文本内容解析模式
     * @return 高度
     */
    public int splitStringWidth(String text, int wrapWidth, TextContentMode textContentMode) {
        FontService fontService = FontService.getInstance();
        synchronized (fontService) {
            fontService.initialize();
            return fontService.getTextLayoutService().splitStringWidth(text, wrapWidth, textContentMode);
        }
    }

    /**
     * 绘制多行文本。
     *
     * @param text 文本
     * @param x 起始 X
     * @param y 起始 Y
     * @param wrapWidth 最大宽度
     * @param color 颜色
     */
    public void drawSplitString(String text, int x, int y, int wrapWidth, int color) {
        drawSplitString(text, x, y, wrapWidth, color, TextContentMode.MINECRAFT_FORMATTED);
    }

    /**
     * 使用指定文本模式绘制多行文本。
     *
     * @param text 文本
     * @param x 起始 X
     * @param y 起始 Y
     * @param wrapWidth 最大宽度
     * @param color 颜色
     * @param textContentMode 文本内容解析模式
     */
    public void drawSplitString(String text, int x, int y, int wrapWidth, int color, TextContentMode textContentMode) {
        List<String> lines = listFormattedStringToWidth(text, wrapWidth, textContentMode);
        int lineHeight = getLineHeight();
        for (String line : lines) {
            drawString(line, x, y, color, false, textContentMode);
            y += lineHeight;
        }
    }

    private int drawInternal(FontService fontService, String text, int x, int y, int color, boolean shadow,
            TextContentMode textContentMode) {
        TextLayoutService textLayoutService = fontService.getTextLayoutService();
        GlyphPageManager glyphPageManager = fontService.getGlyphPageManager();
        List<TextSegment> segments = textLayoutService.layoutSegments(text, color, textContentMode);
        if (segments.isEmpty()) {
            return x;
        }

        float currentX = x + (shadow ? (float) FontConfig.shadowOffsetX : 0.0F);
        float drawY = y + (shadow ? (float) FontConfig.shadowOffsetY : 0.0F);
        for (TextSegment segment : segments) {
            TextStyle style = segment.getStyle();
            String segmentText = segment.getText();
            for (int i = 0; i < segmentText.length();) {
                int codepoint = segmentText.codePointAt(i);
                int renderCodepoint = style.isRandomStyle() ? resolveRandomStyleCodepoint(codepoint, style, textLayoutService) : codepoint;
                FontType fontType = style.getFontType();
                GlyphPage glyphPage = glyphPageManager.getReadyPage(renderCodepoint, fontType);
                GlyphPageSlot slot = glyphPage == null
                        ? null
                        : glyphPage.getSlotMap().get(glyphPageManager.createKey(renderCodepoint, fontType));
                float measuredWidth = (float) textLayoutService.getCodepointWidth(codepoint, style);

                if (glyphPage == null || slot == null) {
                    fontService.getGlyphGenerationDispatcher().submit(new GlyphGenerationTask(
                            fontService.getRuntimeVersion(),
                            renderCodepoint,
                            fontType,
                            Math.max(8, (int) Math.ceil(FontConfig.awtCharSize)),
                            GlyphGenerationPriority.HIGH));
                    collectDecorations(fontService, currentX, drawY, measuredWidth, style, shadow ? darkenShadow(style.getColor()) : style.getColor());
                    currentX += measuredWidth;
                } else {
                    int renderColor = shadow ? darkenShadow(style.getColor()) : style.getColor();
                    GlyphInfo glyphInfo = glyphPageManager.getGlyphInfo(renderCodepoint, fontType);
                    fontService.getBatchRenderer().collect(glyphPage, slot, currentX, drawY,
                            (float) FontConfig.charSize, renderColor, style.isItalic(), glyphInfo);
                    collectDecorations(fontService, currentX, drawY, measuredWidth, style, renderColor);
                    currentX += measuredWidth;
                }

                i += Character.charCount(codepoint);
            }
        }
        flushCoordinator.flush(renderStateGuard, new Runnable() {
            @Override
            public void run() {
                fontService.getBatchRenderer().flush(fontService.getShaderProgram());
            }
        }, new Runnable() {
            @Override
            public void run() {
                fontService.getDecorationRenderer().flush();
            }
        });
        return (int) Math.ceil(currentX);
    }

    private int resolveRandomStyleCodepoint(int originalCodepoint, TextStyle style, TextLayoutService textLayoutService) {
        double originalWidth = textLayoutService.getCodepointWidth(originalCodepoint, style);
        int fallbackCodepoint = originalCodepoint;
        double bestDifference = Double.MAX_VALUE;

        for (int i = 0; i < 16; i++) {
            int randomIndex = ThreadLocalRandom.current().nextInt(RANDOM_SAMPLE.length());
            int candidateCodepoint = RANDOM_SAMPLE.codePointAt(randomIndex);
            double candidateWidth = textLayoutService.getCodepointWidth(candidateCodepoint, style);
            double difference = Math.abs(candidateWidth - originalWidth);
            if (difference < 0.05D) {
                return candidateCodepoint;
            }
            if (difference < bestDifference) {
                bestDifference = difference;
                fallbackCodepoint = candidateCodepoint;
            }
        }
        return fallbackCodepoint;
    }

    private void collectDecorations(FontService fontService, float currentX, float drawY, float width, TextStyle style, int color) {
        if (style.isUnderline()) {
            fontService.getDecorationRenderer().collect(currentX, drawY + (float) FontConfig.charSize - 1.0F, width, 1.0F, color);
        }
        if (style.isStrikethrough()) {
            fontService.getDecorationRenderer().collect(currentX, drawY + ((float) FontConfig.charSize / 2.0F) - 0.5F, width, 1.0F, color);
        }
    }

    private int normalizeColor(int color) {
        if ((color & 0xFC000000) == 0) {
            return color | 0xFF000000;
        }
        return color;
    }

    private int darkenShadow(int color) {
        int normalized = normalizeColor(color);
        return (normalized & 0xFCFCFC) >> 2 | normalized & 0xFF000000;
    }

}
