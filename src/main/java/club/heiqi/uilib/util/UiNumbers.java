package club.heiqi.uilib.util;

/**
 * UI 数值规整原语：闭区间夹取（clamp）、0..1 夹取（clamp01）与有限性判断（isFinite）。
 *
 * <p>收编自仓内 17 处同构 clamp 实现体与 14 处手写有限数谓词（R2 消重）。
 * 全部方法为纯函数，逐位复刻被替换处的原表达式，语义零变更：</p>
 *
 * <ul>
 *   <li><b>夹取组合次序</b>：本类统一写成 {@code Math.max(min, Math.min(max, value))}；
 *       历史实现有 {@code Math.max(min, Math.min(value, max))} 一种内序变体。
 *       {@link Math#min(double,double)} / {@link Math#max(double,double)} 满足交换律
 *       （含 NaN 与 ±0：NaN 任一侧入、NaN 出；{@code min(+0,-0)=-0}、{@code max(+0,-0)=+0}），
 *       两种次序对全部输入逐位相同，合并安全。</li>
 *   <li><b>NaN</b>：{@code value} 为 NaN 时原样返回 NaN（不参与夹取、不兜底为边界）——
 *       与全部被替换实现一致；调用方需要 NaN 兜底必须自行先过 {@link #isFinite(double)}。</li>
 *   <li><b>+Infinity</b>：夹到 max（max 为 +Infinity 时保持 +Infinity）；
 *       <b>-Infinity</b>：夹到 min（min 为 -Infinity 时保持 -Infinity）。</li>
 *   <li><b>负零</b>：min 为 {@code +0.0} 且 value 为 {@code -0.0} 时返回 {@code +0.0}
 *       （{@code max(+0,-0)=+0}），与原 Math 组合一致。</li>
 *   <li><b>逆区间（max &lt; min）</b>：一律返回 min。这是 {@code max(min, min(max, v))}
 *       组合的固有性质，与被替换实现（含带 {@code if (max &lt; min) return min} 显式守卫的
 *       场景原语）对全部输入等值。</li>
 *   <li><b>取整</b>：本类不做任何 floor/round/trunc；取整方向留在调用点，未被本类合并的
 *       取整夹取点（如先 round 后 clamp）保持原样。</li>
 * </ul>
 */
public final class UiNumbers {

    private UiNumbers() {
    }

    /**
     * 把 int 夹到闭区间 [min, max]。
     *
     * <p>无 NaN/Infinity 概念；max &lt; min 时返回 min。int 上下界不溢出
     * （实现只做两次比较选择，无算术）。value 取 {@link Integer#MIN_VALUE} /
     * {@link Integer#MAX_VALUE} 时结果分别是 min / max（界合法时）。</p>
     *
     * @param value 输入值
     * @param min   下界
     * @param max   上界
     * @return min ≤ value ≤ max 时返回 value，否则返回越侧的界；逆区间返回 min
     */
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 把 double 夹到闭区间 [min, max]。
     *
     * <p>value 为 NaN 时返回 NaN（原样透传，不夹取）；+Infinity 夹到 max、
     * -Infinity 夹到 min；min 为 +0.0 且 value 为 -0.0 时返回 +0.0；
     * max &lt; min 时返回 min。不做取整。</p>
     *
     * @param value 输入值
     * @param min   下界
     * @param max   上界
     * @return 夹取结果，边界与 NaN 语义见方法级 javadoc
     */
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 把 float 夹到闭区间 [min, max]。
     *
     * <p>语义与 {@link #clamp(double, double, double)} 逐条相同（NaN 透传、
     * ±Infinity 夹到对应界、-0.0 在 min 为 +0.0 时归 +0.0、逆区间返回 min）；
     * 全程 float 运算，不经 double 拓宽，保持被替换处 float 实现的位级口径。</p>
     *
     * @param value 输入值
     * @param min   下界
     * @param max   上界
     * @return 夹取结果
     */
    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 把 float 夹到 [0, 1]，等价 {@code clamp(value, 0.0F, 1.0F)}。
     *
     * <p>NaN 透传 NaN；-0.0F 归 +0.0F（{@code max(+0F,-0F)=+0F}）；
     * +Infinity→1.0F、-Infinity→0.0F。收编自渲染层重复出现的
     * {@code Math.max(0.0F, Math.min(1.0F, x))} 习语（opacity/lensStrength/UV 夹取）。</p>
     *
     * @param value 输入值
     * @return 夹取结果
     */
    public static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    /**
     * double 有限性判断：非 NaN 且非 ±Infinity。
     *
     * <p>与 {@link Double#isFinite(double)} 及历史手写谓词
     * {@code !Double.isNaN(v) && !Double.isInfinite(v)} 对全部输入等值；
     * 对 +0.0 / -0.0 / {@link Double#MIN_VALUE} 等极端有限值恒为 true。</p>
     *
     * @param value 待判值
     * @return 有限返回 true；NaN 或 ±Infinity 返回 false
     */
    public static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    /**
     * float 有限性判断：非 NaN 且非 ±Infinity。
     *
     * <p>语义同 {@link #isFinite(double)}（float 域）；与
     * {@code !Float.isNaN(v) && !Float.isInfinite(v)} 等值。</p>
     *
     * @param value 待判值
     * @return 有限返回 true；NaN 或 ±Infinity 返回 false
     */
    public static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
