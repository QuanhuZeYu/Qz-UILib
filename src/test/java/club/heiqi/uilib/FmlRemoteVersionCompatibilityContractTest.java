package club.heiqi.uilib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.lang.reflect.Method;

import org.junit.Test;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.network.NetworkCheckHandler;

/** FML 开发期远端版本检查策略的契约测试。 */
public class FmlRemoteVersionCompatibilityContractTest {

    /** 空声明使 FML 默认 checker 只接受与本端完全相同的开发版本。 */
    @Test
    public void annotationUsesExactRemoteVersionDuringDevelopment() {
        Mod declaration = MyMod.class.getAnnotation(Mod.class);
        assertNotNull("MyMod 必须保留 @Mod 声明", declaration);
        assertEquals("开发期必须省略远端范围并使用 FML 精确版本检查", "", declaration.acceptableRemoteVersions());
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
