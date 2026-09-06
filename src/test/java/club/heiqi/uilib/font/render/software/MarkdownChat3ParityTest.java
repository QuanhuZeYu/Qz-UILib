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
 * <h2>门禁判据定稿（C3b2，2026-09-06 对齐裁定第二步；母批 C1a 语义 + C3b1 对拍引擎）</h2>
 *
 * <p><b>C3b3 收紧（2026-09-06 同周第三批）</b>：行接缝 {@code Kind} 补上标题块身份
 * （{@code HEADING} + {@code getHeadingLevel()}=1..6），标题域随之<b>整体从核准表与差异域
 * 词表移除</b>——N01/X10/X11 直接对拍，kind 与 level 都必须等；防拿旧口径当挡箭牌
 * （先例＝SETEXT_NO_SUPPORT 废止），并由 {@link #headingStyleOnlyExemptionMustBeGone()}
 * 反向锁钉死「该域名再现即红」。</p>
 *
 * <p><b>C4 拆除批（2026-09-06 同周第四批）</b>：最后一个 RECORD 豁免——旧 § 桥差异域
 * <b>整体废止</b>（词表 / 核准表 / 分类通道 / 语料声明列四路清除，反向锁
 * {@link #bridgeSectionExemptionMustBeGone()}，先例＝headingStyleOnlyExemptionMustBeGone）。
 * § 处理归位 chat3 集成层输入清洗后，B 路（toLayoutLines，不经 chat3 桥）不再剥 §，R 路
 * commonmark 同样不认 § ⇒ P13/P14 两侧同为字面段落、直接对拍。本批之后<b>门禁不许再有
 * 任何 RECORD 条目</b>（{@code recordTotal == 0} 地板常驻）；豁免照登通道只剩 N11 的
 * EM_FLANK_SIMPLIFIED（唯一 RECORD_ONLY 成员，属 C5 范围，本批不动）。</p>
 *
 * <p><b>分层声明（C6b）</b>：本门禁 B 路继续走 L1 直连 String（{@code toLayoutLines}）、只管
 * markdown 语义对齐；§ → span 输入转换属 chat3 集成层，不归本门禁，其行为由 L3 层
 * {@code ChatMarkdownPipelineTest} 与 {@code ChatMarkdownSectionSpanMigrationLockTest} 钉死——
 * 故 P13/P14 语料两侧 § 均为字面、维持直拍 NO_DIFF，缺陷场景不进门禁语料。</p>
 *
 * <p>R 路＝{@link CommonMarkReferenceSemantics}（commonmark-java 0.21.0 + GFM strikethrough，
 * 官方参考实现）；B 路＝{@link BPathSemantics}（本仓 M10d 行接缝 {@code toLayoutLines} 同构映射）。
 * C3b1 的「全量照登、不判 PASS/FAIL」中间态到此结束：<b>逐条语料、逐行、逐 token 对拍，
 * 归一后仍存的差异一律 FAIL 即红</b>——门禁自本批起恢复常驻门禁地位（红 = build 红 = 不许交付）。</p>
 *
 * <h3>豁免核准表（唯一合法差异口径；逐条经用户裁定，代码落点见 {@link #foldPair}）</h3>
 * <ol>
 *   <li><b>EXT_FORMULA</b>（P04/P05/N08）——{@code $} 公式是本仓扩展、CommonMark 无此语法：
 *       R 侧 TEXT token 文本与 B 侧 FORMULA token 源文本，<b>剥成对 {@code $} 定界后逐字等</b>
 *       ⇒ 判等（类型不比对、切段不比对；{@code visible()} 仍恒 \u27e6源\u27e7 形态，吞字地板不破）。
 *       <b>P06（{@code $5.99} 防误伤）已撤豁免</b>：该条两侧都不产公式，直接逐 token 对拍，
 *       真把 {@code $5.99} 吃成公式原子当场红——豁免域不许当防误伤的挡箭牌。</li>
 *   <li><b>EXT_BARE_URL</b>（P01/P02/P07/P10/P11）——链接化属消费层（{@code ChatUrlLinkifier}），
 *       <b>不进接缝对拍</b>：归一时剥掉两侧 LINK 标记与 dest，接缝文本逐字等即判等。</li>
 *   <li><b>EXT_HTML</b>（语料暂无使用者，规则在案）——本仓刻意不支持 HTML：RAW_HTML token
 *       两侧文本等即判等（剥标记，字面文本仍逐字比）。</li>
 *   <li><b>THEMATIC_BREAK_TEXT</b>（N06/X10）——分隔线可见 36 连字符是样式表旋钮产物、
 *       不是语义：TB 行两侧 tokens <b>归一为空</b>再判（行身份 THEMATIC_BREAK 本身仍须等）。
 *       <b>标题不设同类目</b>：标题块身份与级别 C3b3 起已进行接缝，标题行 kind+level 直拍，
 *       标题基样式（粗体/下划线/字号）与颜色/字号/下划线一样根本不进语义面（B 提取侧剥除，
 *       见 {@code BPathSemantics} 类头 HEADING 条）——旧的「kind 豁免、只比文本」通道已删除。</li>
 *   <li><b>EXT_ORDERED_START</b>——C3b2 修 1（有序列表主流续排：首项源序号 = start，其后按
 *       start+项下标、定界统一句点）后<b>该域应零差异</b>：P09/X04/X05 已撤豁免直接对拍；
 *       域名与分类通道保留在案防回潮（一旦 L1 再偏离，差异照登且当场 FAIL）。</li>
 *   <li><b>EXT_STRIKE / EM_FLANK_SIMPLIFIED / ESCAPE_SUBSET</b>——C3b2 后 N07/N09/N10/X09
 *       已 NO_DIFF，<b>撤豁免直接对拍</b>；<u>N11 是唯一未清项</u>：R 侧按 CommonMark flanking
 *       规则把 {@code a*b，2*3} 判成 emphasis、{@code a**b**c} 判成 strong，本仓行内层仍按
 *       「邻空白」简化口径出字面——差异是<b>真实语义差</b>（文本都不等），任何归一都等于放宽判据，
 *       故本批只保留 N11 的 EM_FLANK_SIMPLIFIED 登记照登（不判红），撤销条件 = 行内 emphasis
 *       定界主流化那一批（规划在案的 C3 后续）落地后复跑。该保留已在本类自检里钉死为
 *       「域内照登不判红 + 域外同形差异必红」两条，不许扩成第二条同类豁免。</li>
 *   <li><b>其余语料无豁免</b>：直接逐行逐 token 对拍。</li>
 * </ol>
 *
 * <h3>断言（常驻，全绿才许交付）</h3>
 * <ol>
 *   <li><b>FAIL 即红</b>：归一后仍存且域未在核准表内的差异 = 0（失败消息逐条列出行号/域/R/B）；</li>
 *   <li>计数 PASS 全量：每条目都要出结论（NO_DIFF / 归一判等 / 豁免照登 三态之一；整条跳过
 *       通道已随 C4 § 域废止，recordTotal == 0 地板常驻），
 *       条目数 == 语料数，缺一即红；</li>
 *   <li>B 侧不崩：{@code toLayoutLines} 与语义提取全程无异常；</li>
 *   <li>B 侧不吞字：①提取器逐行可见串 == 行接缝原段流拼接（latex 恒 \u27e6源\u27e7）
 *       ②行接缝可见文本与段接缝（{@code toSegments}）去行界后逐字等（C1a 两接缝同源钉）；</li>
 *   <li>产物与地板：matrix.txt / diff.txt 落盘、每语料成块、B 路单侧 PNG 出图数与墨水地板
 *       （{@code MIN_INK_PER_PAGE}）照旧；矩阵引擎由自检用例钉死「分类/归一/FAIL」三条通道
 *       都不恒真——归一不许把不该判等的行判成等，FAIL 不许恒 0。</li>
 * </ol>
 *
 * <h3>比较口径（差异域词表；未列入核准表的域一旦出现差异即 FAIL）</h3>
 * <p>kind 家族归一（CODE_FENCED≡CODE_INDENTED≡B 统一 CODE）；F6 空行占位剔除后逐行序号
 * 直对；marker 归一 bullet=样式表符号「\u2022 」、有序=start+项下标+句点（两侧同口径）。
 * 在案域：EXT_FORMULA、EXT_BARE_URL、EXT_HTML、THEMATIC_BREAK_TEXT、
 * EXT_STRIKE、EXT_ORDERED_START、EM_FLANK_SIMPLIFIED、ESCAPE_SUBSET、
 * CODE_SPAN_TRIM、CODE_SCOPE/LINK_DEST/LINK_SCOPE/EXT_AUTOLINK、LIST_DEPTH/QUOTE_DEPTH、
 * LINE_ALIGN/TOKEN_ALIGN、MARKER_MISMATCH、TOKEN_TEXT/KIND_MISMATCH（兜底域均不进豁免表；
 * 标题级别差 = 行身份差，直判 KIND_MISMATCH，不设独立域）。</p>
 *
 * <p><b>常驻测试</b>：随 build 全量执行，门禁本体是只读消费者（C3b3 的接缝标题身份在
 * {@code [Refactor]} 生产码提交里，测试域不自改生产码）；不改 internal/chat3/**。
 * 语料现 48 条 = 20 P + 11 N + 17 X（X12..X17 = C4-fix 补 N2 惰性 setext 与深层嵌套组合
 * 覆盖，全部直接对拍、零新增豁免域；条目数断言与反空转地板随 {@code CORPUS.length} 自动扩，
 * 出图地板 PNG ≥ 条目数 × 2 同式）。
 * 出图/墨水地板/产物目录保留，只出 B 路单侧图（A 侧图随 A 路一并废止）。
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
    static final String D_STRIKE = "EXT_STRIKE";
    static final String D_ORDERED = "EXT_ORDERED_START";
    // C3b2 修 2 落地后 SETEXT_NO_SUPPORT 域废止；C3b3 接缝标题身份（Kind.HEADING +
    // getHeadingLevel）落地后 HEADING_STYLE_ONLY 域也随之整体从词表移除——两个旧域名
    // 都不许再当挡箭牌（反向锁见 headingStyleOnlyExemptionMustBeGone()）。N01/X10/X11
    // 改为直接对拍：行级 kind 与标题级别都必须等，不等即 KIND_MISMATCH 当场红。
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
    // X01..X10 = C3b1 新增主流边界语料（任务书点名场景逐条覆盖，见各行 label）；
    // X11 = C3b2 新增 setext 标题语料（与修 2 同期落地）；
    // X12..X17 = C4-fix 新增惰性 setext 语料（N2 收紧 + 深层嵌套组合，42 → 48 条，全直拍）。
    // 学费场景:① 行 junction 丢失 = P11(+P10@150、P17);② 两断行同形陷阱 = P10;③ 圆点剥除与
    // 链接化作用域 = P07(+P08)。防误伤项:$5.99→P06,hello_world/2*3→N11。
    // C3b2 判据定稿：豁免域声明只准写类头核准表里的域；已修好的域（EXT_ORDERED_START 与
    // EXT_STRIKE/ESCAPE_SUBSET）一律撤成 "-" 直接对拍，域登记留在词表防回潮。
    // C3b3 收紧：标题域连词表登记一并撤销（N01/X10/X11 直拍 kind+level，见域名注释）。
    // C4 拆除批：P13/P14 撤豁免转直拍（§ 归位后两侧同为字面段落）；旧 § 桥域名
    // 禁止再现，反向锁见 bridgeSectionExemptionMustBeGone()。

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
        // P06 撤豁免：两侧都不产公式，直接逐 token 对拍（防误伤真判据，见类头核准表第 1 条）
        {"P06", "$价格防误伤", "0", "-",
            "价格 $5.99 与 100$ 加 x$y 未闭合"},
        {"P07", "列表+链接作用域", "0", D_BARE_URL,
            "- 详见 http://a.b/c 完"},
        {"P08", "嵌套列表缩进", "0", "-",
            "- 甲\n  - 乙\n    - 丙"},
        {"P09", "有序列表", "0", "-",
            "1. 第一\n2. 第二"},
        {"P10", "跨行URL续链②", "0", D_BARE_URL,
            "参考 http://qz.example.com/releases/GTNH-Latest-9.zip 完\nhow 都不同"},
        {"P11", "junction长文①", "0", D_BARE_URL,
            "欢迎来到 GTNH 教程 http://gtnh.example.com/wiki/GTNH-New-Horizons-Modpack-2-入门指南 开始吧 world wide web 词边界测试\n第二行 中英 mixed prose with averyveryverylongtokenwithoutanyspaces 结束\n第三行 短"},
        {"P12", "单层引用行", "0", "-",
            "> 引用的文字"},
        {"P13", "§前缀列表行", "0", "-",
            "\u00a7a- 玩家列表行"},
        {"P14", "§颜色混排桥", "1", "-",
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
        // N01 撤豁免（C3b3）：接缝已带 Kind.HEADING + level 1/5，kind 与级别直拍
        {"N01", "ATX标题", "0", "-",
            "# 一级标题\n##### 五级标题"},
        {"N02", "围栏代码", "0", "-",
            "```java\nint x = 1; // **粗** $y$ > 引号 全字面\n```"},
        {"N03", "嵌套引用", "0", "-",
            "> 甲\n>> 乙\n>>> 丙"},
        {"N04", "列表续行", "0", "-",
            "- 甲项\n  甲项缩进续行"},
        {"N05", "硬换行", "0", "-",
            "第一行  \n第二行\\\n第三行"},
        // N06 源文本改空行隔开式（保住 label「分隔线」本义）：段落紧邻的 --- 现按 CommonMark
        // 判 setext 下划线（修 2），只剩分隔线可见文本这一条核准归一
        {"N06", "分隔线", "0", D_TB_TEXT,
            "上半句。\n\n---\n下半句。"},
        {"N07", "行内强调", "0", "-",
            "**粗** *斜* ~~删~~ ***粗斜*** 混排"},
        {"N08", "行内公式", "0", D_FORMULA,
            "质能 $e=mc^2$ 行内混排 with 尾"},
        {"N09", "链接语法", "0", "-",
            "访问 [Qz 主页](https://example.com/qz) 详情"},
        {"N10", "反斜杠转义", "0", "-",
            "路径 C:\\temp 与 \\* 星号 \\`x\\` 字面"},
        // N11 = 核准表第 6 条点名的唯一未清项：R 按 CommonMark flanking 判 emphasis/strong、
        // 本仓行内层按「邻空白」简化出口字面（文本都不等 ⇒ 无可归一），照登不判红，
        // 行内 emphasis 定界主流化那一批落地后连这条一起撤。
        {"N11", "emphasis防误伤", "0", D_FLANK,
            "hello_world 与 a*b，2*3=6 和 x_1 a**b**c"},
        {"X01", "缩进代码块多行与空行中断", "0", "-",
            "    缩进代码甲行\n    缩进代码乙行\n\n    空行后再起丙行"},
        {"X02", "段落后续行4空格折叠", "0", "-",
            "段落第一行开头\n    带四空格前导的续行应折叠进同一段落"},
        {"X03", "三空格顶级列表", "0", "-",
            "   - 三空格缩进的顶级项甲\n   - 三空格缩进的顶级项乙"},
        {"X04", "宽标记内容列12.", "0", "-",
            "12. 两位数宽标记的项\n13.   标记后多空格仍属同一内容列"},
        // X05 = 修 1 的正面语料：C3b1 该条目 2 处 EXT_ORDERED_START 差异，修后 NO_DIFF（撤豁免）
        {"X05", "有序跨序号续排", "0", "-",
            "3. 源序号三\n1. 源序号一\n9. 源序号九"},
        {"X06", "列表内空行松紧", "0", "-",
            "- 紧凑项甲\n- 松项第一段\n\n  松项第二段\n- 紧凑项乙"},
        {"X07", "引用内列表", "0", "-",
            "> - 引用内项甲\n>   引用内惰性续行\n> - 引用内项乙"},
        {"X08", "围栏带语言标识", "0", "-",
            "```swift\nlet x = 1 // **粗** 与 ~~删~~ 均字面\n```"},
        {"X09", "行内混排嵌套强调", "0", "-",
            "**外粗 *内外都粗斜* 尾** 与 ~~删中 **粗删嵌套** 尾~~ 混排"},
        // X10 保留原文（任务书口径）：首段 --- 按修 2 判 setext H2，C3b3 起接缝带 HEADING(2)
        // 直接对拍（原标题豁免已撤）；第二处 --- 仍是分隔线 ⇒ THEMATIC_BREAK_TEXT 留在核准表
        {"X10", "分隔线与列表歧义", "0", D_TB_TEXT,
            "歧义上句\n---\n- 列表项甲\n---\n歧义下句"},
        // C3b2 修 2 新增：setext 标题（=== → H1、--- → H2），下划线行本身不产内容行；
        // C3b3 撤标题豁免：setext 身份与级别同经接缝直拍（X11 = 全 setext 形态的正对照）
        {"X11", "setext标题", "0", "-",
            "甲行\n===\n乙行\n---"},
        // C4-fix 批补语料（任务书第三部分：C4 N2「setext 惰性续行收紧」挂下的覆盖缺口；
        // 全部 '-' 直接对拍，零新增豁免域——红 = 实现有偏必须修实现，等 = 保守侧锁住）：
        // X12/X13 = N2 收紧本体的两条正反面（惰性跟 === 主流判段内字面；自带标记 === 升格 H1）
        {"X12", "引用惰性setext字面", "0", "-",
            "> 甲\n==="},
        {"X13", "引用自带标记setext", "0", "-",
            "> 甲\n> ==="},
        // X14..X17 = 深层嵌套「惰性继承 + setext」组合（子代理未逐例实证 commonmark 的保守侧
        // 取「宁可少升格」；本批以 R 路 oracle 实证：等 ⇒ 锁，红 ⇒ 修 SrcLine 惰性继承策略）。
        // 立项注：本批初稿 X16 曾取「> a / > - b / >   ===」（内容列自带下划线 = 正常升格），
        // 实测红于「项首块=标题 ⇒ 标记段独占空行」的行折形态差（B=两行 LIST+HEADING，R=一行
        // LIST_ITEM+H1 注记 + 标题基样式位泄漏语义面）——该差对任意「- # t」ATX 同形、与惰性
        // 无关，属 C3b3 在 BPathSemantics 登记域旁的既有装配/C5 范围（放宽判据/改归一通道均
        // 被禁），本批按范围改册惰性形态；证据见规划 §二之八 C4-fix 细账第 7 条。
        {"X14", "引用内列表惰性setext", "0", "-",
            "> a\n> - b\n==="},
        {"X15", "引用内列表自带标记setext", "0", "-",
            "> a\n> - b\n> ==="},
        {"X16", "引用内有序列表惰性setext", "0", "-",
            "> a\n> 1. b\n==="},
        {"X17", "嵌套引用跨层惰性setext", "0", "-",
            ">> a\n> ==="},
    };

    private static final StringBuilder MATRIX = new StringBuilder();
    /** diff.txt 正文：未豁免差异（FAIL 明细）+ 逐条目判定，门禁红时先看这份。 */
    private static final StringBuilder DIFF = new StringBuilder();
    /** diff.txt 头部（判据/计数/FAIL 明细）——先于逐条目判定行写出，故另起一表。 */
    private static final StringBuilder DIFF_HEAD = new StringBuilder();
    private static final StringBuilder PROFILE = new StringBuilder();
    /** 未豁免差异清单（FAIL 即红；自检用例走独立 sink，不入此表）。 */
    private static final List<String> FAILS = new ArrayList<String>();
    /** 归一后仍存且域在核准表内（无归一规则可靠）的照登行数。 */
    private static int exemptTotal;
    /** 施加归一规则后判等（不再算差异）的行数。 */
    private static int normTotal;
    /**
     * 整条记 RECORD 跳过的条目数。<b>C4 收口：该通道已无可达触发点（旧 § 桥豁免域整体
     * 废止），本计数恒 0 由地板断言 == 0 常驻锁死</b>——任何重新造出「整条跳过」的改动
     * 都属放宽判据，当场红。
     */
    private static int recordTotal;
    /** FAIL 条目数（判据定稿：必须恒 0，红即不许交付）。 */
    private static int failEntries;
    /** 已出判定结论的条目数（与 CORPUS.length 全量对齐的计数地板）。 */
    private static int verdicts;
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
        Files.write(new File(OUT_DIR, "diff.txt").toPath(),
                diffReport().getBytes(StandardCharsets.UTF_8));
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

            // —— 判据定稿（C3b2）：归一后仍存的差异 ⇒ FAIL ——
            Verdict v = compareEntry(id, label, exemptions, r, b.lines, b.blanksRemoved,
                    MATRIX, FAILS);
            normTotal += v.normed;
            exemptTotal += v.exempt;
            verdicts++;
            if (v.skipped) {
                recordTotal++;
            }
            DIFF.append("## ").append(id).append(' ').append(label)
                    .append(" 判定=").append(v.summary()).append('\n');
            if (!v.passed()) {
                failEntries++;
            }

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

        MATRIX.append("#\n# 汇总（C3b2 判据定稿 + C3b3 标题直拍 + C4 § 域撤豁免 + C4-fix 乙′与惰性 setext 语料）: 条目=").append(Integer.valueOf(CORPUS.length))
                .append(" FAIL条目=").append(Integer.valueOf(failEntries))
                .append(" FAIL差异行=").append(Integer.valueOf(FAILS.size()))
                .append(" 归一判等行=").append(Integer.valueOf(normTotal))
                .append(" 豁免照登行=").append(Integer.valueOf(exemptTotal))
                .append(" RECORD条目=").append(Integer.valueOf(recordTotal))
                .append(" F6剔行=").append(Integer.valueOf(blankTotal))
                .append(" PNG=").append(Integer.valueOf(pngCount)).append('\n');
        DIFF_HEAD.append("# C3b2/C3b3/C4/C4-fix 门禁判定汇总（判据与豁免核准表见 MarkdownChat3ParityTest 类头）\n")
                .append("# 条目=").append(Integer.valueOf(CORPUS.length))
                .append(" FAIL差异行=").append(Integer.valueOf(FAILS.size()))
                .append(" 归一判等=").append(Integer.valueOf(normTotal))
                .append(" 豁免照登=").append(Integer.valueOf(exemptTotal))
                .append(" RECORD=").append(Integer.valueOf(recordTotal)).append('\n');
        for (String row : FAILS) {
            DIFF_HEAD.append("FAIL ").append(row).append('\n');
        }
        if (FAILS.isEmpty()) {
            DIFF_HEAD.append("(无未豁免差异：以下逐条目判定全部 PASS)\n");
        }
        PROFILE.append("summary entries=").append(Integer.valueOf(CORPUS.length))
                .append(" fails=").append(Integer.valueOf(FAILS.size()))
                .append(" normed=").append(Integer.valueOf(normTotal))
                .append(" exempt=").append(Integer.valueOf(exemptTotal))
                .append(" blanksRemoved=").append(Integer.valueOf(blankTotal)).append('\n');

        // —— 断言③：产物落盘 + 反空转地板 ——
        Files.write(new File(OUT_DIR, "matrix.txt").toPath(),
                MATRIX.toString().getBytes(StandardCharsets.UTF_8));
        Files.write(new File(OUT_DIR, "diff.txt").toPath(),
                diffReport().getBytes(StandardCharsets.UTF_8));
        File matrix = new File(OUT_DIR, "matrix.txt");
        Assert.assertTrue("matrix.txt 必须成功生成: " + matrix,
                matrix.isFile() && matrix.length() > 0);
        File diff = new File(OUT_DIR, "diff.txt");
        Assert.assertTrue("diff.txt（FAIL 明细/判定汇总）必须成功生成: " + diff,
                diff.isFile() && diff.length() > 0);
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
        // —— 断言④（定稿判据）：归一后仍存的未豁免差异一律 FAIL 即红；计数 PASS 必须全量 ——
        Assert.assertTrue("归一通道必须真跑到（否则核准表形同虚设）: normed="
                + Integer.valueOf(normTotal), normTotal >= 3);
        Assert.assertEquals("计数 PASS 全量：每条语料都必须出判定结论（引擎不许漏跑）",
                Integer.valueOf(CORPUS.length), Integer.valueOf(verdicts));
        Assert.assertEquals("C4 收口地板：RECORD（整条跳过）条目必须为 0——旧 § 桥豁免域已整体废止，"
                + "任何条目再被整条跳过都属放宽判据: record=" + Integer.valueOf(recordTotal),
                0, recordTotal);
        if (!FAILS.isEmpty()) {
            Assert.fail("C3b2 门禁 FAIL 即红：" + Integer.valueOf(failEntries)
                    + " 个条目共 " + Integer.valueOf(FAILS.size())
                    + " 处未豁免差异（逐条见 build/reports/markdown-compare/diff.txt）——"
                    + "红即不许交付。\n  " + join(FAILS, "\n  "));
        }
    }

    /** diff.txt 正文：头部汇总 + FAIL 明细在前，逐条目判定行在后。 */
    private static String diffReport() {
        return new StringBuilder(DIFF_HEAD).append(DIFF).toString();
    }

    private static String join(List<String> rows, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                sb.append(sep);
            }
            sb.append(rows.get(i));
        }
        return sb.toString();
    }

    // ==================== 矩阵引擎（C3b2 判据定稿：归一 + FAIL 即红） ====================

    /** 核准表里「只登记不判红」的域（类头第 6 条点名的 N11 唯一特例，不得私自扩充；
     *  C3b3 收紧后仍只剩这一条——标题域已连词表登记一并撤销）。 */
    private static final java.util.Set<String> RECORD_ONLY =
            java.util.Collections.singleton(D_FLANK);

    /** 单条目判定结论（三态计数 + 旧整条跳过标记；C4 起无任何通道把 skipped 置真，
     *  recordTotal == 0 地板 + 反向锁盯回潮）。 */
    static final class Verdict {
        private final int normed;
        private final int exempt;
        private final int fails;
        private final boolean skipped;

        Verdict(int normed, int exempt, int fails, boolean skipped) {
            this.normed = normed;
            this.exempt = exempt;
            this.fails = fails;
            this.skipped = skipped;
        }

        /** 未豁免差异为 0 即 PASS（归一判等与豁免照登都不算红）。 */
        boolean passed() {
            return fails == 0;
        }

        int fails() {
            return fails;
        }

        int normed() {
            return normed;
        }

        int exempt() {
            return exempt;
        }

        boolean skipped() {
            return skipped;
        }

        String summary() {
            if (skipped) {
                return "RECORD(整条跳过——C4 后无通道可达，重现即地板红)";
            }
            if (fails > 0) {
                return "FAIL×" + Integer.valueOf(fails);
            }
            if (normed > 0 && exempt > 0) {
                return "PASS(归一判等×" + Integer.valueOf(normed)
                        + " 豁免照登×" + Integer.valueOf(exempt) + ")";
            }
            if (normed > 0) {
                return "PASS(归一判等×" + Integer.valueOf(normed) + ")";
            }
            if (exempt > 0) {
                return "PASS(豁免照登×" + Integer.valueOf(exempt) + ")";
            }
            return "PASS(NO_DIFF)";
        }
    }

    /**
     * 单语料对拍（C3b2 定稿判据）：逐行先按该条目声明的豁免域施加<b>归一</b>
     * （{@link #foldPair}），归一后判等的行记 NORM（照登不判红、计入 normed）；仍不等的行，
     * 差异域若属 {@link #RECORD_ONLY} 且被该条目声明，记「豁免照登」（唯一一条：N11 的
     * EM_FLANK_SIMPLIFIED，见类头核准表第 6 条），<b>其余一律 FAIL 即红</b>。旧「整条记
     * RECORD 跳过」通道已随 C4 § 域归位废除（不再存在任何整条跳过的声明口径）。
     *
     * <p>行对齐＝剔 F6 空行后序号直对（不做 LCS——错位本身就是判据要看清的东西）；
     * kind 家族归一见 {@code kindFamily}；行级 kind 差异已含的 token 面成因不重复刷行。</p>
     */
    static Verdict compareEntry(String id, String label, String exemptions, List<SemanticLine> r,
            List<SemanticLine> b, int blanksRemoved, StringBuilder out, List<String> fails) {
        java.util.Set<String> ex = declared(exemptions);
        out.append("\n## ").append(id).append(" \u300c").append(label).append("\u300d")
                .append(" 豁免域=").append(exemptions)
                .append(" 行数R=").append(Integer.valueOf(r.size()))
                .append(" 行数B=").append(Integer.valueOf(b.size()))
                .append(" F6剔行=").append(Integer.valueOf(blanksRemoved)).append('\n');
        int normed = 0;
        int exempt = 0;
        int fail = 0;
        int n = Math.max(r.size(), b.size());
        for (int i = 0; i < n; i++) {
            SemanticLine rl = i < r.size() ? r.get(i) : null;
            SemanticLine bl = i < b.size() ? b.get(i) : null;
            List<String[]> strict = new ArrayList<String[]>();
            collectLineRows(id, i, rl, bl, strict);
            if (strict.isEmpty()) {
                continue;
            }
            List<String[]> judged = strict;
            if (!ex.isEmpty() && rl != null && bl != null) {
                List<String> fired = new ArrayList<String>();
                SemanticLine[] pair = foldPair(rl, bl, ex, fired);
                if (!fired.isEmpty()) {
                    List<String[]> folded = new ArrayList<String[]>();
                    collectLineRows(id, i, pair[0], pair[1], folded);
                    if (folded.isEmpty()) {
                        // 归一判等：照登 strict 行（保留未归一时的两侧形态供审计），不计差异
                        normed += strict.size();
                        for (String[] entryRow : strict) {
                            out.append(entryRow[1] + "(归一判等:" + entryRow[0] + ")").append('\n');
                        }
                        continue;
                    }
                    judged = folded;
                }
            }
            for (String[] entryRow : judged) {
                String dom = entryRow[0];
                boolean waived = RECORD_ONLY.contains(dom) && ex.contains(dom);
                String line = entryRow[1] + (waived ? "(豁免照登)" : "(FAIL)");
                out.append(line).append('\n');
                if (waived) {
                    exempt++;
                } else {
                    fail++;
                    if (fails != null) {
                        fails.add(line);
                    }
                }
            }
        }
        if (normed == 0 && exempt == 0 && fail == 0) {
            out.append("| 行* | NO_DIFF | 本条目 R/B 语义全等 |\n");
        }
        return new Verdict(normed, exempt, fail, false);
    }

    /**
     * 兼容旧签名的自检入口：返回照登差异条数（FAIL + 归一判等 + 豁免），不写全局 FAIL 账——
     * 自检用例要的是「分类通道不恒真」，不是门禁结论。
     */
    static int compareEntry(String id, String label, String exemptions, List<SemanticLine> r,
            List<SemanticLine> b, int blanksRemoved, StringBuilder out) {
        Verdict v = compareEntry(id, label, exemptions, r, b, blanksRemoved, out, null);
        return v.fails() + v.normed() + v.exempt();
    }

    /** 豁免域声明解析（逗号分隔；"-" 或空 = 无豁免）。 */
    private static java.util.Set<String> declared(String exemptions) {
        java.util.Set<String> out = new java.util.HashSet<String>();
        if (exemptions == null || "-".equals(exemptions.trim())) {
            return out;
        }
        for (String part : exemptions.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    /**
     * 核准表归一（唯一允许的判等前处理；逐条对偶类头注释，未声明的域一律不施加）。
     * 施加过的域名追加进 {@code fired}，矩阵据此把该行照登成「归一判等」，宽松点全程可见。
     *
     * <ul>
     *   <li><b>THEMATIC_BREAK_TEXT</b>：两侧同为分隔线行 ⇒ tokens 归一为空再判（行身份仍须等）；</li>
     *   <li>标题<b>无归一通道</b>（C3b3 收紧）：接缝已带 HEADING+level，行级 kind 与级别直接
     *       判等，原标题归一通道连同词表登记一并删除——防拿旧口径当挡箭牌；</li>
     *   <li><b>EXT_FORMULA</b>：该行含公式原子或 {@code $} 定界 ⇒ FORMULA 标记与 {@code $}
     *       字符一并剥除、再合并相邻同形 token ⇒「R 侧 TEXT 文本与 B 侧公式源文本逐字等
     *       即判等（类型不比对）」；</li>
     *   <li><b>EXT_BARE_URL</b>：剥 LINK 标记与 dest（链接化属消费层，不进接缝对拍）；</li>
     *   <li><b>EXT_HTML</b>：剥 RAW_HTML 标记（两侧文本仍逐字比）。</li>
     * </ul>
     */
    private static SemanticLine[] foldPair(SemanticLine rl, SemanticLine bl,
            java.util.Set<String> ex, List<String> fired) {
        SemanticLine a = rl;
        SemanticLine b = bl;
        if (ex.contains(D_TB_TEXT) && isTb(a) && isTb(b)) {
            a = reline(a, Collections.<InlineTok>emptyList());
            b = reline(b, Collections.<InlineTok>emptyList());
            fired.add(D_TB_TEXT);
        }
        if (ex.contains(D_FORMULA) && (lineHasMark(a, Mark.FORMULA)
                || lineHasMark(b, Mark.FORMULA) || hasDollar(a) || hasDollar(b))) {
            a = reline(a, mergeText(stripDollars(unFormula(a.tokens))));
            b = reline(b, mergeText(stripDollars(unFormula(b.tokens))));
            fired.add(D_FORMULA);
        }
        if (ex.contains(D_BARE_URL)) {
            a = reline(a, mergeText(stripMark(a.tokens, Mark.LINK)));
            b = reline(b, mergeText(stripMark(b.tokens, Mark.LINK)));
            fired.add(D_BARE_URL);
        }
        if (ex.contains(D_HTML)) {
            a = reline(a, mergeText(stripMark(a.tokens, Mark.RAW_HTML)));
            b = reline(b, mergeText(stripMark(b.tokens, Mark.RAW_HTML)));
            fired.add(D_HTML);
        }
        return new SemanticLine[] {a, b};
    }

    /**
     * 单行比对（行对可为归一后的形态）：行级 kind 差异 + 逐 token 差异 + 段数错位；
     * 每条差异以 {@code {域, 行文本}} 追加进 rows（判据分派与打标由调用方定）。
     */
    private static void collectLineRows(String id, int i, SemanticLine rl, SemanticLine bl,
            List<String[]> rows) {
        if (rl == null || bl == null) {
            // 行界错位无可归一（任何核准规则都不改行数）
            rows.add(new String[] {D_LINE_ALIGN, row(id, i, D_LINE_ALIGN,
                    rl == null ? "\u2205(本侧无此行)" : render(rl),
                    bl == null ? "\u2205(本侧无此行)" : render(bl))});
            return;
        }
        String kindDom = classifyKind(rl, bl);
        if (kindDom != null) {
            rows.add(new String[] {kindDom, row(id, i, kindDom, render(rl), render(bl))});
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
            rows.add(new String[] {dom, row(id, i, dom, lineTok(rl, k), lineTok(bl, k))});
        }
        if (rl.tokens.size() != bl.tokens.size()) {
            String dom = alignDomain(rl, bl);
            if (kindDom == null || !dom.equals(kindDom)) {
                rows.add(new String[] {dom, row(id, i, dom, render(rl), render(bl))});
            }
        }
    }

    private static boolean isTb(SemanticLine line) {
        return CommonMarkReferenceSemantics.kindFamily(line.kind).equals("THEMATIC_BREAK");
    }

    /** 重建行：只换 tokens（kind/level/层级/注记原样保留）。 */
    private static SemanticLine reline(SemanticLine line, List<InlineTok> tokens) {
        return reline(line, line.kind, line.level, tokens);
    }

    /** 重建行（可改 kind/level；ordered/ordinal/depth/note 不动，它们各有自己的域要判）。 */
    private static SemanticLine reline(SemanticLine line, Kind kind, int level,
            List<InlineTok> tokens) {
        return new SemanticLine(kind, level, line.ordered, line.ordinal, line.quoteDepth,
                line.listDepth, line.note, tokens);
    }

    /** FORMULA 标记降为普通文本（类型不比对；token 文本本就是公式源）。 */
    private static List<InlineTok> unFormula(List<InlineTok> in) {
        return stripMark(in, Mark.FORMULA);
    }

    /** 剥 {@code $} 定界字符（公式是本仓扩展，定界不进语义面）。 */
    private static List<InlineTok> stripDollars(List<InlineTok> in) {
        List<InlineTok> out = new ArrayList<InlineTok>(in.size());
        for (InlineTok t : in) {
            out.add(new InlineTok(t.marks, t.text.replace("$", ""), t.linkDest));
        }
        return out;
    }

    /** 剥指定标记；LINK 的 dest 随标记一并退出对拍。 */
    private static List<InlineTok> stripMark(List<InlineTok> in, Mark mark) {
        List<InlineTok> out = new ArrayList<InlineTok>(in.size());
        for (InlineTok t : in) {
            EnumSet<Mark> marks = EnumSet.noneOf(Mark.class);
            marks.addAll(t.marks);
            boolean had = marks.remove(mark);
            out.add(new InlineTok(marks, t.text,
                    had && mark == Mark.LINK ? null : t.linkDest));
        }
        return out;
    }

    /** 合并相邻同形 token（归一后切段差不该算差；与两提取器各自的 merge 口径同构）。 */
    private static List<InlineTok> mergeText(List<InlineTok> in) {
        List<InlineTok> out = new ArrayList<InlineTok>(in.size());
        for (InlineTok t : in) {
            if (t.text.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                InlineTok last = out.get(out.size() - 1);
                if (last.marks.equals(t.marks)
                        && CommonMarkReferenceSemantics.eq(last.linkDest, t.linkDest)) {
                    out.set(out.size() - 1,
                            new InlineTok(last.marks, last.text + t.text, last.linkDest));
                    continue;
                }
            }
            out.add(new InlineTok(t.marks, t.text, t.linkDest));
        }
        return out;
    }

    private static boolean hasDollar(SemanticLine line) {
        for (InlineTok t : line.tokens) {
            if (t.text.indexOf('$') >= 0) {
                return true;
            }
        }
        return false;
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
            if (rl.kind == Kind.HEADING && bl.kind == Kind.HEADING
                    && rl.level != bl.level) {
                // C3b3：标题级别是行身份的一部分，级别差=kind 参数差，直判兜底域（不设豁免域）
                return D_KIND;
            }
            if (rl.quoteDepth != bl.quoteDepth) {
                return D_QUOTE_DEPTH;
            }
            if (rl.listDepth != bl.listDepth) {
                return D_LIST_DEPTH;
            }
            return null;
        }
        return D_KIND; // C4：§ 不再参与任何块身份判定，kind 家族不同就是 kind 差，无桥接域可退
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

    /**
     * C3b3 整体废止的旧标题豁免域名。<b>本类源码里禁止出现该连续字面量</b>（词表/核准表/
     * 比对口径三处反向锁扫源码；历史说明只准写代码区注释），故这里用拼装造字——一旦有人
     * 把域名原样写回核准表或比对口径段，反向锁当场红。
     */
    private static final String GONE_HEADING_DOMAIN = "HEADING_" + "STYLE" + "_ONLY";

    /**
     * C4 整体废止的旧 § 桥豁免域名。<b>本类源码里禁止出现该连续字面量</b>（反向锁
     * {@link #bridgeSectionExemptionMustBeGone()} 全文扫描；历史说明只准写「旧 § 桥域」），
     * 故这里用拼装造字——一旦有人把域名原样写回词表/核准表/语料声明列，反向锁当场红。
     */
    private static final String GONE_BRIDGE_DOMAIN = "BRIDGE_" + "SECTION";

    /** 已知差异样本驱动矩阵引擎自检：正对照（全等输入零差异）+ 各域真实命中（反恒真）。 */
    @Test
    public void matrixChannelMustClassifyKnownDivergences() {
        StringBuilder scratch = new StringBuilder();
        // 正对照：全等 → 0 差异（通道不误报）
        List<SemanticLine> same = new ArrayList<SemanticLine>();
        same.add(line(Kind.TEXT, 0, false, 0, tok(EnumSet.noneOf(Mark.class), "甲", null)));
        Assert.assertEquals("全等输入必须零差异", 0,
                compareEntry("T0", "自检正对照", "-", same, copy(same), 0, scratch));
        // 1) 标题身份差（C3b3 收紧后）：R=HEADING vs B=TEXT+S 标记——行级 kind 差直判
        //    KIND_MISMATCH，token 面 S 标记差不再被标题域吸收 ⇒ 两条都是未豁免差异（红）。
        //    旧「恰一条 + 登记 HEADING 域」的反向对照：域名不得再出现在任何差异行里。
        List<SemanticLine> rH = new ArrayList<SemanticLine>();
        rH.add(line(Kind.HEADING, 2, false, 0, tok(EnumSet.noneOf(Mark.class), "标题", null)));
        List<SemanticLine> bH = new ArrayList<SemanticLine>();
        bH.add(line(Kind.TEXT, 0, false, 0, tok(EnumSet.of(Mark.STRONG), "标题", null)));
        Assert.assertEquals("标题身份差行级+token 面共两条", 2,
                compareEntry("T1", "自检标题直拍", "-", rH, bH, 0, scratch));
        Assert.assertTrue("行级身份差须判 KIND_MISMATCH", scratch.indexOf(D_KIND) >= 0);
        Assert.assertTrue("被废止的标题域不得再现身", scratch.indexOf(GONE_HEADING_DOMAIN) < 0);
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
        // 6) 反空转地板：五个差异样本全命中（T1 因标题直拍=行级+token 面两条，T2..T5 各一条；
        //    T0 正对照零差），且这五例声明都是「-」（无豁免）⇒ 全部计入 FAIL——
        //    判据定稿后「照登即红」的正面钉。
        Assert.assertEquals("无豁免声明的标题差异必须全计入 FAIL", 2, verdict(rH, bH, "-").fails());

        // ===== C3b2 定稿判据 + C3b3/C4 通道自检（归一/豁免/FAIL 都不许恒真；RECORD 恒不可达）=====
        // 7) 标题直拍正对照：kind+level 全等 ⇒ 零差异（NO_DIFF，不走任何归一通道）
        List<SemanticLine> bHsame = new ArrayList<SemanticLine>();
        bHsame.add(line(Kind.HEADING, 2, false, 0, tok(EnumSet.noneOf(Mark.class), "标题", null)));
        Verdict v7 = verdict(rH, bHsame, "-");
        Assert.assertEquals("kind+level 全等必须零差异", 0, v7.fails());
        Assert.assertEquals("直拍不经归一通道", 0, v7.normed());
        // 7b) 级别差也是身份差：HEADING(2) vs HEADING(3) ⇒ 当场 FAIL（KIND_MISMATCH）
        List<SemanticLine> bHlv = new ArrayList<SemanticLine>();
        bHlv.add(line(Kind.HEADING, 3, false, 0, tok(EnumSet.noneOf(Mark.class), "标题", null)));
        Verdict v7b = verdict(rH, bHlv, "-");
        Assert.assertEquals("级别差必须计入 FAIL", 1, v7b.fails());
        // 8) 旧豁免域不是挡箭牌：把被废止的域名当豁免声明传进判据 ⇒ 无归一、无照登、
        //    差异照红（foldPair 通道与分类归口都已从代码中删除）
        Verdict v8 = verdict(rH, bH, GONE_HEADING_DOMAIN);
        Assert.assertEquals("声明旧域名不得放宽判据", 2, v8.fails());
        Assert.assertEquals("旧域名无归一通道", 0, v8.normed());
        Assert.assertEquals("旧域名无照登通道", 0, v8.exempt());
        // 9) 公式归一判等（类型不比对）；而两侧文本真不等时仍须红
        Assert.assertEquals("公式声明后判等", 0, verdict(rF, bF, D_FORMULA).fails());
        Assert.assertEquals("公式声明后计一条 NORM", 1, verdict(rF, bF, D_FORMULA).normed());
        List<SemanticLine> bF2 = new ArrayList<SemanticLine>();
        bF2.add(line(Kind.TEXT, 0, false, 0, tok(EnumSet.of(Mark.FORMULA), "y", null)));
        Assert.assertEquals("公式源文本不等必红", 1, verdict(rF, bF2, D_FORMULA).fails());
        // 10) 分隔线文本归一判等；未声明同一样本必红（T5 已钉）
        Assert.assertEquals("TB 声明后判等", 0, verdict(rT, bT, D_TB_TEXT).fails());
        Assert.assertEquals("TB 声明后计一条 NORM", 1, verdict(rT, bT, D_TB_TEXT).normed());
        // 11) 只登记不判红的唯一特例（N11 的 EM_FLANK_SIMPLIFIED）：声明 ⇒ 豁免照登，不声明 ⇒ 红
        List<SemanticLine> rK = new ArrayList<SemanticLine>();
        rK.add(line(Kind.TEXT, 0, false, 0, tok(EnumSet.of(Mark.EM), "b", null)));
        List<SemanticLine> bK = new ArrayList<SemanticLine>();
        bK.add(line(Kind.TEXT, 0, false, 0, tok(EnumSet.noneOf(Mark.class), "b", null)));
        Verdict v11 = verdict(rK, bK, D_FLANK);
        Assert.assertEquals("豁免域内不判红", 0, v11.fails());
        Assert.assertEquals("豁免必须照登计数", 1, v11.exempt());
        Assert.assertEquals("同一样本不声明即红", 1, verdict(rK, bK, "-").fails());
        // 12) C4 收紧：旧 § 桥豁免域整体废止——把它当豁免声明传进判据 ⇒ 不整条跳过、
        //     无归一、无照登，差异照红（先例＝检查 8 对被废止标题域的同款反向钉）
        Verdict v12 = verdict(rH, bH, GONE_BRIDGE_DOMAIN);
        Assert.assertFalse("旧 § 域名不得再触发整条跳过", v12.skipped());
        Assert.assertEquals("声明旧 § 域名不得放宽判据", 2, v12.fails());
        Assert.assertEquals("旧 § 域名无归一通道", 0, v12.normed());
        Assert.assertEquals("旧 § 域名无照登通道", 0, v12.exempt());
        // 13) 声明了别的域不构成放宽：公式域声明救不了标题身份差（行级+token 面两条照红）
        Assert.assertEquals("跨域声明不得互相顶包", 2, verdict(rH, bH, D_FORMULA).fails());
    }

    /**
     * 反向锁（C3b3）：旧标题豁免域已整体废止——域名不得再出现在<b>差异域词表</b>（本类
     * String 常量值）、<b>语料豁免声明列</b>、<b>类头核准表</b>与<b>比较口径</b>两段 javadoc
     * 里；{@link #RECORD_ONLY} 仍恒等于 N11 的 EM_FLANK_SIMPLIFIED 单例（G 约束：本批之后
     * 照登通道只剩这一条）。任何一处再现身即红——防拿旧口径当挡箭牌
     * （先例：C3b2 废止 SETEXT_NO_SUPPORT 时把域名从词表移除并写明同款理由）。
     */
    @Test
    public void headingStyleOnlyExemptionMustBeGone() throws Exception {
        // ① 词表：差异域常量的值里不得再出现旧域名（按 D_ 命名约定反射扫，含 private；
        //    本锁自用的拼装常量 GONE_HEADING_DOMAIN 不属词表，天然不在 D_ 约定内）
        java.lang.reflect.Field[] fields = MarkdownChat3ParityTest.class.getDeclaredFields();
        int scanned = 0;
        for (int i = 0; i < fields.length; i++) {
            if (fields[i].getType() == String.class
                    && java.lang.reflect.Modifier.isStatic(fields[i].getModifiers())
                    && fields[i].getName().startsWith("D_")) {
                scanned++;
                Assert.assertFalse("差异域词表回潮：常量 " + fields[i].getName()
                        + " 的值是被废止的标题豁免域", GONE_HEADING_DOMAIN.equals(fields[i].get(null)));
            }
        }
        Assert.assertTrue("反射必须真扫到域常量表（实测 D_ 系 String 静态常量 " + scanned
                + " 个，低于地板说明扫描空转）", scanned >= 15);
        // ② 语料豁免声明列：任何条目不得声明旧域名
        for (int i = 0; i < CORPUS.length; i++) {
            Assert.assertFalse(CORPUS[i][0] + " 语料豁免声明列回潮",
                    CORPUS[i][3].contains(GONE_HEADING_DOMAIN));
        }
        // ③ 类头 javadoc：核准表段与比较口径段不得再现该域名（读自身源码；工作目录与
        //    build/reports 产物同一 Gradle 约定 = 项目根）
        File self = new File("src/test/java/club/heiqi/uilib/font/render/software/"
                + "MarkdownChat3ParityTest.java");
        Assert.assertTrue("本类源码必须可按仓内约定路径读回: " + self.getAbsolutePath(),
                self.isFile());
        String src = new String(Files.readAllBytes(self.toPath()), StandardCharsets.UTF_8);
        assertDomainAbsentInRegion(src, "<h3>豁免核准表", "<h3>断言", "核准表");
        assertDomainAbsentInRegion(src, "<h3>比较口径", "public class MarkdownChat3ParityTest",
                "比较口径段");
        // ④ 照登通道唯一性：RECORD_ONLY 恒 = {EM_FLANK_SIMPLIFIED}（本批不许新增豁免域）
        Assert.assertEquals("RECORD_ONLY 必须只剩 N11 的 EM_FLANK_SIMPLIFIED 一条（G 约束）",
                Collections.singleton(D_FLANK), RECORD_ONLY);
    }

    /**
     * 反向锁（C4）：旧 § 桥豁免域已整体废止——照 {@link #headingStyleOnlyExemptionMustBeGone()}
     * 先例格式四路扫描（外加全文零容忍，比标题锁更严：C4 之后该域名在本文件不存在任何合法
     * 出处，历史说明只准写「旧 § 桥域」）。任何一处现身即红：
     * ① 差异域词表（D_ 系 String 常量值）；② 语料豁免声明列；③ 本类源码全文；
     * ④ {@link #RECORD_ONLY} 仍恒等于 N11 的 EM_FLANK_SIMPLIFIED 单例（本批不许新增豁免域，
     * 照登通道唯一性不许漂移）；⑤ RECORD 收口地板 recordTotal == 0 在场。
     */
    @Test
    public void bridgeSectionExemptionMustBeGone() throws Exception {
        // ① 词表：D_ 系 String 静态常量的值里不得再出现旧 § 域名（本锁自用常量按 D_ 约定天然排除）
        java.lang.reflect.Field[] fields = MarkdownChat3ParityTest.class.getDeclaredFields();
        int scanned = 0;
        for (int i = 0; i < fields.length; i++) {
            if (fields[i].getType() == String.class
                    && java.lang.reflect.Modifier.isStatic(fields[i].getModifiers())
                    && fields[i].getName().startsWith("D_")) {
                scanned++;
                Assert.assertFalse("差异域词表回潮：常量 " + fields[i].getName()
                        + " 的值是被废止的旧 § 桥豁免域", GONE_BRIDGE_DOMAIN.equals(fields[i].get(null)));
            }
        }
        Assert.assertTrue("反射必须真扫到域常量表（实测 D_ 系 String 静态常量 " + scanned
                + " 个，低于地板说明扫描空转）", scanned >= 15);
        // ② 语料豁免声明列：任何条目不得声明旧 § 域名
        for (int i = 0; i < CORPUS.length; i++) {
            Assert.assertFalse(CORPUS[i][0] + " 语料豁免声明列回潮",
                    CORPUS[i][3].contains(GONE_BRIDGE_DOMAIN));
        }
        // ③ 全文扫描：旧 § 域名连续字面量在本类源码零出现（词表/核准表/比较口径/断言文案/
        //    判定文案/矩阵行文案/分类注释全在内——C4 后它没有任何合法出处）
        File self = new File("src/test/java/club/heiqi/uilib/font/render/software/"
                + "MarkdownChat3ParityTest.java");
        Assert.assertTrue("本类源码必须可按仓内约定路径读回: " + self.getAbsolutePath(),
                self.isFile());
        String src = new String(Files.readAllBytes(self.toPath()), StandardCharsets.UTF_8);
        Assert.assertFalse("被废止的旧 § 桥豁免域名不得在本类源码任何位置现身",
                src.contains(GONE_BRIDGE_DOMAIN));
        // ④ 照登通道唯一性不漂移：RECORD_ONLY 恒 = {EM_FLANK_SIMPLIFIED}
        Assert.assertEquals("RECORD_ONLY 必须只剩 N11 的 EM_FLANK_SIMPLIFIED 一条（C5 前不许增删）",
                Collections.singleton(D_FLANK), RECORD_ONLY);
        // ⑤ RECORD 收口地板：整条跳过计数必须为 0（主用例未跑时初值即 0，跑后仍须 0）
        Assert.assertEquals("门禁不许再有任何 RECORD 条目", 0, recordTotal);
    }

    /** 反向锁 ③ 的区域切割：找不到边界标记也算红（防止悄悄删掉表头来绕过扫描）。 */
    private static void assertDomainAbsentInRegion(String src, String beginMark, String endMark,
            String regionName) {
        int begin = src.indexOf(beginMark);
        int end = src.indexOf(endMark, begin);
        Assert.assertTrue(regionName + "边界标记丢失（表体被挪走/改名同样是拆锁）: begin="
                + Integer.valueOf(begin) + " end=" + Integer.valueOf(end), begin >= 0 && end > begin);
        String region = src.substring(begin, end);
        Assert.assertFalse("被废止的标题豁免域不得再现身于" + regionName,
                region.contains(GONE_HEADING_DOMAIN));
    }

    /** 自检便捷口：跑一次判据并返回结论（failSink 用局部表，不污染门禁账）。 */
    private static Verdict verdict(List<SemanticLine> r, List<SemanticLine> b, String exemptions) {
        return compareEntry("TX", "自检", exemptions, copy(r), copy(b), 0,
                new StringBuilder(), new ArrayList<String>());
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
        MATRIX.append("# C3b2 判据定稿 + C3b3 标题直拍 + C4 § 域撤豁免 + C4-fix 惰性 setext 语料矩阵"
                + " —— R=commonmark-java 0.21.0(+GFM strikethrough) vs B=本仓 toLayoutLines"
                + "(M10d 行接缝 + C3b3 标题身份)\n")
                .append("# 判据：逐行先按条目声明的豁免域施加归一（核准表见本类 javadoc），归一后仍存且域未核准的差异一律 FAIL 即红。\n")
                .append("# 行标后缀：(FAIL)=未豁免差异（红）｜(归一判等:域)=核准归一后判等，照登不判红｜(豁免照登)=RECORD_ONLY 域（仅 N11 的 EM_FLANK_SIMPLIFIED）。\n")
                .append("# C3b3：标题行 kind+level 直拍（N01/X10/X11 撤豁免；旧标题豁免域已从词表移除），标题基样式位不进 B 语义面。\n")
                .append("# C4：旧 § 桥豁免域整体废止（P13/P14 直拍；§ 处理归位 chat3 集成层，B 路不再剥 §），RECORD 条目恒 0。\n")
                .append("# 口径: kind 家族归一(CODE_FENCED\u2261CODE_INDENTED\u2261B 统一 CODE) | F6 空行占位剔除后逐行序号直对 |\n")
                .append("# marker 归一 bullet=样式表符号「\u2022 」、有序=start+项下标+句点（两侧同口径）| token 文本逐字等、标记集等、LINK dest 等 |\n")
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
