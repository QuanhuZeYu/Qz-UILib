package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Effect;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.runtime.SceneListHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.SceneNode.WidthSizing;
import club.heiqi.uilib.ui.scene.overlay.AnchorProvider;
import club.heiqi.uilib.ui.scene.overlay.OverlayDismissPolicy;

/**
 * SceneAutocompletePrimitive —— 无样式自动补全行为核心。
 *
 * <p>在 {@link SceneTextInputPrimitive} 之上叠加候选浮层：用户在输入框打字时，按 {@link MatchMode}
 * 实时过滤候选并经 portalAnchored 挂载浮层；键盘 ARROW_UP/DOWN 移动高亮、ENTER 提交、ESCAPE 关闭；
 * 鼠标点击候选项也可提交。本 primitive 不设置任何 chrome（背景、圆角、padding、文本色、cursor 等），
 * 浮层与 item 视觉装饰经 {@link ListboxChrome} 在 overlay 构建调用栈内同步注入。</p>
 *
 * <h3>核心信号链（R13：expanded 独立可写 Signal + effect 驱动）</h3>
 * <ul>
 *   <li>{@code filtered = filterCandidates(value, candidates, matchMode, maxVisible)}（L2 纯静态方法）</li>
 *   <li>{@code expanded = Signal.create(FALSE)}（独立可写，与 {@link SceneSelectPrimitive} 同构），
 *       由监听 {@code focused/enabled/filtered/value} 的 effect 命令式 {@code expanded.set(...)} 驱动；
 *       ESC/ENTER/item CLICK/dismissRequest 等显式关闭意图直接 {@code expanded.set(FALSE)}。</li>
 *   <li>{@code highlightedIndex} 本地可写 signal，键盘导航时移动</li>
 * </ul>
 *
 * <h3>三大关键设计</h3>
 * <ol>
 *   <li><b>filtered 动态 → 必须 keyed diff</b>：浮层候选用 {@link SceneRuntime#forEach} keyed 重载
 *       （keyFn = 候选字符串本身），与 {@link SceneSelectPrimitive} 静态 for 循环不同。</li>
 *   <li><b>expanded effect 驱动（R13 重构，替旧 suppressed 中转）</b>：autocomplete 的「开」动作是
 *       focus + 打字（不像 Select 有显式 trigger CLICK），故用一个监听 focused/filtered/value/enabled
 *       的 effect 在条件满足时 {@code expanded.set(TRUE)}；「关」动作（ESC/ENTER/item CLICK/dismiss）
 *       直接 {@code expanded.set(FALSE)}。打字→value 变→filtered 重算→effect 重评→自动重弹，
 *       替代旧 {@code suppressed.set(FALSE)} 复位机制。effect 内 set signal 必须包
 *       {@link Effect#untrack} 避免下游订阅反向触发本 effect（守 I1/I11，参考
 *       {@code SceneRuntime.portalAnchored} L434-437 同款模式）。</li>
 *   <li><b>focus 时序</b>：autocomplete 的 effect 读 primitive 已声明的 focused signal，
 *       primitive.create 必须在前，autocomplete 组合在后（自然顺序满足）。</li>
 * </ol>
 *
 * <h3>键集正交（F1/F2）</h3>
 * <p>autocomplete 的 KEY_DOWN handler 只在 {@code expanded=true} 时处理 ARROW_DOWN/ARROW_UP/ENTER/ESCAPE；
 * ARROW_LEFT/RIGHT/HOME/END/BACKSPACE/DELETE 完全不碰，交回 primitive（caret 移动/删除正常）。</p>
 *
 * <h3>合规守护</h3>
 * <ul>
 *   <li>R1：纯静态工厂 + 零实例字段，状态全在 Props/Result + 局部 Signal。</li>
 *   <li>R2：Props 全只读 signal/常量+回调，candidates 构建期防御 copy 成不可变列表。</li>
 *   <li>R3：create 体只跑一次（建树 + bind + on + portalAnchored），动态全落 Computed/effect。</li>
 *   <li>R4：候选样式经 chrome + rt.bind/Computed。</li>
 *   <li>R5：focus 读 rt.interactionState(root).focused()（复用 primitive 已声明容器）。</li>
 *   <li>R6：装饰节点（listbox label）hitTestable(false)。</li>
 *   <li>R7/R9：受控零缓存，value 只读透传，选中/输入只 onChange/onSelect.accept。</li>
 *   <li>R10/R11：浮层走 signal→portal，expanded 独立可写 Signal 驱动 portalAnchored 挂卸，
 *       dismissRequest 直接 expanded.set(FALSE)。</li>
 *   <li>R12：返回 Result record。</li>
 *   <li>I1/I11：effect 内 set signal 包 Effect.untrack；KEY_DOWN handler 与 item CLICK handler 只写 signal。</li>
 *   <li>I5：浮层候选用 rt.forEach(filtered, keyFn) keyed diff。</li>
 *   <li>I12：root 锚点 AnchorProvider.forNode 读 absoluteBox（逃生舱①只读几何）。</li>
 * </ul>
 */
public final class SceneAutocompletePrimitive {

    /** 浮层默认最多候选数。 */
    private static final int DEFAULT_MAX_VISIBLE = 8;

    /** 完全无样式的 listbox chrome。 */
    private static final ListboxChrome NOOP_CHROME = new ListboxChrome() {
        @Override
        public void decorateListbox(SceneNode listbox) {
        }

        @Override
        public void decorateItem(ItemHandle item) {
        }
    };

    /** 纯静态工厂，禁止实例化。 */
    private SceneAutocompletePrimitive() {
    }

    /**
     * 候选匹配模式。
     *
     * <ul>
     *   <li>{@link #PREFIX}：候选 normalized 形式以输入 normalized 形式开头（默认）。</li>
     *   <li>{@link #CONTAINS}：候选 normalized 形式包含输入 normalized 形式。</li>
     * </ul>
     */
    public enum MatchMode {
        /** 前缀匹配。 */
        PREFIX,
        /** 包含匹配。 */
        CONTAINS
    }

    /**
     * Autocomplete primitive 输入契约 —— 只包含行为所需数据与 listbox chrome 回调。
     *
     * @param value       当前文本（受控源，透传 TextInputPrimitive，R9），控件绝不修改
     * @param enabled     是否启用
     * @param readOnly    是否只读（true 时仍可聚焦/移动 caret/弹浮层，但 TEXT_INPUT 被 primitive 阻断）
     * @param placeholder 占位文本
     * @param maxLength   最大长度（按码点数）
     * @param candidates  构建期固定候选文本，构造期防御性复制为不可变列表（R2 常量）
     * @param matchMode   匹配模式，默认 {@link MatchMode#PREFIX}
     * @param maxVisible  浮层最多候选数，默认 {@value #DEFAULT_MAX_VISIBLE}
     * @param onChange    文本变更上抛回调（透传 primitive）
     * @param onSelect    选中候选上抛回调；缺省 = onChange
     * @param chrome      listbox 与 item 装饰回调，必须同步挂载 overlay 内绑定
     */
    @Desugar
    public record Props(
            ReadableSignal<String> value,
            ReadableSignal<Boolean> enabled,
            ReadableSignal<Boolean> readOnly,
            String placeholder,
            int maxLength,
            List<String> candidates,
            MatchMode matchMode,
            int maxVisible,
            Consumer<String> onChange,
            Consumer<String> onSelect,
            ListboxChrome chrome
    ) {
        /**
         * 兼容构造：matchMode=PREFIX、maxVisible=默认、onSelect=onChange、chrome=NOOP。
         *
         * @param value       受控文本源
         * @param enabled     启用信号
         * @param readOnly    只读信号
         * @param placeholder 占位文本
         * @param maxLength   最大长度
         * @param candidates  候选列表
         * @param onChange    文本变更回调
         */
        public Props(ReadableSignal<String> value,
                     ReadableSignal<Boolean> enabled,
                     ReadableSignal<Boolean> readOnly,
                     String placeholder,
                     int maxLength,
                     List<String> candidates,
                     Consumer<String> onChange) {
            this(value, enabled, readOnly, placeholder, maxLength, candidates,
                    MatchMode.PREFIX, DEFAULT_MAX_VISIBLE, onChange, onChange, NOOP_CHROME);
        }

        /**
         * 兼容构造：指定 matchMode/maxVisible，onSelect=onChange，chrome=NOOP。
         *
         * @param value       受控文本源
         * @param enabled     启用信号
         * @param readOnly    只读信号
         * @param placeholder 占位文本
         * @param maxLength   最大长度
         * @param candidates  候选列表
         * @param matchMode   匹配模式
         * @param maxVisible  浮层最多候选数
         * @param onChange    文本变更回调（同时作为 onSelect）
         */
        public Props(ReadableSignal<String> value,
                     ReadableSignal<Boolean> enabled,
                     ReadableSignal<Boolean> readOnly,
                     String placeholder,
                     int maxLength,
                     List<String> candidates,
                     MatchMode matchMode,
                     int maxVisible,
                     Consumer<String> onChange) {
            this(value, enabled, readOnly, placeholder, maxLength, candidates,
                    matchMode, maxVisible, onChange, onChange, NOOP_CHROME);
        }

        /**
         * 兼容构造：指定 matchMode/maxVisible/onSelect，chrome=NOOP。
         *
         * @param value       受控文本源
         * @param enabled     启用信号
         * @param readOnly    只读信号
         * @param placeholder 占位文本
         * @param maxLength   最大长度
         * @param candidates  候选列表
         * @param matchMode   匹配模式
         * @param maxVisible  浮层最多候选数
         * @param onChange    文本变更回调
         * @param onSelect    选中候选回调（可与 onChange 不同）
         */
        public Props(ReadableSignal<String> value,
                     ReadableSignal<Boolean> enabled,
                     ReadableSignal<Boolean> readOnly,
                     String placeholder,
                     int maxLength,
                     List<String> candidates,
                     MatchMode matchMode,
                     int maxVisible,
                     Consumer<String> onChange,
                     Consumer<String> onSelect) {
            this(value, enabled, readOnly, placeholder, maxLength, candidates,
                    matchMode, maxVisible, onChange, onSelect, NOOP_CHROME);
        }

        /**
         * 全参紧凑构造：null 校验 + candidates 防御 copy + 缺省补全。
         *
         * @param value       受控文本源
         * @param enabled     启用信号
         * @param readOnly    只读信号
         * @param placeholder 占位文本
         * @param maxLength   最大长度
         * @param candidates  候选列表
         * @param matchMode   匹配模式
         * @param maxVisible  浮层最多候选数
         * @param onChange    文本变更回调
         * @param onSelect    选中回调（null 时回退 onChange）
         * @param chrome      listbox chrome（null 时回退 NOOP）
         */
        public Props(ReadableSignal<String> value,
                     ReadableSignal<Boolean> enabled,
                     ReadableSignal<Boolean> readOnly,
                     String placeholder,
                     int maxLength,
                     List<String> candidates,
                     MatchMode matchMode,
                     int maxVisible,
                     Consumer<String> onChange,
                     Consumer<String> onSelect,
                     ListboxChrome chrome) {
            this.value = Objects.requireNonNull(value, "value");
            this.enabled = Objects.requireNonNull(enabled, "enabled");
            this.readOnly = Objects.requireNonNull(readOnly, "readOnly");
            this.placeholder = placeholder == null ? "" : placeholder;
            this.maxLength = maxLength;
            this.candidates = Collections.unmodifiableList(
                    new ArrayList<>(Objects.requireNonNull(candidates, "candidates")));
            this.matchMode = matchMode == null ? MatchMode.PREFIX : matchMode;
            this.maxVisible = maxVisible <= 0 ? DEFAULT_MAX_VISIBLE : maxVisible;
            this.onChange = Objects.requireNonNull(onChange, "onChange");
            // onSelect 缺省 = onChange：选中即提交文本，与手打一致
            this.onSelect = onSelect != null ? onSelect : onChange;
            this.chrome = chrome != null ? chrome : NOOP_CHROME;
        }
    }

    /**
     * listbox chrome 装配回调，必须在 primitive 调用栈内同步执行（与 {@link SceneSelectPrimitive.ListboxChrome}
     * 同构，但 item handle 不含 selected —— autocomplete 浮层项无持久选中态，只有键盘高亮）。
     */
    public interface ListboxChrome {
        /**
         * 装饰 listbox 容器。
         *
         * @param listbox listbox overlay 根节点
         */
        void decorateListbox(SceneNode listbox);

        /**
         * 装饰单个 item。
         *
         * @param item item 句柄，包含结构节点与派生状态
         */
        void decorateItem(ItemHandle item);
    }

    /**
     * item 装饰句柄，暴露 item 结构节点与样式所需响应式状态。
     *
     * @param item        listbox 直接子节点
     * @param label       item 直接子文本节点
     * @param candidate   该 item 绑定的候选字符串（keyed diff 期间恒定）
     * @param highlighted 当前 item 是否为键盘高亮项（相对 filtered 列表）
     * @param interaction item 交互状态，供 wrapper 复用 hover 等 signal
     */
    @Desugar
    public record ItemHandle(
            SceneNode item,
            SceneNode label,
            String candidate,
            ReadableSignal<Boolean> highlighted,
            SceneInteractionState interaction
    ) {
    }

    /**
     * Autocomplete primitive 创建结果，暴露无样式结构节点与派生行为状态。
     *
     * @param root             根节点（= textInput.root，portal anchor + focus target）
     * @param textInput        内嵌 TextInput primitive 结果（透传）
     * @param expanded         当前是否展开浮层（独立 Signal 暴露为只读视图，R13）
     * @param filtered         当前过滤后候选列表（派生只读）
     * @param highlightedIndex 当前键盘高亮下标（相对 filtered）
     */
    @Desugar
    public record Result(
            SceneNode root,
            SceneTextInputPrimitive.Result textInput,
            ReadableSignal<Boolean> expanded,
            ReadableSignal<List<String>> filtered,
            ReadableSignal<Integer> highlightedIndex
    ) {
    }

    /**
     * 创建无样式 Autocomplete primitive。
     *
     * <p>组合 {@link SceneTextInputPrimitive} 拿输入行为 + Result，autocomplete 在其之上叠加候选浮层。
     * 不包装 {@link SceneTextInput} 成品（成品含 chrome，primitive 层要无样式）。</p>
     *
     * @param rt    场景运行时
     * @param props primitive 输入契约
     * @return 创建结果，供 wrapper 或高级控件挂载样式
     */
    public static Result create(SceneRuntime rt, Props props) {
        // 1) 组合 TextInputPrimitive：拿输入行为 + root + focused signal（primitive.create 已声明 focused）
        SceneTextInputPrimitive.Props textProps = new SceneTextInputPrimitive.Props(
                props.value(),
                props.enabled(),
                props.readOnly(),
                props.placeholder(),
                props.maxLength(),
                SceneInputType.TEXT,
                props.onChange());
        SceneTextInputPrimitive.Result textInput = SceneTextInputPrimitive.create(rt, textProps);
        SceneNode root = textInput.root();

        // 复用 primitive 已声明的交互态容器（R5：focus 读 interactionState(root).focused()）
        SceneInteractionState is = rt.interactionState(root);

        // 2) 本地 UI 态 signal（R13：expanded 独立可写，禁派生自 focused）
        //    expanded：浮层显隐，独立 Signal.create(FALSE)；由下方 focused effect 命令式驱动，
        //              ESC/ENTER/item CLICK/dismissRequest 等显式关闭意图直接 set(FALSE)。
        //    highlightedIndex：键盘高亮下标（相对 filtered），filtered 收缩时由 Computed 派生钳位读
        final Signal<Boolean> expanded = Signal.create(Boolean.FALSE);
        final Signal<Integer> highlightedIndex = Signal.create(Integer.valueOf(0));

        // 3) 派生链（全 Computed，声明式）
        //    filtered：L2 纯静态方法过滤，依赖 value + candidates（candidates 是常量，但 value 变化重算）
        final Computed<List<String>> filtered = Computed.create(() ->
                filterCandidates(props.value().get(), props.candidates(), props.matchMode(), props.maxVisible()));

        //    expanded 驱动 effect（R13 核心）：监听 focused/enabled/filtered/value，命令式 set expanded。
        //    替代了原 Computed(focused && ...) 派生——语义等价（条件相同），但从"派生"变为"effect 内命令式 set"，
        //    使 expanded 成为独立可写 Signal（与 SceneSelectPrimitive 对齐），不再被 DOWN 隐式失焦跨帧掐断。
        //    I1/I11：effect 内 set signal 必须包 Effect.untrack（参考 SceneRuntime.portalAnchored L434-437 模式），
        //    否则 set 触发的下游订阅会反向触发本 effect 重订阅形成环。
        //    （R9：disabled 不弹浮层。focusable() effect 在 enabled=false 时会注销焦点环并清焦点，
        //     但 requestFocus 可绕过焦点环直写 focused signal，故此处显式带 enabled 守卫）
        Effect.create(() -> {
            boolean focused = Boolean.TRUE.equals(is.focused().get());
            boolean enabled = Boolean.TRUE.equals(props.enabled().get());
            List<String> f = filtered.get();
            String value = props.value().get();
            boolean shouldExpand = focused && enabled
                    && !f.isEmpty()
                    && !isExactSingleMatch(value, f);
            final boolean next = shouldExpand;
            // effect 内 set 经队列进入 pendingWrites；ReactiveScheduler.flush 已改为双通道（drain-writes
            // 与 run-effects）交替到不动点，effect 内 set 在紧接的 drain 轮内即被应用、订阅者被 markDirty、
            // 下游 portalAnchored effect 在同一 flush 内重跑——无需绕过调度器的同步写入（守 I2）。
            Effect.untrack(() -> expanded.set(Boolean.valueOf(next)));
        });

        //    highlightedIndex 派生读：filtered 收缩后越界钳位（写仍落原 signal，避免 effect 回环）
        final ReadableSignal<Integer> highlightedNormalized = Computed.create(() -> {
            int size = filtered.get().size();
            return Integer.valueOf(clamp(highlightedIndex.get().intValue(), 0, Math.max(0, size - 1)));
        });

        // onSelectResolved：透传到 listbox item 与键盘 ENTER
        final Consumer<String> onSelectResolved = props.onSelect();

        // 4) 键盘交互（oracle §5，键集正交 F1/F2）：在 root 追加 KEY_DOWN handler
        //    与 primitive 的 KEY_DOWN handler 同节点共存；primitive 处理 ARROW_LEFT/RIGHT/HOME/END/BACKSPACE/DELETE，
        //    autocomplete 只在 expanded 时处理 ARROW_DOWN/ARROW_UP/ENTER/ESCAPE（键集不重叠）。
        //    R13：expanded 关闭意图（ENTER/ESC）直接 expanded.set(FALSE)，不再经 suppressed 中转。
        rt.on(root, SceneEventType.KEY_DOWN, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled().get())
                    || ev.getKeyAction() != SceneKeyAction.PRESSED) {
                return;
            }
            if (!Boolean.TRUE.equals(expanded.get())) {
                return;
            }
            SceneKey key = ev.getKey();
            List<String> list = filtered.get();
            int size = list.size();
            int hi = clamp(highlightedIndex.get().intValue(), 0, Math.max(0, size - 1));
            if (key == SceneKey.ARROW_DOWN) {
                highlightedIndex.set(Integer.valueOf(clamp(hi + 1, 0, Math.max(0, size - 1))));
                ctx.stopPropagation();
            } else if (key == SceneKey.ARROW_UP) {
                highlightedIndex.set(Integer.valueOf(clamp(hi - 1, 0, Math.max(0, size - 1))));
                ctx.stopPropagation();
            } else if (key == SceneKey.ENTER) {
                if (size > 0 && hi >= 0 && hi < size) {
                    onSelectResolved.accept(list.get(hi));
                }
                expanded.set(Boolean.FALSE);
                ctx.stopPropagation();
            } else if (key == SceneKey.ESCAPE) {
                expanded.set(Boolean.FALSE);
                ctx.stopPropagation();
            }
        });

        // 5) portal 挂载（R11 核心）：expanded 独立 Signal 驱动挂卸，dismissRequest 直接 expanded.set(FALSE)（I1/I11）
        AnchorProvider anchor = AnchorProvider.forNode(root);
        rt.portalAnchored(
                expanded,
                () -> buildListbox(rt, props, filtered, highlightedNormalized, expanded, onSelectResolved),
                OverlayDismissPolicy.DEFAULT,
                () -> expanded.set(Boolean.FALSE),
                anchor);

        return new Result(root, textInput, expanded, filtered, highlightedNormalized);
    }

    /**
     * 构建 listbox overlay root（每次 expanded: false→true 时调一次）。
     *
     * <p>候选用 {@link SceneRuntime#forEach} keyed diff（keyFn = 候选字符串本身，候选唯一），
     * filtered 动态变化时按 key 复用/增删 item 节点（I5）。item CLICK 走 onSelectResolved.accept(candidate)
     * + expanded.set(FALSE) + stopPropagation（R13：显式关闭意图直接写独立 Signal）。</p>
     *
     * @param rt                  场景运行时
     * @param props               primitive 输入契约
     * @param filtered            过滤后候选 signal（动态）
     * @param highlightedNormalized 钳位后的高亮下标只读 signal
     * @param expanded            浮层显隐独立 signal（item 选中后置 false 关浮层）
     * @param onSelectResolved    选中回调
     * @return listbox 根节点
     */
    private static SceneNode buildListbox(SceneRuntime rt, Props props,
                                          ReadableSignal<List<String>> filtered,
                                          ReadableSignal<Integer> highlightedNormalized,
                                          Signal<Boolean> expanded,
                                          Consumer<String> onSelectResolved) {
        SceneNode listbox = SceneNode.column();
        listbox.setWidthSizing(WidthSizing.SHRINK);
        listbox.setScrollable(true);
        listbox.setClipChildren(true);

        SceneScrolls.attach(rt, listbox);
        props.chrome().decorateListbox(listbox);

        // I5 keyed diff：filtered 动态变化必须 keyed（不可照抄 SceneSelectPrimitive 静态 for 循环）
        // keyFn = 候选字符串本身（候选在 candidates 内唯一；filtered 是 candidates 子序列，亦唯一）
        rt.forEach(listbox, filtered, Function.identity(), candidate -> {
            SceneNode item = SceneNode.row();

            SceneNode itemLabel = new SceneNode();
            itemLabel.setHitTestable(false); // 装饰节点命中穿透（R6）
            itemLabel.setText(candidate);
            item.appendChild(itemLabel);

            SceneInteractionState itemState = rt.interactionState(item);
            // highlighted 派生：item 候选等于 filtered[highlightedNormalized] 时为 true
            ReadableSignal<Boolean> highlightedSig = Computed.create(() -> {
                int hi = highlightedNormalized.get().intValue();
                List<String> f = filtered.get();
                return Boolean.valueOf(hi >= 0 && hi < f.size() && candidate.equals(f.get(hi)));
            });
            ItemHandle handle = new ItemHandle(item, itemLabel, candidate, highlightedSig, itemState);
            props.chrome().decorateItem(handle);

            // item CLICK：上抛候选 + 关浮层（复刻 SceneSelectPrimitive :250-254，R13 直接写独立 Signal）
            rt.on(item, SceneEventType.CLICK, (ev, ctx) -> {
                onSelectResolved.accept(candidate);
                expanded.set(Boolean.FALSE);
                ctx.stopPropagation();
            });
            return item;
        });
        return listbox;
    }

    // ==================== L2 纯数学（可脱 runtime 测） ====================

    /**
     * 过滤候选列表（L2 纯数学，无 runtime/input/reactive 依赖）。
     *
     * <p>归一化规则：{@code trim().toLowerCase(Locale.ENGLISH)}（与 {@code FontMatcher.normalizeFontName}
     * 真正同源，FontMatcher.java:269 即用 Locale.ENGLISH；避免土耳其 i 等 locale 陷阱）。空输入返回空列表，
     * limit 截断最多候选数。</p>
     *
     * @param input      当前输入文本（可为 null）
     * @param candidates 候选列表（不可变，调用方保证）
     * @param mode       匹配模式
     * @param limit      最多返回候选数（&le;0 视为 0）
     * @return 不可变过滤结果列表（保持候选原顺序，保留原始大小写）
     */
    static List<String> filterCandidates(String input, List<String> candidates,
                                         MatchMode mode, int limit) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }
        String norm = normalize(input);
        if (norm.isEmpty()) {
            return Collections.emptyList();
        }
        int cap = limit <= 0 ? 0 : limit;
        if (cap == 0) {
            return Collections.emptyList();
        }
        MatchMode m = mode == null ? MatchMode.PREFIX : mode;
        List<String> out = new ArrayList<>(Math.min(candidates.size(), cap));
        for (String c : candidates) {
            if (c == null) {
                continue;
            }
            String cn = normalize(c);
            boolean hit = (m == MatchMode.CONTAINS) ? cn.contains(norm) : cn.startsWith(norm);
            if (hit) {
                out.add(c);
                if (out.size() >= cap) {
                    break;
                }
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * 归一化字符串：{@code trim().toLowerCase(Locale.ENGLISH)}，null 安全返回空串。
     *
     * <p>Locale 选择：与 {@code FontMatcher.normalizeFontName}（FontMatcher.java:269）真同源。
     * 字体名通常为 ASCII，Locale.ENGLISH 与 Locale.ROOT 在 ASCII 范围行为一致；
     * 显式 Locale.ENGLISH 锁定"用户在 fontSort 配的字体名能直接喂给 FontMatcher"承诺（explorer 事实）。</p>
     *
     * @param value 输入值（可为 null）
     * @return 归一化形式
     */
    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ENGLISH);
    }

    /**
     * 判断是否「精确单命中」：filtered 仅一项且与 input 归一化相等。
     *
     * <p>此时无需再弹浮层（用户已打出唯一候选的归一化形式）。</p>
     *
     * @param input   当前输入文本（可为 null）
     * @param filtered 过滤结果列表
     * @return true 表示应抑制浮层
     */
    static boolean isExactSingleMatch(String input, List<String> filtered) {
        if (filtered == null || filtered.size() != 1) {
            return false;
        }
        return normalize(input).equals(normalize(filtered.get(0)));
    }

    /**
     * 将值裁剪到闭区间（max&lt;min 时返回 min，与 SceneSelectPrimitive.clamp 同语义）。
     *
     * @param value 输入值
     * @param min   最小值
     * @param max   最大值
     * @return 裁剪后的值
     */
    static int clamp(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
