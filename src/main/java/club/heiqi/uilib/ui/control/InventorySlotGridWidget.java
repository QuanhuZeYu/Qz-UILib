package club.heiqi.uilib.ui.control;

import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.widget.Widget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * 只读背包格子网格控件。
 */
public class InventorySlotGridWidget extends Widget {

    private static final RenderItem ITEM_RENDERER = new RenderItem();

    private final int startSlot;
    private final int slotCount;
    private final int preferredColumns;

    private int slotGap = 8;
    private int preferredSlotSize = 34;
    private int minSlotSize = 22;
    private int maxSlotSize = 46;

    /**
     * 创建一个只读物品格子网格。
     *
     * @param startSlot 起始槽位
     * @param slotCount 槽位数量
     * @param preferredColumns 期望列数
     */
    public InventorySlotGridWidget(int startSlot, int slotCount, int preferredColumns) {
        this.startSlot = Math.max(0, startSlot);
        this.slotCount = Math.max(0, slotCount);
        this.preferredColumns = Math.max(1, preferredColumns);
    }

    @Override
    protected void drawSelf(UiRenderContext context) {
        GridMetrics metrics = resolveGridMetrics(Math.max(1, getWidth()));
        int slotSize = metrics.slotSize;
        int absoluteX = getAbsoluteX() + Math.max(0, (getWidth() - metrics.totalWidth) / 2);
        int absoluteY = getAbsoluteY();
        InventoryPlayer inventory = getPlayerInventory();

        for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
            int column = slotIndex % metrics.columnCount;
            int row = slotIndex / metrics.columnCount;
            int left = absoluteX + column * (slotSize + slotGap);
            int top = absoluteY + row * (slotSize + slotGap);
            ItemStack stack = inventory == null ? null : getStackAt(inventory, slotIndex);
            int fillColor = stack == null ? 0xAA171C24 : 0xCC202A38;
            int borderColor = stack == null ? 0xFF465468 : 0xFF9AB8F2;
            context.fillRect(left, top, left + slotSize, top + slotSize, fillColor);
            context.drawBorder(left, top, left + slotSize, top + slotSize, borderColor);
        }

        if (inventory == null) {
            return;
        }

        GL11.glPushMatrix();
        try {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            GL11.glEnable(GL11.GL_LIGHTING);
            RenderHelper.enableGUIStandardItemLighting();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            ITEM_RENDERER.zLevel = 100.0F;

            for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
                ItemStack stack = getStackAt(inventory, slotIndex);
                if (stack == null || stack.getItem() == null) {
                    continue;
                }

                int column = slotIndex % metrics.columnCount;
                int row = slotIndex / metrics.columnCount;
                int left = absoluteX + column * (slotSize + slotGap);
                int top = absoluteY + row * (slotSize + slotGap);
                int itemX = left + Math.max(0, (slotSize - 16) / 2);
                int itemY = top + Math.max(0, (slotSize - 16) / 2);
                ITEM_RENDERER.renderItemAndEffectIntoGUI(Minecraft.getMinecraft().fontRenderer,
                        Minecraft.getMinecraft().renderEngine, stack, itemX, itemY);
                ITEM_RENDERER.renderItemOverlayIntoGUI(Minecraft.getMinecraft().fontRenderer,
                        Minecraft.getMinecraft().renderEngine, stack, itemX, itemY, null);
            }
        } finally {
            ITEM_RENDERER.zLevel = 0.0F;
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glPopMatrix();
        }
    }

    @Override
    public int getPreferredWidth() {
        int columnCount = Math.max(1, Math.min(slotCount, preferredColumns));
        return columnCount * preferredSlotSize + Math.max(0, columnCount - 1) * slotGap;
    }

    @Override
    public int getPreferredHeight() {
        return getPreferredHeightForWidth(getPreferredWidth());
    }

    @Override
    public int getPreferredHeightForWidth(int width) {
        GridMetrics metrics = resolveGridMetrics(Math.max(1, width));
        return metrics.rowCount * metrics.slotSize + Math.max(0, metrics.rowCount - 1) * slotGap;
    }

    @Override
    public int getMinContentWidth() {
        return minSlotSize;
    }

    /**
     * 设置格子间距。
     *
     * @param slotGap 间距
     * @return 当前控件
     */
    public InventorySlotGridWidget setSlotGap(int slotGap) {
        this.slotGap = Math.max(0, slotGap);
        requestLayout();
        return this;
    }

    /**
     * 设置格子尺寸上下限。
     *
     * @param minSlotSize 最小尺寸
     * @param maxSlotSize 最大尺寸
     * @return 当前控件
     */
    public InventorySlotGridWidget setSlotSizeRange(int minSlotSize, int maxSlotSize) {
        this.minSlotSize = Math.max(18, minSlotSize);
        this.maxSlotSize = Math.max(this.minSlotSize, maxSlotSize);
        requestLayout();
        return this;
    }

    /**
     * 设置期望格子尺寸，实际列数会围绕这个尺寸自动重排。
     *
     * @param preferredSlotSize 期望格子尺寸
     * @return 当前控件
     */
    public InventorySlotGridWidget setPreferredSlotSize(int preferredSlotSize) {
        this.preferredSlotSize = Math.max(18, preferredSlotSize);
        requestLayout();
        return this;
    }

    private InventoryPlayer getPlayerInventory() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.thePlayer == null) {
            return null;
        }
        return minecraft.thePlayer.inventory;
    }

    private ItemStack getStackAt(InventoryPlayer inventory, int localIndex) {
        int slotIndex = startSlot + localIndex;
        if (inventory == null || inventory.mainInventory == null || slotIndex < 0 || slotIndex >= inventory.mainInventory.length) {
            return null;
        }
        return inventory.mainInventory[slotIndex];
    }

    private GridMetrics resolveGridMetrics(int width) {
        GridMetrics metrics = new GridMetrics();
        metrics.columnCount = resolveColumnCount(width);
        metrics.rowCount = Math.max(1, (slotCount + metrics.columnCount - 1) / metrics.columnCount);
        int totalGap = Math.max(0, metrics.columnCount - 1) * slotGap;
        int rawSlotSize = Math.max(18, (width - totalGap) / metrics.columnCount);
        metrics.slotSize = Math.max(minSlotSize, Math.min(maxSlotSize, rawSlotSize));
        metrics.totalWidth = metrics.columnCount * metrics.slotSize + totalGap;
        return metrics;
    }

    private int resolveColumnCount(int width) {
        if (slotCount <= 0) {
            return 1;
        }

        int fitByPreferredSize = Math.max(1, (width + slotGap) / Math.max(1, preferredSlotSize + slotGap));
        int fitByMinimumSize = Math.max(1, (width + slotGap) / Math.max(1, minSlotSize + slotGap));
        int columnCount = Math.max(1, Math.min(slotCount, fitByPreferredSize));
        return Math.min(columnCount, Math.max(1, Math.min(slotCount, fitByMinimumSize)));
    }

    /**
     * 当前宽度下的格子布局结果。
     */
    private static class GridMetrics {
        private int columnCount;
        private int rowCount;
        private int slotSize;
        private int totalWidth;
    }
}
