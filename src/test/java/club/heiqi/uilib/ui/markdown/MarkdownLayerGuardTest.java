package club.heiqi.uilib.ui.markdown;

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
 * markdown 分层硬约束守卫（规划-通用Markdown渲染器 §四 G1/G2；M6 的 G3 复生锁不在本期）。
 *
 * <p>与 {@code UiHudRenderListenerGlFenceTest} / {@code LayerContractGuardTest} 同族做法：
 * 反向锁 + 正对照 + 扫描集规模地板，防「扫到 ∅ 仍全绿」的仪器空跑——每条禁扫都配一条
 * 已知含目标 token 的兄弟包正对照，同一扫描器必须报出真实命中，否则该锁无意义。</p>
 */
public class MarkdownLayerGuardTest {

    /** 主源码根。 */
    private static final Path MAIN_SOURCE_ROOT = Paths.get("src/main/java");

    /** L1 解析层包路径（正斜杠，扫描片段用）。 */
    private static final String L1_PACKAGE = "club/heiqi/uilib/font/layout/markdown/";

    /** L2 绘制层包路径。 */
    private static final String L2_PACKAGE = "club/heiqi/uilib/ui/markdown/";

    /**
     * G1 正对照（GL11 真实使用者）：{@code ui/render}；同一扫描器对其计数必须 &gt;30，
     * 否则「两 markdown 包 0 命中」是空跑。
     *
     * <p><b>计数口径统一申明（M5；规划 §二之四「同一把尺没统一」的收口）：本文件与
     * {@code UiHudRenderListenerGlFenceTest} 一律按「出现次数」计</b>——逐行内
     * {@code indexOf} 循环计数，注释行同样计入（{@code countToken} 无注释过滤）。
     * 历史「559 / 386」两个数不是两把尺打架，而是两种口径：559 = 出现次数（本文件口径，
     * {@code ui/render} 现值），386 = 按行去重（父代理 M3 复核的另一算法）。自本注释起
     * 门禁只承认出现次数口径，改动扫描器口径必须同步改本申明。</p>
     */
    private static final String GL11_CONTROL_PACKAGE = "club/heiqi/uilib/ui/render/";

    /**
     * G1 对照命中地板（规划 §四 + M3 任务书：&gt;30；M5 按统一口径实测 {@code ui/render}
     * = 559 次，地板取 100 —— 远高于 30 且对重构留裕量）。
     */
    private static final int GL11_CONTROL_MIN_HITS = 100;

    /** G2 正对照一：client 包 import net.minecraft/cpw.mods（实测 10 处）。 */
    private static final String MC_IMPORT_CONTROL_PACKAGE = "club/heiqi/uilib/client/";

    /** G2 正对照二：font/layout 父包 import java.awt（TextLayoutService 等，实测 &gt;0 文件）。 */
    private static final String AWT_CONTROL_PACKAGE = "club/heiqi/uilib/font/layout/";

    /** G2 禁止的 import（L1 纯 JVM 零 MC 依赖锁 + L2 不直连 GL/AWT 位图面）。
     *  行首锚定：只抓真 import 语句，javadoc 里「不 import net.minecraft」这类自述文字
     *  不算违反（M2 复核口径为「全包 import 仅 java/club」，扫的也是语句行）。 */
    private static final Pattern FORBIDDEN_IMPORT = Pattern.compile(
            "^\\s*import\\s+(static\\s+)?(net\\.minecraft|cpw\\.mods|java\\.awt)\\b");

    /** 公共面探测：顶层 public 类型声明（每个源文件至多一个）。 */
    private static final Pattern PUBLIC_TOP_LEVEL = Pattern.compile(
            "^public\\s+(final\\s+)?(class|interface|enum|@interface)\\b");

    // ==================== G1：GL11. 计数为 0 ====================

    /** G1 锁：L1 与 L2 包内 {@code GL11.} 出现次数恒为 0（绘制走 PaintCommand/段流契约）。 */
    @Test
    public void markdownPackagesMustNeverTouchGl11() throws IOException {
        int l1Files = countJavaFiles(L1_PACKAGE);
        int l2Files = countJavaFiles(L2_PACKAGE);
        Assert.assertTrue("L1 扫描集规模地板（实测 6 文件，路径失效即红）: " + l1Files, l1Files >= 4);
        Assert.assertTrue("L2 扫描集规模地板（实测 2 文件，路径失效即红）: " + l2Files, l2Files >= 2);
        Assert.assertEquals("font/layout/markdown 不得出现 GL11. 调用", 0,
                countToken(L1_PACKAGE, "GL11."));
        Assert.assertEquals("ui/markdown 不得出现 GL11. 调用", 0, countToken(L2_PACKAGE, "GL11."));
    }

    /** G1 反空跑地板：同一扫描器对已知 GL11 大户（ui/render）必须报出 &gt;30 真实命中。 */
    @Test
    public void gl11ScannerMustDetectRealUsageInControlPackage() throws IOException {
        int controlHits = countToken(GL11_CONTROL_PACKAGE, "GL11.");
        Assert.assertTrue("GL11 扫描器在正对照包命中 " + controlHits + " 处（地板 "
                + GL11_CONTROL_MIN_HITS + "，&gt;30 即证明扫描器不是空跑；为 0 说明扫描器失效）",
                controlHits > 30 && controlHits >= GL11_CONTROL_MIN_HITS);
    }

    // ==================== G2：零 MC / AWT import ====================

    /** G2 锁：L1 与 L2 不得 import net.minecraft / cpw.mods / java.awt。 */
    @Test
    public void markdownPackagesMustStayPureJvm() throws IOException {
        List<Path> l1Files = listJavaFiles(L1_PACKAGE);
        Assert.assertTrue("T1 新公共接缝必须明确进入 G2 同一扫描集: " + l1Files,
                l1Files.contains(MAIN_SOURCE_ROOT.resolve(L1_PACKAGE + "MarkdownTableModel.java")));
        Assert.assertTrue("L1 包 import 违反项: " + collectForbidden(L1_PACKAGE, FORBIDDEN_IMPORT),
                collectForbidden(L1_PACKAGE, FORBIDDEN_IMPORT).isEmpty());
        Assert.assertTrue("L2 包 import 违反项: " + collectForbidden(L2_PACKAGE, FORBIDDEN_IMPORT),
                collectForbidden(L2_PACKAGE, FORBIDDEN_IMPORT).isEmpty());
    }

    /** G2 反空跑地板+正对照：同一扫描器对 client 包必须报出 ≥1 个 MC/Forge import 文件。 */
    @Test
    public void forbiddenImportScannerMustSeeRealHitsInControlPackages() throws IOException {
        List<String> mcHits = collectForbidden(MC_IMPORT_CONTROL_PACKAGE, MC_ONLY_IMPORT);
        Assert.assertTrue("import 扫描器在 client 正对照包必须命中 ≥1 文件（实测 " + mcHits.size()
                + "；0 = 扫描器或路径失效，G2 锁即成摆设）", mcHits.size() >= 1);
        List<String> awtHits = collectForbidden(AWT_CONTROL_PACKAGE, AWT_ONLY_IMPORT);
        Assert.assertTrue("import 扫描器在 font/layout 的 java.awt 正对照包必须命中 ≥1 文件（实测 "
                + awtHits.size() + "）", awtHits.size() >= 1);
    }

    /** MC-only 正对照模式（同行首锚定口径）。 */
    private static final Pattern MC_ONLY_IMPORT = Pattern.compile(
            "^\\s*import\\s+(static\\s+)?(net\\.minecraft|cpw\\.mods)\\b");

    /** AWT-only 正对照模式（同行首锚定口径）。 */
    private static final Pattern AWT_ONLY_IMPORT = Pattern.compile(
            "^\\s*import\\s+(static\\s+)?java\\.awt\\b");

    // ==================== 公共面最小锁（M3 门面唯一性） ====================

    /** L2 包公共面地板+上限：顶层 public 类型必须恰为 1 个（MarkdownPainter 门面）。 */
    @Test
    public void l2PackageMustExposeExactlyOnePublicType() throws IOException {
        List<String> publicTypes = new ArrayList<String>();
        for (Path file : listJavaFiles(L2_PACKAGE)) {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (PUBLIC_TOP_LEVEL.matcher(line).find()) {
                    publicTypes.add(file.getFileName().toString());
                }
            }
        }
        Assert.assertEquals("ui/markdown 顶层公共类型必须恰为 1（门面最小面裁定，加面须独立裁）: "
                + publicTypes, 1, publicTypes.size());
        Assert.assertTrue("且必须是 MarkdownPainter: " + publicTypes,
                publicTypes.contains("MarkdownPainter.java"));
    }

    // ==================== 扫描器 ====================

    /** 统计包内字符串 token 出现次数（按出现次数口径、含注释行；与 UiHudRenderListenerGlFenceTest
     *  的 occurrences 及本类 {@link #GL11_CONTROL_PACKAGE} 注释口径申明一致）。 */
    private static int countToken(String packageFragment, String token) throws IOException {
        int count = 0;
        for (Path file : listJavaFiles(packageFragment)) {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                int index = 0;
                while ((index = line.indexOf(token, index)) >= 0) {
                    count++;
                    index += token.length();
                }
            }
        }
        return count;
    }

    /** 收集包内命中模式的违反项（文件:行号 + 原文）。 */
    private static List<String> collectForbidden(String packageFragment, Pattern pattern) throws IOException {
        List<String> violations = new ArrayList<String>();
        for (Path file : listJavaFiles(packageFragment)) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                if (pattern.matcher(lines.get(i)).find()) {
                    violations.add(normalizePath(file) + ":" + (i + 1) + " " + lines.get(i).trim());
                }
            }
        }
        return violations;
    }

    private static int countJavaFiles(String packageFragment) throws IOException {
        return listJavaFiles(packageFragment).size();
    }

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

    private static String normalizePath(Path path) {
        return path.toString().replace('\\', '/');
    }
}
