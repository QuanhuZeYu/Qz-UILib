package club.heiqi.qz_uilib.fontsystem;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UnicodeRecorder {
    public static Map<String, List<Integer>> CATE = new HashMap<>();
    public static int ENG_START = 0x0020;
    public static int ENG_END = 0x007F;
    public static int ENG_EXT_START = 0x0080;
    public static int ENG_EXT_END = 0x00FF;

    public static int CH_U_START = 0x4E00;
    public static int CH_U_END = 0x9FFF;

    public static int MATH_START = 0x2200;
    public static int MATH_END = 0x22FF;

    public static int CURR_START = 0x20A0;
    public static int CURR_END = 0x20CF;

    public static int ARRAY_START = 0x2190;
    public static int ARRAY_END = 0x21FF;

    public static int MISC_START = 0x2600;
    public static int MISC_END = 0x26FF;

    public static int EMOJI_START = 0x1F600;
    public static int EMOJI_END = 0x1F64F;
    public static int EMOJI_EXT_START = 0x1F900;
    public static int EMOJI_EXT_END = 0x1F9FF;

    public static int TECH_START = 0x2300;
    public static int TECH_END = 0x23FF;

    public static int TAB_SYMBOL_START = 0x2000;
    public static int TAB_SYMBOL_END = 0x200F;

    public static int MISC_SYMBOL_START = 0x2700;
    public static int MISC_SYMBOL_END = 0x27BF;

    public static int UNICODE_EXT_START = 0x1FA80;
    public static int UNICODE_EXT_END = 0x1FAFF;

    static {
        CATE.put("英文", Arrays.asList(ENG_START, ENG_END));
        CATE.put("英文扩展", Arrays.asList(ENG_EXT_START, ENG_EXT_END));
    }
}
