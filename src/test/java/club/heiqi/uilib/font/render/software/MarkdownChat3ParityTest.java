package club.heiqi.uilib.font.render.software;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.font.GlyphRuntimeTablesView;
import club.heiqi.uilib.font.api.DefaultFontRendererAdapter;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.layout.TextLayoutService;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.font.layout.markdown.MarkdownDocument;
import club.heiqi.uilib.font.layout.markdown.MarkdownInlineParser;
import club.heiqi.uilib.font.layout.markdown.MarkdownSpan;
import club.heiqi.uilib.font.layout.markdown.MarkdownStyleTable;
import club.heiqi.uilib.font.render.software.BPathSemantics.Result;
import club.heiqi.uilib.font.render.software.CommonMarkReferenceSemantics.InlineTok;
import club.heiqi.uilib.font.render.software.CommonMarkReferenceSemantics.Kind;
import club.heiqi.uilib.font.render.software.CommonMarkReferenceSemantics.Mark;
import club.heiqi.uilib.font.render.software.CommonMarkReferenceSemantics.SemanticLine;
import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.internal.chat3.viewmodel.ChatUrlLinkifier;
import club.heiqi.uilib.ui.markdown.MarkdownPainter;

/**
 * C3b1 门禁重基线（2026-09-06 对齐裁定「UILib 对齐主流引擎」，AGENTS.md :12）：
 * <b>CommonMark 参考对拍矩阵生成器</b>——旧「chat3 旧行为规格快照 A 路 + 三档判据 +
 * 有意差异登记表」整体废止（A 路 replicate 全套私有方法已删），本类改为：
 *
 * <ul>
 *   <li><b>R 路</b>＝{@link CommonMarkReferenceSemantics}（commonmark-java 0.21.0 +
 *       GFM strikethrough 扩展，官方参考实现）；</li>
 *   <li><b>B 路</b>＝{@link BPathSemantics}（本仓 M10d 行接缝
 *       {@code toLayoutLines} 同构映射）；</li>
 *   <li>逐语料、逐行、逐 token 对齐比较，<b>差异一律写
 *       {@code build/reports/markdown-compare/matrix.txt}，本阶段不判 PASS/FAIL</b>
 *       （重基线中间态，矩阵供父代理与用户逐条裁定后再定新判据）。</li>
 * </ul>
 *
 * <h3>本阶段断言（且仅这些）</h3>
 * <ol>
 *   <li>B 侧不崩：{@code toLayoutLines} 与语义提取全程无异常；</li>
 *   <li>B 侧不吞字：①提取器逐行可见串 == 行接缝原段流拼接（latex 恒 \u27e6源\u27e7）
 *       ②行接缝可见文本与段接缝（{@code toSegments}）去行界后逐字等（C1a 两接缝同源钉）；</li>
 *   <li>matrix.txt 成功落盘、每语料有块头、矩阵引擎由自检用例（已知差异样本驱动）钉死
 *       真实分类——「记录差异」通道不许恒真。</li>
 * </ol>
 *
 * <h3>比较口径（差异域词表，矩阵条目携带其一）</h3>
 * <p>kind 家族归一（CODE_FENCED≡CODE_INDENTED≡B 统一 CODE）；F6 空行占位剔除后逐行序号
 * 直对；marker 归一 bullet=样式表符号「\u2022 」。EXT_FORMULA（$公式，R 无此语法）、
 * EXT_BARE_URL（裸链链接化属消费层扩展，接缝两侧同字面——语料声明用）、
 * EXT_HTML（本仓刻意不支持 HTML）、BRIDGE_SECTION（§ 码桥接域，声明用）、
 * EXT_STRIKE（删除线严格度）、EXT_ORDERED_START（本仓有序保留源序号，R 按 start+下标推算）、
 * HEADING_STYLE_ONLY（本仓行接缝无标题块身份：文本+样式豁免）、SETEXT_NO_SUPPORT（本仓无
 * setext，--- 恒分隔线）、THEMATIC_BREAK_TEXT（分隔线可见 36 连字符为样式表旋钮产物）、
 * EM_FLANK_SIMPLIFIED（emphasis 定界简化差）、ESCAPE_SUBSET（反斜杠转义集差）、
 * CODE_SPAN_TRIM（code span 空格剥离差）、CODE_SCOPE/LINK_DEST/LINK_SCOPE/EXT_AUTOLINK
 * （链接与 code 面）、LIST_DEPTH/QUOTE_DEPTH（层级）、LINE_ALIGN/TOKEN_ALIGN（错位）、
 * TOKEN_TEXT/KIND_MISMATCH（兜底）。</p>
 *
 * <p><b>常驻测试</b>：随 build 全量执行；只读消费者——不改生产代码、不改 internal/chat3/**。
 * 出图/墨水地板/产物目录保留，但只出 B 路单侧图（A 侧图随 A 路一并废止）。
 * 共享装配恒 {@code LatexSoftwareRenderKit.Shared}（严禁另 new FontService）。
 * 软件光栅器对 CJK 有水平重影（M3 复核在案），出图判读聚焦结构。</p>
 */
public class MarkdownChat3ParityTest {

    // ==================== 常量 ====================

    private static final File OUT_DIR = new File("build/reports/markdown-compare");
    private static final int BACKGROUND = 0xFF202020;
    /** 气泡正文基准字号(生产口径 ChatMarkdownSettings.chatFontSizePx=13)。 */
    private static final int BASE = 13;
    /** 主容器宽(约真机 360 视口口径)与窄容器(强制词内硬断/代理对断点)。 */
    private static final int W_MAIN = 269;
    private static final int W_NARROW = 150;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int PAD = 8;
    private static final int LABEL_BASE = 11;
    /** 每张出图墨水地板(低于即「空跑」,属场地缺陷而非对拍差异)。 */
    private static final int MIN_INK_PER_PAGE = 30;
    private static final int[] RENDER_WIDTHS = {W_MAIN, W_NARROW};

    // ---- 差异域词表 ----
    static final String D_FORMULA = "EXT_FORMULA";
    static final String D_BARE_URL = "EXT_BARE_URL";
    static final String D_HTML = "EXT_HTML";
    static final String D_BRIDGE = "BRIDGE_SECTION";
    static final String D_STRIKE = "EXT_STRIKE";
    static final String D_ORDERED = "EXT_ORDERED_START";
    static final String D_HEADING = "HEADING_STYLE_ONLY";
    static final String D_SETEXT = "SETEXT_NO_SUPPORT";
    static final String D_TB_TEXT = "THEMATIC_BREAK_TEXT";
    static final String D_FLANK = "EM_FLANK_SIMPLIFIED";
    static final String D_ESCAPE = "ESCAPE_SUBSET";
    static final String D_CODETRIM = "CODE_SPAN_TRIM";
    static final String D_AUTOLINK = "EXT_AUTOLINK";
    static final String D_LINK_DEST = "LINK_DEST";
    static final String D_LINK_SCOPE = "LINK_SCOPE";
    static final String D_LIST_DEPTH = "LIST_DEPTH";
    static final String D_QUOTE_DEPTH = "QUOTE_DEPTH";
    static final String D_LINE_ALIGN = "LINE_ALIGN";
    static final String D_TOKEN_ALIGN = "TOKEN_ALIGN";
    static final String D_KIND = "KIND_MISMATCH";
    static final String D_MARKER = "MARKER_MISMATCH";
    static final String D_CODE_SCOPE = "CODE_SCOPE";
    static final String D_TOKEN_TEXT = "TOKEN_TEXT";

    // ==================== 语料表 ====================
    // {id, 中文label, §桥 1/0(仅出图接缝口径), 豁免域声明(逗号分隔,"-"=无), 源文本}
    // 原 20 P + 11 N 全保留（P/N 三档判据随 A 路废止，id 沿用便于回溯）；
    // X01..X10 = C3b1 新增主流边界语料（任务书点名场景逐条覆盖，见各行 label）。
    // 学费场景:① 行 junction 丢失 = P11(+P10@150、P17);② 两断行同形陷阱 = P10;③ 圆点剥除与
    // 链接化作用域 = P07(+P08)。防误伤项:$5.99→P06,hello_world/2*3→N11。

    private static final String[][] CORPUS = {
        {"P01", "裸URL+www大写", "0", D_BARE_URL,
            "详见 http://qz.club/download?id=3&v=2 与 WWW.MINECRAFT.NET/download 资料"},
        {"P02", "URL尾随标点剥离", "0", D_BARE_URL,
            "见 https://a.test/x，完了。和 http://b.test/y, ok!"},
        {"P03", "code保护URL", "0", "-",
            "命令 `curl http://x.y/z -s` 执行"},
        {"P04", "整行块公式$$", "0", D_FORMULA,
            "$$\\frac{a}{b}$$"},
        {"P05", "整行单$公式", "0", D_FORMULA,
            "$E=mc^2$"},
        {"P06", "$价格防误伤", "0", D_FORMULA,
            "价格 $5.99 与 100$ 加 x$y 未闭合"},
        {"P07", "列表+链接作用域", "0", D_BARE_URL,
            "- 详见 http://a.b/c 完"},
        {"P08", "嵌套列表缩进", "0", "-",
            "- 甲\n  - 乙\n    - 丙"},
        {"P09", "有序列表", "0", D_ORDERED,
            "1. 第一\n2. 第二"},
        {"P10", "跨行URL续链②", "0", D_BARE_URL,
            "参考 http://qz.example.com/releases/GTNH-Latest-9.zip 完\nhow 都不同"},
        {"P11", "junction长文①", "0", D_BARE_URL,
            "欢迎来到 GTNH 教程 http://gtnh.example.com/wiki/GTNH-New-Horizons-Modpack-2-入门指南 开始吧 world wide web 词边界测试\n第二行 中英 mixed prose with averyveryverylongtokenwithoutanyspaces 结束\n第三行 短"},
        {"P12", "单层引用行", "0", "-",
            "> 引用的文字"},
        {"P13", "§前缀列表行", "0", D_BRIDGE,
            "\u00a7a- 玩家列表行"},
        {"P14", "§颜色混排桥", "1", D_BRIDGE,
            "\u00a7c红色警告 \u00a7fplain tail mixed English 123 长到需要断行才能放下更多内容"},
        {"P15", "emoji代理对", "0", "-",
            "服务器 \uD83D\uDE80 发射！\uD83C\uDF89\uD83C\uDF89 成功 launch ok 后接更多内容以便在窄容器把断点推到代理对附近"},
        {"P16", "空行", "0", "-",
            "甲\n\n乙"},
        {"P17", "超长单行", "0", "-",
            "这是超长单行的中文部分用于测试窄容器下的逐字硬断行为它没有任何空白所以只能按字符硬断并且混入englishsegmentwithoutanyspace这种无空白英文串再加上数字1234567890和符号_-.+=/?来覆盖硬断路径的所有字符类别最后以简短收尾"},
        {"P18", "普通文本基线", "0", "-",
            "普通聊天文字 mixed English 12345"},
        {"P19", "深缩进独立列表行", "0", "-",
            "    - deep"},
        {"P20", "二层引用长文扣宽断点", "0", "-",
            ">> 引用的长文要长到在两百六十九与一百五十两档都必然折行以此证明每层八像素的扣宽真的影响断点而不是纸面几何门禁通道重构方案甲落地之后可见文本仍须逐字不变缩进只走行盒与图元"},
        {"N01", "ATX标题", "0", D_HEADING,
            "# 一级标题\n##### 五级标题"},
        {"N02", "围栏代码", "0", "-",
            "```java\nint x = 1; // **粗** $y$ > 引号 全字面\n```"},
        {"N03", "嵌套引用", "0", "-",
            "> 甲\n>> 乙\n>>> 丙"},
        {"N04", "列表续行", "0", "-",
            "- 甲项\n  甲项缩进续行"},
        {"N05", "硬换行", "0", "-",
            "第一行  \n第二行\\\n第三行"},
        {"N06", "分隔线", "0", D_SETEXT + "," + D_TB_TEXT,
            "上半句。\n---\n下半句。"},
        {"N07", "行内强调", "0", D_STRIKE + "," + D_FLANK,
            "**粗** *斜* ~~删~~ ***粗斜*** 混排"},
        {"N08", "行内公式", "0", D_FORMULA,
            "质能 $e=mc^2$ 行内混排 with 尾"},
        {"N09", "链接语法", "0", "-",
            "访问 [Qz 主页](https://example.com/qz) 详情"},
        {"N10", "反斜杠转义", "0", D_ESCAPE,
            "路径 C:\\temp 与 \\* 星号 \\`x\\` 字面"},
        {"N11", "emphasis防误伤", "0", D_FLANK,
            "hello_world 与 a*b，2*3=6 和 x_1 a**b**c"},
        {"X01", "缩进代码块多行与空行中断", "0", "-",
            "    缩进代码甲行\n    缩进代码乙行\n\n    空行后再起丙行"},
        {"X02", "段落后续行4空格折叠", "0", "-",
            "段落第一行开头\n    带四空格前导的续行应折叠进同一段落"},
        {"X03", "三空格顶级列表", "0", "-",
            "   - 三空格缩进的顶级项甲\n   - 三空格缩进的顶级项乙"},
        {"X04", "宽标记内容列12.", "0", D_ORDERED,
            "12. 两位数宽标记的项\n13.   标记后多空格仍属同一内容列"},
        {"X05", "有序跨序号续排", "0", D_ORDERED,
            "3. 源序号三\n1. 源序号一\n9. 源序号九"},
        {"X06", "列表内空行松紧", "0", "-",
            "- 紧凑项甲\n- 松项第一段\n\n  松项第二段\n- 紧凑项乙"},
        {"X07", "引用内列表", "0", "-",
            "> - 引用内项甲\n>   引用内惰性续行\n> - 引用内项乙"},
        {"X08", "围栏带语言标识", "0", "-",
            "```swift\nlet x = 1 // **粗** 与 ~~删~~ 均字面\n```"},
        {"X09", "行内混排嵌套强调", "0", D_STRIKE + "," + D_FLANK,
            "**外粗 *内外都粗斜* 尾** 与 ~~删中 **粗删嵌套** 尾~~ 混排"},
        {"X10", "分隔线与列表歧义", "0", D_SETEXT + "," + D_TB_TEXT,
            "歧义上句\n---\n- 列表项甲\n---\n歧义下句"},
    };

    private static final StringBuilder MATRIX = new StringBuilder();
    private static final StringBuilder PROFILE = new StringBuilder();
    private static int diffTotal;
    private static int blankTotal;
    private static int pngCount;

    // ==================== 生命周期 ====================

    private int savedWidthMissBudget = -1;

    /** 与 M3 出图同纪律:测量期解除宽度 miss 预算,保证 B 路度量稳定同源。 */
    @Before
    public void liftWidthMissBudget() {
        savedWidthMissBudget = FontConfig.widthCacheMissBudgetPerWindow;
        FontConfig.widthCacheMissBudgetPerWindow = 0;
    }

    @After
    public void restoreWidthMissBudget() {
        FontConfig.widthCacheMissBudgetPerWindow = savedWidthMissBudget;
    }

    @AfterClass
    public static void releaseAndWriteReports() throws Exception {
        OUT_DIR.mkdirs();
        Files.write(new File(OUT_DIR, "matrix.txt").toPath(),
                MATRIX.toString().getBytes(StandardCharsets.UTF_8));
        Files.write(new File(OUT_DIR, "profiles.txt").toPath(),
                PROFILE.toString().getBytes(StandardCharsets.UTF_8));
        LatexSoftwareRenderKit.resetShared();
    }

    // ==================== 主流程：矩阵生成 ====================

    /** 全语料 R(commonmark)/B(本仓) 语义对拍矩阵；断言仅 B 不崩/不吞字 + 矩阵落盘+地板。 */
    @Test
    public void buildCommonMarkReferenceMatrix() throws Exception {
        if (!OUT_DIR.exists() && !OUT_DIR.mkdirs()) {
            throw new IllegalStateException("无法创建对拍产物目录: " + OUT_DIR);
        }
        LatexSoftwareRenderKit.Shared shared = LatexSoftwareRenderKit.shared();
        final TextLayoutService service = shared.service;
        writeMatrixHeader();
        PROFILE.append("env jvm=").append(System.getProperty("java.version"))
                .append(" os=").append(System.getProperty("os.name")).append('\n');
        PROFILE.append("fontScene ").append(LatexSoftwareRenderKit.platformFontReport()).append('\n');
        PROFILE.append("render base=").append(Integer.valueOf(BASE))
                .append(" widths=").append(Arrays.toString(RENDER_WIDTHS)).append('\n');

        // 装配阶段：先喂全部 B 出图段流的字形再渲染（与 M3 同纪律；语义提取本身零度量）。
        // 出图走旧 B 路消费接缝（toSegments→linkify→wrapLines）；矩阵走 toLayoutLines。
        List<TextSegment> all = new ArrayList<TextSegment>();
        List<List<TextSegment>> linkifiedPerEntry = new ArrayList<List<TextSegment>>();
        for (String[] entry : CORPUS) {
            String src = entry[4];
            boolean bridge = "1".equals(entry[2]);
            List<TextSegment> doc = bridge
                    ? bridgeSegments(src, service)
                    : MarkdownDocument.parse(src)
                            .toSegments(MarkdownStyleTable.defaults(), bodyStyle());
            List<TextSegment> linkified =
                    ChatUrlLinkifier.linkify(doc, ChatMarkdownSettings.getLinkArgb());
            all.addAll(doc);
            all.addAll(linkified);
            linkifiedPerEntry.add(linkified);
        }
        LatexSoftwareRenderKit.assembleGlyphs(shared, all);
        GlyphRuntimeTablesView view =
                GlyphRuntimeTablesView.snapshot(shared.tables, shared.manager, 1);

        for (int e = 0; e < CORPUS.length; e++) {
            String[] entry = CORPUS[e];
            String id = entry[0];
            String label = entry[1];
            String exemptions = entry[3];
            String src = entry[4];

            List<SemanticLine> r = CommonMarkReferenceSemantics.parse(src);
            Result b = BPathSemantics.extract(src);
            blankTotal += b.blanksRemoved;

            // —— 本阶段断言①②：B 不崩（走到此即未崩）+ 不吞字（两条逐字等值）——
            Assert.assertEquals(id + " B 语义提取器不得吞字/改字：提取可见串必须逐字等于行接缝原段流拼接",
                    BPathSemantics.seamVisibleOf(src), b.seamVisibleJoined);
            Assert.assertEquals(id + " 行接缝可见文本必须与段接缝逐字等（C1a 两接缝同源；去行界比较）",
                    b.seamVisibleJoined.replace("\n", ""), BPathSemantics.segmentSeamVisible(src));
            Assert.assertTrue(id + " 语料非空时 B 侧不得零行输出: " + b.lines,
                    !src.trim().isEmpty() ? !b.lines.isEmpty() : b.lines.isEmpty());

            int diffs = compareEntry(id, label, exemptions, r, b.lines, b.blanksRemoved, MATRIX);
            diffTotal += diffs;

            // —— 出图：B 路单侧（A 侧图随 A 路废止）——
            for (int w : RENDER_WIDTHS) {
                List<List<TextSegment>> wrapped = MarkdownPainter.wrapLines(
                        linkifiedPerEntry.get(e), service, w, BASE);
                renderBPage(shared, view, service, id, label, w, wrapped);
                if (w == W_MAIN) {
                    renderBPageNx(shared, view, service, id, label, w, wrapped);
                }
            }
        }

        MATRIX.append("#\n# 汇总: 条目=").append(Integer.valueOf(CORPUS.length))
                .append(" 差异=").append(Integer.valueOf(diffTotal))
                .append(" F6剔行=").append(Integer.valueOf(blankTotal))
                .append(" PNG=").append(Integer.valueOf(pngCount))
                .append(" 本阶段不判PASS/FAIL(重基线中间态)\n");
        PROFILE.append("summary entries=").append(Integer.valueOf(CORPUS.length))
                .append(" diffs=").append(Integer.valueOf(diffTotal))
                .append(" blanksRemoved=").append(Integer.valueOf(blankTotal)).append('\n');

        // —— 本阶段断言③：矩阵落盘 + 反空转地板 ——
        Files.write(new File(OUT_DIR, "matrix.txt").toPath(),
                MATRIX.toString().getBytes(StandardCharsets.UTF_8));
        File matrix = new File(OUT_DIR, "matrix.txt");
        Assert.assertTrue("matrix.txt 必须成功生成: " + matrix,
                matrix.isFile() && matrix.length() > 0);
        String text = new String(Files.readAllBytes(matrix.toPath()), StandardCharsets.UTF_8);
        int blocks = 0;
        for (String line : text.split("\n", -1)) {
            if (line.startsWith("## ")) {
                blocks++;
            }
        }
        Assert.assertTrue("矩阵必须逐语料成块（块数=条目数）: blocks=" + Integer.valueOf(blocks),
                blocks == CORPUS.length);
        Assert.assertTrue("矩阵正文地板（非空转）: len=" + Integer.valueOf(MATRIX.length()),
                MATRIX.length() >= CORPUS.length * 40);
        Assert.assertTrue("B 侧出图数量地板: png=" + Integer.valueOf(pngCount),
                pngCount >= CORPUS.length * RENDER_WIDTHS.length);
    }

    // ==================== 矩阵引擎 ====================

    /**
     * 单语料逐行逐 token 对齐比较，差异追加到 out；返回差异条数。
     * 行对齐＝剔除 F6 空行后的序号直对（不做 LCS——错位本身按 LINE_ALIGN/TOKEN_ALIGN
     * 照登，供裁定层看清全貌）。kind 家族归一见 {@code kindFamily}；行级 kind 差异已
     * 含 token 面成因（如同域）不重复刷行。
     */
    static int compareEntry(String id, String label, String exemptions, List<SemanticLine> r,
            List<SemanticLine> b, int blanksRemoved, StringBuilder out) {
        out.append("\n## ").append(id).append(" \u300c").append(label).append("\u300d")
                .append(" 豁免域=").append(exemptions)
                .append(" 行数R=").append(Integer.valueOf(r.size()))
                .append(" 行数B=").append(Integer.valueOf(b.size()))
                .append(" F6剔行=").append(Integer.valueOf(blanksRemoved)).append('\n');
        int diffs = 0;
        int n = Math.max(r.size(), b.size());
        for (int i = 0; i < n; i++) {
            SemanticLine rl = i < r.size() ? r.get(i) : null;
            SemanticLine bl = i < b.size() ? b.get(i) : null;
            if (rl == null || bl == null) {
                out.append(row(id, i, D_LINE_ALIGN,
                        rl == null ? "\u2205(本侧无此行)" : render(rl),
                        bl == null ? "\u2205(本侧无此行)" : render(bl))).append('\n');
                diffs++;
                continue;
            }
            String kindDom = classifyKind(rl, bl);
            if (kindDom != null) {
                out.append(row(id, i, kindDom, render(rl), render(bl))).append('\n');
                diffs++;
            }
            int m = Math.min(rl.tokens.size(), bl.tokens.size());
            for (int k = 0; k < m; k++) {
                InlineTok rt = rl.tokens.get(k);
                InlineTok bt = bl.tokens.get(k);
                if (rt.sameShape(bt)) {
                    continue;
                }
                String dom = classifyToken(rl, bl, rt, bt);
                if (kindDom != null && dom.equals(kindDom)) {
                    continue; // 行级 kind 差异已含该成因，不重复刷
                }
                if (kindDom != null && (D_HEADING.equals(kindDom) || D_SETEXT.equals(kindDom))
                        && rt.text.equals(bt.text)) {
                    continue; // 标题豁免：行级已登记一次，token 面「只多一个 S 标记」不再刷
                }
                out.append(row(id, i, dom,
                        lineTok(rl, k), lineTok(bl, k))).append('\n');
                diffs++;
            }
            if (rl.tokens.size() != bl.tokens.size()) {
                String dom = alignDomain(rl, bl);
                if (kindDom != null && dom.equals(kindDom)) {
                    continue;
                }
                out.append(row(id, i, dom, render(rl), render(bl))).append('\n');
                diffs++;
            }
        }
        if (diffs == 0) {
            out.append("| 行* | NO_DIFF | 本条目 R/B 语义全等 |\n");
        }
        return diffs;
    }

    /** 行 kind 差异域（null=kind 家族与参数全等）。 */
    private static String classifyKind(SemanticLine rl, SemanticLine bl) {
        String fr = CommonMarkReferenceSemantics.kindFamily(rl.kind);
        String fb = CommonMarkReferenceSemantics.kindFamily(bl.kind);
        if (fr.equals(fb)) {
            if (rl.kind == Kind.LIST_ITEM && bl.kind == Kind.LIST_ITEM) {
                if (rl.level != bl.level) {
                    return D_LIST_DEPTH;
                }
                if (rl.ordered != bl.ordered || rl.ordinal != bl.ordinal) {
                    return D_ORDERED;
                }
            }
            if (rl.kind == Kind.BLOCK_QUOTE && bl.kind == Kind.BLOCK_QUOTE
                    && rl.level != bl.level) {
                return D_QUOTE_DEPTH;
            }
            if (rl.quoteDepth != bl.quoteDepth) {
                return D_QUOTE_DEPTH;
            }
            if (rl.listDepth != bl.listDepth) {
                return D_LIST_DEPTH;
            }
            return null;
        }
        if (rl.kind == Kind.HEADING
                && (bl.kind == Kind.TEXT || bl.kind == Kind.BLOCK_QUOTE)) {
            return "SETEXT".equals(rl.note) ? D_SETEXT : D_HEADING;
        }
        if (startsWithSection(rl) || startsWithSection(bl)) {
            return D_BRIDGE; // § 前导参与块身份判定（本仓 L1 剥 § 后才识别标记）→ 桥接域
        }
        return D_KIND;
    }

    /** token 差异域分类（优先级：marker→公式→HTML→分隔线→链接→样式面→文本面）。 */
    private static String classifyToken(SemanticLine rl, SemanticLine bl, InlineTok rt,
            InlineTok bt) {
        boolean rMarker = rt.marks.contains(Mark.LIST_MARKER);
        boolean bMarker = bt.marks.contains(Mark.LIST_MARKER);
        if (rMarker || bMarker) {
            boolean numeric = rt.text.trim().matches("[0-9]+[.)]")
                    || bt.text.trim().matches("[0-9]+[.)]");
            return numeric || (rl.ordered && bl.ordered) ? D_ORDERED : D_MARKER;
        }
        if (rt.marks.contains(Mark.FORMULA) || bt.marks.contains(Mark.FORMULA)) {
            return D_FORMULA;
        }
        if (rt.marks.contains(Mark.RAW_HTML) || bt.marks.contains(Mark.RAW_HTML)) {
            return D_HTML;
        }
        if (rl.kind == Kind.THEMATIC_BREAK && bl.kind == Kind.THEMATIC_BREAK) {
            return D_TB_TEXT;
        }
        boolean rLink = rt.marks.contains(Mark.LINK);
        boolean bLink = bt.marks.contains(Mark.LINK);
        if (rLink != bLink || !CommonMarkReferenceSemantics.eq(rt.linkDest, bt.linkDest)) {
            if (!rLink && bLink && rt.text.startsWith("<") && rt.text.endsWith(">")) {
                return D_AUTOLINK; // R 把 <url> 折成 autolink，本仓接缝按字面
            }
            return rLink == bLink ? D_LINK_DEST : D_LINK_SCOPE;
        }
        if (rt.text.equals(bt.text)) {
            if (rt.marks.contains(Mark.STRIKE) != bt.marks.contains(Mark.STRIKE)) {
                return D_STRIKE;
            }
            if (rt.marks.contains(Mark.CODE) != bt.marks.contains(Mark.CODE)) {
                return D_CODE_SCOPE;
            }
            if (rt.marks.contains(Mark.STRONG) != bt.marks.contains(Mark.STRONG)
                    && !rt.marks.contains(Mark.STRONG)
                    && (rl.kind == Kind.HEADING || bl.kind == Kind.HEADING)) {
                return D_HEADING;
            }
            return D_FLANK;
        }
        if (rt.marks.contains(Mark.CODE) && bt.marks.contains(Mark.CODE)) {
            return D_CODETRIM;
        }
        if (rt.marks.isEmpty() && bt.marks.isEmpty() && escapeEquivalent(bt.text, rt.text)) {
            return D_ESCAPE;
        }
        // 行级信号兜底：一侧行内有公式/另一侧没有 → 公式域；一侧有强调标记而另一侧全无
        // → emphasis 定界简化域（错位切段本身多半就是这两种成因，比 TOKEN_TEXT 可裁定）。
        if (lineHasMark(rl, Mark.FORMULA) != lineHasMark(bl, Mark.FORMULA)) {
            return D_FORMULA;
        }
        if (hasEmphasis(rl) != hasEmphasis(bl)) {
            return D_FLANK;
        }
        return D_TOKEN_TEXT;
    }

    private static boolean startsWithSection(SemanticLine line) {
        return !line.tokens.isEmpty()
                && line.tokens.get(0).text.startsWith("\u00a7");
    }

    private static boolean lineHasMark(SemanticLine line, Mark mark) {
        for (InlineTok t : line.tokens) {
            if (t.marks.contains(mark)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasEmphasis(SemanticLine line) {
        for (InlineTok t : line.tokens) {
            if (t.marks.contains(Mark.EM) || t.marks.contains(Mark.STRONG)) {
                return true;
            }
        }
        return false;
    }

    /** 段数错位行的域（整行信号优先于兜底）。 */
    private static String alignDomain(SemanticLine rl, SemanticLine bl) {
        if (rl.kind == Kind.THEMATIC_BREAK && bl.kind == Kind.THEMATIC_BREAK) {
            return D_TB_TEXT;
        }
        if (lineHasMark(rl, Mark.FORMULA) != lineHasMark(bl, Mark.FORMULA)) {
            return D_FORMULA;
        }
        if (hasEmphasis(rl) != hasEmphasis(bl)) {
            return D_FLANK;
        }
        return D_TOKEN_ALIGN;
    }

    /** 「R 文本 == B 文本剥掉反斜杠转义」判据（本仓 escapable 集 ⊂ CommonMark 集时的差）。 */
    private static boolean escapeEquivalent(String bSide, String rSide) {
        return rSide.equals(bSide.replaceAll("\\\\(.)", "$1"));
    }

    private static String lineTok(SemanticLine line, int k) {
        return "行[" + line.renderKind() + "] 段#" + Integer.valueOf(k) + " "
                + line.tokens.get(k).render();
    }

    private static String render(SemanticLine line) {
        String t = line.renderTokens();
        return line.renderKind() + (t.isEmpty() ? " \u2205tokens" : " " + t);
    }

    private static String row(String id, int lineIdx, String domain, String rText, String bText) {
        return "| " + id + " 行#" + Integer.valueOf(lineIdx + 1) + " | " + domain
                + " | R: " + rText + " | B: " + bText + " |";
    }

    /** 已知差异样本驱动矩阵引擎自检：正对照（全等输入零差异）+ 各域真实命中（反恒真）。 */
    @Test
    public void matrixChannelMustClassifyKnownDivergences() {
        StringBuilder scratch = new StringBuilder();
        // 正对照：全等 → 0 差异（通道不误报）
        List<SemanticLine> same = new ArrayList<SemanticLine>();
        same.add(line(Kind.TEXT, 0, false, 0, tok(EnumSet.noneOf(Mark.class), "甲", null)));
        Assert.assertEquals("全等输入必须零差异", 0,
                compareEntry("T0", "自检正对照", "-", same, copy(same), 0, scratch));
        // 1) 标题身份差（R=HEADING vs B=TEXT+S 标记）→ HEADING_STYLE_ONLY 恰一条
        List<SemanticLine> rH = new ArrayList<SemanticLine>();
        rH.add(line(Kind.HEADING, 2, false, 0, tok(EnumSet.noneOf(Mark.class), "标题", null)));
        List<SemanticLine> bH = new ArrayList<SemanticLine>();
        bH.add(line(Kind.TEXT, 0, false, 0, tok(EnumSet.of(Mark.STRONG), "标题", null)));
        Assert.assertEquals("标题域应恰一条", 1,
                compareEntry("T1", "自检标题", "-", rH, bH, 0, scratch));
        Assert.assertTrue("须登记 HEADING_STYLE_ONLY", scratch.indexOf(D_HEADING) >= 0);
        // 2) 有序源序号差 → EXT_ORDERED_START（行级一条，marker token 同域不重复刷）
        List<SemanticLine> rO = new ArrayList<SemanticLine>();
        rO.add(line(Kind.LIST_ITEM, 1, true, 4,
                tok(EnumSet.of(Mark.LIST_MARKER), "4. ", null),
                tok(EnumSet.noneOf(Mark.class), "乙", null)));
        List<SemanticLine> bO = new ArrayList<SemanticLine>();
        bO.add(line(Kind.LIST_ITEM, 1, true, 1,
                tok(EnumSet.of(Mark.LIST_MARKER), "1. ", null),
                tok(EnumSet.noneOf(Mark.class), "乙", null)));
        Assert.assertEquals("序号差应恰一条(去重生效)", 1,
                compareEntry("T2", "自检有序", "-", rO, bO, 0, scratch));
        Assert.assertTrue("须登记 EXT_ORDERED_START", scratch.indexOf(D_ORDERED) >= 0);
        // 3) 公式 token → EXT_FORMULA
        List<SemanticLine> rF = new ArrayList<SemanticLine>();
        rF.add(line(Kind.TEXT, 0, false, 0, tok(EnumSet.noneOf(Mark.class), "$x$", null)));
        List<SemanticLine> bF = new ArrayList<SemanticLine>();
        bF.add(line(Kind.TEXT, 0, false, 0, tok(EnumSet.of(Mark.FORMULA), "x", null)));
        Assert.assertEquals("公式差恰一条", 1,
                compareEntry("T3", "自检公式", "-", rF, bF, 0, scratch));
        Assert.assertTrue("须登记 EXT_FORMULA", scratch.indexOf(D_FORMULA) >= 0);
        // 4) 块类别互斥 → KIND_MISMATCH；行数错位 → LINE_ALIGN
        List<SemanticLine> rM = new ArrayList<SemanticLine>();
        rM.add(line(Kind.TEXT, 0, false, 0, tok(EnumSet.noneOf(Mark.class), "一", null)));
        rM.add(line(Kind.TEXT, 0, false, 0, tok(EnumSet.noneOf(Mark.class), "二", null)));
        List<SemanticLine> bM = new ArrayList<SemanticLine>();
        bM.add(line(Kind.CODE_FENCED, 0, false, 0, tok(EnumSet.noneOf(Mark.class), "一", null)));
        Assert.assertEquals("类别互斥+行缺失共两条", 2,
                compareEntry("T4", "自检错位", "-", rM, bM, 0, scratch));
        Assert.assertTrue("须登记 KIND_MISMATCH", scratch.indexOf(D_KIND) >= 0);
        Assert.assertTrue("须登记 LINE_ALIGN", scratch.indexOf(D_LINE_ALIGN) >= 0);
        // 5) 分隔线可见文本差 → THEMATIC_BREAK_TEXT
        List<SemanticLine> rT = new ArrayList<SemanticLine>();
        rT.add(line(Kind.THEMATIC_BREAK, 0, false, 0));
        List<SemanticLine> bT = new ArrayList<SemanticLine>();
        bT.add(line(Kind.THEMATIC_BREAK, 0, false, 0,
                tok(EnumSet.noneOf(Mark.class), "------------------------------------", null)));
        Assert.assertEquals("分隔线文本差恰一条", 1,
                compareEntry("T5", "自检分隔线", "-", rT, bT, 0, scratch));
        Assert.assertTrue("须登记 THEMATIC_BREAK_TEXT", scratch.indexOf(D_TB_TEXT) >= 0);
        // 6) 反空转地板：五个差异样本各恰命中一条（T1..T5 计数断言即其钉），T0 正对照零差。
    }

    // —— 自检构造小工具 ——
    private static SemanticLine line(Kind kind, int level, boolean ordered, int ordinal,
            InlineTok... toks) {
        List<InlineTok> list = new ArrayList<InlineTok>();
        for (InlineTok t : toks) {
            list.add(t);
        }
        return new SemanticLine(kind, level, ordered, ordinal, 0, 0, null, list);
    }

    private static InlineTok tok(EnumSet<Mark> marks, String text, String dest) {
        return new InlineTok(marks, text, dest);
    }

    private static List<SemanticLine> copy(List<SemanticLine> in) {
        return new ArrayList<SemanticLine>(in);
    }

    // ==================== B 路出图（单侧） ====================

    /** §桥出图接缝（旧 B 路 § 样本口径保留：§ 段→span→行内解析）。 */
    private static List<TextSegment> bridgeSegments(String src, TextLayoutService service) {
        List<MarkdownSpan> spans = new ArrayList<MarkdownSpan>();
        for (TextSegment segment : service.parseSegments(src, WHITE)) {
            if (!segment.isLatex() && !segment.getText().isEmpty()) {
                spans.add(new MarkdownSpan(segment.getText(), segment.getStyle()));
            }
        }
        return MarkdownInlineParser.parse(spans);
    }

    private static void renderBPage(LatexSoftwareRenderKit.Shared shared,
            GlyphRuntimeTablesView view, TextLayoutService service, String id, String label,
            int w, List<List<TextSegment>> bLines) throws Exception {
        List<Integer> bHeights = new ArrayList<Integer>();
        for (List<TextSegment> line : bLines) {
            bHeights.add(Integer.valueOf(MarkdownPainter.lineHeightPx(line, service, BASE)));
        }
        int pageW = w + 2 * PAD;
        int pageH = Math.max(48, total(bHeights) + 16 + 2 * PAD);
        club.heiqi.uilib.font.render.GlyphBatchCollector bCollector =
                new club.heiqi.uilib.font.render.GlyphBatchCollector();
        renderPage(shared, view, service, bLines, bHeights, 14 + PAD, bCollector, PAD, BASE);
        label(bCollector, view, service, shared, "B@" + w + " " + label, LABEL_BASE);
        int[] pixels = FontSoftwareRasterizer.render(buildFrame(bCollector, pageW, pageH),
                shared.gl);
        File png = new File(OUT_DIR, suffix(id, w) + "-b.png");
        FontSoftwareRasterizer.writePng(pixels, pageW, pageH, png);
        int ink = countInk(pixels);
        Assert.assertTrue(id + "@" + w + " B 图墨水地板: 实测=" + Integer.valueOf(ink),
                ink >= MIN_INK_PER_PAGE);
        Assert.assertTrue(id + " B 侧 PNG 必须存在: " + png, png.isFile());
        pngCount++;
        PROFILE.append("png ").append(suffix(id, w)).append(" Blines=")
                .append(Integer.valueOf(bLines.size())).append(" Bh=")
                .append(Integer.valueOf(total(bHeights))).append(" ink=")
                .append(Integer.valueOf(ink)).append('\n');
    }

    /** @Nx 判读副本（同一次解析+换行只换倍率；零断言，仅验写出成功）。 */
    private static void renderBPageNx(LatexSoftwareRenderKit.Shared shared,
            GlyphRuntimeTablesView view, TextLayoutService service, String id, String label,
            int w, List<List<TextSegment>> bLines) throws Exception {
        if (!MarkdownRenderScaleKit.nxEnabled()) {
            return;
        }
        int n = MarkdownRenderScaleKit.N;
        List<List<TextSegment>> scaled = MarkdownRenderScaleKit.scaleLines(bLines, n);
        List<Integer> heights = new ArrayList<Integer>();
        for (List<TextSegment> line : scaled) {
            heights.add(Integer.valueOf(MarkdownPainter.lineHeightPx(line, service, BASE * n)));
        }
        String sfx = MarkdownRenderScaleKit.nxSuffix();
        int pageW = MarkdownRenderScaleKit.padW(w * n + 2 * PAD);
        int pageH = MarkdownRenderScaleKit.padH(Math.max(48,
                total(heights) + 16 * n + 2 * PAD));
        club.heiqi.uilib.font.render.GlyphBatchCollector collector =
                new club.heiqi.uilib.font.render.GlyphBatchCollector();
        renderPage(shared, view, service, scaled, heights, 14 * n + PAD, collector, PAD,
                BASE * n);
        label(collector, view, service, shared, "B@" + w + " " + label + " " + sfx,
                LABEL_BASE * n);
        File png = new File(OUT_DIR, suffix(id, w) + "-b" + sfx + ".png");
        FontSoftwareRasterizer.writePng(FontSoftwareRasterizer.render(
                buildFrame(collector, pageW, pageH), shared.gl), pageW, pageH, png);
        Assert.assertTrue("@Nx 判读副本必须写出成功: " + png, png.isFile() && png.length() > 100);
        PROFILE.append("pngNx ").append(suffix(id, w)).append(sfx)
                .append(" page=").append(Integer.valueOf(pageW)).append('x')
                .append(Integer.valueOf(pageH)).append(" [判读用,无断言]").append('\n');
    }

    private static int renderPage(LatexSoftwareRenderKit.Shared shared,
            GlyphRuntimeTablesView view, TextLayoutService service,
            List<List<TextSegment>> lines, List<Integer> heights, int top0,
            club.heiqi.uilib.font.render.GlyphBatchCollector collector, int x, int basePx) {
        DefaultFontRendererAdapter adapter = DefaultFontRendererAdapter.getInstance();
        int y = top0;
        for (int i = 0; i < lines.size(); i++) {
            List<TextSegment> line = lines.get(i);
            if (!line.isEmpty()) {
                adapter.renderSegmentsToCollector(line, shared.settings, service, view,
                        (float) x, (float) y, false, 1.0F, (float) basePx, collector);
            }
            y += heights.get(i).intValue();
        }
        return y;
    }

    private static void label(club.heiqi.uilib.font.render.GlyphBatchCollector collector,
            GlyphRuntimeTablesView view, TextLayoutService service,
            LatexSoftwareRenderKit.Shared shared, String text, int basePx) {
        DefaultFontRendererAdapter.getInstance().renderSegmentsToCollector(
                Collections.singletonList(labelSeg(text)), shared.settings, service, view,
                PAD, 2, false, 1.0F, (float) basePx, collector);
    }

    private static TextSegment labelSeg(String text) {
        TextStyle style = new TextStyle();
        style.setColor(0xFF8ADFFF);
        return new TextSegment(text, style);
    }

    private static String suffix(String id, int w) {
        return w == W_MAIN ? id : id + "_w" + w;
    }

    private static int total(List<Integer> xs) {
        int t = 0;
        for (Integer x : xs) {
            t += x.intValue();
        }
        return t;
    }

    private static SoftwareRenderFrame buildFrame(
            club.heiqi.uilib.font.render.GlyphBatchCollector collector, int width, int height) {
        SoftwareRenderFrame frame = new SoftwareRenderFrame(width, height, BACKGROUND);
        if (!collector.getMarkBackgroundBatch().isEmpty()) {
            frame.addBatch(collector.getMarkBackgroundBatch());
        }
        for (int index = 0; index < collector.getActivePageCount(); index++) {
            frame.addBatch(collector.getActiveBatch(index));
        }
        if (!collector.getDecorationBatch().isEmpty()) {
            frame.addBatch(collector.getDecorationBatch());
        }
        return frame;
    }

    private static int countInk(int[] pixels) {
        int bg = BACKGROUND | 0xFF000000;
        int count = 0;
        for (int pixel : pixels) {
            if (pixel != bg) {
                count++;
            }
        }
        return count;
    }

    // ==================== 报告 ====================

    private static void writeMatrixHeader() {
        MATRIX.append("# C3b1 重基线矩阵 —— R=commonmark-java 0.21.0(+GFM strikethrough) vs B=本仓 toLayoutLines(M10d 行接缝)\n")
                .append("# 本阶段不判 PASS/FAIL——全部差异逐条照登，供父代理与用户逐条裁定（2026-09-06 对齐裁定第一步）。\n")
                .append("# 口径: kind 家族归一(CODE_FENCED\u2261CODE_INDENTED\u2261B 统一 CODE) | F6 空行占位剔除后逐行序号直对 |\n")
                .append("# marker 归一 bullet=样式表符号「\u2022 」，有序=源数值文本 | token 文本逐字等、标记集等、LINK dest 等 |\n")
                .append("# 颜色/字号/下划线/几何不进语义面（样式豁免）；R 行注记 \u21b6SOFT/\u21b6HARD/\u21b6SETEXT 表示该行来源断行型。\n")
                .append("# 行格式: | 条目 行#N | 域 | R: … | B: … |；条目头: ## 条目 \u300clabel\u300d 豁免域=… 行数R/B=… F6剔行=…。\n");
    }

    // ==================== 共用装配 ====================

    private static TextStyle bodyStyle() {
        TextStyle style = new TextStyle();
        style.setColor(WHITE);
        return style;
    }
}
