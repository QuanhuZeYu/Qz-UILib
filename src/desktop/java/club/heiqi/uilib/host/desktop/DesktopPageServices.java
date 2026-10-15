package club.heiqi.uilib.host.desktop;

import java.util.Objects;
import java.util.function.Consumer;
import club.heiqi.uilib.ui.config.ConfigSession;
import club.heiqi.uilib.ui.scene.image.ImageService;
import club.heiqi.uilib.ui.settings.SettingsConfiguration;

/** 页面借用已初始化的桌面宿主服务；有效期止于窗口关闭，不转移资源或配置所有权。
 * 工厂只装配页面；配置读写和设置动作在ScenePageContext所属状态域内执行。 */
public final class DesktopPageServices {
    private final ImageService images;
    private final SettingsConfiguration settings;
    private final ConfigSession configuration;
    private final Runnable reload;
    private Consumer<Runnable> closeRequest,reloadRequest;

    DesktopPageServices(ImageService images, SettingsConfiguration settings, ConfigSession configuration, Runnable reload) {
        this.images = Objects.requireNonNull(images, "images");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.reload = Objects.requireNonNull(reload, "reload");
    }

    public ImageService images() { return images; }
    public SettingsConfiguration settings() { return settings; }
    public ConfigSession configuration() { return configuration; }

    /** 显式加载真实配置文件并刷新资源；I/O失败以UncheckedIOException交给调用方。 */
    public void reloadConfiguration() { reload.run(); }

    /** 原生请求在已采集输入结算后交给页面；完成动作只在页面确认后调用。 */
    public void onCloseRequested(Consumer<Runnable> handler) {closeRequest=Objects.requireNonNull(handler,"handler");}
    public void onReloadRequested(Consumer<Runnable> handler) {reloadRequest=Objects.requireNonNull(handler,"handler");}
    boolean handlesClose(){return closeRequest!=null;}
    boolean handlesReload(){return reloadRequest!=null;}
    void closeRequested(Runnable complete){closeRequest.accept(complete);}
    void reloadRequested(){reloadRequest.accept(reload);}
    void releaseRequests(){closeRequest=null;reloadRequest=null;}
}
