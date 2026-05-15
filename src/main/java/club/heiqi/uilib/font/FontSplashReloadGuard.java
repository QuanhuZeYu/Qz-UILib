package club.heiqi.uilib.font;

import java.lang.reflect.Field;

/**
 * Splash 阶段字体重载保护。
 */
public final class FontSplashReloadGuard {

    private static final String SPLASH_PROGRESS_CLASS = "cpw.mods.fml.client.SplashProgress";
    private static Boolean splashClassAvailable;

    private FontSplashReloadGuard() {}

    /**
     * 判断当前是否应跳过资源包触发的字体运行时重载。
     *
     * <p>SplashProgress 绘制期间资源重载容易反复摧毁字符页和后台字形任务；这里仅跳过原版资源包重载入口，
     * 不影响配置变更等显式字体重载。</p>
     *
     * @return 是否跳过资源包重载
     */
    public static boolean shouldSkipResourceReload() {
        if (!isSplashProgressAvailable()) {
            return false;
        }
        try {
            Class<?> splashProgressClass = Class.forName(SPLASH_PROGRESS_CLASS);
            Thread splashThread = readThreadField(splashProgressClass, "thread");
            if (splashThread == null || !splashThread.isAlive()) {
                return false;
            }
            Boolean done = readBooleanField(splashProgressClass, "done");
            return done == null || !done.booleanValue();
        } catch (ReflectiveOperationException exception) {
            return false;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean isSplashProgressAvailable() {
        if (splashClassAvailable != null) {
            return splashClassAvailable.booleanValue();
        }
        try {
            Class.forName(SPLASH_PROGRESS_CLASS);
            splashClassAvailable = Boolean.TRUE;
        } catch (ClassNotFoundException exception) {
            splashClassAvailable = Boolean.FALSE;
        }
        return splashClassAvailable.booleanValue();
    }

    private static Thread readThreadField(Class<?> ownerClass, String fieldName) throws ReflectiveOperationException {
        Field field = ownerClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        Object value = field.get(null);
        return value instanceof Thread ? (Thread) value : null;
    }

    private static Boolean readBooleanField(Class<?> ownerClass, String fieldName) throws ReflectiveOperationException {
        Field field = ownerClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        Object value = field.get(null);
        return value instanceof Boolean ? (Boolean) value : null;
    }
}
