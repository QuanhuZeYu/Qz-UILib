package club.heiqi.qz_uilib.fontsystem;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UnicodeRecorder {
    public static Map<UnicodeType, List<Integer>> CATEGORY_RANGES = new HashMap<>();

    // Unicode 区块定义（使用标准名称）
    public static final int BASIC_LATIN_START = 0x0020;
    public static final int BASIC_LATIN_END = 0x007E; // 修正结束码点

    public static final int LATIN1_SUPPLEMENT_START = 0x0080;
    public static final int LATIN1_SUPPLEMENT_END = 0x00FF;

    public static final int GENERAL_PUNCTUATION_START = 0x2000;
    public static final int GENERAL_PUNCTUATION_END = 0x206F;

    public static final int CURRENCY_SYMBOLS_START = 0x20A0;
    public static final int CURRENCY_SYMBOLS_END = 0x20CF;

    public static final int ARROWS_START = 0x2190;
    public static final int ARROWS_END = 0x21FF;

    public static final int MATHEMATICAL_OPERATORS_START = 0x2200;
    public static final int MATHEMATICAL_OPERATORS_END = 0x22FF;

    public static final int MISCELLANEOUS_TECHNICAL_START = 0x2300;
    public static final int MISCELLANEOUS_TECHNICAL_END = 0x23FF;

    public static final int GEOMETRIC_SHAPES_START = 0x25A0;
    public static final int GEOMETRIC_SHAPES_END = 0x25FF;

    public static final int MISCELLANEOUS_SYMBOLS_START = 0x2600;
    public static final int MISCELLANEOUS_SYMBOLS_END = 0x26FF;

    public static final int DINGBATS_START = 0x2700;
    public static final int DINGBATS_END = 0x27BF;

    public static final int HIRAGANA_START = 0x3040;
    public static final int HIRAGANA_END = 0x309F;
    public static final int KATAKANA_START = 0x30A0;
    public static final int KATAKANA_END = 0x30FF;

    public static final int CJK_UNIFIED_IDEOGRAPHS_START = 0x4E00;
    public static final int CJK_UNIFIED_IDEOGRAPHS_END = 0x9FFF;

    public static final int HALFWIDTH_KATAKANA_START = 0xFF65;
    public static final int HALFWIDTH_KATAKANA_END = 0xFF9F;

    // Emoji 相关区块细化
    public static final int MISCELLANEOUS_SYMBOLS_AND_PICTOGRAPHS_START = 0x1F300;
    public static final int MISCELLANEOUS_SYMBOLS_AND_PICTOGRAPHS_END = 0x1F5FF;
    public static final int EMOTICONS_START = 0x1F600;
    public static final int EMOTICONS_END = 0x1F64F;
    public static final int TRANSPORT_AND_MAP_SYMBOLS_START = 0x1F680;
    public static final int TRANSPORT_AND_MAP_SYMBOLS_END = 0x1F6FF;
    public static final int SUPPLEMENTAL_SYMBOLS_AND_PICTOGRAPHS_START = 0x1F900;
    public static final int SUPPLEMENTAL_SYMBOLS_AND_PICTOGRAPHS_END = 0x1F9FF;
    public static final int SYMBOLS_FOR_LEGACY_COMPUTING_START = 0x1FA80;
    public static final int SYMBOLS_FOR_LEGACY_COMPUTING_END = 0x1FAFF;

    static {
        // 按起始码点排序添加范围
        addRange(UnicodeType.BASIC_LATIN);
        addRange(UnicodeType.LATIN1_SUPPLEMENT);
        addRange(UnicodeType.GENERAL_PUNCTUATION);
        addRange(UnicodeType.CURRENCY_SYMBOLS);
        addRange(UnicodeType.ARROWS);
        addRange(UnicodeType.MATHEMATICAL_OPERATORS);
        addRange(UnicodeType.MISCELLANEOUS_TECHNICAL);
        addRange(UnicodeType.GEOMETRIC_SHAPES);
        addRange(UnicodeType.MISCELLANEOUS_SYMBOLS);
        addRange(UnicodeType.DINGBATS);
        addRange(UnicodeType.HIRAGANA);
        addRange(UnicodeType.KATAKANA);
        addRange(UnicodeType.CJK_UNIFIED_IDEOGRAPHS);
        addRange(UnicodeType.HALFWIDTH_KATAKANA);
        addRange(UnicodeType.MISCELLANEOUS_SYMBOLS_AND_PICTOGRAPHS);
        addRange(UnicodeType.EMOTICONS);
        addRange(UnicodeType.TRANSPORT_AND_MAP_SYMBOLS);
        addRange(UnicodeType.SUPPLEMENTAL_SYMBOLS_AND_PICTOGRAPHS);
        addRange(UnicodeType.SYMBOLS_FOR_LEGACY_COMPUTING);
    }

    public enum UnicodeType {
        BASIC_LATIN("基本拉丁字母", BASIC_LATIN_START, BASIC_LATIN_END),
        LATIN1_SUPPLEMENT("拉丁字母补充-1", LATIN1_SUPPLEMENT_START, LATIN1_SUPPLEMENT_END),
        GENERAL_PUNCTUATION("通用标点符号", GENERAL_PUNCTUATION_START, GENERAL_PUNCTUATION_END),
        CURRENCY_SYMBOLS("货币符号", CURRENCY_SYMBOLS_START, CURRENCY_SYMBOLS_END),
        ARROWS("箭头符号", ARROWS_START, ARROWS_END),
        MATHEMATICAL_OPERATORS("数学运算符", MATHEMATICAL_OPERATORS_START, MATHEMATICAL_OPERATORS_END),
        MISCELLANEOUS_TECHNICAL("技术符号", MISCELLANEOUS_TECHNICAL_START, MISCELLANEOUS_TECHNICAL_END),
        GEOMETRIC_SHAPES("几何形状", GEOMETRIC_SHAPES_START, GEOMETRIC_SHAPES_END),
        MISCELLANEOUS_SYMBOLS("杂项符号", MISCELLANEOUS_SYMBOLS_START, MISCELLANEOUS_SYMBOLS_END),
        DINGBATS("装饰符号", DINGBATS_START, DINGBATS_END),
        HIRAGANA("平假名", HIRAGANA_START, HIRAGANA_END),
        KATAKANA("片假名", KATAKANA_START, KATAKANA_END),
        CJK_UNIFIED_IDEOGRAPHS("中日韩统一表意文字", CJK_UNIFIED_IDEOGRAPHS_START, CJK_UNIFIED_IDEOGRAPHS_END),
        HALFWIDTH_KATAKANA("半角片假名", HALFWIDTH_KATAKANA_START, HALFWIDTH_KATAKANA_END),
        MISCELLANEOUS_SYMBOLS_AND_PICTOGRAPHS("杂项符号和象形图", MISCELLANEOUS_SYMBOLS_AND_PICTOGRAPHS_START, MISCELLANEOUS_SYMBOLS_AND_PICTOGRAPHS_END),
        EMOTICONS("表情符号", EMOTICONS_START, EMOTICONS_END),
        TRANSPORT_AND_MAP_SYMBOLS("交通与地图符号", TRANSPORT_AND_MAP_SYMBOLS_START, TRANSPORT_AND_MAP_SYMBOLS_END),
        SUPPLEMENTAL_SYMBOLS_AND_PICTOGRAPHS("补充符号与象形图", SUPPLEMENTAL_SYMBOLS_AND_PICTOGRAPHS_START, SUPPLEMENTAL_SYMBOLS_AND_PICTOGRAPHS_END),
        SYMBOLS_FOR_LEGACY_COMPUTING("传统计算符号", SYMBOLS_FOR_LEGACY_COMPUTING_START, SYMBOLS_FOR_LEGACY_COMPUTING_END);

        final String displayName;
        final int start;
        final int end;
        UnicodeType(String displayName, int start, int end) {
            this.displayName = displayName;
            this.start = start;
            this.end = end;
        }

        public String getName() {
            return displayName;
        }
    }

    /**
     * 辅助方法：确保范围顺序正确
     */
    private static void addRange(UnicodeType type) {
        CATEGORY_RANGES.put(type, Arrays.asList(
                type.start,
                type.end));
    }

    public static final String NEED_LEFT = "qwertyuiopasdfghjklzxcvbnm"+ "!@#$%^&*()-=_+`~[]\\{}|;':\",./<>?" +
        "123456789";

    /**
     * 需要向左贴到0位置的集合方法判断，需要贴到左侧则返回true
     * @param t
     * @return
     */
    public static boolean needLeft(String t) {
        if (NEED_LEFT.contains(t)) return true;
        int codepoint = t.codePointAt(0);

        if (codepoint >= GEOMETRIC_SHAPES_START && codepoint <= GEOMETRIC_SHAPES_END) return true;
        if (codepoint >= MISCELLANEOUS_SYMBOLS_START && codepoint <= MISCELLANEOUS_SYMBOLS_END) return true;
        if (codepoint >= DINGBATS_START && codepoint <= DINGBATS_END) return true;
        if (codepoint >= CJK_UNIFIED_IDEOGRAPHS_START && codepoint <= CJK_UNIFIED_IDEOGRAPHS_END) return true;

        return false;
    }
}
