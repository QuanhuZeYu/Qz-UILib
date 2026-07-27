package club.heiqi.uilib.ui.scene.layout;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** 固定列 Grid 整数数学测试。 */
public class GridLayoutMathTest {

    /** 可整除宽度应生成等宽轨道与稳定起点。 */
    @Test
    public void divisibleWidth_hasEqualTracks() {
        assertEquals(96, GridLayoutMath.usableWidth(100, 2, 3));
        assertEquals(32, GridLayoutMath.trackWidth(100, 2, 3, 0));
        assertEquals(32, GridLayoutMath.trackWidth(100, 2, 3, 2));
        assertEquals(0, GridLayoutMath.trackStart(100, 2, 3, 0));
        assertEquals(34, GridLayoutMath.trackStart(100, 2, 3, 1));
        assertEquals(68, GridLayoutMath.trackStart(100, 2, 3, 2));
    }

    /** 非整除余数应从左到右每列最多补 1px。 */
    @Test
    public void remainder_isDistributedLeftToRight() {
        assertEquals(97, GridLayoutMath.usableWidth(101, 2, 3));
        assertEquals(33, GridLayoutMath.trackWidth(101, 2, 3, 0));
        assertEquals(32, GridLayoutMath.trackWidth(101, 2, 3, 1));
        assertEquals(32, GridLayoutMath.trackWidth(101, 2, 3, 2));
        assertEquals(35, GridLayoutMath.trackStart(101, 2, 3, 1));
        assertEquals(69, GridLayoutMath.trackStart(101, 2, 3, 2));
    }

    /** 宽度窄于全部 gap 时轨道宽归零，起点仍复用 gap。 */
    @Test
    public void narrowWidth_clampsUsableAndTracksToZero() {
        assertEquals(0, GridLayoutMath.usableWidth(3, 2, 4));
        assertEquals(0, GridLayoutMath.trackWidth(3, 2, 4, 3));
        assertEquals(6, GridLayoutMath.trackStart(3, 2, 4, 3));
    }

    /** child index 应按行主序映射，末行不满不增加额外行。 */
    @Test
    public void childIndex_mapsToRowMajorCellsAndActualRows() {
        assertEquals(0, GridLayoutMath.rowOf(2, 3));
        assertEquals(2, GridLayoutMath.columnOf(2, 3));
        assertEquals(1, GridLayoutMath.rowOf(3, 3));
        assertEquals(0, GridLayoutMath.columnOf(3, 3));
        assertEquals(0, GridLayoutMath.rowCount(0, 3));
        assertEquals(1, GridLayoutMath.rowCount(3, 3));
        assertEquals(2, GridLayoutMath.rowCount(4, 3));
    }
}
