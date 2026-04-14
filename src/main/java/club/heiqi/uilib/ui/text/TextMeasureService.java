package club.heiqi.uilib.ui.text;

import java.util.List;

/**
 * UI 侧文本测量服务抽象。
 *
 * <p>该接口只承载最小测量能力，用于隔离控件对具体字体实现与运行时版本号的直接依赖。</p>
 */
public interface TextMeasureService {

    /**
     * 获取当前文本测量纪元。
     *
     * <p>当底层字体运行时发生变化时，应返回新的纪元值，以驱动控件缓存失效。</p>
     *
     * @return 当前测量纪元
     */
    int getEpoch();

    /**
     * 获取字符串宽度。
     *
     * @param text 文本内容
     * @return 原始文本宽度
     */
    int getStringWidth(String text);

    /**
     * 获取原始文本坐标系下的逻辑行高。
     *
     * <p>该值不包含 UI 层缩放，供布局阶段作为统一的单行高度来源。</p>
     *
     * @return 原始文本行高
     */
    int getLineHeight();

    /**
     * 按目标宽度裁剪字符串。
     *
     * @param text 文本内容
     * @param targetWidth 目标宽度
     * @return 裁剪后的字符串
     */
    String trimStringToWidth(String text, int targetWidth);

    /**
     * 按目标宽度拆分字符串列表。
     *
     * @param text 文本内容
     * @param wrapWidth 换行宽度
     * @return 拆分后的多行文本
     */
    List<String> listFormattedStringToWidth(String text, int wrapWidth);
}
