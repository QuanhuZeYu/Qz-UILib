package club.heiqi.uilib.ui.scene.control;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

/**
 * 字号接线源码守卫（字号动态化 I-4，守卫 2）：禁则 + 必需钉。
 *
 * <p>范式对齐 CharacterRuleFieldRendererThemeTest.sourceGuardWritesColorsOnlyViaThemeBindings：
 * 注释剥离后做 banned 检查，再做 required 检查与计数钉；先有正锚（必须真读到源码），负向清单才有意义。</p>
 *
 * <p><b>必需钉覆盖 5 处解析消费点</b>（报告 §七原写「两处读取点」，实测为 5 处）：
 * SizingCalculator:159/:489、ConstraintResolver:270、ScenePaintEngine:469/:482 —
 * 全部必须改读 effectiveFontSize()，漏一处就会出现「文字变了框没变」或「框变了字没变」。</p>
 */
public class FontWiringSourceGuardTest {

    private static final Path CONTROL_ROOT =
            Paths.get("src/main/java/club/heiqi/uilib/ui/scene/control");

    private static final Map<String, Integer> CONSUMER_FILES = consumerFiles();

    private static final Map<String, Integer> ALLOWED_EXPLICIT_WRITES = allowedExplicitWrites();

    private static Map<String, Integer> consumerFiles() {
        Map<String, Integer> map = new LinkedHashMap<String, Integer>();
        map.put("src/main/java/club/heiqi/uilib/ui/scene/layout/SizingCalculator.java", Integer.valueOf(2));
        map.put("src/main/java/club/heiqi/uilib/ui/scene/layout/ConstraintResolver.java", Integer.valueOf(1));
        map.put("src/main/java/club/heiqi/uilib/ui/scene/paint/ScenePaintEngine.java", Integer.valueOf(2));
        return map;
    }

    private static Map<String, Integer> allowedExplicitWrites() {
        Map<String, Integer> map = new LinkedHashMap<String, Integer>();
        // SceneLabel 的 root 自身就是文字叶：Props.fontSizePx 写的是「显式值」（四层真值第 1 层），
        // 属合法语义而非兼容妥协；若收口为写 scope，此处必须同步降为 0（保留断言本体作回潮哨兵）。
        map.put("SceneLabel.java", Integer.valueOf(1));
        return map;
    }

    /** 必需钉：SizingCalculator 的两处文本测量必须走解析字号（:159 文本叶宽、:489 行计划基准）。 */
    @Test
    public void sizingCalculatorUsesResolvedFontSize() throws Exception {
        assertConsumer("src/main/java/club/heiqi/uilib/ui/scene/layout/SizingCalculator.java");
    }

    /** 必需钉：ConstraintResolver 先验子宽路径必须走解析字号（:270，报告漏列的第 3 个消费点）。 */
    @Test
    public void constraintResolverUsesResolvedFontSize() throws Exception {
        assertConsumer("src/main/java/club/heiqi/uilib/ui/scene/layout/ConstraintResolver.java");
    }

    /** 必需钉：绘制引擎 TEXT（:482）与 SEGMENTS（:469）基准字号都必须走解析字号。 */
    @Test
    public void scenePaintEngineUsesResolvedFontSize() throws Exception {
        assertConsumer("src/main/java/club/heiqi/uilib/ui/scene/paint/ScenePaintEngine.java");
    }

    /** 伴随改：config 渲染器的固定外宽必须按解析字号测量（StructuredListFieldRenderer:465）。 */
    @Test
    public void configRendererUsesResolvedFontSize() throws Exception {
        Path file = Paths.get("src/main/java/club/heiqi/config/ui/field/StructuredListFieldRenderer.java");
        Assert.assertTrue("伴随改文件必须存在：" + file, Files.exists(file));
        String code = codeWithoutComments(read(file));
        Assert.assertEquals("StructuredListFieldRenderer 不得再读节点原始字号（:465）",
                0, occurrences(code, "label.getFontSize()"));
        Assert.assertTrue("StructuredListFieldRenderer 必须出现解析字号调用",
                occurrences(code, "effectiveFontSize(") >= 1);
    }

    /** 禁则：控制域不得残留旧通道，显式 setFontSize 必须恰为白名单处数。 */
    @Test
    public void controlDomainHasNoManualFontWiring() throws Exception {
        int typography = 0;
        StringBuilder offenders = new StringBuilder();
        for (Path file : controlSources()) {
            String name = file.getFileName().toString();
            String code = codeWithoutComments(read(file));
            typography += occurrences(code, "SceneControlTypography");
            int writes = occurrences(code, ".setFontSize(");
            Integer allowed = ALLOWED_EXPLICIT_WRITES.get(name);
            int expected = allowed == null ? 0 : allowed.intValue();
            if (writes != expected) {
                offenders.append(name).append("(setFontSize=").append(writes)
                        .append(",允许=").append(expected).append(") ");
            }
        }
        Assert.assertEquals("旧通道 SceneControlTypography 必须归零（手工接线删除后）",
                0, typography);
        Assert.assertEquals("控制域显式 setFontSize 必须恰为白名单处数（新增即红）",
                "", offenders.toString());
    }

    /** 禁则：控制域不得直接读节点原始字号（全部改读解析字号）。 */
    @Test
    public void controlDomainDoesNotReadRawFontSize() throws Exception {
        StringBuilder offenders = new StringBuilder();
        for (Path file : controlSources()) {
            String code = codeWithoutComments(read(file));
            int raw = occurrences(code, "getFontSize()");
            if (raw > 0) {
                offenders.append(file.getFileName()).append('(').append(raw).append(") ");
            }
        }
        Assert.assertEquals("控制域不得读节点原始字号（改读 effectiveFontSize()）",
                "", offenders.toString());
    }

    /**
     * 计数钉（**钉机制不钉字面**）：三入口必须统一经 FontSizeBinding 写层 2 声明，且都不得自建 effect。
     *
     * <p>为什么不再逐字要求 setFontScope(：现实现是 {@code FontSizeBinding.apply()} 写槽
     * （MountHandle/ScenePortalHandle 各持一个绑定器实例，ContextMenu.Handle 委托 portal），
     * 逐字比对会把「经绑定器写槽」的合法实现误判为未接——守卫要钉的是
     * 「每入口唯一绑定 + 不各自建 effect」这一机制，而不是某一行字面。
     * 运行期幂等不变量由 ControlFontRuntimeGuardTest 承担（toastDefaultFontSizeTenCalls...）。</p>
     */
    @Test
    public void entryPointsWriteTheSingleScopeSlot() throws Exception {
        String mount = codeWithoutComments(read(Paths.get(
                "src/main/java/club/heiqi/uilib/ui/scene/runtime/MountHandle.java")));
        Assert.assertTrue("MountHandle 必须经 FontSizeBinding 写层 2 声明（钉机制，不钉 setFontScope 字面）",
                mount.contains("FontSizeBinding"));
        Assert.assertEquals("MountHandle 不得自建字号 effect", 0, occurrences(mount, "createEffect("));

        String portal = codeWithoutComments(read(Paths.get(
                "src/main/java/club/heiqi/uilib/ui/scene/runtime/ScenePortalHandle.java")));
        Assert.assertTrue("ScenePortalHandle 必须经 FontSizeBinding 写层 2 声明",
                portal.contains("FontSizeBinding"));
        Assert.assertEquals("ScenePortalHandle 不得自建字号 effect",
                0, occurrences(portal, "createEffect("));
        Assert.assertFalse("ScenePortalHandle 的 __fontSize() 侧信道必须消除",
                portal.contains("__fontSize("));

        String menu = codeWithoutComments(read(Paths.get(
                "src/main/java/club/heiqi/uilib/ui/scene/control/SceneContextMenu.java")));
        Assert.assertTrue("ContextMenu.Handle 必须委托 portal 字号入口或自持 FontSizeBinding"
                        + "（facade 不自持第二套声明）",
                menu.contains("portal().fontSize(") || menu.contains("FontSizeBinding"));
        Assert.assertEquals("ContextMenu.Handle 不得自建字号 effect",
                0, occurrences(menu, "createEffect("));
    }

    /**
     * 禁则（S5 终态）：SceneToast 内字号绑定不得挂 runtime 根 Owner。
     *
     * <p>过渡态（S2 起）的等价不变量是**行为面**的「重复设置不累积 effect」，由
     * ControlFontRuntimeGuardTest.toastDefaultFontSizeTenCallsDoNotAccumulateEffects 常驻承担；
     * 本方法只管终态（作用域机制消解 root 绑定后 __runRoot( 归零），两者分开登记避免 S5 误判。</p>
     */
    @Test
    @Ignore("S5 转正：SceneToast 内字号绑定不再挂 runtime 根 Owner（__runRoot( 归零，作用域机制消解）；过渡态不变量见 ControlFontRuntimeGuardTest.toastDefaultFontSizeTenCallsDoNotAccumulateEffects（常驻绿）；对应 P1-1 终态；证据 temp/audit-fontsize/i4_align_probe.txt")
    public void toastDoesNotBindOnRuntimeRoot() throws Exception {
        String toast = codeWithoutComments(read(Paths.get(
                "src/main/java/club/heiqi/uilib/ui/scene/control/SceneToast.java")));
        Assert.assertEquals("SceneToast 不得往 runtime 根 Owner 挂永久 effect",
                0, occurrences(toast, "__runRoot("));
    }

    private static void assertConsumer(String relativePath) throws Exception {
        Path file = Paths.get(relativePath);
        Assert.assertTrue("消费点文件必须存在：" + relativePath, Files.exists(file));
        String code = codeWithoutComments(read(file));
        int expected = CONSUMER_FILES.get(relativePath).intValue();
        int resolved = occurrences(code, "effectiveFontSize(");
        int raw = occurrences(code, "getFontSize()");
        Assert.assertTrue("解析字号调用点不足（期望 >= " + expected + "，实际 " + resolved + "）："
                + file.getFileName(), resolved >= expected);
        Assert.assertEquals("消费点不得回退到节点原始字号：" + file.getFileName(), 0, raw);
    }

    private static List<Path> controlSources() throws Exception {
        List<Path> files = new ArrayList<Path>();
        try (java.util.stream.Stream<Path> stream = Files.walk(CONTROL_ROOT)) {
            for (Path path : (Iterable<Path>) stream
                    .filter(p -> p.toString().endsWith(".java"))::iterator) {
                files.add(path);
            }
        }
        return files;
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        for (int index = 0; (index = source.indexOf(needle, index)) >= 0; index += needle.length()) {
            count++;
        }
        return count;
    }

    /** 去掉行/块注释（字面量原样保留；换行保留，行号与原文 1:1）。 */
    private static String codeWithoutComments(String raw) {
        String source = raw.replace("\r\n", "\n");
        char[] out = source.toCharArray();
        int i = 0;
        int n = out.length;
        while (i < n) {
            char c = out[i];
            if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && out[j] != c) {
                    if (out[j] == '\\') {
                        j++;
                    }
                    j++;
                }
                i = Math.min(j + 1, n);
            } else if (c == '/' && i + 1 < n && out[i + 1] == '/') {
                int j = i;
                while (j < n && out[j] != '\n') {
                    out[j] = ' ';
                    j++;
                }
                i = j;
            } else if (c == '/' && i + 1 < n && out[i + 1] == '*') {
                int j = i;
                int end = source.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
                while (j < end) {
                    if (out[j] != '\n') {
                        out[j] = ' ';
                    }
                    j++;
                }
                i = end;
            } else {
                i++;
            }
        }
        return new String(out);
    }
}
