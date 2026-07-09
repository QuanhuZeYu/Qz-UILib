package club.heiqi.config.ui.field;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.config.ui.field.CharacterRuleFieldRenderer.CharacterRuleItem;

/**
 * {@link CharacterRuleFieldRenderer} 的 L2 纯数学边界测试：双向映射、normalize 幂等、
 * errorMessage 派生、无效行写回不丢失。
 *
 * <p>不挂 runtime / 不建 scene，仅断言 render 内部翻译函数的代数性质（按
 * {@code docs/传感层/测试体系约定.md} §L2 边界，纯数学：双向映射/normalize/errorMessage）。</p>
 *
 * <h3>覆盖分支（对齐 FontCharacterRule.parse 各分支）</h3>
 * <ul>
 *   <li>有效单字符规则：{@code "a=Font"} → valid</li>
 *   <li>有效 Unicode 范围：{@code "U+0041-U+005A=Font"} → valid</li>
 *   <li>有效禁用规则：{@code "disabled:a=Font"} → enabled=false, valid</li>
 *   <li>缺 = 分隔符：{@code "ABC"} → invalid（errorMessage 非空）</li>
 *   <li>端点格式错：{@code "XY=Font"} → invalid</li>
 *   <li>空行：selector+fontName 皆空 → toRaw 写空串</li>
 *   <li>带空白的同义输入：{@code " a = font "} 经 normalize → {@code "a=font"}</li>
 * </ul>
 */
public class CharacterRuleFieldRendererTest {

    // ==================== 双向映射 round-trip ====================

    /**
     * toRuleItems(projectValues(x)) 对有效/无效行都语义等价：投影稳定。
     */
    @Test
    public void projectThenParseRoundTripsStably() {
        List<String> draft = Arrays.asList(
                "a=FontA",
                "disabled:b-z=FontB",
                "U+0041=FontC",
                "ABC=BadSelector",
                "",
                "noSeparator"
        );
        List<CharacterRuleItem> items = CharacterRuleFieldRenderer.toRuleItems(draft);
        List<String> projected = CharacterRuleFieldRenderer.projectValues(items);
        // 再次 round-trip 应稳定（投影不变）
        List<CharacterRuleItem> items2 = CharacterRuleFieldRenderer.toRuleItems(projected);
        List<String> projected2 = CharacterRuleFieldRenderer.projectValues(items2);
        Assert.assertEquals("二次 round-trip 投影稳定", projected, projected2);

        // 关键字段语义保留
        Assert.assertEquals("有效单字符 selector 保留", "a", items.get(0).getSelector());
        Assert.assertEquals("有效单字符 fontName 保留", "FontA", items.get(0).getFontName());
        Assert.assertTrue("有效单字符应 valid", items.get(0).getErrorMessage() == null);

        Assert.assertFalse("disabled 前缀解析为 enabled=false", items.get(1).isEnabled());
        Assert.assertTrue("disabled 范围规则应 valid", items.get(1).getErrorMessage() == null);

        Assert.assertEquals("U+XXXX selector 保留", "U+0041", items.get(2).getSelector());
        Assert.assertTrue("U+XXXX 应 valid", items.get(2).getErrorMessage() == null);

        // 无效行（缺 = / 端点错）：toRaw 按 toConfigValue 拼回，projectValues 不丢
        Assert.assertTrue("缺 = 行 errorMessage 非空", items.get(5).getErrorMessage() != null);
        Assert.assertTrue("端点错行 errorMessage 非空", items.get(3).getErrorMessage() != null);
        // 缺 = 行：parse 后 selector="" fontName="" → toRaw 写空串（不丢但规范化）
        Assert.assertEquals("缺 = 行规范化为空串", "", projected.get(5));
        Assert.assertEquals("端点错行投影保留（按 toConfigValue 拼回）", "ABC=BadSelector", projected.get(3));

        // 空行：toRaw 写空串（不写 "="）
        Assert.assertEquals("空行投影为空串", "", projected.get(4));
    }

    /**
     * 添加按钮新建空行（enabled=true, selector="", fontName=""）→ 投影空串，errorMessage 派生。
     */
    @Test
    public void freshEmptyItemProjectsToEmptyString() {
        CharacterRuleItem fresh = new CharacterRuleItem(true, "", "");
        Assert.assertTrue("新空行 isEmpty", fresh.isEmpty());
        Assert.assertEquals("新空行 toRaw 为空串", "", CharacterRuleFieldRenderer.toRaw(fresh));
        // 新空行 parseLine("=") 派生 errorMessage 非空（字符或范围不能为空）—— 对齐 FontCharacterRule.parseLine
        Assert.assertNotNull("新空行 errorMessage 派生非空", fresh.getErrorMessage());
    }

    // ==================== normalize 幂等 ====================

    /**
     * normalize(normalize(x)) == normalize(x)：反复规范化结果稳定。
     */
    @Test
    public void normalizeIsIdempotent() {
        List<String> raw = Arrays.asList(
                "  a = Font  ",
                "disabled: b - z = FontB ",
                "  U+0041  =  FontC",
                "",
                "  noSep  ",
                "  =  "
        );
        List<String> once = CharacterRuleFieldRenderer.normalize(raw);
        List<String> twice = CharacterRuleFieldRenderer.normalize(once);
        Assert.assertEquals("normalize 幂等", once, twice);
    }

    /**
     * normalize 把带空白的同义输入压成与 projectValues 同源形态。
     */
    @Test
    public void normalizeAlignsWithProjection() {
        List<String> raw = Arrays.asList(" a = Font ", "disabled:b=FontB");
        List<String> norm = CharacterRuleFieldRenderer.normalize(raw);
        List<CharacterRuleItem> items = CharacterRuleFieldRenderer.toRuleItems(raw);
        List<String> projected = CharacterRuleFieldRenderer.projectValues(items);
        // normalize 与 projectValues 同源：消除字面空白差异后相等
        Assert.assertEquals("normalize 与 projectValues 同源", norm, projected);
    }

    /**
     * normalize 对 null 安全（parse 容错）。
     */
    @Test
    public void normalizeHandlesNullEntries() {
        List<String> raw = Arrays.asList("a=Font", null, "b=FontB");
        List<String> norm = CharacterRuleFieldRenderer.normalize(raw);
        // 第二项 null 经 parse("") → invalid → toRaw 因 selector+fontName 皆空 → ""
        Assert.assertEquals("normalize 处理 null 项", 3, norm.size());
        Assert.assertEquals("null 项规范化为空串", "", norm.get(1));
    }

    // ==================== errorMessage 派生（对齐 FontCharacterRule.parse 各分支） ====================

    /**
     * 有效 selector（单字符 / 范围 / U+XXXX）→ errorMessage 为 null。
     */
    @Test
    public void validSelectorProducesNoError() {
        Assert.assertNull("单字符 valid", itemErr("a=Font"));
        Assert.assertNull("范围 valid", itemErr("a-z=Font"));
        Assert.assertNull("U+XXXX valid", itemErr("U+0041=Font"));
        Assert.assertNull("U+XXXX 范围 valid", itemErr("U+0041-U+005A=Font"));
        Assert.assertNull("disabled valid", itemErr("disabled:a=Font"));
    }

    /**
     * 无效 selector → errorMessage 非空（对齐 parse 各错误分支）。
     */
    @Test
    public void invalidSelectorProducesError() {
        Assert.assertNotNull("缺 =", itemErr("noSeparator"));
        Assert.assertNotNull("selector 空但 fontName 有", itemErr("=Font"));
        Assert.assertNotNull("多字符端点", itemErr("XY=Font"));
        Assert.assertNotNull("Unicode 解析失败", itemErr("U+XYZ=Font"));
        Assert.assertNotNull("范围起点大于终点", itemErr("z-a=Font"));
    }

    /**
     * withXxx 派生重算 errorMessage：从 valid 改成 invalid 后错误出现，反之消失。
     */
    @Test
    public void withSelectorRecomputesErrorMessage() {
        CharacterRuleItem valid = CharacterRuleItem.fromRaw("a=Font");
        Assert.assertNull("原 valid", valid.getErrorMessage());
        CharacterRuleItem invalid = valid.withSelector("XY");
        Assert.assertNotNull("改成无效 selector 后 errorMessage 出现", invalid.getErrorMessage());
        Assert.assertEquals("id 保持稳定（I5）", valid.getId(), invalid.getId());

        CharacterRuleItem back = invalid.withSelector("a");
        Assert.assertNull("改回有效后 errorMessage 消失", back.getErrorMessage());
    }

    /**
     * withEnabled 切换不改变 selector/fontName，errorMessage 重算（保持原 selector 的 validity）。
     */
    @Test
    public void withEnabledPreservesIdAndSelectorValidity() {
        CharacterRuleItem item = CharacterRuleItem.fromRaw("a=Font");
        CharacterRuleItem toggled = item.withEnabled(false);
        Assert.assertEquals("id 稳定", item.getId(), toggled.getId());
        Assert.assertFalse("enabled 切换", toggled.isEnabled());
        Assert.assertEquals("selector 保留", "a", toggled.getSelector());
        Assert.assertEquals("fontName 保留", "Font", toggled.getFontName());
        Assert.assertNull("valid 不变", toggled.getErrorMessage());
    }

    // ==================== 无效行写回不丢失 ====================

    /**
     * 无效行经 projectValues 仍在列表（按原 selector/fontName 拼回）。
     */
    @Test
    public void invalidLinesArePreservedInProjection() {
        List<String> draft = Arrays.asList("a=Font", "BadSelector=Font", "noSep", "=NoSelector");
        List<CharacterRuleItem> items = CharacterRuleFieldRenderer.toRuleItems(draft);
        List<String> projected = CharacterRuleFieldRenderer.projectValues(items);
        Assert.assertEquals("4 行全部保留", 4, projected.size());
        Assert.assertEquals("有效行不变", "a=Font", projected.get(0));
        Assert.assertEquals("无效 selector 行按原值拼回", "BadSelector=Font", projected.get(1));
        // noSep 缺 = → parse 后 selector="" fontName="" → toRaw 空串
        Assert.assertEquals("缺 = 行规范化为空串", "", projected.get(2));
        // =NoSelector → selector="" fontName="NoSelector" → toRaw 仍拼回 "=NoSelector"
        Assert.assertEquals("selector 空 fontName 非空行按 toConfigValue 拼回", "=NoSelector", projected.get(3));
    }

    // ==================== toDraftList 兜底 ====================

    /**
     * toDraftList 对 null / 非 List 兜底空 list。
     */
    @Test
    public void toDraftListHandlesNullAndNonList() {
        Assert.assertTrue("null → 空 list", CharacterRuleFieldRenderer.toDraftList(null).isEmpty());
        Assert.assertTrue("非 List → 空 list", CharacterRuleFieldRenderer.toDraftList("a=Font").isEmpty());
        Assert.assertEquals("List 元素 null → 空串",
                Collections.singletonList(""),
                CharacterRuleFieldRenderer.toDraftList(Collections.singletonList(null)));
    }

    // ==================== P5 逗号多点 errorMessage 派生回归 ====================

    /**
     * 逗号多点 selector 全 valid：UI 单行 errorMessage 为 null，selector 保留逗号原样。
     */
    @Test
    public void commaMultiPointAllValidProducesNoError() {
        CharacterRuleItem item = CharacterRuleItem.fromRaw("a,b,c=Font");

        Assert.assertNull("全 valid 段 errorMessage 为 null", item.getErrorMessage());
        Assert.assertEquals("selector 保留逗号", "a,b,c", item.getSelector());
        Assert.assertEquals("fontName 透传", "Font", item.getFontName());
    }

    /**
     * 逗号多点含 invalid 段：errorMessage 取首个 invalid 段错误。
     */
    @Test
    public void commaMultiPointWithInvalidReportsFirstError() {
        CharacterRuleItem item = CharacterRuleItem.fromRaw("a,XY,b=Font");

        Assert.assertNotNull("含 invalid 段 errorMessage 非空", item.getErrorMessage());
        Assert.assertEquals("selector 保留逗号原样", "a,XY,b", item.getSelector());
    }

    /**
     * 逗号多点 normalize 幂等：逗号在 selector 内原样保留，反复规范化稳定。
     */
    @Test
    public void commaMultiPointNormalizeIsIdempotent() {
        java.util.List<String> raw = java.util.Arrays.asList("a,b=Font", "  x , y = Z  ");
        java.util.List<String> once = CharacterRuleFieldRenderer.normalize(raw);
        java.util.List<String> twice = CharacterRuleFieldRenderer.normalize(once);
        Assert.assertEquals("逗号多点 normalize 幂等", once, twice);
        // 注意：parseLine 只 trim selector 首尾空白，段间空格保留；幂等性即满足 round-trip 稳定
        Assert.assertEquals("空白首尾 trim 后写回", "x , y=Z", once.get(1));
    }

    // ==================== P-1 全段空 selector 一致性（防回归） ====================

    /**
     * 全段空 selector（如 {@code ",,=Font"}）下，构造内 errorMessage 派生应与 parseLine 一致：
     * 视为 invalid，errorMessage 非空。
     *
     * <p>P-1 修复前：构造走 {@link FontCharacterRule#parse}（返回 List），全段空时逗号展开后
     * 全段跳过 → 返回空 list → errorMessage=null；而 fromRaw 走 {@link FontCharacterRule#parseLine}
     * 视为 invalid → 两路径语义不一致。修复后构造改用 parseLine，统一裁决。</p>
     */
    @Test
    public void allEmptySegmentsProducesErrorConsistentWithParseLine() {
        // fromRaw 路径（一直走 parseLine）：全段空 selector → invalid
        CharacterRuleItem fromRawItem = CharacterRuleItem.fromRaw(",,=Font");
        Assert.assertNotNull("fromRaw 全段空 errorMessage 非空", fromRawItem.getErrorMessage());
        Assert.assertEquals("selector 保留逗号原样", ",,", fromRawItem.getSelector());

        // 构造路径（P-1 修复后走 parseLine）：withSelector 触发构造重派生
        CharacterRuleItem base = CharacterRuleItem.fromRaw("a=Font");
        CharacterRuleItem withEmptySegments = base.withSelector(",,");
        Assert.assertNotNull("withSelector 全段空 errorMessage 非空", withEmptySegments.getErrorMessage());
        Assert.assertEquals("id 保持稳定（I5）", base.getId(), withEmptySegments.getId());
    }

    /** 取单条规则经 CharacterRuleItem 派生的 errorMessage。 */
    private static String itemErr(String raw) {
        return CharacterRuleItem.fromRaw(raw).getErrorMessage();
    }
}
