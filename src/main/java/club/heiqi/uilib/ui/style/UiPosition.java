package club.heiqi.uilib.ui.style;

/**
 * HTML-like 元素定位模式。
 */
public enum UiPosition {
    /**
     * 默认普通流定位。
     */
    STATIC,

    /**
     * 保留普通流位置，仅在绘制与命中阶段按 inset 偏移。
     */
    RELATIVE,

    /**
     * 保留普通流位置，并在最近滚动容器内按 inset 粘滞到滚动视口边缘。
     */
    STICKY,

    /**
     * 脱离普通流，并相对最近 positioned ancestor 的 padding box 按 inset 定位；
     * 无 positioned ancestor 时回退到根元素 padding box。
     */
    ABSOLUTE,

    /**
     * 脱离普通流，并相对当前 HTML-like 视口按 inset 固定定位。
     */
    FIXED
}
