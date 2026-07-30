package club.heiqi.uilib.ui.image;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;

/** Minecraft 的 ItemStack icon-only 渲染委托。 */
public final class MinecraftItemIconRenderer implements ItemIconRenderer {

    private static final int VANILLA_ITEM_ICON_SIZE = 16;
    private static final String[] ITEM_OPERATION_NAMES = {
            "item.matrix-push", "item.prepare-state", "item.lighting-enable", "item.transform",
            "item.blend-prepare", "item.render-effect", "item.lighting-disable", "item.matrix-pop"
    };
    /** 与原版 GUI 物品渲染对齐的可见深度。 */
    static final float GUI_ITEM_Z_LEVEL = 100.0F;

    private RenderItem itemRenderer;

    @Override
    public HostImageRenderOutcome render(ItemStack itemStack, int left, int top, int side) {
        if (itemStack == null || itemStack.getItem() == null || side <= 0) {
            return HostImageRenderOutcome.unavailable("precheck", null, "invalid-item-icon-request");
        }
        RenderItem resolvedItemRenderer = getItemRenderer();
        Minecraft minecraft = Minecraft.getMinecraft();
        float scale = (float) side / (float) VANILLA_ITEM_ICON_SIZE;

        GL11.glPushMatrix();
        HostImageGlErrorTracker.checkpoint(ITEM_OPERATION_NAMES[0]);
        try {
            prepareItemState();
            HostImageGlErrorTracker.checkpoint(ITEM_OPERATION_NAMES[1]);
            RenderHelper.enableGUIStandardItemLighting();
            HostImageGlErrorTracker.checkpoint(ITEM_OPERATION_NAMES[2]);
            runWithGuiItemDepth(new ItemDepthAccess() {
                @Override
                public float get() {
                    return resolvedItemRenderer.zLevel;
                }

                @Override
                public void set(float zLevel) {
                    resolvedItemRenderer.zLevel = zLevel;
                }
            }, () -> {
                GL11.glTranslatef(left, top, 0.0F);
                GL11.glScalef(scale, scale, 1.0F);
                HostImageGlErrorTracker.checkpoint(ITEM_OPERATION_NAMES[3]);
                applyImageBlendState();
                HostImageGlErrorTracker.checkpoint(ITEM_OPERATION_NAMES[4]);
                resolvedItemRenderer.renderItemAndEffectIntoGUI(
                        minecraft.fontRenderer, minecraft.renderEngine, itemStack, 0, 0);
                HostImageGlErrorTracker.checkpoint(ITEM_OPERATION_NAMES[5]);
            });
            return HostImageRenderOutcome.publishable();
        } finally {
            RenderHelper.disableStandardItemLighting();
            HostImageGlErrorTracker.checkpoint(ITEM_OPERATION_NAMES[6]);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            HostImageGlErrorTracker.checkpoint(ITEM_OPERATION_NAMES[7]);
        }
    }

    /** @return 物品生产路径的稳定 GL operation 名序列副本 */
    static String[] itemOperationNames() {
        return ITEM_OPERATION_NAMES.clone();
    }

    /** 在 GUI 可见深度执行物品绘制，并无条件恢复调用前深度。 */
    static void runWithGuiItemDepth(ItemDepthAccess depthAccess, Runnable renderAction) {
        float previousZLevel = depthAccess.get();
        depthAccess.set(GUI_ITEM_Z_LEVEL);
        try {
            renderAction.run();
        } finally {
            depthAccess.set(previousZLevel);
        }
    }

    /** 可在纯 JVM 测试中替换的 zLevel 最小访问缝。 */
    interface ItemDepthAccess {
        float get();
        void set(float zLevel);
    }

    private RenderItem getItemRenderer() {
        if (itemRenderer == null) {
            itemRenderer = new RenderItem();
        }
        return itemRenderer;
    }

    private static void prepareItemState() {
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glColorMask(true, true, true, true);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void applyImageBlendState() {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }
}
