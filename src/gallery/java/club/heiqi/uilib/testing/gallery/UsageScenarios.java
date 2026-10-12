package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.DocumentResources;
import club.heiqi.uilib.ui.scene.control.SceneDocument;
import club.heiqi.uilib.ui.scene.control.SceneLabel;
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
        Signal<String> link = context.domain().signal("链接尚未激活");
        ReadableSignal<SceneTheme> theme =
                context.domain()
                        .computed(
                                context.scope(),
                                () -> {
                                    SceneTheme base = baseTheme.get();
                                    return base.withTextStyle(
                                            new TextStyle(
                                                    "default", em.get(), base.textStyle.color));
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
                        context.domain()
                                .signal(ImageSource.resource("qz_uilib_test:textures/sample.png")),
                        context.domain().signal(ImageOptions.defaults())));
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
                            context,
                            () ->
                                    "生产汇总：投入 "
                                            + value.get() * 12
                                            + "；完成 "
                                            + value.get() * 9
                                            + "；待处理 "
                                            + value.get() * 3,
                            theme));
        }
        if (page == TestGallery.Page.MESSAGES) {
            body.add(
                    GalleryStyle.button(
                            context, () -> "接收新消息", theme, () -> value.set(value.get() + 1)));
            body.add(SceneLabel.create(context, () -> "消息总数：" + value.get(), theme));
            SceneNode list = new SceneNode().size(Size.fill(1), Size.natural()).gap(8);
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
                                                        english.get()
                                                                ? "Operator: café / العربية /"
                                                                      + " हिन्दी. Completed"
                                                                      + " <latex>2+2=4</latex>. <a"
                                                                      + " href=\"activity\">Activity"
                                                                      + " details</a>"
                                                                : "操作员：小明 / café / العربية /"
                                                                      + " हिन्दी。完成"
                                                                      + " <latex>2+2=4</latex>"
                                                                      + " 批次。<a"
                                                                      + " href=\"activity\">查看活动记录</a>",
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
                    page == TestGallery.Page.ARTICLE
                            ? markdown(
                                    context, () -> article(english.get()), theme, resources, link)
                            : document(
                                    context,
                                    () -> content(page, english.get(), value.get()),
                                    theme,
                                    resources,
                                    link));
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

    private static String article(boolean english) {
        return (english
                        ? "---\nCategory: Workshop\nStatus: Ready\n---\n\n# Workshop guide\n\n"
                              + "A production batch combines **materials**, calculations and"
                              + " operator instructions. "
                        : "---\n分类: 工坊\n状态: 已确认\n---\n\n# 工坊使用说明\n\n一份生产任务同时包含**材料**、计算和操作指引。")
                + "![工坊示意图](illustration)\n\n"
                + (english ? "For two inputs per batch: " : "每批需要两份材料，批次数与总需求的关系为：")
                + "[$N=2b$](checklist)\n\n"
                + (english
                        ? "> Keep the instructions and material count together.\n\n"
                              + "3. Check the inputs\n"
                              + "4. Confirm the batch\n"
                              + "   - Review the checklist\n\n"
                        : "> 操作指引与材料数量保持在同一文档中。\n\n3. 核对材料\n4. 确认批次\n   - 复查任务清单\n\n")
                + "---\n\n$$\nN=2b\n$$\n\n"
                + "```text\ncount = batches * 2; // $literal$ **code** <plain>\n```\n\n"
                + (english
                        ? "| Material | Quantity | Status |\n"
                              + "| :--- | ---: | :---: |\n"
                              + "| [Input](checklist) | $2b$ | **Ready** |\n"
                              + "| Water | 12 | Confirmed |\n\n"
                        : "| 材料 | 数量 | 状态 |\n"
                              + "| :--- | ---: | :---: |\n"
                              + "| [输入材料](checklist) | $2b$ | **就绪** |\n"
                              + "| 水 | 12 | 已确认 |\n\n")
                + (english
                        ? "<p>HTML notes: <strong>keep the count</strong> and <a"
                              + " href=\"checklist\">read the instructions</a>.<br>Code stays"
                              + " literal: <code>$x$ **text**</code>.</p>\n\n"
                        : "<p>HTML 备注：<strong>保留数量</strong>，并<a"
                              + " href=\"checklist\">阅读操作说明</a>。<br>代码保留字面：<code>$x$"
                              + " **文字**</code>。</p>\n\n")
                + (english
                        ? "Read the [checklist](checklist) before starting. Contact"
                              + " help@example.test."
                        : "开始前阅读[任务清单](checklist)。联系 help@example.test。");
    }

    private static String steps(TestGallery.Page page) {
        switch (page) {
            case ARTICLE:
                return "操作：切换语言、字号与阅读宽度，查看文章元数据、表格和 HTML 备注，再点击文中链接。预期：同一文章重排，表格列对齐与链接位置一致，超宽内容仅在表格内滚动。";
            case DETAILS:
                return "操作：切换制作批次。预期：材料数量、产出、公式与图标同步更新；切页回来仍保留批次。";
            case MESSAGES:
                return "操作：接收新消息、展开第一条详情并切换语言。预期：新增记录可见，展开状态保留。列表显示最近12条。";
            case REPORT:
                return "操作：切换今日与本周。预期：汇总数字、计算公式与文字说明同步变化。";
            default:
                throw new IllegalArgumentException("不是内容场景：" + page);
        }
    }

    private static String content(TestGallery.Page page, boolean english, int value) {
        switch (page) {
            case ARTICLE:
                return (english
                                ? "Workshop guide\n\n"
                                      + "A production batch combines materials, calculations and"
                                      + " operator instructions. "
                                : "工坊使用说明\n\n一份生产任务同时包含材料、计算和操作指引。")
                        + "<img src=\"illustration\" width=\"96\" height=\"96\" alt=\"工坊示意图\"/>\n\n"
                        + (english ? "For two inputs per batch: " : "每批需要两份材料，批次数与总需求的关系为：")
                        + "<a href=\"checklist\"><latex>N=2b</latex></a>\n\n"
                        + (english
                                ? "Read the checklist before starting. Keep the same document open"
                                      + " when changing language or reading width. "
                                : "开始前阅读清单；改变语言与阅读宽度时，内容仍处于同一阅读场景。")
                        + "<a href=\"checklist\">"
                        + (english ? "Open checklist" : "查看操作清单")
                        + "</a>\n\n"
                        + (english
                                ? "Operator notes: café, العربية, हिन्दी, 👩‍💻.\n\n"
                                      + "Inspect material quantities, then confirm the task. The"
                                      + " task description and formula stay together as the"
                                      + " viewport changes."
                                : "操作员备注：中文、café、العربية、हिन्दी、👩‍💻。\n\n"
                                      + "核对材料数量，然后确认任务。窗口变化时，说明和公式在同一内容流中自然换行。");
            case DETAILS:
                return (english ? "Recipe: precision component" : "配方：精密组件")
                        + "\n\n<img src=\"material\" width=\"48\" height=\"48\" alt=\"材料图像\"/> "
                        + (english ? "Material consumption" : "材料消耗")
                        + " <latex>2 \\times "
                        + value
                        + "="
                        + value * 2
                        + "</latex>\n\n"
                        + (english ? "Output" : "组件产出")
                        + " <latex>4 \\times "
                        + value
                        + "="
                        + value * 4
                        + "</latex>\n\n"
                        + (english
                                ? "Use the batch selector above to plan production. "
                                : "通过上方批次选择规划生产；材料与产出共享同一批次权威值。")
                        + "<a href=\"recipe\">"
                        + (english ? "Recipe instructions" : "查看配方说明")
                        + "</a>";
            case REPORT:
                return (english ? "Production report" : "生产报告")
                        + "\n\n"
                        + (value == 1
                                ? (english ? "Period: today" : "统计周期：今日")
                                : (english ? "Period: this week" : "统计周期：本周"))
                        + "\n\n<latex>"
                        + value * 12
                        + "="
                        + value * 9
                        + "+"
                        + value * 3
                        + "</latex>\n\n"
                        + (english ? "Completion rate: " : "完成率：")
                        + "<latex>\\frac{"
                        + value * 9
                        + "}{"
                        + value * 12
                        + "}=75\\%</latex>\n\n"
                        + (english
                                ? "Pending tasks remain in the selected period. "
                                : "待处理任务计入所选周期；数字和公式随周期一起更新。")
                        + "<img src=\"illustration\" width=\"64\" height=\"64\" alt=\"报告标识\"/> <a"
                        + " href=\"report\">"
                        + (english ? "Report details" : "查看报告详情")
                        + "</a>";
            default:
                throw new IllegalArgumentException("不是文档场景：" + page);
        }
    }
}
