package club.heiqi.uilib.ui.component;

import java.util.Objects;
import java.util.function.Function;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.reactive.Owner;

/**
 * 条件渲染协调器（信条三）：按布尔条件挂载/卸载一棵子树——条件为真时构建内容并插入到 {@code anchor}
 * 之前，为假时 dispose 内容作用域（其 {@code onCleanup} 把 DOM 节点摘除）。
 *
 * <p>条件渲染本质是「0 或 1 项的 keyed 列表」，与 {@link KeyedListReconciler} 同源：</p>
 * <ul>
 *   <li><b>稳定不重建（I7）</b>：条件值未跨越真假边界时（连续两次 true 或连续两次 false），
 *       {@link #update(boolean)} 直接返回、不动 DOM——已挂载的内容子树被完整跳过，不重建。</li>
 *   <li><b>收窄范围（I5）</b>：全部 DOM 操作严格限定在 {@code parent} 内（{@code insertBefore}/{@code removeChild}），
 *       绝不触达外部节点。</li>
 *   <li><b>作用域隔离（I3）</b>：每次挂载的内容拥有独立子 {@link Owner}（复用 {@code mount} 生命周期语义），
 *       其内部 {@code bind*}/{@code createEffect} 自动归属该作用域；卸载时随作用域一并清理。</li>
 * </ul>
 *
 * <p><b>anchor 占位</b>：{@code anchor} 是一个 {@code display:none} 的空元素，始终驻留在 {@code parent} 中，
 * 标记 {@code show} 在声明顺序里的位置。内容总是插入到 anchor 之前，因此即使内容反复增删、或 {@code parent}
 * 下有其它兄弟节点，内容也总回到正确位置（等价于浏览器框架的 marker node 方案）。</p>
 */
final class ConditionalRenderer {

    private final UiDocument document;
    private final ElementNode parent;
    private final ElementNode anchor;
    private final Function<UiDocument, ElementNode> component;
    private final Owner condOwner;

    /** 当前内容作用域；{@code null} 表示当前未挂载内容（条件为假）。 */
    private Owner contentOwner;

    ConditionalRenderer(UiDocument document,
                        ElementNode parent,
                        ElementNode anchor,
                        Function<UiDocument, ElementNode> component,
                        Owner condOwner) {
        this.document = document;
        this.parent = parent;
        this.anchor = anchor;
        this.component = component;
        this.condOwner = condOwner;
    }

    /**
     * 按最新条件值协调挂载状态。
     *
     * <p>必须在<b>非追踪</b>上下文调用（内容构建/更新读取的 signal 不得回流为条件订阅，否则内容内部
     * signal 变化会反向触发条件重算）。</p>
     *
     * @param visible 条件是否为真
     */
    void update(boolean visible) {
        if (visible) {
            if (contentOwner != null) {
                return; // 已挂载，条件仍为真 → 跳过（守 I7：稳定子树不重建）
            }
            mount();
        } else {
            if (contentOwner == null) {
                return; // 已卸载，条件仍为假 → 无操作
            }
            contentOwner.dispose(); // onCleanup 摘除 DOM 节点
            contentOwner = null;
        }
    }

    private void mount() {
        Owner owner = condOwner.createChild();
        ElementNode[] holder = new ElementNode[1];
        owner.run(() -> {
            ElementNode node = component.apply(document);
            holder[0] = Objects.requireNonNull(node, "show content root");
        });
        ElementNode node = holder[0];
        // referenceChild=anchor：插到占位锚点之前，保持声明顺序位置。
        parent.insertBefore(node, anchor);
        owner.onCleanup(() -> {
            if (node.getParent() != null) {
                node.getParent().removeChild(node);
            }
        });
        contentOwner = owner;
    }
}
