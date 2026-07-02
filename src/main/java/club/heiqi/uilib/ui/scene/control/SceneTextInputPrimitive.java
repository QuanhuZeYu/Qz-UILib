package club.heiqi.uilib.ui.scene.control;

import java.util.function.Consumer;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;

import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * SceneTextInputPrimitive —— 无样式单行受控文本输入行为核心。
 *
 * <p>该 primitive 只负责结构、输入行为、caret 状态和文本布局绑定，不设置背景、边框、
 * 文本色、caret 色、cursor 或 padding 等 chrome。外观由上层 wrapper 自行组合。</p>
 *
 * <p><b>消费者契约</b>：primitive 返回的 root/prefixText/caret/suffixText 默认无任何颜色，
 * 直接消费时必须自行挂 PAINT 绑定（尤其 caret 节点需根据 {@code caretVisible} 上色，
 * 否则 caret 在聚焦时不可见）。可参考 {@link SceneTextInput} 的 chrome 挂载方式。</p>
 */
public final class SceneTextInputPrimitive {

    /** root 行内间距：必须 0，否则 caret 与文本间多 1px gap。 */
    private static final int GAP = 0;
    /** caret 宽度（像素，1px 竖线）。 */
    private static final int CARET_WIDTH = 1;
    /** 密码掩码字符（圆点 U+2022）。 */
    private static final char MASK_CHAR = '\u2022';

    /** 纯静态工厂，禁止实例化。 */
    private SceneTextInputPrimitive() {
    }

    /**
     * TextInput primitive 输入契约 —— 只包含行为所需数据，不包含样式开关。
     *
     * @param value       当前文本（响应式只读，受控源）
     * @param enabled     是否启用
     * @param readOnly    是否只读
     * @param placeholder 占位文本
     * @param maxLength   最大长度（按码点数）
     * @param inputType   输入类型
     * @param onChange    文本变更回调
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
     * TextInput primitive 创建结果，暴露无样式结构节点和派生行为状态。
     *
     * @param root          根节点
     * @param prefixText    caret 前文本节点
     * @param caret         caret 节点
     * @param suffixText    caret 后文本节点
     * @param caretIndex    caret 码点索引 signal
     * @param caretVisible  caret 是否可见
     * @param isPlaceholder 当前是否处于空值且有 placeholder 的状态
     */
    @Desugar
    public record Result(
            SceneNode root,
            SceneNode prefixText,
            SceneNode caret,
            SceneNode suffixText,
            ReadableSignal<Integer> caretIndex,
            ReadableSignal<Boolean> caretVisible,
            ReadableSignal<Boolean> isPlaceholder
    ) {
    }

    /**
     * 创建无样式 TextInput primitive。
     *
     * @param rt    场景运行时
     * @param props primitive 输入契约
     * @return 创建结果，供 wrapper 或高级控件挂载样式
     */
    public static Result create(SceneRuntime rt, Props props) {
        final String placeholder = props.placeholder();
        final int maxLength = props.maxLength();
        final SceneInputType inputType = props.inputType();
        final Signal<Integer> caretIndex = Signal.create(Integer.valueOf(0));
        final SceneTextGeometry.PrefixWidthCache prefixWidthCache = new SceneTextGeometry.PrefixWidthCache();

        SceneNode root = SceneNode.row();
        root.setCrossAxisAlign(CrossAxisAlign.CENTER);
        root.setMainAxisAlign(MainAxisAlign.START);
        root.setGap(GAP);
        root.setClipChildren(true);

        SceneNode prefixText = new SceneNode();
        prefixText.setHitTestable(false);
        root.appendChild(prefixText);

        SceneNode caret = new SceneNode();
        caret.setPreferredWidth(CARET_WIDTH);
        caret.setPreferredHeight(rt.lineHeight(caret.getFontSize()));
        caret.setHitTestable(false);
        // 标记为空文本叶：computeWidth 对 text==null 的无文本叶返回 outerWidth（填满父宽），
        // 会把同行 prefix/suffix 推出 row 裁剪区。setText("") 使其走 text.isEmpty() 分支返回 padH=0，
        // 确保 setPreferredWidth(0)（未来若改为条件性取消首选宽度）时宽度真正归零，不撑满行。
        // 与 SceneTextAreaPrimitive 的 caret 兜底对齐，防止潜伏撑满 bug。
        caret.setText("");
        root.appendChild(caret);

        SceneNode suffixText = new SceneNode();
        suffixText.setHitTestable(false);
        root.appendChild(suffixText);

        SceneInteractionState is = rt.interactionState(root);
        ReadableSignal<Boolean> caretVisible = Computed.create(
                () -> Boolean.valueOf(Boolean.TRUE.equals(props.enabled().get()) && Boolean.TRUE.equals(is.focused().get())));
        ReadableSignal<Boolean> isPlaceholder = Computed.create(
                () -> Boolean.valueOf(SceneTextGeometry.nullSafe(props.value().get()).isEmpty() && !SceneTextGeometry.nullSafe(placeholder).isEmpty()));

        rt.bindComputed(() -> prefixDisplayText(
                        props.value().get(), is.focused().get(), placeholder, inputType, caretIndex.get()),
                prefixText::setText);
        rt.bindComputed(() -> suffixDisplayText(
                        props.value().get(), is.focused().get(), inputType, caretIndex.get()),
                suffixText::setText);

        rt.focusable(root, props.enabled());

        rt.on(root, SceneEventType.POINTER_DOWN, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled().get())) {
                return;
            }
            LayoutBox rootBox = (LayoutBox) root.getCachedLayout();
            if (rootBox == null) {
                return;
            }
            String value = SceneTextGeometry.nullSafe(props.value().get());
            String display = displayValue(value, inputType);
            // 坐标系（I12 两层）：effectiveTarget=root，ctx.getLocalPointerX() = raw - absoluteBox(root,treeAbs)
            // = root 局部 X（框架每级重算，rootAbs≠0 不再错位）。再减 paddingLeft 得文本区局部。
            int localX = ctx.getLocalPointerX() - root.getPaddingLeft();
            int fontSizePx = root.getFontSize();
            int[] prefixWidths = prefixWidthCache.get(rt, display, fontSizePx);
            caretIndex.set(Integer.valueOf(SceneTextGeometry.caretIndexFromX(prefixWidths, localX)));
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
            String cur = SceneTextGeometry.nullSafe(props.value().get());
            int caretPos = SceneTextGeometry.clampCaretIndex(cur, caretIndex.get());
            FilteredInsert filtered = filterForInsert(raw, Math.max(0, maxLength - SceneTextGeometry.codePointCount(cur)), inputType);
            if (filtered.text.isEmpty()) {
                return;
            }
            int offset = SceneTextGeometry.charOffsetForCodePointIndex(cur, caretPos);
            String next = cur.substring(0, offset) + filtered.text + cur.substring(offset);
            props.onChange().accept(next);
            caretIndex.set(Integer.valueOf(caretPos + filtered.codePointCount));
        });

        rt.on(root, SceneEventType.KEY_DOWN, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled().get()) || ev.getKeyAction() != SceneKeyAction.PRESSED) {
                return;
            }
            String cur = SceneTextGeometry.nullSafe(props.value().get());
            int caretPos = SceneTextGeometry.clampCaretIndex(cur, caretIndex.get());
            SceneKey key = ev.getKey();
            if (key == SceneKey.ARROW_LEFT) {
                caretIndex.set(Integer.valueOf(Math.max(0, caretPos - 1)));
                return;
            }
            if (key == SceneKey.ARROW_RIGHT) {
                caretIndex.set(Integer.valueOf(Math.min(SceneTextGeometry.codePointCount(cur), caretPos + 1)));
                return;
            }
            if (key == SceneKey.HOME) {
                caretIndex.set(Integer.valueOf(0));
                return;
            }
            if (key == SceneKey.END) {
                caretIndex.set(Integer.valueOf(SceneTextGeometry.codePointCount(cur)));
                return;
            }
            if (Boolean.TRUE.equals(props.readOnly().get())) {
                return;
            }
            if (key == SceneKey.BACKSPACE) {
                SceneTextGeometry.deleteBeforeCaret(cur, caretPos, props.onChange(), caretIndex);
            } else if (key == SceneKey.DELETE) {
                SceneTextGeometry.deleteAfterCaret(cur, caretPos, props.onChange());
            }
        });

        return new Result(root, prefixText, caret, suffixText, caretIndex, caretVisible, isPlaceholder);
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
        String v = SceneTextGeometry.nullSafe(value);
        if (v.isEmpty()) {
            return Boolean.TRUE.equals(focused) ? "" : SceneTextGeometry.nullSafe(placeholder);
        }
        int caret = SceneTextGeometry.clampCaretIndex(v, caretIndex);
        if (inputType == SceneInputType.PASSWORD) {
            return mask(caret);
        }
        return SceneTextGeometry.substringByCodePoints(v, 0, caret);
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
        String v = SceneTextGeometry.nullSafe(value);
        if (v.isEmpty()) {
            return "";
        }
        int caret = SceneTextGeometry.clampCaretIndex(v, caretIndex);
        int count = SceneTextGeometry.codePointCount(v);
        if (inputType == SceneInputType.PASSWORD) {
            return mask(count - caret);
        }
        return SceneTextGeometry.substringByCodePoints(v, caret, count);
    }

    /**
     * 计算用于点击定位的完整显示文本。
     *
     * @param value     真实值
     * @param inputType 输入类型
     * @return display 文本
     */
    private static String displayValue(String value, SceneInputType inputType) {
        String v = SceneTextGeometry.nullSafe(value);
        if (inputType == SceneInputType.PASSWORD) {
            return mask(SceneTextGeometry.codePointCount(v));
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
