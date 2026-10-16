package club.heiqi.uilib.host.desktop;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static club.heiqi.uilib.host.desktop.DesktopTestInput.*;
import static club.heiqi.uilib.host.desktop.DesktopTestFrames.settle;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneScrollArea;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;

/** 真实隐藏 GLFW/GL、正式字体与已安装宿主回调；不触碰系统鼠标。 */
public final class DesktopScrollAreaVerification extends TestApplication {
    private final Path output;
    private DesktopScrollAreaVerification(Path output) { this.output = output; }
    public static void main(String[] args) throws Exception {
        Path output = Paths.get(args.length == 0 ? "scroll-area-report" : args[0]);
        Files.createDirectories(output); new DesktopScrollAreaVerification(output).run(args);
    }
    @Override protected void runTests() throws Exception {
        scenario("纵向滚轮与滑块捕获端点", this::vertical);
        scenario("横向与双轴独立位置", this::bothAxes);
        scenario("动态范围夹取与隐藏初始化", this::dynamic);
        scenario("轨道翻一页、禁止点击及键盘", this::pagingAndKeyboard);
        scenario("自动隐藏、悬停与强制轨道", this::visibility);
        scenario("隐藏禁用卸载及第二键尾部", this::cancellation);
        scenario("嵌套边界交接与双轴主轮轴", this::nested);
        scenario("独立滚动条的动态视口资格", this::standalone);
        scenario("共享参考页按钮、动态内容与切页保留", this::gallery);
        scenario("首帧所属层滚动条可拖动与聚焦", this::layerFirstFrame);
        scenario("无溢出强制轨道消费点击", this::forcedTrackClick);
    }
    private static final class Page {
        final SceneScrollArea.Axes axes;
        SceneScrollArea.Area area;
        SceneNode body, last;
        Signal<Boolean> visible, enabled, attached;
        Signal<Integer> height;
        Signal<SceneScrollArea.Options> options;
        int clicks;
        Page(SceneScrollArea.Axes axes) { this.axes = axes; }
        void build(club.heiqi.uilib.ui.scene.host.ScenePageContext context) {
            visible = context.domain().signal(true); enabled = context.domain().signal(true);
            attached = context.domain().signal(true); height = context.domain().signal(900);
            options = context.domain().signal(new SceneScrollArea.Options().axes(axes).autoHide(false)
                    .colors(0xffe2e8f0, 0xff64748b, 0xff2563eb));
            body = new SceneNode().size(axes == SceneScrollArea.Axes.VERTICAL ? Size.fill(1) : Size.fixed(850), Size.natural())
                    .padding(14).gap(10).background(0xfff8fafc);
            body.add(new SceneNode().size(Size.fill(1), Size.fixed(42)).text("滚动区 / 内容起点"));
            SceneNode spacer = new SceneNode().size(Size.fill(1), Size.fixed(900)).text("长内容");
            spacer.bind(NodeBinding.value(height, (node, value) -> node.size(Size.fill(1), Size.fixed(value)))); body.add(spacer);
            last = SceneButton.create(context, () -> "内容末尾动作", () -> SceneTheme.light(), () -> clicks++)
                    .size(Size.fill(1), Size.fixed(42)); body.add(last);
            area = SceneScrollArea.create(context, body, options);
            area.node().size(Size.fixed(360), Size.fixed(240)).bind(NodeBinding.value(visible, SceneNode::visible));
            area.node().bind(NodeBinding.value(enabled, SceneNode::enabled));
            context.own(area.node()::destroy);
            context.mountOwned(new SceneNode().size(Size.fill(1), Size.fill(1)).padding(30)
                    .bind(NodeBinding.children(() -> attached.get() ? java.util.Collections.singletonList(area.node()) : java.util.Collections.emptyList())));
        }
    }
    private DesktopWindow open(Page page, String name) throws Exception {
        DesktopWindow w = DesktopWindow.openWithServices(output.resolve(name + ".yaml"), false, services -> page::build);
        glfwSetWindowSize(w.handle(), 1000, 680); glfwSwapInterval(0); settle(w);
        require(glfwGetWindowAttrib(w.handle(), GLFW_VISIBLE) == GLFW_FALSE, "验证窗口不得可见"); return w;
    }
    private static SceneNode bar(Page page, SceneNode.Axis axis) { return page.area.node().children().get(axis == SceneNode.Axis.ROW ? 1 : 2); }
    private static int offset(DesktopWindow w, Page page, SceneNode.Axis axis) { return geometry(w, page.area.viewport(axis)).scrollOffset; }
    private static int maximum(DesktopWindow w, Page page, SceneNode.Axis axis) { return geometry(w, page.area.viewport(axis)).scrollMaximum; }
    private static void point(DesktopWindow w, DesktopTestInput in, double x, double y) { in.point(x, y); w.frame(); }
    private static void press(DesktopWindow w, DesktopTestInput in) { in.mouse.invoke(w.handle(), GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS, 0); w.frame(); }
    private static void release(DesktopWindow w, DesktopTestInput in) { in.mouse.invoke(w.handle(), GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE, 0); w.frame(); }
    private static void wheel(DesktopWindow w, DesktopTestInput in, int steps) { in.scroll.invoke(w.handle(), 0, steps); w.frame(); }
    private void vertical() throws Exception {
        Page page = new Page(SceneScrollArea.Axes.VERTICAL);
        try (DesktopWindow w = open(page, "vertical")) {
            DesktopTestInput in = new DesktopTestInput(w); in.moveTo(page.area.node()); wheel(w, in, -3);
            require(offset(w, page, SceneNode.Axis.COLUMN) == 60, "滚轮未消费正式滚动步长");
            wheel(w, in, 1000);
            LayoutBox track = geometry(w, bar(page, SceneNode.Axis.COLUMN)).box;
            point(w, in, track.x + track.width / 2.0, track.y + 8); press(w, in);
            point(w, in, track.x + 80, track.y + track.height + 100); release(w, in);
            require(offset(w, page, SceneNode.Axis.COLUMN) == maximum(w, page, SceneNode.Axis.COLUMN), "拖出轨道未到末端");
            in.click(geometry(w, page.last)); require(page.clicks == 1, "滚动后末尾动作未命中");
            require(glGetError() == GL_NO_ERROR, "滚动条GPU回放错误"); save(w, "vertical-end.png");
            point(w, in, track.x + track.width / 2.0, track.y + track.height - 8); press(w, in);
            point(w, in, track.x + track.width / 2.0, track.y - 30); release(w, in);
            require(offset(w, page, SceneNode.Axis.COLUMN) == 0, "末端反向拖动未立即生效");
        }
    }
    private void bothAxes() throws Exception {
        for (SceneScrollArea.Axes axes : new SceneScrollArea.Axes[] {SceneScrollArea.Axes.HORIZONTAL, SceneScrollArea.Axes.BOTH}) {
            Page page = new Page(axes);
            try (DesktopWindow w = open(page, "axes-" + axes)) {
                DesktopTestInput in = new DesktopTestInput(w);
                require(maximum(w, page, SceneNode.Axis.ROW) == 490, "横向范围未由内容宽度确定");
                LayoutBox x = geometry(w, bar(page, SceneNode.Axis.ROW)).box;
                point(w, in, x.x + 10, x.y + x.height / 2.0); press(w, in);
                point(w, in, x.x + x.width + 80, x.y + x.height / 2.0); release(w, in);
                require(offset(w, page, SceneNode.Axis.ROW) == 490, "横向拖动未到达末端");
                require(offset(w, page, SceneNode.Axis.COLUMN) == 0, "横向拖动同时改变纵向");
                if (axes == SceneScrollArea.Axes.BOTH) {
                    LayoutBox y = geometry(w, bar(page, SceneNode.Axis.COLUMN)).box;
                    require(x.y >= y.y + y.height, "双轴交角重叠");
                    point(w, in, y.x + y.width / 2.0, y.y + 8); press(w, in);
                    point(w, in, y.x + y.width / 2.0, y.y + y.height + 30); release(w, in);
                    require(offset(w, page, SceneNode.Axis.COLUMN) == maximum(w, page, SceneNode.Axis.COLUMN), "双轴纵向未到达末端");
                    require(offset(w, page, SceneNode.Axis.ROW) == 490, "纵向拖动丢失横向位置");
                }
                save(w, "axes-" + axes + ".png");
            }
        }
    }
    private void dynamic() throws Exception {
        Page page = new Page(SceneScrollArea.Axes.VERTICAL);
        try (DesktopWindow w = open(page, "dynamic")) {
            DesktopTestInput in = new DesktopTestInput(w); in.moveTo(page.area.node()); wheel(w, in, -1000);
            w.domain.execute(() -> page.height.set(0)); w.frame();
            require(maximum(w, page, SceneNode.Axis.COLUMN) == 0 && offset(w, page, SceneNode.Axis.COLUMN) == 0, "缩短未夹取范围");
            w.domain.execute(() -> { page.visible.set(false); page.height.set(900); }); w.frame();
            require(geometry(w).get(page.area.node()) == null, "隐藏仍有输入几何");
            w.domain.execute(() -> page.visible.set(true)); w.frame();
            require(maximum(w, page, SceneNode.Axis.COLUMN) > 0 && offset(w, page, SceneNode.Axis.COLUMN) == 0, "显示后范围不正确或复活旧越界位置");
        }
    }
    private static void key(DesktopWindow w, DesktopTestInput in, int key) {
        in.key.invoke(w.handle(), key, 0, GLFW_PRESS, 0); in.key.invoke(w.handle(), key, 0, GLFW_RELEASE, 0); w.frame();
    }
    private static void advance(DesktopWindow w, long millis) throws Exception {
        long until = System.nanoTime() + millis * 1_000_000L;
        do { w.frame(); Thread.sleep(8); } while (System.nanoTime() < until);
        w.frame();
    }
    private static boolean thumbPainted(DesktopWindow w) {
        return w.lastPlan.commands().stream().anyMatch(command -> (command.color >>> 24) > 0
                && ((command.color & 0xffffff) == 0x64748b || (command.color & 0xffffff) == 0x2563eb));
    }
    private void pagingAndKeyboard() throws Exception {
        Page page = new Page(SceneScrollArea.Axes.VERTICAL);
        try (DesktopWindow w = open(page, "paging")) {
            DesktopTestInput in = new DesktopTestInput(w); LayoutBox track = geometry(w, bar(page, SceneNode.Axis.COLUMN)).box;
            point(w, in, track.x + track.width / 2.0, track.y + track.height - 15); press(w, in); release(w, in); advance(w, 220);
            require(offset(w, page, SceneNode.Axis.COLUMN) == 240, "轨道不是翻一视口页");
            w.domain.execute(() -> page.options.set(page.options.get().clickOnTrack(false))); w.frame();
            point(w, in, track.x + track.width / 2.0, track.y + track.height - 15); press(w, in); release(w, in); advance(w, 200);
            require(offset(w, page, SceneNode.Axis.COLUMN) == 240 && page.clicks == 0, "禁止轨道翻页后仍滚动或误触内容");
            w.domain.execute(() -> require(w.page.runtime().focusRouter().request(w.page.runtime().captureMount(bar(page, SceneNode.Axis.COLUMN))), "滚动条焦点申请失败"));
            key(w, in, GLFW_KEY_PAGE_DOWN); require(offset(w, page, SceneNode.Axis.COLUMN) == 480, "PageDown未走宿主正式分页键");
            key(w, in, GLFW_KEY_HOME); require(offset(w, page, SceneNode.Axis.COLUMN) == 0, "Home未到起点");
            key(w, in, GLFW_KEY_END); require(offset(w, page, SceneNode.Axis.COLUMN) == maximum(w, page, SceneNode.Axis.COLUMN), "End未到终点");
            key(w, in, GLFW_KEY_PAGE_UP); require(offset(w, page, SceneNode.Axis.COLUMN) == maximum(w, page, SceneNode.Axis.COLUMN) - 240, "PageUp未翻一页");
            key(w, in, GLFW_KEY_HOME); key(w, in, GLFW_KEY_TAB);
            require(hit(geometry(w, page.last)).height > 0, "Tab未通过两轴祖先正常显露内容");
        }
    }
    private void visibility() throws Exception {
        Page page = new Page(SceneScrollArea.Axes.VERTICAL);
        try (DesktopWindow w = open(page, "visibility")) {
            DesktopTestInput in = new DesktopTestInput(w);
            w.domain.execute(() -> page.options.set(page.options.get().autoHide(true).timing(40, 40))); w.frame();
            in.moveTo(page.area.node()); wheel(w, in, -1); require(thumbPainted(w), "滚动未显示滑块");
            point(w, in, 950, 620); advance(w, 150); require(!thumbPainted(w), "空闲后未停止显示滑块");
            in.moveTo(page.area.node()); w.frame(); require(thumbPainted(w), "进入滚动区未显示滑块");
            advance(w, 120); require(thumbPainted(w), "悬停期间未保持滑块");
            LayoutBox track = geometry(w, bar(page, SceneNode.Axis.COLUMN)).box;
            point(w, in, track.x + track.width / 2.0, track.y + 10); press(w, in);
            point(w, in, 450, 320); advance(w, 150); require(thumbPainted(w), "捕获拖动期间错误隐藏"); release(w, in);
            point(w, in, 950, 620); advance(w, 150); require(!thumbPainted(w), "释放后未结束可见性时间线");
            w.domain.execute(() -> page.options.set(page.options.get().autoHide(false))); w.frame(); require(thumbPainted(w), "关闭自动隐藏后未常显");
            w.domain.execute(() -> { page.height.set(0); page.options.set(page.options.get().forceVisible(SceneScrollArea.Axes.VERTICAL)); }); w.frame();
            require(!thumbPainted(w) && !bar(page, SceneNode.Axis.COLUMN).focusable(), "无溢出强制轨道仍有滑块或焦点");
            require(w.lastPlan.commands().stream().anyMatch(command -> command.color == 0xffe2e8f0), "强制轨道没有绘制");
            save(w, "forced-track.png");
        }
    }
    private void cancellation() throws Exception {
        for (int variant = 0; variant < 3; variant++) {
            Page page = new Page(SceneScrollArea.Axes.VERTICAL);
            try (DesktopWindow w = open(page, "cancel-" + variant)) {
                DesktopTestInput in = new DesktopTestInput(w); LayoutBox track = geometry(w, bar(page, SceneNode.Axis.COLUMN)).box;
                point(w, in, track.x + track.width / 2.0, track.y + 8); press(w, in);
                Signal<Boolean> eligibility = variant == 0 ? page.visible : variant == 1 ? page.enabled : page.attached;
                w.domain.execute(() -> eligibility.set(false)); w.frame(); w.domain.execute(() -> eligibility.set(true)); w.frame();
                int before = offset(w, page, SceneNode.Axis.COLUMN);
                point(w, in, track.x + track.width / 2.0, track.y + track.height + 30); release(w, in);
                require(offset(w, page, SceneNode.Axis.COLUMN) == before && page.clicks == 0, "失活后复活旧拖动或误触尾部");
                point(w, in, track.x + track.width / 2.0, track.y + 8); press(w, in);
                in.mouse.invoke(w.handle(), GLFW_MOUSE_BUTTON_RIGHT, GLFW_PRESS, 0); w.frame();
                point(w, in, track.x + track.width / 2.0, track.y + track.height + 30); release(w, in);
                in.mouse.invoke(w.handle(), GLFW_MOUSE_BUTTON_RIGHT, GLFW_RELEASE, 0); w.frame();
                require(offset(w, page, SceneNode.Axis.COLUMN) == maximum(w, page, SceneNode.Axis.COLUMN) && page.clicks == 0, "第二键干扰原所有者或误触");
            }
        }
    }
    private void nested() throws Exception {
        final SceneScrollArea.Area[] inner = {null}, outer = {null};
        try (DesktopWindow w = DesktopWindow.openWithServices(output.resolve("nested.yaml"), false, services -> context -> {
            SceneNode large = new SceneNode().size(Size.fixed(700), Size.fixed(750)).text("内层双轴内容");
            inner[0] = SceneScrollArea.create(context, large, new SceneScrollArea.Options().axes(SceneScrollArea.Axes.BOTH).autoHide(false));
            inner[0].node().size(Size.fixed(350), Size.fixed(220));
            SceneNode column = new SceneNode().size(Size.fill(1), Size.natural()).gap(20).add(inner[0].node())
                    .add(new SceneNode().size(Size.fill(1), Size.fixed(800)).text("外层内容"));
            outer[0] = SceneScrollArea.create(context, column, new SceneScrollArea.Options().autoHide(false));
            outer[0].node().size(Size.fixed(480), Size.fixed(400));
            context.mountOwned(new SceneNode().size(Size.fill(1), Size.fill(1)).padding(30).add(outer[0].node()));
        })) {
            glfwSetWindowSize(w.handle(), 1000, 680); glfwSwapInterval(0); settle(w); DesktopTestInput in = new DesktopTestInput(w);
            in.moveTo(inner[0].node()); wheel(w, in, -1000);
            require(geometry(w, inner[0].viewport(SceneNode.Axis.COLUMN)).scrollOffset == 530, "内层未先到达自身边界");
            require(geometry(w, outer[0].viewport(SceneNode.Axis.COLUMN)).scrollOffset == 0, "同一滚轮同时驱动外层");
            wheel(w, in, -2);
            require(geometry(w, outer[0].viewport(SceneNode.Axis.COLUMN)).scrollOffset == 40, "内层边界后未交接外层");
            require(geometry(w, inner[0].viewport(SceneNode.Axis.ROW)).scrollOffset == 0, "纵向轮到边界后意外滚动横轴");
            save(w, "nested.png");
        }
    }
    private void standalone() throws Exception {
        final SceneNode[] viewport = {null}, scrollbar = {null};
        final Signal<Boolean>[] attached = new Signal[1];
        try (DesktopWindow w = DesktopWindow.openWithServices(output.resolve("standalone.yaml"), false, services -> context -> {
            attached[0] = context.domain().signal(true);
            viewport[0] = new SceneNode().size(Size.fill(1), Size.fixed(240)).scrollable(true)
                    .add(new SceneNode().size(Size.fill(1), Size.fixed(900)).text("独立视口"));
            scrollbar[0] = club.heiqi.uilib.ui.scene.control.SceneScrollbar.create(context, viewport[0]).size(Size.fixed(11), Size.fixed(240));
            SceneNode holder = new SceneNode().size(Size.fill(1), Size.fixed(240))
                    .bind(NodeBinding.children(() -> attached[0].get() ? java.util.Collections.singletonList(viewport[0]) : java.util.Collections.emptyList()));
            context.own(viewport[0]::destroy);
            context.mountOwned(new SceneNode().size(Size.fixed(380), Size.fixed(240)).axis(SceneNode.Axis.ROW).add(holder).add(scrollbar[0]));
        })) {
            glfwSetWindowSize(w.handle(), 1000, 680); glfwSwapInterval(0); settle(w);
            require(scrollbar[0].focusable(), "独立滚动条初始未取得视口范围");
            DesktopTestInput in = new DesktopTestInput(w); LayoutBox track = geometry(w, scrollbar[0]).box;
            point(w, in, track.x + 5, track.y + 8); press(w, in); point(w, in, track.x + 5, track.y + 500); release(w, in);
            require(geometry(w, viewport[0]).scrollOffset == 660, "独立滚动条没有推动正式视口");
            w.domain.execute(() -> attached[0].set(false)); w.frame();
            require(!scrollbar[0].focusable(), "来源视口卸载后滚动条仍可聚焦");
            w.domain.execute(() -> attached[0].set(true)); w.frame();
            require(scrollbar[0].focusable() && geometry(w, viewport[0]).scrollOffset == 0, "重挂载未按当前声明取得新范围");
        }
    }
    private void forcedTrackClick() throws Exception {
        final SceneScrollArea.Area[] area = {null}; final SceneNode[] button = {null}; final int[] clicks = {0};
        try (DesktopWindow w = DesktopWindow.openWithServices(output.resolve("forced-track-click.yaml"), false, services -> context -> {
            button[0] = SceneButton.create(context, () -> "内容按钮", () -> SceneTheme.light(), () -> clicks[0]++)
                    .size(Size.fixed(100), Size.fixed(60));
            area[0] = SceneScrollArea.create(context, button[0], new SceneScrollArea.Options().forceVisible(SceneScrollArea.Axes.VERTICAL)
                    .colors(0xffe2e8f0, 0xff64748b, 0xff2563eb));
            area[0].node().size(Size.fixed(100), Size.fixed(100));
            context.mountOwned(new SceneNode().size(Size.fill(1), Size.fill(1)).padding(30).add(area[0].node()));
        })) {
            glfwSetWindowSize(w.handle(), 1000, 680); glfwSwapInterval(0); settle(w); DesktopTestInput in = new DesktopTestInput(w);
            SceneNode scrollbar = area[0].node().children().get(2); LayoutBox track = geometry(w, scrollbar).box;
            in.enqueueAt(track.x + track.width / 2.0, track.y + 20); w.frame();
            System.out.println("FORCED_TRACK_CLICK clicks=" + clicks[0] + " maximum=" + geometry(w, area[0].viewport(SceneNode.Axis.COLUMN)).scrollMaximum);
            require(clicks[0] == 0 && !scrollbar.focusable(), "无溢出轨道点击穿透到正文或产生焦点");
            in.click(geometry(w, button[0])); require(clicks[0] == 1, "轨道之外的内容按钮不能正常操作");
        }
    }
    private void layerFirstFrame() throws Exception {
        final SceneScrollArea.Area[] area = {null};
        try (DesktopWindow w = DesktopWindow.openWithServices(output.resolve("layer-first-frame.yaml"), false, services -> context -> {
            area[0] = SceneScrollArea.create(context, new SceneNode().size(Size.fill(1), Size.fixed(900)).text("所属层长内容"),
                    new SceneScrollArea.Options().autoHide(false));
            area[0].node().size(Size.fixed(360), Size.fixed(240));
            SceneNode layer = club.heiqi.uilib.ui.scene.control.SceneLayer.create(context,
                    () -> new club.heiqi.uilib.ui.scene.control.SceneLayer.Policy(0, club.heiqi.uilib.ui.scene.control.SceneLayer.Participation.INTERACTIVE),
                    (node, sourceBox, viewport, measured) -> new LayoutBox(30, 30, measured.width, measured.height), java.util.Collections.emptyList());
            layer.size(Size.fixed(360), Size.fixed(240)).add(area[0].node());
            context.mountOwned(new SceneNode().size(Size.fixed(400), Size.fixed(300)).add(layer));
        })) {
            glfwSetWindowSize(w.handle(), 1000, 680); glfwSwapInterval(0); settle(w); DesktopTestInput in = new DesktopTestInput(w);
            SceneNode scrollbar = area[0].node().children().get(2); boolean initiallyFocusable = scrollbar.focusable();
            LayoutBox track = geometry(w, scrollbar).box;
            point(w, in, track.x + 5, track.y + 8); press(w, in);
            point(w, in, track.x + 5, track.y + track.height + 40); release(w, in);
            SceneGeometryView.Entry result = geometry(w, area[0].viewport(SceneNode.Axis.COLUMN));
            System.out.println("LAYER_SCROLL_FIRST_FRAME focusable=" + initiallyFocusable + " offset=" + result.scrollOffset + " maximum=" + result.scrollMaximum);
            require(initiallyFocusable && result.scrollOffset == result.scrollMaximum, "首帧所属层滚动条未取得同代输入资格");
            key(w, in, GLFW_KEY_TAB);
            final SceneNode[] focused = {null}; w.domain.execute(() -> focused[0] = w.page.runtime().focusRouter().focused().get());
            require(focused[0] == scrollbar, "所属层滚动条未接入Tab范围");
        }
    }
    private void gallery() throws Exception {
        try (DesktopWindow w = DesktopWindow.openWithServices(output.resolve("gallery.yaml"), false,
                services -> DesktopGallery.createGallery(services, club.heiqi.uilib.testing.gallery.TestGallery.Page.SCROLL_AREA))) {
            glfwSetWindowSize(w.handle(), 1600, 1100); glfwSwapInterval(0); settle(w); DesktopTestInput in = new DesktopTestInput(w);
            save(w, "gallery-scroll-area.png");
            clickPageButton(w, in, "清空内容");
            require(geometry(w).ordered().stream().anyMatch(entry -> "当前条目：0 / 12".equals(entry.node.text())), "示例清空未更新真实内容");
            clickPageButton(w, in, "添加一条");
            require(geometry(w).ordered().stream().anyMatch(entry -> "当前条目：1 / 12".equals(entry.node.text())), "示例清空后不能重新添加");
            clickPageButton(w, in, "显示滚动区");
            require(geometry(w).ordered().stream().anyMatch(entry -> entry.node.text().startsWith("滚动区已显示")), "隐藏初始化案例没有实际显示");
            save(w, "gallery-dynamic-hidden.png");
            clickPageButton(w, in, "返回场景概览"); clickPageButton(w, in, "打开滚动区");
            require(geometry(w).ordered().stream().anyMatch(entry -> "当前条目：1 / 12".equals(entry.node.text())), "切页未保留滚动区示例状态");
        }
    }
    private static void clickPageButton(DesktopWindow w, DesktopTestInput in, String text) {
        for (int attempt = 0; attempt < 30; attempt++) {
            SceneGeometryView.Entry entry = geometry(w).ordered().stream()
                    .filter(value -> text.equals(value.node.text()) && value.node.clickAction() != null).findFirst()
                    .orElseThrow(() -> new AssertionError("缺少示例动作：" + text));
            LayoutBox visible = hit(entry);
            if (visible.width > 0 && visible.height > 0) { in.click(entry); return; }
            in.point(w.width() - 4, w.height() / 2.0); wheel(w, in, entry.box.y < 0 ? 8 : -8);
        }
        throw new AssertionError("示例动作不能由页面滚动条显露：" + text);
    }
    private void save(DesktopWindow w, String name) throws Exception {
        settle(w); glClearColor(1, 1, 1, 1); glClear(GL_COLOR_BUFFER_BIT); w.fonts.beginPresentation(1_000_000);
        try { w.replay(w.lastPlan); } finally { w.fonts.endPresentation(); }
        ImageIO.write(DesktopTestReadback.readBack(w), "png", output.resolve(name).toFile());
        StringBuilder facts = new StringBuilder("text\tbox\tclip\toffset\tmaximum\n");
        for (SceneGeometryView.Entry entry : geometry(w).ordered())
            facts.append(entry.node.text().replace("\n", " ").replace("\t", " ")).append("\t").append(entry.box)
                    .append("\t").append(entry.clip).append("\t").append(entry.scrollOffset).append("\t").append(entry.scrollMaximum).append("\n");
        Files.write(output.resolve(name + ".geometry.tsv"), facts.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
