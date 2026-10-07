package club.heiqi.uilib.testing;

import java.util.ArrayList;
import java.util.List;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import static org.lwjgl.glfw.GLFW.*;

/** 测试域的真实GLFW宿主；同一宿主可依次创建core/compatibility上下文。 */
public final class GlfwTestEnvironment implements AutoCloseable {
    public enum Profile { CORE, COMPATIBILITY }
    private final GLFWErrorCallback callback;
    private final List<Window> windows = new ArrayList<>();
    private boolean closed;

    public GlfwTestEnvironment() {
        callback = GLFWErrorCallback.createPrint(System.err);
        try {
            callback.set();
            if (!glfwInit()) throw new IllegalStateException("GLFW初始化失败");
        } catch (Throwable failure) {
            try { close(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public Window window(int width, int height, String title, Profile profile, int minor) {
        return window(width, height, title, profile, minor, false, true);
    }

    /** forwardCompat及延迟caps保留原宿主场景：首次consumer可自行建立capabilities。 */
    public Window window(int width, int height, String title, Profile profile, int minor,
                         boolean forwardCompat, boolean createCapabilities) {
        if (closed) throw new IllegalStateException("GLFW宿主已关闭");
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, minor);
        glfwWindowHint(GLFW_OPENGL_PROFILE, profile == Profile.CORE ? GLFW_OPENGL_CORE_PROFILE : GLFW_OPENGL_COMPAT_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, forwardCompat ? GLFW_TRUE : GLFW_FALSE);
        long handle = glfwCreateWindow(width, height, title, 0, 0);
        if (handle == 0) throw new IllegalStateException("GLFW窗口创建失败: " + title);
        Window window = new Window(handle);
        windows.add(window);
        try {
            window.makeCurrent();
            if (createCapabilities) window.capabilities = GL.createCapabilities();
            return window;
        } catch (Throwable failure) {
            try { window.close(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public final class Window implements AutoCloseable {
        private long handle;
        private GLCapabilities capabilities;
        private Window(long handle) { this.handle = handle; }
        public void makeCurrent() {
            if (handle == 0) throw new IllegalStateException("窗口已关闭");
            // 离开窗口前保存consumer延迟创建的caps，切换时恢复该窗口自己的函数表。
            for (Window other : windows) if (other.handle == glfwGetCurrentContext()) {
                try { other.capabilities = GL.getCapabilities(); } catch (IllegalStateException absent) { other.capabilities = null; }
            }
            GL.setCapabilities(null);
            glfwMakeContextCurrent(handle);
            GL.setCapabilities(capabilities);
        }
        @Override public void close() {
            if (handle == 0) return;
            if (glfwGetCurrentContext() == handle) {
                GL.setCapabilities(null);
                glfwMakeContextCurrent(0);
            }
            glfwDestroyWindow(handle);
            handle = 0;
            capabilities = null;
            windows.remove(this);
        }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        Throwable failure = null;
        List<Window> snapshot = new ArrayList<>(windows);
        for (int index = snapshot.size() - 1; index >= 0; index--) {
            try { snapshot.get(index).close(); } catch (Throwable error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        }
        try { glfwTerminate(); } catch (Throwable error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        try { glfwSetErrorCallback(null); } catch (Throwable error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        try { callback.free(); } catch (Throwable error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        if (failure instanceof Error) throw (Error) failure;
        if (failure != null) throw (RuntimeException) failure;
    }
}
