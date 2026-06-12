package club.heiqi.uilib.ui.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.style.cascade.ComputedStyle;
import club.heiqi.uilib.ui.style.props.UiFontStyle;
import club.heiqi.uilib.ui.style.props.UiFontWeight;
import club.heiqi.uilib.ui.style.props.UiOverflowWrap;
import club.heiqi.uilib.ui.style.props.UiTextAlign;
import club.heiqi.uilib.ui.style.props.UiTextTransform;
import club.heiqi.uilib.ui.style.props.UiWhiteSpace;
import club.heiqi.uilib.ui.style.props.UiWordBreak;
import club.heiqi.uilib.ui.text.TextContentMode;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.text.TextMeasureStyle;

/**
 * 文本布局辅助类。
 *
 * <p>从 {@link DocumentLayoutEngine} 提取的纯文本布局方法群，承担：</p>
 * <ul>
 *     <li>文本规范化（white-space 折叠 / 保留、text-transform）</li>
 *     <li>软换行决策（按 word-break / overflow-wrap / CJK / URL 标点选择断行点）</li>
 *     <li>固有宽度测量（max-content / min-content）</li>
 *     <li>文本尺寸放缩（UI 像素 ⇄ 字体原始像素）与行高、对齐偏移、缩进解析</li>
 * </ul>
 *
 * <p>与 inline 行盒相关的 {@code InlineLayoutContext} 及配套 pending 数据结构由
 * {@link InlineLayoutHelper} 承载，并调用本类提供的纯方法完成文本侧计算。</p>
 */
final class TextLayoutHelper {

    /** 字体原始像素到 UI 像素的旧固定缩放系数，仅供兼容入口使用。 */
    static final float UI_TEXT_SCALE = 2.0F;

    private TextLayoutHelper() {}

    /**
     * 将原始字体像素宽度放缩到 UI 像素。
     */
    static int toUiTextSize(int rawSize) {
        return Math.round(Math.max(0, rawSize) * UI_TEXT_SCALE);
    }

    /**
     * 将 UI 像素宽度还原为字体原始像素，至少保留 1 像素以避免传入测量服务的 0 宽度。
     */
    static int toRawTextSize(int uiSize) {
        return Math.max(1, Math.round(Math.max(1, uiSize) / UI_TEXT_SCALE));
    }

    /**
     * 解析 text-align 在剩余空间中产生的行内偏移量。
     */
    static int resolveTextAlignOffset(UiTextAlign textAlign, int availableWidth, int lineWidth) {
        if (textAlign == UiTextAlign.CENTER) {
            return Math.max(0, availableWidth - lineWidth) / 2;
        }
        if (textAlign == UiTextAlign.END) {
            return Math.max(0, availableWidth - lineWidth);
        }
        return 0;
    }

    /**
     * 解析当前文本默认行高（无 owner style）。
     */
    static int resolveTextLineHeight(TextMeasureService textMeasureService) {
        return resolveTextLineHeight(textMeasureService, null);
    }

    /**
     * 解析文本行高。
     *
     * <p>如果 ownerStyle 的 line-height 为 auto，则回落到字体默认行高；
     * 否则用字体默认行高作为 containingSize 解析（支持 px 和百分比）。</p>
     */
    static int resolveTextLineHeight(TextMeasureService textMeasureService, ComputedStyle ownerStyle) {
        TextMeasureStyle textStyle = resolveTextMeasureStyle(ownerStyle, TextContentMode.UILIB_RAW);
        int fontLineHeight = Math.max(1, textMeasureService.getLineHeight(textStyle));
        if (ownerStyle == null || DocumentLayoutEngine.isAuto(ownerStyle.getLineHeight())) {
            return fontLineHeight;
        }
        int resolvedHeight = ownerStyle.getLineHeight().resolve(fontLineHeight, fontLineHeight);
        return Math.max(1, resolvedHeight);
    }

    /**
     * 解析首行缩进（text-indent）。
     */
    static int resolveTextIndent(ComputedStyle style, int contentWidth) {
        if (style == null || style.getTextIndent() == null) {
            return 0;
        }
        return style.getTextIndent().resolve(contentWidth, 0);
    }

    /**
     * 通过测量服务取实际显示宽度（考虑 font-weight / font-style）。
     */
    static int measureTextWidth(TextMeasureService textMeasureService, String text,
            TextContentMode textContentMode, ComputedStyle ownerStyle) {
        return textMeasureService.getStringWidth(text, resolveTextMeasureStyle(ownerStyle, textContentMode));
    }

    /**
     * 通过测量服务把文本裁剪到目标宽度（字体原始像素），考虑 font-weight / font-style。
     */
    static String trimTextToWidth(TextMeasureService textMeasureService, String text, int targetWidth,
            TextContentMode textContentMode, ComputedStyle ownerStyle) {
        return textMeasureService.trimStringToWidth(text, targetWidth,
                resolveTextMeasureStyle(ownerStyle, textContentMode));
    }

    static TextMeasureStyle resolveTextMeasureStyle(ComputedStyle ownerStyle, TextContentMode textContentMode) {
        return new TextMeasureStyle(resolveFontSizePx(ownerStyle), textContentMode, resolveFontWeight(ownerStyle),
                resolveFontStyle(ownerStyle));
    }

    static int resolveFontSizePx(ComputedStyle ownerStyle) {
        if (ownerStyle == null || ownerStyle.getFontSize() == null) {
            return TextMeasureStyle.DEFAULT_FONT_SIZE_PX;
        }
        return Math.max(1, ownerStyle.getFontSize().resolve(TextMeasureStyle.DEFAULT_FONT_SIZE_PX,
                TextMeasureStyle.DEFAULT_FONT_SIZE_PX));
    }

    static UiFontWeight resolveFontWeight(ComputedStyle ownerStyle) {
        return ownerStyle == null ? UiFontWeight.NORMAL : ownerStyle.getFontWeight();
    }

    static UiFontStyle resolveFontStyle(ComputedStyle ownerStyle) {
        return ownerStyle == null ? UiFontStyle.NORMAL : ownerStyle.getFontStyle();
    }

    static UiWhiteSpace resolveWhiteSpace(ComputedStyle ownerStyle) {
        return ownerStyle == null ? UiWhiteSpace.NORMAL : ownerStyle.getWhiteSpace();
    }

    static boolean allowsSoftWrapping(UiWhiteSpace whiteSpace) {
        return whiteSpace != UiWhiteSpace.NOWRAP && whiteSpace != UiWhiteSpace.PRE;
    }

    static boolean preservesHardLineBreaks(UiWhiteSpace whiteSpace) {
        return whiteSpace == UiWhiteSpace.PRE || whiteSpace == UiWhiteSpace.PRE_WRAP
                || whiteSpace == UiWhiteSpace.PRE_LINE;
    }

    /**
     * 综合 white-space 与 text-transform 对原始文本进行布局前归一化。
     */
    static String normalizeTextForLayout(String text, ComputedStyle ownerStyle,
            TextContentMode textContentMode) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String whitespaceNormalized = normalizeWhiteSpaceForLayout(text, resolveWhiteSpace(ownerStyle),
                textContentMode);
        return applyTextTransform(whitespaceNormalized, ownerStyle == null ? UiTextTransform.NONE
                : ownerStyle.getTextTransform(), textContentMode);
    }

    private static String normalizeWhiteSpaceForLayout(String text, UiWhiteSpace whiteSpace,
            TextContentMode textContentMode) {
        if (whiteSpace == UiWhiteSpace.PRE || whiteSpace == UiWhiteSpace.PRE_WRAP) {
            return normalizePreservedLineBreaks(text, textContentMode, true);
        }
        if (whiteSpace == UiWhiteSpace.PRE_LINE) {
            return normalizePreLineWhiteSpace(text, textContentMode);
        }
        return collapseAllWhiteSpace(text, textContentMode);
    }

    private static String normalizePreservedLineBreaks(String text, TextContentMode textContentMode,
            boolean preserveHorizontalSpaces) {
        StringBuilder builder = new StringBuilder(text.length());
        for (int index = 0; index < text.length();) {
            if (isFormattingCodeStart(text, index, textContentMode)) {
                builder.append(text, index, index + 2);
                index += 2;
                continue;
            }
            char value = text.charAt(index);
            if (value == '\r') {
                builder.append('\n');
                index += index + 1 < text.length() && text.charAt(index + 1) == '\n' ? 2 : 1;
                continue;
            }
            if (value == '\n') {
                builder.append('\n');
                index++;
                continue;
            }
            if (value == '\t') {
                builder.append(preserveHorizontalSpaces ? "    " : " ");
                index++;
                continue;
            }
            builder.append(value);
            index++;
        }
        return builder.toString();
    }

    private static String normalizePreLineWhiteSpace(String text, TextContentMode textContentMode) {
        StringBuilder builder = new StringBuilder(text.length());
        boolean pendingSpace = false;
        for (int index = 0; index < text.length();) {
            if (isFormattingCodeStart(text, index, textContentMode)) {
                if (pendingSpace && builder.length() > 0 && builder.charAt(builder.length() - 1) != '\n') {
                    builder.append(' ');
                }
                pendingSpace = false;
                builder.append(text, index, index + 2);
                index += 2;
                continue;
            }
            char value = text.charAt(index);
            if (value == '\r' || value == '\n') {
                pendingSpace = false;
                if (builder.length() > 0 && builder.charAt(builder.length() - 1) == ' ') {
                    builder.setLength(builder.length() - 1);
                }
                builder.append('\n');
                index += value == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n' ? 2 : 1;
                continue;
            }
            if (Character.isWhitespace(value)) {
                pendingSpace = true;
                index++;
                continue;
            }
            if (pendingSpace && builder.length() > 0 && builder.charAt(builder.length() - 1) != '\n') {
                builder.append(' ');
            }
            pendingSpace = false;
            builder.append(value);
            index++;
        }
        if (pendingSpace && builder.length() > 0 && builder.charAt(builder.length() - 1) != '\n') {
            builder.append(' ');
        }
        return builder.toString();
    }

    private static String collapseAllWhiteSpace(String text, TextContentMode textContentMode) {
        StringBuilder builder = new StringBuilder(text.length());
        boolean pendingSpace = false;
        for (int index = 0; index < text.length();) {
            if (isFormattingCodeStart(text, index, textContentMode)) {
                if (pendingSpace && builder.length() > 0) {
                    builder.append(' ');
                }
                pendingSpace = false;
                builder.append(text, index, index + 2);
                index += 2;
                continue;
            }
            int codePoint = text.codePointAt(index);
            if (Character.isWhitespace(codePoint)) {
                pendingSpace = true;
                index += Character.charCount(codePoint);
                continue;
            }
            if (pendingSpace && builder.length() > 0) {
                builder.append(' ');
            }
            pendingSpace = false;
            builder.appendCodePoint(codePoint);
            index += Character.charCount(codePoint);
        }
        if (pendingSpace && builder.length() > 0) {
            builder.append(' ');
        }
        return builder.toString();
    }

    private static String applyTextTransform(String text, UiTextTransform textTransform,
            TextContentMode textContentMode) {
        if (text == null || text.isEmpty() || textTransform == null || textTransform == UiTextTransform.NONE) {
            return text;
        }
        StringBuilder builder = new StringBuilder(text.length());
        boolean capitalizeNext = true;
        for (int index = 0; index < text.length();) {
            if (isFormattingCodeStart(text, index, textContentMode)) {
                builder.append(text, index, index + 2);
                index += 2;
                continue;
            }
            int codePoint = text.codePointAt(index);
            if (textTransform == UiTextTransform.UPPERCASE) {
                builder.append(toUpperCaseText(codePoint));
            } else if (textTransform == UiTextTransform.LOWERCASE) {
                builder.append(toLowerCaseText(codePoint));
            } else if (textTransform == UiTextTransform.CAPITALIZE) {
                boolean wordCharacter = Character.isLetterOrDigit(codePoint) || codePoint == '_';
                if (capitalizeNext && Character.isLetter(codePoint)) {
                    builder.append(toUpperCaseText(codePoint));
                } else {
                    builder.appendCodePoint(codePoint);
                }
                capitalizeNext = !wordCharacter;
            } else {
                builder.appendCodePoint(codePoint);
            }
            index += Character.charCount(codePoint);
        }
        return builder.toString();
    }

    private static String toUpperCaseText(int codePoint) {
        return new String(Character.toChars(codePoint)).toUpperCase(Locale.ROOT);
    }

    private static String toLowerCaseText(int codePoint) {
        return new String(Character.toChars(codePoint)).toLowerCase(Locale.ROOT);
    }

    /**
     * 按 CSS-like 断词属性将普通文本拆成布局行。
     */
    static List<String> wrapTextToWidth(String text, int availableWidth, ComputedStyle ownerStyle,
            TextContentMode textContentMode, TextMeasureService textMeasureService) {
        if (text == null || text.isEmpty() || availableWidth <= 0) {
            return Collections.emptyList();
        }
        List<String> lines = new ArrayList<String>();
        String remainingText = text;
        while (!remainingText.isEmpty()) {
            TextWrapSegment segment = takeNextTextSegment(remainingText, availableWidth, ownerStyle,
                    textContentMode, textMeasureService);
            if (segment.consumedLength <= 0) {
                break;
            }
            if (!segment.text.isEmpty()) {
                lines.add(segment.text);
            }
            remainingText = remainingText.substring(Math.min(segment.consumedLength, remainingText.length()));
        }
        return lines;
    }

    /**
     * 取出当前行的下一个文本片段，并返回实际消费的源文本长度。
     */
    static TextWrapSegment takeNextTextSegment(String text, int availableWidth, ComputedStyle ownerStyle,
            TextContentMode textContentMode, TextMeasureService textMeasureService) {
        if (text == null || text.isEmpty()) {
            return new TextWrapSegment("", 0);
        }
        UiWhiteSpace whiteSpace = resolveWhiteSpace(ownerStyle);
        if (!allowsSoftWrapping(whiteSpace) && !preservesHardLineBreaks(whiteSpace)) {
            return new TextWrapSegment(text, text.length());
        }
        int hardLineBreakStart = preservesHardLineBreaks(whiteSpace) ? findHardLineBreakStart(text) : text.length();
        int hardLineBreakLength = hardLineBreakStart < text.length()
                ? resolveHardLineBreakLength(text, hardLineBreakStart) : 0;
        String paragraph = text.substring(0, hardLineBreakStart);
        if (!allowsSoftWrapping(whiteSpace)) {
            int consumedLength = paragraph.length() + hardLineBreakLength;
            return new TextWrapSegment(paragraph, consumedLength, hardLineBreakLength > 0);
        }
        if (paragraph.isEmpty()) {
            return new TextWrapSegment("", hardLineBreakLength, hardLineBreakLength > 0);
        }

        UiWordBreak wordBreak = ownerStyle == null ? UiWordBreak.NORMAL : ownerStyle.getWordBreak();
        UiOverflowWrap overflowWrap = ownerStyle == null ? UiOverflowWrap.NORMAL : ownerStyle.getOverflowWrap();
        int maxFitEnd = resolveMaxFittingTextEnd(paragraph, availableWidth, ownerStyle, textContentMode,
                textMeasureService);
        TextBreakPoint breakPoint;
        if (maxFitEnd >= paragraph.length()) {
            breakPoint = TextBreakPoint.at(paragraph.length());
        } else if (wordBreak == UiWordBreak.BREAK_ALL || overflowWrap == UiOverflowWrap.ANYWHERE) {
            breakPoint = TextBreakPoint.at(maxFitEnd);
        } else {
            breakPoint = findLastNormalBreakPoint(paragraph, maxFitEnd, wordBreak, textContentMode);
            if (breakPoint == null && overflowWrap == UiOverflowWrap.BREAK_WORD) {
                breakPoint = TextBreakPoint.at(maxFitEnd);
            }
            if (breakPoint == null) {
                breakPoint = findFirstNormalBreakPointAfter(paragraph, maxFitEnd, wordBreak, textContentMode);
            }
            if (breakPoint == null) {
                breakPoint = TextBreakPoint.at(paragraph.length());
            }
        }

        int consumedLength = Math.max(0, Math.min(breakPoint.consumedEnd, paragraph.length()));
        int textEnd = Math.max(0, Math.min(breakPoint.textEnd, consumedLength));
        boolean forceLineBreak = false;
        if (consumedLength >= paragraph.length()) {
            consumedLength += hardLineBreakLength;
            forceLineBreak = hardLineBreakLength > 0;
        }
        if (consumedLength <= 0) {
            int firstUnitLength = firstTextUnitLength(paragraph, textContentMode);
            consumedLength = firstUnitLength;
            textEnd = firstUnitLength;
        }
        return new TextWrapSegment(paragraph.substring(0, textEnd), consumedLength, forceLineBreak);
    }

    private static int findHardLineBreakStart(String text) {
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value == '\r' || value == '\n') {
                return index;
            }
        }
        return text.length();
    }

    private static int resolveHardLineBreakLength(String text, int breakStart) {
        if (breakStart < text.length() - 1 && text.charAt(breakStart) == '\r'
                && text.charAt(breakStart + 1) == '\n') {
            return 2;
        }
        return 1;
    }

    private static int resolveMaxFittingTextEnd(String text, int availableWidth, ComputedStyle ownerStyle,
            TextContentMode textContentMode, TextMeasureService textMeasureService) {
        String trimmed = trimTextToWidth(textMeasureService, text, availableWidth, textContentMode, ownerStyle);
        int trimmedEnd = trimmed == null ? 0 : trimmed.length();
        int maxFitEnd = normalizeTextUnitBoundary(text, trimmedEnd, textContentMode);
        if (maxFitEnd <= 0) {
            return firstTextUnitLength(text, textContentMode);
        }
        return maxFitEnd;
    }

    private static TextBreakPoint findLastNormalBreakPoint(String text, int maxTextEnd, UiWordBreak wordBreak,
            TextContentMode textContentMode) {
        TextBreakPoint result = null;
        List<TextWrapUnit> units = collectTextWrapUnits(text, textContentMode);
        for (int index = 0; index < units.size(); index++) {
            TextBreakPoint candidate = resolveNormalBreakPoint(units, index, wordBreak);
            if (candidate == null || candidate.textEnd > maxTextEnd) {
                continue;
            }
            result = candidate;
        }
        return result;
    }

    private static TextBreakPoint findFirstNormalBreakPointAfter(String text, int minTextEnd, UiWordBreak wordBreak,
            TextContentMode textContentMode) {
        List<TextWrapUnit> units = collectTextWrapUnits(text, textContentMode);
        for (int index = 0; index < units.size(); index++) {
            TextBreakPoint candidate = resolveNormalBreakPoint(units, index, wordBreak);
            if (candidate != null && candidate.textEnd > minTextEnd) {
                return candidate;
            }
        }
        return null;
    }

    private static TextBreakPoint resolveNormalBreakPoint(List<TextWrapUnit> units, int index,
            UiWordBreak wordBreak) {
        TextWrapUnit unit = units.get(index);
        if (unit.formattingCode) {
            return null;
        }
        if (Character.isWhitespace(unit.codePoint)) {
            int consumedEnd = unit.end;
            while (index + 1 < units.size() && Character.isWhitespace(units.get(index + 1).codePoint)) {
                index++;
                consumedEnd = units.get(index).end;
            }
            return new TextBreakPoint(unit.start, consumedEnd);
        }
        if (isUrlBreakCodePoint(unit.codePoint)) {
            return TextBreakPoint.at(unit.end);
        }
        if (wordBreak != UiWordBreak.KEEP_ALL && index + 1 < units.size()) {
            TextWrapUnit next = units.get(index + 1);
            if (!next.formattingCode && (isCjkCodePoint(unit.codePoint) || isCjkCodePoint(next.codePoint))) {
                return TextBreakPoint.at(unit.end);
            }
        }
        return null;
    }

    private static List<TextWrapUnit> collectTextWrapUnits(String text, TextContentMode textContentMode) {
        List<TextWrapUnit> units = new ArrayList<TextWrapUnit>();
        for (int index = 0; index < text.length();) {
            if (isFormattingCodeStart(text, index, textContentMode)) {
                units.add(new TextWrapUnit(index, index + 2, 0, true));
                index += 2;
                continue;
            }
            int codePoint = text.codePointAt(index);
            int end = index + Character.charCount(codePoint);
            units.add(new TextWrapUnit(index, end, codePoint, false));
            index = end;
        }
        return units;
    }

    private static int normalizeTextUnitBoundary(String text, int requestedEnd, TextContentMode textContentMode) {
        int safeEnd = Math.max(0, Math.min(requestedEnd, text.length()));
        int boundary = 0;
        for (int index = 0; index < text.length();) {
            int nextIndex;
            if (isFormattingCodeStart(text, index, textContentMode)) {
                nextIndex = index + 2;
            } else {
                nextIndex = index + Character.charCount(text.codePointAt(index));
            }
            if (nextIndex > safeEnd) {
                break;
            }
            boundary = nextIndex;
            index = nextIndex;
        }
        return boundary;
    }

    private static int firstTextUnitLength(String text, TextContentMode textContentMode) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        if (isFormattingCodeStart(text, 0, textContentMode)) {
            return 2;
        }
        return Character.charCount(text.codePointAt(0));
    }

    private static boolean isFormattingCodeStart(String text, int index, TextContentMode textContentMode) {
        return textContentMode == TextContentMode.MINECRAFT_FORMATTED
                && index >= 0 && index < text.length() - 1 && text.charAt(index) == '\u00a7';
    }

    private static boolean isUrlBreakCodePoint(int codePoint) {
        return codePoint == '/' || codePoint == '\\' || codePoint == '.' || codePoint == ':'
                || codePoint == '?' || codePoint == '&' || codePoint == '=' || codePoint == '-'
                || codePoint == '_' || codePoint == '#';
    }

    private static boolean isCjkCodePoint(int codePoint) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.HIRAGANA
                || block == Character.UnicodeBlock.KATAKANA
                || block == Character.UnicodeBlock.HANGUL_SYLLABLES
                || block == Character.UnicodeBlock.HANGUL_JAMO
                || block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO;
    }

    /**
     * 测量文本节点的固有宽度，用于 auto 宽度元素的内容宽测量。
     */
    static int measureIntrinsicTextWidth(TextNode textNode, ComputedStyle ownerStyle,
            TextMeasureService textMeasureService) {
        TextContentMode textContentMode = textNode.getTextContentMode();
        String text = normalizeTextForLayout(textNode.getText(), ownerStyle, textContentMode);
        if (text == null || text.isEmpty()) {
            return 0;
        }
        if (ownerStyle != null && (ownerStyle.getOverflowWrap() == UiOverflowWrap.ANYWHERE
                || ownerStyle.getWordBreak() == UiWordBreak.BREAK_ALL)) {
            return measureMaxTextUnitWidth(text, textContentMode, textMeasureService, ownerStyle);
        }
        return measureMaxTextLineWidth(text, textContentMode, textMeasureService, ownerStyle);
    }

    /**
     * 测量文本节点的 CSS-like min-content 宽度，用于 flex item 的 auto 最小宽度。
     */
    static int measureMinContentTextWidth(TextNode textNode, ComputedStyle ownerStyle,
            TextMeasureService textMeasureService) {
        TextContentMode textContentMode = textNode.getTextContentMode();
        String text = normalizeTextForLayout(textNode.getText(), ownerStyle, textContentMode);
        if (text == null || text.isEmpty()) {
            return 0;
        }
        UiWhiteSpace whiteSpace = resolveWhiteSpace(ownerStyle);
        if (!allowsSoftWrapping(whiteSpace)) {
            return measureMaxTextLineWidth(text, textContentMode, textMeasureService, ownerStyle);
        }
        UiWordBreak wordBreak = ownerStyle == null ? UiWordBreak.NORMAL : ownerStyle.getWordBreak();
        UiOverflowWrap overflowWrap = ownerStyle == null ? UiOverflowWrap.NORMAL : ownerStyle.getOverflowWrap();
        if (wordBreak == UiWordBreak.BREAK_ALL || overflowWrap == UiOverflowWrap.ANYWHERE) {
            return measureMaxTextUnitWidth(text, textContentMode, textMeasureService, ownerStyle);
        }
        return measureMaxTextBreakRunWidth(text, textContentMode, textMeasureService, ownerStyle, wordBreak);
    }

    private static int measureMaxTextBreakRunWidth(String text, TextContentMode textContentMode,
            TextMeasureService textMeasureService, ComputedStyle ownerStyle, UiWordBreak wordBreak) {
        int maxWidth = 0;
        int runStart = 0;
        List<TextWrapUnit> units = collectTextWrapUnits(text, textContentMode);
        for (int index = 0; index < units.size(); index++) {
            TextBreakPoint breakPoint = resolveNormalBreakPoint(units, index, wordBreak);
            if (breakPoint == null) {
                continue;
            }
            maxWidth = Math.max(maxWidth, measureTextRunWidth(text, runStart, breakPoint.textEnd,
                    textContentMode, textMeasureService, ownerStyle));
            runStart = Math.max(runStart, Math.min(breakPoint.consumedEnd, text.length()));
        }
        return Math.max(maxWidth, measureTextRunWidth(text, runStart, text.length(), textContentMode,
                textMeasureService, ownerStyle));
    }

    private static int measureTextRunWidth(String text, int start, int end, TextContentMode textContentMode,
            TextMeasureService textMeasureService, ComputedStyle ownerStyle) {
        if (start < 0 || end <= start) {
            return 0;
        }
        return measureTextWidth(textMeasureService, text.substring(start, end), textContentMode, ownerStyle);
    }

    private static int measureMaxTextLineWidth(String text, TextContentMode textContentMode,
            TextMeasureService textMeasureService, ComputedStyle ownerStyle) {
        int maxWidth = 0;
        int lineStart = 0;
        while (lineStart <= text.length()) {
            int lineBreakStart = findHardLineBreakStart(text.substring(lineStart));
            int lineEnd = lineStart + lineBreakStart;
            maxWidth = Math.max(maxWidth, measureTextWidth(textMeasureService, text.substring(lineStart, lineEnd),
                    textContentMode, ownerStyle));
            if (lineEnd >= text.length()) {
                break;
            }
            lineStart = lineEnd + resolveHardLineBreakLength(text, lineEnd);
        }
        return maxWidth;
    }

    private static int measureMaxTextUnitWidth(String text, TextContentMode textContentMode,
            TextMeasureService textMeasureService, ComputedStyle ownerStyle) {
        int maxWidth = 0;
        List<TextWrapUnit> units = collectTextWrapUnits(text, textContentMode);
        for (TextWrapUnit unit : units) {
            if (unit.formattingCode || unit.codePoint == '\r' || unit.codePoint == '\n') {
                continue;
            }
            maxWidth = Math.max(maxWidth, measureTextWidth(textMeasureService, text.substring(unit.start, unit.end),
                    textContentMode, ownerStyle));
        }
        return maxWidth;
    }

    /**
     * 单次文本换行消费结果。
     */
    static final class TextWrapSegment {

        final String text;
        final int consumedLength;
        final boolean forceLineBreak;

        TextWrapSegment(String text, int consumedLength) {
            this(text, consumedLength, false);
        }

        TextWrapSegment(String text, int consumedLength, boolean forceLineBreak) {
            this.text = text == null ? "" : text;
            this.consumedLength = Math.max(0, consumedLength);
            this.forceLineBreak = forceLineBreak;
        }
    }

    /**
     * 可断行位置，区分实际显示文本末尾和源文本消费末尾。
     */
    static final class TextBreakPoint {

        final int textEnd;
        final int consumedEnd;

        TextBreakPoint(int textEnd, int consumedEnd) {
            this.textEnd = Math.max(0, textEnd);
            this.consumedEnd = Math.max(this.textEnd, consumedEnd);
        }

        static TextBreakPoint at(int end) {
            return new TextBreakPoint(end, end);
        }
    }

    /**
     * 文本断行扫描单元；Minecraft 格式码在格式文本模式下作为不可见单元处理。
     */
    static final class TextWrapUnit {

        final int start;
        final int end;
        final int codePoint;
        final boolean formattingCode;

        TextWrapUnit(int start, int end, int codePoint, boolean formattingCode) {
            this.start = Math.max(0, start);
            this.end = Math.max(this.start, end);
            this.codePoint = codePoint;
            this.formattingCode = formattingCode;
        }
    }
}
