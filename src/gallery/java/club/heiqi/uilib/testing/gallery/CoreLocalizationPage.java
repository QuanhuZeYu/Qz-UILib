package club.heiqi.uilib.testing.gallery;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneLabel;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.localization.LocalizationBinding;
import club.heiqi.uilib.ui.scene.localization.LocalizationFlow;
import club.heiqi.uilib.ui.scene.localization.LocalizationResource;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.TextStyle;

/** /qzcore lang短验收页：同页切语言/参数/重载，固定区自然高度，正文独立滚动。
 * 使用独立 classpath 译文与响应主题，切换和重载只改变本页自己的流。 */
final class CoreLocalizationPage {
    private final GalleryEnvironment environment;
    private final java.util.function.Consumer<TestGallery.Page> navigate;
    CoreLocalizationPage(GalleryEnvironment environment, java.util.function.Consumer<TestGallery.Page> navigate) {
        this.environment = environment;
        this.navigate = navigate;
    }


    void buildPage(ScenePageContext ctx, java.util.List<SceneNode> roots) {
        // 本页只提供中英两套资料：宿主偏好仅映射初始选择，其他偏好在本页选择英文。
        String language =
                environment.initialLanguage();
        LocalizationFlow flow;
        try {
            flow =
                    new LocalizationFlow(
                            language,
                            Locale.ROOT,
                            LocalizationResource.classpath(
                                    CoreLocalizationPage.class.getClassLoader(),
                                    "uilib/localization/core-loop"));
        } catch (IOException failure) {
            throw new UncheckedIOException("独立语言验收页加载失败", failure);
        }
        ctx.own(flow); // Owner 先关闭子作用域中的绑定，再关闭本页私有流。
        LocalizationBinding localization = ctx.localization(flow);
        Signal<Integer> count = ctx.domain().signal(1),
                fontSize = ctx.domain().signal(20),
                reloads = ctx.domain().signal(0);
        Signal<Boolean> light = ctx.domain().signal(false), longTitle = ctx.domain().signal(false);
        int[] observedLoads = {-1};
        // 只计实际资料通知，不把点击重载按钮当作加载成功证据；普通计数不成为Effect依赖。
        ctx.domain()
                .effect(
                        ctx.scope(),
                        () -> {
                            localization.snapshot().get();
                            reloads.set(++observedLoads[0]);
                        });
        Computed<SceneTheme> theme =
                ctx.domain()
                        .computed(
                                ctx.scope(),
                                () -> {
                                    SceneTheme base =
                                            light.get() ? SceneTheme.light() : SceneTheme.dark();
                                    return base.withTextStyle(
                                            new TextStyle(
                                                    "default",
                                                    fontSize.get(),
                                                    base.textStyle.color));
                                });
        Computed<String> key =
                ctx.domain()
                        .computed(
                                ctx.scope(),
                                () ->
                                        longTitle.get()
                                                ? "qz_uilib.lang.title.long"
                                                : "qz_uilib.lang.title");
        SceneNode panel =
                new SceneNode()
                        .size(Size.fill(1), Size.fill(1))
                        .padding(10)
                        .gap(6)
                        .bind(
                                NodeBinding.value(
                                        theme, (node, value) -> node.background(value.panel)));
        panel.add(SceneLabel.create(ctx, localization.text(key, count), theme));
        panel.add(
                SceneLabel.create(
                        ctx,
                        localization.text(
                                () -> "qz_uilib.lang.status", localization.language(), reloads),
                        theme));
        SceneNode actions =
                new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural()).gap(4);
        actions.add(
                SceneButton.create(
                        ctx,
                        localization.text("qz_uilib.lang.switch"),
                        theme,
                        () ->
                                select(
                                        flow,
                                        "zh_CN".equals(localization.language().get())
                                                ? "en_US"
                                                : "zh_CN")));
        actions.add(
                SceneButton.create(
                        ctx,
                        localization.text("qz_uilib.lang.parameter"),
                        theme,
                        () -> count.set(count.get() + 1)));
        actions.add(
                SceneButton.create(
                        ctx,
                        localization.text("qz_uilib.lang.key"),
                        theme,
                        () -> longTitle.set(!longTitle.get())));
        actions.add(
                SceneButton.create(
                        ctx, localization.text("qz_uilib.lang.reload"), theme, () -> reload(flow)));
        panel.add(actions);
        panel.add(
                new SceneNode()
                        .axis(SceneNode.Axis.ROW)
                        .size(Size.fill(1), Size.natural())
                        .gap(4)
                        .add(
                                SceneButton.create(
                                        ctx,
                                        localization.text("qz_uilib.lang.theme"),
                                        theme,
                                        () -> light.set(!light.get())))
                        .add(
                                SceneButton.create(
                                        ctx,
                                        localization.text("qz_uilib.lang.font"),
                                        theme,
                                        () -> fontSize.set(fontSize.get() == 20 ? 28 : 20)))
                        .add(
                                SceneButton.create(
                                        ctx,
                                        localization.text("qz_uilib.lang.back"),
                                        theme,
                                        () ->
                                                navigate.accept(TestGallery.Page.CONTROLS))));
        SceneNode body =
                new SceneNode()
                        .size(Size.fill(1), Size.fill(1))
                        .scrollable(true)
                        .scrollStep(32)
                        .gap(6);
        body.add(SceneLabel.create(ctx, localization.text("qz_uilib.lang.instructions"), theme));
        for (int row = 1; row <= 24; row++) {
            final int index = row;
            body.add(
                    SceneLabel.create(
                            ctx,
                            localization.text(() -> "qz_uilib.lang.row", () -> index, count),
                            theme));
        }
        panel.add(body);
        roots.add(panel);
    }

    private void select(LocalizationFlow flow, String language) {
        try {
            flow.select(language);
        } catch (IOException failure) {
            throw new UncheckedIOException("独立语言选择失败", failure);
        }
    }

    private void reload(LocalizationFlow flow) {
        try {
            flow.reload();
        } catch (IOException failure) {
            throw new UncheckedIOException("独立语言重载失败", failure);
        }
    }

}
