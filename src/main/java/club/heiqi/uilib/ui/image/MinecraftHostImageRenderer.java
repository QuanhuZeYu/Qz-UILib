package club.heiqi.uilib.ui.image;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;

/**
 * 基于 Minecraft 运行时的宿主图片渲染实现。
 */
public final class MinecraftHostImageRenderer implements HostImageRenderer {

    private static final int VANILLA_ITEM_ICON_SIZE = 16;

    private final RenderItem itemRenderer = new RenderItem();

    @Override
    public void render(HostImageSource source, int left, int top, int right, int bottom) {
        if (source == null || right <= left || bottom <= top) {
            return;
        }
        if (source.getKind() == HostImageSource.Kind.ITEM_STACK) {
            renderItemStack(source, left, top, right, bottom);
            return;
        }
        renderTexture(source, left, top, right, bottom);
    }

    private void renderItemStack(HostImageSource source, int left, int top, int right, int bottom) {
        ItemStack stack = source.getItemStack();
        if (stack == null || stack.getItem() == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        int targetWidth = right - left;
        int targetHeight = bottom - top;
        int iconSize = Math.max(1, Math.min(targetWidth, targetHeight));
        float scale = (float) iconSize / (float) VANILLA_ITEM_ICON_SIZE;
        float offsetX = left + (targetWidth - iconSize) / 2.0F;
        float offsetY = top + (targetHeight - iconSize) / 2.0F;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            prepareHostImageState();
            RenderHelper.enableGUIStandardItemLighting();
            itemRenderer.zLevel = 0.0F;
            GL11.glTranslatef(offsetX, offsetY, 0.0F);
            GL11.glScalef(scale, scale, 1.0F);
            itemRenderer.renderItemAndEffectIntoGUI(minecraft.fontRenderer, minecraft.renderEngine, stack, 0, 0);
            applyImageBlendState();
            itemRenderer.renderItemOverlayIntoGUI(minecraft.fontRenderer, minecraft.renderEngine, stack, 0, 0, null);
        } finally {
            itemRenderer.zLevel = 0.0F;
            RenderHelper.disableStandardItemLighting();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private void renderTexture(HostImageSource source, int left, int top, int right, int bottom) {
        ResourceLocation texture = source.getTexture();
        if (texture == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        float u0 = (float) source.getRegionU() / (float) source.getTextureWidth();
        float v0 = (float) source.getRegionV() / (float) source.getTextureHeight();
        float u1 = (float) (source.getRegionU() + source.getRegionWidth()) / (float) source.getTextureWidth();
        float v1 = (float) (source.getRegionV() + source.getRegionHeight()) / (float) source.getTextureHeight();

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            prepareHostImageState();
            minecraft.getTextureManager().bindTexture(texture);
            applyImageBlendState();
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            tessellator.addVertexWithUV(left, bottom, 0.0D, u0, v1);
            tessellator.addVertexWithUV(right, bottom, 0.0D, u1, v1);
            tessellator.addVertexWithUV(right, top, 0.0D, u1, v0);
            tessellator.addVertexWithUV(left, top, 0.0D, u0, v0);
            tessellator.draw();
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static void prepareHostImageState() {
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
