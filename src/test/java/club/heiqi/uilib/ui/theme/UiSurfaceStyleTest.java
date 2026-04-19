package club.heiqi.uilib.ui.theme;

import org.junit.Assert;
import org.junit.Test;

/**
 * `UiSurfaceStyle` 的基础契约测试。
 */
public class UiSurfaceStyleTest {

    /**
     * 验证空表面会保持方角。
     */
    @Test
    public void shouldKeepNoneSurfaceSquare() {
        UiSurfaceStyle surfaceStyle = UiSurfaceStyle.none();

        Assert.assertEquals(0, surfaceStyle.fillColor);
        Assert.assertEquals(0, surfaceStyle.borderColor);
        Assert.assertEquals(0, surfaceStyle.cornerRadius);
    }

    /**
     * 验证圆角半径会被钳制，surface 仅保留外观语义。
     */
    @Test
    public void shouldClampNegativeCornerRadius() {
        UiSurfaceStyle surfaceStyle = new UiSurfaceStyle(0xFF101820, 0xFF86A8F0, -12);

        Assert.assertEquals(0, surfaceStyle.cornerRadius);
    }
}
