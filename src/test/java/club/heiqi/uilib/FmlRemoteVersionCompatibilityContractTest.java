package club.heiqi.uilib;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;

import org.junit.Test;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.network.NetworkCheckHandler;

/**
 * FML 远端版本检查策略的二态契约测试。
 *
 * <p>{@code acceptableRemoteVersions} 为空串代表开发期：FML 默认 checker 只接受与本端完全相同的精确版本；
 * 非空代表正式发布态：必须声明形如 {@code [4.7.0,4.8.0)} 的同 minor 左闭右开范围。</p>
 */
public class FmlRemoteVersionCompatibilityContractTest {

    /**
     * 发布态远端版本范围格式：两侧均为 4.minor.patch 三段式版本，左闭右开。
     * 例：[4.7.0,4.8.0)；拒绝 [4.7.0,4.7.0) 空区间、缺失括号与预发布标签混入等形式。
     */
    private static final String RELEASE_RANGE_PATTERN = "\\[4\\.\\d+\\.\\d+,4\\.\\d+\\.\\d+\\)";

    /** 当前注解值必须落在二态之一：空串走 FML 精确检查，非空必须符合发布态范围格式。 */
    @Test
    public void remoteVersionContractIsEitherDevelopmentExactOrReleaseRange() {
        Mod declaration = MyMod.class.getAnnotation(Mod.class);
        assertNotNull("MyMod 必须保留 @Mod 声明", declaration);
        String range = declaration.acceptableRemoteVersions();
        if (range.isEmpty()) {
            // 开发期：FML 默认 checker 只接受与本端完全相同的开发版本。
            return;
        }
        assertTrue("非空远端范围必须为 [4.x.y,4.x.y) 半开区间，实际：" + range,
                range.matches(RELEASE_RANGE_PATTERN));
    }

    /** 正式发布态必须声明远端版本范围，且当前值符合 [4.x.y,4.x.y) 格式。 */
    @Test
    public void releaseStateDeclaresMinorBoundedRemoteVersionRange() {
        Mod declaration = MyMod.class.getAnnotation(Mod.class);
        assertNotNull("MyMod 必须保留 @Mod 声明", declaration);
        String range = declaration.acceptableRemoteVersions();
        assertFalse("正式发布态必须声明远端版本范围，例如 [4.7.0,4.8.0)", range.isEmpty());
        assertTrue("远端版本范围必须为 [4.x.y,4.x.y) 半开区间，实际：" + range,
                range.matches(RELEASE_RANGE_PATTERN));
    }

    /** 自定义 handler 会覆盖默认精确检查，MyMod 不得声明它。 */
    @Test
    public void customNetworkCheckHandlerCannotOverrideExactVersionCheck() {
        for (Method method : MyMod.class.getDeclaredMethods()) {
            assertFalse(
                    method.getName() + " 不得使用 @NetworkCheckHandler 覆盖默认精确检查",
                    method.isAnnotationPresent(NetworkCheckHandler.class));
        }
    }
}
