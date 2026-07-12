package club.heiqi.uilib.client.hud;

import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.RecordingRenderBackend;
import club.heiqi.uilib.ui.scene.testkit.ScenePaintCapture;
import org.junit.Test;

import static org.junit.Assert.*;

/** HUD scene 内容通过 layout→paint→replay 出口的 headless 捕获测试。 */
public class SceneHudPipelineTest {
    @Test public void textHudPaintsThroughDisplayListWithoutGuiScreen() {
        SceneNode root = SceneNode.column().setPadding(2).setBackgroundColor(0xA0000000)
                .setWidthSizing(SceneNode.WidthSizing.SHRINK).setHitTestable(false);
        root.appendChild(new SceneNode().setText("HUD").setTextColor(0xFFFFFFFF).setHitTestable(false));
        RecordingRenderBackend backend = ScenePaintCapture.paintAndCapture(root, 100, 40);
        assertNotNull(ScenePaintCapture.firstFill(backend));
        assertTrue(backend.getCalls().stream().anyMatch(call -> "drawText".equals(call.methodName())));
    }
}
