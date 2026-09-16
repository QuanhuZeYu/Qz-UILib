package club.heiqi.uilib.internal.devtools.headless;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 像素自检：回答「这张图到底画出来没有」，让 agent 在判读前先能区分「图有效」与「图没画出来」。
 *
 * <p>为什么必需：{@code UiRenderBackend} 存在静默 no-op 兜底与资源缺失路径，历史上出现过
 * 「headless 全绿而真机翻车」与「空白图被误判成代码 bug」。本类只做像素侧事实统计，
 * 不做逐像素金样比对（GL 输出依赖驱动实现，不作金样）。</p>
 *
 * <p>alpha 也纳入统计：UI 面板多为半透明玻璃配方，缺宿主背景时整帧 alpha 会停在极低值，
 * 导出后看似「白底淡字」；只统计「非透明像素」会把这种情况误判为通过。</p>
 */
public final class HeadlessSelfCheck {

    /** 判定为「有内容」的最小非透明像素占比。 */
    private static final double MIN_INK_RATIO = 0.005d;
    /** 判定为「不透明底」的最小 alpha=255 像素占比。 */
    private static final double MIN_OPAQUE_RATIO = 0.5d;
    /** 颜色统计采样步长（像素），避免大图逐像素建集合。 */
    private static final int COLOR_SAMPLE_STEP = 7;

    private HeadlessSelfCheck() {
    }

    /** 自检报告：像素事实 + GL 错误 + 结论，逐项可读。 */
    public static final class Report {

        private final int width;
        private final int height;
        private final int inkPixels;
        private final int opaquePixels;
        private final double inkRatio;
        private final double opaqueRatio;
        private final double meanAlpha;
        private final int sampledColors;
        private final int glError;
        private final boolean ok;
        private final List<String> notes;

        Report(int width, int height, int inkPixels, int opaquePixels, double inkRatio, double opaqueRatio,
                double meanAlpha, int sampledColors, int glError, boolean ok, List<String> notes) {
            this.width = width;
            this.height = height;
            this.inkPixels = inkPixels;
            this.opaquePixels = opaquePixels;
            this.inkRatio = inkRatio;
            this.opaqueRatio = opaqueRatio;
            this.meanAlpha = meanAlpha;
            this.sampledColors = sampledColors;
            this.glError = glError;
            this.ok = ok;
            this.notes = Collections.unmodifiableList(new ArrayList<String>(notes));
        }

        /** @return 是否通过自检 */
        public boolean ok() {
            return ok;
        }

        /** @return alpha != 0 的像素数 */
        public int inkPixels() {
            return inkPixels;
        }

        /** @return alpha = 255 的像素数 */
        public int opaquePixels() {
            return opaquePixels;
        }

        /** @return alpha != 0 的像素占比 */
        public double inkRatio() {
            return inkRatio;
        }

        /** @return alpha = 255 的像素占比 */
        public double opaqueRatio() {
            return opaqueRatio;
        }

        /** @return 平均 alpha（0~255） */
        public double meanAlpha() {
            return meanAlpha;
        }

        /** @return 采样到的不同颜色数 */
        public int sampledColors() {
            return sampledColors;
        }

        /** @return glGetError 结果（0 = 无错误） */
        public int glError() {
            return glError;
        }

        /** @return 结论说明（含未通过原因与提示） */
        public List<String> notes() {
            return notes;
        }

        /** @return 单行摘要 */
        public String summary() {
            return (ok ? "ok" : "FAILED")
                    + " " + width + "x" + height
                    + " ink=" + String.format(Locale.ROOT, "%.2f%%", inkRatio * 100d)
                    + " inkPx=" + inkPixels
                    + " opaquePx=" + opaquePixels
                    + " meanAlpha=" + String.format(Locale.ROOT, "%.1f", meanAlpha)
                    + " colors=" + sampledColors
                    + " glError=" + glError;
        }
    }

    /**
     * 检查一帧 ARGB 像素。
     *
     * @param argb    行主序 ARGB 像素（长度必须为 width * height）
     * @param width   像素宽
     * @param height  像素高
     * @param glError 帧末 glGetError 结果
     * @return 自检报告
     */
    public static Report inspect(int[] argb, int width, int height, int glError) {
        return inspect(argb, width, height, glError, null);
    }

    /**
     * 检查一帧 ARGB 像素，并与命令面摘要交叉验证出图完整性。
     *
     * <p>为什么要交叉：命令面与像素面是两条独立证据。只有命令没像素 = 帧前置/绑定/裁剪问题；
     * 只有像素没命令 = 记录器没挂上。两者都不该被当作「UI 画得不好」。</p>
     *
     * @param argb        行主序 ARGB 像素（长度必须为 width * height）
     * @param width       像素宽
     * @param height      像素高
     * @param glError     帧末 glGetError 结果
     * @param drawSummary 命令面摘要；可为 null（不做交叉判据）
     * @return 自检报告
     */
    public static Report inspect(int[] argb, int width, int height, int glError,
            HeadlessDrawSummary drawSummary) {
        if (argb == null || argb.length != width * height) {
            throw new HeadlessFailure(HeadlessFailure.Stage.READBACK,
                    "像素缓冲长度不符：期望 " + (width * height) + "，实际 " + (argb == null ? -1 : argb.length));
        }
        int ink = 0;
        int opaque = 0;
        long alphaSum = 0L;
        Set<Integer> colors = new HashSet<Integer>();
        for (int i = 0; i < argb.length; i++) {
            int px = argb[i];
            int alpha = (px >>> 24) & 0xFF;
            if (alpha != 0) {
                ink++;
            }
            if (alpha >= 250) {
                opaque++;
            }
            alphaSum += alpha;
            if (i % COLOR_SAMPLE_STEP == 0) {
                colors.add(Integer.valueOf(px));
            }
        }
        int total = argb.length;
        double inkRatio = total == 0 ? 0d : (double) ink / (double) total;
        double opaqueRatio = total == 0 ? 0d : (double) opaque / (double) total;
        double meanAlpha = total == 0 ? 0d : (double) alphaSum / (double) total;
        List<String> notes = new ArrayList<String>();
        boolean ok = true;
        if (glError != 0) {
            ok = false;
            notes.add("glGetError=" + glError + "（渲染期存在 GL 错误）");
        }
        if (ink == 0) {
            ok = false;
            notes.add("整帧全透明：不是「UI 没画」，而是 headless 链路没有产出像素");
        } else if (inkRatio < MIN_INK_RATIO) {
            ok = false;
            notes.add("墨水量过低：inkRatio=" + inkRatio + " < " + MIN_INK_RATIO);
        }
        if (colors.size() <= 1 && ink > 0) {
            notes.add("整帧只有一种颜色：可能是纯色底而非真实 UI");
        }
        if (ink > 0 && opaqueRatio < MIN_OPAQUE_RATIO) {
            notes.add("整帧以半透明像素为主（meanAlpha=" + String.format(Locale.ROOT, "%.1f", meanAlpha)
                    + "，不透明占比=" + String.format(Locale.ROOT, "%.2f%%", opaqueRatio * 100d)
                    + "）：若期望不透明判读，请把宿主背景设为不透明（--bg=RRGGBB）");
        }
        if (drawSummary != null) {
            if (drawSummary.drawCommands() > 0 && ink == 0) {
                ok = false;
                notes.add("命令面有 " + drawSummary.drawCommands() + " 条绘制命令但像素全空："
                        + "帧前置语义 / 帧缓冲绑定 / 裁剪栈问题（不是「UI 没画」）");
            }
            if (drawSummary.drawCommands() == 0 && ink > 0) {
                notes.add("像素有内容但命令面为空：命令记录上下文可能未挂上");
            }
            if (drawSummary.rectCommands() > 0
                    && drawSummary.rectsOutsideViewport() == drawSummary.rectCommands()) {
                ok = false;
                notes.add("全部 " + drawSummary.rectCommands() + " 条矩形命令都落在视口外（bounds="
                        + drawSummary.bounds() + "，视口=" + width + "x" + height + "）");
            } else if (drawSummary.rectsOutsideViewport() > 0) {
                notes.add(drawSummary.rectsOutsideViewport() + "/" + drawSummary.rectCommands()
                        + " 条矩形命令完全落在视口外（bounds=" + drawSummary.bounds() + "，视口="
                        + width + "x" + height + "）：小视口下页面可能存在溢出内容，属提示而非失败");
            }
        }
        if (ok) {
            notes.add("像素自检通过");
        }
        return new Report(width, height, ink, opaque, inkRatio, opaqueRatio, meanAlpha, colors.size(), glError, ok,
                notes);
    }
}
