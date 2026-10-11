package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.BindingActivation;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** 可直启的状态和挂载合同场景；生命周期共享正式页面入口，反例直接观察原runtime。 */
public final class SceneCoreLoopTest extends TestApplication {
    private void lifecycleScenario(ReactiveDomain domain) {
        SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost();
        ScenePageSession session =
                ScenePageSession.open(
                        domain,
                        host,
                        host,
                        context -> {
                            Signal<Integer> operations = domain.signal(0);
                            SceneRuntime runtime = context.runtime();
                            SceneNode oldParent = new SceneNode();
                            SceneNode newParent = new SceneNode().enabled(false);
                            MountHandle oldRoot = context.mount(oldParent);
                            MountHandle newRoot = context.mount(newParent);
                            List<String> releases = new ArrayList<>();
                            SceneNode action =
                                    new SceneNode()
                                            .bind(
                                                    NodeBinding.value(
                                                            operations,
                                                            (node, value) ->
                                                                    node.text("操作:" + value)))
                                            .bind(
                                                    (scene, node, owner) ->
                                                            new BindingActivation() {
                                                                @Override
                                                                public void refresh() {}

                                                                @Override
                                                                public void close() {
                                                                    releases.add("关闭");
                                                                }
                                                            })
                                            .onClick(() -> operations.set(operations.get() + 1));
                            UUID identity = action.id();
                            MountHandle active = runtime.attach(oldParent, action);
                            runtime.preparePresentation();
                            require(action.text().equals("操作:0"), "挂载读取当前声明");
                            runtime.dispatchClick(action);
                            runtime.preparePresentation();
                            require(
                                    operations.get() == 1 && action.text().equals("操作:1"),
                                    "操作改数据而非直接改节点");
                            try {
                                action.text("错误");
                                throw new AssertionError("业务不能直接改挂载节点");
                            } catch (IllegalStateException expected) {
                            }
                            active.moveTo(newParent);
                            oldRoot.destroy();
                            require(
                                    releases.isEmpty() && action.parent() == newParent,
                                    "销毁旧父不能释放移出节点");
                            runtime.dispatchClick(action);
                            require(operations.get() == 1, "移后按新父输入资格隔离");
                            active.detach();
                            require(releases.size() == 1 && action.runtime() == null, "卸载恰好释放本次激活");
                            operations.set(7);
                            MountHandle reattached = runtime.attach(null, action);
                            runtime.preparePresentation();
                            require(
                                    action.id().equals(identity) && action.text().equals("操作:7"),
                                    "重挂载身份不变且读取最新状态");
                            runtime.dispatchClick(action);
                            runtime.preparePresentation();
                            require(operations.get() == 8, "重挂载操作只执行一次");
                            try {
                                active.moveTo(null);
                                throw new AssertionError("旧句柄不能复活");
                            } catch (IllegalStateException expected) {
                            }
                            reattached.moveTo(newParent);
                            newRoot.destroy();
                            require(action.destroyed() && releases.size() == 2, "当前父永久删除销毁当前子树");
                            operations.set(9);
                            require(operations.get() == 9, "销毁控件不销毁独立业务状态");
                        });
        onClose(session::close);
    }

    public static void main(String[] args) throws Exception {
        new SceneCoreLoopTest().run(args);
    }

    @Override
    protected void runTests() throws Exception {
        ReactiveDomain domain = ReactiveDomain.direct();
        scenario(
                "写后可读与批量派生",
                () -> {
                    List<String> notifications = new ArrayList<>();
                    domain.execute(
                            () -> {
                                Owner business = domain.owner();
                                Signal<Integer> count = domain.signal(1);
                                Signal<Boolean> selected = domain.signal(true);
                                Signal<String> primary = domain.signal("已选");
                                Signal<String> alternative = domain.signal("候选");
                                Computed<Integer> available =
                                        domain.computed(business, () -> count.get() + 1);
                                Computed<String> title =
                                        domain.computed(
                                                business,
                                                () ->
                                                        (selected.get()
                                                                        ? primary.get()
                                                                        : alternative.get())
                                                                + ":"
                                                                + available.get() * 2);
                                domain.effect(business, () -> notifications.add(title.get()));
                                require(title.get().equals("已选:4"), "首次派生应为当前结果");
                                count.set(2);
                                require(
                                        count.get() == 2 && title.get().equals("已选:6"),
                                        "写后直接状态及多级派生必须立即可读");
                                domain.batch(
                                        () -> {
                                            count.set(count.get() + 1);
                                            count.set(count.get() + 1);
                                        });
                                require(
                                        count.get() == 4 && title.get().equals("已选:10"),
                                        "batch 不能隐藏状态写入");
                                require(notifications.isEmpty(), "纯派生读取不能推进副作用");
                            });
                    require(notifications.equals(Arrays.asList("已选:10")), "无 UI 帧的首次副作用在操作结束执行");
                });
        scenario(
                "副作用切换分支",
                () -> {
                    domain.execute(
                            () -> {
                                Owner business = domain.owner();
                                Signal<Boolean> useLeft = domain.signal(true);
                                Signal<Integer> left = domain.signal(1), right = domain.signal(2);
                                Computed<Integer> chosen =
                                        domain.computed(
                                                business,
                                                () -> useLeft.get() ? left.get() : right.get());
                                List<Integer> observed = new ArrayList<>();
                                domain.effect(business, () -> observed.add(chosen.get()));
                                domain.execute(() -> require(chosen.get() == 1, "分支初值"));
                                business.own(() -> require(observed.size() <= 2, "业务作用域释放"));
                                domain.effect(
                                        business,
                                        () -> {
                                            if (observed.size() == 1) useLeft.set(false);
                                        });
                            });
                });
        scenario(
                "动态依赖与作用域释放",
                () -> {
                    List<Integer> branch = new ArrayList<>();
                    Signal<?>[] source = new Signal<?>[3];
                    Owner[] scope = new Owner[1];
                    Computed<?>[] value = new Computed<?>[1];
                    domain.execute(
                            () -> {
                                scope[0] = domain.owner();
                                Signal<Boolean> flag = domain.signal(true);
                                Signal<Integer> left = domain.signal(3), right = domain.signal(8);
                                source[0] = flag;
                                source[1] = left;
                                source[2] = right;
                                Computed<Integer> choice =
                                        domain.computed(
                                                scope[0],
                                                () -> flag.get() ? left.get() : right.get());
                                value[0] = choice;
                                domain.effect(scope[0], () -> branch.add(choice.get()));
                            });
                    domain.execute(() -> ((Signal<Boolean>) source[0]).set(false));
                    domain.execute(() -> ((Signal<Integer>) source[1]).set(99));
                    require(branch.equals(Arrays.asList(3, 8)), "切换分支后旧依赖不能继续触发副作用");
                    domain.execute(() -> scope[0].dispose());
                    domain.execute(() -> ((Signal<Integer>) source[2]).set(9));
                    require(branch.equals(Arrays.asList(3, 8)), "释放作用域后退订，独立状态仍可更新");
                    domain.execute(
                            () -> {
                                try {
                                    value[0].get();
                                    throw new AssertionError("已释放派生不可读取");
                                } catch (IllegalStateException expected) {
                                    require(expected.getMessage().contains("释放"), "释放诊断");
                                }
                            });
                    try {
                        source[0].get();
                        throw new AssertionError("域外访问必须诊断");
                    } catch (IllegalStateException expected) {
                    }
                });
        scenario(
                "副作用非递归顺序推进",
                () -> {
                    List<Integer> ordered = new ArrayList<>();
                    domain.execute(
                            () -> {
                                Signal<Integer> step = domain.signal(0);
                                Owner business = domain.owner();
                                domain.effect(
                                        business,
                                        () -> {
                                            int current = step.get();
                                            ordered.add(current);
                                            if (current < 3) step.set(current + 1);
                                            require(
                                                    ordered.size() == current + 1,
                                                    "Effect 不得递归重入自身");
                                        });
                            });
                    require(ordered.equals(Arrays.asList(0, 1, 2, 3)), "Effect 回调后顺序推进");
                });
        scenario(
                "独立执行器驱动",
                () -> {
                    ExecutorService worker = Executors.newSingleThreadExecutor();
                    try {
                        ReactiveDomain async = new ReactiveDomain(worker);
                        List<Integer> asyncObserved =
                                Collections.synchronizedList(new ArrayList<>());
                        CountDownLatch finished = new CountDownLatch(1);
                        async.execute(
                                () -> {
                                    Signal<Integer> count = async.signal(7);
                                    Computed<Integer> doubled =
                                            async.computed(() -> count.get() * 2);
                                    async.effect(
                                            async.owner(),
                                            () -> {
                                                asyncObserved.add(doubled.get());
                                                finished.countDown();
                                            });
                                    count.set(8);
                                    require(doubled.get() == 16, "后台合法域内读取当前派生");
                                });
                        require(finished.await(5, TimeUnit.SECONDS), "独立执行器副作用无需宿主帧");
                        require(asyncObserved.equals(Arrays.asList(16)), "异步域实际执行结果");
                    } finally {
                        worker.shutdownNow();
                    }
                });
        scenario("节点挂载移动卸载生命周期", () -> lifecycleScenario(domain));
        scenario(
                "释放失败仍永久销毁所属子树",
                () -> domain.execute(() -> {
                    SceneRuntime runtime = new SceneRuntime(domain);
                    List<String> released = new ArrayList<>();
                    RuntimeException failure = new IllegalStateException("释放失败");
                    SceneNode child = new SceneNode().text("独立子项");
                    SceneNode root = new SceneNode().add(child).bind((scene, node, owner) -> {
                        owner.own(() -> released.add("其余资源"));
                        owner.own(() -> { throw failure; });
                        return new BindingActivation() {
                            public void refresh() {}
                            public void close() { released.add("绑定"); }
                        };
                    });
                    MountHandle handle = runtime.attach(null, root);
                    try {
                        try {
                            handle.destroy();
                            throw new AssertionError("必须报告原始释放错误");
                        } catch (RuntimeException expected) {
                            require(expected == failure, "永久销毁保留原始释放错误");
                        }
                        require(released.equals(Arrays.asList("绑定", "其余资源")), "释放失败不阻断其他资源");
                        require(root.destroyed() && child.destroyed(), "释放失败仍终止整棵所属子树");
                        require(root.runtime() == null && child.runtime() == null && child.parent() == null,
                                "销毁释放挂载与所属关系");
                        for (SceneNode ended : Arrays.asList(root, child)) {
                            try {
                                runtime.attach(null, ended);
                                throw new AssertionError("永久销毁的节点不得重新挂载");
                            } catch (IllegalArgumentException expected) {}
                        }
                        SceneNode retained = new SceneNode();
                        MountHandle old = runtime.attach(null, retained);
                        old.detach();
                        MountHandle current = runtime.attach(null, retained);
                        try {
                            old.destroy();
                            throw new AssertionError("旧句柄必须拒绝销毁新的挂载");
                        } catch (IllegalStateException expected) {}
                        require(!retained.destroyed() && retained.runtime() == runtime,
                                "无效句柄不终止新的挂载生命周期");
                        current.destroy();
                    } finally {
                        runtime.close();
                    }
                }));
        scenario("软件scene完整生产链路", () -> new SoftwareSceneScenario().run());
        System.out.println("无平台业务状态/派生/作用域/副作用及节点生命周期场景通过");
    }
}
