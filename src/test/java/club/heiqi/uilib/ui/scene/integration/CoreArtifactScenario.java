package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** 成品隔离启动：场景仅提供消费者代码，生产定义与资源必须来自正式 coreJar。 */
public final class CoreArtifactScenario extends TestApplication {
    private boolean desktopMode;
    private static final String[] CORE = {
        "club/heiqi/uilib/ui/scene/",
        "club/heiqi/uilib/ui/reactive/",
        "club/heiqi/uilib/ui/config/",
        "club/heiqi/uilib/ui/settings/",
        "club/heiqi/uilib/ui/render/batch/"
    };
    private static final String ADAPTER = "club/heiqi/uilib/host/font/";
    private static final String ICU = "club/heiqi/uilib/shadow/com/ibm/icu/";
    private static final String YAML = "club/heiqi/uilib/shadow/org/yaml/snakeyaml/";
    private static final String MARKDOWN = "club/heiqi/uilib/shadow/org/commonmark/";
    private static final String AUTOLINK = "club/heiqi/uilib/shadow/org/nibor/autolink/";
    private static final String HTML = "club/heiqi/uilib/shadow/org/jsoup/";

    public static void main(String[] args) throws Exception {
        new CoreArtifactScenario().run(args);
    }

    @Override
    protected void runTests() throws Exception {
        String[] args = arguments();
        if (args.length > 0 && "--desktop".equals(args[0])) {
            require(args.length == 4, "桌面隔离需要 core、fontAdapter 与 desktop 成品");
            scenario(
                    "桌面正式成品隔离核心字体及MC平台引用",
                    () -> {
                        verifyCore(new File(args[1]));
                        verifyAdapter(new File(args[2]));
                        desktopMode = true;
                        verifyDesktop(new File(args[3]));
                    });
            return;
        }
        if (args.length > 0 && "--host".equals(args[0])) {
            require(args.length >= 4, "宿主隔离需要 core、fontAdapter 与 MC 成品");
            scenario(
                    "宿主成品不重复定义核心、ICU、YAML或字体适配",
                    () -> {
                        verifyAdapter(new File(args[2]));
                        for (int i = 3; i < args.length; i++) verifyHost(new File(args[i]));
                    });
            return;
        }
        require(args.length == 3, "独立核心启动需要 coreJar、隔离场景输出与 testSupport 输出");
        File core = new File(args[0]).getCanonicalFile();
        scenario("核心成品只含Java8无平台定义和资源", () -> verifyCore(core));
        URL[] urls = {
            core.toURI().toURL(),
            new File(args[1]).toURI().toURL(),
            new File(args[2]).toURI().toURL()
        };
        // null parent 排除开发进程中的 main/core 输出、MC、原始 ICU/YAML 与 native；不把宿主成品接进来。
        try (URLClassLoader loader = new URLClassLoader(urls, null)) {
            scenario(
                    "独立核心实际加载配置状态布局与字体资源",
                    () -> {
                        for (String name :
                                new String[] {
                                    "club.heiqi.uilib.ui.config.ConfigSession",
                                    "club.heiqi.uilib.ui.reactive.Signal",
                                    "club.heiqi.uilib.ui.scene.host.ScenePageSession",
                                    "club.heiqi.uilib.ui.scene.control.SceneGrid",
                                    "club.heiqi.uilib.ui.scene.control.SceneGrid$Policy",
                                     "club.heiqi.uilib.ui.scene.control.SceneLayer",
                                     "club.heiqi.uilib.ui.scene.control.SceneLayer$Policy",
                                     "club.heiqi.uilib.ui.scene.control.SceneLayer$Participation",
                                    "club.heiqi.uilib.ui.scene.text.font.FontTextService",
                                    "club.heiqi.uilib.shadow.org.yaml.snakeyaml.Yaml",
                                    "club.heiqi.uilib.ui.scene.text.MarkdownDocument",
                                    "club.heiqi.uilib.shadow.org.commonmark.parser.Parser",
                                    "club.heiqi.uilib.shadow.org.nibor.autolink.LinkExtractor",
                                    "club.heiqi.uilib.shadow.org.jsoup.parser.Parser",
                                    "club.heiqi.uilib.shadow.org.commonmark.ext.gfm.tables.TablesExtension"
                                }) {
                            Class<?> type = loader.loadClass(name);
                            File source =
                                    new File(
                                                    type.getProtectionDomain()
                                                            .getCodeSource()
                                                            .getLocation()
                                                            .toURI())
                                            .getCanonicalFile();
                            require(
                                    core.equals(source),
                                    "生产定义不是来自coreJar：" + name + " -> " + source);
                        }
                        require(
                                loader.getResource("assets/qz_uilib/fonts/noto/NotoSansArabic.ttf")
                                        != null,
                                "核心缺少字体资源");
                        for (String name :
                                new String[] {
                                    "net.minecraft.client.Minecraft",
                                    "net.minecraftforge.common.MinecraftForge",
                                    "org.lwjgl.system.MemoryUtil",
                                    "club.heiqi.uilib.host.font.IsolatedFontEngine",
                                    "com.ibm.icu.text.Bidi",
                                    "org.yaml.snakeyaml.Yaml",
                                    "org.commonmark.parser.Parser",
                                    "org.nibor.autolink.LinkExtractor",
                                    "org.jsoup.parser.Parser"
                                }) assertAbsent(loader, name);
                        // 成品隔离执行真正的解析、实体资源与数学扩展注册，不能仅检查.class存在。
                        Object markdown =
                                loader.loadClass("club.heiqi.uilib.ui.scene.text.MarkdownDocument")
                                        .getMethod("parse", String.class)
                                        .invoke(
                                                null,
                                                "# heading\n\n"
                                                    + "&NotEqualTilde; **bold** $x$"
                                                    + " https://example.test\n");
                        Object structured =
                                loader.loadClass("club.heiqi.uilib.ui.scene.text.MarkdownDocument")
                                        .getMethod("parse", String.class)
                                        .invoke(
                                                null,
                                                "a | b\n"
                                                    + "--- | ---\n"
                                                    + "$x$ | &amp;\n\n"
                                                    + "<table><tr><th>A</th><td><strong>B</strong></td></tr></table>\n");
                        java.util.List<?> tables =
                                (java.util.List<?>)
                                        structured
                                                .getClass()
                                                .getMethod("blocks")
                                                .invoke(structured);
                        require(tables.size() == 2, "核心成品表格/HTML未形成共同结构");
                        for (Object table : tables) {
                            require(
                                    "TABLE"
                                            .equals(
                                                    table.getClass()
                                                            .getField("kind")
                                                            .get(table)
                                                            .toString()),
                                    "成熟转换没有共同TABLE块");
                            Object value = table.getClass().getField("table").get(table);
                            require(
                                    ((Number)
                                                            value.getClass()
                                                                    .getMethod("columnCount")
                                                                    .invoke(value))
                                                    .intValue()
                                            == 2,
                                    "核心表格列数不正确");
                        }
                        java.util.List<?> blocks =
                                (java.util.List<?>)
                                        markdown.getClass().getMethod("blocks").invoke(markdown);
                        require(blocks.size() == 2, "核心成品Markdown解析没有正确结构");
                        Object paragraph =
                                blocks.get(1).getClass().getField("paragraph").get(blocks.get(1));
                        Object style =
                                loader.loadClass("club.heiqi.uilib.ui.scene.text.TextStyle")
                                        .getConstructor(String.class, int.class, int.class)
                                        .newInstance("default", 16, -1);
                        Object resolved =
                                paragraph
                                        .getClass()
                                        .getMethod("resolve", style.getClass())
                                        .invoke(paragraph, style);
                        Object text = resolved.getClass().getField("text").get(resolved);
                        Object input = text.getClass().getField("input").get(text);
                        require(
                                input.getClass()
                                        .getField("text")
                                        .get(input)
                                        .toString()
                                        .startsWith("≂̸ bold"),
                                "核心成品实体表重定位没有实际生效");
                        runConsumer(loader, "ConfigScenario");
                        runConsumer(loader, "YamlObjectScenario");
                        runConsumer(loader, "ThemeConfigurationScenario");
                        runConsumer(loader, "FontConfigurationScenario");
                        runConsumer(loader, "PageSessionScenario");
                        // 隔离生产来源实际调用公共策略、工厂、frame 与输入，不借开发classpath。
                        loader.loadClass("club.heiqi.uilib.ui.scene.integration.SceneCoreLoopTest")
                                .getMethod("publicGridConsumer").invoke(null);
                         loader.loadClass("club.heiqi.uilib.ui.scene.integration.SceneCoreLoopTest")
                                 .getMethod("publicLayerConsumer").invoke(null);
                        // 字素路径实际使用重定位后的 ICU；配置/布局消费者不依赖原始 ICU 名称。
                        Class<?> unicode =
                                loader.loadClass(
                                        "club.heiqi.uilib.ui.scene.text.unicode.UnicodeGraphemes");
                        Object profile = unicode.getMethod("bundled").invoke(null);
                        String emoji = "👩‍💻";
                        int[] boundaries =
                                (int[])
                                        profile.getClass()
                                                .getMethod("boundaries", String.class)
                                                .invoke(profile, emoji);
                        require(
                                java.util.Arrays.equals(boundaries, new int[] {0, emoji.length()}),
                                "成品核心字素规则拆断ZWJ序列");
                    });
        }
    }

    private static void runConsumer(ClassLoader loader, String simpleName) throws Exception {
        try {
            loader.loadClass("club.heiqi.uilib.ui.scene.integration." + simpleName)
                    .getMethod("main", String[].class)
                    .invoke(null, (Object) new String[0]);
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
            require(
                    jar.getJarEntry("assets/qz_uilib/math/catalog/functions.tsv") != null,
                    "Core数学事实资源缺失：桌面共享页面无法初始化");
            require(
                    jar.getJarEntry("assets/qz_uilib/licenses/ICU-76.1-LICENSE.txt") != null,
                    "独立核心携带ICU时必须同时携带随仓许可，不能依赖MC资源包");
            require(
                    jar.getJarEntry("assets/qz_uilib/licenses/CommonMark-0.21.0-LICENSE.txt")
                            != null,
                    "核心CommonMark许可缺失");
            require(
                    jar.getJarEntry("assets/qz_uilib/licenses/Autolink-0.10.0-LICENSE.txt") != null,
                    "核心自动链接许可缺失");
            require(
                    jar.getJarEntry("assets/qz_uilib/licenses/Jsoup-1.19.1-LICENSE.txt") != null,
                    "核心HTML解析许可缺失");
            boolean config = false, unicode = false, yaml = false;
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                require(
                        !name.startsWith("assets/qz_uilib/runtime/")
                                && !name.endsWith(".dll")
                                && !name.endsWith(".so")
                                && !name.endsWith(".dylib")
                                && !name.endsWith(".jar"),
                        "核心夹带native/嵌套宿主依赖：" + name);
                if (!name.endsWith(".class")) continue;
                require(
                        startsWith(name, CORE)
                                || name.startsWith(ICU)
                                || name.startsWith(YAML)
                                || name.startsWith(MARKDOWN)
                                || name.startsWith(AUTOLINK)
                                || name.startsWith(HTML),
                        "核心夹带所属范围外定义：" + name);
                config |= name.equals("club/heiqi/uilib/ui/config/ConfigSession.class");
                unicode |= name.equals(ICU + "text/BreakIterator.class");
                yaml |= name.equals(YAML + "Yaml.class");
                try (InputStream input = jar.getInputStream(entry)) {
                    verifyBytecode(input, name);
                }
            }
            require(config && unicode && yaml, "核心配置或私有Unicode/YAML运行依赖缺失");
        }
    }

    private void verifyAdapter(File file) throws Exception {
        try (JarFile jar = new JarFile(file)) {
            require(jar.getJarEntry(ADAPTER + "IsolatedFontEngine.class") != null, "字体适配入口缺失");
            require(
                    jar.getJarEntry("assets/qz_uilib/runtime/font/index.txt") != null,
                    "字体适配绑定资源缺失");
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.endsWith(".class"))
                    require(name.startsWith(ADAPTER), "字体适配重复或泄漏其他定义：" + name);
            }
        }
    }

    private void verifyHost(File file) throws Exception {
        try (JarFile jar = new JarFile(file)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                require(
                        !(name.endsWith(".class")
                                && (startsWith(name, CORE)
                                        || name.startsWith(ADAPTER)
                                        || name.startsWith(ICU)
                                        || name.startsWith("com/ibm/icu/")
                                        || name.startsWith(YAML)
                                        || name.startsWith("org/yaml/snakeyaml/")
                                        || name.startsWith(MARKDOWN)
                                        || name.startsWith(AUTOLINK)
                                        || name.startsWith(HTML)
                                        || name.startsWith("org/commonmark/")
                                        || name.startsWith("org/nibor/autolink/")
                                        || name.startsWith("org/jsoup/"))),
                        "MC成品重复定义独立模块：" + name);
                require(!name.startsWith("club/heiqi/uilib/host/desktop/"), "MC成品嵌入桌面宿主：" + name);
                require(
                        !name.startsWith("assets/qz_uilib/runtime/font/")
                                && !name.startsWith("assets/qz_uilib/fonts/")
                                && !name.startsWith("assets/qz_uilib/unicode/")
                                && !name.startsWith("assets/qz_uilib/math/"),
                        "MC成品重复持有独立模块资源：" + name);
            }
        }
    }

    private void verifyDesktop(File file) throws Exception {
        try (JarFile jar = new JarFile(file)) {
            require(
                    jar.getJarEntry("club/heiqi/uilib/host/desktop/DesktopApplication.class")
                            != null,
                    "桌面入口缺失");
            require(
                    jar.getJarEntry("club/heiqi/uilib/host/client/gl/GlFontIrisProducer.class")
                                    == null
                            && jar.getJarEntry(
                                            "club/heiqi/uilib/host/client/gl/GlFontProgramLayout.class")
                                    == null
                            && jar.getJarEntry(
                                            "club/heiqi/uilib/host/client/font/context/AngelicaMatrixStateProvider.class")
                                    == null,
                    "桌面组合不应包含具体MC shader兼容实现");
            require(
                    jar.getJarEntry("org/lwjgl/glfw/GLFW.class") != null
                            && jar.getJarEntry("org/lwjgl/opengl/GL.class") != null,
                    "桌面窗口/GL绑定缺失");
            Enumeration<JarEntry> entries = jar.entries();
            boolean nativeLibrary = false;
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                nativeLibrary |=
                        name.endsWith(".dll") || name.endsWith(".so") || name.endsWith(".dylib");
                require(
                        !name.startsWith("assets/qz_uilib/runtime/font/")
                                && !name.startsWith("assets/qz_uilib/fonts/")
                                && !name.startsWith("assets/qz_uilib/unicode/")
                                && !name.startsWith("assets/qz_uilib/math/"),
                        "桌面重复持有核心或字体适配资源：" + name);
                if (!name.endsWith(".class")) continue;
                require(
                        !name.startsWith("club/heiqi/uilib/testing/")
                                && !name.startsWith("club/heiqi/uilib/internal/devtools/")
                                && !name.startsWith(
                                        "club/heiqi/uilib/host/desktop/DesktopVerification"),
                        "桌面成品夹带测试或开发工具：" + name);
                require(
                        !startsWith(name, CORE)
                                && !name.startsWith(ADAPTER)
                                && !name.startsWith(ICU)
                                && !name.startsWith(YAML)
                                && !name.startsWith(MARKDOWN)
                                && !name.startsWith(AUTOLINK)
                                && !name.startsWith(HTML)
                                && !name.startsWith("org/commonmark/")
                                && !name.startsWith("org/nibor/autolink/")
                                && !name.startsWith("org/jsoup/")
                                && !name.startsWith("com/ibm/icu/")
                                && !name.startsWith("org/yaml/snakeyaml/"),
                        "桌面重复定义独立模块：" + name);
                verifyTypeReference(name, name);
                try (InputStream input = jar.getInputStream(entry)) {
                    verifyBytecode(input, name);
                }
            }
            require(nativeLibrary, "桌面成品缺少当前平台native");
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
        String[] utf8 = new String[count];
        List<Integer> typeReferences = new ArrayList<Integer>();
        // 检查真实类符号与描述符，不把 YAML 为反射保留的任意类名字符串当作平台链接。
        // 类常量和 NameAndType 同时覆盖方法体中的调用，不能只检查公开签名。
        for (int i = 1; i < count; i++) {
            switch (data.readUnsignedByte()) {
                case 1:
                    utf8[i] = data.readUTF();
                    break;
                case 3:
                case 4:
                    data.readInt();
                    break;
                case 5:
                case 6:
                    data.readLong();
                    i++;
                    break;
                case 7:
                case 16:
                    typeReferences.add(data.readUnsignedShort());
                    break;
                case 8:
                case 19:
                case 20:
                    data.readUnsignedShort();
                    break;
                case 12:
                    data.readUnsignedShort();
                    typeReferences.add(data.readUnsignedShort());
                    break;
                case 9:
                case 10:
                case 11:
                case 17:
                case 18:
                    data.readInt();
                    break;
                case 15:
                    data.readUnsignedByte();
                    data.readUnsignedShort();
                    break;
                default:
                    throw new AssertionError("未知常量池项：" + name);
            }
        }
        for (Integer reference : typeReferences) verifyTypeReference(utf8[reference], name);
        data.readUnsignedShort(); // access_flags
        data.readUnsignedShort(); // this_class
        data.readUnsignedShort(); // super_class
        int interfaces = data.readUnsignedShort();
        for (int i = 0; i < interfaces; i++) data.readUnsignedShort();
        // 未在方法体中使用的字段/方法类型也属于链接边界。
        verifyMemberTypes(data, utf8, name);
        verifyMemberTypes(data, utf8, name);
        verifyAttributes(data, utf8, name);
    }

    private void verifyTypeReference(String value, String name) {
        String[] forbiddenTypes =
                desktopMode
                        ? new String[] {
                            "net/minecraft/",
                            "net/minecraftforge/",
                            "cpw/mods/",
                            "me/eigenraven/",
                            "com/ibm/icu/",
                            "org/yaml/snakeyaml/",
                            "org/commonmark/",
                            "org/nibor/autolink/",
                            "org/jsoup/"
                        }
                        : new String[] {
                            "net/minecraft/",
                            "net/minecraftforge/",
                            "cpw/mods/",
                            "org/lwjgl/",
                            "club/heiqi/uilib/host/"
                        };
        for (String forbidden : forbiddenTypes) {
            // 核心供应的私有重定位引用有效；原始依赖类型不能由开发classpath补齐。
            String checked =
                    desktopMode
                                    && (forbidden.equals("com/ibm/icu/")
                                            || forbidden.equals("org/yaml/snakeyaml/")
                                            || forbidden.equals("org/commonmark/")
                                            || forbidden.equals("org/nibor/autolink/")
                                            || forbidden.equals("org/jsoup/"))
                            ? value.replace(
                                    "club/heiqi/uilib/shadow/" + forbidden, "uilib-private/")
                            : value;
            require(!checked.contains(forbidden), "成品字节码引用边界外实现：" + name + " -> " + value);
        }
    }

    private void verifyMemberTypes(DataInputStream data, String[] utf8, String name)
            throws Exception {
        int members = data.readUnsignedShort();
        for (int i = 0; i < members; i++) {
            data.readUnsignedShort(); // access_flags
            data.readUnsignedShort(); // name_index
            verifyTypeReference(utf8[data.readUnsignedShort()], name);
            verifyAttributes(data, utf8, name);
        }
    }

    private void verifyAttributes(DataInputStream data, String[] utf8, String name)
            throws Exception {
        int count = data.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            String attribute = utf8[data.readUnsignedShort()];
            int length = data.readInt();
            require(length >= 0, "无效属性长度：" + name);
            byte[] bytes = new byte[length];
            data.readFully(bytes);
            DataInputStream body = new DataInputStream(new ByteArrayInputStream(bytes));
            if ("Signature".equals(attribute)) {
                verifyTypeReference(utf8[body.readUnsignedShort()], name);
            } else if ("RuntimeVisibleAnnotations".equals(attribute)
                    || "RuntimeInvisibleAnnotations".equals(attribute)) {
                verifyAnnotations(body, utf8, name);
            } else if ("RuntimeVisibleParameterAnnotations".equals(attribute)
                    || "RuntimeInvisibleParameterAnnotations".equals(attribute)) {
                int parameters = body.readUnsignedByte();
                for (int j = 0; j < parameters; j++) verifyAnnotations(body, utf8, name);
            } else if ("RuntimeVisibleTypeAnnotations".equals(attribute)
                    || "RuntimeInvisibleTypeAnnotations".equals(attribute)) {
                int annotations = body.readUnsignedShort();
                for (int j = 0; j < annotations; j++) {
                    skipAnnotationTarget(body, name);
                    skipFully(body, body.readUnsignedByte() * 2);
                    verifyAnnotation(body, utf8, name);
                }
            } else if ("AnnotationDefault".equals(attribute)) {
                verifyAnnotationValue(body, utf8, name);
            } else if ("Code".equals(attribute)) {
                body.readUnsignedShort(); // max_stack
                body.readUnsignedShort(); // max_locals
                skipFully(body, Integer.toUnsignedLong(body.readInt()));
                skipFully(body, body.readUnsignedShort() * 8L);
                verifyAttributes(body, utf8, name);
            } else if ("LocalVariableTable".equals(attribute)
                    || "LocalVariableTypeTable".equals(attribute)) {
                int variables = body.readUnsignedShort();
                for (int j = 0; j < variables; j++) {
                    skipFully(body, 6); // start_pc、length、name_index
                    verifyTypeReference(utf8[body.readUnsignedShort()], name);
                    body.readUnsignedShort(); // index
                }
            }
        }
    }

    private void verifyAnnotations(DataInputStream data, String[] utf8, String name)
            throws Exception {
        int count = data.readUnsignedShort();
        for (int i = 0; i < count; i++) verifyAnnotation(data, utf8, name);
    }

    private void verifyAnnotation(DataInputStream data, String[] utf8, String name)
            throws Exception {
        verifyTypeReference(utf8[data.readUnsignedShort()], name);
        int values = data.readUnsignedShort();
        for (int i = 0; i < values; i++) {
            data.readUnsignedShort(); // element_name_index
            verifyAnnotationValue(data, utf8, name);
        }
    }

    private void verifyAnnotationValue(DataInputStream data, String[] utf8, String name)
            throws Exception {
        switch (data.readUnsignedByte()) {
            case 'e':
                verifyTypeReference(utf8[data.readUnsignedShort()], name);
                data.readUnsignedShort(); // const_name_index
                break;
            case 'c':
                verifyTypeReference(utf8[data.readUnsignedShort()], name);
                break;
            case '@':
                verifyAnnotation(data, utf8, name);
                break;
            case '[':
                int count = data.readUnsignedShort();
                for (int i = 0; i < count; i++) verifyAnnotationValue(data, utf8, name);
                break;
            case 'B':
            case 'C':
            case 'D':
            case 'F':
            case 'I':
            case 'J':
            case 'S':
            case 'Z':
            case 's':
                // 包括注解字符串在内的值不是类型符号，不按其文本推断平台依赖。
                data.readUnsignedShort();
                break;
            default:
                throw new AssertionError("未知注解值：" + name);
        }
    }

    private void skipAnnotationTarget(DataInputStream data, String name) throws Exception {
        int target = data.readUnsignedByte();
        switch (target) {
            case 0x00:
            case 0x01:
            case 0x16:
                skipFully(data, 1);
                break;
            case 0x10:
            case 0x11:
            case 0x12:
            case 0x17:
            case 0x42:
            case 0x43:
            case 0x44:
            case 0x45:
            case 0x46:
                skipFully(data, 2);
                break;
            case 0x13:
            case 0x14:
            case 0x15:
                break;
            case 0x40:
            case 0x41:
                skipFully(data, data.readUnsignedShort() * 6L);
                break;
            case 0x47:
            case 0x48:
            case 0x49:
            case 0x4A:
            case 0x4B:
                skipFully(data, 3);
                break;
            default:
                throw new AssertionError("未知类型注解目标：" + name);
        }
    }

    private void skipFully(DataInputStream data, long remaining) throws Exception {
        while (remaining > 0) {
            int skipped = data.skipBytes((int) Math.min(remaining, Integer.MAX_VALUE));
            if (skipped == 0) {
                data.readByte();
                skipped = 1;
            }
            remaining -= skipped;
        }
    }
}
