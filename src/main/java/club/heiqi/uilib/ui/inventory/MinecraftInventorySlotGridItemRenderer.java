package club.heiqi.uilib.ui.inventory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;

/**
 * 基于 Minecraft 运行时的默认背包物品渲染实现。
 */
public final class MinecraftInventorySlotGridItemRenderer implements InventorySlotGridItemRenderer {

    private static final int VANILLA_ITEM_ICON_SIZE = 16;
    private static final int MAX_RENDERED_ITEM_ICON_SIZE = 24;
    private static final int ITEM_ICON_PADDING = 12;
    private static final int CURSOR_ITEM_ICON_SIZE = 24;

    private final RenderItem itemRenderer = new RenderItem();

    @Override
    public void renderItems(InventorySlotGridItemGeometry geometry, InventorySlotSnapshot[] slotSnapshots) {
        if (geometry == null || slotSnapshots == null || slotSnapshots.length <= 0) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        int itemIconSize = resolveRenderedItemIconSize(geometry.getSlotSize());
        GL11.glPushMatrix();
        try {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            GL11.glEnable(GL11.GL_LIGHTING);
            RenderHelper.enableGUIStandardItemLighting();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            itemRenderer.zLevel = 100.0F;

            for (int slotIndex = 0; slotIndex < slotSnapshots.length; slotIndex++) {
                InventorySlotSnapshot snapshot = slotSnapshots[slotIndex];
                if (snapshot == null || !snapshot.isOccupied()) {
                    continue;
                }

                ItemStack stack = snapshot.getRuntimeStack();
                if (stack == null || stack.getItem() == null) {
                    continue;
                }

                int itemX = geometry.getSlotLeft(slotIndex) + Math.max(0, (geometry.getSlotSize() - itemIconSize) / 2);
                int itemY = geometry.getSlotTop(slotIndex) + Math.max(0, (geometry.getSlotSize() - itemIconSize) / 2);
                renderScaledItem(minecraft, stack, itemX, itemY, itemIconSize);
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

    @Override
    public void renderCursorItem(InventorySlotSnapshot carriedSnapshot, int mouseX, int mouseY) {
        if (carriedSnapshot == null || !carriedSnapshot.isOccupied()) {
            return;
        }

        ItemStack stack = carriedSnapshot.getRuntimeStack();
        if (stack == null || stack.getItem() == null) {
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
            itemRenderer.zLevel = 220.0F;
            renderScaledItem(minecraft, stack, mouseX - CURSOR_ITEM_ICON_SIZE / 2, mouseY - CURSOR_ITEM_ICON_SIZE / 2,
                    CURSOR_ITEM_ICON_SIZE);
        } finally {
            itemRenderer.zLevel = 0.0F;
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glPopMatrix();
        }
    }

    /**
     * 按格子尺寸为原版 16x16 物品渲染提供温和放大，避免在大格子里显得过小。
     *
     * @param slotSize 当前格子尺寸
     * @return 物品目标绘制尺寸
     */
    static int resolveRenderedItemIconSize(int slotSize) {
        int normalizedSlotSize = Math.max(VANILLA_ITEM_ICON_SIZE, slotSize);
        return Math.max(VANILLA_ITEM_ICON_SIZE,
                Math.min(MAX_RENDERED_ITEM_ICON_SIZE, normalizedSlotSize - ITEM_ICON_PADDING));
    }

    /**
     * 在单个物品作用域内包裹缩放矩阵，继续复用原版 RenderItem 的主体、特效与覆盖层绘制路径。
     *
     * @param minecraft Minecraft 运行时
     * @param stack 物品堆栈
     * @param itemX 绘制原点 X
     * @param itemY 绘制原点 Y
     * @param itemIconSize 目标绘制尺寸
     */
    private void renderScaledItem(Minecraft minecraft, ItemStack stack, int itemX, int itemY, int itemIconSize) {
        GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT);
        try {
            applyItemLayerBlendState();
            float scale = (float) itemIconSize / (float) VANILLA_ITEM_ICON_SIZE;
            if (Math.abs(scale - 1.0F) < 0.001F) {
                itemRenderer.renderItemAndEffectIntoGUI(minecraft.fontRenderer, minecraft.renderEngine, stack, itemX,
                        itemY);
                applyItemLayerBlendState();
                itemRenderer.renderItemOverlayIntoGUI(minecraft.fontRenderer, minecraft.renderEngine, stack, itemX,
                        itemY, null);
                return;
            }

            GL11.glPushMatrix();
            try {
                GL11.glTranslatef((float) itemX, (float) itemY, 0.0F);
                GL11.glScalef(scale, scale, 1.0F);
                itemRenderer.renderItemAndEffectIntoGUI(minecraft.fontRenderer, minecraft.renderEngine, stack, 0, 0);
                applyItemLayerBlendState();
                itemRenderer.renderItemOverlayIntoGUI(minecraft.fontRenderer, minecraft.renderEngine, stack, 0, 0,
                        null);
            } finally {
                GL11.glPopMatrix();
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    /**
     * 物品层现在回放到独立 FBO 中，颜色与 alpha 都应该完整写入该层，
     * 这样宿主才能在回贴到主 UI FBO 时只混合颜色、不改写主层 coverage alpha。
     */
    private static void applyItemLayerBlendState() {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }
}
