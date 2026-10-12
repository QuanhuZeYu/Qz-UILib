package club.heiqi.uilib.host.desktop;

import java.util.Objects;
import club.heiqi.uilib.ui.config.ConfigCodecs;
import club.heiqi.uilib.ui.config.ConfigKey;
import club.heiqi.uilib.ui.config.ConfigSession;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneLabel;
import club.heiqi.uilib.ui.scene.control.SceneTextField;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.TextStyle;

/** 桌面示例只声明内容与业务意图；排列、编辑及滚动仍由核心负责。 */
final class DesktopPage {
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
    Signal<Integer> counter;
    SceneEditState editing;
    SceneNode root, title, button, reloadButton, field, scroll;
    int builds;

    void build(ScenePageContext ctx, ConfigSession config, Runnable reload) {
        builds++;
        ReactiveDomain d = ctx.domain();
        counter = d.signal(0);
        editing = new SceneEditState(d, "Edit here");
        Computed<SceneTheme> theme = d.computed(ctx.scope(), () -> {
            WindowProfile p = config.get(PROFILE);
            SceneTheme base = ctx.theme().get();
            return base.withTextStyle(new TextStyle("default", p.fontSize, base.textStyle.color));
        });
        title = SceneLabel.create(ctx, () -> config.get(PROFILE).text, theme);
        button = SceneButton.create(ctx, () -> "Count: " + counter.get(), theme,
                () -> counter.set(counter.get() + 1));
        reloadButton = SceneButton.create(ctx, () -> "Reload YAML (Ctrl+R)", theme, reload);
        field = SceneTextField.create(ctx, editing, theme, () -> false,
                submission -> editing.accept(submission, submission.text));
        scroll = new SceneNode().size(Size.fill(1), Size.fill(1)).scrollable(true).clip(true).gap(8);
        for (int i = 1; i <= 32; i++) {
            final int row = i;
            scroll.add(SceneLabel.create(ctx, () -> "Row " + row + " · " + config.get(PROFILE).text, theme));
        }
        root = new SceneNode().size(Size.fill(1), Size.fill(1)).padding(16).gap(10)
                .bind(NodeBinding.value(theme, (n, t) -> n.background(t.panel)))
                .add(title).add(button).add(reloadButton).add(field).add(scroll);
        ctx.mount(root);
    }
}
