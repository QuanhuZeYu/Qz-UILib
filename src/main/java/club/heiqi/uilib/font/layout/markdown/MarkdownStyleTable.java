package club.heiqi.uilib.font.layout.markdown;

import club.heiqi.uilib.font.layout.FontSizeLimits;

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

    // 表格样式只在包内登记，由 LayoutContent 的 TableUnit 快照送到 L2。
    // 长度类（内衬/边框厚）随 scaledDesignMetrics 换算，故不是 final；两个色值不随倍率。
    int tablePaddingXPx = 6;
    int tablePaddingYPx = 4;
    int tableBorderPx = 1;
    final int tableBorderArgb = 0x40FFFFFF;
    final int tableHeaderArgb = 0x18FFFFFF;

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

    // ==================== 设计量 -> 生效量（唯一换算面） ====================

    /**
     * 按用户级缩放倍率把本表的<b>长度类设计量</b>换算成生效量，返回<b>新表</b>（本表不变）。
     *
     * <p><b>为什么必须有这个出口</b>：本表的长度量（兜底基准字号、标题增量、行内 code 字号、
     * 引用步长与竖条宽、分隔线厚、表格内衬与边框厚）都是<b>设计量</b>，而 markdown 内容在场景里按
     * 生效字号渲染（设计值 × 倍率）。此前只有基准字号与标题增量有公共 setter，行内 code 字号等量
     * 既无 setter、也无统一换算口 —— 下游即使知道要换算也改不动，于是同一段内容里出现两种尺度：
     * 实测 fs=200 时行内 code 仍是 12px 而正文 28px（应为 24），fs=50 时 code 比正文还大
     * （独立审核 2026-09-19 实测）。这与页面「几何按设计值、渲染按生效值」的分叉同源：
     * <b>设计量必须有唯一的「设计 → 生效」换算面</b>，否则每个下游都会各自复发。</p>
     *
     * <p>只换算长度类量；颜色、标记字符串与开关与倍率无关，逐值保留。基准字号走字号域解析出口
     * （与 {@code SceneNode.effectiveFontSize()} 同式），其余长度量按 {@code Math.round} 取整。
     * {@code scale == 1.0f} 时逐值恒等 —— 默认倍率出图逐像素不变。</p>
     *
     * @param scale 用户级缩放倍率（{@code 1.0f} = 100%；负值按 0）
     * @return 换算后的新表（调用方持有；本表不被修改）
     */
    public MarkdownStyleTable scaledDesignMetrics(float scale) {
        float effective = Math.max(0.0f, scale);
        MarkdownStyleTable out = new MarkdownStyleTable();
        // 与倍率无关的量：逐值搬运。
        out.headingBold = headingBold;
        out.headingUnderline = headingUnderline;
        out.quoteItalic = quoteItalic;
        out.bulletMarker = bulletMarker;
        out.thematicBreakText = thematicBreakText;
        out.quoteTextColor = quoteTextColor;
        out.codeBackgroundColor = codeBackgroundColor;
        out.blockAccentArgb = blockAccentArgb;
        // 长度类：基准字号走字号域出口，其余按同一倍率取整。
        out.defaultFontSizePx = FontSizeLimits.effectiveFontSizePx(defaultFontSizePx, effective);
        out.codeFontSizePx = scaledPx(codeFontSizePx, effective);
        out.quoteIndentPx = scaledPx(quoteIndentPx, effective);
        out.quoteBarWidthPx = scaledPx(quoteBarWidthPx, effective);
        out.ruleThicknessPx = scaledPx(ruleThicknessPx, effective);
        out.tablePaddingXPx = scaledPx(tablePaddingXPx, effective);
        out.tablePaddingYPx = scaledPx(tablePaddingYPx, effective);
        out.tableBorderPx = scaledPx(tableBorderPx, effective);
        for (int i = 0; i < headingFontSizeDeltaPx.length; i++) {
            out.headingFontSizeDeltaPx[i] = scaledPx(headingFontSizeDeltaPx[i], effective);
        }
        return out;
    }

    /** 单个长度设计量 → 生效量（与字号出口同式取整；{@code 0} 恒等映射到 0）。 */
    private static int scaledPx(int designPx, float scale) {
        return Math.round(designPx * scale);
    }
}
