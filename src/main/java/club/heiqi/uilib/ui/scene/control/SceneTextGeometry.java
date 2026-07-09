package club.heiqi.uilib.ui.scene.control;

import java.util.function.Consumer;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * SceneTextGeometry —— 文本输入控件共用的码点几何与编辑工具集合。
 *
 * <p>从 {@link SceneTextInputPrimitive} 与 {@link SceneTextAreaPrimitive} 收敛字面重复的
 * 静态方法，分三区：几何（码点计数/夹取/偏移/截串/点击定位/前缀宽）/ 编辑（caret 前后删除）
 * / 缓存（点击定位前缀宽数组缓存类）。</p>
 *
 * <h3>放置 control 包的理由</h3>
 * <p>{@link #buildPrefixWidths} 与 {@link PrefixWidthCache} 依赖 {@link SceneRuntime}（调
 * {@code measureTextWidth}/{@code textMeasureEpoch}），control 包本就依赖 runtime，放此处
 * 不引入反向依赖；不放进 text/util 包以避免那些包反向引用 runtime。</p>
 *
 * <h3>substringByCodePoints 统一取 TextArea 版</h3>
 * <p>原 TextInput 版用 {@link #charOffsetForCodePointIndex}（其内部多一层幂等 clamp 再
 * {@code offsetByCodePoints}），TextArea 版直接 {@code text.offsetByCodePoints(0, start)}。
 * 因 {@code start}/{@code end} 入参已在本方法体内 clamp 到 {@code [0,max]}，二层数学等价，
 * 故统一取 TextArea 版（更简洁），emoji 代理对边界由单元测试固化。</p>
 *
 * <h3>PrefixWidthCache 实例级持有</h3>
 * <p>{@link PrefixWidthCache} 虽定义在工具类，但各 primitive 的 {@code create()} 闭包内仍各自
 * {@code new SceneTextGeometry.PrefixWidthCache()} 实例级持有——绝不能改成静态字段，
 * 否则多输入控件实例会跨实例串味（display/fontSize/epoch 三元组失效键被覆盖）。</p>
 *
 * <p>本类为纯静态工具：不碰节点、不订阅 signal、不写失效级别（守 NORTH_STAR I1-I12）。
 * 删除/编辑方法的副作用全部落在传入的 {@code onChange}/{@code caretIndex} 参数上。</p>
 */
public final class SceneTextGeometry {

    /** 纯静态工具，禁止实例化。 */
    private SceneTextGeometry() {
    }

    // ==================== 几何区 ====================

    /**
     * 计算字符串码点数（按 Unicode 码点，代理对算 1 个码点）。
     *
     * @param s 字符串
     * @return 码点数
     */
    public static int codePointCount(String s) {
        String text = SceneTextUtils.nullSafe(s);
        return text.codePointCount(0, text.length());
    }

    /**
     * 将 caret 码点索引钳制到当前 value 合法范围 {@code [0, codePointCount]}。
     *
     * @param value      当前真实值
     * @param caretIndex caret 码点索引（可为 null，视作 0）
     * @return 合法 caret 码点索引
     */
    public static int clampCaretIndex(String value, Integer caretIndex) {
        int max = codePointCount(value);
        int index = caretIndex == null ? 0 : caretIndex.intValue();
        return Math.max(0, Math.min(max, index));
    }

    /**
     * 把码点索引转换为 Java char offset（内部幂等 clamp 到 {@code [0,max]}）。
     *
     * @param value 字符串
     * @param index 码点索引
     * @return char offset
     */
    public static int charOffsetForCodePointIndex(String value, int index) {
        String text = SceneTextUtils.nullSafe(value);
        int clamped = Math.max(0, Math.min(codePointCount(text), index));
        return text.offsetByCodePoints(0, clamped);
    }

    /**
     * 按码点范围截取字符串（入参先 clamp 到 {@code [0,max]} 且 {@code end>=start}）。
     *
     * @param value   字符串
     * @param startCp 起始码点索引
     * @param endCp   结束码点索引
     * @return 子串
     */
    public static String substringByCodePoints(String value, int startCp, int endCp) {
        String text = SceneTextUtils.nullSafe(value);
        int max = codePointCount(text);
        int start = Math.max(0, Math.min(max, startCp));
        int end = Math.max(start, Math.min(max, endCp));
        int startOffset = text.offsetByCodePoints(0, start);
        int endOffset = text.offsetByCodePoints(0, end);
        return text.substring(startOffset, endOffset);
    }

    /**
     * 根据点击 X 和前缀宽度数组计算最近 caret 码点边界（按中点判归属）。
     *
     * @param prefixWidths 前缀宽度数组（长度=码点数+1，{@code prefixWidths[i]} 为前 i 个码点累计宽）
     * @param localX       root 内容区内 X
     * @return caret 码点索引
     */
    public static int caretIndexFromX(int[] prefixWidths, int localX) {
        int[] widths = prefixWidths == null || prefixWidths.length == 0 ? new int[] {0} : prefixWidths;
        int count = widths.length - 1;
        if (localX <= 0) {
            return 0;
        }
        for (int i = 0; i < count; i++) {
            int leftWidth = widths[i];
            int rightWidth = widths[i + 1];
            int midpoint = leftWidth + (rightWidth - leftWidth) / 2;
            if (localX < midpoint) {
                return i;
            }
        }
        return count;
    }

    /**
     * 构建用于点击定位的码点前缀宽度数组（逐边界 {@code measureTextWidth(整前缀)} 整测量）。
     *
     * @param rt         场景运行时
     * @param display    显示文本
     * @param fontSizePx 字号像素
     * @return 前缀宽度数组，长度为码点数 + 1
     */
    public static int[] buildPrefixWidths(SceneRuntime rt, String display, int fontSizePx) {
        String text = SceneTextUtils.nullSafe(display);
        int count = codePointCount(text);
        int[] prefixWidths = new int[count + 1];
        prefixWidths[0] = 0;
        for (int i = 1; i <= count; i++) {
            String prefix = substringByCodePoints(text, 0, i);
            prefixWidths[i] = rt.measureTextWidth(prefix, fontSizePx);
        }
        return prefixWidths;
    }

    // ==================== 编辑区 ====================

    /**
     * 删除 caret 前一码点（若该码点为代理对，整对删除）。
     *
     * <p>副作用经 {@code onChange} 上抛新值、经 {@code caretIndex} 写入新 caret 位置，
     * 本方法不直接持有任何真值或 signal。</p>
     *
     * @param cur        当前真实值
     * @param caret      caret 码点索引
     * @param onChange   变更回调
     * @param caretIndex caret signal
     */
    public static void deleteBeforeCaret(String cur, int caret, Consumer<String> onChange,
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
     * 删除 caret 后一码点（若该码点为代理对，整对删除）。
     *
     * @param cur      当前真实值
     * @param caret    caret 码点索引
     * @param onChange 变更回调
     */
    public static void deleteAfterCaret(String cur, int caret, Consumer<String> onChange) {
        if (caret >= codePointCount(cur)) {
            return;
        }
        int start = charOffsetForCodePointIndex(cur, caret);
        int end = charOffsetForCodePointIndex(cur, caret + 1);
        onChange.accept(cur.substring(0, start) + cur.substring(end));
    }

    // ==================== 点击前缀宽数组缓存 ====================

    /**
     * 点击定位前缀宽数组缓存：跨帧复用最近一次测量的整前缀宽数组。
     *
     * <p><b>必须实例级持有</b>：各 primitive 的 {@code create()} 闭包内各自 {@code new} 一个，
     * 绝不能改为静态字段——多输入控件实例会跨实例串味（三元组失效键被覆盖）。</p>
     *
     * <p>失效键三元组：显示文本 {@code display} + 字号 {@code fontSizePx} + 文本度量纪元
     * {@code textMeasureEpoch()}。必须含 epoch——字体重载后旧宽不可复用。</p>
     *
     * <p>像素一致保证：单次构建仍走 {@link #buildPrefixWidths} 逐边界整测量，缓存只跳过
     * 重复构建，不改变测量方式。</p>
     *
     * <p><b>禁止改为逐码点累加</b>：scene 的 measureWidth 内部含 ceil + round 双取整，
     * 若用「逐码点宽度相加」替代「整前缀整测量」，前缀宽会随累加步数产生像素漂移，
     * 导致点击定位错列。必须对每个前缀边界单独调用整测量。</p>
     */
    public static final class PrefixWidthCache {
        /** 缓存的显示文本。 */
        private String display;
        /** 缓存的字号像素。 */
        private int fontSizePx;
        /** 缓存的文本度量纪元。 */
        private int epoch;
        /** 缓存的码点前缀宽度数组。 */
        private int[] widths;

        /**
         * 获取与当前文本、字号和度量纪元匹配的前缀宽度数组。
         * 命中三元组时直接返回缓存，未命中时重建并刷新缓存。
         *
         * @param rt         场景运行时
         * @param display    显示文本（可为 null，内部 nullSafe）
         * @param fontSizePx 字号像素
         * @return 前缀宽度数组
         */
        public int[] get(SceneRuntime rt, String display, int fontSizePx) {
            String safeDisplay = SceneTextUtils.nullSafe(display);
            int currentEpoch = rt.textMeasureEpoch();
            if (widths != null && safeDisplay.equals(this.display)
                    && fontSizePx == this.fontSizePx && currentEpoch == this.epoch) {
                return widths;
            }
            this.display = safeDisplay;
            this.fontSizePx = fontSizePx;
            this.epoch = currentEpoch;
            this.widths = buildPrefixWidths(rt, safeDisplay, fontSizePx);
            return widths;
        }
    }
}
