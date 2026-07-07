/**
 * scene 新栈控件层 —— 纯静态工厂 + 响应式契约的控件库。
 *
 * <h2>控件层契约红线（违反即阻断合并）</h2>
 *
 * <p>本包所有控件必须遵守以下契约红线，后续所有控件照此评审。
 * {@link club.heiqi.uilib.ui.scene.control.SceneButton} 是首个参考实现，
 * 用一个文件撞齐 scene 全部新地基能力（flex 居中 + padding + 边框 + 圆角 +
 * 裁剪 + 非白文字 + 四态），并确立后续控件照抄的契约范本。R1-R13 共 13 条红线。</p>
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
 * <p>外观随状态变化只能通过 {@code rt.bind(computed(...), setter)} 派生，
 * 禁止在 {@code rt.on} 的 handler 里直接调任何 SceneNode 的 {@code setXxx}（I1/I11）。
 * handler 只允许 {@code signal.set} 或调 props 回调。</p>
 *
 * <h3>R5：交互态只能读 interactionState 暴露的 signal</h3>
 * <p>交互态（hover/pressed/focus）只能读 {@code rt.interactionState(node)} 暴露的
 * 只读 signal，禁止控件自己维护 {@code boolean active/pressed} 字段。
 * 交互态的权威源是 {@link club.heiqi.uilib.ui.scene.input.SceneInputRouter}。</p>
 *
 * <h3>R6：装饰性子节点必须 setHitTestable(false)</h3>
 * <p>复合控件中纯装饰、不独立接收交互的子节点（如标签文字、图标、滑轨），
 * 必须在建树时调 {@code node.setHitTestable(false)}，使 hit-test 命中穿透到控件根节点
 * （交互单元）。交互态（pressed/hovered）只绑在控件根节点，读
 * {@code rt.interactionState(root)} 的 signal。</p>
 *
 * <p>背景：{@link club.heiqi.uilib.ui.scene.input.SceneInputRouter} 的 POINTER_DOWN
 * 只给<b>最深命中节点</b>写 pressed（不冒泡）。若装饰子节点（如 label 文字）仍参与命中，
 * 用户点文字时最深命中是子节点 → 控件根节点 pressed 永远 false → 点文字按钮不会 pressed。
 * 让装饰子节点退出命中候选，命中穿透到根节点，即可修复此「容器挂交互态/命中叶节点」拓扑错配。</p>
 *
 * <p><b>★ hitTestable=false 仅用于「装饰穿透」，禁止用它做逻辑禁用</b>——
 * 禁用态走 enabled signal 控制 {@code onClick} 与视觉，不靠命中穿透。</p>
 *
 * <p><b>★ 段式控件（Segmented）中每个「段」是独立交互单元</b>，段本身 hitTestable=true，
 * 仅段内文字/图标 hitTestable=false 穿透到所属段。</p>
 *
 * <h3>R7：受控双向控件必须零内部状态</h3>
 * <p>带可切换值的受控双向控件（如
 * {@link club.heiqi.uilib.ui.scene.control.SceneCheckbox}、
 * {@link club.heiqi.uilib.ui.scene.control.SceneToggle}）必须<b>零内部状态</b>——
 * 当前值由外部只读 signal 驱动，交互时只经 {@code onChange} 回调把「期望的新值」交还外部，
 * 控件自身<b>绝不翻转或缓存值</b>（守 R1/R5/I11，避免双向状态源不一致）。</p>
 *
 * <p>背景：若控件自持 {@code boolean checked} 字段并在 handler 里自己翻转，则同时存在
 * 「控件内部值」与「外部 signal 值」两个状态源，二者一旦失步（外部 set 与内部翻转时序错位、
 * 外部受约束拒绝某次切换等）即产生不可调和的视图/模型分裂。受控范式只保留外部 signal 这一唯一源，
 * 控件退化为「读外部值渲染 + 把期望新值上抛」的纯函数式视图，从根上杜绝双源。</p>
 *
 * <p><b>★ 激活落点</b>：CLICK / Enter / Space 激活时一律
 * {@code onChange.accept(!currentSignal.get())}，由外部决定是否真正 set 回受控 signal；
 * 控件在外部回 set 前视觉保持旧值（这正是「受控」的语义，非 bug）。</p>
 *
 * <h3>R8：多选项单选受控控件必须零状态</h3>
 * <p>带「N 选 1」语义的受控控件（如
 * {@link club.heiqi.uilib.ui.scene.control.SceneRadioGroup}、
 * {@link club.heiqi.uilib.ui.scene.control.SceneSegmented}），当前选中项由外部
 * {@link club.heiqi.uilib.ui.reactive.ReadableSignal}{@code <Integer> selectedIndex}
 * <b>唯一驱动</b>；激活某选项时只经 {@code onSelect.accept(targetIndex)} 上抛期望选中项，
 * 控件<b>绝不自己维护或修改 selectedIndex</b>。</p>
 *
 * <p>这是 R7 从二值布尔到 N 值下标的推广——同一灵魂（外部唯一源 + 期望值上抛），
 * 杜绝「内部选中态」与「外部 signal」双源。激活落点：CLICK / Enter / Space 一律
 * {@code onSelect.accept(targetIndex)}，由外部决定是否真正 set 回 selectedIndex signal；
 * 方向键导航时 handler 内读 {@code selectedIndex.get()} 算相邻下标后 onSelect 上抛
 * （读 signal 合法，I11 只禁写节点属性槽不禁读 signal），并经 {@code rt.requestFocus}
 * 移动焦点（受控逃生舱合法）。</p>
 *
 * <h3>R9：受控文本输入控件必须零内部状态</h3>
 * <p>带文本输入语义的受控控件（如
 * {@link club.heiqi.uilib.ui.scene.control.SceneTextInput}），当前文本由外部
 * {@link club.heiqi.uilib.ui.reactive.ReadableSignal}{@code <String> value}
 * <b>唯一驱动</b>；字符输入/退格时控件 handler 在<b>纯函数</b>内读 {@code value.get()}、
 * 算出期望的新字符串，只经 {@code onChange.accept(newString)} 上抛<b>真实 String</b>，
 * 控件<b>绝不自己缓存或修改 value</b>（守 R1/R5/I11/R9）。这是 R7 从二值布尔、R8 从 N 值下标
 * 到任意 String 的推广——同一灵魂（外部唯一源 + 期望值上抛），杜绝「内部文本缓冲」与
 * 「外部 signal」双源。</p>
 *
 * <p><b>★ caret 位置是布局派生而非状态</b>（档位 A）：caret 恒在文本末尾，其位置完全由
 * {@code f(value 长度)} 经 ROW 布局逐子定位自然决定（caret 节点排在文本叶之后，cursor 累加
 * 把它推到文本右侧），<b>零本地 signal、零度量</b>。caret 唯一需要的派生是<b>可见性</b>
 * ＝聚焦态，读 {@code rt.interactionState(root).focused()} 的 signal 切 caret 背景色
 * 透明↔实色（守 R5，不自维护 boolean）。档位 A 明确不做：选区、方向键、字符级定位、
 * IME、剪贴板、闪烁。</p>
 *
 * <p><b>★ 密码掩码只影响显示层</b>：PASSWORD 类型经 displayText computed 把真实值按
 * <b>码点数</b>替换为等量掩码符（{@code \u2022}），但 {@code onChange} 与受控 value
 * <b>始终是真实明文</b>——掩码是渲染派生，绝不污染回调真值（受控范式只保留外部 signal
 * 这一唯一真值源，显示层退化为纯函数视图）。</p>
 *
 * <p><b>★ readOnly 与 disabled 区分</b>：disabled（{@code enabled=false}）＝不可聚焦
 * ＋阻断所有输入＋灰态；readOnly＝可聚焦可见＋仅阻断文本写入（字符/退格 handler 早退）。
 *   maxLength 按<b>码点数</b>判定（{@code String.codePointCount}），填满拒绝新增不截断已有；
 *   退格删<b>末尾一个码点</b>（{@code offsetByCodePoints}），代理对（emoji）删整码点不删半 char。</p>
 *
 * <h3>R10：条件渲染内容区切换必须经 show，禁止命令式挂卸</h3>
 * <p>带「N 选 1 内容区切换」语义的复合控件（如
 * {@link club.heiqi.uilib.ui.scene.control.SceneTab}），其内容区的页切换<b>必须</b>落成
 * N 个独立 {@code rt.show(contentPanel, Computed(activeIndex==i), tabPanels.get(i))}——
 * 每页一个 show，condition 是该页是否活动的派生 {@link club.heiqi.uilib.ui.reactive.Computed}，
 * 由 show 引擎按 condition 挂载/卸载内容（守 I5 收窄、I7 稳定不重建）。</p>
 *
 * <p><b>★ 双重禁止</b>：①<b>绝不</b>在 {@code create} 的 {@code Supplier} 体内
 * {@code activeIndex.get()} 做 {@code if} 分支建树（违 R3：组件函数只执行一次，分支建树会把
 * 「当前活动页」固化进唯一一次建树，后续切页失灵）；②<b>绝不</b>命令式
 * {@code clearChildren() + 重新 append} 切换内容（旧栈 {@code DocumentTabControl.mountActiveTab}
 * 老路，违 I1/I11：UI 变化只经 signal→show 派生，不靠命令式重挂）。各页 builder 必须是
 * Props 传入的独立 {@link java.util.function.Supplier}，分别交各自的 show，<b>不得合并</b>。</p>
 *
 * <p>这是「UI = f(state)」声明式范式在「内容区切换」场景的落地：内容区是 activeIndex 的纯函数派生，
 * show 引擎负责按 condition 增删子树，控件本身只声明「哪页在何条件下显示」，绝不命令式驱动挂卸。</p>
 *
 * <h3>R11：浮层显隐必须经 signal→portal 派生，禁止命令式挂卸</h3>
 * <p>带浮层语义的控件（如 {@link club.heiqi.uilib.ui.scene.control.SceneSelect}）必须把浮层可见性
 * 表达为 {@link club.heiqi.uilib.ui.reactive.ReadableSignal}{@code <Boolean>}，并交由
 * {@code rt.portal(...)} 或 {@code rt.portalAnchored(...)} 响应式派生挂载/卸载 overlay root。
 * 事件 handler 只能写 visible signal 或上抛业务回调，禁止在 handler 中命令式注册、提升、摘除
 * {@link club.heiqi.uilib.ui.scene.node.SceneNode}。</p>
 *
 * <p>这是 R10 的 top-layer 版本：主树条件内容走 show，浮层内容走 portal；二者都坚持
 * 「显隐 = signal 的纯函数派生」，避免控件层绕过运行时生命周期与 Owner cleanup 直接改树。</p>
 *
 * <h3>R12：两派分层规则（Primitive 行为核心 / 组件样式壳）</h3>
 * <p>控件层按「是否承载样式」分两派，命名与返回类型严格分层：</p>
 * <ul>
 *   <li><b>无样式行为核心 = {@code XxxPrimitive}</b>：返回 {@code Result}（具名字段，供上层组合）。
 *       只负责行为与结构骨架，不夹带视觉样式（颜色/边框/圆角等由调用方决定）。</li>
 *   <li><b>有样式组件 = {@code Xxx}</b>：返回 {@code Supplier<SceneNode>}（供 {@code rt.mount} 挂载）。
 *       在 Primitive 之上叠样式与默认交互，是面向业务的成品控件。</li>
 * </ul>
 * <p><b>Props 形态取舍</b>：字段 ≤6 且无可选参 → {@code record Props}；字段 >6 或多可选参 →
 * {@code class Props + Builder}（Builder 统一可选参缺省填充，避免构造器重载爆炸）。</p>
 *
 * <h3>R13：浮层显隐状态独立可写，禁派生自瞬态交互态</h3>
 * <p>控件的浮层显隐状态（{@code expanded}/{@code open}/{@code visible}）必须是<b>独立可写</b>
 * {@link club.heiqi.uilib.ui.reactive.Signal}{@code <Boolean>}，由明确用户意图动作（CLICK / ENTER /
 * ARROW 等）显式翻转，或经独立 signal 中转的 dismiss 请求写入。</p>
 *
 * <p><b>禁止</b>把显隐状态直接定义为读 {@code focused}/{@code hovered}/{@code pressed} 等交互态的
 * {@link club.heiqi.uilib.ui.reactive.Computed}——这些交互态由
 * {@link club.heiqi.uilib.ui.scene.input.SceneInputRouter} 权威状态机在 route 期即时改写，
 * DOWN 隐式失焦等路由行为会在 DOWN/UP 跨帧间掐断派生的显隐态，导致浮层在手势中途卸载、
 * CLICK 无法合成（真因 D1，2026-07 字符配置下拉框故障）。</p>
 *
 * <p>若显隐需响应 focus（如失焦关闭），应在 {@code focused} 的 effect 内写显隐 signal
 * （{@code open.set(false)}），而非令 {@code open = Computed(focused && ...)}。</p>
 *
 * <p><b>对照</b>：{@link club.heiqi.uilib.ui.scene.control.SceneSelect} 的 {@code expanded}
 * 是独立可写 Signal（健康）；历史 {@code SceneAutocompletePrimitive.expanded} 曾派生自
 * {@code focused}（脆弱，P0 框架侧豁免止血，P2 组件层重构计划中）。违反即阻断合并。</p>
 */
package club.heiqi.uilib.ui.scene.control;
