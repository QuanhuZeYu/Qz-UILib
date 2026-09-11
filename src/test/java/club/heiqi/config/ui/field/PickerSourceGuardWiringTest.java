package club.heiqi.config.ui.field;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;

/**
 * 候选源线程守卫与失效通道的<b>装配期源码守卫</b>（S-10：白名单 + 反向断言，不靠运行时行为）。
 *
 * <p>契约出处：{@code team/P0-ADR-契约与测量.md} §1.3（生产路径恒有判定源）、§2.1 #1/#2
 * （reload listener 单一注册点 = UILib 统一总线，业务侧不得自注册）。</p>
 *
 * <p>为什么必须钉装配期：{@link PickerSourceGuard} 在无判定源时降级放行（headless 属预期），
 * 若客户端引导忘记安装判定源，生产路径的线程契约会静默失效而没有任何测试变红。</p>
 */
public class PickerSourceGuardWiringTest {

    private static final Path MAIN_ROOT = Paths.get("src/main/java");

    /** 必需钉：客户端引导必须安装判定源，并挂上资源/语言代际通道。 */
    @Test
    public void clientBootstrapInstallsGuardAndEpochChannels() throws Exception {
        Path clientProxy = Paths.get("src/main/java/club/heiqi/uilib/ClientProxy.java");
        Assert.assertTrue("ClientProxy 必须存在：" + clientProxy, Files.exists(clientProxy));
        String code = codeWithoutComments(read(clientProxy));

        Assert.assertTrue("客户端引导必须安装主线程判定源（否则线程契约静默失效）",
                code.contains("PickerSourceGuard.installThreadOracle(new MinecraftMainThreadOracle())"));
        Assert.assertTrue("客户端引导必须注册资源重载通道",
                code.contains("ResourceReloadService.getInstance().registerToClient()"));
        Assert.assertTrue("客户端引导必须安装语言代际通道",
                code.contains("LanguageEpochService.getInstance().install()"));
        Assert.assertTrue("资源重载必须同时使分级表失效（旧 UNRENDERABLE 不得驻留）",
                code.contains("ItemRenderTierRegistry.invalidateAll(\"resource_reload\")"));
        Assert.assertTrue("客户端断连必须使分级表失效（跨世界旧图标结论不驻留）",
                code.contains("ItemRenderTierRegistry.invalidateAll(\"client_disconnect\")"));
    }

    /** 反向断言：全 main 源码只有 ResourceReloadService 一处注册 reload listener（统一失效总线）。 */
    @Test
    public void reloadListenerIsRegisteredFromExactlyOnePlace() throws Exception {
        List<String> registrations = new ArrayList<String>();
        List<String> implementations = new ArrayList<String>();
        for (Path file : mainSources()) {
            String code = codeWithoutComments(read(file));
            if (code.contains("registerReloadListener(")) {
                registrations.add(file.getFileName().toString());
            }
            if (code.contains("implements IResourceManagerReloadListener")
                    || code.contains("IResourceManagerReloadListener,")) {
                implementations.add(file.getFileName().toString());
            }
        }
        Assert.assertEquals("reload listener 注册点必须恰为 1 处（ResourceReloadService）",
                java.util.Collections.singletonList("ResourceReloadService.java"), registrations);
        Assert.assertEquals("IResourceManagerReloadListener 实现类必须恰为 1 个（ResourceReloadService）",
                java.util.Collections.singletonList("ResourceReloadService.java"), implementations);
    }

    /** 反向断言：平台判定源只能由客户端适配器提供（config 包不得 import Minecraft）。 */
    @Test
    public void guardCoreStaysPlatformFree() throws Exception {
        Path guard = Paths.get("src/main/java/club/heiqi/config/ui/field/PickerSourceGuard.java");
        String code = codeWithoutComments(read(guard));
        Assert.assertFalse("PickerSourceGuard 必须平台无关（判定源经装配期注入）",
                code.contains("net.minecraft") || code.contains("cpw.mods"));
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

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /** 剥离注释：守卫只对真实代码生效（注释里出现关键字不得触发/规避断言）。 */
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
