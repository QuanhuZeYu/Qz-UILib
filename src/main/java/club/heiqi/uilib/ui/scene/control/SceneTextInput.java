package club.heiqi.uilib.ui.scene.control;

import java.util.function.Consumer;
import java.util.function.Supplier;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.node.Invalidation;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * SceneTextInput —— scene 新栈字符级单行受控文本输入框（B1 核心版）。
 *
 * <h3>B1 范围</h3>
 * <p>本版提供字符级 caret、点击定位、方向键/Home/End 移动，以及 TEXT_INPUT、Backspace、Delete
 * 编辑键。暂不提供选区、剪贴板、IME 组合态、caret 闪烁、动画与横向滚动。</p>
 *
 * <h3>受控契约</h3>
 * <p>文本真值仍由外部 {@code value} 唯一持有；控件不缓存 value、不自改 value。内部仅维护
 * {@code caretIndex} 本地 UI 状态，语义为真实文本的码点索引。所有写入都只经
 * {@code onChange.accept(next)} 上抛，handler 内不直接改文本节点属性。</p>
 *
 * <h3>结构</h3>
 * <pre>
 * root (ROW, clipChildren=true, focusable, padding)
 *   ├─ prefixText (caret 前显示文本，hitTestable=false)
 *   ├─ caret      (1px 竖线，hitTestable=false)
 *   └─ suffixText (caret 后显示文本，hitTestable=false)
 * </pre>
 *
 * <h3>已知局限</h3>
 * <p>B1 不做横向滚动：root 继续裁剪超出内容，长文本会被裁剪，caret 也可能在可视区域外。</p>
 */
public final class SceneTextInput {

    /** enabled 背景（深石板灰） */
    private static final int BG_ENABLED = 0xFF1E293B;
    /** disabled 背景（更暗灰） */
    private static final int BG_DISABLED = 0xFF111827;
    /** flat 变体透明背景 */
    private static final int BG_TRANSPARENT = 0x00000000;

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
    /** 纯静态工厂，禁止实例化。 */
    private SceneTextInput() {
    }

    /**
     * TextInput 输入契约 —— 受控文本：当前文本由外部只读 signal 驱动，
     * 输入经 onChange 交还期望新值真实 String。
     *
     * @param value       当前文本（响应式只读，受控源），控件绝不自己缓存/修改此值
     * @param enabled     是否启用，false 时不可输入且 handler 兜底早退
     * @param readOnly    是否只读，true 时可聚焦/移动 caret，但阻断文本写入
     * @param placeholder 占位文本，value 空串且未聚焦时显示
     * @param maxLength   最大长度（按码点数），填满后拒绝新增
     * @param inputType   输入类型，控制字符过滤与密码掩码显示
     * @param onChange    文本变更回调，以期望新值真实 String 调用
     * @param flat        是否使用无背景、无边框、无内边距的扁平变体
     */
    @Desugar
    public record Props(
            ReadableSignal<String> value,
            ReadableSignal<Boolean> enabled,
            ReadableSignal<Boolean> readOnly,
            String placeholder,
            int maxLength,
            SceneInputType inputType,
            Consumer<String> onChange,
            boolean flat
    ) {
        /**
         * 兼容构造：默认使用原始非 flat 外观。
         *
         * @param value       当前文本
         * @param enabled     是否启用
         * @param readOnly    是否只读
         * @param placeholder 占位文本
         * @param maxLength   最大长度
         * @param inputType   输入类型
         * @param onChange    文本变更回调
         */
        public Props(ReadableSignal<String> value,
                     ReadableSignal<Boolean> enabled,
                     ReadableSignal<Boolean> readOnly,
                     String placeholder,
                     int maxLength,
                     SceneInputType inputType,
                     Consumer<String> onChange) {
            this(value, enabled, readOnly, placeholder, maxLength, inputType, onChange, false);
        }
    }

    /**
     * 工厂：构建 TextInput 组件函数。
     *
     * @param rt    场景运行时
     * @param props TextInput 输入契约
     * @return 组件函数，交 {@code rt.mount(parent, ...)} 挂载
     */
    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        return () -> {
            SceneTextInputPrimitive.Props primitiveProps = new SceneTextInputPrimitive.Props(
                    props.value(), props.enabled(), props.readOnly(), props.placeholder(), props.maxLength(),
                    props.inputType(), props.onChange());
            SceneTextInputPrimitive.Result result = SceneTextInputPrimitive.create(rt, primitiveProps);
            SceneNode root = result.root();
            root.setPadding(props.flat() ? 0 : PADDING);
            root.setBorderWidth(props.flat() ? 0 : BORDER_WIDTH);
            root.setCornerRadius(props.flat() ? 0 : CORNER_RADIUS);

            rt.bind(Invalidation.PAINT,
                    Computed.create(() -> resolveTextColor(result.isPlaceholder().get(), props.enabled().get())),
                    result.prefixText()::setTextColor);
            rt.bind(Invalidation.PAINT,
                    Computed.create(() -> resolveTextColor(result.isPlaceholder().get(), props.enabled().get())),
                    result.suffixText()::setTextColor);

            rt.bind(Invalidation.PAINT,
                    Computed.create(() -> resolveBackgroundColor(props.enabled().get(), props.flat())),
                    root::setBackgroundColor);
            rt.bind(Invalidation.PAINT,
                    Computed.create(() -> resolveBorderColor(props.enabled().get(), result.caretVisible().get(), props.flat())),
                    root::setBorderColor);
            rt.bind(Invalidation.PAINT,
                    Computed.create(() -> resolveCaretColor(result.caretVisible().get())),
                    result.caret()::setBackgroundColor);
            rt.bind(Invalidation.PAINT, props.enabled(),
                    e -> root.setCursor(Boolean.TRUE.equals(e) ? SceneCursor.TEXT : SceneCursor.NOT_ALLOWED));
            rt.bind(Invalidation.PAINT, props.enabled(),
                    e -> root.setHitTestable(Boolean.TRUE.equals(e)));

            return root;
        };
    }

    /**
     * 解析文本色。
     *
     * @param placeholder 是否处于 placeholder 状态
     * @param enabled     是否启用
     * @return 文本色 ARGB
     */
    private static int resolveTextColor(Boolean placeholder, Boolean enabled) {
        if (!Boolean.TRUE.equals(enabled)) {
            return TEXT_DISABLED;
        }
        if (Boolean.TRUE.equals(placeholder)) {
            return TEXT_PLACEHOLDER;
        }
        return TEXT_ENABLED;
    }

    /**
     * 解析根节点背景色。
     *
     * @param enabled 是否启用
     * @param flat    是否 flat 变体
     * @return 背景色 ARGB
     */
    private static int resolveBackgroundColor(Boolean enabled, boolean flat) {
        if (flat) {
            return BG_TRANSPARENT;
        }
        return Boolean.TRUE.equals(enabled) ? BG_ENABLED : BG_DISABLED;
    }

    /**
     * 解析边框色。
     *
     * @param enabled 是否启用
     * @param caretVisible caret 是否可见
     * @param flat    是否 flat 变体
     * @return 边框色 ARGB
     */
    private static int resolveBorderColor(Boolean enabled, Boolean caretVisible, boolean flat) {
        if (flat) {
            return BG_TRANSPARENT;
        }
        if (!Boolean.TRUE.equals(enabled)) {
            return BORDER_DISABLED;
        }
        if (Boolean.TRUE.equals(caretVisible)) {
            return BORDER_FOCUSED;
        }
        return BORDER_ENABLED;
    }

    /**
     * 解析 caret 颜色。
     *
     * @param caretVisible caret 是否可见
     * @return caret 背景色 ARGB
     */
    private static int resolveCaretColor(Boolean caretVisible) {
        if (Boolean.TRUE.equals(caretVisible)) {
            return CARET_COLOR;
        }
        return CARET_TRANSPARENT;
    }
}
