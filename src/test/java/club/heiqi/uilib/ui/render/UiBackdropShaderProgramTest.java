package club.heiqi.uilib.ui.render;

import org.junit.Assert;
import org.junit.Test;

/**
 * `UiBackdropShaderProgram` 的资源契约测试。
 */
public class UiBackdropShaderProgramTest {

    /**
     * 验证 UI 磨玻璃 shader 资源会随主资源打包。
     */
    @Test
    public void shouldExposeBackdropShaderResources() {
        Assert.assertNotNull(UiBackdropShaderProgram.class.getResourceAsStream("/shader/uiBackdropV.vert"));
        Assert.assertNotNull(UiBackdropShaderProgram.class.getResourceAsStream("/shader/uiBackdropF.frag"));
    }
}
