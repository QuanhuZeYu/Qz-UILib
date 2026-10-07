package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.BindingActivation;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** 可直启的真实业务场景；无需测试库或平台类，后续接通场景树与绘制计划。 */
public final class SceneCoreLoopTest {
    private static void lifecycleScenario(ReactiveDomain domain) {
        domain.execute(() -> {
            Signal<Integer> operations = domain.signal(0);
            SceneRuntime runtime = new SceneRuntime(domain);
            SceneNode oldParent = new SceneNode();
            SceneNode newParent = new SceneNode().enabled(false);
            MountHandle oldRoot = runtime.attach(null, oldParent);
            MountHandle newRoot = runtime.attach(null, newParent);
            List<String> releases = new ArrayList<>();
            SceneNode action = new SceneNode().bind(NodeBinding.value(operations, (node, value) -> node.text("操作:" + value)))
                .bind((scene, node, owner) -> new BindingActivation() {
                    @Override public void refresh() { }
                    @Override public void close() { releases.add("关闭"); }
                }).onClick(() -> operations.set(operations.get() + 1));
            UUID identity = action.id();
            MountHandle active = runtime.attach(oldParent, action);
            runtime.preparePresentation();
            require(action.text().equals("操作:0"), "挂载读取当前声明");
            runtime.dispatchClick(action); runtime.preparePresentation();
            require(operations.get() == 1 && action.text().equals("操作:1"), "操作改数据而非直接改节点");
            try { action.text("错误"); throw new AssertionError("业务不能直接改挂载节点"); }
            catch (IllegalStateException expected) { }
            active.moveTo(newParent);
            oldRoot.destroy();
            require(releases.isEmpty() && action.parent() == newParent, "销毁旧父不能释放移出节点");
            runtime.dispatchClick(action);
            require(operations.get() == 1, "移后按新父输入资格隔离");
            active.detach();
            require(releases.size() == 1 && action.runtime() == null, "卸载恰好释放本次激活");
            operations.set(7);
            MountHandle reattached = runtime.attach(null, action);
            runtime.preparePresentation();
            require(action.id().equals(identity) && action.text().equals("操作:7"), "重挂载身份不变且读取最新状态");
            runtime.dispatchClick(action); runtime.preparePresentation();
            require(operations.get() == 8, "重挂载操作只执行一次");
            try { active.moveTo(null); throw new AssertionError("旧句柄不能复活"); }
            catch (IllegalStateException expected) { }
            reattached.moveTo(newParent);
            newRoot.destroy();
            require(action.destroyed() && releases.size() == 2, "当前父永久删除销毁当前子树");
            operations.set(9);
            require(operations.get() == 9, "销毁控件不销毁独立业务状态");
            runtime.close();
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        ReactiveDomain domain = ReactiveDomain.direct();
        List<String> notifications = new ArrayList<>();
        domain.execute(() -> {
            Owner business = domain.owner();
            Signal<Integer> count = domain.signal(1);
            Signal<Boolean> selected = domain.signal(true);
            Signal<String> primary = domain.signal("已选");
            Signal<String> alternative = domain.signal("候选");
            Computed<Integer> available = domain.computed(business, () -> count.get() + 1);
            Computed<String> title = domain.computed(business, () ->
                (selected.get() ? primary.get() : alternative.get()) + ":" + available.get() * 2);
            domain.effect(business, () -> notifications.add(title.get()));
            require(title.get().equals("已选:4"), "首次派生应为当前结果");
            count.set(2);
            require(count.get() == 2 && title.get().equals("已选:6"), "写后直接状态及多级派生必须立即可读");
            domain.batch(() -> { count.set(count.get() + 1); count.set(count.get() + 1); });
            require(count.get() == 4 && title.get().equals("已选:10"), "batch 不能隐藏状态写入");
            require(notifications.isEmpty(), "纯派生读取不能推进副作用");
        });
        require(notifications.equals(Arrays.asList("已选:10")), "无 UI 帧的首次副作用在操作结束执行");
        domain.execute(() -> {
            Owner business = domain.owner();
            Signal<Boolean> useLeft = domain.signal(true);
            Signal<Integer> left = domain.signal(1), right = domain.signal(2);
            Computed<Integer> chosen = domain.computed(business, () -> useLeft.get() ? left.get() : right.get());
            List<Integer> observed = new ArrayList<>();
            domain.effect(business, () -> observed.add(chosen.get()));
            domain.execute(() -> require(chosen.get() == 1, "分支初值"));
            business.own(() -> require(observed.size() <= 2, "业务作用域释放"));
            domain.effect(business, () -> { if (observed.size() == 1) useLeft.set(false); });
        });
        List<Integer> branch = new ArrayList<>();
        Signal<?>[] source = new Signal<?>[3];
        Owner[] scope = new Owner[1];
        Computed<?>[] value = new Computed<?>[1];
        domain.execute(() -> {
            scope[0] = domain.owner();
            Signal<Boolean> flag = domain.signal(true);
            Signal<Integer> left = domain.signal(3), right = domain.signal(8);
            source[0] = flag; source[1] = left; source[2] = right;
            Computed<Integer> choice = domain.computed(scope[0], () -> flag.get() ? left.get() : right.get());
            value[0] = choice;
            domain.effect(scope[0], () -> branch.add(choice.get()));
        });
        domain.execute(() -> ((Signal<Boolean>) source[0]).set(false));
        domain.execute(() -> ((Signal<Integer>) source[1]).set(99));
        require(branch.equals(Arrays.asList(3, 8)), "切换分支后旧依赖不能继续触发副作用");
        domain.execute(() -> scope[0].dispose());
        domain.execute(() -> ((Signal<Integer>) source[2]).set(9));
        require(branch.equals(Arrays.asList(3, 8)), "释放作用域后退订，独立状态仍可更新");
        domain.execute(() -> {
            try { value[0].get(); throw new AssertionError("已释放派生不可读取"); }
            catch (IllegalStateException expected) { require(expected.getMessage().contains("释放"), "释放诊断"); }
        });
        try { source[0].get(); throw new AssertionError("域外访问必须诊断"); }
        catch (IllegalStateException expected) { }
        List<Integer> ordered = new ArrayList<>();
        domain.execute(() -> {
            Signal<Integer> step = domain.signal(0);
            Owner business = domain.owner();
            domain.effect(business, () -> {
                int current = step.get(); ordered.add(current);
                if (current < 3) step.set(current + 1);
                require(ordered.size() == current + 1, "Effect 不得递归重入自身");
            });
        });
        require(ordered.equals(Arrays.asList(0, 1, 2, 3)), "Effect 回调后顺序推进");
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            ReactiveDomain async = new ReactiveDomain(worker);
            List<Integer> asyncObserved = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch finished = new CountDownLatch(1);
            async.execute(() -> {
                Signal<Integer> count = async.signal(7);
                Computed<Integer> doubled = async.computed(() -> count.get() * 2);
                async.effect(async.owner(), () -> { asyncObserved.add(doubled.get()); finished.countDown(); });
                count.set(8);
                require(doubled.get() == 16, "后台合法域内读取当前派生");
            });
            require(finished.await(5, TimeUnit.SECONDS), "独立执行器副作用无需宿主帧");
            require(asyncObserved.equals(Arrays.asList(16)), "异步域实际执行结果");
        } finally { worker.shutdownNow(); }
        lifecycleScenario(domain);
        SoftwareSceneScenario.run();
        System.out.println("无平台业务状态/派生/作用域/副作用及节点生命周期场景通过");
    }
}
