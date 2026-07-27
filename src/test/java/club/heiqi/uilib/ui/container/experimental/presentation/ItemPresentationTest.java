package club.heiqi.uilib.ui.container.experimental.presentation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class ItemPresentationTest {
    @Test public void permitsNullIconAndFreezesTooltip() {
        List<String> lines = new ArrayList<String>(Arrays.asList("a"));
        ItemPresentation<String> presentation = new ItemPresentation<String>(null, "Name", lines);
        lines.add("b");
        Assert.assertNull(presentation.icon()); Assert.assertEquals(Arrays.asList("a"), presentation.tooltipLines());
        try { presentation.tooltipLines().add("x"); Assert.fail(); } catch (UnsupportedOperationException expected) { }
        Assert.assertEquals(presentation, new ItemPresentation<String>(null, "Name", Arrays.asList("a")));
    }
    @Test public void rejectsNullNameListAndLine() {
        try { new ItemPresentation<String>(null, null, Arrays.asList("a")); Assert.fail(); } catch (NullPointerException expected) { }
        try { new ItemPresentation<String>(null, "n", null); Assert.fail(); } catch (NullPointerException expected) { }
        try { new ItemPresentation<String>(null, "n", Arrays.asList((String) null)); Assert.fail(); } catch (NullPointerException expected) { }
    }
}
