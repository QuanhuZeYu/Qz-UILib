package club.heiqi.uilib.ui.style.cascade;

import club.heiqi.uilib.ui.style.values.UiStyleLength;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * CSS 自定义属性（变量）容器。
 *
 * <p>类似浏览器的 CSS Variables（{@code --custom-property}），提供命名的样式值存储，
 * 支持主题切换和跨元素样式复用。</p>
 *
 * <p>变量挂载在 {@code UiDocument} 上，作用域等同于 CSS 的 {@code :root} 变量。
 * 变量值变更时会触发文档样式重算。</p>
 *
 * @apiNote 当前版本仅作为代码侧读写的命名样式值字典，不解析 CSS {@code var(--name, fallback)} 表达式。
 *          字符串中包含 {@code var(...)} 不会被任何样式入口识别为变量引用，请由页面代码读取变量值后
 *          再写入对应样式声明。LTS 不承诺 4.x 期间补足 {@code var(...)} 解析；如有需要请用业务侧字符串拼接。
 *
 * <p>用法示例：</p>
 * <pre>{@code
 * UiStyleVariables vars = UiStyleVariables.create()
 *     .setColor("--primary", 0xFF4488FF)
 *     .setColor("--bg-surface", 0xFF1A1A2E)
 *     .setLength("--spacing-md", UiStyleLength.px(8))
 *     .setLength("--radius-sm", UiStyleLength.px(4));
 * document.setStyleVariables(vars);
 *
 * // 在样式声明中引用变量
 * element.style().setBackgroundColor(vars.getColor("--bg-surface"));
 * }</pre>
 */
public final class UiStyleVariables {

    private final Map<String, Integer> colors = new LinkedHashMap<String, Integer>();
    private final Map<String, UiStyleLength> lengths = new LinkedHashMap<String, UiStyleLength>();
    private final Map<String, String> strings = new LinkedHashMap<String, String>();
    private Runnable changeCallback;

    private UiStyleVariables() {}

    /**
     * 创建空变量容器。
     *
     * @return 变量容器
     */
    public static UiStyleVariables create() {
        return new UiStyleVariables();
    }

    /**
     * 设置变更回调（框架内部使用）。
     *
     * @param changeCallback 变更回调
     */
    public void setChangeCallback(Runnable changeCallback) {
        this.changeCallback = changeCallback;
    }

    // ========== 颜色变量 ==========

    /**
     * 设置颜色变量。
     *
     * @param name 变量名（建议以 -- 开头）
     * @param color 颜色值（ARGB）
     * @return 当前容器（链式调用）
     */
    public UiStyleVariables setColor(String name, int color) {
        Objects.requireNonNull(name, "name");
        Integer previous = colors.put(name, Integer.valueOf(color));
        if (previous == null || previous.intValue() != color) {
            notifyChange();
        }
        return this;
    }

    /**
     * 获取颜色变量值。
     *
     * @param name 变量名
     * @return 颜色值；变量不存在时返回 0（透明）
     */
    public int getColor(String name) {
        return getColor(name, 0);
    }

    /**
     * 获取颜色变量值（带默认值）。
     *
     * @param name 变量名
     * @param defaultValue 变量不存在时的默认值
     * @return 颜色值
     */
    public int getColor(String name, int defaultValue) {
        Integer value = colors.get(name);
        return value != null ? value.intValue() : defaultValue;
    }

    /**
     * 判断颜色变量是否存在。
     *
     * @param name 变量名
     * @return 是否存在
     */
    public boolean hasColor(String name) {
        return colors.containsKey(name);
    }

    /**
     * 移除颜色变量。
     *
     * @param name 变量名
     * @return 当前容器（链式调用）
     */
    public UiStyleVariables removeColor(String name) {
        if (colors.remove(name) != null) {
            notifyChange();
        }
        return this;
    }

    // ========== 长度变量 ==========

    /**
     * 设置长度变量。
     *
     * @param name 变量名（建议以 -- 开头）
     * @param length 长度值
     * @return 当前容器（链式调用）
     */
    public UiStyleVariables setLength(String name, UiStyleLength length) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(length, "length");
        UiStyleLength previous = lengths.put(name, length);
        if (!length.equals(previous)) {
            notifyChange();
        }
        return this;
    }

    /**
     * 获取长度变量值。
     *
     * @param name 变量名
     * @return 长度值；变量不存在时返回 null
     */
    public UiStyleLength getLength(String name) {
        return lengths.get(name);
    }

    /**
     * 获取长度变量值（带默认值）。
     *
     * @param name 变量名
     * @param defaultValue 变量不存在时的默认值
     * @return 长度值
     */
    public UiStyleLength getLength(String name, UiStyleLength defaultValue) {
        UiStyleLength value = lengths.get(name);
        return value != null ? value : defaultValue;
    }

    /**
     * 判断长度变量是否存在。
     *
     * @param name 变量名
     * @return 是否存在
     */
    public boolean hasLength(String name) {
        return lengths.containsKey(name);
    }

    /**
     * 移除长度变量。
     *
     * @param name 变量名
     * @return 当前容器（链式调用）
     */
    public UiStyleVariables removeLength(String name) {
        if (lengths.remove(name) != null) {
            notifyChange();
        }
        return this;
    }

    // ========== 字符串变量 ==========

    /**
     * 设置字符串变量。
     *
     * @param name 变量名
     * @param value 字符串值
     * @return 当前容器（链式调用）
     */
    public UiStyleVariables setString(String name, String value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        String previous = strings.put(name, value);
        if (!value.equals(previous)) {
            notifyChange();
        }
        return this;
    }

    /**
     * 获取字符串变量值。
     *
     * @param name 变量名
     * @return 字符串值；变量不存在时返回 null
     */
    public String getString(String name) {
        return strings.get(name);
    }

    /**
     * 获取字符串变量值（带默认值）。
     *
     * @param name 变量名
     * @param defaultValue 变量不存在时的默认值
     * @return 字符串值
     */
    public String getString(String name, String defaultValue) {
        String value = strings.get(name);
        return value != null ? value : defaultValue;
    }

    // ========== 批量操作 ==========

    /**
     * 返回所有颜色变量的只读视图。
     *
     * @return 颜色变量映射
     */
    public Map<String, Integer> getColors() {
        return Collections.unmodifiableMap(colors);
    }

    /**
     * 返回所有长度变量的只读视图。
     *
     * @return 长度变量映射
     */
    public Map<String, UiStyleLength> getLengths() {
        return Collections.unmodifiableMap(lengths);
    }

    /**
     * 返回所有字符串变量的只读视图。
     *
     * @return 字符串变量映射
     */
    public Map<String, String> getStrings() {
        return Collections.unmodifiableMap(strings);
    }

    /**
     * 清除所有变量。
     *
     * @return 当前容器（链式调用）
     */
    public UiStyleVariables clear() {
        boolean hadValues = !colors.isEmpty() || !lengths.isEmpty() || !strings.isEmpty();
        colors.clear();
        lengths.clear();
        strings.clear();
        if (hadValues) {
            notifyChange();
        }
        return this;
    }

    /**
     * 从另一个变量容器复制所有变量（覆盖同名变量）。
     *
     * @param source 源变量容器
     * @return 当前容器（链式调用）
     */
    public UiStyleVariables copyFrom(UiStyleVariables source) {
        Objects.requireNonNull(source, "source");
        colors.putAll(source.colors);
        lengths.putAll(source.lengths);
        strings.putAll(source.strings);
        notifyChange();
        return this;
    }

    private void notifyChange() {
        if (changeCallback != null) {
            changeCallback.run();
        }
    }

    @Override
    public String toString() {
        int total = colors.size() + lengths.size() + strings.size();
        return "UiStyleVariables[" + total + " variables]";
    }
}
