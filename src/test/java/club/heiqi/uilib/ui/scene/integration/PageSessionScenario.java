package club.heiqi.uilib.ui.scene.integration;

import java.util.ArrayDeque;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;

/** 正式声明入口驱动真实scene；围堵接入归属、重初始化、结束与具体失败。 */
public final class PageSessionScenario extends TestApplication {
    private final SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost();
    @Override protected void runTests() throws Exception {
        scenario("声明页面真实绑定输入与尺寸更新只构造一次", () -> {
            ReactiveDomain domain = ReactiveDomain.direct(); int[] builds = {0}, clicks = {0}; SceneNode[] root = {null};
            ScenePageSession page = ScenePageSession.open(domain, host, host, context -> {
                builds[0]++; Signal<String> text = domain.signal("初值");
                root[0] = new SceneNode().size(Size.fill(1), Size.fill(1)).bind(NodeBinding.value(text, SceneNode::text))
                    .onClick(() -> { clicks[0]++; text.set("更新"); });
                context.mount(root[0]);
            }); onClose(page::close);
            page.frame(); page.pointer(ScenePointerEvent.down(0,1,1)); page.pointer(ScenePointerEvent.up(0,1,1)); page.frame();
            domain.execute(() -> require("更新".equals(root[0].text()) && clicks[0] == 1, "正式页面未结算真实click绑定"));
            host.width = 200; page.frame();
            domain.execute(() -> require(page.pipeline().current().geometry.get(root[0]).box.width == 200 && builds[0] == 1, "尺寸更新重建声明或未重排"));
        });
        scenario("结束不回放输入外部状态可再接入", () -> {
            ReactiveDomain domain = ReactiveDomain.direct(); Signal<String>[] external = new Signal[1];
            domain.execute(() -> external[0] = domain.signal("外部")); int[] clicked = {0}, released = {0}; SceneNode[] old = {null};
            ScenePageSession first = ScenePageSession.open(domain, host, host, context -> {
                context.own(() -> released[0]++);
                old[0] = new SceneNode().size(Size.fill(1), Size.fill(1)).onClick(() -> clicked[0]++)
                    .bind(NodeBinding.value(external[0], SceneNode::text)); context.mount(old[0]);
            });first.frame(); first.pointer(ScenePointerEvent.syntheticActivation(1,1)); first.close(); first.close();
            first.frame(); first.escape(() -> clicked[0]++);
            domain.execute(() -> { external[0].set("再开"); require(old[0].runtime() == null, "旧页面仍挂载"); });
            SceneNode[] next = {null}; ScenePageSession second = ScenePageSession.open(domain,host,host,context -> {
                next[0] = new SceneNode().bind(NodeBinding.value(external[0], SceneNode::text)); context.mount(next[0]);
            });onClose(second::close);second.frame();
            domain.execute(() -> require("再开".equals(next[0].text()) && clicked[0] == 0 && released[0] == 1, "外部状态归属或旧回调错误"));
        });
        scenario("外部executor初始输入排在声明之后", () -> {
            ArrayDeque<Runnable> tasks = new ArrayDeque<>(); ReactiveDomain domain = new ReactiveDomain(tasks::add);
            int[] clicked = {0}; ScenePageSession page = ScenePageSession.open(domain,host,host,c ->
                c.mount(new SceneNode().size(Size.fill(1),Size.fill(1)).onClick(() -> clicked[0]++)));
            onClose(() -> {page.close();while(!tasks.isEmpty())tasks.remove().run();});
            page.pointer(ScenePointerEvent.syntheticActivation(1,1));page.frame();
            while(!tasks.isEmpty())tasks.remove().run();require(clicked[0] == 1,"初始输入未走正式排队入口");
        });
        scenario("关闭前未处理ESC随页面丢弃", () -> {
            ArrayDeque<Runnable> tasks = new ArrayDeque<>(); ReactiveDomain domain = new ReactiveDomain(tasks::add);
            int[] oldFallback={0}, newClick={0};
            ScenePageSession old = ScenePageSession.open(domain,host,host,c -> c.mount(new SceneNode()));
            while(!tasks.isEmpty())tasks.remove().run();old.escape(() -> oldFallback[0]++);old.frame();old.close();
            ScenePageSession next = ScenePageSession.open(domain,host,host,c -> c.mount(new SceneNode()
                .size(Size.fill(1),Size.fill(1)).onClick(() -> newClick[0]++)));
            next.pointer(ScenePointerEvent.syntheticActivation(1,1));next.frame();
            while(!tasks.isEmpty())tasks.remove().run();onClose(() -> {next.close();while(!tasks.isEmpty())tasks.remove().run();});
            require(oldFallback[0] == 0 && newClick[0] == 1 && !next.ended(),"关闭应丢弃未处理事件且不阻断新页输入");
        });
        scenario("ESC处理中关闭页面仍执行未消费默认行为", () -> {
            ReactiveDomain domain=ReactiveDomain.direct();ScenePageSession[] current={null};int[] defaults={0};
            current[0]=ScenePageSession.open(domain,host,host,c -> c.mount(new SceneNode().onEscape(() -> {
                current[0].close();return false;
            })));
            current[0].escape(() -> defaults[0]++);current[0].frame();
            require(defaults[0]==1 && current[0].ended(),"已处理未消费ESC不应因结束被撤销");
        });
        scenario("handler结束页面不在routing内卸载或回放", () -> {
            ReactiveDomain domain=ReactiveDomain.direct();ScenePageSession[] current={null};int[] releases={0};
            current[0]=ScenePageSession.open(domain,host,host,c -> {c.own(() -> releases[0]++);
                c.mount(new SceneNode().size(Size.fill(1),Size.fill(1)).onClick(() -> current[0].close()));});
            current[0].pointer(ScenePointerEvent.syntheticActivation(1,1));current[0].frame();
            require(current[0].ended() && releases[0] == 1,"事件handler结束失败或残留");
        });
        for (boolean effectFailure : new boolean[]{false,true}) scenario("关闭后失败仍释放页面/"+effectFailure, () -> {
            ReactiveDomain domain=ReactiveDomain.direct();ScenePageSession[] current={null};SceneNode[] node={null};
            RuntimeException original=new IllegalArgumentException("关闭后失败");int[] releases={0};
            current[0]=ScenePageSession.open(domain,host,host,c -> {
                Signal<Boolean> fail=domain.signal(false);
                if(effectFailure)domain.effect(c.scope(),() -> {if(fail.get())throw original;});
                c.own(() -> releases[0]++);c.own(() -> {throw new IllegalStateException("清理附加失败");});
                node[0]=new SceneNode().size(Size.fill(1),Size.fill(1)).onClick(() -> {
                    current[0].close();if(effectFailure)fail.set(true);else throw original;
                });c.mount(node[0]);
            });Throwable caught=null;current[0].pointer(ScenePointerEvent.syntheticActivation(1,1));
            try{current[0].frame();}catch(Throwable failure){caught=failure;}
            require(caught==original && original.getSuppressed().length==1 && releases[0]==1 && node[0].runtime()==null,
                "handler/Effect失败滞留清理或覆盖原异常");current[0].close();
        });
        scenario("Effect自身关闭再抛Error仍在整轮结束清理", () -> {
            ReactiveDomain domain=ReactiveDomain.direct();ScenePageSession[] current={null};SceneNode[] node={null};
            AssertionError original=new AssertionError("Effect关闭后Error");int[] releases={0};
            current[0]=ScenePageSession.open(domain,host,host,c -> {
                Signal<Boolean> close=domain.signal(false);c.own(() -> releases[0]++);
                domain.effect(c.scope(),() -> {if(close.get()){current[0].close();throw original;}});
                node[0]=new SceneNode().size(Size.fill(1),Size.fill(1)).onClick(() -> close.set(true));c.mount(node[0]);
            });Throwable caught=null;current[0].pointer(ScenePointerEvent.syntheticActivation(1,1));
            try{current[0].frame();}catch(Throwable failure){caught=failure;}
            require(caught==original && releases[0]==1 && node[0].runtime()==null,"Effect自身Error遗留清理或丢原异常");
        });
        scenario("排队初始化前关闭不构造旧页", () -> {
            ArrayDeque<Runnable> tasks = new ArrayDeque<>(); ReactiveDomain domain = new ReactiveDomain(tasks::add);
            int[] builds = {0}; ScenePageSession page = ScenePageSession.open(domain,host,host,c -> builds[0]++);
            page.close(); while (!tasks.isEmpty()) tasks.remove().run(); require(builds[0] == 0, "已关闭接入仍初始化");
        });
        scenario("构造失败原异常与清理异常保留且清完", () -> {
            ReactiveDomain domain = ReactiveDomain.direct(); RuntimeException original = new IllegalArgumentException("构造失败");
            int[] released = {0}; Throwable[] caught = {null};
            try { ScenePageSession.open(domain,host,host,c -> {
                c.own(() -> released[0]++); c.own(() -> {throw new IllegalStateException("清理失败");});
                c.mount(new SceneNode()); throw original;
            }); } catch (Throwable failure) { caught[0] = failure; }
            require(caught[0] == original && original.getSuppressed().length == 1 && released[0] == 1, "初始化丢失原失败或漏清理");
        });
        scenario("外部executor初始化Effect失败仍完成清理", () -> {
            ArrayDeque<Runnable> tasks = new ArrayDeque<>(); ReactiveDomain domain = new ReactiveDomain(tasks::add);
            RuntimeException original = new IllegalStateException("Effect失败"); int[] released = {0};
            ScenePageSession page = ScenePageSession.open(domain,host,host,c -> {
                c.own(() -> released[0]++); c.mount(new SceneNode()); domain.effect(c.scope(), () -> {throw original;});
            });Throwable caught = null;
            try { while(!tasks.isEmpty()) tasks.remove().run(); } catch(Throwable failure) {caught = failure;}
            require(caught == original && page.ended() && released[0] == 1, "异步Effect失败未归初始化边界"); page.frame(); page.close();
        });
        scenario("释放Error仍清理余下资源且结束幂等", () -> {
            ReactiveDomain domain = ReactiveDomain.direct(); AssertionError original = new AssertionError("释放Error"); int[] released={0};
            ScenePageSession page = ScenePageSession.open(domain,host,host,c -> {
                c.own(() -> released[0]++); c.own(() -> {throw original;}); c.mount(new SceneNode());
            });Throwable caught = null;try {page.close();} catch(Throwable failure) {caught = failure;}
            page.close();page.frame();require(caught == original && released[0] == 1 && page.ended(), "Error中止了其他资源释放");
        });
        scenario("只读不能结束会话", () -> {
            ReactiveDomain domain = ReactiveDomain.direct(); ScenePageSession page = ScenePageSession.open(domain,host,host,c -> c.mount(new SceneNode()));
            onClose(page::close);domain.execute(() -> {
                try {domain.readOnly(() -> {page.close();return null;});throw new AssertionError("只读释放未拒绝");}
                catch(IllegalStateException expected) {require(!page.ended(),"拒绝之前已结束");}
            });page.frame();
        });
    }
    public static void main(String[] args) throws Exception { new PageSessionScenario().run(args); }
}
