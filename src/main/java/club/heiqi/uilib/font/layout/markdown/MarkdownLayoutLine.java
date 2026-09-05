package club.heiqi.uilib.font.layout.markdown;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.font.layout.TextSegment;

/**
 * 块身份行（L1→L2 新接缝的唯一公共类型；2026-09-05 用户裁定方案乙，规划 §二之三 M7 重开注记）。
 *
 * <p><b>它解决什么</b>：裁定 B 把块级几何随块模型整体留在包内，接缝只剩扁平
 * {@code List<TextSegment>}——L2 因此「看得见字、看不见块」，引用嵌套缩进、真分隔线、
 * 围栏块底色三类块级几何无处表达（实机截图实证缺失）。本类型把<b>行粒度块身份</b>送进接缝：
 * 一条 = 一个逻辑行（按 \n 与 F6 占位边界切好），携带块类别（kind）、引用层级
 * （quoteLevel）、块归属（blockId）、行盒几何（leftInsetPx / indentStepPx / barWidthPx /
 * ruleThicknessPx）与两路装饰色（accentArgb 竖条·横线色，backgroundArgb 块底色）。</p>
 *
 * <p><b>它刻意不是什么</b>：不是块模型（无子树、无 children、无源偏移量——
 * {@code MarkdownBlock}/{@code MarkdownBlockParser} 恒 package-private，裁定 B 未重开的
 * 「不外开块树」这一半不变）；也不是文本通道（{@link #getSegments()} 携扁平接缝对应行的
 * 原段引用，可见文本一字不改——缩进<b>绝不</b>写成前导空格塞进段文本，列表项 F2 的
 * 「  」前导空格机制保持原样）。它是扁平的行序列，不可变。</p>
 *
 * <p><b>几何数值出处（G4 度量同源）</b>：indentStepPx/barWidthPx/ruleThicknessPx 与两路
 * 装饰色全部由 L1 在装配行时从 {@link MarkdownStyleTable} 的<b>包内登记项</b>解析
 * （沿用 F1 code 字号/衬底的包内登记先例）——{@code MarkdownStyleTable} 的公共方法面
 * 不因此膨胀，L2 侧零自设常量。</p>
 *
 * <p><b>双重角色</b>：L1 的 {@code MarkdownDocument.toLayoutLines} 产「逻辑行」（身份与
 * 缩进已解析）；L2 的 {@code MarkdownPainter.layoutLines} 把逻辑行按容器宽（已扣
 * {@link #getLeftInsetPx()}）切成「视觉行」——同一类型逐行透传身份，续行天然继承
 * （长引用折断的续行也带层级与竖条，与 M5「行为差」口径一致）。</p>
 *
 * <p>纯 JVM，不依赖 Minecraft/AWT（G2 锁）。</p>
 */
public final class MarkdownLayoutLine {

    /**
     * 行的块类别（封闭枚举；决定 L2 产哪种块级几何）。
     *
     * <p>引用身份不占枚举措——{@link #getQuoteLevel()} 独立正交（引用块内的 CODE 行 =
     * kind=CODE + quoteLevel&gt;0，竖条与底色同时成立）。标题/普通段对 L2 无块级几何差异，
     * 恒 TEXT。<b>列表行不在此列（2026-09-05 裁定 2，推翻本类旧版「列表对 L2 无块级几何
     * 差异」的说法）</b>：带标记的列表首行 = {@link Kind#LIST}，L2 据此把该块其余视觉行
     * （软折续行与同块懒延续行）的 {@link #getLeftInsetPx()} 追加「正文列宽」，让续行
     * 对齐标记行之后的正文列；列表专属偏移与引用缩进共用 leftInsetPx 这唯一行左偏移
     * 真相（{@code leftInsetPx = quoteLevel × indentStepPx + 列表续行的正文列}），不新增
     * 几何字段、不开样式表旋钮。</p>
     */
    public enum Kind {
        /** 普通文本行（段落/标题；无块级底几何）。 */
        TEXT,
        /**
         * 带列表标记的行（M10b，2026-09-05 裁定 2）：同一 {@code blockId} 内<b>只有首行</b>
         * 携带标记段（{@code segments.get(0)} 即标记——L1 续行按内嵌 \n 断行时继承行身份，
         * 标记段不复制）。L2 只给该块的第一个 LIST 逻辑行保持原 {@code leftInsetPx}，
         * 其余视觉行一律追加「正文列」= 该标记段实测宽（{@code ceil(推进宽)}），
         * 由 {@link #withLeftInsetPx(int)} 落进接缝；页面/出图/聊天读的都是这一个数。
         */
        LIST,
        /** 围栏代码块的一源行（同块各源行同 blockId，底色经归组合并）。 */
        CODE,
        /** 分隔线行（真横线：一条 ruleThicknessPx 高的 BACKGROUND）。 */
        THEMATIC_BREAK
    }

    /** 无归属块的行（空行占位）的 blockId。 */
    public static final int NO_BLOCK = -1;

    private final Kind kind;
    private final int quoteLevel;
    private final int blockId;
    private final List<TextSegment> segments;
    private final int leftInsetPx;
    private final int indentStepPx;
    private final int barWidthPx;
    private final int ruleThicknessPx;
    private final int accentArgb;
    private final int backgroundArgb;
    private final int blockContentWidthPx;

    /**
     * 全字段构造（L1 装配与 L2 视觉行复制共用；<b>签名自 M7 起冻结，存量消费者不受影响</b>）。
     *
     * <p>本构造器把 {@link #getBlockContentWidthPx()} 置 {@code 0 = 不适用}——块统一内容宽
     * 是<b>度量事实</b>，只有持度量服务的 L2（{@code MarkdownPainter.wrapLayoutLines}）能算，
     * L1 与手写样本一律取定义值 0；需要带上该值走 {@link #withBlockContentWidthPx(int)}。</p>
     *
     * @param kind            块类别（不可为 null）
     * @param quoteLevel      引用嵌套层数（&ge;0；0 = 不在引用内）
     * @param blockId         块归属 id（同一块的行同值；{@link #NO_BLOCK} = 无归属）
     * @param segments        行段流（可见文本；null 归一为空表）
     * @param leftInsetPx     行文本左偏移（L1 装配时 = quoteLevel × indentStepPx；M10b 起
     *                        L2 可对 LIST 块的续行视觉行经 {@link #withLeftInsetPx(int)}
     *                        追加正文列——它是接缝上唯一的「行左偏移」真相）
     * @param indentStepPx    每层引用水平步长（非引用行 0）
     * @param barWidthPx      引用竖条宽（非引用行 0）
     * @param ruleThicknessPx 分隔线厚（仅 THEMATIC_BREAK 行 &gt;0）
     * @param accentArgb      装饰色（竖条/横线共用；0 = 无）
     * @param backgroundArgb  块底色（CODE 行衬底；0 = 无）
     */
    public MarkdownLayoutLine(Kind kind, int quoteLevel, int blockId, List<TextSegment> segments,
            int leftInsetPx, int indentStepPx, int barWidthPx, int ruleThicknessPx,
            int accentArgb, int backgroundArgb) {
        this(kind, quoteLevel, blockId, segments, leftInsetPx, indentStepPx, barWidthPx,
                ruleThicknessPx, accentArgb, backgroundArgb, 0);
    }

    /**
     * 全字段构造（含块内容宽；唯一实现体，公共 10 参构造器与本类两个 {@code with*} 拷贝法共用）。
     *
     * @param blockContentWidthPx 块内统一内容宽（{@code >=0}；非 CODE 行恒 0 = 不适用）
     */
    private MarkdownLayoutLine(Kind kind, int quoteLevel, int blockId, List<TextSegment> segments,
            int leftInsetPx, int indentStepPx, int barWidthPx, int ruleThicknessPx,
            int accentArgb, int backgroundArgb, int blockContentWidthPx) {
        if (kind == null) {
            throw new IllegalArgumentException("kind 不能为空");
        }
        this.kind = kind;
        this.quoteLevel = Math.max(0, quoteLevel);
        this.blockId = blockId;
        this.segments = segments == null
                ? Collections.<TextSegment>emptyList()
                : Collections.unmodifiableList(new ArrayList<TextSegment>(segments));
        this.leftInsetPx = Math.max(0, leftInsetPx);
        this.indentStepPx = Math.max(0, indentStepPx);
        this.barWidthPx = Math.max(0, barWidthPx);
        this.ruleThicknessPx = Math.max(0, ruleThicknessPx);
        this.accentArgb = accentArgb;
        this.backgroundArgb = backgroundArgb;
        this.blockContentWidthPx = Math.max(0, blockContentWidthPx);
    }

    /**
     * 产空行（F6 块边界占位的行级形态：零段、无块身份、无几何）。
     *
     * @return 空行实例
     */
    public static MarkdownLayoutLine blank() {
        return new MarkdownLayoutLine(Kind.TEXT, 0, NO_BLOCK, Collections.<TextSegment>emptyList(),
                0, 0, 0, 0, 0, 0);
    }

    /**
     * 同身份换段流副本（M7 消费侧 §桥/链接化/换行的行级重挂点；身份与几何字段原样继承）。
     *
     * @param newSegments 新段流（可见文本与原段流逐字等值或由行内切段产生）
     * @return 携带相同 kind/quoteLevel/blockId/几何/装饰色的新行
     */
    public MarkdownLayoutLine withSegments(List<TextSegment> newSegments) {
        return new MarkdownLayoutLine(kind, quoteLevel, blockId, newSegments,
                leftInsetPx, indentStepPx, barWidthPx, ruleThicknessPx, accentArgb, backgroundArgb,
                blockContentWidthPx);
    }

    /**
     * 同身份换块内容宽副本（M8 块统一内容宽上收 L2 的唯一写入口；与 {@link #withSegments} 同形）。
     *
     * <p><b>为什么是拷贝法而不是追加公共构造器</b>：本类公共 10 参构造器已对外且有存量消费者
     * （L1 装配、门禁 {@code MarkdownChat3ParityTest} 合成行、L2 复制），再加一个 11 参重载会把
     * 「哪个是全字段入口」变成两代并存、并在下次加字段时继续膨胀；而块内容宽是 L2 换行<b>之后</b>
     * 才存在的派生量（同一行集内的最大值），本质就是「从已有行派生一行」，与 withSegments 同构。
     * 公共面因此只 +1 getter +1 拷贝法，构造器一个不加。</p>
     *
     * @param newBlockContentWidthPx 块内统一内容宽（{@code >=0}；{@code 0} = 不适用）
     * @return 携带相同 kind/quoteLevel/blockId/段流/几何/装饰色的新行
     */
    public MarkdownLayoutLine withBlockContentWidthPx(int newBlockContentWidthPx) {
        return new MarkdownLayoutLine(kind, quoteLevel, blockId, segments,
                leftInsetPx, indentStepPx, barWidthPx, ruleThicknessPx, accentArgb, backgroundArgb,
                newBlockContentWidthPx);
    }

    /**
     * 同身份换左偏移副本（M10b 列表续行对齐正文列的唯一写入口；与 {@link #withSegments}、
     * {@link #withBlockContentWidthPx} 同形的拷贝法）。
     *
     * <p><b>为什么走本方法而不是新字段/新旋钮</b>：{@code leftInsetPx} 是接缝上唯一的
     * 「行左偏移」真相——引用缩进（{@code quoteLevel × indentStepPx}）与列表正文列共用它，
     * L2 出图（SEGMENTS.left）、聊天面板与页面装配都读同一个数；正文列是<b>度量事实</b>，
     * 只有持度量服务的 L2 算得出，故由 L2 在折行时以本方法把续行偏移改写为
     * {@code 原 inset + ceil(标记段宽)}。公共 10 参构造器签名自 M7 起冻结，不因它膨胀。</p>
     *
     * @param newLeftInsetPx 新行左偏移（{@code >=0}）
     * @return 携带相同 kind/quoteLevel/blockId/段流/其余几何与装饰色的新行
     */
    public MarkdownLayoutLine withLeftInsetPx(int newLeftInsetPx) {
        return new MarkdownLayoutLine(kind, quoteLevel, blockId, segments,
                newLeftInsetPx, indentStepPx, barWidthPx, ruleThicknessPx, accentArgb,
                backgroundArgb, blockContentWidthPx);
    }

    /** @return 块类别 */
    public Kind getKind() {
        return kind;
    }

    /** @return 引用嵌套层数（0 = 不在引用内） */
    public int getQuoteLevel() {
        return quoteLevel;
    }

    /** @return 块归属 id（供 L2 把围栏底色合并为覆盖全部显示行的单矩形） */
    public int getBlockId() {
        return blockId;
    }

    /** @return 行段流（不可变；与扁平接缝对应行的段一字相同，永不携带缩进空格） */
    public List<TextSegment> getSegments() {
        return segments;
    }

    /**
     * @return 行文本左偏移（px；接缝唯一「行左偏移」真相——L1 写引用缩进
     *         {@code quoteLevel × indentStepPx}，L2 可对 LIST 块续行视觉行追加正文列，
     *         L2 出图/聊天面板/演示页三侧共读此值）
     */
    public int getLeftInsetPx() {
        return leftInsetPx;
    }

    /** @return 每层引用水平步长（px；非引用行 0） */
    public int getIndentStepPx() {
        return indentStepPx;
    }

    /** @return 引用竖条宽（px；非引用行 0） */
    public int getBarWidthPx() {
        return barWidthPx;
    }

    /** @return 分隔线厚度（px；仅 THEMATIC_BREAK 行 &gt;0） */
    public int getRuleThicknessPx() {
        return ruleThicknessPx;
    }

    /** @return 装饰色（竖条/横线共用 ARGB；0 = 无） */
    public int getAccentArgb() {
        return accentArgb;
    }

    /** @return 块底色 ARGB（0 = 无衬底） */
    public int getBackgroundArgb() {
        return backgroundArgb;
    }

    /**
     * 块内统一内容宽（UI 像素）——围栏底色的<b>唯一宽度真相</b>，三表面共读此值。
     *
     * <table border="1">
     *   <caption>取值语义（无未定义值）</caption>
     *   <tr><th>行类别</th><th>取值</th></tr>
     *   <tr><td>{@code CODE}</td><td>该块（同 {@code blockId}）全部 CODE <b>视觉行</b>自身文字宽
     *       （{@code ceil(段流推进宽)}）的<b>最大值</b>，下限 1（纯空行围栏也画得出一条可辨识底色）。
     *       同块各 CODE 行因此<b>彼此相等</b>，且 {@code >=} 每行自身文字宽。</td></tr>
     *   <tr><td>{@code TEXT} / {@code THEMATIC_BREAK}</td><td>恒 {@code 0} = <b>不适用</b>
     *       （这两类无「块内统一宽」概念；行宽取自身实测）。</td></tr>
     *   <tr><td>L1 逻辑行（未经 L2 换行）</td><td>恒 {@code 0} = <b>未算</b>——块内容宽是度量事实，
     *       只有持 {@code TextLayoutService} 的 L2 能产；L1 纯解析层零度量。</td></tr>
     * </table>
     *
     * <p>由 L2 {@code MarkdownPainter.wrapLayoutLines} 在折行时按 blockId 聚合写入
     * （M8 上收，规划 §二之七·续 第 9 条）。L2 出图路的合并 BACKGROUND 矩形宽与本值<b>恒等</b>，
     * 由 {@code MarkdownBlockContentWidthLockTest} 机器锁死（配独立 oracle 与反同义反复地板）；
     * 聊天面板与 devtools 页一律读本 getter，不得自算第二份块宽。</p>
     *
     * @return 块内统一内容宽（{@code >=0}；非 CODE 行 0 = 不适用）
     */
    public int getBlockContentWidthPx() {
        return blockContentWidthPx;
    }

    @Override
    public String toString() {
        return "MarkdownLayoutLine(" + kind + " q" + quoteLevel + " b" + blockId
                + " inset=" + leftInsetPx + " segs=" + segments.size() + ")";
    }
}
