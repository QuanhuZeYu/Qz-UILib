package club.heiqi.uilib.ui.scene.integration;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.host.SceneFramePipeline;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.runtime.BindingActivation;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/** 正式typed集成场景；复用既有固定字体度量，路由、布局、绑定与计划均为生产实现。
 * red.log是旧pipeline.click表示宿主DOWN的独立探针，不是此typed测试的baseline运行。 */
public final class PointerInputScenario extends TestApplication {
    private final class Page {
        final ReactiveDomain domain = ReactiveDomain.direct();
        final SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost();
        ScenePageSession session;
        ScenePageContext context;
        SceneRuntime runtime;
        SceneFramePipeline pipe;
        Signal<Integer> count, gap;
        Signal<Boolean> visible, enabled, modal;
        Signal<java.util.List<SceneNode>> children;
        SceneNode button, root, overlay;
        MountHandle mount;
        int releases;
        Page() {
            session = ScenePageSession.open(domain, host, host, ctx -> {
                context = ctx;
                count = domain.signal(0); gap = domain.signal(0);
                visible = domain.signal(true); enabled = domain.signal(true); modal = domain.signal(false);
                runtime = ctx.runtime();
                button = new SceneNode().size(Size.fixed(40), Size.fixed(20)).background(0xFF112233)
                    .bind(NodeBinding.value(count, (node, value) -> node.text("次数:" + value)))
                    .bind(NodeBinding.value(visible, SceneNode::visible))
                    .bind(NodeBinding.value(enabled, SceneNode::enabled))
                    .bind((scene, node, owner) -> new BindingActivation() {
                        @Override public void refresh() { }
                        @Override public void close() { releases++; }
                    }).onClick(() -> count.set(count.get() + 1));
                children = domain.signal(Collections.singletonList(button));
                root = new SceneNode().size(Size.fixed(80), Size.fixed(60))
                    .bind(NodeBinding.children(children));
                ctx.mount(root);
            });
            pipe = session.pipeline();
            onClose(session::close);
            session.frame();
        }
        void events(ScenePointerEvent... events) { for (ScenePointerEvent event : events) session.pointer(event); session.frame(); }
        void down() { events(ScenePointerEvent.down(0, 5, 5)); }
        void up() { events(ScenePointerEvent.up(0, 5, 5)); }
        void tap() { events(ScenePointerEvent.down(0, 5, 5), ScenePointerEvent.up(0, 5, 5)); }
        void expect(int expected, String message) {
            domain.execute(() -> require(count.get() == expected, message + " 实际=" + count.get()));
            require(host.last != null, "真实PaintPlan已回放到宿主边界");
        }
    }
    public static void main(String[] args) throws Exception { new PointerInputScenario().run(args); }
    @Override protected void runTests() throws Exception {
        scenario("纯核心Java8类路径", () -> {
            require(System.getProperty("java.specification.version").equals("1.8"), "场景必须Java8");
            for (String name : Arrays.asList("net.minecraft.client.Minecraft", "cpw.mods.fml.common.Mod", "org.lwjgl.Sys", "org.lwjgl.system.MemoryUtil")) {
                try { Class.forName(name); throw new AssertionError("宿主进入核心classpath: " + name); }
                catch (ClassNotFoundException expected) { }
            }
        });
        scenario("业务Effect结算异常仍保留本批第二键尾部", () -> {
            Page p = new Page(); RuntimeException failure = new RuntimeException("业务Effect失败");
            final club.heiqi.uilib.ui.reactive.Owner[] business = new club.heiqi.uilib.ui.reactive.Owner[1];
            p.domain.execute(() -> { business[0] = p.context.scope().child(); p.domain.effect(business[0], () -> { if (p.count.get() > 0) throw failure; }); });
            p.session.pointer(ScenePointerEvent.down(0, 5, 5)); p.session.pointer(ScenePointerEvent.up(0, 5, 5)); p.session.pointer(ScenePointerEvent.down(1, 5, 5));
            try { p.session.frame(); throw new AssertionError("Effect错误必须外抛"); } catch (RuntimeException actual) { require(actual == failure, "Effect原异常身份"); }
            p.domain.execute(business[0]::dispose);
            p.tap(); p.expect(1, "Effect异常后第二键仍按，下一手势必须吞尾");
            p.events(ScenePointerEvent.up(1, 5, 5)); p.tap(); p.expect(2, "Effect错误清理后全部释放恢复");
        });
        scenario("外部executor的Effect结算异常保留尾部", () -> {
            java.util.ArrayDeque<Runnable> work = new java.util.ArrayDeque<>();
            Runnable drain = () -> { while (!work.isEmpty()) work.remove().run(); };
            ReactiveDomain domain = new ReactiveDomain(work::add);
            SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost();
            SceneRuntime[] runtime = new SceneRuntime[1]; SceneFramePipeline[] pipe = new SceneFramePipeline[1]; Signal<Integer>[] count = new Signal[1];
            club.heiqi.uilib.ui.reactive.Owner[] business = new club.heiqi.uilib.ui.reactive.Owner[1]; RuntimeException failure = new RuntimeException("executor Effect失败");
            ScenePageSession session = ScenePageSession.open(domain, host, host, ctx -> {
                runtime[0] = ctx.runtime(); count[0] = domain.signal(0);
                ctx.mount(new SceneNode().size(Size.fixed(40), Size.fixed(20)).onClick(() -> count[0].set(count[0].get() + 1)));
                business[0] = ctx.scope().child();
                domain.effect(business[0], () -> { if (count[0].get() > 0) throw failure; });
            }); drain.run();
            pipe[0] = session.pipeline();
            onClose(() -> { session.close(); drain.run(); });
            session.pointer(ScenePointerEvent.down(0, 5, 5)); session.pointer(ScenePointerEvent.up(0, 5, 5)); session.pointer(ScenePointerEvent.down(1, 5, 5)); session.frame();
            try { drain.run(); throw new AssertionError("外部executor必须得到Effect异常"); } catch (RuntimeException actual) { require(actual == failure, "executor保原异常"); }
            domain.execute(business[0]::dispose); drain.run();
            session.pointer(ScenePointerEvent.down(0, 5, 5)); session.pointer(ScenePointerEvent.up(0, 5, 5)); session.frame(); drain.run();
            domain.execute(() -> require(count[0].get() == 1, "外部executor错误后本批第二键保留")); drain.run();
            session.pointer(ScenePointerEvent.up(1, 5, 5)); session.pointer(ScenePointerEvent.down(0, 5, 5)); session.pointer(ScenePointerEvent.up(0, 5, 5)); session.frame(); drain.run();
            domain.execute(() -> require(count[0].get() == 2, "外部executor全释放后恢复")); drain.run();
        });
        scenario("跨frame按下移动释放与重复UP", () -> {
            Page p = new Page(); p.down(); p.expect(0, "DOWN不得激活");
            p.events(ScenePointerEvent.move(70, 50), ScenePointerEvent.move(5, 5));
            p.expect(0, "MOVE只维护手势"); p.up(); p.up(); p.expect(1, "同目标UP恰好激活一次");
            p.domain.execute(() -> require(p.button.text().equals("次数:1"), "handler状态经binding影响下一prepare"));
            require(p.host.last.commands().stream().anyMatch(c -> c.kind == PaintCommand.Kind.TEXT && c.text.lines().get(0).text.equals("次数:1")), "PaintPlan含当前业务状态");
        });
        scenario("同batch两次激活与handler影响下一事件", () -> {
            Page p = new Page();
            p.events(ScenePointerEvent.down(0, 5, 5), ScenePointerEvent.up(0, 5, 5), ScenePointerEvent.down(0, 5, 5), ScenePointerEvent.up(0, 5, 5));
            p.expect(2, "同batch独立域操作保留连续状态");
            p.domain.execute(() -> { p.children.set(Collections.emptyList()); }); p.session.frame();
            p.domain.execute(() -> { p.button.onClick(() -> { p.count.set(p.count.get() + 1); p.visible.set(false); }); p.children.set(Collections.singletonList(p.button)); });
            p.session.frame();
            p.events(ScenePointerEvent.down(0, 5, 5), ScenePointerEvent.up(0, 5, 5), ScenePointerEvent.down(0, 5, 5), ScenePointerEvent.up(0, 5, 5));
            p.expect(3, "首handler隐藏后下一DOWN不能使用旧几何资格");
        });
        scenario("空白起手及出界释放不得转移目标", () -> {
            Page p = new Page();
            p.events(ScenePointerEvent.down(0, 70, 50), ScenePointerEvent.move(5, 5), ScenePointerEvent.up(0, 5, 5));
            p.expect(0, "空白拖入不得误点"); p.down();
            p.events(ScenePointerEvent.up(0, 70, 50)); p.expect(0, "出界释放不得激活");
            p.tap(); p.expect(1, "物理结束后恢复");
        });
        scenario("第二键尾部与第一释放后的第三键", () -> {
            Page p = new Page(); p.down();
            p.events(ScenePointerEvent.down(1, 5, 5), ScenePointerEvent.up(0, 5, 5)); p.expect(1, "第一owner正常释放，第二没有owner");
            p.events(ScenePointerEvent.down(0, 5, 5), ScenePointerEvent.up(0, 5, 5)); p.expect(1, "第二仍按不能第三起手");
            p.events(ScenePointerEvent.up(1, 5, 5)); p.tap(); p.expect(2, "全部物理释放后恢复");
            p.events(ScenePointerEvent.down(1, 5, 5), ScenePointerEvent.down(0, 5, 5), ScenePointerEvent.up(1, 5, 5), ScenePointerEvent.up(0, 5, 5));
            p.expect(2, "右键起手不得让普通按钮激活或让左键接管");
        });
        scenario("不匹配UP与重复DOWN不覆盖owner", () -> {
            Page p = new Page(); p.down();
            p.events(ScenePointerEvent.up(2, 5, 5), ScenePointerEvent.down(0, 70, 50)); p.expect(0, "未知UP不结束owner，重复DOWN不迁移");
            p.up(); p.expect(1, "原按钮正常释放");
        });
        scenario("失焦关闭CANCEL吞尾及显式物理结束", () -> {
            Page p = new Page(); p.down(); p.events(ScenePointerEvent.cancel());
            p.session.pointer(ScenePointerEvent.syntheticActivation(5, 5)); p.domain.execute(() -> p.runtime.dispatchClick(p.button)); p.session.frame(); p.expect(0, "pipeline及runtime synthetic不得绕过取消尾部");
            p.events(ScenePointerEvent.down(1, 5, 5), ScenePointerEvent.up(0, 5, 5)); p.expect(0, "cancel后旧UP不激活");
            p.events(ScenePointerEvent.down(0, 5, 5), ScenePointerEvent.up(0, 5, 5)); p.expect(0, "仍有第二物理按钮必须吞");
            p.events(ScenePointerEvent.allButtonsReleased()); p.tap(); p.expect(1, "宿主确认全释放可恢复丢UP");
            p.down(); p.events(ScenePointerEvent.cancel(), ScenePointerEvent.up(0, 5, 5)); p.expect(1, "关闭取消不激活");
            p.session.pointer(ScenePointerEvent.syntheticActivation(5, 5)); p.session.frame(); p.expect(2, "空闲synthetic是完整激活");
        });
        for (String cause : Arrays.asList("hide", "disable", "modal", "detach")) scenario("无事件prepare取消owner:" + cause, () -> {
            Page p = new Page(); p.down();
            p.domain.execute(() -> {
                if (cause.equals("hide")) p.visible.set(false);
                if (cause.equals("disable")) p.enabled.set(false);
                if (cause.equals("detach")) p.children.set(Collections.emptyList());
                if (cause.equals("modal")) {
                    p.overlay = new SceneNode().size(Size.fixed(5), Size.fixed(5)).modal(true).bind(NodeBinding.value(p.modal, SceneNode::visible));
                    p.runtime.attach(null, p.overlay); p.modal.set(true);
                }
            }); p.session.frame();
            p.domain.execute(() -> { p.visible.set(true); p.enabled.set(true); p.modal.set(false); p.children.set(Collections.singletonList(p.button)); }); p.session.frame();
            p.up(); p.expect(0, "资格恢复不得复活取消owner:" + cause); p.tap(); p.expect(1, "尾部结束后新手势:" + cause);
        });
        scenario("close未处理第二键不能使另一管线误建owner", () -> {
            Page p = new Page(); p.down(); p.session.pointer(ScenePointerEvent.down(1, 5, 5)); p.pipe.close();
            // 底层合同：关闭一条管线后另建管线，共享runtime的物理尾部权威。
            SceneFramePipeline replacement = new SceneFramePipeline(p.runtime, p.host, p.host);
            p.domain.execute(() -> p.context.own(replacement));
            replacement.pointer(ScenePointerEvent.up(0, 5, 5)); replacement.frame();
            replacement.pointer(ScenePointerEvent.down(0, 5, 5)); replacement.pointer(ScenePointerEvent.up(0, 5, 5)); replacement.frame();
            p.expect(0, "第二键仍按不能由另一管线建owner");
            replacement.pointer(ScenePointerEvent.up(1, 5, 5)); replacement.pointer(ScenePointerEvent.down(0, 5, 5)); replacement.pointer(ScenePointerEvent.up(0, 5, 5)); replacement.frame();
            p.expect(1, "另一管线在全部物理结束后恢复");
        });
        scenario("模态激活后关闭无中间frame不得恢复后台owner", () -> {
            Page p = new Page(); p.down(); final MountHandle[] modal = new MountHandle[1];
            p.domain.execute(() -> {
                modal[0] = p.runtime.attach(null, new SceneNode().modal(true).size(Size.fixed(5), Size.fixed(5)));
                require(!p.runtime.interactive(p.button), "真实模态激活使owner失活");
            });
            p.domain.execute(() -> modal[0].detach()); p.up(); p.expect(0, "已观测模态失活后关闭不能恢复旧owner");
        });
        for (String cause : Arrays.asList("visible", "enabled")) scenario("声明生效失活后恢复无frame:" + cause, () -> {
            Page p = new Page(); p.down();
            p.domain.execute(() -> {
                if (cause.equals("visible")) p.visible.set(false); else p.enabled.set(false);
                p.runtime.preparePresentation();
                require(!p.runtime.interactive(p.button), "已生效声明使owner失活");
            });
            p.domain.execute(() -> { p.visible.set(true); p.enabled.set(true); p.runtime.preparePresentation(); });
            p.up(); p.expect(0, "两帧之间生效失活不能复活:" + cause);
        });
        scenario("移入禁用父再移回无frame取消旧owner", () -> {
            Page p = new Page(); final MountHandle[] action = new MountHandle[1]; final SceneNode[] disabled = new SceneNode[1];
            p.domain.execute(() -> p.children.set(Collections.emptyList())); p.session.frame();
            p.domain.execute(() -> { action[0] = p.runtime.attach(p.root, p.button); disabled[0] = new SceneNode().enabled(false); p.runtime.attach(null, disabled[0]); });
            p.down(); p.domain.execute(() -> action[0].moveTo(disabled[0])); p.domain.execute(() -> action[0].moveTo(p.root));
            p.up(); p.expect(0, "新父失活不能依靠移回复活旧owner");
        });
        scenario("close已提交域但未执行批次保留第二键", () -> {
            java.util.ArrayDeque<Runnable> work = new java.util.ArrayDeque<>();
            Runnable drain = () -> { while (!work.isEmpty()) work.remove().run(); };
            ReactiveDomain domain = new ReactiveDomain(work::add);
            SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost();
            SceneRuntime[] runtime = new SceneRuntime[1]; SceneFramePipeline[] pipe = new SceneFramePipeline[1]; Signal<Integer>[] count = new Signal[1];
            club.heiqi.uilib.ui.reactive.Owner[] scope = new club.heiqi.uilib.ui.reactive.Owner[1];
            ScenePageSession session = ScenePageSession.open(domain, host, host, ctx -> {
                scope[0] = ctx.scope(); runtime[0] = ctx.runtime(); count[0] = domain.signal(0);
                ctx.mount(new SceneNode().size(Size.fixed(40), Size.fixed(20)).onClick(() -> count[0].set(count[0].get() + 1)));
            }); drain.run();
            pipe[0] = session.pipeline();
            onClose(() -> { session.close(); drain.run(); });
            session.pointer(ScenePointerEvent.down(0, 5, 5)); session.frame(); drain.run();
            session.pointer(ScenePointerEvent.down(1, 5, 5)); session.frame(); // 已从queue搬到batch，域executor尚未运行。
            pipe[0].close(); drain.run();
            // 底层合同：已提交未执行批次的close须将尾部交给同runtime的新管线。
            SceneFramePipeline next = new SceneFramePipeline(runtime[0], host, host);
            domain.execute(() -> scope[0].own(next)); drain.run();
            next.pointer(ScenePointerEvent.up(0, 5, 5)); next.pointer(ScenePointerEvent.down(0, 5, 5)); next.pointer(ScenePointerEvent.up(0, 5, 5)); next.frame(); drain.run();
            domain.execute(() -> require(count[0].get() == 0, "关闭已提交批次不丢第二键")); drain.run();
            next.pointer(ScenePointerEvent.up(1, 5, 5)); next.pointer(ScenePointerEvent.down(0, 5, 5)); next.pointer(ScenePointerEvent.up(0, 5, 5)); next.frame(); drain.run();
            domain.execute(() -> require(count[0].get() == 1, "尾部结束后异步域正常恢复")); drain.run();
        });
        scenario("同UUID重挂载旧token不得激活", () -> {
            Page p = new Page(); UUID id = p.button.id(); p.down();
            p.domain.execute(() -> {
                p.children.set(Collections.emptyList()); p.runtime.preparePresentation();
                p.children.set(Collections.singletonList(p.button)); p.runtime.preparePresentation();
                require(p.button.id().equals(id), "真实同节点重挂载保留UUID");
            }); p.up(); p.expect(0, "没有中间frame也须检查旧挂载token"); p.tap(); p.expect(1, "新token新手势可激活");
            require(p.releases == 1, "detach恰好关闭旧绑定");
        });
        scenario("新顶层目标不得接管原owner", () -> {
            Page p = new Page(); p.down();
            p.domain.execute(() -> p.runtime.attach(null, new SceneNode().size(Size.fixed(40), Size.fixed(20)).onClick(() -> p.count.set(100))));
            p.up(); p.expect(0, "UP新顶层命中不得激活旧或新目标");
        });
        scenario("同runtime移动保token但复核新父资格", () -> {
            Page p = new Page(); final MountHandle[] action = new MountHandle[1]; final SceneNode[] target = new SceneNode[1];
            p.domain.execute(() -> { p.children.set(Collections.emptyList()); }); p.session.frame();
            p.domain.execute(() -> { action[0] = p.runtime.attach(p.root, p.button); target[0] = new SceneNode().size(Size.fixed(80), Size.fixed(60)); p.runtime.attach(null, target[0]); });
            p.down(); p.domain.execute(() -> action[0].moveTo(target[0])); p.up(); p.expect(1, "合法移动身份保留");
            p.down(); p.domain.execute(() -> { SceneNode disabled = new SceneNode().enabled(false); p.runtime.attach(null, disabled); action[0].moveTo(disabled); });
            p.session.frame(); p.domain.execute(() -> action[0].moveTo(target[0])); p.up(); p.expect(1, "移入禁用父取消后移回不能复活");
        });
        scenario("geometry重排与共同PaintPlan", () -> {
            Page p = new Page(); SceneNode spacer = new SceneNode().size(Size.fixed(40), Size.fixed(0)).bind(NodeBinding.value(p.gap, (n, v) -> n.size(Size.fixed(40), Size.fixed(v))));
            p.domain.execute(() -> p.children.set(Arrays.asList(spacer, p.button))); p.session.frame(); p.down();
            p.domain.execute(() -> p.gap.set(30));
            p.events(ScenePointerEvent.move(5, 35), ScenePointerEvent.up(0, 5, 35)); p.expect(1, "UP按当前几何同目标判定");
            require(p.host.last.commands().stream().anyMatch(c -> c.kind == PaintCommand.Kind.RECT && c.box.y == 30), "绘制采用相同重排位置");
            p.events(ScenePointerEvent.down(0, 5, 35)); p.domain.execute(() -> p.gap.set(0));
            p.events(ScenePointerEvent.up(0, 5, 35)); p.expect(1, "原旧位置释放不误点");
        });
        scenario("滚轮沿现路且handler影响下一指针事件", () -> {
            Page p = new Page(); p.domain.execute(() -> {
                p.runtime.attach(null, new SceneNode().size(Size.fixed(80), Size.fixed(60)).onScroll(delta -> p.visible.set(false)));
            });
            p.down(); p.session.pointer(ScenePointerEvent.scroll(5, 5, 1)); p.session.pointer(ScenePointerEvent.up(0, 5, 5)); p.session.frame();
            p.expect(0, "scroll业务状态使随后UP取消owner");
        });
        scenario("handler异常释放owner并保留已采集物理尾部", () -> {
            Page p = new Page(); RuntimeException failure = new RuntimeException("业务handler失败");
            p.domain.execute(() -> p.children.set(Collections.emptyList())); p.session.frame();
            p.domain.execute(() -> { p.button.onClick(() -> { p.count.set(p.count.get() + 1); throw failure; }); p.children.set(Collections.singletonList(p.button)); }); p.session.frame();
            p.session.pointer(ScenePointerEvent.down(0, 5, 5)); p.session.pointer(ScenePointerEvent.up(0, 5, 5));
            p.session.pointer(ScenePointerEvent.down(1, 5, 5));
            try { p.session.frame(); throw new AssertionError("异常应外抛"); } catch (RuntimeException actual) { require(actual == failure, "原异常身份"); }
            p.expect(1, "handler只执行一次");
            p.domain.execute(() -> p.children.set(Collections.emptyList())); p.session.frame();
            p.domain.execute(() -> { p.button.onClick(() -> p.count.set(p.count.get() + 1)); p.children.set(Collections.singletonList(p.button)); }); p.session.frame();
            p.tap(); p.expect(1, "异常后已采集第二DOWN仍须吞尾");
            p.events(ScenePointerEvent.up(1, 5, 5)); p.tap(); p.expect(2, "异常后物理结束正常恢复");
        });
        // 底层合同：pipeline.close丢弃队列但不释放runtime，后者仍可接受完整synthetic。
        scenario("管线close丢弃pending输入且不重放关闭页面", () -> {
            Page p = new Page(); p.down();
            club.heiqi.uilib.ui.scene.paint.PaintPlan last = p.host.last;
            p.session.pointer(ScenePointerEvent.up(0, 5, 5)); p.pipe.close(); p.pipe.close();
            try { p.session.frame(); throw new AssertionError("关闭管线不能frame"); } catch (IllegalStateException expected) { }
            try { p.session.pointer(ScenePointerEvent.down(0, 5, 5)); throw new AssertionError("关闭管线不能输入"); } catch (IllegalStateException expected) { }
            p.expect(0, "pending UP不激活"); require(p.host.last == last, "close不replay");
            p.domain.execute(() -> p.runtime.dispatchClick(p.button)); p.expect(1, "close记pending UP后物理已结束，另一完整synthetic可激活");
        });
        scenario("同batch滚轮状态卸载重挂载的旧token", () -> {
            Page p = new Page();
            p.domain.execute(() -> p.runtime.attach(null, new SceneNode().size(Size.fixed(80), Size.fixed(60)).onScroll(delta -> {
                p.children.set(delta < 0 ? Collections.emptyList() : Collections.singletonList(p.button));
            })));
            p.down(); p.session.pointer(ScenePointerEvent.scroll(5, 5, -1)); p.session.pointer(ScenePointerEvent.scroll(5, 5, 1)); p.session.pointer(ScenePointerEvent.up(0, 5, 5)); p.session.frame();
            p.expect(0, "事件间prepare卸载后重挂载不得恢复旧owner"); p.tap(); p.expect(1, "新物理段可以激活新挂载");
        });
        scenario("runtime关闭释放绑定和指针资源", () -> {
            // 底层合同：runtime单独close释放绑定，保留对底层关闭入口的覆盖。
            Page p = new Page(); p.down(); p.domain.execute(p.runtime::close);
            require(p.releases == 1 && p.button.runtime() == null, "close释放挂载订阅");
            p.domain.execute(() -> { p.count.set(9); require(p.button.text().equals("次数:0"), "释放后binding不再持有节点"); });
            // 应用仅登记session；runtime单独close之后由同一session完成剩余清理。
        });
    }
}
