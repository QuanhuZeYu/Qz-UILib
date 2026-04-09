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
     * 测量字符串宽度。
     *
     * @param text 文本
     * @return 宽度
     */
    int getStringWidth(String text);

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
