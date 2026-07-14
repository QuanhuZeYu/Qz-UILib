package club.heiqi.uilib.ui.render;

/** GL 状态无法可信恢复时用于中止当前 UI 帧的 fail-closed 信号。 */
public final class UiRenderFrameAbortException extends RuntimeException {
    public UiRenderFrameAbortException(String message) { super(message); }
    public UiRenderFrameAbortException(String message, Throwable cause) { super(message, cause); }
}
