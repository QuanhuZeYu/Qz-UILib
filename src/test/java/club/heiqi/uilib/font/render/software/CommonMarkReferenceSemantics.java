package club.heiqi.uilib.font.render.software;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.SourceSpan;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

/**
 * C3b1 重基线（2026-09-06 对齐裁定）：CommonMark 参考语义提取器——门禁对拍的 <b>R 路</b>。
 *
 * <p>用 org.commonmark:commonmark:0.21.0（+ GFM strikethrough 扩展，对齐本仓 ~~删~~ 支持）
 * parse 源文本，递归 AST 提取<b>语义行列表</b> {@link SemanticLine}；文本一律取节点字面
 * （反斜杠转义已被 commonmark 剥除，与本仓 L1 行内层同口径）。纯测试域，零生产依赖。</p>
 *
 * <h3>与 B 路（{@link BPathSemantics}）共用的行 kind 口径（两提取器同构，此处记死）</h3>
 * <ul>
 *   <li><b>kind 优先级</b>：CODE（围栏/缩进）＞ THEMATIC_BREAK ＞ 列表归属（LIST_ITEM，
 *       含项内标题/段落/续行）＞ HEADING(level)（C3b3 起 B 侧接缝同带标题身份，两侧直拍；
 *       原 HEADING_STYLE_ONLY 登记域已整体废止）＞ BLOCK_QUOTE(depth) ＞ TEXT。
 *       引用/列表嵌套层数同时记在 {@link SemanticLine#quoteDepth}/{@link SemanticLine#listDepth}
 *       两个正交字段上，kind 只取最高优先级者（单 kind 模型对组合块的表达口径）。</li>
 *   <li><b>行粒度＝逻辑行</b>：Paragraph/Heading 内 SoftLineBreak、HardLineBreak 都断行
 *       （对齐 B 路 M10d 行接缝的段内换行断行；软/硬身份 B 接缝不携带，R 侧记
 *       {@link SemanticLine#note} 供矩阵注明）。代码块按 literal 逐行字面（commonmark 的
 *       literal 已剥块缩进/围栏缩进）。空行占位（B 侧 F6）在 R 不存在——对齐前由矩阵
 *       侧剔除并在 profiles 记数。</li>
 *   <li><b>列表项标记</b>：R 侧 AST 不携带 marker 原文（无序只有字符、有序只有
 *       startNumber，续项按 start+项下标推算），故合成 {@link Mark#LIST_MARKER} 行首
 *       token：bullet 归一为样式表符号「\u2022 」，有序取 (start+项下标) + ". "。
 *       C3b2 起本仓 L1 与本合同口径一致（有序续排 + 定界归一句点），该域两侧应零差异——
 *       EXT_ORDERED_START 域登记保留在门禁词表里防回潮，语料已撤豁免直接对拍。
 *       已知口径缺口：「项的第一块即嵌套列表/引用直落」等病态形态的合成 marker 归
 *       最内层项（父 marker 丢行）——语料不含该形态，出现即矩阵 LINE_ALIGN 照登。</li>
 *   <li><b>HTML</b>：HtmlBlock/HtmlInline 字面进 {@link Mark#RAW_HTML} token（本仓刻意不
 *       支持 HTML，B 侧对应字面 TEXT，差异按 EXT_HTML 域登记）。</li>
 *   <li><b>图片</b>：commonmark 把 {@code ![alt](url)} 解析为 Image 元素（alt 是属性、非
 *       可见链接文本）；本仓裁定（规划 §五 D2）是字面 {@code !} + 链接段。R 侧取主流
 *       表达：Image 子节点（alt 文本）按现标记集透传、{@code !} 与 url 不进可见 token
 *       ——与 B 的 {@code !}+LINK 差按 IMAGE_LITERAL 域登记。</li>
 *   <li><b>公式</b>：commonmark 无 $ 语法，{@code $...$} 是普通 TEXT；B 侧 FORMULA token
 *       的差异按 EXT_FORMULA 域登记。</li>
 * </ul>
 */
final class CommonMarkReferenceSemantics {

    private CommonMarkReferenceSemantics() {
    }

    // ==================== R/B 共用数据结构 ====================

    /** 行块类别（kind 优先级见类 javadoc）。 */
    enum Kind {
        TEXT, HEADING, CODE_FENCED, CODE_INDENTED, THEMATIC_BREAK, BLOCK_QUOTE, LIST_ITEM
    }

    /** 行内 token 标记集（空集=纯文本；嵌套强调按标记并集压平，与 B 路段样式位对拍）。 */
    enum Mark {
        EM, STRONG, CODE, STRIKE, LINK, FORMULA, LIST_MARKER, RAW_HTML
    }

    /** 行内 token：标记集 + 字面文本 + 链接目标（仅 LINK 非 null）。 */
    static final class InlineTok {
        final EnumSet<Mark> marks;
        final String text;
        final String linkDest;

        InlineTok(EnumSet<Mark> marks, String text, String linkDest) {
            this.marks = marks;
            this.text = text;
            this.linkDest = linkDest;
        }

        boolean sameShape(InlineTok o) {
            return marks.equals(o.marks) && text.equals(o.text) && eq(linkDest, o.linkDest);
        }

        /** 矩阵展示形态：{@code \u00ab文本\u00bb{SEXLHF}\ dest=…}；marker 特殊形态。 */
        String render() {
            StringBuilder sb = new StringBuilder();
            if (marks.contains(Mark.LIST_MARKER)) {
                sb.append("[marker]").append(sp(text));
                return sb.toString();
            }
            sb.append('\u00ab').append(sp(text)).append('\u00bb');
            if (marks.contains(Mark.STRONG)) {
                sb.append('S');
            }
            if (marks.contains(Mark.EM)) {
                sb.append('E');
            }
            if (marks.contains(Mark.CODE)) {
                sb.append('C');
            }
            if (marks.contains(Mark.STRIKE)) {
                sb.append('X');
            }
            if (marks.contains(Mark.LINK)) {
                sb.append('L');
            }
            if (marks.contains(Mark.FORMULA)) {
                sb.append('F');
            }
            if (marks.contains(Mark.RAW_HTML)) {
                sb.append('H');
            }
            if (linkDest != null) {
                sb.append(" dest=").append(linkDest);
            }
            return sb.toString();
        }

        private static String sp(String s) {
            return s.replace(' ', '\u00b7').replace("\n", "\u23ce");
        }
    }

    /** 一条语义行：块 kind（含 level/ordered/ordinal/depth 参数）+ 行内 token 列表。 */
    static final class SemanticLine {
        final Kind kind;
        /** HEADING=级别；BLOCK_QUOTE=引用深度；LIST_ITEM=列表嵌套深度；其余 0。 */
        final int level;
        /** LIST_ITEM：是否有序（R=列表 start、B=marker 可解析出序号）。 */
        final boolean ordered;
        /** LIST_ITEM 参考序：R=start+项下标；B=源序号原文数值；非首行/无序 0。 */
        final int ordinal;
        final int quoteDepth;
        final int listDepth;
        /** 断行/来源注记（SOFT/HARD/SETEXT/H<n>；B 侧恒 null——接缝不携带该身份）。 */
        final String note;
        final List<InlineTok> tokens;

        SemanticLine(Kind kind, int level, boolean ordered, int ordinal, int quoteDepth,
                int listDepth, String note, List<InlineTok> tokens) {
            this.kind = kind;
            this.level = level;
            this.ordered = ordered;
            this.ordinal = ordinal;
            this.quoteDepth = quoteDepth;
            this.listDepth = listDepth;
            this.note = note;
            this.tokens = Collections.unmodifiableList(new ArrayList<InlineTok>(tokens));
        }

        String renderKind() {
            StringBuilder sb = new StringBuilder();
            sb.append(kind);
            if (kind == Kind.HEADING || kind == Kind.BLOCK_QUOTE) {
                sb.append('(').append(Integer.valueOf(level)).append(')');
            } else if (kind == Kind.LIST_ITEM) {
                sb.append(ordered
                        ? "(ordered,ord=" + Integer.valueOf(ordinal) + ",depth="
                                + Integer.valueOf(level) + ")"
                        : "(bullet,depth=" + Integer.valueOf(level) + ")");
            }
            if (kind != Kind.BLOCK_QUOTE && kind != Kind.HEADING && quoteDepth > 0) {
                sb.append("[q").append(Integer.valueOf(quoteDepth)).append(']');
            }
            if (kind != Kind.LIST_ITEM && listDepth > 0) {
                sb.append("[l").append(Integer.valueOf(listDepth)).append(']');
            }
            if (note != null) {
                sb.append(" \u21b6").append(note);
            }
            return sb.toString();
        }

        String renderTokens() {
            StringBuilder sb = new StringBuilder();
            for (InlineTok t : tokens) {
                if (sb.length() > 0) {
                    sb.append(" | ");
                }
                sb.append(t.render());
            }
            return sb.toString();
        }

        /** 行可见文本（FORMULA 按 \u27e6源\u27e7 占位，与门禁 B 侧不吞字口径同构）。 */
        String visible() {
            StringBuilder sb = new StringBuilder();
            for (InlineTok t : tokens) {
                sb.append(t.marks.contains(Mark.FORMULA)
                        ? "\u27e6" + t.text + "\u27e7" : t.text);
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            String t = renderTokens();
            return renderKind() + (t.isEmpty() ? "" : " " + t);
        }
    }

    /** 无序 marker 归一样式表符号（{@code MarkdownStyleTable.defaults().getBulletMarker()+" "}）。 */
    static final String BULLET_MARK = "\u2022 ";

    /**
     * kind 家族归一：CODE_FENCED / CODE_INDENTED 与 B 路统一 CODE 同类（本仓行接缝不区分
     * 围栏/缩进、info 语言忽略）；矩阵只在跨家族差异时报 KIND。
     */
    static String kindFamily(Kind k) {
        return k == Kind.CODE_FENCED || k == Kind.CODE_INDENTED ? "CODE" : k.name();
    }

    static boolean eq(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    // ==================== R 路提取 ====================

    /** 默认 Parser + GFM strikethrough 扩展（本仓 ~~删~~ 支持的对齐参考）。 */
    static List<SemanticLine> parse(String source) {
        String src = source == null ? "" : source;
        Parser parser = Parser.builder()
                .extensions(Collections.singletonList(StrikethroughExtension.create()))
                // sourceSpan 默认不记录；setext 判定按行号对位必须开 BLOCKS 级
                .includeSourceSpans(IncludeSourceSpans.BLOCKS)
                .build();
        Node doc = parser.parse(src);
        List<SemanticLine> out = new ArrayList<SemanticLine>();
        walk(doc, out, 0, 0, 0, false, null, src);
        return out;
    }

    /**
     * 块层递归。
     *
     * @param quote    当前引用嵌套层数
     * @param listDepth 当前列表嵌套层数（0=不在列表项内）
     * @param ordinalRef 当前列表项参考序（有序=start+下标；无序 0）
     * @param ordered  当前列表是否有序
     * @param marker   待落合成 LIST_MARKER token 的单槽（null=不在有标记的项内）
     */
    private static void walk(Node parent, List<SemanticLine> out, int quote, int listDepth,
            int ordinalRef, boolean ordered, Marker marker, String source) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNext()) {
            if (n instanceof Paragraph) {
                Kind kind;
                int level;
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
                emitLines(n, out, kind, level, kind == Kind.LIST_ITEM ? ordered : false,
                        kind == Kind.LIST_ITEM ? ordinalRef : 0, quote, listDepth, marker, null);
            } else if (n instanceof Heading) {
                Heading h = (Heading) n;
                if (listDepth > 0) {
                    // 项内标题：kind 让位 LIST_ITEM（链优先，B 路同构），级别进 note 留档
                    emitLines(n, out, Kind.LIST_ITEM, listDepth, ordered, ordinalRef, quote,
                            listDepth, marker, "H" + Integer.valueOf(h.getLevel()));
                } else {
                    emitLines(n, out, Kind.HEADING, h.getLevel(), false, 0, quote, listDepth,
                            marker, setextNote(h, source));
                }
            } else if (n instanceof FencedCodeBlock) {
                emitCode(((FencedCodeBlock) n).getLiteral(), Kind.CODE_FENCED, out, quote,
                        listDepth);
            } else if (n instanceof IndentedCodeBlock) {
                emitCode(((IndentedCodeBlock) n).getLiteral(), Kind.CODE_INDENTED, out, quote,
                        listDepth);
            } else if (n instanceof ThematicBreak) {
                out.add(new SemanticLine(Kind.THEMATIC_BREAK, 0, false, 0, quote, listDepth,
                        null, Collections.<InlineTok>emptyList()));
            } else if (n instanceof BlockQuote) {
                walk(n, out, quote + 1, listDepth, ordinalRef, ordered, marker, source);
            } else if (n instanceof BulletList) {
                walkListItems(n, out, quote, listDepth + 1, false, 0, source);
            } else if (n instanceof OrderedList) {
                walkListItems(n, out, quote, listDepth + 1, true,
                        (int) ((OrderedList) n).getStartNumber(), source);
            } else if (n instanceof HtmlBlock) {
                for (String line : splitLines(((HtmlBlock) n).getLiteral())) {
                    List<InlineTok> toks = new ArrayList<InlineTok>(1);
                    if (!line.isEmpty()) {
                        toks.add(tok(EnumSet.of(Mark.RAW_HTML), line));
                    }
                    out.add(new SemanticLine(kindOfTextish(quote, listDepth), 0, false, 0, quote,
                            listDepth, null, toks));
                }
            }
            // 其余块（ReferenceDefinition 等）不产行——与 B 侧「不识别即字面段落」的差
            // 会在行对不齐时按 LINE_ALIGN 照登。
        }
    }

    /** 列表子项循环：每项新建合成 marker 槽（父项 pending marker 到此为止，见类 javadoc）。 */
    private static void walkListItems(Node listNode, List<SemanticLine> out, int quote,
            int listDepth, boolean ordered, int startNumber, String source) {
        int index = 0;
        for (Node item = listNode.getFirstChild(); item != null; item = item.getNext()) {
            if (item instanceof ListItem) {
                int ordinal = ordered ? startNumber + index : 0;
                Marker marker = new Marker(ordered
                        ? String.valueOf(startNumber + index) + ". " : BULLET_MARK);
                walk(item, out, quote, listDepth, ordinal, ordered, marker, source);
                index++;
            }
        }
    }

    private static Kind kindOfTextish(int quote, int listDepth) {
        return listDepth > 0 ? Kind.LIST_ITEM : (quote > 0 ? Kind.BLOCK_QUOTE : Kind.TEXT);
    }

    /** 段落/标题类块 → 逐逻辑行（软/硬断行切开；marker 落首行行首）。 */
    private static void emitLines(Node block, List<SemanticLine> out, Kind kind, int level,
            boolean ordered, int ordinal, int quote, int listDepth, Marker marker, String note) {
        LineBuilder b = new LineBuilder(out, kind, level, ordered, ordinal, quote, listDepth,
                marker, note);
        collect(block.getFirstChild(), b, EnumSet.noneOf(Mark.class), null);
        b.finishIfOpen();
    }

    /** 行内递归：标记集随嵌套累积，断行节点切行。 */
    private static void collect(Node child, LineBuilder b, EnumSet<Mark> marks, String linkDest) {
        for (Node n = child; n != null; n = n.getNext()) {
            if (n instanceof Text) {
                b.add(marks, linkDest, ((Text) n).getLiteral());
            } else if (n instanceof Code) {
                b.add(with(marks, Mark.CODE), linkDest, ((Code) n).getLiteral());
            } else if (n instanceof Emphasis) {
                collect(n.getFirstChild(), b, with(marks, Mark.EM), linkDest);
            } else if (n instanceof StrongEmphasis) {
                collect(n.getFirstChild(), b, with(marks, Mark.STRONG), linkDest);
            } else if (n instanceof Strikethrough) {
                collect(n.getFirstChild(), b, with(marks, Mark.STRIKE), linkDest);
            } else if (n instanceof Link) {
                collect(n.getFirstChild(), b, with(marks, Mark.LINK), ((Link) n).getDestination());
            } else if (n instanceof Image) {
                // 图片口径见类 javadoc：alt 子节点透传，! 与 url 不进可见 token
                collect(n.getFirstChild(), b, marks, linkDest);
            } else if (n instanceof HtmlInline) {
                b.add(with(marks, Mark.RAW_HTML), linkDest, ((HtmlInline) n).getLiteral());
            } else if (n instanceof SoftLineBreak) {
                b.lineBreak("SOFT");
            } else if (n instanceof HardLineBreak) {
                b.lineBreak("HARD");
            }
        }
    }

    private static EnumSet<Mark> with(EnumSet<Mark> marks, Mark extra) {
        EnumSet<Mark> out = EnumSet.copyOf(marks);
        out.add(extra);
        return out;
    }

    /** 代码块 literal → 逐行字面（commonmark literal 以换行结尾，剥尾换行后按行装）。 */
    private static void emitCode(String literal, Kind kind, List<SemanticLine> out, int quote,
            int listDepth) {
        for (String text : splitLines(literal)) {
            List<InlineTok> toks = new ArrayList<InlineTok>(1);
            if (!text.isEmpty()) {
                toks.add(tok(EnumSet.noneOf(Mark.class), text));
            }
            out.add(new SemanticLine(kind, 0, false, 0, quote, listDepth, null, toks));
        }
    }

    /** 拆行：单个尾随换行不产空尾行；其余空行保留。 */
    private static List<String> splitLines(String literal) {
        List<String> out = new ArrayList<String>();
        if (literal == null || literal.isEmpty()) {
            return out;
        }
        String s = literal;
        if (s.endsWith("\n")) {
            s = s.substring(0, s.length() - 1);
        }
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') {
                out.add(s.substring(start, i));
                start = i + 1;
            }
        }
        out.add(s.substring(start));
        return out;
    }

    /**
     * setext 判定（SourceSpan 0.21 只有行/列索引，按行号对位）：标题 span 未从 '#' 起
     * （非 ATX），且 span 末行或紧随一行为「=== / ---」全字符下划线行时记 SETEXT。
     * C3b2 起本仓已支持 setext，该注记只作矩阵展示（\u21b6SETEXT）；标题身份两侧直拍。
     * （SETEXT_NO_SUPPORT 域 C3b2 废止；HEADING_STYLE_ONLY 域随 C3b3 接缝标题身份落地
     * 后整体从词表移除——注记不参与判等，kind 与 level 都必须等。）
     */
    private static String setextNote(Heading h, String source) {
        if (h.getLevel() > 2) {
            return null;
        }
        List<SourceSpan> spans = h.getSourceSpans();
        if (spans == null || spans.isEmpty()) {
            return null;
        }
        int first = Integer.MAX_VALUE;
        int last = -1;
        for (SourceSpan s : spans) {
            if (s.getLineIndex() < first) {
                first = s.getLineIndex();
            }
            if (s.getLineIndex() > last) {
                last = s.getLineIndex();
            }
        }
        List<String> lines = sourceLines(source);
        if (first >= lines.size()) {
            return null;
        }
        String head = lines.get(first).trim();
        if (head.startsWith("#")) {
            return null; // ATX
        }
        char want = h.getLevel() == 1 ? '=' : '-';
        if (last < lines.size() && isUnderline(lines.get(last), want)) {
            return "SETEXT"; // 情形 A：span 覆盖下划线行
        }
        return last + 1 < lines.size() && isUnderline(lines.get(last + 1), want)
                ? "SETEXT" : null; // 情形 B：紧随
    }

    /** 按 CommonMark 行界（\n、\r\n、\r）切源行为行表。 */
    private static List<String> sourceLines(String source) {
        List<String> out = new ArrayList<String>();
        int n = source.length();
        int start = 0;
        for (int i = 0; i < n; i++) {
            char ch = source.charAt(i);
            if (ch == '\n') {
                out.add(source.substring(start, i));
                start = i + 1;
            } else if (ch == '\r') {
                out.add(source.substring(start, i));
                if (i + 1 < n && source.charAt(i + 1) == '\n') {
                    i++;
                }
                start = i + 1;
            }
        }
        if (start < n) {
            out.add(source.substring(start));
        }
        return out;
    }

    /** 整行（trim 后）是否为非空同字符下划线行。 */
    private static boolean isUnderline(String line, char want) {
        String t = line.trim();
        if (t.isEmpty()) {
            return false;
        }
        for (int k = 0; k < t.length(); k++) {
            if (t.charAt(k) != want) {
                return false;
            }
        }
        return true;
    }

    /** 合成 marker 的单槽（首行领取后清空）。 */
    private static final class Marker {
        String text;

        Marker(String text) {
            this.text = text;
        }
    }

    /** 行收集器：断行处封行续行（kind 参数继承，note 随断行更换）。 */
    private static final class LineBuilder {
        private final List<SemanticLine> out;
        private final Kind kind;
        private final int level;
        private final boolean ordered;
        private final int ordinal;
        private final int quote;
        private final int listDepth;
        private final Marker marker;
        private final List<Object[]> cur = new ArrayList<Object[]>();
        private String note;
        private boolean opened;

        LineBuilder(List<SemanticLine> out, Kind kind, int level, boolean ordered, int ordinal,
                int quote, int listDepth, Marker marker, String note) {
            this.out = out;
            this.kind = kind;
            this.level = level;
            this.ordered = ordered;
            this.ordinal = ordinal;
            this.quote = quote;
            this.listDepth = listDepth;
            this.marker = marker;
            this.note = note;
        }

        void add(EnumSet<Mark> marks, String linkDest, String text) {
            if (text.indexOf('\n') < 0) {
                if (!text.isEmpty()) {
                    cur.add(new Object[] { marks, linkDest, text });
                    opened = true;
                }
                return;
            }
            int start = 0;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n') {
                    if (i > start) {
                        cur.add(new Object[] { marks, linkDest, text.substring(start, i) });
                    }
                    opened = true;
                    lineBreak(null);
                    start = i + 1;
                }
            }
            if (start < text.length()) {
                cur.add(new Object[] { marks, linkDest, text.substring(start) });
                opened = true;
            }
        }

        void lineBreak(String breakNote) {
            flush();
            note = breakNote;
        }

        void finishIfOpen() {
            if (opened || (marker != null && marker.text != null && cur.isEmpty())) {
                flush();
            }
        }

        private void flush() {
            List<InlineTok> toks = new ArrayList<InlineTok>(cur.size() + 1);
            if (marker != null && marker.text != null) {
                toks.add(new InlineTok(EnumSet.of(Mark.LIST_MARKER), marker.text, null));
                marker.text = null;
            }
            String pendingText = null;
            EnumSet<Mark> pendingSet = null;
            String pendingDest = null;
            for (Object[] e : cur) {
                @SuppressWarnings("unchecked")
                EnumSet<Mark> m = (EnumSet<Mark>) e[0];
                String dest = (String) e[1];
                String text = (String) e[2];
                if (pendingText != null && pendingSet.equals(m) && eq(pendingDest, dest)) {
                    pendingText = pendingText + text; // 相邻同形合并（见 merge 口径）
                    continue;
                }
                if (pendingText != null) {
                    toks.add(new InlineTok(pendingSet, pendingText, pendingDest));
                }
                pendingSet = EnumSet.copyOf(m);
                pendingText = text;
                pendingDest = dest;
            }
            if (pendingText != null) {
                toks.add(new InlineTok(pendingSet, pendingText, pendingDest));
            }
            out.add(new SemanticLine(kind, level, ordered, ordinal, quote, listDepth, note, toks));
            cur.clear();
            note = null;
            opened = false;
        }
    }

    private static InlineTok tok(EnumSet<Mark> marks, String text) {
        return new InlineTok(EnumSet.copyOf(marks), text, null);
    }
}
