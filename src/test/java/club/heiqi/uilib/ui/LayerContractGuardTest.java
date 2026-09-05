package club.heiqi.uilib.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;

/**
 * 分层契约守护测试。
 *
 * <p>把架构关键不变量里反复靠人工 grep + 交接口述守护的分层铁律（I1-I9 旧编号，含义以本类断言为准），
 * 资产化为可执行、可在 CI 阻断合并的源码扫描断言。复用 {@code Lwjgl3ifyInputBackendTest}
 * 已验证的 {@code Files.walk("src/main/java")} 静态扫描范式，无需真机 / GUI 依赖。</p>
 *
 * <p>本测试只做「源码静态 import 扫描」，对包重命名敏感（会误报），这是相对人工口述守护
 * 的可接受弱点；它阻断的是「错误依赖方向被悄悄引入」这一最高频回归。</p>
 */
public class LayerContractGuardTest {

    /** 主源码根目录。 */
    private static final Path MAIN_SOURCE_ROOT = Paths.get("src/main/java");

    /** 数据层（响应式）包相对路径片段。 */
    private static final String REACTIVE_PACKAGE = "club/heiqi/uilib/ui/reactive/";

    /**
     * 已按 2026-09-01《裁定-UILib-布局绘制系统》删除的分层包：绘制层 {@code ui.paint}、
     * 组件层 {@code ui.control}、DOM 层 {@code ui.dom}（含 {@code ElementNode}）。
     *
     * <p>原先这里是一条「paint 包不得 import ElementNode」的基线扫描，但被扫的包与被抓的
     * 类都已不存在，规则退化为 ∅⊆∅（把包名字符串改成任意假串仍全绿）。现改成**反向守卫**：
     * 目录一旦复生即红，这才是那句话现在唯一还拦得住的东西。</p>
     */
    private static final String[] DELETED_PACKAGE_DIRS = {
            "club/heiqi/uilib/ui/paint",
            "club/heiqi/uilib/ui/control",
            "club/heiqi/uilib/ui/dom",
    };

    /** 匹配对控件层（{@code ui.control}）的静态 import。 */
    private static final Pattern IMPORT_UI_CONTROL =
            Pattern.compile("\\bimport\\s+club\\.heiqi\\.uilib\\.ui\\.control\\.");

    /** 字体引擎包相对路径片段（含全部子包）。 */
    private static final String FONT_PACKAGE = "club/heiqi/uilib/font/";

    /** 匹配字体层对 UI 文本门面包（{@code ui.text}）的 import。 */
    private static final Pattern IMPORT_UI_TEXT =
            Pattern.compile("\\bimport\\s+club\\.heiqi\\.uilib\\.ui\\.text\\.");

    /**
     * 跨顶层轴包环（font/layout ↔ ui/text）反向边冻结基线（2026-09-01 布局绘制防屎山
     * 审查 D-6 裁定：不立项物理断环，只冻结增量）。
     *
     * <p>环成因：{@code TextContentMode / TextMeasureStyle / TextLinkRegion} 三个纯值词汇
     * 类型住在上层 ui.text，font 层两个文件被迫反向 import。三类型均在
     * v4.x-LTS-稳定API清单点名 ✅，换包=breaking，物理断环留 5.x 窗口。</p>
     */
    private static final Set<String> KNOWN_FONT_TO_UITEXT_BASELINE = new TreeSet<String>(Arrays.asList(
            "TextLayoutService.java", "DefaultFontRendererAdapter.java"));

    /**
     * 守护 I6 依赖方向：数据层（响应式）不得静态 import 控件层（{@code ui.control}）。
     *
     * <p>旧组件层与 DOM 层已删除；保留数据层反向 import 守线，避免响应式基础设施穿透控件层。</p>
     */
    @Test
    public void shouldKeepComponentReactiveDomLayersFreeOfControlLayerImports() throws IOException {
        List<String> violations = new ArrayList<String>();
        violations.addAll(collectImportViolations(REACTIVE_PACKAGE, IMPORT_UI_CONTROL));

        Assert.assertTrue(
                "数据层不得 import 控件层 ui.control（I6 依赖方向）：" + violations,
                violations.isEmpty());
    }

    /**
     * 反向守卫：已裁撤的三个分层包不得复生（2026-09-01 裁定：Grid 与旧组件/DOM/绘制三层删除）。
     *
     * <p>取代原来的「paint 包 import ElementNode 基线」—— 那两个名字都不存在了，规则只能在
     * ∅ 上打转；目录存在性是一条**今天就会红**的断言。</p>
     */
    @Test
    public void shouldKeepRevokedUiLayerPackagesDeleted() {
        List<String> resurrected = new ArrayList<String>();
        for (String packageDir : DELETED_PACKAGE_DIRS) {
            Path dir = MAIN_SOURCE_ROOT.resolve(packageDir);
            if (Files.exists(dir)) {
                resurrected.add(packageDir + " -> " + dir);
            }
        }
        Assert.assertTrue(
                "已裁撤的分层包复生（Grid/旧组件层/DOM 层/旧绘制层已按 2026-09-01 裁定删除，"
                        + "要复活请先经该裁定的复审窗口）：" + resurrected,
                resurrected.isEmpty());
    }

    /**
     * 反空跑地板：本类每条规则都必须真的扫到文件，否则「零违反」只是没扫到东西。
     *
     * <p>{@link #listJavaFiles(String)} 对不存在的路径返回空列表而不报错，因此任何一条包路径
     * 拼错都会让上面的扫描规则静默变成 ∅⊆∅（本项目已真实踩过一次）。下界取当前实测规模的一半
     * 左右（{@code font} 95 个、{@code ui/reactive} 8 个源文件），只防「路径失效」，不防重构增减。</p>
     */
    @Test
    public void scanHarnessMustSeeNonEmptyPopulations() throws IOException {
        int fontFiles = listJavaFiles(FONT_PACKAGE).size();
        Assert.assertTrue("font 包扫描集为空说明路径失效（实测 95 个源文件）：" + fontFiles, fontFiles >= 40);

        int reactiveFiles = listJavaFiles(REACTIVE_PACKAGE).size();
        Assert.assertTrue("ui/reactive 扫描集为空说明路径失效（实测 8 个源文件）：" + reactiveFiles,
                reactiveFiles >= 5);

        Assert.assertTrue("主源码根本身必须存在：" + MAIN_SOURCE_ROOT.toAbsolutePath(),
                Files.isDirectory(MAIN_SOURCE_ROOT));
    }

    /**
     * 冻结 font→ui.text 跨轴环增量（D-6 裁定：环未产生缺陷不立项断环，但禁止再长大）。
     *
     * <p>断言「精确相等」而非「不超过基线」：既阻断 font 层新增对 ui.text 的反向 import
     * （静默加环边），也在提纯发生时提示下收基线。5.x breaking 窗口物理断环（值对象
     * 换包）落地后，本基线应清零并保留断言作长期回归锁。</p>
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void shouldFreezeFontLayerToUiTextReverseImportBaseline() throws IOException {
        Set<String> actual = collectFileNamesMatching(FONT_PACKAGE, IMPORT_UI_TEXT);

        Set<String> newlyIntroduced = new TreeSet<String>(actual);
        newlyIntroduced.removeAll(KNOWN_FONT_TO_UITEXT_BASELINE);
        Assert.assertTrue(
                "font 层新增对 ui.text 的 import（LTS 锁定值词汇，全仓唯一跨顶层轴包环边），禁止扩散；"
                        + "确需引入请先经 5.x breaking 窗口换包裁决：" + newlyIntroduced,
                newlyIntroduced.isEmpty());

        Set<String> alreadyPurified = new TreeSet<String>(KNOWN_FONT_TO_UITEXT_BASELINE);
        alreadyPurified.removeAll(actual);
        Assert.assertTrue(
                "以下 font 文件已不再反向 import ui.text，请从 KNOWN_FONT_TO_UITEXT_BASELINE 基线移除以向下收敛："
                        + alreadyPurified,
                alreadyPurified.isEmpty());
    }

    /**
     * 收集指定包内匹配给定 import 模式的违反项（含文件相对路径与行号）。
     *
     * @param packageFragment 包相对路径片段（正斜杠结尾）
     * @param pattern         需要禁止的 import 模式
     * @return 违反项描述列表；无违反时为空列表
     * @throws IOException 读取源码失败时抛出
     */
    private static List<String> collectImportViolations(String packageFragment, Pattern pattern) throws IOException {
        List<String> violations = new ArrayList<String>();
        for (Path javaFile : listJavaFiles(packageFragment)) {
            List<String> lines = Files.readAllLines(javaFile, StandardCharsets.UTF_8);
            for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                String line = lines.get(lineIndex);
                if (pattern.matcher(line).find()) {
                    violations.add(normalizePath(javaFile) + ":" + (lineIndex + 1) + " " + line.trim());
                }
            }
        }
        return violations;
    }

    /**
     * 收集指定包内命中给定模式的文件名集合（仅文件名，便于与基线集合比对）。
     *
     * @param packageFragment 包相对路径片段（正斜杠结尾）
     * @param pattern         需要匹配的 import 模式
     * @return 命中文件的简单文件名集合（已排序）
     * @throws IOException 读取源码失败时抛出
     */
    private static Set<String> collectFileNamesMatching(String packageFragment, Pattern pattern) throws IOException {
        Set<String> matched = new TreeSet<String>();
        for (Path javaFile : listJavaFiles(packageFragment)) {
            List<String> lines = Files.readAllLines(javaFile, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (pattern.matcher(line).find()) {
                    matched.add(javaFile.getFileName().toString());
                    break;
                }
            }
        }
        return matched;
    }

    /**
     * 列出指定包相对路径下的全部 {@code .java} 源文件。
     *
     * @param packageFragment 包相对路径片段（正斜杠结尾）
     * @return 该包下的 Java 源文件列表
     * @throws IOException 遍历目录失败时抛出
     */
    private static List<Path> listJavaFiles(String packageFragment) throws IOException {
        List<Path> javaFiles = new ArrayList<Path>();
        try (Stream<Path> sourcePaths = Files.walk(MAIN_SOURCE_ROOT)) {
            sourcePaths
                    .filter(sourcePath -> sourcePath.toString().endsWith(".java"))
                    .filter(sourcePath -> normalizePath(sourcePath).contains(packageFragment))
                    .forEach(javaFiles::add);
        }
        return javaFiles;
    }

    /**
     * 把路径统一规范为正斜杠分隔，消除 Windows / *nix 分隔符差异。
     *
     * @param path 原始路径
     * @return 正斜杠分隔的路径字符串
     */
    private static String normalizePath(Path path) {
        return path.toString().replace('\\', '/');
    }
}
