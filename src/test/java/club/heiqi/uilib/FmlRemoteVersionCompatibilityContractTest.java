package club.heiqi.uilib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;

import org.junit.Test;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.network.NetworkCheckHandler;
import cpw.mods.fml.common.versioning.DefaultArtifactVersion;
import cpw.mods.fml.common.versioning.InvalidVersionSpecificationException;
import cpw.mods.fml.common.versioning.VersionRange;

/** FML 远端版本声明与 Maven 版本排序语义的契约测试。 */
public class FmlRemoteVersionCompatibilityContractTest {

    /** 静态范围必须来自生产 annotation，并覆盖同 minor 与约定的预发布边界。 */
    @Test
    public void annotationDeclaresBoundedMinorCompatibility()
            throws InvalidVersionSpecificationException {
        Mod declaration = MyMod.class.getAnnotation(Mod.class);
        assertNotNull("MyMod 必须保留 @Mod 声明", declaration);

        String specification = declaration.acceptableRemoteVersions();
        assertFalse("远端兼容范围不得为空", specification.isEmpty());
        assertFalse("远端兼容范围不得使用 wildcard", "*".equals(specification));
        assertEquals("远端兼容范围必须固定为当前 4.6 patch 线", "[4.6.2,4.7.0)", specification);

        VersionRange range = VersionRange.createFromVersionSpec(specification);
        assertAccepted(
                range,
                "4.6.2",
                "4.6.3",
                "4.6.999",
                "4.7.0-alpha",
                "4.7.0-beta",
                "4.7.0-rc1",
                "4.7.0-SNAPSHOT");
        assertRejected(range, "4.6.1", "4.6.2-alpha", "4.7.0", "5.0.0", "*");
    }

    /** 自定义 handler 会覆盖静态范围，MyMod 不得声明它。 */
    @Test
    public void customNetworkCheckHandlerCannotOverrideStaticRange() {
        for (Method method : MyMod.class.getDeclaredMethods()) {
            assertFalse(
                    method.getName() + " 不得使用 @NetworkCheckHandler 覆盖静态范围",
                    method.isAnnotationPresent(NetworkCheckHandler.class));
        }
    }

    /** 断言每个远端版本都落在生产声明解析出的范围内。 */
    private static void assertAccepted(VersionRange range, String... versions) {
        for (String version : versions) {
            assertTrue(
                    "应接受远端版本 " + version,
                    range.containsVersion(new DefaultArtifactVersion(version)));
        }
    }

    /** 断言每个远端版本都落在生产声明解析出的范围外。 */
    private static void assertRejected(VersionRange range, String... versions) {
        for (String version : versions) {
            assertFalse(
                    "应拒绝远端版本 " + version,
                    range.containsVersion(new DefaultArtifactVersion(version)));
        }
    }
}
