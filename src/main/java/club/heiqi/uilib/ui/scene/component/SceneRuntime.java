package club.heiqi.uilib.ui.scene.component;

import java.util.function.Supplier;

import club.heiqi.uilib.ui.reactive.Effect;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.input.InputBinding;
import club.heiqi.uilib.ui.scene.input.SceneEventHandler;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneInputRouter;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.node.Invalidation;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 场景树运行时 —— 新 UI 组件层入口，对接 reactive 原语与 SceneNode 属性槽。
 *
 * <h3>与旧 {@code UiComponentRuntime} 的关系</h3>
 * <p>借鉴其 Owner/untrack/mount/bind 的生命周期语义，操作对象从 {@code ElementNode} 换成
 * {@link SceneNode}。不再依赖 {@code UiDocument}，以纯 Owner 作用域管理生命周期。</p>
 *
 * <h3>核心职责</h3>
 * <ul>
 *   <li><b>mount</b>：在 Owner 子作用域内执行组件 builder 一次（I3），产物挂到父节点，
 *       卸载时自动 removeChild + 回收所有 effect。</li>
 *   <li><b>bind</b>：建 effect 订阅 {@link ReadableSignal}，读值交给 applier 写 SceneNode
 *       属性槽——属性槽 setter 内部自动打出正确失效级别（I4），调用方无需手选级别。</li>
 *   <li><b>flush</b>：委托 {@link ReactiveScheduler#flush()} 帧末统一应用写入 + 重跑脏 effect（I2/I9）。</li>
 *   <li><b>dispose</b>：递归销毁整棵 Owner 作用域树，回收所有 effect 订阅。</li>
 * </ul>
 */
public class SceneRuntime {

    /** 根 Owner 作用域：所有 mount/bind 最终归属的根，dispose 时全量清理。 */
    private final Owner rootOwner;

    /** 输入路由器：route / on 委托至此，整个 runtime 共享同一实例。 */
    private final SceneInputRouter inputRouter;

    /** 创建一个新的场景运行时实例。 */
    public SceneRuntime() {
        this.rootOwner = new Owner();
        this.inputRouter = new SceneInputRouter();
    }

    /**
     * 挂载一个组件：在 Owner 子作用域内执行组件 builder 一次（信条三 I3），
     * builder 产出的 SceneNode 自动 append 到 parent。
     *
     * <p>卸载时（調用返回句柄的 {@link MountHandle#dispose()}）：
     * 该作用域内所有 effect（含 bind 创建的绑定）全部退订；
     * mount 的根节点自动从 parent 移除（通过 onCleanup 注册的回调）。</p>
     *
     * @param parent  挂载到的父节点（不可为 null）
     * @param builder 组件构建函数，返回组件根节点（执行一次，I3）
     * @return 挂载句柄（含根节点引用 + 卸载能力）
     */
    public MountHandle mount(SceneNode parent, Supplier<SceneNode> builder) {
        if (parent == null || builder == null) {
            throw new IllegalArgumentException("parent 与 builder 均不可为 null");
        }
        Owner childOwner = rootOwner.createChild();
        SceneNode[] rootHolder = new SceneNode[1];
        childOwner.run(() -> {
            SceneNode root = builder.get();
            if (root != null) {
                parent.appendChild(root);
                rootHolder[0] = root;
            }
        });
        // 卸载时：从父节点摘除根节点
        childOwner.onCleanup(() -> {
            SceneNode root = rootHolder[0];
            if (root != null) {
                parent.removeChild(root);
            }
        });
        return new MountHandle(childOwner, rootHolder[0]);
    }

    /**
     * 绑定一个响应式信号到 SceneNode 属性槽。
     *
     * <h3>失效级别（I4）的自动打出</h3>
     * <p>{@code impact} 参数用于声明绑定意图/校验，但真正的失效级别由 {@link SceneNode}
     * 的强类型属性槽 setter 内部自动决定。例如：
     * <ul>
     *   <li>{@code bind(Invalidation.PAINT, colorSignal, node::setBackgroundColor)}
     *       → effect 首次执行及后续 signal 变化时调用 {@code node.setBackgroundColor(x)}，
     *       其内部自动调 {@code markSelfPaint()} 打出 PAINT 级标记。</li>
     *   <li>{@code bind(Invalidation.COMPOSITE, opacitySignal, node::setOpacity)}
     *       → 同理，{@code setOpacity} 内部自动调 {@code markComposite()}。</li>
     * </ul>
     * 调用方无需手选级别，从而降低 I4"打错级别"的风险。</p>
     *
     * <h3>Effect 归属</h3>
     * <p>若当前处于 {@link Owner} 作用域内（如 mount 的 builder 回调中），effect 归属该作用域，
     * 随组件卸载一并退订。否则归属根 Owner，由 {@link #dispose()} 统一清理——确保没有任何 orphan effect。</p>
     *
     * @param <T>     信号值类型
     * @param impact  声明的失效级别（用于校验/文档，真正打级靠属性槽）
     * @param src     响应式数据源（signal 或 computed）
     * @param applier 属性写入回调（如 {@code node::setBackgroundColor}、{@code node::setText}）
     * @return 绑定句柄（可手动 dispose 退订）
     */
    public <T> Binding bind(Invalidation impact, ReadableSignal<T> src, java.util.function.Consumer<T> applier) {
        if (src == null || applier == null) {
            throw new IllegalArgumentException("src 与 applier 均不可为 null");
        }
        Owner current = Owner.current();
        // 若在 mount builder 内部（有当前作用域），effect 归属该作用域随组件卸载一并退订；
        // 否则归属 rootOwner，由 runtime.dispose() 统一清理——确保没有任何 orphan effect。
        Owner targetOwner = current != null ? current : rootOwner;
        Effect effect = targetOwner.createEffect(() -> applier.accept(src.get()));
        return new Binding(effect);
    }

    /**
     * 注册输入事件处理器。
     *
     * <p>薄委托到内部 {@link SceneInputRouter#on(SceneNode, SceneEventType, SceneEventHandler)}。
     * handler 非响应式订阅，不创建 Effect。若当前处于 Owner 作用域内，
     * 自动登记退订回调，随组件卸载一并移除。</p>
     *
     * @param node    目标节点
     * @param type    事件类型
     * @param handler 事件处理器
     * @return 绑定句柄（可手动 dispose 退订）
     */
    public InputBinding on(SceneNode node, SceneEventType type, SceneEventHandler handler) {
        return inputRouter.on(node, type, handler);
    }

    /**
     * 获取或创建指定节点的交互状态容器（薄委托到 {@link SceneInputRouter#interactionState}）。
     *
     * @param node 目标节点
     * @return 交互状态容器
     */
    public SceneInteractionState interactionState(SceneNode node) {
        return inputRouter.interactionState(node);
    }

    /**
     * 执行一帧输入路由（薄委托到 {@link SceneInputRouter#route}）。
     *
     * @param root      场景树根节点
     * @param frame     输入帧快照
     * @param rootAbsX  根节点屏幕绝对 X 偏移
     * @param rootAbsY  根节点屏幕绝对 Y 偏移
     */
    public void route(SceneNode root, SceneInputFrame frame, int rootAbsX, int rootAbsY) {
        inputRouter.route(root, frame, rootAbsX, rootAbsY);
    }

    /**
     * 获取内部输入路由器引用（供测试探针使用）。
     *
     * @return 共享的 SceneInputRouter 实例
     */
    public SceneInputRouter getInputRouter() {
        return inputRouter;
    }

    /**
     * 帧末批量刷新：委托 {@link ReactiveScheduler#flush()} 统一应用所有待写入 signal
     * 并重跑所有脏 effect（信条四 I2/I9）。
     */
    public void flush() {
        ReactiveScheduler.get().flush();
    }

    /**
     * 销毁整个运行时：递归 dispose 根 Owner 作用域，清理所有 mount 的子作用域、
     * 所有 bind 创建的 effect，并从父节点摘除所有挂载节点。
     */
    public void dispose() {
        rootOwner.dispose();
    }
}
