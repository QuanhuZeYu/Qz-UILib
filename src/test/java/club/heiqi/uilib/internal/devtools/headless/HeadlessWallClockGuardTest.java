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
 * headless 出图「与真实墙钟解耦」的源码门禁。
 *
 * <h3>它守什么</h3>
 * <p>判据：headless 生产包内不得出现 {@code System.currentTimeMillis()}。出图的每一处时间事实
 * （消息到达时刻、帧时钟起点）都必须来自 {@link HeadlessRequest#clockMillis()}。</p>
 *
 * <p>违反后的实测症状：聊天组头时间戳取「消息到达时刻」格式化出的 {@code HH:mm}，而到达时刻若取
 * 进程当前时刻，则<b>同一命令在不同分钟出图必然像素不同</b>（实测跨分钟差异 9590 px，集中在组头
 * 时间戳及相邻区），"改一行 → 出图对拍"这条主用途随之失效。</p>
 *
 * <p>{@code System.nanoTime()} 不受本门禁限制：它只用于耗时度量（{@code elapsedMillis} / 帧时间预算），
 * 不进入任何渲染输入。</p>
 */
public class HeadlessWallClockGuardTest {

    /** headless 生产包（相对 {@code src/main/java}）。 */
    private static final Path PACKAGE_ROOT =
            Paths.get("src/main/java/club/heiqi/uilib/internal/devtools/headless");

    @Test
    public void headlessSourcesNeverReadTheWallClock() throws Exception {
        List<String> offenders = new ArrayList<String>();
        try (Stream<Path> files = Files.walk(PACKAGE_ROOT)) {
            for (Path file : (Iterable<Path>) files.filter(path -> path.toString().endsWith(".java"))::iterator) {
                String code = stripComments(source(file));
                int hits = occurrences(code, "System.currentTimeMillis");
                if (hits == 0) {
                    continue;
                }
                offenders.add(file.getFileName() + " x" + hits);
            }
        }
        assertTrue("headless 出图必须与真实墙钟解耦：时间事实只允许来自 HeadlessRequest#clockMillis()，"
                + "违规文件：" + offenders, offenders.isEmpty());
    }

    /**
     * 正锚：先证明"读到了真的探针源码"，再让上面的空集断言有意义。
     *
     * <p>没有本方法时，包路径写错（{@code Files.walk} 抛异常除外）或后缀写错都会让门禁静默全绿。</p>
     */
    @Test
    public void guardActuallySeesTheInjectedClock() throws Exception {
        for (String name : new String[] {"ChatSceneProbeHost.java", "HudSceneProbeHost.java"}) {
            String code = stripComments(source(PACKAGE_ROOT.resolve(name)));
            assertTrue(name + " 必须从请求注入虚拟墙钟（构造参数）", code.contains("long clockMillis"));
            assertTrue(name + " 必须把注入值作为帧时钟起点", code.contains("this.clockMillis = clockMillis"));
            assertTrue(name + " 必须用注入基准写入消息到达时刻（不得走二参 append）",
                    code.contains("new ChatLineRecord(component, messageId++, clockMillis)"));
        }
        String request = stripComments(source(PACKAGE_ROOT.resolve("HeadlessRequest.java")));
        assertTrue("请求必须提供默认基准常量", request.contains("DEFAULT_CLOCK_MILLIS"));
        assertTrue("请求必须暴露基准访问器", request.contains("public long clockMillis()"));
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
