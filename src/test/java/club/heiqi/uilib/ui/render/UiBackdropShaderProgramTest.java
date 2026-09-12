package club.heiqi.uilib.ui.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Assert;
import org.junit.Test;

/**
 * {@code UiBackdropShaderProgram} 的资源与抽头预算变体契约测试。
 *
 * <p>档位（{@link BackdropQuality}）通过同一份 frag 资源 + 注入 {@code #define UIB_TAP_BUDGET}
 * 得到两套卷积核。这里钉住三件事：<strong>同一预算永远复用同一实例</strong>（进程级最多两个
 * GL program，热路径不得每次新建）、<strong>注入点在 #version 之后且早于任何抽头条件</strong>
 * （否则宏未定义时 {@code #if} 求值不可控）、<strong>资源被单独编译时仍有完整档默认</strong>。</p>
 */
public class UiBackdropShaderProgramTest {

    /**
     * 验证 UI 磨玻璃 shader 资源会随主资源打包。
     */
    @Test
    public void shouldExposeBackdropShaderResources() {
        Assert.assertNotNull(UiBackdropShaderProgram.class.getResourceAsStream("/shader/uiBackdropV.vert"));
        Assert.assertNotNull(UiBackdropShaderProgram.class.getResourceAsStream("/shader/uiBackdropF.frag"));
    }

    /** 同一预算复用同一实例；未知预算回落完整档（宁多采样，不因参数错值静默降质）。 */
    @Test
    public void programForCachesOneInstancePerBudgetAndFallsBackToFull() {
        assertSame(UiBackdropShaderProgram.programFor(13), UiBackdropShaderProgram.programFor(13));
        assertSame(UiBackdropShaderProgram.programFor(9), UiBackdropShaderProgram.programFor(9));
        assertNotSame(UiBackdropShaderProgram.programFor(13), UiBackdropShaderProgram.programFor(9));

        assertSame("未知预算必须回落完整档", UiBackdropShaderProgram.programFor(13),
                UiBackdropShaderProgram.programFor(7));
        assertSame("未知预算必须回落完整档", UiBackdropShaderProgram.programFor(13),
                UiBackdropShaderProgram.programFor(0));
        assertSame("未知预算必须回落完整档", UiBackdropShaderProgram.programFor(13),
                UiBackdropShaderProgram.programFor(-1));

        assertEquals(13, UiBackdropShaderProgram.programFor(13).getTapBudget());
        assertEquals(9, UiBackdropShaderProgram.programFor(9).getTapBudget());
    }

    /** 注入位置与内容：紧跟 #version，各档注入各自的值。 */
    @Test
    public void tapBudgetDefineIsInjectedRightAfterVersion() {
        String source = "#version 120\n\nvoid main(void) {\n}\n";
        String full = UiBackdropShaderProgram.withTapBudgetDefine(source, 13);
        String eco = UiBackdropShaderProgram.withTapBudgetDefine(source, 9);

        assertEquals("UIB_TAP_BUDGET", UiBackdropShaderProgram.TAP_BUDGET_DEFINE);
        assertTrue("完整档注入 13: " + full, full.startsWith("#version 120\n#define UIB_TAP_BUDGET 13\n"));
        assertTrue("省电档注入 9: " + eco, eco.startsWith("#version 120\n#define UIB_TAP_BUDGET 9\n"));
        assertTrue("原源必须完整保留", full.endsWith(source.substring("#version 120\n".length())));
    }

    /** 真实资源：默认块在 #version 之后，注入的 define 必须早于抽头条件。 */
    @Test
    public void realFragmentSourceKeepsStandaloneDefaultAndInjectionOrder() throws IOException {
        String source = new String(Files.readAllBytes(resolveFragPath()), StandardCharsets.UTF_8);

        assertTrue("资源必须有 #ifndef 默认块（被单独编译时仍是完整档）",
                source.contains("#ifndef UIB_TAP_BUDGET"));
        assertTrue(source.contains("#define UIB_TAP_BUDGET 13"));
        assertTrue(source.contains("#if UIB_TAP_BUDGET >= 13"));

        String injected = UiBackdropShaderProgram.withTapBudgetDefine(source, 9);
        int defineAt = injected.indexOf("#define UIB_TAP_BUDGET 9");
        int conditionalAt = injected.indexOf("#if UIB_TAP_BUDGET");
        assertTrue("注入的 define 必须存在", defineAt > 0);
        assertTrue("注入的 define 必须早于任何抽头条件求值", conditionalAt > defineAt);
        assertTrue("默认块必须晚于注入点（#ifndef 因此不生效）",
                injected.indexOf("#ifndef UIB_TAP_BUDGET") > defineAt);
    }

    /** 首个非空行不是 #version 时必须显式失败（会被 ensureInitialized 转成"程序不可用"）。 */
    @Test
    public void malformedSourceWithoutVersionIsRejected() {
        try {
            UiBackdropShaderProgram.withTapBudgetDefine("void main(void) {}\n", 13);
            fail("缺少 #version 必须抛异常，不得拿着默认预算静默编译");
        } catch (IllegalStateException expected) {
            assertTrue("异常信息应指出 #version: " + expected.getMessage(),
                    expected.getMessage() != null && expected.getMessage().contains("#version"));
        }
    }

    /** 兼容不同 Gradle 测试工作目录：优先根目录相对路径，必要时向上查找。 */
    private static Path resolveFragPath() {
        Path direct = Paths.get("src/main/resources/shader/uiBackdropF.frag");
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("src/main/resources/shader/uiBackdropF.frag");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("找不到 src/main/resources/shader/uiBackdropF.frag");
    }
}
