package club.heiqi.uilib.ui.component;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.reactive.Effect;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.style.UiStyleChangeImpact;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiVisibility;
import club.heiqi.uilib.ui.base.values.UiTransform;

/**
 * 组件运行时（宪章③组件层）：把响应式数据层（①signal/②effect）绑定到保留式 DOM（④），
 * 并管理组件的挂载生命周期（信条二/三，I3）。
 *
 * <p><b>分层定位</b>：本类只依赖 {@link UiDocument}（DOM）与 {@code ui.reactive}（响应式原语），
 * <b>不认识 widget / 渲染层 / OpenGL</b>。这是宪章「组件层只依赖 DOM + reactive」的落地——
 * 组件运行时可独立于任何渲染后端构造、测试、复用，渲染适配器（如 {@code HtmlLikeDocumentWidget}）
 * 只需在帧循环里调用 {@link #flush()}、生命周期结束时调用 {@link #dispose()}。</p>
 *
 * <p><b>典型用法</b>：</p>
 * <pre>
 *   UiDocument document = UiDocument.create();
 *   UiComponentRuntime runtime = new UiComponentRuntime(document);
 *   runtime.mount(document.getRootElement(), doc -&gt; {
 *       ElementNode card = doc.div();
 *       runtime.bindBackgroundColor(card, bgSignal);   // signal → 属性，自动归属组件作用域
 *       return card;
 *   });
 *   // 宿主帧循环：runtime.flush(); 关闭时：runtime.dispose();
 * </pre>
 */
public final class UiComponentRuntime {

    private final UiDocument document;
    private final Owner rootOwner = new Owner();

    /**
     * 以指定文档创建组件运行时。
     *
     * @param document 组件运行时操作的目标文档
     */
    public UiComponentRuntime(UiDocument document) {
        this.document = Objects.requireNonNull(document, "document");
    }

    /**
     * 返回本运行时操作的文档。
     *
     * @return 目标文档
     */
    public UiDocument getDocument() {
        return document;
    }

    /**
     * 帧末批量刷新响应式状态：应用本帧排队的 signal 写入并重跑脏 effect（I9）。
     *
     * <p>由宿主帧循环（渲染适配器的每帧绘制开头）调用，保证 signal 变化先于版本检查生效。
     * 委托全局 {@link ReactiveScheduler}。</p>
     */
    public void flush() {
        ReactiveScheduler.get().flush();
    }

    /**
     * 在本运行时生命周期内创建一个响应式 effect。
     *
     * <p>effect body 在每次刷新时执行，自动追踪 {@link club.heiqi.uilib.ui.reactive.Signal} 依赖；
     * 任意依赖变化时 body 重跑。</p>
     *
     * <p><b>标脏由属性写入自带的节点级精确链路负责</b>（信条二/I4/I7）：style setter、{@code setText}、
     * {@code setAttribute}、结构增删等写入路径各自按精确 impact 在<b>命中节点/子树</b>上自动标脏，
     * effect 不再追加任何桥接层全局粗粒度 bump（避免 root 整树标脏的性能 bug）。纯副作用 effect
     * （不碰 DOM）本就无需标脏。</p>
     *
     * <p>effect 归属策略：若处于某组件挂载作用域内（{@link #mount} 期间），自动归属该组件 Owner，
     * 随组件卸载一并清理；否则归属运行时根 Owner，{@link #dispose()} 时统一清理。</p>
     *
     * @param body effect 体，在追踪上下文中执行
     * @return 创建的 effect（通常无需持有）
     */
    public Effect createEffect(Runnable body) {
        Objects.requireNonNull(body, "body");
        Owner targetOwner = Owner.current();
        if (targetOwner == null) {
            targetOwner = rootOwner;
        }
        return targetOwner.createEffect(body);
    }

    /**
     * 通用事件订阅绑定（输入半环：外部事件源 → signal）。建立时立即执行 {@code register}（订阅），
     * 并把 {@code unregister}（退订）登记到<b>当前 {@link Owner} 作用域</b>的
     * {@link Owner#onCleanup(Runnable)} —— 作用域卸载时自动退订，成对出现，<b>从构造上杜绝悬挂监听器</b>
     * （无论订阅在构造期根作用域，还是 {@link #forEach}/{@link #show} 的项/内容作用域内建立）。
     *
     * <p><b>归属策略</b>：与 {@link #createEffect} 一致——若处于某组件/项/内容挂载作用域内，自动归属该作用域，
     * 随其卸载一并退订；否则归属运行时根作用域，{@link #dispose()} 时统一退订。这正是「重建即注册、卸载即注销」
     * 的对称性来源：例如 {@code forEach} 某行被移除时，该行 owner dispose 会自动触发本绑定的退订。</p>
     *
     * <p><b>纪律（信条一/I1）</b>：{@code register}/{@code unregister} 只做「订阅/退订外部事件源」的命令式动作；
     * 事件处理逻辑里若要改 UI，<b>必须改 signal</b>，不得命令式操作 DOM 节点。</p>
     *
     * <p><b>幂等</b>：{@code unregister} 被包装为「最多执行一次」——无论是作用域 dispose 触发的 onCleanup，
     * 还是 {@link Binding#dispose()} 提前触发，退订都只跑一次，二者互不冲突、可任意先后/重复调用。</p>
     *
     * @param register   订阅外部事件源的回调（建立时立即执行一次）
     * @param unregister 退订外部事件源的回调（作用域卸载或 {@link Binding#dispose()} 时执行一次）
     * @return 绑定句柄，可单独 {@link Binding#dispose()} 提前退订
     */
    public Binding on(Runnable register, Runnable unregister) {
        Objects.requireNonNull(register, "register");
        Objects.requireNonNull(unregister, "unregister");
        Binding binding = new Binding(unregister);
        register.run();
        Owner scope = Owner.current();
        (scope != null ? scope : rootOwner).onCleanup(binding::dispose);
        return binding;
    }

    /**
     * DOM 级便利糖（只碰 {@code ui.dom}，留在组件层以守 I6）：把元素的<b>左键点击</b>事件桥接到
     * {@code action}（输入半环：DOM 事件 → signal）。建立时通过 {@link #on(Runnable, Runnable)} 注册点击
     * 处理器，作用域卸载（或 {@link Binding#dispose()}）时自动 {@code setClickHandler(null)} 退订。
     *
     * <p>仅左键（{@code button == 0}）触发 {@code action} 并消费事件（{@code onClick} 返回 {@code true}）；
     * 其余按钮返回 {@code false} 继续冒泡（仅左键消费）。</p>
     *
     * <p><b>纪律（信条一/I1）</b>：{@code action} 内应改 signal 驱动界面变化，不得命令式操作节点。</p>
     *
     * @param element 目标元素
     * @param action  左键点击时执行的动作（应改 signal）
     * @return 绑定句柄，可单独 {@link Binding#dispose()} 提前退订
     */
    public Binding onClick(ElementNode element, Runnable action) {
        Objects.requireNonNull(element, "element");
        Objects.requireNonNull(action, "action");
        return on(
                () -> element.setClickHandler(new DocumentElementClickHandler() {
                    @Override
                    public boolean onClick(DocumentElementClickEvent event) {
                        if (event.getButton() != 0) {
                            return false;
                        }
                        action.run();
                        return true;
                    }
                }),
                () -> element.setClickHandler(null));
    }

    /**
     * 通用细粒度绑定：把一个 {@link ReadableSignal} 的值写入由 {@code applier} 指定的样式属性
     * （信条二：signal → 节点属性绑定）。
     *
     * <p>建立后，源每次变化都会重跑 effect 并执行 {@code applier}；源值为 {@code null} 时跳过 applier，
     * 不写入。</p>
     *
     * <p><b>标脏由属性 setter 自带的节点级精确链路负责</b>（I4/I7）：{@code applier} 内调用的
     * style setter / {@code setText} 等各自按属性精确 impact 在命中节点上自动标脏（LAYOUT → 重排，
     * PAINT → 重绘，COMPOSITE → composite-only 回放），bind 不再追加桥接层全局 bump。</p>
     *
     * <p>{@link club.heiqi.uilib.ui.reactive.Signal} 与 {@link club.heiqi.uilib.ui.reactive.Computed}
     * 均实现 {@link ReadableSignal}，故派生值也可直接作为源喂入。</p>
     *
     * @param source  数据源（signal 或 computed）
     * @param applier 把源值写入样式的回调，仅在源值非 {@code null} 时调用
     * @param <T>     值类型
     * @return 创建的 effect（通常无需持有）
     */
    public <T> Effect bind(ReadableSignal<T> source, Consumer<T> applier) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(applier, "applier");
        return createEffect(() -> {
            T value = source.get();
            if (value != null) {
                applier.accept(value);
            }
        });
    }

    /**
     * 将一个响应式源细粒度绑定到元素的 opacity（信条二）。
     *
     * <p>opacity 属于 {@link UiStyleChangeImpact#COMPOSITE} 级，故变化只触发合成层失效
     * （{@link UiDocument#markCompositeDirty()}），命中 composite-only 就地回放路径，
     * 不触发绘制命令全量重建。</p>
     *
     * @param element 目标元素
     * @param source  opacity 数据源（值域 [0,1]，由 {@code setOpacity} 自行 clamp）
     * @return 创建的 effect（通常无需持有）
     * @see #bind(ReadableSignal, Consumer)
     */
    public Effect bindOpacity(ElementNode element, ReadableSignal<Float> source) {
        Objects.requireNonNull(element, "element");
        return bind(source,
                value -> element.style().setOpacity(value.floatValue()));
    }

    /**
     * 将一个响应式源细粒度绑定到元素的 transform（信条二）。
     *
     * <p>transform 属于 {@link UiStyleChangeImpact#COMPOSITE} 级，故变化只触发合成层失效，
     * 命中 composite-only 就地回放路径，不触发绘制命令全量重建。</p>
     *
     * @param element 目标元素
     * @param source  transform 数据源（{@code null} 值会被跳过，不写入）
     * @return 创建的 effect（通常无需持有）
     * @see #bind(ReadableSignal, Consumer)
     */
    public Effect bindTransform(ElementNode element, ReadableSignal<UiTransform> source) {
        Objects.requireNonNull(element, "element");
        return bind(source,
                value -> element.style().setTransform(value));
    }

    /**
     * 将一个响应式源细粒度绑定到元素的 background-color（信条二）。
     *
     * <p>背景色属于 {@link UiStyleChangeImpact#PAINT} 级，变化触发重绘（不重排）。</p>
     *
     * @param element 目标元素
     * @param source  ARGB 颜色数据源（{@code null} 值会被跳过，不写入）
     * @return 创建的 effect（通常无需持有）
     * @see #bind(ReadableSignal, Consumer)
     */
    public Effect bindBackgroundColor(ElementNode element, ReadableSignal<Integer> source) {
        Objects.requireNonNull(element, "element");
        return bind(source,
                value -> element.style().setBackgroundColor(value.intValue()));
    }

    /**
     * 将一个响应式源细粒度绑定到元素的 text-color（信条二）。
     *
     * <p>文字色属于 {@link UiStyleChangeImpact#PAINT} 级，变化触发重绘（不重排）。</p>
     *
     * @param element 目标元素
     * @param source  ARGB 颜色数据源（{@code null} 值会被跳过，不写入）
     * @return 创建的 effect（通常无需持有）
     * @see #bind(ReadableSignal, Consumer)
     */
    public Effect bindTextColor(ElementNode element, ReadableSignal<Integer> source) {
        Objects.requireNonNull(element, "element");
        return bind(source,
                value -> element.style().setTextColor(value.intValue()));
    }

    /**
     * 将一个响应式源细粒度绑定到元素的 visibility（信条二）。
     *
     * <p>visibility 属于 {@link UiStyleChangeImpact#PAINT} 级，变化触发重绘（不重排）。</p>
     *
     * @param element 目标元素
     * @param source  visibility 数据源（{@code null} 值会被跳过，不写入）
     * @return 创建的 effect（通常无需持有）
     * @see #bind(ReadableSignal, Consumer)
     */
    public Effect bindVisibility(ElementNode element, ReadableSignal<UiVisibility> source) {
        Objects.requireNonNull(element, "element");
        return bind(source,
                value -> element.style().setVisibility(value));
    }

    /**
     * 将一个响应式源细粒度绑定到文本节点的内容（信条二：signal → 文本）。
     *
     * <p>文本变化会改变节点尺寸，属 {@link UiStyleChangeImpact#LAYOUT} 级，故绑定按 LAYOUT 触发失效
     * （重排 → 重绘 → 重合成）。这是声明式三基石之一（条件/列表/<b>文本</b>）：用它即可纯靠改 signal
     * 驱动文本内容，无需命令式 {@code textNode.setText()}。</p>
     *
     * <p>源值为 {@code null} 时跳过（{@link TextNode#setText(String)} 本身把 null 当空串处理，但 bind
     * 统一对 null 跳过以保持与其它 bind 一致的语义）。</p>
     *
     * @param textNode 目标文本节点
     * @param source   文本数据源（signal 或 computed）
     * @return 创建的 effect（通常无需持有）
     * @see #bind(ReadableSignal, Consumer)
     */
    public Effect bindText(TextNode textNode, ReadableSignal<String> source) {
        Objects.requireNonNull(textNode, "textNode");
        return bind(source, textNode::setText);
    }

    /**
     * 挂载一个声明式组件到 {@code parent} 下（信条二/三，I3：组件函数只跑一次）。
     *
     * <p>挂载流程：</p>
     * <ol>
     *   <li>在当前作用域（嵌套挂载时为外层组件作用域，顶层时为运行时根作用域）下建立一个子
     *       {@link Owner}（组件的生命周期作用域）；</li>
     *   <li>在该作用域上下文中<b>只执行一次</b> {@code component}，得到组件根节点——期间组件内部调用的
     *       {@code bind*}/{@code createEffect} 自动归属本组件作用域（不会泄漏到外层）；</li>
     *   <li>把组件根节点 append 到 {@code parent}，并登记卸载回调（{@link MountHandle#unmount()} 时从 DOM 摘除）。</li>
     * </ol>
     *
     * <p>组件函数是纯构建逻辑：用 {@code document.element(...)} 等建节点、用 {@code bind*} 把 signal
     * 绑到属性，<b>不在函数体内做命令式后续更新</b>——动态行为一律落在 effect 里（I3）。</p>
     *
     * @param parent    挂载点父元素
     * @param component 组件构建函数，接收文档、返回组件根节点，仅执行一次
     * @return 挂载句柄，可单独 {@link MountHandle#unmount()}
     */
    public MountHandle mount(ElementNode parent, Function<UiDocument, ElementNode> component) {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(component, "component");
        Owner scope = Owner.current();
        Owner componentOwner = (scope != null ? scope : rootOwner).createChild();
        ElementNode[] rootHolder = new ElementNode[1];
        componentOwner.run(() -> {
            ElementNode root = component.apply(document);
            Objects.requireNonNull(root, "component root");
            rootHolder[0] = root;
            parent.append(root);
        });
        ElementNode mountedRoot = rootHolder[0];
        componentOwner.onCleanup(() -> {
            if (mountedRoot.getParent() != null) {
                mountedRoot.getParent().removeChild(mountedRoot);
            }
        });
        return new MountHandle(componentOwner, mountedRoot);
    }

    /**
     * 在 {@code container} 下挂载一个 <b>keyed 动态列表</b>（信条三，I5）：{@code itemsSignal} 变化时，
     * 按 {@code keyFn} 对齐新旧子项，<b>只增删移动变化项</b>，key 不变的项复用其 DOM 节点与生命周期作用域、
     * 不重建（守 I7：干净子树被跳过）。
     *
     * <p><b>红线（I5）</b>：协调的 DOM 操作严格收窄在 {@code container} 内部，绝不退化成全树 diff。
     * 列表的 reconcile effect <b>只订阅 {@code itemsSignal} 本身</b>；每次协调及每个 item 的构建都在
     * <b>非追踪</b>上下文（{@link Effect#untrack}）中执行——因此某个 item 内部读取的 signal <b>不会</b>
     * 回流成为「列表」的依赖，单项变化只重跑该项自己的 effect，不触发整列表重协调。</p>
     *
     * <p><b>每项一个作用域</b>：每个列表项拥有独立子 {@link Owner}（复用 {@link #mount} 的生命周期语义），
     * 其内部 {@code bind*}/{@code createEffect} 自动归属该项；项被移除（或整个列表卸载）时，作用域 dispose
     * 递归清理其 effect 并把 DOM 节点摘除。</p>
     *
     * <p><b>组件签名</b>：{@code itemComponent} 接收文档与<b>当前项数据快照</b>，返回该项根节点，<b>每个 key
     * 只调用一次</b>（I3）。若项需要随数据更新，应在组件体内对 item 派生的 signal 建立 {@code bind}/effect，
     * 而非依赖 forEach 重建该项。</p>
     *
     * @param container     列表挂载点父元素（协调范围严格限定于此节点的子节点）
     * @param itemsSignal   列表数据源（reconcile effect 唯一订阅的依赖）
     * @param keyFn         项 → 稳定唯一 key 的映射（同一次列表内 key 不得重复，否则抛异常）
     * @param itemComponent 项组件构建函数，接收文档与项数据，返回项根节点，每个 key 仅执行一次
     * @param <T>           列表项数据类型
     * @return 列表句柄，可单独 {@link ListHandle#dispose()}
     */
    public <T> ListHandle forEach(ElementNode container,
                                  ReadableSignal<? extends List<T>> itemsSignal,
                                  Function<? super T, ?> keyFn,
                                  BiFunction<UiDocument, ? super T, ElementNode> itemComponent) {
        Objects.requireNonNull(container, "container");
        Objects.requireNonNull(itemsSignal, "itemsSignal");
        Objects.requireNonNull(keyFn, "keyFn");
        Objects.requireNonNull(itemComponent, "itemComponent");

        Owner scope = Owner.current();
        Owner listOwner = (scope != null ? scope : rootOwner).createChild();
        KeyedListReconciler<T> reconciler =
                new KeyedListReconciler<>(document, container, keyFn, itemComponent, listOwner);

        // reconcile effect 在 listOwner 作用域内创建（随列表卸载一并清理）。
        // body 只读 itemsSignal（唯一追踪点），实际协调在 untrack 内执行——item 构建/更新读取的
        // signal 不会回流成列表依赖（守 I5：杜绝全列表 diff）。
        listOwner.run(() -> Effect.create(() -> {
            List<T> items = itemsSignal.get();
            Effect.untrack(() -> reconciler.reconcile(items));
        }));
        return new ListHandle(listOwner);
    }

    /**
     * 在 {@code parent} 下挂载一棵 <b>条件渲染</b>子树（信条三）：{@code condition} 为真时构建并插入
     * {@code component}，为假时卸载它。补齐声明式三基石的最后一块（条件 / 列表 / 文本）——配合 {@link #forEach}
     * 与 {@link #bindText}，即可纯靠改 signal 驱动一个含条件、列表、文本的完整界面，无需命令式增删节点（信条一，I1）。
     *
     * <p><b>稳定不重建（I7）</b>：{@code condition} 在两次刷新间保持同一真假值时，已挂载的内容子树被完整跳过、
     * 不重建；只有真假翻转才触发一次挂载或卸载。</p>
     *
     * <p><b>红线（I5）</b>：条件的 reconcile effect <b>只订阅 {@code condition} 本身</b>；内容的构建在
     * <b>非追踪</b>上下文（{@link Effect#untrack}）中执行——内容内部读取的 signal <b>不会</b>回流成为「条件」的
     * 依赖，内容内部变化只重跑其自己的 effect，不触发条件重算。DOM 操作严格收窄在 {@code parent} 内。</p>
     *
     * <p><b>anchor 占位</b>：调用时立即在 {@code parent} 末尾追加一个 {@code display:none} 的空锚点元素，
     * 标记内容在声明顺序里的位置；内容总插入到锚点之前。因此若需要内容出现在 {@code parent} 的特定位置，
     * 应在期望位置调用 {@code show}（其后再 append 的兄弟节点会排在锚点之后）。</p>
     *
     * <p><b>作用域</b>：每次挂载的内容拥有独立子 {@link Owner}（复用 {@link #mount} 生命周期语义），
     * 内部 {@code bind*}/{@code createEffect} 自动归属它；卸载（或整个条件块 {@link ConditionHandle#dispose()}）
     * 时随作用域清理 effect 与 DOM。</p>
     *
     * @param parent    挂载点父元素（协调范围严格限定于此节点的子节点）
     * @param condition 布尔条件源（reconcile effect 唯一订阅的依赖）；{@code null} 值视为 {@code false}
     * @param component 内容构建函数，接收文档、返回内容根节点，每次挂载执行一次
     * @return 条件块句柄，可单独 {@link ConditionHandle#dispose()}
     */
    public ConditionHandle show(ElementNode parent,
                                ReadableSignal<Boolean> condition,
                                Function<UiDocument, ElementNode> component) {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(component, "component");

        Owner scope = Owner.current();
        Owner condOwner = (scope != null ? scope : rootOwner).createChild();

        // 占位锚点：display:none，不参与布局，仅标记内容的声明顺序位置。
        ElementNode anchor = document.div();
        anchor.setAttribute("data-ui-show-anchor", "true");
        anchor.style().setDisplay(UiDisplay.NONE);
        parent.append(anchor);

        ConditionalRenderer renderer =
                new ConditionalRenderer(document, parent, anchor, component, condOwner);
        // 锚点随条件块卸载一并摘除。
        condOwner.onCleanup(() -> {
            if (anchor.getParent() != null) {
                anchor.getParent().removeChild(anchor);
            }
        });

        // reconcile effect 只读 condition（唯一追踪点）；挂载/卸载在 untrack 内执行（守 I5）。
        condOwner.run(() -> Effect.create(() -> {
            Boolean value = condition.get();
            boolean visible = Boolean.TRUE.equals(value);
            Effect.untrack(() -> renderer.update(visible));
        }));
        return new ConditionHandle(condOwner);
    }

    /**
     * 释放本运行时持有的全部组件作用域、effect 订阅与挂载节点。
     *
     * <p>宿主生命周期结束（如 Screen 关闭）时调用；重复调用安全。</p>
     */
    public void dispose() {
        rootOwner.dispose();
    }

    /**
     * 事件订阅绑定句柄（{@link #on(Runnable, Runnable)} / {@link #onClick(ElementNode, Runnable)} 返回）：
     * 持有「最多执行一次」的退订回调，支持提前 {@link #dispose()}。
     *
     * <p><b>幂等</b>：退订只跑一次——无论先由作用域 dispose 触发的 onCleanup、还是先由 {@link #dispose()}
     * 触发，{@code unregister} 都不会被重复调用（用 {@code disposed} 守卫）。因此「自动退订（随作用域卸载）」
     * 与「手动退订（显式 dispose）」可任意先后、可重复调用，互不冲突。</p>
     */
    public static final class Binding {

        private final Runnable unregister;
        private boolean disposed = false;

        private Binding(Runnable unregister) {
            this.unregister = unregister;
        }

        /**
         * 提前退订外部事件源；幂等（重复调用、或与作用域自动退订并发都只执行一次）。
         */
        public void dispose() {
            if (disposed) {
                return;
            }
            disposed = true;
            unregister.run();
        }
    }

    /**
     * keyed 列表句柄：持有列表生命周期作用域，支持整列表卸载（清理全部项作用域与协调 effect）。
     */
    public static final class ListHandle {

        private final Owner owner;

        private ListHandle(Owner owner) {
            this.owner = owner;
        }

        /**
         * 卸载整个列表：dispose 列表作用域，递归清理协调 effect 与全部列表项（含其 effect 与 DOM 节点）。
         * 重复调用安全。
         */
        public void dispose() {
            owner.dispose();
        }
    }

    /**
     * 条件渲染句柄：持有条件块生命周期作用域，支持整块卸载（清理条件 effect、当前已挂载内容及占位锚点）。
     */
    public static final class ConditionHandle {

        private final Owner owner;

        private ConditionHandle(Owner owner) {
            this.owner = owner;
        }

        /**
         * 卸载整个条件块：dispose 条件作用域，递归清理条件 reconcile effect、当前挂载的内容子树
         * （含其 effect 与 DOM 节点）以及占位锚点。重复调用安全。
         */
        public void dispose() {
            owner.dispose();
        }
    }

    /**
     * 组件挂载句柄：持有组件根节点与其生命周期作用域，支持单独卸载。
     */
    public static final class MountHandle {

        private final Owner owner;
        private final ElementNode root;

        private MountHandle(Owner owner, ElementNode root) {
            this.owner = owner;
            this.root = root;
        }

        /**
         * 返回组件根节点。
         *
         * @return 组件根节点
         */
        public ElementNode getRoot() {
            return root;
        }

        /**
         * 卸载组件：递归清理本组件作用域的全部 effect 与子组件，并把组件根节点从 DOM 摘除。
         * 重复调用安全。
         */
        public void unmount() {
            owner.dispose();
        }
    }
}
