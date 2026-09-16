package club.heiqi.uilib.internal.devtools.headless;

import java.nio.file.Path;

/**
 * 一次出图的产物：像素文件 + 自检 + 命令面摘要 + 能力快照 + 耗时。
 *
 * <p>不变量：<b>像素必须带证据</b>——自检回答「画出来没有」，命令面摘要回答「下发过什么命令」。
 * 只返回路径而不给出证据，是历史上「空白图被当成代码 bug」与「静默 no-op 无人察觉」的直接来源。</p>
 */
public final class HeadlessArtifact {

    private final HeadlessRequest request;
    private final HeadlessCapabilities capabilities;
    private final HeadlessSelfCheck.Report selfCheck;
    private final HeadlessDrawSummary drawSummary;
    private final Path output;
    private final long pngBytes;
    private final long elapsedMillis;

    HeadlessArtifact(HeadlessRequest request, HeadlessCapabilities capabilities,
            HeadlessSelfCheck.Report selfCheck, HeadlessDrawSummary drawSummary, Path output, long pngBytes,
            long elapsedMillis) {
        this.request = request;
        this.capabilities = capabilities;
        this.selfCheck = selfCheck;
        this.drawSummary = drawSummary;
        this.output = output;
        this.pngBytes = pngBytes;
        this.elapsedMillis = elapsedMillis;
    }

    /** @return 源请求 */
    public HeadlessRequest request() {
        return request;
    }

    /** @return 出图时的能力快照 */
    public HeadlessCapabilities capabilities() {
        return capabilities;
    }

    /** @return 像素自检报告 */
    public HeadlessSelfCheck.Report selfCheck() {
        return selfCheck;
    }

    /** @return 命令面摘要 */
    public HeadlessDrawSummary drawSummary() {
        return drawSummary;
    }

    /** @return PNG 绝对或相对路径 */
    public Path output() {
        return output;
    }

    /** @return PNG 字节数 */
    public long pngBytes() {
        return pngBytes;
    }

    /** @return 本次 capture 的墙钟耗时（毫秒） */
    public long elapsedMillis() {
        return elapsedMillis;
    }

    /** @return 多行可读摘要（CLI 默认输出） */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("[headless] request: ").append(request.summary()).append('\n');
        sb.append("[headless] capabilities: ").append(capabilities.summary()).append('\n');
        sb.append("[headless] commands: ").append(drawSummary.describe()).append('\n');
        sb.append("[headless] output: ").append(output).append(" (").append(pngBytes).append(" bytes)")
                .append('\n');
        sb.append("[headless] self-check: ").append(selfCheck.summary()).append('\n');
        for (String note : selfCheck.notes()) {
            sb.append("[headless]   - ").append(note).append('\n');
        }
        sb.append("[headless] elapsed: ").append(elapsedMillis).append(" ms");
        return sb.toString();
    }
}
