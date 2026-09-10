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

    private static void assertIllegalArgument(Runnable action) {
        try {
            action.run();
            Assert.fail("应抛 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // 预期
        }
    }
}
