package club.heiqi.uilib.font;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.page.GlyphPage;
import club.heiqi.uilib.font.page.GlyphPageManager;
import club.heiqi.uilib.font.page.GlyphRuntimeTables;

/**
 * GlyphRuntimeTablesView 帧级页表快照语义测试。
 */
public class GlyphRuntimeTablesViewSnapshotTest {

    @Test
    public void snapshotCapturesValidPageTexturesAtConstruction() {
        GlyphPageManager manager = new GlyphPageManager();
        GlyphRuntimeTables tables = manager.getRuntimeTables();
        tables.setPage(FontType.NORMAL, 0, new FakeTexturePage(1, 0, 42, 128));
        tables.setPage(FontType.NORMAL, 1, new FakeTexturePage(1, 1, 43, 256));
        tables.setPage(FontType.BOLD, 0, new FakeTexturePage(1, 0, 77, 128));

        GlyphRuntimeTablesView view = new GlyphRuntimeTablesView(tables, manager, null, 1);

        Assert.assertEquals(42, view.getPageTextureIdSnapshot(FontType.NORMAL, 0));
        Assert.assertEquals(128, view.getPageTextureSizeSnapshot(FontType.NORMAL, 0));
        Assert.assertEquals(43, view.getPageTextureIdSnapshot(FontType.NORMAL, 1));
        Assert.assertEquals(256, view.getPageTextureSizeSnapshot(FontType.NORMAL, 1));
        Assert.assertEquals(77, view.getPageTextureIdSnapshot(FontType.BOLD, 0));
    }

    @Test
    public void snapshotReturnsZeroForInvalidPagesAndOutOfRange() {
        GlyphPageManager manager = new GlyphPageManager();
        GlyphRuntimeTables tables = manager.getRuntimeTables();
        // 版本不匹配的页
        tables.setPage(FontType.NORMAL, 0, new FakeTexturePage(99, 0, 42, 128));
        // null 页 + 未分配纹理的页
        tables.setPage(FontType.NORMAL, 1, null);
        tables.setPage(FontType.NORMAL, 2, new FakeTexturePage(1, 2, 0, 128));

        GlyphRuntimeTablesView view = new GlyphRuntimeTablesView(tables, manager, null, 1);

        Assert.assertEquals("版本不匹配页应快照为 0", 0, view.getPageTextureIdSnapshot(FontType.NORMAL, 0));
        Assert.assertEquals(0, view.getPageTextureSizeSnapshot(FontType.NORMAL, 0));
        Assert.assertEquals("null 页应快照为 0", 0, view.getPageTextureIdSnapshot(FontType.NORMAL, 1));
        Assert.assertEquals("未分配纹理页应快照为 0", 0, view.getPageTextureIdSnapshot(FontType.NORMAL, 2));
        Assert.assertEquals("越界页应返回 0", 0, view.getPageTextureIdSnapshot(FontType.NORMAL, 9));
        Assert.assertEquals("负索引应返回 0", 0, view.getPageTextureIdSnapshot(FontType.NORMAL, -1));
        Assert.assertEquals("无页字重应返回 0", 0, view.getPageTextureIdSnapshot(FontType.BOLD, 0));
    }

    @Test
    public void snapshotIsFrozenAtConstruction() {
        GlyphPageManager manager = new GlyphPageManager();
        GlyphRuntimeTables tables = manager.getRuntimeTables();
        tables.setPage(FontType.NORMAL, 0, new FakeTexturePage(1, 0, 42, 128));

        GlyphRuntimeTablesView view = new GlyphRuntimeTablesView(tables, manager, null, 1);
        Assert.assertEquals(42, view.getPageTextureIdSnapshot(FontType.NORMAL, 0));

        // 构造后页表变化（新纹理/换页）：快照保持不变（帧内一致性），实表已变。
        tables.setPage(FontType.NORMAL, 0, new FakeTexturePage(1, 0, 99, 512));
        tables.setPage(FontType.NORMAL, 1, new FakeTexturePage(1, 1, 123, 256));

        Assert.assertEquals("快照应冻结构造时刻的值", 42, view.getPageTextureIdSnapshot(FontType.NORMAL, 0));
        Assert.assertEquals(128, view.getPageTextureSizeSnapshot(FontType.NORMAL, 0));
        Assert.assertEquals("构造后新增页不在快照内", 0, view.getPageTextureIdSnapshot(FontType.NORMAL, 1));
        Assert.assertEquals(99, tables.normalPages[0].getTextureId());
    }

    private static final class FakeTexturePage extends GlyphPage {

        private final int fakeTextureId;
        private final int fakeTextureSize;

        private FakeTexturePage(int runtimeVersion, int pageIndex, int fakeTextureId, int fakeTextureSize) {
            super(runtimeVersion, pageIndex, 128, 64, 3);
            this.fakeTextureId = fakeTextureId;
            this.fakeTextureSize = fakeTextureSize;
        }

        @Override
        public int getTextureId() {
            return fakeTextureId;
        }

        @Override
        public int getTextureSize() {
            return fakeTextureSize;
        }
    }
}
