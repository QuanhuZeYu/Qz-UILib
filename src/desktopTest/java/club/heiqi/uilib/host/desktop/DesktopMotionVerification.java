package club.heiqi.uilib.host.desktop;

import static org.lwjgl.glfw.GLFW.*;
import static club.heiqi.uilib.host.desktop.DesktopTestInput.*;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.testing.gallery.MotionPage;
import club.heiqi.uilib.testing.gallery.TestGallery;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.motion.MotionPolicy;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;

/** 既有真实桌面宿主、发行 jar 和共享页面；输入来自已安装 GLFW 回调的自动注入。 */
public final class DesktopMotionVerification extends TestApplication {
    private final Path output;
    private final StringBuilder samples = new StringBuilder("sequence,elapsedNanos,translationX,panelHeight,dragX,dragY,dragWidth,dragHeight,parentX,parentY,parentWidth,parentHeight,scrollOffset,scrollMaximum,policy,clicks,drops\n");
    private final StringBuilder inputs = new StringBuilder("sequence,elapsedNanos,kind,x,y,source\n");
    private DesktopMotionVerification(Path output) { this.output = output; }
    public static void main(String[] args) throws Exception {
        Path output = Paths.get(args.length == 0 ? "motion-report" : args[0]);
        Files.createDirectories(output); new DesktopMotionVerification(output).run(args);
    }
    @Override protected void runTests() throws Exception {
        scenario("目录入口、主题与切页保留", this::directory);
        scenario("目标反向、阻尼切换与运动中点击", this::stateMotion);
        scenario("自然展开、文字编辑与窗口变化", this::naturalPanel);
        scenario("重排身份、结构退场与显隐反向", this::collections);
        scenario("拖拽回槽、取消与滚动边界", this::gestures);
        scenario("高速释放全段遵守父边界", this::fastRelease);
        scenario("途中关闭、减少动效与静态复用", this::policies);
        scenario("短窗口操作与离页释放", this::shortWindow);
        System.out.println("MOTION_DESKTOP sharedGallery=true realWindow=true realGlyphs=true inputSource=installed-GLFW-callback-injection readBuffer=GL_FRONT continuousFrames=true");
    }
    @Override protected void teardown() throws Exception {
        Files.write(output.resolve("frames.csv"), samples.toString().getBytes(StandardCharsets.UTF_8));
        Files.write(output.resolve("inputs.csv"), inputs.toString().getBytes(StandardCharsets.UTF_8));
        Files.write(output.resolve("source.txt"), ("开发模式：runtime-driven-development\n宿主：既有 DesktopWindow / GLFW / OpenGL\n输入：自动调用已安装 GLFW 回调；不是物理鼠标或触控板采集\n画面：GL_FRONT 实际呈现帧，PNG 在连续采样结束后编码\n").getBytes(StandardCharsets.UTF_8));
    }
    private final class Fixture implements AutoCloseable {
        final DesktopWindow window;
        final DesktopTestInput input;
        final TestGallery gallery;
        final MotionPage page;
        Fixture(String name, TestGallery.Page initial) throws Exception {
            TestGallery[] created = {null};
            window = DesktopWindow.openWithServices(output.resolve(name + ".yaml"),
                    Boolean.parseBoolean(System.getProperty("uilib.desktop.visible", "true")), services -> {
                        created[0] = DesktopGallery.createGallery(services, initial); return created[0];
                    });
            gallery = created[0]; page = gallery.motionPage();
            try {
                input = new DesktopTestInput(window);
                glfwSetWindowSize(window.handle(), 1180, 920); glfwSwapInterval(1);
                DesktopTestFrames.settle(window); advance(window, 1000);
            } catch (RuntimeException | Error failure) {
                try { window.close(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }
        @Override public void close() {
            SceneNode root = page.root(); window.close();
            require(root.runtime() == null, "窗口结束后仍保留页面挂载");
        }
    }
    private Fixture open(String name) throws Exception { return own(new Fixture(name, TestGallery.Page.MOTION)); }
    private void directory() throws Exception {
        Fixture f = own(new Fixture("directory", TestGallery.Page.HOME));
        f.input.click("打开动效"); advance(f.window, 1000); save(f.window, "01-light");
        f.input.click("切换目标"); require(read(f.window, f.page.target()), "目录动作没有进入共享动效页");
        f.input.click("切换浅深色"); advance(f.window, 300); save(f.window, "02-dark");
        f.input.click("返回场景概览"); f.input.click("打开动效");
        require(read(f.window, f.page.target()), "离页后业务状态丢失");
        require(f.page.movingButton().translationX() == 76f, "重新挂载没有直接消费当前目标");
    }
    private void stateMotion() throws Exception {
        Fixture f = open("state");
        f.input.click("切换目标"); require(read(f.window, f.page.target()), "权威状态未立即可读");
        boolean[] done = new boolean[4]; Set<Integer> positions = new HashSet<>();
        series(f, "state", 680, elapsed -> {
            positions.add(Math.round(f.page.movingButton().translationX()));
            if (elapsed >= 70 && !done[0]) {
                done[0] = true; f.input.click(geometry(f.window, f.page.movingButton()));
            }
            if (elapsed >= 220 && !done[1]) { done[1] = true; f.input.click("切换目标"); }
            if (elapsed >= 330 && !done[2]) { done[2] = true; f.input.click("阻尼：弹性"); }
            if (elapsed >= 430 && !done[3]) { done[3] = true; f.input.click("切换目标"); }
        });
        require(read(f.window, f.page.clicks()) == 1, "运动中的可见按钮未准确激活一次");
        require(positions.size() >= 3 && read(f.window, f.page.target()), "没有取得目标反向及连续位置");
        advance(f.window, 1500); require(f.page.movingButton().translationX() == 76f, "弹簧没有精确落到目标");
    }
    private void naturalPanel() throws Exception {
        Fixture f = open("panel");
        int collapsed = geometry(f.window, f.page.panel()).box.height;
        f.input.click("展开面板"); advance(f.window, 1000);
        int shortHeight = geometry(f.window, f.page.panel()).box.height;
        require(shortHeight > collapsed, "自然面板没有增加真实布局空间");
        f.input.click("使用长文案"); boolean[] edited = {false}, resized = {false}; Set<Integer> heights = new HashSet<>();
        series(f, "panel", 650, elapsed -> {
            heights.add(geometry(f.window, f.page.panel()).box.height);
            if (elapsed >= 140 && !edited[0]) {
                edited[0] = true; f.input.click(geometry(f.window, f.page.editor()));
                f.input.character.invoke(f.window.handle(), '!'); f.window.frame();
            }
            if (elapsed >= 290 && !resized[0]) { resized[0] = true; glfwSetWindowSize(f.window.handle(), 960, 760); }
        });
        advance(f.window, 1200);
        require(heights.size() >= 3, "自然内容变化没有连续尺寸反馈");
        require(geometry(f.window, f.page.panel()).box.height > shortHeight, "长文案没有影响自然高度");
        String text = read(f.window, f.page.draft().text()); require(text.contains("!"), "动画期间原生字符回调没有编辑草稿");
        SceneNode editor = f.page.editor();
        f.input.click("收起面板"); advance(f.window, 1000); f.input.click("展开面板"); advance(f.window, 1000);
        require(editor == f.page.editor() && text.equals(read(f.window, f.page.draft().text())), "展开反向丢失节点或草稿");
        save(f.window, "03-natural-panel");
    }
    private void collections() throws Exception {
        Fixture f = open("collection"); f.input.click("倒转顺序");
        SceneNode design = row(f, "选择 · 设计"); java.util.UUID original = design.id();
        boolean[] clicked = {false};
        series(f, "reorder", 450, elapsed -> {
            if (elapsed >= 70 && !clicked[0]) { clicked[0] = true; f.input.click(geometry(f.window, design)); }
        });
        require("已选择：设计".equals(read(f.window, f.page.selection())), "重排中的条目命中没有使用当前几何");
        require(row(f, "选择 · 设计").id().equals(original), "重排替换了条目身份");
        f.input.click("移除末项"); require(design.retired(), "结构删除没有结束活跃身份");
        require(geometry(f.window).get(design) == null, "退出条目仍进入有效几何");
        boolean[] restored = {false};
        series(f, "exit-return", 450, elapsed -> {
            if (elapsed >= 90 && !restored[0]) { restored[0] = true; f.input.click("恢复移除项"); }
        });
        require(!row(f, "选择 · 设计").id().equals(original), "同键返回复活了已删除身份");
        pageEnd(f);
        java.util.UUID noticeId = f.page.notice().id();
        f.input.click("隐藏提示"); require(geometry(f.window).get(f.page.noticeButton()) == null, "退场提示仍能命中");
        boolean[] shown = {false};
        series(f, "presence-reverse", 500, elapsed -> {
            if (elapsed >= 90 && !shown[0]) { shown[0] = true; f.input.click("显示提示"); }
        });
        require(f.page.notice().id().equals(noticeId), "显隐反向替换了逻辑节点");
        f.input.click("确认提示 · 0"); require(read(f.window, f.page.noticeClicks()) == 1, "显示后的提示未恢复合法操作");
        save(f.window, "04-collection");
    }
    private void gestures() throws Exception {
        Fixture f = open("gestures"); pageEnd(f);
        LayoutBox slot = releaseDrag(f, "normal-drag", true);
        Set<Integer> positions = new HashSet<>();
        series(f, "drag-release", 1100, elapsed -> {
            assertParentBounds(f); positions.add(geometry(f.window, f.page.dragContent()).box.x);
        });
        advanceWithinParent(f, 800);
        require(positions.size() >= 3 && geometry(f.window, f.page.dragContent()).box.x == slot.x, "释放没有连续回到合法槽位");
        SceneNode handle = dragHandle(f); LayoutBox grip = geometry(f.window, handle).box;
        double x = grip.x + grip.width / 2.0, y = grip.y + grip.height / 2.0;
        f.input.moveTo(handle); f.input.mouse.invoke(f.window.handle(), GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS, 0); f.window.frame();
        f.input.point(x + 45, y); f.window.frame();
        f.input.key.invoke(f.window.handle(), GLFW_KEY_ESCAPE, 0, GLFW_PRESS, 0);
        f.input.key.invoke(f.window.handle(), GLFW_KEY_ESCAPE, 0, GLFW_RELEASE, 0);
        f.input.mouse.invoke(f.window.handle(), GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE, 0); f.window.frame();
        require(read(f.window, f.page.drops()) == 1, "取消拖动提交了业务动作");
        SceneNode viewport = f.page.scrollArea().viewport(SceneNode.Axis.COLUMN);
        f.input.moveTo(viewport); f.input.scroll.invoke(f.window.handle(), 0, -100); f.window.frame();
        SceneGeometryView.Entry end = geometry(f.window, viewport);
        require(end.scrollMaximum > 0 && end.scrollOffset == end.scrollMaximum, "没有到达内层有效滚动边界");
        advance(f.window, 1000); int base = geometry(f.window, row(f, "滚动条目 1")).box.y;
        f.input.scroll.invoke(f.window.handle(), 0, -3); f.window.frame();
        Set<Integer> rebounds = new HashSet<>();
        series(f, "scroll-rebound", 900, elapsed -> {
            SceneGeometryView.Entry current = geometry(f.window, viewport);
            require(current.scrollOffset >= 0 && current.scrollOffset <= current.scrollMaximum, "回弹污染有效滚动偏移");
            rebounds.add(geometry(f.window, row(f, "滚动条目 1")).box.y);
        });
        advance(f.window, 800);
        require(rebounds.size() >= 3 && geometry(f.window, row(f, "滚动条目 1")).box.y == base, "边界没有连续回弹并归位");
        f.input.scroll.invoke(f.window.handle(), 0, 1); f.window.frame();
        require(geometry(f.window, viewport).scrollOffset < end.scrollOffset, "反向滚轮未立即作用于有效偏移");
        require(read(f.window, f.page.drops()) == 1, "释放结束重复提交业务动作"); save(f.window, "05-gestures");
    }
    private void fastRelease() throws Exception {
        Fixture f = open("fast-release"); pageEnd(f);
        LayoutBox slot = releaseDrag(f, "fast-drag", false); Set<Integer> positions = new HashSet<>();
        series(f, "fast-drag-release", 1100, elapsed -> {
            assertParentBounds(f); positions.add(geometry(f.window, f.page.dragContent()).box.x);
        });
        advanceWithinParent(f, 800);
        require(positions.size() >= 3 && geometry(f.window, f.page.dragContent()).box.x == slot.x,
                "高速释放没有连续回到槽位");
        require(read(f.window, f.page.drops()) == 1, "高速回槽重复提交业务动作"); save(f.window, "09-fast-release");
    }
    private LayoutBox releaseDrag(Fixture f, String sequence, boolean paced) {
        LayoutBox slot = geometry(f.window, f.page.dragContent()).box;
        SceneNode handle = dragHandle(f); LayoutBox grip = geometry(f.window, handle).box;
        double x = grip.x + grip.width / 2.0, y = grip.y + grip.height / 2.0;
        f.input.moveTo(handle); long start = System.nanoTime(); recordInput(sequence, start, "DOWN", x, y);
        f.input.mouse.invoke(f.window.handle(), GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS, 0); f.window.frame();
        if (paced) advance(f.window, 60);
        recordInput(sequence, start, "MOVE", x + 30, y + 8); f.input.point(x + 30, y + 8); f.window.frame();
        if (paced) advance(f.window, 60);
        recordInput(sequence, start, "MOVE", x + 70, y + 12); f.input.point(x + 70, y + 12); f.window.frame();
        require(read(f.window, f.page.dragState().snapshot()).active, "正式把手没有激活拖动");
        recordInput(sequence, start, "UP", x + 70, y + 12);
        f.input.mouse.invoke(f.window.handle(), GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE, 0); f.window.frame();
        require(read(f.window, f.page.drops()) == 1, "正常放下没有只提交一次");
        assertParentBounds(f); return slot;
    }
    private void recordInput(String sequence, long start, String kind, double x, double y) {
        inputs.append(sequence).append(',').append(System.nanoTime() - start).append(',').append(kind)
                .append(',').append(x).append(',').append(y).append(",installed-GLFW-callback-injection\n");
    }
    private static SceneNode dragHandle(Fixture f) { return f.page.dragContent().children().get(0).children().get(0); }
    private static LayoutBox parentContent(Fixture f) {
        SceneNode parent = f.page.draggable().parent(); LayoutBox box = geometry(f.window, parent).box; int padding = parent.padding();
        return new LayoutBox(box.x + padding, box.y + padding, Math.max(0, box.width - padding * 2), Math.max(0, box.height - padding * 2));
    }
    private static void assertParentBounds(Fixture f) {
        SceneGeometryView.Entry entry = geometry(f.window, f.page.dragContent()); LayoutBox child = entry.box, bounds = parentContent(f);
        require(child.x >= bounds.x && child.y >= bounds.y && child.x + child.width <= bounds.x + bounds.width
                && child.y + child.height <= bounds.y + bounds.height,
                "回槽越过声明父边界：child=[" + child.x + "," + child.y + "," + child.width + "," + child.height
                        + "] parent=[" + bounds.x + "," + bounds.y + "," + bounds.width + "," + bounds.height + "]");
        LayoutBox visible = hit(entry);
        require(visible.width == child.width && visible.height == child.height, "回槽期间卡片离开有效裁剪区");
    }
    private void policies() throws Exception {
        Fixture f = open("policies"); f.input.click("切换目标"); advance(f.window, 60); f.input.click("关闭动效");
        require(read(f.window, f.page.policy()) == MotionPolicy.OFF && f.page.movingButton().translationX() == 76f,
                "途中关闭没有立即服从当前目标");
        f.input.click("切换目标"); require(f.page.movingButton().translationX() == 0f, "关闭策略仍产生空间运动");
        f.input.click("隐藏提示");
        require(geometry(f.window).get(f.page.noticeButton()) == null, "关闭策略保留了退场输入");
        f.input.click("减少动效"); f.input.click("切换目标");
        require(read(f.window, f.page.policy()) == MotionPolicy.REDUCED && f.page.movingButton().translationX() == 76f,
                "减少动效仍产生空间往返");
        require(read(f.window, f.page.clicks()) == 0 && read(f.window, f.page.drops()) == 0, "策略切换执行了业务动作");
        f.input.click("关闭动效"); f.input.point(8, 8); DesktopTestFrames.settle(f.window); advance(f.window, 500);
        PaintPlan plan = f.window.lastPlan; f.window.frame(); f.window.frame();
        require(plan == f.window.lastPlan, "关闭且静止后没有恢复 PaintPlan 复用"); save(f.window, "06-off");
    }
    private void shortWindow() throws Exception {
        Fixture f = open("short"); glfwSetWindowSize(f.window.handle(), 560, 420); DesktopTestFrames.settle(f.window);
        f.input.click("关闭动效"); f.input.click("切换目标"); require(read(f.window, f.page.target()), "短窗口无法操作状态目标");
        f.input.click("点击 · 0"); require(read(f.window, f.page.clicks()) == 1, "短窗口的移动按钮不可操作");
        save(f.window, "07-short-state");
        f.input.click("展开面板"); f.input.click("使用长文案");
        f.input.click("添加条目"); require(read(f.window, f.page.items()).size() == 4, "短窗口无法增删集合");
        f.input.click("移除末项"); require(read(f.window, f.page.items()).size() == 3, "短窗口移除操作不可达");
        f.input.click("切换浅深色"); save(f.window, "08-short-dark");
        SceneNode root = f.page.root(); f.input.click("返回场景概览");
        require(root.runtime() == null, "离开页面没有解除挂载");
        f.input.click("打开动效"); require(read(f.window, f.page.target()), "短窗口重新进入丢失状态");
    }
    private static SceneNode row(Fixture f, String text) {
        return geometry(f.window).ordered().stream().filter(entry -> text.equals(entry.node.text()) && entry.node.clickAction() != null)
                .map(entry -> entry.node).findFirst().orElseThrow(() -> new AssertionError("没有正式条目：" + text));
    }
    private static <T> T read(DesktopWindow window, ReadableSignal<T> source) {
        final List<T> value = new ArrayList<>(); window.domain.execute(() -> value.add(source.get())); return value.get(0);
    }
    private static void advance(DesktopWindow window, long millis) {
        long end = System.nanoTime() + Math.multiplyExact(millis, 1_000_000L);
        do { require(!window.shouldClose(), "真实窗口提前结束"); window.frame(); } while (System.nanoTime() < end);
    }
    private static void advanceWithinParent(Fixture f, long millis) {
        long end = System.nanoTime() + Math.multiplyExact(millis, 1_000_000L);
        do { f.window.frame(); assertParentBounds(f); } while (System.nanoTime() < end);
    }
    private static void pageEnd(Fixture f) {
        f.input.point(8, f.window.height() / 2.0); f.input.scroll.invoke(f.window.handle(), 0, -100); f.window.frame();
        require(geometry(f.window, f.page.root()).scrollOffset == geometry(f.window, f.page.root()).scrollMaximum, "外层没有到达底部");
    }
    private void save(DesktopWindow window, String name) throws Exception {
        BufferedImage image = DesktopTestReadback.readFront(window);
        ImageIO.write(image, "png", output.resolve(name + ".png").toFile());
        DesktopGalleryEvidence.saveGeometry(window, output.resolve(name + ".geometry.json"));
    }
    private static void appendBox(StringBuilder out, LayoutBox box) {
        out.append(',').append(box == null ? -1 : box.x).append(',').append(box == null ? -1 : box.y)
                .append(',').append(box == null ? -1 : box.width).append(',').append(box == null ? -1 : box.height);
    }
    @FunctionalInterface private interface During { void frame(long elapsedMillis); }
    private static final class Captured {
        final BufferedImage image; final long nanos;
        Captured(BufferedImage image, long nanos) { this.image = image; this.nanos = nanos; }
    }
    private void series(Fixture f, String name, long millis, During action) throws Exception {
        List<Captured> frames = new ArrayList<>(); long start = System.nanoTime(), next = start;
        long end = start + Math.multiplyExact(millis, 1_000_000L);
        try {
            do {
                f.window.frame(); long now = System.nanoTime(); SceneGeometryView geometry = geometry(f.window);
                SceneGeometryView.Entry panel = geometry.get(f.page.panel()), drag = geometry.get(f.page.dragContent());
                SceneGeometryView.Entry scroll = geometry.get(f.page.scrollArea().viewport(SceneNode.Axis.COLUMN));
                samples.append(name).append(',').append(now - start).append(',').append(f.page.movingButton().translationX())
                        .append(',').append(panel == null ? -1 : panel.box.height);
                appendBox(samples, drag == null ? null : drag.box); appendBox(samples, parentContent(f));
                samples.append(',').append(scroll == null ? -1 : scroll.scrollOffset).append(',').append(scroll == null ? -1 : scroll.scrollMaximum)
                        .append(',').append(read(f.window, f.page.policy())).append(',').append(read(f.window, f.page.clicks()))
                        .append(',').append(read(f.window, f.page.drops())).append('\n');
                if (now >= next && frames.size() < 64) {
                    frames.add(new Captured(DesktopTestReadback.readFront(f.window), now - start)); next = now + 25_000_000L;
                }
                action.frame((now - start) / 1_000_000L);
            } while (System.nanoTime() < end);
        } finally {
            Path directory = output.resolve(name); Files.createDirectories(directory);
            StringBuilder index = new StringBuilder("file,elapsedNanos\n");
            for (int i = 0; i < frames.size(); i++) {
                String file = String.format(java.util.Locale.ROOT, "frame-%03d.png", i);
                ImageIO.write(frames.get(i).image, "png", directory.resolve(file).toFile());
                index.append(file).append(',').append(frames.get(i).nanos).append('\n');
            }
            Files.write(directory.resolve("index.csv"), index.toString().getBytes(StandardCharsets.UTF_8));
        }
        require(frames.size() >= 3, "连续帧取证不足：" + name);
    }
}
