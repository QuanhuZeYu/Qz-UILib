package club.heiqi.qz_uilib.fontsystem.utils.data;

public class EmojiRange {
    public static final int start = 0x1F000;
    public static final int end = 0x1FAFF;

    public static boolean isEmoji(int codepoint) {
        return codepoint >= start && codepoint <= end;
    }
}
