/**
 * scene 新栈控件层 —— 纯静态工厂 + 响应式契约的控件库。
 *
 * <h2>控件层契约红线（违反即阻断合并）</h2>
 *
 * <p>本包所有控件必须遵守以下 5 条契约红线，后续所有控件照此评审。
 * {@link club.heiqi.uilib.ui.scene.control.SceneButton} 是首个参考实现，
 * 用一个文件撞齐 scene 全部新地基能力（flex 居中 + padding + 边框 + 圆角 +
 * 裁剪 + 非白文字 + 四态），并确立后续控件照抄的契约范本。</p>
 *
 * <h3>R1：控件必须是纯静态工厂</h3>
 * <p>控件类必须是 {@code private} 构造器 + {@code static create()} 工厂，
 * 控件类自身禁止任何实例字段。控件是无状态的工厂，状态全部由 signal 承载。</p>
 *
 * <h3>R2：Props 只接受只读 signal 或不可变常量 + 输出回调</h3>
 * <p>Props 字段只能是 {@link club.heiqi.uilib.ui.reactive.ReadableSignal}{@code <T>}
 * 或不可变常量，输出只能是回调（{@code Runnable}/{@code Consumer}）。
 * 禁止可变容器、禁止直接传 {@code SceneNode}。</p>
 *
 * <h3>R3：组件函数只执行一次（I3）</h3>
 * <p>{@code create} 返回的 {@code Supplier} 体只执行一次：只允许建 SceneNode 树 +
 * 设静态属性 + {@code rt.bind/bindText/on/focusable}。禁止在 Supplier 体内读 signal
 * 当前值（{@code signal.get()}）做 if 分支建树——动态部分必须落到 bind。</p>
 *
 * <h3>R4：外观随状态变化只能经 rt.bind(computed(...))</h3>
 * <p>外观随状态变化只能通过 {@code rt.bind(Invalidation, computed(...), setter)} 派生，
 * 禁止在 {@code rt.on} 的 handler 里直接调任何 SceneNode 的 {@code setXxx}（I1/I11）。
 * handler 只允许 {@code signal.set} 或调 props 回调。</p>
 *
 * <h3>R5：交互态只能读 interactionState 暴露的 signal</h3>
 * <p>交互态（hover/pressed/focus）只能读 {@code rt.interactionState(node)} 暴露的
 * 只读 signal，禁止控件自己维护 {@code boolean active/pressed} 字段。
 * 交互态的权威源是 {@link club.heiqi.uilib.ui.scene.input.SceneInputRouter}。</p>
 */
package club.heiqi.uilib.ui.scene.control;
