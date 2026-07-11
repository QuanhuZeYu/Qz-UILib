package club.heiqi.config.ui.field;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.Assert;
import org.junit.Test;

/** {@link FontSortOrderModel} 纯 Java 顺序、筛选和索引边界测试。 */
public class FontSortOrderModelTest {

    @Test
    public void mergeKeepsCanonicalDiscoveredOrderAndDropsStaleEntries() {
        List<String> discovered = Arrays.asList(" Sans ", "SERIF", "sans", null, "");
        List<String> draft = Arrays.asList(" serif ", "Missing", "SANS", "serif");

        Assert.assertEquals(Arrays.asList("SERIF", "Sans"),
                FontSortOrderModel.merge(discovered, draft));
        Assert.assertEquals(Arrays.asList("Sans", "SERIF"),
                FontSortOrderModel.merge(discovered, Arrays.<String>asList()));
    }

    @Test
    public void identityAlwaysUsesEnglishLocaleAndFilterDoesNotMutateFullOrder() {
        Locale old = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            List<String> full = Arrays.asList("Iota Font", "Arial");
            Assert.assertEquals("iota font", FontSortOrderModel.identity(" IOTA FONT "));
            Assert.assertEquals(Arrays.asList("Iota Font"),
                    FontSortOrderModel.filter(full, "IOTA"));
            Assert.assertEquals(Arrays.asList("Iota Font", "Arial"), full);
        } finally {
            Locale.setDefault(old);
        }
    }

    @Test
    public void fullMoveUsesClampAndSamePositionIsNoOp() {
        List<String> full = Arrays.asList("A", "B", "C");
        Assert.assertEquals(Arrays.asList("A", "C", "B"),
                FontSortOrderModel.move(full, 1, 99));
        Assert.assertEquals(Arrays.asList("B", "A", "C"),
                FontSortOrderModel.move(full, 1, -4));
        Assert.assertEquals(full, FontSortOrderModel.moveToOneBased(full, "b", 2));
        Assert.assertEquals(Arrays.asList("B", "A", "C"),
                FontSortOrderModel.moveToOneBased(full, "b", 1));
    }

    @Test
    public void filteredMovePreservesHiddenItemsAndTheirRelativeOrder() {
        List<String> full = Arrays.asList("Hidden A", "Visible A", "Hidden B", "Visible B", "Hidden C",
                "Visible C");
        List<String> visible = FontSortOrderModel.filter(full, "visible");

        Assert.assertEquals(Arrays.asList("Hidden A", "Visible C", "Hidden B", "Visible A", "Hidden C",
                "Visible B"), FontSortOrderModel.moveVisible(full, visible, 2, 0));
        Assert.assertEquals(Arrays.asList("Hidden A", "Hidden B", "Hidden C"),
                FontSortOrderModel.filter(full, "hidden"));
    }

    @Test
    public void indexInputIsStrictAndClamped() {
        Assert.assertEquals(Integer.valueOf(1), FontSortOrderModel.parseOneBasedTarget("0", 3));
        Assert.assertEquals(Integer.valueOf(3), FontSortOrderModel.parseOneBasedTarget("999", 3));
        Assert.assertEquals(Integer.valueOf(2), FontSortOrderModel.parseOneBasedTarget("2", 3));
        Assert.assertNull(FontSortOrderModel.parseOneBasedTarget("", 3));
        Assert.assertNull(FontSortOrderModel.parseOneBasedTarget("1.0", 3));
        Assert.assertNull(FontSortOrderModel.parseOneBasedTarget("1e2", 3));
        Assert.assertNull(FontSortOrderModel.parseOneBasedTarget("+1", 3));
    }
}
