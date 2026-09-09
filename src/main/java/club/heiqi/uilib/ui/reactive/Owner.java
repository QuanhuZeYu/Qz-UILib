package club.heiqi.uilib.ui.reactive;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 生命周期作用域（信条三：组件挂载/卸载）。
 *
 * <p>Owner 形成一棵树：每个组件挂载时建立一个子 Owner，组件内创建的 effect 与子组件 Owner 都归属于它。
 * 卸载时 {@link #dispose()} 递归清理整棵子树——先 dispose 子 Owner、再释放本作用域 effect、最后运行 cleanup 回调，
 * 一次性回收订阅与资源，互不波及兄弟作用域。</p>
 *
 * <p><b>自动归属</b>：{@link #run(Runnable)} 在 {@link ReactiveContext} 当前 owner 上下文中执行 body，
 * 期间新建的 {@link Effect} 与子 {@link Owner} 自动 attach 到本作用域，无需手动传递。这是「组件函数只跑一次、
 * 动态行为落在 effect 里、且 effect 不泄漏」（I3）的机制基础。</p>
 */
public final class Owner {

    private final List<Effect> effects = new ArrayList<>();
    private final List<Owner> children = new ArrayList<>();
    private final List<Runnable> cleanups = new ArrayList<>();
    private final Owner parent;
    private boolean disposed = false;

    /** 作用域上下文表（懒创建）：键为调用方自持的标记对象，供子作用域沿父链继承。 */
    private Map<Object, Object> scopeValues;

    /** 创建一个根 Owner（无父作用域）。 */
    public Owner() {
        this.parent = null;
    }

    private Owner(Owner parent) {
        this.parent = parent;
    }

    /**
     * 返回当前激活的 Owner 作用域（{@link #run(Runnable)} 上下文内），无激活作用域时返回 {@code null}。
     *
     * @return 当前作用域或 {@code null}
     */
    public static Owner current() {
        return ReactiveContext.getCurrentOwner();
    }

    /**
     * 在本作用域内创建一个子 Owner。
     *
     * @return 子作用域
     */
    public Owner createChild() {
        Owner child = new Owner(this);
        if (!disposed) {
            children.add(child);
        }
        return child;
    }

    /**
     * 在本作用域内创建一个 effect，生命周期绑定到此 Owner。
     *
     * @param body effect 体（I3：动态行为的唯一落点）
     * @return 创建的 effect（通常不需要直接持有）
     */
    public Effect createEffect(Runnable body) {
        Owner prev = ReactiveContext.setCurrentOwner(this);
        try {
            // Effect 构造时读 current-owner 自动归属本作用域（单次 attach，避免重复）。
            return new Effect(body);
        } finally {
            ReactiveContext.setCurrentOwner(prev);
        }
    }

    /**
     * 在本作用域上下文中执行 {@code body}：期间新建的 effect / 子 Owner 自动归属本作用域。
     *
     * <p>用于「组件函数只跑一次」的挂载：组件构建期调用的 {@code createEffect}/{@code bind} 自动 attach 到本 Owner，
     * 卸载时随本 Owner 一并清理。</p>
     *
     * @param body 在本作用域上下文中执行一次的构建逻辑
     */
    public void run(Runnable body) {
        Owner prev = ReactiveContext.setCurrentOwner(this);
        try {
            body.run();
        } finally {
            ReactiveContext.setCurrentOwner(prev);
        }
    }

    /**
     * 登记一个卸载回调：本作用域 {@link #dispose()} 时（在子树清理后）执行一次。
     *
     * <p>组件用它在卸载时移除自己挂载的 DOM 节点、注销外部监听等。</p>
     *
     * @param cleanup 卸载回调
     */
    public void onCleanup(Runnable cleanup) {
        if (cleanup == null) {
            return;
        }
        if (disposed) {
            cleanup.run();
            return;
        }
        cleanups.add(cleanup);
    }

    /** 由 {@link Effect} 构造时自动归属调用，把 effect attach 到本作用域。 */
    void attach(Effect e) {
        if (disposed) {
            e.dispose();
            return;
        }
        effects.add(e);
    }

    /**
     * 递归销毁整棵子树：子 Owner → 本作用域 effect → cleanup 回调，并从父作用域摘除。
     * 重复调用安全。
     */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        for (int i = children.size() - 1; i >= 0; i--) {
            children.get(i).dispose();
        }
        children.clear();
        for (Effect e : effects) {
            e.dispose();
        }
        effects.clear();
        for (int i = cleanups.size() - 1; i >= 0; i--) {
            cleanups.get(i).run();
        }
        cleanups.clear();
        // 作用域销毁即上下文不可见：清表释放引用（findScope 也已对 disposed 直接返回 null）。
        if (scopeValues != null) {
            scopeValues.clear();
            scopeValues = null;
        }
        if (parent != null) {
            parent.children.remove(this);
        }
    }

    /**
     * 在本作用域写入一个上下文值，供本作用域与子作用域读取。
     *
     * <p>键是调用方自持的标记对象（建议 {@code static final} 常量），相等按 {@link Object#equals}；
     * 值不做拷贝、不做深比较。{@code value} 为 null 表示移除本作用域上的该键，父作用域的值重新可见。
     * 本作用域已 {@link #dispose()} 时写入无效（作用域已不可见）。</p>
     *
     * <p>本能力只提供「值沿 Owner 树继承」这一件事，不建立选择器、优先级合并或样式级联：
     * 冲突消解由消费方（如主题解析）自行定义。</p>
     *
     * @param key   非 null 标记键
     * @param value 上下文值，null 表示移除
     */
    public void setScope(Object key, Object value) {
        if (key == null) {
            throw new IllegalArgumentException("key 不可为 null");
        }
        if (value == null) {
            if (scopeValues != null) {
                scopeValues.remove(key);
            }
            return;
        }
        if (disposed) {
            return;
        }
        if (scopeValues == null) {
            scopeValues = new HashMap<Object, Object>();
        }
        scopeValues.put(key, value);
    }

    /**
     * 沿本作用域到根作用域向上查找首个非 null 上下文值。
     *
     * <p>本作用域已 {@link #dispose()} 时返回 null（作用域销毁即上下文不可见）；父链上出现
     * 已销毁的祖先同样按断链处理。键不存在时返回 null。</p>
     *
     * @param key 标记键，null 返回 null
     * @return 上下文值或 null
     */
    public Object findScope(Object key) {
        if (key == null) {
            return null;
        }
        for (Owner owner = this; owner != null; owner = owner.parent) {
            if (owner.disposed) {
                return null;
            }
            if (owner.scopeValues != null) {
                Object value = owner.scopeValues.get(key);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    /**
     * {@link #findScope(Object)} 的类型化便捷入口：值类型不匹配时返回 null（不抛异常）。
     *
     * @param key  标记键
     * @param type 期望值类型，null 返回 null
     * @param <T>  值类型
     * @return 类型匹配的上下文值，否则 null
     */
    public <T> T findScope(Object key, Class<T> type) {
        Object value = findScope(key);
        return type != null && type.isInstance(value) ? type.cast(value) : null;
    }

    /** 是否已销毁。 */
    public boolean isDisposed() {
        return disposed;
    }
}
