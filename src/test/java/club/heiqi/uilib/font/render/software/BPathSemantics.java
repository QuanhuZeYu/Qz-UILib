package club.heiqi.uilib.font.render.software;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.font.layout.markdown.MarkdownDocument;
import club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine;
import club.heiqi.uilib.font.layout.markdown.MarkdownStyleTable;
import club.heiqi.uilib.font.render.software.CommonMarkReferenceSemantics.InlineTok;
import club.heiqi.uilib.font.render.software.CommonMarkReferenceSemantics.Kind;
import club.heiqi.uilib.font.render.software.CommonMarkReferenceSemantics.Mark;
import club.heiqi.uilib.font.render.software.CommonMarkReferenceSemantics.SemanticLine;

/**
 * C3b1 重基线：本仓 B 路语义提取器——把 M10d 行接缝（{@code toLayoutLines}）映射成与
 * {@link CommonMarkReferenceSemantics} 同构的 {@link SemanticLine} 列表。纯测试域。
 *
 * <h3>映射口径（与 R 路类 javadoc 的同构契约逐条对偶，差异点全部照登矩阵）</h3>
 * <ul>
 *   <li><b>kind 优先级</b>：CODE ＞ THEMATIC_BREAK ＞ 列表归属（LIST kind，或 TEXT kind 但
 *       {@code listMarkerChain} 非空=项内后续块）＞ 引用（quoteLevel&gt;0）＞ TEXT。
 *       与 R 路一致；引用/列表层级同时落在 quoteDepth/listDepth 正交字段。</li>
 *   <li><b>LIST depth 口径（M10d {@code getListMarkerChain} 读后定死）</b>：链=「从最外层到
 *       本行所属项的渲染后标记段，每级一段」（圆点被样式表配空串时该级不进链——默认表恒有
 *       圆点，链长=列表嵌套层数），故 {@code depth = chain.size()}，与 R 路的列表祖先计数
 *       同尺。有序判定与参考序取链尾标记文本（{@code "3. "} / {@code "3) "} 形态 →
 *       ordinal=3）；本仓有序保留源序号原文，与 commonmark start+下标推算之差按
 *       EXT_ORDERED_START 域登记。</li>
 *   <li><b>marker 段</b>：kind==LIST 行的 seg0 即本级标记段（{@link MarkdownLayoutLine.Kind#LIST}
 *       javadoc 钉死），但「同一 blockId 内只有首行带标记段」——故判据为
 *       {@code seg0.text == 链尾标记段文本}（同一文本在链上恒为标记形态「X. 」/「\u2022 」）。
 *       命中则单列 LIST_MARKER token、不并入内容文本；不命中（项内续行/后续块）不产
 *       marker token（R 路同：合成 marker 只落项首语义行）。</li>
 *   <li><b>CODE</b>：kind==CODE 恒映射 {@link Kind#CODE_FENCED} 家族值（本仓行接缝不区分
 *       围栏/缩进、info 语言忽略——{@code kindFamily} 归一后与 R 的 CODE_INDENTED 同类，
 *       子型差不报；这是有意豁免，见 R 路类注释）。</li>
 *   <li><b>HEADING</b>：本仓 {@code MarkdownLayoutLine.Kind} 只有 TEXT/LIST/CODE/
 *       THEMATIC_BREAK，<b>无标题块身份</b>（默认样式表标题仅=全文 BOLD、字号增量 0）——
 *       B 侧标题行按 TEXT 提取，与 R 的 HEADING(level) 差由矩阵按 HEADING_STYLE_ONLY 域
 *       登记（「文本+样式豁免」，2026-09-06 拆除批口径）。</li>
 *   <li><b>F6 空行占位</b>：{@code blank()} 行（零段 + blockId==NO_BLOCK）是块间距接缝工件，
 *       R 路不存在——对齐前剔除并计数，矩阵条目头注明剔行数。</li>
 *   <li><b>行内标记</b>：FontType.BOLD→STRONG、isItalic→EM、isCodeSpan→CODE、
 *       isStrikethrough→STRIKE、isLatex→FORMULA（token 文本=latexSource，展示/visible 以
 *       \u27e6\u27e7 包裹防吞字误判）、getLink()!=null→LINK(dest)。颜色/字号/下划线不进
 *       语义面（样式豁免）；相邻同形 token 合并，与 R 路口径对偶。</li>
 * </ul>
 */
final class BPathSemantics {

    private BPathSemantics() {
    }

    /** B 路提取结果：语义行（已剔 F6 空行）+ 剔行数 + 接缝可见文本整串（吞字地板断言用）。 */
    static final class Result {
        final List<SemanticLine> lines;
        final int blanksRemoved;
        final String seamVisibleJoined;

        Result(List<SemanticLine> lines, int blanksRemoved, String seamVisibleJoined) {
            this.lines = lines;
            this.blanksRemoved = blanksRemoved;
            this.seamVisibleJoined = seamVisibleJoined;
        }
    }

    static Result extract(String source) {
        TextStyle base = new TextStyle();
        base.setColor(0xFFFFFFFF);
        List<MarkdownLayoutLine> raw = MarkdownDocument.parse(source)
                .toLayoutLines(MarkdownStyleTable.defaults(), base);
        List<SemanticLine> out = new ArrayList<SemanticLine>();
        StringBuilder joined = new StringBuilder();
        int blanks = 0;
        for (MarkdownLayoutLine line : raw) {
            if (line.getSegments().isEmpty() && line.getBlockId() == MarkdownLayoutLine.NO_BLOCK) {
                blanks++;
                continue;
            }
            SemanticLine mapped = mapLine(line);
            out.add(mapped);
            if (joined.length() > 0) {
                joined.append('\n');
            }
            joined.append(mapped.visible());
        }
        return new Result(out, blanks, joined.toString());
    }

    /** 单行映射：kind 参数 + 行内 token 两阶段。 */
    private static SemanticLine mapLine(MarkdownLayoutLine line) {
        List<TextSegment> chain = line.getListMarkerChain();
        int quote = line.getQuoteLevel();
        int listDepth = chain.size();
        boolean chainOrdered = !chain.isEmpty() && isOrderedMarker(chain.get(chain.size() - 1).getText());
        int chainOrdinal = chain.isEmpty() ? 0 : ordinalOfMarker(chain.get(chain.size() - 1).getText());

        Kind kind;
        int level;
        switch (line.getKind()) {
            case CODE:
                kind = Kind.CODE_FENCED;
                level = 0;
                break;
            case THEMATIC_BREAK:
                kind = Kind.THEMATIC_BREAK;
                level = 0;
                break;
            case LIST:
                kind = Kind.LIST_ITEM;
                level = listDepth;
                break;
            default: // TEXT：链优先（项内后续块），其次引用，最后裸文本
                if (listDepth > 0) {
                    kind = Kind.LIST_ITEM;
                    level = listDepth;
                } else if (quote > 0) {
                    kind = Kind.BLOCK_QUOTE;
                    level = quote;
                } else {
                    kind = Kind.TEXT;
                    level = 0;
                }
                break;
        }

        List<TextSegment> segs = line.getSegments();
        List<InlineTok> toks = new ArrayList<InlineTok>();
        String ownMarkerText = chain.isEmpty() ? null : chain.get(chain.size() - 1).getText();
        int start = 0;
        if (line.getKind() == MarkdownLayoutLine.Kind.LIST && !segs.isEmpty() && ownMarkerText != null
                && ownMarkerText.equals(segs.get(0).getText())) {
            toks.add(new InlineTok(EnumSet.of(Mark.LIST_MARKER), ownMarkerText, null));
            start = 1;
        }
        for (int i = start; i < segs.size(); i++) {
            TextSegment seg = segs.get(i);
            if (seg.getText().isEmpty() && !seg.isLatex()) {
                continue; // F6 占位段（行内零宽）
            }
            toks.add(mapSeg(seg));
        }
        return new SemanticLine(kind, level, chainOrdered, chainOrdinal, quote, listDepth,
                null, mergeAdjacent(toks));
    }

    private static InlineTok mapSeg(TextSegment seg) {
        if (seg.isLatex()) {
            return new InlineTok(EnumSet.of(Mark.FORMULA), seg.getLatexSource(), null);
        }
        TextStyle style = seg.getStyle();
        EnumSet<Mark> marks = EnumSet.noneOf(Mark.class);
        if (style.getFontType() == FontType.BOLD) {
            marks.add(Mark.STRONG);
        }
        if (style.isItalic()) {
            marks.add(Mark.EM);
        }
        if (style.isStrikethrough()) {
            marks.add(Mark.STRIKE);
        }
        if (style.isCodeSpan()) {
            marks.add(Mark.CODE);
        }
        String dest = null;
        if (style.getLink() != null) {
            marks.add(Mark.LINK);
            dest = style.getLink();
        }
        return new InlineTok(marks, seg.getText(), dest);
    }

    /** 有序标记文本判定（链尾形态 "N. " / "N) "，源序号原文，见 bareListMarker）。 */
    static boolean isOrderedMarker(String markerText) {
        return ordinalOfMarker(markerText) > 0;
    }

    /** 有序标记取数（"3. "→3，"3) "→3；bullet/非法→0）。 */
    static int ordinalOfMarker(String markerText) {
        String s = markerText == null ? "" : markerText.trim();
        if (s.length() < 2 || (s.charAt(s.length() - 1) != '.' && s.charAt(s.length() - 1) != ')')) {
            return 0;
        }
        int n = 0;
        int i = 0;
        while (i < s.length() - 1 && Character.isDigit(s.charAt(i))) {
            n = n * 10 + (s.charAt(i) - '0');
            i++;
        }
        return i == s.length() - 1 ? n : 0;
    }

    /** 相邻同形 token 合并（与 R 路 mergeAdjacentText 对偶）。 */
    private static List<InlineTok> mergeAdjacent(List<InlineTok> in) {
        List<InlineTok> out = new ArrayList<InlineTok>(in.size());
        for (InlineTok t : in) {
            if (!out.isEmpty()) {
                InlineTok last = out.get(out.size() - 1);
                if (last.marks.equals(t.marks) && CommonMarkReferenceSemantics.eq(last.linkDest,
                        t.linkDest)) {
                    out.set(out.size() - 1,
                            new InlineTok(last.marks, last.text + t.text, last.linkDest));
                    continue;
                }
            }
            out.add(t);
        }
        return out;
    }

    /**
     * 接缝原文可见串（不经语义 token 化的独立口径）：逐段拼接，latex 以 \u27e6源\u27e7
     * 占位，行界 '\n'。与 {@link #extract} 的 joined 对比即「提取器吞字」地板断言。
     */
    static String seamVisibleOf(String source) {
        TextStyle base = new TextStyle();
        base.setColor(0xFFFFFFFF);
        List<MarkdownLayoutLine> raw = MarkdownDocument.parse(source)
                .toLayoutLines(MarkdownStyleTable.defaults(), base);
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (MarkdownLayoutLine line : raw) {
            if (line.getSegments().isEmpty() && line.getBlockId() == MarkdownLayoutLine.NO_BLOCK) {
                continue;
            }
            if (!first) {
                sb.append('\n');
            }
            first = false;
            for (TextSegment seg : line.getSegments()) {
                sb.append(seg.isLatex()
                        ? "\u27e6" + seg.getLatexSource() + "\u27e7" : seg.getText());
            }
        }
        return sb.toString();
    }

    /** 段接缝（toSegments）的同一可见串：两接缝文本同源等值断言用（C1a 起两接缝一字不差）。 */
    static String segmentSeamVisible(String source) {
        TextStyle base = new TextStyle();
        base.setColor(0xFFFFFFFF);
        StringBuilder sb = new StringBuilder();
        for (TextSegment seg : MarkdownDocument.parse(source)
                .toSegments(MarkdownStyleTable.defaults(), base)) {
            if (seg.isLatex()) {
                sb.append("\u27e6").append(seg.getLatexSource()).append("\u27e7");
            } else {
                // 段接缝的行界有三种形态（块间独立 \n 段、段内软换行嵌于文本、F6 零宽段）
                // 一律不计——本对比只钉「可见字符零丢失」，不比行界。
                sb.append(seg.getText().replace("\n", ""));
            }
        }
        return sb.toString();
    }

    static List<SemanticLine> emptyLines() {
        return Collections.emptyList();
    }
}
