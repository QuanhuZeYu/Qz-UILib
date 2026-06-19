package club.heiqi.uilib.ui.scene.component;

import java.util.Objects;
import java.util.function.Supplier;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 场景树条件渲染协调器（信条三）：按布尔条件挂载/卸载一棵子树——条件为真时构建内容并插入到
 * {@code anchor} 之前，为假时 dispose 内容作用域（其 {@code onCleanup} 把内容节点摘除）。
 *
 * <p>本类是旧栈 {@code club.heiqi.uilib.ui.component.ConditionalRenderer} 在新场景树栈上的对等实现：
 * 操作对象从 {@code ElementNode} 换成 {@link SceneNode}，去掉 {@code UiDocument} 依赖（新栈无
 * document，内容直接由 {@link Supplier} 工厂构造）。条件渲染本质是「0 或 1 项的 keyed 列表」：</p>
 * <ul>
 *   <li><b>稳定不重建（I7）</b>：条件值未跨越真假边界时（连续两次 true 或连续两次 false），
 *       {@link #update(boolean)} 直接返回、不动场景树——已挂载的内容子树被完整跳过，不重建。</li>
 *   <li><b>收窄范围（I5）</b>：全部结构操作严格限定在 {@code parent} 内
 *       （{@link SceneNode#insertBefore}/{@link SceneNode#removeChild}），绝不触达外部节点。</li>
 *   <li><b>作用域隔离（I3）</b>：每次挂载的内容拥有独立子 {@link Owner}，其内部 bind/effect 自动
 *       归属该作用域；卸载时随作用域一并清理。</li>
 * </ul>
 *
 * <h3>为什么 show 不走 {@code applyChildReconcile}（有意设计，严禁后续改动）</h3>
 * <p>show 的 {@code parent} 不是 show 独占的容器，parent 下可能并存其它兄弟节点。
 * {@link SceneNode#applyChildReconcile} 会用 reconciler 提交的 finalOrder <b>整体替换</b>
 * children 列表，这会把不归 show 管的兄弟节点误删。因此 show 故意采用
 * 「anchor 占位 + insertBefore/removeChild 副作用」的精确局部操作驱动，而非批量 reconcile。
 * <b>严禁后续把 show 改成走 applyChildReconcile。</b></p>
 *
 * <h3>anchor 占位语义</h3>
 * <p>{@code anchor} 是一个零尺寸、不可见的空 {@link SceneNode}（{@code new SceneNode()} 不设
 * text/背景色/preferredHeight：layout 自然算出 height=0，paint 不产生可见命令），始终驻留在
 * {@code parent} 中，标记 show 在声明顺序里的位置。内容总是插入到 anchor 之前，因此即使内容反复
 * 增删、或 parent 下有其它兄弟节点，内容也总回到正确位置（等价于浏览器框架的 marker node 方案）。
 * <b>anchor 由调用方（show 方法）创建并 append 到 parent，本类不创建 anchor，只把它当作
 * insertBefore 的锚点使用。</b></p>
 *
 * <h3>非追踪约束（守 I5）</h3>
 * <p>内容工厂读取的 signal 不得回流为条件订阅，否则内容内部 signal 变化会反向触发条件重算。
 * 因此 {@link #update(boolean)} 内部<b>绝不直接订阅任何信号</b>（只读 {@code visible} 入参、
 * 操作场景树、跑子作用域），从而保证调用方可以安全地把 {@code update} 调用整体包在
 * {@code Effect.untrack(...)} 里。effect 本身由调用方在 condOwner.run() 内创建并只订阅条件信号。</p>
 */
final class SceneConditionalRenderer {

    /** 内容挂载到的父节点（show 的宿主容器，可含其它兄弟节点）。 */
    private final SceneNode parent;

    /** 占位锚点：内容总插到它之前，标记 show 在声明顺序里的位置。本类不创建它。 */
    private final SceneNode anchor;

    /** 内容工厂：条件为真时调用一次构建内容根节点（新栈无 document，直接 Supplier 构造）。 */
    private final Supplier<SceneNode> content;

    /** 条件作用域：每次挂载在其下建子作用域，整体卸载时由调用方 dispose 它。 */
    private final Owner condOwner;

    /** 当前内容作用域；{@code null} 表示当前未挂载内容（条件为假）。 */
    private Owner contentOwner;

    /** 当前挂载的内容根节点；{@code null} 表示未挂载。 */
    private SceneNode contentNode;

    /**
     * 构造条件渲染协调器。
     *
     * @param parent    内容挂载到的父节点，不可为 null
     * @param anchor    占位锚点（由调用方创建并已 append 到 parent），内容插到它之前，不可为 null
     * @param content   内容工厂，条件为真时调用一次构建内容根节点，不可为 null
     * @param condOwner 条件作用域，每次挂载在其下建子作用域，不可为 null
     */
    SceneConditionalRenderer(SceneNode parent,
                             SceneNode anchor,
                             Supplier<SceneNode> content,
                             Owner condOwner) {
        this.parent = Objects.requireNonNull(parent, "parent");
        this.anchor = Objects.requireNonNull(anchor, "anchor");
        this.content = Objects.requireNonNull(content, "content");
        this.condOwner = Objects.requireNonNull(condOwner, "condOwner");
    }

    /**
     * 按最新条件值协调挂载状态。
     *
     * <p>四分支语义对齐旧栈 {@code ConditionalRenderer.update}：</p>
     * <ul>
     *   <li>visible=true 且已挂载（contentOwner != null）→ 直接 return（守 I7：稳定子树不重建）。</li>
     *   <li>visible=true 且未挂载 → {@link #mount()} 构建并插入内容。</li>
     *   <li>visible=false 且已挂载 → dispose 内容作用域（onCleanup 摘除内容节点），置空状态。</li>
     *   <li>visible=false 且未挂载 → 无操作（no-op）。</li>
     * </ul>
     *
     * <p>必须在<b>非追踪</b>上下文调用（内容构建/更新读取的 signal 不得回流为条件订阅）。
     * 本方法内部绝不直接订阅任何信号，因此可被调用方安全地包在 {@code Effect.untrack(...)} 里。</p>
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
            contentOwner.dispose(); // onCleanup 摘除内容节点
            contentOwner = null;
            contentNode = null;
        }
    }

    /**
     * 构建并挂载内容子树：在 condOwner 下建子作用域，于其 run() 内调内容工厂构造节点，
     * 把内容插到 anchor 之前，并登记 onCleanup 在卸载时摘除内容节点。
     */
    private void mount() {
        Owner owner = condOwner.createChild();
        SceneNode[] holder = new SceneNode[1];
        owner.run(() -> {
            SceneNode node = content.get();
            holder[0] = Objects.requireNonNull(node, "show content root");
        });
        SceneNode node = holder[0];
        // anchor 作为锚点：插到占位锚点之前，保持声明顺序位置。
        parent.insertBefore(node, anchor);
        owner.onCleanup(() -> {
            if (node.__getParent() != null) {
                node.__getParent().removeChild(node);
            }
        });
        contentOwner = owner;
        contentNode = node;
    }
}
