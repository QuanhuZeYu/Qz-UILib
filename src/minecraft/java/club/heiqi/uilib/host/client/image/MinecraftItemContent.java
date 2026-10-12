package club.heiqi.uilib.host.client.image;

import net.minecraft.item.ItemStack;
import club.heiqi.uilib.ui.scene.image.ImageContent;
import club.heiqi.uilib.ui.scene.image.ItemImageFacts;

/** HOST_ITEM 专用快照；RenderItem 每次取得隔离 copy，Forge hook 无法污染被已冻结 PaintPlan 持有的内容。 */
public final class MinecraftItemContent extends ImageContent {
    private final ItemStack snapshot;
    private final ItemImageFacts facts;

    MinecraftItemContent(ItemStack stack, ItemImageFacts facts) {
        super(16, 16);
        snapshot = stack.copy();
        this.facts = facts;
    }

    ItemStack copy() {
        return snapshot.copy();
    }

    @Override
    public ItemImageFacts itemFacts() {
        return facts;
    }
}
