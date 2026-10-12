package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneDocument;
import club.heiqi.uilib.ui.scene.control.SceneImage;
import club.heiqi.uilib.ui.scene.control.SceneMath;
import club.heiqi.uilib.ui.scene.control.SceneTextArea;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.image.ImageContent;
import club.heiqi.uilib.ui.scene.image.ImageOptions;
import club.heiqi.uilib.ui.scene.image.ImageSource;
import club.heiqi.uilib.ui.scene.image.ImageState;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.DocumentContent;
import club.heiqi.uilib.ui.scene.text.TextContent;
import club.heiqi.uilib.ui.scene.text.math.MathContent;
import club.heiqi.uilib.ui.scene.text.math.MathEmbedMetrics;
import club.heiqi.uilib.ui.scene.text.math.MathLayout;
import club.heiqi.uilib.ui.scene.text.math.MathStyle;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 图像与公式的人工情景；数据属于保留的页面，加载、排版、绘制与输入均走正式控件。 */
final class MediaScenarios {
    private static final String RESOURCE = "qz_uilib_test:textures/sample.png";
    private static final String MISSING = "qz_uilib_test:textures/media_scenario_missing.png";
    private static final String[][] SAMPLES = {
        {"复杂分式、根号与双脚本", "@frac{1+@frac{x_1^2}{@sqrt[3]{y^2+1}}}{@sqrt{1+@frac{a_i}{b^{n+1}}}}",
            "预期：嵌套分式、三次根号、上下标保留间隙；改变字号后整体重新排版。"},
        {"伸缩定界符与矩阵", "@left(@begin{matrix}@frac{1}{1+x}&@sqrt{y}&z_1@@a^2&@frac{b}{c}&d@@u&v&w@end{matrix}@right)",
            "预期：矩阵各行各列排列，圆括号随内部高度伸缩；窄区域只裁剪当前公式。"},
        {"多行对齐", "@begin{aligned}f(x)&=@frac{x^2+1}{@sqrt{x+2}}@@g(x)&=@sum_{i=1}^{n}i^2@end{aligned}",
            "预期：等号列对齐，多行基线分开；大算子随数学样式改变。"},
        {"本公式宏定义", "@newcommand{@sq}[1]{#1^2}@sq{x}+@sq{y}=@sq{z}",
            "预期：sq 在本公式内展开；切换其他样本不会继承隐藏全局宏。"},
        {"HTML 十六进制颜色", "@textcolor{#2563eb}{@frac{x_1^2}{@sqrt{y+1}}}+@textcolor{#e11d48}{@sum_{i=1}^{n}i}",
            "预期：蓝色分式与红色求和使用显式 HTML 色值，主题变化保留显式颜色。"},
        {"未知命令", "x+@galleryUnknown{y}",
            "预期：完整原公式可见并有来源诊断；点击任一合法样本恢复，下面的有效公式仍可用。"},
        {"缺失分母的语法错误", "@frac{1}",
            "预期：整式降级为源文，不出现残缺分式；恢复合法样本后重新呈现。"},
        {"HTML 命令的环境边界", "@htmlClass{gallery}{x^2}",
            "预期：按固定默认 trust=false 的正式结果显示不支持；不能把 HTML 命令当浏览器执行。"},
        {"宽公式局部溢出", "@sum_{i=1}^{n}@frac{x_i^2+@sqrt{y_i+1}}{1+z_i}+@prod_{j=1}^{m}@frac{a_j+b_j}{c_j+d_j}+@int_0^1@frac{1+t^2}{@sqrt{1-t^2}}@,dt",
            "预期：公式保持不可拆数学结构；指针停在公式区域滚轮横向查看，页外操作仍纵向排列。"}
    };
    private static final String[] EMBED_SAMPLES = {
        "@int_0^1@frac{@embed{main}+x}{@sqrt{1+x^2}}@,dx+y^{@embed{upper}}_{@embed{lower}}",
        "@left[@begin{matrix}@embed{main}&@frac{a}{b}@@x^{@embed{upper}}&y_{@embed{lower}}@end{matrix}@right]",
        "@embed{main}+@embed{main}",
        "@frac{@embed{unbound}}{1+x}"
    };

    private MediaScenarios() {}

    static SceneNode create(ScenePageContext context, GalleryEnvironment environment,
            ReadableSignal<SceneTheme> baseTheme) {
        Owner scope = context.scope().child();
        State state = new State(context, scope, environment, baseTheme);
        // 派生归本页逻辑生命周期；切页仅卸载时保留，结束广场销毁时释放。
        SceneNode page = new SceneNode().size(Size.fill(1), Size.fill(1))
                .gap(16).scrollable(true).own(scope);
        page.add(GalleryStyle.label(context, () -> "图像与公式", baseTheme, 28, false));
        page.add(GalleryStyle.label(context, () -> "依次操作来源、适配、物品与公式。每段都有预期；失败和不支持保留真实状态。切页返回保留本页设置与草稿。", baseTheme, 15, true));
        addButton(page, context, baseTheme, () -> "动态字号：" + state.em.get(),
                () -> state.em.set(state.em.get() == 18 ? 26 : state.em.get() == 26 ? 34 : 18));
        images(page, context, environment, state);
        items(page, context, environment, state);
        mathematics(page, context, state);
        embeds(page, context, environment, state);
        return page;
    }

    private static void images(SceneNode page, ScenePageContext context,
            GalleryEnvironment environment, State state) {
        SceneNode card = section(page, context, state.theme, "1. 来源、失败恢复与尺寸适配",
                "操作：选择资源、资源区域、位图或 MC 物品；选择不存在资源，再恢复有效资源或位图，也可重新加载当前来源。预期：真实状态区分 LOADING、READY、FAILED 与 UNSUPPORTED，失败占位保留，恢复无需重建节点。");
        addButton(card, context, state.theme, () -> "有效资源 / 恢复资源", () -> state.sourceMode.set(SourceMode.RESOURCE));
        addButton(card, context, state.theme, () -> "资源区域：左上 16 × 16", () -> state.sourceMode.set(SourceMode.REGION));
        addButton(card, context, state.theme, () -> "运行时位图", () -> state.sourceMode.set(SourceMode.BITMAP));
        addButton(card, context, state.theme, () -> "MC 物品来源", () -> state.sourceMode.set(SourceMode.ITEM));
        addButton(card, context, state.theme, () -> "不存在的资源", () -> state.sourceMode.set(SourceMode.MISSING));
        addButton(card, context, state.theme, () -> "重新加载当前来源", () -> environment.images().reload(state.source.get()));
        addButton(card, context, state.theme, () -> "位图内容：" + (state.alternate.get() ? "第二组颜色" : "第一组颜色"),
                () -> state.alternate.set(!state.alternate.get()));
        card.add(GalleryStyle.label(context, () -> "操作：选位图后切换 Fit、尺寸及对齐，观察四周颜色标记与留白；切换窄视口。预期：CONTAIN 完整等比、COVER 铺满裁剪、STRETCH 改变比例、NATIVE 保持原尺寸。原尺寸墨迹受有效裁剪限制；局部滚轮查看超出视口的布局盒。", state.theme, 14, true));
        for (ImageOptions.Fit fit : ImageOptions.Fit.values()) {
            addButton(card, context, state.theme, () -> (state.fit.get() == fit ? "已选 " : "选择 ") + fit,
                    () -> state.fit.set(fit));
        }
        addButton(card, context, state.theme, () -> "尺寸：" + sizeName(state.size.get()),
                () -> state.size.set((state.size.get() + 1) % 4));
        addButton(card, context, state.theme, () -> "水平对齐：" + alignmentName(state.alignX.get()),
                () -> state.alignX.set((state.alignX.get() + 1) % 3));
        addButton(card, context, state.theme, () -> "垂直对齐：" + alignmentName(state.alignY.get()),
                () -> state.alignY.set((state.alignY.get() + 1) % 3));
        addButton(card, context, state.theme, () -> state.narrow.get() ? "视口：窄（切换宽）" : "视口：宽（切换窄）",
                () -> state.narrow.set(!state.narrow.get()));
        SceneNode image = SceneImage.themed(environment.images(), state.source, state.imageOptions, state.theme);
        SceneNode viewport = horizontal(image);
        viewport.bind(NodeBinding.value(state.narrow, (node, value) ->
                node.size(Size.fill(1).bounds(0, value ? 180 : 480), Size.natural())));
        card.add(GalleryStyle.frame(viewport, state.theme));
        ReadableSignal<ImageState> status = SceneImage.state(image);
        card.add(GalleryStyle.label(context, () -> "当前来源：" + state.sourceMode.get() + " · " + state.source.get(), state.theme, 13, true));
        card.add(GalleryStyle.label(context, () -> "主图：" + imageStatus(status.get()), state.theme, 14, false));
        card.add(GalleryStyle.label(context, () -> "单边尺寸会在来源 READY 后沿真实比例补齐；自然尺寸使用真实像素尺寸。Fit 改变盒内呈现，尺寸按钮改变声明盒，两者可独立比较。", state.theme, 13, true));


    }

    private static void items(SceneNode page, ScenePageContext context,
            GalleryEnvironment environment, State state) {
        SceneNode card = section(page, context, state.theme, "2. 物品内容、覆盖层与提示",
                "操作：开关数量、耐久与 tooltip；改变 enchanted、amount、damage，再悬停图标。预期：同一图标更新实际宿主内容；覆盖层只在成功的物品分支按开关显示，提示不抢焦点。非物品来源不伪造数量或耐久。");
        card.add(GalleryStyle.label(context, () -> environment.hasItems()
                ? "宿主声明提供物品；实际加载结果仍以下方状态为准。当前 MC 供给会在钻石与附魔镐之间切换，damage 仅对有耐久的物品生效。"
                : "当前宿主未供给 MC 物品；下方保留 item 来源与 UNSUPPORTED 诊断。位图成功不能作为附魔、数量、耐久或物品提示通过的证据。", state.theme, 14, true));
        addButton(card, context, state.theme, () -> "数量覆盖层：" + onOff(state.quantity.get()),
                () -> state.quantity.set(!state.quantity.get()));
        addButton(card, context, state.theme, () -> "耐久覆盖层：" + onOff(state.durability.get()),
                () -> state.durability.set(!state.durability.get()));
        addButton(card, context, state.theme, () -> "悬浮 tooltip：" + onOff(state.tooltip.get()),
                () -> state.tooltip.set(!state.tooltip.get()));
        addButton(card, context, state.theme, () -> "enchanted：" + state.enchanted.get(),
                () -> state.enchanted.set(!state.enchanted.get()));
        addButton(card, context, state.theme, () -> "amount：" + state.amount.get(),
                () -> state.amount.set(state.amount.get() == 1 ? 32 : state.amount.get() == 32 ? 64 : 1));
        addButton(card, context, state.theme, () -> "damage：" + state.damage.get(),
                () -> state.damage.set(state.damage.get() == 0 ? 450 : state.damage.get() == 450 ? 900 : 0));
        SceneNode item = SceneImage.themed(environment.images(), state.item,
                () -> state.overlays().size(Size.fixed(96), Size.fixed(96)).alt("物品暂不可用"), state.theme);
        card.add(GalleryStyle.frame(horizontal(item), state.theme));
        ReadableSignal<ImageState> status = SceneImage.state(item);
        card.add(GalleryStyle.label(context, () -> imageStatus(status.get()), state.theme, 14, false));
        card.add(GalleryStyle.label(context, () -> "数量为 1、物品无损伤或无耐久事实时，宿主可能不提供对应覆盖层。开启 enchanted 再增加 damage 可核对当前 MC 镐的耐久；默认关闭三个覆盖选项。", state.theme, 13, true));
    }

    private static void mathematics(SceneNode page, ScenePageContext context, State state) {
        SceneNode card = section(page, context, state.theme, "3. 可编辑公式、数学样式与错误恢复",
                "操作：选择样本或编辑下方源文，预览随正文变化；Enter 插入换行，Ctrl+Enter 完成编辑。切换数学样式、动态字号和窄视口，再选择未知命令或语法错误。预期：失败呈现完整源文与诊断，切回合法样本恢复，其他有效内容继续显示。");
        for (int i = 0; i < SAMPLES.length; i++) {
            final int index = i;
            addButton(card, context, state.theme, () -> SAMPLES[index][0], () -> {
                // 这是用户明确选择新样本，允许重置草稿；普通主题/字号变化不重置编辑状态。
                state.editor.reset(tex(SAMPLES[index][1]));
                state.expected.set(SAMPLES[index][2]);
            });
        }
        card.add(GalleryStyle.label(context, state.expected, state.theme, 14, true));
        card.add(SceneTextArea.create(context, state.editor, state.theme, () -> false,
                submission -> state.editor.accept(submission, submission.text))
                .size(Size.fill(1), Size.fixed(150)));
        card.add(GalleryStyle.label(context, () -> parseStatus(state.formula.get()), state.theme, 13, true));
        card.add(GalleryStyle.label(context, () -> "MathStyle 没有单独 INLINE 枚举：行内默认使用 TEXT；DISPLAY、TEXT、SCRIPT、SCRIPT_SCRIPT 均沿实际枚举。scriptscript 是二级脚本样式。", state.theme, 13, true));
        for (MathStyle style : MathStyle.values()) {
            addButton(card, context, state.theme, () -> (state.mathStyle.get() == style ? "已选 " : "选择 ") + style,
                    () -> state.mathStyle.set(style));
        }
        final Map<MathStyle, SceneNode> formulas = new LinkedHashMap<>();
        for (MathStyle style : MathStyle.values()) {
            // MathStyle 是正式控件的构造声明。各样式保留各自节点，显隐绑定切换，不重挂同一实例。
            SceneNode formula = SceneMath.create(context, state.formula, state.theme, style, Collections.emptyMap());
            formulas.put(style, formula);
            SceneNode viewport = horizontal(formula);
            viewport.bind(NodeBinding.value(state.mathStyle, (node, value) -> node.visible(value == style)));
            viewport.bind(NodeBinding.value(state.narrow, (node, value) ->
                    node.size(Size.fill(1).bounds(0, value ? 180 : 640), Size.natural())));
            card.add(viewport);
        }
        addButton(card, context, state.theme, () -> "读取当前公式的排版诊断", () ->
                state.layoutFeedback.set(layoutStatus(formulas.get(state.mathStyle.get()))));
        card.add(GalleryStyle.label(context, () -> "诊断快照（改变内容或样式后可重新读取）：" + state.layoutFeedback.get(), state.theme, 13, true));
        card.add(GalleryStyle.label(context, () -> "解析通过只表示语法有效；缺少数学字体、伸缩字形或环境能力时，正式布局仍可能降级。读取排版诊断可区分解析、布局与字体阶段。", state.theme, 13, true));
        card.add(GalleryStyle.label(context, () -> "真实行内混排对照：修改字号或缩窄窗口，核对下面正文与公式的基线、行高和换行。它独立于上方错误样本。", state.theme, 14, true));
        DocumentContent inline = DocumentContent.paragraph(TextContent.richTags(
                "正文前 <latex>" + tex("@frac{x_i^2}{@sqrt{1+y}}") + "</latex> 正文后，行内公式与文字共享排版。"));
        card.add(SceneDocument.create(context, () -> inline, state.theme, null, target -> {}));
    }

    private static void embeds(SceneNode page, ScenePageContext context,
            GalleryEnvironment environment, State state) {
        SceneNode card = section(page, context, state.theme, "4. 数学图像嵌入与唯一身份边界",
                "操作：切换位图、MC 物品或不存在资源，切换积分/分式/脚本与矩阵样本，再改变字号和物品参数。预期：嵌入跟随数学 em 与脚本样式缩放，图像失败保留所在数学盒；可见物品提示沿同一输入管线。重复标识或缺失绑定时整式降级，恢复合法样本即可恢复。");
        addButton(card, context, state.theme, () -> "嵌入来源：运行时位图", () -> state.embedMode.set(SourceMode.BITMAP));
        addButton(card, context, state.theme, () -> "嵌入来源：MC 物品", () -> state.embedMode.set(SourceMode.ITEM));
        addButton(card, context, state.theme, () -> "嵌入来源：不存在资源", () -> state.embedMode.set(SourceMode.MISSING));
        String[] names = {"恢复积分、分式与双脚本", "矩阵内嵌图像", "重复嵌入标识错误", "缺失嵌入绑定错误"};
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            addButton(card, context, state.theme, () -> names[index], () -> state.embedSample.set(index));
        }
        Map<String, SceneMath.Embed> slots = new LinkedHashMap<>();
        SceneNode main = SceneImage.themed(environment.images(), state.embedSource,
                () -> state.overlays().alt("嵌入图像暂不可用"), state.theme);
        slots.put("main", new SceneMath.Embed(main, new MathEmbedMetrics(1.8f, 1.8f, 1.45f)));
        for (String id : new String[] {"upper", "lower"}) {
            SceneNode child = SceneImage.themed(environment.images(), state.embedSource,
                    () -> state.overlays().alt("嵌入图像暂不可用"), state.theme);
            slots.put(id, new SceneMath.Embed(child, new MathEmbedMetrics(1.8f, 1.8f, 1.45f)));
        }
        SceneNode formula = SceneMath.create(context, state.embedFormula, state.theme, MathStyle.DISPLAY, slots);
        SceneNode viewport = horizontal(formula);
        viewport.bind(NodeBinding.value(state.narrow, (node, value) ->
                node.size(Size.fill(1).bounds(0, value ? 180 : 640), Size.natural())));
        card.add(GalleryStyle.frame(viewport, state.theme));
        ReadableSignal<ImageState> status = SceneImage.state(main);
        card.add(GalleryStyle.label(context, () -> "嵌入来源：" + state.embedMode.get() + " · " + imageStatus(status.get()), state.theme, 13, true));
        addButton(card, context, state.theme, () -> "读取嵌入公式的排版诊断", () ->
                state.embedFeedback.set(layoutStatus(formula)));
        card.add(GalleryStyle.label(context, () -> "嵌入诊断快照：" + state.embedFeedback.get(), state.theme, 13, true));
        card.add(GalleryStyle.label(context, () -> "三个标识各有独立直属 SceneImage，可共享同一来源。图像服务归宿主；切页卸载释放呈现租约，页面销毁释放本页派生，迟到完成不由页面手动接管。", state.theme, 13, true));
    }

    private enum SourceMode { RESOURCE, REGION, BITMAP, ITEM, MISSING }

    private static final class State {
        final Signal<Integer> em, size, alignX, alignY, amount, damage, embedSample;
        final Signal<Boolean> alternate, narrow, enchanted, quantity, durability, tooltip;
        final Signal<SourceMode> sourceMode, embedMode;
        final Signal<ImageOptions.Fit> fit;
        final Signal<MathStyle> mathStyle;
        final Signal<String> expected, layoutFeedback, embedFeedback;
        final SceneEditState editor;
        final ReadableSignal<SceneTheme> theme;
        final ReadableSignal<ImageSource> source, item, embedSource;
        final ReadableSignal<ImageOptions> imageOptions;
        final ReadableSignal<MathContent> formula, embedFormula;

        State(ScenePageContext context, Owner scope, GalleryEnvironment environment,
                ReadableSignal<SceneTheme> baseTheme) {
            em = context.domain().signal(18);
            size = context.domain().signal(0);
            alignX = context.domain().signal(1);
            alignY = context.domain().signal(1);
            amount = context.domain().signal(32);
            damage = context.domain().signal(0);
            embedSample = context.domain().signal(0);
            alternate = context.domain().signal(false);
            narrow = context.domain().signal(false);
            enchanted = context.domain().signal(false);
            quantity = context.domain().signal(false);
            durability = context.domain().signal(false);
            tooltip = context.domain().signal(false);
            sourceMode = context.domain().signal(SourceMode.RESOURCE);
            embedMode = context.domain().signal(SourceMode.BITMAP);
            fit = context.domain().signal(ImageOptions.Fit.CONTAIN);
            mathStyle = context.domain().signal(MathStyle.DISPLAY);
            expected = context.domain().signal(SAMPLES[0][2]);
            layoutFeedback = context.domain().signal("尚未读取；在公式呈现后点击诊断按钮。");
            embedFeedback = context.domain().signal("尚未读取；在嵌入公式呈现后点击诊断按钮。");
            editor = new SceneEditState(context.domain(), tex(SAMPLES[0][1]), SceneEditState.Mode.MULTI_LINE);
            theme = context.domain().computed(scope, () -> baseTheme.get().withTextStyle(baseTheme.get().textStyle.withEm(em.get())));
            // 两个位图 identity 在本页内保留，切换只换来源；不在 measure/paint 或每次读取时造纹理。
            ImageSource firstBitmap = bitmap(false), secondBitmap = bitmap(true);
            ImageSource resource = ImageSource.resource(RESOURCE);
            ImageSource region = ImageSource.resource(RESOURCE, new ImageSource.Region(0, 0, 16, 16));
            ImageSource missing = ImageSource.resource(MISSING);
            ReadableSignal<ImageSource> bitmap = context.domain().computed(scope,
                    () -> alternate.get() ? secondBitmap : firstBitmap);
            item = context.domain().computed(scope, () -> environment.item(enchanted.get(), amount.get(), damage.get()));
            source = context.domain().computed(scope, () -> {
                switch (sourceMode.get()) {
                    case RESOURCE: return resource;
                    case REGION: return region;
                    case BITMAP: return bitmap.get();
                    case ITEM: return item.get();
                    case MISSING: return missing;
                    default: throw new IllegalStateException("未知来源模式");
                }
            });
            embedSource = context.domain().computed(scope, () -> embedMode.get() == SourceMode.ITEM
                    ? item.get() : embedMode.get() == SourceMode.MISSING ? missing : bitmap.get());
            imageOptions = context.domain().computed(scope, () -> {
                Size width = size.get() == 3 ? Size.natural() : Size.fixed(size.get() == 1 ? 96 : 160);
                Size height = size.get() >= 2 ? Size.natural() : Size.fixed(size.get() == 1 ? 160 : 96);
                return new ImageOptions(width, height, fit.get(), alignX.get() * .5f, alignY.get() * .5f,
                        32, 32, "预览暂不可用，请查看加载状态", false)
                        .quantity(quantity.get()).durability(durability.get()).tooltip(tooltip.get());
            });
            formula = context.domain().computed(scope, () -> MathContent.parse(editor.text().get()));
            embedFormula = context.domain().computed(scope, () -> MathContent.parse(tex(EMBED_SAMPLES[embedSample.get()])));
        }

        ImageOptions overlays() {
            return ImageOptions.defaults().quantity(quantity.get()).durability(durability.get()).tooltip(tooltip.get());
        }


    }

    /** 高对比非正方形位图，四周标记便于肉眼观察等比、裁剪、拉伸和对齐。 */
    private static ImageSource bitmap(boolean alternate) {
        int width = 240, height = 96;
        byte[] rgba = new byte[width * height * 4];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int color = x < width / 2 ? (alternate ? 0xff16a34a : 0xff2563eb)
                        : (alternate ? 0xff9333ea : 0xfff59e0b);
                if (x < 8) color = 0xffe11d48;
                else if (x >= width - 8) color = 0xff06b6d4;
                else if (y < 8) color = 0xff18181b;
                else if (y >= height - 8) color = 0xfffafafa;
                else if ((x / 16 + y / 16) % 2 == 0) color = (color & 0xfffefefe) >>> 1 | 0xff000000;
                int offset = (y * width + x) * 4;
                rgba[offset] = (byte) (color >>> 16);
                rgba[offset + 1] = (byte) (color >>> 8);
                rgba[offset + 2] = (byte) color;
                rgba[offset + 3] = (byte) 255;
            }
        }
        return ImageSource.bitmap(new ImageContent(width, height, rgba));
    }

    private static SceneNode section(SceneNode page, ScenePageContext context,
            ReadableSignal<SceneTheme> theme, String title, String steps) {
        SceneNode card = GalleryStyle.card(theme);
        page.add(GalleryStyle.frame(card, theme));
        card.add(GalleryStyle.label(context, () -> title, theme, 20, false));
        card.add(GalleryStyle.label(context, () -> steps, theme, 14, true));
        return card;
    }

    private static void addButton(SceneNode parent, ScenePageContext context,
            ReadableSignal<SceneTheme> theme, ReadableSignal<String> title, Runnable action) {
        // 每个操作独占一行，并声明填充宽度，正式按钮自动换行，避免自然横排撑出小屏。
        parent.add(GalleryStyle.button(context, title, theme, action).size(Size.fill(1), Size.natural()));
    }

    private static SceneNode horizontal(SceneNode content) {
        return new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural())
                .padding(8).scrollable(true).clip(true).add(content);
    }

    private static String sizeName(int size) {
        switch (size) {
            case 0: return "固定 160 × 96";
            case 1: return "固定 96 × 160";
            case 2: return "仅宽 160，高自然";
            case 3: return "宽高均自然";
            default: throw new IllegalArgumentException("未知尺寸样本");
        }
    }

    private static String alignmentName(int value) {
        return value == 0 ? "起始 / 0" : value == 1 ? "居中 / 0.5" : "末尾 / 1";
    }

    private static String onOff(boolean value) { return value ? "开启" : "关闭"; }

    private static String imageStatus(ImageState state) {
        return "图像状态：" + state.status + " · 阶段：" + state.stage
                + (state.content == null ? "" : " · 实际尺寸：" + state.content.width + " × " + state.content.height)
                + (state.diagnostic.isEmpty() ? "" : " · " + state.diagnostic);
    }

    private static String parseStatus(MathContent content) {
        if (content.diagnostics.isEmpty()) return "解析无错误；最终呈现还取决于数学字体与排版能力。";
        return "解析降级：" + diagnostics(content.diagnostics);
    }

    private static String layoutStatus(SceneNode formula) {
        MathLayout layout = SceneMath.layout(formula);
        if (layout == null) return "当前节点尚未完成呈现；待画面更新后再次读取。";
        return (layout.fallback ? "已降级为完整源文" : "已完成数学排版")
                + (layout.diagnostics.isEmpty() ? "；无排版诊断。" : "；" + diagnostics(layout.diagnostics));
    }

    private static String diagnostics(java.util.List<MathLayout.Diagnostic> values) {
        StringBuilder result = new StringBuilder();
        for (MathLayout.Diagnostic diagnostic : values) {
            if (result.length() > 0) result.append("；");
            result.append(diagnostic.stage).append(" UTF-16 [").append(diagnostic.start)
                    .append(", ").append(diagnostic.end).append(")：").append(diagnostic.message);
        }
        return result.toString();
    }

    // 样本用 @ 表示反斜杠，避免 Java 转义掩盖可阅读的数学源码；不建立另一套数学语法。
    private static String tex(String source) { return source.replace('@', (char) 92); }
}
