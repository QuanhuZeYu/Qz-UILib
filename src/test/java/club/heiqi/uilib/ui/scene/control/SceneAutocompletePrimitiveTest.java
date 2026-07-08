package club.heiqi.uilib.ui.scene.control;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.junit.Assert;
import org.junit.Test;

/**
 * SceneAutocompletePrimitive L2 纯数学测试 —— 验证 filter_candidates / normalize / isExactMatchInFiltered / clamp
 * 等无 runtime/input/reactive 依赖的纯函数契约。
 *
 * <p>按 {@code docs/传感层/测试体系约定.md} §L2：本层只测纯数学，禁依赖 runtime/input/reactive；
 * 守 L2 边界靠评审纪律（无 @Before、无 SceneRuntime 字段、无 reactive signal）。</p>
 *
 * <p>覆盖：前缀/包含匹配、大小写不敏感（Locale.ENGLISH）、trim、空输入、空候选、input 长于候选、
 * limit 截断、全命中、零命中、null 安全、isExactMatchInFiltered、clamp 越界钳位。</p>
 */
public class SceneAutocompletePrimitiveTest {

    // ==================== filterCandidates（PREFIX 默认） ====================

    /** 前缀命中：保留原大小写、保持候选顺序。 */
    @Test
    public void prefixMatchKeepsOriginalCaseAndOrder() {
        List<String> candidates = Arrays.asList("Arial", "Arial Black", "Calibri", "Arial Unicode MS");
        List<String> out = SceneAutocompletePrimitive.filterCandidates(
                "ari", candidates, SceneAutocompletePrimitive.MatchMode.PREFIX, 8);
        Assert.assertEquals(Arrays.asList("Arial", "Arial Black", "Arial Unicode MS"), out);
    }

    /** 大小写不敏感：输入大写、候选小写也可命中（Locale.ENGLISH 归一化）。 */
    @Test
    public void prefixMatchIsCaseInsensitive() {
        List<String> candidates = Arrays.asList("arial", "Arial", "ARIAL");
        List<String> out = SceneAutocompletePrimitive.filterCandidates(
                "ARI", candidates, SceneAutocompletePrimitive.MatchMode.PREFIX, 8);
        Assert.assertEquals(3, out.size());
        Assert.assertEquals("arial", out.get(0));
        Assert.assertEquals("Arial", out.get(1));
        Assert.assertEquals("ARIAL", out.get(2));
    }

    /** trim：输入前后空白不影响命中。 */
    @Test
    public void prefixMatchTrimsInput() {
        List<String> candidates = Arrays.asList("Segoe UI", "Tahoma");
        List<String> out = SceneAutocompletePrimitive.filterCandidates(
                "  seg ", candidates, SceneAutocompletePrimitive.MatchMode.PREFIX, 8);
        Assert.assertEquals(Collections.singletonList("Segoe UI"), out);
    }

    /** 不命中：返回空列表。 */
    @Test
    public void prefixNoMatchReturnsEmpty() {
        List<String> candidates = Arrays.asList("Arial", "Calibri");
        List<String> out = SceneAutocompletePrimitive.filterCandidates(
                "zzz", candidates, SceneAutocompletePrimitive.MatchMode.PREFIX, 8);
        Assert.assertTrue(out.isEmpty());
    }

    /** limit 截断：达到上限后停止扫描。 */
    @Test
    public void limitTruncatesAtCap() {
        List<String> candidates = Arrays.asList("A1", "A2", "A3", "A4", "A5");
        List<String> out = SceneAutocompletePrimitive.filterCandidates(
                "A", candidates, SceneAutocompletePrimitive.MatchMode.PREFIX, 3);
        Assert.assertEquals(Arrays.asList("A1", "A2", "A3"), out);
    }

    // ==================== filterCandidates（CONTAINS） ====================

    /** CONTAINS 模式：中缀命中。 */
    @Test
    public void containsModeMatchesInfix() {
        List<String> candidates = Arrays.asList("Microsoft Sans Serif", "Sans Serif", "DejaVu Sans");
        List<String> out = SceneAutocompletePrimitive.filterCandidates(
                "sans", candidates, SceneAutocompletePrimitive.MatchMode.CONTAINS, 8);
        Assert.assertEquals(Arrays.asList("Microsoft Sans Serif", "Sans Serif", "DejaVu Sans"), out);
    }

    /** CONTAINS 大小写不敏感。 */
    @Test
    public void containsModeIsCaseInsensitive() {
        List<String> candidates = Arrays.asList("MySansFont", "MySerifFont");
        List<String> out = SceneAutocompletePrimitive.filterCandidates(
                "SANS", candidates, SceneAutocompletePrimitive.MatchMode.CONTAINS, 8);
        Assert.assertEquals(Collections.singletonList("MySansFont"), out);
    }

    // ==================== 边界 ====================

    /** 空输入：返回空列表（不弹浮层）。 */
    @Test
    public void emptyInputReturnsEmpty() {
        List<String> candidates = Arrays.asList("Arial", "Calibri");
        Assert.assertTrue(SceneAutocompletePrimitive.filterCandidates(
                "", candidates, SceneAutocompletePrimitive.MatchMode.PREFIX, 8).isEmpty());
    }

    /** 仅空白输入：normalize 后为空串，返回空。 */
    @Test
    public void blankInputReturnsEmpty() {
        List<String> candidates = Arrays.asList("Arial", "Calibri");
        Assert.assertTrue(SceneAutocompletePrimitive.filterCandidates(
                "   ", candidates, SceneAutocompletePrimitive.MatchMode.PREFIX, 8).isEmpty());
    }

    /** null 输入：normalize 返回空串，返回空。 */
    @Test
    public void nullInputReturnsEmpty() {
        List<String> candidates = Arrays.asList("Arial");
        Assert.assertTrue(SceneAutocompletePrimitive.filterCandidates(
                null, candidates, SceneAutocompletePrimitive.MatchMode.PREFIX, 8).isEmpty());
    }

    /** 空候选集：返回空。 */
    @Test
    public void emptyCandidatesReturnsEmpty() {
        List<String> out = SceneAutocompletePrimitive.filterCandidates(
                "x", Collections.<String>emptyList(), SceneAutocompletePrimitive.MatchMode.PREFIX, 8);
        Assert.assertTrue(out.isEmpty());
    }

    /** null 候选集：返回空（不抛 NPE）。 */
    @Test
    public void nullCandidatesReturnsEmpty() {
        List<String> out = SceneAutocompletePrimitive.filterCandidates(
                "x", null, SceneAutocompletePrimitive.MatchMode.PREFIX, 8);
        Assert.assertTrue(out.isEmpty());
    }

    /** 候选含 null 元素：跳过，不抛 NPE。 */
    @Test
    public void nullCandidateElementIsSkipped() {
        List<String> candidates = Arrays.asList("Arial", null, "Calibri");
        List<String> out = SceneAutocompletePrimitive.filterCandidates(
                "a", candidates, SceneAutocompletePrimitive.MatchMode.PREFIX, 8);
        Assert.assertEquals(Collections.singletonList("Arial"), out);
    }

    /** input 长于所有候选：返回空。 */
    @Test
    public void inputLongerThanAllCandidatesReturnsEmpty() {
        List<String> candidates = Arrays.asList("A", "AB", "ABC");
        List<String> out = SceneAutocompletePrimitive.filterCandidates(
                "ABCDEFG", candidates, SceneAutocompletePrimitive.MatchMode.PREFIX, 8);
        Assert.assertTrue(out.isEmpty());
    }

    /** 全命中：limit 足够大时返回所有候选。 */
    @Test
    public void allMatchWhenLimitLargeEnough() {
        List<String> candidates = Arrays.asList("A1", "A2", "A3");
        List<String> out = SceneAutocompletePrimitive.filterCandidates(
                "A", candidates, SceneAutocompletePrimitive.MatchMode.PREFIX, 100);
        Assert.assertEquals(candidates, out);
    }

    /** limit=0：返回空（视为 0）。 */
    @Test
    public void zeroLimitReturnsEmpty() {
        List<String> candidates = Arrays.asList("A1", "A2");
        Assert.assertTrue(SceneAutocompletePrimitive.filterCandidates(
                "A", candidates, SceneAutocompletePrimitive.MatchMode.PREFIX, 0).isEmpty());
    }

    /** 负 limit：视为 0，返回空。 */
    @Test
    public void negativeLimitReturnsEmpty() {
        List<String> candidates = Arrays.asList("A1", "A2");
        Assert.assertTrue(SceneAutocompletePrimitive.filterCandidates(
                "A", candidates, SceneAutocompletePrimitive.MatchMode.PREFIX, -1).isEmpty());
    }

    /** null matchMode：默认 PREFIX。 */
    @Test
    public void nullMatchModeDefaultsToPrefix() {
        List<String> candidates = Arrays.asList("Arial", "Calibri");
        List<String> out = SceneAutocompletePrimitive.filterCandidates("ari", candidates, null, 8);
        Assert.assertEquals(Collections.singletonList("Arial"), out);
    }

    /** 结果不可变（防御 caller 改写）。 */
    @Test(expected = UnsupportedOperationException.class)
    public void resultIsImmutable() {
        List<String> candidates = Arrays.asList("Arial", "Calibri");
        List<String> out = SceneAutocompletePrimitive.filterCandidates(
                "a", candidates, SceneAutocompletePrimitive.MatchMode.PREFIX, 8);
        out.add("Hacked");
    }

    // ==================== normalize ====================

    /** normalize：trim + toLowerCase(Locale.ENGLISH)，与 FontMatcher.normalizeFontName 同源。 */
    @Test
    public void normalizeTrimsAndLowercases() {
        Assert.assertEquals("arial", SceneAutocompletePrimitive.normalize("  Arial  "));
        Assert.assertEquals("arial", SceneAutocompletePrimitive.normalize("ARIAL"));
        Assert.assertEquals("segoe ui", SceneAutocompletePrimitive.normalize("Segoe UI"));
    }

    /** normalize null 安全。 */
    @Test
    public void normalizeNullSafe() {
        Assert.assertEquals("", SceneAutocompletePrimitive.normalize(null));
        Assert.assertEquals("", SceneAutocompletePrimitive.normalize(""));
        Assert.assertEquals("", SceneAutocompletePrimitive.normalize("   "));
    }

    /**
     * Locale 稳定性：I (U+0049) 在 Locale.ENGLISH 下小写仍为 i (U+0069)，
     * 不会变成土耳其 İ (U+0130) 风格 —— 守 oracle 三大陷阱之一。
     *
     * <p>注意：JVM 默认 locale 可能非 ENGLISH，但 normalize 显式传 Locale.ENGLISH 保证与 FontMatcher 同源。
     * 本测断言 ENGLISH 下结果与默认 locale 无关。</p>
     */
    @Test
    public void normalizeStableAcrossLocales() {
        // 强制重置默认 locale 不实际可行（影响其它测试），但 ENGLISH 显式传参保证归一化稳定
        String result = SceneAutocompletePrimitive.normalize("INPUT");
        // ENGLISH 下 I → i（不会变土耳其 İ），断言长度不变且首字符为 ASCII i
        Assert.assertEquals(5, result.length());
        Assert.assertEquals('i', result.charAt(0));
        // 对照：在 TR locale 下 toLowerCase 可能不同；显式 ENGLISH 必须与 ENGLISH 一致
        Assert.assertEquals("INPUT".toLowerCase(Locale.ENGLISH), result);
    }

    // ==================== isExactMatchInFiltered ====================

    /** filtered 单项且与 input 归一化相等 → true（应抑制浮层）。 */
    @Test
    public void exactMatchInFilteredSingleReturnsTrue() {
        Assert.assertTrue(SceneAutocompletePrimitive.isExactMatchInFiltered(
                "Arial", Collections.singletonList("Arial")));
    }

    /** 大小写/空白差异但归一化相等 → true。 */
    @Test
    public void exactMatchInFilteredAcceptsCaseAndWhitespaceDifference() {
        Assert.assertTrue(SceneAutocompletePrimitive.isExactMatchInFiltered(
                "  ARIAL ", Collections.singletonList("arial")));
    }

    /** filtered 多项时，只要其中任一项与 input 精确相等就应抑制浮层。 */
    @Test
    public void exactMatchInFilteredMultipleReturnsTrueWhenAnyCandidateMatches() {
        Assert.assertTrue(SceneAutocompletePrimitive.isExactMatchInFiltered(
                "Arial", Arrays.asList("Arial", "Arial Black")));
    }

    /** 前缀/子串命中但没有精确相等项 → false。 */
    @Test
    public void exactMatchInFilteredPartialButNotExactReturnsFalse() {
        Assert.assertFalse(SceneAutocompletePrimitive.isExactMatchInFiltered(
                "Ari", Arrays.asList("Arial", "Arial Black")));
        Assert.assertFalse(SceneAutocompletePrimitive.isExactMatchInFiltered(
                "rial", Arrays.asList("Arial", "Arial Black")));
    }

    /** input 为 null：normalize 后空串；filtered 单项非空 → false。 */
    @Test
    public void exactMatchInFilteredNullInputReturnsFalse() {
        Assert.assertFalse(SceneAutocompletePrimitive.isExactMatchInFiltered(
                null, Collections.singletonList("Arial")));
    }

    /** input 为空：即使 filtered 含空串也不视作精确命中。 */
    @Test
    public void exactMatchInFilteredEmptyInputReturnsFalse() {
        Assert.assertFalse(SceneAutocompletePrimitive.isExactMatchInFiltered(
                "", Collections.singletonList("")));
    }

    /** filtered 空 → false。 */
    @Test
    public void exactMatchInFilteredEmptyReturnsFalse() {
        Assert.assertFalse(SceneAutocompletePrimitive.isExactMatchInFiltered(
                "ari", Collections.<String>emptyList()));
    }

    /** filtered null → false。 */
    @Test
    public void exactMatchInFilteredNullFilteredReturnsFalse() {
        Assert.assertFalse(SceneAutocompletePrimitive.isExactMatchInFiltered("ari", null));
    }

    /** filtered 含 null 元素：跳过，不抛异常。 */
    @Test
    public void exactMatchInFilteredNullCandidateSafe() {
        Assert.assertFalse(SceneAutocompletePrimitive.isExactMatchInFiltered(
                "Arial", Arrays.asList(null, "Arial Black")));
    }

    // ==================== clamp ====================

    /** clamp 在区间内：原值返回。 */
    @Test
    public void clampInRangeReturnsValue() {
        Assert.assertEquals(5, SceneAutocompletePrimitive.clamp(5, 0, 10));
    }

    /** clamp 超上界：返回上界。 */
    @Test
    public void clampAboveMaxReturnsMax() {
        Assert.assertEquals(10, SceneAutocompletePrimitive.clamp(15, 0, 10));
    }

    /** clamp 超下界：返回下界。 */
    @Test
    public void clampBelowMinReturnsMin() {
        Assert.assertEquals(0, SceneAutocompletePrimitive.clamp(-3, 0, 10));
    }

    /** clamp max<min：返回 min（与 SceneSelectPrimitive.clamp 同语义）。 */
    @Test
    public void clampInvertedBoundsReturnsMin() {
        Assert.assertEquals(5, SceneAutocompletePrimitive.clamp(3, 5, 2));
    }

    /** clamp 等边界：返回该边界。 */
    @Test
    public void clampEqualBoundsReturnsBoundary() {
        Assert.assertEquals(7, SceneAutocompletePrimitive.clamp(7, 7, 7));
        Assert.assertEquals(7, SceneAutocompletePrimitive.clamp(0, 7, 7));
        Assert.assertEquals(7, SceneAutocompletePrimitive.clamp(100, 7, 7));
    }
}
