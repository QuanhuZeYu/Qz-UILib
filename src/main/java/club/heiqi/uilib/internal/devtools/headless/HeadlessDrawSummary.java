package club.heiqi.uilib.internal.devtools.headless;

import java.util.List;

/**
 * 命令面摘要：本帧经 {@code UiRenderBackend} 下发的绘制命令统计与几何并集。
 *
 * <p>存在理由：{@code UiRenderBackend} 的 {@code drawImage} / {@code drawSegments} /
 * {@code publishTextDemand} 都带静默 no-op 兜底，「没抛异常」不等于「画出来了」。
 * 命令面（有多少条绘制指令、覆盖到什么范围）与像素面（有多少墨迹）是两条独立证据，
 * 二者交叉才能判定「出图完整性」，而不是只看 PNG 是否存在。</p>
 *
 * <p>计数是<b>入口调用数</b>（同一命令经不同重载转发时按最外层入口计一次），够用即可；
 * 本类不做逐命令轨迹（那是 {@code RecordingRenderBackend} 的职责，位于测试域）。</p>
 */
public final class HeadlessDrawSummary {

    private final int viewportWidth;
    private final int viewportHeight;

    private int fillRectCount;
    private int drawSurfaceCount;
    private int drawBorderCount;
    private int drawTextCount;
    private int drawSegmentsCount;
    private int drawImageCount;
    private int clipPushCount;
    private int clipPopCount;
    private int groupOpacityCount;
    private int textDemandCount;
    private int textDemandChars;
    private int textChars;
    private int segmentsCount;

    private int rectCommands;
    private int rectsOutsideViewport;
    private boolean hasBounds;
    private int minX = Integer.MAX_VALUE;
    private int minY = Integer.MAX_VALUE;
    private int maxX = Integer.MIN_VALUE;
    private int maxY = Integer.MIN_VALUE;

    HeadlessDrawSummary(int viewportWidth, int viewportHeight) {
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
    }

    private void recordRect(int left, int top, int right, int bottom) {
        rectCommands++;
        if (right < 0 || bottom < 0 || left > viewportWidth || top > viewportHeight) {
            rectsOutsideViewport++;
        }
        if (!hasBounds) {
            hasBounds = true;
            minX = left;
            minY = top;
            maxX = right;
            maxY = bottom;
            return;
        }
        minX = Math.min(minX, left);
        minY = Math.min(minY, top);
        maxX = Math.max(maxX, right);
        maxY = Math.max(maxY, bottom);
    }

    void recordFillRect(int left, int top, int right, int bottom) {
        fillRectCount++;
        recordRect(left, top, right, bottom);
    }

    void recordDrawSurface(int left, int top, int right, int bottom) {
        drawSurfaceCount++;
        recordRect(left, top, right, bottom);
    }

    void recordDrawBorder(int left, int top, int right, int bottom) {
        drawBorderCount++;
        recordRect(left, top, right, bottom);
    }

    void recordClipPush(int left, int top, int right, int bottom) {
        clipPushCount++;
        recordRect(left, top, right, bottom);
    }

    void recordClipPop() {
        clipPopCount++;
    }

    void recordText(String text) {
        drawTextCount++;
        textChars += text == null ? 0 : text.length();
    }

    void recordSegments(List<?> segments) {
        drawSegmentsCount++;
        segmentsCount += segments == null ? 0 : segments.size();
    }

    void recordImage() {
        drawImageCount++;
    }

    void recordGroupOpacity() {
        groupOpacityCount++;
    }

    void recordTextDemand(List<String> texts) {
        textDemandCount++;
        if (texts != null) {
            for (String text : texts) {
                textDemandChars += text == null ? 0 : text.length();
            }
        }
    }

    /** @return 几何类绘制命令总数（fillRect + drawSurface + drawBorder） */
    public int drawCommands() {
        return fillRectCount + drawSurfaceCount + drawBorderCount + drawTextCount + drawSegmentsCount
                + drawImageCount;
    }

    /** @return fillRect 次数 */
    public int fillRectCount() {
        return fillRectCount;
    }

    /** @return drawSurface 次数 */
    public int drawSurfaceCount() {
        return drawSurfaceCount;
    }

    /** @return drawBorder 次数 */
    public int drawBorderCount() {
        return drawBorderCount;
    }

    /** @return drawText 次数 */
    public int drawTextCount() {
        return drawTextCount;
    }

    /** @return drawText 声明的字符总数 */
    public int textChars() {
        return textChars;
    }

    /** @return drawSegments 次数 */
    public int drawSegmentsCount() {
        return drawSegmentsCount;
    }

    /** @return 富文本段总数 */
    public int segmentsCount() {
        return segmentsCount;
    }

    /** @return drawImage 次数 */
    public int drawImageCount() {
        return drawImageCount;
    }

    /** @return 文本需求发布次数（publishTextDemand 调用数） */
    public int textDemandCount() {
        return textDemandCount;
    }

    /** @return 文本需求字符总数 */
    public int textDemandChars() {
        return textDemandChars;
    }

    /** @return pushClip 次数 */
    public int clipPushCount() {
        return clipPushCount;
    }

    /** @return popClip 次数 */
    public int clipPopCount() {
        return clipPopCount;
    }

    /** @return pushGroupOpacity 次数 */
    public int groupOpacityCount() {
        return groupOpacityCount;
    }

    /** @return 矩形命令总数 */
    public int rectCommands() {
        return rectCommands;
    }

    /** @return 完全落在视口外的矩形命令数 */
    public int rectsOutsideViewport() {
        return rectsOutsideViewport;
    }

    /** @return 是否有几何命令 */
    public boolean hasBounds() {
        return hasBounds;
    }

    /** @return 几何并集，格式 {@code minX,minY..maxX,maxY}；无命令时为 {@code (empty)} */
    public String bounds() {
        return hasBounds ? minX + "," + minY + ".." + maxX + "," + maxY : "(empty)";
    }

    /** @return 单行摘要，用于 artifact 与 CLI 输出 */
    public String describe() {
        return "commands=" + drawCommands()
                + " [fill=" + fillRectCount + " surface=" + drawSurfaceCount + " border=" + drawBorderCount
                + " text=" + drawTextCount + "/" + textChars + "ch segments=" + drawSegmentsCount
                + " image=" + drawImageCount + "]"
                + " clip=" + clipPushCount + "/" + clipPopCount + "(入口计数，含实现内部重入)"
                + " opacity=" + groupOpacityCount
                + " textDemand=" + textDemandCount + "/" + textDemandChars + "ch"
                + " bounds=" + bounds()
                + " outsideViewport=" + rectsOutsideViewport;
    }
}
