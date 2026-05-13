package club.heiqi.uilib.font.util;

import java.awt.Font;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 字体排序规划结果。
 */
public final class FontOrderSnapshot {

    private final List<Font> orderedFonts;
    private final String[] resolvedFontNames;
    private final String[] missingConfiguredFontNames;

    FontOrderSnapshot(List<Font> orderedFonts, List<String> resolvedFontNames,
            List<String> missingConfiguredFontNames) {
        this.orderedFonts = Collections.unmodifiableList(new ArrayList<Font>(orderedFonts));
        this.resolvedFontNames = resolvedFontNames.toArray(new String[resolvedFontNames.size()]);
        this.missingConfiguredFontNames = missingConfiguredFontNames
                .toArray(new String[missingConfiguredFontNames.size()]);
    }

    /**
     * 返回按优先级排好的字体列表。
     *
     * @return 排序后的字体列表
     */
    public List<Font> getOrderedFonts() {
        return orderedFonts;
    }

    /**
     * 返回可写回配置的字体顺序名称。
     *
     * @return 字体名称列表
     */
    public String[] getResolvedFontNames() {
        return resolvedFontNames.clone();
    }

    /**
     * 返回本次配置中未能找到的字体名称。
     *
     * @return 缺失字体名称列表
     */
    public String[] getMissingConfiguredFontNames() {
        return missingConfiguredFontNames.clone();
    }
}
