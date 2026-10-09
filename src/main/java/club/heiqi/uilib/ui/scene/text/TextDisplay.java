package club.heiqi.uilib.ui.scene.text;

/** 字体显示与编辑方向分析共用的字符投影，不写回业务源文或改变来源映射。 */
public final class TextDisplay {
    private TextDisplay() {}
    public static boolean controlMarker(int codePoint) {
        return codePoint < 32 && codePoint != 9 && codePoint != 10 && codePoint != 13
            || codePoint >= 0x7f && codePoint <= 0x9f;
    }
    public static int codePoint(int codePoint) {
        if(codePoint == 13 || codePoint == 0x2028 || codePoint == 0x2029) return 10;
        if(controlMarker(codePoint)) return codePoint < 32 ? 0x2400 + codePoint : codePoint == 127 ? 0x2421 : 0x2426;
        return codePoint;
    }
    /** 多行编辑已规范化 CRLF；此等长投影保留 Line.logical 的 UTF16 坐标。 */
    public static String directionText(String text) {
        StringBuilder projected = new StringBuilder(text.length());
        for(int offset=0;offset<text.length();){int cp=text.codePointAt(offset);offset+=Character.charCount(cp);projected.appendCodePoint(codePoint(cp));}
        return projected.toString();
    }
}
