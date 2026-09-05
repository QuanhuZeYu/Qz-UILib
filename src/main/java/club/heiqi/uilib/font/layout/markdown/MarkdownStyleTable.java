package club.heiqi.uilib.font.layout.markdown;

/**
 * 块级样式/排版表（D3 最小公共面中「一个样式/排版表类型」的落点）。
 *
 * <p>设计见《规划-通用Markdown渲染器.md》§二 / §五 D3、G4 与风险 4。本表是 markdown
 * 块级排版数值的<b>唯一登记面</b>：L1 解析与 {@link MarkdownDocument#toSegments} 扁平化
 * 不读取任何散落的私有常量；字号相关数值仅在能解析出基准字号时写入
 * {@link club.heiqi.uilib.font.layout.TextStyle#setFontSizePx(int)}，基准不可解析
 * （{@code getDefaultFontSizePx() == 0} 且基础样式未显式指定）时保持继承渲染器基准，
 * 满足 G4「度量同源」。</p>
 *
 * <p><b>裁定 B（M2-narrow）记死在案</b>：块级几何（列表缩进/引用缩进/块间距 px）
 * 刻意不进本期公共面——{@code toSegments()} 的接缝是 {@code List<TextSegment>}，而
 * {@code TextSegment} 只有 text/style/latexSource 三通道、{@code TextStyle} 无任何行/块几何
 * 通道，块边界在扁平化时已被抹掉，这组旋钮在当前形状下没有消费者；待 M3 需要块级
 * 几何再裁「块模型是否进公共面」——加方法是兼容变更、删方法是破坏变更，故先删后加。</p>
 *
 * <p>语义约定：字号增量 {@code 0} = 不改变继承字号；{@code bulletMarker} 空串 = 列表标记
 * 不输出；{@code thematicBreakText} 空串 = 分隔线在扁平文本流中不输出占位文本；
 * {@code quoteTextColor} 为 {@code 0} = 引用块不改色、继承调用方基础样式。</p>
 *
 * <p>实例在构造期可变异，传给 {@code toSegments} 后应视为只读；线程安全仅在该约定下成立。</p>
 */
public final class MarkdownStyleTable {

    /** 分隔线默认文本长度（个 ASCII '-'），仅用于扁平文本流展示，绘制宽度由 L2 盒模型决定。 */
    private static final int DEFAULT_BREAK_LENGTH = 36;

    /**
     * 引用文字默认色：chat3 出货口径的次级色（{@code ChatMarkdownSettings.textSecondaryArgb}
     * 现值 0xFF9AA0A8，见 {@code ChatMessageList.java:891-897} 引用行降色）。M4-fix F3 登记。
     */
    private static final int DEFAULT_QUOTE_TEXT_ARGB = 0xFF9AA0A8;

    /**
     * 行内 code 段默认字号：chat3 出货口径 font-code 12px
     * （{@code ChatCodeSpanSplitter.java:107-112} 的 {@code getCodeFontSizePx()} 现值）。
     * 值进本表即满足 G4「度量同源」——L1 不读散落常量。
     */
    private static final int DEFAULT_CODE_FONT_SIZE_PX = 12;

    /**
     * 行内 code 段默认衬底色：chat3 出货口径（{@code ChatMarkdownSettings.codeBackgroundArgb}
     * 现值 0x26FFFFFF，设计稿 §3.5）。M4-fix F1 登记。
     */
    private static final int DEFAULT_CODE_BACKGROUND_ARGB = 0x26FFFFFF;

    private boolean headingBold = true;
    private boolean headingUnderline;
    private boolean quoteItalic;
    private final int[] headingFontSizeDeltaPx = new int[6];
    private int defaultFontSizePx;
    private String bulletMarker = "\u2022";
    private String thematicBreakText = repeat('-', DEFAULT_BREAK_LENGTH);
    private int quoteTextColor = DEFAULT_QUOTE_TEXT_ARGB;
    /** 包内登记：行内 code 段字号/衬底色（M4-fix F1）；本期不外开公共旋钮（加方法是兼容变更）。 */
    private int codeFontSizePx = DEFAULT_CODE_FONT_SIZE_PX;
    private int codeBackgroundColor = DEFAULT_CODE_BACKGROUND_ARGB;
    /**
     * 包内登记：块级几何旋钮（M7 方案乙，2026-09-05）——引用步长/竖条宽、分隔线厚与装饰色。
     *
     * <p>数值只经 {@code MarkdownDocument.toLayoutLines} 解析进 {@link MarkdownLayoutLine}
     * 的几何字段送达 L2；{@code MarkdownStyleTable} 的<b>公共</b>方法面不因此膨胀
     * （裁定 B 重开注记，规划 §二之三）。默认值取 chat3 出货口径：每层缩进 8 = 竖条 2 + 间隙 6
     * （{@code ChatMessageList} QUOTE_BAR_WIDTH_PX/QUOTE_GAP_PX），竖条色 0x40FFFFFF
     * （{@code ChatMarkdownSettings.getQuoteBarArgb}），分隔线厚 1px（规划 §二之三 M7：
     * 真横线 = 一条 1px 高的 BACKGROUND）。</p>
     */
    private int quoteIndentPx = 8;
    private int quoteBarWidthPx = 2;
    private int ruleThicknessPx = 1;
    private int blockAccentArgb = 0x40FFFFFF;

    /**
     * 创建默认表（行为规格即此组默认值，块级 javadoc 与测试矩阵按它钉死）。
     *
     * @return 默认样式/排版表
     */
    public static MarkdownStyleTable defaults() {
        return new MarkdownStyleTable();
    }

    private static String repeat(char ch, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(ch);
        }
        return builder.toString();
    }

    /**
     * 拷贝当前表。
     *
     * @return 数值与字符串全部等值的独立副本
     */
    public MarkdownStyleTable copy() {
        MarkdownStyleTable out = new MarkdownStyleTable();
        out.headingBold = headingBold;
        out.headingUnderline = headingUnderline;
        out.quoteItalic = quoteItalic;
        for (int i = 0; i < headingFontSizeDeltaPx.length; i++) {
            out.headingFontSizeDeltaPx[i] = headingFontSizeDeltaPx[i];
        }
        out.defaultFontSizePx = defaultFontSizePx;
        out.bulletMarker = bulletMarker;
        out.thematicBreakText = thematicBreakText;
        out.quoteTextColor = quoteTextColor;
        out.codeFontSizePx = codeFontSizePx;
        out.codeBackgroundColor = codeBackgroundColor;
        out.quoteIndentPx = quoteIndentPx;
        out.quoteBarWidthPx = quoteBarWidthPx;
        out.ruleThicknessPx = ruleThicknessPx;
        out.blockAccentArgb = blockAccentArgb;
        return out;
    }

    /** @return 标题正文是否叠加粗体（默认 true） */
    public boolean isHeadingBold() {
        return headingBold;
    }

    /** @param headingBold 标题正文是否叠加粗体 */
    public void setHeadingBold(boolean headingBold) {
        this.headingBold = headingBold;
    }

    /** @return 标题正文是否叠加下划线（默认 false，交由渲染器决定） */
    public boolean isHeadingUnderline() {
        return headingUnderline;
    }

    /** @param headingUnderline 标题正文是否叠加下划线 */
    public void setHeadingUnderline(boolean headingUnderline) {
        this.headingUnderline = headingUnderline;
    }

    /** @return 引用块正文是否叠加斜体（默认 false） */
    public boolean isQuoteItalic() {
        return quoteItalic;
    }

    /** @param quoteItalic 引用块正文是否叠加斜体 */
    public void setQuoteItalic(boolean quoteItalic) {
        this.quoteItalic = quoteItalic;
    }

    /**
     * 取指定级别的标题字号增量。
     *
     * @param level 标题级别（越界自动收窄到 1..6）
     * @return 像素增量；0 = 继承不改变
     */
    public int getHeadingFontSizeDeltaPx(int level) {
        return headingFontSizeDeltaPx[indexOf(level)];
    }

    /**
     * 设置指定级别的标题字号增量。
     *
     * @param level 标题级别（越界自动收窄到 1..6）
     * @param delta 像素增量；0 = 继承不改变
     */
    public void setHeadingFontSizeDeltaPx(int level, int delta) {
        headingFontSizeDeltaPx[indexOf(level)] = delta;
    }

    private static int indexOf(int level) {
        return Math.min(6, Math.max(1, level)) - 1;
    }

    /** @return 扁平文本流计算标题绝对字号时的兜底基准；0 = 由渲染器决定（不写死字号） */
    public int getDefaultFontSizePx() {
        return defaultFontSizePx;
    }

    /** @param defaultFontSizePx 兜底基准字号；0 = 由渲染器决定 */
    public void setDefaultFontSizePx(int defaultFontSizePx) {
        this.defaultFontSizePx = Math.max(0, defaultFontSizePx);
    }

    /** @return 无序列表扁平文本流符号（默认 U+2022 圆点，与 chat3 现行视觉对齐）；空串 = 不输出 */
    public String getBulletMarker() {
        return bulletMarker;
    }

    /** @param bulletMarker 无序列表符号；null 归一为空串（不输出） */
    public void setBulletMarker(String bulletMarker) {
        this.bulletMarker = bulletMarker == null ? "" : bulletMarker;
    }

    /** @return 分隔线扁平文本流文本（默认 36 个 '-'）；空串 = 不输出 */
    public String getThematicBreakText() {
        return thematicBreakText;
    }

    /** @param thematicBreakText 分隔线文本；null 归一为空串（不输出） */
    public void setThematicBreakText(String thematicBreakText) {
        this.thematicBreakText = thematicBreakText == null ? "" : thematicBreakText;
    }

    /**
     * 引用块正文色（M4-fix F3 新增旋钮）。
     *
     * <p>chat3 现行行为：引用行剥 {@code "> "} 后整行降为次级色
     * （{@code ChatMessageList.java:891-897} → {@code getTextSecondaryArgb()}），默认值与本旋钮
     * 默认值同为 {@code 0xFF9AA0A8}。由 {@code MarkdownDocument.toSegments} 在 QUOTE 块上应用。</p>
     *
     * @return 引用正文色（ARGB）；{@code 0} = 不改色，继承调用方基础样式
     */
    public int getQuoteTextColor() {
        return quoteTextColor;
    }

    /**
     * 设置引用正文色。
     *
     * @param quoteTextColor ARGB 引用色；{@code 0} = 关闭降色（继承基础样式）；
     *                       非 0 时高位 alpha 缺失（&lt; 0x01000000 量级）按不透明补全
     */
    public void setQuoteTextColor(int quoteTextColor) {
        this.quoteTextColor = quoteTextColor == 0 ? 0
                : ((quoteTextColor >>> 24) == 0 ? (quoteTextColor | 0xFF000000) : quoteTextColor);
    }

    /**
     * 行内 code 段字号（包内登记，非公共面）；{@code 0} = 继承渲染器基准。
     *
     * <p>M4-fix F1：chat3 出货口径 12px（{@code ChatCodeSpanSplitter.java:107-112}），
     * 数值恒由 L1 在吃反引号时对 code 段 {@code setFontSizePx} 写入，公共面本期不外开旋钮
     * （未发布类上加方法是兼容变更，需要时另裁）。</p>
     */
    int getCodeFontSizePx() {
        return codeFontSizePx;
    }

    /** 行内 code 段衬底色（包内登记，非公共面）；{@code 0} = 无衬底。 */
    int getCodeBackgroundColor() {
        return codeBackgroundColor;
    }

    /** 每层引用水平步长（包内登记，非公共面；M7）：默认 8 = 竖条 2 + 间隙 6。 */
    int getQuoteIndentPx() {
        return quoteIndentPx;
    }

    /** 引用竖条宽（包内登记，非公共面；M7）：默认 2，与 chat3 QUOTE_BAR_WIDTH_PX 同值。 */
    int getQuoteBarWidthPx() {
        return quoteBarWidthPx;
    }

    /** 分隔线厚度（包内登记，非公共面；M7）：默认 1px（真横线 = 1px 高 BACKGROUND）。 */
    int getRuleThicknessPx() {
        return ruleThicknessPx;
    }

    /** 块级装饰色（包内登记，非公共面；M7）：引用竖条与分隔线共用，默认 0x40FFFFFF。 */
    int getBlockAccentArgb() {
        return blockAccentArgb;
    }
}
