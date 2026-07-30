package club.heiqi.uilib.ui.image;

/** ItemStack icon 栅格化及宿主状态恢复结果。 */
public final class HostImageRenderOutcome {

    /** 协调器必须据此决定发布、降级或中止 host frame。 */
    public enum Status {
        PUBLISHABLE,
        UNAVAILABLE,
        HOST_STATE_LOST
    }

    private final Status status;
    private final String stage;
    private final String detail;
    private final Throwable failure;

    private HostImageRenderOutcome(Status status, String stage, String detail, Throwable failure) {
        this.status = status;
        this.stage = stage;
        this.detail = detail;
        this.failure = failure;
    }

    /** @return 内容已绘制且宿主状态验证通过的可发布结果 */
    public static HostImageRenderOutcome publishable() {
        return new HostImageRenderOutcome(Status.PUBLISHABLE, "complete", null, null);
    }

    /** 创建可恢复、不可发布的结果。 */
    public static HostImageRenderOutcome unavailable(String stage, Throwable failure, String detail) {
        return new HostImageRenderOutcome(Status.UNAVAILABLE, stage, detail, failure);
    }

    /** 创建无法恢复或验证宿主状态的结果。 */
    public static HostImageRenderOutcome hostStateLost(String stage, Throwable failure, String detail) {
        return new HostImageRenderOutcome(Status.HOST_STATE_LOST, stage, detail, failure);
    }

    public Status getStatus() { return status; }
    public boolean isPublishable() { return status == Status.PUBLISHABLE; }
    public boolean isUnavailable() { return status == Status.UNAVAILABLE; }
    public boolean isHostStateLost() { return status == Status.HOST_STATE_LOST; }
    public String getStage() { return stage; }
    public String getDetail() { return detail; }
    public Throwable getFailure() { return failure; }
}
