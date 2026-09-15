package club.heiqi.config.ui.theme;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;

/**
 * 配置页专用主题偏好；仅消费已有主题配方，不改变库默认主题或全局滤镜质量。
 * 启动加载、保存和重载经配置桥写入同一信号；页面的订阅随 Owner 释放。
 * 内部装配入口，非公共接入 API。
 */
public final class ConfigThemePreference {

    private static final SceneTheme FLAT = SceneTheme.solidDark();
    private static final SceneTheme GLASS = SceneTheme.liquidGlassDark();
    private static final Signal<SceneTheme> THEME = Signal.create(FLAT);

    private ConfigThemePreference() {}

    public static ReadableSignal<SceneTheme> signal() {
        return THEME;
    }

    /** 缺键或非法值回落平面档，保证旧配置首次打开也不启用背景采样。 */
    public static void applyConfigured(String value) {
        THEME.set(value != null && "glass".equalsIgnoreCase(value.trim()) ? GLASS : FLAT);
    }
}
