package club.heiqi.config.ui.field;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;

/**
 * 候选源调用点源码守卫（S-10：<b>白名单调用点 + 反向断言</b>，不把字符串扫描当唯一守卫）。
 *
 * <p>契约出处：{@code team/P0-ADR-契约与测量.md} §1.3。真正的守卫是运行期
 * {@link PickerSourceGuard#requireMainThread(String)} fail-fast（{@code PickerSourceGuardTest} /
 * {@code SearchPickerFieldSupportSpiPathTest} 已覆盖）；本测试补两条静态钉子：</p>
 * <ol>
 *   <li><b>白名单</b>：{@code page(}/{@code matchCount(}/{@code exact(} 只允许出现在固定调用点集合内
 *       （ADR 列出的合法集合 = PickerRevisionBridge、面板内容 Owner 内的窗口 Computed、
 *       SearchPickerFieldSupport 的成员解析；面板侧窗口 Computed 属 P3，届时在此显式登记）；</li>
 *   <li><b>反向断言</b>：白名单文件内不得出现线程/执行器构造，且必须出现线程断言调用
 *       （防止「把调用点搬进 Runnable/lambda 逃逸」的形态静默通过）。</li>
 * </ol>
 */
public class PickerSourceCallSiteGuardTest {

    private static final Path MAIN_ROOT = Paths.get("src/main/java");

    /** 允许出现 page/matchCount/exact 的文件（P3 新增面板窗口 Computed 时必须显式登记）。 */
    private static final Map<String, String> QUERY_CALL_SITES = queryCallSites();

    /** 允许出现 version()/onEnvironmentChanged() 的文件。 */
    private static final Map<String, String> REVISION_CALL_SITES = revisionCallSites();

    private static Map<String, String> queryCallSites() {
        Map<String, String> map = new LinkedHashMap<String, String>();
        map.put("SearchPickerFieldSupport.java", "字段侧成员解析（exact）与查询条件构造");
        map.put("ScenePickerPanel.java", "面板内容 Owner 内的窗口切片消费者（ADR §3.2：pageProvider 闭包 = "
                + "size/matchCount/page/exact 的唯一窗口求值点；P4 从字段侧迁入）");
        return map;
    }

    private static Map<String, String> revisionCallSites() {
        Map<String, String> map = new LinkedHashMap<String, String>();
        map.put("PickerRevisionBridge.java", "唯一「推/拉」桥：version() 读取与 onEnvironmentChanged 下行");
        return map;
    }

    /** 正锚 + 白名单：查询方法只出现在登记过的调用点。 */
    @Test
    public void queryMethodsAppearOnlyAtWhitelistedCallSites() throws Exception {
        List<String> offenders = new ArrayList<String>();
        List<String> seen = new ArrayList<String>();
        for (Path file : mainSources()) {
            String code = codeWithoutComments(read(file));
            if (touchesCandidateSource(code)) {
                String name = file.getFileName().toString();
                seen.add(name);
                if (!QUERY_CALL_SITES.containsKey(name)) {
                    offenders.add(name);
                }
            }
        }
        Assert.assertEquals("白名单必须恰为已登记调用点（新增即红，须显式登记并说明理由）",
                new ArrayList<String>(QUERY_CALL_SITES.keySet()), seen);
        Assert.assertEquals("出现未登记的查询调用点", new ArrayList<String>(), offenders);
    }

    /**
     * 是否<strong>直接</strong>触及候选源查询面：文件内出现 {@code PickerCandidateSource} 类型
     * 且出现 {@code .page(}/{@code .matchCount(}/{@code .exact(} 之一。
     *
     * <p>P3 修订（窗口化内核）：{@code SearchResultList} 新增的 {@code WindowRequest/WindowPage/PageProvider}
     * 是<strong>控件与宿主之间的窗口切片回调</strong>（{@code props.pageProvider().page(req)}），既不持有也不
     * 调用 {@code PickerCandidateSource}（控件层依赖方向不允许）；旧判定把任何 {@code .page(} 回调都算作
     * 候选源调用点，会把「窗口 Computed 的消费形态」误判为未登记调用点。收窄到「同文件确实引用候选源类型」
     * 既保住白名单强度（真正的候选源调用仍必须登记），又不误伤 UI 侧回调。</p>
     */
    private static boolean touchesCandidateSource(String code) {
        if (!code.contains("PickerCandidateSource")) {
            return false;
        }
        return code.contains(".page(") || code.contains(".matchCount(") || code.contains(".exact(");
    }

    /** 正锚 + 白名单：版本读取与环境下行只出现在桥内。 */
    @Test
    public void revisionMethodsAppearOnlyInBridge() throws Exception {
        List<String> offenders = new ArrayList<String>();
        List<String> seen = new ArrayList<String>();
        for (Path file : mainSources()) {
            String code = codeWithoutComments(read(file));
            if (code.contains(".version()") || code.contains(".onEnvironmentChanged(")) {
                String name = file.getFileName().toString();
                seen.add(name);
                if (!REVISION_CALL_SITES.containsKey(name)) {
                    offenders.add(name);
                }
            }
        }
        Assert.assertEquals("版本通道白名单必须恰为桥（新增即红）",
                new ArrayList<String>(REVISION_CALL_SITES.keySet()), seen);
        Assert.assertEquals("出现未登记的版本通道调用点", new ArrayList<String>(), offenders);
    }

    /** 反向断言：调用点不得处于线程/执行器上下文，且必须携带运行期线程断言。 */
    @Test
    public void callSitesAreNotInThreadContextsAndCarryRuntimeAssertion() throws Exception {
        // 只列「构造线程/执行器上下文」的形态：Runnable/lambda 是正常回调参数类型，不构成执行上下文。
        List<String> threadTokens = Arrays.asList("new Thread(", "Executors.", "CompletableFuture",
                "ThreadPoolExecutor", "ForkJoinPool", ".submit(");
        List<String> callSiteFiles = new ArrayList<String>(QUERY_CALL_SITES.keySet());
        callSiteFiles.addAll(REVISION_CALL_SITES.keySet());
        for (String name : callSiteFiles) {
            Path file = findMainSource(name);
            String code = codeWithoutComments(read(file));
            for (String token : threadTokens) {
                Assert.assertFalse(name + " 不得在调用点构造线程/执行器上下文（token=" + token + "）",
                        code.contains(token));
            }
            Assert.assertTrue(name + " 必须携带运行期主线程断言 PickerSourceGuard.requireMainThread",
                    code.contains("PickerSourceGuard.requireMainThread")
                            || code.contains("PickerSourceGuard."));
        }
    }

    // ==================== 源码读取助手 ====================

    private static List<Path> mainSources() throws IOException {
        final List<Path> files = new ArrayList<Path>();
        try (Stream<Path> stream = Files.walk(MAIN_ROOT)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(files::add);
        }
        Assert.assertTrue("必须真读到 main 源码（先有正锚，负向清单才有意义）", files.size() > 100);
        return files;
    }

    private static Path findMainSource(String fileName) throws IOException {
        for (Path file : mainSources()) {
            if (file.getFileName().toString().equals(fileName)) {
                return file;
            }
        }
        throw new AssertionError("找不到源码文件：" + fileName);
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /** 剥离注释（守卫只对真实代码生效）。 */
    private static String codeWithoutComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int index = 0;
        int length = source.length();
        while (index < length) {
            char current = source.charAt(index);
            if (current == '/' && index + 1 < length && source.charAt(index + 1) == '/') {
                while (index < length && source.charAt(index) != '\n') {
                    index++;
                }
            } else if (current == '/' && index + 1 < length && source.charAt(index + 1) == '*') {
                index += 2;
                while (index + 1 < length && !(source.charAt(index) == '*' && source.charAt(index + 1) == '/')) {
                    index++;
                }
                index += 2;
            } else if (current == '"') {
                out.append(current);
                index++;
                while (index < length && source.charAt(index) != '"') {
                    if (source.charAt(index) == '\\') {
                        out.append(source.charAt(index));
                        index++;
                    }
                    if (index < length) {
                        out.append(source.charAt(index));
                        index++;
                    }
                }
                if (index < length) {
                    out.append(source.charAt(index));
                    index++;
                }
            } else {
                out.append(current);
                index++;
            }
        }
        return out.toString();
    }
}
