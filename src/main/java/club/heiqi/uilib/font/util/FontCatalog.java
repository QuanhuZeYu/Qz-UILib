package club.heiqi.uilib.font.util;

import java.awt.Font;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 字体目录快照。
 */
public class FontCatalog {

    private final List<Font> fonts = new ArrayList<Font>();

    /**
     * 使用新字体列表替换当前目录。
     *
     * @param updatedFonts 新字体列表
     */
    public void replaceAll(List<Font> updatedFonts) {
        fonts.clear();
        fonts.addAll(updatedFonts);
    }

    /**
     * 获取只读字体列表。
     *
     * @return 字体列表
     */
    public List<Font> getFonts() {
        return Collections.unmodifiableList(fonts);
    }

    /**
     * 判断是否为空。
     *
     * @return 是否为空
     */
    public boolean isEmpty() {
        return fonts.isEmpty();
    }
}
