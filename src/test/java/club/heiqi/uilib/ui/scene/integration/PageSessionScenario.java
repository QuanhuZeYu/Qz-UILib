package club.heiqi.uilib.ui.scene.integration;

import java.util.ArrayDeque;
import java.util.Collections;
import club.heiqi.uilib.ui.reactive.Observation;
import club.heiqi.uilib.ui.scene.control.SceneControl;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.BindingActivation;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
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
    private final SoftwareSceneScenario.SoftwareHost host =
            new SoftwareSceneScenario.SoftwareHost();

    @Override
    protected void runTests() throws Exception {
        scenario(
                "声明页面真实绑定输入与尺寸更新只构造一次",
                () -> {
                    ReactiveDomain domain = ReactiveDomain.direct();
                    int[] builds = {0}, clicks = {0};
                    SceneNode[] root = {null};
                    ScenePageSession page =
                            ScenePageSession.open(
                                    domain,
                                    host,
                                    host,
                                    context -> {
                                        builds[0]++;
                                        Signal<String> text = domain.signal("初值");
                                        root[0] =
                                                new SceneNode()
                                                        .size(Size.fill(1), Size.fill(1))
                                                        .bind(
                                                                NodeBinding.value(
                                                                        text, SceneNode::text))
                                                        .onClick(
                                                                () -> {
                                                                    clicks[0]++;
                                                                    text.set("更新");
                                                                });
                                        context.mount(root[0]);
                                    });
                    onClose(page::close);
                    page.frame();
                    page.pointer(ScenePointerEvent.down(0, 1, 1));
                    page.pointer(ScenePointerEvent.up(0, 1, 1));
                    page.frame();
                    domain.execute(
                            () ->
                                    require(
                                            "更新".equals(root[0].text()) && clicks[0] == 1,
                                            "正式页面未结算真实click绑定"));
                    host.width = 200;
                    page.frame();
                    domain.execute(
                            () ->
                                    require(
                                            page.pipeline()
                                                                    .current()
                                                                    .geometry
                                                                    .get(root[0])
                                                                    .box
                                                                    .width
                                                            == 200
                                                    && builds[0] == 1,
                                            "尺寸更新重建声明或未重排"));
                });
        scenario(
                "结束不回放输入外部状态可再接入",
                () -> {
                    ReactiveDomain domain = ReactiveDomain.direct();
                    Signal<String>[] external = new Signal[1];
                    domain.execute(() -> external[0] = domain.signal("外部"));
                    int[] clicked = {0}, released = {0};
                    SceneNode[] old = {null};
                    ScenePageSession first =
                            ScenePageSession.open(
                                    domain,
                                    host,
                                    host,
                                    context -> {
                                        context.own(() -> released[0]++);
                                        old[0] =
                                                new SceneNode()
                                                        .size(Size.fill(1), Size.fill(1))
                                                        .onClick(() -> clicked[0]++)
                                                        .bind(
                                                                NodeBinding.value(
                                                                        external[0],
                                                                        SceneNode::text));
                                        context.mount(old[0]);
                                    });
                    first.frame();
                    first.pointer(ScenePointerEvent.syntheticActivation(1, 1));
                    first.close();
                    first.close();
                    first.frame();
                    first.escape(() -> clicked[0]++);
                    domain.execute(
                            () -> {
                                external[0].set("再开");
                                require(old[0].runtime() == null, "旧页面仍挂载");
                            });
                    SceneNode[] next = {null};
                    ScenePageSession second =
                            ScenePageSession.open(
                                    domain,
                                    host,
                                    host,
                                    context -> {
                                        next[0] =
                                                new SceneNode()
                                                        .bind(
                                                                NodeBinding.value(
                                                                        external[0],
                                                                        SceneNode::text));
                                        context.mount(next[0]);
                                    });
                    onClose(second::close);
                    second.frame();
                    domain.execute(
                            () ->
                                    require(
                                            "再开".equals(next[0].text())
                                                    && clicked[0] == 0
                                                    && released[0] == 1,
                                            "外部状态归属或旧回调错误"));
                });
        scenario(
                "外部executor初始输入排在声明之后",
                () -> {
                    ArrayDeque<Runnable> tasks = new ArrayDeque<>();
                    ReactiveDomain domain = new ReactiveDomain(tasks::add);
                    int[] clicked = {0};
                    ScenePageSession page =
                            ScenePageSession.open(
                                    domain,
                                    host,
                                    host,
                                    c ->
                                            c.mount(
                                                    new SceneNode()
                                                            .size(Size.fill(1), Size.fill(1))
                                                            .onClick(() -> clicked[0]++)));
                    onClose(
                            () -> {
                                page.close();
                                while (!tasks.isEmpty()) tasks.remove().run();
                            });
                    page.pointer(ScenePointerEvent.syntheticActivation(1, 1));
                    page.frame();
                    while (!tasks.isEmpty()) tasks.remove().run();
                    require(clicked[0] == 1, "初始输入未走正式排队入口");
                });
        scenario(
                "旧会话排队ESC不影响后来声明页",
                () -> {
                    ArrayDeque<Runnable> tasks = new ArrayDeque<>();
                    ReactiveDomain domain = new ReactiveDomain(tasks::add);
                    int[] oldFallback = {0}, newClick = {0};
                    ScenePageSession old =
                            ScenePageSession.open(
                                    domain, host, host, c -> c.mount(new SceneNode()));
                    while (!tasks.isEmpty()) tasks.remove().run();
                    old.escape(() -> oldFallback[0]++);
                    old.frame();
                    old.close();
                    ScenePageSession next =
                            ScenePageSession.open(
                                    domain,
                                    host,
                                    host,
                                    c ->
                                            c.mount(
                                                    new SceneNode()
                                                            .size(Size.fill(1), Size.fill(1))
                                                            .onClick(() -> newClick[0]++)));
                    next.pointer(ScenePointerEvent.syntheticActivation(1, 1));
                    next.frame();
                    while (!tasks.isEmpty()) tasks.remove().run();
                    onClose(
                            () -> {
                                next.close();
                                while (!tasks.isEmpty()) tasks.remove().run();
                            });
                    require(
                            oldFallback[0] == 0 && newClick[0] == 1 && !next.ended(),
                            "旧ESC侵入新页或阻断新输入");
                });
        scenario(
                "handler结束页面不在routing内卸载或回放",
                () -> {
                    ReactiveDomain domain = ReactiveDomain.direct();
                    ScenePageSession[] current = {null};
                    int[] releases = {0};
                    current[0] =
                            ScenePageSession.open(
                                    domain,
                                    host,
                                    host,
                                    c -> {
                                        c.own(() -> releases[0]++);
                                        c.mount(
                                                new SceneNode()
                                                        .size(Size.fill(1), Size.fill(1))
                                                        .onClick(() -> current[0].close()));
                                    });
                    current[0].pointer(ScenePointerEvent.syntheticActivation(1, 1));
                    current[0].frame();
                    require(current[0].ended() && releases[0] == 1, "事件handler结束失败或残留");
                });
        for (boolean effectFailure : new boolean[] {false, true})
            scenario(
                    "关闭后失败仍释放页面/" + effectFailure,
                    () -> {
                        ReactiveDomain domain = ReactiveDomain.direct();
                        ScenePageSession[] current = {null};
                        SceneNode[] node = {null};
                        RuntimeException original = new IllegalArgumentException("关闭后失败");
                        int[] releases = {0};
                        current[0] =
                                ScenePageSession.open(
                                        domain,
                                        host,
                                        host,
                                        c -> {
                                            Signal<Boolean> fail = domain.signal(false);
                                            if (effectFailure)
                                                domain.effect(
                                                        c.scope(),
                                                        () -> {
                                                            if (fail.get()) throw original;
                                                        });
                                            c.own(() -> releases[0]++);
                                            c.own(
                                                    () -> {
                                                        throw new IllegalStateException("清理附加失败");
                                                    });
                                            node[0] =
                                                    new SceneNode()
                                                            .size(Size.fill(1), Size.fill(1))
                                                            .onClick(
                                                                    () -> {
                                                                        current[0].close();
                                                                        if (effectFailure)
                                                                            fail.set(true);
                                                                        else throw original;
                                                                    });
                                            c.mount(node[0]);
                                        });
                        Throwable caught = null;
                        current[0].pointer(ScenePointerEvent.syntheticActivation(1, 1));
                        try {
                            current[0].frame();
                        } catch (Throwable failure) {
                            caught = failure;
                        }
                        require(
                                caught == original
                                        && original.getSuppressed().length == 1
                                        && releases[0] == 1
                                        && node[0].runtime() == null,
                                "handler/Effect失败滞留清理或覆盖原异常");
                        current[0].close();
                    });
        scenario(
                "Effect自身关闭再抛Error仍在整轮结束清理",
                () -> {
                    ReactiveDomain domain = ReactiveDomain.direct();
                    ScenePageSession[] current = {null};
                    SceneNode[] node = {null};
                    AssertionError original = new AssertionError("Effect关闭后Error");
                    int[] releases = {0};
                    current[0] =
                            ScenePageSession.open(
                                    domain,
                                    host,
                                    host,
                                    c -> {
                                        Signal<Boolean> close = domain.signal(false);
                                        c.own(() -> releases[0]++);
                                        domain.effect(
                                                c.scope(),
                                                () -> {
                                                    if (close.get()) {
                                                        current[0].close();
                                                        throw original;
                                                    }
                                                });
                                        node[0] =
                                                new SceneNode()
                                                        .size(Size.fill(1), Size.fill(1))
                                                        .onClick(() -> close.set(true));
                                        c.mount(node[0]);
                                    });
                    Throwable caught = null;
                    current[0].pointer(ScenePointerEvent.syntheticActivation(1, 1));
                    try {
                        current[0].frame();
                    } catch (Throwable failure) {
                        caught = failure;
                    }
                    require(
                            caught == original && releases[0] == 1 && node[0].runtime() == null,
                            "Effect自身Error遗留清理或丢原异常");
                });
        scenario(
                "排队初始化前关闭不构造旧页",
                () -> {
                    ArrayDeque<Runnable> tasks = new ArrayDeque<>();
                    ReactiveDomain domain = new ReactiveDomain(tasks::add);
                    int[] builds = {0};
                    ScenePageSession page =
                            ScenePageSession.open(domain, host, host, c -> builds[0]++);
                    page.close();
                    while (!tasks.isEmpty()) tasks.remove().run();
                    require(builds[0] == 0, "已关闭接入仍初始化");
                });
        scenario(
                "构造失败原异常与清理异常保留且清完",
                () -> {
                    ReactiveDomain domain = ReactiveDomain.direct();
                    RuntimeException original = new IllegalArgumentException("构造失败");
                    int[] released = {0};
                    Throwable[] caught = {null};
                    try {
                        ScenePageSession.open(
                                domain,
                                host,
                                host,
                                c -> {
                                    c.own(() -> released[0]++);
                                    c.own(
                                            () -> {
                                                throw new IllegalStateException("清理失败");
                                            });
                                    c.mount(new SceneNode());
                                    throw original;
                                });
                    } catch (Throwable failure) {
                        caught[0] = failure;
                    }
                    require(
                            caught[0] == original
                                    && original.getSuppressed().length == 1
                                    && released[0] == 1,
                            "初始化丢失原失败或漏清理");
                });
        scenario(
                "外部executor初始化Effect失败仍完成清理",
                () -> {
                    ArrayDeque<Runnable> tasks = new ArrayDeque<>();
                    ReactiveDomain domain = new ReactiveDomain(tasks::add);
                    RuntimeException original = new IllegalStateException("Effect失败");
                    int[] released = {0};
                    ScenePageSession page =
                            ScenePageSession.open(
                                    domain,
                                    host,
                                    host,
                                    c -> {
                                        c.own(() -> released[0]++);
                                        c.mount(new SceneNode());
                                        domain.effect(
                                                c.scope(),
                                                () -> {
                                                    throw original;
                                                });
                                    });
                    Throwable caught = null;
                    try {
                        while (!tasks.isEmpty()) tasks.remove().run();
                    } catch (Throwable failure) {
                        caught = failure;
                    }
                    require(
                            caught == original && page.ended() && released[0] == 1,
                            "异步Effect失败未归初始化边界");
                    page.frame();
                    page.close();
                });
        scenario(
                "释放Error仍清理余下资源且结束幂等",
                () -> {
                    ReactiveDomain domain = ReactiveDomain.direct();
                    AssertionError original = new AssertionError("释放Error");
                    int[] released = {0};
                    ScenePageSession page =
                            ScenePageSession.open(
                                    domain,
                                    host,
                                    host,
                                    c -> {
                                        c.own(() -> released[0]++);
                                        c.own(
                                                () -> {
                                                    throw original;
                                                });
                                        c.mount(new SceneNode());
                                    });
                    Throwable caught = null;
                    try {
                        page.close();
                    } catch (Throwable failure) {
                        caught = failure;
                    }
                    page.close();
                    page.frame();
                    require(
                            caught == original && released[0] == 1 && page.ended(),
                            "Error中止了其他资源释放");
                });
        for (boolean fatal : new boolean[] {false, true})
            scenario(
                    "失焦扩展失败仍结束全部挂载与订阅/" + fatal,
                    () -> {
                        ReactiveDomain domain = ReactiveDomain.direct();
                        Throwable original =
                                fatal
                                        ? new AssertionError("失焦扩展Error")
                                        : new IllegalArgumentException("失焦扩展失败");
                        RuntimeException cleanup = new IllegalStateException("挂载资源释放失败");
                        Signal<String>[] external = new Signal[1];
                        SceneRuntime[] runtime = {null};
                        SceneNode[] nodes = new SceneNode[3];
                        MountHandle[] mounts = new MountHandle[3];
                        int[] released = new int[3],
                                notifications = {0},
                                lostFocus = {0},
                                pageReleased = {0};
                        domain.execute(() -> external[0] = domain.signal("初值"));
                        ScenePageSession page =
                                ScenePageSession.open(
                                        domain,
                                        host,
                                        host,
                                        context -> {
                                            runtime[0] = context.runtime();
                                            context.own(() -> pageReleased[0]++);
                                            nodes[0] =
                                                    new SceneNode()
                                                            .focusable(true)
                                                            .size(Size.fixed(20), Size.fixed(20))
                                                            .control(
                                                                    new SceneControl() {
                                                                        @Override
                                                                        public MeasuredSize measure(
                                                                                SceneNode node,
                                                                                Constraints limits,
                                                                                SceneTextMeasurer
                                                                                        text) {
                                                                            return new MeasuredSize(
                                                                                    20, 20);
                                                                        }

                                                                        @Override
                                                                        public ChildPlacements
                                                                                arrange(
                                                                                        SceneNode
                                                                                                node,
                                                                                        LayoutBox
                                                                                                allocated, SceneChildMeasurer childMeasurer) {
                                                                            return new ChildPlacements(
                                                                                    Collections
                                                                                            .singletonList(
                                                                                                    new ChildPlacements
                                                                                                            .Entry(
                                                                                                            nodes[
                                                                                                                    1],
                                                                                                            new LayoutBox(
                                                                                                                    0,
                                                                                                                    0,
                                                                                                                    10,
                                                                                                                    10))));
                                                                        }

                                                                        @Override
                                                                        public void paint(
                                                                                SceneNode node,
                                                                                SceneGeometryView
                                                                                        geometry,
                                                                                PaintPlanBuilder
                                                                                        target) {}

                                                                        @Override
                                                                        public void focusChanged(
                                                                                SceneNode node,
                                                                                boolean focused,
                                                                                boolean eligible) {
                                                                            if (!focused) {
                                                                                lostFocus[0]++;
                                                                                if (original
                                                                                        instanceof
                                                                                        Error)
                                                                                    throw (Error)
                                                                                            original;
                                                                                throw (RuntimeException)
                                                                                        original;
                                                                            }
                                                                        }
                                                                    });
                                            nodes[1] = new SceneNode();
                                            nodes[2] = new SceneNode();
                                            nodes[0].add(nodes[1]);
                                            for (int i = 0; i < nodes.length; i++) {
                                                final int index = i;
                                                nodes[i].bind(
                                                        (activeRuntime, node, owner) -> {
                                                            owner.own(
                                                                    () -> {
                                                                        released[index]++;
                                                                        if (index == 1)
                                                                            throw cleanup;
                                                                    });
                                                            Observation observation =
                                                                    domain.observe(
                                                                            owner,
                                                                            () -> external[0].get(),
                                                                            () -> {
                                                                                notifications[0]++;
                                                                                activeRuntime
                                                                                        .invalidateBinding(
                                                                                                node);
                                                                            });
                                                            return new BindingActivation() {
                                                                @Override
                                                                public void refresh() {
                                                                    observation.refresh();
                                                                }

                                                                @Override
                                                                public void close() {
                                                                    observation.close();
                                                                }
                                                            };
                                                        });
                                            }
                                            context.mount(nodes[0]);
                                            context.mount(nodes[2]);
                                            for (int i = 0; i < nodes.length; i++)
                                                mounts[i] = runtime[0].captureMount(nodes[i]);
                                        });
                        onClose(page::close);
                        page.frame();
                        domain.execute(
                                () -> {
                                    require(
                                            runtime[0].focusRouter().focused().get() == nodes[0],
                                            "反例未进入真实控件焦点");
                                    external[0].set("关闭前变化");
                                    require(notifications[0] == nodes.length, "反例未建立全部真实挂载订阅");
                                });
                        page.frame();
                        Throwable caught = null;
                        try {
                            page.close();
                        } catch (Throwable failure) {
                            caught = failure;
                        }
                        require(
                                caught == original
                                        && java.util.Arrays.asList(original.getSuppressed())
                                                .contains(cleanup),
                                "关闭丢失原失焦错误或附加释放错误");
                        domain.execute(
                                () -> {
                                    require(
                                            page.ended()
                                                    && !runtime[0].alive()
                                                    && runtime[0].roots().isEmpty(),
                                            "失焦失败留下存活运行时或根挂载");
                                    for (int i = 0; i < nodes.length; i++)
                                        require(
                                                nodes[i].runtime() == null
                                                        && !mounts[i].active()
                                                        && released[i] == 1,
                                                "失焦失败跳过节点或所属资源释放");
                                    external[0].set("关闭后变化");
                                    require(
                                            "关闭后变化".equals(external[0].get())
                                                    && notifications[0] == nodes.length,
                                            "页面关闭破坏外部状态或残留挂载订阅");
                                    runtime[0].close();
                                });
                        page.close();
                        require(lostFocus[0] == 1 && pageReleased[0] == 1, "重复结束重入扩展或重复释放页面资源");
                        for (int count : released) require(count == 1, "重复结束再次释放挂载资源");
                    });
        scenario(
                "只读不能结束会话",
                () -> {
                    ReactiveDomain domain = ReactiveDomain.direct();
                    ScenePageSession page =
                            ScenePageSession.open(
                                    domain, host, host, c -> c.mount(new SceneNode()));
                    onClose(page::close);
                    domain.execute(
                            () -> {
                                try {
                                    domain.readOnly(
                                            () -> {
                                                page.close();
                                                return null;
                                            });
                                    throw new AssertionError("只读释放未拒绝");
                                } catch (IllegalStateException expected) {
                                    require(!page.ended(), "拒绝之前已结束");
                                }
                            });
                    page.frame();
                });
    }

    public static void main(String[] args) throws Exception {
        new PageSessionScenario().run(args);
    }
}
