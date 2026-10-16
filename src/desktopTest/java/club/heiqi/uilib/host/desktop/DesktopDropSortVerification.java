package club.heiqi.uilib.host.desktop;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static club.heiqi.uilib.host.desktop.DesktopTestInput.*;
import static club.heiqi.uilib.host.desktop.DesktopTestFrames.settle;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneDragDrop;
import club.heiqi.uilib.ui.scene.control.SceneDraggable;
import club.heiqi.uilib.ui.scene.control.SceneDroppable;
import club.heiqi.uilib.ui.scene.control.SceneLayer;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import javax.imageio.ImageIO;

/** 真实隐藏GLFW窗口及已安装回调；不移动系统鼠标，也不直接调用控件动作。 */
public final class DesktopDropSortVerification extends TestApplication {
    private final Path output;
    private DesktopDropSortVerification(Path output) { this.output = output; }
    public static void main(String[] args) throws Exception {
        Path output = Paths.get(args.length == 0 ? "drop-sort-report" : args[0]);
        Files.createDirectories(output);
        new DesktopDropSortVerification(output).run(args);
    }
    @Override protected void runTests() throws Exception {
        for (SceneDragDrop.Collision collision : SceneDragDrop.Collision.values())
            scenario("接收碰撞 " + collision, () -> collision(collision));
        for (SceneDragDrop.Collision collision : SceneDragDrop.Collision.values())
            for (boolean overlay : new boolean[] {false, true})
                for (SceneDraggable.Bounds bounds : SceneDraggable.Bounds.values())
                    scenario("无前置MOVE的UP " + collision + " overlay=" + overlay + " bounds=" + bounds,
                            () -> releaseWithoutMove(collision, overlay, bounds));
        scenario("独立预览的最终边缘几何与缩窗接收再确认", this::overlayFinalGeometry);
        scenario("当前接收规则、挂载与物理尾部", this::dynamicTargets);
        scenario("裁剪、被动层和模态范围", this::targetEligibility);
        scenario("接收区键盘与普通子动作", this::keyboardAndChildren);
        scenario("键盘接收规则异常取消旧手势", this::keyboardFailure);
        scenario("滚动后的候选与呈现一致", this::scrollCandidates);
        if (arguments().length < 2 || !"--droppable-only".equals(arguments()[1])) {
            new DesktopSortableVerification(output).run();
            scenario("共享广场接收与排序入口", this::gallery);
        }
    }

    private static final class DropPage {
        final SceneDragDrop.Collision collision;
        SceneDragDrop<String> scope;
        SceneDraggable.State state;
        SceneNode source, first, second, clipped, passive;
        Signal<Boolean> accept, attached, sourceVisible, modal;
        Signal<SceneDraggable.Options> options;
        int drops, clicks;
        boolean failNext;
        final RuntimeException failure = new RuntimeException("候选规则的一次真实失败");
        String last;
        ReleaseGeometryProbe releaseProbe;
        LayoutBox lastBox;
        club.heiqi.uilib.ui.scene.layout.SceneGeometryView.Entry entryAtDrop;
        SceneDraggable.Snapshot finalDrag;
        int releaseX, releaseY;
        DropPage(SceneDragDrop.Collision collision) { this.collision = collision; }
        void build(club.heiqi.uilib.ui.scene.host.ScenePageContext ctx) {
            accept = ctx.domain().signal(true); attached = ctx.domain().signal(true);
            sourceVisible = ctx.domain().signal(true); modal = ctx.domain().signal(false);
            scope = SceneDragDrop.create(ctx, collision, drop -> {
                drops++; last = drop.target; lastBox = drop.box; releaseX = drop.x; releaseY = drop.y;
                if (releaseProbe != null) entryAtDrop = releaseProbe.entry;
            });
            state = SceneDraggable.state(ctx.domain());
            SceneNode body = new SceneNode().size(Size.fixed(140), Size.fixed(110)).padding(8).gap(8).background(0xffdbeafe);
            body.add(new SceneNode().size(Size.fill(1), Size.fixed(35)).text("拖放来源"));
            body.add(SceneButton.create(ctx, () -> "内部动作", () -> SceneTheme.light(), () -> clicks++).size(Size.fill(1), Size.fixed(35)));
            SceneNode preview = new SceneNode().size(Size.fixed(140), Size.fixed(110)).text("拖动预览").background(0xffb6d4ff);
            if (releaseProbe != null) { body.control(releaseProbe); preview.control(releaseProbe); }
            options = ctx.domain().signal(scope.draggable("source", new SceneDraggable.Options().overlay(preview)
                    .onDrop(value -> finalDrag = value)));
            source = SceneDraggable.create(ctx, body, state, options);
            source.bind(NodeBinding.value(sourceVisible, SceneNode::visible));
            first = SceneDroppable.create(ctx, scope, "A", target("目标 A"),
                    () -> new SceneDroppable.Options<String>().accepts(key -> { if (failNext) { failNext = false; throw failure; } return accept.get(); }));
            second = SceneDroppable.create(ctx, scope, "B", target("目标 B"));
            SceneNode holder = new SceneNode().size(Size.fixed(140), Size.fixed(140));
            holder.bind(NodeBinding.children(() -> attached.get() ? Collections.singletonList(first) : Collections.emptyList()));
            ctx.own(first::destroy);
            SceneNode row = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural()).gap(30);
            row.add(source).add(holder).add(second);
            clipped = SceneDroppable.create(ctx, scope, "clipped", target("被裁剪目标"));
            row.add(new SceneNode().size(Size.fixed(40), Size.fixed(140)).clip(true).add(clipped));
            SceneNode passiveSource = new SceneNode().size(Size.fixed(140), Size.fixed(140));
            SceneNode layer = SceneLayer.create(ctx, () -> new SceneLayer.Policy(0, SceneLayer.Participation.PASSIVE),
                    (node, from, viewport, size) -> new LayoutBox(from.x, from.y, size.width, size.height), Collections.emptyList());
            passive = SceneDroppable.create(ctx, scope, "passive", target("被动目标"));
            layer.add(passive); passiveSource.add(layer); row.add(passiveSource);
            SceneNode modalNode = new SceneNode().size(Size.fixed(180), Size.fixed(100)).modal(true).text("当前模态范围");
            modalNode.bind(NodeBinding.value(modal, SceneNode::visible));
            ctx.mountOwned(new SceneNode().size(Size.fill(1), Size.fill(1)).padding(30).gap(24).add(row).add(modalNode));
        }
        private SceneNode target(String text) {
            return new SceneNode().size(Size.fixed(140), Size.fixed(140)).text(text).background(0xffe2e8f0);
        }
    }
    private DesktopWindow open(DropPage page, String name) throws Exception {
        DesktopWindow window = DesktopWindow.openWithServices(output.resolve(name + ".yaml"), false, services -> page::build);
        glfwSetWindowSize(window.handle(), 1100, 650); glfwSwapInterval(0); settle(window);
        require(glfwGetWindowAttrib(window.handle(), GLFW_VISIBLE) == GLFW_FALSE, "验收窗口不得可见");
        return window;
    }
    private void collision(SceneDragDrop.Collision collision) throws Exception {
        DropPage page = new DropPage(collision);
        try (DesktopWindow w = open(page, "collision-" + collision)) {
            DesktopTestInput input = new DesktopTestInput(w);
            pickup(w, input, page.source); pointAt(w, input, page.first);
            require("A".equals(over(w, page)), "未沿当前几何命中 A：" + over(w, page));
            save(w, output.resolve("droppable-" + collision + "-active.png"));
            release(w, input);
            require(page.drops == 1 && "A".equals(page.last) && !active(w, page), "正常放下未发布一次 A");
            pickup(w, input, page.source); pointAt(w, input, page.second); key(w, input, GLFW_KEY_ESCAPE); release(w, input);
            require(page.drops == 1 && !active(w, page), "取消仍投放或遗留会话");
            require(glGetError() == GL_NO_ERROR, "接收回放GL错误");
        }
    }
    private void overlayFinalGeometry() throws Exception {
        SceneDragDrop<String>[] scope = new SceneDragDrop[1];
        SceneDraggable.State[] state = new SceneDraggable.State[1];
        SceneNode[] source = new SceneNode[1], preview = new SceneNode[1];
        int[] drops = {0}, delta = {0, 0}; String[] received = {null}; LayoutBox[] receivedBox = {null};
        try (DesktopWindow w = DesktopWindow.openWithServices(output.resolve("overlay-final-geometry.yaml"), false, services -> ctx -> {
            scope[0] = SceneDragDrop.create(ctx, SceneDragDrop.Collision.INTERSECTION,
                    drop -> { drops[0]++; received[0] = drop.target; receivedBox[0] = drop.box; });
            state[0] = SceneDraggable.state(ctx.domain());
            preview[0] = new SceneNode().size(Size.fixed(140), Size.fixed(110)).text("最终共同盒").background(0xffb6d4ff);
            source[0] = SceneDraggable.create(ctx,
                    new SceneNode().size(Size.fixed(140), Size.fixed(110)).text("边缘来源").background(0xffdbeafe), state[0],
                    scope[0].draggable("edge", new SceneDraggable.Options().overlay(preview[0]).autoScroll(false)
                            .onDrop(value -> { delta[0] = value.deltaX; delta[1] = value.deltaY; })));
            ctx.mountOwned(new SceneNode().size(Size.fill(1), Size.fill(1)).padding(30).add(source[0]));
            SceneNode old = SceneDroppable.create(ctx, scope[0], "old-box",
                    new SceneNode().size(Size.fixed(40), Size.fixed(40)).background(0xffffbb88));
            SceneNode current = SceneDroppable.create(ctx, scope[0], "current-box",
                    new SceneNode().size(Size.fixed(40), Size.fixed(40)).background(0xff99ddbb));
            // 两个普通流目标有独立固定位置；缩窗后旧盒只在视口外，不能继续接收。
            ctx.mountOwned(new SceneNode().size(Size.fixed(1100), Size.fixed(650))
                    .add(new SceneNode().size(Size.fixed(1), Size.fixed(540)))
                    .add(new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fixed(1100), Size.fixed(40))
                            .add(new SceneNode().size(Size.fixed(960), Size.fixed(1))).add(old)));
            ctx.mountOwned(new SceneNode().size(Size.fixed(1100), Size.fixed(650))
                    .add(new SceneNode().size(Size.fixed(1), Size.fixed(390)))
                    .add(new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fixed(1100), Size.fixed(40))
                            .add(new SceneNode().size(Size.fixed(660), Size.fixed(1))).add(current)));
        })) {
            glfwSetWindowSize(w.handle(), 1100, 650); glfwSwapInterval(0); settle(w);
            require(glfwGetWindowAttrib(w.handle(), GLFW_VISIBLE) == GLFW_FALSE, "边缘验证必须隐藏窗口");
            DesktopTestInput input = new DesktopTestInput(w);
            LayoutBox start = geometry(w, source[0]).box;
            pickup(w, input, source[0]); move(w, input, w.width() - 2, w.height() - 2);
            LayoutBox edge = geometry(w, preview[0]).box;
            require(edge.x + edge.width == w.width() && edge.y + edge.height == w.height(), "Bounds.NONE 预览未统一限位窗口右下");
            requireDragGeometry(w, scope[0], state[0], start, edge, "old-box");
            save(w, output.resolve("overlay-final-edge.png"));
            glfwSetWindowSize(w.handle(), 800, 500); settle(w);
            LayoutBox resized = geometry(w, preview[0]).box;
            require(resized.x == 660 && resized.y == 390 && resized.width == 140 && resized.height == 110,
                    "缩窗未按独立期望更新正式预览盒");
            requireDragGeometry(w, scope[0], state[0], start, resized, "current-box");
            save(w, output.resolve("overlay-final-resized.png"));
            release(w, input);
            require(drops[0] == 1 && "current-box".equals(received[0]) && sameBox(receivedBox[0], resized),
                    "UP 没有按最终几何再确认，或命中了只在旧盒中的目标");
            require(delta[0] == resized.x - start.x && delta[1] == resized.y - start.y,
                    "UP 的有效位移与最终 Entry 不同");
            require(glGetError() == GL_NO_ERROR, "边缘场景真实 GL 出错");
            System.out.println("DROP_FINAL_GEOMETRY bounds=NONE actualCallbacks=true edge=" + edge.x + "," + edge.y
                    + " resized=" + resized.x + "," + resized.y + " scopeMatchesEntry=true target=" + received[0]
                    + " dropDelta=" + delta[0] + "," + delta[1] + " oldBoxRejected=true");
        }
    }
    private static void requireDragGeometry(DesktopWindow w, SceneDragDrop<String> scope, SceneDraggable.State state,
            LayoutBox start, LayoutBox box, String target) {
        w.domain.execute(() -> {
            SceneDragDrop.Snapshot<String> value = scope.snapshot().get();
            SceneDraggable.Snapshot drag = state.snapshot().get();
            require(value.active && sameBox(value.box, box) && target.equals(value.target), "observer 的盒/目标不等于最终共同几何");
            require(drag.deltaX == box.x - start.x && drag.deltaY == box.y - start.y, "公开拖动位移未消费最终 Entry");
        });
    }
    private static boolean sameBox(LayoutBox a, LayoutBox b) {
        return a != null && b != null && a.x == b.x && a.y == b.y && a.width == b.width && a.height == b.height;
    }

    /** 只记录正式 paint 收到的 Entry，断言不参与生产位置求解。 */
    private static final class ReleaseGeometryProbe implements club.heiqi.uilib.ui.scene.control.SceneControl {
        club.heiqi.uilib.ui.scene.layout.SceneGeometryView.Entry entry;
        public club.heiqi.uilib.ui.scene.layout.MeasuredSize measure(SceneNode node,
                club.heiqi.uilib.ui.scene.layout.Constraints constraints, club.heiqi.uilib.ui.scene.text.SceneTextMeasurer text) {
            return new club.heiqi.uilib.ui.scene.layout.MeasuredSize(0, 0);
        }
        public club.heiqi.uilib.ui.scene.layout.MeasuredSize measure(SceneNode node,
                club.heiqi.uilib.ui.scene.layout.Constraints constraints, club.heiqi.uilib.ui.scene.text.SceneTextMeasurer text,
                club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer children) {
            return club.heiqi.uilib.ui.scene.layout.SceneFlowLayout.measure(node, constraints, children);
        }
        public club.heiqi.uilib.ui.scene.layout.ChildPlacements arrange(SceneNode node, LayoutBox box,
                club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer children) {
            return club.heiqi.uilib.ui.scene.layout.SceneFlowLayout.arrange(node, box, children);
        }
        public void paint(SceneNode node, club.heiqi.uilib.ui.scene.layout.SceneGeometryView geometry,
                club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder paint) {
            entry = geometry.get(node);
            paint.add(club.heiqi.uilib.ui.scene.paint.PaintCommand.rect(entry.box, entry.clip, node.background()));
        }
    }
    private void releaseWithoutMove(SceneDragDrop.Collision collision, boolean overlay, SceneDraggable.Bounds bounds) throws Exception {
        DropPage page = new DropPage(collision); page.releaseProbe = new ReleaseGeometryProbe();
        try (DesktopWindow w = open(page, "up-only-" + collision + "-" + overlay + "-" + bounds)) {
            w.domain.execute(() -> {
                // 声明变更在输入前完成；无 overlay 时直接使用同一普通内容 Entry。
                SceneDraggable.Options configured = new SceneDraggable.Options().bounds(bounds).autoScroll(false)
                        .onDrop(value -> page.finalDrag = value);
                if (overlay) configured = configured.overlay(page.source.children().get(1).children().get(0));
                page.options.set(page.scope.draggable("source", configured));
            });
            w.frame();
            DesktopTestInput input = new DesktopTestInput(w);
            LayoutBox start = geometry(w, page.source).box, destination = geometry(w, page.second).box;
            pickup(w, input, page.source); pointAt(w, input, page.first);
            require("A".equals(over(w, page)), "无MOVE-UP反例没有先在 A 建立活动反馈");
            int x = destination.x + destination.width / 2, y = destination.y + destination.height / 2;
            int expectedX = x - 12, expectedY = y - 12;
            if (bounds == SceneDraggable.Bounds.PARENT) {
                LayoutBox parent = geometry(w, page.source.parent()).box;
                expectedY = Math.min(expectedY, parent.y + parent.height - start.height);
            }
            // 直接递交公开 UP；不调用 cursor/point，也不在 UP 前补 MOVE。
            w.page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.up(0, x, y));
            w.frame();
            require(page.drops == 1 && "B".equals(page.last), "UP 新坐标仍按旧 A 接收：" + page.last);
            require(page.lastBox.x == expectedX && page.lastBox.y == expectedY
                    && page.lastBox.width == 140 && page.lastBox.height == 110, "UP 未正式排列新坐标或边界解错误");
            require(page.entryAtDrop != null && sameBox(page.entryAtDrop.box, page.lastBox),
                    "UP observer 不是消费正式 paint 的同一最终 Entry");
            require(page.finalDrag != null && page.finalDrag.deltaX == expectedX - start.x
                    && page.finalDrag.deltaY == expectedY - start.y, "UP 有效 delta 与当前 Entry 不同");
            require(page.releaseX == x && page.releaseY == y, "POINTER 接收点未使用 UP 新坐标");
            // 宿主采集状态仍由其实际 RELEASE 清理；重复 UP 不得再投放。
            release(w, input); require(page.drops == 1 && !active(w, page), "直接 UP 后物理尾部重复提交");
            require(glGetError() == GL_NO_ERROR, "无MOVE-UP真实 GPU 回放异常");
            System.out.println("DROP_UP_ONLY collision=" + collision + " overlay=" + overlay + " bounds=" + bounds
                    + " target=" + page.last + " box=" + page.lastBox.x + "," + page.lastBox.y
                    + " delta=" + page.finalDrag.deltaX + "," + page.finalDrag.deltaY + " formalEntry=true");
        }
    }

    private void dynamicTargets() throws Exception {
        DropPage page = new DropPage(SceneDragDrop.Collision.POINTER);
        try (DesktopWindow w = open(page, "dynamic-targets")) {
            DesktopTestInput input = new DesktopTestInput(w);
            pickup(w, input, page.source); pointAt(w, input, page.first);
            w.domain.execute(() -> page.accept.set(false)); w.frame();
            require(over(w, page) == null, "静止指针下接收规则变化未失效"); release(w, input);
            require(page.drops == 0, "失格接收区仍提交");
            w.domain.execute(() -> page.accept.set(true)); w.frame();
            pickup(w, input, page.source); pointAt(w, input, page.first);
            w.domain.execute(() -> page.attached.set(false)); w.frame();
            require(over(w, page) == null, "卸载目标仍保留候选"); release(w, input);
            require(page.drops == 0, "卸载目标收到晚UP");
            w.domain.execute(() -> page.attached.set(true)); settle(w);
            pickup(w, input, page.source); pointAt(w, input, page.first); release(w, input);
            require(page.drops == 1, "重挂目标重复注册或未接收");
            pickup(w, input, page.source); pointAt(w, input, page.second);
            w.domain.execute(() -> page.sourceVisible.set(false)); w.frame();
            w.domain.execute(() -> page.sourceVisible.set(true)); w.frame(); release(w, input);
            require(page.drops == 1 && !active(w, page), "来源失格后尾部复活");
            pickup(w, input, page.source); pointAt(w, input, page.second);
            w.domain.execute(() -> page.options.set(page.scope.draggable("replacement", new SceneDraggable.Options()))); w.frame(); release(w, input);
            require(page.drops == 1 && !active(w, page), "中途替换来源把旧手势提交为新身份");
        }
    }
    private void targetEligibility() throws Exception {
        DropPage page = new DropPage(SceneDragDrop.Collision.POINTER);
        try (DesktopWindow w = open(page, "target-eligibility")) {
            DesktopTestInput input = new DesktopTestInput(w);
            LayoutBox hidden = geometry(w, page.clipped).box;
            pickup(w, input, page.source); move(w, input, hidden.x + hidden.width - 8, hidden.y + 20);
            require(over(w, page) == null, "clip 隐藏部分参与接收"); release(w, input);
            pickup(w, input, page.source); pointAt(w, input, page.passive);
            require(over(w, page) == null, "PASSIVE 层参与接收"); release(w, input);
            pickup(w, input, page.source); pointAt(w, input, page.first);
            w.domain.execute(() -> page.modal.set(true)); w.frame(); release(w, input);
            require(page.drops == 0 && !active(w, page), "模态隔离仍向后台投放");
        }
    }
    private void keyboardAndChildren() throws Exception {
        DropPage page = new DropPage(SceneDragDrop.Collision.POINTER);
        try (DesktopWindow w = open(page, "keyboard-and-children")) {
            DesktopTestInput input = new DesktopTestInput(w);
            input.click("内部动作");
            require(page.clicks == 1 && page.drops == 0 && !active(w, page), "内部按钮被父拖放劫持");
            w.domain.execute(() -> require(w.page.runtime().focusRouter().request(w.page.runtime().captureMount(page.source)), "正式来源焦点失败"));
            key(w, input, GLFW_KEY_ENTER); key(w, input, GLFW_KEY_RIGHT);
            require(over(w, page) != null && active(w, page), "方向键无法选择接收区");
            key(w, input, GLFW_KEY_ENTER);
            require(page.drops == 1 && !active(w, page), "键盘投放未确认一次");
            key(w, input, GLFW_KEY_SPACE); key(w, input, GLFW_KEY_RIGHT); key(w, input, GLFW_KEY_ESCAPE);
            require(page.drops == 1 && !active(w, page), "键盘取消误投放");
            key(w, input, GLFW_KEY_ENTER);
            for (int i = 0; i < 3; i++) key(w, input, GLFW_KEY_RIGHT);
            require("clipped".equals(over(w, page)), "部分裁剪接收区未保持键盘可达：" + over(w, page));
            key(w, input, GLFW_KEY_ESCAPE);
        }
    }
    private void keyboardFailure() throws Exception {
        DropPage page = new DropPage(SceneDragDrop.Collision.POINTER);
        try (DesktopWindow w = open(page, "keyboard-failure")) {
            DesktopTestInput input = new DesktopTestInput(w);
            w.domain.execute(() -> require(w.page.runtime().focusRouter().request(w.page.runtime().captureMount(page.source)), "正式来源焦点失败"));
            for (boolean direction : new boolean[]{false, true}) {
                if (direction) key(w, input, GLFW_KEY_ENTER);
                page.failNext = true;
                boolean failed = false;
                try { key(w, input, direction ? GLFW_KEY_RIGHT : GLFW_KEY_ENTER); }
                catch (RuntimeException error) { require(error == page.failure, "接收规则异常身份被覆盖"); failed = true; }
                require(failed && !active(w, page), "键盘handler异常未取消拖放会话");
                boolean[] dragging = {true}; w.domain.execute(() -> dragging[0] = page.state.snapshot().get().active);
                require(!dragging[0] && page.drops == 0, "失败键盘手势仍可提交");
                key(w, input, GLFW_KEY_ENTER); require(active(w, page), "异常后的Enter没有开启新手势");
                key(w, input, GLFW_KEY_ESCAPE); require(page.drops == 0, "异常旧手势被后来确认");
            }
        }
    }
    private void scrollCandidates() throws Exception {
        SceneDragDrop<Integer>[] scope = new SceneDragDrop[1]; SceneNode[] source = {null}, list = {null};
        try (DesktopWindow w = DesktopWindow.openWithServices(output.resolve("scroll-candidates.yaml"), false, services -> ctx -> {
            scope[0] = SceneDragDrop.create(ctx, SceneDragDrop.Collision.POINTER, drop -> {});
            source[0] = SceneDraggable.create(ctx, new SceneNode().size(Size.fixed(120), Size.fixed(80)).text("来源"),
                    SceneDraggable.state(ctx.domain()), scope[0].draggable(-1, new SceneDraggable.Options()
                            .overlay(new SceneNode().size(Size.fixed(120), Size.fixed(80)).text("预览"))));
            list[0] = new SceneNode().size(Size.fixed(240), Size.fixed(260)).scrollable(true).clip(true).gap(0);
            for (int i = 0; i < 20; i++) list[0].add(SceneDroppable.create(ctx, scope[0], i,
                    new SceneNode().size(Size.fixed(240), Size.fixed(60)).text("目标 " + i)));
            ctx.mountOwned(new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.fill(1)).padding(30).gap(30).add(source[0]).add(list[0]));
        })) {
            settle(w); DesktopTestInput input = new DesktopTestInput(w); pickup(w, input, source[0]);
            LayoutBox viewport = hit(geometry(w, list[0])); int x = viewport.x + 120, y = viewport.y + viewport.height - 2;
            move(w, input, x, y);
            for (int i = 0; i < 18; i++) {
                Thread.sleep(20); w.frame(); Integer expected = null;
                for (club.heiqi.uilib.ui.scene.layout.SceneGeometryView.Entry entry : geometry(w).ordered())
                    if (entry.node.text().startsWith("目标 ") && hit(entry).contains(x, y)) expected = Integer.valueOf(entry.node.text().substring(3));
                Integer[] actual = {null}; w.domain.execute(() -> actual[0] = scope[0].snapshot().get().target);
                require(java.util.Objects.equals(expected, actual[0]), "滚动后候选与当帧呈现不一致 expected=" + expected + " actual=" + actual[0]);
            }
            key(w, input, GLFW_KEY_ESCAPE); release(w, input);
        }
    }
    private void gallery() throws Exception {
        try (DesktopWindow w = DesktopWindow.openWithServices(output.resolve("gallery-drop-sort.yaml"), false,
                services -> DesktopGallery.createGallery(services, club.heiqi.uilib.testing.gallery.TestGallery.Page.HOME))) {
            glfwSetWindowSize(w.handle(), 1600, 1100); settle(w); DesktopTestInput input = new DesktopTestInput(w);
            input.click("打开接收区"); settle(w); save(w, output.resolve("gallery-droppable.png"));
            club.heiqi.uilib.ui.scene.layout.SceneGeometryView.Entry source = geometry(w).ordered().stream()
                    .filter(e -> "拾起卡片".equals(e.node.text())).findFirst().orElseThrow(() -> new AssertionError("共享接收区缺来源"));
            club.heiqi.uilib.ui.scene.layout.SceneGeometryView.Entry target = geometry(w).ordered().stream()
                    .filter(e -> "将卡片拖到这里".equals(e.node.text())).findFirst().orElseThrow(() -> new AssertionError("共享接收区缺目标"));
            LayoutBox a = hit(source), b = hit(target);
            down(w, input, a.x + a.width / 2, a.y + a.height / 2); move(w, input, b.x + b.width / 2, b.y + b.height / 2);
            save(w, output.resolve("gallery-droppable-active.png")); release(w, input);
            require(geometry(w).ordered().stream().anyMatch(e -> "已放入 接收区".equals(e.node.text())), "广场接收动作未更新业务状态");
            save(w, output.resolve("gallery-droppable-stored.png"));
            input.click("返回场景概览"); input.click("打开排序"); settle(w);
            require(geometry(w).ordered().stream().anyMatch(e -> "Sortable".equals(e.node.text())), "共享目录未进入排序页");
            save(w, output.resolve("gallery-sortable.png"));
            SceneNode first = geometry(w).ordered().stream().filter(e -> "条目 1".equals(e.node.text())).findFirst().get().node;
            SceneNode third = geometry(w).ordered().stream().filter(e -> "条目 3".equals(e.node.text())).findFirst().get().node;
            SceneNode firstRow = first, thirdRow = third;
            while (!firstRow.focusable()) firstRow = firstRow.parent();
            while (!thirdRow.focusable()) thirdRow = thirdRow.parent();
            LayoutBox initial = geometry(w, thirdRow).box, grab = geometry(w, first).box;
            down(w, input, grab.x + grab.width / 2, grab.y + grab.height / 2);
            move(w, input, initial.x + initial.width / 2, initial.y + initial.height - 3);
            long until = System.nanoTime() + 350_000_000L;
            do { w.frame(); Thread.sleep(10); } while (System.nanoTime() < until);
            require(geometry(w, thirdRow).box.y < initial.y, "共享广场拖动未实时让位");
            save(w, output.resolve("gallery-sortable-active.png")); release(w, input);
            until = System.nanoTime() + 350_000_000L;
            do { w.frame(); Thread.sleep(10); } while (System.nanoTime() < until);
            save(w, output.resolve("gallery-sortable-dropped.png"));
            input.click("返回场景概览"); input.click("打开接收区"); settle(w);
            require(geometry(w).ordered().stream().anyMatch(e -> "已放入 接收区".equals(e.node.text())), "切页丢接收状态");
            require(glGetError() == GL_NO_ERROR, "共享案例真实GL错误");
        }
    }
    private static String over(DesktopWindow w, DropPage page) {
        String[] result = {null}; w.domain.execute(() -> result[0] = page.scope.snapshot().get().target); return result[0];
    }
    private static boolean active(DesktopWindow w, DropPage page) {
        boolean[] result = {false}; w.domain.execute(() -> result[0] = page.scope.snapshot().get().active); return result[0];
    }
    static void pickup(DesktopWindow w, DesktopTestInput input, SceneNode node) {
        LayoutBox box = hit(geometry(w, node)); down(w, input, box.x + 12, box.y + 12);
    }
    static void pointAt(DesktopWindow w, DesktopTestInput input, SceneNode node) {
        LayoutBox box = hit(geometry(w, node)); move(w, input, box.x + box.width / 2, box.y + box.height / 2);
    }
    static void down(DesktopWindow w, DesktopTestInput input, int x, int y) {
        input.point(x, y); input.mouse.invoke(w.handle(), GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS, 0); w.frame();
    }
    static void move(DesktopWindow w, DesktopTestInput input, int x, int y) { input.point(x, y); w.frame(); }
    static void release(DesktopWindow w, DesktopTestInput input) {
        input.mouse.invoke(w.handle(), GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE, 0); w.frame();
    }
    static void key(DesktopWindow w, DesktopTestInput input, int key) {
        input.key.invoke(w.handle(), key, 0, GLFW_PRESS, 0); input.key.invoke(w.handle(), key, 0, GLFW_RELEASE, 0); w.frame();
    }
    static void save(DesktopWindow w, Path path) throws Exception {
        settle(w); glClearColor(1, 1, 1, 1); glClear(GL_COLOR_BUFFER_BIT);
        w.fonts.beginPresentation(1000000);
        try { w.replay(w.lastPlan); } finally { w.fonts.endPresentation(); }
        glFinish(); ImageIO.write(DesktopTestReadback.readBack(w), "png", path.toFile());
    }
}
