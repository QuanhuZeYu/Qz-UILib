package club.heiqi.uilib.ui.scene.control;

import java.util.function.Consumer;
import java.util.function.Supplier;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneEvent;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;
import club.heiqi.uilib.ui.scene.node.Invalidation;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * SceneTextInput —— scene 新栈控件层 Phase 4 批 3 第二个迁移控件（受控文本输入框，档位 A）。
 *
 * <h3>定位：受控文本输入控件（契约 R9 确立者）</h3>
 * <p>本控件确立「受控文本输入零状态」契约 R9（R7 从布尔/连续值到 String 的推广）：当前文本由外部
 * {@code value} 只读 signal 唯一驱动，控件<b>零内部受控状态</b>；输入时只经
 * {@code onChange.accept(期望新值真实 String)} 上抛，控件<b>绝不自缓存或自改 value</b>
 * （守 R1/R5/I11/R9）。</p>
 *
 * <h3>档位 A 范围（已锁定）</h3>
 * <p>caret 恒在文本末尾、无选区、无方向键、无字符级定位、无 IME、无剪贴板、无闪烁。
 * <b>关键洞察：caret 位置不是状态，是 {@code f(value 长度)}，由 ROW 布局自然把 caret 排到
 * 文本末尾右侧决定，零本地 signal、零度量</b>（比 SceneSlider 还简单，无 draggingValue）。
 * caret 只需可见性 = 聚焦态，读 {@code interactionState(root).focused()}。</p>
 *
 * <h3>范式升级：声明式 caret</h3>
 * <p>旧栈用命令式 {@code DocumentCustomRenderer.fillRect} 画 caret 竖线，新栈改为
 * <b>声明式 caret 子节点 + signal 驱动可见性</b>（focused 时切 {@link #CARET_COLOR}、失焦切透明），
 * 行为等价旧栈但范式纯净（守 I1/I11，绝不命令式画）。</p>
 *
 * <h3>结构</h3>
 * <pre>
 * root (交互单元 hitTestable=true, focusable, ROW, cross=CENTER, gap=0,
 *       clipChildren=true 对齐旧栈 overflow:hidden, 边框/背景 bind enabled+focused, padding)
 *   ├─ textNode (叶, text bind displayText, shrink-to-fit 文本宽, 装饰穿透)
 *   └─ caret    (叶, preferredWidth=1, preferredHeight=行高, bg bind focused 切色, 装饰穿透)
 * </pre>
 * <p><b>两个落地铁律</b>：① caret 必须自设 {@code preferredHeight}（≈fontSize 行高），
 * 否则无文本叶高=0 不可见；② root 必须 {@code gap=0}，否则 caret 与文本间多 1px gap。
 * caret 在 textNode 之后，ROW 逐子定位 {@code cursor+=childMain} 自然把 caret 排到文本末尾右侧。</p>
 *
 * <h3>契约</h3>
 * <p>R1 纯静态工厂零实例字段 / R2 Props 只读 signal + 不可变常量 + 回调 / R3 组件函数只执行一次 /
 * R4 外观随状态经 bind 派生 / R5 交互态读 interactionState / R6 装饰子节点命中穿透 /
 * R9 受控文本输入零内部状态。</p>
 */
public final class SceneTextInput {

    // ==================== 配色（grounded 常量，深色系输入框） ====================

    /** enabled 背景（深石板灰） */
    private static final int BG_ENABLED = 0xFF1E293B;
    /** disabled 背景（更暗灰） */
    private static final int BG_DISABLED = 0xFF111827;

    /** 默认边框色（中灰） */
    private static final int BORDER_ENABLED = 0xFF475569;
    /** focused 边框色（亮蓝，聚焦高亮） */
    private static final int BORDER_FOCUSED = 0xFF4A90D9;
    /** disabled 边框色（暗灰） */
    private static final int BORDER_DISABLED = 0xFF334155;

    /** enabled 真实文本色（近白） */
    private static final int TEXT_ENABLED = 0xFFE2E8F0;
    /** disabled 文本色（暗灰） */
    private static final int TEXT_DISABLED = 0xFF64748B;
    /** placeholder 占位文本色（灰，区别真实文本） */
    private static final int TEXT_PLACEHOLDER = 0xFF64748B;

    /** caret 可见时颜色（近白竖线） */
    private static final int CARET_COLOR = 0xFFE2E8F0;
    /** caret 不可见（全透明，纯 PAINT 切换不重排） */
    private static final int CARET_TRANSPARENT = 0x00000000;

    /** 边框宽度（像素） */
    private static final int BORDER_WIDTH = 1;
    /** 圆角半径（像素，小圆角） */
    private static final int CORNER_RADIUS = 4;
    /** 内边距（像素） */
    private static final int PADDING = 6;
    /** root 行内间距：必须 0，否则 caret 与文本间多 1px gap（落地铁律②） */
    private static final int GAP = 0;
    /** caret 宽度（像素，1px 竖线） */
    private static final int CARET_WIDTH = 1;
    /** caret 高度（像素，≈fontSize 行高；无文本叶高=0 不可见，落地铁律①） */
    private static final int CARET_HEIGHT = 16;

    /** 密码掩码字符（圆点 U+2022） */
    private static final char MASK_CHAR = '\u2022';

    /** 纯静态工厂，禁止实例化（强制无状态，契约 R1） */
    private SceneTextInput() {
    }

    /**
     * TextInput 输入契约 —— 受控文本：当前文本由外部只读 signal 驱动，
     * 输入经 onChange 交还期望新值真实 String（契约 R2/R9）。
     *
     * @param value       当前文本（响应式只读，受控源），控件绝不自己缓存/修改此值
     * @param enabled     是否启用（响应式只读），false 时不可聚焦 + 阻断所有输入 + 灰态
     * @param readOnly    是否只读（响应式只读），true 时可聚焦可见但阻断文本写入（区别于 disabled）
     * @param placeholder 占位文本（不可变常量），value 空串且本串非空时以占位色显示
     * @param maxLength   最大长度（不可变常量，按码点数），填满后拒绝新增（不截断已有）
     * @param inputType   输入类型（不可变常量枚举），控制字符过滤与密码掩码显示
     * @param onChange    文本变更回调，内容真实变化时以期望新值（真实 String）调用，由外部 set 回 value
     */
    @Desugar
    public record Props(
            ReadableSignal<String> value,
            ReadableSignal<Boolean> enabled,
            ReadableSignal<Boolean> readOnly,
            String placeholder,
            int maxLength,
            SceneInputType inputType,
            Consumer<String> onChange
    ) {
    }

    /**
     * 工厂：构建 TextInput 组件函数。
     *
     * <p>返回的 {@code Supplier} 体由 {@link SceneRuntime#mount} 执行一次（R3）：
     * 建树 + 设静态属性 + {@code rt.bind/bindText/on/focusable}。动态外观全落
     * {@code bind(computed(...))}，文本/退格交互只经 {@code onChange} 上抛期望新值真实 String，
     * 绝不在 handler 里 setXxx（R4/R5/R9）。caret 位置靠 ROW 布局自然定位，零本地 signal、零度量。</p>
     *
     * @param rt    场景运行时
     * @param props TextInput 输入契约
     * @return 组件函数，交 {@code rt.mount(parent, ...)} 挂载
     */
    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        return () -> {
            // 不可变常量 Props 捕获为 final 局部，供闭包与纯函数用（R2）
            final String placeholder = props.placeholder();
            final int maxLength = props.maxLength();
            final SceneInputType inputType = props.inputType();

            // ① 建树一次（无副作用，I3）—— 纯结构 + 静态样式
            SceneNode root = new SceneNode();
            root.setFlexDirection(FlexDirection.ROW);
            root.setCrossAxisAlign(CrossAxisAlign.CENTER);
            root.setMainAxisAlign(MainAxisAlign.START);
            root.setGap(GAP);                    // 落地铁律②：必须 0，否则 caret 与文本多 1px gap
            root.setPadding(PADDING);
            root.setBorderWidth(BORDER_WIDTH);
            root.setCornerRadius(CORNER_RADIUS);
            root.setClipChildren(true);          // 对齐旧栈 overflow:hidden（SceneButton:104 先例）

            // textNode：文本叶，shrink-to-fit 到文本宽（文本叶天然 shrink，不被拉伸），装饰穿透（R6）
            SceneNode textNode = new SceneNode();
            textNode.setHitTestable(false);
            root.appendChild(textNode);

            // caret：1px 竖线，自设高度（落地铁律①），紧随 textNode 靠 ROW 自然排到末尾右侧，装饰穿透（R6）
            SceneNode caret = new SceneNode();
            caret.setPreferredWidth(CARET_WIDTH);
            caret.setPreferredHeight(CARET_HEIGHT);
            caret.setHitTestable(false);
            root.appendChild(caret);

            // ② 交互态：读 Router 权威 signal（挂在交互单元 root 上），绝不自维护 boolean（契约 R5）
            SceneInteractionState is = rt.interactionState(root);

            // ③ 动态外观全走 bind（契约 R4）
            //    textNode 文本内容：displayText = f(value, placeholder, inputType)（LAYOUT 级，影响文本宽）
            rt.bind(Invalidation.LAYOUT,
                    Computed.create(() -> displayText(props.value().get(), placeholder, inputType)),
                    textNode::setText);

            //    textNode 文本色：value 空且有 placeholder → 占位色；disabled → 灰；否则真实文本色（PAINT 级）
            rt.bind(Invalidation.PAINT,
                    Computed.create(() -> resolveTextColor(
                            props.value().get(), placeholder, props.enabled().get())),
                    textNode::setTextColor);

            //    背景：enabled/disabled（PAINT 级）
            rt.bind(Invalidation.PAINT, props.enabled(),
                    e -> root.setBackgroundColor(Boolean.TRUE.equals(e) ? BG_ENABLED : BG_DISABLED));

            //    边框色：disabled > focused > 默认（PAINT 级，focused 高亮聚焦态）
            rt.bind(Invalidation.PAINT,
                    Computed.create(() -> resolveBorderColor(
                            props.enabled().get(), is.focused().get())),
                    root::setBorderColor);

            //    caret 可见性：focused 且 enabled 时切 CARET_COLOR，否则透明（PAINT 级，照搬 Toggle 读 is 范式换 focused）
            rt.bind(Invalidation.PAINT,
                    Computed.create(() -> resolveCaretColor(
                            props.enabled().get(), is.focused().get())),
                    caret::setBackgroundColor);

            //    cursor：enabled 文本光标语义用 POINTER（scene 暂无 TEXT 光标枚举，统一 POINTER）、disabled 禁止符号
            rt.bind(Invalidation.PAINT, props.enabled(),
                    e -> root.setCursor(Boolean.TRUE.equals(e) ? SceneCursor.POINTER : SceneCursor.NOT_ALLOWED));

            // ④ 键盘可达：登记进 Tab 焦点环（focusable 是聚焦前提，caret 可见性靠 focused 态）
            rt.focusable(root);

            // ⑤ 文本输入：TEXT_INPUT 事件 route 到 focused 节点，handler 纯函数算 newString 上抛（R9，I11 只 onChange）
            rt.on(root, SceneEventType.TEXT_INPUT, (ev, ctx) -> {
                // disabled 不可聚焦本不会收到事件，仍早退兜底；readOnly 阻断写入
                if (!Boolean.TRUE.equals(props.enabled().get())
                        || Boolean.TRUE.equals(props.readOnly().get())) {
                    return;
                }
                String raw = ev.getText();
                if (raw == null || raw.isEmpty()) {
                    return;
                }
                String cur = nullSafe(props.value().get());
                String next = appendFiltered(cur, raw, maxLength, inputType);
                // 仅内容真实变化才上抛（全被过滤/填满拒绝时 next==cur，不触发）
                if (!next.equals(cur)) {
                    props.onChange().accept(next);
                }
            });

            // ⑥ 退格：KEY_DOWN 判 BACKSPACE，删末尾一个码点；空串无操作（不调 onChange）。readOnly/disabled 阻断
            rt.on(root, SceneEventType.KEY_DOWN, (ev, ctx) -> {
                if (!Boolean.TRUE.equals(props.enabled().get())
                        || Boolean.TRUE.equals(props.readOnly().get())) {
                    return;
                }
                if (ev.getKey() != SceneKey.BACKSPACE) {
                    return;
                }
                String cur = nullSafe(props.value().get());
                if (cur.isEmpty()) {
                    return; // 空串退格无操作，不调 onChange
                }
                // 删末尾一个码点（用 offsetByCodePoints 防代理对被砍半）
                int newEnd = cur.offsetByCodePoints(cur.length(), -1);
                props.onChange().accept(cur.substring(0, newEnd));
            });

            return root;
        };
    }

    // ==================== 纯函数辅助（无副作用，无实例状态） ====================

    /**
     * null 安全：null → 空串。
     *
     * @param s 可能为 null 的字符串
     * @return 非 null 字符串
     */
    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    /**
     * 计算显示文本（纯函数）：处理 placeholder 与密码掩码。
     *
     * <p>① value 空串且 placeholder 非空 → 返回 placeholder（占位显示，配占位色见 {@link #resolveTextColor}）；
     * ② PASSWORD 类型 → 按<b>码点数</b>替换为等量 {@link #MASK_CHAR}；
     * ③ 其它 → 原样返回真实值。</p>
     *
     * @param value       外部受控真实值（可能为 null）
     * @param placeholder 占位文本
     * @param inputType   输入类型
     * @return 用于 textNode 显示的字符串
     */
    private static String displayText(String value, String placeholder, SceneInputType inputType) {
        String v = nullSafe(value);
        if (v.isEmpty()) {
            // 空串显示 placeholder（placeholder 自身也可能空串，则显示空）
            return nullSafe(placeholder);
        }
        if (inputType == SceneInputType.PASSWORD) {
            // 按码点数掩码（代理对算一个码点 → 一个圆点），不影响回调真实值
            int cps = v.codePointCount(0, v.length());
            StringBuilder sb = new StringBuilder(cps);
            for (int i = 0; i < cps; i++) {
                sb.append(MASK_CHAR);
            }
            return sb.toString();
        }
        return v;
    }

    /**
     * 解析 textNode 文本色（纯函数）。
     *
     * <p>优先级：disabled → 灰；value 空且 placeholder 非空 → 占位色；否则真实文本色。</p>
     *
     * @param value       外部受控真实值（可能为 null）
     * @param placeholder 占位文本
     * @param enabled     是否启用
     * @return 文本色 ARGB
     */
    private static int resolveTextColor(String value, String placeholder, Boolean enabled) {
        if (!Boolean.TRUE.equals(enabled)) {
            return TEXT_DISABLED;
        }
        String v = nullSafe(value);
        if (v.isEmpty() && !nullSafe(placeholder).isEmpty()) {
            return TEXT_PLACEHOLDER;
        }
        return TEXT_ENABLED;
    }

    /**
     * 解析边框色（纯函数）。
     *
     * <p>优先级：disabled > focused > 默认。</p>
     *
     * @param enabled 是否启用
     * @param focused 是否聚焦
     * @return 边框色 ARGB
     */
    private static int resolveBorderColor(Boolean enabled, Boolean focused) {
        if (!Boolean.TRUE.equals(enabled)) {
            return BORDER_DISABLED;
        }
        if (Boolean.TRUE.equals(focused)) {
            return BORDER_FOCUSED;
        }
        return BORDER_ENABLED;
    }

    /**
     * 解析 caret 颜色（纯函数）：enabled 且 focused 时可见，否则透明。
     *
     * <p>档位 A 无闪烁：caret 可见性纯由聚焦态决定（focused→实色、失焦→透明），
     * disabled 时即便理论不可聚焦也兜底透明。</p>
     *
     * @param enabled 是否启用
     * @param focused 是否聚焦
     * @return caret 背景色 ARGB（可见=CARET_COLOR，不可见=透明）
     */
    private static int resolveCaretColor(Boolean enabled, Boolean focused) {
        if (Boolean.TRUE.equals(enabled) && Boolean.TRUE.equals(focused)) {
            return CARET_COLOR;
        }
        return CARET_TRANSPARENT;
    }

    /**
     * 字符输入纯函数：把输入串逐码点过滤后追加到当前值末尾，受 maxLength（码点数）约束。
     *
     * <p>过滤规则（行为等价旧栈）：所有类型先拒 {@code Character.isISOControl(cp) || cp=='\n'||'\r'||'\t'}；
     * TEXT/PASSWORD 过控制字符后全放行；NUMBER 额外只放行 {@code '0'-'9'} 及 {@code '.' '-' '+' 'e' 'E'}
     * （仅字符集过滤，不校验是否合法数字）。maxLength 逐码点判 {@code 当前码点数 < maxLength} 才追加，
     * <b>填满拒绝新增（不截断已有）</b>。追加恒在末尾。</p>
     *
     * @param current   当前真实值（非 null）
     * @param input     本次输入串（非 null 非空）
     * @param maxLength  最大码点数
     * @param inputType 输入类型
     * @return 追加过滤后的新值（全被拒时等于 current）
     */
    private static String appendFiltered(String current, String input, int maxLength,
                                         SceneInputType inputType) {
        int curCps = current.codePointCount(0, current.length());
        StringBuilder sb = new StringBuilder(current);
        int cps = curCps;
        int i = 0;
        while (i < input.length()) {
            int cp = input.codePointAt(i);
            int charCount = Character.charCount(cp);
            i += charCount;
            if (!isAccepted(cp, inputType)) {
                continue;
            }
            if (cps >= maxLength) {
                break; // 填满：拒绝后续新增（不截断已有）
            }
            sb.appendCodePoint(cp);
            cps++;
        }
        return sb.toString();
    }

    /**
     * 单码点是否被接受（纯函数）。
     *
     * @param cp        码点
     * @param inputType 输入类型
     * @return true 表示放行
     */
    private static boolean isAccepted(int cp, SceneInputType inputType) {
        // 所有类型先拒控制字符与换行/回车/制表
        if (Character.isISOControl(cp) || cp == '\n' || cp == '\r' || cp == '\t') {
            return false;
        }
        if (inputType == SceneInputType.NUMBER) {
            // NUMBER：仅放行数字相关字符集（不校验是否合法数字）
            if (cp >= '0' && cp <= '9') {
                return true;
            }
            return cp == '.' || cp == '-' || cp == '+' || cp == 'e' || cp == 'E';
        }
        // TEXT / PASSWORD：过控制字符后全放行
        return true;
    }
}
