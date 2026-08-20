package club.heiqi.uilib.font.util;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.util.UnicodeTextClassifier.CharClass;

/**
 * {@link UnicodeTextClassifier} 控制字符分类边界测试。
 */
public class UnicodeTextClassifierTest {

    @Test
    public void shouldClassifyNewlineFamily() {
        int[] newlines = { '\n', '\r', '\u000B', '\f', 0x0085, 0x2028, 0x2029 };
        for (int cp : newlines) {
            Assert.assertEquals("U+" + Integer.toHexString(cp), CharClass.NEWLINE,
                    UnicodeTextClassifier.classify(cp));
            Assert.assertTrue(UnicodeTextClassifier.isLineBreak(cp));
            Assert.assertTrue(UnicodeTextClassifier.isRenderSkipped(cp));
            Assert.assertTrue(UnicodeTextClassifier.isZeroWidth(cp));
            Assert.assertFalse(UnicodeTextClassifier.isStripped(cp));
        }
    }

    @Test
    public void shouldClassifyTab() {
        Assert.assertEquals(CharClass.TAB, UnicodeTextClassifier.classify('\t'));
        Assert.assertTrue(UnicodeTextClassifier.isTab('\t'));
        Assert.assertTrue(UnicodeTextClassifier.isWordBoundary('\t'));
        Assert.assertFalse(UnicodeTextClassifier.isZeroWidth('\t'));
        Assert.assertFalse(UnicodeTextClassifier.isLineBreak('\t'));
    }

    @Test
    public void shouldClassifyDocumentSpace() {
        Assert.assertEquals(CharClass.SPACE, UnicodeTextClassifier.classify(' '));
        Assert.assertTrue(UnicodeTextClassifier.isFoldableSpace(' '));
        Assert.assertTrue(UnicodeTextClassifier.isWordBoundary(' '));
        Assert.assertTrue(UnicodeTextClassifier.isTrailingFoldable(' '));
    }

    @Test
    public void shouldClassifyBreakableSpaceFamily() {
        int[] spaces = { 0x1680, 0x2000, 0x2001, 0x2006, 0x2008, 0x200A, 0x205F, 0x3000 };
        for (int cp : spaces) {
            Assert.assertEquals("U+" + Integer.toHexString(cp), CharClass.BREAKABLE_SPACE,
                    UnicodeTextClassifier.classify(cp));
            Assert.assertTrue(UnicodeTextClassifier.isFoldableSpace(cp));
            Assert.assertTrue(UnicodeTextClassifier.isWordBoundary(cp));
            Assert.assertFalse(UnicodeTextClassifier.isTrailingFoldable(cp));
            Assert.assertFalse(UnicodeTextClassifier.isGlue(cp));
        }
    }

    @Test
    public void shouldClassifyGlueAsNonBreaking() {
        int[] glue = { 0x00A0, 0x2007, 0x2011, 0x202F, 0x180E };
        for (int cp : glue) {
            Assert.assertEquals("U+" + Integer.toHexString(cp), CharClass.GLUE,
                    UnicodeTextClassifier.classify(cp));
            Assert.assertTrue(UnicodeTextClassifier.isGlue(cp));
            Assert.assertFalse("GL 胶水不是断词分隔", UnicodeTextClassifier.isWordBoundary(cp));
            Assert.assertFalse(UnicodeTextClassifier.isFoldableSpace(cp));
            Assert.assertFalse(UnicodeTextClassifier.isTrailingFoldable(cp));
        }
    }

    @Test
    public void shouldClassifySoftBreakAndSoftHyphen() {
        Assert.assertEquals(CharClass.SOFT_BREAK, UnicodeTextClassifier.classify(0x200B));
        Assert.assertEquals(CharClass.SOFT_HYPHEN, UnicodeTextClassifier.classify(0x00AD));
        Assert.assertTrue(UnicodeTextClassifier.isSoftBreakOpportunity(0x200B));
        Assert.assertTrue(UnicodeTextClassifier.isSoftBreakOpportunity(0x00AD));
        Assert.assertTrue(UnicodeTextClassifier.isSoftHyphen(0x00AD));
        Assert.assertFalse(UnicodeTextClassifier.isSoftHyphen(0x200B));
        Assert.assertTrue(UnicodeTextClassifier.isZeroWidth(0x200B));
        Assert.assertTrue(UnicodeTextClassifier.isZeroWidth(0x00AD));
    }

    @Test
    public void shouldClassifyJoiners() {
        Assert.assertEquals(CharClass.JOINER, UnicodeTextClassifier.classify(0x200C));
        Assert.assertEquals(CharClass.JOINER, UnicodeTextClassifier.classify(0x200D));
        Assert.assertTrue(UnicodeTextClassifier.isZeroWidth(0x200C));
        Assert.assertTrue(UnicodeTextClassifier.isZeroWidth(0x200D));
        Assert.assertFalse(UnicodeTextClassifier.isClusterContinuation(0x200D));
    }

    @Test
    public void shouldClassifyVariationSelectors() {
        int[] selectors = { 0xFE00, 0xFE0F, 0xE0100, 0xE01EF };
        for (int cp : selectors) {
            Assert.assertEquals(CharClass.VARIATION_SELECTOR, UnicodeTextClassifier.classify(cp));
            Assert.assertTrue(UnicodeTextClassifier.isClusterContinuation(cp));
            Assert.assertTrue(UnicodeTextClassifier.isZeroWidth(cp));
        }
    }

    @Test
    public void shouldClassifyCombiningMarks() {
        int[] marks = { 0x0301, 0x0903, 0x20DD };
        for (int cp : marks) {
            Assert.assertEquals(CharClass.COMBINING_MARK, UnicodeTextClassifier.classify(cp));
            Assert.assertTrue(UnicodeTextClassifier.isClusterContinuation(cp));
            Assert.assertFalse(UnicodeTextClassifier.isZeroWidth(cp));
        }
    }

    @Test
    public void shouldClassifyBidiControls() {
        int[] bidi = { 0x200E, 0x200F, 0x061C, 0x202A, 0x202B, 0x202C, 0x202D, 0x202E,
                0x2066, 0x2067, 0x2068, 0x2069 };
        for (int cp : bidi) {
            Assert.assertEquals("U+" + Integer.toHexString(cp), CharClass.BIDI_CONTROL,
                    UnicodeTextClassifier.classify(cp));
            Assert.assertTrue(UnicodeTextClassifier.isStripped(cp));
            Assert.assertTrue(UnicodeTextClassifier.isZeroWidth(cp));
        }
    }

    @Test
    public void shouldClassifyInvisibleControls() {
        int[] invisible = { 0x0000, 0x0001, 0x0008, 0x000E, 0x001F, 0x007F, 0x0080, 0x009F,
                0xFEFF, 0x2060, 0x2061, 0x2062, 0x2063, 0x2064 };
        for (int cp : invisible) {
            Assert.assertEquals("U+" + Integer.toHexString(cp), CharClass.INVISIBLE,
                    UnicodeTextClassifier.classify(cp));
            Assert.assertTrue(UnicodeTextClassifier.isStripped(cp));
            Assert.assertTrue(UnicodeTextClassifier.isZeroWidth(cp));
        }
        // NEL(U+0085) 属换行类而非不可见剥离类
        Assert.assertFalse(UnicodeTextClassifier.isStripped(0x0085));
    }

    @Test
    public void shouldClassifyRemainingFormatCharacters() {
        int[] format = { 0x0600, 0x0605, 0x06DD, 0x070F, 0x0890, 0x0891, 0x08E2,
                0x110BD, 0x110CD, 0x13430, 0x13436, 0x1BCA0, 0x1BCA3, 0x1D173, 0x1D17A,
                0xE0001, 0xE0020, 0xE007F, 0x2065, 0xFFF9, 0xFFFB };
        for (int cp : format) {
            Assert.assertEquals("U+" + Integer.toHexString(cp), CharClass.INVISIBLE,
                    UnicodeTextClassifier.classify(cp));
            Assert.assertTrue(UnicodeTextClassifier.isStripped(cp));
            Assert.assertTrue(UnicodeTextClassifier.isZeroWidth(cp));
        }
    }

    @Test
    public void shouldClassifyMongolianVariationSelectors() {
        int[] fvs = { 0x180B, 0x180C, 0x180D, 0x180F };
        for (int cp : fvs) {
            Assert.assertEquals(CharClass.VARIATION_SELECTOR, UnicodeTextClassifier.classify(cp));
            Assert.assertTrue(UnicodeTextClassifier.isClusterContinuation(cp));
            Assert.assertTrue(UnicodeTextClassifier.isZeroWidth(cp));
        }
        // MONGOLIAN VOWEL SEPARATOR：GL 禁断胶水（历史上 Zs，现 Cf）
        Assert.assertEquals(CharClass.GLUE, UnicodeTextClassifier.classify(0x180E));
        Assert.assertTrue(UnicodeTextClassifier.isGlue(0x180E));
    }

    @Test
    public void shouldClassifyRemainingDefaultIgnorable() {
        // DerivedCoreProperties.txt Default_Ignorable 补全（审计批次2）
        int[] ignorable = { 0x034F, 0x17B4, 0x17B5, 0x206A, 0x206B, 0x206E, 0x206F,
                0xFFA0, 0xFFF0, 0xFFF8, 0xE0000, 0xE0002, 0xE0080, 0xE01F0, 0x13439, 0x16FE4 };
        for (int cp : ignorable) {
            Assert.assertEquals("U+" + Integer.toHexString(cp), CharClass.INVISIBLE,
                    UnicodeTextClassifier.classify(cp));
            Assert.assertTrue(UnicodeTextClassifier.isZeroWidth(cp));
        }
    }

    @Test
    public void shouldClassifyNoncharactersAndHangulFillers() {
        int[] invisible = { 0xFFFE, 0xFFFF, 0x10FFFE, 0x10FFFF, 0xFDD0, 0xFDEF,
                0x115F, 0x1160, 0x3164, 0xD800, 0xDFFF };
        for (int cp : invisible) {
            Assert.assertEquals("U+" + Integer.toHexString(cp), CharClass.INVISIBLE,
                    UnicodeTextClassifier.classify(cp));
            Assert.assertTrue(UnicodeTextClassifier.isZeroWidth(cp));
        }
    }

    @Test
    public void shouldKeepPrivateUseAndUnassignedAsRegular() {
        // 私用区不是控制字符；未分配码点保留 REGULAR（未来可能被 Unicode 分配，豆腐块是诚实反馈）
        Assert.assertEquals(CharClass.REGULAR, UnicodeTextClassifier.classify(0xE000));
        Assert.assertEquals(CharClass.REGULAR, UnicodeTextClassifier.classify(0x0378));
    }

    @Test
    public void shouldClassifyRegularCharacters() {
        Assert.assertEquals(CharClass.REGULAR, UnicodeTextClassifier.classify('A'));
        Assert.assertEquals(CharClass.REGULAR, UnicodeTextClassifier.classify('中'));
        Assert.assertEquals(CharClass.REGULAR, UnicodeTextClassifier.classify(0x1F600));
        Assert.assertFalse(UnicodeTextClassifier.isLineBreak('A'));
        Assert.assertFalse(UnicodeTextClassifier.isZeroWidth('A'));
        Assert.assertFalse(UnicodeTextClassifier.isWordBoundary('A'));
    }
}
