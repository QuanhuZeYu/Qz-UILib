package club.heiqi.uilib.font.util;

import java.awt.Font;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 字体排序器。
 */
public class FontSorter {

    /**
     * 按目标名称顺序重排字体列表。
     *
     * @param fonts 原始字体列表
     * @param fontSort 排序目标
     * @return 排序结果
     */
    public List<Font> sort(List<Font> fonts, String[] fontSort) {
        if (fontSort == null || fontSort.length == 0) {
            return new ArrayList<Font>(fonts);
        }

        Map<String, Integer> order = new HashMap<String, Integer>();
        for (int i = 0; i < fontSort.length; i++) {
            order.put(fontSort[i].toLowerCase(), Integer.valueOf(i + 1));
        }

        List<Font> result = new ArrayList<Font>(fonts);
        result.sort((left, right) -> {
            int leftOrder = order.containsKey(left.getName().toLowerCase()) ? order.get(left.getName().toLowerCase()).intValue() : Integer.MAX_VALUE;
            int rightOrder = order.containsKey(right.getName().toLowerCase()) ? order.get(right.getName().toLowerCase()).intValue() : Integer.MAX_VALUE;

            if (leftOrder != rightOrder) {
                return Integer.compare(leftOrder, rightOrder);
            }
            return left.getName().compareToIgnoreCase(right.getName());
        });
        return result;
    }
}
