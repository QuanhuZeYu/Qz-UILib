package club.heiqi.uilib.testing.gallery.mail;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.control.ThemeMode;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;

/** 页面局部呈现意图与响应策略；业务消息仍由外部模型拥有。 */
final class MailViewState implements AutoCloseable {
    private final Owner scope;
    private final Signal<Float> fontScale;
    final Signal<Boolean> collapsed;
    final ReadableSignal<MailPalette> palette;
    final ReadableSignal<SceneTheme> theme;
    final ReadableSignal<Boolean> wide,split,compact;

    MailViewState(ScenePageContext p,ReadableSignal<SceneTheme> base,ReadableSignal<ThemeMode> mode,
                  ReadableSignal<Boolean> reading,boolean standalone) {
        scope=p.domain().owner();fontScale=p.domain().signal(1f);collapsed=p.domain().signal(false);
        palette=p.domain().computed(scope,() -> MailPalette.from(base.get().withTextStyle(base.get().textStyle
                .withEm(base.get().textStyle.logicalEm*(standalone?14f/15f:1f)*fontScale.get())),mode.get()));
        theme=p.domain().computed(scope,() -> palette.get().theme);
        wide=p.domain().computed(scope,() -> p.viewportWidth().get()>=Math.ceil(theme.get().textStyle.logicalEm*70));
        split=p.domain().computed(scope,() -> p.viewportWidth().get()>=Math.ceil(theme.get().textStyle.logicalEm*80));
        compact=p.domain().computed(scope,() -> !wide.get()||reading.get());
    }
    void toggleNavigation() {collapsed.set(!collapsed.get());}
    String fontActionName() {return fontScale.get()==1f?"放大文字":"标准文字";}
    void toggleFont() {fontScale.set(fontScale.get()==1f?1.25f:1f);}
    @Override public void close() {scope.dispose();}
}
