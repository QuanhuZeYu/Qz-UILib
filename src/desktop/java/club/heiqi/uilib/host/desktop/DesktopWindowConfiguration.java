package club.heiqi.uilib.host.desktop;

import java.util.Objects;
import club.heiqi.uilib.ui.config.ConfigCodecs;
import club.heiqi.uilib.ui.config.ConfigKey;

/** 桌面应用所属的窗口与示例内容声明；保留既有windowProfile文档结构。 */
final class DesktopWindowConfiguration {
    private DesktopWindowConfiguration() {}
    static final class WindowProfile {
        final String title, text;
        final boolean dark;
        final int fontSize;
        WindowProfile(String title, String text, boolean dark, int fontSize) {
            this.title = Objects.requireNonNull(title, "title");
            this.text = Objects.requireNonNull(text, "text");
            if (fontSize < 8 || fontSize > 96) throw new IllegalArgumentException("fontSize必须在8至96之间");
            this.dark = dark;
            this.fontSize = fontSize;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof WindowProfile)) return false;
            WindowProfile p = (WindowProfile) other;
            return title.equals(p.title) && text.equals(p.text) && dark == p.dark && fontSize == p.fontSize;
        }
        @Override public int hashCode() { return Objects.hash(title, text, dark, fontSize); }
    }
    static final WindowProfile DEFAULT = new WindowProfile("UILib Desktop", "UILib desktop — live signals", true, 22);
    static final ConfigKey<WindowProfile> PROFILE = new ConfigKey<>("windowProfile",
            ConfigCodecs.<WindowProfile>object()
                .field("title", ConfigCodecs.STRING, DEFAULT.title, p -> p.title, "窗口标题")
                .field("text", ConfigCodecs.STRING, DEFAULT.text, p -> p.text, "页面标题文字")
                .field("dark", ConfigCodecs.BOOLEAN, DEFAULT.dark, p -> p.dark, "旧示例深色字段（读取兼容）；主题由ui.theme决定")
                .field("fontSize", ConfigCodecs.INTEGER, DEFAULT.fontSize, p -> p.fontSize, "文字字号：8至96")
                .build(v -> new WindowProfile((String)v.get("title"), (String)v.get("text"),
                        (Boolean)v.get("dark"), (Integer)v.get("fontSize"))), DEFAULT, p -> true)
            .withComment("桌面窗口与页面呈现对象；修改后按Ctrl+R或重载按钮。");
}
