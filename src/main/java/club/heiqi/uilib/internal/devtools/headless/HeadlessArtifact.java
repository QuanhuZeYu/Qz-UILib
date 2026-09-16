package club.heiqi.uilib.internal.devtools.headless;

import java.nio.file.Path;

/**
 * 一次出图的产物：像素文件 + 自检 + 能力快照 + 耗时。
 *
 * <p>不变量：<b>像素必须带自检</b>——只返回路径而不给出「图里有没有内容」的证据，是历史上
 * 「空白图被当成代码 bug」的直接来源，因此本对象把两者绑在一起返回。</p>
 */
public final class HeadlessArtifact {

    private final HeadlessRequest request;
    private final HeadlessCapabilities capabilities;
    private final HeadlessSelfCheck.Report selfCheck;
    private final Path output;
    private final long pngBytes;
    private final long elapsedMillis;

    HeadlessArtifact(HeadlessRequest request, HeadlessCapabilities capabilities,
            HeadlessSelfCheck.Report selfCheck, Path output, long pngBytes, long elapsedMillis) {
        this.request = request;
        this.capabilities = capabilities;
        this.selfCheck = selfCheck;
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
