package club.heiqi.uilib.ui.hud.api;

/** 提供其它 HUD 已占用的四向安全区；无法可靠测量的第三方 HUD 不作猜测。 */
@FunctionalInterface
public interface HudAvoidanceProvider {
    /** 返回当前逻辑像素占位，null 视为无占位。 */
    HudInsets getInsets();
}
