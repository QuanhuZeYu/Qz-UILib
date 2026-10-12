package club.heiqi.uilib.host.desktop;

import static org.lwjgl.glfw.GLFW.*;
import java.util.Objects;
import java.util.stream.Collectors;
import org.lwjgl.glfw.GLFWCharCallback;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWScrollCallback;
import org.lwjgl.glfw.GLFWWindowCloseCallback;
import org.lwjgl.glfw.GLFWWindowFocusCallback;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 包内机械输入支持：借用已安装的生产回调，不拥有或释放回调，也不直写业务模型。 */
final class DesktopTestInput {
    private final DesktopWindow window;
    final GLFWCursorPosCallback cursor;
    final GLFWMouseButtonCallback mouse;
    final GLFWScrollCallback scroll;
    final GLFWCharCallback character;
    final GLFWKeyCallback key;
    final GLFWWindowFocusCallback focus;
    final GLFWWindowCloseCallback close;

    DesktopTestInput(DesktopWindow window) {
        this.window = window;
        long handle = window.handle();
        cursor = glfwSetCursorPosCallback(handle, null); glfwSetCursorPosCallback(handle, cursor);
        mouse = glfwSetMouseButtonCallback(handle, null); glfwSetMouseButtonCallback(handle, mouse);
        scroll = glfwSetScrollCallback(handle, null); glfwSetScrollCallback(handle, scroll);
        character = glfwSetCharCallback(handle, null); glfwSetCharCallback(handle, character);
        key = glfwSetKeyCallback(handle, null); glfwSetKeyCallback(handle, key);
        focus = glfwSetWindowFocusCallback(handle, null); glfwSetWindowFocusCallback(handle, focus);
        close = glfwSetWindowCloseCallback(handle, null); glfwSetWindowCloseCallback(handle, close);
        if (cursor == null || mouse == null || scroll == null || character == null
                || key == null || focus == null || close == null)
            throw new AssertionError("生产宿主未安装标准GLFW回调");
    }

    static SceneGeometryView geometry(DesktopWindow window) {
        final SceneGeometryView[] result = {null};
        window.domain.execute(() -> result[0] = window.page.pipeline().current().geometry);
        return Objects.requireNonNull(result[0], "页面缺少统一几何");
    }

    static SceneGeometryView.Entry geometry(DesktopWindow window, SceneNode node) {
        return Objects.requireNonNull(geometry(window).get(node), "节点缺少统一几何");
    }

    static LayoutBox hit(SceneGeometryView.Entry entry) {
        return entry.clip == null ? entry.box : entry.box.intersect(entry.clip);
    }

    void point(double x, double y) {
        if (window.width() <= 0 || window.height() <= 0)
            throw new AssertionError("窗口没有有效framebuffer空间");
        int[] width = {0}, height = {0};
        glfwGetWindowSize(window.handle(), width, height);
        cursor.invoke(window.handle(), x * width[0] / window.width(), y * height[0] / window.height());
    }

    void moveTo(SceneNode node) { moveTo(geometry(window, node)); }
    private void moveTo(SceneGeometryView.Entry entry) {
        LayoutBox box = hit(entry);
        if (box.width <= 0 || box.height <= 0) throw new AssertionError("目标没有可命中空间");
        point(box.x + box.width / 2.0, box.y + box.height / 2.0);
    }

    void enqueueAt(double x, double y) {
        point(x, y);
        mouse.invoke(window.handle(), GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS, 0);
        mouse.invoke(window.handle(), GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE, 0);
    }

    void enqueue(SceneGeometryView.Entry entry) {
        moveTo(entry);
        mouse.invoke(window.handle(), GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS, 0);
        mouse.invoke(window.handle(), GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE, 0);
    }

    void click(SceneGeometryView.Entry entry) { enqueue(entry); window.frame(); }

    private SceneGeometryView.Entry button(String text, boolean visible) {
        return geometry(window).ordered().stream().filter(entry -> entry.text != null
                && text.equals(entry.text.originalText) && entry.node.clickAction() != null
                && entry.node.enabled() && (!visible || hit(entry).width > 0 && hit(entry).height > 0))
                .findFirst().orElse(null);
    }

    void enqueueClick(String text) {
        SceneGeometryView.Entry target = button(text, true);
        if (target == null) throw new AssertionError("找不到同批按钮：" + text);
        enqueue(target);
    }

    void click(String text) {
        for (int attempt = 0; attempt < 40; attempt++) {
            SceneGeometryView.Entry target = button(text, true);
            if (target != null) { click(target); return; }
            SceneGeometryView.Entry clipped = button(text, false);
            if (clipped == null) throw new AssertionError("找不到可操作按钮：" + text + "；当前文本："
                    + geometry(window).ordered().stream().filter(entry -> entry.text != null)
                        .map(entry -> entry.text.originalText).collect(Collectors.joining(" | ")));
            LayoutBox clip = clipped.clip == null
                    ? new LayoutBox(0, 0, window.width(), window.height()) : clipped.clip;
            point(clip.x + clip.width / 2.0, clip.y + clip.height / 2.0);
            scroll.invoke(window.handle(), 0, clipped.box.y < clip.y ? 5 : -5);
            window.frame();
        }
        throw new AssertionError("按钮不可显露：" + text);
    }
}
