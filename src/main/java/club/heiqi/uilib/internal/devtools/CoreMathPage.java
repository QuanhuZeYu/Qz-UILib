package club.heiqi.uilib.internal.devtools;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import club.heiqi.uilib.host.client.image.MinecraftImageServices;
import club.heiqi.uilib.host.client.image.MinecraftItemSource;
import club.heiqi.uilib.ui.host.minecraft.MinecraftSceneScreen;
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

/** /qzcore math：正式数学与 Item 来源接入页，宿主只提交物品来源。 */
public final class CoreMathPage extends MinecraftSceneScreen {
    public CoreMathPage() {
        super(CoreMathPage::buildPage);
    }

    private static String tex(String source) {
        return source.replace('@', (char) 92);
    }

    private static Map<String, SceneMath.Embed> slots(
            ScenePageContext ctx,
            ReadableSignal<SceneTheme> theme,
            ReadableSignal<ImageSource> item,
            String... ids) {
        Map<String, SceneMath.Embed> result = new LinkedHashMap<>();
        for (String id : ids) {
            SceneNode image =
                    SceneImage.themed(
                            MinecraftImageServices.shared().images(),
                            item,
                            ctx.domain()
                                    .signal(
                                            ImageOptions.defaults()
                                                    .tooltip(true)
                                                    .quantity(true)
                                                    .durability(true)
                                                    .alt("物品")),
                            theme);
            result.put(id, new SceneMath.Embed(image, new MathEmbedMetrics(1, 1, 0.8f)));
        }
        return result;
    }

    private static String diagnostics(MathLayout layout) {
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

    private static void buildPage(ScenePageContext ctx) {
        Signal<Boolean> light = ctx.domain().signal(false),
                invalid = ctx.domain().signal(false),
                tool = ctx.domain().signal(false);
        Signal<Integer> em = ctx.domain().signal(28);
        Signal<String> observed = ctx.domain().signal("点击“读取公式诊断”查看当前呈现结果");
        Signal<ImageSource> item =
                ctx.domain()
                        .signal(
                                new MinecraftItemSource(
                                        new net.minecraft.item.ItemStack(
                                                net.minecraft.init.Items.iron_ingot, 32)));
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
                                                                "@sum@limits_{n=1}^{@embed{limit}} @frac{@embed{numerator}}{n^2}"))),
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
                        .add(binomial);
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
        SceneNode controls =
                new SceneNode()
                        .axis(SceneNode.Axis.ROW)
                        .size(Size.fill(1), Size.natural())
                        .gap(4)
                        .add(
                                SceneButton.create(
                                        ctx,
                                        () -> tool.get() ? "物品：附魔工具" : "物品：铁锭",
                                        theme,
                                        () -> {
                                            tool.set(!tool.get());
                                            net.minecraft.item.ItemStack stack =
                                                    tool.get()
                                                            ? new net.minecraft.item.ItemStack(
                                                                    net.minecraft.init.Items
                                                                            .diamond_pickaxe,
                                                                    1,
                                                                    900)
                                                            : new net.minecraft.item.ItemStack(
                                                                    net.minecraft.init.Items
                                                                            .iron_ingot,
                                                                    32);
                                            if (tool.get())
                                                stack.addEnchantment(
                                                        net.minecraft.enchantment.Enchantment
                                                                .efficiency,
                                                        3);
                                            item.set(new MinecraftItemSource(stack));
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
                                                                        SceneMath.layout(
                                                                                binomial)))));
        SceneNode page =
                new SceneNode()
                        .size(Size.fill(1), Size.fill(1))
                        .scrollable(true)
                        .padding(10)
                        .gap(6)
                        .bind(
                                NodeBinding.value(
                                        theme, (node, value) -> node.background(value.panel)))
                        .add(SceneLabel.create(ctx, () -> "数学与物品：积分、脚本、伸缩定界符、大根式与二项式", theme))
                        .add(actions)
                        .add(controls)
                        .add(viewport)
                        .add(SceneLabel.create(ctx, () -> "积分源文：" + content.get().source, theme))
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
                                                "缩窄窗口并滚动，悬停可见图标查看物品提示；切换字号/主题/物品与非法公式后恢复。F3+T可检查资源更新。",
                                        theme));
        ctx.mount(page);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
