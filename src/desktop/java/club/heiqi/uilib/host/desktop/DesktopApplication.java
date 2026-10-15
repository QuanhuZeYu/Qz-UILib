package club.heiqi.uilib.host.desktop;

import java.nio.file.Path;
import java.nio.file.Paths;

/** 独立生产入口；默认打开正式设置页，业务可使用DesktopWindow.open(Path, ScenePage)。 */
public final class DesktopApplication {
    private DesktopApplication() {}
    public static void main(String[] args) throws Exception {
        Path path = Paths.get("config/desktop.yaml");
        for (int i = 0; i < args.length; i++) {
            if ("--config".equals(args[i]) && i + 1 < args.length) path = Paths.get(args[++i]);
            else throw new IllegalArgumentException("用法: DesktopApplication [--config <path>]");
        }
        try (DesktopWindow window = DesktopWindow.open(path)) {
            while (!window.shouldClose()) window.frame();
        }
    }
}
