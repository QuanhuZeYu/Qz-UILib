package club.heiqi.uilib.ui.hud.api;

/** 可幂等关闭的 HUD 注册句柄。 */
public interface HudRegistration extends AutoCloseable {
    /** 注销 HUD；重复调用无副作用。必须在客户端主线程调用。 */
    @Override
    void close();
    /** 返回该注册是否已关闭。 */
    boolean isClosed();
}
