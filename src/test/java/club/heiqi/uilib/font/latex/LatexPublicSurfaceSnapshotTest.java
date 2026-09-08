package club.heiqi.uilib.font.latex;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/**
 * LaTeX 模块公共面常驻锁：包内公共类型「全成员」快照 + 跨包接缝成员存在性。
 *
 * <p><b>为什么要有本锁</b>：B0–B5 的公共增量原先只有一次性构建产物
 * （{@code build/reports/latex-plan-completion/integration-clip/api/verification.json}），
 * 仓库内既无生成脚本也无常驻测试，且那份台账只判「旧成员缺失」——新增公共成员不会被拦下。
 * 本锁把两侧都钉住：包内任何类型的新增/删除/可见性变化、任何公共或受保护成员的增删改，
 * 以及跨包接缝成员消失，都会让快照失配。</p>
 *
 * <p><b>口径</b>：只数<b>声明</b>成员（{@code getDeclared*} 过滤 public/protected），
 * 不并入继承成员；嵌套类型独立记账；不排除 synthetic/bridge（与 {@code javap -public} 同口径）。
 * 成员行只记录二进制类型名（{@code Class.getName()}），不记录泛型字符串——后者在不同 JDK 上
 * 格式可能漂移，会把跨 JDK 的绿色 CI 变成假红。</p>
 *
 * <p><b>快照生成</b>：资源缺失时本测试写入当前事实并失败一次；复核 diff 后重跑即绿。
 * 有意变更公共面时删除该资源重新生成，或把 {@code qz.latex.surface.update=true} 作为
 * 系统属性传给测试 JVM。</p>
 *
 * <p>纯反射：零字体注册、零 GL、不启动场景。</p>
 */
public class LatexPublicSurfaceSnapshotTest {

    /** 只扫描 LaTeX 模块自有的三个包（含嵌套类型与包内非公共类型，后者用于拦「包内变公共」）。 */
    private static final String[] SCANNED_PACKAGES = {
        "club.heiqi.uilib.font.latex",
        "club.heiqi.uilib.font.latex.layout",
        "club.heiqi.uilib.font.latex.node",
    };

    /** 跨包接缝：LaTeX 契约依赖、但类本身属于其他包的成员（正向存在性）。 */
    private static final String[] SEAM_MEMBERS = {
        "club.heiqi.uilib.font.layout.TextSegment#forLatex(java.lang.String,"
                + "club.heiqi.uilib.font.layout.TextStyle,club.heiqi.uilib.font.latex.MathStyleOverride)",
        "club.heiqi.uilib.font.layout.TextSegment#getLatexMathStyle()",
        "club.heiqi.uilib.font.layout.TextSegment#withStyle(club.heiqi.uilib.font.layout.TextStyle)",
        "club.heiqi.uilib.font.layout.TextLayoutService#getLatexBox("
                + "club.heiqi.uilib.font.layout.TextSegment,int)",
        "club.heiqi.uilib.font.glyph.GlyphRequestToken#forMathGlyph(int,long,"
                + "club.heiqi.uilib.font.latex.layout.MathGlyphRef,int,int)",
        "club.heiqi.uilib.font.glyph.GlyphRequestToken#getKind()",
        "club.heiqi.uilib.font.glyph.GlyphRequestToken#getMathGlyphRef()",
        "club.heiqi.uilib.font.glyph.GlyphRequestToken#getRasterSize()",
        "club.heiqi.uilib.font.glyph.GlyphRequestToken#getTileIndex()",
        "club.heiqi.uilib.font.glyph.GlyphGenerationTask#forMathGlyph(int,"
                + "club.heiqi.uilib.font.latex.layout.MathGlyphRef,int,int,"
                + "club.heiqi.uilib.font.glyph.GlyphGenerationPriority)",
        "club.heiqi.uilib.font.glyph.GlyphGenerationTask#getKind()",
        "club.heiqi.uilib.font.glyph.GlyphGenerationTask#getMathGlyphRef()",
        "club.heiqi.uilib.font.glyph.GlyphGenerationTask#getRasterSize()",
        "club.heiqi.uilib.font.glyph.GlyphGenerationTask#getTileIndex()",
        "club.heiqi.uilib.font.glyph.GlyphGenerationResult#forMathGlyph("
                + "club.heiqi.uilib.font.glyph.GlyphRequestToken,java.awt.image.BufferedImage,"
                + "club.heiqi.uilib.font.glyph.GlyphInfo)",
        "club.heiqi.uilib.font.glyph.GlyphGenerationResult#getKind()",
        "club.heiqi.uilib.font.glyph.GlyphGenerationResult#getMathGlyphRef()",
        "club.heiqi.uilib.font.glyph.GlyphGenerationResult#getRasterSize()",
        "club.heiqi.uilib.font.glyph.GlyphGenerationResult#getTileIndex()",
        "club.heiqi.uilib.font.glyph.GlyphGenerationResult#getMathGlyphInfo()",
        "club.heiqi.uilib.font.glyph.GlyphInfo#getKind()",
        "club.heiqi.uilib.font.glyph.GlyphInfo#getMathGlyphRef()",
        "club.heiqi.uilib.font.glyph.GlyphInfo#copyOf(club.heiqi.uilib.font.glyph.GlyphInfo)",
        "club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine$Kind#MATH_DISPLAY",
    };

    private static final String RESOURCE = "latex-public-surface.snapshot";
    private static final Path RESOURCE_PATH = Paths.get("src", "test", "resources", "club", "heiqi",
            "uilib", "font", "latex", RESOURCE);

    @Test
    public void publicSurfaceMatchesSnapshot() throws Exception {
        String actual = render();
        if (Boolean.getBoolean("qz.latex.surface.update") || !RESOURCE_PATH.toFile().isFile()) {
            Files.createDirectories(RESOURCE_PATH.getParent());
            Files.write(RESOURCE_PATH, actual.getBytes(StandardCharsets.UTF_8));
            Assert.fail("LaTeX 公共面快照已按当前事实写入 " + RESOURCE_PATH
                    + "；复核 diff 后重跑本测试即绿。");
        }
        Assert.assertEquals("LaTeX 公共面快照失配：若为本批有意变更，请更新快照并复核 diff",
                readSnapshot(), actual);
    }

    private static String readSnapshot() throws Exception {
        InputStream input = LatexPublicSurfaceSnapshotTest.class.getResourceAsStream(RESOURCE);
        Assert.assertNotNull("缺少快照资源 " + RESOURCE, input);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            input.close();
        }
    }

    private static String render() throws Exception {
        StringBuilder out = new StringBuilder();
        out.append("# LaTeX 公共面快照；缺失或 qz.latex.surface.update=true 时自动重建。\n");
        for (String name : scanPackages()) {
            Class<?> type = Class.forName(name, false, loader());
            int modifiers = type.getModifiers();
            out.append('\n').append("## ").append(name).append('\n');
            out.append("visibility ").append(visibility(modifiers)).append('\n');
            if (!isExposed(modifiers)) {
                continue;
            }
            List<String> members = new ArrayList<String>();
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                if (isExposed(constructor.getModifiers())) {
                    members.add("ctor " + Modifier.toString(constructor.getModifiers()
                            & Modifier.constructorModifiers()) + " " + parameters(constructor.getParameterTypes()));
                }
            }
            for (Method method : type.getDeclaredMethods()) {
                if (isExposed(method.getModifiers())) {
                    members.add("method " + Modifier.toString(method.getModifiers()
                            & Modifier.methodModifiers()) + " " + method.getReturnType().getName()
                            + " " + method.getName() + parameters(method.getParameterTypes()));
                }
            }
            for (Field field : type.getDeclaredFields()) {
                if (isExposed(field.getModifiers())) {
                    members.add("field " + Modifier.toString(field.getModifiers()
                            & Modifier.fieldModifiers()) + " " + field.getType().getName()
                            + " " + field.getName());
                }
            }
            Collections.sort(members);
            for (String member : members) {
                out.append(member).append('\n');
            }
        }
        out.append("\n## seam\n");
        List<String> seams = new ArrayList<String>();
        for (String seam : SEAM_MEMBERS) {
            seams.add(seam + (seamExists(seam) ? " present" : " MISSING"));
        }
        Collections.sort(seams);
        for (String seam : seams) {
            out.append(seam).append('\n');
        }
        return out.toString();
    }

    private static List<String> scanPackages() throws Exception {
        File root = mainRoot();
        List<String> names = new ArrayList<String>();
        for (String packageName : SCANNED_PACKAGES) {
            File directory = new File(root, packageName.replace('.', '/'));
            if (directory.isDirectory()) {
                collect(directory, packageName, names);
            }
        }
        Collections.sort(names);
        Assert.assertFalse("未扫描到任何 LaTeX 公共类型，classpath 布局可能已变", names.isEmpty());
        return names;
    }

    /** main classpath 根目录：用包内主源类定位，避免把 src/test 下的同名包测试类算进公共面。 */
    private static File mainRoot() throws Exception {
        URL location = LatexNode.class.getProtectionDomain().getCodeSource().getLocation();
        return new File(location.toURI());
    }

    private static void collect(File directory, String packageName, List<String> names) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                collect(file, packageName + "." + file.getName(), names);
            } else if (file.getName().endsWith(".class") && !"package-info.class".equals(file.getName())) {
                names.add(packageName + "." + file.getName().substring(0, file.getName().length() - 6));
            }
        }
    }

    private static boolean seamExists(String seam) throws Exception {
        int separator = seam.indexOf('#');
        Class<?> type = Class.forName(seam.substring(0, separator), false, loader());
        String description = seam.substring(separator + 1);
        int parenthesis = description.indexOf('(');
        if (parenthesis < 0) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getName().equals(description) && isExposed(field.getModifiers())) {
                    return true;
                }
            }
            return false;
        }
        String name = description.substring(0, parenthesis);
        Class<?>[] parameters = parseParameters(
                description.substring(parenthesis + 1, description.length() - 1));
        if ("<init>".equals(name)) {
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                if (isExposed(constructor.getModifiers())
                        && Arrays.equals(constructor.getParameterTypes(), parameters)) {
                    return true;
                }
            }
            return false;
        }
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name) && isExposed(method.getModifiers())
                    && Arrays.equals(method.getParameterTypes(), parameters)) {
                return true;
            }
        }
        return false;
    }

    private static Class<?>[] parseParameters(String text) throws Exception {
        if (text.isEmpty()) {
            return new Class<?>[0];
        }
        String[] names = text.split(",");
        Class<?>[] types = new Class<?>[names.length];
        for (int i = 0; i < names.length; i++) {
            types[i] = primitiveOrClass(names[i].trim());
        }
        return types;
    }

    /** 参数类型按 {@code Class.getName()} 记账，基本类型名必须回映射，不能交给 Class.forName。 */
    private static Class<?> primitiveOrClass(String name) throws Exception {
        if ("int".equals(name)) { return int.class; }
        if ("long".equals(name)) { return long.class; }
        if ("float".equals(name)) { return float.class; }
        if ("double".equals(name)) { return double.class; }
        if ("boolean".equals(name)) { return boolean.class; }
        if ("byte".equals(name)) { return byte.class; }
        if ("short".equals(name)) { return short.class; }
        if ("char".equals(name)) { return char.class; }
        if ("void".equals(name)) { return void.class; }
        return Class.forName(name, false, loader());
    }

    private static boolean isExposed(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }

    private static String visibility(int modifiers) {
        if (Modifier.isPublic(modifiers)) {
            return "public";
        }
        if (Modifier.isProtected(modifiers)) {
            return "protected";
        }
        return Modifier.isPrivate(modifiers) ? "private" : "package";
    }

    private static String parameters(Class<?>[] types) {
        StringBuilder out = new StringBuilder("(");
        for (int i = 0; i < types.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(types[i].getName());
        }
        return out.append(')').toString();
    }

    private static ClassLoader loader() {
        return LatexPublicSurfaceSnapshotTest.class.getClassLoader();
    }
}
