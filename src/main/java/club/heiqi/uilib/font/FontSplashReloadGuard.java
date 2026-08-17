package club.heiqi.uilib.font;

import java.lang.reflect.Field;

/**
 * Splash 阶段字体重载执行保护。
 */
public final class FontSplashReloadGuard {

    private static final String SPLASH_PROGRESS_CLASS = "cpw.mods.fml.client.SplashProgress";
    private static Boolean splashClassAvailable;

    private FontSplashReloadGuard() {}

    /**
     * 判断当前是否应延后字体运行时 reconcile。
     *
     * <p>历史方法名保留兼容；返回 true 只阻止当前 render tick 执行完整 reload，不再丢弃已经发布的
     * desired signal。Splash 结束后的安全 render tick 会继续收敛。</p>
     *
     * @return 是否延后当前 reconcile
     */
    public static boolean shouldSkipResourceReload() {
        return shouldDeferFontReload();
    }

    /**
     * 判断 SplashProgress 是否仍拥有不安全的资源加载阶段。
     *
     * @return 是否应保留 signal 并延后完整字体 reload
     */
    public static boolean shouldDeferFontReload() {
        if (!isSplashProgressAvailable()) {
            return false;
        }
        try {
            Class<?> splashProgressClass = loadSplashProgressClass();
            Thread splashThread = readThreadField(splashProgressClass, "thread");
            if (splashThread == null || !splashThread.isAlive()) {
                return false;
            }
            Boolean done = readBooleanField(splashProgressClass, "done");
            return done == null || !done.booleanValue();
        } catch (ReflectiveOperationException exception) {
            splashClassAvailable = Boolean.FALSE;
            return false;
        } catch (RuntimeException exception) {
            splashClassAvailable = Boolean.FALSE;
            return false;
        } catch (LinkageError error) {
            splashClassAvailable = Boolean.FALSE;
            return false;
        }
    }

    private static boolean isSplashProgressAvailable() {
        if (splashClassAvailable != null) {
            return splashClassAvailable.booleanValue();
        }
        try {
            loadSplashProgressClass();
            splashClassAvailable = Boolean.TRUE;
        } catch (ClassNotFoundException exception) {
            splashClassAvailable = Boolean.FALSE;
        } catch (LinkageError error) {
            splashClassAvailable = Boolean.FALSE;
        }
        return splashClassAvailable.booleanValue();
    }

    private static Class<?> loadSplashProgressClass() throws ClassNotFoundException {
        return Class.forName(SPLASH_PROGRESS_CLASS, false, FontSplashReloadGuard.class.getClassLoader());
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
