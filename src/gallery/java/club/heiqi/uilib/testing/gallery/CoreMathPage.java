package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
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
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.math.MathContent;
import club.heiqi.uilib.ui.scene.text.math.MathEmbedMetrics;
import club.heiqi.uilib.ui.scene.text.math.MathLayout;
import club.heiqi.uilib.ui.scene.text.math.MathStyle;

import java.util.LinkedHashMap;
import java.util.Map;

/** /qzcore math：正式数学与 Item 来源接入页，宿主只提交嵌入图像来源。 */
final class CoreMathPage {
    private final GalleryEnvironment environment;
    private final java.util.function.Consumer<TestGallery.Page> navigate;
    CoreMathPage(GalleryEnvironment environment, java.util.function.Consumer<TestGallery.Page> navigate) {
        this.environment = environment;
        this.navigate = navigate;
    }


    private String tex(String source) {
        return source.replace('@', (char) 92);
    }

    private Map<String, SceneMath.Embed> slots(
            ScenePageContext ctx,
            ReadableSignal<SceneTheme> theme,
            ReadableSignal<ImageSource> item,
            String... ids) {
        Map<String, SceneMath.Embed> result = new LinkedHashMap<>();
        for (String id : ids) {
            SceneNode image =
                    SceneImage.themed(
                            environment.images(),
                            item,
                            ctx.domain()
                                    .signal(
                                            ImageOptions.defaults()
                                                    .tooltip(true)
                                                    .quantity(true)
                                                    .durability(true)
                                                    .alt("嵌入图像")),
                            theme);
            result.put(id, new SceneMath.Embed(image, new MathEmbedMetrics(1, 1, 0.8f)));
        }
        return result;
    }

    private String diagnostics(MathLayout layout) {
        if (layout == null) return "公式尚未呈现";
        if (layout.diagnostics.isEmpty()) return "无诊断";
        StringBuilder result = new StringBuilder();
        for (MathLayout.Diagnostic d : layout.diagnostics)
            result.append(d.stage)
                    .append(" [")
                    .append(d.start)
                    .append(',')
                    .append(d.end)
                    .append("] ")
                    .append(d.message)
                    .append("；");
        return result.toString();
    }

    void buildPage(ScenePageContext ctx, java.util.List<SceneNode> roots) {
        Signal<Boolean> light = ctx.domain().signal(false),
                invalid = ctx.domain().signal(false),
                tool = ctx.domain().signal(false);
        Signal<Integer> em = ctx.domain().signal(28);
        Signal<String> observed = ctx.domain().signal("点击“读取公式诊断”查看当前呈现结果");
        Signal<ImageSource> item =
                ctx.domain()
                        .signal(
                                environment.embed(false));
        Computed<SceneTheme> theme =
                ctx.domain()
                        .computed(
                                ctx.scope(),
                                () -> {
                                    SceneTheme value =
                                            light.get() ? SceneTheme.light() : SceneTheme.dark();
                                    return value.withTextStyle(
                                            new TextStyle(
                                                    "default", em.get(), value.textStyle.color));
                                });
        MathContent valid =
                MathContent.parse(tex("@int_0^1 @frac{@embed{ore}}{1+x^2}@,@mathrm{d}x"));
        MathContent bad = MathContent.parse(tex("@int_0^1 @unknown{@embed{ore}}"));
        ReadableSignal<MathContent> content =
                ctx.domain().computed(ctx.scope(), () -> invalid.get() ? bad : valid);
        SceneNode integral =
                SceneMath.create(
                                ctx,
                                content,
                                theme,
                                MathStyle.DISPLAY,
                                slots(ctx, theme, item, "ore"))
                        .size(Size.fill(1), Size.natural())
                        .padding(5);
        SceneNode sum =
                SceneMath.create(
                                ctx,
                                ctx.domain()
                                        .signal(
                                                MathContent.parse(
                                                        tex(
                                                                "@sum@limits_{n=1}^{@embed{limit}}"
                                                                        + " @frac{@embed{numerator}}{n^2}"))),
                                theme,
                                MathStyle.DISPLAY,
                                slots(ctx, theme, item, "limit", "numerator"))
                        .padding(5);
        SceneNode root =
                SceneMath.create(
                                ctx,
                                ctx.domain()
                                        .signal(
                                                MathContent.parse(
                                                        tex("@sqrt[3]{1+@frac{@embed{root}}{2}}"))),
                                theme,
                                MathStyle.DISPLAY,
                                slots(ctx, theme, item, "root"))
                        .padding(5);
        SceneNode scripts =
                SceneMath.create(
                                ctx,
                                ctx.domain()
                                        .signal(
                                                MathContent.parse(
                                                        tex(
                                                                "x^{@embed{up}}_{@embed{down}}+@frac{@embed{top}}{1+x}"))),
                                theme,
                                MathStyle.TEXT,
                                slots(ctx, theme, item, "up", "down", "top"))
                        .padding(5);
        SceneNode fences =
                SceneMath.create(
                                ctx,
                                ctx.domain()
                                        .signal(
                                                MathContent.parse(
                                                        tex(
                                                                "@left@{@frac{1}{@frac{1}{@frac{@embed{fence}}{1+x}}}@middle|@binom{n}{k}@right@}^{2}"))),
                                theme,
                                MathStyle.DISPLAY,
                                slots(ctx, theme, item, "fence"))
                        .padding(5);
        SceneNode tallRoot =
                SceneMath.create(
                                ctx,
                                ctx.domain()
                                        .signal(
                                                MathContent.parse(
                                                        tex(
                                                                "@sqrt{@frac{1}{@frac{1}{@frac{1}{@frac{@embed{tall}}{1+x}}}}}"))),
                                theme,
                                MathStyle.DISPLAY,
                                slots(ctx, theme, item, "tall"))
                        .padding(5);
        SceneNode binomial =
                SceneMath.create(
                                ctx,
                                ctx.domain()
                                        .signal(
                                                MathContent.parse(
                                                        tex(
                                                                "@binom{@embed{chooseTop}+n}{@embed{chooseBottom}}+@left.@frac{x}{1+x}@right|_{x=0}"))),
                                theme,
                                MathStyle.DISPLAY,
                                slots(ctx, theme, item, "chooseTop", "chooseBottom"))
                        .padding(5);
        String[] familyNames = {
            "矩阵与分段",
            "数组与行列线",
            "对齐与编号",
            "重音与宽构建",
            "上下说明与箭头",
            "字体与文字",
            "颜色边框与取消线",
            "尺寸与盒",
            "广义分式与脚本",
            "内置及文档宏",
            "默认拒绝与来源"
        };
        String[] familySources = {
            "@begin{pmatrix}1&@embed{family}@@@frac12&@sqrt{x}@end{pmatrix} @qquad"
                    + " f(x)=@begin{cases}x^2&x>0@@0&x@leq0@end{cases}",
            "@begin{array}{l|c:r}@hline @embed{family}&x&y@@[.3em]a&b&c@@@hdashline"
                    + " d&e&f@end{array}",
            "@begin{align}E&=mc^2@@A&=@embed{family}@tag{Item}@@B&=@frac{1}{2}@nonumber@end{align}",
            "@hat{x}+@widehat{a+b+c}+@widetilde{abcdef}+@vec{F}+@underbrace{x+@embed{family}}_{n}+@overbrace{a+b}^{m}",
            "@overset{def}{=} @underset{x}{@sum} @xrightarrow[n@to@infty]{a+b+c} @embed{family}"
                    + " @xleftrightharpoons[u]{v}",
            "@mathbb{R}+@mathcal{ABC}+@mathfrak{g}+@boldsymbol{@alpha} @quad @textrm{Roman}"
                    + " @textsf{Sans} @texttt{Mono} @text{mass @textbf{Item}} @embed{family}",
            "@colorbox{yellow}{$@textcolor{blue}{x+@embed{family}}$} @quad"
                    + " @fcolorbox{red}{white}{y} @quad @xcancel{a+b}",
            "@phantom{XX}x+@hphantom{@embed{family}}y+@smash[b]{@frac{a}{b}}+@raisebox{.5em}{z}+@rule[.2em]{1em}{.1em}",
            "@genfrac{(}{)}{0pt}{0}{@embed{family}+1}{2}+{a@over"
                    + " b}+@mathchoice{D}{T}{S}{SS}+x''_i@@[1pt]@left({x@middle|y}@right)+@left(@frac{x@middle|y}{z}@right)",
            "@newcommand{@sample}[1]{@frac{#1}{2}} @sample{@embed{family}}+@Braket{a|b}+@pmod{n}",
            "@href{https://example.invalid}{x} + @htmlClass{test}{hidden} + @embed{family}"
        };
        MathContent[] familyContent = new MathContent[familySources.length];
        for (int i = 0; i < familySources.length; i++)
            familyContent[i] = MathContent.parse(tex(familySources[i]));
        Signal<Integer> family = ctx.domain().signal(0);
        ReadableSignal<MathContent> selectedFamily =
                ctx.domain().computed(ctx.scope(), () -> familyContent[family.get()]);
        SceneNode familyMath =
                SceneMath.create(
                                ctx,
                                selectedFamily,
                                theme,
                                MathStyle.DISPLAY,
                                slots(ctx, theme, item, "family"))
                        .padding(5);
        SceneNode viewport =
                new SceneNode()
                        .size(Size.fill(1), Size.fixed(210))
                        .scrollable(true)
                        .scrollStep(30)
                        .padding(6)
                        .gap(16)
                        .bind(
                                NodeBinding.value(
                                        theme, (node, value) -> node.background(value.button)))
                        .add(integral)
                        .add(sum)
                        .add(root)
                        .add(scripts)
                        .add(fences)
                        .add(tallRoot)
                        .add(binomial)
                        .add(
                                SceneLabel.create(
                                        ctx, () -> "完整数学家族：" + familyNames[family.get()], theme))
                        .add(familyMath);
        SceneNode actions =
                new SceneNode()
                        .axis(SceneNode.Axis.ROW)
                        .size(Size.fill(1), Size.natural())
                        .gap(4)
                        .add(
                                SceneButton.create(
                                        ctx,
                                        () -> "字号：" + em.get(),
                                        theme,
                                        () -> em.set(em.get() == 28 ? 38 : 28)))
                        .add(
                                SceneButton.create(
                                        ctx,
                                        () -> light.get() ? "深色主题" : "浅色主题",
                                        theme,
                                        () -> light.set(!light.get())))
                        .add(
                                SceneButton.create(
                                        ctx,
                                        () -> invalid.get() ? "恢复积分" : "非法公式",
                                        theme,
                                        () -> invalid.set(!invalid.get())));
        actions.add(
                SceneButton.create(
                        ctx,
                        () -> "下一家族：" + familyNames[family.get()],
                        theme,
                        () -> family.set((family.get() + 1) % familyContent.length)));
        SceneNode controls =
                new SceneNode()
                        .axis(SceneNode.Axis.ROW)
                        .size(Size.fill(1), Size.natural())
                        .gap(4)
                        .add(
                                SceneButton.create(
                                        ctx,
                                        () -> environment.hasItems() ? (tool.get() ? "嵌入物品：附魔工具" : "嵌入物品：铁锭") : (tool.get() ? "嵌入图像：绿色" : "嵌入图像：蓝色"),
                                        theme,
                                        () -> {
                                            tool.set(!tool.get());
                                            item.set(environment.embed(tool.get()));
                                        }))
                        .add(
                                SceneButton.create(
                                        ctx,
                                        () -> "读取公式诊断",
                                        theme,
                                        () ->
                                                observed.set(
                                                        "积分："
                                                                + diagnostics(
                                                                        SceneMath.layout(integral))
                                                                + " 求和："
                                                                + diagnostics(SceneMath.layout(sum))
                                                                + " 根式："
                                                                + diagnostics(
                                                                        SceneMath.layout(root))
                                                                + " 脚本："
                                                                + diagnostics(
                                                                        SceneMath.layout(scripts))
                                                                + " 定界符："
                                                                + diagnostics(
                                                                        SceneMath.layout(fences))
                                                                + " 大根式："
                                                                + diagnostics(
                                                                        SceneMath.layout(tallRoot))
                                                                + " 二项式："
                                                                + diagnostics(
                                                                        SceneMath.layout(binomial))
                                                                + " 当前家族："
                                                                + diagnostics(
                                                                        SceneMath.layout(
                                                                                familyMath)))));
        SceneNode page =
                new SceneNode()
                        .size(Size.fill(1), Size.fill(1))
                        .scrollable(true)
                        .padding(10)
                        .gap(6)
                        .bind(
                                NodeBinding.value(
                                        theme, (node, value) -> node.background(value.panel)))
                        .add(SceneLabel.create(ctx, () -> "完整数学与嵌入图像：基础公式、二维环境、宏、样式与装饰", theme))
                        .add(actions)
                        .add(controls)
                        .add(viewport)
                        .add(SceneLabel.create(ctx, () -> "积分源文：" + content.get().source, theme))
                        .add(
                                SceneLabel.create(
                                        ctx, () -> "家族源文：" + selectedFamily.get().source, theme))
                        .add(
                                SceneLabel.create(
                                        ctx,
                                        () -> {
                                            StringBuilder result = new StringBuilder("源码诊断：");
                                            for (MathLayout.Diagnostic d :
                                                    content.get().diagnostics)
                                                result.append(d.stage)
                                                        .append(" [")
                                                        .append(d.start)
                                                        .append(',')
                                                        .append(d.end)
                                                        .append("] ")
                                                        .append(d.message)
                                                        .append("；");
                                            return result.toString();
                                        },
                                        theme))
                        .add(SceneLabel.create(ctx, observed, theme))
                        .add(
                                SceneLabel.create(
                                        ctx,
                                        () ->
                                                "缩窄窗口并滚动；切换字号/主题/嵌入图像与非法公式后恢复。"
                                                        + (environment.hasItems() ? " 悬停物品查看原版提示；F3+T检查资源更新。" : " MC物品、原版提示与F3+T专项：当前宿主不支持；位图只验收通用嵌入图像。"),
                                        theme));
        roots.add(page);
    }

}
