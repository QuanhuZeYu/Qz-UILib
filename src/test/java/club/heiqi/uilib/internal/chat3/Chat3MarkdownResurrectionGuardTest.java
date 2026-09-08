package club.heiqi.uilib.internal.chat3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;

/**
 * M6 复生锁（规划《通用Markdown渲染器》§四 G3；与 M5 接线同批落地，工作树任何一刻不留两条真相）。
 *
 * <p><b>断言①</b>：{@code internal/chat3/**} 下不存在 {@code ChatMarkdownLineRule.java}，且
 * 旧 {@code ChatCodeSpanSplitter} 的 markdown 定界解析形态（反引号配对/引号星号对/"~~"/"$$"）
 * 不得以代码行形式复活。反引号书写沿用仓内 (char) 0x60 惯例，扫描模式对字面量、
 * Unicode 转义文本与 0x60 强转三种写法全覆盖。</p>
 *
 * <p><b>断言②</b>：chat3 不得再自建段流解析入口——markdown 消费唯一入口 =
 * {@code ChatMarkdownPipeline}（必须实际调用 {@code MarkdownDocument.parse} 与
 * {@code MarkdownPainter.wrapLines}，即「经 ui/markdown 或 font/layout/markdown」），
 * chat3 其余文件不得引用 markdown 层类型。</p>
 *
 * <p><b>每条反向断言配正对照 + 反空跑地板</b>（规划 §四 G1 纪律，扫到 ∅ 先怀疑扫帚）：
 * 同一扫描器对已知含目标模式的兄弟面（L1 {@code font/layout/markdown} 与门禁 A 路复刻文件）
 * 必须报出真实命中数（下限 = M5 实测写死）；文件存在性探测器对现存文件必须返回 true；
 * chat3 主源文件计数低于地板即路径失效先红。</p>
 */
public class Chat3MarkdownResurrectionGuardTest {

    private static final Path MAIN_ROOT = Paths.get("src/main/java");
    private static final Path CHAT3_DIR = Paths.get(
            "src/main/java/club/heiqi/uilib/internal/chat3");
    /** L1 兄弟包 = 定界解析的合法之家（模式正对照源）。 */
    private static final String L1_PACKAGE = "club/heiqi/uilib/font/layout/markdown/";
    /** 门禁 A 路复刻文件 = 反引号配对与 "$$" 行级判据的正对照源（测试源树）。 */
    private static final Path GATE_TEST_FILE = Paths.get(
            "src/test/java/club/heiqi/uilib/font/render/software/MarkdownChat3ParityTest.java");
    /** devtools markdown 页 = L1/L2 import 扫描器能看见合法消费者的正对照。 */
    private static final String MARKDOWN_PAGE_FILE =
            "club/heiqi/uilib/internal/devtools/playground/pages/MarkdownPage.java";

    /** 反引号字符与「反斜杠u转义」文本（运行期拼装，规避 Java 源码 Unicode 预处理器陷阱——同 L1 (char) 0x60 惯例）。 */
    private static final String TICK = String.valueOf((char) 0x60);
    private static final String TICK_ESCAPED_TEXT = "\\" + "u0060";
    private static final String STAR = String.valueOf((char) 0x2a);

    /** 复生禁止模式（代码行口径；M5 实测 chat3 主源全部 0 命中）。 */
    private static final Pattern[] RESURRECTION_PATTERNS = {
            // 旧 ChatCodeSpanSplitter：反引号配对的三种书写形态 + 标识
            Pattern.compile(Pattern.quote(TICK)),
            Pattern.compile(Pattern.quote(TICK_ESCAPED_TEXT)),
            Pattern.compile("\\(char\\)\\s*0x60\\b"),
            Pattern.compile("\\bCODE_TICK\\b"),
            // 旧行级规则：定界字符/字面
            Pattern.compile(Pattern.quote("'" + STAR + "'")),
            Pattern.compile(Pattern.quote("\"" + STAR + STAR + "\"")),
            Pattern.compile(Pattern.quote("\"~~\"")),
            Pattern.compile(Pattern.quote("\"$$\"")),
            // 旧类名本体不得复活（import/声明/调用；注释已由扫描器剥除）
            Pattern.compile("\\bChatMarkdownLineRule\\b"),
            Pattern.compile("\\bChatCodeSpanSplitter\\b"),
    };

    /** 正对照一（L1）M5 实测合计命中 ≥13（dstar 3 + tilde 3 + 星号单引号 7 + CODE_TICK 7 去重后），地板取 8。 */
    private static final int L1_CONTROL_MIN_HITS = 8;
    /** 正对照二（门禁复刻）M5 实测：Unicode 转义文本 + (char) 0x60 + "$$"×3 + indexOf(TICK)，地板取 4。 */
    private static final int GATE_CONTROL_MIN_HITS = 4;
    /** chat3 主源文件计数地板（M5 实测 37）。 */
    private static final int CHAT3_FILE_FLOOR = 30;

    // ==================== 断言① ====================

    /** 旧实现文件恒不存在；探测器用现存文件反向校准（反空跑）。 */
    @Test
    public void deletedShimFilesMustStayDeleted() {
        Assert.assertTrue("探测器校准:ChatMessageList.java 必须被判为存在",
                Files.isRegularFile(CHAT3_DIR.resolve("view/ChatMessageList.java")));
        Assert.assertTrue("探测器校准:ChatUrlLinkifier(存留件)必须被判为存在",
                Files.isRegularFile(CHAT3_DIR.resolve("viewmodel/ChatUrlLinkifier.java")));
        Assert.assertFalse("ChatMarkdownLineRule 已随 M5 删除,不得复活(规划 §三 M5/§四 G3)",
                Files.isRegularFile(CHAT3_DIR.resolve("viewmodel/ChatMarkdownLineRule.java")));
        Assert.assertFalse("ChatCodeSpanSplitter 的定界解析已随 M5 删除,不得复活"
                + "(code 样式职责由 M4-fix F1 承接进 L1 MarkdownStyleTable)",
                Files.isRegularFile(CHAT3_DIR.resolve("viewmodel/ChatCodeSpanSplitter.java")));
    }

    /** 定界解析模式在 chat3 主源代码行恒 0 命中；同一扫描器对兄弟面必须报出真实命中。 */
    @Test
    public void markdownDelimitersMustNotBeParsedInChat3() throws IOException {
        List<Path> chat3Files = listJava(CHAT3_DIR);
        Assert.assertTrue("chat3 扫描集规模地板(实测 37 文件,路径失效即红): "
                + chat3Files.size(), chat3Files.size() >= CHAT3_FILE_FLOOR);
        List<String> violations = new ArrayList<String>();
        for (Path file : chat3Files) {
            List<String> code = codeLines(file);
            for (int i = 0; i < code.size(); i++) {
                for (Pattern pattern : RESURRECTION_PATTERNS) {
                    if (pattern.matcher(code.get(i)).find()) {
                        violations.add(normalize(file) + ":" + (i + 1) + " " + code.get(i).trim());
                    }
                }
            }
        }
        Assert.assertTrue("chat3 主源出现 markdown 定界解析形态(复生锁 G3 断言①): "
                + violations, violations.isEmpty());
        int l1Hits = countPatternsInPackage(L1_PACKAGE);
        Assert.assertTrue("同一扫描器对 L1 兄弟包实测命中 " + l1Hits + "(M5 基准地板 "
                + L1_CONTROL_MIN_HITS + ");命中 0 = 扫描器失效,断言①即虚锁",
                l1Hits >= L1_CONTROL_MIN_HITS);
        int gateHits = countPatternsInFile(GATE_TEST_FILE);
        Assert.assertTrue("同族模式对门禁 A 路复刻实测命中 " + gateHits + "(M5 基准地板 "
                + GATE_CONTROL_MIN_HITS + ")", gateHits >= GATE_CONTROL_MIN_HITS);
    }

    /** T1：管线字符检测只能在 L1。扫描字符、单管线字符串、转义管线正则与数值写法。
     *  普通 || 运算符与 SenderExtractor 的既有正则分支不是表格语法。 */
    private static final Pattern[] TABLE_SYNTAX_PATTERNS = {
            Pattern.compile(Pattern.quote("'|'")),
            Pattern.compile(Pattern.quote("\"|\"")),
            Pattern.compile(Pattern.quote("\\\\|")),
            Pattern.compile(Pattern.quote("\\" + "u007c"), Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b0[xX]0*7[cC]\\b"),
            Pattern.compile("\\(char\\)\\s*124\\b"),
    };

    /** 正对照来自真实 L1 主源（T1 Python 同口径复核 6 次命中），地板取 2；不能用测试夹具替代。 */
    private static final int TABLE_CONTROL_MIN_HITS = 2;

    @Test
    public void tableSyntaxMustStayInL1() throws IOException {
        List<Path> l2Files = listJava(MAIN_ROOT.resolve("club/heiqi/uilib/ui/markdown"));
        List<Path> chat3Files = listJava(CHAT3_DIR);
        Assert.assertTrue("ui/markdown 扫描集地板", l2Files.size() >= 2);
        Assert.assertTrue("chat3 扫描集地板", chat3Files.size() >= CHAT3_FILE_FLOOR);
        List<Path> consumers = new ArrayList<Path>(l2Files);
        consumers.addAll(chat3Files);
        List<String> violations = new ArrayList<String>();
        for (Path file : consumers) {
            for (String line : codeLines(file)) {
                for (Pattern pattern : TABLE_SYNTAX_PATTERNS) {
                    if (pattern.matcher(line).find()) {
                        violations.add(normalize(file) + ": " + line.trim());
                    }
                }
            }
        }
        Assert.assertTrue("ui/markdown 与 chat3 不得自行检测管线表格语法: " + violations,
                violations.isEmpty());
        List<Path> l1Files = listJava(MAIN_ROOT.resolve(L1_PACKAGE));
        Assert.assertTrue("L1 正对照扫描集地板", l1Files.size() >= 4);
        int hits = 0;
        for (Path file : l1Files) {
            hits += countPatternsInFile(file, TABLE_SYNTAX_PATTERNS);
        }
        Assert.assertTrue("同一扫描器必须看到真实 L1 管线检测，命中 " + hits
                + "，地板 " + TABLE_CONTROL_MIN_HITS, hits >= TABLE_CONTROL_MIN_HITS);
    }

    // ==================== 断言② ====================

    /**
     * 生产锚：唯一入口必须真的调用 L1 parse 与 L2 换行。
     *
     * <p>M7（2026-09-05 方案乙）锚字符串随生产接线演进：{@code .toSegments(} →
     * {@code .toLayoutLines(}、{@code MarkdownPainter.wrapLines(} →
     * {@code MarkdownPainter.wrapLayoutLines(}——断言语义（「chat3 唯一入口必须经 L1 解析 +
     * L2 换行 + 换行前链接化」，命中数 &gt;= 1 的正向锚）一字未放松，只随实际入口更名而更。
     * C6b（2026-09-07 方案甲）同款演进：{@code MarkdownDocument.parse(} →
     * {@code MarkdownDocument.parseSpans(}（String 入口随输入清洗退役，span 流入口成为
     * 唯一 L1 解析入口）；<b>C7（2026-09-07 划界）再演回</b> {@code MarkdownDocument.parse(}
     * ——§ → span 输入转换随「markdown 路径不解释 §」退役，String 入口重新是唯一 L1 解析入口
     * （{@code parseSpans} 保留在 L1 公共面留给将来富文本 component 通道，chat3 侧零 § 消费者）。
     * 两轮都仍是「>=1 命中」正向锚，语义零放松。
     * 门禁 {@code MarkdownChat3ParityTest} 的判据/容差/引擎与本锁无关、未动。</p>
     */
    @Test
    public void pipelineMustActuallyRouteThroughL1AndL2() throws IOException {
        Path pipeline = CHAT3_DIR.resolve("view/ChatMarkdownPipeline.java");
        Assert.assertTrue("M5 接线本体 ChatMarkdownPipeline.java 必须存在",
                Files.isRegularFile(pipeline));
        List<String> code = codeLines(pipeline);
        // C6b → C7 两轮锚串随生产入口演进（parse( → parseSpans( → parse(），「>=1 命中」正向
        // 语义与全部地板一字未放松（先例与理由见本方法 javadoc）。
        Assert.assertTrue("入口必须经 L1:MarkdownDocument.parse 调用点 >=1",
                countSubstring(code, "MarkdownDocument.parse(") >= 1);
        Assert.assertTrue("入口必须经 L2:MarkdownPainter.wrapLayoutLines 调用点 >=1",
                countSubstring(code, "MarkdownPainter.wrapLayoutLines(") >= 1);
        Assert.assertTrue("块身份行接缝调用点 >=1: .toLayoutLines(",
                countSubstring(code, ".toLayoutLines(") >= 1);
        Assert.assertTrue("链接化存留件调用点 >=1: ChatUrlLinkifier.linkify(",
                countSubstring(code, "ChatUrlLinkifier.linkify(") >= 1);
    }

    /** chat3 内 markdown 层类型引用必须收敛在唯一入口文件（不得长出第二个解析入口）。 */
    @Test
    public void onlyOneChat3FileMayReferenceMarkdownLayers() throws IOException {
        List<String> offenders = new ArrayList<String>();
        Set<String> hitFiles = new LinkedHashSet<String>();
        for (Path file : listJava(CHAT3_DIR)) {
            String name = file.getFileName().toString();
            for (String line : codeLines(file)) {
                if (line.contains("font.layout.markdown") || line.contains("ui.markdown")
                        || line.contains("MarkdownDocument") || line.contains("MarkdownPainter")
                        || line.contains("MarkdownStyleTable") || line.contains("MarkdownInlineParser")
                        || line.contains("MarkdownSpan")) {
                    hitFiles.add(name);
                    if (!"ChatMarkdownPipeline.java".equals(name)) {
                        offenders.add(name + ": " + line.trim());
                    }
                }
            }
        }
        Assert.assertTrue("chat3 出现第二个 markdown 解析入口(复生锁 G3 断言②): " + offenders,
                offenders.isEmpty());
        Assert.assertTrue("扫描器必须在 ChatMarkdownPipeline.java 真命中 L1/L2 引用(反空跑): "
                + hitFiles, hitFiles.size() == 1
                        && hitFiles.contains("ChatMarkdownPipeline.java"));
        Path page = MAIN_ROOT.resolve(MARKDOWN_PAGE_FILE);
        Assert.assertTrue("MarkdownPage 存在性探测(扫描器自校准)", Files.isRegularFile(page));
        List<String> pageCode = codeLines(page);
        Assert.assertTrue("对照:MarkdownPage 同时引用 MarkdownDocument 与 MarkdownPainter"
                + "(L1/L2 引用扫描器对合法消费者不瞎)",
                countSubstring(pageCode, "MarkdownDocument") >= 1
                        && countSubstring(pageCode, "MarkdownPainter") >= 1);
    }

    // ==================== 扫描器 ====================

    private static int countPatternsInPackage(String packageFragment) throws IOException {
        int hits = 0;
        for (Path file : listJava(MAIN_ROOT)) {
            if (!normalize(file).contains(packageFragment)) {
                continue;
            }
            hits += countPatternsInFile(file);
        }
        return hits;
    }

    private static int countPatternsInFile(Path file) throws IOException {
        return countPatternsInFile(file, RESURRECTION_PATTERNS);
    }

    private static int countPatternsInFile(Path file, Pattern[] patterns) throws IOException {
        int hits = 0;
        for (String line : codeLines(file)) {
            for (Pattern pattern : patterns) {
                java.util.regex.Matcher matcher = pattern.matcher(line);
                while (matcher.find()) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private static int countSubstring(List<String> lines, String token) {
        int n = 0;
        for (String line : lines) {
            n += line.split(Pattern.quote(token), -1).length - 1;
        }
        return n;
    }

    /** 读源文件剥注释（行注释/块注释完整行与前后缀；javadoc 提及不算代码）。 */
    private static List<String> codeLines(Path file) throws IOException {
        List<String> out = new ArrayList<String>();
        boolean inBlock = false;
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String s = raw.trim();
            if (inBlock) {
                int close = s.indexOf("*/");
                if (close >= 0) {
                    inBlock = false;
                    String tail = s.substring(close + 2).trim();
                    if (!tail.isEmpty()) {
                        out.add(tail);
                    }
                }
                continue;
            }
            if (s.startsWith("//")) {
                continue;
            }
            int blockOpen = s.indexOf("/*");
            if (blockOpen >= 0) {
                String head = s.substring(0, blockOpen).trim();
                if (!head.isEmpty()) {
                    out.add(head);
                }
                int close = s.indexOf("*/", blockOpen + 2);
                if (close < 0) {
                    inBlock = true;
                } else {
                    String tail = s.substring(close + 2).trim();
                    if (!tail.isEmpty()) {
                        out.add(tail);
                    }
                }
                continue;
            }
            if (s.startsWith("*")) {
                continue;
            }
            String line = raw;
            int lineComment = line.indexOf("//");
            if (lineComment >= 0) {
                line = line.substring(0, lineComment);
            }
            if (!line.trim().isEmpty()) {
                out.add(line);
            }
        }
        return out;
    }

    private static List<Path> listJava(Path root) throws IOException {
        List<Path> out = new ArrayList<Path>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(out::add);
        }
        return out;
    }

    private static String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }
}