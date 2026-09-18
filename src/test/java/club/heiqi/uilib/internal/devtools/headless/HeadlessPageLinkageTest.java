package club.heiqi.uilib.internal.devtools.headless;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

/**
 * headless 页面装配的<b>端到端</b>门禁：能在最小集类路径上装配的页面就应当真的出图，
 * 且本类交付的每一项能力都要有一条会红的判据。
 *
 * <h3>它守的回归（实测发生过一次）</h3>
 * <p>配置页接入时，它的装配入口与 MC 宿主包装在<b>同一个类</b>里。那个类在最小集类路径
 * （不含 {@code net.minecraft.*}）下<b>根本加载不了</b>——实测
 * {@code Class.forName("…ModernConfigEntry")} 抛
 * {@code NoClassDefFoundError: net/minecraft/client/gui/GuiScreen}，于是出图直接非 0 退出。
 * 修法是按「一个类要么是宿主、要么是装配」拆开（{@code ModernConfigEntry} /
 * {@code ModernConfigAssembly}）。</p>
 *
 * <p>触发点（独立复核的合成实验 + 单行变异定位）：<b>不是</b>「方法签名引用了 MC 类型」——
 * 仅出现在方法签名 / 字段类型 / {@code checkcast} / {@code instanceof} 里的 MC 类型都不触发；
 * 真实原因是校验期的可赋值性检查迫使 JVM 解析缺失的父类型（{@code return new ModernConfigScreen(…)}
 * 要证明它可赋给 {@code GuiScreen}）。故本类只钉端到端事实，不把机制推断写进断言。</p>
 *
 * <h3>为什么 {@code chat} / {@code hud} 不在覆盖内</h3>
 * <p>它们的探针宿主在<b>方法体</b>里使用 {@code net.minecraft.*}（{@code ChatComponentText} 等），
 * 本来就只在 {@code classpath-full.txt} 上可跑（规划 F21 记录）。那是注入面的事实，不是被测行为；
 * 把它写进断言只会把「页面需要哪份 classpath」固化成假契约。</p>
 *
 * <h3>为什么 playground 也要测</h3>
 * <p>它不是回归对象，是<b>正锚</b>：若最小集本身缺件（natives 未解压、classpath 未重建），
 * 只测配置页会红得看不出成因。playground 通过即排除「环境没搭好」，剩下的红就是页面自己的问题。</p>
 *
 * <h3>为什么 section 轴与临时目录也各占一条</h3>
 * <p>独立复核用变异实测过：把 {@code ConfigScreen.showSection} 改成空操作、或摘掉
 * {@code HeadlessSession.close()} 里的 {@code hostCleanup.run()}，本类其余用例<b>全绿</b>
 * ——即「参数收下了但没接线」「痕迹没清掉」这两类静默降级没有被守住。三条判据各钉一项交付。</p>
 */
public class HeadlessPageLinkageTest {

    private static final Pattern COMMANDS = Pattern.compile("commands=(\\d+)");
    private static final Pattern COLORS = Pattern.compile("colors=(\\d+)");
    private static final String TEMP_DIR_PREFIX = "qz-headless-config-";

    /** 配置页在最小集上必须真的出图（本轮回归的守卫）。 */
    @Test
    public void configPageRendersOnTheMinimalClasspath() throws Exception {
        String output = render("linkage-config", "--page=config");
        Assert.assertTrue("配置页必须下发绘制命令（否则是装配失败而非「页面没内容」）：\n" + output,
                commandsOf(output) > 0);
        Assert.assertTrue("配置页自检必须通过：\n" + output, output.contains("self-check: ok"));
    }

    /** 正锚：最小集本身是好的（含 natives 与 classpath 注入）。 */
    @Test
    public void playgroundPageRendersOnTheMinimalClasspath() throws Exception {
        String output = render("linkage-playground", "--page=playground");
        Assert.assertTrue("playground 也必须出图；它失败说明最小集/注入面坏了，而不是被测页面：\n" + output,
                commandsOf(output) > 0);
    }

    /** section 轴必须真的切换内容（否则 {@code --page-index} 对 config 页是空话）。 */
    @Test
    public void configSectionAxisSwitchesContent() throws Exception {
        String output = render("linkage-section", "--page=config", "--page-indexes=0,1,2");
        Set<String> colors = new LinkedHashSet<String>();
        Matcher matcher = COLORS.matcher(output);
        while (matcher.find()) {
            colors.add(matcher.group(1));
        }
        // 三档各一条 self-check 行；三档颜色数必须两两不同（实测 1156 / 1066 / 1117）。
        Assert.assertEquals("三档 section 必须画出不同内容 —— 三档颜色数全同时说明切换没接线：\n" + output,
                3, colors.size());
    }

    /** 出图不得在进程外留痕：临时配置目录跑完必须消失。 */
    @Test
    public void configPageLeavesNoTempDirectory() throws Exception {
        Set<String> before = tempConfigDirs();
        render("linkage-tempdir", "--page=config");
        Set<String> after = tempConfigDirs();
        after.removeAll(before);
        Assert.assertTrue("出图后残留了临时配置目录（会话清理没执行）：" + after, after.isEmpty());
    }

    /** 直启一次出图并返回进程输出；退出码非 0 即断言失败（附完整输出便于定因）。 */
    private static String render(String name, String... extraArgs) throws Exception {
        String classpathFile = System.getProperty("qz.headless.classpathFile", "");
        Assume.assumeTrue("未注入 headless 直启 classpath，跳过页面可链接性门禁",
                !classpathFile.isEmpty() && new File(classpathFile).isFile());
        String nativesDir = System.getProperty("qz.headless.nativesDir", "");

        Path out = Paths.get("build", "reports", "headless", name + ".png").toAbsolutePath();
        Files.createDirectories(out.getParent());
        for (Path stale : sameStem(out)) {
            Files.deleteIfExists(stale);
        }

        String javaExecutable = Paths.get(System.getProperty("java.home"), "bin",
                isWindows() ? "java.exe" : "java").toString();
        List<String> command = new ArrayList<String>();
        command.add(javaExecutable);
        command.add("-Djava.library.path=" + nativesDir);
        command.add("-Xmx2g");
        command.add("-cp");
        command.add(new String(Files.readAllBytes(Paths.get(classpathFile)), StandardCharsets.UTF_8).trim());
        command.add("club.heiqi.uilib.internal.devtools.headless.HeadlessShotMain");
        for (String one : extraArgs) {
            command.add(one);
        }
        command.add("--size=1280x720");
        command.add("--out=" + out);

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
        HeadlessShotGate.assumeEnvironmentAvailable(name, exit, text);
        Assert.assertEquals("headless 直启失败（" + name + " exit=" + exit + "）：\n" + text, 0, exit);
        // 单档落主名、多档落带轴后缀的名字，故只要求「同前缀至少一个产物」。
        List<Path> produced = sameStem(out);
        Assert.assertFalse("未产出 PNG（" + name + "）：\n" + text, produced.isEmpty());
        return text;
    }

    /** 与给定产物同前缀的 PNG（多档矩阵下会有多个）。 */
    private static List<Path> sameStem(Path out) throws Exception {
        String fileName = out.getFileName().toString();
        final String prefix = fileName.endsWith(".png")
                ? fileName.substring(0, fileName.length() - 4) : fileName;
        List<Path> found = new ArrayList<Path>();
        try (Stream<Path> stream = Files.list(out.getParent())) {
            for (Path path : (Iterable<Path>) stream.filter(candidate -> {
                String candidateName = candidate.getFileName().toString();
                return candidateName.startsWith(prefix) && candidateName.endsWith(".png");
            })::iterator) {
                found.add(path);
            }
        }
        return found;
    }

    /** 当前 {@code java.io.tmpdir} 下的临时配置目录名（用前后差集判定，避免算进并发进程的目录）。 */
    private static Set<String> tempConfigDirs() throws Exception {
        Set<String> found = new LinkedHashSet<String>();
        Path root = Paths.get(System.getProperty("java.io.tmpdir", "."));
        if (!Files.isDirectory(root)) {
            return found;
        }
        try (Stream<Path> stream = Files.list(root)) {
            for (Path path : (Iterable<Path>) stream.filter(candidate -> candidate.getFileName().toString()
                    .startsWith(TEMP_DIR_PREFIX))::iterator) {
                found.add(path.getFileName().toString());
            }
        }
        return found;
    }

    /** 从输出里取命令面摘要的命令数；没有该行返回 -1。 */
    private static int commandsOf(String output) {
        Matcher matcher = COMMANDS.matcher(output);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }
}
