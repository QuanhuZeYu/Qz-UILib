package club.heiqi.qz_uilib.fontsystem;

import static club.heiqi.qz_uilib.fontsystem.CharPage.PAGE_SIZE;

// 从左往右 从上往下 越来越大
public class CharInfo {
    public final CharPage page;
    public final float left, right, top, bottom;
    public CharInfo(CharPage cp, float l, float r, float t, float b) {
        page=cp; left = l; right = r; top = t; bottom = b;
    }

    public double getU1() { // 左U
        return (double) left/PAGE_SIZE;
    }

    public double getU2() {
        return (double) right /PAGE_SIZE;
    }

    public double getV1() { // 上V
        return (double) top/PAGE_SIZE;
    }

    public double getV2() {
        return (double) bottom/PAGE_SIZE;
    }
}
