package club.heiqi.uilib.host.desktop;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

import club.heiqi.uilib.testing.gallery.GalleryJourney;
import club.heiqi.uilib.testing.gallery.TestGallery;
import club.heiqi.uilib.ui.scene.control.ThemeConfiguration;
import club.heiqi.uilib.ui.scene.control.ThemeMode;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;

import org.lwjgl.glfw.GLFWCharCallback;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWScrollCallback;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.imageio.ImageIO;

/** 人工和自动入口都打开同一TestGallery；自动驱动只递交已安装GLFW回调。 */
public final class DesktopGallery {
    public static void main(String[] args) throws Exception {
        boolean verify = args.length > 0 && "--verify".equals(args[0]);
        Path output = args.length > 1 ? Paths.get(args[1]) : Paths.get("gallery-report");
        Files.createDirectories(output);
        Path configuration = output.resolve("gallery.yaml");
        if (verify) Files.deleteIfExists(configuration);
        try (DesktopWindow window =
                DesktopWindow.openWithImages(
                        configuration, images -> new TestGallery(() -> images))) {
            if (!verify) {
                while (!window.shouldClose()) window.frame();
                return;
            }
            glfwSetWindowSize(window.handle(), 1400, 1100);
            window.frame();
            GLFWCursorPosCallback cursor = glfwSetCursorPosCallback(window.handle(), null);
            glfwSetCursorPosCallback(window.handle(), cursor);
            GLFWMouseButtonCallback mouse = glfwSetMouseButtonCallback(window.handle(), null);
            glfwSetMouseButtonCallback(window.handle(), mouse);
            GLFWCharCallback character = glfwSetCharCallback(window.handle(), null);
            glfwSetCharCallback(window.handle(), character);
            GLFWScrollCallback scroll = glfwSetScrollCallback(window.handle(), null);
            glfwSetScrollCallback(window.handle(), scroll);
            GLFWKeyCallback key = glfwSetKeyCallback(window.handle(), null);
            glfwSetKeyCallback(window.handle(), key);
            if (cursor == null || mouse == null || character == null || key == null)
                throw new AssertionError("宿主没有安装输入回调");
            GalleryJourney.run(
                    new GalleryJourney.Driver() {
                        public void frame() {
                            window.frame();
                        }

                        public club.heiqi.uilib.ui.scene.node.SceneNode focused() {
                            final club.heiqi.uilib.ui.scene.node.SceneNode[] value = {null};
                            window.domain.execute(() -> value[0] = window.page.runtime().focusRouter().focused().get());
                            return value[0];
                        }

                        public PaintPlan paint() {
                            return window.lastPlan;
                        }

                        public SceneGeometryView geometry() {
                            final SceneGeometryView[] result = {null};
                            window.domain.execute(
                                    () -> result[0] = window.page.pipeline().current().geometry);
                            return result[0];
                        }

                        public void click(int x, int y) {
                            int[] width = new int[1], height = new int[1];
                            glfwGetWindowSize(window.handle(), width, height);
                            cursor.invoke(
                                    window.handle(),
                                    (double) x * width[0] / window.width(),
                                    (double) y * height[0] / window.height());
                            mouse.invoke(window.handle(), 0, GLFW_PRESS, 0);
                            mouse.invoke(window.handle(), 0, GLFW_RELEASE, 0);
                        }

                        public void scroll(int x, int y, int delta) {
                            int[] width = new int[1], height = new int[1];
                            glfwGetWindowSize(window.handle(), width, height);
                            cursor.invoke(
                                    window.handle(),
                                    (double) x * width[0] / window.width(),
                                    (double) y * height[0] / window.height());
                            scroll.invoke(window.handle(), 0, delta);
                        }

                        public void command(club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command command, boolean shift) {
                            int code, mods = shift ? GLFW_MOD_SHIFT : 0;
                            switch (command) {
                                case UNDO: code = GLFW_KEY_Z; mods |= GLFW_MOD_CONTROL; break;
                                case REDO: code = GLFW_KEY_Y; mods |= GLFW_MOD_CONTROL; break;
                                case SUBMIT: code = GLFW_KEY_ENTER; mods |= GLFW_MOD_CONTROL; break;
                                case CONFIRM: code = GLFW_KEY_ENTER; break;
                                case DOCUMENT_HOME: code = GLFW_KEY_HOME; mods |= GLFW_MOD_CONTROL; break;
                                case DOCUMENT_END: code = GLFW_KEY_END; mods |= GLFW_MOD_CONTROL; break;
                                default: throw new IllegalArgumentException("旅程未声明宿主键：" + command);
                            }
                            key.invoke(window.handle(), code, 0, GLFW_PRESS, mods);
                            key.invoke(window.handle(), code, 0, GLFW_RELEASE, mods);
                        }
                        public void escape() {
                            key.invoke(window.handle(), GLFW_KEY_ESCAPE, 0, GLFW_PRESS, 0);
                            key.invoke(window.handle(), GLFW_KEY_ESCAPE, 0, GLFW_RELEASE, 0);
                        }
                        public void typeText(String value) {
                            value.codePoints().forEach(c -> character.invoke(window.handle(), c));
                        }
                        public void evidence(String name) {
                            try { settle(window); screenshot(window, output.resolve(name + ".png"), 0xfffafafa); }
                            catch (Exception failure) { throw new IllegalStateException("广场实窗取证失败：" + name, failure); }
                        }
                        public void replaceText(String value) {
                            key.invoke(
                                    window.handle(), GLFW_KEY_A, 0, GLFW_PRESS, GLFW_MOD_CONTROL);
                            key.invoke(
                                    window.handle(), GLFW_KEY_A, 0, GLFW_RELEASE, GLFW_MOD_CONTROL);
                            value.codePoints().forEach(c -> character.invoke(window.handle(), c));
                        }
                    });
            // 固定本地样本的等待上限只属于验收，不替生产增加重试；每次检查实际绘制载荷。
            PaintCommand image = image(window);
            long deadline = System.nanoTime() + 5000000000L;
            while (image == null && System.nanoTime() < deadline) {
                window.frame();
                image = image(window);
            }
            if (image == null) throw new AssertionError("资源图像未ready");
            deadline = System.nanoTime() + 5000000000L;
            do {
                window.frame();
                if (window.fonts.rasterizer().stats().pending == 0
                        && window.fonts.rasterizer().stats().ready == 0
                        && window.fonts.presentationStats().uploadCalls == 0) break;
            } while (System.nanoTime() < deadline);
            if (window.fonts.rasterizer().stats().pending != 0
                    || window.fonts.rasterizer().stats().errors != 0)
                throw new AssertionError("可见字形未准备完成");
            screenshot(window, output.resolve("gallery-desktop.png"), 0xfffafafa);
            verifyImage(window, image);
            Object generation = image.image.content;
            window.reloadConfiguration();
            image = image(window);
            deadline = System.nanoTime() + 5000000000L;
            while ((image == null || image.image.content == generation)
                    && System.nanoTime() < deadline) {
                window.frame();
                image = image(window);
            }
            if (image == null || image.image.content == generation)
                throw new AssertionError("资源重载未换代");
            verifyImage(window, image);
            click(window, cursor, mouse, scroll, "场景概览");
            settle(window);
            screenshot(window, output.resolve("gallery-home.png"), 0xfffafafa);
            click(window, cursor, mouse, scroll, "预览深色");
            settle(window);
            byte[] beforeTheme = Files.readAllBytes(configuration);
            Object samePage = window.page;
            window.domain.execute(() -> window.configuration.set(ThemeConfiguration.KEY,ThemeMode.DARK));
            settle(window);
            window.domain.execute(() -> window.configuration.set(ThemeConfiguration.KEY,ThemeMode.LIGHT));
            settle(window);
            // 全局发布清除旧深色预览，按钮才能重新变为“预览深色”。
            click(window, cursor, mouse, scroll, "预览深色");
            settle(window);
            if (window.page != samePage || !java.util.Arrays.equals(beforeTheme,Files.readAllBytes(configuration)))
                throw new AssertionError("图库主题发布重建页面或隐式写盘");
            System.out.println("PASS gallery theme publication clears local preview and follows global, same page, no implicit save");
            screenshot(window, output.resolve("gallery-dark.png"), 0xff09090b);
            glfwSetWindowSize(window.handle(), 640, 900);
            settle(window);
            screenshot(window, output.resolve("gallery-narrow.png"), 0xff09090b);
            click(window, cursor, mouse, scroll, "打开知识库");
            settle(window);
            screenshot(window, output.resolve("gallery-markdown-narrow.png"), 0xff09090b);
            glfwSetWindowSize(window.handle(), 1100, 900);
            settle(window);
            screenshot(window, output.resolve("gallery-markdown-wide.png"), 0xff09090b);
            click(window, cursor, mouse, scroll, "排产核对");
            settle(window);
            screenshot(window, output.resolve("gallery-checklist-wide.png"), 0xff09090b);
            glfwSetWindowSize(window.handle(), 640, 900);
            settle(window);
            screenshot(window, output.resolve("gallery-checklist-narrow.png"), 0xff09090b);
            glfwSetWindowSize(window.handle(), 1100, 900);
            settle(window);
            click(window, cursor, mouse, scroll, "交接备忘");
            settle(window);
            screenshot(window, output.resolve("gallery-handover.png"), 0xff09090b);
            click(window, cursor, mouse, scroll, "场景概览");
            click(window, cursor, mouse, scroll, "打开生产计划");
            settle(window);
            screenshot(window, output.resolve("gallery-production.png"), 0xff09090b);
            scrollPage(window, cursor, scroll, -20);
            settle(window);
            screenshot(window, output.resolve("gallery-production-math.png"), 0xff09090b);
            click(window, cursor, mouse, scroll, "场景概览");
            click(window, cursor, mouse, scroll, "打开数据报告");
            settle(window);
            screenshot(window, output.resolve("gallery-report.png"), 0xff09090b);
            scrollPage(window, cursor, scroll, -20);
            settle(window);
            screenshot(window, output.resolve("gallery-report-math.png"), 0xff09090b);
            click(window, cursor, mouse, scroll, "场景概览");
            System.out.println(
                    "GALLERY host=desktop sharedDeclaration=true sharedJourney=true imagePixels=true resourceReload=true renderer="
                            + glGetString(GL_RENDERER));
        }
        Files.write(
                output.resolve("result.txt"),
                "PASS sharedJourney actualGLFWCallbacks actualImagePixels resourceReload close\n"
                        .getBytes("UTF-8"));
    }

    private static void scrollPage(
            DesktopWindow window,
            GLFWCursorPosCallback cursor,
            GLFWScrollCallback scroll,
            int delta) {
        int[] width = {0}, height = {0};
        glfwGetWindowSize(window.handle(), width, height);
        // 页面右侧留白仍属正式滚动根，避开文档中的局部横滚视口。
        cursor.invoke(window.handle(), width[0] - 32, height[0] / 2.0);
        scroll.invoke(window.handle(), 0, delta);
        window.frame();
    }

    private static PaintCommand image(DesktopWindow window) {
        return window.lastPlan.commands().stream()
                .filter(
                        c ->
                                c.kind == PaintCommand.Kind.IMAGE
                                        && c.image.content.width == 16
                                        && c.image.content.height == 16)
                .findFirst()
                .orElse(null);
    }

    private static void verifyImage(DesktopWindow window, PaintCommand image) {
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        PaintPlanBuilder builder = new PaintPlanBuilder();
        builder.add(image);
        window.fonts.beginPresentation(1000000);
        try {
            window.replay(builder.build());
        } finally {
            window.fonts.endPresentation();
        }
        glFinish();
        ByteBuffer pixel = ByteBuffer.allocateDirect(4);
        int x = (int) (image.image.x + image.image.width / 2),
                y = (int) (image.image.y + image.image.height / 2);
        glReadPixels(x, window.height() - 1 - y, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
        if (glGetError() != GL_NO_ERROR || (pixel.get(2) & 255) < 150)
            throw new AssertionError("PNG纹理没有真实蓝色像素");
    }

    private static void settle(DesktopWindow window) {
        long deadline = System.nanoTime() + 5000000000L;
        do {
            window.frame();
            if (window.fonts.rasterizer().stats().pending == 0
                    && window.fonts.rasterizer().stats().ready == 0
                    && window.fonts.presentationStats().uploadCalls == 0) break;
        } while (System.nanoTime() < deadline);
        if (window.fonts.rasterizer().stats().pending != 0
                || window.fonts.rasterizer().stats().errors != 0)
            throw new AssertionError("截图可见字形未准备完成");
    }

    private static void click(
            DesktopWindow window,
            GLFWCursorPosCallback cursor,
            GLFWMouseButtonCallback mouse,
            GLFWScrollCallback scroll,
            String text) {
        final SceneGeometryView[] view = {null};
        for (int attempt = 0; attempt < 30; attempt++) {
            window.domain.execute(() -> view[0] = window.page.pipeline().current().geometry);
            SceneGeometryView.Entry target =
                    view[0].ordered().stream()
                            .filter(e -> e.text != null && text.equals(e.text.originalText))
                            .findFirst()
                            .orElse(null);
            if (target != null
                    && (target.clip == null ? target.box : target.box.intersect(target.clip)).height
                            > 0) break;
            int[] sizeW = {0}, sizeH = {0};
            glfwGetWindowSize(window.handle(), sizeW, sizeH);
            cursor.invoke(window.handle(), sizeW[0] / 2.0, sizeH[0] / 2.0);
            scroll.invoke(
                    window.handle(),
                    0,
                    target != null && target.box.y < (target.clip == null ? 0 : target.clip.y)
                            ? 6
                            : -6);
            window.frame();
        }
        SceneGeometryView.Entry entry =
                view[0].ordered().stream()
                        .filter(e -> e.text != null && text.equals(e.text.originalText))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("找不到导航：" + text));
        club.heiqi.uilib.ui.scene.layout.LayoutBox hit =
                entry.clip == null ? entry.box : entry.box.intersect(entry.clip);
        if (hit.width <= 0 || hit.height <= 0) throw new AssertionError("导航被裁剪：" + text);
        int[] width = new int[1], height = new int[1];
        glfwGetWindowSize(window.handle(), width, height);
        cursor.invoke(
                window.handle(),
                (double) (hit.x + hit.width / 2) * width[0] / window.width(),
                (double) (hit.y + hit.height / 2) * height[0] / window.height());
        mouse.invoke(window.handle(), 0, GLFW_PRESS, 0);
        mouse.invoke(window.handle(), 0, GLFW_RELEASE, 0);
        window.frame();
    }

    private static void screenshot(DesktopWindow window, Path target, int background)
            throws Exception {
        window.fonts.beginPresentation(1000000);
        try {
            window.replay(window.lastPlan);
        } finally {
            window.fonts.endPresentation();
        }
        glFinish();
        ByteBuffer bytes = ByteBuffer.allocateDirect(window.width() * window.height() * 4);
        glReadPixels(0, 0, window.width(), window.height(), GL_RGBA, GL_UNSIGNED_BYTE, bytes);
        BufferedImage output =
                new BufferedImage(window.width(), window.height(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < window.height(); y++)
            for (int x = 0; x < window.width(); x++) {
                int p = ((window.height() - 1 - y) * window.width() + x) * 4;
                output.setRGB(
                        x,
                        y,
                        0xff000000
                                | ((bytes.get(p) & 255) << 16)
                                | ((bytes.get(p + 1) & 255) << 8)
                                | (bytes.get(p + 2) & 255));
            }
        if (glGetError() != GL_NO_ERROR
                || output.getRGB(window.width() - 4, window.height() - 4) != background)
            throw new AssertionError("共享广场背景未覆盖视口");
        PaintCommand title =
                window.lastPlan.commands().stream()
                        .filter(
                                c ->
                                        c.kind == PaintCommand.Kind.TEXT
                                                && c.text.originalText.startsWith("QZ / 场景工作台"))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("广场标题缺失"));
        int ink = 0;
        for (int y = Math.max(0, title.box.y);
                y < Math.min(window.height(), title.box.y + title.box.height);
                y++)
            for (int x = Math.max(0, title.box.x);
                    x < Math.min(window.width(), title.box.x + title.box.width);
                    x++) if (output.getRGB(x, y) != background) ink++;
        if (ink == 0) throw new AssertionError("广场标题没有真实GL字形像素");
        ImageIO.write(output, "png", target.toFile());
    }
}
