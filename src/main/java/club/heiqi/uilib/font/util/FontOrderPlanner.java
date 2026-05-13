package club.heiqi.uilib.font.util;

import java.awt.Font;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 字体顺序规划器。
 */
public final class FontOrderPlanner {

    /**
     * 基于当前已发现字体和配置顺序生成排序快照。
     *
     * <p>配置中不存在的字体会只保留在缺失状态里，不参与当前有效排序。</p>
     *
     * @param discoveredFonts 已发现字体
     * @param configuredOrder 配置中的顺序提示
     * @return 排序快照
     */
    public FontOrderSnapshot plan(List<Font> discoveredFonts, String[] configuredOrder) {
        List<Font> resolvedFonts = new ArrayList<Font>();
        List<String> resolvedNames = new ArrayList<String>();
        List<String> missingNames = new ArrayList<String>();

        if (discoveredFonts == null || discoveredFonts.isEmpty()) {
            return new FontOrderSnapshot(resolvedFonts, resolvedNames, missingNames);
        }

        Map<String, FontGroup> discoveredGroups = groupFonts(discoveredFonts);
        Set<String> consumedKeys = new HashSet<String>();
        Set<String> missingKeys = new HashSet<String>();

        String[] configuredNames = normalizeConfiguredNames(configuredOrder);
        for (String configuredName : configuredNames) {
            String lookupKey = normalizeKey(configuredName);
            FontGroup group = discoveredGroups.get(lookupKey);
            if (group == null) {
                if (missingKeys.add(lookupKey)) {
                    missingNames.add(configuredName);
                }
                continue;
            }
            if (!consumedKeys.add(lookupKey)) {
                continue;
            }
            resolvedFonts.addAll(group.fonts);
            resolvedNames.add(group.displayName);
        }

        List<FontGroup> remainingGroups = new ArrayList<FontGroup>(discoveredGroups.values());
        Collections.sort(remainingGroups, new java.util.Comparator<FontGroup>() {
            @Override
            public int compare(FontGroup left, FontGroup right) {
                return compareNaturalNames(left.displayName, right.displayName);
            }
        });
        for (FontGroup group : remainingGroups) {
            if (!consumedKeys.add(group.lookupKey)) {
                continue;
            }
            resolvedFonts.addAll(group.fonts);
            resolvedNames.add(group.displayName);
        }

        return new FontOrderSnapshot(resolvedFonts, resolvedNames, missingNames);
    }

    private static Map<String, FontGroup> groupFonts(List<Font> fonts) {
        Map<String, FontGroup> discoveredGroups = new LinkedHashMap<String, FontGroup>();
        for (Font font : fonts) {
            if (font == null) {
                continue;
            }
            String displayName = normalizeDisplayName(font.getName());
            if (displayName.isEmpty()) {
                continue;
            }
            String lookupKey = normalizeKey(displayName);
            FontGroup group = discoveredGroups.get(lookupKey);
            if (group == null) {
                group = new FontGroup(lookupKey, displayName);
                discoveredGroups.put(lookupKey, group);
            }
            group.fonts.add(font);
        }
        return discoveredGroups;
    }

    private static String[] normalizeConfiguredNames(String[] configuredOrder) {
        if (configuredOrder == null || configuredOrder.length == 0) {
            return new String[0];
        }
        List<String> normalizedNames = new ArrayList<String>(configuredOrder.length);
        Set<String> seenKeys = new HashSet<String>();
        for (String configuredName : configuredOrder) {
            String resolvedName = normalizeDisplayName(configuredName);
            if (resolvedName.isEmpty()) {
                continue;
            }
            String lookupKey = normalizeKey(resolvedName);
            if (!seenKeys.add(lookupKey)) {
                continue;
            }
            normalizedNames.add(resolvedName);
        }
        return normalizedNames.toArray(new String[normalizedNames.size()]);
    }

    private static String normalizeDisplayName(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeKey(String value) {
        return normalizeDisplayName(value).toLowerCase(Locale.ENGLISH);
    }

    private static int compareNaturalNames(String left, String right) {
        String resolvedLeft = left == null ? "" : left;
        String resolvedRight = right == null ? "" : right;
        int leftIndex = 0;
        int rightIndex = 0;

        while (leftIndex < resolvedLeft.length() && rightIndex < resolvedRight.length()) {
            char leftChar = resolvedLeft.charAt(leftIndex);
            char rightChar = resolvedRight.charAt(rightIndex);
            if (Character.isDigit(leftChar) && Character.isDigit(rightChar)) {
                int leftEnd = findDigitRunEnd(resolvedLeft, leftIndex);
                int rightEnd = findDigitRunEnd(resolvedRight, rightIndex);
                int numericComparison = compareDigitRuns(resolvedLeft, leftIndex, leftEnd, resolvedRight, rightIndex,
                        rightEnd);
                if (numericComparison != 0) {
                    return numericComparison;
                }
                leftIndex = leftEnd;
                rightIndex = rightEnd;
                continue;
            }

            int charComparison = Character.compare(Character.toLowerCase(leftChar), Character.toLowerCase(rightChar));
            if (charComparison != 0) {
                return charComparison;
            }
            leftIndex++;
            rightIndex++;
        }
        return Integer.compare(resolvedLeft.length(), resolvedRight.length());
    }

    private static int findDigitRunEnd(String value, int startIndex) {
        int index = startIndex;
        while (index < value.length() && Character.isDigit(value.charAt(index))) {
            index++;
        }
        return index;
    }

    private static int compareDigitRuns(String left, int leftStart, int leftEnd, String right, int rightStart,
            int rightEnd) {
        int normalizedLeftStart = skipLeadingZeros(left, leftStart, leftEnd);
        int normalizedRightStart = skipLeadingZeros(right, rightStart, rightEnd);
        int normalizedLeftLength = leftEnd - normalizedLeftStart;
        int normalizedRightLength = rightEnd - normalizedRightStart;
        if (normalizedLeftLength != normalizedRightLength) {
            return Integer.compare(normalizedLeftLength, normalizedRightLength);
        }
        for (int index = 0; index < normalizedLeftLength; index++) {
            char leftChar = left.charAt(normalizedLeftStart + index);
            char rightChar = right.charAt(normalizedRightStart + index);
            if (leftChar != rightChar) {
                return Character.compare(leftChar, rightChar);
            }
        }
        return Integer.compare(leftEnd - leftStart, rightEnd - rightStart);
    }

    private static int skipLeadingZeros(String value, int start, int end) {
        int index = start;
        while (index < end - 1 && value.charAt(index) == '0') {
            index++;
        }
        return index;
    }

    private static final class FontGroup {

        private final String lookupKey;
        private final String displayName;
        private final List<Font> fonts = new ArrayList<Font>();

        private FontGroup(String lookupKey, String displayName) {
            this.lookupKey = lookupKey;
            this.displayName = displayName;
        }
    }
}
