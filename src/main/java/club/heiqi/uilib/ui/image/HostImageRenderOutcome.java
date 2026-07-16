package club.heiqi.uilib.ui.image;

/** 宿主图片栅格化及状态恢复结果。 */
public final class HostImageRenderOutcome {
    private final boolean rendered;
    private final boolean recovered;
    private final String stage;
    private final String detail;
    private final Throwable failure;

    private HostImageRenderOutcome(boolean rendered, boolean recovered, String stage, String detail,
            Throwable failure) {
        this.rendered = rendered;
        this.recovered = recovered;
        this.stage = stage;
        this.detail = detail;
        this.failure = failure;
    }

    /** @return 成功且状态验证通过的结果 */
    public static HostImageRenderOutcome success() {
        return new HostImageRenderOutcome(true, true, "complete", null, null);
    }

    /** 创建失败结果。 */
    public static HostImageRenderOutcome failure(String stage, Throwable failure, boolean recovered, String detail) {
        return new HostImageRenderOutcome(false, recovered, stage, detail, failure);
    }

    public boolean isRendered() { return rendered; }
    public boolean isRecovered() { return recovered; }
    public String getStage() { return stage; }
    public String getDetail() { return detail; }
    public Throwable getFailure() { return failure; }
}
