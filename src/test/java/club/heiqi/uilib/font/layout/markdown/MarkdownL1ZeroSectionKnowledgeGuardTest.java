package club.heiqi.uilib.font.layout.markdown;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;

/**
 * L1 对 § 零知识守卫（C6a 追加任务书；宪法裁定：「markdown 解析器内没有 § 这种东西的
 * 解析，这不属于现代 markdown」）。
 *
 * <p><b>断言</b>：{@code src/main/java/club/heiqi/uilib/font/layout/markdown/}（L1 全部
 * 主源）的<b>代码行</b>（剥注释后，字符串字面量不剥——代码行里的 § 字面本身就是违规）
 * 不存在：§（U+00A7）字符字面、其 Unicode 转义文本形态、名为 SECTION 的常量、
 * isFormatCode / applyFormat / markerView / stripLeadingSectionCodes 标识符。
 * L1 的 span 流入口是<b>通用样式锚点通道</b>（IChatComponent 展开、业务 mod 富文本、
 * chat3 的 § 转换都只是调用方），不得做成 § 专用通道。</p>
 *
 * <p><b>反空跑地板</b>（规划 §四 G1 纪律「扫到 ∅ 先怀疑扫帚」，先例
 * {@code Chat3MarkdownResurrectionGuardTest}:68/:88-89）：同一扫描器对已知含 § 知识的
 * 对照文件 {@code internal/chat3/view/ChatMarkdownPipeline.java} 必须报出真实命中
 * （地板 = 实测基线）；L1 注释行 § 命中计数恒 &gt; 0（边界声明「本层对 § 零认知」
 * 确实以注释形态存在于扫到的文件里——证明扫描器看见了文件与字符，只是代码面为零）；
 * 目录/文件缺失、扫描集规模低于地板一律<b>红</b>而非静默跳过。</p>
 */
public class MarkdownL1ZeroSectionKnowledgeGuardTest {

    /** L1 主源目录（相对项目根 = 测试工作目录，与门禁产物 build/reports 同一既有约定）。 */
    private static final Path L1_DIR = Paths.get(
            "src/main/java/club/heiqi/uilib/font/layout/markdown");

    /** 正对照：chat3 集成层的 § 管道（现含 § 字符面、markerView、stripLeadingSectionCodes、applyFormat）。 */
    private static final Path CONTROL_FILE = Paths.get(
            "src/main/java/club/heiqi/uilib/internal/chat3/view/ChatMarkdownPipeline.java");

    /** § 字符本体（(char) 0x00A7 运行时拼装，规避 Java 源码 Unicode 预处理陷阱——同 L1 (char) 0x60 惯例）。 */
    private static final String SECTION_CHAR = String.valueOf((char) 0x00A7);

    /** § 的「反斜杠 + u 转义」文本形态（运行期拼串，防本守卫源码自身被预处理器吃掉）。 */
    private static final String SECTION_ESCAPED_LOWER = "\\" + "u00a7";
    private static final String SECTION_ESCAPED_UPPER = "\\" + "u00A7";

    /** § 知识禁止模式（代码行口径）。 */
    private static final Pattern[] FORBIDDEN = {
            Pattern.compile(Pattern.quote(SECTION_CHAR)),
            Pattern.compile(Pattern.quote(SECTION_ESCAPED_LOWER)),
            Pattern.compile(Pattern.quote(SECTION_ESCAPED_UPPER)),
            Pattern.compile("\\bSECTION\\b"),
            Pattern.compile("\\b(isFormatCode|applyFormat|markerView|stripLeadingSectionCodes)\\b"),
    };

    /** L1 扫描集规模地板（实测 9 文件；路径失效/整包失踪先红）。 */
    private static final int L1_FILE_FLOOR = 7;
    /** 对照文件代码行命中地板（实测基线，低于即扫描器失效）。 */
    private static final int CONTROL_CODE_HIT_FLOOR = 3;
    /** 对照文件含注释全行命中地板。 */
    private static final int CONTROL_ANY_HIT_FLOOR = 10;
    /** L1 注释行 § 提及地板（「对 § 零认知」边界声明在案，命中 0 = 扫描器或注释一起没了）。 */
    private static final int L1_COMMENT_HIT_FLOOR = 1;

    @Test
    public void l1SourceDirectoryMustExistAndBeSane() {
        Assert.assertTrue("L1 目录必须存在（测试工作目录 = 项目根；路径不存在必须红，不静默跳过）: "
                + L1_DIR.toAbsolutePath(), Files.isDirectory(L1_DIR));
        Assert.assertTrue("对照文件必须存在（正对照失效即红）: " + CONTROL_FILE,
                Files.isRegularFile(CONTROL_FILE));
    }

    /** 主断言：L1 代码行 § 知识恒 0；注释提及照登并设地板（反空跑）。 */
    @Test
    public void sectionCodeKnowledgeMustNeverEnterL1Code() throws IOException {
        List<Path> files = listJava(L1_DIR);
        Assert.assertTrue("L1 扫描集规模地板(实测 9 文件,路径失效即红): " + files.size(),
                files.size() >= L1_FILE_FLOOR);
        List<String> violations = new ArrayList<String>();
        int commentHits = 0;
        for (Path file : files) {
            List<String> code = codeLines(file);
            for (int i = 0; i < code.size(); i++) {
                if (countAll(code.get(i)) > 0) {
                    violations.add(normalize(file) + ":" + (i + 1) + " " + code.get(i).trim());
                }
            }
            // 注释提及 = 全行命中 − 代码行命中（两种形态分开照登：代码面断言 0，注释面设地板）
            commentHits += countAllLines(file) - countFile(file, code);
        }
        Assert.assertTrue("L1 主源代码行出现 § 解析知识（宪法违规：markdown 解析器内没有 § 这种东西）: "
                + violations, violations.isEmpty());
        Assert.assertTrue("L1 注释行 § 边界声明提及计数(实测 " + commentHits + ")必须 >= 地板 "
                + L1_COMMENT_HIT_FLOOR + "（0 = 扫描器看不见文件或 § 已烧进源码字面，两态皆虚锁）",
                commentHits >= L1_COMMENT_HIT_FLOOR);
        System.out.println("[C6a-guard] L1 files=" + files.size()
                + " codeLineViolations=" + violations.size()
                + " commentMentions=" + commentHits);
    }

    /** 反空跑：同一扫描器对已知含 § 知识的对照文件必须命中。 */
    @Test
    public void scannerMustSeeSectionKnowledgeInControlFile() throws IOException {
        Assert.assertTrue("对照文件存在性探测（扫描器自校准）: " + CONTROL_FILE,
                Files.isRegularFile(CONTROL_FILE));
        int codeHits = countFile(CONTROL_FILE, codeLines(CONTROL_FILE));
        int allHits = countAllLines(CONTROL_FILE);
        Assert.assertTrue("同一扫描器对 ChatMarkdownPipeline 代码行实测命中 " + codeHits
                + "（基线地板 " + CONTROL_CODE_HIT_FLOOR + "）；命中 0 = 扫描器恒假，主断言即虚锁",
                codeHits >= CONTROL_CODE_HIT_FLOOR);
        Assert.assertTrue("对照文件全行命中 " + allHits + " 必须 >= 代码行命中（注释里也有 § 提及）",
                allHits >= codeHits && allHits >= CONTROL_ANY_HIT_FLOOR);
        System.out.println("[C6a-guard] control=" + CONTROL_FILE + " codeHits=" + codeHits
                + " allLineHits=" + allHits);
    }

    // ==================== 扫描器（剥注释口径，沿用 Chat3MarkdownResurrectionGuardTest 先例） ====================

    private static int countFile(Path file, List<String> lines) {
        int hits = 0;
        for (String line : lines) {
            hits += countAll(line);
        }
        return hits;
    }

    private static int countAllLines(Path file) throws IOException {
        int hits = 0;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            hits += countAll(line);
        }
        return hits;
    }

    private static int countAll(String line) {
        int hits = 0;
        for (Pattern pattern : FORBIDDEN) {
            java.util.regex.Matcher matcher = pattern.matcher(line);
            while (matcher.find()) {
                hits++;
            }
        }
        return hits;
    }

    /** 读源文件剥注释（行注释/块注释完整行与前后缀；javadoc 提及不算代码；字符串字面量<b>不</b>剥）。 */
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
