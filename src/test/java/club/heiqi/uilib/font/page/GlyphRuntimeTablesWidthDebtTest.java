package club.heiqi.uilib.font.page;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;

/**
 * 宽度近似债务账本契约测试（方案 D 的数据源侧）。
 *
 * <p><b>锁什么</b>：{@code GlyphRuntimeTables} 的「近似债务按码点对账、债务归零才递增
 * 宽度收敛代」语义。收敛代是页面层文本测量纪元的低位分量，它一旦失真就会要么
 * 让页面永不自愈（漏 bump），要么让页面每帧重排（误 bump），两种都必须被钉住。</p>
 *
 * <p><b>为什么按码点而不是按次数</b>：若只记「近似次数 / 真值写入次数」两个计数器，
 * 同一窗口内无关码点的真值写入会立刻把债务冲平而提前收敛（本类的
 * {@link #convergeEpochBumpsOnlyWhenAllDistinctDebtsClear}）。</p>
 */
public class GlyphRuntimeTablesWidthDebtTest {

    @Test
    public void debtCountsDistinctCodepointsOnceEach() {
        GlyphRuntimeTables tables = new GlyphRuntimeTables();

        tables.markWidthApproximated(FontType.NORMAL, '中');
        tables.markWidthApproximated(FontType.NORMAL, '中');
        tables.markWidthApproximated(FontType.NORMAL, '文');

        Assert.assertEquals("同一码点重复近似只记一笔",
                2, tables.getWidthApproximationDebtCount());
    }

    @Test
    public void convergeEpochBumpsOnlyWhenAllDistinctDebtsClear() {
        GlyphRuntimeTables tables = new GlyphRuntimeTables();
        int base = tables.getWidthConvergeEpoch();

        tables.markWidthApproximated(FontType.NORMAL, '中');
        tables.markWidthApproximated(FontType.NORMAL, '文');
        tables.clearWidthApproximated(FontType.NORMAL, '中');

        Assert.assertEquals("仍有未清偿码点时不得宣布收敛",
                base, tables.getWidthConvergeEpoch());
        Assert.assertEquals(1, tables.getWidthApproximationDebtCount());

        tables.clearWidthApproximated(FontType.NORMAL, '文');

        Assert.assertEquals("债务全部清偿即收敛一代",
                base + 1, tables.getWidthConvergeEpoch());
        Assert.assertEquals(0, tables.getWidthApproximationDebtCount());
    }

    @Test
    public void clearingUnmarkedCodepointMustNotFabricateConvergence() {
        GlyphRuntimeTables tables = new GlyphRuntimeTables();
        int base = tables.getWidthConvergeEpoch();

        tables.clearWidthApproximated(FontType.NORMAL, '中');
        tables.clearWidthApproximated(FontType.BOLD, '中');

        Assert.assertEquals("无债务不得伪造收敛",
                base, tables.getWidthConvergeEpoch());
        Assert.assertEquals(0, tables.getWidthApproximationDebtCount());
    }

    @Test
    public void weightsCarryIndependentDebtForSameCodepoint() {
        GlyphRuntimeTables tables = new GlyphRuntimeTables();
        int base = tables.getWidthConvergeEpoch();

        tables.markWidthApproximated(FontType.NORMAL, '中');
        tables.markWidthApproximated(FontType.BOLD, '中');
        tables.clearWidthApproximated(FontType.NORMAL, '中');

        Assert.assertEquals("粗体同码点仍有债务，不得收敛",
                base, tables.getWidthConvergeEpoch());
        tables.clearWidthApproximated(FontType.BOLD, '中');
        Assert.assertEquals(base + 1, tables.getWidthConvergeEpoch());
    }

    @Test
    public void repeatedConvergenceCyclesEachBumpOnce() {
        GlyphRuntimeTables tables = new GlyphRuntimeTables();
        int base = tables.getWidthConvergeEpoch();

        for (int round = 1; round <= 3; round++) {
            tables.markWidthApproximated(FontType.NORMAL, '中');
            tables.clearWidthApproximated(FontType.NORMAL, '中');
            Assert.assertEquals("第 " + round + " 轮清偿应恰好递进一代",
                    base + round, tables.getWidthConvergeEpoch());
        }
    }

    @Test
    public void lifecycleResetsDropDebtWithoutBumpingEpoch() {
        GlyphRuntimeTables tables = new GlyphRuntimeTables();
        tables.markWidthApproximated(FontType.NORMAL, '中');
        tables.markWidthApproximated(FontType.BOLD, '文');
        int base = tables.getWidthConvergeEpoch();

        tables.resetGlyphLifecycle();

        Assert.assertEquals(0, tables.getWidthApproximationDebtCount());
        Assert.assertEquals("换代重置不是收敛，不得递进纪元",
                base, tables.getWidthConvergeEpoch());

        tables.markWidthApproximated(FontType.NORMAL, '甲');
        tables.resetGlyphRuntime();
        Assert.assertEquals(0, tables.getWidthApproximationDebtCount());
        Assert.assertEquals(base, tables.getWidthConvergeEpoch());
    }

    @Test
    public void outOfRangeCodepointsAreIgnoredNotCrash() {
        GlyphRuntimeTables tables = new GlyphRuntimeTables();
        int base = tables.getWidthConvergeEpoch();

        tables.markWidthApproximated(FontType.NORMAL, -1);
        tables.markWidthApproximated(FontType.NORMAL, GlyphRuntimeTables.CODEPOINT_COUNT);
        tables.clearWidthApproximated(FontType.NORMAL, -1);

        Assert.assertEquals(0, tables.getWidthApproximationDebtCount());
        Assert.assertEquals(base, tables.getWidthConvergeEpoch());
    }

    /**
     * 装配回填是冷启动最主要的清账通道：真值写进宽度缓存必须同时销账。
     */
    @Test
    public void assembledAdvancePublishClearsTheDebt() {
        GlyphRuntimeTables tables = new GlyphRuntimeTables();
        int base = tables.getWidthConvergeEpoch();
        tables.markWidthApproximated(FontType.NORMAL, '中');
        tables.markWidthApproximated(FontType.NORMAL, '文');

        tables.publishAssembledAdvance(FontType.NORMAL, '中', 12.0F, club.heiqi.uilib.font.FontRuntimeSettings.capture());

        Assert.assertEquals("已回填的码点即销账",
                1, tables.getWidthApproximationDebtCount());
        Assert.assertEquals("仍有未回填码点时不得收敛",
                base, tables.getWidthConvergeEpoch());

        tables.publishAssembledAdvance(FontType.NORMAL, '文', 12.0F, club.heiqi.uilib.font.FontRuntimeSettings.capture());

        Assert.assertEquals(0, tables.getWidthApproximationDebtCount());
        Assert.assertEquals(base + 1, tables.getWidthConvergeEpoch());
        Assert.assertFalse("回填值确实进了宽度缓存",
                Float.isNaN(tables.widthArray(FontType.NORMAL)['文']));
    }
}
