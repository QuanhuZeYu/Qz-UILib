package club.heiqi.uilib.ui.text;

import java.util.List;

import club.heiqi.uilib.ui.style.props.UiFontStyle;
import club.heiqi.uilib.ui.style.props.UiFontWeight;

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
     * 获取指定解析模式下的字符串宽度。
     *
     * <p>默认实现回落到不区分模式的旧接口，方便测试替身和旧实现按需渐进升级。</p>
     *
     * @param text 文本内容
     * @param textContentMode 文本内容解析模式
     * @return 原始文本宽度
     */
    default int getStringWidth(String text, TextContentMode textContentMode) {
        return getStringWidth(text);
    }

    /**
     * 获取指定解析模式和字体样式下的字符串宽度。
     *
     * <p>默认实现回落到不区分字体样式的测量逻辑，方便现有测试替身保持兼容。</p>
     *
     * @param text 文本内容
     * @param textContentMode 文本内容解析模式
     * @param fontWeight 字体粗细
     * @param fontStyle 字体样式
     * @return 原始文本宽度
     */
    default int getStringWidth(String text, TextContentMode textContentMode, UiFontWeight fontWeight,
            UiFontStyle fontStyle) {
        return getStringWidth(text, textContentMode);
    }

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
     * 按目标宽度裁剪指定解析模式下的字符串。
     *
     * <p>默认实现回落到不区分模式的旧接口，方便测试替身和旧实现按需渐进升级。</p>
     *
     * @param text 文本内容
     * @param targetWidth 目标宽度
     * @param textContentMode 文本内容解析模式
     * @return 裁剪后的字符串
     */
    default String trimStringToWidth(String text, int targetWidth, TextContentMode textContentMode) {
        return trimStringToWidth(text, targetWidth);
    }

    /**
     * 按目标宽度裁剪指定解析模式和字体样式下的字符串。
     *
     * <p>默认实现回落到不区分字体样式的旧接口。</p>
     *
     * @param text 文本内容
     * @param targetWidth 目标宽度
     * @param textContentMode 文本内容解析模式
     * @param fontWeight 字体粗细
     * @param fontStyle 字体样式
     * @return 裁剪后的字符串
     */
    default String trimStringToWidth(String text, int targetWidth, TextContentMode textContentMode,
            UiFontWeight fontWeight, UiFontStyle fontStyle) {
        return trimStringToWidth(text, targetWidth, textContentMode);
    }

    /**
     * 按目标宽度拆分字符串列表。
     *
     * @param text 文本内容
     * @param wrapWidth 换行宽度
     * @return 拆分后的多行文本
     */
    List<String> listFormattedStringToWidth(String text, int wrapWidth);

    /**
     * 按目标宽度拆分指定解析模式下的字符串列表。
     *
     * <p>默认实现回落到不区分模式的旧接口，方便测试替身和旧实现按需渐进升级。</p>
     *
     * @param text 文本内容
     * @param wrapWidth 换行宽度
     * @param textContentMode 文本内容解析模式
     * @return 拆分后的多行文本
     */
    default List<String> listFormattedStringToWidth(String text, int wrapWidth, TextContentMode textContentMode) {
        return listFormattedStringToWidth(text, wrapWidth);
    }
}
