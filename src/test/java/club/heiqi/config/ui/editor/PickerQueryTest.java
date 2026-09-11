package club.heiqi.config.ui.editor;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@link PickerQuery} 值语义测试：三态歧义消除、归一化口径、可作缓存键。
 *
 * <p>契约出处：{@code team/P0-ADR-契约与测量.md} §1.2（S-09 归一化写死）。</p>
 */
public class PickerQueryTest {

    /** 归一化：trim + Locale.ROOT 小写；null → 空串；browse 与空文本等价。 */
    @Test
    public void normalizationFoldsNullBlankAndCase() {
        assertEquals("", PickerQuery.normalize(null));
        assertEquals("", PickerQuery.normalize("   "));
        assertEquals("stone", PickerQuery.normalize("  StOnE  "));

        assertTrue(PickerQuery.text(null, 0, null).isBrowse());
        assertTrue(PickerQuery.text("   ", 0, null).isBrowse());
        assertTrue(PickerQuery.browse(0, null).isBrowse());
        assertFalse(PickerQuery.text("stone", 0, null).isBrowse());

        assertEquals("", PickerQuery.text(null, 0, null).normalizedText());
        assertEquals("stone", PickerQuery.text(" StOne ", 0, null).normalizedText());
    }

    /** categoryKey 的 null 与空串折叠为同一空串（= 不做分类过滤）；hasCategoryFilter 与之同源。 */
    @Test
    public void categoryKeyFoldsNullAndEmpty() {
        assertEquals(PickerQuery.NO_CATEGORY, PickerQuery.browse(0, null).categoryKey());
        assertEquals(PickerQuery.NO_CATEGORY, PickerQuery.browse(0, "").categoryKey());
        assertEquals(PickerQuery.NO_CATEGORY, PickerQuery.browse(0, "  ").categoryKey());
        assertFalse(PickerQuery.browse(0, null).hasCategoryFilter());

        assertEquals("mods", PickerQuery.browse(2, " Mods ").categoryKey());
        assertTrue(PickerQuery.browse(2, "mods").hasCategoryFilter());
        assertEquals(2, PickerQuery.browse(2, "mods").categoryDimension());
    }

    /** 负维度立即失败（与 CategorizedValueEditorProvider.categories(int) 同口径）。 */
    @Test
    public void negativeDimensionIsRejected() {
        try {
            PickerQuery.browse(-1, null);
            fail("expected negative dimension rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("categoryDimension"));
        }
        try {
            PickerQuery.text("x", -3, null);
            fail("expected negative dimension rejection");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    /** equals/hashCode 按 (normalizedText, categoryDimension, categoryKey)：可作候选源缓存键。 */
    @Test
    public void equalsAndHashCodeFollowNormalizedTriple() {
        assertEquals(PickerQuery.text(" Stone ", 0, null), PickerQuery.text("stone", 0, ""));
        assertEquals(PickerQuery.text(" Stone ", 0, null).hashCode(), PickerQuery.text("stone", 0, "").hashCode());
        assertEquals(PickerQuery.browse(0, null), PickerQuery.text("   ", 0, ""));

        assertNotEquals(PickerQuery.text("stone", 0, null), PickerQuery.text("stone", 1, null));
        assertNotEquals(PickerQuery.text("stone", 0, null), PickerQuery.browse(0, null));
        assertNotEquals(PickerQuery.text("stone", 0, "a"), PickerQuery.text("stone", 0, "b"));

        Map<PickerQuery, String> cache = new HashMap<PickerQuery, String>();
        cache.put(PickerQuery.text(" Stone ", 0, null), "hit");
        assertEquals("hit", cache.get(PickerQuery.text("stone", 0, "")));
    }
}
