package club.heiqi.qz_uilib.fontsystem;

import static club.heiqi.qz_uilib.fontsystem.CharPage.PAGE_SIZE;
import static club.heiqi.qz_uilib.fontsystem.FontManager.FONT_PIXEL_SIZE;

public class CharInfo {
    public CharPage page;
    public int index;

    public CharInfo(CharPage page, int index) {
        this.page = page; this.index = index;
    }

    public double getU1() {
        // 左上角坐标
        int 每行数 = PAGE_SIZE / FONT_PIXEL_SIZE;
        // 计算行列时使用 charCounter
        int 行 = index / 每行数;
        int 列 = index % 每行数;
        int x = 列 * FONT_PIXEL_SIZE;
        int y = 行 * FONT_PIXEL_SIZE;
        return (double) x / PAGE_SIZE;
    }

    public double getU2() {
        // 左上角坐标
        int 每行数 = PAGE_SIZE / FONT_PIXEL_SIZE;
        int 行 = index / 每行数;
        int 列 = index % 每行数;
        int x = 列 * FONT_PIXEL_SIZE;
        int y = 行 * FONT_PIXEL_SIZE;
        return (double) (x + FONT_PIXEL_SIZE) / PAGE_SIZE;
    }

    public double getV1() {
        // 左上角坐标
        int 每行数 = PAGE_SIZE / FONT_PIXEL_SIZE;
        int 行 = index / 每行数;
        int 列 = index % 每行数;
        int x = 列 * FONT_PIXEL_SIZE;
        int y = 行 * FONT_PIXEL_SIZE;
        return y;
    }

    public double getV2() {
        // 左上角坐标
        int 每行数 = PAGE_SIZE / FONT_PIXEL_SIZE;
        int 行 = index / 每行数;
        int 列 = index % 每行数;
        int x = 列 * FONT_PIXEL_SIZE;
        int y = 行 * FONT_PIXEL_SIZE;
        return (double) (y + FONT_PIXEL_SIZE) / PAGE_SIZE;
    }
}
