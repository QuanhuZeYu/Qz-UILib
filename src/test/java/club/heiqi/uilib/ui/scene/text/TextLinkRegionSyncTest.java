package club.heiqi.uilib.ui.scene.text;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Assert;
import org.junit.Test;

/**
 * 两套 TextLinkRegion 编译期字段同步守卫（审查报告附录）。
 *
 * <p>scene.text.TextLinkRegion 与 ui.text.TextLinkRegion 是接缝两侧的平行结构
 * （scene 核心守 I10 不 import 渲染层类型），adapter 逐字段映射转换。任一侧
 * 增删字段若不同步，adapter 会静默丢字段或编译期取不到值——本测试锁死
 * 字段集与 getter 出口的逐位一致。</p>
 */
public class TextLinkRegionSyncTest {

    @Test
    public void sceneAndRenderFieldsShouldStayInSync() {
        Set<String> sceneFields = fieldNames(TextLinkRegion.class);
        Set<String> renderFields = fieldNames(club.heiqi.uilib.ui.text.TextLinkRegion.class);
        Assert.assertEquals("两套 TextLinkRegion 字段集必须逐位一致（增删字段须两侧同步）",
                sceneFields, renderFields);
    }

    @Test
    public void bothShouldExposeStartXWidthUrlGetters() {
        for (Class<?> type : new Class<?>[] {
                TextLinkRegion.class,
                club.heiqi.uilib.ui.text.TextLinkRegion.class }) {
            Assert.assertEquals("字段数应为 3", 3, type.getDeclaredFields().length);
            try {
                type.getMethod("getStartX");
                type.getMethod("getWidth");
                type.getMethod("getUrl");
            } catch (NoSuchMethodException exception) {
                Assert.fail("getter 缺失: " + type.getName() + " -> " + exception.getMessage());
            }
        }
    }

    private static Set<String> fieldNames(Class<?> type) {
        Set<String> names = new TreeSet<String>();
        for (Field field : type.getDeclaredFields()) {
            names.add(field.getName());
        }
        return names;
    }
}
