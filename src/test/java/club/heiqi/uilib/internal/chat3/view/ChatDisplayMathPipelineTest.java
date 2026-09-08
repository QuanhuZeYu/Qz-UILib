package club.heiqi.uilib.internal.chat3.view;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.latex.MathStyleOverride;
import club.heiqi.uilib.font.layout.TextLayoutService;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.markdown.MarkdownDocument;
import club.heiqi.uilib.font.render.software.LatexSoftwareRenderKit;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;

/** 聊天新数学 Content 选择及后处理边界；保持旧行内处理器可注入。 */
public class ChatDisplayMathPipelineTest {
    private static final int FONT = 16;
    private static final String TEX = "\\frac{\\frac{a}{b}}{\\frac{c}{d}}";
    private int budget;
    @Before public void before() {
        budget = FontConfig.widthCacheMissBudgetPerWindow;
        FontConfig.widthCacheMissBudgetPerWindow = 0;
    }
    @After public void after() { FontConfig.widthCacheMissBudgetPerWindow = budget; }
    @AfterClass public static void releaseShared() { LatexSoftwareRenderKit.resetShared(); }
    private static TextLayoutService service() { return LatexSoftwareRenderKit.currentService(); }
    private static List<TextSegment> formulas(ChatMarkdownPipeline.RenderedContent plan) {
        List<TextSegment> out = new ArrayList<TextSegment>();
        for (ChatMarkdownPipeline.PaintLeaf leaf : plan.leaves) {
            if (leaf.command.getType() != PaintCommandType.SEGMENTS) continue;
            for (TextSegment s : leaf.command.getSegments()) if (s.isLatex()) out.add(s);
        }
        return out;
    }

    @Test public void displaySelectionWorksWithoutTablesAndSharesParseCache() {
        AtomicInteger parses = new AtomicInteger();
        ChatMarkdownPipeline pipeline = new ChatMarkdownPipeline(source -> {
            parses.incrementAndGet();
            return MarkdownDocument.parse(source);
        });
        String source = "$$" + TEX + "$$";
        assertFalse(pipeline.hasTables(source));
        assertTrue(pipeline.hasDisplayMath(source));
        ChatMarkdownPipeline.RenderedContent first = pipeline.layoutContent(source, -1, 240, FONT, null, service(), 1);
        assertSame(first, pipeline.layoutContent(source, -1, 240, FONT, null, service(), 1));
        assertEquals(1, parses.get());
        assertTrue(pipeline.hasDisplayMath("before $$x$$ after"));
        assertTrue(pipeline.hasDisplayMath("> - $$x$$"));
        assertFalse(pipeline.hasDisplayMath("before $x$ after"));
        assertFalse(pipeline.hasDisplayMath("plain"));
        assertFalse(pipeline.hasDisplayMath("~~~\n$$x$$\n~~~"));
        assertFalse(pipeline.hasDisplayMath("$$$x$$$"));
    }

    @Test public void displayBlockBypassesProcessorWhileInlineDisplayStillUsesIt() {
        ChatMarkdownPipeline pipeline = new ChatMarkdownPipeline();
        AtomicInteger mathProcessed = new AtomicInteger();
        ChatMessageList.SegmentPostProcessor processor = (segments, font) -> {
            for (TextSegment s : segments) if (s.isLatex()) mathProcessed.incrementAndGet();
            int lineHeight = service().getAscent(font) + service().getDescent(font) + service().getLineGap(font);
            return service().applyLatexLineHeightConstraint(segments, font, lineHeight, 1.0F, 0.25F);
        };
        String block = "$$" + TEX + "$$";
        ChatMarkdownPipeline.RenderedContent raw = pipeline.layoutContent(block, -1, 240, FONT, null, service(), 1);
        ChatMarkdownPipeline.RenderedContent processed = pipeline.layoutContent(block, -1, 240, FONT, processor, service(), 1);
        assertEquals("块公式不进入旧行内限高处理器", 0, mathProcessed.get());
        assertEquals(raw.height, processed.height);
        TextSegment segment = formulas(processed).get(0);
        assertEquals(FONT, segment.getStyle().resolveEffectiveFontSizePx(FONT));
        assertEquals(MathStyleOverride.DISPLAY, segment.getLatexMathStyle());
        assertEquals(TEX, segment.getLatexSource());
        assertTrue(processed.height >= service().getLatexBox(segment, FONT).getTotalHeight());

        ChatMarkdownPipeline.RenderedContent inline = pipeline.layoutContent("before $$" + TEX + "$$ after",
                -1, 320, FONT, processor, service(), 1);
        assertTrue("行内 DISPLAY 仍经过调用方处理", mathProcessed.get() > 0);
        TextSegment inlineMath = formulas(inline).get(0);
        assertEquals(MathStyleOverride.DISPLAY, inlineMath.getLatexMathStyle());
        assertEquals(TEX, inlineMath.getLatexSource());
        assertTrue("fixture 确实触发行内缩放", inlineMath.getStyle().resolveEffectiveFontSizePx(FONT) < FONT);
    }

    @Test public void listDisplayPostprocessingPreservesSharedMarkerAndSingleHeight() {
        ChatMarkdownPipeline pipeline = new ChatMarkdownPipeline();
        // 返回新段模拟真实后处理，必须保留 L1 marker chain 身份映射。
        ChatMessageList.SegmentPostProcessor copy = (segments, font) -> {
            List<TextSegment> out = new ArrayList<TextSegment>();
            for (TextSegment segment : segments) out.add(segment.withStyle(segment.getStyle().copy()));
            return out;
        };
        ChatMarkdownPipeline.RenderedContent plain = pipeline.layoutContent("$$" + TEX + "$$", -1,
                320, FONT, copy, service(), 1);
        ChatMarkdownPipeline.RenderedContent list = pipeline.layoutContent("- $$" + TEX + "$$", -1,
                320, FONT, copy, service(), 1);
        assertEquals(plain.height, list.height);
        assertEquals(1, formulas(list).size());
        int textCommands = 0;
        for (ChatMarkdownPipeline.PaintLeaf leaf : list.leaves) {
            if (leaf.command.getType() == PaintCommandType.SEGMENTS) textCommands++;
        }
        assertEquals("一个公式命令和一个 marker 命令", 2, textCommands);
    }
}
