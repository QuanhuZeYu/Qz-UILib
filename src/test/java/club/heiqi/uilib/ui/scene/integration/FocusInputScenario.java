package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import java.util.ArrayDeque;
import java.util.Collections;
import club.heiqi.uilib.ui.scene.host.SceneFramePipeline;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/** 实际状态/声明/布局/回放闭环；只复用既有固定字体夹具，不替代焦点路由。 */
public final class FocusInputScenario extends TestApplication {
    private final class Page {
        final ReactiveDomain domain = ReactiveDomain.direct();
        final SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost();
        SceneRuntime runtime;
        SceneFramePipeline pipe;
        SceneNode a, b, root;
        MountHandle ah, bh;
        Signal<Boolean> visible, enabled, focusable;
        Signal<String> caption;
        int clicks;
        Page() {
            domain.execute(() -> {
                runtime = new SceneRuntime(domain);
                onClose(() -> domain.execute(runtime::close));
                visible = domain.signal(true); enabled = domain.signal(true); focusable = domain.signal(true); caption = domain.signal("A");
                a = new SceneNode().focusable(true)
                    .bind(NodeBinding.value(caption, SceneNode::text))
                    .bind(NodeBinding.value(focusable, SceneNode::focusable)).size(Size.fixed(40), Size.fixed(20)).onClick(() -> { clicks++; runtime.focusRouter().request(ah); });
                b = new SceneNode().text("B").focusable(true).size(Size.fixed(40), Size.fixed(20));
                SceneNode status = new SceneNode().bind(NodeBinding.value(runtime.focusRouter().focused(),
                    (node, target) -> node.text(target == a ? "焦点A" : target == b ? "焦点B" : "无焦点")));
                root = new SceneNode().bind(NodeBinding.value(visible, SceneNode::visible))
                    .bind(NodeBinding.value(enabled, SceneNode::enabled)).add(a).add(b).add(status);
                runtime.attach(null, root);
                ah = runtime.captureMount(a); bh = runtime.captureMount(b);
                pipe = new SceneFramePipeline(runtime, host, host);
                onClose(pipe::close);
            });
            pipe.frame();
        }
        void expect(SceneNode target, String text) {
            domain.execute(() -> require(runtime.focusRouter().focused().get() == target, "焦点权威错误"));
            pipe.frame();
            require(host.last.commands().stream().anyMatch(c -> c.kind == PaintCommand.Kind.TEXT &&
                c.text.lines().stream().anyMatch(line -> line.text.equals(text))), "实际计划未显示" + text);
        }
    }
    @Override protected void runTests() throws Exception {
        scenario("请求切换清除经真实绑定与PaintPlan观察", () -> {
            Page p = new Page(); p.expect(null, "无焦点");
            p.domain.execute(() -> require(p.runtime.focusRouter().request(p.ah), "请求A应接受")); p.expect(p.a, "焦点A");
            p.domain.execute(() -> require(p.runtime.focusRouter().request(p.bh), "请求B应接受")); p.expect(p.b, "焦点B");
            require(p.clicks == 0, "请求焦点不能激活业务");
            p.host.width = 80;
            p.domain.execute(() -> p.runtime.preparePresentation());
            p.expect(p.b, "焦点B");
            p.domain.execute(() -> p.runtime.focusRouter().clear()); p.expect(null, "无焦点");
        });
        scenario("失活声明生效再恢复无frame不复活", () -> {
            for (String reason : new String[] { "visible", "enabled", "focusable" }) {
                Page p = new Page(); p.domain.execute(() -> p.runtime.focusRouter().request(p.ah));
                Signal<Boolean> source = reason.equals("visible") ? p.visible : reason.equals("enabled") ? p.enabled : p.focusable;
                p.domain.execute(() -> { source.set(false); p.runtime.preparePresentation(); require(p.runtime.focusRouter().focused().get() == null, reason + "立即释放"); });
                p.domain.execute(() -> { source.set(true); p.runtime.preparePresentation(); }); p.expect(null, "无焦点");
                p.domain.execute(() -> require(p.runtime.focusRouter().request(p.ah), "新请求恢复")); p.expect(p.a, "焦点A");
            }
        });
        scenario("模态隔离及被动内容", () -> {
            Page p = new Page(); final MountHandle[] modal = new MountHandle[1];
            p.domain.execute(() -> {
                p.runtime.focusRouter().request(p.ah); p.runtime.attach(null, new SceneNode().text("提示"));
                require(p.runtime.focusRouter().focused().get() == p.a, "被动内容不抢焦点");
                SceneNode child = new SceneNode().focusable(true); modal[0] = p.runtime.attach(null, new SceneNode().modal(true).add(child));
                require(p.runtime.focusRouter().focused().get() == null, "模态立即释放");
                require(!p.runtime.focusRouter().request(p.ah), "后台不得请求");
                require(p.runtime.focusRouter().request(p.runtime.captureMount(child)), "模态内可请求");
            });
            p.domain.execute(() -> modal[0].detach()); p.expect(null, "无焦点");
            p.domain.execute(() -> p.runtime.focusRouter().request(p.ah)); p.expect(p.a, "焦点A");
        });
        scenario("挂载代际和移动资格", () -> {
            Page p = new Page();
            p.domain.execute(() -> {
                p.runtime.focusRouter().request(p.ah);
                MountHandle parent = p.runtime.attach(null, new SceneNode()); p.ah.moveTo(parent.node());
                require(p.runtime.focusRouter().focused().get() == p.a, "有效move保持");
                MountHandle disabled = p.runtime.attach(null, new SceneNode().enabled(false)); p.ah.moveTo(disabled.node());
                require(p.runtime.focusRouter().focused().get() == null, "失活父释放");
            });
            p.domain.execute(() -> p.ah.moveTo(p.root)); p.expect(null, "无焦点");
            p.domain.execute(() -> {
                p.runtime.focusRouter().request(p.ah); p.ah.detach(); MountHandle fresh = p.runtime.attach(p.root, p.a);
                require(!p.runtime.focusRouter().request(p.ah), "同UUID旧token拒绝");
                require(p.runtime.focusRouter().request(fresh), "新token接受"); fresh.destroy();
                require(p.runtime.focusRouter().focused().get() == null, "destroy释放");
            }); p.expect(null, "无焦点");
        });
        scenario("无效请求保留合法焦点及重复通知", () -> {
            Page p = new Page(); final int[] notifications = {0};
            p.domain.execute(() -> {
                SceneRuntime other = new SceneRuntime(p.domain); onClose(() -> p.domain.execute(other::close));
                MountHandle foreign = other.attach(null, new SceneNode().focusable(true));
                p.domain.effect(p.domain.owner(), () -> { p.runtime.focusRouter().focused().get(); notifications[0]++; });
                p.runtime.focusRouter().request(p.ah);
                require(!p.runtime.focusRouter().request(foreign), "异runtime拒绝");
                require(!p.runtime.focusRouter().request(p.runtime.captureMount(p.root)), "未声明可聚焦拒绝");
            });
            int count = notifications[0]; p.domain.execute(() -> p.runtime.focusRouter().request(p.ah)); require(notifications[0] == count, "重复请求不通知");
            p.expect(p.a, "焦点A"); p.domain.execute(() -> p.runtime.focusRouter().clear()); count = notifications[0];
            p.domain.execute(() -> p.runtime.focusRouter().clear()); require(notifications[0] == count, "重复clear不通知");
            require(p.clicks == 0, "请求不激活业务");
        });
        scenario("handler受控请求与只读写保护", () -> {
            Page p = new Page(); p.pipe.pointer(ScenePointerEvent.down(0, 5, 5)); p.pipe.pointer(ScenePointerEvent.up(0, 5, 5)); p.pipe.frame();
            p.expect(p.a, "焦点A"); require(p.clicks == 1, "合法UP可请求焦点");
            p.domain.execute(() -> {
                boolean rejected = false;
                try { p.domain.readOnly(() -> p.runtime.focusRouter().request(p.bh)); } catch (IllegalStateException expected) { rejected = true; }
                require(rejected && p.runtime.focusRouter().focused().get() == p.a, "只读命令不得暗改owner");
                rejected = false;
                try { p.domain.computed(() -> p.runtime.focusRouter().request(p.bh)).get(); } catch (IllegalStateException expected) { rejected = true; }
                require(rejected && p.runtime.focusRouter().focused().get() == p.a, "纯派生不得写");
                p.caption.set("新内容"); p.runtime.preparePresentation();
            }); p.expect(p.a, "焦点A");
        });
        scenario("管线关闭保留共享焦点与runtime关闭清空", () -> {
            Page p = new Page(); p.domain.execute(() -> p.runtime.focusRouter().request(p.ah)); p.pipe.close();
            p.domain.execute(() -> require(p.runtime.focusRouter().focused().get() == p.a, "pipeline不销毁焦点"));
            p.domain.execute(p.runtime::close); p.domain.execute(() -> {
                require(p.runtime.focusRouter().focused().get() == null && !p.ah.active(), "runtime释放");
                require(!p.runtime.focusRouter().request(p.ah), "关闭后拒绝"); p.runtime.close();
            });
        });
        scenario("外部executor焦点请求关闭顺序", () -> {
            ArrayDeque<Runnable> queue = new ArrayDeque<>(); ReactiveDomain domain = new ReactiveDomain(queue::add);
            final SceneRuntime[] runtime = new SceneRuntime[1]; final MountHandle[] target = new MountHandle[1];
            Runnable drain = () -> { while (!queue.isEmpty()) queue.remove().run(); };
            domain.execute(() -> { runtime[0] = new SceneRuntime(domain); target[0] = runtime[0].attach(null, new SceneNode().focusable(true)); }); drain.run();
            onClose(() -> { domain.execute(runtime[0]::close); drain.run(); });
            domain.execute(() -> require(runtime[0].focusRouter().request(target[0]), "排队请求接受"));
            domain.execute(() -> { require(runtime[0].focusRouter().focused().get() == target[0].node(), "后操作可读前结果"); runtime[0].close(); });
            domain.execute(() -> require(runtime[0].focusRouter().focused().get() == null, "排队close释放")); drain.run();
        });
    }
    public static void main(String[] args) throws Exception { new FocusInputScenario().run(args); }
}
