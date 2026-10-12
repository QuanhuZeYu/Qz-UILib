package club.heiqi.uilib.host.desktop;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static club.heiqi.uilib.host.desktop.DesktopTestFrames.*;
import static club.heiqi.uilib.host.desktop.DesktopTestInput.geometry;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Objects;
import javax.imageio.ImageIO;
import club.heiqi.uilib.host.client.font.FramebufferSceneRenderer;
import club.heiqi.uilib.ui.config.ConfigSchema;
import club.heiqi.uilib.ui.config.ConfigSession;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.control.ThemeConfiguration;
import club.heiqi.uilib.ui.scene.control.ThemeMode;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.font.glyph.AsyncGlyphRasterizer;

/** 只在desktopTest产物中的有限真实窗口验收；注入已安装回调，原生resize单独取证。 */
public final class DesktopVerification {
    private static final StringBuilder evidence = new StringBuilder();
    private DesktopVerification() {}
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void pass(String message) { evidence.append("PASS ").append(message).append('\n'); System.out.println("PASS " + message); }
    public static void main(String[] args) throws Exception {
        Path output = args.length == 0 ? Paths.get("build/desktop/report") : Paths.get(args[0]);
        Files.createDirectories(output);
        Path config = output.resolve("desktop.yaml");
        Files.deleteIfExists(config);
        DesktopWindow window = null;
        Throwable primary = null;
        try {
            window = DesktopWindow.open(config);
            final DesktopWindow w = window;
            DesktopTestInput input = new DesktopTestInput(w);
            DesktopPage view = w.samplePage;
            ScenePageSession session = w.page;
            AsyncGlyphRasterizer rasterizer = w.fonts.rasterizer();
            evidence.append("GL vendor=").append(glGetString(GL_VENDOR))
                .append(" renderer=").append(glGetString(GL_RENDERER))
                .append(" version=").append(glGetString(GL_VERSION)).append('\n');
            require(glfwGetWindowAttrib(w.handle(), GLFW_VISIBLE) == GLFW_TRUE, "窗口不可见");
            frames(w, 24);
            pass("visible real GLFW window, native OpenGL, 24 production frames");
            w.domain.execute(() -> {
                require(w.configuration.get(ThemeConfiguration.KEY)==ThemeMode.LIGHT,"新配置主题不是默认浅色");
                require(view.root.background()==SceneTheme.light().panel,"旧sample.dark压过全局默认浅色");
            });
            settle(w);
            BufferedImage initial = capture(w, w.lastPlan);
            ImageIO.write(initial, "png", output.resolve("initial.png").toFile());
            PaintPlan titlePlan = titlePlan(w, DesktopWindowConfiguration.DEFAULT.text);
            BufferedImage glyphs = capture(w, titlePlan);
            int glyphPixels = nonBlack(glyphs);
            require(glyphPixels > 0, "标题字形没有真实GL像素");
            ImageIO.write(glyphs, "png", output.resolve("glyphs.png").toFile());
            pass("TEXT-only GL readback glyphPixels=" + glyphPixels);

            input.moveTo(view.button);
            input.mouse.invoke(w.handle(), GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS, 0);
            input.mouse.invoke(w.handle(), GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE, 0);
            frames(w, 2);
            w.domain.execute(() -> require(view.counter.get() == 1, "按钮未写入业务signal"));
            pass("installed mouse callback DOWN/UP -> counter signal=1");
            input.moveTo(view.field);
            input.mouse.invoke(w.handle(), 0, GLFW_PRESS, 0);
            input.mouse.invoke(w.handle(), 0, GLFW_RELEASE, 0);
            input.key.invoke(w.handle(), GLFW_KEY_A, 0, GLFW_PRESS, GLFW_MOD_CONTROL);
            input.character.invoke(w.handle(), 'A');
            input.character.invoke(w.handle(), 0x1F600);
            input.key.invoke(w.handle(), GLFW_KEY_SPACE, 0, GLFW_PRESS, 0);
            input.character.invoke(w.handle(), ' ');
            input.character.invoke(w.handle(), 'Z');
            String edited = "A" + new String(Character.toChars(0x1F600)) + " Z";
            frames(w, 2);
            w.domain.execute(() -> require(edited.equals(view.editing.text().get()), "Unicode/空格提交重复或遗漏，actual=" + view.editing.text().get() + ", expected=" + edited));
            input.key.invoke(w.handle(), GLFW_KEY_A, 0, GLFW_PRESS, GLFW_MOD_CONTROL);
            input.key.invoke(w.handle(), GLFW_KEY_C, 0, GLFW_PRESS, GLFW_MOD_CONTROL);
            frames(w, 2);
            require(edited.equals(glfwGetClipboardString(w.handle())), "真实GLFW剪贴板copy失败");
            input.key.invoke(w.handle(), GLFW_KEY_X, 0, GLFW_PRESS, GLFW_MOD_CONTROL);
            frames(w, 2);
            w.domain.execute(() -> require(view.editing.text().get().isEmpty(), "剪切未清空已选草稿"));
            input.key.invoke(w.handle(), GLFW_KEY_V, 0, GLFW_PRESS, GLFW_MOD_CONTROL);
            input.key.invoke(w.handle(), GLFW_KEY_ENTER, 0, GLFW_PRESS, 0);
            frames(w, 2);
            w.domain.execute(() -> require(edited.equals(view.editing.text().get()), "剪切粘贴未恢复文本"));
            pass("installed key/char callbacks -> supplementary Unicode, single space, native clipboard cut/paste/confirm");

            input.moveTo(view.scroll);
            int beforeScroll = geometry(w, view.scroll).scrollOffset;
            input.scroll.invoke(w.handle(), 0, -3);
            frames(w, 2);
            require(geometry(w, view.scroll).scrollOffset > beforeScroll, "滚轮没有推进核心滚动位置");
            pass("installed scroll callback -> shared scene geometry scrollOffset=" + geometry(w, view.scroll).scrollOffset);
            int oldWidth = w.width();
            int callbackCount = w.resizeCallbacks;
            glfwSetWindowSize(w.handle(), 640, 480);
            frames(w, 8);
            require(w.width() != oldWidth && w.resizeCallbacks > callbackCount, "真实native resize未进入宿主");
            require(geometry(w, view.root).box.width == w.width(), "布局与新framebuffer尺寸不一致");
            require(w.page == session && view.builds == 1, "resize重建了页面");
            pass("native GLFW resize callback -> framebuffer=" + w.width() + "x" + w.height() + ", page builds=1");

            // 外部配置作者通过另一份核心配置会话修改真实YAML，不提前写窗口权威值。
            ReactiveDomain writerDomain = ReactiveDomain.direct();
            long oldGeneration = w.fonts.fonts().generation();
            Path systemFont = args.length > 1 ? Paths.get(args[1])
                    : Paths.get(Objects.requireNonNull(System.getenv("WINDIR"), "非Windows请显式提供字体参数"), "Fonts", "segoeui.ttf");
            Path fixtures = output.resolve("fonts");
            Files.createDirectories(fixtures);
            Path selectedFont = fixtures.resolve("selected.ttf").toAbsolutePath();
            Files.copy(systemFont, selectedFont, StandardCopyOption.REPLACE_EXISTING);
            writerDomain.execute(() -> {
                ConfigSession writer = new ConfigSession(writerDomain, ConfigSchema.of(DesktopWindowConfiguration.PROFILE, w.fontCandidates, ThemeConfiguration.KEY));
                try {
                    writer.load(config);
                    writer.set(DesktopWindowConfiguration.PROFILE, new DesktopWindowConfiguration.WindowProfile("Desktop reloaded", "Reloaded title 中文", false, 26));
                    writer.set(ThemeConfiguration.KEY, ThemeMode.DARK);
                    writer.set(w.fontCandidates, Collections.singletonList("file:" + selectedFont));
                    writer.save(config);
                } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            });
            require(!"Desktop reloaded".equals(w.title()), "文件写入前已更新窗口");
            input.key.invoke(w.handle(), GLFW_KEY_R, 0, GLFW_PRESS, GLFW_MOD_CONTROL);
            frames(w, 24);
            require("Desktop reloaded".equals(w.title()), "Ctrl+R未更新窗口标题");
            require(w.fonts.fonts().generation() != oldGeneration, "字体来源未换代");
            settle(w);
            PaintPlan reloadedText = titlePlan(w, "Reloaded title 中文");
            BufferedImage reloaded = capture(w, w.lastPlan);
            ImageIO.write(reloaded, "png", output.resolve("reloaded.png").toFile());
            BufferedImage fileGlyphs = capture(w, reloadedText);
            require(nonBlack(fileGlyphs) > 0, "file候选没有真实文字像素");
            ImageIO.write(fileGlyphs, "png", output.resolve("file-glyphs.png").toFile());
            w.domain.execute(() -> {
                require(view.title.style().size == 26, "配置字号未进入标准控件");
                require(view.root.background() == SceneTheme.dark().panel, "全局主题未重载，或旧sample.dark覆盖配置");
                require(view.counter.get() == 1 && edited.equals(view.editing.text().get()), "重载丢失业务/编辑状态");
            });
            require(w.page == session && view.builds == 1, "配置重载重建页面");
            pass("external core YAML write + installed Ctrl+R callback -> title/text/globalTheme/fontSize + file-family source generation, same page");
            byte[] themeSaved=Files.readAllBytes(config);
            w.domain.execute(() -> w.configuration.set(ThemeConfiguration.KEY,ThemeMode.LIGHT));frames(w,2);
            w.domain.execute(() -> require(view.root.background()==SceneTheme.light().panel&&view.title.style().size==26,"全局浅色更新未自动呈现或字号丢失"));
            w.domain.execute(() -> w.configuration.set(ThemeConfiguration.KEY,ThemeMode.DARK));frames(w,2);
            w.domain.execute(() -> require(view.root.background()==SceneTheme.dark().panel&&view.counter.get()==1,"全局深色更新未自动呈现或业务状态丢失"));
            require(w.page==session&&view.builds==1,"全局主题更新重建页面");
            require(java.util.Arrays.equals(themeSaved,Files.readAllBytes(config)),"内存主题发布隐式写盘");
            pass("application theme light/dark publishes dynamically to existing sample, legacy dark ignored, fontSize/state preserved, no implicit save");
            long reloadedGeneration = w.fonts.fonts().generation();
            input.moveTo(view.reloadButton);
            input.mouse.invoke(w.handle(), 0, GLFW_PRESS, 0);
            input.mouse.invoke(w.handle(), 0, GLFW_RELEASE, 0);
            frames(w, 4);
            require(w.fonts.fonts().generation() != reloadedGeneration, "重载按钮未刷新字体来源");
            pass("standard reload button -> explicit core load and font resource generation");

            input.focus.invoke(w.handle(), false);
            frames(w, 2);
            w.domain.execute(() -> require(w.page.runtime().focusRouter().focused().get() == null, "窗口失焦未清理核心焦点"));
            // 同一输入批次：先收到DOWN，随后失焦，最后收到文字；失焦不可越过DOWN结算。
            input.moveTo(view.field);
            input.mouse.invoke(w.handle(), 0, GLFW_PRESS, 0);
            input.focus.invoke(w.handle(), false);
            input.character.invoke(w.handle(), 'Q');
            frames(w, 2);
            w.domain.execute(() -> {
                require(w.page.runtime().focusRouter().focused().get() == null, "同批DOWN在失焦后重新取得焦点");
                require(edited.equals(view.editing.text().get()), "失焦后的文字进入旧字段");
            });
            pass("installed focus callback queued after DOWN and before TEXT -> focus cleared, draft unchanged");
            input.focus.invoke(w.handle(), true);
            input.mouse.invoke(w.handle(), 0, GLFW_RELEASE, 0);
            input.moveTo(view.field);
            input.mouse.invoke(w.handle(), 0, GLFW_PRESS, 0);
            input.mouse.invoke(w.handle(), 0, GLFW_RELEASE, 0);
            input.key.invoke(w.handle(), GLFW_KEY_END, 0, GLFW_PRESS, 0);
            input.character.invoke(w.handle(), '!');
            input.focus.invoke(w.handle(), false);
            frames(w, 2);
            w.domain.execute(() -> {
                require((edited + "!").equals(view.editing.text().get()), "失焦前采集的文字被丢弃");
                require(w.page.runtime().focusRouter().focused().get() == null, "TEXT后失焦仍残留焦点");
            });
            pass("installed TEXT before focus loss -> committed draft preserved in collection order");
            input.focus.invoke(w.handle(), true);
            input.moveTo(view.field);
            input.mouse.invoke(w.handle(), 0, GLFW_PRESS, 0);
            input.mouse.invoke(w.handle(), 0, GLFW_RELEASE, 0);
            input.key.invoke(w.handle(), GLFW_KEY_END, 0, GLFW_PRESS, 0);
            input.character.invoke(w.handle(), '?');
            frames(w, 2);
            w.domain.execute(() -> require((edited + "!?").equals(view.editing.text().get()), "恢复后新手势无法编辑"));
            pass("focus gain confirms physical release -> new gesture can edit again");
            require(rasterizer.workerFailure() == null && rasterizer.failures().isEmpty(), "字体worker存在未报告失败");
            input.close.invoke(w.handle());
            require(w.shouldClose(), "native close callback未结束窗口循环");
            w.close();
            require(session.ended() && w.handle() == 0 && rasterizer.stats().terminated, "页面/窗口/font native worker未完整关闭");
            pass("installed close callback -> page ended, subscriptions/GPU released, native worker terminated, GLFW destroyed");
            window = null;
            customPageFirstClick(output.resolve("custom-page.yaml"));
            evidence.append("input provenance: key/char/mouse/scroll/focus/close are deterministic installed-callback injection; resize is actual GLFW native event. No manual interaction or IME preedit claimed.\n");
            Files.write(output.resolve("result.txt"), evidence.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println("PASS DesktopVerification completed; no resident process");
        } catch (Throwable failure) {
            primary = failure;
            evidence.append("FAIL ").append(failure).append('\n');
            Files.write(output.resolve("result.txt"), evidence.toString().getBytes(StandardCharsets.UTF_8));
            throw failure;
        } finally {
            if (window != null) {
                try { window.close(); }
                catch (Throwable cleanup) {
                    if (primary == null) throw cleanup;
                    if (primary != cleanup) primary.addSuppressed(cleanup);
                }
            }
        }
    }
    private static void customPageFirstClick(Path config) throws Exception {
        @SuppressWarnings("unchecked") final Signal<Integer>[] clicks = new Signal[1];
        final double[] nativePosition = new double[2];
        try (DesktopWindow custom = DesktopWindow.open(config, ctx -> {
            clicks[0] = ctx.domain().signal(0);
            SceneNode button = SceneButton.create(ctx, () -> "Business page", ctx.domain().signal(SceneTheme.dark()),
                    () -> clicks[0].set(clicks[0].get() + 1));
            ctx.mount(new SceneNode().size(Size.fill(1), Size.fill(1)).padding(16).add(button));
            // 业务页面装配时callbacks尚未安装。只设置真实native光标，不发送宿主MOVE。
            long window = glfwGetCurrentContext();
            int[] width = new int[1], height = new int[1], fw = new int[1], fh = new int[1];
            glfwGetWindowSize(window, width, height);
            glfwGetFramebufferSize(window, fw, fh);
            glfwSetCursorPos(window, 24.0 * width[0] / fw[0], 24.0 * height[0] / fh[0]);
            double[] x = new double[1], y = new double[1];
            glfwGetCursorPos(window, x, y);
            nativePosition[0] = x[0]; nativePosition[1] = y[0];
            require(x[0] > 0 && y[0] > 0, "native光标未设置到首击目标区域");
        })) {
            DesktopTestInput input = new DesktopTestInput(custom);
            input.mouse.invoke(custom.handle(), 0, GLFW_PRESS, 0);
            input.mouse.invoke(custom.handle(), 0, GLFW_RELEASE, 0);
            frames(custom, 1);
            custom.domain.execute(() -> require(clicks[0].get() == 1, "callbacks安装前定位的无MOVE首击未命中业务按钮"));
            pass("same-process reopen + public business ScenePage + first mouse DOWN/UP without MOVE -> native cursor="
                    + nativePosition[0] + "," + nativePosition[1] + ", business signal=1");
        }
    }
    private static PaintPlan titlePlan(DesktopWindow w, String title) {
        PaintPlanBuilder builder = new PaintPlanBuilder();
        int count = 0;
        for (PaintCommand command : w.lastPlan.commands()) {
            if (command.kind == PaintCommand.Kind.TEXT && title.equals(command.text.originalText)) {
                require(command.text.lines().stream().flatMap(l -> l.glyphs().stream()).anyMatch(g -> g.glyphId != 0), "标题没有正式非空字形");
                builder.add(command); count++;
            }
        }
        require(count > 0, "PaintPlan没有标题文字");
        return builder.build();
    }
    private static BufferedImage capture(DesktopWindow w, PaintPlan plan) {
        glClearColor(0, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT);
        w.fonts.beginPresentation(1000000);
        try (FramebufferSceneRenderer renderer = new FramebufferSceneRenderer(w.fonts)) {
            renderer.replay(plan, w.width(), w.height());
        } finally { w.fonts.endPresentation(); }
        return DesktopTestReadback.readBack(w);
    }
    private static int nonBlack(BufferedImage image) {
        int pixels = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
            if ((image.getRGB(x, y) & 0xFFFFFF) != 0) pixels++;
        return pixels;
    }

}
