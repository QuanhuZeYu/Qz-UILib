package club.heiqi.uilib.ui.render;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

/**
 * 磨玻璃着色器 uniform / varying 交叉契约测试。
 *
 * <p>背景：GLSL 里声明了但宿主从未赋值的 uniform 不会报错，只会保持默认 0。
 * 2026-09-01 材质升级过程中真实踩到——片元声明 {@code panelSizePx}、宿主赋值
 * {@code panelSize}，名字对不上导致该 uniform 恒为 0，边缘亮边会铺满整块面板
 * 且无任何异常。这类"静默错值"比崩溃更难查，故按源码契约锚定：</p>
 *
 * <ul>
 *   <li>两阶段声明的每个 uniform，必须被 {@link UiBackdropFilterRenderer} 显式赋值；</li>
 *   <li>跨阶段共享的同名 uniform 必须类型一致（GLSL 规定同名异型是非法的）；</li>
 *   <li>片元使用的每个 varying 必须由顶点着色器写出。</li>
 * </ul>
 *
 * <p>刻意不用正则与反斜杠转义，纯 indexOf/substring 逐行扫描，避免跨语言写正则
 * 把转义吃掉（见仓库踩坑记录）。</p>
 */
public class UiBackdropShaderContractTest {

    private static final String FRAG = "src/main/resources/shader/uiBackdropF.frag";
    private static final String VERT = "src/main/resources/shader/uiBackdropV.vert";
    private static final String RENDERER = "src/main/java/club/heiqi/uilib/ui/render/UiBackdropFilterRenderer.java";

    /** 收集单个 shader 文件里的 uniform 声明（名称 -> 类型）。 */
    private static Set<String> collectUniforms(List<String> lines, List<String> outTypes) throws IOException {
        Set<String> names = new LinkedHashSet<String>();
        for (String raw : lines) {
            String line = raw.trim();
            if (!line.startsWith("uniform ")) {
                continue;
            }
            String body = line.substring("uniform ".length()).trim();
            int space = body.indexOf(' ');
            assertTrue("uniform 声明应为 \"<type> <name>\"", space > 0);
            String type = body.substring(0, space).trim();
            String name = body.substring(space + 1).trim();
            int semicolon = name.indexOf(';');
            if (semicolon >= 0) {
                name = name.substring(0, semicolon).trim();
            }
            assertFalse("uniform 名称为空: " + line, name.isEmpty());
            names.add(name);
            outTypes.add(type + " " + name);
        }
        return names;
    }

    private static List<String> readLines(String relativePath) throws IOException {
        return Files.readAllLines(resolveExisting(relativePath), StandardCharsets.UTF_8);
    }

    @Test
    public void everyDeclaredUniformIsAssignedByRenderer() throws IOException {
        List<String> fragTypes = new ArrayList<String>();
        List<String> vertTypes = new ArrayList<String>();
        Set<String> uniforms = collectUniforms(readLines(FRAG), fragTypes);
        uniforms.addAll(collectUniforms(readLines(VERT), vertTypes));

        String renderer = String.join(String.valueOf((char) 10), readLines(RENDERER));
        List<String> unset = new ArrayList<String>();
        for (String uniform : uniforms) {
            if (!renderer.contains("\"" + uniform + "\"")) {
                unset.add(uniform);
            }
        }
        assertTrue("以下 shader uniform 从未被宿主赋值，会以默认 0 静默生效: " + unset, unset.isEmpty());
    }

    @Test
    public void sharedUniformsHaveIdenticalTypesAcrossStages() throws IOException {
        List<String> fragTypes = new ArrayList<String>();
        List<String> vertTypes = new ArrayList<String>();
        collectUniforms(readLines(FRAG), fragTypes);
        collectUniforms(readLines(VERT), vertTypes);

        List<String> conflicts = new ArrayList<String>();
        for (String vertDecl : vertTypes) {
            String name = vertDecl.substring(vertDecl.indexOf(' ') + 1);
            for (String fragDecl : fragTypes) {
                if (!fragDecl.substring(fragDecl.indexOf(' ') + 1).equals(name)) {
                    continue;
                }
                if (!fragDecl.equals(vertDecl)) {
                    conflicts.add(name + ": vert=\"" + vertDecl + "\" frag=\"" + fragDecl + "\"");
                }
            }
        }
        assertTrue("同名 uniform 在两阶段类型不一致（GLSL 非法）: " + conflicts, conflicts.isEmpty());
    }

    @Test
    public void fragmentVaryingsAreAllWrittenByVertexShader() throws IOException {
        Set<String> fragVaryings = new LinkedHashSet<String>();
        for (String raw : readLines(FRAG)) {
            String line = raw.trim();
            if (line.startsWith("varying ")) {
                String name = line.substring("varying ".length()).trim();
                int space = name.indexOf(' ');
                assertTrue("varying 声明应为 \"<type> <name>\"", space > 0);
                fragVaryings.add(name.substring(space + 1).replace(";", "").trim());
            }
        }
        List<String> vertLines = readLines(VERT);
        StringBuilder vert = new StringBuilder();
        for (String line : vertLines) {
            vert.append(line);
        }
        List<String> missing = new ArrayList<String>();
        for (String varying : fragVaryings) {
            if (vert.indexOf(varying + " =") < 0 && vert.indexOf(varying + "=") < 0) {
                missing.add(varying);
            }
        }
        assertTrue("片元用到的 varying 未被顶点着色器写出: " + missing, missing.isEmpty());
    }

    /** 兼容不同 Gradle 测试工作目录：优先相对路径，必要时逐级向上查找。 */
    private static Path resolveExisting(String relativePath) {
        Path direct = Paths.get(relativePath);
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        fail("找不到 " + relativePath);
        return direct;
    }
}
