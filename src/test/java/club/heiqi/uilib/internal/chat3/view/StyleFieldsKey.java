package club.heiqi.uilib.internal.chat3.view;

import club.heiqi.uilib.font.layout.TextStyle;

/**
 * {@link TextStyle} 全字段逐位字符串键（C6b 迁移等价锁 / 桥 no-op 实证锁的比较尺）。
 *
 * <p>字段集 = {@code StyleValues.same} 的视觉/样式轴，<b>不含 {@code colorExplicit}</b>：
 * C6b 锚点语义下该位有系统性差异——甲（span 流）只在宿主 §/组件显式着色位置 true，
 * 乙′（清洗+桥）因 caller 底色经 {@code setColor} 恒 true。该位在 chat3 渲染链零消费点
 * （本仓消费点只有 {@code RichTextTagParser}/{@code TextContentModeStrategy} 的 vanilla
 * 合并尺与 {@code StyleValues} 分组粒度，均不触像素），其落点由转换器单元锁单独照登，
 * 不进甲↔乙′ 等值尺（规划 §二之八 C6b 细账差异清单第 7 条）。</p>
 */
final class StyleFieldsKey {

    private StyleFieldsKey() {
    }

    /** 全视觉/样式字段拼键（latex 位不在此尺内，由段级 isLatex/latexSource 断言）。 */
    static String of(TextStyle s) {
        StringBuilder b = new StringBuilder(96);
        b.append("c=").append(Integer.toHexString(s.getColor())).append('|');
        b.append("ft=").append(s.getFontType()).append('|');
        b.append("rnd=").append(s.isRandomStyle()).append('|');
        b.append("u=").append(s.isUnderline()).append('|');
        b.append("s=").append(s.isStrikethrough()).append('|');
        b.append("i=").append(s.isItalic()).append('|');
        b.append("px=").append(s.getFontSizePx()).append('|');
        b.append("mk=").append(Integer.toHexString(s.getMarkColor())).append('|');
        b.append("sup=").append(s.isSuperscript()).append('|');
        b.append("sub=").append(s.isSubscript()).append('|');
        b.append("ls=").append(Float.floatToRawIntBits(s.getLetterSpacing())).append('|');
        b.append("link=").append(s.getLink()).append('|');
        b.append("code=").append(s.isCodeSpan()).append('|');
        b.append("cbg=").append(Integer.toHexString(s.getCodeBackgroundColor()));
        return b.toString();
    }
}
