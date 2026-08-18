package club.heiqi.uilib.internal.devtools.playground.pages;

import java.util.function.Supplier;

import club.heiqi.uilib.internal.devtools.playground.PlaygroundKit;
import club.heiqi.uilib.internal.devtools.playground.PlaygroundPage;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneInputType;
import club.heiqi.uilib.ui.scene.control.SceneTextInput;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 单行文本输入演示页。
 *
 * <p>覆盖：受控文本契约（外部 signal 唯一持有真值 + onChange 上抛回写）、placeholder、
 * maxLength 限长、只读态、密码/数字输入类型、输入实时回读与码点统计。
 * 框选/剪贴板/词跳/Undo/Redo 等快捷键能力在真机内直接体验（见 Home 快捷键速查）。</p>
 */
public final class TextInputPage implements PlaygroundPage {

    /** 受控值：普通输入框。 */
    private final Signal<String> name = Signal.create("Hello Qz UILib");
    /** 受控值：密码输入框（显示掩码，回调仍上抛真实值）。 */
    private final Signal<String> secret = Signal.create("p@ssw0rd");
    /** 受控值：数字输入框（仅放行数字字符集）。 */
    private final Signal<String> number = Signal.create("3.14159");
    /** 受控值：限长输入框（8 码点）。 */
    private final Signal<String> limited = Signal.create("12345678");
    /** 只读开关（切换编辑/只读演示）。 */
    private final Signal<Boolean> readOnly = Signal.create(Boolean.FALSE);
    /** 受控值：只读演示输入框（独立 signal，与受控输入互不串扰）。 */
    private final Signal<String> readOnlyDemo = Signal.create("只读内容");

    @Override
    public String id() {
        return "text-input";
    }

    @Override
    public String title() {
        return "单行文本";
    }

    @Override
    public String description() {
        return "SceneTextInput：受控契约、placeholder、限长、只读、密码/数字、实时回读";
    }

    @Override
    public Supplier<SceneNode> build(final SceneRuntime rt) {
        return () -> {
            SceneNode root = SceneNode.column();
            root.setFillParentWidth(true);
            root.setGap(10);

            // ===== 卡片1：受控输入 + 实时回读 =====
            SceneNode controlledCard = PlaygroundKit.card();
            controlledCard.appendChild(PlaygroundKit.title("受控输入（文本真值由外部 signal 唯一持有）"));
            mountInput(rt, controlledCard, name, null, Integer.MAX_VALUE, SceneInputType.TEXT, "在此输入…", true);
            controlledCard.appendChild(PlaygroundKit.hint("拖选 / Shift+方向键扩展选区；Ctrl+Z/Y 撤销重做；Ctrl+C/X/V 剪贴板；Ctrl+←/→ 词跳转。"));
            SceneNode readout = PlaygroundKit.text("", PlaygroundKit.MUTED, 12);
            controlledCard.appendChild(readout);
            rt.bind(Computed.create(() -> formatReadout("当前文本", name.get())), readout::setText);

            // ===== 卡片2：输入类型与只读态 =====
            SceneNode typeCard = PlaygroundKit.card();
            typeCard.appendChild(PlaygroundKit.title("输入类型与只读态"));

            typeCard.appendChild(PlaygroundKit.hint("只读输入框（可聚焦/选中/复制，禁止编辑与撤销；可点击下方按钮切换）："));
            SceneNode readOnlyInput = mountInput(rt, typeCard, readOnlyDemo, readOnly, Integer.MAX_VALUE,
                    SceneInputType.TEXT, "只读内容", true);
            rt.bind(Computed.create(() -> Boolean.TRUE.equals(readOnly.get())),
                    v -> readOnlyInput.setOpacity(v ? 0.85f : 1.0f));

            typeCard.appendChild(PlaygroundKit.hint("密码输入（显示 ••• 掩码，回调上抛真实值）："));
            mountInput(rt, typeCard, secret, null, Integer.MAX_VALUE, SceneInputType.PASSWORD, "密码", false);
            SceneNode secretReadout = PlaygroundKit.text("", PlaygroundKit.MUTED, 12);
            typeCard.appendChild(secretReadout);
            rt.bind(Computed.create(() -> "真实值：" + secret.get() + "（仅演示掩码，不经安全用途）"), secretReadout::setText);

            typeCard.appendChild(PlaygroundKit.hint("数字输入（仅放行 0-9 . - + e E 字符集）："));
            mountInput(rt, typeCard, number, null, Integer.MAX_VALUE, SceneInputType.NUMBER, "数字", false);
            SceneNode numberReadout = PlaygroundKit.text("", PlaygroundKit.MUTED, 12);
            typeCard.appendChild(numberReadout);
            rt.bind(Computed.create(() -> "当前值：" + number.get()), numberReadout::setText);

            typeCard.appendChild(PlaygroundKit.hint("限长输入（maxLength=8，填满后拒绝新增）："));
            mountInput(rt, typeCard, limited, null, 8, SceneInputType.TEXT, "限 8 字", false);

            // ===== 卡片3：快捷操作 =====
            SceneNode opsCard = PlaygroundKit.card();
            opsCard.appendChild(PlaygroundKit.title("快捷操作"));
            SceneNode opsRow = PlaygroundKit.row(8);
            PlaygroundKit.button(rt, opsRow, "填充示例", () -> name.set("Hello, Qz UILib! 1234567890"));
            PlaygroundKit.button(rt, opsRow, "清空", () -> name.set(""));
            PlaygroundKit.button(rt, opsRow, "切换只读", () -> readOnly.set(Boolean.valueOf(!readOnly.get().booleanValue())));
            opsCard.appendChild(opsRow);

            root.appendChild(controlledCard);
            root.appendChild(typeCard);
            root.appendChild(opsCard);
            return root;
        };
    }

    /**
     * 挂载一个受控 TextInput 组件到父节点。
     *
     * <p>受控回写：控件以只读视角消费 {@code value}，变更经 onChange 上抛后由页面写回
     * 同一个 Signal（受控契约标准回路）。readOnly 传非 null 时绑定到该只读开关。</p>
     *
     * @param rt          场景运行时
     * @param parent      挂载父节点
     * @param value       受控值（可写 Signal，页面持有）
     * @param readOnlyIn  只读开关；null 表示恒可编辑
     * @param maxLength   最大码点数
     * @param inputType   输入类型
     * @param placeholder 占位文本
     * @param bindReadOnly 是否把 readOnlyIn 绑定为输入框只读源（false 时仅作页面侧标记）
     * @return 输入框根节点
     */
    private SceneNode mountInput(SceneRuntime rt, SceneNode parent, final Signal<String> value,
                                 Signal<Boolean> readOnlyIn, int maxLength, SceneInputType inputType,
                                 String placeholder, boolean bindReadOnly) {
        SceneTextInput.Props.Builder builder = SceneTextInput.Props.builder(value)
                .placeholder(placeholder)
                .maxLength(maxLength)
                .inputType(inputType)
                .onChange(next -> value.set(next == null ? "" : next));
        if (bindReadOnly && readOnlyIn != null) {
            builder = builder.readOnly(readOnlyIn);
        }
        SceneNode inputRoot = rt.mount(parent, SceneTextInput.create(rt, builder.build())).getRoot();
        if (bindReadOnly && readOnlyIn != null && inputRoot != null) {
            // 只读态下弱化输入框不透明度，便于肉眼确认切换生效。
            rt.bind(readOnlyIn, v -> inputRoot.setOpacity(Boolean.TRUE.equals(v) ? 0.85f : 1.0f));
        }
        return inputRoot;
    }

    private static String formatReadout(String label, String value) {
        String safe = value == null ? "" : value;
        int codepoints = safe.codePointCount(0, safe.length());
        return label + "：" + safe + "　长度：" + codepoints + " 码点";
    }
}