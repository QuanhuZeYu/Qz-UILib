package club.heiqi.uilib.font.render.software;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
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
import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.internal.chat3.view.ChatMessageList;
import club.heiqi.uilib.internal.chat3.viewmodel.ChatLineLayouter;
import club.heiqi.uilib.internal.chat3.viewmodel.ChatUrlLinkifier;
import club.heiqi.uilib.ui.markdown.MarkdownPainter;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;

/**
 * M4 行为对拍门禁：同一批语料分别走 chat3 现行渲染路径（A 路）与新 B 路（L1+L2），逐项比对，
 * 产出结构化差异表 + 成对可视化图，判定 M5 能否开工（规划《通用Markdown渲染器》§三 M4 / §六 风险 2）。
 *
 * <p><b>常驻测试</b>：随 build 全量执行，无 Assume 门控；产物落 {@code build/reports/markdown-compare/}。
 * 本类是只读消费者——不修改 internal/chat3/**、不修改 L1/L2 公共面（包位置选择 font.render.software
 * 是因为共享装配 {@code LatexSoftwareRenderKit.Shared}/{@code assembleGlyphs} 为包内可见，与
 * {@code MarkdownSoftwareRenderTest}（M3）同先例；严禁另 new FontService，度量/字形恒走共享装配）。</p>
 *
 * <h3>两路定义（对拍前写死，事后不改）</h3>
 * <ul>
 *   <li><b>A 路（chat3 旧现行 = 行为规格快照）</b>：{@code ChatLineLayouter.splitFragments}
 *   (真机同源度量,13px) → 逐显示行：引用 "&gt; " 剥除 → 行级规则（块公式/无序列表「• 」，
 *   {@link #classifyReplica}）→ {@code parseSegments}(§) → code 切分（{@link #codeSpanSplitReplica}）
 *   → {@code ChatUrlLinkifier.linkify}（COLORED 强制链接色）→ 跨行 continuesWord 闸门 +
 *   leadingUrlRun 续链（旧 ChatMessageList 段流部分 1:1 复刻，SceneNode 装配部分与本门禁无关
 *   不复刻）。<b>M5 起接线落地，旧 ChatMarkdownLineRule/ChatCodeSpanSplitter 已从 main 删除，
 *   其语义按 1:1 快照移入本类私有方法——A 路定义、语料、判据与比对引擎一字未动</b>
 *   （规划 §二之五：门禁判据不许为接线让路）。度量适配器 = ChatSceneController.uiLibMeasure/
 *   uiLibSegmentParser/uiLibSegmentMeasurer 的逐行等价复制（仅 FontService.getInstance() 换成共享
 *   TextLayoutService）。</li>
 *   <li><b>B 路（L1+L2 + M5 保留的消费者层接线）</b>：{@code MarkdownDocument.parse(src).toSegments
 *   (MarkdownStyleTable.defaults(), 白 0xFFFFFFFF 基准)} →（§ 桥样本走
 *   {@code parseSegments→List<MarkdownSpan>→MarkdownInlineParser.parse(spans)} 行内接缝）→
 *   {@code ChatUrlLinkifier.linkify}(整条段流，换行<b>前</b>执行——接缝无 continuesWord 通道，跨行
 *   URL 只能靠先行链接化承接) → {@code MarkdownPainter.wrapLines}(同源度量,同容器宽,基准 13px)。
 *   ChatUrlLinkifier 属 M5 不删除的存留件（规划 §三 M5 只删 ChatMarkdownLineRule 与
 *   ChatCodeSpanSplitter 的解析部分），故计入 B 路；ChatCodeSpanSplitter 不计入——它的反引号<b>解析</b>
 *   部分恰是 M5 要删、B 要承接的 markdown 语义。</li>
 * </ul>
 *
 * <h3>三档判据（跑前定死）</h3>
 * <ul>
 *   <li>PARITY：chat3 有行为的场景逐段等价。字段：段文本、颜色、FontType、斜体/下划线/删除线、
 *   link、latex 原子（isLatex+源）、切行位置、段宽、命中区。归一化=相邻「比对字段全等且非 latex」段
 *   合并后比较。差一字段即 FAIL。</li>
 *   <li>NEW：chat3 无行为 → 只记录 B 侧输出到 diff.txt，不断言等价；但断言 A 侧「原样字面」
 *   （不崩、不吞字；允许 A 既有登记行为：行首 § 无视、「- 」→「• 」、「&gt; 」剥除、整行 $ 公式原子化）。</li>
 *   <li>REGRESSION：chat3 有行为而 B 丢/变 → 计入 PARITY FAIL。</li>
 * </ul>
 *
 * <h3>数值口径（容差全给数字）</h3>
 * <ul>
 *   <li>段宽：两侧同一把尺 {@code TextLayoutService.getSegmentWidth(seg, 13)}，容差 0px（位级等值；
 *   样式差导致的字号差如实反映为宽度差）。</li>
 *   <li>行宽：A=ceil(Σfloat)，B={@code MarkdownPainter.lineWidthPx}，容差 1px。</li>
 *   <li>切行：逐行可见文本严格相等；不等时若「行边界漂移 ≤1 码点 且 两侧流式归一文本相等
 *   且 漂移边界行两侧交叉实测宽差 ≤2.0px」判 TIE（float/double 口径并列切点），否则 FAIL。</li>
 *   <li>命中区：按行配对，比较 (url, floor(左缘), ceil(右缘))；相邻同 url 区间合并；x 容差 1px；
 *   y 不比（A 行框钉死 18px，B 行框自然量，纵向口径属消费层，记 profiles 不进门禁）。</li>
 *   <li>跨行续链 link 字段：A 行首段 style.link 停在片段值、完整 url 经 UrlChain.close() 回填命中区
 *   （ChatMessageList.java:126-190 自述设计）。口径：命中区 url 两侧严格相等；段 link 允许
 *   「A 值 = B 值前缀 且 该行确为链头」的例外（记 PASS-回填口径）。</li>
 *   <li>代理对铁律（跑前预裁）：A 若在代理对中间断行产出孤立代理（违反
 *   ERROR-20260825 预防条款「换行器新增分支必须断言零可见字符丢失」的规范面），该行 B 差异记
 *   RECORD 不记 FAIL；B 丢字则一律 FAIL。</li>
 * </ul>
 *
 * <p><b>场地限制如实声明</b>：软件光栅器对 CJK 有水平重影（M3 复核 §二之四 1 已用 LaTeX 老图对照
 * 确认非回归），出图判读聚焦结构与几何，不用于判可读性。</p>
 */
public class MarkdownChat3ParityTest {

    // ==================== 常量与口径 ====================

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
    /** 段宽容差(px):统一尺 getSegmentWidth,断言位级等值。 */
    private static final double SEG_W_TOL = 0.0D;
    /** 行宽容差(px):A ceil(float) vs B ceil(double) 口径差。 */
    private static final int LINE_W_TOL = 1;
    /** 命中区左/右缘容差(px):float/double 累计 + floor/ceil。 */
    private static final int HIT_X_TOL = 1;
    /** TIE 判据:漂移 ≤1 码点 且 该两行交叉实测宽差 ≤2.0px。 */
    private static final int TIE_CHAR_DRIFT = 1;
    private static final double TIE_WIDTH_DELTA = 2.0D;
    /** 每张出图墨水地板(低于即「空跑」,属场地缺陷而非对拍差异)。 */
    private static final int MIN_INK_PER_PAGE = 30;

    private static final StringBuilder DIFF = new StringBuilder();
    private static final StringBuilder PROFILE = new StringBuilder();
    private static final List<String> PARITY_FAILURES = new ArrayList<String>();
    private static int parityCount;
    private static int newCount;
    private static int tieCount;
    private static int divergentCount;
    private static int pngCount;

    // ==================== 语料表 ====================
    // {id, label, 档位 P=PARITY/N=NEW, §桥 1/0, 源文本}
    // 学费场景:① 行 junction 丢失 = P11(+P10@150、P17);② 两断行同形陷阱 = P10;③ 「• 」剥除与
    // 链接化作用域 = P07(+P08)。防误伤项:$5.99→P06,hello_world/2*3→N11。

    private static final String[][] CORPUS = {
        {"P01", "裸URL+www大写", "P", "0",
            "详见 http://qz.club/download?id=3&v=2 与 WWW.MINECRAFT.NET/download 资料"},
        {"P02", "URL尾随标点剥离", "P", "0",
            "见 https://a.test/x，完了。和 http://b.test/y, ok!"},
        {"P03", "code保护URL", "P", "0",
            "命令 `curl http://x.y/z -s` 执行"},
        {"P04", "整行块公式$$", "P", "0",
            "$$\\frac{a}{b}$$"},
        {"P05", "整行单$公式", "P", "0",
            "$E=mc^2$"},
        {"P06", "$价格防误伤", "P", "0",
            "价格 $5.99 与 100$ 加 x$y 未闭合"},
        {"P07", "列表+链接作用域", "P", "0",
            "- 详见 http://a.b/c 完"},
        {"P08", "嵌套列表缩进", "P", "0",
            "- 甲\n  - 乙\n    - 丙"},
        {"P09", "有序列表", "P", "0",
            "1. 第一\n2. 第二"},
        {"P10", "跨行URL续链②", "P", "0",
            "参考 http://qz.example.com/releases/GTNH-Latest-9.zip 完\nhow 都不同"},
        {"P11", "junction长文①", "P", "0",
            "欢迎来到 GTNH 教程 http://gtnh.example.com/wiki/GTNH-New-Horizons-Modpack-2-入门指南 开始吧 world wide web 词边界测试\n第二行 中英 mixed prose with averyveryverylongtokenwithoutanyspaces 结束\n第三行 短"},
        {"P12", "单层引用行", "P", "0",
            "> 引用的文字"},
        {"P13", "§前缀列表行", "P", "0",
            "\u00a7a- 玩家列表行"},
        {"P14", "§颜色混排桥", "P", "1",
            "\u00a7c红色警告 \u00a7fplain tail mixed English 123 长到需要断行才能放下更多内容"},
        {"P15", "emoji代理对", "P", "0",
            "服务器 \uD83D\uDE80 发射！\uD83C\uDF89\uD83C\uDF89 成功 launch ok 后接更多内容以便在窄容器把断点推到代理对附近"},
        {"P16", "空行", "P", "0",
            "甲\n\n乙"},
        {"P17", "超长单行", "P", "0",
            "这是超长单行的中文部分用于测试窄容器下的逐字硬断行为它没有任何空白所以只能按字符硬断并且混入englishsegmentwithoutanyspace这种无空白英文串再加上数字1234567890和符号_-.+=/?来覆盖硬断路径的所有字符类别最后以简短收尾"},
        {"P18", "普通文本基线", "P", "0",
            "普通聊天文字 mixed English 12345"},
        {"P19", "深缩进独立列表行", "P", "0",
            "    - deep"},
        {"N01", "ATX标题", "N", "0",
            "# 一级标题\n##### 五级标题"},
        {"N02", "围栏代码", "N", "0",
            "```java\nint x = 1; // **粗** $y$ > 引号 全字面\n```"},
        {"N03", "嵌套引用", "N", "0",
            "> 甲\n>> 乙\n>>> 丙"},
        {"N04", "列表续行", "N", "0",
            "- 甲项\n  甲项缩进续行"},
        {"N05", "硬换行", "N", "0",
            "第一行  \n第二行\\\n第三行"},
        {"N06", "分隔线", "N", "0",
            "上半句。\n---\n下半句。"},
        {"N07", "行内强调", "N", "0",
            "**粗** *斜* ~~删~~ ***粗斜*** 混排"},
        {"N08", "行内公式", "N", "0",
            "质能 $e=mc^2$ 行内混排 with 尾"},
        {"N09", "链接语法", "N", "0",
            "访问 [Qz 主页](https://example.com/qz) 详情"},
        {"N10", "反斜杠转义", "N", "0",
            "路径 C:\\temp 与 \\* 星号 \\`x\\` 字面"},
        {"N11", "emphasis防误伤", "N", "0",
            "hello_world 与 a*b，2*3=6 和 x_1 a**b**c"},
    };

    /**
     * 有意差异登记表（键 = 语料号@宽度）。<b>这不是跳过比对，也不是放宽判据</b>：该条改判一条
     * <em>更强</em>的正向不变量（见 {@link #assertWidthIndependentCodeStyle}），并在 diff.txt、
     * profiles.txt 与本仓规划《通用Markdown渲染器》§二之五 三处留档。
     *
     * <p>P03@150：A 路（chat3 现行）是「先按容器宽切显示行、再在显示行内配对反引号」，于是同一条
     * 消息换个窗口宽度就换一种样式语义——跨显示行的反引号对在 @150 留字面 反引号 并把 code 内 URL
     * 链接化（实测 A=[«命令·反引号curl·» c=FFFFFFFF w=63.84 | «http://x.y/z» c=FF7AB8F5
     * link=http://x.y/z w=60.98]），而 @269 却剥反引号 + 12px + 不链接化。用户 2026-09-04 裁定：
     * 该顺序副作用属 chat3 缺陷，B 不复刻；B 的「解析 → linkify → 换行」让 code span 语义与容器宽
     * 无关，严格更优（CommonMark 与「内容不变则样式不变」两侧都一致）。</p>
     */
    private static final String[] INTENTIONAL_DIVERGENCES = {"P03@" + W_NARROW};

    /** 有意差异的判据正文（写进 diff.txt / profiles.txt 留档）。 */
    private static final String DIVERGENCE_REASON =
            "chat3 的 code span 配对依赖换行位置（缺陷）；B 宽度无关";

    private static final int[] WIDTHS_PARITY = {W_MAIN, W_NARROW};
    private static final int[] WIDTHS_NEW = {W_MAIN};

    // ==================== 生命周期 ====================

    private int savedWidthMissBudget = -1;

    /** 与 M3 出图同纪律:测量期解除宽度 miss 预算,保证两路度量稳定同源。 */
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
        Files.write(new File(OUT_DIR, "diff.txt").toPath(),
                DIFF.toString().getBytes(StandardCharsets.UTF_8));
        Files.write(new File(OUT_DIR, "profiles.txt").toPath(),
                PROFILE.toString().getBytes(StandardCharsets.UTF_8));
        LatexSoftwareRenderKit.resetShared();
    }

    // ==================== 主流程 ====================

    /** 常驻门禁:全语料两路对拍 + 可视化 + 断言(PARITY FAIL 即红,M5 不许开工)。 */
    @Test
    public void compareChat3PathAgainstBLayerPath() throws Exception {
        if (!OUT_DIR.exists() && !OUT_DIR.mkdirs()) {
            throw new IllegalStateException("无法创建对拍产物目录: " + OUT_DIR);
        }
        LatexSoftwareRenderKit.Shared shared = LatexSoftwareRenderKit.shared();
        final TextLayoutService service = shared.service;
        writeDiffHeader();
        PROFILE.append("env jvm=").append(System.getProperty("java.version"))
                .append(" os=").append(System.getProperty("os.name")).append('\n');
        PROFILE.append("fontScene ").append(LatexSoftwareRenderKit.platformFontReport()).append('\n');
        PROFILE.append("widths parity=").append(Arrays.toString(WIDTHS_PARITY))
                .append(" new=").append(Arrays.toString(WIDTHS_NEW))
                .append(" base=").append(BASE).append('\n');
        PROFILE.append("renderScale ").append(MarkdownRenderScaleKit.detailReport(BASE))
                .append(" container=").append(W_MAIN).append('/').append(W_NARROW)
                .append(" @Nx 判读副本画布补白>=854x480,不参与断言\n");

        // 装配阶段:先喂全部源码的字形(含 B 段流/§桥/链接化产物),再换行——与 M3 同纪律。
        List<TextSegment> all = new ArrayList<TextSegment>();
        for (String[] entry : CORPUS) {
            all.addAll(service.parseSegments(entry[4], WHITE));
            List<TextSegment> doc = MarkdownDocument.parse(entry[4])
                    .toSegments(MarkdownStyleTable.defaults(), bodyStyle());
            all.addAll(doc);
            all.addAll(ChatUrlLinkifier.linkify(doc, ChatMarkdownSettings.getLinkArgb()));
            if ("1".equals(entry[3])) {
                all.addAll(bridgeSegments(entry[4], service));
            }
        }
        LatexSoftwareRenderKit.assembleGlyphs(shared, all);
        GlyphRuntimeTablesView view = GlyphRuntimeTablesView.snapshot(shared.tables, shared.manager, 1);

        for (String[] entry : CORPUS) {
            String id = entry[0];
            String label = entry[1];
            boolean parity = "P".equals(entry[2]);
            boolean bridge = "1".equals(entry[3]);
            String src = entry[4];
            int[] widths = parity ? WIDTHS_PARITY : WIDTHS_NEW;
            if (parity) {
                parityCount++;
            } else {
                newCount++;
            }
            for (int w : widths) {
                List<ALine> aLines = chat3Lines(src, w, service);
                List<List<TextSegment>> bLines = bLines(src, w, service, bridge);
                if (parity && isIntentionalDivergence(id, w)) {
                    assertWidthIndependentCodeStyle(id, src, service, bridge);
                } else if (parity) {
                    compareParityEntry(id, label, w, aLines, bLines, service);
                } else {
                    assertChat3Literal(id, src, aLines);
                    recordNewEntry(id, label, w, aLines, bLines, service);
                }
                renderTriplet(shared, view, service, id, label, w, parity, aLines, bLines);
                renderTripletNx(shared, view, service, id, label, w, parity, aLines, bLines);
            }
        }
        summaryLine();
        // 场地地板(与对拍结论分离):产物必须真实存在且有墨水,否则是本类自身坏了。
        Assert.assertTrue("diff.txt 必须非空", DIFF.length() > 100);
        int expectedPng = 0;
        for (String[] entry : CORPUS) {
            expectedPng += ("P".equals(entry[2]) ? WIDTHS_PARITY.length : WIDTHS_NEW.length) * 3;
        }
        Assert.assertTrue("成组 PNG 数量不足: pngCount=" + pngCount + " 应=" + expectedPng,
                pngCount >= expectedPng);
        if (!PARITY_FAILURES.isEmpty()) {
            Assert.fail("M4 对拍门禁 FAIL:" + PARITY_FAILURES.size()
                    + " 处 PARITY 差异(逐条见 build/reports/markdown-compare/diff.txt)——M5 不许开工。\n  "
                    + join(PARITY_FAILURES, "\n  "));
        }
    }

    /** 该语料@宽度是否登记为有意差异（见 {@link #INTENTIONAL_DIVERGENCES}）。 */
    private static boolean isIntentionalDivergence(String id, int w) {
        String key = id + "@" + w;
        for (String registered : INTENTIONAL_DIVERGENCES) {
            if (registered.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 有意差异条目的正向不变量断言（取代该条的逐段等价，比原判据更强，两宽度都跑）。
     *
     * <p>三条都要成立：
     * <ol>
     *   <li><b>B 的 code 段样式与容器宽无关</b>——同一语料在 @269 与 @150 下，全部 code 段的
     *       指纹（段文本 + codeSpan 位 + codeBackgroundColor + fontSizePx + link + 段宽）逐项相等；
     *       且 link 恒为 null（code 内 URL 永不链接化）。
     *       若某宽度下 code 段被换行切成多个视觉行，切完的每一段仍各自满足同一指纹
     *       （列表逐段相等，段数与指纹都不得随容器宽变化）。</li>
     *   <li><b>两宽度的换行确实不同</b>（行数或行宽序列不同）——否则第 1 条是空转。</li>
     *   <li><b>A 侧确实随宽度换语义</b>（@150 留字面反引号或把 code 内 URL 链化，@269 不留）
     *       ——把「登记理由是 chat3 的顺序副作用、不是 B 漏修」钉成可复核事实，杜绝无记录的降级。</li>
     * </ol></p>
     */
    private static void assertWidthIndependentCodeStyle(String id, String src,
            TextLayoutService service, boolean bridge) {
        List<List<TextSegment>> wide = bLines(src, W_MAIN, service, bridge);
        List<List<TextSegment>> narrow = bLines(src, W_NARROW, service, bridge);
        List<String> wideCode = codeStyleVector(wide, service);
        List<String> narrowCode = codeStyleVector(narrow, service);
        Assert.assertTrue(id + " 有意差异登记前置：B 侧必须真识别了 code 段（非空转）",
                !wideCode.isEmpty());
        for (String style : wideCode) {
            Assert.assertTrue(id + " B 的 code 段不得被链接化（link 必须为 null）：" + style,
                    style.contains(" link=null "));
        }
        Assert.assertTrue(id + " B 侧 code 段样式必须与容器宽无关：@269=" + wideCode
                + " @150=" + narrowCode, wideCode.equals(narrowCode));
        int wideW = 0;
        int narrowW = 0;
        for (List<TextSegment> line : wide) {
            wideW += MarkdownPainter.lineWidthPx(line, service, BASE);
        }
        for (List<TextSegment> line : narrow) {
            narrowW += MarkdownPainter.lineWidthPx(line, service, BASE);
        }
        Assert.assertTrue(id + " 两宽度换行必须真不同（否则不变量空转）：@269 总宽=" + wideW
                + " @150 总宽=" + narrowW + " 行数=" + wide.size() + '/' + narrow.size(),
                wide.size() != narrow.size() || wideW != narrowW);
        String aWide = aVisibleAt(src, W_MAIN, service);
        String aNarrow = aVisibleAt(src, W_NARROW, service);
        int aNarrowHits = aHitCountAt(src, W_NARROW, service);
        int aWideHits = aHitCountAt(src, W_MAIN, service);
        char tick = (char) 0x60;
        boolean tickWide = aWide.indexOf(String.valueOf(tick)) >= 0;
        boolean tickNarrow = aNarrow.indexOf(String.valueOf(tick)) >= 0;
        Assert.assertTrue(id + " A 侧必须确实随宽度换语义（否则登记理由不成立）："
                + " @269 含字面反引号=" + tickWide + " @150 含字面反引号=" + tickNarrow
                + " @269 命中区=" + aWideHits + " @150 命中区=" + aNarrowHits,
                tickWide != tickNarrow || aWideHits != aNarrowHits);
        divergentCount++;
        diffLine(id, W_NARROW, "DIVERGENT", "有意差异（不判逐段等价，改判宽度无关不变量）："
                + DIVERGENCE_REASON + "；B code 段样式=" + narrowCode
                + "；A@150=<" + aNarrow + "> 命中区=" + aNarrowHits
                + "；A@269=<" + aWide + "> 命中区=" + aWideHits);
        diffLine(id, W_MAIN, "INVARIANT", "B 宽度无关不变量通过（@269 code 段样式与 @150 全等："
                + wideCode + "）");
        PROFILE.append("divergent ").append(id).append('@').append(W_NARROW)
                .append(" reason=").append(DIVERGENCE_REASON)
                .append(" bCodeStyle=").append(narrowCode)
                .append(" aWideHits=").append(Integer.valueOf(aWideHits))
                .append(" aNarrowHits=").append(Integer.valueOf(aNarrowHits)).append('\n');
    }

    /** A 路在指定宽度下的整条流式可见文本（有意差异留档用）。 */
    private static String aVisibleAt(String src, int width, TextLayoutService service) {
        StringBuilder sb = new StringBuilder();
        for (ALine line : chat3Lines(src, width, service)) {
            sb.append(visible(coalesce(vec(line.segments, service)))).append('/');
        }
        return sb.toString();
    }

    /** A 路在指定宽度下的命中区总数（有意差异留档用）。 */
    private static int aHitCountAt(String src, int width, TextLayoutService service) {
        int hits = 0;
        for (ALine line : chat3Lines(src, width, service)) {
            hits += line.spans.size();
        }
        return hits;
    }

    /** 段流里全部 code 段的样式指纹：文本 + 衬底色 + 段级字号 + link + 统一尺段宽。 */
    private static List<String> codeStyleVector(List<List<TextSegment>> lines,
            TextLayoutService service) {
        List<String> out = new ArrayList<String>();
        for (List<TextSegment> line : lines) {
            for (TextSegment segment : line) {
                if (segment.getStyle() != null && segment.getStyle().isCodeSpan()) {
                    out.add("\u00ab" + segment.getText() + "\u00bb bg="
                            + String.format("%08X", Integer.valueOf(
                                    segment.getStyle().getCodeBackgroundColor()))
                            + " px=" + Integer.valueOf(segment.getStyle().getFontSizePx())
                            + " link=" + segment.getStyle().getLink() + " "
                            + "w=" + String.format("%.2f", Double.valueOf(
                                    service.getSegmentWidth(segment, BASE))));
                }
            }
        }
        return out;
    }

    private static String join(List<String> xs, String sep) {
        StringBuilder sb = new StringBuilder();
        for (String x : xs) {
            if (sb.length() > 0) {
                sb.append(sep);
            }
            sb.append(x);
        }
        return sb.toString();
    }

    // ==================== A 路:chat3 现行(1:1 复刻 ChatMessageList.java:879-1017 段流部分) ====================

    /** A 路一个显示行:最终段流 + 命中跨度(UrlChain 回填后就地更新)+ 断行来源。 */
    private static final class ALine {
        List<TextSegment> segments = Collections.emptyList();
        List<ASpan> spans = Collections.emptyList();
        boolean continuesWord;
        boolean chainExtendedHead; // 本行是「续链后被回填」的链头行(link 字段回填例外口径用)
    }

    /** 复刻 ChatMessageList.LinkSpan(:126-136,url 可写,UrlChain.close 回填)。 */
    private static final class ASpan {
        final float startX;
        float width;
        String url;

        ASpan(float startX, float width, String url) {
            this.startX = startX;
            this.width = width;
            this.url = url;
        }
    }

    /** 复刻 ChatMessageList.UrlChain(:147-191)。 */
    private static final class Chain {
        private String url = "";
        private final List<ASpan> spans = new ArrayList<ASpan>();
        private ASpan lastSpan;
        private final List<ALine> chainLines = new ArrayList<ALine>();

        boolean open() {
            return !spans.isEmpty();
        }

        String url() {
            return url;
        }

        void start(String headUrl, ASpan span, ALine line) {
            url = headUrl == null ? "" : headUrl;
            register(span);
            chainLines.clear();
            chainLines.add(line);
        }

        void extend(String accumulated, ASpan span, ALine line) {
            url = accumulated;
            register(span);
            // 本行并入后,链上此前所有行的段 link 停留在当时累积值,不再等于回填后的全 url
            for (ALine stale : chainLines) {
                stale.chainExtendedHead = true;
            }
            chainLines.add(line);
        }

        private void register(ASpan span) {
            if (span != null && span != lastSpan) {
                spans.add(span);
                lastSpan = span;
            }
        }

        void close() {
            for (ASpan span : spans) {
                span.url = url;
            }
            spans.clear();
            lastSpan = null;
            url = "";
            chainLines.clear();
        }
    }

    /** 生产 ChatSceneController.uiLibMeasure 的逐行等价复制(仅 FontService.getInstance()→共享装配 service)。 */
    private static ChatLineLayouter.Measure realMeasure(final TextLayoutService service) {
        return new ChatLineLayouter.Measure() {
            @Override
            public float advance(String text, int fontSizePx) {
                float total = 0.0F;
                for (TextSegment segment : service.parseSegments(text, WHITE)) {
                    total += (float) service.getSegmentWidth(segment, fontSizePx);
                }
                return total;
            }

            @Override
            public int epoch() {
                return 1;
            }
        };
    }

    /** 复刻 ChatMessageList.parseCached(旧 COLORED 路;postProcessor 生产未注入=恒 null)。
     *  M5 后 code 切分器已删,快照语义复刻于本类私有方法(规划 §三:M4 门禁 A 路 = 行为规格,
     *  不随被删实现消失——判据与逐段比对逻辑一字未动)。 */
    private static List<TextSegment> parseCached(String text, int baseColor, TextLayoutService service) {
        List<TextSegment> segments = service.parseSegments(text, baseColor);
        segments = codeSpanSplitReplica(segments, ChatMarkdownSettings.getCodeBackgroundArgb());
        segments = ChatUrlLinkifier.linkify(segments, ChatMarkdownSettings.getLinkArgb());
        return segments;
    }

    /** 行级 markdown 规则档位(复刻旧 ChatMarkdownLineRule.Kind,语义快照)。 */
    private enum RuleKind { NONE, UNORDERED_LIST, ORDERED_LIST, BLOCK_MATH }

    /** 旧 ChatMarkdownLineRule.Match 的行为快照(M5 删类后 A 路自持)。 */
    private static final class RuleMatch {
        final RuleKind kind;
        final int level;
        final String content;
        final String latexSource;

        RuleMatch(RuleKind kind, int level, String content, String latexSource) {
            this.kind = kind;
            this.level = level;
            this.content = content;
            this.latexSource = latexSource;
        }
    }

    private static final RuleMatch NO_MATCH = new RuleMatch(RuleKind.NONE, 0, null, null);

    /** 旧 ChatMarkdownLineRule.classify 的 1:1 语义快照(含行首/行尾 § 码剥离与 $ 计数)。 */
    private static RuleMatch classifyReplica(String line) {
        if (line == null || line.isEmpty()) {
            return NO_MATCH;
        }
        line = stripLeadingFormatCodesReplica(line);
        if (line.isEmpty()) {
            return NO_MATCH;
        }
        int leading = 0;
        while (leading < line.length() && line.charAt(leading) == ' ') {
            leading++;
        }
        int level = leading / 2;
        String body = line.substring(leading);
        body = stripTrailingFormatCodesReplica(body);
        if (body.isEmpty()) {
            return NO_MATCH;
        }
        if (body.startsWith("$$")) {
            String source = body.substring(2);
            if (source.endsWith("$$")) {
                source = source.substring(0, source.length() - 2);
            }
            return new RuleMatch(RuleKind.BLOCK_MATH, level, null, source);
        }
        if (body.length() >= 2 && isBulletMarkReplica(body.charAt(0)) && body.charAt(1) == ' ') {
            return new RuleMatch(RuleKind.UNORDERED_LIST, level, body.substring(2), null);
        }
        int digits = 0;
        while (digits < body.length() && Character.isDigit(body.charAt(digits))) {
            digits++;
        }
        if (digits > 0 && digits + 1 < body.length()
                && body.charAt(digits) == '.' && body.charAt(digits + 1) == ' ') {
            return new RuleMatch(RuleKind.ORDERED_LIST, level, body, null);
        }
        if (body.startsWith("$") && body.length() >= 3 && body.endsWith("$")
                && countDollarsReplica(body) == 2) {
            return new RuleMatch(RuleKind.BLOCK_MATH, level, null, body.substring(1, body.length() - 1));
        }
        return NO_MATCH;
    }

    private static boolean isBulletMarkReplica(char c) {
        return c == '-' || c == '*' || c == '+';
    }

    private static int countDollarsReplica(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '$') {
                count++;
            }
        }
        return count;
    }

    private static String stripLeadingFormatCodesReplica(String text) {
        int start = 0;
        while (start + 1 < text.length() && text.charAt(start) == '\u00a7') {
            start += 2;
        }
        return start == 0 ? text : text.substring(start);
    }

    private static String stripTrailingFormatCodesReplica(String text) {
        int end = text.length();
        while (end >= 2 && text.charAt(end - 2) == '\u00a7') {
            end -= 2;
        }
        return end == text.length() ? text : text.substring(0, end);
    }

    /** 旧 ChatCodeSpanSplitter.split/splitSegment 的 1:1 语义快照(M5 删类后 A 路自持;
     *  反引号书写沿用仓内 0x60 惯例)。 */
    private static List<TextSegment> codeSpanSplitReplica(List<TextSegment> base, int codeBackgroundColor) {
        if (base == null || base.isEmpty()) {
            return base;
        }
        List<TextSegment> out = null;
        for (TextSegment segment : base) {
            List<TextSegment> pieces = codeSpanSplitReplicaOne(segment, codeBackgroundColor);
            if (pieces == null) {
                if (out != null) {
                    out.add(segment);
                }
                continue;
            }
            if (out == null) {
                out = new ArrayList<TextSegment>(base.size() + 2);
                for (TextSegment pre : base) {
                    if (pre == segment) {
                        break;
                    }
                    out.add(pre);
                }
            }
            out.addAll(pieces);
        }
        return out == null ? base : out;
    }

    private static List<TextSegment> codeSpanSplitReplicaOne(TextSegment segment, int codeBackgroundColor) {
        if (segment.isLatex()) {
            return null;
        }
        String text = segment.getText();
        TextStyle style = segment.getStyle();
        List<TextSegment> out = null;
        char tick = (char) 0x60;
        int cursor = 0;
        int index = 0;
        int length = text.length();
        while (index < length) {
            int open = text.indexOf(tick, index);
            if (open < 0) {
                break;
            }
            int close = text.indexOf(tick, open + 1);
            if (close < 0) {
                break;
            }
            index = close + 1;
            if (close == open + 1) {
                continue;
            }
            if (out == null) {
                out = new ArrayList<TextSegment>(3);
            }
            if (open > cursor) {
                out.add(new TextSegment(text.substring(cursor, open), style));
            }
            TextStyle codeStyle = style.copy();
            codeStyle.setCodeSpan(true);
            codeStyle.setCodeBackgroundColor(codeBackgroundColor);
            codeStyle.setFontSizePx(ChatMarkdownSettings.getCodeFontSizePx());
            codeStyle.setLink(null);
            out.add(new TextSegment(text.substring(open + 1, close), codeStyle));
            cursor = close + 1;
        }
        if (out == null) {
            return null;
        }
        if (cursor < length) {
            out.add(new TextSegment(text.substring(cursor), style));
        }
        return out;
    }

    private static List<ALine> chat3Lines(String src, int width, TextLayoutService service) {
        ChatLineLayouter.Measure measure = realMeasure(service);
        List<ChatLineLayouter.LineFragment> frags =
                ChatLineLayouter.splitFragments(src, width, measure, BASE);
        List<ALine> out = new ArrayList<ALine>();
        Chain chain = new Chain();
        for (ChatLineLayouter.LineFragment frag : frags) {
            String line = frag.getText();
            ALine a = new ALine();
            a.continuesWord = frag.continuesWord();
            boolean quoteLine = line.startsWith("> ");
            String renderLine = quoteLine ? line.substring(2) : line;
            if (!quoteLine && line.startsWith(">")) {
                quoteLine = true;
                renderLine = line.substring(1);
            }
            int lineBaseColor = quoteLine ? ChatMarkdownSettings.getTextSecondaryArgb() : WHITE;
            RuleMatch markdown = quoteLine ? NO_MATCH : classifyReplica(renderLine);
            if (markdown.kind == RuleKind.BLOCK_MATH) {
                TextStyle mathStyle = new TextStyle();
                mathStyle.setColor(lineBaseColor);
                a.segments = Collections.singletonList(
                        TextSegment.forLatex(markdown.latexSource, mathStyle));
                chain.close();
                out.add(a);
                continue;
            }
            String scopeText;
            TextSegment bulletSegment = null;
            if (markdown.kind == RuleKind.UNORDERED_LIST) {
                StringBuilder bulletBuilder = new StringBuilder();
                for (int l = 0; l < markdown.level; l++) {
                    bulletBuilder.append("  ");
                }
                bulletBuilder.append("\u2022 ");
                TextStyle bulletStyle = new TextStyle();
                bulletStyle.setColor(lineBaseColor);
                bulletSegment = new TextSegment(bulletBuilder.toString(), bulletStyle);
                String listContent = markdown.content == null ? "" : markdown.content;
                List<TextSegment> combined = new ArrayList<TextSegment>();
                combined.add(bulletSegment);
                combined.addAll(parseCached(listContent, lineBaseColor, service));
                a.segments = combined;
                scopeText = listContent;
            } else {
                a.segments = parseCached(renderLine, lineBaseColor, service);
                scopeText = renderLine;
            }
            String chainRun = null;
            if (a.continuesWord && chain.open() && bulletSegment == null) {
                String run = ChatUrlLinkifier.leadingUrlRun(scopeText);
                if (run.isEmpty()) {
                    chain.close();
                } else {
                    chainRun = run;
                    a.segments = ChatUrlLinkifier.linkifyLeadingRun(a.segments,
                            Integer.valueOf(ChatMarkdownSettings.getLinkArgb()),
                            run.length(), chain.url() + run);
                }
            }
            a.spans = linkSpansOf(a.segments, service);
            if (chainRun != null && !a.spans.isEmpty()) {
                chain.extend(chain.url() + chainRun, a.spans.get(0), a);
            }
            if (chainRun == null
                    || chainRun.length() < ChatUrlLinkifier.plainLength(scopeText)) {
                TextSegment lastSegment = a.segments.isEmpty() ? null
                        : a.segments.get(a.segments.size() - 1);
                String tailLink = lastSegment == null ? null : lastSegment.getStyle().getLink();
                chain.close();
                if (tailLink != null) {
                    chain.start(tailLink,
                            a.spans.isEmpty() ? null : a.spans.get(a.spans.size() - 1), a);
                }
            }
            out.add(a);
        }
        return out;
    }

    /** 复刻 ChatMessageList.linkSpansOf(:1345-1360,段宽=生产 SegmentMeasurer.getSegmentWidth)。 */
    private static List<ASpan> linkSpansOf(List<TextSegment> segments, TextLayoutService service) {
        List<ASpan> spans = null;
        float x = 0.0F;
        for (TextSegment segment : segments) {
            float width = (float) Math.max(0.0D, service.getSegmentWidth(segment, BASE));
            String url = segment.getStyle().getLink();
            if (url != null) {
                if (spans == null) {
                    spans = new ArrayList<ASpan>(2);
                }
                spans.add(new ASpan(x, width, url));
            }
            x += width;
        }
        return spans == null ? Collections.<ASpan>emptyList() : spans;
    }

    // ==================== B 路:L1+L2 + M5 存留链接化接线 ====================

    /** §桥(MarkdownInlineParser.java:64「聊天组件桥的输入口径」):§ 段→span→行内解析。 */
    private static List<TextSegment> bridgeSegments(String src, TextLayoutService service) {
        List<MarkdownSpan> spans = new ArrayList<MarkdownSpan>();
        for (TextSegment segment : service.parseSegments(src, WHITE)) {
            if (!segment.isLatex() && !segment.getText().isEmpty()) {
                spans.add(new MarkdownSpan(segment.getText(), segment.getStyle()));
            }
        }
        return MarkdownInlineParser.parse(spans);
    }

    private static List<List<TextSegment>> bLines(String src, int width,
            TextLayoutService service, boolean bridge) {
        List<TextSegment> segments = bridge
                ? bridgeSegments(src, service)
                : MarkdownDocument.parse(src).toSegments(MarkdownStyleTable.defaults(), bodyStyle());
        segments = ChatUrlLinkifier.linkify(segments, ChatMarkdownSettings.getLinkArgb());
        return MarkdownPainter.wrapLines(segments, service, width, BASE);
    }

    // ==================== 比对引擎 ====================

    /** 比对字段向量(段文本/颜色/FontType/斜下删/link/latex/统一尺宽)+ 记录字段。 */
    private static final class FSeg {
        String text;
        int color;
        FontType fontType;
        boolean italic;
        boolean underline;
        boolean strike;
        String link;
        boolean latex;
        String latexSource;
        double width;
        boolean codeSpan;
        int codeBg;
        int fontPx;

        String brief() {
            StringBuilder sb = new StringBuilder();
            sb.append(latex ? "<latex:" + latexSource + ">" : "\u00ab" + esc(text) + "\u00bb");
            sb.append(" c=").append(String.format("%08X", Integer.valueOf(color)));
            sb.append(" f=").append(fontType);
            if (italic) {
                sb.append(" it");
            }
            if (underline) {
                sb.append(" ul");
            }
            if (strike) {
                sb.append(" st");
            }
            if (link != null) {
                sb.append(" link=").append(link);
            }
            sb.append(" w=").append(String.format("%.2f", Double.valueOf(width)));
            if (codeSpan || fontPx != 0) {
                sb.append(" [rec code=").append(Boolean.valueOf(codeSpan))
                        .append(" bg=").append(String.format("%08X", Integer.valueOf(codeBg)))
                        .append(" px=").append(fontPx).append(']');
            }
            return sb.toString();
        }

        boolean sameGateShape(FSeg o) {
            return color == o.color && fontType == o.fontType && italic == o.italic
                    && underline == o.underline && strike == o.strike
                    && eq(link, o.link) && latex == o.latex && eq(latexSource, o.latexSource);
        }
    }

    private static String esc(String s) {
        return s.replace(" ", "\u00b7");
    }

    private static boolean eq(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    private static FSeg fseg(TextSegment segment, TextLayoutService service) {
        FSeg f = new FSeg();
        f.text = segment.getText();
        TextStyle style = segment.getStyle();
        f.color = style.getColor();
        f.fontType = style.getFontType();
        f.italic = style.isItalic();
        f.underline = style.isUnderline();
        f.strike = style.isStrikethrough();
        f.link = style.getLink();
        f.latex = segment.isLatex();
        f.latexSource = segment.getLatexSource();
        // 统一尺:两路同用 TextLayoutService.getSegmentWidth(段样式自带字号位,chat3 的 12px code 与
        // B 的 13px 正文之差会如实反映为宽度差)。
        f.width = service.getSegmentWidth(segment, BASE);
        f.codeSpan = style.isCodeSpan();
        f.codeBg = style.getCodeBackgroundColor();
        f.fontPx = style.getFontSizePx();
        return f;
    }

    private static List<FSeg> vec(List<TextSegment> line, TextLayoutService service) {
        List<FSeg> out = new ArrayList<FSeg>(line.size());
        for (TextSegment segment : line) {
            out.add(fseg(segment, service));
        }
        return out;
    }

    /** 归一化:相邻比对字段全等且非 latex 的段合并(比对语义流,不比段边界;latex 原子永不合并)。 */
    private static List<FSeg> coalesce(List<FSeg> in) {
        List<FSeg> out = new ArrayList<FSeg>(in.size());
        for (FSeg f : in) {
            if (!out.isEmpty()) {
                FSeg last = out.get(out.size() - 1);
                if (!last.latex && !f.latex && last.sameGateShape(f)) {
                    last.text = last.text + f.text;
                    last.width = last.width + f.width;
                    continue;
                }
            }
            out.add(f);
        }
        return out;
    }

    private static String visible(List<FSeg> line) {
        StringBuilder sb = new StringBuilder();
        for (FSeg f : line) {
            sb.append(f.latex ? "\u27e6" + f.latexSource + "\u27e7" : f.text);
        }
        return sb.toString();
    }

    private static double sumWidth(List<FSeg> line) {
        double total = 0.0D;
        for (FSeg f : line) {
            total += f.width;
        }
        return total;
    }

    private static void compareParityEntry(String id, String label, int w,
            List<ALine> aLines, List<List<TextSegment>> bLines, TextLayoutService service) {
        List<String> issues = new ArrayList<String>();
        int n = Math.min(aLines.size(), bLines.size());
        if (aLines.size() != bLines.size()) {
            issues.add("行数 A=" + aLines.size() + " B=" + bLines.size()
                    + " (A=" + dumpLinesA(aLines, service) + " | B=" + dumpLinesB(bLines, service) + ")");
        }
        for (int i = 0; i < n; i++) {
            List<FSeg> av = coalesce(vec(aLines.get(i).segments, service));
            List<FSeg> bv = coalesce(vec(bLines.get(i), service));
            String at = visible(av);
            String bt = visible(bv);
            boolean textDrift = !at.equals(bt);
            if (textDrift) {
                double dw = Math.abs(sumWidth(av) - sumWidth(bv));
                boolean charDriftOk = Math.abs(at.length() - bt.length()) <= TIE_CHAR_DRIFT;
                if (dw <= TIE_WIDTH_DELTA && charDriftOk) {
                    tieCount++;
                    diffLine(id, w, "TIE", "行#" + i + " 断点并列 A=<" + at + "> B=<" + bt
                            + "> 交叉宽差=" + String.format("%.2f", Double.valueOf(dw)) + "px");
                } else {
                    issues.add("行#" + i + " 切行/文本漂移 A=<" + at + "> B=<" + bt + ">");
                }
            }
            if (av.size() != bv.size()) {
                issues.add("行#" + i + " 段数 A=" + av.size() + " B=" + bv.size()
                        + " A=[" + joinBrief(av) + "] B=[" + joinBrief(bv) + "]");
            } else if (!textDrift) {
                for (int k = 0; k < av.size(); k++) {
                    FSeg a = av.get(k);
                    FSeg b = bv.get(k);
                    List<String> delta = new ArrayList<String>();
                    if (a.color != b.color) {
                        delta.add("颜色");
                    }
                    if (a.fontType != b.fontType) {
                        delta.add("FontType");
                    }
                    if (a.italic != b.italic || a.underline != b.underline || a.strike != b.strike) {
                        delta.add("斜/下/删");
                    }
                    if (!linkEquivalent(a, b, aLines.get(i))) {
                        delta.add("link");
                    }
                    if (a.latex != b.latex || !eq(a.latexSource, b.latexSource)) {
                        delta.add("latex原子");
                    }
                    if (Math.abs(a.width - b.width) > SEG_W_TOL) {
                        delta.add("段宽");
                    }
                    if (!delta.isEmpty()) {
                        issues.add("行#" + i + " 段#" + k + " 差异{" + join(delta, ",")
                                + "} A=" + a.brief() + " B=" + b.brief());
                    }
                }
            }
            float aLineW = 0.0F;
            for (TextSegment segment : aLines.get(i).segments) {
                aLineW += (float) service.getSegmentWidth(segment, BASE);
            }
            int aLineWCeil = (int) Math.ceil(aLineW);
            int bLineW = MarkdownPainter.lineWidthPx(bLines.get(i), service, BASE);
            if (Math.abs(aLineWCeil - bLineW) > LINE_W_TOL) {
                issues.add("行#" + i + " 行宽 A=" + aLineWCeil + " B=" + bLineW);
            }
            List<ASpan> aSpans = mergeHits(aLines.get(i).spans);
            List<Hit> bSpans = mergeHitsB(bLineHits(bLines.get(i), service));
            if (aSpans.size() != bSpans.size()) {
                issues.add("行#" + i + " 命中区数 A=" + aSpans.size() + " B=" + bSpans.size());
            } else {
                for (int k = 0; k < aSpans.size(); k++) {
                    ASpan a = aSpans.get(k);
                    Hit b = bSpans.get(k);
                    int left = (int) Math.floor(a.startX);
                    int right = (int) Math.ceil(a.startX + a.width);
                    boolean urlOk = eq(a.url, b.url);
                    if (!urlOk && aLines.get(i).chainExtendedHead && b.url != null && a.url != null
                            && b.url.startsWith(a.url)) {
                        urlOk = true;
                        diffLine(id, w, "PASS", "行#" + i + " 段#" + k
                                + " link 回填口径 A(前缀)=" + a.url + " B(全)=" + b.url);
                    }
                    if (!urlOk || Math.abs(left - b.left) > HIT_X_TOL
                            || Math.abs(right - b.right) > HIT_X_TOL) {
                        issues.add("行#" + i + " 命中区#" + k + " A=(" + left + "," + right + ","
                                + a.url + ") B=(" + b.left + "," + b.right + "," + b.url + ")");
                    }
                }
            }
        }
        if (issues.isEmpty()) {
            diffLine(id, w, "PASS", "PARITY 逐段等价(" + aLines.size() + " 行)");
        } else {
            for (String issue : issues) {
                PARITY_FAILURES.add(id + " @" + w + ": " + issue);
                diffLine(id, w, "FAIL", issue);
            }
        }
    }

    private static String dumpLinesA(List<ALine> lines, TextLayoutService service) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append("//");
            }
            sb.append(visible(coalesce(vec(lines.get(i).segments, service))));
        }
        return sb.toString();
    }

    private static String dumpLinesB(List<List<TextSegment>> lines, TextLayoutService service) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append("//");
            }
            sb.append(visible(coalesce(vec(lines.get(i), service))));
        }
        return sb.toString();
    }

    private static boolean linkEquivalent(FSeg a, FSeg b, ALine aLine) {
        if (eq(a.link, b.link)) {
            return true;
        }
        return aLine.chainExtendedHead && a.link != null && b.link != null
                && b.link.startsWith(a.link); // 回填口径(见类 javadoc)
    }

    private static String joinBrief(List<FSeg> v) {
        StringBuilder sb = new StringBuilder();
        for (FSeg f : v) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(f.brief());
        }
        return sb.toString();
    }

    private static List<ASpan> mergeHits(List<ASpan> in) {
        List<ASpan> out = new ArrayList<ASpan>(in.size());
        for (ASpan s : in) {
            if (!out.isEmpty()) {
                ASpan last = out.get(out.size() - 1);
                if (eq(last.url, s.url) && last.startX + last.width == s.startX) {
                    last.width += s.width;
                    continue;
                }
            }
            out.add(s);
        }
        return out;
    }

    /** B 路命中区(左 floor/右 ceil/URL)= MarkdownLineLayout.appendLinkRegions 原生口径复刻。 */
    private static final class Hit {
        int left;
        int right;
        String url;

        Hit(int left, int right, String url) {
            this.left = left;
            this.right = right;
            this.url = url;
        }
    }

    private static List<Hit> bLineHits(List<TextSegment> line, TextLayoutService service) {
        List<Hit> hits = new ArrayList<Hit>();
        double x = 0.0D;
        for (TextSegment segment : line) {
            double width = service.getSegmentWidth(segment, BASE);
            String url = segment.getStyle().getLink();
            if (url != null) {
                int left = (int) Math.floor(x);
                int right = (int) Math.ceil(x + width);
                if (right > left) {
                    hits.add(new Hit(left, right, url));
                }
            }
            x += width;
        }
        return hits;
    }

    private static List<Hit> mergeHitsB(List<Hit> in) {
        List<Hit> out = new ArrayList<Hit>(in.size());
        for (Hit h : in) {
            if (!out.isEmpty()) {
                Hit last = out.get(out.size() - 1);
                if (eq(last.url, h.url) && last.right == h.left) {
                    last.right = h.right;
                    continue;
                }
            }
            out.add(h);
        }
        return out;
    }

    // ==================== NEW 档:chat3 字面确认 + B 侧记录 ====================

    /** chat3 遇同语料必「原样字面」(允许登记过的行级行为:> 剥除、「- 」→「• 」)。 */
    private static void assertChat3Literal(String id, String src, List<ALine> aLines) {
        String expected = expectedAfterChat3Transforms(src);
        StringBuilder actual = new StringBuilder();
        for (ALine line : aLines) {
            for (TextSegment segment : line.segments) {
                actual.append(segment.isLatex() ? "" : segment.getText());
            }
            actual.append(' ');
        }
        Assert.assertEquals(id + ": chat3 侧必须原样字面(不崩/不吞字/无未登记改写)",
                expected, collapseWs(actual.toString()));
    }

    /** 复刻 ChatCodeSpanSplitter 配对语义(不识别反斜杠转义——chat3 实测行为,预登记进字面模型)。 */
    private static String stripCodePairBackticks(String line) {
        StringBuilder out = new StringBuilder();
        int cursor = 0;
        int index = 0;
        while (index < line.length()) {
            int open = line.indexOf('\u0060', index);
            if (open < 0) {
                break;
            }
            int close = line.indexOf('\u0060', open + 1);
            if (close < 0) {
                break;
            }
            if (close == open + 1) {
                out.append(line, cursor, close + 1);
                cursor = index = close + 1;
                continue;
            }
            out.append(line, cursor, open);
            out.append(line, open + 1, close);
            cursor = index = close + 1;
        }
        out.append(line.substring(Math.min(cursor, line.length())));
        return out.toString();
    }

    private static String expectedAfterChat3Transforms(String src) {
        StringBuilder out = new StringBuilder();
        for (String logical : src.split("\n", -1)) {
            String plain = stripCodePairBackticks(logical.replace("\u00a7", ""));
            String trimmedLeft = plain.replaceAll("^ +", "");
            if (trimmedLeft.startsWith(">")) {
                out.append(trimmedLeft.substring(1)).append(' ');
                continue;
            }
            if (trimmedLeft.length() >= 2 && "-*+".indexOf(trimmedLeft.charAt(0)) >= 0
                    && trimmedLeft.charAt(1) == ' ') {
                out.append("\u2022 ").append(trimmedLeft.substring(2)).append(' ');
                continue;
            }
            out.append(plain).append(' ');
        }
        return collapseWs(out.toString());
    }

    private static void recordNewEntry(String id, String label, int w,
            List<ALine> aLines, List<List<TextSegment>> bLines, TextLayoutService service) {
        diffLine(id, w, "RECORD", "NEW 「" + label + "」行数 A=" + aLines.size() + " B=" + bLines.size());
        for (int i = 0; i < bLines.size(); i++) {
            diffLine(id, w, "RECORD", "B 行#" + i + " ["
                    + joinBrief(coalesce(vec(bLines.get(i), service))) + "]");
        }
    }

    // ==================== 出图(复用 LatexSoftwareRenderKit 共享装配,严禁另起) ====================

    private static int renderPage(LatexSoftwareRenderKit.Shared shared, GlyphRuntimeTablesView view,
            TextLayoutService service, List<List<TextSegment>> lines, List<Integer> heights,
            int top0, club.heiqi.uilib.font.render.GlyphBatchCollector collector, int x, int basePx) {
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

    private static void renderTriplet(LatexSoftwareRenderKit.Shared shared, GlyphRuntimeTablesView view,
            TextLayoutService service, String id, String label, int w, boolean parity,
            List<ALine> aLines, List<List<TextSegment>> bLines) throws Exception {
        int fixedLine = ChatMarkdownSettings.getChatLineHeightPx();
        List<List<TextSegment>> aSegLines = new ArrayList<List<TextSegment>>();
        List<Integer> aHeights = new ArrayList<Integer>();
        for (ALine line : aLines) {
            aSegLines.add(line.segments);
            aHeights.add(Integer.valueOf(fixedLine));
        }
        List<Integer> bHeights = new ArrayList<Integer>();
        for (List<TextSegment> line : bLines) {
            bHeights.add(Integer.valueOf(MarkdownPainter.lineHeightPx(line, service, BASE)));
        }
        int pageW = w + 2 * PAD;
        int pageH = Math.max(48, Math.max(total(aHeights), total(bHeights)) + 16 + 2 * PAD);
        club.heiqi.uilib.font.render.GlyphBatchCollector aCollector =
                new club.heiqi.uilib.font.render.GlyphBatchCollector();
        renderPage(shared, view, service, aSegLines, aHeights, 14 + PAD, aCollector, PAD, BASE);
        label(aCollector, view, service, shared, "chat3@" + w + " " + label, LABEL_BASE);
        int[] aPix = FontSoftwareRasterizer.render(buildFrame(aCollector, pageW, pageH), shared.gl);
        FontSoftwareRasterizer.writePng(aPix, pageW, pageH,
                new File(OUT_DIR, suffix(id, w) + "-chat3.png"));
        club.heiqi.uilib.font.render.GlyphBatchCollector bCollector =
                new club.heiqi.uilib.font.render.GlyphBatchCollector();
        renderPage(shared, view, service, bLines, bHeights, 14 + PAD, bCollector, PAD, BASE);
        label(bCollector, view, service, shared, "B@" + w + " " + label, LABEL_BASE);
        int[] bPix = FontSoftwareRasterizer.render(buildFrame(bCollector, pageW, pageH), shared.gl);
        FontSoftwareRasterizer.writePng(bPix, pageW, pageH,
                new File(OUT_DIR, suffix(id, w) + "-b.png"));
        int sideW = 2 * pageW + 3 * PAD;
        club.heiqi.uilib.font.render.GlyphBatchCollector side =
                new club.heiqi.uilib.font.render.GlyphBatchCollector();
        renderPage(shared, view, service, aSegLines, aHeights, 14 + 2 * PAD, side, PAD, BASE);
        renderPage(shared, view, service, bLines, bHeights, 14 + 2 * PAD, side, 2 * PAD + pageW, BASE);
        DefaultFontRendererAdapter.getInstance().renderSegmentsToCollector(
                Collections.singletonList(labelSeg("chat3 @" + w)), shared.settings, service, view,
                PAD, 2, false, 1.0F, (float) LABEL_BASE, side);
        DefaultFontRendererAdapter.getInstance().renderSegmentsToCollector(
                Collections.singletonList(labelSeg("B @" + w)), shared.settings, service, view,
                2 * PAD + pageW, 2, false, 1.0F, (float) LABEL_BASE, side);
        int[] pixels = FontSoftwareRasterizer.render(buildFrame(side, sideW, pageH + PAD), shared.gl);
        File sideFile = new File(OUT_DIR, suffix(id, w) + "-side.png");
        FontSoftwareRasterizer.writePng(pixels, sideW, pageH + PAD, sideFile);
        int ink = countInk(pixels);
        Assert.assertTrue(id + " side 图墨水地板: 实测=" + ink, ink >= MIN_INK_PER_PAGE);
        Assert.assertTrue(id + " 两路 PNG 均存在且可解码",
                new File(OUT_DIR, suffix(id, w) + "-chat3.png").isFile()
                        && new File(OUT_DIR, suffix(id, w) + "-b.png").isFile());
        pngCount += 3;
        PROFILE.append("png ").append(suffix(id, w))
                .append(parity ? "" : "(NEW)").append(" Alines=").append(aLines.size())
                .append(" Blines=").append(bLines.size())
                .append(" Ah=").append(total(aHeights)).append(" Bh=").append(total(bHeights))
                .append(" sideInk=").append(ink).append('\n');
    }

    private static void label(club.heiqi.uilib.font.render.GlyphBatchCollector collector,
            GlyphRuntimeTablesView view, TextLayoutService service,
            LatexSoftwareRenderKit.Shared shared, String text, int basePx) {
        DefaultFontRendererAdapter.getInstance().renderSegmentsToCollector(
                Collections.singletonList(labelSeg(text)), shared.settings, service, view,
                PAD, 2, false, 1.0F, (float) basePx, collector);
    }

    /**
     * @Nx 判读副本（M4 增补）：与 @1x 同一 aLines/bLines（同一次解析+换行），只换倍率参数——
     * 段样式 fontSizePx×N、base=13N、A 行框 18N、B 行框在 13N 基准下自然量；画布短边背景补白
     * 到 ≥854×480。N=4 时 renderPx=52 ≤ atlas 64px，为按字形 px 真放大（零事后缩放）；
     * 不参与任何机器断言（仅验文件写出成功）。
     */
    private static void renderTripletNx(LatexSoftwareRenderKit.Shared shared, GlyphRuntimeTablesView view,
            TextLayoutService service, String id, String label, int w, boolean parity,
            List<ALine> aLines, List<List<TextSegment>> bLines) throws Exception {
        if (!MarkdownRenderScaleKit.nxEnabled() || (!parity && w != W_MAIN)) {
            return;
        }
        int n = MarkdownRenderScaleKit.N;
        int fixedLine = ChatMarkdownSettings.getChatLineHeightPx() * n;
        List<List<TextSegment>> aSegLines = new ArrayList<List<TextSegment>>();
        List<Integer> aHeights = new ArrayList<Integer>();
        for (ALine line : aLines) {
            aSegLines.add(MarkdownRenderScaleKit.scaleSegments(line.segments, n));
            aHeights.add(Integer.valueOf(fixedLine));
        }
        List<List<TextSegment>> bSegLines = MarkdownRenderScaleKit.scaleLines(bLines, n);
        List<Integer> bHeights = new ArrayList<Integer>();
        for (List<TextSegment> line : bSegLines) {
            bHeights.add(Integer.valueOf(MarkdownPainter.lineHeightPx(line, service, BASE * n)));
        }
        String sfx = MarkdownRenderScaleKit.nxSuffix();
        int pageW = w * n + 2 * PAD;
        int pageH = MarkdownRenderScaleKit.padH(Math.max(48,
                Math.max(total(aHeights), total(bHeights)) + 16 * n + 2 * PAD));
        pageW = MarkdownRenderScaleKit.padW(pageW);
        club.heiqi.uilib.font.render.GlyphBatchCollector aCollector =
                new club.heiqi.uilib.font.render.GlyphBatchCollector();
        renderPage(shared, view, service, aSegLines, aHeights, 14 * n + PAD, aCollector, PAD, BASE * n);
        label(aCollector, view, service, shared, "chat3@" + w + " " + label + " " + sfx, LABEL_BASE * n);
        File aFile = new File(OUT_DIR, suffix(id, w) + "-chat3" + sfx + ".png");
        FontSoftwareRasterizer.writePng(FontSoftwareRasterizer.render(
                buildFrame(aCollector, pageW, pageH), shared.gl), pageW, pageH, aFile);
        club.heiqi.uilib.font.render.GlyphBatchCollector bCollector =
                new club.heiqi.uilib.font.render.GlyphBatchCollector();
        renderPage(shared, view, service, bSegLines, bHeights, 14 * n + PAD, bCollector, PAD, BASE * n);
        label(bCollector, view, service, shared, "B@" + w + " " + label + " " + sfx, LABEL_BASE * n);
        File bFile = new File(OUT_DIR, suffix(id, w) + "-b" + sfx + ".png");
        FontSoftwareRasterizer.writePng(FontSoftwareRasterizer.render(
                buildFrame(bCollector, pageW, pageH), shared.gl), pageW, pageH, bFile);
        int sideW = MarkdownRenderScaleKit.padW(2 * pageW + 3 * PAD);
        club.heiqi.uilib.font.render.GlyphBatchCollector side =
                new club.heiqi.uilib.font.render.GlyphBatchCollector();
        renderPage(shared, view, service, aSegLines, aHeights, 14 * n + 2 * PAD, side, PAD, BASE * n);
        renderPage(shared, view, service, bSegLines, bHeights, 14 * n + 2 * PAD, side, 2 * PAD + pageW, BASE * n);
        DefaultFontRendererAdapter.getInstance().renderSegmentsToCollector(
                Collections.singletonList(labelSeg("chat3 @" + w)), shared.settings, service, view,
                PAD, 2, false, 1.0F, (float) (LABEL_BASE * n), side);
        DefaultFontRendererAdapter.getInstance().renderSegmentsToCollector(
                Collections.singletonList(labelSeg("B @" + w)), shared.settings, service, view,
                2 * PAD + pageW, 2, false, 1.0F, (float) (LABEL_BASE * n), side);
        File sideFile = new File(OUT_DIR, suffix(id, w) + "-side" + sfx + ".png");
        FontSoftwareRasterizer.writePng(FontSoftwareRasterizer.render(
                buildFrame(side, sideW, pageH), shared.gl), sideW, pageH, sideFile);
        Assert.assertTrue("@Nx 判读副本必须写出成功: " + sideFile, sideFile.isFile() && sideFile.length() > 100);
        PROFILE.append("pngNx ").append(suffix(id, w)).append(sfx)
                .append(" page=").append(pageW).append('x').append(pageH)
                .append(" side=").append(sideW).append('x').append(pageH)
                .append(" [判读用,无断言] ").append(MarkdownRenderScaleKit.detailReport(BASE)).append('\n');
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

    private static void writeDiffHeader() {
        DIFF.append("# M4 行为对拍门禁 —— chat3 现行路径(A) vs B(L1+L2+M5 存留链接化接线)\n")
                .append("# 口径:段=相邻比对字段全等合并后比较;字段=文本/颜色/FontType/斜删下/link/latex原子/切行/段宽/命中区\n")
                .append("# 容差:段宽 0px(统一尺 getSegmentWidth) | 行宽 1px | 命中区 x 1px(左 floor 右 ceil)\n")
                .append("# TIE:文本漂移但漂移 ≤1 码点 且 该两行交叉实测宽差 ≤2.0px(float/double 并列切点)\n")
                .append("# 命中区 y 不比(A 行框钉死 18px、B 自然量;记 profiles)。链头行段 link 允许「A 为 B 前缀」回填口径,\n")
                .append("# 但回填后命中区 url 必须与 B 全 url 严格相等。\n")
                .append("# A 路=旧 ChatMessageList 段流部分 1:1 行为快照(真机同源度量;M5 删旧实现后复刻在本类私有方法);B 路=toSegments→linkify→wrapLines。\n")
                .append("# 代理对预裁:A 若在代理对中间断行(违反 ERROR-20260825 零丢失规范面),该行差异记 TIE/RECORD 不比字面。\n")
                .append("# 出图倍率:").append(MarkdownRenderScaleKit.detailReport(BASE)).append('\n')
                .append("# @1x=现有文件名(全部机器断言只跑这份);@Nx 后缀=判读副本(同一次解析/换行,只换倍率,零断言,\n")
                .append("# 画布短边背景补白到 ≥854x480)。\n#\n");
    }

    private static void diffLine(String id, int w, String verdict, String message) {
        DIFF.append(id).append('@').append(w).append(' ').append(verdict).append(" | ")
                .append(message).append('\n');
    }

    private static void summaryLine() {
        DIFF.append("#\n# 汇总: PARITY 条目=").append(parityCount)
                .append(" NEW 条目=").append(newCount)
                .append(" FAIL 差异=").append(PARITY_FAILURES.size())
                .append(" TIE=").append(tieCount)
                .append(" 有意差异=").append(divergentCount)
                .append(" PNG=").append(pngCount).append('\n');
        PROFILE.append("summary parity=").append(parityCount).append(" new=").append(newCount)
                .append(" failIssues=").append(PARITY_FAILURES.size())
                .append(" divergent=").append(divergentCount).append('\n');
    }

    // ==================== 共用装配 ====================

    private static TextStyle bodyStyle() {
        TextStyle style = new TextStyle();
        style.setColor(WHITE);
        return style;
    }

    private static String collapseWs(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }
}
