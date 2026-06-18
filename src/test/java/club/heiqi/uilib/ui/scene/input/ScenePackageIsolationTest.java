package club.heiqi.uilib.ui.scene.input;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * I10 包隔离红线单元测试。
 *
 * <p>验证 src/main/java/club/heiqi/uilib/ui/scene/input/ 下的所有核心源文件
 * 不引入任何平台绑定或 Minecraft 依赖，确保输入层与平台完全解耦。</p>
 *
 * <p>红线规则：任何非注释代码行中不得出现以下平台包前缀（不仅限于 import 行，
 * 也包括全限定名直接引用）：</p>
 * <ul>
 *   <li>org.lwjgl</li>
 *   <li>org.lwjglx</li>
 *   <li>net.minecraft</li>
 *   <li>net.minecraftforge</li>
 *   <li>club.heiqi.uilib.ui.event</li>
 * </ul>
 */
public class ScenePackageIsolationTest {

    /**
     * 禁止的平台包前缀正则。
     * 匹配形式包括：
     * <ul>
     *   <li>import 语句：import org.lwjgl.xxx</li>
     *   <li>全限定名引用：org.lwjgl.xxx.yyy（类型注解、参数声明等）</li>
     * </ul>
     * 跳过以 // 或 * 开头的纯注释行。
     */
    private static final Pattern FORBIDDEN_PLATFORM_REF = Pattern.compile(
            "(org\\.lwjgl|org\\.lwjglx|net\\.minecraft|net\\.minecraftforge|club\\.heiqi\\.uilib\\.ui\\.event)\\.");

    /**
     * 验证：input 包及其子包下所有 .java 源文件不包含任何禁止的平台引用。
     */
    @Test
    public void shouldHaveNoForbiddenImports() throws IOException {
        // 源文件所在目录（相对于项目根目录）
        Path inputDir = Paths.get("src", "main", "java", "club", "heiqi", "uilib", "ui", "scene", "input");

        Assert.assertTrue("input 源文件目录应存在", Files.isDirectory(inputDir));

        List<Path> javaFiles;
        try (Stream<Path> files = Files.walk(inputDir)) {
            javaFiles = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }

        Assert.assertTrue("input 包下应有至少 14 个 .java 文件（含新增 FocusManager 等 I4a 文件）",
                javaFiles.size() >= 14);

        for (Path javaFile : javaFiles) {
            List<String> lines = Files.readAllLines(javaFile);
            int lineNum = 1;
            for (String line : lines) {
                // 跳过纯注释行
                String trimmed = line.trim();
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                    lineNum++;
                    continue;
                }
                if (FORBIDDEN_PLATFORM_REF.matcher(line).find()) {
                    Assert.fail("文件 " + javaFile.getFileName()
                            + " 第 " + lineNum + " 行包含禁止的平台引用：\"" + line + "\"");
                }
                lineNum++;
            }
        }
    }
}
