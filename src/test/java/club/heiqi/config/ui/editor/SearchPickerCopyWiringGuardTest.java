package club.heiqi.config.ui.editor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;

/**
 * 文案接线源码守卫（P5 §3.2 / U-P5-1）。
 *
 * <p>规格要求「Presentation 访问器 = 键」这一既有口径下，<b>每个访问器都必须有 UI 消费者</b>：
 * 注入了却从不显示的键属不合格（P5 §6 必须项 2「死键清零」）。本类把 {@code temp/p5_i18n_keys.py}
 * 的审计口径 CI 化，并把「真死键删除」钉进守卫：</p>
 *
 * <ul>
 *   <li>每个公开 {@code String xxx(...)} 访问器在 main 源码（除两个 Presentation 文件本身）里
 *       必须有 {@code <presentation>().xxx(} 形态的调用；</li>
 *   <li>唯一豁免：{@code infoBarIdPattern} —— 它是 {@code infoBarIdLabel} 的模板，
 *       在同一类内部被消费（豁免必须同时满足「同类内被引用」，防止豁免变成垃圾桶）；</li>
 *   <li>{@code currentMember(CurrentMember)} 已按 ADR V2.4 对 A-24/A-25 的显式放行删除
 *       （真死键，登记进 P6「明确删除」清单），出现即红；</li>
 *   <li>变体浮层的分段/按钮/占位/空态/只读提示必须走注入键，且不得残留硬编码回退字面量。</li>
 * </ul>
 */
public class SearchPickerCopyWiringGuardTest {

    private static final Path MAIN_ROOT = Paths.get("src/main/java");

    /** 访问器声明形态：{@code public String xxx(}（含带参访问器，如 memberEditingBanner(String)）。 */
    private static final Pattern ACCESSOR =
            Pattern.compile("public\\s+(?:static\\s+)?String\\s+([A-Za-z0-9_]+)\\s*\\(");

    /** 消费形态：{@code <...>presentation().xxx(}（同一行内，与 p5_i18n_keys.py 口径一致）。 */
    private static final String CONSUMER_TEMPLATE =
            "(?i)resentation[^\\n]*?\\b%s\\s*\\(";

    /** 同类内消费的模板型访问器（豁免清单；必须同时满足「本类内被引用」）。 */
    private static final Set<String> TEMPLATE_ACCESSORS =
            new LinkedHashSet<String>(Arrays.asList("infoBarIdPattern"));

    /** 已退役的真死键（ADR V2.4 显式放行删除；出现即说明契约被回退）。 */
    private static final List<String> RETIRED_ACCESSORS = Arrays.asList("currentMember");

    /** 变体浮层的旧硬编码回退（U-P5-1 后必须消失）。 */
    private static final List<String> RETIRED_VARIANT_LITERALS = Arrays.asList(
            "SEGMENT_LABELS", "搜索变体", "\"选择变体\"");

    /** 每个 Presentation 访问器都必须有消费者（死键清零）。 */
    @Test
    public void everyPresentationAccessorHasConsumer() throws Exception {
        List<Path> sources = mainSources();
        List<String> dead = new ArrayList<String>();
        Set<String> checked = new LinkedHashSet<String>();
        for (String fileName : Arrays.asList(
                "SearchPickerPresentation.java", "SearchPickerPanelPresentation.java")) {
            Path owner = findMainSource(sources, fileName);
            String ownerCode = code(owner);
            for (String accessor : accessors(ownerCode)) {
                checked.add(owner.getFileName() + "#" + accessor);
                if (TEMPLATE_ACCESSORS.contains(accessor)) {
                    Assert.assertTrue("豁免项必须仍在本类内被消费（豁免不是垃圾桶）: " + accessor,
                            ownerCode.contains(accessor + "("));
                    continue;
                }
                boolean consumed = false;
                for (Path file : sources) {
                    if (file.getFileName().toString().equals(fileName)) {
                        continue;
                    }
                    if (Pattern.compile(String.format(CONSUMER_TEMPLATE, accessor))
                            .matcher(code(file)).find()) {
                        consumed = true;
                        break;
                    }
                }
                if (!consumed) {
                    dead.add(owner.getFileName() + "#" + accessor);
                }
            }
        }
        Assert.assertTrue("必须真枚举到 Presentation 访问器（正锚）", checked.size() >= 30);
        Assert.assertEquals("禁止「注入了从不显示」的键（P5 §6 必须项 2）：" + dead,
                new ArrayList<String>(), dead);
    }

    /** 真死键 {@code currentMember} 已删除且不得复活（ADR V2.4 放行 + P6 删除清单登记）。 */
    @Test
    public void retiredCurrentMemberAliasStaysDeleted() throws Exception {
        for (Path file : mainSources()) {
            String code = code(file);
            for (String retired : RETIRED_ACCESSORS) {
                Assert.assertFalse("已退役访问器不得复活：" + retired + " @ " + file.getFileName(),
                        Pattern.compile("public\\s+(?:static\\s+)?String\\s+" + retired + "\\s*\\(")
                                .matcher(code).find());
            }
        }
    }

    /** 变体浮层文案全部走注入键，且旧硬编码回退已删除。 */
    @Test
    public void variantOverlayReadsInjectedCopy() throws Exception {
        Path chooser = findMainSource(mainSources(), "VariantChooser.java");
        String code = code(chooser);
        for (String accessor : Arrays.asList("all", "selected", "cancel", "confirm",
                "emptyVariants", "modeReadOnlyHint", "variantSearchPlaceholder", "back")) {
            Assert.assertTrue("变体浮层必须读注入键：" + accessor,
                    Pattern.compile(String.format(CONSUMER_TEMPLATE, accessor)).matcher(code).find());
        }
        for (String literal : RETIRED_VARIANT_LITERALS) {
            Assert.assertFalse("旧硬编码回退必须删除：" + literal, code.contains(literal));
        }
    }

    /** 结果区/成员区/顶栏三处新接线必须有正锚（防止只加键不显示）。 */
    @Test
    public void panelRegionsReadInjectedCopy() throws Exception {
        String panel = code(findMainSource(mainSources(), "ScenePickerPanel.java"));
        for (String accessor : Arrays.asList("categoryDimensionTitle", "densityLabel", "close",
                "addMember", "memberAddingBanner", "memberEditingBanner", "keyboardHint",
                "scrollHint", "searchResultsTitle", "truncated", "emptyCategoryResults",
                "emptySearchResults", "empty")) {
            Assert.assertTrue("面板必须读注入键：" + accessor,
                    Pattern.compile(String.format(CONSUMER_TEMPLATE, accessor)).matcher(panel).find());
        }
    }

    // ==================== 源码读取助手 ====================

    private static List<String> accessors(String ownerCode) {
        List<String> names = new ArrayList<String>();
        Matcher matcher = ACCESSOR.matcher(ownerCode);
        while (matcher.find()) {
            if (!names.contains(matcher.group(1))) {
                names.add(matcher.group(1));
            }
        }
        return names;
    }

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

    private static Path findMainSource(List<Path> sources, String fileName) {
        for (Path file : sources) {
            if (file.getFileName().toString().equals(fileName)) {
                return file;
            }
        }
        throw new AssertionError("找不到源码文件：" + fileName);
    }

    private static String code(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
