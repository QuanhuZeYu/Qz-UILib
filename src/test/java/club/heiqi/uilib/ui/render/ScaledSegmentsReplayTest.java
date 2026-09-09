package club.heiqi.uilib.ui.render;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.latex.MathStyleOverride;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.RecordingRenderBackend;
import club.heiqi.uilib.ui.scene.paint.ScenePaintReplayer;

import static org.junit.Assert.*;

/** HUD 缩放必须把 SEGMENTS 送到真实后端，且不能污染复用的 logical 绘制计划。 */
public class ScaledSegmentsReplayTest {
    @Test
    public void zoomAndResetReplayRichTextInsideTheScaledClipWithoutMutatingThePlan() {
        TextStyle inherited = new TextStyle();
        inherited.setColor(0xFF123456);
        TextStyle explicit = new TextStyle();
        explicit.setFontSizePx(18);
        explicit.setLetterSpacing(-1.5F);
        explicit.setFontType(FontType.BOLD);
        explicit.setItalic(true);
        explicit.setUnderline(true);
        explicit.setStrikethrough(true);
        explicit.setSuperscript(true);
        explicit.setMarkColor(0x66334455);
        explicit.setLink("https://example.org");
        explicit.setCodeSpan(true);
        explicit.setCodeBackgroundColor(0x66445566);
        List<TextSegment> segments = Arrays.asList(
                new TextSegment("HUD 中文", inherited),
                new TextSegment("styled", explicit),
                TextSegment.forLatex("x^2", explicit, MathStyleOverride.DISPLAY));
        PaintPlan plan = new PaintPlan()
                .addCommand(PaintCommand.clipPush(0, 0, 200, 80, 4))
                .addCommand(PaintCommand.segments(segments, 5, 7, 12))
                .addCommand(PaintCommand.clipPop());
        // 同一计划先放大、再缩小、最后复位；旧装饰器会吞掉中间的 drawSegments。
        for (float scale : new float[] {1F, 1.1F, 1.5F, 2F, 0.5F, 0.9F, 1F}) {
            RecordingRenderBackend target = new RecordingRenderBackend();
            new ScenePaintReplayer().replay(plan, target.scaled(scale), 20, 30);
            assertEquals("scale=" + scale, Arrays.asList("pushClip", "drawSegments", "popClip"),
                    target.getMethodNames());
            assertEquals(Math.round(20 * scale), target.getCall(0).getInt(0));
            assertEquals(Math.round(30 * scale), target.getCall(0).getInt(1));
            assertEquals(Math.round(220 * scale), target.getCall(0).getInt(2));
            assertEquals(Math.round(110 * scale), target.getCall(0).getInt(3));
            assertEquals(Math.round(4 * scale), target.getCall(0).getInt(4));
            RecordingRenderBackend.RenderCall draw = target.getCall(1);
            assertEquals(Math.round(25 * scale), draw.getInt(1));
            assertEquals(Math.round(37 * scale), draw.getInt(2));
            assertEquals(Math.round(12 * scale), draw.getInt(3));
            @SuppressWarnings("unchecked")
            List<TextSegment> actual = (List<TextSegment>) draw.args()[0];
            assertEquals(3, actual.size());
            assertEquals("HUD 中文", actual.get(0).getText());
            assertEquals(0, actual.get(0).getStyle().getFontSizePx());
            assertEquals(0xFF123456, actual.get(0).getStyle().getColor());
            for (int i = 1; i < actual.size(); i++) {
                TextStyle style = actual.get(i).getStyle();
                assertEquals(Math.round(18 * scale), style.getFontSizePx());
                assertEquals(-1.5F * scale, style.getLetterSpacing(), 0.0001F);
                assertEquals(FontType.BOLD, style.getFontType());
                assertTrue(style.isItalic());
                assertTrue(style.isUnderline());
                assertTrue(style.isStrikethrough());
                assertTrue(style.isSuperscript());
                assertEquals(0x66334455, style.getMarkColor());
                assertEquals("https://example.org", style.getLink());
                assertTrue(style.isCodeSpan());
                assertEquals(0x66445566, style.getCodeBackgroundColor());
            }
            assertEquals("styled", actual.get(1).getText());
            assertTrue(actual.get(2).isLatex());
            assertEquals("x^2", actual.get(2).getLatexSource());
            assertEquals(MathStyleOverride.DISPLAY, actual.get(2).getLatexMathStyle());
            assertSame(segments, plan.getCommands().get(1).getSegments());
            assertEquals(18, explicit.getFontSizePx());
            assertEquals(-1.5F, explicit.getLetterSpacing(), 0F);
        }
    }

    @Test
    public void shrinkingAnExplicitTinyFontMustNotTurnItIntoInheritedSize() {
        TextStyle tiny = new TextStyle();
        tiny.setFontSizePx(1);
        RecordingRenderBackend target = new RecordingRenderBackend();
        target.scaled(0.25F).drawSegments(Arrays.asList(new TextSegment("tiny", tiny)), 0, 0, 12);
        assertEquals(1, target.getCallCount());
        @SuppressWarnings("unchecked")
        List<TextSegment> actual = (List<TextSegment>) target.getCall(0).args()[0];
        assertEquals(1, actual.get(0).getStyle().getFontSizePx());
        assertEquals(1, tiny.getFontSizePx());
    }
}
