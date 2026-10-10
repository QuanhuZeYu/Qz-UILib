package club.heiqi.uilib.host.client.image;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import club.heiqi.uilib.ui.scene.image.ImageContent;
import club.heiqi.uilib.ui.scene.image.ImageLoader;
import club.heiqi.uilib.ui.scene.image.ImageSource;
import club.heiqi.uilib.ui.scene.image.ItemImageFacts;

/** prepare 在宿主 drain 线程取得物品/tooltip 事实；worker 只返回准备好的不可变内容。 */
public final class MinecraftImageLoader implements ImageLoader {
    private final Thread thread = Thread.currentThread();
    private final ImageLoader raster;

    public MinecraftImageLoader(ImageLoader raster) {
        this.raster = java.util.Objects.requireNonNull(raster, "raster");
    }

    @Override
    public java.util.concurrent.Callable<ImageContent> prepare(ImageSource source)
            throws Exception {
        if (Thread.currentThread() != thread) throw new IllegalStateException("物品事实仅在宿主线程准备");
        if (!(source instanceof MinecraftItemSource)) return raster.prepare(source);
        ItemStack stack = ((MinecraftItemSource) source).copy();
        Minecraft mc = Minecraft.getMinecraft();
        List<?> lines = stack.getTooltip(mc.thePlayer, mc.gameSettings.advancedItemTooltips);
        StringBuilder tooltip = new StringBuilder();
        for (Object line : lines) {
            if (tooltip.length() != 0) tooltip.append('\n');
            tooltip.append(String.valueOf(line));
        }
        boolean durabilityVisible = stack.getItem().showDurabilityBar(stack);
        // 原版非耐久物品的比例是0/0；仅显示条时该事实才有意义，保留mod的showDurabilityBar语义。
        double remaining =
                durabilityVisible
                        ? Math.max(
                                0, Math.min(1, 1 - stack.getItem().getDurabilityForDisplay(stack)))
                        : 1;
        int green = (int) Math.round(255 * remaining);
        ItemImageFacts facts =
                new ItemImageFacts(
                        stack.stackSize == 1 ? "" : Integer.toString(stack.stackSize),
                        MinecraftTooltipContent.fromMinecraft(
                                tooltip.toString(),
                                ((club.heiqi.uilib.host.client.font.FontRendererAccess)
                                                mc.fontRenderer)
                                        .qz$palette()),
                        durabilityVisible,
                        remaining,
                        0xff000000 | (255 - green) << 16 | green << 8);
        ImageContent content = new MinecraftItemContent(stack, facts);
        return () -> content;
    }
}
