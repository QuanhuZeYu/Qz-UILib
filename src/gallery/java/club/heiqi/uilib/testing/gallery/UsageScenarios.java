package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.DocumentResources;
import club.heiqi.uilib.ui.scene.control.SceneDocument;
import club.heiqi.uilib.ui.scene.control.SceneImage;
import club.heiqi.uilib.ui.scene.control.SceneLabel;
import club.heiqi.uilib.ui.scene.control.SceneMath;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.image.ImageOptions;
import club.heiqi.uilib.ui.scene.image.ImageSource;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.DocumentContent;
import club.heiqi.uilib.ui.scene.text.TextContent;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.math.MathContent;
import club.heiqi.uilib.ui.scene.text.math.MathEmbedMetrics;
import club.heiqi.uilib.ui.scene.text.math.MathStyle;

import java.util.LinkedHashMap;
import java.util.Map;

/** 真实任务组合内容能力；所有状态属于本次场景，宿主只提供资源事实。 */
final class UsageScenarios {
    private UsageScenarios() {}

    static SceneNode create(
            ScenePageContext context,
            GalleryEnvironment environment,
            TestGallery.Page page,
            ReadableSignal<SceneTheme> baseTheme) {
        Signal<Boolean> english =
                context.domain().signal("en_US".equals(environment.initialLanguage()));
        Signal<Boolean> narrow = context.domain().signal(false);
        Signal<Integer> em = context.domain().signal(16), value = context.domain().signal(1);
        Signal<Integer> articleSelection = context.domain().signal(0);
        Signal<Boolean> alternateIllustration = context.domain().signal(false);
        Signal<ImageSource> illustration =
                context.domain().signal(ImageSource.resource("qz_uilib_test:textures/sample.png"));
        ReadableSignal<int[][]> reportRows =
                context.domain()
                        .computed(context.scope(), () -> GallerySamples.reportRows(value.get()));
        Signal<String> link = context.domain().signal("链接尚未激活");
        ReadableSignal<SceneTheme> theme =
                context.domain()
                        .computed(
                                context.scope(),
                                () -> {
                                    SceneTheme base = baseTheme.get();
                                    return base.withTextStyle(
                                            base.textStyle.withEm(em.get()));
                                });
        Signal<ImageSource> item =
                context.domain()
                        .signal(
                                environment.hasItems()
                                        ? environment.item(false, 2, 0)
                                        : environment.embed(false));
        Map<String, DocumentResources.ImageReference> images = new LinkedHashMap<>();
        images.put(
                "material",
                new DocumentResources.ImageReference(
                        item,
                        context.domain()
                                .signal(ImageOptions.defaults().quantity(true).tooltip(true))));
        images.put(
                "illustration",
                new DocumentResources.ImageReference(
                        illustration, context.domain().signal(ImageOptions.defaults())));
        DocumentResources resources =
                new DocumentResources(
                        environment.images(),
                        context.domain().signal("https://example.test/guide/"),
                        images,
                        null);
        SceneNode root = new SceneNode().size(Size.fill(1), Size.fill(1)).gap(16).scrollable(true);
        root.add(GalleryStyle.label(context, () -> page.title, baseTheme, 28, false));
        root.add(GalleryStyle.label(context, () -> page.purpose, baseTheme, 15, true));
        SceneNode body = GalleryStyle.card(theme);
        root.add(GalleryStyle.frame(body, theme));
        root.add(GalleryStyle.label(context, () -> steps(page), baseTheme, 13, true));
        SceneNode actions =
                new SceneNode().size(Size.fill(1), Size.natural()).axis(SceneNode.Axis.ROW).gap(4);
        actions.add(
                GalleryStyle.button(
                        context,
                        () -> english.get() ? "中文" : "English",
                        theme,
                        () -> english.set(!english.get())));
        actions.add(
                GalleryStyle.button(
                        context,
                        () -> "字号：" + em.get(),
                        theme,
                        () -> em.set(em.get() == 16 ? 20 : 16)));
        actions.add(
                GalleryStyle.button(
                        context,
                        () -> narrow.get() ? "舒展阅读" : "专注阅读",
                        theme,
                        () -> narrow.set(!narrow.get())));
        body.add(actions);
        if (page == TestGallery.Page.ARTICLE) {
            SceneNode documents =
                    new SceneNode()
                            .size(Size.fill(1), Size.natural())
                            .axis(SceneNode.Axis.ROW)
                            .gap(8);
            String[] names = {"工坊指南", "排产核对", "交接备忘"};
            for (int index = 0; index < names.length; index++) {
                final int selected = index;
                documents.add(
                        GalleryStyle.button(
                                context,
                                () -> names[selected],
                                theme,
                                () -> articleSelection.set(selected)));
            }
            body.add(documents);
            body.add(
                    GalleryStyle.button(
                            context,
                            () -> "切换插图",
                            theme,
                            () -> {
                                alternateIllustration.set(!alternateIllustration.get());
                                illustration.set(
                                        alternateIllustration.get()
                                                ? environment.embed(true)
                                                : ImageSource.resource(
                                                        "qz_uilib_test:textures/sample.png"));
                            }));
            body.add(
                    GalleryStyle.label(
                            context,
                            () -> alternateIllustration.get() ? "当前插图：交接标记" : "当前插图：工坊示意图",
                            theme,
                            13,
                            true));
        }
        if (page == TestGallery.Page.DETAILS) {
            body.add(
                    GalleryStyle.button(
                            context,
                            () -> "制作批次：" + value.get(),
                            theme,
                            () -> {
                                int batches = value.get() == 1 ? 3 : 1;
                                value.set(batches);
                                item.set(
                                        environment.hasItems()
                                                ? environment.item(false, batches * 2, 0)
                                                : environment.embed(batches == 3));
                            }));
            body.add(
                    SceneLabel.create(
                            context,
                            () -> "材料需求：" + value.get() * 2 + "；产出：" + value.get() * 4,
                            theme));
            body.add(
                    SceneLabel.create(
                            context,
                            () ->
                                    environment.hasItems()
                                            ? "MC物品专项：悬停材料图标查看物品提示与数量。"
                                            : "MC物品与原版提示专项：当前宿主不支持；彩色位图只表示通用材料图像。",
                            theme));
        }
        if (page == TestGallery.Page.REPORT) {
            body.add(
                    GalleryStyle.button(
                            context,
                            () -> value.get() == 1 ? "切换到本周" : "切换到今日",
                            theme,
                            () -> value.set(value.get() == 1 ? 7 : 1)));
            body.add(
                    SceneLabel.create(
                            context, () -> GallerySamples.reportSummary(reportRows.get()), theme));
        }
        if (page == TestGallery.Page.MESSAGES) {
            body.add(
                    GalleryStyle.button(
                            context, () -> "接收新消息", theme, () -> value.set(value.get() + 1)));
            body.add(SceneLabel.create(context, () -> "消息总数：" + value.get(), theme));
            // 普通页面只声明策略与真实内容；repeat 保持消息身份，空间和字号变化由公共网格重排。
            SceneNode list = GalleryStyle.grid(context, theme);
            list.bind(NodeBinding.value(narrow, (node, compact) -> node.size(
                    compact ? Size.fill(1).bounds(0, 560) : Size.fill(1), Size.natural())));
            list.bind(
                    NodeBinding.repeat(
                            () -> {
                                java.util.List<Integer> ids = new java.util.ArrayList<>();
                                for (int i = value.get(); i > Math.max(0, value.get() - 12); i--)
                                    ids.add(i);
                                return ids;
                            },
                            id -> id,
                            id -> {
                                Signal<Boolean> expanded = context.domain().signal(false);
                                SceneNode card = GalleryStyle.card(theme);
                                card.add(
                                        SceneLabel.create(
                                                context,
                                                () ->
                                                        "消息 #"
                                                                + id.get()
                                                                + " · "
                                                                + (english.get()
                                                                        ? "Workshop ready 👩‍💻"
                                                                        : "工坊任务就绪 👩‍💻"),
                                                theme));
                                card.add(
                                        GalleryStyle.button(
                                                context,
                                                () ->
                                                        (expanded.get() ? "收起消息 #" : "展开消息 #")
                                                                + id.get(),
                                                theme,
                                                () -> expanded.set(!expanded.get())));
                                SceneNode detail =
                                        document(
                                                context,
                                                () ->
                                                        GallerySamples.message(
                                                                english.get(), id.get()),
                                                theme,
                                                resources,
                                                link);
                                detail.bind(NodeBinding.value(expanded, SceneNode::visible));
                                card.add(detail);
                                return GalleryStyle.frame(card, theme);
                            }));
            body.add(list);
        } else {
            SceneNode reader = new SceneNode().size(Size.fill(1), Size.natural());
            reader.bind(
                    NodeBinding.value(
                            narrow,
                            (node, valueNarrow) ->
                                    node.size(
                                            valueNarrow
                                                    ? Size.fill(1).bounds(0, 560)
                                                    : Size.fill(1),
                                            Size.natural())));
            reader.add(
                    markdown(
                            context,
                            () ->
                                    page == TestGallery.Page.ARTICLE
                                            ? GallerySamples.article(
                                                    articleSelection.get(), english.get())
                                            : page == TestGallery.Page.DETAILS
                                                    ? GallerySamples.recipe(
                                                            english.get(), value.get())
                                                    : GallerySamples.report(
                                                            english.get(),
                                                            value.get(),
                                                            reportRows.get()),
                            theme,
                            resources,
                            link));
            if (page == TestGallery.Page.DETAILS) {
                SceneNode formulaCard = GalleryStyle.card(theme);
                formulaCard.add(
                        GalleryStyle.label(
                                context,
                                () -> english.get() ? "Material and packing check" : "材料与托盘核对",
                                theme,
                                18,
                                false));
                formulaCard.add(
                        GalleryStyle.label(
                                context,
                                () ->
                                        english.get()
                                                ? "The material icon shares the selected batch with the packing formula."
                                                : "材料图标与托盘计算共用所选批次；悬停图标核对宿主提供的材料事实。",
                                theme,
                                13,
                                true));
                // 共同文档的数学段落没有嵌入槽；专属配方卡片直接复用正式 SceneMath 的图像槽。
                SceneNode material =
                        SceneImage.themed(
                                environment.images(),
                                item,
                                () -> ImageOptions.defaults().quantity(true).tooltip(true),
                                theme);
                Map<String, SceneMath.Embed> slots = new LinkedHashMap<>();
                slots.put(
                        "material",
                        new SceneMath.Embed(material, new MathEmbedMetrics(1.6f, 1.6f, 1.3f)));
                ReadableSignal<MathContent> formula =
                        context.domain()
                                .computed(
                                        context.scope(),
                                        () ->
                                                MathContent.parse(
                                                        GallerySamples.materialFormula(
                                                                value.get())));
                formulaCard.add(
                        SceneMath.create(context, formula, theme, MathStyle.DISPLAY, slots));
                reader.add(GalleryStyle.frame(formulaCard, theme));
            }
            if (page == TestGallery.Page.REPORT) {
                reader.add(document(context,
                        () -> english.get()
                                ? "<mark>Verify before dispatch</mark>: CO<sub>2</sub> / m<sup>3</sup> · <spacing value=\"1.5\">READY</spacing> · <spacing value=\"-0.5\">compact</spacing>"
                                : "<mark>发货前核对</mark>：CO<sub>2</sub> / m<sup>3</sup> · <spacing value=\"1.5\">就绪 READY</spacing> · <spacing value=\"-0.5\">紧凑排版</spacing>",
                        theme, resources, link));
            }
            body.add(reader);
        }
        body.add(GalleryStyle.label(context, link, theme, 13, true));
        return root;
    }

    private static SceneNode document(
            ScenePageContext context,
            ReadableSignal<String> source,
            ReadableSignal<SceneTheme> theme,
            DocumentResources resources,
            Signal<String> link) {
        // 文档声明身份保持到输入手势结束；条目淘汰时由逻辑节点释放派生与订阅。
        club.heiqi.uilib.ui.reactive.Owner scope = context.scope().child();
        ReadableSignal<DocumentContent> content =
                context.domain()
                        .computed(
                                scope,
                                () ->
                                        DocumentContent.paragraph(
                                                TextContent.richTags(source.get())));
        return SceneDocument.create(
                        context, content, theme, resources, target -> link.set("已激活链接：" + target))
                .size(Size.fill(1), Size.natural())
                .own(scope);
    }

    private static SceneNode markdown(
            ScenePageContext context,
            ReadableSignal<String> source,
            ReadableSignal<SceneTheme> theme,
            DocumentResources resources,
            Signal<String> link) {
        ReadableSignal<DocumentContent> content =
                context.domain()
                        .computed(
                                context.scope(),
                                () ->
                                        club.heiqi.uilib.ui.scene.text.MarkdownDocument.parse(
                                                source.get()));
        return SceneDocument.create(
                        context, content, theme, resources, target -> link.set("已激活链接：" + target))
                .size(Size.fill(1), Size.natural());
    }

    private static String steps(TestGallery.Page page) {
        switch (page) {
            case ARTICLE:
                return "操作：选择工坊指南、排产核对或交接备忘，切换插图、语言、字号与阅读宽度，再点击文中链接。预期：阅读设置保留，插图更新，超长指令和不可拆公式只在各自区域横滚。";
            case DETAILS:
                return "操作：切换制作批次。预期：材料数量、产出、公式与图标同步更新；切页回来仍保留批次。";
            case MESSAGES:
                return "操作：接收新消息、展开第一条详情，切换语言、字号与阅读宽度。预期：消息卡片按可用空间自动换列，真实正文决定行高，重排和切页保留展开状态。显示最近12条。";
            case REPORT:
                return "操作：切换今日与本周。预期：汇总数字、计算公式与文字说明同步变化。";
            default:
                throw new IllegalArgumentException("不是内容场景：" + page);
        }
    }
}
