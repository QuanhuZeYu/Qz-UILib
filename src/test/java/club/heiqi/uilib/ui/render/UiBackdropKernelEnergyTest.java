package club.heiqi.uilib.ui.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

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
 * <p>2026-09-02 散光调优把规则核（十字+对角）换成 13 抽头向日葵螺旋核
 * （r=sqrt(i/12)*1.6、黄金角 2.39996，13 个连续半径 + 黄金角分布，
 * 双半径盘的"两亮环"即散光源）。2026-09-12 玻璃档位（{@link BackdropQuality}）
 * 把抽头段拆成两支：{@code #if UIB_TAP_BUDGET >= 13}（完整档，13 抽头）与
 * {@code #else}（省电档，9 抽头变体）。本测试按预算分支分别解析并逐项钉住：</p>
 *
 * <ul>
 *   <li>完整档抽头段的<strong>原文（含缩进与空行）</strong>必须与引入档位前的源码逐字节一致
 *       ——这是"默认档可回退"的铁证；</li>
 *   <li>两支的抽头数 = 各自预算，权重和都精确为 1；</li>
 *   <li>省电档的权重与偏移必须等于 Python 复算值（{@code temp/perf-impl-render/kernel-9tap.py}
 *       输出 {@code kernel-9tap.json}），防止有人手改核而不复算；</li>
 *   <li>省电档覆盖半径与完整档同量级、等效模糊强度与完整档一致（1% 内，实测 -0.069%）；</li>
 *   <li>完整档等效半径不漂移（沿用 5% 观感口径锁）。</li>
 * </ul>
 */
public class UiBackdropKernelEnergyTest {

    private static final int FULL_TAP_BUDGET = 13;

    private static final int ECO_TAP_BUDGET = 9;

    /**
     * 引入档位前（抽头段只有 13 抽头一个版本时）的原文，逐字节基准：含 8 空格缩进与
     * 每 4 个抽头之间的空行分隔。任何"顺手重排/重算"完整档核的行为都会在此处变红。
     */
    private static final String[] FROZEN_FULL_TAP_BLOCK = {
            "        blurred *= (161.0 / 1000.0);",
            "",
            "        blurred += texture2D(mainTex, texCoord + lensShift + kernelBasis * vec2(-0.341, 0.312) * radiusStep) * (139.0 / 1000.0);",
            "        blurred += texture2D(mainTex, texCoord + lensShift + kernelBasis * vec2(0.057, -0.651) * radiusStep) * (120.0 / 1000.0);",
            "        blurred += texture2D(mainTex, texCoord + lensShift + kernelBasis * vec2(0.487, 0.635) * radiusStep) * (103.0 / 1000.0);",
            "        blurred += texture2D(mainTex, texCoord + lensShift + kernelBasis * vec2(-0.910, -0.161) * radiusStep) * (89.0 / 1000.0);",
            "",
            "        blurred += texture2D(mainTex, texCoord + lensShift + kernelBasis * vec2(0.871, -0.554) * radiusStep) * (77.0 / 1000.0);",
            "        blurred += texture2D(mainTex, texCoord + lensShift + kernelBasis * vec2(-0.294, 1.093) * radiusStep) * (66.0 / 1000.0);",
            "        blurred += texture2D(mainTex, texCoord + lensShift + kernelBasis * vec2(-0.563, -1.084) * radiusStep) * (57.0 / 1000.0);",
            "        blurred += texture2D(mainTex, texCoord + lensShift + kernelBasis * vec2(1.227, 0.448) * radiusStep) * (49.0 / 1000.0);",
            "",
            "        blurred += texture2D(mainTex, texCoord + lensShift + kernelBasis * vec2(-1.281, 0.529) * radiusStep) * (43.0 / 1000.0);",
            "        blurred += texture2D(mainTex, texCoord + lensShift + kernelBasis * vec2(0.619, -1.323) * radiusStep) * (37.0 / 1000.0);",
            "        blurred += texture2D(mainTex, texCoord + lensShift + kernelBasis * vec2(0.458, 1.462) * radiusStep) * (32.0 / 1000.0);",
            "        blurred += texture2D(mainTex, texCoord + lensShift + kernelBasis * vec2(-1.384, -0.802) * radiusStep) * (27.0 / 1000.0);",
    };

    /** 省电档 9 抽头权重（/1000 整数，和 = 1000）；出处 = kernel-9tap.py 复算输出。 */
    private static final int[] ECO_KERNEL_WEIGHTS_1000 = { 221, 180, 146, 119, 97, 79, 64, 52, 42 };

    /** 省电档 9 抽头偏移（中心 + 8 个向日葵螺旋抽头 r=sqrt(i/8)*1.6、黄金角）；同上。 */
    private static final double[][] ECO_KERNEL_OFFSETS = {
            { 0.0D, 0.0D },
            { -0.417D, 0.382D },
            { 0.070D, -0.797D },
            { 0.596D, 0.778D },
            { -1.114D, -0.197D },
            { 1.067D, -0.679D },
            { -0.360D, 1.338D },
            { -0.690D, -1.328D },
            { 1.503D, 0.549D },
    };

    /** 升级前规则核（十字+对角，权重和已归一）的加权 RMS 半径，作为观感口径基准。 */
    private static final double REFERENCE_KERNEL_RMS = 0.91148D;

    /** 允许的等效模糊强度偏差：5% 以内人眼不可辨，超出即视为改了作者的 blurRadius 口径。 */
    private static final double RMS_TOLERANCE = 0.05D;

    /** 省电档对完整档的等效半径偏差上限：1%（实测 -0.069%，与"只减抽头、不换强度"的设计一致）。 */
    private static final double ECO_RMS_TOLERANCE_VS_FULL = 0.01D;

    /** 省电档对完整档的覆盖半径偏差上限：2%（实测 1.0003 倍，同量级）。 */
    private static final double ECO_COVERAGE_TOLERANCE = 0.02D;

    /** 完整档（13 抽头）抽头位的原文必须与引入档位前逐字节相同。 */
    @Test
    public void fullBudgetTapBlockIsByteIdenticalToPreBudgetSource() throws IOException {
        List<String> rawBlock = fullBranchRawLines(readFrag());

        assertEquals("完整档抽头段必须与引入档位前逐字节一致（含缩进与空行）",
                Arrays.asList(FROZEN_FULL_TAP_BLOCK), rawBlock);
    }

    /** 两个预算档的抽头数与权重和都必须成立（亮度保持操作，不得漂移）。 */
    @Test
    public void everyBudgetKeepsItsTapCountAndUnitWeightSum() throws IOException {
        String source = readFrag();
        List<double[]> fullTaps = kernelTaps(source, FULL_TAP_BUDGET);
        List<double[]> ecoTaps = kernelTaps(source, ECO_TAP_BUDGET);

        assertEquals("完整档抽头数变化需同步更新本契约与 shader 注释", FULL_TAP_BUDGET, fullTaps.size());
        assertEquals("省电档抽头数变化需同步更新本契约与 shader 注释", ECO_TAP_BUDGET, ecoTaps.size());
        assertEquals("完整档卷积核权重和必须为 1（亮度保持），当前=" + weightSum(fullTaps),
                1.0D, weightSum(fullTaps), 1.0e-9D);
        assertEquals("省电档卷积核权重和必须为 1（亮度保持），当前=" + weightSum(ecoTaps),
                1.0D, weightSum(ecoTaps), 1.0e-9D);
    }

    /** 省电档核的权重与偏移必须等于 Python 复算输出，改核必须同步脚本与数字。 */
    @Test
    public void ecoKernelMatchesPythonComputedWeightsAndOffsets() throws IOException {
        List<double[]> taps = kernelTaps(readFrag(), ECO_TAP_BUDGET);

        for (int index = 0; index < taps.size(); index++) {
            double[] tap = taps.get(index);
            assertEquals("第 " + index + " 抽头权重（Python 复算值，/1000）",
                    ECO_KERNEL_WEIGHTS_1000[index] / 1000.0D, tap[2], 1.0e-9D);
            assertEquals("第 " + index + " 抽头 x 偏移（Python 复算值）",
                    ECO_KERNEL_OFFSETS[index][0], tap[0], 1.0e-9D);
            assertEquals("第 " + index + " 抽头 y 偏移（Python 复算值）",
                    ECO_KERNEL_OFFSETS[index][1], tap[1], 1.0e-9D);
        }
    }

    /**
     * 省电档必须保持"覆盖半径同量级 + 等效模糊强度不变"这两条口径。
     *
     * <p>只锁权重和是不够的：把抽头全部缩到中心附近同样能让权重和为 1，但模糊会明显变弱
     * （备选的"截断内圈 8 抽头 + 重归一"方案等效半径就漂了 -13.4%，故未采用）。</p>
     */
    @Test
    public void ecoKernelKeepsCoverageRadiusAndEffectiveBlurStrength() throws IOException {
        String source = readFrag();
        List<double[]> fullTaps = kernelTaps(source, FULL_TAP_BUDGET);
        List<double[]> ecoTaps = kernelTaps(source, ECO_TAP_BUDGET);
        double radiusScale = parseTapRadiusScale(source);

        double fullCoverage = maxRadius(fullTaps);
        double ecoCoverage = maxRadius(ecoTaps);
        assertEquals("完整档最远抽头（覆盖半径，步）", 1.5996D, fullCoverage, 1.0e-4D);
        assertEquals("省电档最远抽头（覆盖半径，步）", 1.6001D, ecoCoverage, 1.0e-4D);
        double coverageRatio = ecoCoverage / fullCoverage;
        assertTrue("省电档覆盖半径必须与完整档同量级，实际比=" + coverageRatio,
                Math.abs(coverageRatio - 1.0D) <= ECO_COVERAGE_TOLERANCE);

        double fullEffective = Math.sqrt(weightedRadiusSquare(fullTaps) / weightSum(fullTaps)) * radiusScale;
        double ecoEffective = Math.sqrt(weightedRadiusSquare(ecoTaps) / weightSum(ecoTaps)) * radiusScale;
        double drift = ecoEffective / fullEffective - 1.0D;
        assertTrue("省电档等效模糊强度漂移 " + (drift * 100.0D) + "%（effective=" + ecoEffective
                        + "，完整档=" + fullEffective + "）。设计前提是「只换抽头预算、不换模糊强度」；"
                        + "确需改动请用 kernel-9tap.py 复算并同步本契约。",
                Math.abs(drift) <= ECO_RMS_TOLERANCE_VS_FULL);
        System.out.println("kernel check: fullCoverage=" + fullCoverage + " ecoCoverage=" + ecoCoverage
                + " fullEffectiveRms=" + fullEffective + " ecoEffectiveRms=" + ecoEffective
                + " drift=" + (drift * 100.0D) + "%");
    }

    /**
     * 完整档等效模糊强度契约：抽头权重的加权 RMS 半径乘以抽头半径补偿系数，必须与升级前
     * 规则核一致（正负 5%）。
     *
     * <p>为什么单独锁这个：决定"糊到什么程度"的是核的加权 RMS 半径（积分量），不是最远
     * 抽头距离（极值）。2026-09-01 从规则核换成 Poisson 盘时，按极值比例估的补偿系数
     * （1.25/0.96 约 1.30）与按 RMS 估的（1.394）并不相同；若只锁权重和为 1，改盘位会
     * 静默改变模糊强度而守卫全绿。本测试把能量与等效半径一起锁住。</p>
     */
    @Test
    public void fullKernelEffectiveRadiusMatchesLegacyCaliber() throws IOException {
        String source = readFrag();
        List<double[]> taps = kernelTaps(source, FULL_TAP_BUDGET);
        double weightSum = weightSum(taps);
        assertEquals("权重和必须为 1，否则 RMS 口径无意义", 1.0D, weightSum, 1.0e-9D);

        double diskRms = Math.sqrt(weightedRadiusSquare(taps) / weightSum);
        double effective = diskRms * parseTapRadiusScale(source);
        double drift = effective / REFERENCE_KERNEL_RMS - 1.0D;
        assertTrue("核等效半径漂移 " + (drift * 100.0D) + "%（effective=" + effective
                        + "，基准=" + REFERENCE_KERNEL_RMS + "）。改核会静默改变作者侧 blurRadius 的观感，"
                        + "确需改动时同步更新基准值并在提交信息里说明。",
                Math.abs(drift) <= RMS_TOLERANCE);
    }

    /** 完整档分支的原文行（{@code #if UIB_TAP_BUDGET >= 13} 与 {@code #else} 之间）。 */
    private static List<String> fullBranchRawLines(String source) {
        List<String> lines = new ArrayList<String>();
        boolean inside = false;
        for (String rawLine : source.split("\n")) {
            String line = rawLine.trim();
            if (!inside) {
                if ("#if UIB_TAP_BUDGET >= 13".equals(line)) {
                    inside = true;
                }
                continue;
            }
            if ("#else".equals(line) || line.startsWith("#endif")) {
                break;
            }
            lines.add(rawLine);
        }
        assertTrue("shader 必须存在 #if UIB_TAP_BUDGET >= 13 抽头分支", inside);
        return lines;
    }

    /**
     * 按预算做最小预处理器展开，返回被选中的原始行。
     *
     * <p>模型与宿主一致：{@code UiBackdropShaderProgram} 在 {@code #version} 之后注入
     * {@code #define UIB_TAP_BUDGET <budget>}，故 {@code UIB_TAP_BUDGET} 在这里视为<b>已定义</b>；
     * 源文件里的 {@code #ifndef} 默认块因此被挡掉（这正是它在真机上不发生作用的原因）。</p>
     */
    private static List<String> selectedRawLines(String source, int budget) {
        List<String> selected = new ArrayList<String>();
        Deque<Boolean> enclosingActive = new ArrayDeque<Boolean>();
        boolean active = true;
        for (String rawLine : source.split("\n")) {
            String line = rawLine.trim();
            if (line.startsWith("#if")) {
                enclosingActive.push(Boolean.valueOf(active));
                active = active && evaluateCondition(line, budget);
                continue;
            }
            if (line.startsWith("#else")) {
                boolean parentActive = enclosingActive.pop().booleanValue();
                active = parentActive && !active;
                enclosingActive.push(Boolean.valueOf(parentActive));
                continue;
            }
            if (line.startsWith("#endif")) {
                assertTrue("shader 的 #if/#endif 未配平", !enclosingActive.isEmpty());
                active = enclosingActive.pop().booleanValue();
                continue;
            }
            if (active) {
                selected.add(rawLine);
            }
        }
        assertTrue("shader 的 #if/#endif 未配平", enclosingActive.isEmpty());
        return selected;
    }

    /**
     * 最小条件求值：只支持本 shader 实际使用的两种形态——
     * {@code #if UIB_TAP_BUDGET >= N} 与 {@code #ifndef UIB_TAP_BUDGET}。
     */
    private static boolean evaluateCondition(String directive, int budget) {
        String condition = directive.substring(3).trim();
        boolean negated = false;
        if (condition.startsWith("ndef")) {
            condition = condition.substring("ndef".length()).trim();
            negated = true;
        }
        String macro = "UIB_TAP_BUDGET";
        assertTrue("只认识 UIB_TAP_BUDGET 条件，实际=" + condition, condition.startsWith(macro));
        String rest = condition.substring(macro.length()).trim();
        if (negated) {
            assertTrue("ifndef 条件只能直接跟宏名，实际=" + condition, rest.isEmpty());
            return false;
        }
        String operator = ">= ";
        assertTrue("只支持 >= 比较，实际=" + condition, rest.startsWith(operator));
        return budget >= Integer.parseInt(rest.substring(operator.length()).trim());
    }

    private static List<double[]> kernelTaps(String source, int budget) {
        List<double[]> taps = new ArrayList<double[]>();
        for (String rawLine : selectedRawLines(source, budget)) {
            String line = rawLine.trim();
            if (!isKernelWeight(line)) {
                continue;
            }
            double[] offset = offset(line);
            taps.add(new double[] { offset[0], offset[1], weight(line) });
        }
        return taps;
    }

    /** 正半径卷积的权重行；零半径单次采样不参与此核。 */
    private static boolean isKernelWeight(String line) {
        return line.startsWith("blurred *=") || line.startsWith("blurred += texture2D(mainTex,");
    }

    /** 抽头权重（形如 (n.0 / d.0) 分式）。 */
    private static double weight(String line) {
        String operator = line.startsWith("blurred *=") ? "*=" : "* ";
        int start = line.lastIndexOf(operator);
        assertTrue("抽头行缺少权重乘数: " + line, start >= 0);
        String value = line.substring(start + operator.length()).trim();
        assertTrue("权重必须是 (n.0 / d.0) 分式: " + line, value.startsWith("("));
        int slash = value.indexOf('/');
        int close = value.indexOf(')');
        assertTrue("分式权重格式应为 (n.0 / d.0): " + line, slash > 1 && close > slash);
        return Double.parseDouble(value.substring(1, slash).trim())
                / Double.parseDouble(value.substring(slash + 1, close).trim());
    }

    /** 抽头偏移（中心样本没有 vec2，返回 0,0）。 */
    private static double[] offset(String line) {
        int start = line.indexOf("vec2(");
        if (start < 0) {
            return new double[] { 0.0D, 0.0D };
        }
        int end = line.indexOf(')', start);
        String inner = line.substring(start + "vec2(".length(), end);
        int comma = inner.indexOf(',');
        return new double[] { Double.parseDouble(inner.substring(0, comma).trim()),
                Double.parseDouble(inner.substring(comma + 1).trim()) };
    }

    private static double weightSum(List<double[]> taps) {
        double total = 0.0D;
        for (double[] tap : taps) {
            total += tap[2];
        }
        return total;
    }

    private static double weightedRadiusSquare(List<double[]> taps) {
        double energy = 0.0D;
        for (double[] tap : taps) {
            energy += tap[2] * (tap[0] * tap[0] + tap[1] * tap[1]);
        }
        return energy;
    }

    private static double maxRadius(List<double[]> taps) {
        double max = 0.0D;
        for (double[] tap : taps) {
            max = Math.max(max, Math.sqrt(tap[0] * tap[0] + tap[1] * tap[1]));
        }
        return max;
    }

    /** 解析 radiusStep 末尾的抽头半径补偿系数（形如 "... 128.0) * 1.35;"）。 */
    private static double parseTapRadiusScale(String source) {
        int at = source.indexOf("vec2 radiusStep");
        assertTrue("shader 里必须存在 radiusStep 定义", at > 0);
        int end = source.indexOf(';', at);
        String line = source.substring(at, end);
        String clampTail = "128.0)";
        int clampEnd = line.indexOf(clampTail);
        assertTrue("radiusStep 必须带 128.0 上限", clampEnd > 0);
        String rest = line.substring(clampEnd + clampTail.length()).trim();
        assertTrue("radiusStep 必须带显式抽头半径补偿系数，实际=" + rest, rest.startsWith("*"));
        return Double.parseDouble(rest.substring(1).trim());
    }

    private static String readFrag() throws IOException {
        return new String(Files.readAllBytes(resolveFragPath()), StandardCharsets.UTF_8);
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
