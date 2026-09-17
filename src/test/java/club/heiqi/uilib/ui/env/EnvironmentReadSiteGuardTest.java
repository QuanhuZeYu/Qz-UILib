package club.heiqi.uilib.ui.env;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.junit.Test;

/**
 * 环境代际「读取点」源码门禁。
 *
 * <h3>它守什么</h3>
 * <p>生产源码中直读 {@code ResourceReloadService.getInstance()} / {@code LanguageEpochService.getInstance()}
 * 的位置只允许三类：<b>环境适配器</b>（{@link ProcessUiEnvironment}，把进程单例转发成资源 / 语言域）、
 * <b>宿主写入口</b>（{@code ClientProxy}，注册与安装这两个服务）、以及<b>两个服务自身</b>。</p>
 *
 * <p>任何第三处直读都意味着这份代际有了第二个来源：headless 出图与测试无法替换它（端口注入的意义正在于此），
 * 且「换代际 → 缓存失效」这条链会有一半走进程单例、一半走端口 —— 收不到变化的那个消费者静默用旧缓存。
 * 本轮把 {@code PickerIconResolver} / {@code PickerRevisionBridge} 从进程单例改走端口，本门禁防它回退。</p>
 *
 * <h3>为什么剥离注释</h3>
 * <p>本仓注释里保留着「原为直读 {@code ResourceReloadService}」这类历史说明，那是需要留的信息；
 * 门禁只看可执行代码。</p>
 */
public class EnvironmentReadSiteGuardTest {

    /** 允许直读两个代际单例的生产文件（相对 {@code src/main/java}，正斜杠）。 */
    private static final List<String> ALLOWED = Collections.unmodifiableList(Arrays.asList(
            "club/heiqi/uilib/ui/env/ProcessUiEnvironment.java",
            "club/heiqi/uilib/ClientProxy.java",
            "club/heiqi/uilib/resource/ResourceReloadService.java",
            "club/heiqi/uilib/i18n/LanguageEpochService.java"));

    /**
     * 代际读取点的受禁串。
     *
     * <p>第三个是<b>静态</b>探测口（{@code currentLanguageCode} 直读客户端语言）：它不走
     * {@code getInstance()}，只查后者的门禁会漏掉「绕过单例直接读静态探测」这条路径。</p>
     */
    private static final String[] FORBIDDEN = {
            "ResourceReloadService.getInstance()", "LanguageEpochService.getInstance()",
            "LanguageEpochService.currentLanguageCode"};

    private static final Path SOURCE_ROOT = Paths.get("src/main/java");

    @Test
    public void epochsAreReadOnlyByTheAdapterAndTheAssembly() throws Exception {
        List<String> offenders = new ArrayList<String>();
        int scanned = 0;
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            for (Path file : (Iterable<Path>) files.filter(path -> path.toString().endsWith(".java"))::iterator) {
                scanned++;
                String code = stripComments(source(file));
                int hits = 0;
                for (String needle : FORBIDDEN) {
                    hits += occurrences(code, needle);
                }
                if (hits == 0) {
                    continue;
                }
                String relative = SOURCE_ROOT.relativize(file).toString().replace('\\', '/');
                if (!ALLOWED.contains(relative)) {
                    offenders.add(relative + " x" + hits);
                }
            }
        }
        assertTrue("扫描范围异常：生产源码远多于 100 个文件，实际 " + scanned, scanned > 100);
        assertTrue("代际出现了第三个读取源（应当只经环境端口）：" + offenders, offenders.isEmpty());
    }

    /**
     * 正锚 + 接线存在性：白名单里的适配器必须真的在转发两个代际，装配点必须真的把环境注进去。
     *
     * <p>没有前半段，白名单文件被改空后上面的空集断言照样全绿；没有后半段，「端口在、接线断」
     * 这类静默降级（代际恒 0 ⇒ 缓存永不失效）无人察觉。</p>
     */
    @Test
    public void adapterForwardsBothEpochsAndAssemblyInjectsTheEnvironment() throws Exception {
        String adapter = stripComments(source(SOURCE_ROOT.resolve(ALLOWED.get(0))));
        // 3 处 = 资源代际 + 语言代际 + 语言码静态探测（值读与代际读各一条，见各域「值 vs 代际」语义）
        assertEquals("环境适配器必须转发资源代际、语言代际与语言码", 3,
                occurrences(adapter, "ResourceReloadService.getInstance()")
                        + occurrences(adapter, "LanguageEpochService.getInstance()")
                        + occurrences(adapter, "LanguageEpochService.currentLanguageCode"));

        String assembly = stripComments(source(Paths.get(
                "src/main/java/club/heiqi/config/ui/field/SearchPickerFieldSupport.java")));
        assertTrue("图标解析器必须拿到宿主环境的资源域",
                assembly.contains("PickerIconResolver.of(provider, rt.environment().resources())"));
        assertTrue("版本桥必须拿到宿主环境",
                assembly.contains("PickerRevisionBridge.forSource(source, rt.environment())"));
    }

    /** 读取 UTF-8 生产源码。 */
    private static String source(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /** 统计固定源码片段出现次数。 */
    private static int occurrences(String source, String needle) {
        int count = 0;
        for (int index = 0; (index = source.indexOf(needle, index)) >= 0; index += needle.length()) {
            count++;
        }
        return count;
    }

    /** 剥离行注释与块注释（保留换行以免拼接出假匹配）。 */
    private static String stripComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        boolean inBlock = false;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            if (inBlock) {
                if (current == '*' && next == '/') {
                    inBlock = false;
                    index++;
                } else if (current == '\n') {
                    out.append('\n');
                }
                continue;
            }
            if (current == '/' && next == '/') {
                while (index < source.length() && source.charAt(index) != '\n') {
                    index++;
                }
                out.append('\n');
                continue;
            }
            if (current == '/' && next == '*') {
                inBlock = true;
                index++;
                continue;
            }
            out.append(current);
        }
        return out.toString();
    }
}
