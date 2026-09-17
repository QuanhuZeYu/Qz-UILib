package club.heiqi.uilib.ui.env;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
 * 调试开关「单源」源码门禁。
 *
 * <h3>它守什么</h3>
 * <p>生产源码中直读 {@code Config.useDebug} / {@code Config.uiDebug} 的位置只允许两类：
 * <b>环境适配器</b>（{@link ProcessUiEnvironment}，把静态字段转发成诊断域的值读）与
 * <b>配置回灌</b>（{@code ConfigValueBridge.applyGeneral}，写完字段后投影订阅通道）。</p>
 *
 * <p>任何第三处直读都意味着采样/显隐出现了第二个开关源：改配置只影响其中一处，会得到
 * 「有会话无计数」（帧管线开、采样器关）或「开关变了调试浮层不动」的半开态——这正是本批次
 * 收敛掉的缺陷。此门禁把「只有一个源」变成机器可核的事实，而不是靠约定。</p>
 *
 * <h3>为什么剥离注释</h3>
 * <p>本仓大量注释在解释"旧实现曾直读 {@code Config.useDebug}"，那是需要保留的历史信息。
 * 门禁只看<b>可执行代码</b>，故先按状态机剥离行注释与块注释。</p>
 */
public class DiagnosticsSourceGuardTest {

    /** 允许直读调试开关的生产文件（相对 {@code src/main/java}，正斜杠）。 */
    private static final List<String> ALLOWED = Collections.unmodifiableList(Arrays.asList(
            "club/heiqi/uilib/ui/env/ProcessUiEnvironment.java",
            "club/heiqi/uilib/config/modern/ConfigValueBridge.java"));

    private static final Path SOURCE_ROOT = Paths.get("src/main/java");

    @Test
    public void debugSwitchesAreReadOnlyByTheAdapterAndTheSingleWriteEntry() throws Exception {
        List<String> offenders = new ArrayList<String>();
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            for (Path file : (Iterable<Path>) files.filter(path -> path.toString().endsWith(".java"))::iterator) {
                String code = stripComments(source(file));
                int hits = occurrences(code, "Config.useDebug") + occurrences(code, "Config.uiDebug");
                if (hits == 0) {
                    continue;
                }
                String relative = SOURCE_ROOT.relativize(file).toString().replace('\\', '/');
                if (!ALLOWED.contains(relative)) {
                    offenders.add(relative + " x" + hits);
                }
            }
        }
        assertTrue("调试开关出现了第二个读取源（应当只经诊断域环境端口）：" + offenders,
                offenders.isEmpty());
    }

    /**
     * 正锚：先证明"读到了真的生产源码"，再让上面的空集断言有意义。
     *
     * <p>没有本方法时，把扫描路径或后缀写错（例如 {@code src/main/java} 写成相对 cwd 的错误路径，
     * {@code Files.walk} 直接抛异常除外——但也可能是文件后缀写死错导致全空）会让门禁静默全绿。</p>
     */
    @Test
    public void guardActuallySeesBothSanctionedReads() throws Exception {
        String adapter = stripComments(source(SOURCE_ROOT.resolve(ALLOWED.get(0))));
        String bridge = stripComments(source(SOURCE_ROOT.resolve(ALLOWED.get(1))));
        assertEquals("环境适配器必须同时转发两个调试开关（值读权威）", 2,
                occurrences(adapter, "Config.useDebug") + occurrences(adapter, "Config.uiDebug"));
        // 三处 = 两个字段各写一次（值读权威）+ publish 处读一次（订阅投影的入参）
        assertEquals("配置回灌必须写完两个字段并投影订阅通道（唯一写入口）", 3,
                occurrences(bridge, "Config.useDebug") + occurrences(bridge, "Config.uiDebug"));
    }

    /**
     * 调试浮层的消费方必须<b>订阅</b>通道，而不是把开关包成 {@code Computed} 快照。
     *
     * <p>这是仓库内实测过的事故形态：{@code Computed.create(() -> Config.uiDebug)} 没有任何依赖，
     * 首次 flush 后永不重算 —— 关闭再打开，调试浮层再也不出现（静默失效）。
     * {@link #debugSwitchesAreReadOnlyByTheAdapterAndTheSingleWriteEntry} 已从"不得直读"侧守住它，
     * 本方法再从"必须订阅"侧给一条正锚，避免把消费点改成别的等价快照形态后无人察觉。</p>
     */
    @Test
    public void debugOverlayConsumerSubscribesToTheChannel() throws Exception {
        String listener = stripComments(source(Paths.get(
                "src/main/java/club/heiqi/uilib/client/UiHudRenderListener.java")));
        assertTrue("调试浮层消费方必须订阅诊断域通道",
                listener.contains("rt.environment().diagnostics().debugOverlayChanges()"));
        assertFalse("消费方不得回退成 Computed 快照", listener.contains("Computed.create(() -> Config"));
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
