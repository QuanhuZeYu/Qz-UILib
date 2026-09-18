package club.heiqi.uilib.internal.devtools.headless;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

/**
 * headless 环境接线<b>行为</b>门禁：请求声明的环境量必须真的改变产物。
 *
 * <h3>它替代了什么</h3>
 * <p>此前这层保障是 18 条源码串 {@code contains} 快照（「session 里必须有
 * {@code .fontScale(scalePercent)} 这一行」）。源码串把「实现怎么写」抄进测试：换个等价的局部变量名
 * 就红，而真正的回归（参数收下了但没接线）与「接线了但写法不同」在字符串层面不可区分。本类改钉
 * <b>进程外的可观测事实</b>：同一页面、同一尺寸，只改一个环境参数，产物必须不同。</p>
 *
 * <h3>它为什么不能是进程内单测</h3>
 * <p>这几条接线的失败形态都是<b>静默降级</b>：命令不报错、产物照出，只是与不传该参数逐像素相同。
 * 要观测这一点就必须真的走一遍 GL 出图，故本类直启 {@code HeadlessShotMain}（与 agent 使用路径
 * 一致），无 GL 或未注入 classpath 时 {@code Assume} 跳过。</p>
 *
 * <h3>覆盖的轴</h3>
 * <ul>
 *   <li>字号倍率 {@code --font-scale}：投影到 runtime 的字号代际通道；</li>
 *   <li>外观档 {@code --theme}：配方派生在构建期捕获主题信号，装晚了不会重算；</li>
 *   <li>诊断 {@code --debug}：环境端口是否真的接到了采样器 —— 断言 {@code perf:} 行里的
 *       {@code phases=} / {@code counters=} <b>非空</b>（采样会话真的开了），而不是「有没有打印
 *       perf 这行字」：后者由请求字段回显决定，把环境端口掐断照样打印、内容全 0（独立复核实测）；</li>
 *   <li>矩阵命名：多页面 + 多档出图必须落出多个<b>文件名互不相同</b>的产物，否则各档互相覆盖。</li>
 * </ul>
 */
public class HeadlessWiringBehaviourTest {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final String REPORT_DIR = "headless";

    /** 只改字号倍率的两次出图必须不同（否则 {@code --font-scale} 是空话）。 */
    @Test
    public void fontScaleChangesTheImage() throws Exception {
        Shot base = shot("wiring-fs100", "--font-scale=100");
        Shot scaled = shot("wiring-fs150", "--font-scale=150");
        int diff = differingPixels(base, scaled);
        Assert.assertTrue("字号倍率 100% 与 150% 必须改变像素（当前差 " + diff + " 像素）："
                + "参数被收下但没投影到 runtime，或投影发生在建树之后", diff > 1000);
    }

    /** 只改外观档的两次出图必须不同（否则 {@code --theme} 装晚了或没装）。 */
    @Test
    public void themeChangesTheImage() throws Exception {
        Shot base = shot("wiring-theme-default");
        Shot light = shot("wiring-theme-light", "--theme=liquid-glass-light");
        int diff = differingPixels(base, light);
        Assert.assertTrue("外观档缺省与浅色档必须改变像素（当前差 " + diff + " 像素）："
                + "配方派生在构建期捕获主题信号，装晚了不会让已建树的派生重算", diff > 1000);
    }

    /**
     * {@code --debug} 必须真的打开采样器（环境端口接线），且不改变绘制。
     *
     * <p><b>断言的是采样折叠结果，不是「有没有打印 perf 这行字」</b>：perf 行的有无由请求字段回显
     * 决定，把环境端口掐断（{@code HeadlessEnvironment.of(false)}）而 {@code request.diagnostics()}
     * 照旧时，仍然会打印一行 {@code phases=<none> counters=<none>} 的全 0 摘要 —— 那恰恰就是原始缺陷
     * 的形态（命令不报错、采样器没开），独立复核用变异测试实测过这一盲区。故这里要求折叠结果非空。</p>
     */
    @Test
    public void debugOpensTheSamplerWithoutChangingDrawing() throws Exception {
        Shot plain = shot("wiring-nodebug");
        Shot debug = shot("wiring-debug", "--debug");
        String perf = perfLine(debug.output);
        Assert.assertNotNull("--debug 必须让采样器表态（输出缺少 perf: 行）：\n" + debug.output, perf);
        Assert.assertFalse("采样会话没开：perf 行的 phases/counters 为空 —— " + perf,
                perf.contains("phases=<none>") || perf.contains("counters=<none>"));
        Assert.assertTrue("perf 行必须给出阶段耗时事实：" + perf, perf.contains("phases="));
        Assert.assertEquals("--debug 不得改变绘制（既有性质：0 / " + (WIDTH * HEIGHT) + "）",
                0, differingPixels(plain, debug));
    }

    /** 从进程输出里取 {@code perf:} 行；没有则返回 null。 */
    private static String perfLine(String output) {
        for (String line : output.split("\\r?\\n")) {
            int index = line.indexOf("perf:");
            if (index >= 0) {
                return line.substring(index);
            }
        }
        return null;
    }

    /**
     * 多轴矩阵必须落出多个<b>文件名互不相同</b>的产物（否则各档互相覆盖，矩阵只剩最后一张）。
     *
     * <p>不钉命名格式，只钉事实：三轴同时给（3 页面 × 2 外观档）必须是 6 个不同文件名。
     * 这是原「产物名必须带页名 / 档名」源码串断言所守的可观测后果。</p>
     *
     * <p><b>为什么页面轴必须一起给</b>：只给外观档时页面段（{@code -pg}）恒为缺省，删掉页面段
     * 的实现照样落出多个文件 —— 独立复核实测该缺口（删 {@code -pg} 段后门禁仍绿、真实产物从 2 塌成 1）。
     * 同时只比「文件数」也不够：两档写进同一个文件名同样满足计数，故这里比对<b>去重后的文件名集合</b>。</p>
     *
     * <p>页面轴只用 <b>最小集类路径</b>可跑的页面：注入的 classpath 是 {@code classpath.txt}（agent 默认
     * 启动器那一份），{@code chat} / {@code hud} 需要 {@code net.minecraft.*}（只在
     * {@code classpath-full.txt} 里），在这条链路上会以非 0 退出。这是注入面的事实，不是被测行为。</p>
     */
    @Test
    public void matrixAxesGetDistinctArtifactNames() throws Exception {
        String classpathFile = assumeInjected();
        String nativesDir = System.getProperty("qz.headless.nativesDir", "");
        Path out = reportPath("wiring-matrix.png");
        for (Path stale : siblings(out)) {
            Files.deleteIfExists(stale);
        }
        List<String> args = new ArrayList<String>();
        args.add("--pages=playground,text-probe");
        args.add("--size=" + WIDTH + "x" + HEIGHT);
        args.add("--themes=liquid-glass-light,solid-dark");
        args.add("--out=" + out);
        String text = execute("wiring-matrix", classpathFile, nativesDir, args);

        List<Path> produced = siblings(out);
        Set<String> names = new LinkedHashSet<String>();
        for (Path path : produced) {
            names.add(path.getFileName().toString());
        }
        Assert.assertEquals("2 页面 × 2 外观档必须落出 4 个互不相同的文件名（页面段/档名段缺一即塌缩）：\n"
                + text + "实际 " + names, 4, names.size());
    }

    /** 出图产物 + 进程输出。 */
    private static final class Shot {

        private final Path png;
        private final String output;

        Shot(Path png, String output) {
            this.png = png;
            this.output = output;
        }
    }

    /** 直启一次 playground 单档出图。 */
    private static Shot shot(String name, String... extra) throws Exception {
        String classpathFile = assumeInjected();
        String nativesDir = System.getProperty("qz.headless.nativesDir", "");
        Path output = reportPath(name + ".png");
        Files.deleteIfExists(output);

        List<String> args = new ArrayList<String>();
        args.add("--page=playground");
        args.add("--size=" + WIDTH + "x" + HEIGHT);
        if (extra != null) {
            for (String one : extra) {
                if (one != null) {
                    args.add(one);
                }
            }
        }
        args.add("--out=" + output);

        String text = execute(name, classpathFile, nativesDir, args);
        Assert.assertTrue("headless 未产出 PNG（" + name + "）：\n" + text, Files.isRegularFile(output));
        return new Shot(output, text);
    }

    /**
     * 直启 headless 出图并返回进程输出；退出码非 0 即断言失败。
     *
     * @param label 直启标签（产物名），出现在跳过原因与失败文案里
     */
    private static String execute(String label, String classpathFile, String nativesDir, List<String> args)
            throws Exception {
        String javaExecutable = Paths.get(System.getProperty("java.home"), "bin",
                isWindows() ? "java.exe" : "java").toString();
        String classpath = new String(Files.readAllBytes(Paths.get(classpathFile)),
                StandardCharsets.UTF_8).trim();
        List<String> command = new ArrayList<String>();
        command.add(javaExecutable);
        command.add("-Djava.library.path=" + nativesDir);
        command.add("-Xmx2g");
        command.add("-cp");
        command.add(classpath);
        command.add("club.heiqi.uilib.internal.devtools.headless.HeadlessShotMain");
        command.addAll(args);

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String text;
        InputStream stream = process.getInputStream();
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = stream.read(chunk)) >= 0) {
                buffer.write(chunk, 0, read);
            }
            text = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            stream.close();
        }
        int exit = process.waitFor();
        // 运行环境不具备（无 GL / natives 加载不了）时跳过，其余非 0 一律红。
        HeadlessShotGate.assumeEnvironmentAvailable(label, exit, text);
        Assert.assertEquals("headless 直启失败（" + label + " exit=" + exit + "）：\n" + text, 0, exit);
        return text;
    }

    /** 校验直启 classpath 已注入并返回其路径；未注入即跳过本类。 */
    private static String assumeInjected() {
        String classpathFile = System.getProperty("qz.headless.classpathFile", "");
        Assume.assumeTrue("未注入 headless 直启 classpath，跳过接线行为门禁",
                !classpathFile.isEmpty() && new File(classpathFile).isFile());
        return classpathFile;
    }

    /** 报告目录下的产物路径。 */
    private static Path reportPath(String fileName) throws Exception {
        Path path = Paths.get("build", "reports", REPORT_DIR, fileName).toAbsolutePath();
        Files.createDirectories(path.getParent());
        return path;
    }

    /** 与给定产物同前缀的既有 PNG（矩阵模式下会有多个）。 */
    private static List<Path> siblings(Path out) throws Exception {
        String fileName = out.getFileName().toString();
        final String prefix = fileName.endsWith(".png")
                ? fileName.substring(0, fileName.length() - 4) : fileName;
        List<Path> found = new ArrayList<Path>();
        try (java.util.stream.Stream<Path> stream = Files.list(out.getParent())) {
            for (Path path : (Iterable<Path>) stream.filter(candidate -> {
                String name = candidate.getFileName().toString();
                return name.startsWith(prefix) && name.endsWith(".png");
            })::iterator) {
                found.add(path);
            }
        }
        return found;
    }

    /** 两张 PNG 的不同像素数（尺寸不一致视为全不同）。 */
    private static int differingPixels(Shot left, Shot right) throws Exception {
        BufferedImage a = ImageIO.read(left.png.toFile());
        BufferedImage b = ImageIO.read(right.png.toFile());
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            return a.getWidth() * a.getHeight();
        }
        int[] pa = new int[a.getWidth() * a.getHeight()];
        int[] pb = new int[b.getWidth() * b.getHeight()];
        a.getRGB(0, 0, a.getWidth(), a.getHeight(), pa, 0, a.getWidth());
        b.getRGB(0, 0, b.getWidth(), b.getHeight(), pb, 0, b.getWidth());
        int diff = 0;
        for (int i = 0; i < pa.length; i++) {
            if (pa[i] != pb[i]) {
                diff++;
            }
        }
        return diff;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }
}
