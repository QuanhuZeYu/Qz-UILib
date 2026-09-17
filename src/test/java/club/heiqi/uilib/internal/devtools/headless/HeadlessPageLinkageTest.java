package club.heiqi.uilib.internal.devtools.headless;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

/**
 * headless 页面装配的<b>可链接性</b>门禁：能在最小集类路径上装配的页面就应当真的出图。
 *
 * <h3>它守的回归（实测发生过一次）</h3>
 * <p>配置页接入时，它的装配入口与 MC 宿主包装在<b>同一个类</b>里。那个类在最小集类路径
 * （不含 {@code net.minecraft.*}）下<b>根本加载不了</b>——实测
 * {@code Class.forName("…ModernConfigEntry")} 抛
 * {@code NoClassDefFoundError: net/minecraft/client/gui/GuiScreen}，于是出图直接非 0 退出。
 * 修法是按「一个类要么是宿主、要么是装配」拆开（{@code ModernConfigEntry} /
 * {@code ModernConfigAssembly}）。</p>
 *
 * <p>本门禁钉的是<b>端到端事实</b>（进程 exit=0 且落出 PNG），不依赖对「哪一处 MC 引用触发
 * 加载失败」的推断：曾用「仅方法签名引用 MC 类型」的变异探针试图复现，<b>未触发</b>该失败
 * （触发点在类内更深处，未逐项测定）——因此该推断不写进断言。</p>
 *
 * <p>这条回归用进程内单测测不出来（它只在「类路径里没有 MC」时才现形），也无法靠读代码发现
 * ——两类混在一个文件里看上去完全正常。故本类直启 {@code HeadlessShotMain}（与 agent 使用路径
 * 一致，走 {@code classpath.txt} 那份最小集），无 GL 或未注入 classpath 时 {@code Assume} 跳过。</p>
 *
 * <h3>为什么 {@code chat} / {@code hud} 不在覆盖内</h3>
 * <p>它们的探针宿主在<b>方法体</b>里使用 {@code net.minecraft.*}（{@code ChatComponentText} 等），
 * 本来就只在 {@code classpath-full.txt} 上可跑（规划 F21 记录）。那是注入面的事实，不是被测行为；
 * 把它写进断言只会把「页面需要哪份 classpath」固化成假契约。</p>
 *
 * <h3>为什么 playground 也要测</h3>
 * <p>它不是回归对象，是<b>正锚</b>：若最小集本身缺件（natives 未解压、classpath 未重建），
 * 只测配置页会红得看不出成因。playground 通过即排除「环境没搭好」，剩下的红就是页面自己的问题。</p>
 */
public class HeadlessPageLinkageTest {

    private static final Pattern COMMANDS = Pattern.compile("commands=(\\d+)");

    /** 配置页在最小集上必须真的出图（本轮回归的守卫）。 */
    @Test
    public void configPageRendersOnTheMinimalClasspath() throws Exception {
        String output = render("config", "linkage-config");
        Assert.assertTrue("配置页必须下发绘制命令（否则是装配失败而非「页面没内容」）：\n" + output,
                commandsOf(output) > 0);
        Assert.assertTrue("配置页自检必须通过：\n" + output, output.contains("self-check: ok"));
    }

    /** 正锚：最小集本身是好的（含 natives 与 classpath 注入）。 */
    @Test
    public void playgroundPageRendersOnTheMinimalClasspath() throws Exception {
        String output = render("playground", "linkage-playground");
        Assert.assertTrue("playground 也必须出图；它失败说明最小集/注入面坏了，而不是被测页面：\n" + output,
                commandsOf(output) > 0);
    }

    /** 直启一次出图并返回进程输出；退出码非 0 即断言失败（附完整输出便于定因）。 */
    private static String render(String page, String name) throws Exception {
        String classpathFile = System.getProperty("qz.headless.classpathFile", "");
        Assume.assumeTrue("未注入 headless 直启 classpath，跳过页面可链接性门禁",
                !classpathFile.isEmpty() && new File(classpathFile).isFile());
        String nativesDir = System.getProperty("qz.headless.nativesDir", "");

        Path output = Paths.get("build", "reports", "headless", name + ".png").toAbsolutePath();
        Files.createDirectories(output.getParent());
        Files.deleteIfExists(output);

        String javaExecutable = Paths.get(System.getProperty("java.home"), "bin",
                isWindows() ? "java.exe" : "java").toString();
        List<String> command = new ArrayList<String>();
        command.add(javaExecutable);
        command.add("-Djava.library.path=" + nativesDir);
        command.add("-Xmx2g");
        command.add("-cp");
        command.add(new String(Files.readAllBytes(Paths.get(classpathFile)), StandardCharsets.UTF_8).trim());
        command.add("club.heiqi.uilib.internal.devtools.headless.HeadlessShotMain");
        command.add("--page=" + page);
        command.add("--size=1280x720");
        command.add("--out=" + output);

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
        Assert.assertEquals("headless 直启失败（page=" + page + " exit=" + exit + "）：\n" + text, 0, exit);
        Assert.assertTrue("未产出 PNG（page=" + page + "）：\n" + text, Files.isRegularFile(output));
        return text;
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
