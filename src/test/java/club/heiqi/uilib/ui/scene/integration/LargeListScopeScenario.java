package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.host.font.freetype.LwjglFontEngine;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneLabel;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.runtime.BindingActivation;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/** 大列表范围复用的真实边界；行为验收不依赖缓存字段或探针。 */
public final class LargeListScopeScenario extends TestApplication {
    private FontTextService fonts;
    public static void main(String[] args) throws Exception { new LargeListScopeScenario().run(args); }
    @Override protected void setup() throws Exception { fonts = own(new FontTextService(new LwjglFontEngine())); }
    @Override protected void runTests() throws Exception {
        scenario("部分挂载查询与无几何动态范围立即生效", this::logicalScope);
        scenario("真实列表视口外更新自然高度滚回与业务命中", this::listInput);
        scenario("已查询模态退出及关闭不持续持有节点", this::releasedScope);
    }
    private static BindingActivation emptyBinding() {
        return new BindingActivation() {
            public void refresh() {}
            public void close() {}
        };
    }
    private void logicalScope() {
        ReactiveDomain domain = ReactiveDomain.direct();
        domain.execute(() -> {
            try (SceneRuntime runtime = new SceneRuntime(domain)) {
                Signal<Boolean> visible = domain.signal(true), enabled = domain.signal(true), modal = domain.signal(true);
                boolean[] observed = new boolean[2];
                SceneNode background = new SceneNode().focusable(true);
                background.bind((r, n, owner) -> { observed[0] = r.interactive(n); return emptyBinding(); });
                SceneNode inside = new SceneNode().focusable(true);
                inside.bind((r, n, owner) -> { observed[1] = !r.interactive(background) && r.interactive(n); return emptyBinding(); });
                SceneNode scope = new SceneNode().modal(true).add(inside)
                    .bind(NodeBinding.value(visible, SceneNode::visible))
                    .bind(NodeBinding.value(enabled, SceneNode::enabled))
                    .bind(NodeBinding.value(modal, SceneNode::modal));
                MountHandle root = runtime.attach(null, new SceneNode().add(background).add(scope));
                require(observed[0] && observed[1], "部分挂载的null范围遮蔽了后来加入的模态成员");
                runtime.preparePresentation();
                require(!runtime.interactive(background) && runtime.interactive(inside), "无几何时模态隔离失效");
                visible.set(false); runtime.preparePresentation();
                require(runtime.interactive(background) && !runtime.interactive(inside), "隐藏后残留旧模态");
                visible.set(true); enabled.set(false); runtime.preparePresentation();
                require(runtime.interactive(background) && !runtime.interactive(inside), "禁用后残留旧模态");
                enabled.set(true); modal.set(false); runtime.preparePresentation();
                require(runtime.interactive(background) && runtime.interactive(inside), "modal声明关闭后未恢复普通范围");
                modal.set(true); runtime.preparePresentation();
                require(!runtime.interactive(background), "已知null结果没有响应新的模态声明");
                SceneNode top = new SceneNode().focusable(true);
                MountHandle next = runtime.attach(null, new SceneNode().modal(true).add(top));
                runtime.preparePresentation();
                require(runtime.interactive(top) && !runtime.interactive(inside), "后挂载模态没有取得当前范围");
                next.detach(); runtime.preparePresentation();
                require(runtime.interactive(inside) && !runtime.interactive(top), "卸载后保留旧模态或丢失外层");
                root.detach();
                require(!runtime.interactive(inside), "结束挂载仍允许旧目标交互");
            }
        });
    }
    private final class Host implements SceneHost {
        int width = 340;
        PaintPlan plan;
        public int width() { return width; }
        public int height() { return 180; }
        public long metricsRevision() { return fonts.generation(); }
        public void replay(PaintPlan plan) { this.plan = plan; }
    }
    private final class ListPage implements AutoCloseable {
        final ReactiveDomain domain = ReactiveDomain.direct();
        final Host host = new Host();
        final List<Signal<String>> values = new ArrayList<>();
        final List<SceneNode> labels = new ArrayList<>(), buttons = new ArrayList<>();
        final ScenePageSession session;
        SceneNode root;
        Signal<Boolean> modalOpen;
        String clicked;
        ListPage() {
            session = ScenePageSession.open(domain, host, fonts, context -> {
                Signal<SceneTheme> theme = domain.signal(SceneTheme.light());
                root = new SceneNode().size(Size.fill(1), Size.fill(1)).scrollable(true).clip(true).gap(4);
                for (int i = 0; i < 80; i++) {
                    final int index = i;
                    Signal<String> value = domain.signal("条目 " + i + " / 材料 Café"); values.add(value);
                    SceneNode label = SceneLabel.create(context, value, theme).size(Size.fill(1), Size.natural());
                    SceneNode button = SceneButton.create(context, () -> "核对 " + index, theme,
                        () -> clicked = value.get()).size(Size.fixed(90), Size.natural());
                    labels.add(label); buttons.add(button);
                    root.add(new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural()).gap(6).add(label).add(button));
                }
                modalOpen = domain.signal(false);
                context.mount(root);
                context.mount(new SceneNode().modal(true).size(Size.fill(1), Size.fill(1))
                    .bind(NodeBinding.value(modalOpen, SceneNode::visible)));
            });
            session.frame();
        }
        void update(Runnable change) { domain.execute(change); session.frame(); }
        SceneGeometryView.Entry entry(SceneNode node) { return session.pipeline().current().geometry.get(node); }
        int maximum() { int[] result = new int[1]; domain.execute(() -> result[0] = entry(root).scrollMaximum); return result[0]; }
        void scroll(int delta) { session.pointer(ScenePointerEvent.scroll(8, 8, delta)); session.frame(); }
        void click(SceneNode button) {
            int[] point = new int[2];
            domain.execute(() -> {
                SceneGeometryView geometry = session.pipeline().current().geometry;
                SceneGeometryView.Entry entry = geometry.get(button);
                LayoutBox visible = entry.box.intersect(geometry.viewport());
                if (entry.clip != null) visible = visible.intersect(entry.clip);
                require(visible.width > 0 && visible.height > 0, "目标按钮没有可见命中区域");
                point[0] = visible.x + visible.width / 2; point[1] = visible.y + visible.height / 2;
            });
            session.pointer(ScenePointerEvent.down(0, point[0], point[1]));
            session.pointer(ScenePointerEvent.up(0, point[0], point[1])); session.frame();
        }
        boolean visibleText(String value) {
            for (PaintCommand command : host.plan.commands()) {
                if (command.kind != PaintCommand.Kind.TEXT || !value.equals(command.text.originalText)) continue;
                LayoutBox visible = command.box.intersect(new LayoutBox(0, 0, host.width(), host.height()));
                if (command.clip != null) visible = visible.intersect(command.clip);
                if (visible.width > 0 && visible.height > 0) return true;
            }
            return false;
        }
        public void close() { session.close(); }
    }
    private void listInput() {
        try (ListPage page = new ListPage()) {
            int last = page.values.size() - 1;
            int maximum = page.maximum();
            String updated = "末行最新资料 中文与 Café 需要自然换行。材料核对的当前数据继续更新，不能沿用旧高度。";
            page.update(() -> page.values.get(last).set(updated));
            require(!page.visibleText(updated), "视口外条目被错误显示到首屏");
            require(page.maximum() > maximum, "视口外自然高度未更新滚动范围");
            page.scroll(-100000);
            require(page.visibleText(updated), "滚回目标行没有显示当前文案");
            page.click(page.buttons.get(last));
            require(updated.equals(page.clicked), "可见末行业务仍使用旧数据或邻行");
            page.update(() -> page.modalOpen.set(true));
            page.clicked = null; page.click(page.buttons.get(last));
            require(page.clicked == null, "动态模态没有阻断旧列表输入");
            page.update(() -> page.modalOpen.set(false));
            page.click(page.buttons.get(last));
            require(updated.equals(page.clicked), "模态结束后旧范围结果阻断列表");
            page.host.width = 260; page.session.frame(); page.scroll(-100000);
            require(page.visibleText(updated), "宽度重排后的末行内容不可达");
            page.click(page.buttons.get(last));
            require(updated.equals(page.clicked), "宽度重排后画面与命中不一致");
            page.scroll(100000); page.scroll(-1);
            page.domain.execute(() -> require(page.session.runtime().scrollOffset(page.root) > 0, "末端额外滚轮污染了反向滚动"));
            page.scroll(100000);
            page.click(page.buttons.get(0));
            page.domain.execute(() -> require(page.values.get(0).get().equals(page.clicked), "往返滚动后首行点击对象错误"));
        }
    }
    private static WeakReference<SceneNode> queriedAndRemoved(SceneRuntime runtime) {
        SceneNode modal = new SceneNode().modal(true);
        MountHandle handle = runtime.attach(null, modal);
        runtime.preparePresentation();
        require(runtime.interactive(modal), "释放场景未实际查询当前模态");
        WeakReference<SceneNode> reference = new WeakReference<>(modal);
        handle.detach();
        return reference;
    }
    private static WeakReference<SceneNode> queriedAndClosed(SceneRuntime runtime) {
        SceneNode modal = new SceneNode().modal(true);
        runtime.attach(null, modal); runtime.preparePresentation();
        require(runtime.interactive(modal), "关闭场景未实际查询当前模态");
        WeakReference<SceneNode> reference = new WeakReference<>(modal);
        runtime.close();
        return reference;
    }
    private void releasedScope() {
        ReactiveDomain domain = ReactiveDomain.direct();
        SceneRuntime[] retained = new SceneRuntime[1];
        List<WeakReference<SceneNode>> references = new ArrayList<>();
        domain.execute(() -> {
            retained[0] = new SceneRuntime(domain);
            for (int i = 0; i < 3; i++) references.add(queriedAndRemoved(retained[0]));
        });
        for (int i = 0; i < 3; i++) { System.gc(); System.runFinalization(); }
        require(references.stream().allMatch(ref -> ref.get() == null), "仍活动运行时持有已结束模态");
        domain.execute(() -> {
            retained[0].close();
            retained[0] = new SceneRuntime(domain);
            references.add(queriedAndClosed(retained[0]));
        });
        for (int i = 0; i < 3; i++) { System.gc(); System.runFinalization(); }
        require(retained[0] != null && references.stream().allMatch(ref -> ref.get() == null),
            "调用方仍持有已关闭运行时，旧模态没有释放");
    }
}
