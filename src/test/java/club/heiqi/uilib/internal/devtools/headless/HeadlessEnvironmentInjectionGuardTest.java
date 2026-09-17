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
 * headless 环境注入的源码门禁。
 *
 * <h3>它守什么</h3>
 * <p>判据：headless 生产包内不得回落生产环境单例（{@code SceneHostAssembly.defaultEnvironment()} /
 * {@code ProcessUiEnvironment}），且请求声明的环境量必须真的投影到装配点与 runtime。</p>
 *
 * <p>违反后的实测症状：诊断开关恒取 {@code Config.useDebug}（缺省 false），于是 {@code --debug} 是一句
 * 空话 —— 采样器永远打不开、帧内事实永远是空的，而命令本身<b>不报错</b>。同类症状还有字号倍率：请求里
 * 有字段、会话里忘了投影，{@code --font-scale=150} 会出一张与 100% 逐像素相同的图。</p>
 *
 * <p>这两类都是「接口在、接线断」的静默失效，行为测试很难覆盖（需要真 GL 上下文），故用源码结构钉住。</p>
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

    /**
     * 接线存在性：请求 → 环境端口 → 各页面宿主，以及请求 → runtime 的字号倍率投影。
     *
     * <p>这些片段一旦被删掉，功能即静默降级为「参数收下了但没用」。</p>
     */
    @Test
    public void requestEnvironmentIsActuallyProjected() throws Exception {
        String session = stripComments(source(PACKAGE_ROOT.resolve("HeadlessSession.java")));
        assertTrue("环境必须由请求构造", session.contains("HeadlessEnvironment.of(request.diagnostics())"));
        assertTrue("字号倍率必须投影到页面 runtime", session.contains("setFontScale(request.fontScalePercent())"));
        assertTrue("帧采样会话必须表态请求声明的环境（否则 --debug 收下但不生效）",
                session.contains("environment.diagnostics()"));
        for (String name : new String[] {"ChatSceneProbeHost.java", "TextProbeHost.java", "HudSceneProbeHost.java"}) {
            String code = stripComments(source(PACKAGE_ROOT.resolve(name)));
            assertTrue(name + " 必须把环境作为构造依赖", code.contains("UiEnvironment environment"));
        }
        assertTrue("会话必须解析请求里的外观档", session.contains("HeadlessThemes.resolve(request.theme())"));
        assertTrue("playground 页必须走完整注入构造（环境 + 外观，不得回落单参构造）",
                session.contains("new TestPlaygroundHost(inputSource, environment, theme)"));

        // 外观档是「装配期」环境量：装晚了不会让已建树的配方派生重算，只会静默出一张没换过配色的图。
        for (String name : new String[] {"ChatSceneProbeHost.java", "HudSceneProbeHost.java"}) {
            String code = stripComments(source(PACKAGE_ROOT.resolve(name)));
            int install = code.indexOf("SceneThemes.install(");
            int build = code.indexOf("buildContent(");
            assertTrue(name + " 必须在装配期装入外观档", code.contains("SceneTheme theme") && install >= 0);
            assertTrue(name + " 的外观档安装必须早于内容构建（否则已建树的派生仍绑在旧主题信号上）",
                    install >= 0 && build >= 0 && install < build);
        }
        String request = stripComments(source(PACKAGE_ROOT.resolve("HeadlessRequest.java")));
        assertTrue("请求必须暴露字号倍率", request.contains("public int fontScalePercent()"));
        assertTrue("请求必须暴露诊断开关", request.contains("public boolean diagnostics()"));
        String cli = stripComments(source(PACKAGE_ROOT.resolve("HeadlessShotMain.java")));
        assertTrue("命令行必须把 --font-scale 接到请求", cli.contains(".fontScale(scalePercent)"));
        assertTrue("命令行必须把 --debug 接到请求", cli.contains(".diagnostics(diagnostics)"));
        assertTrue("命令行必须把 --theme 接到请求", cli.contains(".theme(targetTheme)"));
        assertTrue("命令行必须把外观档接进产物命名（否则矩阵各档互相覆盖）",
                cli.contains("\"-th\" + theme"));
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
