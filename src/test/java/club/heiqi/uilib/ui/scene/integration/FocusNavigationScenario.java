package club.heiqi.uilib.ui.scene.integration;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayDeque;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.scene.host.SceneFramePipeline;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.host.SceneTabInputAdapter;
import club.heiqi.uilib.ui.scene.input.SceneNavigationEvent;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/** 复用真实场景、绑定、队列与Plan验证导航；固定字体仅替代字体度量边界。 */
public final class FocusNavigationScenario extends TestApplication {
    private final class Page {
        final ReactiveDomain domain;
        final Runnable drain;
        final SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost();
        ScenePageSession session;
        ScenePageContext context;
        SceneRuntime runtime;
        SceneFramePipeline pipe;
        SceneTabInputAdapter keys;
        SceneNode a, b, c, m, n, root, status;
        Signal<Boolean> bEnabled, cFocusable, cVisible;
        Signal<List<SceneNode>> order;
        Signal<Runnable> action;
        int clicks;
        Page() { this(ReactiveDomain.direct(), () -> {}); }
        Page(ReactiveDomain domain, Runnable drain) {
            this.domain = domain; this.drain = drain;
            session = ScenePageSession.open(domain, host, host, ctx -> {
                context = ctx;
                runtime = ctx.runtime();
                bEnabled = domain.signal(true); cFocusable = domain.signal(true); cVisible = domain.signal(true); action = domain.signal(() -> clicks++);
                a = new SceneNode().text("A").focusable(true).size(Size.fixed(30), Size.fixed(20)).bind(NodeBinding.value(action, SceneNode::onClick));
                b = new SceneNode().text("B").focusable(true).size(Size.fixed(30), Size.fixed(20)).bind(NodeBinding.value(bEnabled, SceneNode::enabled));
                c = new SceneNode().text("C").focusable(true).size(Size.fixed(30), Size.fixed(20)).bind(NodeBinding.value(cFocusable, SceneNode::focusable)).bind(NodeBinding.value(cVisible, SceneNode::visible));
                status = new SceneNode().bind(NodeBinding.value(runtime.focusRouter().focused(), (node,target) -> node.text("@"+name(target))));
                order = domain.signal(Arrays.asList(a,b,c,status));
                root = new SceneNode().bind(NodeBinding.children(order)); ctx.mount(root);
                keys = new SceneTabInputAdapter(event -> session.navigation(event)); keys.availability(true); keys.historyDrained(false,false,false);
            }); drain.run();
            pipe = session.pipeline();
            onClose(() -> { session.close(); drain.run(); });
            session.frame(); drain.run();
        }
        String name(SceneNode node) { return node == a ? "A" : node == b ? "B" : node == c ? "C" : node == null ? "无" : node == n ? "N" : "M"; }
        void expect(SceneNode target) {
            session.frame(); drain.run();
            domain.execute(() -> { require(runtime.focusRouter().focused().get() == target,"应为"+name(target)+"实际"+name(runtime.focusRouter().focused().get())); require(clicks == 0,"导航不能激活业务"); }); drain.run();
            require(host.last.commands().stream().anyMatch(command -> command.kind == PaintCommand.Kind.TEXT && command.text.lines().stream().anyMatch(line -> line.text.equals("@"+name(target)))),"实际Plan未反映导航焦点");
        }
        void next() { session.navigation(SceneNavigationEvent.NEXT); }
        void previous() { session.navigation(SceneNavigationEvent.PREVIOUS); }
    }
    @Override protected void runTests() throws Exception {
        scenario("前后循环与clear后首末经真实Plan", () -> {
            Page p = new Page(); p.expect(p.a); p.next(); p.expect(p.b); p.next(); p.expect(p.c); p.next(); p.expect(p.a);
            p.previous(); p.expect(p.c); p.previous(); p.expect(p.b);
            p.domain.execute(() -> p.runtime.focusRouter().clear()); p.next(); p.expect(p.a);
            p.domain.execute(() -> p.runtime.focusRouter().clear()); p.previous(); p.expect(p.c);
        });
        scenario("历史Shift与repeat适配走同一导航管线", () -> {
            Page p = new Page(); p.keys.tab(true,false); p.expect(p.b);
            p.keys.tab(true,false); p.expect(p.b); p.keys.tab(true,true); p.expect(p.c);
            p.keys.shift(true,true); p.keys.tab(true,true); p.expect(p.b);
            p.keys.tab(false,false); p.keys.shift(true,false); p.keys.shift(false,true); p.keys.tab(true,false); p.expect(p.a);
        });
        scenario("资格过滤单项无重复通知与动态逻辑重排", () -> {
            Page p = new Page(); final int[] changes = {0};
            p.domain.execute(() -> {
                Owner watcher = p.context.scope();
                p.domain.effect(watcher, () -> { p.runtime.focusRouter().focused().get(); changes[0]++; });
                p.bEnabled.set(false); p.cFocusable.set(false);
            }); int before = changes[0]; p.next(); p.expect(p.a); require(changes[0] == before,"单项不重复通知");
            p.domain.execute(() -> { p.cFocusable.set(true); p.cVisible.set(false); }); p.next(); p.expect(p.a);
            p.domain.execute(() -> p.cVisible.set(true)); p.next(); p.expect(p.c);
            p.domain.execute(() -> { p.bEnabled.set(true); p.order.set(Arrays.asList(p.c,p.b,p.a,p.status)); });
            p.next(); p.expect(p.b); p.host.width = 30; p.host.height = 240; p.previous(); p.expect(p.c);
        });
        scenario("范围内循环空内层与返回先前目标", () -> {
            Page p = new Page(); p.next(); p.next(); p.expect(p.c);
            final MountHandle[] outer = {null}, inner = {null};
            p.domain.execute(() -> {
                p.m = new SceneNode().text("M").focusable(true); p.n = new SceneNode().text("N").focusable(true);
                outer[0] = p.runtime.attach(null,new SceneNode().modal(true).initialFocus(p.m).add(p.m).add(p.n));
            }); p.expect(p.m); p.previous(); p.expect(p.n); p.next(); p.expect(p.m); p.next(); p.expect(p.n);
            p.domain.execute(() -> inner[0] = p.runtime.attach(outer[0].node(),new SceneNode().modal(true)));
            p.next(); p.expect(null); p.previous(); p.expect(null);
            p.domain.execute(() -> inner[0].detach()); p.expect(p.n);
            p.domain.execute(() -> outer[0].detach()); p.expect(p.c);
        });
        scenario("指针与导航保留交错顺序每事件结算声明", () -> {
            Page p = new Page();
            p.domain.execute(() -> p.action.set(() -> {
                require(p.runtime.focusRouter().focused().get() == p.b,"导航应先于当前click");
                p.bEnabled.set(false); p.cFocusable.set(false); p.runtime.focusRouter().clear();
            }));
            p.next(); p.session.pointer(ScenePointerEvent.syntheticActivation(1,1)); p.next(); p.expect(p.a);
        });
        scenario("失焦历史repeat及开屏持键隔离恢复后鲜活Tab", () -> {
            Page p = new Page(); p.keys.tab(true,false); p.expect(p.b);
            p.keys.availability(false); p.keys.availability(true);
            p.keys.tab(true,true); p.keys.historyDrained(true,false,false); p.keys.tab(true,false); p.expect(p.b);
            p.keys.tab(false,false); p.keys.tab(true,false); p.expect(p.b);
            p.keys.historyDrained(false,true,false); p.keys.tab(true,true); p.expect(p.b);
            p.keys.tab(true,false); p.expect(p.a); // 物理Shift基线适用于随后新Tab。
            SceneTabInputAdapter opening = new SceneTabInputAdapter(p.session::navigation); opening.availability(true);
            opening.tab(true,false); opening.historyDrained(true,false,false); opening.tab(true,true); p.expect(p.a);
            opening.tab(false,false); opening.historyDrained(false,false,false); opening.tab(true,false); p.expect(p.b);
        });
        scenario("导航Effect失败取消旧物理owner并吞剩余输入", () -> {
            Page p = new Page(); RuntimeException primary = new RuntimeException("导航业务Effect失败");
            p.domain.execute(() -> {
                Owner watcher = p.context.scope();
                p.domain.effect(watcher, () -> { if (p.runtime.focusRouter().focused().get() == p.b) throw primary; });
            });
            p.session.pointer(ScenePointerEvent.down(0,1,1)); p.next();
            p.session.pointer(ScenePointerEvent.down(1,1,1)); p.session.pointer(ScenePointerEvent.up(0,1,1)); p.previous(); p.session.pointer(ScenePointerEvent.up(1,1,1));
            try { p.session.frame(); throw new AssertionError("应保留Effect失败"); } catch (RuntimeException actual) { require(actual == primary,"原Throwable被改变"); }
            p.expect(p.b); p.domain.execute(() -> { p.runtime.dispatchClick(p.a); require(p.clicks == 1,"尾部结清后新激活可执行"); });
        });
        scenario("无旧token导航表及只读命令保护", () -> {
            Page p = new Page(); p.next(); p.expect(p.b);
            p.domain.execute(() -> {
                p.runtime.captureMount(p.b).detach(); p.runtime.attach(p.root,p.b);
                p.runtime.preparePresentation();
                require(p.runtime.focusRouter().focused().get() == null,"卸载不复活旧token");
                try { p.domain.readOnly(() -> p.runtime.focusRouter().navigate(false)); throw new AssertionError("只读应拒绝"); }
                catch (IllegalStateException expected) { require(p.runtime.focusRouter().focused().get() == null,"拒绝前不得写owner"); }
            }); p.previous(); p.expect(p.b); // 重挂载加入声明末尾；新的导航可选择新挂载。
        });
        scenario("排队域关闭跳过导航但保留第二物理键尾部", () -> {
            ArrayDeque<Runnable> queue = new ArrayDeque<>(); Runnable drain = () -> { while (!queue.isEmpty()) queue.remove().run(); };
            Page p = new Page(new ReactiveDomain(queue::add),drain);
            p.session.pointer(ScenePointerEvent.down(0,1,1)); p.next(); p.session.pointer(ScenePointerEvent.down(1,1,1)); p.session.frame(); p.pipe.close(); drain.run();
            p.domain.execute(() -> { require(p.runtime.focusRouter().focused().get() == p.a,"关闭不执行导航"); p.runtime.dispatchClick(p.a); require(p.clicks == 0,"未释放占位必须吞synthetic激活"); }); drain.run();
            // 底层合同：pipeline.close不释放runtime，另一管线须共用物理尾部权威。
            SceneFramePipeline other = new SceneFramePipeline(p.runtime,p.host,p.host);
            p.domain.execute(() -> p.context.own(other)); drain.run();
            other.pointer(ScenePointerEvent.up(0,1,1)); other.pointer(ScenePointerEvent.up(1,1,1)); other.frame(); drain.run();
            p.domain.execute(() -> { require(p.clicks == 0,"关闭尾部不click"); p.runtime.dispatchClick(p.a); require(p.clicks == 1,"共享管线结清后新激活可执行"); }); drain.run();
            try { p.next(); throw new AssertionError("关闭入口拒绝输入"); } catch (IllegalStateException expected) { }
        });
    }
    public static void main(String[] args) throws Exception { new FocusNavigationScenario().run(args); }
}
