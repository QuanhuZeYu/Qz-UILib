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

    /** 升级前规则核（十字+对角，权重和已归一）的加权 RMS 半径，作为观感口径基准。 */
    private static final double REFERENCE_KERNEL_RMS = 0.91148D;

    /** 允许的等效模糊强度偏差：5% 以内人眼不可辨，超出即视为改了作者的 blurRadius 口径。 */
    private static final double RMS_TOLERANCE = 0.05D;

    /**
     * 核等效模糊强度契约：抽头权重的加权 RMS 半径乘以抽头半径补偿系数，必须与升级前
     * 规则核一致（正负 5%）。
     *
     * <p>为什么单独锁这个：决定"糊到什么程度"的是核的加权 RMS 半径（积分量），不是最远
     * 抽头距离（极值）。2026-09-01 从规则核换成 Poisson 盘时，按极值比例估的补偿系数
     * （1.25/0.96 约 1.30）与按 RMS 估的（1.394）并不相同；若只锁权重和为 1，改盘位会
     * 静默改变模糊强度而守卫全绿。本测试把能量与等效半径一起锁住。</p>
     */
    @Test
    public void kernelEffectiveRadiusMatchesLegacyCaliber() throws IOException {
        String source = new String(Files.readAllBytes(resolveFragPath()), StandardCharsets.UTF_8);
        double energy = 0.0D;
        double weightSum = 0.0D;
        for (String rawLine : source.split("\n")) {
            String line = rawLine.trim();
            int marker = line.indexOf(TAP_MARKER);
            if (marker < 0) {
                continue;
            }
            double offsetX = 0.0D;
            double offsetY = 0.0D;
            int vecStart = line.indexOf("vec2(", marker);
            if (vecStart > 0) {
                int vecEnd = line.indexOf(')', vecStart);
                String inner = line.substring(vecStart + 5, vecEnd);
                int comma = inner.indexOf(',');
                offsetX = Double.parseDouble(inner.substring(0, comma).trim());
                offsetY = Double.parseDouble(inner.substring(comma + 1).trim());
            }
            int star = line.lastIndexOf("* ");
            String weight = line.substring(star + 2).trim();
            int slash = weight.indexOf("/");
            int close = weight.indexOf(')');
            double w = Double.parseDouble(weight.substring(1, slash).trim())
                    / Double.parseDouble(weight.substring(slash + 1, close).trim());
            weightSum += w;
            energy += w * (offsetX * offsetX + offsetY * offsetY);
        }
        assertEquals("权重和必须为 1，否则 RMS 口径无意义", 1.0D, weightSum, 1.0e-9);
        double diskRms = Math.sqrt(energy / weightSum);
        double effective = diskRms * parseTapRadiusScale(source);
        double drift = effective / REFERENCE_KERNEL_RMS - 1.0D;
        assertTrue("核等效半径漂移 " + (drift * 100.0D) + "%（effective=" + effective
                + "，基准=" + REFERENCE_KERNEL_RMS + "）。改核会静默改变作者侧 blurRadius 的观感，"
                + "确需改动时同步更新基准值并在提交信息里说明。",
                Math.abs(drift) <= RMS_TOLERANCE);
    }

    /** 解析 radiusStep 末尾的抽头半径补偿系数（形如 "... 128.0) * 1.35;"）。 */
    private static double parseTapRadiusScale(String source) {
        int at = source.indexOf("vec2 radiusStep");
        assertTrue("shader 里必须存在 radiusStep 定义", at > 0);
        int end = source.indexOf(';', at);
        String line = source.substring(at, end);
        String clampTail = "128.0)";
        int clampEnd = line.indexOf(clampTail);
        assertTrue("radiusStep 必须以 clamp(blurRadius, 1.0, 128.0) 为基数", clampEnd > 0);
        String rest = line.substring(clampEnd + clampTail.length()).trim();
        assertTrue("radiusStep 必须带显式抽头半径补偿系数，实际=" + rest, rest.startsWith("*"));
        return Double.parseDouble(rest.substring(1).trim());
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
