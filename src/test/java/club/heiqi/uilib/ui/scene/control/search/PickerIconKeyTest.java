package club.heiqi.uilib.ui.scene.control.search;

import org.junit.Assert;
import org.junit.Test;

/**
 * {@link PickerIconKey} 键语义测试（S-08 写死的 5 类输入）。
 *
 * <p>契约出处：{@code team/P0-ADR-契约与测量.md} §5.1（Z-2/Z-3）：一套键 + 一套解析，
 * {@code minecraft:stone} 必须<b>整体</b>成键、{@code minecraft:stone@3} 必须切成
 * {@code {"minecraft:stone", "3"}}。</p>
 */
public class PickerIconKeyTest {

    /** 生成端：候选级 = 原样；变体级 = key@meta；meta 为 null/空串折叠为候选级（键空间无空洞）。 */
    @Test
    public void generationFoldsCandidateAndVariant() {
        Assert.assertEquals("minecraft:stone", PickerIconKey.candidate("minecraft:stone"));
        Assert.assertEquals("minecraft:stone@3", PickerIconKey.variant("minecraft:stone", "3"));
        Assert.assertEquals("meta 为 null → 候选整体", "minecraft:stone",
                PickerIconKey.variant("minecraft:stone", null));
        Assert.assertEquals("meta 为空串 → 候选整体", "minecraft:stone",
                PickerIconKey.variant("minecraft:stone", ""));
    }

    /** 生成端拒绝空候选键（避免产出无法解析的形态）。 */
    @Test
    public void generationRejectsEmptyCandidateKey() {
        try {
            PickerIconKey.candidate(null);
            Assert.fail("expected null rejection");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            PickerIconKey.variant("", "3");
            Assert.fail("expected empty rejection");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    /** 解析端 5 类输入：无 @ 返单元素；有 @ 按最后一个切；首/末位 @ 与 null 返 null。 */
    @Test
    public void splitCoversTheFiveInputClasses() {
        Assert.assertNull("null 入参 → null", PickerIconKey.split(null));
        Assert.assertArrayEquals("无 @ → 单元素整体成键", new String[] { "minecraft:stone" },
                PickerIconKey.split("minecraft:stone"));
        Assert.assertArrayEquals("按最后一个 @ 切", new String[] { "minecraft:stone", "3" },
                PickerIconKey.split("minecraft:stone@3"));
        Assert.assertArrayEquals("多段 @ 取最后", new String[] { "a@b", "3" }, PickerIconKey.split("a@b@3"));
        Assert.assertNull("@ 在首位 → null", PickerIconKey.split("@3"));
        Assert.assertNull("@ 在末位 → null", PickerIconKey.split("minecraft:stone@"));
    }

    /** 生成 → 解析往返一致（候选级与变体级都在键空间内闭环）。 */
    @Test
    public void generationAndSplitRoundTrip() {
        Assert.assertArrayEquals("候选级键整体成键", new String[] { "minecraft:stone" },
                PickerIconKey.split(PickerIconKey.candidate("minecraft:stone")));
        Assert.assertArrayEquals("变体级键切成两段", new String[] { "minecraft:stone", "3" },
                PickerIconKey.split(PickerIconKey.variant("minecraft:stone", "3")));
        Assert.assertEquals("meta 为 null 时取候选级键（原样，含 @ 的候选键也整体保留）",
                "minecraft:stone@3", PickerIconKey.variant("minecraft:stone@3", null));
    }

    /**
     * 反证（为什么旧 splitRegistryKey 形态必须删除）：按冒号拆分候选域键会把「方块名」当 meta，
     * 拼出的键与候选 key 永不相等 ⇒ 回退集合恒空、UNRENDERABLE 静默失效。
     */
    @Test
    public void colonSplitCannotProduceTheCandidateDomainKey() {
        String iconKey = PickerIconKey.candidate("minecraft:stone");
        int separator = iconKey.lastIndexOf(':');
        String[] legacyParts = { iconKey.substring(0, separator), iconKey.substring(separator + 1) };
        Assert.assertEquals("minecraft", legacyParts[0]);
        Assert.assertNotEquals("旧拆键形态拼不出候选域键", iconKey, legacyParts[0] + "@" + legacyParts[1]);
    }
}
