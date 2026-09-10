package club.heiqi.uilib.ui.scene.control;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;

/**
 * 内建文字控件清单完备性守卫（字号动态化 I-4，守卫 1a）。
 *
 * <p>不变量（INV-FONT-1 的静态下限）：{@code ui/scene/control/**} 内每一个「创建用户可见文字」的文件，
 * 必须落进三类之一——</p>
 * <ol>
 *   <li>{@link #TEXT_CONTROLS}：控件清单（其内建文字必须跟随控件字号）；</li>
 *   <li>{@link #EXEMPT_TEXT_FILES}：文字载体豁免（primitive 实体 / 仅装饰类，逐条给理由）；</li>
 *   <li>{@link #EXEMPT_TEXTS}：装饰字面值豁免（要求计数恰 2，禁止通配忽略）。</li>
 * </ol>
 *
 * <p>新增控件不登记、清单数字被改小、豁免被顺手扩大、装饰字符被删——都必须变红。</p>
 *
 * <p>口径与逐类证据见 temp/audit-fontsize/improve-4-guards-tests.md §1.2（行号由 i4_rescan.py
 * 重扫校验：注释字符替换为空格、换行保留，故行号与原始文件 1:1）。</p>
 */
public class ControlTextInventoryTest {

    private static final Path CONTROL_ROOT =
            Paths.get("src/main/java/club/heiqi/uilib/ui/scene/control");

    /** 内建用户可见文字的公开控件（28 = control 包 23 + search 子包 5）。 */
    public static final List<String> TEXT_CONTROLS = Arrays.asList(
            // control 包 23：自建文字 15 + 经 primitive 承载 8
            "SceneBreadcrumb", "SceneCheckbox", "SceneContextMenu", "SceneDataTable", "SceneDialog",
            "SceneKeyValueMap", "SceneLabel", "SceneObjectField", "ScenePickerPanel", "SceneSegmented",
            "SceneSimpleList", "SceneTab", "SceneToast", "SceneTooltip", "SceneVirtualGrid",
            "SceneButton", "SceneTextInput", "SceneTextArea", "SceneAutocomplete", "SceneSelect",
            "SceneNavList", "SceneToggle", "SceneRadioGroup",
            // search 子包 5
            "CategoryNavPane", "MemberGrid", "PickerInfoBar", "SearchResultList", "VariantChooser");

    private static final List<String> SEARCH_CONTROLS = Arrays.asList(
            "CategoryNavPane", "MemberGrid", "PickerInfoBar", "SearchResultList", "VariantChooser");

    /** 装饰字面值：允许保持独立字号，但必须逐条登记并计数钉死；禁止通配忽略。 */
    public static final Map<String, String> EXEMPT_TEXTS = exemptTexts();

    /** 有文字创建点但不进控件清单的文件：逐条给理由，计数钉死。 */
    public static final Map<String, String> EXEMPT_TEXT_FILES = exemptTextFiles();

    /** wrapper → 承载其文字的 primitive（防止「清单里有控件、实现里找不到文字来源」）。 */
    public static final Map<String, String> DELEGATED_TEXT = delegatedText();

    private static final Pattern TEXT_SITE = Pattern.compile(
            "\\.setText\\(|::setText(?![A-Za-z])|\\.bindText\\(|\\bSceneLabel\\b");

    private static Map<String, String> exemptTexts() {
        Map<String, String> map = new LinkedHashMap<String, String>();
        map.put("\u2713", "SceneCheckbox 勾选标记 CHECK_MARK_TEXT（装饰，真值 SceneCheckbox.java:114）");
        map.put("\u2261", "SceneDragReorder 拖拽把手 HANDLE_ICON（装饰，真值 SceneDragReorder.java:226）");
        return map;
    }

    private static Map<String, String> exemptTextFiles() {
        Map<String, String> map = new LinkedHashMap<String, String>();
        map.put("SceneButtonPrimitive.java", "文字实体所在（:76/:77），跟随 SceneButton");
        map.put("SceneTextInputPrimitive.java", "文字实体所在（:287/:298/:303/:310/:315），跟随 SceneTextInput");
        map.put("SceneTextAreaPrimitive.java", "文字实体所在（:397/:398/:758/:767/:771/:779/:783），跟随 SceneTextArea");
        map.put("SceneToggleablePrimitive.java", "文字实体所在（:89），跟随 SceneCheckbox / SceneToggle");
        map.put("SceneSingleSelectPrimitive.java", "文字实体所在（:132），跟随 SceneNavList / SceneRadioGroup");
        map.put("SceneSelectPrimitive.java", "文字实体所在（:170/:174/:252），跟随 SceneSelect");
        map.put("SceneAutocompletePrimitive.java", "文字实体所在（:490），跟随 SceneAutocomplete");
        map.put("SceneDragReorder.java", "仅装饰把手字符（EXEMPT_TEXTS 的 \u2261，真值 :226），无用户可见正文");
        return map;
    }

    private static Map<String, String> delegatedText() {
        Map<String, String> map = new LinkedHashMap<String, String>();
        map.put("SceneButton", "SceneButtonPrimitive.java");
        map.put("SceneTextInput", "SceneTextInputPrimitive.java");
        map.put("SceneTextArea", "SceneTextAreaPrimitive.java");
        map.put("SceneToggle", "SceneToggleablePrimitive.java");
        map.put("SceneRadioGroup", "SceneSingleSelectPrimitive.java");
        map.put("SceneNavList", "SceneSingleSelectPrimitive.java");
        map.put("SceneSelect", "SceneSelectPrimitive.java");
        map.put("SceneAutocomplete", "SceneAutocompletePrimitive.java");
        return map;
    }

    /** 计数钉 1：清单规模与包分布必须与实现同步（新增控件不登记即红）。 */
    @Test
    public void inventorySizeIsPinned() {
        Assert.assertEquals("内建文字控件清单必须与实现同步", 28, TEXT_CONTROLS.size());
        int search = 0;
        for (String control : TEXT_CONTROLS) {
            if (SEARCH_CONTROLS.contains(control)) {
                search++;
            }
        }
        Assert.assertEquals("search 子包控件数", 5, search);
        Assert.assertEquals("control 包控件数", 23, TEXT_CONTROLS.size() - search);
        Assert.assertEquals("清单不得有重复项", TEXT_CONTROLS.size(),
                new LinkedHashSet<String>(TEXT_CONTROLS).size());
    }

    /** 计数钉 2：豁免必须显式、恰数、逐条有理由，禁止扩大化。 */
    @Test
    public void exemptionsStayExplicitAndCounted() {
        Assert.assertEquals("装饰字面值豁免必须恰 2 处", 2, EXEMPT_TEXTS.size());
        Assert.assertEquals("非控件文字文件豁免必须恰 8 处", 8, EXEMPT_TEXT_FILES.size());
        Assert.assertEquals("primitive 文字载体必须恰 7 个", 7, distinctCarriers().size());
        for (Map.Entry<String, String> entry : EXEMPT_TEXTS.entrySet()) {
            Assert.assertFalse("装饰豁免必须写明理由：" + entry.getKey(),
                    entry.getValue() == null || entry.getValue().isEmpty());
        }
        for (Map.Entry<String, String> entry : EXEMPT_TEXT_FILES.entrySet()) {
            Assert.assertFalse("文件豁免必须写明理由：" + entry.getKey(),
                    entry.getValue() == null || entry.getValue().isEmpty());
        }
    }

    /** 完整性：任何有文字创建点的源文件都必须被登记（新增 helper 不会静默溜过）。 */
    @Test
    public void everySourceFileWithTextCreationIsClassified() throws Exception {
        Map<String, Integer> texty = scanTextSourceFiles();
        StringBuilder unclassified = new StringBuilder();
        for (String file : texty.keySet()) {
            String control = file.substring(0, file.length() - ".java".length());
            boolean inInventory = TEXT_CONTROLS.contains(control);
            boolean exempt = EXEMPT_TEXT_FILES.containsKey(file);
            if (!inInventory && !exempt) {
                unclassified.append(file).append('(').append(texty.get(file)).append(") ");
            }
        }
        Assert.assertEquals("新增文字创建文件必须登记：控件入 TEXT_CONTROLS，"
                + "载体/装饰入 EXEMPT_TEXT_FILES（逐条理由）", "", unclassified.toString());
        Assert.assertTrue("扫描必须真的读到源码（空结果会让上面的断言空转）：" + texty.size(),
                texty.size() >= 20);
    }

    /** 清单控件必须有文字来源：自有创建点，或经登记的 primitive 承载。 */
    @Test
    public void everyInventoryControlHasTextSource() throws Exception {
        Map<String, Integer> texty = scanTextSourceFiles();
        StringBuilder missing = new StringBuilder();
        for (String control : TEXT_CONTROLS) {
            String file = control + ".java";
            String carrier = DELEGATED_TEXT.get(control);
            boolean own = texty.containsKey(file);
            if (!own && carrier == null) {
                missing.append(control).append("(无自有文字且无登记载体) ");
                continue;
            }
            if (carrier != null) {
                if (!Files.exists(CONTROL_ROOT.resolve(carrier))) {
                    missing.append(control).append("(载体不存在 ").append(carrier).append(") ");
                } else if (!EXEMPT_TEXT_FILES.containsKey(carrier)) {
                    missing.append(carrier).append("(载体未登记豁免) ");
                } else if (!codeWithoutComments(read(CONTROL_ROOT.resolve(file)))
                        .contains(carrier.substring(0, carrier.length() - ".java".length()))) {
                    missing.append(control).append("(未引用载体 ").append(carrier).append(") ");
                }
            }
        }
        Assert.assertEquals("清单控件的文字来源必须可定位", "", missing.toString());
    }

    /** 装饰字面值必须真的出现在其归属控件里（防止豁免条目变成空头支票）。 */
    @Test
    public void everyExemptTextStillExistsInItsOwner() throws Exception {
        Assert.assertTrue("勾选标记仍应存在于 SceneCheckbox",
                read(CONTROL_ROOT.resolve("SceneCheckbox.java")).contains("CHECK_MARK_TEXT"));
        Assert.assertTrue("把手字符仍应存在于 SceneDragReorder",
                read(CONTROL_ROOT.resolve("SceneDragReorder.java")).contains("HANDLE_ICON"));
    }

    private static Set<String> distinctCarriers() {
        return new LinkedHashSet<String>(DELEGATED_TEXT.values());
    }

    /** 扫描 ui/scene/control/**（含 search/）的注释剥离源码，返回「文件 → 文字创建点数」。 */
    private static Map<String, Integer> scanTextSourceFiles() throws Exception {
        Map<String, Integer> found = new LinkedHashMap<String, Integer>();
        try (Stream<Path> files = Files.walk(CONTROL_ROOT)) {
            for (Path file : (Iterable<Path>) files
                    .filter(path -> path.toString().endsWith(".java"))::iterator) {
                if (file.getFileName().toString().startsWith("package-info")) {
                    continue;
                }
                String code = codeWithoutComments(read(file));
                int count = 0;
                Matcher matcher = TEXT_SITE.matcher(code);
                while (matcher.find()) {
                    count++;
                }
                if (count > 0) {
                    found.put(file.getFileName().toString(), Integer.valueOf(count));
                }
            }
        }
        return found;
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /**
     * 去掉行注释与块注释（字符串/字符字面量原样保留；换行全部保留，行号与原文 1:1）。
     *
     * <p>与 CharacterRuleFieldRendererThemeTest.codeWithoutComments 同款，唯一差别是块注释内容
     * 以等长空白替换而非直接删除——直接删除会折叠块注释内的换行，导致后续行号整体前移。</p>
     */
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
