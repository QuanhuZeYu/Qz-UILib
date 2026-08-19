package club.heiqi.uilib.font.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 内容模式 trim/wrap 策略（package 内部）：每种 {@code TextContentMode} 的裁剪/换行独立实现，
 * {@link TextLayoutService} 只做模式分派。新增内容模式时只需新增一个策略实现。
 */
interface TextContentModeStrategy {

    /**
     * 正向裁剪（保留前缀）。
     *
     * @param text           文本
     * @param targetWidth    目标宽度
     * @param baseStyle      基准样式
     * @param baseFontSizePx 基准字号（RAW/MINECRAFT 直接作测量字号，RICH 作未显式字号段落的基准）
     * @return 裁剪结果
     */
    String trim(String text, int targetWidth, TextStyle baseStyle, int baseFontSizePx);

    /**
     * 按宽度插入换行符。
     *
     * @param text           文本
     * @param wrapWidth      换行宽度
     * @param baseStyle      基准样式
     * @param baseFontSizePx 基准字号（语义同 {@link #trim}）
     * @return 含换行符的文本
     */
    String wrap(String text, int wrapWidth, TextStyle baseStyle, int baseFontSizePx);
}

/** UILIB_RAW 模式：无格式码，逐码点测量。 */
final class RawTextContentStrategy implements TextContentModeStrategy {

    private final TextLayoutService service;

    RawTextContentStrategy(TextLayoutService service) {
        this.service = service;
    }

    @Override
    public String trim(String text, int targetWidth, TextStyle baseStyle, int baseFontSizePx) {
        StringBuilder builder = new StringBuilder();
        double width = 0.0D;
        for (int i = 0; i < text.length(); ) {
            int codepoint = text.codePointAt(i);
            double charWidth = service.getCodepointWidth(codepoint, baseStyle, baseFontSizePx);
            if (width + charWidth > targetWidth) {
                break;
            }
            width += charWidth;
            builder.appendCodePoint(codepoint);
            i += Character.charCount(codepoint);
        }
        return builder.toString();
    }

    @Override
    public String wrap(String text, int wrapWidth, TextStyle baseStyle, int baseFontSizePx) {
        StringBuilder builder = new StringBuilder();
        double width = 0.0D;
        boolean lineHasVisibleContent = false;
        for (int i = 0; i < text.length(); ) {
            int codepoint = text.codePointAt(i);
            if (codepoint == '\r' || codepoint == '\n') {
                i += Character.charCount(codepoint);
                if (codepoint == '\r' && i < text.length() && text.charAt(i) == '\n') {
                    i++;
                }
                builder.append('\n');
                width = 0.0D;
                lineHasVisibleContent = false;
                continue;
            }

            double charWidth = service.getCodepointWidth(codepoint, baseStyle);
            if (width + charWidth > wrapWidth && lineHasVisibleContent) {
                builder.append('\n');
                width = 0.0D;
                lineHasVisibleContent = false;
            }
            builder.appendCodePoint(codepoint);
            width += charWidth;
            lineHasVisibleContent = true;
            i += Character.charCount(codepoint);
        }
        return builder.toString();
    }
}

/** MINECRAFT_FORMATTED 模式：§ 格式码原样保留、跨行续传。 */
final class MinecraftTextContentStrategy implements TextContentModeStrategy {

    private final TextLayoutService service;

    MinecraftTextContentStrategy(TextLayoutService service) {
        this.service = service;
    }

    @Override
    public String trim(String text, int targetWidth, TextStyle baseStyle, int baseFontSizePx) {
        StringBuilder builder = new StringBuilder();
        TextStyle currentStyle = baseStyle.copy();
        double width = 0.0D;

        for (int i = 0; i < text.length(); ) {
            int codepoint = text.codePointAt(i);
            if (codepoint == '§' && i < text.length() - 1) {
                builder.appendCodePoint(codepoint);
                i += Character.charCount(codepoint);
                char formatCode = text.charAt(i);
                builder.append(formatCode);
                currentStyle.applyFormat(Character.toLowerCase(formatCode), 0xFFFFFFFF);
                i++;
                continue;
            }

            double charWidth = service.measureCodepointWidth(codepoint, currentStyle.getFontType(), baseFontSizePx);
            if (width + charWidth > targetWidth) {
                break;
            }
            width += charWidth;
            builder.appendCodePoint(codepoint);
            i += Character.charCount(codepoint);
        }
        return builder.toString();
    }

    @Override
    public String wrap(String text, int wrapWidth, TextStyle baseStyle, int baseFontSizePx) {
        StringBuilder builder = new StringBuilder();
        TextStyle currentStyle = baseStyle.copy();
        double width = 0.0D;
        boolean lineHasVisibleContent = false;

        for (int i = 0; i < text.length(); ) {
            int codepoint = text.codePointAt(i);
            if (codepoint == '§' && i < text.length() - 1) {
                builder.appendCodePoint(codepoint);
                i += Character.charCount(codepoint);
                char formatCode = text.charAt(i);
                builder.append(formatCode);
                currentStyle.applyFormat(Character.toLowerCase(formatCode), 0xFFFFFFFF);
                i++;
                continue;
            }

            if (codepoint == '\r' || codepoint == '\n') {
                i += Character.charCount(codepoint);
                if (codepoint == '\r' && i < text.length() && text.charAt(i) == '\n') {
                    i++;
                }
                builder.append('\n');
                if (i < text.length()) {
                    builder.append(currentStyle.toFormattingCodes(0xFFFFFFFF));
                }
                width = 0.0D;
                lineHasVisibleContent = false;
                continue;
            }

            double charWidth = service.measureCodepointWidth(codepoint, currentStyle.getFontType(), baseFontSizePx);
            if (width + charWidth > wrapWidth && lineHasVisibleContent) {
                builder.append('\n');
                builder.append(currentStyle.toFormattingCodes(0xFFFFFFFF));
                width = 0.0D;
                lineHasVisibleContent = false;
            }
            builder.appendCodePoint(codepoint);
            width += charWidth;
            lineHasVisibleContent = true;
            i += Character.charCount(codepoint);
        }
        return builder.toString();
    }
}

/** RICH_TAGS 模式：富文本感知裁剪与 word-break 换行（样式跨行续传）。 */
final class RichTextContentStrategy implements TextContentModeStrategy {

    private final TextLayoutService service;

    RichTextContentStrategy(TextLayoutService service) {
        this.service = service;
    }

    @Override
    public String trim(String text, int targetWidth, TextStyle baseStyle, int baseFontSizePx) {
        List<TextSegment> segments = RichTextTagParser.parse(text, baseStyle);
        List<TextSegment> kept = new ArrayList<TextSegment>();
        double width = 0.0D;
        int safeBaseSize = Math.max(1, baseFontSizePx);
        for (TextSegment segment : segments) {
            TextStyle style = segment.getStyle();
            String segmentText = segment.getText();
            int effectiveSize = style.resolveEffectiveFontSizePx(safeBaseSize);
            StringBuilder keptText = new StringBuilder();
            for (int i = 0; i < segmentText.length(); ) {
                int codepoint = segmentText.codePointAt(i);
                double charWidth = service.resolveCodepointAdvance(codepoint, style, effectiveSize);
                if (width + charWidth > targetWidth) {
                    break;
                }
                width += charWidth;
                keptText.appendCodePoint(codepoint);
                i += Character.charCount(codepoint);
            }
            if (keptText.length() > 0) {
                kept.add(new TextSegment(keptText.toString(), style));
            }
            if (keptText.length() < segmentText.length()) {
                break;
            }
        }
        return RichTextTagParser.serialize(kept, baseStyle);
    }

    @Override
    public String wrap(String text, int wrapWidth, TextStyle baseStyle, int baseFontSizePx) {
        int safeBaseSize = Math.max(1, baseFontSizePx);
        List<TextSegment> segments = RichTextTagParser.parse(text, baseStyle);
        List<String> lines = new ArrayList<String>();
        List<TextSegment> currentLine = new ArrayList<TextSegment>();
        double width = 0.0D;
        boolean lineHasVisibleContent = false;
        for (TextSegment segment : segments) {
            TextStyle style = segment.getStyle();
            String remaining = segment.getText();
            while (!remaining.isEmpty()) {
                int codepoint = remaining.codePointAt(0);
                int codepointLength = Character.charCount(codepoint);
                if (codepoint == '\n' || codepoint == '\r') {
                    flushRichLine(lines, currentLine, baseStyle, true);
                    width = 0.0D;
                    lineHasVisibleContent = false;
                    if (codepoint == '\r' && remaining.length() > codepointLength
                            && remaining.charAt(codepointLength) == '\n') {
                        remaining = remaining.substring(codepointLength + 1);
                    } else {
                        remaining = remaining.substring(codepointLength);
                    }
                    continue;
                }
                int tokenEnd = findRichTokenEnd(remaining);
                String token = remaining.substring(0, tokenEnd);
                boolean tokenIsSpace = isBreakSpace(codepoint);
                double tokenWidth = measureTokenWidth(token, style, safeBaseSize);
                if (width + tokenWidth > wrapWidth && lineHasVisibleContent) {
                    flushRichLine(lines, currentLine, baseStyle, false);
                    width = 0.0D;
                    lineHasVisibleContent = false;
                }
                if (tokenIsSpace) {
                    if (lineHasVisibleContent) {
                        appendTokenToRichLine(currentLine, token, style);
                        width += tokenWidth;
                    }
                    remaining = remaining.substring(tokenEnd);
                    continue;
                }
                if (width + tokenWidth > wrapWidth && !lineHasVisibleContent) {
                    // 空行放不下整词：按字符硬断，填满一行折一行
                    int effectiveSize = style.resolveEffectiveFontSizePx(safeBaseSize);
                    for (int i = 0; i < token.length(); ) {
                        int tokenCodepoint = token.codePointAt(i);
                        double charWidth = service.resolveCodepointAdvance(tokenCodepoint, style, effectiveSize);
                        if (width + charWidth > wrapWidth && lineHasVisibleContent) {
                            flushRichLine(lines, currentLine, baseStyle, false);
                            width = 0.0D;
                            lineHasVisibleContent = false;
                        }
                        appendToRichLine(currentLine, tokenCodepoint, style);
                        width += charWidth;
                        lineHasVisibleContent = true;
                        i += Character.charCount(tokenCodepoint);
                    }
                    remaining = remaining.substring(tokenEnd);
                    continue;
                }
                appendTokenToRichLine(currentLine, token, style);
                width += tokenWidth;
                lineHasVisibleContent = true;
                remaining = remaining.substring(tokenEnd);
            }
        }
        flushRichLine(lines, currentLine, baseStyle, false);
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(line);
        }
        return builder.toString();
    }

    /** 把一个码点追加到行片段列表尾部（样式一致时合并入末段）。 */
    private void appendToRichLine(List<TextSegment> line, int codepoint, TextStyle style) {
        String glyphText = new String(Character.toChars(codepoint));
        if (!line.isEmpty()) {
            TextSegment last = line.get(line.size() - 1);
            if (sameRichStyle(last.getStyle(), style)) {
                line.set(line.size() - 1, new TextSegment(last.getText() + glyphText, style));
                return;
            }
        }
        line.add(new TextSegment(glyphText, style));
    }

    /** 把一个完整 token 追加到行片段列表尾部（逐码点合并，样式一致时并入末段）。 */
    private void appendTokenToRichLine(List<TextSegment> line, String token, TextStyle style) {
        for (int i = 0; i < token.length(); ) {
            int codepoint = token.codePointAt(i);
            appendToRichLine(line, codepoint, style);
            i += Character.charCount(codepoint);
        }
    }

    /** 折叠行尾空白并落行：移除行片段末尾空白后序列化入行列表。 */
    private void flushRichLine(List<String> lines, List<TextSegment> line, TextStyle baseStyle,
            boolean keepEmptyLine) {
        removeTrailingSpaces(line);
        if (line.isEmpty()) {
            if (keepEmptyLine) {
                lines.add("");
            }
            return;
        }
        lines.add(RichTextTagParser.serialize(line, baseStyle));
        line.clear();
    }

    /** 移除行片段列表末段的尾部空白字符（空格/tab/全角空格），跨段回溯直至无空白。 */
    private void removeTrailingSpaces(List<TextSegment> line) {
        while (!line.isEmpty()) {
            TextSegment last = line.get(line.size() - 1);
            String text = last.getText();
            int end = text.length();
            while (end > 0 && isBreakSpace(text.charAt(end - 1))) {
                end--;
            }
            if (end == text.length()) {
                return;
            }
            if (end == 0) {
                line.remove(line.size() - 1);
            } else {
                line.set(line.size() - 1, new TextSegment(text.substring(0, end), last.getStyle()));
                return;
            }
        }
    }

    /** 从文本首字符起提取一个 word-break token（token 不跨换行符）。 */
    private int findRichTokenEnd(String text) {
        int first = text.codePointAt(0);
        int end = Character.charCount(first);
        if (isBreakSpace(first)) {
            while (end < text.length() && isBreakSpace(text.codePointAt(end))) {
                end += Character.charCount(text.codePointAt(end));
            }
            return end;
        }
        if (isCjk(first)) {
            return end;
        }
        while (end < text.length()) {
            int codepoint = text.codePointAt(end);
            if (codepoint == '\n' || codepoint == '\r' || isBreakSpace(codepoint) || isCjk(codepoint)) {
                break;
            }
            end += Character.charCount(codepoint);
        }
        return end;
    }

    /** 计算 token 宽度（逐码点按段字号测量后累加）。 */
    private double measureTokenWidth(String token, TextStyle style, int safeBaseSize) {
        double total = 0.0D;
        int effectiveSize = style.resolveEffectiveFontSizePx(safeBaseSize);
        for (int i = 0; i < token.length(); ) {
            int codepoint = token.codePointAt(i);
            total += service.resolveCodepointAdvance(codepoint, style, effectiveSize);
            i += Character.charCount(codepoint);
        }
        return total;
    }

    /** 判断码点是否为可折叠断行空白（ASCII 空格、tab、全角空格）。 */
    private static boolean isBreakSpace(int codepoint) {
        return codepoint == ' ' || codepoint == '\t' || codepoint == 0x3000;
    }

    /** 判断码点是否属于 CJK 书写体系（字间任意位置可断行）。 */
    private static boolean isCjk(int codepoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codepoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL
                || script == Character.UnicodeScript.BOPOMOFO;
    }

    /** 比较两个富文本片段样式是否一致（不含随机样式标记）。 */
    private boolean sameRichStyle(TextStyle left, TextStyle right) {
        return left.getColor() == right.getColor()
                && left.isColorExplicit() == right.isColorExplicit()
                && left.getFontType() == right.getFontType()
                && left.isItalic() == right.isItalic()
                && left.isUnderline() == right.isUnderline()
                && left.isStrikethrough() == right.isStrikethrough()
                && left.getFontSizePx() == right.getFontSizePx()
                && left.getMarkColor() == right.getMarkColor()
                && left.isSuperscript() == right.isSuperscript()
                && left.isSubscript() == right.isSubscript()
                && left.getLetterSpacing() == right.getLetterSpacing()
                && Objects.equals(left.getLink(), right.getLink());
    }
}
