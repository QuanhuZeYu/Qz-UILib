package club.heiqi.uilib.ui.scene.control;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.scene.control.SceneVirtualGrid.Item;

/**
 * {@link SceneItemIndex} 与 {@link SceneGridSnapshot} 测试。
 *
 * <p>覆盖：索引与数据同源一致（重排/前插/删除）、未命中 -1、重复 key 首项胜、
 * {@code windowOffset} 全局下标语义、以及**结构判据** —— 计数列表包装下
 * {@code indexOf}/{@code itemAt} 的 {@code get} 调用数 == 0（索引查询与 N 无关）。</p>
 */
public class SceneItemIndexTest {

    private static List<Item> items(int... keys) {
        List<Item> list = new ArrayList<Item>();
        for (int key : keys) {
            list.add(new Item(Integer.valueOf(key), null, "item" + key));
        }
        return list;
    }

    @Test
    public void indexMatchesItemsAfterReorder() {
        SceneItemIndex index = SceneItemIndex.of(items(0, 1, 2, 3, 4));
        Assert.assertEquals(0, index.indexOf(0));
        Assert.assertEquals(4, index.indexOf(4));
        // 重排后重建索引 → 下标跟随新顺序
        SceneItemIndex reordered = SceneItemIndex.of(items(4, 3, 2, 1, 0));
        Assert.assertEquals(0, reordered.indexOf(4));
        Assert.assertEquals(4, reordered.indexOf(0));
    }

    @Test
    public void indexTracksInsertAndRemove() {
        SceneItemIndex before = SceneItemIndex.of(items(10, 11, 12));
        Assert.assertEquals(1, before.indexOf(11));
        SceneItemIndex inserted = SceneItemIndex.of(items(9, 10, 11, 12));
        Assert.assertEquals(2, inserted.indexOf(11));
        SceneItemIndex removed = SceneItemIndex.of(items(11, 12));
        Assert.assertEquals(0, removed.indexOf(11));
        Assert.assertEquals(2, removed.size());
    }

    @Test
    public void missingKeyReturnsMinusOneAndNullItem() {
        SceneItemIndex index = SceneItemIndex.of(items(1, 2));
        Assert.assertEquals(-1, index.indexOf(99));
        Assert.assertEquals(-1, index.indexOf(null));
        Assert.assertNull(index.itemAt(99));
        Assert.assertNull(index.itemAt(null));
    }

    @Test
    public void duplicateKeyFirstOccurrenceWins() {
        // 与线性反查（首个命中）语义一致
        List<Item> list = new ArrayList<Item>();
        list.add(new Item("dup", null, "first"));
        list.add(new Item("dup", null, "second"));
        SceneItemIndex index = SceneItemIndex.of(list);
        Assert.assertEquals(0, index.indexOf("dup"));
        Assert.assertEquals("first", index.itemAt("dup").label());
    }

    @Test
    public void windowOffsetYieldsGlobalIndexAndGlobalLookup() {
        SceneItemIndex index = SceneItemIndex.of(items(100, 101, 102), 20);
        Assert.assertEquals(20, index.windowOffset());
        Assert.assertEquals(3, index.size());
        Assert.assertEquals(21, index.indexOf(101));
        Assert.assertEquals(22, index.indexOf(102));
        Assert.assertEquals(Integer.valueOf(100), index.itemAtGlobal(20).key());
        Assert.assertEquals(Integer.valueOf(102), index.itemAtGlobal(22).key());
        Assert.assertNull("全局下标越界返回 null", index.itemAtGlobal(23));
        Assert.assertNull("全局下标低于窗口起点返回 null", index.itemAtGlobal(19));
    }

    @Test
    public void emptyListYieldsEmptyIndex() {
        SceneItemIndex empty = SceneItemIndex.of(new ArrayList<Item>());
        Assert.assertEquals(0, empty.size());
        Assert.assertEquals(-1, empty.indexOf("any"));
        SceneItemIndex nullIndex = SceneItemIndex.of(null);
        Assert.assertEquals(0, nullIndex.size());
        Assert.assertEquals(-1, nullIndex.indexOf("any"));
        Assert.assertNull(nullIndex.itemAtGlobal(0));
    }

    @Test
    public void indexQueriesDoNotTouchSourceList() {
        CountingList source = new CountingList(items(0, 1, 2, 3, 4, 5, 6, 7));
        SceneItemIndex index = SceneItemIndex.of(source);
        Assert.assertTrue("建表必须遍历一次数据源", source.gets > 0);
        source.gets = 0;
        // 结构判据（ADR §3.8）：命中与未命中各一次，来源列表 get 调用数必须为 0
        Assert.assertEquals(3, index.indexOf(3));
        Assert.assertEquals(-1, index.indexOf(99));
        Assert.assertNotNull(index.itemAt(3));
        Assert.assertNull(index.itemAt(99));
        Assert.assertNotNull(index.itemAtGlobal(5));
        Assert.assertEquals("索引查询与 N 无关：来源列表 get 调用数 == 0", 0, source.gets);
    }

    @Test
    public void snapshotKeepsItemsAndIndexInSync() {
        List<Item> source = items(7, 8, 9);
        SceneGridSnapshot snapshot = SceneGridSnapshot.of(source);
        Assert.assertSame(source, snapshot.items());
        Assert.assertEquals(3, snapshot.size());
        Assert.assertEquals(1, snapshot.index().indexOf(8));
        Assert.assertSame(snapshot.items().get(1), snapshot.index().itemAt(8));
        // 窗口切片：全局限定在切片起点
        SceneGridSnapshot slice = SceneGridSnapshot.of(source, 40);
        Assert.assertEquals(40, slice.windowOffset());
        Assert.assertEquals(41, slice.index().indexOf(8));
        Assert.assertEquals(0, SceneGridSnapshot.of(null).size());
        Assert.assertEquals(0, SceneGridSnapshot.of(null).windowOffset());
    }

    /** 计数列表：记录 {@code get} 调用次数，用于「索引查询不触碰数据源」的结构判据。 */
    private static final class CountingList extends AbstractList<Item> {
        private final List<Item> delegate;
        private int gets;

        private CountingList(List<Item> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Item get(int index) {
            gets++;
            return delegate.get(index);
        }

        @Override
        public int size() {
            return delegate.size();
        }
    }
}
