package club.heiqi.uilib.host.desktop;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static club.heiqi.uilib.host.desktop.DesktopTestInput.*;
import static club.heiqi.uilib.host.desktop.DesktopTestFrames.settle;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneScrollViewport;
import club.heiqi.uilib.ui.scene.control.SceneScrollbar;
import club.heiqi.uilib.ui.scene.control.SceneTextArea;
import club.heiqi.uilib.ui.scene.control.SceneTextField;
import club.heiqi.uilib.ui.scene.control.SceneDraggable;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
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
        scenario("连续滚动不被移入指针的子区接走", this::continuousWheel);
        scenario("连续滚轮的失活、重挂载与模态范围", this::wheelLifetime);
        scenario("独立滚动条的动态视口资格", this::standalone);
        scenario("共享参考页按钮、动态内容与切页保留", this::gallery);
        scenario("首帧所属层滚动条可拖动与聚焦", this::layerFirstFrame);
        scenario("专用编辑视口共用滚动条且端点不重排", this::editorViewport);
        scenario("代码与表格内部视口共用滚动条", this::documentViewport);
        scenario("样式期限不重排与悬停范围恢复", this::configurationProjection);
        scenario("双轴拖动自动滚动独立推进", this::bothDragScroll);
        scenario("旧节点入口轴资格与动态换轴", this::legacyAdapter);
        scenario("视口端口拒绝跨运行时组合", this::runtimeIdentity);
        scenario("两个滚动条同批分页只提交目标位置", this::multiplePages);
        scenario("旧scrollable声明自动获得可拖滚动条", this::automaticViewport);
        scenario("旧设置与邮件页面自动获得滚动条", this::automaticLegacyPages);
        scenario("标准编辑与文档自动获得内部滚动条", this::automaticContent);
        scenario("自动条动态资格、接管与挂载清理", this::automaticLifetime);
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
    private void continuousWheel() throws Exception {
        final SceneScrollArea.Area[] inner = {null}, outer = {null};
        try (DesktopWindow w = DesktopWindow.openWithServices(output.resolve("continuous-wheel.yaml"), false, services -> context -> {
            inner[0] = SceneScrollArea.create(context, new SceneNode().size(Size.fill(1), Size.fixed(700)).text("内部可滚动内容"),
                    new SceneScrollArea.Options().autoHide(false));
            inner[0].node().size(Size.fill(1), Size.fixed(180));
            SceneNode content = new SceneNode().size(Size.fill(1), Size.natural()).gap(20)
                    .add(new SceneNode().size(Size.fill(1), Size.fixed(250)).text("页面顶部"))
                    .add(inner[0].node()).add(new SceneNode().size(Size.fill(1), Size.fixed(900)).text("页面后续内容"));
            outer[0] = SceneScrollArea.create(context, content, new SceneScrollArea.Options().autoHide(false));
            outer[0].node().size(Size.fixed(480), Size.fixed(400));
            context.mountOwned(new SceneNode().size(Size.fill(1), Size.fill(1)).padding(30).add(outer[0].node()));
        })) {
            glfwSetWindowSize(w.handle(), 1000, 680); glfwSwapInterval(0); settle(w); DesktopTestInput in = new DesktopTestInput(w);
            point(w, in, 180, 230);
            wheel(w, in, -6);
            require(hit(geometry(w, inner[0].node())).contains(180, 230), "滚动后内部区域没有进入固定指针位置");
            wheel(w, in, -1);
            int parentOffset = geometry(w, outer[0].viewport(SceneNode.Axis.COLUMN)).scrollOffset;
            int childOffset = geometry(w, inner[0].viewport(SceneNode.Axis.COLUMN)).scrollOffset;
            System.out.println("CONTINUOUS_WHEEL stationary=true outer=" + parentOffset + " inner=" + childOffset);
            require(parentOffset == 140 && childOffset == 0, "连续滚动被新移入指针下方的子区域接走");
            wheel(w, in, 1);
            require(geometry(w, outer[0].viewport(SceneNode.Axis.COLUMN)).scrollOffset == 120
                    && geometry(w, inner[0].viewport(SceneNode.Axis.COLUMN)).scrollOffset == 0, "连续反向重新落入子区");
            point(w, in, 182, 232); wheel(w, in, -1);
            require(geometry(w, outer[0].viewport(SceneNode.Axis.COLUMN)).scrollOffset == 140, "轻微指针抖动打断连续滚动");
            // MC会在原地滚轮之前补同坐标MOVE，不能将它当作重新选区。
            point(w, in, 182, 232); wheel(w, in, -1);
            require(geometry(w, outer[0].viewport(SceneNode.Axis.COLUMN)).scrollOffset == 160, "同坐标MOVE重选了子区");
            for (int shift = 4; shift <= 10; shift += 2) { point(w, in, 180 + shift, 230 + shift); wheel(w, in, -1); }
            require(geometry(w, outer[0].viewport(SceneNode.Axis.COLUMN)).scrollOffset == 220
                    && geometry(w, inner[0].viewport(SceneNode.Axis.COLUMN)).scrollOffset == 20, "缓慢累计移入子区不能切换目标");
            advance(w, 450); wheel(w, in, -1);
            require(geometry(w, inner[0].viewport(SceneNode.Axis.COLUMN)).scrollOffset == 40, "停顿后不能按当前位置滚动子区");
            wheel(w, in, -1000); wheel(w, in, -1);
            int innerEnd = geometry(w, inner[0].viewport(SceneNode.Axis.COLUMN)).scrollMaximum;
            require(geometry(w, outer[0].viewport(SceneNode.Axis.COLUMN)).scrollOffset == 240, "内层边界未接续外层");
            wheel(w, in, 1);
            require(geometry(w, outer[0].viewport(SceneNode.Axis.COLUMN)).scrollOffset == 220
                    && geometry(w, inner[0].viewport(SceneNode.Axis.COLUMN)).scrollOffset == innerEnd, "祖先接续后反向又被子区接走");
            point(w, in, 240, 190); wheel(w, in, 1);
            require(geometry(w, inner[0].viewport(SceneNode.Axis.COLUMN)).scrollOffset == innerEnd - 20, "主动移入子区不能立即切换");
            System.out.println("CONTINUOUS_WHEEL reverse=true jitter=true unchangedMove=true accumulatedMove=true pauseReselect=true boundaryPromotion=true intentionalMove=true");
        }
    }
    private void wheelLifetime() throws Exception {
        final SceneScrollArea.Area[] inner = {null}, outer = {null}, modal = {null};
        final Signal<Boolean>[] enabled = new Signal[1], attached = new Signal[1], showing = new Signal[1], ancestorModal = new Signal[1];
        try (DesktopWindow w = DesktopWindow.openWithServices(output.resolve("wheel-lifetime.yaml"), false, services -> context -> {
            enabled[0] = context.domain().signal(true); attached[0] = context.domain().signal(true); showing[0] = context.domain().signal(false);
            ancestorModal[0] = context.domain().signal(false);
            inner[0] = SceneScrollArea.create(context, new SceneNode().size(Size.fill(1), Size.fixed(700)), new SceneScrollArea.Options());
            inner[0].node().size(Size.fill(1), Size.fixed(180));
            SceneNode content = new SceneNode().size(Size.fill(1), Size.natural()).gap(20)
                    .add(new SceneNode().size(Size.fill(1), Size.fixed(250))).add(inner[0].node())
                    .add(new SceneNode().size(Size.fill(1), Size.fixed(900)));
            outer[0] = SceneScrollArea.create(context, content, new SceneScrollArea.Options());
            outer[0].node().size(Size.fixed(480), Size.fixed(400)).bind(NodeBinding.value(enabled[0], SceneNode::enabled));
            SceneNode holder = new SceneNode().size(Size.fixed(480), Size.fixed(400))
                    .bind(NodeBinding.value(ancestorModal[0], SceneNode::modal))
                    .bind(NodeBinding.children(() -> attached[0].get() ? java.util.Collections.singletonList(outer[0].node()) : java.util.Collections.emptyList()));
            context.own(outer[0].node()::destroy);
            modal[0] = SceneScrollArea.create(context, new SceneNode().size(Size.fill(1), Size.fixed(900)), new SceneScrollArea.Options());
            modal[0].node().size(Size.fixed(360), Size.fixed(300));
            SceneNode layer = club.heiqi.uilib.ui.scene.control.SceneLayer.create(context,
                    () -> new club.heiqi.uilib.ui.scene.control.SceneLayer.Policy(0, club.heiqi.uilib.ui.scene.control.SceneLayer.Participation.INTERACTIVE),
                    (node, sourceBox, viewport, measured) -> new LayoutBox(30, 30, measured.width, measured.height), java.util.Collections.emptyList());
            layer.size(Size.fixed(360), Size.fixed(300)).modal(true).bind(NodeBinding.value(showing[0], SceneNode::visible)).add(modal[0].node());
            context.mountOwned(new SceneNode().size(Size.fill(1), Size.fill(1)).padding(30).add(holder).add(layer));
        })) {
            glfwSetWindowSize(w.handle(), 1000, 680); glfwSwapInterval(0); settle(w); DesktopTestInput in = new DesktopTestInput(w);
            point(w, in, 180, 230); wheel(w, in, -6); wheel(w, in, -1);
            require(geometry(w, outer[0].viewport(SceneNode.Axis.COLUMN)).scrollOffset == 140, "未建立外层连续滚动");
            w.domain.execute(() -> ancestorModal[0].set(true)); w.frame(); wheel(w, in, -1);
            int enteringInner = geometry(w, inner[0].viewport(SceneNode.Axis.COLUMN)).scrollOffset;
            System.out.println("WHEEL_ANCESTOR_MODAL outer=" + geometry(w, outer[0].viewport(SceneNode.Axis.COLUMN)).scrollOffset + " inner=" + enteringInner);
            require(enteringInner == 20, "现目标祖先变模态后仍沿旧范围连续滚动");
            w.domain.execute(() -> ancestorModal[0].set(false)); w.frame(); wheel(w, in, -1);
            require(geometry(w, inner[0].viewport(SceneNode.Axis.COLUMN)).scrollOffset == 40, "退出祖先模态仍沿旧联系");
            w.domain.execute(() -> enabled[0].set(false)); w.frame(); w.domain.execute(() -> enabled[0].set(true)); w.frame();
            wheel(w, in, -1);
            require(geometry(w, outer[0].viewport(SceneNode.Axis.COLUMN)).scrollOffset == 140
                    && geometry(w, inner[0].viewport(SceneNode.Axis.COLUMN)).scrollOffset == 60, "禁用后恢复复活了原滚轮目标");
            wheel(w, in, -1000); wheel(w, in, -1);
            in.focus.invoke(w.handle(), false); w.frame(); in.focus.invoke(w.handle(), true); w.frame();
            wheel(w, in, 1);
            int innerOffset = geometry(w, inner[0].viewport(SceneNode.Axis.COLUMN)).scrollOffset;
            require(geometry(w, outer[0].viewport(SceneNode.Axis.COLUMN)).scrollOffset == 160
                    && innerOffset == 500, "窗口失焦后复活旧滚轮消费者");
            w.domain.execute(() -> attached[0].set(false)); w.frame(); w.domain.execute(() -> attached[0].set(true)); w.frame();
            wheel(w, in, -6); wheel(w, in, -1);
            require(geometry(w, outer[0].viewport(SceneNode.Axis.COLUMN)).scrollOffset == 140
                    && geometry(w, inner[0].viewport(SceneNode.Axis.COLUMN)).scrollOffset == 0, "重挂载继承了旧会话");
            w.domain.execute(() -> showing[0].set(true)); w.frame(); wheel(w, in, -1);
            require(geometry(w, modal[0].viewport(SceneNode.Axis.COLUMN)).scrollOffset == 20
                    && geometry(w, outer[0].viewport(SceneNode.Axis.COLUMN)).scrollOffset == 140, "新模态未接管滚轮或滚动泄漏后台");
            w.domain.execute(() -> showing[0].set(false)); w.frame(); wheel(w, in, -1);
            require(geometry(w, inner[0].viewport(SceneNode.Axis.COLUMN)).scrollOffset == 20, "模态结束后恢复了旧外层滚轮联系");
            System.out.println("CONTINUOUS_WHEEL_LIFETIME disabled=true focusLost=true remount=true modalExclusive=true noRevival=true");
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
    private static SceneScrollViewport.Snapshot viewport(DesktopWindow w, SceneScrollViewport port) {
        final SceneScrollViewport.Snapshot[] result = {null};
        w.domain.execute(() -> result[0] = port.snapshot(geometry(w))); return result[0];
    }
    private static void dragBarToEnd(DesktopWindow w, DesktopTestInput in, SceneNode bar, boolean horizontal) {
        LayoutBox box = geometry(w, bar).box;
        point(w, in, box.x + 5, box.y + 5); press(w, in);
        point(w, in, horizontal ? box.x + box.width + 50 : box.x + 5, horizontal ? box.y + 5 : box.y + box.height + 50);
        release(w, in);
    }
    private void editorViewport() throws Exception {
        for (boolean multiline : new boolean[] {true, false}) {
            final SceneNode[] editor = {null}, bar = {null}; final SceneEditState[] state = {null}; final SceneScrollViewport[] port = {null};
            final String text = multiline ? String.join("\n", java.util.Collections.nCopies(30, "正文与草稿保持原有领域语义"))
                    : String.join("", java.util.Collections.nCopies(35, "宽文字"));
            try (DesktopWindow w = DesktopWindow.openWithServices(output.resolve("editor-port-" + multiline + ".yaml"), false, services -> context -> {
                state[0] = new SceneEditState(context.domain(), text, multiline ? SceneEditState.Mode.MULTI_LINE : SceneEditState.Mode.SINGLE_LINE);
                editor[0] = multiline ? SceneTextArea.create(context, state[0], () -> SceneTheme.light(), () -> false, submission -> {})
                        : SceneTextField.create(context, state[0], () -> SceneTheme.light(), () -> false, submission -> {});
                editor[0].size(Size.fixed(320), Size.fixed(multiline ? 150 : 45));
                port[0] = SceneScrollViewport.of(context, editor[0], multiline ? SceneNode.Axis.COLUMN : SceneNode.Axis.ROW);
                bar[0] = SceneScrollbar.create(context, port[0], () -> new SceneScrollbar.Options().autoHide(false), () -> SceneTheme.light())
                        .size(Size.fixed(multiline ? 11 : 320), Size.fixed(multiline ? 150 : 11));
                context.mountOwned(new SceneNode().size(Size.fill(1), Size.fill(1)).padding(30)
                        .axis(multiline ? SceneNode.Axis.ROW : SceneNode.Axis.COLUMN).add(editor[0]).add(bar[0]));
            })) {
                glfwSetWindowSize(w.handle(), 1000, 680); glfwSwapInterval(0); settle(w); DesktopTestInput in = new DesktopTestInput(w);
                require(viewport(w, port[0]).maximum > 0 && !editor[0].scrollable(), "专用正文没有通过中性端口发布范围");
                w.domain.execute(() -> { port[0].moveByPixels(-Integer.MAX_VALUE); w.page.runtime().focusRouter().request(w.page.runtime().captureMount(editor[0])); }); w.frame();
                final SceneEditState.Snapshot[] before = {null}; w.domain.execute(() -> before[0] = state[0].snapshot().get());
                float paintedStart = geometry(w, editor[0]).text.lines().get(0).x;
                dragBarToEnd(w, in, bar[0], !multiline);
                SceneScrollViewport.Snapshot atEnd = viewport(w, port[0]);
                require(atEnd.offset == atEnd.maximum, "通用条未驱动专用正文末端");
                if (!multiline) {
                    float paintedEnd = geometry(w, editor[0]).text.lines().get(0).x;
                    System.out.println("FIELD_PORT_PAINT before=" + paintedStart + " after=" + paintedEnd + " offset=" + atEnd.offset);
                    require(paintedEnd == paintedStart - atEnd.offset, "端口移动没有更新单行正文共同投影");
                }
                w.domain.execute(() -> {
                    require(state[0].snapshot().get().text.equals(text) && state[0].snapshot().get().caret == before[0].caret, "拖条改变草稿或光标");
                    require(w.page.runtime().focusRouter().focused().get() == editor[0], "拖条夺走编辑焦点");
                });
                if (multiline) {
                    in.moveTo(editor[0]); SceneGeometryView stable = geometry(w); wheel(w, in, -1);
                    require(geometry(w) == stable, "字段端点保留消费仍重建共同几何");
                }
                save(w, "editor-port-" + multiline + ".png");
            }
        }
    }
    private void documentViewport() throws Exception {
        for (boolean table : new boolean[] {false, true}) {
            final club.heiqi.uilib.ui.scene.host.ScenePageContext[] context = {null}; final SceneNode[] document = {null}, bar = {null}; final SceneScrollViewport[] port = {null};
            try (DesktopWindow w = DesktopWindow.openWithServices(output.resolve("document-port-" + table + ".yaml"), false, services -> ctx -> {
                context[0] = ctx;
                String wide = String.join("", java.util.Collections.nCopies(80, "unbreakable_"));
                club.heiqi.uilib.ui.scene.text.DocumentContent.Block code = club.heiqi.uilib.ui.scene.text.DocumentContent.Block.code(
                        club.heiqi.uilib.ui.scene.text.TextContent.plain(wide), "", 0, 0);
                club.heiqi.uilib.ui.scene.text.DocumentContent.Block block = code;
                if (table) {
                    club.heiqi.uilib.ui.scene.text.TableContent.Cell cell = new club.heiqi.uilib.ui.scene.text.TableContent.Cell(
                            java.util.Collections.singletonList(code), club.heiqi.uilib.ui.scene.text.TableContent.Alignment.DEFAULT, false, 0, 0);
                    block = club.heiqi.uilib.ui.scene.text.DocumentContent.Block.table(new club.heiqi.uilib.ui.scene.text.TableContent(
                            java.util.Collections.singletonList(new club.heiqi.uilib.ui.scene.text.TableContent.Row(java.util.Collections.singletonList(cell), 0, 0))), 0, 0);
                }
                final club.heiqi.uilib.ui.scene.text.DocumentContent content = new club.heiqi.uilib.ui.scene.text.DocumentContent(java.util.Collections.singletonList(block));
                document[0] = club.heiqi.uilib.ui.scene.control.SceneDocument.create(ctx, () -> content, () -> SceneTheme.light(), null, link -> {})
                        .size(Size.fixed(320), Size.natural());
                ctx.mountOwned(new SceneNode().size(Size.fill(1), Size.fill(1)).padding(30).add(document[0]));
            })) {
                glfwSetWindowSize(w.handle(), 1000, 680); glfwSwapInterval(0); settle(w); DesktopTestInput in = new DesktopTestInput(w);
                w.domain.execute(() -> {
                    for (SceneGeometryView.Entry entry : geometry(w).ordered()) {
                        SceneScrollViewport candidate = SceneScrollViewport.of(context[0], entry.node, SceneNode.Axis.ROW);
                        SceneScrollViewport.Snapshot view = candidate.snapshot(geometry(w));
                        if (view != null && view.maximum > 0) { port[0] = candidate; break; }
                    }
                    require(port[0] != null, "真实文档未发布内部横向视口");
                    bar[0] = SceneScrollbar.create(context[0], port[0], () -> new SceneScrollbar.Options().autoHide(false), () -> SceneTheme.light())
                            .size(Size.fixed(320), Size.fixed(11)); context[0].mountOwned(bar[0]);
                }); w.frame(); dragBarToEnd(w, in, bar[0], true);
                require(viewport(w, port[0]).offset == viewport(w, port[0]).maximum, "文档内部横向位置未由通用条驱动");
                require(!port[0].node().scrollable(), "专用文档视口额外声明普通布局滚动");
                save(w, "document-port-" + table + ".png");
            }
        }
    }
    private void configurationProjection() throws Exception {
        Page page = new Page(SceneScrollArea.Axes.VERTICAL);
        try (DesktopWindow w = open(page, "configuration-projection")) {
            DesktopTestInput in = new DesktopTestInput(w); SceneGeometryView before = geometry(w);
            w.domain.execute(() -> page.options.set(page.options.get().colors(0xffe2e8f0, 0xff64748b, 0xff2563eb).timing(50, 40))); w.frame();
            require(geometry(w) == before, "仅颜色或期限变化重排内容");
            w.domain.execute(() -> page.options.set(page.options.get().autoHide(true))); w.frame();
            require(geometry(w) == before, "切换自动隐藏重排内容");
            in.moveTo(page.area.node());
            w.domain.execute(() -> page.options.set(page.options.get().axes(SceneScrollArea.Axes.NONE))); w.frame();
            w.domain.execute(() -> page.options.set(page.options.get().axes(SceneScrollArea.Axes.VERTICAL))); w.frame();
            advance(w, 200); require(thumbPainted(w), "范围恢复丢失仍在区域内的正式悬停事实");
            w.domain.execute(() -> page.options.set(page.options.get().dimensions(16, 30, 3))); w.frame();
            require(geometry(w, bar(page, SceneNode.Axis.COLUMN)).box.width == 16, "条几何变化没有正式重排");
        }
    }
    private void bothDragScroll() throws Exception {
        final SceneScrollArea.Area[] area = {null}; final SceneNode[] draggable = {null};
        try (DesktopWindow w = DesktopWindow.openWithServices(output.resolve("both-drag-scroll.yaml"), false, services -> ctx -> {
            draggable[0] = SceneDraggable.create(ctx, new SceneNode().size(Size.fixed(280), Size.fixed(180)).text("双轴拖动"), SceneDraggable.state(ctx.domain()));
            SceneNode content = new SceneNode().size(Size.fixed(850), Size.fixed(850)).add(draggable[0]);
            area[0] = SceneScrollArea.create(ctx, content, new SceneScrollArea.Options().axes(SceneScrollArea.Axes.BOTH).autoHide(false));
            area[0].node().size(Size.fixed(360), Size.fixed(240));
            ctx.mountOwned(new SceneNode().size(Size.fill(1), Size.fill(1)).padding(30).add(area[0].node()));
        })) {
            glfwSetWindowSize(w.handle(), 1000, 680); glfwSwapInterval(0); settle(w); DesktopTestInput in = new DesktopTestInput(w);
            in.moveTo(draggable[0]); press(w, in); point(w, in, 385, 150); advance(w, 180);
            int horizontal = geometry(w, area[0].viewport(SceneNode.Axis.ROW)).scrollOffset;
            require(horizontal > 0 && geometry(w, area[0].viewport(SceneNode.Axis.COLUMN)).scrollOffset == 0, "水平边缘被内层纵轴遮住");
            point(w, in, 385, 265); advance(w, 180);
            int cornerX = geometry(w, area[0].viewport(SceneNode.Axis.ROW)).scrollOffset;
            int cornerY = geometry(w, area[0].viewport(SceneNode.Axis.COLUMN)).scrollOffset;
            System.out.println("SCROLL_BOTH_DRAG beforeX=" + horizontal + " cornerX=" + cornerX + " cornerY=" + cornerY);
            require(cornerX > horizontal && cornerY > 0, "角落未独立推进双轴");
            release(w, in); System.out.println("SCROLL_BOTH_DRAG horizontal=true cornerBoth=true");
        }
    }
    private void legacyAdapter() throws Exception {
        final Signal<SceneNode.Axis>[] axis = new Signal[1]; final Signal<SceneScrollArea.Options>[] policy = new Signal[1];
        final SceneNode[] node = {null}, bar = {null};
        try (DesktopWindow w = DesktopWindow.openWithServices(output.resolve("legacy-adapter.yaml"), false, services -> ctx -> {
            axis[0] = ctx.domain().signal(SceneNode.Axis.COLUMN);
            policy[0] = ctx.domain().signal(new SceneScrollArea.Options().axes(SceneScrollArea.Axes.BOTH).autoHide(false));
            node[0] = new SceneNode().size(Size.fixed(320), Size.fixed(320)).scrollable(true)
                    .bind(NodeBinding.value(axis[0], SceneNode::axis)).add(new SceneNode().size(Size.fixed(700), Size.fixed(700)));
            bar[0] = SceneScrollbar.create(ctx, node[0], policy[0])
                    .bind(NodeBinding.value(axis[0], (self, current) -> self.size(
                            current == SceneNode.Axis.ROW ? Size.fixed(320) : Size.natural(),
                            current == SceneNode.Axis.ROW ? Size.natural() : Size.fixed(320))));
            ctx.mountOwned(new SceneNode().size(Size.fill(1), Size.fill(1)).padding(30).gap(10).add(node[0]).add(bar[0]));
        })) {
            glfwSetWindowSize(w.handle(), 1000, 680); glfwSwapInterval(0); settle(w); DesktopTestInput in = new DesktopTestInput(w);
            require(bar[0].focusable(), "旧接口初始纵轴无资格");
            w.domain.execute(() -> policy[0].set(policy[0].get().axes(SceneScrollArea.Axes.NONE))); w.frame();
            require(!bar[0].focusable(), "旧入口axes NONE仍保留资格");
            in.moveTo(bar[0]); wheel(w, in, -1); require(geometry(w, node[0]).scrollOffset == 0, "禁用轴仍通过条滚动来源");
            w.domain.execute(() -> { axis[0].set(SceneNode.Axis.ROW); policy[0].set(policy[0].get().axes(SceneScrollArea.Axes.BOTH)); }); w.frame();
            require(bar[0].focusable(), "普通Node入口换轴后永久失去视口");
            require(geometry(w, bar[0]).box.height == 11, "等范围换轴没有更新自然条尺寸");
            dragBarToEnd(w, in, bar[0], true);
            require(geometry(w, node[0]).scrollOffset == geometry(w, node[0]).scrollMaximum, "换轴后拖条未使用当前轴");
            System.out.println("SCROLL_LEGACY axesDisabled=true dynamicAxis=true");
        }
    }
    private void runtimeIdentity() throws Exception {
        final boolean[] rejected = {false}, mountRejected = {false};
        try (DesktopWindow w = DesktopWindow.openWithServices(output.resolve("runtime-identity.yaml"), false, services -> ctx -> {
            club.heiqi.uilib.ui.scene.runtime.SceneRuntime foreign = new club.heiqi.uilib.ui.scene.runtime.SceneRuntime(ctx.domain());
            try {
                SceneScrollViewport port = new SceneScrollViewport(foreign, new SceneNode().scrollable(true), SceneNode.Axis.COLUMN);
                try { SceneScrollbar.create(ctx, port, () -> new SceneScrollbar.Options(), () -> SceneTheme.light()); }
                catch (IllegalArgumentException expected) { rejected[0] = true; }
                SceneNode local = SceneScrollbar.create(ctx, SceneScrollViewport.of(ctx, new SceneNode(), SceneNode.Axis.COLUMN),
                        () -> new SceneScrollbar.Options(), () -> SceneTheme.light());
                try { foreign.attach(null, local); }
                catch (IllegalArgumentException expected) { mountRejected[0] = true; }
                ctx.own(local::destroy);
            } finally { foreign.close(); }
            ctx.mountOwned(new SceneNode().size(Size.fill(1), Size.fill(1)).text("运行时边界"));
        })) {
            settle(w); require(rejected[0] && mountRejected[0], "异runtime端口或挂载未在所属边界拒绝");
        }
    }
    private void multiplePages() throws Exception {
        final SceneNode[] viewport = {null}, first = {null}, second = {null};
        try (DesktopWindow w = DesktopWindow.openWithServices(output.resolve("multiple-pages.yaml"), false, services -> ctx -> {
            viewport[0] = new SceneNode().size(Size.fixed(320), Size.fixed(180)).scrollable(true)
                    .add(new SceneNode().size(Size.fixed(320), Size.fixed(900)));
            first[0] = SceneScrollbar.create(ctx, viewport[0]).size(Size.fixed(11), Size.fixed(180));
            second[0] = SceneScrollbar.create(ctx, viewport[0]).size(Size.fixed(11), Size.fixed(180));
            ctx.mountOwned(new SceneNode().size(Size.fill(1), Size.fill(1)).axis(SceneNode.Axis.ROW).padding(30).gap(15)
                    .add(viewport[0]).add(first[0]).add(second[0]));
        })) {
            glfwSetWindowSize(w.handle(), 1000, 680); glfwSwapInterval(0); settle(w); DesktopTestInput in = new DesktopTestInput(w);
            for (SceneNode bar : new SceneNode[] {first[0], second[0]}) {
                LayoutBox box = geometry(w, bar).box; in.enqueueAt(box.x + 5, box.y + box.height - 10);
            }
            w.frame();
            // 两次正常轨道点击已入同批，下一次输入推进直接到有限页动画末端。
            Thread.sleep(200); w.frame();
            int actual = geometry(w, viewport[0]).scrollOffset;
            System.out.println("SCROLL_MULTIPLE_PAGES offset=" + actual + " target=180");
            require(actual == 180, "同批绝对位置被重复按旧几何累加");
        }
    }
    private static SceneNode automaticBar(DesktopWindow w, SceneNode host, SceneNode.Axis axis) {
        final SceneNode[] result = {null};
        w.domain.execute(() -> {
            java.util.List<SceneNode> bars = w.page.runtime().scrollDecorations(host);
            if (!bars.isEmpty()) result[0] = bars.get(axis == SceneNode.Axis.ROW ? 0 : 1);
        });
        return result[0];
    }
    private static boolean defaultThumbPainted(DesktopWindow w, SceneNode bar) {
        LayoutBox box = geometry(w, bar).box;
        return w.lastPlan.commands().stream().anyMatch(command -> command.kind == club.heiqi.uilib.ui.scene.paint.PaintCommand.Kind.RECT
                && (command.color >>> 24) > 0 && command.box.width > 0 && command.box.height > 0
                && box.contains(command.box.x, command.box.y) && command.box.width < box.width);
    }
    private static void requireVisibleBar(DesktopWindow w, SceneNode host, SceneNode.Axis axis) {
        SceneNode bar = automaticBar(w, host, axis);
        require(bar != null && geometry(w, bar).box.width > 0 && geometry(w, bar).box.height > 0, "旧视口没有自动条几何");
        require(!bar.focusable() && bar.parent() == host && !host.children().contains(bar), "自动条污染业务子项或Tab顺序");
    }
    private void automaticViewport() throws Exception {
        final SceneNode[] host = {null}, child = {null};
        try (DesktopWindow w = DesktopWindow.openWithServices(output.resolve("automatic-viewport.yaml"), false, services -> ctx -> {
            child[0] = new SceneNode().size(Size.fixed(300), Size.fixed(900)).background(0xffe2e8f0);
            host[0] = new SceneNode().size(Size.fixed(320), Size.fixed(220)).padding(10).scrollable(true).add(child[0]);
            ctx.mountOwned(new SceneNode().size(Size.fill(1), Size.fill(1)).padding(30).add(host[0]));
        })) {
            glfwSetWindowSize(w.handle(), 1000, 680); glfwSwapInterval(0); settle(w); DesktopTestInput in = new DesktopTestInput(w);
            requireVisibleBar(w, host[0], SceneNode.Axis.COLUMN);
            require(host[0].children().size() == 1 && geometry(w, child[0]).box.height == 900, "默认条改变内容布局");
            point(w, in, 800, 600); advance(w, 950);
            require(!defaultThumbPainted(w, automaticBar(w, host[0], SceneNode.Axis.COLUMN)), "离开区域后默认条未淡出");
            in.moveTo(host[0]); w.frame(); advance(w, 950);
            require(defaultThumbPainted(w, automaticBar(w, host[0], SceneNode.Axis.COLUMN)), "纯内容区域进入/停留未保持默认条");
            dragBarToEnd(w, in, automaticBar(w, host[0], SceneNode.Axis.COLUMN), false);
            require(geometry(w, host[0]).scrollOffset == geometry(w, host[0]).scrollMaximum, "旧scrollable默认条没有驱动原偏移");
            save(w, "automatic-viewport.png");
        }
    }
    private void automaticLegacyPages() throws Exception {
        for (club.heiqi.uilib.testing.gallery.TestGallery.Page page : new club.heiqi.uilib.testing.gallery.TestGallery.Page[] {
                club.heiqi.uilib.testing.gallery.TestGallery.Page.SETTINGS, club.heiqi.uilib.testing.gallery.TestGallery.Page.MAILBOX}) {
            try (DesktopWindow w = DesktopWindow.openWithServices(output.resolve("automatic-" + page + ".yaml"), false,
                    services -> DesktopGallery.createGallery(services, page))) {
                glfwSetWindowSize(w.handle(), 1100, 760); glfwSwapInterval(0); settle(w); DesktopTestInput in = new DesktopTestInput(w);
                SceneNode host = geometry(w).ordered().stream().filter(e -> e.node.scrollable() && e.scrollMaximum > 0
                        && e.box.intersect(geometry(w).viewport()).height > 80).map(e -> e.node).findFirst().orElse(null);
                require(host != null, "旧页面未触发真实正文溢出: " + page); requireVisibleBar(w, host, host.axis());
                in.moveTo(host); w.frame(); save(w, "automatic-" + page + ".png");
                dragBarToEnd(w, in, automaticBar(w, host, host.axis()), host.axis() == SceneNode.Axis.ROW);
                require(geometry(w, host).scrollOffset == geometry(w, host).scrollMaximum, "旧页面默认条未到末端: " + page);
                System.out.println("AUTOMATIC_LEGACY_PAGE page=" + page + " maximum=" + geometry(w, host).scrollMaximum);
            }
        }
    }
    private void automaticContent() throws Exception {
        final SceneNode[] editor = {null}; final SceneEditState[] state = {null}; final SceneScrollViewport[] port = {null};
        final String text = String.join("\n", java.util.Collections.nCopies(30, "标准正文无需手工加条"));
        try (DesktopWindow w = DesktopWindow.openWithServices(output.resolve("automatic-editor.yaml"), false, services -> ctx -> {
            state[0] = new SceneEditState(ctx.domain(), text, SceneEditState.Mode.MULTI_LINE);
            editor[0] = SceneTextArea.create(ctx, state[0], () -> SceneTheme.light(), () -> false, value -> {})
                    .size(Size.fixed(320), Size.fixed(180));
            port[0] = SceneScrollViewport.of(ctx, editor[0], SceneNode.Axis.COLUMN);
            ctx.mountOwned(new SceneNode().size(Size.fill(1), Size.fill(1)).padding(30).add(editor[0]));
        })) {
            glfwSetWindowSize(w.handle(), 1000, 680); glfwSwapInterval(0); settle(w); DesktopTestInput in = new DesktopTestInput(w);
            requireVisibleBar(w, editor[0], SceneNode.Axis.COLUMN);
            w.domain.execute(() -> {
                w.page.runtime().focusRouter().request(w.page.runtime().captureMount(editor[0]));
                port[0].moveToPixels(0);
            });
            w.frame(); dragBarToEnd(w, in, automaticBar(w, editor[0], SceneNode.Axis.COLUMN), false);
            require(viewport(w, port[0]).offset == viewport(w, port[0]).maximum, "默认条没有驱动标准正文偏移");
            w.domain.execute(() -> require(state[0].snapshot().get().text.equals(text)
                    && w.page.runtime().focusRouter().focused().get() == editor[0], "默认条修改草稿或抢走编辑焦点"));
            save(w, "automatic-editor.png");
        }
        for (boolean table : new boolean[] {false, true}) {
            final SceneScrollViewport[] documentPort = {null};
            try (DesktopWindow w = DesktopWindow.openWithServices(output.resolve("automatic-document-" + table + ".yaml"), false, services -> ctx -> {
                String wide = String.join("", java.util.Collections.nCopies(80, "unbreakable_"));
                club.heiqi.uilib.ui.scene.text.DocumentContent.Block code = club.heiqi.uilib.ui.scene.text.DocumentContent.Block.code(
                        club.heiqi.uilib.ui.scene.text.TextContent.plain(wide), "", 0, 0);
                club.heiqi.uilib.ui.scene.text.DocumentContent.Block block = code;
                if (table) {
                    club.heiqi.uilib.ui.scene.text.TableContent.Cell cell = new club.heiqi.uilib.ui.scene.text.TableContent.Cell(
                            java.util.Collections.singletonList(code), club.heiqi.uilib.ui.scene.text.TableContent.Alignment.DEFAULT, false, 0, 0);
                    block = club.heiqi.uilib.ui.scene.text.DocumentContent.Block.table(new club.heiqi.uilib.ui.scene.text.TableContent(
                            java.util.Collections.singletonList(new club.heiqi.uilib.ui.scene.text.TableContent.Row(java.util.Collections.singletonList(cell), 0, 0))), 0, 0);
                }
                final club.heiqi.uilib.ui.scene.text.DocumentContent content = new club.heiqi.uilib.ui.scene.text.DocumentContent(java.util.Collections.singletonList(block));
                SceneNode document = club.heiqi.uilib.ui.scene.control.SceneDocument.create(ctx, () -> content, () -> SceneTheme.light(), null, link -> {})
                        .size(Size.fixed(320), Size.natural());
                ctx.mountOwned(new SceneNode().size(Size.fill(1), Size.fill(1)).padding(30).add(document));
            })) {
                glfwSetWindowSize(w.handle(), 1000, 680); glfwSwapInterval(0); settle(w); DesktopTestInput in = new DesktopTestInput(w);
                w.domain.execute(() -> {
                    for (SceneGeometryView.Entry entry : geometry(w).ordered()) {
                        SceneScrollViewport candidate = new SceneScrollViewport(w.page.runtime(), entry.node, SceneNode.Axis.ROW);
                        SceneScrollViewport.Snapshot view = candidate.snapshot(geometry(w));
                        if (view != null && view.maximum > 0) { documentPort[0] = candidate; break; }
                    }
                });
                require(documentPort[0] != null, "标准代码表格未发布横向范围");
                requireVisibleBar(w, documentPort[0].node(), SceneNode.Axis.ROW);
                dragBarToEnd(w, in, automaticBar(w, documentPort[0].node(), SceneNode.Axis.ROW), true);
                require(viewport(w, documentPort[0]).offset == viewport(w, documentPort[0]).maximum, "标准文档内部条未自动接入");
            }
        }
    }

    private void automaticLifetime() throws Exception {
        final SceneNode[] host = {null}; final Signal<Boolean>[] enabled = new Signal[1];
        final club.heiqi.uilib.ui.scene.host.ScenePageContext[] context = {null};
        final club.heiqi.uilib.ui.scene.runtime.MountHandle[] mount = {null};
        try (DesktopWindow w = DesktopWindow.openWithServices(output.resolve("automatic-lifetime.yaml"), false, services -> ctx -> {
            context[0] = ctx; enabled[0] = ctx.domain().signal(false);
            host[0] = new SceneNode().size(Size.fixed(320), Size.fixed(180)).bind(NodeBinding.value(enabled[0], SceneNode::scrollable))
                    .add(new SceneNode().size(Size.fixed(320), Size.fixed(900)));
            mount[0] = ctx.mount(host[0]);
        })) {
            glfwSetWindowSize(w.handle(), 1000, 680); glfwSwapInterval(0); settle(w);
            require(automaticBar(w, host[0], SceneNode.Axis.COLUMN) == null, "未声明滚动就自动转换溢出");
            w.domain.execute(() -> enabled[0].set(true)); w.frame(); requireVisibleBar(w, host[0], SceneNode.Axis.COLUMN);
            SceneNode old = automaticBar(w, host[0], SceneNode.Axis.COLUMN);
            final club.heiqi.uilib.ui.scene.runtime.MountHandle[] custom = {null}; final SceneNode[] explicit = {null};
            w.domain.execute(() -> {
                explicit[0] = SceneScrollbar.create(context[0], host[0]).size(Size.fixed(11), Size.fixed(180));
                custom[0] = context[0].mount(explicit[0]);
            }); w.frame(); require(geometry(w, old).box.width == 0, "显式条未接管默认条");
            w.domain.execute(() -> custom[0].detach()); w.frame(); requireVisibleBar(w, host[0], SceneNode.Axis.COLUMN);
            w.domain.execute(() -> mount[0].detach()); w.frame();
            require(old.runtime() == null && old.destroyed(), "卸载遗留默认条挂载资源");
            w.domain.execute(() -> mount[0] = context[0].mount(host[0])); w.frame();
            requireVisibleBar(w, host[0], SceneNode.Axis.COLUMN); require(automaticBar(w, host[0], SceneNode.Axis.COLUMN) != old, "重挂复活旧条会话");
            w.domain.execute(() -> enabled[0].set(false)); w.frame(); require(automaticBar(w, host[0], SceneNode.Axis.COLUMN) == null, "取消滚动声明后仍留默认条");
            w.domain.execute(() -> { mount[0].detach(); host[0].destroy(); explicit[0].destroy(); });
        }
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
