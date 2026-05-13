package club.heiqi.uilib.font.util;

import java.awt.Font;
import java.util.List;

/**
 * 字体排序器。
 */
public class FontSorter {

    private final FontOrderPlanner fontOrderPlanner = new FontOrderPlanner();

    /**
     * 按目标名称顺序重排字体列表。
     *
     * @param fonts 原始字体列表
     * @param fontSort 排序目标
     * @return 排序结果
     */
    public List<Font> sort(List<Font> fonts, String[] fontSort) {
        return fontOrderPlanner.plan(fonts, fontSort).getOrderedFonts();
    }
}
