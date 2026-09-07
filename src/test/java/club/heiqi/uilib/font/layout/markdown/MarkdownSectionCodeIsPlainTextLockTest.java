package club.heiqi.uilib.font.layout.markdown;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;

/**
 * L1「§ 是普通字符」正向行为锁（C7 定案 4 / 用户裁决：markdown 解析遇 § 就当普通字符，
 * 这是<b>无条件的解析规则</b>，不是「输入保证不含 § 所以不用管」的推论）。
 *
 * <p>事实面（原版 {@code ChatAllowedCharacters.isAllowedCharacter} 排除 U+00A7）只解释了
 * <b>为什么可以删掉那堆 § 机制</b>，不参与决定本层行为。因此本类每一例都<b>真的把 § 喂进
 * {@code parse}</b> 再断言结果——没有任何一例靠「输入里没有 §」蒙过。锁的判据：
 * ① 输出文本逐字符含 §；② 样式字段与<b>去掉 § 的等价输入</b>完全一致（§ 零样式效果）；
 * ③ 行首 § 会吃掉块标记（{@code §a- x} 是段落不是列表）——这是「§ 是普通字符」的
 * CommonMark 直接后果，<b>预期正确</b>，同时配「去掉行首 § 后照常命中块标记」的正例对照，
 * 防本类被误锁成「一切恒字面」的空断言。</p>
 *
 * <p>禁止清单（加锁即立此存照）：不得新增任何 § 识别/剥离/转换机制，不得恢复输出侧 § 桥，
 * 不得引入 markerView/κ 一类检测视图。常驻反向守卫见
 * {@code MarkdownL1ZeroSectionKnowledgeGuardTest}。</p>
 */
public class MarkdownSectionCodeIsPlainTextLockTest {

    private static final int WHITE = 0xFFFFFFFF;
    /** § 本体（(char) 运行期拼装，规避 Java 源码 Unicode 预处理陷阱——仓内既有惯例）。 */
    private static final String SECTION = String.valueOf((char) 0x00A7);
    /** 反引号 U+0060（仓内 (char) 0x60 惯例）。 */
    private static final String TICK = String.valueOf((char) 0x60);

    private static List<MarkdownLayoutLine> layout(String source) {
        TextStyle base = new TextStyle();
        base.setColor(WHITE);
        return MarkdownDocument.parse(source).toLayoutLines(MarkdownStyleTable.defaults(), base);
    }

    private static String visible(List<MarkdownLayoutLine> lines) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                out.append('\n');
            }
            for (TextSegment segment : lines.get(i).getSegments()) {
                out.append(segment.isLatex() ? "\u27e6" + segment.getLatexSource() + "\u27e7"
                        : segment.getText());
            }
        }
        return out.toString();
    }

    private static String styleKey(TextStyle style) {
        StringBuilder b = new StringBuilder(96);
        b.append("c=").append(Integer.toHexString(style.getColor())).append('|');
        b.append("ce=").append(style.isColorExplicit()).append('|');
        b.append("ft=").append(style.getFontType()).append('|');
        b.append("rnd=").append(style.isRandomStyle()).append('|');
        b.append("u=").append(style.isUnderline()).append('|');
        b.append("s=").append(style.isStrikethrough()).append('|');
        b.append("i=").append(style.isItalic()).append('|');
        b.append("px=").append(style.getFontSizePx()).append('|');
        b.append("mk=").append(Integer.toHexString(style.getMarkColor())).append('|');
        b.append("sup=").append(style.isSuperscript()).append('|');
        b.append("sub=").append(style.isSubscript()).append('|');
        b.append("ls=").append(Float.floatToRawIntBits(style.getLetterSpacing())).append('|');
        b.append("link=").append(style.getLink()).append('|');
        b.append("code=").append(style.isCodeSpan()).append('|');
        b.append("cbg=").append(Integer.toHexString(style.getCodeBackgroundColor()));
        return b.toString();
    }

    /** 全字段样式清单（逐段）：结构 + 文本 + 样式，用来跟「无 § 等价输入」整体对照。 */
    private static String shape(List<MarkdownLayoutLine> lines) {
        StringBuilder b = new StringBuilder();
        for (MarkdownLayoutLine line : lines) {
            b.append(line.getKind()).append("/q").append(line.getQuoteLevel())
                    .append("/h").append(line.getHeadingLevel()).append(':');
            for (TextSegment segment : line.getSegments()) {
                b.append('[').append(segment.isLatex() ? "LATEX" : "TXT").append("]");
            }
            b.append('\n');
        }
        return b.toString();
    }

    // ==================== ① 正文里的 §：字面保留 + 零样式效果 ====================

    /** 定案 6 点名锁：{@code a§fb} 输出逐字符含 §，且样式字段与 {@code afb} 完全一致。 */
    @Test
    public void sectionCodeInBodyIsLiteralAndHasZeroStyleEffect() {
        List<MarkdownLayoutLine> withSection = layout("a" + SECTION + "fb");
        Assert.assertEquals("§ 不切段", 1, withSection.size());
        List<TextSegment> segments = withSection.get(0).getSegments();
        Assert.assertEquals("§ 不产生任何 markdown 结构", 1, segments.size());
        String text = segments.get(0).getText();
        Assert.assertEquals("a" + SECTION + "fb", text);
        Assert.assertTrue("输出文本必须真的含 §（本锁不得靠输入干净过关）",
                text.indexOf((char) 0x00A7) >= 0);
        Assert.assertEquals("§ 必须停在原位（第 2 个字符）", SECTION.charAt(0), text.charAt(1));
        // § 零样式效果：样式与「去掉 § 的等价输入」逐字段一致
        List<MarkdownLayoutLine> withoutSection = layout("afb");
        Assert.assertEquals("样式字段必须与无 § 等价输入完全一致",
                styleKey(withoutSection.get(0).getSegments().get(0).getStyle()),
                styleKey(segments.get(0).getStyle()));
        Assert.assertEquals("基色原样", WHITE, segments.get(0).getStyle().getColor());
        Assert.assertEquals("§f 不得变成粗体/斜体等任何位", FontType.NORMAL,
                segments.get(0).getStyle().getFontType());
        // 注：colorExplicit 由 caller 的 setColor 决定（基色本身是显式色），与 § 无关——
        // 「§ 零效果」的尺是上面那条「与无 § 等价输入逐字段等值」（styleKey 已含 ce= 位）。
    }

    /** 结构侧同样零效果：段数/行身份与无 § 等价输入全等（防「§ 只影响样式不影响结构」的半截锁）。 */
    @Test
    public void sectionCodeDoesNotChangeSegmentOrLineShape() {
        Assert.assertEquals(shape(layout("a" + SECTION + "b **粗**")),
                shape(layout("ab **粗**")));
        Assert.assertEquals(MarkdownLayoutLine.Kind.TEXT, layout(SECTION + "x **粗**").get(0).getKind());
        boolean bold = false;
        for (TextSegment segment : layout("a" + SECTION + "b **粗**").get(0).getSegments()) {
            if (segment.getStyle().getFontType() == FontType.BOLD) {
                bold = true;
            }
        }
        Assert.assertTrue("正例对照：§ 紧挨强调定界（合法 left-flanking 位）时 markdown 照常加粗"
                + "（否则上面的等值比较是空谈）", bold);
    }

    // ==================== ② 字面域里的 §：行内 code / 围栏 / latex ====================

    /** 行内 code 内的 § 字面保留（code 内容恒字面，§ 也不额外上色、也不消失）。 */
    @Test
    public void sectionCodeInsideInlineCodeStaysLiteral() {
        List<MarkdownLayoutLine> out = layout(TICK + "a" + SECTION + "fb" + TICK);
        List<TextSegment> segments = out.get(0).getSegments();
        Assert.assertEquals(1, segments.size());
        Assert.assertEquals("行内 code 内 § 一字不动", "a" + SECTION + "fb", segments.get(0).getText());
        Assert.assertTrue("仍是 code 段（F1 口径未破）", segments.get(0).getStyle().isCodeSpan());
        Assert.assertEquals("§ 不给 code 段上色", WHITE, segments.get(0).getStyle().getColor());
    }

    /** 围栏代码块内的 § 字面保留（块级 CODE 文本含 § 原样）。 */
    @Test
    public void sectionCodeInsideFenceStaysLiteral() {
        String fence = TICK + TICK + TICK;
        List<MarkdownLayoutLine> out = layout(fence + "\n" + SECTION + "a 内容\n" + fence);
        Assert.assertEquals(MarkdownLayoutLine.Kind.CODE, out.get(0).getKind());
        Assert.assertTrue("围栏内 § 原样: " + visible(out),
                visible(out).contains(SECTION + "a 内容"));
    }

    /** latex 原子内容里的 § 字面保留（TeX 源里 § 就是普通字符，既不吞也不上色）。 */
    @Test
    public void sectionCodeInsideLatexAtomStaysLiteral() {
        List<MarkdownLayoutLine> out = layout("$x" + SECTION + "y$");
        List<TextSegment> segments = out.get(0).getSegments();
        Assert.assertTrue("必须是 latex 原子段（否则本例没测到 latex 域）",
                segments.get(0).isLatex());
        Assert.assertEquals("TeX 源里 § 字面保留", "x" + SECTION + "y",
                segments.get(0).getLatexSource());
    }

    // ==================== ③ 行首 § 吃掉块标记（预期正确，非缺陷） ====================

    /**
     * 定案 4 的直接后果：§ 是普通字符 ⇒ 行首 § 让块标记不再位于行首 ⇒ 列表/标题/引用全部
     * 降为段落字面。对照正例（去掉行首 §）必须照常命中专用块，防本锁退化成「一切恒字面」。
     */
    @Test
    public void leadingSectionCodeDefeatsBlockMarkersAndThatIsCorrect() {
        Assert.assertEquals("§a- 列表项 ⇒ 段落，不是列表", MarkdownLayoutLine.Kind.TEXT,
                layout(SECTION + "a- 列表项").get(0).getKind());
        Assert.assertEquals("整行字面（含 §a 与 - ）", SECTION + "a- 列表项",
                visible(layout(SECTION + "a- 列表项")));

        Assert.assertEquals("§a# 标题 ⇒ 段落，不是标题", MarkdownLayoutLine.Kind.TEXT,
                layout(SECTION + "a# 标题").get(0).getKind());
        Assert.assertEquals(0, layout(SECTION + "a# 标题").get(0).getHeadingLevel());

        List<MarkdownLayoutLine> quote = layout(SECTION + "a> 引用");
        Assert.assertEquals("§a> 引用 ⇒ 段落，不是引用", MarkdownLayoutLine.Kind.TEXT,
                quote.get(0).getKind());
        Assert.assertEquals("引用层级必须为 0（没升格成引用）", 0, quote.get(0).getQuoteLevel());

        // 正例对照：同一行去掉行首 § 后，三种块标记照常识别
        Assert.assertEquals(MarkdownLayoutLine.Kind.LIST, layout("- 列表项").get(0).getKind());
        Assert.assertEquals(MarkdownLayoutLine.Kind.HEADING, layout("# 标题").get(0).getKind());
        Assert.assertEquals(1, layout("# 标题").get(0).getHeadingLevel());
        Assert.assertEquals(1, layout("> 引用").get(0).getQuoteLevel());
    }

    /**
     * § 出现在缩进之后 ⇒ 仍按「§ 是普通字符」的 CommonMark 后果走，不为它开任何特例通道：
     * 4 空格 + § 开头 = 缩进代码块（§ 原样留在 CODE 文本）；≤3 空格 + § 开头 = 段落字面，
     * 其后的列表标记因不在行首而失效。
     */
    @Test
    public void sectionCodeAfterIndentTakesThePlainCharacterConsequence() {
        List<MarkdownLayoutLine> indented = layout("    " + SECTION + "a - x");
        Assert.assertEquals("4 空格起 = 缩进代码块（与 § 无关，纯缩进判据）",
                MarkdownLayoutLine.Kind.CODE, indented.get(0).getKind());
        Assert.assertTrue("CODE 文本里 § 原样: " + visible(indented),
                visible(indented).contains(SECTION + "a - x"));

        List<MarkdownLayoutLine> shallow = layout("  " + SECTION + "a - x");
        Assert.assertEquals("≤3 空格 + § 开头 = 段落（§ 不是空白，也不算标记）",
                MarkdownLayoutLine.Kind.TEXT, shallow.get(0).getKind());
        Assert.assertEquals("行首 ≤3 空格按 CommonMark 段落口径折叠（C1a 既有裁定的正常后果），"
                + "§ 与其后的「 - x」全部字面", SECTION + "a - x", visible(shallow));
    }

    /** 空/纯 § 输入的边界：不吞字符、不产样式、不抛异常（宽容失败口径与 String 路一致）。 */
    @Test
    public void loneSectionIsToleratedLiterally() {
        Assert.assertEquals(SECTION, visible(layout(SECTION)));
        Assert.assertEquals(SECTION + SECTION, visible(layout(SECTION + SECTION)));
        Assert.assertEquals("行尾孤立 § 也字面", "x" + SECTION, visible(layout("x" + SECTION)));
    }
}