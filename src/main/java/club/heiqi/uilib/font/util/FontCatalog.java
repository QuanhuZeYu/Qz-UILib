package club.heiqi.uilib.font.util;

import java.awt.Font;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 字体目录快照。
 */
public class FontCatalog {

    private volatile List<Font> fonts = Collections.emptyList();

    /**
     * 使用新字体列表替换当前目录。
     *
     * @param updatedFonts 新字体列表
     */
    public void replaceAll(List<Font> updatedFonts) {
        if (updatedFonts == null || updatedFonts.isEmpty()) {
            fonts = Collections.emptyList();
            return;
        }
        fonts = Collections.unmodifiableList(new ArrayList<Font>(updatedFonts));
    }

    /**
     * 获取只读字体列表。
     *
     * @return 字体列表
     */
    public List<Font> getFonts() {
        return fonts;
    }

    /**
     * 按目录索引获取字体。
     *
     * @param index 字体索引
     * @return 字体，索引无效时返回 null
     */
    public Font getFont(int index) {
        List<Font> snapshot = fonts;
        if (index < 0 || index >= snapshot.size()) {
            return null;
        }
        return snapshot.get(index);
    }

    /**
     * 获取字体数量。
     *
     * @return 字体数量
     */
    public int size() {
        return fonts.size();
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
