package club.heiqi.uilib.ui.scene.control;

import org.junit.Assert;
import org.junit.Test;

/**
 * SceneTextGeometry 纯数学单元测试（L2 边界）。
 *
 * <p>只测不依赖 {@code SceneRuntime}/{@code Signal}/{@code Consumer} 的纯函数：
 * nullSafe / codePointCount / clampCaretIndex / charOffsetForCodePointIndex /
 * substringByCodePoints / caretIndexFromX。不 import reactive/runtime/input/paint 包。</p>
 *
 * <p>重点固化 emoji 代理对边界：抽取前 {@link SceneTextInputPrimitive} 的
 * {@code substringByCodePoints} 走 {@code charOffsetForCodePointIndex}（多一层幂等 clamp），
 * {@link SceneTextAreaPrimitive} 直接 {@code text.offsetByCodePoints(0, start)}；两版入参
 * 已 clamp 到 {@code [0,max]} 故数学等价，统一取 TextArea 版后用本测试钉死边界行为，
 * 确保两 primitive 替换后行为不变。</p>
 */
public class SceneTextGeometryTest {

    // ==================== nullSafe ====================

    @Test
    public void nullSafe_nullReturnsEmpty() {
        Assert.assertEquals("", SceneTextGeometry.nullSafe(null));
    }

    @Test
    public void nullSafe_emptyKept() {
        Assert.assertEquals("", SceneTextGeometry.nullSafe(""));
    }

    @Test
    public void nullSafe_nonNullKept() {
        Assert.assertEquals("abc", SceneTextGeometry.nullSafe("abc"));
    }

    // ==================== codePointCount ====================

    @Test
    public void codePointCount_emptyIsZero() {
        Assert.assertEquals(0, SceneTextGeometry.codePointCount(""));
    }

    @Test
    public void codePointCount_nullIsZero() {
        Assert.assertEquals(0, SceneTextGeometry.codePointCount(null));
    }

    @Test
    public void codePointCount_ascii() {
        Assert.assertEquals(3, SceneTextGeometry.codePointCount("abc"));
    }

    @Test
    public void codePointCount_cjkOnePerChar() {
        Assert.assertEquals(2, SceneTextGeometry.codePointCount("中文"));
    }

    @Test
    public void codePointCount_surrogatePairCountsAsOne() {
        // 😀 = U+1F600，代理对占 2 个 char 但算 1 个码点
        Assert.assertEquals(3, SceneTextGeometry.codePointCount("a\uD83D\uDE00b"));
    }

    @Test
    public void codePointCount_newlineCountsAsOne() {
        Assert.assertEquals(3, SceneTextGeometry.codePointCount("a\nb"));
    }

    // ==================== clampCaretIndex ====================

    @Test
    public void clampCaretIndex_inRangeUnchanged() {
        Assert.assertEquals(1, SceneTextGeometry.clampCaretIndex("abc", Integer.valueOf(1)));
    }

    @Test
    public void clampCaretIndex_atZero() {
        Assert.assertEquals(0, SceneTextGeometry.clampCaretIndex("abc", Integer.valueOf(0)));
    }

    @Test
    public void clampCaretIndex_atMax() {
        Assert.assertEquals(3, SceneTextGeometry.clampCaretIndex("abc", Integer.valueOf(3)));
    }

    @Test
    public void clampCaretIndex_belowZeroClampedToZero() {
        Assert.assertEquals(0, SceneTextGeometry.clampCaretIndex("abc", Integer.valueOf(-1)));
    }

    @Test
    public void clampCaretIndex_aboveMaxClampedToMax() {
        Assert.assertEquals(3, SceneTextGeometry.clampCaretIndex("abc", Integer.valueOf(5)));
    }

    @Test
    public void clampCaretIndex_nullCaretTreatedAsZero() {
        Assert.assertEquals(0, SceneTextGeometry.clampCaretIndex("abc", null));
    }

    @Test
    public void clampCaretIndex_emptyStringMaxZero() {
        Assert.assertEquals(0, SceneTextGeometry.clampCaretIndex("", Integer.valueOf(5)));
    }

    @Test
    public void clampCaretIndex_surrogatePairMaxIsCodePointCount() {
        // a😀b 共 3 个码点
        Assert.assertEquals(3, SceneTextGeometry.clampCaretIndex("a\uD83D\uDE00b", Integer.valueOf(99)));
    }

    // ==================== charOffsetForCodePointIndex ====================

    @Test
    public void charOffset_asciiBoundary() {
        Assert.assertEquals(0, SceneTextGeometry.charOffsetForCodePointIndex("abc", 0));
        Assert.assertEquals(1, SceneTextGeometry.charOffsetForCodePointIndex("abc", 1));
        Assert.assertEquals(3, SceneTextGeometry.charOffsetForCodePointIndex("abc", 3));
    }

    @Test
    public void charOffset_surrogatePairAdvancesTwoChars() {
        // a😀b：cp0→char0, cp1→char1, cp2→char3（跳过代理对 2 char）, cp3→char4
        String s = "a\uD83D\uDE00b";
        Assert.assertEquals(0, SceneTextGeometry.charOffsetForCodePointIndex(s, 0));
        Assert.assertEquals(1, SceneTextGeometry.charOffsetForCodePointIndex(s, 1));
        Assert.assertEquals(3, SceneTextGeometry.charOffsetForCodePointIndex(s, 2));
        Assert.assertEquals(4, SceneTextGeometry.charOffsetForCodePointIndex(s, 3));
    }

    @Test
    public void charOffset_aboveMaxClamped() {
        Assert.assertEquals(4, SceneTextGeometry.charOffsetForCodePointIndex("a\uD83D\uDE00b", 100));
    }

    @Test
    public void charOffset_belowZeroClamped() {
        Assert.assertEquals(0, SceneTextGeometry.charOffsetForCodePointIndex("abc", -5));
    }

    @Test
    public void charOffset_nullStringTreatedEmpty() {
        Assert.assertEquals(0, SceneTextGeometry.charOffsetForCodePointIndex(null, 0));
    }

    // ==================== substringByCodePoints ====================

    @Test
    public void substring_fullRange() {
        Assert.assertEquals("abc", SceneTextGeometry.substringByCodePoints("abc", 0, 3));
    }

    @Test
    public void substring_emptyRange() {
        Assert.assertEquals("", SceneTextGeometry.substringByCodePoints("abc", 0, 0));
    }

    @Test
    public void substring_middleSlice() {
        Assert.assertEquals("b", SceneTextGeometry.substringByCodePoints("abc", 1, 2));
    }

    @Test
    public void substring_surrogatePairKeptWhole() {
        // 固化两版合并等价性：代理对必须整对截取，不能截出半个代理对
        String s = "a\uD83D\uDE00b";
        Assert.assertEquals("a", SceneTextGeometry.substringByCodePoints(s, 0, 1));
        Assert.assertEquals("\uD83D\uDE00", SceneTextGeometry.substringByCodePoints(s, 1, 2));
        Assert.assertEquals("b", SceneTextGeometry.substringByCodePoints(s, 2, 3));
        Assert.assertEquals("a\uD83D\uDE00b", SceneTextGeometry.substringByCodePoints(s, 0, 3));
        Assert.assertEquals("\uD83D\uDE00b", SceneTextGeometry.substringByCodePoints(s, 1, 3));
    }

    @Test
    public void substring_startAfterEndClampedToEmpty() {
        // end < start 时 end clamp 到 start，返回空串
        Assert.assertEquals("", SceneTextGeometry.substringByCodePoints("abc", 2, 1));
    }

    @Test
    public void substring_endAboveMaxClamped() {
        Assert.assertEquals("abc", SceneTextGeometry.substringByCodePoints("abc", 0, 100));
    }

    @Test
    public void substring_startBelowZeroClamped() {
        Assert.assertEquals("ab", SceneTextGeometry.substringByCodePoints("abc", -1, 2));
    }

    @Test
    public void substring_nullStringReturnsEmpty() {
        Assert.assertEquals("", SceneTextGeometry.substringByCodePoints(null, 0, 0));
    }

    // ==================== caretIndexFromX ====================

    @Test
    public void caretIndexFromX_nullArrayReturnsZero() {
        Assert.assertEquals(0, SceneTextGeometry.caretIndexFromX(null, 50));
    }

    @Test
    public void caretIndexFromX_emptyArrayReturnsZero() {
        Assert.assertEquals(0, SceneTextGeometry.caretIndexFromX(new int[0], 50));
    }

    @Test
    public void caretIndexFromX_singleElementReturnsZero() {
        // [0] → count=0，任何 X 都返回 0
        Assert.assertEquals(0, SceneTextGeometry.caretIndexFromX(new int[] {0}, 50));
    }

    @Test
    public void caretIndexFromX_nonPositiveXReturnsZero() {
        int[] widths = {0, 10, 20};
        Assert.assertEquals(0, SceneTextGeometry.caretIndexFromX(widths, 0));
        Assert.assertEquals(0, SceneTextGeometry.caretIndexFromX(widths, -5));
    }

    @Test
    public void caretIndexFromX_leftHalfOfFirstCellReturnsZero() {
        // [0,10,20]：cell0 中点=5，X<5 归 0
        int[] widths = {0, 10, 20};
        Assert.assertEquals(0, SceneTextGeometry.caretIndexFromX(widths, 4));
    }

    @Test
    public void caretIndexFromX_rightHalfOfFirstCellReturnsOne() {
        // cell0 中点=5，X>=5 进 cell1（中点=15），X<15 归 1
        int[] widths = {0, 10, 20};
        Assert.assertEquals(1, SceneTextGeometry.caretIndexFromX(widths, 5));
        Assert.assertEquals(1, SceneTextGeometry.caretIndexFromX(widths, 14));
    }

    @Test
    public void caretIndexFromX_atOrBeyondLastMidpointReturnsCount() {
        // cell1 中点=15，X>=15 归末尾 count=2
        int[] widths = {0, 10, 20};
        Assert.assertEquals(2, SceneTextGeometry.caretIndexFromX(widths, 15));
        Assert.assertEquals(2, SceneTextGeometry.caretIndexFromX(widths, 100));
    }

    @Test
    public void caretIndexFromX_singleCellArray() {
        // [0,10]：count=1，cell0 中点=5
        int[] widths = {0, 10};
        Assert.assertEquals(0, SceneTextGeometry.caretIndexFromX(widths, 4));
        Assert.assertEquals(1, SceneTextGeometry.caretIndexFromX(widths, 5));
        Assert.assertEquals(1, SceneTextGeometry.caretIndexFromX(widths, 6));
    }
}
