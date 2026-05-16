package club.heiqi.uilib.font.util;

import java.util.Arrays;

import club.heiqi.uilib.font.page.GlyphRuntimeTables;

/**
 * 按码点缓存 AWT 需要的单字符字符串。
 */
public final class CodepointTextCache {

    private static final String[] BMP_TEXTS = new String[Character.MAX_VALUE + 1];
    private static final int SUPPLEMENTARY_CACHE_SIZE = 4096;
    private static final int[] SUPPLEMENTARY_CODEPOINTS = new int[SUPPLEMENTARY_CACHE_SIZE];
    private static final String[] SUPPLEMENTARY_TEXTS = new String[SUPPLEMENTARY_CACHE_SIZE];

    static {
        Arrays.fill(SUPPLEMENTARY_CODEPOINTS, GlyphRuntimeTables.LOCATION_NOT_READY);
    }

    private CodepointTextCache() {}

    /**
     * 获取指定码点对应的单字符字符串。
     *
     * @param codepoint 字符码点
     * @return 单字符字符串
     */
    public static String getText(int codepoint) {
        if (!GlyphRuntimeTables.isValidCodepoint(codepoint)) {
            return "?";
        }
        if (codepoint >= 0 && codepoint <= Character.MAX_VALUE) {
            String text = BMP_TEXTS[codepoint];
            if (text == null) {
                text = String.valueOf((char) codepoint);
                BMP_TEXTS[codepoint] = text;
            }
            return text;
        }

        int index = codepoint & (SUPPLEMENTARY_CACHE_SIZE - 1);
        synchronized (SUPPLEMENTARY_TEXTS) {
            if (SUPPLEMENTARY_CODEPOINTS[index] == codepoint && SUPPLEMENTARY_TEXTS[index] != null) {
                return SUPPLEMENTARY_TEXTS[index];
            }
            String text = new String(Character.toChars(codepoint));
            SUPPLEMENTARY_CODEPOINTS[index] = codepoint;
            SUPPLEMENTARY_TEXTS[index] = text;
            return text;
        }
    }
}
