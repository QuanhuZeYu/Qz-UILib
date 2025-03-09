package club.heiqi.qz_uilib.fontsystem;

import java.util.Arrays;
import java.util.List;

import static club.heiqi.qz_uilib.fontsystem.CharPage.PAGE_SIZE;
import static club.heiqi.qz_uilib.fontsystem.FontManager.FONT_PIXEL_SIZE;

public class CharInfo {
    public CharPage page;
    public short index;
    public short width, height;

    public CharInfo(CharPage page, short index, short width, short height) {
        this.page = page; this.index = index;
        this.width = width; this.height = height;
    }

    public double getU1() {
        int x = _calculateLT().get(0);
        return (double) x / PAGE_SIZE;
    }

    public double getU2() {
        int x = _calculateLT().get(0);
        return (double) (x + FONT_PIXEL_SIZE - 1) / PAGE_SIZE;
    }

    public double getV1() { // 翻转后的下
        int y = _calculateLT().get(1);
        return (double) (y) / PAGE_SIZE;
    }

    public double getV2() { // 翻转后的上
        int y = _calculateLT().get(1);
        y = y + FONT_PIXEL_SIZE - 1;
        return (double) (y) / PAGE_SIZE;
    }

    public List<Integer> _calculateLT() {
        if (index == 0) {
            int x = 0;
            int y = 0;
            return Arrays.asList(x, y);
        }
        // 左上角坐标
        int 每行数 = PAGE_SIZE / FONT_PIXEL_SIZE;
        int 行 = index / 每行数;
        int 列 = index % 每行数;
        int x = 列 * FONT_PIXEL_SIZE;
        int y = 行 * FONT_PIXEL_SIZE;
        return Arrays.asList(x, y);
    }
}
