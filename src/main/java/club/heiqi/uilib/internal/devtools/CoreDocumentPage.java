package club.heiqi.uilib.internal.devtools;

import club.heiqi.uilib.host.client.image.MinecraftImageServices;
import club.heiqi.uilib.host.client.image.MinecraftItemSource;
import club.heiqi.uilib.ui.host.minecraft.MinecraftSceneScreen;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.DocumentResources;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneDocument;
import club.heiqi.uilib.ui.scene.control.SceneLabel;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.image.ImageOptions;
import club.heiqi.uilib.ui.scene.image.ImageSource;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.text.DocumentContent;
import club.heiqi.uilib.ui.scene.text.TextContent;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.math.MathContent;
import club.heiqi.uilib.ui.scene.text.math.MathStyle;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** /qzcore document：共同文档实机入口；物品来源沿正式Minecraft图像服务。 */
public final class CoreDocumentPage extends MinecraftSceneScreen {
    public CoreDocumentPage() {
        super(CoreDocumentPage::buildPage);
    }

    private static String tex(String text) {
        return text.replace('@', (char) 92);
    }

    private static void buildPage(ScenePageContext ctx) {
        Signal<Boolean> light = ctx.domain().signal(false),
                invalid = ctx.domain().signal(false),
                tool = ctx.domain().signal(false);
        Signal<Integer> em = ctx.domain().signal(28);
        Signal<String> observed = ctx.domain().signal("点击文中链接仅显示目标；悬停物品应显示原版内容提示。");
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
        String prose =
                "中文与English共同行内分式：<a href=\"next\"><latex>"
                        + tex("@frac{a+b}{1+x^2}")
                        + "</latex> 链接公式与物品 <img src=\"ore\" width=\"40\" alt=\"物品\"/></a>"
                        + " 后续正文。<br/>混合RTL：אבג <latex>"
                        + tex("@sqrt{x}")
                        + "</latex> é 👩‍💻 中文；字号变化时公式和图像仍在行高内。<br/>"
                        + "样式：<color value=\"#35aaff\">蓝字<latex>"
                        + tex("x_i^2")
                        + "</latex></color>；display样式保持行内：<latex math-style=\"display\">"
                        + tex("@sum_{i=1}^n i")
                        + "</latex>。<br/>"
                        + "尺寸与盒：<latex>"
                        + tex("@smash[b]{@frac{a}{b}}")
                        + "</latex> 后续文字和自然物品 <img src=\"ore\"/>。<br/>";
        Computed<DocumentContent> content =
                ctx.domain()
                        .computed(
                                ctx.scope(),
                                () ->
                                        new DocumentContent(
                                                Arrays.asList(
                                                        DocumentContent.Block.paragraph(
                                                                TextContent.richTags(
                                                                        prose + prose + prose)),
                                                        DocumentContent.Block.formula(
                                                                MathContent.parse(
                                                                        tex(
                                                                                "@begin{align}E&=mc^2@@A&=@frac{1}{2}@tag{共同文档}@end{align}")),
                                                                MathStyle.DISPLAY),
                                                        DocumentContent.Block.paragraph(
                                                                TextContent.richTags(
                                                                        "错误公式只影响原子项：<latex>"
                                                                                + tex(
                                                                                        invalid
                                                                                                        .get()
                                                                                                ? "@notacommand{x}"
                                                                                                : "@frac{1}{2}")
                                                                                + "</latex>"
                                                                                + " 后续正文完整。<img"
                                                                                + " src=\"ore\""
                                                                                + " width=\"24\"/>")))));
        Map<String, DocumentResources.ImageReference> references = new LinkedHashMap<>();
        references.put(
                "ore",
                new DocumentResources.ImageReference(
                        item,
                        ctx.domain()
                                .signal(
                                        ImageOptions.defaults()
                                                .quantity(true)
                                                .durability(true)
                                                .tooltip(true))));
        DocumentResources resources =
                new DocumentResources(
                        MinecraftImageServices.shared().images(),
                        ctx.domain().signal("https://example.test/docs/"),
                        references,
                        null);
        SceneNode document =
                SceneDocument.create(ctx, content, theme, resources, observed::set)
                        .size(Size.fill(1), Size.natural());
        SceneNode viewport =
                new SceneNode()
                        .size(Size.fill(1), Size.fixed(230))
                        .scrollable(true)
                        .clip(true)
                        .scrollStep(28)
                        .add(document);
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
                                        () -> invalid.get() ? "恢复公式" : "错误公式",
                                        theme,
                                        () -> invalid.set(!invalid.get())))
                        .add(
                                SceneButton.create(
                                        ctx,
                                        () -> tool.get() ? "换铁锭" : "换工具",
                                        theme,
                                        () -> {
                                            tool.set(!tool.get());
                                            net.minecraft.item.ItemStack stack =
                                                    new net.minecraft.item.ItemStack(
                                                            tool.get()
                                                                    ? net.minecraft.init.Items
                                                                            .iron_pickaxe
                                                                    : net.minecraft.init.Items
                                                                            .iron_ingot,
                                                            tool.get() ? 1 : 32);
                                            if (tool.get()) stack.setItemDamage(100);
                                            item.set(new MinecraftItemSource(stack));
                                        }));
        ctx.mount(
                new SceneNode()
                        .size(Size.fill(1), Size.fill(1))
                        .padding(8)
                        .gap(5)
                        .add(
                                SceneLabel.create(
                                        ctx,
                                        ctx.domain().signal("共同文档：文字、公式和物品同一内容流；缩窄窗口、滚到底、F3+T。"),
                                        theme))
                        .add(actions)
                        .add(viewport)
                        .add(SceneLabel.create(ctx, observed, theme)));
    }
}
