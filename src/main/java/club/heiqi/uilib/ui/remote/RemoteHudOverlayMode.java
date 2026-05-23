package club.heiqi.uilib.ui.remote;

/**
 * 远程 HUD 浮层展示模式。
 */
public enum RemoteHudOverlayMode {

    /** 需要用户主动关闭的居中浮窗。 */
    DIALOG,

    /** 自动消失的角落提示浮窗。 */
    TOAST,

    /** 横向飘过屏幕的视频弹幕式浮层。 */
    DANMAKU
}
