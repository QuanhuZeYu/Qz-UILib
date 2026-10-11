package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneLabel;
import club.heiqi.uilib.ui.scene.control.SceneRichText;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.TextContent;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;

/** /qzcore rich：正式页面入口验收样式、动态重排与可见链接；只记录业务目标。 */
final class CoreRichTextPage {
    private final GalleryEnvironment environment;
    private final java.util.function.Consumer<TestGallery.Page> navigate;
    CoreRichTextPage(GalleryEnvironment environment, java.util.function.Consumer<TestGallery.Page> navigate) {
        this.environment = environment;
        this.navigate = navigate;
    }


    private final String RICH =
            "基础样式：<b>粗体<b>重复粗体</b>恢复粗体</b>；<i>斜体</i>；<u>下划线 <size value=\"28\">大字号</size></u>；<s>删除线</s>。<br/>"
                    + "颜色：<color value=\"#35aaff\">蓝色</color>、<color value=\"#80ee6688\">显式半透明</color>。实体：&lt;b&gt; &amp; &gt;；§l在富文本模式保持字面。<br/>"
                    + "外层<a href=\"../文档?x=1&amp;y=2\">链接 A：多行正文 אבג 中文 👩‍💻，内层<a href=\"业务:内部目标\">链接 B</a>，恢复 A；缩窄窗口后应继续按每行可见内容点击。</a><br/>"
                    + "普通正文与行尾空白不计链接。<br/><a href=\"https://example.invalid/仅发布目标\">长链接：ABCDEFGHIJKLMNOPQRSTUVWXYZ 中文正文 中文正文 中文正文 中文正文</a><br/>";

    void buildPage(ScenePageContext ctx, java.util.List<SceneNode> roots) {
        Signal<TextContent.Mode> mode = ctx.domain().signal(TextContent.Mode.RICH_TAGS);
        Signal<Boolean> light = ctx.domain().signal(false),
                longer = ctx.domain().signal(false),
                invalid = ctx.domain().signal(false),
                enabled = ctx.domain().signal(true);
        Signal<Integer> fontSize = ctx.domain().signal(18),
                declarations = ctx.domain().signal(0),
                activations = ctx.domain().signal(0);
        Signal<String> latest = ctx.domain().signal("尚未点击链接");
        Computed<SceneTheme> theme =
                ctx.domain()
                        .computed(
                                ctx.scope(),
                                () -> {
                                    SceneTheme value =
                                            light.get() ? SceneTheme.light() : SceneTheme.dark();
                                    return value.withTextStyle(
                                            new TextStyle(
                                                    "default",
                                                    fontSize.get(),
                                                    value.textStyle.color));
                                });
        Computed<TextContent> content =
                ctx.domain()
                        .computed(
                                ctx.scope(),
                                () -> {
                                    declarations.get();
                                    String raw =
                                            invalid.get()
                                                    ? "<b>非法样例：这里不补闭合 <a href='目标'>整段字面呈现"
                                                    : RICH;
                                    if (longer.get()) raw = raw + "\n" + raw + "\n" + raw;
                                    return mode.get() == TextContent.Mode.PLAIN
                                            ? TextContent.plain(raw)
                                            : TextContent.richTags(raw);
                                });
        SceneNode rich =
                SceneRichText.create(
                                ctx,
                                content,
                                theme,
                                target -> {
                                    latest.set(target);
                                    activations.set(activations.get() + 1);
                                })
                        .size(Size.fill(1), Size.natural())
                        .bind(NodeBinding.value(enabled, SceneNode::enabled));
        SceneNode viewport =
                new SceneNode()
                        .size(Size.fill(1), Size.fixed(170))
                        .scrollable(true)
                        .scrollStep(28)
                        .padding(6)
                        .bind(
                                NodeBinding.value(
                                        theme, (node, value) -> node.background(value.button)))
                        .add(rich);
        SceneNode actions =
                new SceneNode()
                        .axis(SceneNode.Axis.ROW)
                        .size(Size.fill(1), Size.natural())
                        .gap(4)
                        .add(
                                SceneButton.create(
                                        ctx,
                                        () -> "模式：" + mode.get(),
                                        theme,
                                        () ->
                                                mode.set(
                                                        mode.get() == TextContent.Mode.RICH_TAGS
                                                                ? TextContent.Mode.PLAIN
                                                                : TextContent.Mode.RICH_TAGS)))
                        .add(
                                SceneButton.create(
                                        ctx,
                                        () -> "字号：" + fontSize.get(),
                                        theme,
                                        () -> fontSize.set(fontSize.get() == 18 ? 26 : 18)))
                        .add(
                                SceneButton.create(
                                        ctx,
                                        () -> light.get() ? "深色主题" : "浅色主题",
                                        theme,
                                        () -> light.set(!light.get())));
        SceneNode controls =
                new SceneNode()
                        .axis(SceneNode.Axis.ROW)
                        .size(Size.fill(1), Size.natural())
                        .gap(4)
                        .add(
                                SceneButton.create(
                                        ctx,
                                        () -> longer.get() ? "短内容" : "长内容",
                                        theme,
                                        () -> longer.set(!longer.get())))
                        .add(
                                SceneButton.create(
                                        ctx,
                                        () -> invalid.get() ? "正常样例" : "非法样例",
                                        theme,
                                        () -> invalid.set(!invalid.get())))
                        .add(
                                SceneButton.create(
                                        ctx,
                                        () -> "同文替换",
                                        theme,
                                        () -> declarations.set(declarations.get() + 1)))
                        .add(
                                SceneButton.create(
                                        ctx,
                                        () -> enabled.get() ? "禁用链接" : "启用链接",
                                        theme,
                                        () -> enabled.set(!enabled.get())));
        Computed<String> diagnostics =
                ctx.domain()
                        .computed(
                                ctx.scope(),
                                () -> {
                                    StringBuilder result = new StringBuilder("源码诊断：");
                                    for (TextLayout.Diagnostic diagnostic :
                                            content.get()
                                                    .resolve(theme.get().textStyle)
                                                    .text
                                                    .diagnostics())
                                        result.append(diagnostic.message)
                                                .append(" [")
                                                .append(diagnostic.sourceStart)
                                                .append(',')
                                                .append(diagnostic.sourceEnd)
                                                .append("] ");
                                    return result.toString();
                                });
        SceneNode page =
                new SceneNode()
                        .size(Size.fill(1), Size.fill(1))
                        .scrollable(true)
                        .padding(10)
                        .gap(6)
                        .bind(
                                NodeBinding.value(
                                        theme, (node, value) -> node.background(value.panel)))
                        .add(SceneLabel.create(ctx, () -> "基础富文本与链接：只发布目标，不打开外部程序", theme))
                        .add(actions)
                        .add(controls)
                        .add(
                                SceneLabel.create(
                                        ctx,
                                        () -> "点击次数：" + activations.get() + "；最近目标：" + latest.get(),
                                        theme))
                        .add(
                                SceneLabel.create(
                                        ctx,
                                        () -> "操作：切模式/字号/主题 → 缩窄窗口和滚动 → 点击 A/B 与空白，观察目标与计数。",
                                        theme))
                        .add(viewport)
                        .add(SceneLabel.create(ctx, diagnostics, theme))
                        .add(
                                SceneLabel.create(
                                        ctx,
                                        () -> "内层视口裁剪与外层页面滚动均使用 scene 几何；本页结果需在实际游戏观察。",
                                        theme))
                        .add(
                                SceneButton.create(
                                        ctx,
                                        () -> "返回核心页",
                                        theme,
                                        () ->
                                                navigate.accept(TestGallery.Page.CONTROLS)));
        roots.add(page);
    }

}
