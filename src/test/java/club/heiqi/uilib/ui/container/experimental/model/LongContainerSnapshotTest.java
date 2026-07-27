package club.heiqi.uilib.ui.container.experimental.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class LongContainerSnapshotTest {
    private static LongEntrySnapshot entry(String value, long amount) { return new LongEntrySnapshot(new EntryKey("n", value), new ItemDescriptor("t", "c", new byte[] {1}), amount); }
    @Test public void rejectsInvalidEntriesAndDuplicateKeys() {
        EntryKey key = new EntryKey("n", "x");
        ItemDescriptor item = new ItemDescriptor("t", "c", new byte[] {1});
        try { new LongEntrySnapshot(null, item, 1); Assert.fail(); } catch (NullPointerException expected) { }
        try { new LongEntrySnapshot(key, null, 1); Assert.fail(); } catch (NullPointerException expected) { }
        try { entry("x", 0); Assert.fail(); } catch (IllegalArgumentException expected) { }
        try { entry("x", -1); Assert.fail(); } catch (IllegalArgumentException expected) { }
        try { new LongContainerSnapshot(null); Assert.fail(); } catch (NullPointerException expected) { }
        try { new LongContainerSnapshot(Arrays.asList(entry("x", 1), null)); Assert.fail(); } catch (NullPointerException expected) { }
        try { new LongContainerSnapshot(Arrays.asList(entry("x", 1), entry("x", 2))); Assert.fail(); } catch (IllegalArgumentException expected) { }
        Assert.assertEquals(Long.MAX_VALUE, entry("max", Long.MAX_VALUE).amount());
    }
    @Test public void preservesOrderAndFreezesListWithValueSemantics() {
        List<LongEntrySnapshot> source = new ArrayList<LongEntrySnapshot>(Arrays.asList(entry("a", 1), entry("b", 2)));
        LongContainerSnapshot snapshot = new LongContainerSnapshot(source);
        source.clear();
        Assert.assertEquals("a", snapshot.entries().get(0).key().value());
        try { snapshot.entries().clear(); Assert.fail(); } catch (UnsupportedOperationException expected) { }
        Assert.assertEquals(snapshot, new LongContainerSnapshot(Arrays.asList(entry("a", 1), entry("b", 2))));
    }
}
