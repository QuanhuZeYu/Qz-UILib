package club.heiqi.uilib.ui.host.minecraft.control;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import club.heiqi.uilib.host.client.image.MinecraftImageServices;
import club.heiqi.uilib.host.client.image.MinecraftItemContent;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneAppearance;
import club.heiqi.uilib.ui.scene.control.SceneDataTable;
import club.heiqi.uilib.ui.scene.control.SceneImage;
import club.heiqi.uilib.ui.scene.control.SceneRichText;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.image.ImageOptions;
import club.heiqi.uilib.ui.scene.image.ImageSource;
import club.heiqi.uilib.ui.scene.image.ImageState;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.TextContent;

/**
 * MC 物品列表模板：首列组合图标、宿主名称与业务描述，其余列沿正式数据表声明。
 * 业务提供稳定行键、物品来源和读数；滚动、虚拟化、提示与资源租约仍归原控件。
 * 来源应使用 MinecraftItemSource 保存样本；描述始终保留，未知物品也不会失去身份。
 */
public final class MinecraftItemList {
    private MinecraftItemList() { }

    public static <T, K, C> SceneDataTable<T, K, C> create(
            ScenePageContext page, ReadableSignal<List<T>> items, Function<T, K> key,
            C itemColumnKey, ReadableSignal<String> title, Function<T, ImageSource> source,
            Function<T, String> description, ReadableSignal<List<SceneDataTable.Column<T, C>>> columns,
            ReadableSignal<SceneDataTable.Options> options, ReadableSignal<SceneTheme> theme) {
        page.domain().checkWrite();
        Owner owner = page.scope().child();
        SceneAppearance style = new SceneAppearance(page, theme);
        SceneDataTable.Column<T, C> itemColumn = new SceneDataTable.Column<>(itemColumnKey,
            () -> Size.fill(3).bounds(Math.round(theme.get().textStyle.logicalEm * 16), Integer.MAX_VALUE),
            () -> style.label(title, .78f, true), item -> {
                Owner rowOwner = page.scope().child();
                ReadableSignal<ImageSource> image = page.domain().computed(rowOwner, () -> source.apply(item.get()));
                ReadableSignal<String> detail = page.domain().computed(rowOwner, () -> description.apply(item.get()));
                SceneNode icon = SceneImage.create(page, MinecraftImageServices.shared().images(), image,
                    () -> {
                        int side = Math.round(theme.get().textStyle.logicalEm * 2);
                        return ImageOptions.defaults().size(Size.fixed(side), Size.fixed(side))
                            .alt(detail.get()).tooltip(true);
                    }, theme);
                ReadableSignal<ImageState> state = SceneImage.state(icon);
                ReadableSignal<TextContent> name = page.domain().computed(rowOwner, () -> {
                    ImageState current = state.get();
                    return current.content instanceof MinecraftItemContent
                        ? ((MinecraftItemContent) current.content).displayName() : TextContent.plain(detail.get());
                });
                SceneNode labels = SceneAppearance.column(0)
                    .add(SceneRichText.create(page, name,
                        () -> style.colors.get().theme(.9f, style.colors.get().text, 0), null))
                    .add(style.label(detail, .72f, true));
                return SceneAppearance.row(0).add(icon).add(labels)
                    .bind(NodeBinding.value(() -> theme.get().gap, SceneNode::gap)).own(rowOwner);
            });
        ReadableSignal<List<SceneDataTable.Column<T, C>>> combined = page.domain().computed(owner, () -> {
            List<SceneDataTable.Column<T, C>> result = new ArrayList<>();
            result.add(itemColumn); result.addAll(columns.get()); return result;
        });
        SceneDataTable<T, K, C> table = SceneDataTable.create(page, items, key, combined, options, theme);
        table.node().own(owner).own(style.scope);
        return table;
    }
}
