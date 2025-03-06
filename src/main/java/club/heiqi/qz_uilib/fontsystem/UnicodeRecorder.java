package club.heiqi.qz_uilib.fontsystem;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UnicodeRecorder {
    public static Map<String, List> CATE = new HashMap<>();
    public static final int ENG_START = 0x0020;
    public static final int ENG_END = 0x007F;
    public static final int ENG_EXT_START = 0x0080;
    public static final int ENG_EXT_END = 0x00FF;

    public static final int CH_U_START = 0x4E00;
    public static final int CH_U_END = 0x9FFF;

    public static final int MATH_START = 0x2200;
    public static final int MATH_END = 0x22FF;

    public static final int CURR_START = 0x20A0;
    public static final int CURR_END = 0x20CF;

    public static int ARROW_START = 0x2190;   // 修正命名歧义（原 ARRAY 改为 ARROW）
    public static int ARROW_END = 0x21FF;

    public static final int MISC_START = 0x2600;
    public static final int MISC_END = 0x26FF;

    public static final int EMOJI_START = 0x1F600;
    public static final int EMOJI_END = 0x1F64F;
    public static final int EMOJI_EXT_START = 0x1F900;
    public static final int EMOJI_EXT_END = 0x1F9FF;

    public static final int TECH_START = 0x2300;
    public static final int TECH_END = 0x23FF;

    public static final int GENERAL_PUNCTUATION_START = 0x2000;
    public static final int GENERAL_PUNCTUATION_END = 0x206F;

    public static final int MISC_SYMBOL_START = 0x2700;
    public static final int MISC_SYMBOL_END = 0x27BF;

    public static final int JP_HIR_START = 0x3040;
    public static final int JP_HIR_END = 0x309F;
    public static final int JP_KATA_START = 0x30A0;
    public static final int JP_KATA_END = 0x30FF;
    public static final int JP_HA_KATA_START = 0xFF65;
    public static final int JP_HA_KATA_END = 0xFF9F;

    public static final int UNICODE_EXT_START = 0x1FA80;
    public static final int UNICODE_EXT_END = 0x1FAFF;

    static {
        // 手动按起始码点从小到大插入
        addRange(UnicodeType.英文.类型名, ENG_START, ENG_END);                    // 0x0020
        addRange(UnicodeType.英文扩展.类型名, ENG_EXT_START, ENG_EXT_END);        // 0x0080
        addRange(UnicodeType.通用标点符号.类型名, GENERAL_PUNCTUATION_START, GENERAL_PUNCTUATION_END); // 0x2000~0x206F
        addRange("货币符号", CURR_START, CURR_END);              // 0x20A0
        addRange("箭头符号", ARROW_START, ARROW_END);            // 0x2190
        addRange("数学符号", MATH_START, MATH_END);              // 0x2200
        addRange("技术符号", TECH_START, TECH_END);              // 0x2300
        addRange("其他符号", MISC_START, MISC_END);              // 0x2600
        addRange("其他符号2", MISC_SYMBOL_START, MISC_SYMBOL_END); // 0x2700
        addRange("平假名", JP_HIR_START, JP_HIR_END);              // 0x3040
        addRange("片假名", JP_KATA_START, JP_KATA_END);            // 0x30A0
        addRange("中文Unicode", CH_U_START, CH_U_END);            // 0x4E00
        addRange("半角片假名", JP_HA_KATA_START, JP_HA_KATA_END);    // 0xFF65
        addRange("表情符号", EMOJI_START, EMOJI_END);               // 0x1F600
        addRange("扩展表情符号", EMOJI_EXT_START, EMOJI_EXT_END);     // 0x1F900
        addRange("Unicode扩展", UNICODE_EXT_START, UNICODE_EXT_END); // 0x1FA80
    }

    public enum UnicodeType {
        英文("英文"),
        英文扩展("英文扩展"),
        通用标点符号("通用标点符号"),
        货币符号("货币符号"),
        箭头符号("箭头符号"),
        数学符号("数学符号"),
        技术符号("技术符号"),
        其他符号("其他符号"),
        其他符号2("其他符号2"),
        平假名("平假名"),
        片假名("片假名"),
        中文Unicode("中文Unicode"),
        半角片假名("半角片假名"),
        表情符号("表情符号"),
        扩展表情符号("扩展表情符号"),
        Unicode扩展("Unicode扩展"),
        ;
        final String 类型名;
        UnicodeType(String typeName) {
            this.类型名 = typeName;
        }
    }

    /**
     * 辅助方法：确保范围顺序正确
     */
    private static void addRange(String category, int start, int end) {
        CATE.put(category, Arrays.asList(
                Math.min(start, end),
                Math.max(start, end),
                true
        ));
    }
}
