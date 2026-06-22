package club.heiqi.uilib.ui.scene.control;

import java.util.function.Consumer;
import java.util.function.Supplier;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;
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
    /** root 行内间距：必须 0，否则 caret 与文本间多 1px gap */
    private static final int GAP = 0;
    /** caret 宽度（像素，1px 竖线） */
    private static final int CARET_WIDTH = 1;

    /** 密码掩码字符（圆点 U+2022） */
    private static final char MASK_CHAR = '\u2022';

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
     * @param rt    场景运行时
     * @param props TextInput 输入契约
     * @return 组件函数，交 {@code rt.mount(parent, ...)} 挂载
     */
    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        return () -> {
            final String placeholder = props.placeholder();
            final int maxLength = props.maxLength();
            final SceneInputType inputType = props.inputType();
            final Signal<Integer> caretIndex = Signal.create(Integer.valueOf(0));

            SceneNode root = new SceneNode();
            root.setFlexDirection(FlexDirection.ROW);
            root.setCrossAxisAlign(CrossAxisAlign.CENTER);
            root.setMainAxisAlign(MainAxisAlign.START);
            root.setGap(GAP);
            root.setPadding(PADDING);
            root.setBorderWidth(BORDER_WIDTH);
            root.setCornerRadius(CORNER_RADIUS);
            root.setClipChildren(true);

            SceneNode prefixText = new SceneNode();
            prefixText.setHitTestable(false);
            root.appendChild(prefixText);

            SceneNode caret = new SceneNode();
            caret.setPreferredWidth(CARET_WIDTH);
            caret.setPreferredHeight(rt.lineHeight(caret.getFontSize()));
            caret.setHitTestable(false);
            root.appendChild(caret);

            SceneNode suffixText = new SceneNode();
            suffixText.setHitTestable(false);
            root.appendChild(suffixText);

            SceneInteractionState is = rt.interactionState(root);

            rt.bind(Invalidation.LAYOUT,
                    Computed.create(() -> prefixDisplayText(
                            props.value().get(), is.focused().get(), placeholder, inputType, caretIndex.get())),
                    prefixText::setText);
            rt.bind(Invalidation.LAYOUT,
                    Computed.create(() -> suffixDisplayText(
                            props.value().get(), is.focused().get(), inputType, caretIndex.get())),
                    suffixText::setText);

            rt.bind(Invalidation.PAINT,
                    Computed.create(() -> resolveTextColor(
                            props.value().get(), is.focused().get(), placeholder, props.enabled().get())),
                    prefixText::setTextColor);
            rt.bind(Invalidation.PAINT,
                    Computed.create(() -> resolveTextColor(
                            props.value().get(), is.focused().get(), placeholder, props.enabled().get())),
                    suffixText::setTextColor);

            rt.bind(Invalidation.PAINT, props.enabled(),
                    e -> root.setBackgroundColor(Boolean.TRUE.equals(e) ? BG_ENABLED : BG_DISABLED));
            rt.bind(Invalidation.PAINT,
                    Computed.create(() -> resolveBorderColor(props.enabled().get(), is.focused().get())),
                    root::setBorderColor);
            rt.bind(Invalidation.PAINT,
                    Computed.create(() -> resolveCaretColor(props.enabled().get(), is.focused().get())),
                    caret::setBackgroundColor);
            rt.bind(Invalidation.PAINT, props.enabled(),
                    e -> root.setCursor(Boolean.TRUE.equals(e) ? SceneCursor.TEXT : SceneCursor.NOT_ALLOWED));
            rt.bind(Invalidation.PAINT, props.enabled(),
                    e -> root.setHitTestable(Boolean.TRUE.equals(e)));

            rt.focusable(root);

            rt.on(root, SceneEventType.POINTER_DOWN, (ev, ctx) -> {
                if (!Boolean.TRUE.equals(props.enabled().get())) {
                    return;
                }
                LayoutBox rootBox = (LayoutBox) root.getCachedLayout();
                if (rootBox == null) {
                    return;
                }
                String value = nullSafe(props.value().get());
                String display = displayValue(value, inputType);
                int localX = ev.getPointerX() - absoluteX(root) - PADDING;
                caretIndex.set(Integer.valueOf(caretIndexFromX(rt, display, localX, root.getFontSize())));
            });

            rt.on(root, SceneEventType.TEXT_INPUT, (ev, ctx) -> {
                if (!Boolean.TRUE.equals(props.enabled().get())
                        || Boolean.TRUE.equals(props.readOnly().get())) {
                    return;
                }
                String raw = ev.getText();
                if (raw == null || raw.isEmpty()) {
                    return;
                }
                String cur = nullSafe(props.value().get());
                int caretPos = clampCaretIndex(cur, caretIndex.get());
                FilteredInsert filtered = filterForInsert(raw, Math.max(0, maxLength - codePointCount(cur)), inputType);
                if (filtered.text.isEmpty()) {
                    return;
                }
                int offset = charOffsetForCodePointIndex(cur, caretPos);
                String next = cur.substring(0, offset) + filtered.text + cur.substring(offset);
                props.onChange().accept(next);
                caretIndex.set(Integer.valueOf(caretPos + filtered.codePointCount));
            });

            rt.on(root, SceneEventType.KEY_DOWN, (ev, ctx) -> {
                if (!Boolean.TRUE.equals(props.enabled().get()) || ev.getKeyAction() != SceneKeyAction.PRESSED) {
                    return;
                }
                String cur = nullSafe(props.value().get());
                int caretPos = clampCaretIndex(cur, caretIndex.get());
                SceneKey key = ev.getKey();
                if (key == SceneKey.ARROW_LEFT) {
                    caretIndex.set(Integer.valueOf(Math.max(0, caretPos - 1)));
                    return;
                }
                if (key == SceneKey.ARROW_RIGHT) {
                    caretIndex.set(Integer.valueOf(Math.min(codePointCount(cur), caretPos + 1)));
                    return;
                }
                if (key == SceneKey.HOME) {
                    caretIndex.set(Integer.valueOf(0));
                    return;
                }
                if (key == SceneKey.END) {
                    caretIndex.set(Integer.valueOf(codePointCount(cur)));
                    return;
                }
                if (Boolean.TRUE.equals(props.readOnly().get())) {
                    return;
                }
                if (key == SceneKey.BACKSPACE) {
                    deleteBeforeCaret(cur, caretPos, props.onChange(), caretIndex);
                } else if (key == SceneKey.DELETE) {
                    deleteAfterCaret(cur, caretPos, props.onChange());
                }
            });

            return root;
        };
    }

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
     * 计算字符串码点数。
     *
     * @param s 字符串
     * @return 码点数
     */
    private static int codePointCount(String s) {
        String text = nullSafe(s);
        return text.codePointCount(0, text.length());
    }

    /**
     * 将 caret 码点索引钳制到当前 value 合法范围。
     *
     * @param value      当前真实值
     * @param caretIndex caret 码点索引
     * @return 合法 caret 码点索引
     */
    private static int clampCaretIndex(String value, Integer caretIndex) {
        int max = codePointCount(value);
        int index = caretIndex == null ? 0 : caretIndex.intValue();
        return Math.max(0, Math.min(max, index));
    }

    /**
     * 把码点索引转换为 Java char offset。
     *
     * @param value 字符串
     * @param index 码点索引
     * @return char offset
     */
    private static int charOffsetForCodePointIndex(String value, int index) {
        String text = nullSafe(value);
        int clamped = Math.max(0, Math.min(codePointCount(text), index));
        return text.offsetByCodePoints(0, clamped);
    }

    /**
     * 按码点范围截取字符串。
     *
     * @param value   字符串
     * @param startCp 起始码点索引
     * @param endCp   结束码点索引
     * @return 子串
     */
    private static String substringByCodePoints(String value, int startCp, int endCp) {
        String text = nullSafe(value);
        int max = codePointCount(text);
        int start = Math.max(0, Math.min(max, startCp));
        int end = Math.max(start, Math.min(max, endCp));
        int startOffset = charOffsetForCodePointIndex(text, start);
        int endOffset = charOffsetForCodePointIndex(text, end);
        return text.substring(startOffset, endOffset);
    }

    /**
     * 计算 caret 前显示文本。
     *
     * @param value       真实值
     * @param focused     是否聚焦
     * @param placeholder 占位文本
     * @param inputType   输入类型
     * @param caretIndex  caret 码点索引
     * @return prefix 显示文本
     */
    private static String prefixDisplayText(String value, Boolean focused, String placeholder,
                                            SceneInputType inputType, Integer caretIndex) {
        String v = nullSafe(value);
        if (v.isEmpty()) {
            return Boolean.TRUE.equals(focused) ? "" : nullSafe(placeholder);
        }
        int caret = clampCaretIndex(v, caretIndex);
        if (inputType == SceneInputType.PASSWORD) {
            return mask(caret);
        }
        return substringByCodePoints(v, 0, caret);
    }

    /**
     * 计算 caret 后显示文本。
     *
     * @param value      真实值
     * @param focused    是否聚焦
     * @param inputType  输入类型
     * @param caretIndex caret 码点索引
     * @return suffix 显示文本
     */
    private static String suffixDisplayText(String value, Boolean focused,
                                            SceneInputType inputType, Integer caretIndex) {
        String v = nullSafe(value);
        if (v.isEmpty()) {
            return "";
        }
        int caret = clampCaretIndex(v, caretIndex);
        int count = codePointCount(v);
        if (inputType == SceneInputType.PASSWORD) {
            return mask(count - caret);
        }
        return substringByCodePoints(v, caret, count);
    }

    /**
     * 计算用于点击定位的完整显示文本。
     *
     * @param value     真实值
     * @param inputType 输入类型
     * @return display 文本
     */
    private static String displayValue(String value, SceneInputType inputType) {
        String v = nullSafe(value);
        if (inputType == SceneInputType.PASSWORD) {
            return mask(codePointCount(v));
        }
        return v;
    }

    /**
     * 生成指定码点数量的密码掩码。
     *
     * @param count 掩码数量
     * @return 掩码串
     */
    private static String mask(int count) {
        StringBuilder sb = new StringBuilder(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            sb.append(MASK_CHAR);
        }
        return sb.toString();
    }

    /**
     * 解析文本色。
     *
     * @param value       当前真实值
     * @param focused     是否聚焦
     * @param placeholder 占位文本
     * @param enabled     是否启用
     * @return 文本色 ARGB
     */
    private static int resolveTextColor(String value, Boolean focused, String placeholder, Boolean enabled) {
        if (!Boolean.TRUE.equals(enabled)) {
            return TEXT_DISABLED;
        }
        String v = nullSafe(value);
        if (v.isEmpty() && !Boolean.TRUE.equals(focused) && !nullSafe(placeholder).isEmpty()) {
            return TEXT_PLACEHOLDER;
        }
        return TEXT_ENABLED;
    }

    /**
     * 解析边框色。
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
     * 解析 caret 颜色。
     *
     * @param enabled 是否启用
     * @param focused 是否聚焦
     * @return caret 背景色 ARGB
     */
    private static int resolveCaretColor(Boolean enabled, Boolean focused) {
        if (Boolean.TRUE.equals(enabled) && Boolean.TRUE.equals(focused)) {
            return CARET_COLOR;
        }
        return CARET_TRANSPARENT;
    }

    /**
     * 过滤输入并限制本次可插入码点数。
     *
     * @param input     原始输入
     * @param available 剩余可插入码点数
     * @param inputType 输入类型
     * @return 过滤结果
     */
    private static FilteredInsert filterForInsert(String input, int available, SceneInputType inputType) {
        StringBuilder sb = new StringBuilder();
        int accepted = 0;
        int i = 0;
        while (i < input.length() && accepted < available) {
            int cp = input.codePointAt(i);
            i += Character.charCount(cp);
            if (!isAccepted(cp, inputType)) {
                continue;
            }
            sb.appendCodePoint(cp);
            accepted++;
        }
        return new FilteredInsert(sb.toString(), accepted);
    }

    /**
     * 单码点是否被接受。
     *
     * @param cp        码点
     * @param inputType 输入类型
     * @return true 表示放行
     */
    private static boolean isAccepted(int cp, SceneInputType inputType) {
        if (Character.isISOControl(cp) || cp == '\n' || cp == '\r' || cp == '\t') {
            return false;
        }
        if (inputType == SceneInputType.NUMBER) {
            if (cp >= '0' && cp <= '9') {
                return true;
            }
            return cp == '.' || cp == '-' || cp == '+' || cp == 'e' || cp == 'E';
        }
        return true;
    }

    /**
     * 删除 caret 前一码点。
     *
     * @param cur        当前真实值
     * @param caret      caret 码点索引
     * @param onChange   变更回调
     * @param caretIndex caret signal
     */
    private static void deleteBeforeCaret(String cur, int caret, Consumer<String> onChange,
                                          Signal<Integer> caretIndex) {
        if (caret <= 0) {
            return;
        }
        int start = charOffsetForCodePointIndex(cur, caret - 1);
        int end = charOffsetForCodePointIndex(cur, caret);
        onChange.accept(cur.substring(0, start) + cur.substring(end));
        caretIndex.set(Integer.valueOf(caret - 1));
    }

    /**
     * 删除 caret 后一码点。
     *
     * @param cur      当前真实值
     * @param caret    caret 码点索引
     * @param onChange 变更回调
     */
    private static void deleteAfterCaret(String cur, int caret, Consumer<String> onChange) {
        if (caret >= codePointCount(cur)) {
            return;
        }
        int start = charOffsetForCodePointIndex(cur, caret);
        int end = charOffsetForCodePointIndex(cur, caret + 1);
        onChange.accept(cur.substring(0, start) + cur.substring(end));
    }

    /**
     * 根据点击 X 计算最近 caret 码点边界。
     *
     * @param rt         场景运行时
     * @param display    显示文本
     * @param localX     root 内容区内 X
     * @param fontSizePx 字号像素
     * @return caret 码点索引
     */
    private static int caretIndexFromX(SceneRuntime rt, String display, int localX, int fontSizePx) {
        String text = nullSafe(display);
        int count = codePointCount(text);
        if (localX <= 0) {
            return 0;
        }
        for (int i = 0; i < count; i++) {
            String left = substringByCodePoints(text, 0, i);
            String right = substringByCodePoints(text, 0, i + 1);
            int leftWidth = rt.measureTextWidth(left, fontSizePx);
            int rightWidth = rt.measureTextWidth(right, fontSizePx);
            int midpoint = leftWidth + (rightWidth - leftWidth) / 2;
            if (localX < midpoint) {
                return i;
            }
        }
        return count;
    }

    /**
     * 累加节点及祖先的 LayoutBox x，得到相对场景树根的绝对 x。
     *
     * @param node 目标节点
     * @return 相对场景树根的绝对 x
     */
    private static int absoluteX(SceneNode node) {
        int x = 0;
        SceneNode cur = node;
        while (cur != null) {
            Object cached = cur.getCachedLayout();
            if (cached instanceof LayoutBox) {
                x += ((LayoutBox) cached).getX();
            }
            cur = cur.__getParent();
        }
        return x;
    }

    /** 过滤后的插入文本与码点数。 */
    private static final class FilteredInsert {
        /** 过滤后的文本。 */
        private final String text;
        /** 过滤后码点数。 */
        private final int codePointCount;

        /**
         * 创建过滤结果。
         *
         * @param text           文本
         * @param codePointCount 码点数
         */
        private FilteredInsert(String text, int codePointCount) {
            this.text = text;
            this.codePointCount = codePointCount;
        }
    }
}
