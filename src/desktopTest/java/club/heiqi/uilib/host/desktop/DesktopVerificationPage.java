package club.heiqi.uilib.host.desktop;

import java.util.Objects;
import club.heiqi.uilib.ui.config.ConfigSession;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneLabel;
import club.heiqi.uilib.ui.scene.control.SceneTextField;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.host.ScenePage;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.TextStyle;

/** 真实宿主回归的显式页面fixture；借用窗口会话，排列、编辑及滚动仍由核心负责。 */
final class DesktopVerificationPage implements ScenePage {
    final ConfigSession configuration;
    private final Runnable reload;
    ReactiveDomain domain;
    Signal<Integer> counter;
    SceneEditState editing;
    SceneNode root, title, button, reloadButton, field, scroll;
    int builds;

    DesktopVerificationPage(ConfigSession configuration, Runnable reload) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.reload = Objects.requireNonNull(reload, "reload");
    }

    @Override public void build(ScenePageContext ctx) {
        builds++;
        ReactiveDomain d = domain = ctx.domain();
        counter = d.signal(0);
        editing = new SceneEditState(d, "Edit here");
        Computed<SceneTheme> theme = d.computed(ctx.scope(), () -> {
            DesktopWindowConfiguration.WindowProfile p = configuration.get(DesktopWindowConfiguration.PROFILE);
            SceneTheme base = ctx.theme().get();
            return base.withTextStyle(new TextStyle("default", p.fontSize, base.textStyle.color));
        });
        title = SceneLabel.create(ctx, () -> configuration.get(DesktopWindowConfiguration.PROFILE).text, theme);
        button = SceneButton.create(ctx, () -> "Count: " + counter.get(), theme,
                () -> counter.set(counter.get() + 1));
        reloadButton = SceneButton.create(ctx, () -> "Reload YAML (Ctrl+R)", theme, reload);
        field = SceneTextField.create(ctx, editing, theme, () -> false,
                submission -> editing.accept(submission, submission.text));
        scroll = new SceneNode().size(Size.fill(1), Size.fill(1)).scrollable(true).clip(true).gap(8);
        for (int i = 1; i <= 32; i++) {
            final int row = i;
            scroll.add(SceneLabel.create(ctx, () -> "Row " + row + " · " + configuration.get(DesktopWindowConfiguration.PROFILE).text, theme));
        }
        root = new SceneNode().size(Size.fill(1), Size.fill(1)).padding(16).gap(10)
                .bind(NodeBinding.value(theme, (n, t) -> n.background(t.panel)))
                .add(title).add(button).add(reloadButton).add(field).add(scroll);
        ctx.mount(root);
    }
}
