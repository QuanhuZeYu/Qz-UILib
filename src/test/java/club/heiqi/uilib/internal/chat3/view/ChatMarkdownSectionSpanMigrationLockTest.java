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

    /** 乙′：预清洗 + 输出后置桥（C6b·3 起机制退役，读数走 {@link RetiredBPrime} 只读镜像）。 */
    private static List<MarkdownLayoutLine> bPrime(String messageText) {
        String cleaned = RetiredBPrime.stripLeadingSectionCodes(
                messageText == null ? "" : messageText);
        List<MarkdownLayoutLine> logical = MarkdownDocument.parse(cleaned)
                .toLayoutLines(ChatMarkdownPipeline.chatStyleTable(), base());
        List<MarkdownLayoutLine> out = new ArrayList<MarkdownLayoutLine>(logical.size());
        for (int i = 0; i < logical.size(); i++) {
            MarkdownLayoutLine line = logical.get(i);
            List<TextSegment> segs = RetiredBPrime.bridgeSectionCodes(line.getSegments());
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

    // ==================== 桥退役后遗留实证：甲 段流上桥 = 恒 no-op（任务书第三部分第 3 条） ====================

    /**
     * 甲 产物段文本里 § 只可能以行尾孤立体存在（无可消费码对）⇒ 旧桥对甲 段流恒无事可做：
     * 逐段文本 + 全样式字段等值、段数等值（桥在无可消费对时即便重建列表，内容也是恒等）。
     * 这是 C6b·3 删桥的前置实证（当时桥=生产函数、逐条实测全绿；桥退役后镜像仍钉同一判据，
     * 防止甲 侧未来漏出可消费 § 对）。
     */
    @Test
    public void convertedStreamLeavesNoConsumableSectionPairsForTheBridge() {
        String[] corpus = {
            "§a- x", "§c\u7532§f\u4e59", "> §c\u7532",
            "\u0060\u0060\u0060\n§cfoo\n\u0060\u0060\u0060",
            "\u0060\u0060\u0060\n§c# \u6807\n\u0060\u0060\u0060", "> §a- x",
            "§c\u7532§r\u4e59", "§c§l\u7532", "\u7532§",
            "§f    - item", "§z- x", "§c\u7532\n**\u4e59**",
            "\u524d §c**\u7c97**\u5c3e", "§c\u7532\n\u4e59",
            "> §c\u7532§r\u4e59",
            // 门禁 P13/P14 原文（字面复制自 MarkdownChat3ParityTest:204-207；其余 46 条无 §
            // ⇒ 转换器零改写 + 桥对无 § 段流按构造 no-op——结构论证见本类 javadoc）
            "§c\u7ea2\u8272\u8b66\u544a §fplain tail mixed English 123 \u957f\u5230\u9700\u8981\u65ad\u884c\u624d\u80fd\u653e\u4e0b\u66f4\u591a\u5185\u5bb9",
            "\u666e\u901a\u6bb5\u843d", "- \u65e0\u5b57\u7b26 \u5217\u8868", "# \u6807\u9898",
        };
        for (int i = 0; i < corpus.length; i++) {
            String msg = corpus[i];
            List<MarkdownLayoutLine> logical = jia(msg);
            for (int k = 0; k < logical.size(); k++) {
                List<TextSegment> segments = logical.get(k).getSegments();
                for (int sIdx = 0; sIdx < segments.size(); sIdx++) {
                    TextSegment segment = segments.get(sIdx);
                    if (segment.isLatex()) {
                        Assert.fail("甲 下 latex 源不该含 §（转换先于 markdown 已全量消化）: " + msg);
                    }
                    String text = segment.getText();
                    int at = text.indexOf('\u00a7');
                    if (at >= 0) {
                        Assert.assertTrue("孤立 § 只许在段文本末位且全段仅此一枚: " + msg + " / " + text,
                                at == text.length() - 1 && text.indexOf('\u00a7', at + 1) < 0);
                    }
                    List<TextSegment> bridged = RetiredBPrime.bridgeSectionCodes(segments);
                    Assert.assertEquals("桥改段数 = 桥未 no-op: " + msg,
                            segments.size(), bridged.size());
                    for (int bIdx = 0; bIdx < segments.size(); bIdx++) {
                        Assert.assertEquals("桥改文本: " + msg,
                                segments.get(bIdx).getText(), bridged.get(bIdx).getText());
                        Assert.assertEquals("桥改样式: " + msg,
                                StyleFieldsKey.of(segments.get(bIdx).getStyle()),
                                StyleFieldsKey.of(bridged.get(bIdx).getStyle()));
                    }
                }
            }
        }
    }

    // ==================== 乙′ 机制只读镜像（C6b·3 生产拆除时逐句照搬自 git 7d92e4f5） ====================

    /**
     * 退役的乙′ 机制（预清洗 strip/markerView/coarse 粗检族 + 输出后置桥 bridge/splitRuns），
     * 逐句照搬自 {@code 7d92e4f5}（C6b·2 基线）的 ChatMarkdownPipeline 生产码，作为本锁
     * 乙′ 侧读数的<b>只读镜像</b>：不随生产演化、不允许在此新增行为；若未来生产侧语义与
     * 此分叉，本锁对账的基线随之失真——「迁移锁必须留住被迁移机制」的固有代价，如实登记
     * （规划 §二之八 C6b 细账）。
     */
    private static final class RetiredBPrime {
        private RetiredBPrime() {
        }
        /**
         * 〔C6b·3 迁移 oracle：自 git 7d92e4f5 的 ChatMarkdownPipeline 生产机制逐句照搬
         * 的只读镜像（乙′ 输出侧桥），供本文件 oracle 读数；随生产拆除冻结、不随生产演化〕
         *
         * <p>§ 桥（输出侧）：把 toLayoutLines 产物里残留在段文本中的 § 样式码解释为样式（chat3 现有
         * § 颜色语义的出口面）。C4-fix 乙′配套：{@link #stripLeadingSectionCodes} 只消费命中块标记
         * 行的行首码对——<b>未命中行的行首码与行中/段中残留码都在本方法出口上色</b>（这正是
         * {@code §c红色警告} 行首色保住的机制，C4 前旧 NONE 分支同款）——输入清洗与输出解释均在
         * 本集成层，L1 零认知。
         *
         * <p>逐段扫描：latex 原子段（TeX 源是数学文本，§ 无意义）、code 衬底段（code 内容恒字面，
         * F1/旧裁定「code 内不解析任何标记」）与不含 § 的段原样透传（零拷贝）。命中的段以
         * {@link TextStyle#applyFormat(char, int)}（L0 唯一 § 语义实现，度量/颜色同源）按码对
         * 切分：每个可视 run 持有原段样式拷贝 + 依序 applyFormat——markdown 样式位（粗/斜/删/
         * 下/code/链接位/段级字号）先叠加，§ 码后生效，{@code §r} 重置与 {@code TextLayoutService
         * .parseSegments} 同语义。连续 § 码（如 {@code §c§l}）与 A 路同样逐对消费；纯格式码段
         * （如行首残留 {@code §f} 独占段）整段消失，与 parseSegments 的空 run 丢弃一致。</p>
         *
         * @param segments 行段流（只读）
         * @return 桥后段流（无 § 残留时同引用）
         */
        static List<TextSegment> bridgeSectionCodes(List<TextSegment> segments) {
            if (segments == null || segments.isEmpty()) {
                return segments;
            }
            List<TextSegment> out = null;
            for (int i = 0; i < segments.size(); i++) {
                TextSegment segment = segments.get(i);
                String text = segment.getText();
                if (segment.isLatex() || segment.getStyle().isCodeSpan()
                        || text == null || text.indexOf('\u00a7') < 0) {
                    if (out != null) {
                        out.add(segment);
                    }
                    continue;
                }
                List<TextSegment> runs = splitRunsOnFormatCodes(segment);
                if (runs == null) {
                    if (out != null) {
                        out.add(segment);
                    }
                    continue;
                }
                if (out == null) {
                    out = new ArrayList<TextSegment>(segments.size() + 4);
                    for (int k = 0; k < i; k++) {
                        out.add(segments.get(k));
                    }
                }
                out.addAll(runs);
            }
            return out == null ? segments : out;
        }

        /**
         * 单段 § 码切分（与 {@code TextLayoutService.parseSegments} 同一扫描语义，差别仅在起点
         * 样式 = 本段样式拷贝而非新建——markdown 样式位因此得以保留）。
         *
         * @return 切分段列表；文本确无可消费 § 对时返回 null（调用方透传原段）
         */
        private static List<TextSegment> splitRunsOnFormatCodes(TextSegment segment) {
            String text = segment.getText();
            if (text.indexOf('\u00a7') < 0) {
                return null;
            }
            int baseColor = segment.getStyle().getColor();
            List<TextSegment> out = new ArrayList<TextSegment>(2);
            TextStyle current = segment.getStyle().copy();
            StringBuilder buffer = new StringBuilder(text.length());
            for (int i = 0; i < text.length();) {
                char c = text.charAt(i);
                if (c == '\u00a7' && i + 1 < text.length()) {
                    if (buffer.length() > 0) {
                        out.add(new TextSegment(buffer.toString(), current));
                        buffer.setLength(0);
                    }
                    current = current.copy();
                    current.applyFormat(Character.toLowerCase(text.charAt(i + 1)), baseColor);
                    i += 2;
                    continue;
                }
                buffer.append(c);
                i++;
            }
            if (buffer.length() > 0) {
                out.add(new TextSegment(buffer.toString(), current));
            }
            return out;
        }

        /**
         * 〔C6b·3 迁移 oracle：自 git 7d92e4f5 的 ChatMarkdownPipeline 生产机制逐句照搬
         * 的只读镜像（乙′ 预清洗），供本文件 oracle 读数；随生产拆除冻结、不随生产演化〕
         *
         * <p>输入侧 § 清洗（C4 归位 + C4-fix 乙′，规划 §二之五 F4 承接面的归位形 → §二之八 C4-fix
         * 改判）：逐行做「至多 3 个空格 + 连续 § 码对」交替检测视图（{@link #markerView}），
         * <b>只有视图中确实命中块标记（标题/围栏/引用/列表/分隔线）才采用剥后视图</b>；不命中的
         * 行一字不动。码集 0-9a-f 颜色、k-o 样式、r 重置，大小写同义；非法码字符（§z）与行尾
         * 孤立 § 原样保留，非破坏性宽容。
         *
         * <p><b>判据出处（C4-fix 裁定，用户裁定方案乙′）</b>：旧 L1（{@code bd58b343^}）
         * markerView 的「命中块标记才消费、未命中原样保留」精确复刻旧 chat3 观感——旧
         * ChatMarkdownLineRule.classify 的剥码只作用于其检测局部变量、从不改显示文本；旧
         * ChatMessageList 的 NONE 分支走 parseCached(renderLine)，renderLine 含行首 § 码 ⇒
         * 不命中块标记的行<b>颜色保留</b>；只有命中列表/公式的行才用 markdown.getContent()
         * （无码）⇒ 那类行行首色才丢。C4 初版误按「无条件剥」口径搬迁，实测丢失
         * {@code §c红色警告} 的行首色（当时如实登记的代价⑤C），本批改判乙′：判据与循环结构
         * 整体搬进集成层（L1 仍零认知 §，宪法满足），未命中行的行首码与行中/段中码一律由输出侧
         * {@link #bridgeSectionCodes} 解释上色；命中行消费的是行首码对——与 C4 前逐位一致
         * （那类行旧口径同样是剥后无码显示）。</p>
         *
         * <p>纯函数 + 幂等：命中的视图行首必是 ≤3 空格 + 块标记触发字符（二次调用无可剥 § 序列、
         * 恒同引用，如 {@code §a- x} 剥后 {@code - x} 二次不动）；不命中的行恒返回原行（二次判定
         * 结果不变，如 {@code §c红色警告} 二次仍不剥）⇒ 二次调用恒同引用。这是缓存 key 用清洗后
         * 文本仍自洽的前提（见 {@link #layout} 的注释）。</p>
         *
         * <p><b>粗检漂移风险与锁</b>：本类粗检判据与 L1 真判据是两份代码（L1 私有面跨包不可见、
         * 公共面冻结，不得为此扩 L1、不得反射进 L1 私有），（原一致性锁
         * coarseBlockMarkerAgreesWithL1BlockIdentity 已随机制在 C6b·3 退役；甲↔乙′ 对账见本类用例。）</p>
         *
         * @param text 消息原文（非 null）
         * @return 清洗后文本（无任何行命中块标记时同引用）
         */
        static String stripLeadingSectionCodes(String text) {
            if (text.indexOf('\u00a7') < 0) {
                return text;
            }
            StringBuilder out = new StringBuilder(text.length());
            boolean changed = false;
            int pos = 0;
            while (true) {
                int br = text.indexOf('\n', pos);
                int end = br < 0 ? text.length() : br;
                String line = text.substring(pos, end);
                String cleaned = markerView(line);
                if (cleaned != line) {
                    changed = true;
                }
                out.append(cleaned);
                if (br < 0) {
                    break;
                }
                out.append('\n');
                pos = br + 1;
            }
            // 无任何行命中 ⇒ 同引用返回（「无命中不复制」与桥的零拷贝纪律同款；命中的视图行首
            // 必是块标记触发字符 ⇒ 二次调用恒不命中、恒同引用 = 幂等）
            return changed ? out.toString() : text;
        }

        /**
         * 单行块标记检测视图（乙′本体；自 C4 从 L1 拆除的 markerView 逐句照搬——循环结构与判据
         * 蓝本 {@code git show bd58b343^} 该文件 :172-210，勿简化成「只剥最前面连续码对」：
         * 蓝本是「至多 3 个空格 + 连续 § 码对」<b>交替</b>循环，空格位置保留进视图（嵌套列表
         * 缩进语义不变），{@code §f §a- x}、{@code §f  - x} 这类形态因此能正确命中；停住处首个
         * 非空白字符不是块标记触发字符时返回原行、不做任何剥除（{@code §f + 4 空格 + 标记}
         * ind&gt;3 不算命中 ⇒ 整行保留，行首色进桥））。
         *
         * @param line 源行（{@code \r} 只在行尾、不碍行首判定）
         * @return 命中块标记 ⇒ 剥后视图（新串）；否则原行同引用
         */
        static String markerView(String line) {
            if (line == null || line.isEmpty() || line.indexOf('\u00a7') < 0) {
                return line;
            }
            StringBuilder view = new StringBuilder(line.length());
            int i = 0;
            int stripped = 0;
            while (i < line.length()) {
                int spaces = 0;
                while (i + spaces < line.length() && line.charAt(i + spaces) == ' ' && spaces < 3) {
                    spaces++;
                }
                view.append(line, i, i + spaces);
                int cursor = i + spaces;
                int eaten = 0;
                while (cursor + 1 < line.length() && line.charAt(cursor) == '\u00a7'
                        && isChatFormatCode(Character.toLowerCase(line.charAt(cursor + 1)))) {
                    cursor += 2;
                    eaten += 2;
                }
                stripped += eaten;
                if (eaten == 0) {
                    // 蓝本 M5 修原样保留：本跳刚追加进视图的空格不得随 substring(i) 二次追加
                    // （否则「§f + 空格 + 标记」视图缩进翻倍落回字面，丢失旧口径「行首 § 码后
                    // 照常取标记」行为）
                    i = cursor;
                    break;
                }
                i = cursor;
            }
            if (stripped == 0) {
                return line;
            }
            view.append(line.substring(i));
            // 只有「视图中确实命中一个块标记」才允许消费 § 序列，否则原样交回（普通段落行的
            // 行首 § 码一字不动——容忍不得变成吞字）
            String candidate = view.toString();
            return coarseBlockStart(candidate) ? candidate : line;
        }

        /** 乙′粗检命中判据（一致性锁的 chat3 侧读数）：视图与原文不同引用 = 命中块标记才消费。 */
        static boolean coarseHitsBlockMarker(String line) {
            return markerView(line) != line;
        }

        // ==================== 块标记粗检（oracle 镜像：原「L1 真判据的包私有同构复刻」） ====================

        /**
         * 反引号 U+0060：以无强转十六进制赋值书写——复生锁 G3 断言①禁 chat3 源码出现反引号
         * 字面、反斜杠-u 转义文本与 char 强转 0x60 三种形态（注释虽被扫描器剥除，书写面仍从严）。
         */
        private static final char FENCE_TICK_HEX = 0x60;

        /** 星号 U+002A：单引号字面被复生锁 G3 断言①列为旧行级规则定界形态，同样以常量代。 */
        private static final char MARK_STAR_HEX = 0x2A;

        /** 有序列表序号最大位数（L1 同值口径——粗检与真判据的漂移点之一就是这里）。 */
        private static final int MAX_ORDINAL_DIGITS = 9;

        /**
         * 块起始粗检：判据结构与 L1 已退役的 isBlockStart（蓝本 {@code bd58b343^} :212-222）
         * 逐句同构——围栏/ATX/分隔线/引用（对剥 ≤3 空格后的 body）或列表起始（对整视图，内部
         * 自数缩进）。<b>刻意不含 setext 下划线</b>（蓝本同缺：独立 {@code ===} 不是块起点，
         * 段落上下文中才可能升格，行首 § 序列不为其消费——一致性锁对此有专项样本）。
         */
        private static boolean coarseBlockStart(String view) {
            int ind = coarseLeadingSpaces(view);
            if (ind > 3 || ind >= view.length()) {
                return false;
            }
            String body = view.substring(ind);
            if (coarseFenceStart(body, 3) != 0 || coarseHeadingLevel(body) > 0
                    || coarseThematicBreak(body) || body.charAt(0) == '>') {
                return true;
            }
            return coarseListStart(view);
        }

        /** 行首空格数（制表符不计入块缩进——L1 同款已裁简化，粗检必须同口径）。 */
        private static int coarseLeadingSpaces(String s) {
            int i = 0;
            while (i < s.length() && s.charAt(i) == ' ') {
                i++;
            }
            return i;
        }

        /** ATX 标题级别；0 = 非标题。1..6 个 # 后须为空格/制表符/行尾（L1 同款）。 */
        private static int coarseHeadingLevel(String body) {
            int i = 0;
            while (i < body.length() && body.charAt(i) == '#') {
                i++;
            }
            if (i == 0 || i > 6) {
                return 0;
            }
            if (i == body.length()) {
                return i;
            }
            char next = body.charAt(i);
            return (next == ' ' || next == '\t') ? i : 0;
        }

        /** 围栏起始定界符字符；0 = 非围栏；backtick 围栏 info 含反引号不算起始（L1 同款）。 */
        private static char coarseFenceStart(String body, int minLen) {
            if (body.isEmpty()) {
                return 0;
            }
            char ch = body.charAt(0);
            if (ch != FENCE_TICK_HEX && ch != '~') {
                return 0;
            }
            int run = 0;
            while (run < body.length() && body.charAt(run) == ch) {
                run++;
            }
            if (run < minLen) {
                return 0;
            }
            if (ch == FENCE_TICK_HEX && body.indexOf(ch, run) >= 0) {
                return 0;
            }
            return ch;
        }

        /** 分隔线：整行仅一种星号/连字符/下划线加空白，且标记字符数 &gt;= 3（L1 同款）。 */
        private static boolean coarseThematicBreak(String body) {
            char unit = 0;
            int count = 0;
            for (int i = 0; i < body.length(); i++) {
                char ch = body.charAt(i);
                if (ch == ' ' || ch == '\t') {
                    continue;
                }
                if (ch != '-' && ch != '_' && ch != MARK_STAR_HEX) {
                    return false;
                }
                if (unit == 0) {
                    unit = ch;
                } else if (ch != unit) {
                    return false;
                }
                count++;
            }
            return count >= 3;
        }

        /**
         * 列表起始粗检（bool 版 matchListStart，蓝本 L1 :283-313）：无序 星/连/加 + 空格/制表符/
         * 行尾；有序 1..9 位数字 + 句点/右括号 + 同上（数字不解析、无异常面）。
         */
        private static boolean coarseListStart(String line) {
            int ind = coarseLeadingSpaces(line);
            if (ind > 3 || ind >= line.length()) {
                return false;
            }
            String body = line.substring(ind);
            char c0 = body.charAt(0);
            if ((c0 == '-' || c0 == MARK_STAR_HEX || c0 == '+')
                    && (body.length() == 1 || body.charAt(1) == ' ' || body.charAt(1) == '\t')) {
                return true;
            }
            int digits = 0;
            while (digits < body.length() && digits <= MAX_ORDINAL_DIGITS
                    && Character.isDigit(body.charAt(digits))) {
                digits++;
            }
            if (digits == 0 || digits > MAX_ORDINAL_DIGITS || digits >= body.length()) {
                return false;
            }
            char delim = body.charAt(digits);
            if (delim != '.' && delim != ')') {
                return false;
            }
            return digits + 1 >= body.length() || body.charAt(digits + 1) == ' '
                    || body.charAt(digits + 1) == '\t';
        }

        /** MC 格式码字符集（0-9a-f 颜色、k-o 样式、r 重置；入参已小写化）。 */
        private static boolean isChatFormatCode(char lower) {
            return (lower >= '0' && lower <= '9') || (lower >= 'a' && lower <= 'f')
                    || (lower >= 'k' && lower <= 'o') || lower == 'r';
        }
    }
}
