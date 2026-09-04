package club.heiqi.uilib.font.layout.markdown;

/**
 * 块级样式/排版表（D3 最小公共面中「一个样式/排版表类型」的落点）。
 *
 * <p>设计见《规划-通用Markdown渲染器.md》§二 / §五 D3、G4 与风险 4。本表是 markdown
 * 块级排版数值的<b>唯一登记面</b>：L1 解析与 {@link MarkdownDocument#toSegments} 扁平化
 * 不读取任何散落的私有常量；字号相关数值仅在能解析出基准字号时写入
 * {@link club.heiqi.uilib.font.layout.TextStyle#setFontSizePx(int)}，基准不可解析
 * （{@code getDefaultFontSizePx() == 0} 且基础样式未显式指定）时保持继承渲染器基准，
 * 满足 G4「度量同源」。缩进/间距 px 不被 L1 消费，供 L2 绘制层（M3）盒模型读取。</p>
 *
 * <p>语义约定：字号增量 {@code 0} = 不改变继承字号；{@code bulletMarker} 空串 = 列表标记
 * 不输出；{@code thematicBreakText} 空串 = 分隔线在扁平文本流中不输出占位文本。</p>
 *
 * <p>实例在构造期可变异，传给 {@code toSegments} 后应视为只读；线程安全仅在该约定下成立。</p>
 */
public final class MarkdownStyleTable {

    /** 分隔线默认文本长度（个 ASCII '-'），仅用于扁平文本流展示，绘制宽度由 L2 盒模型决定。 */
    private static final int DEFAULT_BREAK_LENGTH = 36;

    private boolean headingBold = true;
    private boolean headingUnderline;
    private boolean quoteItalic;
    private final int[] headingFontSizeDeltaPx = new int[6];
    private int defaultFontSizePx;
    private String bulletMarker = "\u2022";
    private String thematicBreakText = repeat('-', DEFAULT_BREAK_LENGTH);
    private int listIndentPx = 10;
    private int quoteIndentPx = 8;
    private int blockSpacingPx = 4;

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
        out.listIndentPx = listIndentPx;
        out.quoteIndentPx = quoteIndentPx;
        out.blockSpacingPx = blockSpacingPx;
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

    /** @return 每级列表缩进 px（L2 绘制层消费；L1 解析不读取） */
    public int getListIndentPx() {
        return listIndentPx;
    }

    /** @param listIndentPx 每级列表缩进 px，负值归 0 */
    public void setListIndentPx(int listIndentPx) {
        this.listIndentPx = Math.max(0, listIndentPx);
    }

    /** @return 每级引用缩进 px（L2 绘制层消费；L1 解析不读取） */
    public int getQuoteIndentPx() {
        return quoteIndentPx;
    }

    /** @param quoteIndentPx 每级引用缩进 px，负值归 0 */
    public void setQuoteIndentPx(int quoteIndentPx) {
        this.quoteIndentPx = Math.max(0, quoteIndentPx);
    }

    /** @return 块间距 px（L2 绘制层消费；L1 解析不读取） */
    public int getBlockSpacingPx() {
        return blockSpacingPx;
    }

    /** @param blockSpacingPx 块间距 px，负值归 0 */
    public void setBlockSpacingPx(int blockSpacingPx) {
        this.blockSpacingPx = Math.max(0, blockSpacingPx);
    }
}
