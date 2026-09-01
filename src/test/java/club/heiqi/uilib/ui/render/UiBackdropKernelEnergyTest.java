package club.heiqi.uilib.ui.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/**
 * 磨玻璃 shader 卷积核能量守恒契约测试。
 *
 * <p>卷积核权重和必须恒为 1：磨玻璃是亮度保持操作。柔化提交 e5a6b2ae
 * 重写 13 抽头核时把权重和漂移到 1.12（0.24+0.10*4+0.07*4+0.05*4），导致
 * 磨玻璃区域整体过曝 12%、高光处 clamp 偏色；而同一体系内 CPU 侧可分离核
 * SnapshotFilterPassRenderer.FILTER_BLUR_SAMPLES 与宿主级
 * UiHostBackgroundBlurRenderer（除以 totalWeight）都是归一的，
 * 旧核（柔化前）权重和亦为 1.02——证明归一是本意而非风格选择。</p>
 *
 * <p>本测试按源码契约锚定：逐行解析 uiBackdropF.frag 的 texture2D 抽头，
 * 提取每抽头权重（形如 (n.0 / d.0) 分式或裸小数），断言抽头数与权重和。
 * 2026-09-01 质感升级把规则核（十字+对角）换成 13 抽头 Poisson 盘以消除大半径
 * 下的方向性拉丝，抽头数不变、权重和仍须为 1；今后改盘位或权重必须同步本契约。</p>
 */
public class UiBackdropKernelEnergyTest {

    /** 期望抽头数：中心 1 + Poisson 盘内环 4 + 外环 8。 */
    private static final int EXPECTED_TAP_COUNT = 13;

    /** 抽头行标记（每个 texture2D(mainTex, 采样即一个抽头）。 */
    private static final String TAP_MARKER = "texture2D(mainTex,";

    /** 磨玻璃 shader 权重和必须为 1（能量守恒），且抽头数锁定为 13。 */
    @Test
    public void backdropKernelWeightsSumToUnity() throws IOException {
        Path frag = resolveFragPath();
        String source = new String(Files.readAllBytes(frag), StandardCharsets.UTF_8);
        double total = 0.0D;
        int taps = 0;
        for (String rawLine : source.split("\n")) {
            String line = rawLine.trim();
            int marker = line.indexOf(TAP_MARKER);
            if (marker < 0) {
                continue;
            }
            taps++;
            int star = line.lastIndexOf("* ");
            assertTrue("抽头行缺少权重乘数: " + line, star > marker);
            String weight = line.substring(star + 2).trim();
            if (weight.startsWith("(")) {
                int slash = weight.indexOf('/');
                int close = weight.indexOf(')');
                assertTrue("分式权重格式应为 (n.0 / d.0): " + line, slash > 1 && close > slash);
                double numerator = Double.parseDouble(weight.substring(1, slash).trim());
                double denominator = Double.parseDouble(weight.substring(slash + 1, close).trim());
                total += numerator / denominator;
            } else {
                int semicolon = weight.indexOf(';');
                if (semicolon > 0) {
                    weight = weight.substring(0, semicolon);
                }
                total += Double.parseDouble(weight.trim());
            }
        }
        assertEquals("磨玻璃核抽头数变化需同步更新本契约与 shader 注释", EXPECTED_TAP_COUNT, taps);
        assertEquals("磨玻璃卷积核权重和必须为 1（亮度保持），当前=" + total,
                1.0D, total, 1.0e-9);
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
