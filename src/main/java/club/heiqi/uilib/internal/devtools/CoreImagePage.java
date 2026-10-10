package club.heiqi.uilib.internal.devtools;

import club.heiqi.uilib.host.client.image.MinecraftImageServices;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.control.SceneImage;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneLabel;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.image.ImageSource;
import club.heiqi.uilib.ui.scene.image.ImageState;
import club.heiqi.uilib.ui.scene.image.ImageContent;
import club.heiqi.uilib.ui.scene.image.ImageOptions;
import club.heiqi.uilib.ui.scene.image.ImageService;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 手工图像验收只声明正式控件与signals；无下载缓存/纹理上传/布局补算。 */
final class CoreImagePage {
    private CoreImagePage() {}

    private static ImageContent bitmap(int width, int height) {
        byte[] rgba = new byte[width * height * 4];
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) {
                int i = (y * width + x) * 4;
                rgba[i] = (byte) (x * 255 / Math.max(1, width - 1));
                rgba[i + 1] = (byte) (y * 255 / Math.max(1, height - 1));
                rgba[i + 2] = (byte) 190;
                rgba[i + 3] = (byte) ((x / 8 + y / 8) % 2 == 0 ? 255 : 100);
            }
        return new ImageContent(width, height, rgba);
    }

    private static SceneNode tile(
            ScenePageContext context,
            ReadableSignal<SceneTheme> theme,
            ImageService service,
            ReadableSignal<ImageSource> source,
            ReadableSignal<ImageOptions> options) {
        SceneNode image = SceneImage.themed(service, source, options, theme);
        ReadableSignal<ImageState> state = SceneImage.state(image);
        return new SceneNode()
                .size(Size.fill(1), Size.natural())
                .gap(2)
                .add(image)
                .add(
                        SceneLabel.create(
                                context,
                                () -> {
                                    ImageState value = state.get();
                                    return value.status
                                            + " / "
                                            + value.stage
                                            + " / "
                                            + value.source.location
                                            + (value.diagnostic.isEmpty()
                                                    ? ""
                                                    : " / " + value.diagnostic);
                                },
                                theme));
    }

    private static void publishItem(
            Signal<ImageSource> target, boolean enchanted, int amount, int damage) {
        net.minecraft.item.ItemStack stack =
                new net.minecraft.item.ItemStack(
                        enchanted
                                ? net.minecraft.init.Items.diamond_pickaxe
                                : net.minecraft.init.Items.diamond,
                        amount,
                        enchanted ? damage : 0);
        if (enchanted) stack.addEnchantment(net.minecraft.enchantment.Enchantment.efficiency, 3);
        target.set(new club.heiqi.uilib.host.client.image.MinecraftItemSource(stack));
    }

    static SceneNode create(ScenePageContext context, ReadableSignal<SceneTheme> theme) {
        ReactiveDomain domain = context.domain();
        ImageService service = MinecraftImageServices.shared().images();
        Signal<ImageSource> runtime = domain.signal(ImageSource.bitmap(bitmap(120, 60)));
        Signal<ImageOptions> options =
                domain.signal(
                        ImageOptions.defaults().size(Size.fixed(150), Size.fixed(90)).alt("图像占位"));
        Signal<Integer> fit = domain.signal(0);
        Signal<Boolean> large = domain.signal(false);
        Signal<ImageSource> resource =
                domain.signal(ImageSource.resource("minecraft:textures/blocks/planks_oak.png"));
        Signal<ImageSource> selected =
                domain.signal(
                        ImageSource.resource(
                                "minecraft:textures/blocks/planks_oak.png",
                                new ImageSource.Region(0, 0, 8, 8)));
        Signal<ImageSource> remote =
                domain.signal(ImageSource.http("https://httpbin.org/image/png"));
        Signal<ImageSource> item =
                domain.signal(
                        new club.heiqi.uilib.host.client.image.MinecraftItemSource(
                                new net.minecraft.item.ItemStack(
                                        net.minecraft.init.Items.diamond)));
        Signal<Boolean> enchanted = domain.signal(false);
        Signal<Integer> amount = domain.signal(1), damage = domain.signal(0);
        Signal<ImageOptions> itemOptions =
                domain.signal(
                        ImageOptions.defaults().size(Size.fixed(100), Size.fixed(70)).alt("物品图标"));
        SceneNode panel = new SceneNode().size(Size.fill(1), Size.natural()).gap(4);
        panel.add(
                SceneLabel.create(
                        context,
                        () -> "统一图像：资源选区、动态图像、HTTP、原版物品；F3+T可验证资源更新。物品数量、耐久、提示各自选择，附魔效果持续变化。",
                        theme));
        panel.add(
                new SceneNode()
                        .axis(SceneNode.Axis.ROW)
                        .size(Size.fill(1), Size.natural())
                        .gap(3)
                        .add(
                                SceneButton.create(
                                        context,
                                        () -> "切换fit " + ImageOptions.Fit.values()[fit.get()],
                                        theme,
                                        () -> {
                                            fit.set(
                                                    (fit.get() + 1)
                                                            % ImageOptions.Fit.values().length);
                                            options.set(
                                                    options.get()
                                                            .fit(
                                                                    ImageOptions.Fit.values()[
                                                                            fit.get()]));
                                        }))
                        .add(
                                SceneButton.create(
                                        context,
                                        () -> "更新动态图像",
                                        theme,
                                        () -> {
                                            large.set(!large.get());
                                            runtime.set(
                                                    ImageSource.bitmap(
                                                            large.get()
                                                                    ? bitmap(240, 120)
                                                                    : bitmap(120, 60)));
                                        }))
                        .add(
                                SceneButton.create(
                                        context,
                                        () -> "重新加载当前HTTP",
                                        theme,
                                        () -> service.reload(remote.get()))));
        panel.add(
                new SceneNode()
                        .axis(SceneNode.Axis.ROW)
                        .size(Size.fill(1), Size.natural())
                        .gap(4)
                        .add(tile(context, theme, service, resource, options))
                        .add(tile(context, theme, service, selected, options))
                        .add(tile(context, theme, service, runtime, options)));
        panel.add(
                new SceneNode()
                        .axis(SceneNode.Axis.ROW)
                        .size(Size.fill(1), Size.natural())
                        .gap(4)
                        .add(tile(context, theme, service, remote, options))
                        .add(tile(context, theme, service, item, itemOptions)));
        panel.add(
                new SceneNode()
                        .axis(SceneNode.Axis.ROW)
                        .size(Size.fill(1), Size.natural())
                        .gap(3)
                        .add(
                                SceneButton.create(
                                        context,
                                        () -> enchanted.get() ? "物品：附魔工具" : "物品：钻石",
                                        theme,
                                        () -> {
                                            enchanted.set(!enchanted.get());
                                            publishItem(
                                                    item,
                                                    enchanted.get(),
                                                    amount.get(),
                                                    damage.get());
                                        }))
                        .add(
                                SceneButton.create(
                                        context,
                                        () -> "数量 " + amount.get(),
                                        theme,
                                        () -> {
                                            amount.set(amount.get() == 1 ? 32 : 1);
                                            publishItem(
                                                    item,
                                                    enchanted.get(),
                                                    amount.get(),
                                                    damage.get());
                                        }))
                        .add(
                                SceneButton.create(
                                        context,
                                        () -> "损耗 " + damage.get(),
                                        theme,
                                        () -> {
                                            damage.set(damage.get() == 0 ? 900 : 0);
                                            publishItem(
                                                    item,
                                                    enchanted.get(),
                                                    amount.get(),
                                                    damage.get());
                                        })));
        panel.add(
                new SceneNode()
                        .axis(SceneNode.Axis.ROW)
                        .size(Size.fill(1), Size.natural())
                        .gap(3)
                        .add(
                                SceneButton.create(
                                        context,
                                        () -> "数量显示 " + itemOptions.get().quantity,
                                        theme,
                                        () ->
                                                itemOptions.set(
                                                        itemOptions
                                                                .get()
                                                                .quantity(
                                                                        !itemOptions.get()
                                                                                .quantity))))
                        .add(
                                SceneButton.create(
                                        context,
                                        () -> "耐久显示 " + itemOptions.get().durability,
                                        theme,
                                        () ->
                                                itemOptions.set(
                                                        itemOptions
                                                                .get()
                                                                .durability(
                                                                        !itemOptions.get()
                                                                                .durability))))
                        .add(
                                SceneButton.create(
                                        context,
                                        () -> "悬停提示 " + itemOptions.get().tooltip,
                                        theme,
                                        () ->
                                                itemOptions.set(
                                                        itemOptions
                                                                .get()
                                                                .tooltip(
                                                                        !itemOptions.get()
                                                                                .tooltip)))));
        panel.add(SceneLabel.create(context, () -> "高度填满时自然宽度跟随来源比例；边界裁剪不改变布局尺寸。", theme));
        panel.add(
                new SceneNode()
                        .axis(SceneNode.Axis.ROW)
                        .size(Size.fill(1), Size.fixed(80))
                        .gap(8)
                        .add(
                                SceneImage.themed(
                                        service,
                                        runtime,
                                        domain.signal(
                                                ImageOptions.defaults()
                                                        .size(Size.natural(), Size.fill(1))),
                                        theme)));
        SceneNode document =
                SceneImage.documentImage(
                        service, runtime, domain.signal(ImageOptions.defaults().alt("文档图像")));
        panel.add(SceneLabel.create(context, () -> "文档图片：大图随可用宽度缩小，小图不放大；明确尺寸可覆盖默认行为。", theme))
                .add(document);
        return panel;
    }
}
