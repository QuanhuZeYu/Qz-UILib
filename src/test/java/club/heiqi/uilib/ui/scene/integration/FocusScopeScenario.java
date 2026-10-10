package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import java.util.Arrays;
import java.util.ArrayDeque;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.host.SceneFramePipeline;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/** 范围进入/返回使用真实声明、绑定、挂载和PaintPlan，不mock焦点策略。 */
public final class FocusScopeScenario extends TestApplication {
    private final class Page {
        final ReactiveDomain domain = ReactiveDomain.direct();
        final SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost();
        ScenePageSession session;
        ScenePageContext context;
        SceneRuntime runtime;
        SceneFramePipeline pipe;
        SceneNode a, b, m, n, root, outer, inner;
        Signal<Boolean> outerOpen, innerOpen, aEnabled, aVisible, aFocusable;
        Signal<SceneNode> initial, restore;
        MountHandle outerHandle;

        Page(boolean explicit) {
            session =
                    ScenePageSession.open(
                            domain,
                            host,
                            host,
                            ctx -> {
                                context = ctx;
                                runtime = ctx.runtime();
                                outerOpen = domain.signal(false);
                                innerOpen = domain.signal(false);
                                aEnabled = domain.signal(true);
                                aVisible = domain.signal(true);
                                aFocusable = domain.signal(true);
                                a =
                                        new SceneNode()
                                                .text("A")
                                                .focusable(true)
                                                .bind(
                                                        NodeBinding.value(
                                                                aEnabled, SceneNode::enabled))
                                                .bind(
                                                        NodeBinding.value(
                                                                aVisible, SceneNode::visible))
                                                .bind(
                                                        NodeBinding.value(
                                                                aFocusable, SceneNode::focusable));
                                b = new SceneNode().text("B").focusable(true);
                                m = new SceneNode().text("M").focusable(true);
                                n = new SceneNode().text("N").focusable(true);
                                initial = domain.signal(explicit ? b : null);
                                restore = domain.signal(null);
                                SceneNode status =
                                        new SceneNode()
                                                .bind(
                                                        NodeBinding.value(
                                                                runtime.focusRouter().focused(),
                                                                (node, target) ->
                                                                        node.text(
                                                                                "焦点="
                                                                                        + name(
                                                                                                target))));
                                root =
                                        new SceneNode()
                                                .bind(
                                                        NodeBinding.value(
                                                                initial, SceneNode::initialFocus))
                                                .bind(
                                                        NodeBinding.value(
                                                                restore, SceneNode::restoreFocus))
                                                .add(a)
                                                .add(b)
                                                .add(status);
                                ctx.mount(root);
                                inner =
                                        new SceneNode()
                                                .modal(true)
                                                .visible(false)
                                                .initialFocus(n)
                                                .bind(
                                                        NodeBinding.value(
                                                                innerOpen, SceneNode::visible))
                                                .add(n);
                                outer =
                                        new SceneNode()
                                                .modal(true)
                                                .visible(false)
                                                .initialFocus(m)
                                                .bind(
                                                        NodeBinding.value(
                                                                outerOpen, SceneNode::visible))
                                                .add(m)
                                                .add(inner);
                                outerHandle = ctx.mount(outer);
                            });
            pipe = session.pipeline();
            onClose(session::close);
        }

        String name(SceneNode node) {
            return node == a ? "A" : node == b ? "B" : node == m ? "M" : node == n ? "N" : "无";
        }

        void expect(SceneNode node) {
            session.frame();
            domain.execute(
                    () ->
                            require(
                                    runtime.focusRouter().focused().get() == node,
                                    "期望"
                                            + name(node)
                                            + "，实际"
                                            + name(runtime.focusRouter().focused().get())));
            require(
                    host.last.commands().stream()
                            .anyMatch(
                                    c ->
                                            c.kind == PaintCommand.Kind.TEXT
                                                    && c.text.lines().stream()
                                                            .anyMatch(
                                                                    l ->
                                                                            l.text.equals(
                                                                                    "焦点="
                                                                                            + name(
                                                                                                    node)))),
                    "真实Plan焦点显示错误");
        }

        void choose(SceneNode node) {
            domain.execute(
                    () ->
                            require(
                                    runtime.focusRouter().request(runtime.captureMount(node)),
                                    "合法请求应接受"));
        }

        void open(Signal<Boolean> state, boolean value) {
            domain.execute(() -> state.set(value));
        }
    }

    @Override
    protected void runTests() throws Exception {
        scenario(
                "页面初始B模态进入M返回B真实闭环",
                () -> {
                    Page p = new Page(true);
                    p.expect(p.b);
                    p.open(p.outerOpen, true);
                    p.expect(p.m);
                    p.open(p.outerOpen, false);
                    p.expect(p.b);
                });
        scenario(
                "默认声明顺序初始A及声明恢复优先",
                () -> {
                    Page p = new Page(false);
                    p.expect(p.a);
                    p.choose(p.b);
                    p.domain.execute(() -> p.restore.set(p.a));
                    p.expect(p.b);
                    p.open(p.outerOpen, true);
                    p.expect(p.m);
                    p.open(p.outerOpen, false);
                    p.expect(p.a);
                });
        scenario(
                "保存目标失活再恢复不复活旧记录",
                () -> {
                    for (String reason : new String[] {"enabled", "visible", "focusable"}) {
                        Page p = new Page(true);
                        p.expect(p.b);
                        p.choose(p.a);
                        p.open(p.outerOpen, true);
                        p.expect(p.m);
                        Signal<Boolean> source =
                                reason.equals("enabled")
                                        ? p.aEnabled
                                        : reason.equals("visible") ? p.aVisible : p.aFocusable;
                        p.domain.execute(
                                () -> {
                                    source.set(false);
                                    p.runtime.preparePresentation();
                                });
                        p.domain.execute(
                                () -> {
                                    source.set(true);
                                    p.runtime.preparePresentation();
                                });
                        p.open(p.outerOpen, false);
                        p.expect(p.b);
                    }
                });
        scenario(
                "嵌套返回合法外层与外层结束清理所属内层",
                () -> {
                    Page p = new Page(true);
                    p.expect(p.b);
                    p.choose(p.a);
                    p.open(p.outerOpen, true);
                    p.expect(p.m);
                    p.open(p.innerOpen, true);
                    p.expect(p.n);
                    p.open(p.innerOpen, false);
                    p.expect(p.m);
                    p.open(p.innerOpen, true);
                    p.expect(p.n);
                    p.open(p.outerOpen, false);
                    p.expect(p.a);
                    p.domain.execute(() -> p.outerHandle.destroy());
                    p.expect(p.a);
                });
        scenario(
                "无中间绘制的范围切换保留原返回记录",
                () -> {
                    Page p = new Page(false);
                    p.expect(p.a);
                    p.choose(p.b);
                    p.domain.execute(
                            () -> {
                                p.outerOpen.set(true);
                                p.runtime.preparePresentation();
                            });
                    p.domain.execute(
                            () -> {
                                p.outerOpen.set(false);
                                p.runtime.preparePresentation();
                            });
                    p.domain.execute(
                            () -> {
                                p.outerOpen.set(true);
                                p.runtime.preparePresentation();
                            });
                    p.domain.execute(
                            () -> {
                                p.outerOpen.set(false);
                                p.runtime.preparePresentation();
                            });
                    p.expect(p.b);
                    // 完全不结算进入选择的短范围也不能把空owner写回覆盖已有记录。
                    p.domain.execute(
                            () -> {
                                MountHandle one =
                                        p.runtime.attach(null, new SceneNode().modal(true));
                                one.detach();
                                MountHandle two =
                                        p.runtime.attach(null, new SceneNode().modal(true));
                                two.detach();
                            });
                    p.expect(p.b);
                });
        scenario(
                "旧挂载恢复取消而声明可明确选择新挂载",
                () -> {
                    Page p = new Page(true);
                    p.expect(p.b);
                    p.choose(p.a);
                    p.open(p.outerOpen, true);
                    p.expect(p.m);
                    p.domain.execute(
                            () -> {
                                p.runtime.captureMount(p.a).detach();
                                p.runtime.attach(p.root, p.a);
                            });
                    p.open(p.outerOpen, false);
                    p.expect(p.b);
                    p.choose(p.a);
                    p.open(p.outerOpen, true);
                    p.expect(p.m);
                    p.domain.execute(
                            () -> {
                                p.runtime.captureMount(p.a).detach();
                                p.runtime.attach(p.root, p.a);
                                p.restore.set(p.a);
                            });
                    p.open(p.outerOpen, false);
                    p.expect(p.a);
                });
        scenario(
                "目标越范围或跨runtime被拒且空范围无焦点",
                () -> {
                    Page p = new Page(true);
                    p.expect(p.b);
                    p.domain.execute(
                            () -> {
                                // 底层合同：异runtime目标必须被同一焦点权威拒绝，刻意构造外域目标。
                                SceneRuntime other = new SceneRuntime(p.domain);
                                p.context.own(other);
                                SceneNode foreign = new SceneNode().focusable(true);
                                other.attach(null, foreign);
                                p.initial.set(foreign);
                                p.restore.set(foreign);
                            });
                    p.expect(p.b);
                    p.open(p.outerOpen, true);
                    p.expect(p.m);
                    p.domain.execute(
                            () -> {
                                MountHandle child =
                                        p.runtime.attach(
                                                p.outer,
                                                new SceneNode().modal(true).initialFocus(p.a));
                                p.runtime.preparePresentation();
                                require(
                                        p.runtime.focusRouter().focused().get() == null,
                                        "空范围不能穿透声明的后台目标");
                                child.detach();
                                p.runtime.preparePresentation();
                                require(p.runtime.focusRouter().focused().get() == p.m, "空内层返回M");
                            });
                    p.open(p.outerOpen, false);
                    p.expect(p.b);
                });
        scenario(
                "清除声明变化被动内容与普通布局更新不补选",
                () -> {
                    Page p = new Page(true);
                    p.expect(p.b);
                    p.domain.execute(
                            () -> {
                                p.initial.set(p.a);
                                p.runtime.attach(null, new SceneNode().text("提示").focusable(true));
                            });
                    p.expect(p.b);
                    p.host.width = 90;
                    p.host.height = 180;
                    p.expect(p.b);
                    p.domain.execute(() -> p.runtime.focusRouter().clear());
                    p.expect(null);
                    p.domain.execute(
                            () ->
                                    p.runtime.attach(
                                            p.root, new SceneNode().text("新增").focusable(true)));
                    p.expect(null);
                    p.open(p.outerOpen, true);
                    p.expect(p.m);
                    p.open(p.outerOpen, false);
                    p.expect(p.a);
                });
        scenario(
                "首次选择等声明结算且按实际逻辑顺序",
                () -> {
                    Page p = new Page(false);
                    p.domain.execute(() -> p.aEnabled.set(false));
                    p.expect(p.b);
                    p.domain.execute(
                            () -> {
                                p.aEnabled.set(true);
                                p.runtime.preparePresentation();
                            });
                    p.expect(p.b);
                    p.choose(p.a);
                    p.open(p.outerOpen, true);
                    p.expect(p.m);
                    p.domain.execute(
                            () -> {
                                p.aEnabled.set(false);
                                p.runtime.preparePresentation();
                                p.aEnabled.set(true);
                                p.runtime.preparePresentation();
                            });
                    // 动态声明改变顺序；状态绑定自己驱动children，不从handler改树。
                    p.domain.execute(
                            () -> {
                                SceneNode c = new SceneNode().focusable(true).text("C");
                                Signal<java.util.List<SceneNode>> children =
                                        p.domain.signal(Arrays.asList(c, p.b, p.a));
                                SceneNode holder =
                                        new SceneNode().bind(NodeBinding.children(children));
                                // 已有A/B有挂载归属，先卸载再由声明接入。
                                p.runtime.captureMount(p.a).detach();
                                p.runtime.captureMount(p.b).detach();
                                p.runtime.attach(p.root, holder);
                                p.initial.set(null);
                            });
                    p.open(p.outerOpen, false);
                    p.session.frame();
                    p.domain.execute(
                            () ->
                                    require(
                                            p.runtime
                                                    .focusRouter()
                                                    .focused()
                                                    .get()
                                                    .text()
                                                    .equals("C"),
                                            "恢复失败按新声明顺序选C"));
                });
        scenario(
                "modal声明关闭后返回而非保留原模态目标",
                () -> {
                    Page p = new Page(true);
                    p.expect(p.b);
                    final SceneNode[] z = new SceneNode[1];
                    final Runnable[] end = new Runnable[1];
                    p.domain.execute(
                            () -> {
                                Signal<Boolean> mode = p.domain.signal(true);
                                z[0] = new SceneNode().focusable(true);
                                p.runtime.attach(
                                        null,
                                        new SceneNode()
                                                .modal(true)
                                                .initialFocus(z[0])
                                                .bind(NodeBinding.value(mode, SceneNode::modal))
                                                .add(z[0]));
                                end[0] = () -> mode.set(false);
                            });
                    p.session.frame();
                    p.domain.execute(
                            () ->
                                    require(
                                            p.runtime.focusRouter().focused().get() == z[0],
                                            "进入新模态"));
                    p.domain.execute(end[0]);
                    p.expect(p.b);
                });
        scenario(
                "独立后台模态结束不改变仍有效的前台焦点",
                () -> {
                    for (boolean clear : new boolean[] {false, true}) {
                        Page p = new Page(true);
                        p.expect(p.b);
                        p.open(p.outerOpen, true);
                        p.expect(p.m);
                        final SceneNode[] chosen = new SceneNode[1];
                        final MountHandle[] foreground = new MountHandle[1];
                        p.domain.execute(
                                () -> {
                                    SceneNode initial = new SceneNode().focusable(true);
                                    chosen[0] = new SceneNode().focusable(true);
                                    foreground[0] =
                                            p.runtime.attach(
                                                    null,
                                                    new SceneNode()
                                                            .modal(true)
                                                            .initialFocus(initial)
                                                            .add(initial)
                                                            .add(chosen[0]));
                                    p.runtime.preparePresentation();
                                    p.runtime
                                            .focusRouter()
                                            .request(p.runtime.captureMount(chosen[0]));
                                    if (clear) p.runtime.focusRouter().clear();
                                    // 独立根不是所属内层；后台结束不能重新运行未变前台的进入策略。
                                    p.outerHandle.detach();
                                    p.runtime.preparePresentation();
                                    require(
                                            p.runtime.focusRouter().focused().get()
                                                    == (clear ? null : chosen[0]),
                                            "后台结束不应重置前台选择或补选clear");
                                    foreground[0].detach();
                                });
                        p.expect(p.b);
                    }
                });
        scenario(
                "自定义激活显露父范围时尚未挂载内层不参与查询",
                () -> {
                    Page p = new Page(true);
                    p.expect(p.b);
                    p.domain.execute(
                            () -> {
                                SceneNode target = new SceneNode().focusable(true);
                                SceneNode nested =
                                        new SceneNode()
                                                .modal(true)
                                                .initialFocus(target)
                                                .add(target);
                                MountHandle outer =
                                        p.runtime.attach(
                                                null,
                                                new SceneNode()
                                                        .modal(true)
                                                        .visible(false)
                                                        .bind(
                                                                (runtime, node, owner) -> {
                                                                    node.visible(true);
                                                                    return new club.heiqi.uilib.ui
                                                                            .scene.runtime
                                                                            .BindingActivation() {
                                                                        public void refresh() {}

                                                                        public void close() {}
                                                                    };
                                                                })
                                                        .add(nested));
                                p.runtime.preparePresentation();
                                require(
                                        p.runtime.focusRouter().focused().get() == target,
                                        "完成挂载后选择内层");
                                outer.detach();
                            });
                    p.expect(p.b);
                });
        scenario(
                "挂载声明失败清理范围并保留原异常",
                () -> {
                    Page p = new Page(true);
                    p.expect(p.b);
                    RuntimeException primary = new RuntimeException("声明激活失败");
                    p.domain.execute(
                            () -> {
                                SceneNode failed =
                                        new SceneNode()
                                                .modal(true)
                                                .visible(false)
                                                .bind(
                                                        (runtime, node, owner) -> {
                                                            node.visible(true);
                                                            throw primary;
                                                        });
                                try {
                                    p.runtime.attach(null, failed);
                                    throw new AssertionError("预期声明失败");
                                } catch (RuntimeException actual) {
                                    require(actual == primary, "保持原Throwable");
                                }
                                require(failed.runtime() == null, "失败挂载已清理");
                            });
                    p.expect(p.b);
                });
        scenario(
                "排队执行进入与返回关闭保持顺序",
                () -> {
                    ArrayDeque<Runnable> queue = new ArrayDeque<>();
                    ReactiveDomain domain = new ReactiveDomain(queue::add);
                    final SceneRuntime[] runtime = new SceneRuntime[1];
                    final SceneNode[] target = new SceneNode[1];
                    final MountHandle[] modal = new MountHandle[1];
                    Runnable drain =
                            () -> {
                                while (!queue.isEmpty()) queue.remove().run();
                            };
                    SoftwareSceneScenario.SoftwareHost host =
                            new SoftwareSceneScenario.SoftwareHost();
                    ScenePageSession session =
                            ScenePageSession.open(
                                    domain,
                                    host,
                                    host,
                                    ctx -> {
                                        runtime[0] = ctx.runtime();
                                        target[0] = new SceneNode().focusable(true);
                                        ctx.mount(new SceneNode().add(target[0]));
                                    });
                    drain.run();
                    onClose(
                            () -> {
                                session.close();
                                drain.run();
                            });
                    domain.execute(
                            () -> {
                                runtime[0].preparePresentation();
                                require(
                                        runtime[0].focusRouter().focused().get() == target[0],
                                        "默认进入先执行");
                            });
                    domain.execute(
                            () -> {
                                modal[0] = runtime[0].attach(null, new SceneNode().modal(true));
                                runtime[0].preparePresentation();
                                require(runtime[0].focusRouter().focused().get() == null, "空模态隔离");
                            });
                    domain.execute(
                            () -> {
                                modal[0].detach();
                                runtime[0].preparePresentation();
                                require(
                                        runtime[0].focusRouter().focused().get() == target[0],
                                        "返回恢复");
                                session.close();
                            });
                    drain.run();
                });
    }

    public static void main(String[] args) throws Exception {
        new FocusScopeScenario().run(args);
    }
}
