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
import org.junit.Test;

/**
 * 字号接线源码守卫（字号动态化，守卫 2）：只留<b>禁则、唯一真值与机制钉</b>。
 *
 * <h3>为什么删掉「消费点必须调用 effectiveFontSize()」那组钉</h3>
 * <p>那组断言（{@code SizingCalculator} / {@code ConstraintResolver} / {@code ScenePaintEngine} /
 * {@code StructuredListFieldRenderer}）是<b>源码串快照</b>：数的是某个文件里
 * {@code "effectiveFontSize("} 与 {@code "getFontSize()"} 的出现次数。它守的情形已经被语义消除 ——
 * {@code SceneNode.getFontSize()} 的现状就是 {@code return effectiveFontSize();}，控制域不存在
 * 「读到原始字号」这条路径，继续钉一个不可能发生的回归只是把实现抄进测试。同理删除两组计数钉
 * （{@code setFontSize} 白名单处数、溢出白名单文件数）：一改实现就红的数字不构成回归防线。</p>
 *
 * <p>留下的是三类仍有真实回归风险的项：<b>禁则</b>（旧通道 {@code SceneControlTypography} 不得
 * 回潮）、<b>唯一真值</b>（字号上限字面量只有 {@code FontSizeLimits} 一个宿主）、
 * <b>机制钉</b>（三个对外字号入口经同一绑定器写槽且都不自建 effect —— 钉机制、不钉字面）。</p>
 */
public class FontWiringSourceGuardTest {

    private static final Path CONTROL_ROOT =
            Paths.get("src/main/java/club/heiqi/uilib/ui/scene/control");

    /** by-design 滚动视口 / 单行短文本边界：clip+text 同现但无需槽位溢出策略（逐条登记理由）。 */
    private static final Map<String, String> OVERFLOW_SCROLL_WHITELIST = overflowScrollWhitelist();

    private static Map<String, String> overflowScrollWhitelist() {
        Map<String, String> map = new LinkedHashMap<String, String>();
        map.put("SceneTextInputPrimitive.java", "横向滚动视口自带溢出出口");
        map.put("SceneTextAreaPrimitive.java", "纵横滚动视口自带溢出出口");
        map.put("SceneAutocompletePrimitive.java", "候选下拉为滚动视口，行文本由 listbox 承载");
        map.put("SceneSelectPrimitive.java", "下拉列表为滚动视口");
        map.put("SceneSimpleList.java", "列表视口 + 行内编辑器自带横向滚动");
        map.put("CategoryNavPane.java", "分类导航为滚动视口");
        map.put("MemberGrid.java", "成员网格为滚动视口");
        map.put("VariantChooser.java", "变体列表为滚动视口");
        map.put("SceneContextMenu.java", "面板 clip 仅防越界；菜单项为单行短文本（边界登记，见台账 §九）");
        map.put("SceneToast.java", "卡片 clip 仅防越界；通知消息为单行短文本（边界登记，见台账 §九）");
        map.put("ScenePickerPanel.java", "面板 clip 仅防越界；顶栏/统计由父布局约束（边界登记，见台账 §九）");
        return map;
    }

    /** 禁则：控制域不得残留旧字号通道（手工接线删除后，唯一真值是字号链）。 */
    @Test
    public void controlDomainHasNoLegacyTypographyChannel() throws Exception {
        int typography = 0;
        for (Path file : controlSources()) {
            typography += occurrences(codeWithoutComments(read(file)), "SceneControlTypography");
        }
        Assert.assertEquals("旧通道 SceneControlTypography 必须归零（字号只有字号链一条真值）",
                0, typography);
    }

    /**
     * 机制钉（<b>钉机制不钉字面</b>）：三入口必须统一经 FontSizeBinding 写层 2 声明，且都不得自建 effect。
     *
     * <p>为什么不再逐字要求 setFontScope(：现实现是 {@code FontSizeBinding.apply()} 写槽
     * （MountHandle/ScenePortalHandle 各持一个绑定器实例，ContextMenu.Handle 委托 portal），
     * 逐字比对会把「经绑定器写槽」的合法实现误判为未接 —— 守卫要钉的是
     * 「每入口唯一绑定 + 不各自建 effect」这一机制。运行期幂等不变量由
     * {@code ControlFontRuntimeGuardTest} 承担（toastDefaultFontSizeTenCalls...）。</p>
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
     * 溢出策略并轨（守卫 2 / improve-3 G-A）：clip 且含文本的文件必须显式声明溢出策略。
     *
     * <p>白名单是「by-design 滚动视口 / 单行短文本边界」的逐条登记（每条必须写明理由、
     * 且必须真在候选集合内）——<b>不钉数量</b>：新增边界登记是正常演进，钉死文件数只会让
     * 合理改动撞红。</p>
     */
    @Test
    public void overflowPolicyDeclaredNextToClip() throws Exception {
        List<String> offenders = new ArrayList<String>();
        List<String> seen = new ArrayList<String>();
        for (Path file : controlSources()) {
            String name = file.getFileName().toString();
            String code = codeWithoutComments(read(file));
            if (!code.contains(".setClipChildren(true)") || !code.contains(".setText(")) {
                continue;
            }
            seen.add(name);
            boolean policy = code.contains(".setMaxTextWidth(") || code.contains(".setEllipsis(")
                    || code.contains(".setMaxLines(");
            if (!policy && !OVERFLOW_SCROLL_WHITELIST.containsKey(name)) {
                offenders.add(name);
            }
        }
        Assert.assertFalse("扫描范围异常：控制域应当存在 clip+文本同现文件", seen.isEmpty());
        for (Map.Entry<String, String> entry : OVERFLOW_SCROLL_WHITELIST.entrySet()) {
            Assert.assertFalse("白名单必须写明理由：" + entry.getKey(),
                    entry.getValue() == null || entry.getValue().isEmpty());
            Assert.assertTrue("白名单文件必须真在 clip+文本同现集合内：" + entry.getKey(),
                    seen.contains(entry.getKey()));
        }
        Assert.assertEquals("clip 且含文本的文件必须声明溢出策略或命中白名单（不得静默截断）："
                + offenders, 0, offenders.size());
    }

    /** 裁决 3：字号域上限必须只有一处定义（FontSizeLimits），消费方引用常量而非再写字面量。 */
    @Test
    public void fontSizeLimitIsDeclaredOnce() throws Exception {
        String limits = codeWithoutComments(read(Paths.get(
                "src/main/java/club/heiqi/uilib/font/layout/FontSizeLimits.java")));
        Assert.assertEquals("FontSizeLimits 必须是 256 字面量的唯一宿主",
                1, occurrences(limits, "= 256"));
        for (String path : new String[]{
                "src/main/java/club/heiqi/uilib/ui/scene/node/SceneNode.java",
                "src/main/java/club/heiqi/uilib/font/layout/RichTextTagParser.java"}) {
            String code = codeWithoutComments(read(Paths.get(path)));
            Assert.assertTrue(path + " 必须引用 FontSizeLimits 常量", code.contains("FontSizeLimits"));
            Assert.assertEquals(path + " 不得再写字号上限字面量",
                    0, occurrences(code, "256"));
        }
    }

    /**
     * 禁则：SceneToast 内字号绑定不得挂 runtime 根 Owner。
     *
     * <p>过渡态的等价不变量是<b>行为面</b>的「重复设置不累积 effect」，由
     * {@code ControlFontRuntimeGuardTest.toastDefaultFontSizeTenCallsDoNotAccumulateEffects}
     * 常驻承担；本方法只管终态（作用域机制消解 root 绑定后 __runRoot( 归零）。</p>
     */
    @Test
    public void toastDoesNotBindOnRuntimeRoot() throws Exception {
        String toast = codeWithoutComments(read(Paths.get(
                "src/main/java/club/heiqi/uilib/ui/scene/control/SceneToast.java")));
        Assert.assertEquals("SceneToast 不得往 runtime 根 Owner 挂永久 effect",
                0, occurrences(toast, "__runRoot("));
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
