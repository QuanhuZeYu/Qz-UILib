package club.heiqi.qz_uilib.fontsystem;

import static club.heiqi.qz_uilib.fontsystem.CharPage.GRID_SIZE;
import static club.heiqi.qz_uilib.fontsystem.CharPage.PAGE_SIZE;

public class CharInfo {
    public final CharPage page;
    public final short index;
    public final float width, height; // 字符的宽高
    public CharInfo(CharPage cp, short i, float w, float h) {
        page=cp; index=i; width=w; height=h;
    }

    public double getU1() { // 左U
        int 每行个数 = PAGE_SIZE/GRID_SIZE;
        int 行 = index/每行个数;
        int 列 = index%每行个数;
        int x = 列*GRID_SIZE; // 左上角X
        int y = 行*GRID_SIZE; // 左上角Y
        return (double) x/*+(GRID_SIZE*0.2)*//PAGE_SIZE;
    }

    public double getU2() {
        int 每行个数 = PAGE_SIZE/GRID_SIZE;
        int 行 = index/每行个数;
        int 列 = index%每行个数;
        int x = 列*GRID_SIZE; // 左上角X
        int y = 行*GRID_SIZE; // 左上角Y
        return (double) (x+GRID_SIZE)/*+(GRID_SIZE*0.2)*//PAGE_SIZE;
    }

    public double getV2() { // 上V
        int 每行个数 = PAGE_SIZE/GRID_SIZE;
        int 行 = index/每行个数;
        int 列 = index%每行个数;
        int x = 列*GRID_SIZE; // 左上角X
        int y = 行*GRID_SIZE; // 左上角Y
        return (double) (y+GRID_SIZE)/PAGE_SIZE;
    }

    public double getV1() {
        int 每行个数 = PAGE_SIZE/GRID_SIZE;
        int 行 = index/每行个数;
        int 列 = index%每行个数;
        int x = 列*GRID_SIZE; // 左上角X
        int y = 行*GRID_SIZE; // 左上角Y
        return (double) (y)/PAGE_SIZE;
    }
}
