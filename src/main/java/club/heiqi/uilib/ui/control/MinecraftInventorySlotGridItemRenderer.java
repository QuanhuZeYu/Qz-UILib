package club.heiqi.uilib.ui.control;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * 基于 Minecraft 运行时的默认背包物品渲染实现。
 */
final class MinecraftInventorySlotGridItemRenderer implements InventorySlotGridItemRenderer {

    private final RenderItem itemRenderer = new RenderItem();

    @Override
    public void renderItems(InventorySlotGridLayout layout, int absoluteX, int absoluteY, ItemStack[] slotStacks) {
        if (layout == null || slotStacks == null || slotStacks.length <= 0) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        GL11.glPushMatrix();
        try {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            GL11.glEnable(GL11.GL_LIGHTING);
            RenderHelper.enableGUIStandardItemLighting();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            itemRenderer.zLevel = 100.0F;

            for (int slotIndex = 0; slotIndex < slotStacks.length; slotIndex++) {
                ItemStack stack = slotStacks[slotIndex];
                if (!InventorySlotGridWidget.hasRenderableStack(stack)) {
                    continue;
                }

                InventorySlotGridLayout.ItemIconOrigin iconOrigin = layout.getItemIconOrigin(slotIndex);
                int itemX = absoluteX + iconOrigin.x;
                int itemY = absoluteY + iconOrigin.y;
                itemRenderer.renderItemAndEffectIntoGUI(minecraft.fontRenderer, minecraft.renderEngine, stack, itemX,
                        itemY);
                itemRenderer.renderItemOverlayIntoGUI(minecraft.fontRenderer, minecraft.renderEngine, stack, itemX,
                        itemY, null);
            }
        } finally {
            itemRenderer.zLevel = 0.0F;
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glPopMatrix();
        }
    }
}
