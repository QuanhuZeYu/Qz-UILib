package club.heiqi.uilib.font;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.config.FontCharacterRuleSet;
import club.heiqi.uilib.font.config.FontConfig;

/**
 * "字体运行时能表示什么配置"判据与构造校验的一致性锁（#71 同族审计 A1 的底座）。
 *
 * <p>A1 的根因是约束装错了地方：新栈 schema 声明了 range，但只有配置 UI 的提交路径消费它，
 * 启动加载完全不读，于是手改文件的越界值一路直写静态字段，最后撞在
 * {@link FontRuntimeSettings} 的构造校验上，把 {@code FontService} 饿汉单例的类初始化炸掉。
 * 修复方式是在回灌侧问判据；判据一旦和构造校验各写一份，就会出现"判据说行、构造说不行"
 * 的第三种崩溃。本类把两者钉成同一结论，谁漂移谁红。</p>
 *
 * <p>覆盖范围：能用公开构造器表达的 5 个字段用矩阵逐个探测；{@code atlasTextureScale}
 * 只能经 {@link FontRuntimeSettings#capture()} 进入（配置侧不写它），单独用真实漏斗覆盖。</p>
 */
public class FontRuntimeSettingsRepresentabilityTest {

    /** 探测值：合法、边界、越界、零/负、非有限都在内。 */
    private static final double[] PROBE_VALUES = {
            0.0D, -1.0D, 1.0D, 2.5D, 3.0D, 3.5D, 9.0D, 8.0D, 72.0D, 90.0D, 256.0D, 257.0D,
            Double.MIN_VALUE, 1.0E308D, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
    };

    /** 公开构造器能表达的字段（atlasTextureScale 不在其中，见类级说明）。 */
    private static final String[] CONSTRUCTED_FIELDS = {
            FontRuntimeSettings.FIELD_LERP_MODE,
            FontRuntimeSettings.FIELD_AWT_CHAR_SIZE,
            FontRuntimeSettings.FIELD_CHAR_SIZE,
            FontRuntimeSettings.FIELD_SPACE_WIDTH,
            FontRuntimeSettings.FIELD_CHARACTER_SPACING,
    };

    /**
     * 核心锁：判据说"可表示"当且仅当构造函数真的接受。
     */
    @Test
    public void predicateAndConstructorAlwaysAgree() {
        for (String field : CONSTRUCTED_FIELDS) {
            for (double value : PROBE_VALUES) {
                if (FontRuntimeSettings.FIELD_LERP_MODE.equals(field) && !isIntDomainValue(value)) {
                    // lerpMode 在配置侧是 double、在构造器是 int，收窄发生在回灌层
                    // （Math.round(NaN)=0、Math.round(Infinity)=Integer.MAX_VALUE 会伪装成合法值，
                    //  所以必须先按 double 判据修好再收窄）。跨域部分由另两条用例专门锁。
                    continue;
                }
                boolean representable = FontRuntimeSettings.isRepresentable(field, value);
                boolean accepted = constructorAccepts(field, value);
                Assert.assertEquals("判据与构造校验对 " + field + "=" + value + " 结论不一致："
                                + "判据可表示=" + representable + "，构造器接受=" + accepted,
                        representable, accepted);
            }
        }
    }

    /**
     * 跨域锁：lerpMode 的非有限值必须在 double 域就被判据拒绝。
     *
     * <p>它永远进不了 int 构造器，因此不在矩阵里；但它是 A1 的真实入口之一——
     * 若判据放行 NaN，回灌侧的 Math.round 会把它变成合法的 0，坏值就伪装过关了。</p>
     */
    @Test
    public void nonFiniteLerpModeIsRejectedBeforeNarrowing() {
        Assert.assertFalse("NaN 必须在收窄成 int 之前就被拒绝",
                FontRuntimeSettings.isRepresentable(FontRuntimeSettings.FIELD_LERP_MODE, Double.NaN));
        Assert.assertFalse("Infinity 同理（Math.round 会把它变成 Integer.MAX_VALUE）",
                FontRuntimeSettings.isRepresentable(FontRuntimeSettings.FIELD_LERP_MODE,
                        Double.POSITIVE_INFINITY));
        Assert.assertFalse("负 Infinity 同理",
                FontRuntimeSettings.isRepresentable(FontRuntimeSettings.FIELD_LERP_MODE,
                        Double.NEGATIVE_INFINITY));
        Assert.assertTrue("0 与 3 是合法两端",
                FontRuntimeSettings.isRepresentable(FontRuntimeSettings.FIELD_LERP_MODE, 0.0D)
                        && FontRuntimeSettings.isRepresentable(FontRuntimeSettings.FIELD_LERP_MODE, 3.0D));
    }

    /**
     * 判据必须比配置 UI 的 range 宽：产品能表示的高值不得被判成坏值。
     *
     * <p>{@code charSize: 90} 超出 schema 声明的 1..72（那是 UI 滑条上限），但字体运行时
     * 完全能表示它。这条断言把 A1 选定语义钉住："只钳产品无法表示的值"，
     * 谁把判据改成按 schema 范围判，这里立刻红。</p>
     */
    @Test
    public void legalValuesBeyondUiRangesAreNotTreatedAsBroken() {
        Assert.assertTrue("charSize=90 超出 UI 上限但产品可表示",
                FontRuntimeSettings.isRepresentable(FontRuntimeSettings.FIELD_CHAR_SIZE, 90.0D));
        Assert.assertTrue("awtCharSize=300 超出 UI 上限但产品可表示",
                FontRuntimeSettings.isRepresentable(FontRuntimeSettings.FIELD_AWT_CHAR_SIZE, 300.0D));
        Assert.assertTrue("spaceWidth 允许 0 与负值（有明确语义）",
                FontRuntimeSettings.isRepresentable(FontRuntimeSettings.FIELD_SPACE_WIDTH, 0.0D)
                        && FontRuntimeSettings.isRepresentable(FontRuntimeSettings.FIELD_SPACE_WIDTH, -2.0D));
    }

    /**
     * atlasTextureScale 的真实漏斗：capture() 的接受域与判据一致。
     */
    @Test
    public void atlasTextureScaleGuardedThroughCaptureFunnel() {
        double saved = FontConfig.atlasTextureScale;
        try {
            FontConfig.atlasTextureScale = 0.0D;
            Assert.assertFalse("判据必须认定 0 不可表示",
                    FontRuntimeSettings.isRepresentable(FontRuntimeSettings.FIELD_ATLAS_TEXTURE_SCALE, 0.0D));
            try {
                FontRuntimeSettings.capture();
                Assert.fail("构造校验必须仍然拒绝不可表示的值（判据不是用来放松不变量的）");
            } catch (IllegalArgumentException expected) {
                Assert.assertTrue("异常文案要指名被拒字段，实际：" + expected.getMessage(),
                        expected.getMessage().contains(FontRuntimeSettings.FIELD_ATLAS_TEXTURE_SCALE));
            }
            FontConfig.atlasTextureScale = 64.0D;
            Assert.assertEquals("可表示的值应当正常建出快照", 64.0D,
                    FontRuntimeSettings.capture().getAtlasTextureScale(), 0.0D);
        } finally {
            FontConfig.atlasTextureScale = saved;
        }
    }

    /**
     * 未列入规则表的字段：本类不约束它，一律放行（不是漏判），因此调用方只准传 FIELD_* 常量。
     */
    @Test
    public void unlistedFieldsAreReportedAsUnconstrained() {
        Assert.assertTrue("aaMode 不由本类校验",
                FontRuntimeSettings.isRepresentable("aaMode", -1.0D));
        // 拼错的字段名同样会被放行——这不是判据的漏洞，而是它不认识的字段一律不约束。
        // 因此调用方必须传 FIELD_* 常量；写错常量的后果由用例 D（真实 YAML 越界不崩）兜住。
        Assert.assertTrue("拼错的字段名按未约束处理（所以只准传 FIELD_* 常量）",
                FontRuntimeSettings.isRepresentable("charSze", -1.0D));
    }

    private static boolean isIntDomainValue(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && Math.floor(value) == value
                && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE;
    }

    /**
     * 把探测值代入指定字段，问公开构造器要不要。
     */
    private static boolean constructorAccepts(String field, double value) {
        int lerpMode = 3;
        double awtCharSize = 64.0D;
        double charSize = 9.0D;
        double spaceWidth = 4.0D;
        double characterSpacing = 0.1D;
        if (FontRuntimeSettings.FIELD_LERP_MODE.equals(field)) {
            lerpMode = (int) value;
        } else if (FontRuntimeSettings.FIELD_AWT_CHAR_SIZE.equals(field)) {
            awtCharSize = value;
        } else if (FontRuntimeSettings.FIELD_CHAR_SIZE.equals(field)) {
            charSize = value;
        } else if (FontRuntimeSettings.FIELD_SPACE_WIDTH.equals(field)) {
            spaceWidth = value;
        } else if (FontRuntimeSettings.FIELD_CHARACTER_SPACING.equals(field)) {
            characterSpacing = value;
        } else {
            throw new IllegalArgumentException("测试未覆盖字段 " + field);
        }
        try {
            new FontRuntimeSettings(lerpMode, awtCharSize, charSize, spaceWidth, characterSpacing,
                    false, new String[0], FontCharacterRuleSet.empty());
            return true;
        } catch (IllegalArgumentException rejected) {
            return false;
        }
    }
}
