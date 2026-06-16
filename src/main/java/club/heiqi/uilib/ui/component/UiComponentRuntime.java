package club.heiqi.uilib.ui.component;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.reactive.Effect;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.style.UiStyleChangeImpact;
import club.heiqi.uilib.ui.style.props.UiVisibility;
import club.heiqi.uilib.ui.style.values.UiTransform;

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
     * 任意依赖变化时 body 重跑，并按 {@code impact} 指定的级别标记文档失效：</p>
     * <ul>
     *   <li>{@code LAYOUT} → {@link UiDocument#markLayoutDirty()}</li>
     *   <li>{@code COMPOSITE} → {@link UiDocument#markCompositeDirty()}（transform/opacity，走 composite-only 回放）</li>
     *   <li>{@code PAINT}（及其它）→ {@link UiDocument#markPaintDirty()}</li>
     * </ul>
     * <p>effect 归属策略：若处于某组件挂载作用域内（{@link #mount} 期间），自动归属该组件 Owner，
     * 随组件卸载一并清理；否则归属运行时根 Owner，{@link #dispose()} 时统一清理。</p>
     *
     * @param impact 影响级别
     * @param body   effect 体，在追踪上下文中执行
     * @return 创建的 effect（通常无需持有）
     */
    public Effect createEffect(UiStyleChangeImpact impact, Runnable body) {
        Objects.requireNonNull(impact, "impact");
        Objects.requireNonNull(body, "body");
        Owner targetOwner = Owner.current();
        if (targetOwner == null) {
            targetOwner = rootOwner;
        }
        return targetOwner.createEffect(() -> {
            body.run();
            if (impact == UiStyleChangeImpact.LAYOUT) {
                document.markLayoutDirty();
            } else if (impact == UiStyleChangeImpact.COMPOSITE) {
                document.markCompositeDirty();
            } else {
                document.markPaintDirty();
            }
        });
    }

    /**
     * 通用细粒度绑定：把一个 {@link ReadableSignal} 的值写入由 {@code applier} 指定的样式属性
     * （信条二：signal → 节点属性绑定）。
     *
     * <p>建立后，源每次变化都会重跑 effect 并执行 {@code applier}；effect 体跑完后按 {@code impact}
     * 触发对应级别的文档失效（LAYOUT → 重排，PAINT → 重绘，COMPOSITE → composite-only 回放）。
     * 源值为 {@code null} 时跳过 applier，不写入。</p>
     *
     * <p>{@link club.heiqi.uilib.ui.reactive.Signal} 与 {@link club.heiqi.uilib.ui.reactive.Computed}
     * 均实现 {@link ReadableSignal}，故派生值也可直接作为源喂入。</p>
     *
     * @param impact  本绑定写入属性的失效级别（应与目标属性在 {@code UiStyleProperty} 的标注一致）
     * @param source  数据源（signal 或 computed）
     * @param applier 把源值写入样式的回调，仅在源值非 {@code null} 时调用
     * @param <T>     值类型
     * @return 创建的 effect（通常无需持有）
     */
    public <T> Effect bind(UiStyleChangeImpact impact, ReadableSignal<T> source, Consumer<T> applier) {
        Objects.requireNonNull(impact, "impact");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(applier, "applier");
        return createEffect(impact, () -> {
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
     * @see #bind(UiStyleChangeImpact, ReadableSignal, Consumer)
     */
    public Effect bindOpacity(ElementNode element, ReadableSignal<Float> source) {
        Objects.requireNonNull(element, "element");
        return bind(UiStyleChangeImpact.COMPOSITE, source,
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
     * @see #bind(UiStyleChangeImpact, ReadableSignal, Consumer)
     */
    public Effect bindTransform(ElementNode element, ReadableSignal<UiTransform> source) {
        Objects.requireNonNull(element, "element");
        return bind(UiStyleChangeImpact.COMPOSITE, source,
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
     * @see #bind(UiStyleChangeImpact, ReadableSignal, Consumer)
     */
    public Effect bindBackgroundColor(ElementNode element, ReadableSignal<Integer> source) {
        Objects.requireNonNull(element, "element");
        return bind(UiStyleChangeImpact.PAINT, source,
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
     * @see #bind(UiStyleChangeImpact, ReadableSignal, Consumer)
     */
    public Effect bindTextColor(ElementNode element, ReadableSignal<Integer> source) {
        Objects.requireNonNull(element, "element");
        return bind(UiStyleChangeImpact.PAINT, source,
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
     * @see #bind(UiStyleChangeImpact, ReadableSignal, Consumer)
     */
    public Effect bindVisibility(ElementNode element, ReadableSignal<UiVisibility> source) {
        Objects.requireNonNull(element, "element");
        return bind(UiStyleChangeImpact.PAINT, source,
                value -> element.style().setVisibility(value));
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
     * 释放本运行时持有的全部组件作用域、effect 订阅与挂载节点。
     *
     * <p>宿主生命周期结束（如 Screen 关闭）时调用；重复调用安全。</p>
     */
    public void dispose() {
        rootOwner.dispose();
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
