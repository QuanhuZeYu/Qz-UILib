package club.heiqi.uilib.ui.scene.control.search;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;

/**
 * 分级键空间源码守卫（判据 A-17 的静态部分：Z-2 / Z-3）。
 *
 * <p>契约出处：{@code team/P0-ADR-契约与测量.md} §5.1/§5.2：</p>
 * <ul>
 *   <li>分级键唯一来自 {@code SceneImageSource.registryKey()}，<b>不得</b>新增 {@code tierKey()} 第二入口；</li>
 *   <li>键空间 = 候选域键（{@code PickerIconKey} 是唯一生成器）；</li>
 *   <li>消费端<b>不做任何拆键</b>：{@code splitRegistryKey} 与三处「registryKey → itemKey」映射助手已删除，
 *       三消费点一律 {@code ItemRenderFallbackKeys.track(registryKey -> registryKey)}；</li>
 *   <li>{@code HostImageSource.registryKey()} 形态保持不变（{@code 注册名:meta}，无前缀），且不得调用
 *       {@code PickerIconKey}（避免污染跨 Mod 分级键族）。</li>
 * </ul>
 */
public class PickerTierKeySpaceGuardTest {

    private static final Path MAIN_ROOT = Paths.get("src/main/java");

    /** 三消费点：结果列表 / 成员网格 / 变体浮层。 */
    private static final List<String> FALLBACK_CONSUMERS =
            Arrays.asList("SearchResultList.java", "MemberGrid.java", "VariantChooser.java");

    /** 已退役的拆键形态（出现即红：说明契约被回退）。 */
    private static final List<String> RETIRED_SPLIT_HELPERS = Arrays.asList(
            "splitRegistryKey", "itemKeyForRegistryKey", "variantKeyForRegistryKey",
            "candidateKeyForRegistryKey");

    /** Z-2：不得新增第二分级键入口。 */
    @Test
    public void noSecondTierKeyEntryPoint() throws Exception {
        List<String> offenders = new ArrayList<String>();
        for (Path file : mainSources()) {
            String code = codeWithoutComments(read(file));
            if (code.contains("tierKey(")) {
                offenders.add(file.getFileName().toString());
            }
        }
        Assert.assertEquals("分级键只能经既有 SceneImageSource.registryKey() 上报，禁止新增 tierKey()",
                new ArrayList<String>(), offenders);
    }

    /** Z-3：拆键形态全部退役（消费端只做恒等映射）。 */
    @Test
    public void splitBasedKeyMappingIsRetired() throws Exception {
        List<String> offenders = new ArrayList<String>();
        for (Path file : mainSources()) {
            String code = codeWithoutComments(read(file));
            for (String token : RETIRED_SPLIT_HELPERS) {
                if (code.contains(token)) {
                    offenders.add(file.getFileName() + ":" + token);
                }
            }
        }
        Assert.assertEquals("拆键形态必须退役（对候选域键是错解析）", new ArrayList<String>(), offenders);
    }

    /** 三消费点必须用恒等映射装配回退集合（正锚 + 全覆盖）。 */
    @Test
    public void fallbackConsumersUseIdentityMapping() throws Exception {
        Map<String, Boolean> seen = new LinkedHashMap<String, Boolean>();
        for (String name : FALLBACK_CONSUMERS) {
            Path file = findMainSource(name);
            String code = codeWithoutComments(read(file));
            Assert.assertTrue(name + " 必须经统一装配挂回退集合",
                    code.contains("ItemRenderFallbackKeys.track("));
            Assert.assertTrue(name + " 必须使用恒等映射（键空间 = 候选域键）",
                    code.contains("ItemRenderFallbackKeys.track(registryKey -> registryKey)"));
            seen.put(name, Boolean.TRUE);
        }
        Assert.assertEquals("三消费点全覆盖（漏一处即 S-1 漏匹配复现）", 3, seen.size());
    }

    /** Z-2 边界：HostImageSource 键形态不变且不得调用 PickerIconKey。 */
    @Test
    public void hostImageSourceKeyShapeIsUnchanged() throws Exception {
        Path host = findMainSource("HostImageSource.java");
        String code = codeWithoutComments(read(host));
        Assert.assertTrue("HostImageSource 键形态 = 注册名:meta（getItemDamage）",
                code.contains("getItemDamage()"));
        Assert.assertFalse("HostImageSource 不得调用 PickerIconKey（键族隔离）",
                code.contains("PickerIconKey"));
    }

    /** 生成器唯一性：PickerIconKey 的生成方法只被选择器侧调用（不得在通用图片源里出现）。 */
    @Test
    public void iconKeyGeneratorIsUsedOnlyByPickerSides() throws Exception {
        List<String> callers = new ArrayList<String>();
        for (Path file : mainSources()) {
            String name = file.getFileName().toString();
            if ("PickerIconKey.java".equals(name)) {
                continue;
            }
            String code = codeWithoutComments(read(file));
            if (code.contains("PickerIconKey.candidate(") || code.contains("PickerIconKey.variant(")) {
                callers.add(name);
            }
        }
        List<String> expected = Arrays.asList("MemberGrid.java", "PickerIconCache.java");
        Assert.assertEquals("生成器调用面 = 选择器缓存 + 成员网格（新增即红，须说明键族归属）",
                expected, callers);
    }

    // ==================== 源码读取助手 ====================

    private static List<Path> mainSources() throws IOException {
        final List<Path> files = new ArrayList<Path>();
        try (Stream<Path> stream = Files.walk(MAIN_ROOT)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(files::add);
        }
        Assert.assertTrue("必须真读到 main 源码（先有正锚，负向清单才有意义）", files.size() > 100);
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
            } else if (current == '"') {
                out.append(current);
                index++;
                while (index < length && source.charAt(index) != '"') {
                    if (source.charAt(index) == '\\') {
                        out.append(source.charAt(index));
                        index++;
                    }
                    if (index < length) {
                        out.append(source.charAt(index));
                        index++;
                    }
                }
                if (index < length) {
                    out.append(source.charAt(index));
                    index++;
                }
            } else {
                out.append(current);
                index++;
            }
        }
        return out.toString();
    }
}
