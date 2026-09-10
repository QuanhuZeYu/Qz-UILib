package club.heiqi.uilib.ui.scene.theme;

import org.junit.Assert;
import org.junit.Test;

/**
 * {@link SceneTheme} / {@link SceneSurfaceStyle} 值对象契约测试。
 *
 * <p>覆盖：角色齐全、值相等语义、无滤镜替代档的「关闭滤镜 + 不透明底」不变式、
 * 实色对照档、builder 单角色覆盖。</p>
 */
public class SceneThemeTest {

    @Test
    public void everyFactoryProvidesAllRoles() {
        for (SceneTheme theme : new SceneTheme[] {
                SceneTheme.liquidGlassDark(), SceneTheme.liquidGlassLight(), SceneTheme.solidDark() }) {
            for (SceneTheme.Role role : SceneTheme.Role.values()) {
                Assert.assertNotNull("主题缺少角色 " + role, theme.surface(role));
            }
        }
    }

    @Test
    public void themesWithSameValuesAreEqual() {
        Assert.assertEquals(SceneTheme.liquidGlassDark(), SceneTheme.liquidGlassDark());
        Assert.assertEquals(SceneTheme.liquidGlassDark().hashCode(), SceneTheme.liquidGlassDark().hashCode());
        Assert.assertNotEquals("深色与浅色档必须可区分",
                SceneTheme.liquidGlassDark(), SceneTheme.liquidGlassLight());
    }

    @Test
    public void builderOverrideOnlyChangesTargetRole() {
        SceneTheme base = SceneTheme.liquidGlassDark();
        SceneSurfaceStyle custom = SceneSurfaceStyle.builder().cornerRadius(24).build();
        SceneTheme changed = SceneTheme.builder().surface(SceneTheme.Role.PANEL, custom).build();

        Assert.assertEquals(Integer.valueOf(24), Integer.valueOf(changed.surface(SceneTheme.Role.PANEL).getCornerRadius()));
        Assert.assertEquals("未覆盖的角色保持默认",
                base.surface(SceneTheme.Role.OVERLAY), changed.surface(SceneTheme.Role.OVERLAY));
        Assert.assertNotEquals(base, changed);
    }

    @Test
    public void withoutBackdropDisablesFilterAndKeepsOpaqueFallback() {
        SceneTheme opaque = SceneTheme.liquidGlassDark().withoutBackdrop();

        for (SceneTheme.Role role : SceneTheme.Role.values()) {
            SceneSurfaceStyle style = opaque.surface(role);
            Assert.assertNull("无滤镜替代档必须关闭 backdrop：" + role, style.getBackdrop());
            for (SceneSurfaceStyle.StateStyle state : new SceneSurfaceStyle.StateStyle[] {
                    style.getIdle(), style.getHovered(), style.getPressed(), style.getDisabled() }) {
                int alpha = (state.getTint() >>> 24) & 0xFF;
                Assert.assertEquals("无滤镜替代底色必须不透明：" + role, 0xFF, alpha);
            }
        }
        Assert.assertEquals("语义色在替代档中保持不变",
                Integer.valueOf(SceneTheme.liquidGlassDark().foreground()),
                Integer.valueOf(opaque.foreground()));
    }

    @Test
    public void solidDarkClosesFilterForEveryRole() {
        SceneTheme solid = SceneTheme.solidDark();
        for (SceneTheme.Role role : SceneTheme.Role.values()) {
            Assert.assertNull("实色档必须关闭 backdrop：" + role, solid.surface(role).getBackdrop());
        }
    }

    @Test
    public void surfaceStyleRejectsInvalidValues() {
        assertIllegalArgument(() -> SceneSurfaceStyle.builder().cornerRadius(-1).build());
        assertIllegalArgument(() -> SceneSurfaceStyle.builder().disabledOpacity(1.5F).build());
        assertIllegalArgument(() -> SceneSurfaceStyle.builder().contentLift(Float.NaN).build());
    }

    @Test
    public void surfaceStyleValueEquality() {
        SceneSurfaceStyle left = SceneSurfaceStyle.builder().cornerRadius(12).foreground(0xFFABCDEF).build();
        SceneSurfaceStyle right = SceneSurfaceStyle.builder().cornerRadius(12).foreground(0xFFABCDEF).build();
        SceneSurfaceStyle different = SceneSurfaceStyle.builder().cornerRadius(13).foreground(0xFFABCDEF).build();

        Assert.assertEquals(left, right);
        Assert.assertEquals(left.hashCode(), right.hashCode());
        Assert.assertNotEquals(left, different);
    }

    /**
     * {@link SceneTheme#toBuilder()}：以本实例现值为起点派生，可从任一内置档（含非默认档与
     * {@code withoutBackdrop} 派生档）出发只改目标字段，不必逐项抄写全部语义色与角色配方。
     */
    @Test
    public void toBuilderDerivesFromAnyExistingTier() {
        for (SceneTheme base : new SceneTheme[] {
                SceneTheme.liquidGlassDark(), SceneTheme.liquidGlassLight(), SceneTheme.solidDark(),
                SceneTheme.liquidGlassDark().withoutBackdrop() }) {
            Assert.assertEquals("toBuilder 空派生必须值相等：" + base, base, base.toBuilder().build());
            Assert.assertEquals("toBuilder 空派生 hashCode 相等：" + base,
                    base.hashCode(), base.toBuilder().build().hashCode());
        }

        SceneTheme light = SceneTheme.liquidGlassLight();
        SceneTheme derived = light.toBuilder().accent(0xFF00BFA5).build();
        Assert.assertEquals("只改目标字段", 0xFF00BFA5, derived.accent());
        Assert.assertEquals("未改语义色保持浅色档值", light.foreground(), derived.foreground());
        Assert.assertEquals("未改边框色保持浅色档值", light.borderDefault(), derived.borderDefault());
        Assert.assertEquals("未改选区色保持浅色档值",
                light.selectionBackground(), derived.selectionBackground());
        for (SceneTheme.Role role : SceneTheme.Role.values()) {
            Assert.assertEquals("未改角色配方保持浅色档值：" + role,
                    light.surface(role), derived.surface(role));
        }
        Assert.assertNotEquals("派生结果与基准可区分", light, derived);
        Assert.assertEquals("基准实例不被派生改动影响（值对象不可变）",
                SceneTheme.liquidGlassLight().accent(), light.accent());
    }

    /**
     * 字体槽位分量：三个内置档都必须给全七档，且每档默认值<b>逐值等于既有静态字号 token</b>
     * （{@code ConfigTheme} 九档字号去重后的 6 个数值 + {@code SceneNode} 默认 16）。
     *
     * <p>这是「默认外观零变化」的守卫：任一行被改动都会让本断言红。默认档还必须<b>不接管行距</b>
     * （{@code 0}），否则既有控件会在无人调用行距 API 的情况下改变行高。</p>
     */
    @Test
    public void everyFactoryProvidesAllFontSlotsWithLegacyDefaultValues() {
        for (SceneTheme theme : new SceneTheme[] {
                SceneTheme.liquidGlassDark(), SceneTheme.liquidGlassLight(), SceneTheme.solidDark() }) {
            String label = "默认档字号：" + theme + " ";
            Assert.assertEquals(label + "TITLE", 24, theme.fontSize(SceneTheme.FontSlot.TITLE));
            Assert.assertEquals(label + "SECTION", 18, theme.fontSize(SceneTheme.FontSlot.SECTION));
            Assert.assertEquals(label + "BASE", 16, theme.fontSize(SceneTheme.FontSlot.BASE));
            Assert.assertEquals(label + "LABEL", 16, theme.fontSize(SceneTheme.FontSlot.LABEL));
            Assert.assertEquals(label + "READOUT", 14, theme.fontSize(SceneTheme.FontSlot.READOUT));
            Assert.assertEquals(label + "HELPER", 13, theme.fontSize(SceneTheme.FontSlot.HELPER));
            Assert.assertEquals(label + "CAPTION", 12, theme.fontSize(SceneTheme.FontSlot.CAPTION));
            Assert.assertEquals("默认档不接管行距：" + theme, 0.0D, theme.lineHeightMultiplier(), 0.0D);
        }
    }

    /**
     * 字号分量参与值相等并被 {@link SceneTheme#toBuilder()} 携带：单槽覆盖只影响该槽，
     * 未覆盖槽保持基准值，派生结果与基准可区分。
     */
    @Test
    public void fontSlotOverrideIsCarriedByToBuilderAndPartOfValueEquality() {
        SceneTheme base = SceneTheme.liquidGlassDark();
        SceneTheme changed = base.toBuilder().fontSize(SceneTheme.FontSlot.TITLE, 30).build();

        Assert.assertEquals("覆盖槽生效", 30, changed.fontSize(SceneTheme.FontSlot.TITLE));
        Assert.assertEquals("未覆盖槽保持基准值", base.fontSize(SceneTheme.FontSlot.SECTION),
                changed.fontSize(SceneTheme.FontSlot.SECTION));
        Assert.assertNotEquals("字号差异必须可区分", base, changed);
        Assert.assertNotEquals("字号差异必须体现在 hashCode", base.hashCode(), changed.hashCode());
        Assert.assertEquals("同名同值重建必须值相等",
                changed, base.toBuilder().fontSize(SceneTheme.FontSlot.TITLE, 30).build());
    }

    /** 行距倍数参与值相等与 toBuilder 携带；{@code -0.0} 归一为 {@code 0.0}（「不接管」只有一个表示）。 */
    @Test
    public void lineHeightMultiplierIsCarriedAndNormalized() {
        SceneTheme base = SceneTheme.liquidGlassDark();
        SceneTheme spaced = base.toBuilder().lineHeightMultiplier(1.4D).build();

        Assert.assertEquals(1.4D, spaced.lineHeightMultiplier(), 0.0D);
        Assert.assertNotEquals("行距差异必须可区分", base, spaced);
        Assert.assertEquals("toBuilder 空派生携带行距", spaced, spaced.toBuilder().build());
        Assert.assertEquals("负零归一为 0", base, base.toBuilder().lineHeightMultiplier(-0.0D).build());
    }

    /** 非法字号/行距必须在设置或构建时就地拒绝，而不是留到度量期才炸。 */
    @Test
    public void fontAndLineHeightRejectInvalidValues() {
        assertIllegalArgument(() -> SceneTheme.builder().fontSize(SceneTheme.FontSlot.BASE, 0));
        assertIllegalArgument(() -> SceneTheme.builder().fontSize(SceneTheme.FontSlot.BASE, -1));
        assertIllegalArgument(() -> SceneTheme.builder().lineHeightMultiplier(-0.5D));
        assertIllegalArgument(() -> SceneTheme.builder().lineHeightMultiplier(Double.NaN));
        assertIllegalArgument(() -> SceneTheme.builder().lineHeightMultiplier(Double.POSITIVE_INFINITY));
        assertNullPointer(() -> SceneTheme.builder().fontSize(null, 12));
        assertNullPointer(() -> SceneTheme.liquidGlassDark().fontSize(null));
    }

    private static void assertNullPointer(Runnable action) {
        try {
            action.run();
            Assert.fail("应抛 NullPointerException");
        } catch (NullPointerException expected) {
            // 预期
        }
    }

    private static void assertIllegalArgument(Runnable action) {
        try {
            action.run();
            Assert.fail("应抛 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // 预期
        }
    }
}
