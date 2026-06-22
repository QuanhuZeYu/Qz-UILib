package club.heiqi.uilib.ui.host;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.ui.style.props.UiCursor;

/**
 * 基于 LWJGL3ify SDL 系统光标的 UI 宿主实现。
 */
public class SystemUiCursorHost implements UiCursorHost {

    private static final SystemUiCursorHost INSTANCE =
            new SystemUiCursorHost(createDefaultBackend());

    private final NativeCursorBackend backend;
    private ResolvedCursorKind appliedCursor = ResolvedCursorKind.DEFAULT;
    private boolean runtimeCursorSynchronized;
    private boolean runtimeCursorDisabled;

    public SystemUiCursorHost(NativeCursorBackend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    public static SystemUiCursorHost getInstance() {
        return INSTANCE;
    }

    static NativeCursorBackend createDefaultBackend() {
        return new SdlNativeCursorBackend();
    }

    @Override
    public synchronized void applyCursor(UiCursor cursor) {
        ResolvedCursorKind resolvedCursor = resolveRequestedCursor(cursor);
        if (runtimeCursorDisabled || !isRuntimeAvailable(resolvedCursor)) {
            appliedCursor = resolvedCursor;
            runtimeCursorSynchronized = false;
            return;
        }
        if (runtimeCursorSynchronized && appliedCursor == resolvedCursor) {
            return;
        }
        applyResolvedCursor(resolvedCursor);
    }

    /**
     * 强制下发当前 UI 期望光标，修复宿主原生状态与本类 {@code appliedCursor} 缓存漂移后的同值短路。
     *
     * @param cursor 当前 UI 期望的光标样式；为空时按默认光标处理
     */
    @Override
    public synchronized void forceApplyCursor(UiCursor cursor) {
        ResolvedCursorKind resolvedCursor = resolveRequestedCursor(cursor);
        if (runtimeCursorDisabled || !isRuntimeAvailable(resolvedCursor)) {
            appliedCursor = resolvedCursor;
            runtimeCursorSynchronized = false;
            return;
        }
        applyResolvedCursor(resolvedCursor);
    }

    private boolean isRuntimeAvailable(ResolvedCursorKind resolvedCursor) {
        try {
            return backend.isRuntimeAvailable();
        } catch (RuntimeException exception) {
            disableRuntimeCursor(resolvedCursor, exception);
            return false;
        } catch (LinkageError error) {
            disableRuntimeCursor(resolvedCursor, error);
            return false;
        }
    }

    private void applyResolvedCursor(ResolvedCursorKind resolvedCursor) {
        appliedCursor = resolvedCursor;
        if (resolvedCursor == ResolvedCursorKind.HIDDEN) {
            try {
                backend.hideCursor();
                runtimeCursorSynchronized = true;
            } catch (IllegalStateException exception) {
                MyMod.LOG.debug("UILib 系统光标隐藏失败，本次操作跳过。", exception);
                runtimeCursorSynchronized = false;
            }
            return;
        }
        try {
            backend.showCursor();
            backend.applySystemCursor(resolvedCursor);
            runtimeCursorSynchronized = true;
        } catch (IllegalStateException exception) {
            MyMod.LOG.debug("UILib 系统光标应用失败，本次操作跳过：cursor={}", resolvedCursor, exception);
            runtimeCursorSynchronized = false;
        }
    }

    private void disableRuntimeCursor(ResolvedCursorKind resolvedCursor, Throwable cause) {
        appliedCursor = resolvedCursor;
        runtimeCursorSynchronized = false;
        runtimeCursorDisabled = true;
        MyMod.LOG.warn("UILib 系统光标宿主调用失败，已在本次运行中降级为 no-op。", cause);
    }

    public static ResolvedCursorKind resolveRequestedCursor(UiCursor cursor) {
        UiCursor resolvedCursor = cursor == null ? UiCursor.DEFAULT : cursor;
        switch (resolvedCursor) {
            case POINTER:
                return ResolvedCursorKind.POINTER;
            case TEXT:
                return ResolvedCursorKind.TEXT;
            case MOVE:
            case GRAB:
            case GRABBING:
                return ResolvedCursorKind.MOVE;
            case NOT_ALLOWED:
                return ResolvedCursorKind.NOT_ALLOWED;
            case WAIT:
                return ResolvedCursorKind.WAIT;
            case CROSSHAIR:
                return ResolvedCursorKind.CROSSHAIR;
            case NONE:
                return ResolvedCursorKind.HIDDEN;
            case EW_RESIZE:
                return ResolvedCursorKind.EW_RESIZE;
            case NS_RESIZE:
                return ResolvedCursorKind.NS_RESIZE;
            case HELP:
            case DEFAULT:
            default:
                return ResolvedCursorKind.DEFAULT;
        }
    }

    /**
     * 解析后的系统光标种类。
     */
    public enum ResolvedCursorKind {
        DEFAULT,
        POINTER,
        TEXT,
        MOVE,
        NOT_ALLOWED,
        WAIT,
        CROSSHAIR,
        EW_RESIZE,
        NS_RESIZE,
        HIDDEN
    }

    /**
     * 系统光标原生后端抽象。
     */
    public interface NativeCursorBackend {

        boolean isRuntimeAvailable();

        void showCursor();

        void hideCursor();

        void applyDefaultCursor();

        void applySystemCursor(ResolvedCursorKind cursorKind);
    }

    private static final class SdlNativeCursorBackend implements NativeCursorBackend {

        private static final AtomicBoolean DISPLAY_PROBE_FAILURE_LOGGED = new AtomicBoolean(false);

        private final SdlReflectionBridge reflectionBridge = SdlReflectionBridge.getInstance();
        private final Map<ResolvedCursorKind, Long> cursorHandles =
                new EnumMap<ResolvedCursorKind, Long>(ResolvedCursorKind.class);

        @Override
        public boolean isRuntimeAvailable() {
            if (!reflectionBridge.isAvailable()) {
                return false;
            }
            try {
                return reflectionBridge.isDisplayCreated();
            } catch (LinkageError error) {
                logDisplayProbeFailureOnce(error);
                return false;
            } catch (RuntimeException exception) {
                logDisplayProbeFailureOnce(exception);
                return false;
            }
        }

        private static void logDisplayProbeFailureOnce(Throwable cause) {
            if (DISPLAY_PROBE_FAILURE_LOGGED.compareAndSet(false, true)) {
                MyMod.LOG.debug("UILib 系统光标宿主探测 Display 状态失败，已降级为不应用系统光标。", cause);
            }
        }

        @Override
        public void showCursor() {
            reflectionBridge.runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    if (!reflectionBridge.showCursor()) {
                        throw new IllegalStateException("Failed to show SDL system cursor");
                    }
                }
            });
        }

        @Override
        public void hideCursor() {
            reflectionBridge.runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    if (!reflectionBridge.hideCursor()) {
                        throw new IllegalStateException("Failed to hide SDL system cursor");
                    }
                }
            });
        }

        @Override
        public void applyDefaultCursor() {
            applySystemCursor(ResolvedCursorKind.DEFAULT);
        }

        @Override
        public void applySystemCursor(ResolvedCursorKind cursorKind) {
            applySystemCursor(cursorKind, true);
        }

        private void applySystemCursor(ResolvedCursorKind cursorKind, boolean allowDefaultFallback) {
            reflectionBridge.runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    applySystemCursorOnMainThread(cursorKind, allowDefaultFallback);
                }
            });
        }

        private void applySystemCursorOnMainThread(ResolvedCursorKind cursorKind, boolean allowDefaultFallback) {
            long cursorHandle = getOrCreateCursorHandleOnMainThread(cursorKind);
            if (cursorHandle == 0L) {
                if (allowDefaultFallback && cursorKind != ResolvedCursorKind.DEFAULT) {
                    applySystemCursorOnMainThread(ResolvedCursorKind.DEFAULT, false);
                    return;
                }
                throw new IllegalStateException("Failed to create SDL system cursor: " + cursorKind);
            }
            if (!reflectionBridge.setCursor(cursorHandle)) {
                if (allowDefaultFallback && cursorKind != ResolvedCursorKind.DEFAULT) {
                    applySystemCursorOnMainThread(ResolvedCursorKind.DEFAULT, false);
                    return;
                }
                throw new IllegalStateException("Failed to set SDL system cursor: " + cursorKind);
            }
        }

        private synchronized long getOrCreateCursorHandleOnMainThread(ResolvedCursorKind cursorKind) {
            Long existingHandle = cursorHandles.get(cursorKind);
            if (existingHandle != null && existingHandle.longValue() != 0L) {
                return existingHandle.longValue();
            }
            long cursorHandle = reflectionBridge.createSystemCursor(toSdlSystemCursor(cursorKind));
            cursorHandles.put(cursorKind, Long.valueOf(cursorHandle));
            return cursorHandle;
        }

        private int toSdlSystemCursor(ResolvedCursorKind cursorKind) {
            return reflectionBridge.getSystemCursorConstant(cursorKind);
        }
    }

    private static final class SdlReflectionBridge {

        private static final String SDL_MOUSE_CLASS_NAME = "org.lwjgl.sdl.SDLMouse";
        private static final String MAIN_THREAD_EXEC_CLASS_NAME = "me.eigenraven.lwjgl3ify.client.MainThreadExec";
        private static final String LWJGLX_DISPLAY_CLASS_NAME = "org.lwjglx.opengl.Display";
        private static final String LWJGL2_DISPLAY_CLASS_NAME = "org.lwjgl.opengl.Display";
        private static final AtomicBoolean BRIDGE_RESOLUTION_FAILURE_LOGGED = new AtomicBoolean(false);
        private static final SdlReflectionBridge INSTANCE = new SdlReflectionBridge();

        private final Method displayIsCreatedMethod;
        private final Method showCursorMethod;
        private final Method hideCursorMethod;
        private final Method setCursorMethod;
        private final Method createSystemCursorMethod;
        private final Method runOnMainThreadMethod;
        private final Map<ResolvedCursorKind, Integer> systemCursorConstants;
        private final boolean available;

        private SdlReflectionBridge() {
            Method resolvedShowCursorMethod = null;
            Method resolvedHideCursorMethod = null;
            Method resolvedSetCursorMethod = null;
            Method resolvedCreateSystemCursorMethod = null;
            Method resolvedRunOnMainThreadMethod = null;
            Method resolvedDisplayIsCreatedMethod = null;
            Map<ResolvedCursorKind, Integer> resolvedSystemCursorConstants =
                    new EnumMap<ResolvedCursorKind, Integer>(ResolvedCursorKind.class);
            boolean resolvedAvailable = false;
            try {
                Class<?> sdlMouseClass = Class.forName(SDL_MOUSE_CLASS_NAME);
                Class<?> mainThreadExecClass = Class.forName(MAIN_THREAD_EXEC_CLASS_NAME);
                Class<?> displayClass = resolveFirstClass(LWJGLX_DISPLAY_CLASS_NAME, LWJGL2_DISPLAY_CLASS_NAME);
                resolvedDisplayIsCreatedMethod = displayClass.getMethod("isCreated");
                resolvedShowCursorMethod = sdlMouseClass.getMethod("SDL_ShowCursor");
                resolvedHideCursorMethod = sdlMouseClass.getMethod("SDL_HideCursor");
                resolvedSetCursorMethod = sdlMouseClass.getMethod("SDL_SetCursor", Long.TYPE);
                resolvedCreateSystemCursorMethod = sdlMouseClass.getMethod("SDL_CreateSystemCursor", Integer.TYPE);
                resolvedRunOnMainThreadMethod = mainThreadExecClass.getMethod("runOnMainThread", Runnable.class);
                resolvedSystemCursorConstants.put(ResolvedCursorKind.DEFAULT,
                        Integer.valueOf(readStaticInt(sdlMouseClass, "SDL_SYSTEM_CURSOR_DEFAULT")));
                resolvedSystemCursorConstants.put(ResolvedCursorKind.POINTER,
                        Integer.valueOf(readStaticInt(sdlMouseClass, "SDL_SYSTEM_CURSOR_POINTER")));
                resolvedSystemCursorConstants.put(ResolvedCursorKind.TEXT,
                        Integer.valueOf(readStaticInt(sdlMouseClass, "SDL_SYSTEM_CURSOR_TEXT")));
                resolvedSystemCursorConstants.put(ResolvedCursorKind.MOVE,
                        Integer.valueOf(readStaticInt(sdlMouseClass, "SDL_SYSTEM_CURSOR_MOVE")));
                resolvedSystemCursorConstants.put(ResolvedCursorKind.NOT_ALLOWED,
                        Integer.valueOf(readStaticInt(sdlMouseClass, "SDL_SYSTEM_CURSOR_NOT_ALLOWED")));
                resolvedSystemCursorConstants.put(ResolvedCursorKind.WAIT,
                        Integer.valueOf(readStaticInt(sdlMouseClass, "SDL_SYSTEM_CURSOR_WAIT")));
                resolvedSystemCursorConstants.put(ResolvedCursorKind.CROSSHAIR,
                        Integer.valueOf(readStaticInt(sdlMouseClass, "SDL_SYSTEM_CURSOR_CROSSHAIR")));
                resolvedSystemCursorConstants.put(ResolvedCursorKind.EW_RESIZE,
                        Integer.valueOf(readStaticInt(sdlMouseClass, "SDL_SYSTEM_CURSOR_EW_RESIZE")));
                resolvedSystemCursorConstants.put(ResolvedCursorKind.NS_RESIZE,
                        Integer.valueOf(readStaticInt(sdlMouseClass, "SDL_SYSTEM_CURSOR_NS_RESIZE")));
                resolvedAvailable = true;
            } catch (ReflectiveOperationException exception) {
                resolvedSystemCursorConstants.clear();
                logBridgeResolutionFailureOnce(exception);
            } catch (SecurityException exception) {
                resolvedSystemCursorConstants.clear();
                logBridgeResolutionFailureOnce(exception);
            } catch (LinkageError error) {
                resolvedSystemCursorConstants.clear();
                logBridgeResolutionFailureOnce(error);
            }
            this.displayIsCreatedMethod = resolvedDisplayIsCreatedMethod;
            this.showCursorMethod = resolvedShowCursorMethod;
            this.hideCursorMethod = resolvedHideCursorMethod;
            this.setCursorMethod = resolvedSetCursorMethod;
            this.createSystemCursorMethod = resolvedCreateSystemCursorMethod;
            this.runOnMainThreadMethod = resolvedRunOnMainThreadMethod;
            this.systemCursorConstants = resolvedSystemCursorConstants;
            this.available = resolvedAvailable;
        }

        static SdlReflectionBridge getInstance() {
            return INSTANCE;
        }

        boolean isAvailable() {
            return available;
        }

        boolean isDisplayCreated() {
            return ((Boolean) invoke(displayIsCreatedMethod, null)).booleanValue();
        }

        void runOnMainThread(Runnable runnable) {
            Objects.requireNonNull(runnable, "runnable");
            if (!available || runOnMainThreadMethod == null) {
                runnable.run();
                return;
            }
            invoke(runOnMainThreadMethod, null, runnable);
        }

        boolean showCursor() {
            return ((Boolean) invoke(showCursorMethod, null)).booleanValue();
        }

        boolean hideCursor() {
            return ((Boolean) invoke(hideCursorMethod, null)).booleanValue();
        }

        boolean setCursor(long cursorHandle) {
            return ((Boolean) invoke(setCursorMethod, null, Long.valueOf(cursorHandle))).booleanValue();
        }

        long createSystemCursor(int systemCursor) {
            return ((Long) invoke(createSystemCursorMethod, null, Integer.valueOf(systemCursor))).longValue();
        }

        int getSystemCursorConstant(ResolvedCursorKind cursorKind) {
            Integer resolvedConstant = systemCursorConstants.get(cursorKind);
            if (resolvedConstant != null) {
                return resolvedConstant.intValue();
            }
            Integer defaultConstant = systemCursorConstants.get(ResolvedCursorKind.DEFAULT);
            return defaultConstant == null ? 0 : defaultConstant.intValue();
        }

        private static int readStaticInt(Class<?> ownerClass, String fieldName) throws ReflectiveOperationException {
            Field field = ownerClass.getField(fieldName);
            return field.getInt(null);
        }

        private static Class<?> resolveFirstClass(String firstClassName, String secondClassName)
                throws ClassNotFoundException {
            try {
                return Class.forName(firstClassName);
            } catch (ClassNotFoundException exception) {
                return Class.forName(secondClassName);
            }
        }

        private static void logBridgeResolutionFailureOnce(Throwable throwable) {
            if (BRIDGE_RESOLUTION_FAILURE_LOGGED.compareAndSet(false, true)) {
                MyMod.LOG.debug("UILib 系统光标桥接反射解析失败，已在本次运行中降级为 no-op。", throwable);
            }
        }

        private static Object invoke(Method method, Object target, Object... args) {
            if (method == null) {
                throw new IllegalStateException("SDL system cursor bridge is unavailable");
            }
            try {
                return method.invoke(target, args);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Failed to access reflected SDL cursor method", exception);
            } catch (InvocationTargetException exception) {
                throw new IllegalStateException("Reflected SDL cursor method failed", exception.getCause());
            }
        }
    }
}
