package club.heiqi.qz_uilib.fontsystem;

public class CharInfo {
    public final int codepoint;
    /**字符在纹理页中的坐标*/
    public int x, y;
    /**字符格像素大小*/
    public int width, height;
    /**字符前进量*/
    public final float advance;

    public CharInfo(int codepoint, int x, int y, int width, int height, float advance) {
        this.codepoint = codepoint;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.advance = advance;
    }

    public double getU0(int pageSize) {
        return (double) (x + 1) / pageSize;
    }
    public double getU1(int pageSize) {
        return (double) (x + width - 1) /pageSize;
    }
    public double getV0(int pageSize) {
        return (double) (y + 1) /pageSize;
    }
    public double getV1(int pageSize) {
        return (double) (y + height - 1) /pageSize;
    }

    @Override
    public int hashCode() {
        return codepoint;
    }
}
