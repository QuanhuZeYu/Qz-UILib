package club.heiqi.uilib.font.render.software;

import java.util.ArrayList;
import java.util.List;

import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;

/**
 * markdown 出图渲染倍率的单一开关（M4 增补需求：@1x 出货口径 + @Nx 判读副本）。
 *
 * <p><b>真放大依据（一手核实）</b>：软件装配的 atlas 字形按
 * {@code FontConfig.awtCharSize=64.0} 生成（{@code FontRuntimeSettings.getPageGlyphSize()}=64），
 * 生产渲染基准 13px 本就是对 64px 位图的降采样。故 {@code renderSegmentsToCollector} 的
 * {@code baseFontSizePx} 提到 13×N（N≤4 时 52px ≤ 64px）即<b>按字形 px 真放大</b>，
 * 零事后缩放、零新增代码路径——@Nx 与 @1x 共享同一语料、同一次解析/换行结果，只换倍率参数。
 * N=4 是「13N ≤ atlas 64」的最大整数倍；若显式调高 N（&gt;4），超出部分为 atlas 位图的
 * 纹理插值放大（有细节上限，非凭空造细节），profiles 须如实记录。</p>
 *
 * <p><b>断言纪律</b>：一切机器断言（ink 地板/行宽/行框顶单调/阶梯/LINK_REGION/对拍字段）
 * 只允许跑在 @1x；@Nx 仅供肉眼判读，文件名带 {@code @Nx} 后缀，画布短边以背景色补白到
 * ≥854×480（补白不改内容倍率，只保证文件达到 480P 级）。</p>
 */
final class MarkdownRenderScaleKit {

    /** 单一开关：系统属性 {@code qz.md.renderScale}（测试 JVM 收到时生效），默认 4。 */
    static final int N = readN();

    /** 判读副本画布下限（480P 级）。 */
    static final int MIN_CANVAS_W = 854;
    static final int MIN_CANVAS_H = 480;

    /** atlas 字形生成分辨率（FontConfig.awtCharSize 默认值；profiles 记录用）。 */
    static final int ATLAS_GLYPH_PX = 64;

    private static int readN() {
        String raw = null;
        try {
            raw = System.getProperty("qz.md.renderScale");
        } catch (SecurityException ignored) {
            raw = null;
        }
        if (raw == null || raw.trim().isEmpty()) {
            return 4;
        }
        try {
            int v = Integer.parseInt(raw.trim());
            return Math.max(1, Math.min(8, v));
        } catch (NumberFormatException ignored) {
            return 4;
        }
    }

    /** @return true = 需要另产 @Nx 判读副本（N==1 时与 @1x 重合，不产） */
    static boolean nxEnabled() {
        return N > 1;
    }

    /** @return 文件名后缀 "@Nx"（如 "@4x"） */
    static String nxSuffix() {
        return "@" + N + "x";
    }

    /** @return 真放大口径说明（renderPx 与 atlas 关系），写 profiles/diff 用 */
    static String detailReport(int basePx) {
        int renderPx = basePx * N;
        return "N=" + N + " renderPx=" + renderPx + " atlasPx=" + ATLAS_GLYPH_PX
                + (renderPx <= ATLAS_GLYPH_PX
                ? " 真放大(按字形px,零事后缩放)" : " 超atlas,超出部分为纹理插值放大");
    }

    /** 画布短边补白到 480P 级（仅 @Nx 用；补的是背景，不是内容）。 */
    static int padW(int w) {
        return Math.max(w, MIN_CANVAS_W);
    }

    static int padH(int h) {
        return Math.max(h, MIN_CANVAS_H);
    }

    /**
     * 段克隆 ×N：显式 {@code fontSizePx}（如 chat3 code 段 12px）乘 N，保持与基准同比缩放；
     * 未显式指定（0=继承）保持 0，由渲染调用侧以 base×N 传入。latex 原子经 forLatex 克隆。
     * n==1 时恒等返回原列表（@1x 路径零变化）。
     */
    static List<TextSegment> scaleSegments(List<TextSegment> line, int n) {
        if (n == 1) {
            return line;
        }
        List<TextSegment> out = new ArrayList<TextSegment>(line.size());
        for (TextSegment segment : line) {
            TextStyle style = segment.getStyle().copy();
            if (style.getFontSizePx() > 0) {
                style.setFontSizePx(style.getFontSizePx() * n);
            }
            out.add(segment.isLatex()
                    ? TextSegment.forLatex(segment.getLatexSource(), style)
                    : new TextSegment(segment.getText(), style));
        }
        return out;
    }

    static List<List<TextSegment>> scaleLines(List<List<TextSegment>> lines, int n) {
        if (n == 1) {
            return lines;
        }
        List<List<TextSegment>> out = new ArrayList<List<TextSegment>>(lines.size());
        for (List<TextSegment> line : lines) {
            out.add(scaleSegments(line, n));
        }
        return out;
    }

    private MarkdownRenderScaleKit() {
    }
}
