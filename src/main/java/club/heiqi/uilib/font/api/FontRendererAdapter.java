package club.heiqi.uilib.font.api;

import java.util.List;

/**
 * 字体渲染兼容接口。
 */
public interface FontRendererAdapter {

    /**
     * 绘制字符串。
     *
     * @param text 文本
     * @param x 横坐标
     * @param y 纵坐标
     * @param color 颜色
     * @param dropShadow 是否启用阴影
     * @return 绘制结束后的光标位置
     */
    int drawString(String text, int x, int y, int color, boolean dropShadow);

    /**
     * 按字体 atlas 基线对齐契约绘制字符串。
     *
     * <p>默认兼容实现仍转回旧入口，具体字体适配器可覆盖该方法暴露更精确的渲染语义。</p>
     *
     * @param text 文本
     * @param x 横坐标
     * @param y 纵坐标
     * @param color 颜色
     * @param dropShadow 是否启用阴影
     * @return 绘制结束后的光标位置
     */
    default int drawBaselineAlignedString(String text, int x, int y, int color, boolean dropShadow) {
        return drawString(text, x, y, color, dropShadow);
    }

    /**
     * 测量字符串宽度。
     *
     * @param text 文本
     * @return 宽度
     */
    int getStringWidth(String text);

    /**
     * 获取文本测量缓存失效纪元。
     *
     * <p>底层字体运行时（注册、匹配缓存或文本布局缓存）发生变化时该值递增，用于驱动上层文本布局缓存失效。
     * 默认实现返回 {@code 0}，表示测量不随时间变化（适用于确定性测试替身）。</p>
     *
     * @return 文本测量纪元
     */
    default int getTextMeasureEpoch() {
        return 0;
    }

    /**
     * 获取原始文本坐标系下的逻辑行高。
     *
     * @return 原始文本行高
     */
    int getLineHeight();

    /**
     * 按宽度裁剪文本。
     *
     * @param text 文本
     * @param targetWidth 目标宽度
     * @return 裁剪结果
     */
    String trimStringToWidth(String text, int targetWidth);

    /**
     * 按宽度插入换行。
     *
     * @param text 文本
     * @param wrapWidth 最大宽度
     * @return 包含换行的新文本
     */
    String wrapFormattedStringToWidth(String text, int wrapWidth);

    /**
     * 按宽度拆分格式化文本。
     *
     * @param text 文本
     * @param wrapWidth 最大宽度
     * @return 拆分结果
     */
    List<String> listFormattedStringToWidth(String text, int wrapWidth);

    /**
     * 计算拆行后的高度。
     *
     * @param text 文本
     * @param wrapWidth 最大宽度
     * @return 高度
     */
    int splitStringWidth(String text, int wrapWidth);
}
