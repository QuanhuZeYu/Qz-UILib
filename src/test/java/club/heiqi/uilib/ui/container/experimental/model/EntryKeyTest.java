package club.heiqi.uilib.ui.container.experimental.model;

import org.junit.Assert;
import org.junit.Test;

public class EntryKeyTest {
    @Test public void validatesAndPreservesExactStrings() {
        try { new EntryKey(null, "v"); Assert.fail(); } catch (NullPointerException expected) { }
        try { new EntryKey("", "v"); Assert.fail(); } catch (IllegalArgumentException expected) { }
        try { new EntryKey("n", null); Assert.fail(); } catch (NullPointerException expected) { }
        try { new EntryKey("n", ""); Assert.fail(); } catch (IllegalArgumentException expected) { }
        EntryKey key = new EntryKey(" n ", "Value");
        Assert.assertEquals(" n ", key.namespace());
        Assert.assertEquals("Value", key.value());
        Assert.assertNotEquals(key, new EntryKey(" n ", "value"));
    }
    @Test public void hasStrictValueSemantics() {
        EntryKey a = new EntryKey("n", "v");
        Assert.assertEquals(a, new EntryKey("n", "v"));
        Assert.assertEquals(a.hashCode(), new EntryKey("n", "v").hashCode());
        Assert.assertTrue(a.toString().contains("n:v"));
    }
}
