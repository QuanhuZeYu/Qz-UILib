package club.heiqi.uilib.mixin.early.tesr;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Assert;
import org.junit.Test;

/**
 * TESR 批次提交点世界文字回放围栏的兼容契约测试。
 *
 * <p>钉死两件事：宿主 ABI 只以字符串类名出现（无编译期依赖，Angelica 缺席也能正常运行）；
 * 所有注入点 {@code require = 0}，宿主版本漂移时整段围栏静默不应用，协调器保持 fail-open。</p>
 */
public class TesrTextReplayMixinContractTest {

    private static final Path MAIN_ROOT = Paths.get("src/main/java/club/heiqi/uilib");
    private static final Path MIXIN = MAIN_ROOT.resolve("mixin/early/tesr/MixinAngelicaTesrBatchReplay.java");
    private static final Path COORDINATOR = MAIN_ROOT.resolve("internal/font/tesr/TesrTextReplayCoordinator.java");
    private static final Path PROBE = MAIN_ROOT.resolve(
            "internal/font/tesr/angelica/AngelicaTesrBatchProbe.java");

    /** 宿主类只经字符串目标引用：不新增编译期依赖，Angelica 缺席时 Mixin 不应用。 */
    @Test
    public void hostAbiStaysStringOnly() throws IOException {
        String mixin = source(MIXIN);
        String probe = source(PROBE);

        Assert.assertTrue(mixin.contains(
                "@Mixin(targets = \"com.gtnewhorizons.angelica.rendering.tesr.TesrBatchRenderer\", remap = false)"));
        Assert.assertFalse("混入类不得 import 宿主包", mixin.contains("import com.gtnewhorizons"));
        Assert.assertFalse("探针不得 import 宿主包", probe.contains("import com.gtnewhorizons"));
        Assert.assertTrue(probe.contains("Class.forName(RENDERER_CLASS)"));
    }

    /** 三个注入点都 require = 0：宿主方法缺失时只跳过围栏，不崩、不阻断启动。 */
    @Test
    public void everyInjectionPointIsOptional() throws IOException {
        String mixin = source(MIXIN);
        int injects = occurrences(mixin, "@Inject(");

        Assert.assertEquals(3, injects);
        Assert.assertEquals("全部注入点必须 require = 0", injects, occurrences(mixin, "require = 0"));
        Assert.assertTrue(mixin.contains("method = \"flush\", at = @At(\"RETURN\")"));
        Assert.assertTrue(mixin.contains("method = \"flushAfterDeferred\", at = @At(\"RETURN\")"));
    }

    /** 回放只发生在宿主提交点之后，且捕获以「宿主钩子已安装 + 探针报告有未提交几何」为前提。 */
    @Test
    public void replayFollowsHostCommitAndCaptureStaysFailOpen() throws IOException {
        String coordinator = source(COORDINATOR);

        Assert.assertTrue(coordinator.contains("if (!hostHookInstalled || replaySink == null || uiDeferredScopeActive)"));
        Assert.assertTrue(coordinator.contains("public static void markHostHookInstalled()"));
        Assert.assertTrue("宿主仍有未提交几何时不得回放",
                coordinator.contains("if (probe != null && probe.hasPendingDeferredGeometry()) {"));
    }

    private static String source(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        for (int index = 0; (index = source.indexOf(needle, index)) >= 0; index += needle.length()) {
            count++;
        }
        return count;
    }
}
