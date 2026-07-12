package club.heiqi.uilib.client.hud;

import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.RecordingRenderBackend;
import club.heiqi.uilib.ui.scene.paint.ScenePaintReplayer;
import club.heiqi.uilib.ui.scene.paint.TextStyle;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/** 独立 HUD scale 在 replay 边界只执行一次的完整参数契约测试。 */
public class ScaledHudBackendContractTest {
    @Test public void allGeometryClipReplayOffsetsAndFontScaleExactlyOnce() {
        float[] scales = { 1F, 1.25F, 1.5F, 1.75F, 2F };
        PaintPlan plan = completePlan();
        String logicalCommands = plan.getCommands().toString();
        for (float scale : scales) {
            RecordingRenderBackend raw = new RecordingRenderBackend();
            new ScenePaintReplayer().replay(plan, new ScaledHudBackend(raw, scale), 3, 5);
            assertEquals("replay 不得改写 logical PaintPlan", logicalCommands, plan.getCommands().toString());
            assertScaled(raw.getCalls(), scale);
        }
    }

    private static PaintPlan completePlan() {
        return new PaintPlan()
                .addClipPush(1, 2, 257, 129, 3)
                .addCommand(PaintCommand.background(2, 3, 251, 127, 0xFF010203))
                .addCommand(PaintCommand.border(3, 4, 250, 126, 0xFF040506, 1, 0))
                .addCommand(PaintCommand.text(5, 7, "scale", new TextStyle(0xFFFFFFFF, 11)))
                .addCommand(PaintCommand.pushTransform(7, 9, 249, 125, 1.5F, 2.5F, 10F,
                        1.1F, 0.9F, 0.25F, 0.75F))
                .addCommand(PaintCommand.popTransform())
                .addClipPop();
    }

    private static void assertScaled(List<RecordingRenderBackend.RenderCall> calls, float scale) {
        RecordingRenderBackend.RenderCall clip = calls.get(0);
        assertInts(clip, scale, 4, 7, 260, 134, 3);
        assertInts(calls.get(1), scale, 5, 8, 254, 132);
        assertInts(calls.get(2), scale, 6, 9, 253, 131);
        RecordingRenderBackend.RenderCall text = calls.get(3);
        assertEquals(Math.round(8 * scale), text.getInt(1));
        assertEquals(Math.round(12 * scale), text.getInt(2));
        assertEquals(Math.round(11 * scale), text.getInt(5));
        RecordingRenderBackend.RenderCall transform = calls.get(4);
        assertEquals(1.5F * scale, transform.getFloat(0), 0F);
        assertEquals(2.5F * scale, transform.getFloat(1), 0F);
        assertEquals(Math.round(7 * scale), transform.getInt(7));
        assertEquals(Math.round(9 * scale), transform.getInt(8));
        assertEquals(Math.round(249 * scale), transform.getInt(9));
        assertEquals(Math.round(125 * scale), transform.getInt(10));
    }

    private static void assertInts(RecordingRenderBackend.RenderCall call, float scale, int... values) {
        for (int i = 0; i < values.length; i++) assertEquals(Math.round(values[i] * scale), call.getInt(i));
    }
}
