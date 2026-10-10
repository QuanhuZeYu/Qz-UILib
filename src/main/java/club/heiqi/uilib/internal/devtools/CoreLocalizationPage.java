package club.heiqi.uilib.internal.devtools;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.Language;
import net.minecraft.client.resources.LanguageManager;
import club.heiqi.uilib.ui.host.minecraft.MinecraftSceneScreen;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneLabel;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.localization.LocalizationBinding;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.TextStyle;

/** /qzcore lang短验收页：同页切语言/参数/重载，固定区自然高度，正文独立滚动。
 * 使用宿主当前字体与响应主题，不修改字体配置；资源包覆盖的译文交给原版加载。 */
public final class CoreLocalizationPage extends MinecraftSceneScreen {
    public CoreLocalizationPage() { super(CoreLocalizationPage::buildPage); }
    private static void buildPage(ScenePageContext ctx) {
        LocalizationBinding localization = ctx.localization();
        Signal<Integer> count = ctx.domain().signal(1), fontSize = ctx.domain().signal(20), reloads = ctx.domain().signal(0);
        Signal<Boolean> light = ctx.domain().signal(false), longTitle = ctx.domain().signal(false);
        int[] observedLoads = {-1};
        // 只计实际资料通知，不把点击重载按钮当作加载成功证据；普通计数不成为Effect依赖。
        ctx.domain().effect(ctx.scope(), () -> { localization.snapshot().get(); reloads.set(++observedLoads[0]); });
        Computed<SceneTheme> theme = ctx.domain().computed(ctx.scope(), () -> {
            SceneTheme base = light.get() ? SceneTheme.light() : SceneTheme.dark();
            return base.withTextStyle(new TextStyle("default", fontSize.get(), base.textStyle.color));
        });
        Computed<String> key = ctx.domain().computed(ctx.scope(), () -> longTitle.get() ? "qz_uilib.lang.title.long" : "qz_uilib.lang.title");
        SceneNode panel = new SceneNode().size(Size.fill(1), Size.fill(1)).padding(10).gap(6)
            .bind(NodeBinding.value(theme, (node, value) -> node.background(value.panel)));
        panel.add(SceneLabel.create(ctx, localization.text(key, count), theme));
        panel.add(SceneLabel.create(ctx, localization.text(() -> "qz_uilib.lang.status", localization.language(), reloads), theme));
        SceneNode actions = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural()).gap(4);
        actions.add(SceneButton.create(ctx, localization.text("qz_uilib.lang.switch"), theme, () -> switchLanguage(localization.language().get())));
        actions.add(SceneButton.create(ctx, localization.text("qz_uilib.lang.parameter"), theme, () -> count.set(count.get() + 1)));
        actions.add(SceneButton.create(ctx, localization.text("qz_uilib.lang.key"), theme, () -> longTitle.set(!longTitle.get())));
        actions.add(SceneButton.create(ctx, localization.text("qz_uilib.lang.reload"), theme, () -> Minecraft.getMinecraft().refreshResources()));
        panel.add(actions);
        panel.add(new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural()).gap(4)
            .add(SceneButton.create(ctx, localization.text("qz_uilib.lang.theme"), theme, () -> light.set(!light.get())))
            .add(SceneButton.create(ctx, localization.text("qz_uilib.lang.font"), theme, () -> fontSize.set(fontSize.get() == 20 ? 28 : 20)))
            .add(SceneButton.create(ctx, localization.text("qz_uilib.lang.back"), theme, () -> Minecraft.getMinecraft().displayGuiScreen(new CoreLoopScreen()))));
        SceneNode body = new SceneNode().size(Size.fill(1), Size.fill(1)).scrollable(true).scrollStep(32).gap(6);
        body.add(SceneLabel.create(ctx, localization.text("qz_uilib.lang.instructions"), theme));
        for (int row = 1; row <= 24; row++) {
            final int index = row;
            body.add(SceneLabel.create(ctx, localization.text(() -> "qz_uilib.lang.row", () -> index, count), theme));
        }
        panel.add(body); ctx.mount(panel);
    }
    private static void switchLanguage(String current) {
        Minecraft minecraft = Minecraft.getMinecraft(); LanguageManager manager = minecraft.getLanguageManager();
        String next = "zh_CN".equals(current) ? "en_US" : "zh_CN";
        for (Object value : manager.getLanguages()) {
            Language language = (Language) value;
            if (!next.equals(language.getLanguageCode())) continue;
            manager.setCurrentLanguage(language); minecraft.gameSettings.language = next;
            minecraft.refreshResources(); minecraft.gameSettings.saveOptions(); return;
        }
        throw new IllegalStateException("宿主语言资料未提供 " + next);
    }
    @Override public boolean doesGuiPauseGame() { return false; }
}
