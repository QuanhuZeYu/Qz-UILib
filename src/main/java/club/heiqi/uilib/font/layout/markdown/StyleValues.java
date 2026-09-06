package club.heiqi.uilib.font.layout.markdown;

import club.heiqi.uilib.font.layout.TextStyle;

/**
 * {@link TextStyle} 的值相等判定（包内，C6a）。{@code TextStyle} 公共面未实现 equals
 * （L0 既有承诺，不为 L1 扩张），本类只做读端深比较，字段集与 {@code TextStyle#copy()}
 * 逐一对齐——copy 保什么，这里就比什么，两把尺同源。
 *
 * <p>消费点：span 流的相邻同值合并（切行/拼体时相邻样式锚点值相等 ⇒ 并为同一区间，
 * 保证「单 span 白底」文档的段粒度与 String 路逐位一致）、行内跨 span 连续扫描的
 * 样式边界判定。</p>
 */
final class StyleValues {

    private StyleValues() {
    }

    /**
     * 两个样式的值是否逐字段相等（同引用短路为 true）。
     *
     * @param a 样式（可为 null，null 只与 null 相等）
     * @param b 样式（可为 null）
     * @return 深比较结果
     */
    static boolean same(TextStyle a, TextStyle b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.getColor() == b.getColor()
                && a.getFontType() == b.getFontType()
                && a.isColorExplicit() == b.isColorExplicit()
                && a.isRandomStyle() == b.isRandomStyle()
                && a.isUnderline() == b.isUnderline()
                && a.isStrikethrough() == b.isStrikethrough()
                && a.isItalic() == b.isItalic()
                && a.getFontSizePx() == b.getFontSizePx()
                && a.getMarkColor() == b.getMarkColor()
                && a.isSuperscript() == b.isSuperscript()
                && a.isSubscript() == b.isSubscript()
                && Float.compare(a.getLetterSpacing(), b.getLetterSpacing()) == 0
                && linkSame(a.getLink(), b.getLink())
                && a.isCodeSpan() == b.isCodeSpan()
                && a.getCodeBackgroundColor() == b.getCodeBackgroundColor();
    }

    private static boolean linkSame(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
