package club.heiqi.uilib.util;

import org.junit.Assert;
import org.junit.Test;

/**
 * {@link UiNumbers} 契约测试：正对照 + NaN/±Infinity/负零/逆区间/int 溢出边界矩阵。
 *
 * <p>核心手法是「golden 逐位对照」：对每个特殊值 × 每组合界，断言
 * {@code UiNumbers.clamp} 与被 R2 收编的两套原实现序
 * （{@code Math.max(lo, Math.min(v, hi))} 渲染层序 与 {@code Math.max(lo, Math.min(hi, v))}
 * 场景层序，含 {@code if (hi &lt; lo) return lo} 显式守卫变体）逐位一致
 * （{@code Double.compare}/{@code Float.compare} 判等，能区分 +0/-0 且 NaN==NaN）。
 * 这钉死了「合并语义零变更」的承诺本身。</p>
 */
public class UiNumbersTest {

    /** double 特殊值矩阵（含 NaN、±Inf、±0、极值）。 */
    private static final double[] DOUBLE_SPECIALS = {
            Double.NaN,
            Double.NEGATIVE_INFINITY,
            -Double.MAX_VALUE,
            -2.0D,
            -1.0D,
            -0.0D,
            0.0D,
            Double.MIN_VALUE,
            0.5D,
            1.0D,
            2.0D,
            Double.MAX_VALUE,
            Double.POSITIVE_INFINITY,
    };

    /** float 特殊值矩阵。 */
    private static final float[] FLOAT_SPECIALS = {
            Float.NaN,
            Float.NEGATIVE_INFINITY,
            -Float.MAX_VALUE,
            -2.0F,
            -1.0F,
            -0.0F,
            0.0F,
            Float.MIN_VALUE,
            0.5F,
            1.0F,
            2.0F,
            Float.MAX_VALUE,
            Float.POSITIVE_INFINITY,
    };

    /** int 特殊值矩阵（含溢出端点）。 */
    private static final int[] INT_SPECIALS = {
            Integer.MIN_VALUE, -5, 0, 5, Integer.MAX_VALUE,
    };

    /** 界组合矩阵（含逆区间、上下界相等、溢出端点作界）。 */
    private static final int[][] INT_BOUNDS = {
            {0, 10}, {-10, 10}, {10, 0}, {5, 5},
            {Integer.MIN_VALUE, Integer.MAX_VALUE}, {Integer.MAX_VALUE, Integer.MIN_VALUE},
            {0, Integer.MAX_VALUE}, {-1, -5}, {Integer.MIN_VALUE, Integer.MIN_VALUE},
    };

    /** double/float 界组合矩阵（含逆区间、相等界、±Inf 作界）。 */
    private static final double[][] DOUBLE_BOUNDS = {
            {0.0D, 1.0D},
            {-1.0D, 1.0D},
            {1.0D, 0.0D},
            {0.5D, 0.5D},
            {Double.NaN, 1.0D},
            {0.0D, Double.POSITIVE_INFINITY},
            {Double.NEGATIVE_INFINITY, 0.0D},
            {-Double.MAX_VALUE, Double.MAX_VALUE},
    };

    private static final float[][] FLOAT_BOUNDS = {
            {0.0F, 1.0F},
            {-1.0F, 1.0F},
            {1.0F, 0.0F},
            {0.5F, 0.5F},
            {Float.NaN, 1.0F},
            {0.0F, Float.POSITIVE_INFINITY},
            {Float.NEGATIVE_INFINITY, 0.0F},
    };

    // ==================== 正对照 ====================

    @Test
    public void positiveControlInsideRangeReturnsValueUnchanged() {
        Assert.assertEquals(5, UiNumbers.clamp(5, 0, 10));
        Assert.assertEquals(2.5D, UiNumbers.clamp(2.5D, 0.0D, 10.0D), 0.0D);
        Assert.assertEquals(0.25F, UiNumbers.clamp(0.25F, 0.0F, 10.0F), 0.0F);
        Assert.assertEquals(0.75F, UiNumbers.clamp01(0.75F), 0.0F);
        Assert.assertEquals(7, UiNumbers.clamp(7, 7, 7));
    }

    // ==================== int 矩阵 ====================

    @Test
    public void intClampBitwiseMatchesBothLegacyOrdersOnBoundaryMatrix() {
        int rows = 0;
        for (int value : INT_SPECIALS) {
            for (int[] b : INT_BOUNDS) {
                int min = b[0];
                int max = b[1];
                int orderA = Math.max(min, Math.min(max, value));   // 场景层原实现序
                int orderB = Math.max(min, Math.min(value, max));   // 渲染层原实现序
                int guarded = max < min ? min : orderA;             // 带显式守卫的场景层变体
                int actual = UiNumbers.clamp(value, min, max);
                Assert.assertEquals("orderA v=" + value + " [" + min + "," + max + "]", orderA, actual);
                Assert.assertEquals("orderB v=" + value + " [" + min + "," + max + "]", orderB, actual);
                Assert.assertEquals("guarded v=" + value + " [" + min + "," + max + "]", guarded, actual);
                rows++;
            }
        }
        Assert.assertEquals(45, rows);
    }

    @Test
    public void intClampInvertedBoundsReturnsMinAndNoOverflow() {
        // 逆区间一律返回 min（与带守卫旧实现一致）；纯比较选择，不产生算术溢出。
        Assert.assertEquals(10, UiNumbers.clamp(5, 10, 0));
        Assert.assertEquals(Integer.MAX_VALUE, UiNumbers.clamp(0, Integer.MAX_VALUE, Integer.MIN_VALUE));
        Assert.assertEquals(0, UiNumbers.clamp(Integer.MIN_VALUE, 0, Integer.MAX_VALUE));
        Assert.assertEquals(0, UiNumbers.clamp(Integer.MAX_VALUE, Integer.MIN_VALUE, 0));
    }

    // ==================== double 矩阵 ====================

    @Test
    public void doubleClampBitwiseMatchesBothLegacyOrdersOnSpecials() {
        int rows = 0;
        for (double value : DOUBLE_SPECIALS) {
            for (double[] b : DOUBLE_BOUNDS) {
                double min = b[0];
                double max = b[1];
                double expected = Math.max(min, Math.min(value, max));   // 渲染层原实现序
                double alt = Math.max(min, Math.min(max, value));        // 场景层原实现序
                Assert.assertTrue(Double.isNaN(expected) == Double.isNaN(alt));
                if (!Double.isNaN(expected)) { // Math.min/max 交换律：两种次序逐位相同（±0 含内）
                    Assert.assertEquals(0, Double.compare(expected, alt));
                }
                double actual = UiNumbers.clamp(value, min, max);
                if (Double.isNaN(expected)) {
                    Assert.assertTrue("NaN v=" + value + " [" + min + "," + max + "]",
                            Double.isNaN(actual));
                } else {
                    Assert.assertEquals(0, Double.compare(expected, actual));
                }
                rows++;
            }
        }
        Assert.assertEquals(104, rows);
    }

    @Test
    public void doubleClampNaNPropagatesAndInfinitiesHitBounds() {
        Assert.assertTrue(Double.isNaN(UiNumbers.clamp(Double.NaN, 0.0D, 1.0D)));
        Assert.assertEquals(1.0D, UiNumbers.clamp(Double.POSITIVE_INFINITY, 0.0D, 1.0D), 0.0D);
        Assert.assertEquals(0.0D, UiNumbers.clamp(Double.NEGATIVE_INFINITY, 0.0D, 1.0D), 0.0D);
        // 界自身为 +Inf/-Inf 时保持
        Assert.assertEquals(Double.POSITIVE_INFINITY,
                UiNumbers.clamp(Double.POSITIVE_INFINITY, 0.0D, Double.POSITIVE_INFINITY), 0.0D);
    }

    @Test
    public void doubleClampNegativeZeroFollowsLegacyMaxSemantics() {
        // Math.max(+0.0, -0.0) == +0.0：下界为 +0 时 -0 输入归 +0（Double.compare 区分 ±0）
        Assert.assertEquals(0, Double.compare(0.0D, UiNumbers.clamp(-0.0D, 0.0D, 1.0D)));
        // 下界非 ±0 语义域（min<0）时 -0 原样透传
        Assert.assertEquals(0, Double.compare(-0.0D, UiNumbers.clamp(-0.0D, -1.0D, 1.0D)));
    }

    // ==================== float 矩阵 ====================

    @Test
    public void floatClampBitwiseMatchesBothLegacyOrdersOnSpecials() {
        int rows = 0;
        for (float value : FLOAT_SPECIALS) {
            for (float[] b : FLOAT_BOUNDS) {
                float min = b[0];
                float max = b[1];
                float orderA = Math.max(min, Math.min(max, value));  // 场景层原实现序
                float orderB = Math.max(min, Math.min(value, max));  // 渲染层原实现序
                float actual = UiNumbers.clamp(value, min, max);
                if (Float.isNaN(orderA)) {
                    Assert.assertTrue("NaN v=" + value, Float.isNaN(actual));
                } else {
                    Assert.assertEquals(0, Float.compare(orderA, actual));
                    Assert.assertEquals(0, Float.compare(orderB, actual));
                }
                rows++;
            }
        }
        Assert.assertEquals(91, rows);
    }

    @Test
    public void clamp01BitwiseMatchesLegacyIdiomOnSpecials() {
        int rows = 0;
        for (float value : FLOAT_SPECIALS) {
            float legacy = Math.max(0.0F, Math.min(1.0F, value));   // R2 前渲染层习语原文
            float actual = UiNumbers.clamp01(value);
            if (Float.isNaN(legacy)) {
                Assert.assertTrue("NaN v=" + value, Float.isNaN(actual));
            } else {
                Assert.assertEquals(0, Float.compare(legacy, actual));
            }
            // clamp01 == 通用 float clamp 同界
            Assert.assertEquals(0, Float.compare(UiNumbers.clamp(value, 0.0F, 1.0F), actual));
            rows++;
        }
        Assert.assertEquals(13, rows);
    }

    @Test
    public void clamp01NegativeZeroNormalizedToPositiveZero() {
        // max(+0F, min(1F, -0F)) = max(+0F, -0F) = +0F（位级钉死）
        Assert.assertEquals(0, Float.compare(0.0F, UiNumbers.clamp01(-0.0F)));
        Assert.assertTrue(Float.compare(UiNumbers.clamp01(-0.0F), -0.0F) > 0);
    }

    @Test
    public void clamp01InfinitiesAndNaN() {
        Assert.assertEquals(1.0F, UiNumbers.clamp01(Float.POSITIVE_INFINITY), 0.0F);
        Assert.assertEquals(0.0F, UiNumbers.clamp01(Float.NEGATIVE_INFINITY), 0.0F);
        Assert.assertTrue(Float.isNaN(UiNumbers.clamp01(Float.NaN)));
    }

    // ==================== isFinite ====================

    @Test
    public void doubleIsFiniteMatchesHandwrittenPredicateAndJdk() {
        double[] values = {
                Double.NaN, Double.NEGATIVE_INFINITY, -Double.MAX_VALUE, -1.0D, -0.0D, 0.0D,
                Double.MIN_VALUE, 1.0D, Double.MAX_VALUE, Double.POSITIVE_INFINITY,
        };
        for (double value : values) {
            boolean handwritten = !Double.isNaN(value) && !Double.isInfinite(value); // 收编前原文
            Assert.assertEquals("jdk " + value, Double.isFinite(value), UiNumbers.isFinite(value));
            Assert.assertEquals("handwritten " + value, handwritten, UiNumbers.isFinite(value));
        }
        Assert.assertTrue(UiNumbers.isFinite(-0.0D));
        Assert.assertTrue(UiNumbers.isFinite(Double.MIN_VALUE));
        Assert.assertFalse(UiNumbers.isFinite(Double.NaN));
        Assert.assertFalse(UiNumbers.isFinite(Double.POSITIVE_INFINITY));
        Assert.assertFalse(UiNumbers.isFinite(Double.NEGATIVE_INFINITY));
    }

    @Test
    public void floatIsFiniteMatchesHandwrittenPredicateAndJdk() {
        float[] values = {
                Float.NaN, Float.NEGATIVE_INFINITY, -Float.MAX_VALUE, -1.0F, -0.0F, 0.0F,
                Float.MIN_VALUE, 1.0F, Float.MAX_VALUE, Float.POSITIVE_INFINITY,
        };
        for (float value : values) {
            boolean handwritten = !Float.isNaN(value) && !Float.isInfinite(value); // 收编前原文
            Assert.assertEquals("jdk " + value, Float.isFinite(value), UiNumbers.isFinite(value));
            Assert.assertEquals("handwritten " + value, handwritten, UiNumbers.isFinite(value));
        }
        Assert.assertTrue(UiNumbers.isFinite(-0.0F));
        Assert.assertFalse(UiNumbers.isFinite(Float.NaN));
    }
}
