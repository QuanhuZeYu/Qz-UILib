package club.heiqi.uilib.ui.style.values;

import club.heiqi.uilib.ui.style.cascade.UiStyleDeclaration;

/**
 * CSS 级联关键字。
 *
 * <p>用于在 {@link UiStyleDeclaration} 中表达 {@code inherit}、{@code initial}
 * 与 {@code unset} 这类不依赖具体属性值类型的声明。</p>
 */
public enum UiStyleKeyword {

    /** 显式继承父元素的计算值；没有父元素时回退到属性初始值。 */
    INHERIT,

    /** 使用属性初始值，不继承父元素。 */
    INITIAL,

    /** 可继承属性等同于 inherit，不可继承属性等同于 initial。 */
    UNSET
}
