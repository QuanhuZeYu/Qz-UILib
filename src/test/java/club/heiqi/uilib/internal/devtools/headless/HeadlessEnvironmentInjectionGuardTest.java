package club.heiqi.uilib.internal.devtools.headless;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.Test;

/**
 * headless 环境注入的源码门禁：只留<b>禁则</b>。
 *
 * <h3>它守什么</h3>
 * <p>判据：headless 生产包内不得回落生产环境单例（{@code SceneHostAssembly.defaultEnvironment()} /
 * {@code ProcessUiEnvironment}）。</p>
 *
 * <p>违反后的实测症状：诊断开关恒取 {@code Config.useDebug}（缺省 false），于是 {@code --debug}
 * 是一句空话 —— 采样器永远打不开、帧内事实永远是空的，而命令本身<b>不报错</b>。这类「接口在、
 * 接线断」的静默失效在行为测试里很难覆盖（需要真 GL 上下文），故用禁则钉住。</p>
 *
 * <h3>为什么删掉「请求 → 环境 → 各页面宿主」的接线存在性检查</h3>
 * <p>那组检查是 18 条源码串 {@code contains("...")} 快照（如
 * {@code session.contains(".fontScale(scalePercent)")}、{@code cli.contains("\"-pg\" + pageName")}）。
 * 它把「实现怎么写」抄进了测试：换个等价的局部变量名就红，而真正的回归（参数收下了但没接线）
 * 与「接线了但写法不同」在字符串层面不可区分。接线的强制手段改为<b>构造依赖</b>：环境事实由
 * {@code HeadlessEnvironment.of(request.diagnostics())} 构造，各宿主把 {@code UiEnvironment}
 * 作为构造参数（不提供单参构造），漏接即编译失败。</p>
 *
 * <p>「参数是否真的生效」是<b>运行态事实</b>（{@code --font-scale=150} 必须出一张与 100% 不同的
 * 图），由出图验收矩阵承担（见 {@code docs/使用文档/headless出图指南.md}），不由单测的字符串比对冒充。</p>
 */
public class HeadlessEnvironmentInjectionGuardTest {

    /** headless 生产包（相对 {@code src/main/java}）。 */
    private static final Path PACKAGE_ROOT =
            Paths.get("src/main/java/club/heiqi/uilib/internal/devtools/headless");

    @Test
    public void headlessSourcesNeverFallBackToTheProcessEnvironment() throws Exception {
        List<String> scanned = new ArrayList<String>();
        List<String> offenders = new ArrayList<String>();
        try (Stream<Path> files = Files.walk(PACKAGE_ROOT)) {
            for (Path file : (Iterable<Path>) files.filter(path -> path.toString().endsWith(".java"))::iterator) {
                String code = stripComments(source(file));
                scanned.add(file.getFileName().toString());
                for (String needle : new String[] {"defaultEnvironment", "ProcessUiEnvironment"}) {
                    int hits = occurrences(code, needle);
                    if (hits > 0) {
                        offenders.add(file.getFileName() + " " + needle + " x" + hits);
                    }
                }
            }
        }
        // 正锚：扫描范围必须真的覆盖到本包源码，否则路径写错时下面的空集断言会静默全绿。
        assertTrue("扫描范围异常：本包至少有 10 个源文件，实际 " + scanned.size() + " -> " + scanned,
                scanned.size() >= 10);
        assertTrue("环境事实必须来自请求（HeadlessEnvironment），不得回落生产环境单例：" + offenders,
                offenders.isEmpty());
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