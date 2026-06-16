package club.heiqi.uilib.ui.reactive;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Owner 树形作用域契约测试（信条三：组件挂载/卸载）。
 *
 * <p>覆盖：嵌套子作用域、递归 dispose、effect 自动归属当前作用域、cleanup 回调、dispose 后从父摘除。</p>
 */
public class OwnerTreeTest {

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        ReactiveScheduler.get().reset();
    }

    @Test
    public void effectCreatedInsideRunAutoAttachesToOwner() {
        Owner owner = new Owner();
        Signal<Integer> s = Signal.create(0);
        List<Integer> seen = new ArrayList<>();

        owner.run(() -> Effect.create(() -> seen.add(s.get())));
        ReactiveScheduler.get().flush();
        Assert.assertEquals(1, seen.size());

        // owner dispose 后，自动归属的 effect 应停止重跑
        owner.dispose();
        s.set(1);
        ReactiveScheduler.get().flush();
        Assert.assertEquals("dispose 后自动归属的 effect 不应重跑", 1, seen.size());
    }

    @Test
    public void disposeRecursesIntoChildOwners() {
        Owner parent = new Owner();
        Owner child = parent.createChild();
        Signal<Integer> s = Signal.create(0);
        List<Integer> seen = new ArrayList<>();

        child.createEffect(() -> seen.add(s.get()));
        ReactiveScheduler.get().flush();
        Assert.assertEquals(1, seen.size());

        // dispose 父作用域应递归清理子作用域的 effect
        parent.dispose();
        s.set(1);
        ReactiveScheduler.get().flush();
        Assert.assertEquals("父 dispose 应递归清理子 effect", 1, seen.size());
    }

    @Test
    public void cleanupRunsOnDisposeAfterChildren() {
        Owner parent = new Owner();
        List<String> order = new ArrayList<>();

        Owner child = parent.createChild();
        child.onCleanup(() -> order.add("child-cleanup"));
        parent.onCleanup(() -> order.add("parent-cleanup"));

        parent.dispose();
        // 子作用域先清理，父 cleanup 后执行
        Assert.assertEquals(Arrays.asList("child-cleanup", "parent-cleanup"), order);
    }

    @Test
    public void cleanupRunsImmediatelyWhenRegisteredOnDisposedOwner() {
        Owner owner = new Owner();
        owner.dispose();

        List<String> log = new ArrayList<>();
        owner.onCleanup(() -> log.add("late"));
        Assert.assertEquals("在已 dispose 的 owner 上登记 cleanup 应立即执行", 1, log.size());
    }

    @Test
    public void disposingChildRemovesItFromParent() {
        Owner parent = new Owner();
        Owner child = parent.createChild();

        child.dispose();
        Assert.assertTrue(child.isDisposed());
        // 子 dispose 后再 dispose 父不应出错（child 已从父摘除）
        parent.dispose();
        Assert.assertTrue(parent.isDisposed());
    }

    @Test
    public void effectCreatedOnDisposedOwnerIsImmediatelyDisposed() {
        Owner owner = new Owner();
        owner.dispose();

        Signal<Integer> s = Signal.create(0);
        List<Integer> seen = new ArrayList<>();
        owner.createEffect(() -> seen.add(s.get()));
        ReactiveScheduler.get().flush();

        // 已 dispose 的 owner 创建的 effect 应被立即 dispose，不参与 flush
        Assert.assertTrue(seen.isEmpty());
    }

    @Test
    public void nestedRunRestoresOuterOwnerContext() {
        Owner outer = new Owner();
        Owner inner = new Owner();
        Signal<Integer> s = Signal.create(0);
        List<Integer> outerSeen = new ArrayList<>();
        List<Integer> innerSeen = new ArrayList<>();

        outer.run(() -> {
            Effect.create(() -> outerSeen.add(s.get()));
            inner.run(() -> Effect.create(() -> innerSeen.add(s.get())));
            // inner.run 结束后应恢复到 outer 上下文：此处再建的 effect 归属 outer
            Effect.create(() -> outerSeen.add(s.get() * 100));
        });
        ReactiveScheduler.get().flush();
        Assert.assertEquals(2, outerSeen.size());
        Assert.assertEquals(1, innerSeen.size());

        // dispose inner 只清 inner 的 effect，不影响 outer
        inner.dispose();
        s.set(1);
        ReactiveScheduler.get().flush();
        Assert.assertEquals("inner dispose 不应影响 outer effect", 4, outerSeen.size());
        Assert.assertEquals("inner effect 已停止", 1, innerSeen.size());
    }
}
