package club.heiqi.uilib.host.desktop;

import java.nio.file.Path;
import java.nio.file.Paths;

/** 独立配置入口；使用与正式桌面窗口相同的配置路径默认值。 */
public final class DesktopSettingsMain {
    private DesktopSettingsMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length > 1) throw new IllegalArgumentException("用法: DesktopSettingsMain [配置文件路径]");
        Path path = Paths.get(args.length == 0 ? "config/desktop.yaml" : args[0]);
        try (DesktopWindow window = DesktopWindow.openSettings(path)) {
            while (!window.shouldClose()) window.frame();
        }
    }
}
