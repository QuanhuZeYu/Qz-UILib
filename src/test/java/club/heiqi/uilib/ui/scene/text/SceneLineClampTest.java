package club.heiqi.uilib.ui.scene.text;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.scene.FixedTextMeasurer;

/**
 * {@link SceneLineClamp} maxLines 限行与末行省略号测试。
 */
public class SceneLineClampTest {

    @Test
    public void shouldClampToMaxLines() {
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        List<String> lines = Arrays.asList("AAAA", "BBBB", "CCCC");

        List<String> clamped = SceneLineClamp.clamp(lines, 2, false, measurer, 16, 40, 0);

        Assert.assertEquals(Arrays.asList("AAAA", "BBBB"), clamped);
    }

    @Test
    public void shouldReturnUnchangedWhenWithinMaxLines() {
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        List<String> lines = Arrays.asList("AAAA", "BBBB");

        // 行数恰好等于 maxLines：无内容被截，不加省略号
        List<String> clamped = SceneLineClamp.clamp(lines, 2, true, measurer, 16, 40, 0);

        Assert.assertEquals(lines, clamped);
    }

    @Test
    public void shouldReturnUnchangedWhenMaxLinesNotSet() {
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        List<String> lines = Arrays.asList("AAAA", "BBBB");

        Assert.assertEquals(lines, SceneLineClamp.clamp(lines, 0, true, measurer, 16, 40, 0));
    }

    @Test
    public void shouldAppendEllipsisByTrimmingLastLine() {
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        List<String> lines = Arrays.asList("AAAA", "BBBB", "CCCC");

        // 省略号宽 8，可用 32；末行 32px 恰好放下 → 直接追加
        List<String> clamped = SceneLineClamp.clamp(lines, 2, true, measurer, 16, 40, 0);
        Assert.assertEquals(Arrays.asList("AAAA", "BBBB\u2026"), clamped);

        // 行宽 32：可用 24 → 末行 32px 裁剪到 24（BBB）再追加
        List<String> trimmed = SceneLineClamp.clamp(lines, 2, true, measurer, 16, 32, 0);
        Assert.assertEquals(Arrays.asList("AAAA", "BBB\u2026"), trimmed);
    }

    @Test
    public void shouldKeepOnlyEllipsisWhenWidthTooSmall() {
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        List<String> lines = Arrays.asList("AAAA", "BBBB");

        // 行宽 4 < 省略号 8：末行只剩省略号
        List<String> clamped = SceneLineClamp.clamp(lines, 1, true, measurer, 16, 4, 0);

        Assert.assertEquals(Arrays.asList("\u2026"), clamped);
    }

    @Test
    public void shouldSkipEllipsisWithoutWrapWidth() {
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        List<String> lines = Arrays.asList("AAAA", "BBBB");

        // 非 wrap（wrapWidth<=0）：只截行数，不追加省略号
        List<String> clamped = SceneLineClamp.clamp(lines, 1, true, measurer, 16, 0, 0);

        Assert.assertEquals(Arrays.asList("AAAA"), clamped);
    }
}