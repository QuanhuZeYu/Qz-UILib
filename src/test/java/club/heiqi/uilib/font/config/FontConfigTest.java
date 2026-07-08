package club.heiqi.uilib.font.config;

import java.lang.reflect.Field;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * {@link FontConfig#getFontSortSnapshot()} 的边界测试。
 *
 * <p>FontConfig 使用 public static 字段承载配置值，测试前后保存并恢复字段，避免污染其他测试。</p>
 */
public class FontConfigTest {

    private Field fontSortField;
    private String[] saveFontSort;

    /**
     * 保存 fontSort 静态字段快照。
     *
     * @throws Exception 反射字段访问失败时抛出
     */
    @Before
    public void saveStaticState() throws Exception {
        fontSortField = FontConfig.class.getDeclaredField("fontSort");
        fontSortField.setAccessible(true);
        saveFontSort = (String[]) fontSortField.get(null);
    }

    /**
     * 恢复 fontSort 静态字段快照。
     *
     * @throws Exception 反射字段访问失败时抛出
     */
    @After
    public void restoreStaticState() throws Exception {
        fontSortField.set(null, saveFontSort);
        FontConfig.onConfigReload();
    }

    /**
     * fontSort 为 null 时返回非 null 空数组。
     *
     * @throws Exception 反射字段写入失败时抛出
     */
    @Test
    public void getFontSortSnapshot_whenFontSortIsNull_returnsEmptyArray() throws Exception {
        fontSortField.set(null, null);

        String[] result = FontConfig.getFontSortSnapshot();

        assertNotNull("null fontSort 应返回非 null 数组", result);
        assertEquals("null fontSort 应返回空数组", 0, result.length);
    }

    /**
     * fontSort 为默认空数组时返回空数组。
     *
     * @throws Exception 反射字段写入失败时抛出
     */
    @Test
    public void getFontSortSnapshot_whenFontSortIsEmpty_returnsEmptyArray() throws Exception {
        fontSortField.set(null, new String[0]);

        String[] result = FontConfig.getFontSortSnapshot();

        assertNotNull("空 fontSort 应返回非 null 数组", result);
        assertEquals("空 fontSort 应返回空数组", 0, result.length);
    }

    /**
     * fontSort 非空时返回防御性拷贝，调用方 mutation 不污染原字段。
     *
     * @throws Exception 反射字段写入失败时抛出
     */
    @Test
    public void getFontSortSnapshot_whenFontSortIsNonEmpty_returnsDefensiveCopy() throws Exception {
        fontSortField.set(null, new String[]{"Arial", "宋体"});

        String[] result = FontConfig.getFontSortSnapshot();

        assertArrayEquals("非空 fontSort 应按原顺序返回", new String[]{"Arial", "宋体"}, result);
        result[0] = "MUTATED";

        String[] secondResult = FontConfig.getFontSortSnapshot();
        assertArrayEquals("修改返回数组不应污染原 fontSort", new String[]{"Arial", "宋体"}, secondResult);
    }
}
