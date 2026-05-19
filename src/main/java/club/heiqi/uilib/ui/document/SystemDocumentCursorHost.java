package club.heiqi.uilib.ui.document;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.lwjglx.opengl.Display;

import club.heiqi.uilib.ui.style.UiCursor;

/**
 * 基于 LWJGL3ify SDL 系统光标的宿主实现。
 */
final class SystemDocumentCursorHost implements DocumentCursorHost {

    private static final SystemDocumentCursorHost INSTANCE =
            new SystemDocumentCursorHost(new SdlNativeCursorBackend());

    private final NativeCursorBackend backend;
    private ResolvedCursorKind appliedCursor = ResolvedCursorKind.DEFAULT;
    private boolean runtimeCursorSynchronized;

    SystemDocumentCursorHost(NativeCursorBackend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    static SystemDocumentCursorHost getInstance() {
        return INSTANCE;
    }

    @Override
    public synchronized void applyCursor(UiCursor cursor) {
        ResolvedCursorKind resolvedCursor = resolveRequestedCursor(cursor);
        if (!backend.isRuntimeAvailable()) {
            appliedCursor = resolvedCursor;
            runtimeCursorSynchronized = false;
            return;
        }
        if (runtimeCursorSynchronized && appliedCursor == resolvedCursor) {
            return;
        }
        appliedCursor = resolvedCursor;
        if (resolvedCursor == ResolvedCursorKind.HIDDEN) {
            backend.hideCursor();
            runtimeCursorSynchronized = true;
            return;
        }
        backend.showCursor();
        if (resolvedCursor == ResolvedCursorKind.DEFAULT) {
            backend.applyDefaultCursor();
            runtimeCursorSynchronized = true;
            return;
        }
        backend.applySystemCursor(resolvedCursor);
        runtimeCursorSynchronized = true;
    }

    static ResolvedCursorKind resolveRequestedCursor(UiCursor cursor) {
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

    enum ResolvedCursorKind {
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

    interface NativeCursorBackend {

        boolean isRuntimeAvailable();

        void showCursor();

        void hideCursor();

        void applyDefaultCursor();

        void applySystemCursor(ResolvedCursorKind cursorKind);
    }

    private static final class SdlNativeCursorBackend implements NativeCursorBackend {

        private final SdlReflectionBridge reflectionBridge = SdlReflectionBridge.getInstance();
        private final Map<ResolvedCursorKind, Long> cursorHandles =
                new EnumMap<ResolvedCursorKind, Long>(ResolvedCursorKind.class);

        @Override
        public boolean isRuntimeAvailable() {
            if (!reflectionBridge.isAvailable()) {
                return false;
            }
            try {
                return Display.isCreated();
            } catch (LinkageError ignored) {
                return false;
            } catch (RuntimeException ignored) {
                return false;
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
            reflectionBridge.runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    reflectionBridge.applyDefaultCursor();
                }
            });
        }

        @Override
        public void applySystemCursor(ResolvedCursorKind cursorKind) {
            final long cursorHandle = getOrCreateCursorHandle(cursorKind);
            if (cursorHandle == 0L) {
                applyDefaultCursor();
                return;
            }
            reflectionBridge.runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    if (!reflectionBridge.setCursor(cursorHandle)) {
                        reflectionBridge.applyDefaultCursor();
                    }
                }
            });
        }

        private synchronized long getOrCreateCursorHandle(ResolvedCursorKind cursorKind) {
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
        private static final SdlReflectionBridge INSTANCE = new SdlReflectionBridge();

        private final Method showCursorMethod;
        private final Method hideCursorMethod;
        private final Method setCursorMethod;
        private final Method createSystemCursorMethod;
        private final Method setNativeCursorMethod;
        private final Method runOnMainThreadMethod;
        private final Map<ResolvedCursorKind, Integer> systemCursorConstants;
        private final boolean available;

        private SdlReflectionBridge() {
            Method resolvedShowCursorMethod = null;
            Method resolvedHideCursorMethod = null;
            Method resolvedSetCursorMethod = null;
            Method resolvedCreateSystemCursorMethod = null;
            Method resolvedSetNativeCursorMethod = null;
            Method resolvedRunOnMainThreadMethod = null;
            Map<ResolvedCursorKind, Integer> resolvedSystemCursorConstants =
                    new EnumMap<ResolvedCursorKind, Integer>(ResolvedCursorKind.class);
            boolean resolvedAvailable = false;
            try {
                Class<?> sdlMouseClass = Class.forName(SDL_MOUSE_CLASS_NAME);
                Class<?> mainThreadExecClass = Class.forName(MAIN_THREAD_EXEC_CLASS_NAME);
                Class<?> lwjglMouseClass = Class.forName("org.lwjglx.input.Mouse");
                Class<?> lwjglCursorClass = Class.forName("org.lwjglx.input.Cursor");
                resolvedShowCursorMethod = sdlMouseClass.getMethod("SDL_ShowCursor");
                resolvedHideCursorMethod = sdlMouseClass.getMethod("SDL_HideCursor");
                resolvedSetCursorMethod = sdlMouseClass.getMethod("SDL_SetCursor", Long.TYPE);
                resolvedCreateSystemCursorMethod = sdlMouseClass.getMethod("SDL_CreateSystemCursor", Integer.TYPE);
                resolvedSetNativeCursorMethod = lwjglMouseClass.getMethod("setNativeCursor", lwjglCursorClass);
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
            } catch (ReflectiveOperationException ignored) {
                resolvedSystemCursorConstants.clear();
            }
            this.showCursorMethod = resolvedShowCursorMethod;
            this.hideCursorMethod = resolvedHideCursorMethod;
            this.setCursorMethod = resolvedSetCursorMethod;
            this.createSystemCursorMethod = resolvedCreateSystemCursorMethod;
            this.setNativeCursorMethod = resolvedSetNativeCursorMethod;
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

        void applyDefaultCursor() {
            invoke(setNativeCursorMethod, null, new Object[] { null });
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
