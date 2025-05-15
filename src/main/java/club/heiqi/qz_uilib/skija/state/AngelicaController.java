package club.heiqi.qz_uilib.skija.state;

import java.lang.reflect.Field;

/**
 * 强制关闭GL缓存功能
 */
public class AngelicaController {
    public static Class<?> glStateManager;
    public static Field bypassCaching;

    public static void forceOffGLCache() {
        if (glStateManager == null) {
            try {
                glStateManager = Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager");
            } catch (ClassNotFoundException ignored) {

            }
        }
        if (bypassCaching == null) {
            try {
                bypassCaching = glStateManager.getField("BYPASS_CACHE");
                bypassCaching.setAccessible(true);
            } catch (NoSuchFieldException ignored) {

            }
        }
        try {
            if (bypassCaching != null && bypassCaching.getBoolean(null)) {
                bypassCaching.setBoolean(null, false);
            }
        } catch (IllegalAccessException e) {

        }
    }
}
