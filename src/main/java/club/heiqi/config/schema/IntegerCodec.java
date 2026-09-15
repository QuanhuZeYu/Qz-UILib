package club.heiqi.config.schema;

import club.heiqi.config.ConfigException;
import club.heiqi.config.ConfigNode;
import club.heiqi.uilib.util.UiNumbers;

/**
 * INTEGER 字段/整数值的判读唯一实现（纯函数，无状态、不依赖 scene/控件）。
 *
 * <p><b>值语义</b>：64 位有符号整数，内存形态 {@link Long}，落盘形态十进制整数字面量
 * （{@code 16777216}，不是 {@code 1.6777216E7}）。<b>整数值</b>指有限、无小数部分的数——
 * {@code 16777216} / {@code 1.0} / {@code 1.6777216E7} 都是整数值 16777216；{@code 1.5}
 * 不是整数值。判读失败一律返回 {@code null} / 抛异常，<b>不截断、不夹取、不回落默认值</b>：
 * 非法值必须能被上层校验拒绝，而不是被伪装成合法值。</p>
 *
 * <h3>为什么不能只用 {@link ConfigNode#asLong()}</h3>
 * <p>{@code asLong()} 对 {@code Double} 原始值走 {@code Number#longValue()}，会把 {@code 1.5}
 * <b>静默截断</b>成 {@code 1}。故本类先按 double 判「是否为整数值」，再取回 long：见
 * {@link #toLong(Object)} 与 {@link #readNumberNode(ConfigNode)} 的分工。</p>
 *
 * <h3>精度边界（为什么读节点要单独一条路径）</h3>
 * <p>double 只能精确表示 {@code |v| <= 2^53} 的整数。内存中的 {@link Number} 按它自身的
 * 精确整数值取 long（{@link #toLong(Object)}：整型原样取，不经 double）；从 NUMBER 节点读值时
 * 优先按<b>十进制字面量</b>精确读（YAML 整数走这条），否则 {@code 9007199254740993} 会被 double
 * 舍成 {@code ...992}、{@code Long.MAX_VALUE} 会被舍成 {@code 2^63}——静默改值与「整数语义」
 * 的初衷相悖。指数/小数写法读不出精确字面量时，取 double 的整数值（等于该字面量已丢失精度后的
 * 机器值，不假装更精确）。</p>
 *
 * <h3>越界</h3>
 * <p>64 位域外（{@code |v| >= 2^63}）不是合法整数值，判读失败。超出 {@code long} 字面量
 * 范围的 YAML 整数在加载层已退化为 STRING 节点（见 {@code YamlConfigLoader.parseInteger}），
 * 会被严格 NodeType 检查拒绝，本类不为此另设通道。</p>
 */
public final class IntegerCodec {

    /**
     * long 界在 double 域的上界（{@code 2^63}）：{@code |v|} 达到它时上界侧越界
     * （long 最大值为 {@code 2^63-1}，double 只能表示 {@code 2^63}），下界侧
     * {@code -2^63} 恰是 {@link Long#MIN_VALUE}，合法。
     */
    private static final double LONG_LIMIT = 9.223372036854776E18D;

    /** 纯静态工具，禁止实例化。 */
    private IntegerCodec() {
    }

    /**
     * 内存 {@link Number} → {@link Long}，取该 Number 自身的精确整数值。
     *
     * <p>非 {@link Number}、非有限值、有小数部分、或超出 64 位域时返回 {@code null}
     * （调用方据此按原值保留给校验拒绝）。</p>
     *
     * <p><b>为什么整型 Number 不走 double</b>：{@code Long} 9007199254740993 的
     * {@code doubleValue()} 是 {@code ...992}，统一走 double 会把「本来就是精确整数」的值改坏。
     * 故整型原样取 long、{@link java.math.BigInteger}/{@link java.math.BigDecimal} 走
     * {@code longValueExact()}（越界或有小数部分即失败），只有 {@code Float}/{@code Double}
     * 才按 double 的整数值判读。</p>
     *
     * @param value 待判值，可为 null
     * @return 整数值；不是整数值时为 null
     */
    public static Long toLong(Object value) {
        if (!(value instanceof Number)) {
            return null;
        }
        Number number = (Number) value;
        if (number instanceof Byte || number instanceof Short
                || number instanceof Integer || number instanceof Long) {
            return Long.valueOf(number.longValue());
        }
        if (number instanceof java.math.BigInteger) {
            return exactOrNull((java.math.BigInteger) number);
        }
        if (number instanceof java.math.BigDecimal) {
            return exactOrNull((java.math.BigDecimal) number);
        }
        double floating = number.doubleValue();
        if (!UiNumbers.isFinite(floating) || floating != Math.floor(floating)) {
            return null;
        }
        if (floating < -LONG_LIMIT || floating >= LONG_LIMIT) {
            return null;
        }
        return Long.valueOf((long) floating);
    }

    /** {@code longValueExact()}：越界或有小数部分都抛 {@link ArithmeticException} ⇒ 判读失败。 */
    private static Long exactOrNull(java.math.BigInteger value) {
        try {
            return Long.valueOf(value.longValueExact());
        } catch (ArithmeticException e) {
            return null;
        }
    }

    /** {@code longValueExact()}：越界或有小数部分都抛 {@link ArithmeticException} ⇒ 判读失败。 */
    private static Long exactOrNull(java.math.BigDecimal value) {
        try {
            return Long.valueOf(value.longValueExact());
        } catch (ArithmeticException e) {
            return null;
        }
    }

    /**
     * NUMBER 节点 → {@link Long}；类型不符 / 非整数值 / 越界返回 {@code null}。
     *
     * <p>判读规则（顺序是刻意的）：</p>
     * <ol>
     *   <li>节点原文若是十进制整数字面量，先按字面量精确读，且要求与数值判读一致才采信——
     *       {@code 9007199254740993}（{@code 2^53+1}）与 {@code Long.MAX_VALUE} 只有这条能读准，
     *       走 double 会被舍成 {@code ...992} / {@code 2^63}。</li>
     *   <li>其余形态（{@code 1.0} / {@code 1.6777216E7} / {@code 1.0E17}）按 double 判整数值后
     *       窄化取回；{@code 1.5} 在这里被挡下（不截断），越界在这里被挡下（不夹取）。</li>
     * </ol>
     *
     * @param node 配置节点，可为 null
     * @return 整数值；节点不是 NUMBER 或不是整数值时为 null
     */
    public static Long readNumberNode(ConfigNode node) {
        if (node == null || node.isNull() || node.getType() != ConfigNode.NodeType.NUMBER) {
            return null;
        }
        double number;
        try {
            number = node.asDouble();
        } catch (ConfigException e) {
            return null;
        } catch (RuntimeException e) {
            // 未知 Number 实现（如 LazilyParsedNumber 的非整数字面量）判读失败即 null，不静默折叠
            return null;
        }
        if (!UiNumbers.isFinite(number) || number != Math.floor(number)) {
            return null;
        }
        Long literal = parse(node.asString());
        if (literal != null && (double) literal.longValue() == number) {
            return literal;
        }
        if (number < -LONG_LIMIT || number >= LONG_LIMIT) {
            return null;
        }
        return Long.valueOf((long) number);
    }

    /**
     * NUMBER 节点 → {@code long}；判读失败抛 {@link ConfigException}（严格路径）。
     *
     * @param node 配置节点，可为 null
     * @param path 诊断用字段路径
     * @return 整数值
     * @throws ConfigException 节点类型不符或不是整数值
     */
    public static long requireNumberNode(ConfigNode node, String path) throws ConfigException {
        if (node == null || node.isNull() || node.getType() != ConfigNode.NodeType.NUMBER) {
            throw new ConfigException("integer value: " + path + " expected NUMBER NodeType, got "
                    + (node == null ? "null" : String.valueOf(node.getType())),
                    ConfigException.Category.VALIDATION);
        }
        Long value = readNumberNode(node);
        if (value == null) {
            throw new ConfigException("integer value: " + path
                    + " must be a finite integral 64-bit number, got " + node.asString(),
                    ConfigException.Category.VALIDATION);
        }
        return value.longValue();
    }

    /**
     * 十进制整数文本 → {@link Long}（首尾空白忽略，仅接受十进制整数字面量）。
     *
     * <p>UI 输入的唯一判读点：{@code "1.5"} / {@code "1e5"} / {@code "0x10"} / 空串一律
     * 返回 {@code null}，由调用方按原文写入草稿、交草稿校验报「不是有效整数」——
     * 不近似兜底、不静默取整。</p>
     *
     * @param text 待解析文本，可为 null
     * @return 整数值；非法时为 null
     */
    public static Long parse(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(Long.parseLong(trimmed));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 值 → 规范显示文本（十进制整数形态，无 {@code .0} 与指数写法）。
     *
     * <p>{@code null} → 空串；整数值 {@link Number} → 十进制整数；其余（非法原文、非整数值）
     * 原样 {@link String#valueOf(Object)} 透出——用户要能看见自己写错在哪。</p>
     *
     * @param value draft 当前值，可为 null
     * @return 显示文本，恒非 null
     */
    public static String textOf(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number) {
            Long integral = toLong(value);
            return integral != null ? Long.toString(integral.longValue()) : String.valueOf(value);
        }
        return String.valueOf(value);
    }
}
