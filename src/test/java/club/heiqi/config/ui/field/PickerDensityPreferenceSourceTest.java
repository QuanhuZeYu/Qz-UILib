package club.heiqi.config.ui.field;

import org.junit.After;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.search.PickerDensityPreference;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 密度偏好源接入点（通用装配层接线缝）生命周期守卫。
 *
 * <p>三条：未接线 = {@code null}（面板按 {@code AUTO}，与接线前逐值一致）；安装后
 * {@link PickerDensityPreferenceSource#installed()} 返回同一实例且后装者生效；
 * {@code release()} 撤线、{@code install(null)} 被拒绝（防止把「忘记接线」写成「显式撤线」）。</p>
 */
public class PickerDensityPreferenceSourceTest {

    @After
    public void releaseSource() {
        PickerDensityPreferenceSource.release();
    }

    @Test
    public void uninstalledSourceIsNullSoPanelsKeepAuto() {
        assertFalse("测试起始状态必须未接线", PickerDensityPreferenceSource.isInstalled());
        assertNull("未接线 = null（面板按 AUTO 处理）", PickerDensityPreferenceSource.installed());
    }

    @Test
    public void installExposesSameSignalAndLaterInstallWins() {
        Signal<PickerDensityPreference> first = Signal.create(PickerDensityPreference.COMPACT);
        PickerDensityPreferenceSource.install(first);
        assertTrue(PickerDensityPreferenceSource.isInstalled());
        assertSame("接线只持引用，不复制信号", first, PickerDensityPreferenceSource.installed());

        Signal<PickerDensityPreference> second = Signal.create(PickerDensityPreference.ROOMY);
        PickerDensityPreferenceSource.install(second);
        assertSame("后装者生效（装配期换线语义）", second, PickerDensityPreferenceSource.installed());
    }

    @Test
    public void releaseUnwiresAndNullInstallIsRejected() {
        PickerDensityPreferenceSource.install(Signal.create(PickerDensityPreference.STANDARD));
        PickerDensityPreferenceSource.release();
        assertFalse(PickerDensityPreferenceSource.isInstalled());
        assertNull(PickerDensityPreferenceSource.installed());
        try {
            PickerDensityPreferenceSource.install(null);
            fail("install(null) 必须被拒绝（撤线请走 release()）");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }
}
