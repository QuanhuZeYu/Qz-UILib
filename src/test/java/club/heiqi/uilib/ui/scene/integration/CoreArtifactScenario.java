package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import java.io.DataInputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** 成品隔离启动：场景仅提供消费者代码，生产定义与资源必须来自正式 coreJar。 */
public final class CoreArtifactScenario extends TestApplication {
    private static final String[] CORE = {
        "club/heiqi/uilib/ui/scene/", "club/heiqi/uilib/ui/reactive/", "club/heiqi/uilib/ui/config/"
    };
    private static final String ADAPTER = "club/heiqi/uilib/host/font/";
    private static final String ICU = "club/heiqi/uilib/shadow/com/ibm/icu/";

    public static void main(String[] args) throws Exception {
        new CoreArtifactScenario().run(args);
    }

    @Override
    protected void runTests() throws Exception {
        String[] args = arguments();
        if (args.length > 0 && "--host".equals(args[0])) {
            require(args.length >= 4, "宿主隔离需要 core、fontAdapter 与 MC 成品");
            scenario("宿主成品不重复定义核心、ICU或字体适配", () -> {
                verifyAdapter(new File(args[2]));
                for (int i = 3; i < args.length; i++) verifyHost(new File(args[i]));
            });
            return;
        }
        require(args.length == 3, "独立核心启动需要 coreJar、隔离场景输出与 testSupport 输出");
        File core = new File(args[0]).getCanonicalFile();
        scenario("核心成品只含Java8无平台定义和资源", () -> verifyCore(core));
        URL[] urls = {core.toURI().toURL(), new File(args[1]).toURI().toURL(), new File(args[2]).toURI().toURL()};
        // null parent 排除开发进程中的 main/core 输出、MC、原始 ICU 与 native；不把宿主成品接进来。
        try (URLClassLoader loader = new URLClassLoader(urls, null)) {
            scenario("独立核心实际加载配置状态布局与字体资源", () -> {
                for (String name : new String[] {
                    "club.heiqi.uilib.ui.config.ConfigSession",
                    "club.heiqi.uilib.ui.reactive.Signal",
                    "club.heiqi.uilib.ui.scene.host.ScenePageSession",
                    "club.heiqi.uilib.ui.scene.text.font.FontTextService"
                }) {
                    Class<?> type = loader.loadClass(name);
                    File source = new File(type.getProtectionDomain().getCodeSource().getLocation().toURI()).getCanonicalFile();
                    require(core.equals(source), "生产定义不是来自coreJar：" + name + " -> " + source);
                }
                require(loader.getResource("assets/qz_uilib/fonts/noto/NotoSansArabic.ttf") != null, "核心缺少字体资源");
                for (String name : new String[] {
                    "net.minecraft.client.Minecraft", "net.minecraftforge.common.MinecraftForge",
                    "org.lwjgl.system.MemoryUtil", "club.heiqi.uilib.host.font.IsolatedFontEngine",
                    "com.ibm.icu.text.Bidi"
                }) assertAbsent(loader, name);
                runConsumer(loader, "ConfigScenario");
                runConsumer(loader, "FontConfigurationScenario");
                runConsumer(loader, "PageSessionScenario");
                // 字素路径实际使用重定位后的 ICU；配置/布局消费者不依赖原始 ICU 名称。
                Class<?> unicode = loader.loadClass("club.heiqi.uilib.ui.scene.text.unicode.UnicodeGraphemes");
                Object profile = unicode.getMethod("bundled").invoke(null);
                String emoji = "👩‍💻";
                int[] boundaries = (int[]) profile.getClass().getMethod("boundaries", String.class).invoke(profile, emoji);
                require(java.util.Arrays.equals(boundaries, new int[] {0, emoji.length()}), "成品核心字素规则拆断ZWJ序列");
            });
        }
    }

    private static void runConsumer(ClassLoader loader, String simpleName) throws Exception {
        try {
            loader.loadClass("club.heiqi.uilib.ui.scene.integration." + simpleName)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException(cause);
        }
    }

    private static void assertAbsent(ClassLoader loader, String name) throws Exception {
        try {
            loader.loadClass(name);
            throw new AssertionError("独立核心可访问平台/未隔离依赖：" + name);
        } catch (ClassNotFoundException expected) {
            // 无平台运行所需的反例：缺少这些定义仍应完成下方生产路径。
        }
    }

    private void verifyCore(File file) throws Exception {
        try (JarFile jar = new JarFile(file)) {
            Enumeration<JarEntry> entries = jar.entries();
            boolean config = false, unicode = false;
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                require(!name.startsWith("assets/qz_uilib/runtime/") && !name.endsWith(".dll")
                        && !name.endsWith(".so") && !name.endsWith(".dylib") && !name.endsWith(".jar"),
                        "核心夹带native/嵌套宿主依赖：" + name);
                if (!name.endsWith(".class")) continue;
                require(startsWith(name, CORE) || name.startsWith(ICU), "核心夹带所属范围外定义：" + name);
                config |= name.equals("club/heiqi/uilib/ui/config/ConfigSession.class");
                unicode |= name.equals(ICU + "text/BreakIterator.class");
                try (InputStream input = jar.getInputStream(entry)) {
                    verifyBytecode(input, name);
                }
            }
            require(config && unicode, "核心配置或独立Unicode运行依赖缺失");
        }
    }

    private void verifyAdapter(File file) throws Exception {
        try (JarFile jar = new JarFile(file)) {
            require(jar.getJarEntry(ADAPTER + "IsolatedFontEngine.class") != null, "字体适配入口缺失");
            require(jar.getJarEntry("assets/qz_uilib/runtime/font/index.txt") != null, "字体适配绑定资源缺失");
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.endsWith(".class")) require(name.startsWith(ADAPTER), "字体适配重复或泄漏其他定义：" + name);
            }
        }
    }

    private void verifyHost(File file) throws Exception {
        try (JarFile jar = new JarFile(file)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                require(!(name.endsWith(".class") && (startsWith(name, CORE) || name.startsWith(ADAPTER)
                        || name.startsWith(ICU) || name.startsWith("com/ibm/icu/"))), "MC成品重复定义独立模块：" + name);
                require(!name.startsWith("assets/qz_uilib/runtime/font/")
                        && !name.startsWith("assets/qz_uilib/fonts/") && !name.startsWith("assets/qz_uilib/unicode/"),
                        "MC成品重复持有独立模块资源：" + name);
            }
        }
    }

    private static boolean startsWith(String value, String[] prefixes) {
        for (String prefix : prefixes) if (value.startsWith(prefix)) return true;
        return false;
    }

    private void verifyBytecode(InputStream input, String name) throws Exception {
        DataInputStream data = new DataInputStream(input);
        require(data.readInt() == 0xCAFEBABE, "无效class：" + name);
        data.readUnsignedShort();
        require(data.readUnsignedShort() <= 52, "核心不是Java8字节码：" + name);
        int count = data.readUnsignedShort();
        // 常量池同时覆盖方法体中的平台引用，单纯反射公开签名无法发现尚未执行的宿主调用。
        for (int i = 1; i < count; i++) {
            switch (data.readUnsignedByte()) {
                case 1:
                    String value = data.readUTF();
                    for (String forbidden : new String[] {"net/minecraft/", "net/minecraftforge/", "cpw/mods/", "org/lwjgl/", "club/heiqi/uilib/host/"})
                        require(!value.contains(forbidden), "核心字节码引用平台实现：" + name + " -> " + value);
                    break;
                case 3: case 4: data.readInt(); break;
                case 5: case 6: data.readLong(); i++; break;
                case 7: case 8: case 16: case 19: case 20: data.readUnsignedShort(); break;
                case 9: case 10: case 11: case 12: case 17: case 18: data.readInt(); break;
                case 15: data.readUnsignedByte(); data.readUnsignedShort(); break;
                default: throw new AssertionError("未知常量池项：" + name);
            }
        }
    }
}
