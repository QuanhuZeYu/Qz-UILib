package club.heiqi.uilib.ui.scene.runtime;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Effect;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.input.CursorBackend;
import club.heiqi.uilib.ui.scene.input.InputBinding;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneEventHandler;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneInputRouter;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.node.Invalidation;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.AnchorProvider;
import club.heiqi.uilib.ui.scene.overlay.OverlayDismissPolicy;
import club.heiqi.uilib.ui.scene.overlay.OverlayHandle;
import club.heiqi.uilib.ui.scene.overlay.SceneOverlayHost;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

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

    /** 浮层宿主：维护由 portal 派生出的 active overlay roots。 */
    private final SceneOverlayHost overlayHost;

    /** 只读文本度量窄端口：供控件做点击定位等只读几何计算。 */
    private final SceneTextMeasurer textMeasurer;

    /** 创建一个新的场景运行时实例。 */
    public SceneRuntime() {
        this(null);
    }

    /**
     * 创建一个带文本度量窄端口的场景运行时实例。
     *
     * @param textMeasurer 文本度量窄端口，可为 null；调用度量方法时 null 会抛出异常
     */
    public SceneRuntime(SceneTextMeasurer textMeasurer) {
        this.rootOwner = new Owner();
        this.overlayHost = new SceneOverlayHost();
        this.inputRouter = new SceneInputRouter(overlayHost);
        this.textMeasurer = textMeasurer;
    }

    /**
     * 测量指定字号下单行文本宽度。
     *
     * @param text       文本内容
     * @param fontSizePx 字号像素
     * @return 文本宽度像素
     */
    public int measureTextWidth(String text, int fontSizePx) {
        return requireTextMeasurer().measureWidth(text, fontSizePx);
    }

    /**
     * 获取指定字号下行高。
     *
     * @param fontSizePx 字号像素
     * @return 行高像素
     */
    public int lineHeight(int fontSizePx) {
        return requireTextMeasurer().lineHeight(fontSizePx);
    }

    /**
     * 获取文本度量缓存失效纪元。
     *
     * @return 当前字体运行时纪元
     */
    public int textMeasureEpoch() {
        return requireTextMeasurer().epoch();
    }

    /**
     * 获取已注入的文本度量端口，未注入时快速失败。
     *
     * @return 文本度量端口
     */
    private SceneTextMeasurer requireTextMeasurer() {
        if (textMeasurer == null) {
            throw new IllegalStateException("SceneRuntime 未注入 SceneTextMeasurer，无法执行文本度量");
        }
        return textMeasurer;
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
        Owner current = Owner.current();
        Owner childOwner = (current != null ? current : rootOwner).createChild();
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
     * <p>{@code impact} 是声明式失效意图标注：调用方据此声明该绑定预期影响的失效级别，
     * 与 setter 自动打出的实际级别构成可交叉核对的双轨（I4 审查锚点）。运行时不依赖此参数决定级别，
     * 真正的失效级别由 {@link SceneNode} 的强类型属性槽 setter 内部自动决定。例如：
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
     * @param impact  声明式失效意图标注。调用方据此声明该绑定预期影响的失效级别，
     *                与 setter 自动打出的实际级别构成可交叉核对的双轨（I4 审查锚点）。
     *                运行时不依赖此参数决定级别。
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
     * 绑定派生函数到节点属性槽（{@link #bind} 的 Supplier 重载，命名 {@code bindDerived} 以避免歧义）。
     *
     * <p>与 {@link #bind(Invalidation, ReadableSignal, java.util.function.Consumer)} 的区别：
     * 第二参数是 {@link Supplier} 而非 {@link ReadableSignal}。两者均为单方法函数式接口且方法签名
     * 相同（{@code T get()}），若同名重载，lambda {@code () -> x} 将产生编译歧义（无法判定目标类型），
     * 故以方法名 {@code bindDerived} 显式区分。</p>
     *
     * <p>内部用 {@link Computed#create(Supplier)} 包裹派生函数为只读派生 signal，再委托
     * {@link #bind(Invalidation, ReadableSignal, java.util.function.Consumer)}。派生函数在追踪上下文中
     * 执行，读取的上游源自动成为依赖；上游变化时重算并经记忆化闸门决定是否传播下游。</p>
     *
     * <p><b>初值</b>：本重载使用 {@link Computed#create(Supplier)} 默认初值 {@code null}，
     * 首次 flush 前 applier 不会收到派生值。若需 flush 前占位，请自行
     * {@link Computed#create(Object, Supplier)} 构造带初值的派生 signal 后调 {@link #bind}。</p>
     *
     * @param <T>        派生值类型
     * @param impact     声明式失效意图标注（运行时不依赖此参数决定级别，详见 {@link #bind}）
     * @param derivation 派生函数，在追踪上下文中执行，读取的上游源自动成为依赖
     * @param applier    属性写入回调
     * @return 绑定句柄（可手动 dispose 退订）
     */
    public <T> Binding bindDerived(Invalidation impact,
                                   Supplier<T> derivation,
                                   java.util.function.Consumer<T> applier) {
        if (derivation == null || applier == null) {
            throw new IllegalArgumentException("derivation 与 applier 均不可为 null");
        }
        return bind(impact, Computed.create(derivation), applier);
    }

    /**
     * 绑定派生函数到节点属性槽（无 impact 版，命名 {@code bindDerived} 以避免歧义）。
     *
     * <p>与 {@link #bindDerived(Invalidation, Supplier, java.util.function.Consumer)} 的区别：
     * 省略 {@link Invalidation} 参数。因 impact 仅作声明式失效意图标注，运行时不依赖此参数决定级别
     * （真正失效级别由 setter 内部自动打出，详见 {@link #bind}），故可安全以 {@link Invalidation#LAYOUT}
     * 占位委托带 impact 版。</p>
     *
     * <p>同样因 {@link Supplier} 与 {@link ReadableSignal} 方法签名相同，若与
     * {@link #bind(Invalidation, ReadableSignal, java.util.function.Consumer)} 同名重载将产生 lambda
     * 编译歧义，故以 {@code bindDerived} 命名区分。</p>
     *
     * @param <T>        派生值类型
     * @param derivation 派生函数，在追踪上下文中执行，读取的上游源自动成为依赖
     * @param applier    属性写入回调
     * @return 绑定句柄（可手动 dispose 退订）
     */
    public <T> Binding bindDerived(Supplier<T> derivation,
                                   java.util.function.Consumer<T> applier) {
        return bindDerived(Invalidation.LAYOUT, derivation, applier);
    }

    /**
     * 绑定文本信号到节点文本槽（{@link #bind} 的语义化薄封装）。
     *
     * <p>{@code setText} 内部自动打出 LAYOUT+PAINT 级失效，调用方无需手选 {@link Invalidation}，
     * 消除「文本绑定还要想填什么级别」的认知负担。本方法内部固定按 LAYOUT 调 {@link #bind}。</p>
     *
     * <h3>null 跳过语义</h3>
     * <p>信号值为 null 时跳过 {@code setText}（不以 null 覆盖既有文本），null 跳过语义与旧栈 bindText 对齐。
     * 非 null 值经 {@code toString()} 写入——这是新栈相对旧栈（要求 source 已是 String）新增的便利转换，
     * 故本方法泛型放宽到 {@code <T>}，行为非与旧栈完全一致。</p>
     *
     * @param <T>    信号值类型（任意，最终 toString）
     * @param node   目标节点（不可为 null）
     * @param source 文本响应式数据源（不可为 null）
     * @return 绑定句柄（可手动 dispose 退订）
     */
    public <T> Binding bindText(SceneNode node, ReadableSignal<T> source) {
        if (node == null || source == null) {
            throw new IllegalArgumentException("node 与 source 均不可为 null");
        }
        return bind(Invalidation.LAYOUT, source, v -> {
            if (v != null) {
                node.setText(v.toString());
            }
        });
    }

    /**
     * keyed 列表渲染：把响应式列表信号绑定到容器子节点，按 key 复用、增删、最小移动。
     *
     * <h3>路 B：批量 applyChildReconcile 一次原子提交</h3>
     * <p>内部 {@link SceneKeyedListReconciler} 用 LIS 算出 finalOrder 后一次性调用
     * {@link SceneNode#applyChildReconcile}，取代旧栈逐项 insertBefore 的副作用驱动。
     * 容器只被 {@code markSelfLayout} 一次，稳定项零重算由 layout 引擎的几何 equals 闸门坐实（守 I7）。</p>
     *
     * <h3>I5 隔离</h3>
     * <p>reconcile effect 只订阅 {@code itemsSignal}，协调逻辑包在 {@link Effect#untrack} 内，
     * 项内部读取的 signal 不会回流成整列表依赖——单项变化绝不触发整列表重协调。</p>
     *
     * @param <T>           列表项类型
     * @param container     列表容器节点（独占容器，子节点全由本列表管理，不可为 null）
     * @param itemsSignal   列表数据源（不可为 null）
     * @param keyFn         项→唯一 key 的映射（不可为 null，重复 key 抛异常）
     * @param itemComponent 项→SceneNode 的构建函数（每 key 只调一次，不可为 null）
     * @return 列表句柄（dispose 卸载整列表并回收所有项 effect）
     */
    public <T> SceneListHandle forEach(SceneNode container,
                                       ReadableSignal<? extends java.util.List<T>> itemsSignal,
                                       java.util.function.Function<? super T, ?> keyFn,
                                       java.util.function.Function<? super T, SceneNode> itemComponent) {
        if (container == null || itemsSignal == null || keyFn == null || itemComponent == null) {
            throw new IllegalArgumentException("container/itemsSignal/keyFn/itemComponent 均不可为 null");
        }
        Owner current = Owner.current();
        Owner listOwner = (current != null ? current : rootOwner).createChild();
        SceneKeyedListReconciler<T> reconciler =
                new SceneKeyedListReconciler<>(container, keyFn, itemComponent, listOwner);
        // reconcile effect 只订阅 itemsSignal（唯一追踪点）；协调在 untrack 内，
        // 隔离 item 构建期对内部 signal 的读取，杜绝单项变化触发整列表重协调（守 I5）。
        listOwner.run(() -> Effect.create(() -> {
            java.util.List<T> items = itemsSignal.get();
            Effect.untrack(() -> reconciler.reconcile(items));
        }));
        return new SceneListHandle(listOwner);
    }

    /**
     * 条件渲染：condition 为 true 时挂载内容，false 时卸载。
     *
     * <h3>走 anchor + insertBefore，不走 applyChildReconcile</h3>
     * <p>show 的 parent 非独占容器（可有其它兄弟），applyChildReconcile 会整体替换 children
     * 误删兄弟，故 show 用零尺寸 anchor 占位 + insertBefore/removeChild 副作用驱动（0/1 项无批量收益）。
     * 详见 {@link SceneConditionalRenderer}。</p>
     *
     * <h3>I5 隔离 + I7 稳定</h3>
     * <p>effect 只订阅 {@code condition}，内容协调包在 untrack 内；连续两次 true 不重建已挂载内容。</p>
     *
     * @param parent    内容挂载到的父节点（不可为 null，可含其它兄弟）
     * @param condition 条件响应式数据源（不可为 null）
     * @param content   内容构建函数（true 时调用，不可为 null）
     * @return 条件句柄（dispose 卸载内容 + 摘除 anchor）
     */
    public SceneShowHandle show(SceneNode parent,
                                ReadableSignal<Boolean> condition,
                                Supplier<SceneNode> content) {
        if (parent == null || condition == null || content == null) {
            throw new IllegalArgumentException("parent/condition/content 均不可为 null");
        }
        Owner current = Owner.current();
        Owner condOwner = (current != null ? current : rootOwner).createChild();
        // anchor 占位：零尺寸不可见节点（无 text/背景/preferredHeight → height=0、paint 无命令），
        // append 到 parent 标记内容的声明顺序位置。
        SceneNode anchor = new SceneNode();
        parent.appendChild(anchor);
        SceneConditionalRenderer renderer =
                new SceneConditionalRenderer(parent, anchor, content, condOwner);
        // effect 只订阅 condition（唯一追踪点）；update 内的内容构建/卸载包在 untrack 内（守 I5）。
        condOwner.run(() -> Effect.create(() -> {
            boolean visible = Boolean.TRUE.equals(condition.get());
            Effect.untrack(() -> renderer.update(visible));
        }));
        // 整个 show 卸载时摘除 anchor 占位（内容反复增删时 anchor 常驻）。
        condOwner.onCleanup(() -> {
            if (anchor.__getParent() != null) {
                anchor.__getParent().removeChild(anchor);
            }
        });
        return new SceneShowHandle(condOwner);
    }

    /**
     * 受控浮层 portal：visible 为 true 时构建 overlay root 并注册到浮层宿主，false 时卸载。
     *
     * <p>portal 与 {@link #show(SceneNode, ReadableSignal, Supplier)} 的响应式语义一致：effect 只订阅
     * {@code visible}，内容构建与卸载包在 {@link Effect#untrack(Runnable)} 内，避免 overlay 内部 bind/on
     * 或其它 signal 读取回流成 visible 依赖。handler 不应直接挂卸浮层，只能写 visible signal。</p>
     *
     * <p>每次可见挂载都会创建独立子 {@link Owner}。该作用域清理时会摘除 overlay entry，并回收 builder
     * 内注册的 bind/effect/on；组件 Owner cleanup、返回句柄 dispose 以及 {@link #dispose()} 都会清理残留浮层。</p>
     *
     * @param visible 浮层可见性信号，不可为 null
     * @param content 浮层根节点构建函数，visible 首次变 true 时调用，不可为 null
     * @return portal 句柄，可手动停止响应并移除当前浮层
     */
    public ScenePortalHandle portal(ReadableSignal<Boolean> visible, Supplier<SceneNode> content) {
        return portal(visible, content, OverlayDismissPolicy.DEFAULT, null);
    }

    /**
     * 受控浮层 portal：visible 为 true 时构建 overlay root，并带关闭策略注册到浮层宿主。
     *
     * @param visible 浮层可见性信号，不可为 null
     * @param content 浮层根节点构建函数，visible 首次变 true 时调用，不可为 null
     * @param dismissPolicy 关闭策略，传入 null 时使用默认策略
     * @param dismissRequest 关闭请求回调，只允许写 signal，可为 null
     * @return portal 句柄，可手动停止响应并移除当前浮层
     */
    public ScenePortalHandle portal(ReadableSignal<Boolean> visible,
                                    Supplier<SceneNode> content,
                                    OverlayDismissPolicy dismissPolicy,
                                    Runnable dismissRequest) {
        return portalAnchored(visible, content, dismissPolicy, dismissRequest, null);
    }

    /**
     * 受控锚定浮层 portal：visible 为 true 时构建 overlay root，并按 trigger 几何定位。
     *
     * <p>anchorProvider 是 I11 逃生舱①只读几何探针，只返回 host 局部坐标盒，不写节点、不打脏。</p>
     *
     * @param visible 浮层可见性信号，不可为 null
     * @param content 浮层根节点构建函数，visible 首次变 true 时调用，不可为 null
     * @param dismissPolicy 关闭策略，传入 null 时使用默认策略
     * @param dismissRequest 关闭请求回调，只允许写 signal，可为 null
     * @param anchorProvider 只读锚点探针，可为 null 表示非锚定浮层
     * @return portal 句柄，可手动停止响应并移除当前浮层
     */
    public ScenePortalHandle portalAnchored(ReadableSignal<Boolean> visible,
                                            Supplier<SceneNode> content,
                                            OverlayDismissPolicy dismissPolicy,
                                            Runnable dismissRequest,
                                            AnchorProvider anchorProvider) {
        Set<SceneNode> derived = (anchorProvider != null && anchorProvider.getNode() != null)
                ? Collections.singleton(anchorProvider.getNode())
                : Collections.emptySet();
        return portalAnchored(visible, content, dismissPolicy, dismissRequest, anchorProvider, derived);
    }

    /**
     * 受控锚定浮层 portal：visible 为 true 时构建 overlay root，并允许显式声明外部点击保护节点。
     *
     * @param visible 浮层可见性信号，不可为 null
     * @param content 浮层根节点构建函数，visible 首次变 true 时调用，不可为 null
     * @param dismissPolicy 关闭策略，传入 null 时使用默认策略
     * @param dismissRequest 关闭请求回调，只允许写 signal，可为 null
     * @param anchorProvider 只读锚点探针，可为 null 表示非锚定浮层
     * @param protectedNodes 外部点击判定中视为浮层内部的保护节点集，可为 null
     * @return portal 句柄，可手动停止响应并移除当前浮层
     */
    public ScenePortalHandle portalAnchored(ReadableSignal<Boolean> visible,
                                            Supplier<SceneNode> content,
                                            OverlayDismissPolicy dismissPolicy,
                                            Runnable dismissRequest,
                                            AnchorProvider anchorProvider,
                                            Collection<SceneNode> protectedNodes) {
        if (visible == null || content == null) {
            throw new IllegalArgumentException("visible/content 均不可为 null");
        }
        Owner current = Owner.current();
        Owner portalOwner = (current != null ? current : rootOwner).createChild();
        ScenePortalRenderer renderer = new ScenePortalRenderer(content, portalOwner, dismissPolicy, dismissRequest,
                anchorProvider, protectedNodes);
        portalOwner.run(() -> Effect.create(() -> {
            boolean shouldShow = Boolean.TRUE.equals(visible.get());
            Effect.untrack(() -> renderer.update(shouldShow));
        }));
        portalOwner.onCleanup(renderer::disposeMounted);
        return new ScenePortalHandle(portalOwner);
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
        InputBinding binding = inputRouter.on(node, type, handler);
        Owner current = Owner.current();
        if (current != null) {
            current.onCleanup(binding::dispose);
        }
        return binding;
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
     * flush 后滚动 hover 重算（B8 修复，内部协议，薄委托到 {@link SceneInputRouter#reconcileHoverAfterScroll}）。
     *
     * <p>由 host 在 flush + layout 之后调用，用末次指针坐标重做 hit-test + hover 切换。
     * 详见 {@link SceneInputRouter#reconcileHoverAfterScroll}。</p>
     *
     * @param root     场景树根节点
     * @param pointerX 末次指针逻辑 X 坐标
     * @param pointerY 末次指针逻辑 Y 坐标
     * @param absX     根节点屏幕绝对 X 偏移
     * @param absY     根节点屏幕绝对 Y 偏移
     */
    public void reconcileHoverAfterScroll(SceneNode root, int pointerX, int pointerY, int absX, int absY) {
        inputRouter.reconcileHoverAfterScroll(root, pointerX, pointerY, absX, absY);
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
     * 获取内部浮层宿主引用。
     *
     * <p>宿主自身只暴露不可变快照，不向业务作者暴露内部 mutable active list；该访问器供后续
     * host/router 管线与测试探针消费。</p>
     *
     * @return 共享的 SceneOverlayHost 实例
     */
    public SceneOverlayHost getOverlayHost() {
        return overlayHost;
    }

    // ==================== I4a 焦点/键盘委托 ====================

    /**
     * 请求将焦点切换到指定节点（薄委托到 Router → FocusManager）。
     *
     * @param node 要聚焦的节点
     * @return true 表示焦点切换成功
     */
    public boolean requestFocus(SceneNode node) {
        return inputRouter.requestFocus(node);
    }

    /**
     * 将节点登记为可聚焦（薄委托到 Router → FocusManager）。
     *
     * @param node 目标节点
     */
    public void focusable(SceneNode node) {
        inputRouter.registerFocusable(node);
    }

    /**
     * 按 enabled signal 动态登记/注销可聚焦节点（兑现 package-info R9「disabled 不可聚焦」）。
     *
     * <p>创建一个 effect 订阅 {@code enabledSignal}：
     * <ul>
     *   <li>enabled=true → 注册进 FocusManager Tab 环（{@code registerFocusableRaw}，不登记 cleanup）</li>
     *   <li>enabled=false → 从 Tab 环注销（{@code unregisterFocusable}），若该节点正聚焦则立即清失焦点</li>
     * </ul>
     * enabled 变化时 effect 重跑，自动进出 Tab 环。Tab 顺序由 FocusManager 按 DOM 前序实时排序，
     * 故 enabled=true 恢复时自然回到原 DOM 位置（不跑末尾）。</p>
     *
     * <h3>I1 signal-first / I7 Owner 归属</h3>
     * <p>focusable 的动态进出完全经 signal→effect 派生，不命令式。effect 归属规则与 {@link #bind}
     * 一致：当前处于 Owner 作用域内则归属该作用域（随组件卸载一并退订），否则归属 rootOwner。
     * 卸载兜底 cleanup 只登记一次（{@code unregisterFocusable} 幂等），避免 effect 重跑累积 cleanup。</p>
     *
     * <h3>effect body 包 untrack</h3>
     * <p>register/unregister 不读 signal，包 {@link Effect#untrack} 是防御性隔离，确保 effect 唯一
     * 追踪点只有 {@code enabledSignal}（守 I5）。</p>
     *
     * @param node          目标节点
     * @param enabledSignal 是否启用的响应式数据源，true=进 Tab 环，false=退出
     */
    public void focusable(SceneNode node, ReadableSignal<Boolean> enabledSignal) {
        if (node == null || enabledSignal == null) {
            throw new IllegalArgumentException("node 与 enabledSignal 均不可为 null");
        }
        Owner current = Owner.current();
        Owner targetOwner = current != null ? current : rootOwner;
        // 卸载兜底：组件卸载时确保从 Tab 环移除（只登记一次，unregisterFocusable 幂等）
        targetOwner.onCleanup(() -> inputRouter.unregisterFocusable(node));
        targetOwner.createEffect(() -> {
            boolean enabled = Boolean.TRUE.equals(enabledSignal.get());
            Effect.untrack(() -> {
                if (enabled) {
                    inputRouter.registerFocusableRaw(node);
                } else {
                    inputRouter.unregisterFocusable(node);
                }
            });
        });
    }

    /**
     * @return 当前焦点节点（薄委托到 Router → FocusManager）
     */
    public SceneNode getFocusedNode() {
        return inputRouter.getFocusedNode();
    }

    // ==================== I4c cursor 投影委托 ====================

    /**
     * 绑定光标后端：创建 cursor effect，订阅 Router 的全局 cursorSignal，
     * signal 变化时自动调用 {@code backend.apply(cursor)} 将光标样式应用到平台光标系统。
     *
     * <h3>Oracle 纠偏①：cursor effect 不需要独立 Owner</h3>
     * <p>本方法用 rootOwner 创建 effect，body 只调 {@code backend.apply}，
     * 绝不碰任何 SceneNode setter。因此不会打任何脏标记，普通 rootOwner effect 天然不污染。
     * 独立 Owner 唯一正当理由可单独 dispose（此处不需要，cursor effect 全生命周期伴随 runtime）。</p>
     *
     * <h3>信号链：I11 cursor 投影纪律</h3>
     * <p>Router 写 cursorSignal → cursor effect 订阅它 → 调 backend.apply。
     * 绝不命令式 setCursor，走 signal→effect 派生（I11）。</p>
     *
     * @param backend 光标后端实现（如 {@code LwjglCursorBackend}），不可为 null
     */
    public void bindCursor(CursorBackend backend) {
        if (backend == null) {
            throw new IllegalArgumentException("backend 不可为 null");
        }
        ReadableSignal<SceneCursor> src = inputRouter.cursorSignal();
        rootOwner.createEffect(() -> backend.apply(src.get()));
    }

    /**
     * 暴露全局 cursor signal（只读），委托到 {@link SceneInputRouter#cursorSignal()}。
     *
     * @return 全局光标样式 signal（只读）
     */
    public ReadableSignal<SceneCursor> cursorSignal() {
        return inputRouter.cursorSignal();
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

    /** portal 挂卸协调器：只在 visible 边界变化时注册/摘除 overlay root。 */
    private final class ScenePortalRenderer {

        /** 浮层内容工厂。 */
        private final Supplier<SceneNode> content;

        /** portal 生命周期根 Owner。 */
        private final Owner portalOwner;

        /** 浮层关闭策略。 */
        private final OverlayDismissPolicy dismissPolicy;

        /** 浮层关闭请求回调。 */
        private final Runnable dismissRequest;

        /** 只读锚点探针。 */
        private final AnchorProvider anchorProvider;

        /** 外部点击判定中视为浮层内部的保护节点集。 */
        private final Set<SceneNode> protectedNodes;

        /** 当前可见浮层的子 Owner，null 表示未挂载。 */
        private Owner contentOwner;

        /** 当前浮层注册句柄，随 contentOwner cleanup 摘除。 */
        private OverlayHandle overlayHandle;

        private ScenePortalRenderer(Supplier<SceneNode> content,
                                    Owner portalOwner,
                                    OverlayDismissPolicy dismissPolicy,
                                    Runnable dismissRequest,
                                    AnchorProvider anchorProvider,
                                    Collection<SceneNode> protectedNodes) {
            this.content = content;
            this.portalOwner = portalOwner;
            this.dismissPolicy = dismissPolicy == null ? OverlayDismissPolicy.DEFAULT : dismissPolicy;
            this.dismissRequest = dismissRequest;
            this.anchorProvider = anchorProvider;
            this.protectedNodes = protectedNodes == null || protectedNodes.isEmpty()
                    ? Collections.emptySet()
                    : Collections.unmodifiableSet(new HashSet<>(protectedNodes));
        }

        /**
         * 按 visible 协调浮层挂卸；连续 true/false 不重复构建或摘除。
         *
         * @param visible 当前可见性
         */
        private void update(boolean visible) {
            if (visible) {
                if (contentOwner != null) {
                    return;
                }
                mount();
            } else {
                disposeMounted();
            }
        }

        /** 构建浮层 root 并注册到 overlay host。 */
        private void mount() {
            Owner owner = portalOwner.createChild();
            SceneNode[] holder = new SceneNode[1];
            owner.run(() -> holder[0] = Objects.requireNonNull(content.get(), "portal content root"));
            OverlayHandle handle = overlayHost.register(holder[0], dismissPolicy, dismissRequest, anchorProvider,
                    protectedNodes);
            owner.onCleanup(handle::dispose);
            contentOwner = owner;
            overlayHandle = handle;
        }

        /** 卸载当前浮层并清理其子 Owner。 */
        private void disposeMounted() {
            if (contentOwner == null) {
                return;
            }
            contentOwner.dispose();
            contentOwner = null;
            overlayHandle = null;
        }
    }
}
