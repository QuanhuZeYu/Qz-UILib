package club.heiqi.uilib.ui.scene.control.search;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;

/**
 * 主题令牌化源码守卫（P5 U-P5-3 的机械核对口径）。
 *
 * <p>钉住三件事：</p>
 * <ol>
 *   <li><b>硬编码色清零</b>（P5 §4.3 条 1）：picker 扫描面（{@code ui.scene.control.search.*}、
 *       {@code ScenePickerPanel}、{@code SearchPickerFieldSupport}、{@code StructuredListFieldRenderer}）
 *       内不得出现 6/8 位十六进制色字面量 —— 协议色集中定义处是
 *       {@code ui.scene.paint.SceneRenderProtocolTokens}（本扫描面之外）。RGB 掩码 {@code 0x00FFFFFF}
 *       与注释不算。</li>
 *   <li><b>遮罩收敛</b>：Dialog 与变体浮层共用 {@code SceneRenderProtocolTokens.SCRIM_ARGB}，
 *       旧的 {@code 0xCC000000} 不得残留。</li>
 *   <li><b>两态可区分</b>（ADR A-19 / X-1）：「无图」= 静态协议色（{@code IMAGE_PLACEHOLDER_ARGB}），
 *       UNRENDERABLE = 由主题 {@code errorText} 派生的状态色；三处消费点都走
 *       {@code ItemRenderFallbackKeys.unrenderableTint}，不得把两者混为一类。</li>
 * </ol>
 */
public class ScenePickerTokenGuardTest {

    private static final Path MAIN_ROOT = Paths.get("src/main/java");

    /** 扫描面（P5 §4.3 条 1 的文件范围）。 */
    private static final List<String> SCOPE_MARKERS = Arrays.asList(
            "ui\\scene\\control\\search\\", "ScenePickerPanel.java",
            "SearchPickerFieldSupport.java", "StructuredListFieldRenderer.java");

    /** 允许出现的算术掩码（非布局/非配色语义）。 */
    private static final String ALLOWED_MASK = "0x00FFFFFF";

    private static final Pattern HEX_COLOR = Pattern.compile("0x[0-9A-Fa-f]{6,8}");

    /** 三消费点：结果列表 / 成员网格 / 变体浮层。 */
    private static final List<String> FALLBACK_CONSUMERS =
            Arrays.asList("SearchResultList.java", "MemberGrid.java", "VariantChooser.java");

    /** §4.3 条 1：扫描面内不得有硬编码色字面量。 */
    @Test
    public void pickerScopeHasNoHardcodedColorLiterals() throws Exception {
        List<String> offenders = new ArrayList<String>();
        for (Path file : mainSources()) {
            if (!inScope(file)) {
                continue;
            }
            String code = codeWithoutComments(read(file)).replace(ALLOWED_MASK, "MASK");
            Matcher matcher = HEX_COLOR.matcher(code);
            while (matcher.find()) {
                offenders.add(file.getFileName() + ":" + matcher.group());
            }
        }
        Assert.assertEquals("picker 扫描面内禁止硬编码色字面量（协议色集中定义处 = SceneRenderProtocolTokens）："
                + offenders, new ArrayList<String>(), offenders);
    }

    /** 遮罩收敛：Dialog 与变体浮层同源，旧 0xCC000000 不得残留。 */
    @Test
    public void scrimIsConvergedToOneProtocolToken() throws Exception {
        String dialog = codeWithoutComments(read(findMainSource("SceneDialog.java")));
        String chooser = codeWithoutComments(read(findMainSource("VariantChooser.java")));
        Assert.assertTrue("Dialog 遮罩取协议令牌",
                dialog.contains("SceneRenderProtocolTokens.SCRIM_ARGB"));
        Assert.assertTrue("变体浮层遮罩取同一协议令牌",
                chooser.contains("SceneRenderProtocolTokens.SCRIM_ARGB"));
        List<String> offenders = new ArrayList<String>();
        for (Path file : mainSources()) {
            if (codeWithoutComments(read(file)).contains("0xCC000000")) {
                offenders.add(file.getFileName().toString());
            }
        }
        Assert.assertEquals("两值冲突必须收敛（T5 实测 0xCC000000 vs 0xCC121016）",
                new ArrayList<String>(), offenders);
    }

    /** ADR A-19：UNRENDERABLE 主题派生 + 与「无图」协议色可区分，三消费点全覆盖。 */
    @Test
    public void unrenderableStateIsThemeDerivedAndDistinctFromNoImageProtocol() throws Exception {
        String protocol = codeWithoutComments(read(findMainSource("SceneRenderProtocolTokens.java")));
        Assert.assertTrue("「无图」协议色集中定义", protocol.contains("IMAGE_PLACEHOLDER_ARGB"));
        // 定义处只允许三枚静态色值：无图占位 / 透明 / 遮罩 —— UNRENDERABLE 只能给 alpha，
        // 色相必须来自主题（否则「主题派生状态语义」不成立）。
        Assert.assertEquals("协议色定义处只允许 3 枚静态色值（无图/透明/遮罩）",
                3, countHex(protocol));
        Assert.assertTrue("UNRENDERABLE 只登记 alpha", protocol.contains("UNRENDERABLE_TINT_ALPHA"));

        String keys = codeWithoutComments(read(findMainSource("ItemRenderFallbackKeys.java")));
        Assert.assertTrue("UNRENDERABLE 底色由主题 errorText 派生",
                keys.contains("SceneThemes.errorText("));
        Assert.assertTrue("UNRENDERABLE 透明度取协议令牌",
                keys.contains("UNRENDERABLE_TINT_ALPHA"));
        for (String consumer : FALLBACK_CONSUMERS) {
            String code = codeWithoutComments(read(findMainSource(consumer)));
            Assert.assertTrue(consumer + " 必须经统一入口取 UNRENDERABLE 底色",
                    code.contains("ItemRenderFallbackKeys.unrenderableTint("));
            Assert.assertTrue(consumer + " 必须保留「无图」协议占位色分支",
                    code.contains("PLACEHOLDER_COLOR"));
        }
    }

    /** @return 代码中 6/8 位十六进制字面量个数（掩码不计）。 */
    private static int countHex(String code) {
        String text = code.replace(ALLOWED_MASK, "MASK");
        Matcher matcher = HEX_COLOR.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    // ==================== 源码读取助手 ====================

    private static boolean inScope(Path file) {
        String normalized = file.toString().replace('/', '\\');
        for (String marker : SCOPE_MARKERS) {
            if (normalized.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static List<Path> mainSources() throws IOException {
        final List<Path> files = new ArrayList<Path>();
        try (Stream<Path> stream = Files.walk(MAIN_ROOT)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(files::add);
        }
        Assert.assertTrue("必须真读到 main 源码（先有正锚）", files.size() > 100);
        return files;
    }

    private static Path findMainSource(String fileName) throws IOException {
        for (Path file : mainSources()) {
            if (file.getFileName().toString().equals(fileName)) {
                return file;
            }
        }
        throw new AssertionError("找不到源码文件：" + fileName);
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /** 剥离注释（守卫只对真实代码生效）。 */
    private static String codeWithoutComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int index = 0;
        int length = source.length();
        while (index < length) {
            char current = source.charAt(index);
            if (current == '/' && index + 1 < length && source.charAt(index + 1) == '/') {
                while (index < length && source.charAt(index) != '\n') {
                    index++;
                }
            } else if (current == '/' && index + 1 < length && source.charAt(index + 1) == '*') {
                index += 2;
                while (index + 1 < length && !(source.charAt(index) == '*' && source.charAt(index + 1) == '/')) {
                    index++;
                }
                index += 2;
            } else {
                out.append(current);
                index++;
            }
        }
        return out.toString();
    }
}
