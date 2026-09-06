package club.heiqi.uilib.internal.chat3.view;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.font.layout.markdown.MarkdownDocument;
import club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine;
import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.internal.chat3.viewmodel.ChatUrlLinkifier;

/**
 * C6b 迁移等价锁（任务书第二部分点名交付）：同一批含 § 语料，甲 路径（§ → 样式锚点
 * span 流 → {@code parseSpans}）逐段文本 + 样式 对账 乙′ 路径（{@code stripLeadingSectionCodes}
 * → {@code parse(String)} → {@code bridgeSectionCodes} 后置桥）。
 *
 * <p>oracle 直连<b>在退役中的生产函数</b>（strip/bridge；C6b·3 批机制与 oracle 依赖同批处理：
 * 机制删除时本锁切至测试侧逐句照搬副本，等值结论不随生产删除而失效）。比较尺 =
 * {@link StyleFieldsKey}（全视觉/样式字段，逐段文本 + latex 位/源 + 行身份
 * kind/quoteLevel/headingLevel；<b>colorExplicit 除外</b>——C6b 锚点语义的系统性差异，
 * 差异清单第 7 条，落点由转换器单元锁单独照登）。等价项逐段直断；结构差项逐条给
 * 甲/乙′ 双期望并解释成因； latex/code span 两项<b>不可能等价</b>（转换先于 markdown，
 * 跳过这两域需第二套块扫描=违宪+G3 雷区），双侧期望写死留证。</p>
 */
public class ChatMarkdownSectionSpanMigrationLockTest {

    private static final int WHITE = 0xFFFFFFFF;
    private static final int RED = 0xFFFF5555;
    private static final int GREEN = 0xFF55FF55;

    private static TextStyle base() {
        TextStyle s = new TextStyle();
        s.setColor(WHITE);
        return s;
    }

    // ==================== 两条路径 ====================

    /** 甲：§ → span 流 → parseSpans（生产入口，缓存同路）。 */
    private static List<MarkdownLayoutLine> jia(String messageText) {
        return new ChatMarkdownPipeline().logicalForTest(messageText, WHITE);
    }

    /** 乙′：预清洗 + 输出后置桥（退役机制逐点对齐旧 logicalCached 处理序）。 */
    private static List<MarkdownLayoutLine> bPrime(String messageText) {
        String cleaned = ChatMarkdownPipeline.stripLeadingSectionCodes(
                messageText == null ? "" : messageText);
        List<MarkdownLayoutLine> logical = MarkdownDocument.parse(cleaned)
                .toLayoutLines(ChatMarkdownPipeline.chatStyleTable(), base());
        List<MarkdownLayoutLine> out = new ArrayList<MarkdownLayoutLine>(logical.size());
        for (int i = 0; i < logical.size(); i++) {
            MarkdownLayoutLine line = logical.get(i);
            List<TextSegment> segs = ChatMarkdownPipeline.bridgeSectionCodes(line.getSegments());
            segs = ChatUrlLinkifier.linkify(segs, ChatMarkdownSettings.getLinkArgb());
            out.add(line.withSegments(segs));
        }
        return out;
    }

    // ==================== 断言工具 ====================

    private static String visible(MarkdownLayoutLine line) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < line.getSegments().size(); i++) {
            TextSegment s = line.getSegments().get(i);
            b.append(s.isLatex() ? "\u27e6" + s.getLatexSource() + "\u27e7" : s.getText());
        }
        return b.toString();
    }

    private static String lineDump(List<MarkdownLayoutLine> lines) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            MarkdownLayoutLine line = lines.get(i);
            b.append('#').append(i).append(' ')
             .append(line.getKind()).append("/q").append(line.getQuoteLevel())
             .append("/h").append(line.getHeadingLevel()).append(' ')
             .append(visible(line)).append('\n');
            for (int s = 0; s < line.getSegments().size(); s++) {
                TextSegment seg = line.getSegments().get(s);
                b.append("  seg").append(s).append(' ')
                 .append(seg.isLatex() ? "LATEX[" + seg.getLatexSource() + "]"
                         : "TXT[" + seg.getText() + "] ")
                 .append(StyleFieldsKey.of(seg.getStyle())).append('\n');
            }
        }
        return b.toString();
    }

    /** 逐段等值（文本 + latex 源 + 全视觉样式字段）+ 行身份等值；两侧段粒度也必须一致。 */
    private static void assertEquivalent(String label, String msg) {
        List<MarkdownLayoutLine> a = jia(msg);
        List<MarkdownLayoutLine> c = bPrime(msg);
        Assert.assertEquals(label + "（行数）", c.size(), a.size());
        for (int i = 0; i < a.size(); i++) {
            MarkdownLayoutLine la = a.get(i);
            MarkdownLayoutLine lc = c.get(i);
            Assert.assertEquals(label + " 行" + i + " kind", lc.getKind(), la.getKind());
            Assert.assertEquals(label + " 行" + i + " quoteLevel", lc.getQuoteLevel(),
                    la.getQuoteLevel());
            Assert.assertEquals(label + " 行" + i + " headingLevel", lc.getHeadingLevel(),
                    la.getHeadingLevel());
            Assert.assertEquals(label + " 行" + i + " 段数", lc.getSegments().size(),
                    la.getSegments().size());
            for (int s = 0; s < la.getSegments().size(); s++) {
                TextSegment sa = la.getSegments().get(s);
                TextSegment sc = lc.getSegments().get(s);
                Assert.assertEquals(label + " 行" + i + " 段" + s + " latex位",
                        sc.isLatex(), sa.isLatex());
                Assert.assertEquals(label + " 行" + i + " 段" + s + " latex源",
                        sc.getLatexSource(), sa.getLatexSource());
                Assert.assertEquals(label + " 行" + i + " 段" + s + " 文本",
                        sc.getText(), sa.getText());
                Assert.assertEquals(label + " 行" + i + " 段" + s + " 样式（"
                        + lineDump(c) + " vs " + lineDump(a) + "）",
                        StyleFieldsKey.of(sc.getStyle()), StyleFieldsKey.of(sa.getStyle()));
            }
        }
    }

    // ==================== 等价项（逐段文本+样式直断） ====================

    /** 行中色 §c甲§f乙（任务书点名）：桥切分与锚点切分逐段同形。 */
    @Test
    public void midLineColorPairMigratesEquivalently() {
        assertEquivalent("行中色 §c甲§f乙", "\u00a7c\u7532\u00a7f\u4e59");
    }

    /** 引用内色 > §c甲（任务书点名；C6b 主裁定「服务端色优先块级色」两路同值）。 */
    @Test
    public void colorInsideQuoteMigratesEquivalently() {
        assertEquivalent("引用内 > §c甲", "> \u00a7c\u7532");
    }

    /** 围栏内普通 §（非标记形）：乙′ 不剥+桥上色 = 甲 锚点上色，围栏字面两侧同守。 */
    @Test
    public void plainCodeLineInFenceMigratesEquivalently() {
        String tick = String.valueOf((char) 0x60);
        String fence = tick + tick + tick;
        assertEquivalent("围栏内 §cfoo", fence + "\n\u00a7cfoo\n" + fence);
    }

    /** §r 重置（任务书点名）：甲 resetAll→非显式底色、乙′ 桥 resetAll→段起色；
     *  引用外两色同（bubble），§c甲 段两侧同红。 */
    @Test
    public void sectionResetMigratesEquivalently() {
        assertEquivalent("§c甲§r乙", "\u00a7c\u7532\u00a7r\u4e59");
    }

    /** §r 在引用内（乙′ resetAll(段起色=引用色) vs 甲 非显式→引用色兜底）：色等值，
     *  仅 colorExplicit 系统差（差异清单第 7 条，StyleFieldsKey 不含该位）。 */
    @Test
    public void sectionResetInsideQuoteMatchesOnColors() {
        assertEquivalent("> §c甲§r乙", "> \u00a7c\u7532\u00a7r\u4e59");
    }

    /** 连续码 §c§l（任务书点名）：逐对消费同源，红+粗落点一致。 */
    @Test
    public void consecutiveCodesMigrateEquivalently() {
        assertEquivalent("§c§l甲", "\u00a7c\u00a7l\u7532");
    }

    /** 未闭合孤立 §（任务书点名）：两侧都不消费、字面保留。 */
    @Test
    public void loneTrailingSectionMigratesEquivalently() {
        assertEquivalent("甲§", "\u7532\u00a7");
    }

    /** 强调内色 **a§cb**：乙′ 桥的 resetFlags 落回 baseFontType=BOLD（L0 setFontType 同步
     *  基位语义，桥锁 bridgePreservesMarkdownBits... 同款）、甲 bold 层后叠 ⇒ 两侧 b 段
     *  同为红+粗，逐段等值。 */
    @Test
    public void colorInsideEmphasisMigratesEquivalently() {
        assertEquivalent("**a§cb**", "**a\u00a7cb**");
    }

    /** 复合「§a- §citem」（行首码+行中码）：甲 的 §a 绿停在被列表标记吃掉的「- 」区间，
     *  可见形与乙′ 同为「• item 红」⇒ 逐段等值。 */
    @Test
    public void leadingAndMidCodesOnListLineMigrateEquivalently() {
        assertEquivalent("§a- §citem", "\u00a7a- \u00a7citem");
    }

    // ==================== 差异项（双期望写死 + 逐条解释，不用容差） ====================

    /** 行首色+列表标记 §a- x（任务书点名）：乙′ 命中块标记 ⇒ 色随消费丢弃（C4 前同款）；
     *  甲 色进锚点 ⇒ 服务端绿存活。结构（LIST/标记段/行数）不变，正文色为有意差异。 */
    @Test
    public void leadingColorOnListMarkerLineIsDocumentedDelta() {
        List<MarkdownLayoutLine> b = bPrime("\u00a7a- x");
        Assert.assertEquals(1, b.size());
        Assert.assertEquals(MarkdownLayoutLine.Kind.LIST, b.get(0).getKind());
        Assert.assertEquals("\u2022 x", visible(b.get(0)));
        Assert.assertEquals("乙′：行首色随命中消费丢弃", WHITE,
                b.get(0).getSegments().get(1).getStyle().getColor());
        List<MarkdownLayoutLine> a = jia("\u00a7a- x");
        Assert.assertEquals(1, a.size());
        Assert.assertEquals(MarkdownLayoutLine.Kind.LIST, a.get(0).getKind());
        Assert.assertEquals("\u2022 x", visible(a.get(0)));
        Assert.assertEquals("甲：绿随锚点存活（设计意图）", GREEN,
                a.get(0).getSegments().get(1).getStyle().getColor());
    }

    /** 围栏内标记形 §（缺陷 a 场景）：乙′ 预清洗看不到块上下文，把「§c# 标」误剥成
     *  「# 标」（色丢）；甲 转换在块检测之前、围栏内容恒字面且色存活。
     *  可见文本两侧同为「# 标」，色不同 = 登记差异。 */
    @Test
    public void fenceLineLookingLikeBlockMarkerIsDocumentedDelta() {
        String tick = String.valueOf((char) 0x60);
        String fence = tick + tick + tick;
        String msg = fence + "\n\u00a7c# \u6807\n" + fence;
        List<MarkdownLayoutLine> b = bPrime(msg);
        Assert.assertEquals(MarkdownLayoutLine.Kind.CODE, b.get(0).getKind());
        Assert.assertEquals("# \u6807", visible(b.get(0)));
        Assert.assertEquals("乙′：误剥后剩字面「# 标」且白字", WHITE,
                b.get(0).getSegments().get(0).getStyle().getColor());
        List<MarkdownLayoutLine> a = jia(msg);
        Assert.assertEquals(MarkdownLayoutLine.Kind.CODE, a.get(0).getKind());
        Assert.assertEquals("# \u6807", visible(a.get(0)));
        Assert.assertEquals("甲：围栏内容字面 + §c 红随锚点保留", RED,
                a.get(0).getSegments().get(0).getStyle().getColor());
    }

    /** 容器行首 §（缺陷 b 场景）：乙′ 判据只认物理行首 ⇒「> §a- x」不升格（旧登记残留
     *  不一致·其一的本体）；甲 转成纯文本「> - x」⇒ L1 引用内列表自然升格。 */
    @Test
    public void listLineInsideContainerIsDocumentedDelta() {
        List<MarkdownLayoutLine> b = bPrime("> \u00a7a- x");
        Assert.assertEquals(1, b.size());
        Assert.assertEquals("乙′：引用内段落字面「- x」", MarkdownLayoutLine.Kind.TEXT,
                b.get(0).getKind());
        Assert.assertEquals(1, b.get(0).getQuoteLevel());
        Assert.assertEquals("- x", visible(b.get(0)));
        Assert.assertEquals(GREEN, b.get(0).getSegments().get(0).getStyle().getColor());
        List<MarkdownLayoutLine> a = jia("> \u00a7a- x");
        Assert.assertEquals(1, a.size());
        Assert.assertEquals("甲：引用内列表升格", MarkdownLayoutLine.Kind.LIST, a.get(0).getKind());
        Assert.assertEquals(1, a.get(0).getQuoteLevel());
        Assert.assertEquals("\u2022 x", visible(a.get(0)));
        Assert.assertEquals("合成标记段 = caller 基色叠块级链 ⇒ 引用色（与 String 路合成段同尺）",
                ChatMarkdownSettings.getTextSecondaryArgb(),
                a.get(0).getSegments().get(0).getStyle().getColor());
        Assert.assertEquals("正文绿存活（显式色压引用色）", GREEN,
                a.get(0).getSegments().get(1).getStyle().getColor());
    }

    /** §f+4空格（任务书第三部分第 4 条改判点）：乙′「段落字面」是保色妥协；甲 色进锚点
     *  ⇒ 文本「    - item」⇒ CommonMark §4.4 缩进代码块（kind=CODE、4 空格缩进剥除、
     *  §f 白随锚点存活）。这是本批点名的主流正确结果。 */
    @Test
    public void colorPlusFourSpacesBecomesIndentedCodeIsDocumentedDelta() {
        List<MarkdownLayoutLine> b = bPrime("\u00a7f    - item");
        Assert.assertEquals(1, b.size());
        Assert.assertEquals(MarkdownLayoutLine.Kind.TEXT, b.get(0).getKind());
        Assert.assertEquals("    - item", visible(b.get(0)));
        List<MarkdownLayoutLine> a = jia("\u00a7f    - item");
        Assert.assertEquals(1, a.size());
        Assert.assertEquals("甲：缩进代码块", MarkdownLayoutLine.Kind.CODE, a.get(0).getKind());
        Assert.assertEquals("- item", visible(a.get(0)));
        Assert.assertTrue("§f 显式白存活",
                a.get(0).getSegments().get(0).getStyle().isColorExplicit());
        Assert.assertEquals(WHITE, a.get(0).getSegments().get(0).getStyle().getColor());
    }

    /** 未知码对 §z- x：两侧都消费「§z」（applyFormat default=重置，L0/桥/转换三点同源），
     *  差别只在消费时机 ⇒ 乙′ 的 markdown 看见「§z- x」= 段落字面；甲 看见「- x」= 列表。
     *  甲 与原版 parseSegments（系统消息路）同解读 = 归位收益，登记为有意差异。 */
    @Test
    public void unknownCodeBeforeMarkerIsDocumentedDelta() {
        List<MarkdownLayoutLine> b = bPrime("\u00a7z- x");
        Assert.assertEquals(MarkdownLayoutLine.Kind.TEXT, b.get(0).getKind());
        Assert.assertEquals("- x", visible(b.get(0)));
        List<MarkdownLayoutLine> a = jia("\u00a7z- x");
        Assert.assertEquals(MarkdownLayoutLine.Kind.LIST, a.get(0).getKind());
        Assert.assertEquals("\u2022 x", visible(a.get(0)));
    }

    /** 色码 × 强调族·① 持续染色跨强调边界（「§c甲⏎**乙**」）：两路 markdown 结构相同
     *  （甲=普通文字、乙=加粗），差在色——乙′ 桥按段局部重启样式 ⇒ 换段后红死（乙白）；
     *  甲 锚点沿整条消息持续 ⇒ 乙红。方案甲立论场景（宿主码不认 markdown 边界），
     *  登记为有意差异。 */
    @Test
    public void colorPersistenceAcrossEmphasisIsDocumentedDelta() {
        String msg = "\u00a7c\u7532\n**\u4e59**";
        List<MarkdownLayoutLine> b = bPrime(msg);
        Assert.assertEquals(2, b.size());
        Assert.assertEquals(RED, b.get(0).getSegments().get(0).getStyle().getColor());
        Assert.assertEquals("\u4e59", b.get(1).getSegments().get(0).getText());
        Assert.assertEquals(FontType.BOLD, b.get(1).getSegments().get(0).getStyle().getFontType());
        Assert.assertEquals("乙′：桥段局部重启，强调行红丢失（旧结构性缺陷）", WHITE,
                b.get(1).getSegments().get(0).getStyle().getColor());
        List<MarkdownLayoutLine> a = jia(msg);
        Assert.assertEquals(2, a.size());
        Assert.assertEquals(RED, a.get(0).getSegments().get(0).getStyle().getColor());
        Assert.assertEquals("\u4e59", a.get(1).getSegments().get(0).getText());
        Assert.assertEquals(FontType.BOLD, a.get(1).getSegments().get(0).getStyle().getFontType());
        Assert.assertEquals("甲：锚点持续，强调行红存活", RED,
                a.get(1).getSegments().get(0).getStyle().getColor());
    }

    /** 色码 × 强调族·② 残留 § 反噬 markdown 结构（「前 §c**粗**尾」）：乙′ 段文本里
     *  §c 紧贴 **，定界判据看见前一字符是字母 c ⇒ 不配对、整行字面（连加粗都丢，实测
     *  段流「前 」+「**粗**尾」两段）；甲 转换先行，markdown 看见「前 **粗**尾」⇒
     *  正常「前␣粗尾」三段、粗位+红位齐。文本与位双差，登记为有意差异——乙′ 的 §
     *  残留本就污染行内判据，正是本批要消灭的病灶。 */
    @Test
    public void lingeringSectionBreakingEmphasisIsDocumentedDelta() {
        String msg = "\u524d \u00a7c**\u7c97**\u5c3e";
        List<MarkdownLayoutLine> b = bPrime(msg);
        Assert.assertEquals("前 **\u7c97**尾", visible(b.get(0)));
        Assert.assertFalse("乙′：强调被残留 § 反噬成字面", anyBold(b.get(0)));
        List<MarkdownLayoutLine> a = jia(msg);
        Assert.assertEquals("\u524d \u7c97\u5c3e", visible(a.get(0)));
        Assert.assertTrue("甲：正常加粗", anyBold(a.get(0)));
        Assert.assertEquals("\u7c97", a.get(0).getSegments().get(1).getText());
        Assert.assertEquals(RED, a.get(0).getSegments().get(1).getStyle().getColor());
        Assert.assertEquals(FontType.BOLD, a.get(0).getSegments().get(1).getStyle().getFontType());
    }

    private static boolean anyBold(MarkdownLayoutLine line) {
        for (int i = 0; i < line.getSegments().size(); i++) {
            if (line.getSegments().get(i).getStyle().getFontType() == FontType.BOLD) {
                return true;
            }
        }
        return false;
    }

    /** § 色跨软换行：乙′ 桥按逻辑行逐段作业 ⇒ 第二行白色；甲 锚点沿整条消息持续 ⇒
     *  第二行仍红（IChatComponent 桥语义下服务端色本就跨行；方案甲设计意图）。 */
    @Test
    public void colorAcrossSoftLineBreakIsDocumentedDelta() {
        List<MarkdownLayoutLine> b = bPrime("\u00a7c\u7532\n\u4e59");
        Assert.assertEquals(2, b.size());
        Assert.assertEquals(RED, b.get(0).getSegments().get(0).getStyle().getColor());
        Assert.assertEquals("乙′：第二行红丢失", WHITE,
                b.get(1).getSegments().get(0).getStyle().getColor());
        List<MarkdownLayoutLine> a = jia("\u00a7c\u7532\n\u4e59");
        Assert.assertEquals(2, a.size());
        Assert.assertEquals(RED, a.get(0).getSegments().get(0).getStyle().getColor());
        Assert.assertEquals("甲：第二行仍红", RED,
                a.get(1).getSegments().get(0).getStyle().getColor());
    }

    // ==================== 不可能等价项（双侧期望写死留证；理由见 javadoc） ====================

    /** code span 内 §（任务书点名）：桥恒透传 codeSpan 段 ⇒ 乙′ 段文本字面「§cfoo」；
     *  甲 转换先于 markdown、§ 在 code 定界被识别之前就消化 ⇒ 文本「foo」+红+code 位。
     *  <b>不可能等价</b>：要让甲 跳过 code 区须给转换器一份 markdown 上下文 = 第二套块扫描
     *  （违宪 + G3 复生雷区）；且乙′ 该行为本就不自洽——围栏（块级 code）内的 § 乙′ 是上色
     *  的（桥只豁免行内 codeSpan/latex 两域），甲 把两域统一为「与原版 parseSegments 同解读」。 */
    @Test
    public void sectionInsideCodeSpanIsImpossibleToEqualize() {
        String tick = String.valueOf((char) 0x60);
        String msg = tick + "\u00a7cfoo" + tick;
        List<MarkdownLayoutLine> b = bPrime(msg);
        Assert.assertEquals("§cfoo", b.get(0).getSegments().get(0).getText());
        Assert.assertTrue(b.get(0).getSegments().get(0).getStyle().isCodeSpan());
        Assert.assertEquals(WHITE, b.get(0).getSegments().get(0).getStyle().getColor());
        List<MarkdownLayoutLine> a = jia(msg);
        Assert.assertEquals("foo", a.get(0).getSegments().get(0).getText());
        Assert.assertTrue(a.get(0).getSegments().get(0).getStyle().isCodeSpan());
        Assert.assertEquals(RED, a.get(0).getSegments().get(0).getStyle().getColor());
    }

    /** latex 段内 §（任务书点名）：桥恒透传 latex ⇒ 乙′ TeX 源字面「x §by」；甲 在进
     *  markdown 前消化 ⇒ TeX 源「x y」。<b>不可能等价</b>（同 code span 理由：转换器无
     *  markdown 上下文是甲 的定义性前提）；且 TeX 源里的 § 本非合法公式字符，乙′ 的
     *  「字面保留」实为把渲染噪声留进数学源——甲 消除之。 */
    @Test
    public void sectionInsideLatexIsImpossibleToEqualize() {
        String msg = "$x \u00a7by$";
        List<MarkdownLayoutLine> b = bPrime(msg);
        Assert.assertTrue(b.get(0).getSegments().get(0).isLatex());
        Assert.assertEquals("x \u00a7by", b.get(0).getSegments().get(0).getLatexSource());
        List<MarkdownLayoutLine> a = jia(msg);
        Assert.assertTrue(a.get(0).getSegments().get(0).isLatex());
        Assert.assertEquals("x y", a.get(0).getSegments().get(0).getLatexSource());
    }
}
