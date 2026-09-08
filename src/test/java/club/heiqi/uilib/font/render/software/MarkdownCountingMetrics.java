package club.heiqi.uilib.font.render.software;

import club.heiqi.uilib.font.layout.TextLayoutService;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;

/** 使用既有独立软件装配计数，不读取 FontService owner 的内部 storage。 */
public final class MarkdownCountingMetrics extends TextLayoutService {
    public int calls;
    public MarkdownCountingMetrics() { this(LatexSoftwareRenderKit.shared()); }
    private MarkdownCountingMetrics(LatexSoftwareRenderKit.Shared shared) {
        super(shared.fontMatcher, shared.manager, shared.derivedFontCache);
        setRuntimeVersion(1);
    }
    @Override public double getSegmentWidth(TextSegment segment, int font) {
        calls++;
        return super.getSegmentWidth(segment, font);
    }
    @Override public double resolveAdvance(int cp, TextStyle style, int font) {
        calls++;
        return super.resolveAdvance(cp, style, font);
    }
}
