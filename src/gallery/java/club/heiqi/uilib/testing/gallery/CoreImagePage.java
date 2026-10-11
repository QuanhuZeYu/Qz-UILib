package club.heiqi.uilib.testing.gallery;

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
    private final GalleryEnvironment environment;
    private final java.util.function.Consumer<TestGallery.Page> navigate;
    CoreImagePage(GalleryEnvironment environment, java.util.function.Consumer<TestGallery.Page> navigate) {
        this.environment = environment;
        this.navigate = navigate;
    }


    private ImageContent bitmap(int width, int height) {
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

    private SceneNode tile(
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

    private void publishItem(
            Signal<ImageSource> target, boolean enchanted, int amount, int damage) {
        target.set(environment.item(enchanted, amount, damage));
    }

    SceneNode create(ScenePageContext context, ReadableSignal<SceneTheme> theme) {
        ReactiveDomain domain = context.domain();
        ImageService service = environment.images();
        Signal<ImageSource> runtime = domain.signal(ImageSource.bitmap(bitmap(120, 60)));
        Signal<ImageOptions> options =
                domain.signal(
                        ImageOptions.defaults().size(Size.fixed(150), Size.fixed(90)).alt("图像占位"));
        Signal<Integer> fit = domain.signal(0);
        Signal<Boolean> large = domain.signal(false);
        Signal<ImageSource> resource =
                domain.signal(ImageSource.resource("qz_uilib_test:textures/sample.png"));
        Signal<ImageSource> selected =
                domain.signal(
                        ImageSource.resource(
                                "qz_uilib_test:textures/sample.png",
                                new ImageSource.Region(0, 0, 8, 8)));
        Signal<ImageSource> remote =
                domain.signal(ImageSource.resource("qz_uilib_test:textures/sample.png"));
        Signal<ImageSource> item =
                domain.signal(
                        environment.item(false, 1, 0));
        Signal<Boolean> enchanted = domain.signal(false);
        Signal<Integer> amount = domain.signal(1), damage = domain.signal(0);
        Signal<ImageOptions> itemOptions =
                domain.signal(
                        ImageOptions.defaults().size(Size.fixed(100), Size.fixed(70)).alt("物品图标"));
        SceneNode panel = new SceneNode().size(Size.fill(1), Size.natural()).gap(4);
        panel.add(
                SceneLabel.create(
                        context,
                        () -> "统一图像：资源选区、动态图像；HTTP通过显式联网按钮加载。"
                                + (environment.hasItems() ? " MC物品专项：数量、耐久、提示与附魔；F3+T重载资源。" : " 本宿主的资源重载操作由入口提供。"),
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
                                        () -> "请求/重载HTTP（联网）",
                                        theme,
                                        () -> { if (remote.get().kind != ImageSource.Kind.HTTP) remote.set(ImageSource.http("https://httpbin.org/image/png")); else service.reload(remote.get()); })));
        panel.add(
                new SceneNode()
                        .axis(SceneNode.Axis.ROW)
                        .size(Size.fill(1), Size.natural())
                        .gap(4)
                        .add(tile(context, theme, service, resource, options))
                        .add(tile(context, theme, service, selected, options))
                        .add(tile(context, theme, service, runtime, options)));
        SceneNode imageRow = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural()).gap(4)
                .add(tile(context, theme, service, remote, options));
        if (environment.hasItems()) imageRow.add(tile(context, theme, service, item, itemOptions));
        panel.add(imageRow);
        if (environment.hasItems()) {
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
        }
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
        if (!environment.hasItems()) panel.add(SceneLabel.create(context, () -> "MC物品专项：当前宿主不支持；通用图像不计为物品验收", theme));
        return panel;
    }
}
