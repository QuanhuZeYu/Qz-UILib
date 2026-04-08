package club.heiqi.qz_uilib.fontsystem;

/**
 * 专为渲染字符存储信息
 */
public final class RenderInfo {
    public final int codepoint;
    public final CharPage page;
    public final CharInfo charInfo;

    public RenderInfo(int codepoint, CharPage page, CharInfo charInfo) {
        this.codepoint = codepoint;
        this.page = page;
        this.charInfo = charInfo;
    }
}
