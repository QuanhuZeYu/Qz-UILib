package club.heiqi.qz_uilib.fontsystem;

public class CharInfo {
    public final FontTexturePage page; // 这个字体是在哪一页
    /** 左上角坐标 */
    public final int x, y; // 字符在此页的x，y坐标 左上角
    public final int beginX, endX;

    public CharInfo(FontTexturePage page, int x, int y, int beginX, int endX) {
        this.page = page;
        this.x = x;
        this.y = y;
        this.beginX = beginX;
        this.endX = endX;
    }

    public double getU1() {
        return (double) x / FontTexturePage.TEXTURE_SIZE;
    }

    public double getU2() {
        return (double) (x + FontTexturePage.FONT_PIXEL_SIZE) / FontTexturePage.TEXTURE_SIZE;
    }

    public double getV1() {
        return (double) y / FontTexturePage.TEXTURE_SIZE;
    }

    public double getV2() {
        return (double) (y + FontTexturePage.FONT_PIXEL_SIZE) / FontTexturePage.TEXTURE_SIZE;
    }

    public int getWidth() {
        return endX;
    }
}
