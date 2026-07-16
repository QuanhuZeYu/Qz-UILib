package club.heiqi.uilib.ui.image;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.apache.commons.io.IOUtils;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;

/**
 * 基于 Minecraft 运行时的宿主图片渲染实现。
 */
public final class MinecraftHostImageRenderer implements HostImageRenderer {

    private static final int VANILLA_ITEM_ICON_SIZE = 16;
    private static final String[] ITEM_OPERATION_NAMES = {
            "item.matrix-push", "item.prepare-state", "item.lighting-enable", "item.transform",
            "item.blend-prepare", "item.render-effect", "item.blend-reset", "item.render-overlay",
            "item.lighting-disable", "item.matrix-pop"
    };
    /** 与原版 GUI 物品渲染对齐的可见深度。 */
    static final float GUI_ITEM_Z_LEVEL = 100.0F;

    private RenderItem itemRenderer;
    private final Map<String, ResourceLocation> dynamicImageTextures = new HashMap<String, ResourceLocation>();
    private final HostTextureResourceChecker textureResourceChecker;

    /**
     * 创建使用 Minecraft 资源管理器检查纹理可用性的宿主图片渲染器。
     */
    public MinecraftHostImageRenderer() {
        this(new MinecraftTextureResourceChecker());
    }

    /**
     * 创建注入纹理资源检查器的宿主图片渲染器。
     *
     * @param textureResourceChecker 纹理资源检查器
     */
    MinecraftHostImageRenderer(HostTextureResourceChecker textureResourceChecker) {
        this.textureResourceChecker = textureResourceChecker == null
                ? new MinecraftTextureResourceChecker()
                : textureResourceChecker;
    }

    @Override
    public void render(HostImageSource source, int left, int top, int right, int bottom) {
        if (source == null || right <= left || bottom <= top) {
            return;
        }
        if (source.getKind() == HostImageSource.Kind.ITEM_STACK) {
            renderItemStack(source, left, top, right, bottom);
            return;
        }
        if (source.getKind() == HostImageSource.Kind.BUFFERED_IMAGE) {
            renderBufferedImage(source, left, top, right, bottom);
            return;
        }
        renderTexture(source, left, top, right, bottom);
    }

    private void renderItemStack(HostImageSource source, int left, int top, int right, int bottom) {
        ItemStack stack = source.getItemStack();
        if (stack == null || stack.getItem() == null) {
            return;
        }
        RenderItem resolvedItemRenderer = getItemRenderer();

        Minecraft minecraft = Minecraft.getMinecraft();
        int targetWidth = right - left;
        int targetHeight = bottom - top;
        int iconSize = Math.max(1, Math.min(targetWidth, targetHeight));
        float scale = (float) iconSize / (float) VANILLA_ITEM_ICON_SIZE;
        float offsetX = left + (targetWidth - iconSize) / 2.0F;
        float offsetY = top + (targetHeight - iconSize) / 2.0F;

        GL11.glPushMatrix();
        HostImageGlErrorTracker.checkpoint(ITEM_OPERATION_NAMES[0]);
        try {
            prepareHostImageState();
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
                GL11.glTranslatef(offsetX, offsetY, 0.0F);
                GL11.glScalef(scale, scale, 1.0F);
                HostImageGlErrorTracker.checkpoint(ITEM_OPERATION_NAMES[3]);
                applyImageBlendState();
                HostImageGlErrorTracker.checkpoint(ITEM_OPERATION_NAMES[4]);
                resolvedItemRenderer.renderItemAndEffectIntoGUI(
                        minecraft.fontRenderer, minecraft.renderEngine, stack, 0, 0);
                HostImageGlErrorTracker.checkpoint(ITEM_OPERATION_NAMES[5]);
                applyImageBlendState();
                HostImageGlErrorTracker.checkpoint(ITEM_OPERATION_NAMES[6]);
                resolvedItemRenderer.renderItemOverlayIntoGUI(
                        minecraft.fontRenderer, minecraft.renderEngine, stack, 0, 0, null);
                HostImageGlErrorTracker.checkpoint(ITEM_OPERATION_NAMES[7]);
            });
        } finally {
            RenderHelper.disableStandardItemLighting();
            HostImageGlErrorTracker.checkpoint(ITEM_OPERATION_NAMES[8]);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            HostImageGlErrorTracker.checkpoint(ITEM_OPERATION_NAMES[9]);
        }
    }

    /** @return 物品生产路径的稳定 GL operation 名序列副本 */
    static String[] itemOperationNames() {
        return ITEM_OPERATION_NAMES.clone();
    }

    /**
     * 在 GUI 可见深度执行物品绘制，并无条件恢复调用前深度。
     *
     * @param depthAccess 物品渲染深度访问缝
     * @param renderAction 物品绘制动作
     */
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
        /** @return 当前 zLevel */
        float get();

        /** @param zLevel 待设置的 zLevel */
        void set(float zLevel);
    }

    /**
     * 按需创建 Minecraft 物品渲染器，避免构造阶段触发客户端静态初始化。
     *
     * @return 物品渲染器
     */
    private RenderItem getItemRenderer() {
        if (itemRenderer == null) {
            itemRenderer = new RenderItem();
        }
        return itemRenderer;
    }

    private void renderTexture(HostImageSource source, int left, int top, int right, int bottom) {
        ResourceLocation texture = source.getTexture();
        if (texture == null) {
            return;
        }
        if (!textureResourceChecker.isTextureAvailable(texture)) {
            return;
        }
        renderTextureRegion(texture, source.getRegionU(), source.getRegionV(), source.getRegionWidth(),
                source.getRegionHeight(), source.getTextureWidth(), source.getTextureHeight(), left, top, right,
                bottom);
    }

    private void renderBufferedImage(HostImageSource source, int left, int top, int right, int bottom) {
        BufferedImage image = source.getBufferedImage();
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            return;
        }
        ResourceLocation texture = resolveDynamicImageTexture(source);
        if (texture == null) {
            return;
        }
        renderTextureRegion(texture, 0, 0, image.getWidth(), image.getHeight(), image.getWidth(), image.getHeight(),
                left, top, right, bottom);
    }

    private ResourceLocation resolveDynamicImageTexture(HostImageSource source) {
        String imageKey = source.getImageKey();
        ResourceLocation cachedTexture = dynamicImageTextures.get(imageKey);
        if (cachedTexture != null) {
            return cachedTexture;
        }
        BufferedImage image = source.getBufferedImage();
        if (image == null) {
            return null;
        }
        ResourceLocation texture = Minecraft.getMinecraft().getTextureManager()
                .getDynamicTextureLocation("qz_img", new DynamicTexture(image));
        dynamicImageTextures.put(imageKey, texture);
        return texture;
    }

    private void renderTextureRegion(ResourceLocation texture, int regionU, int regionV, int regionWidth,
            int regionHeight, int textureWidth, int textureHeight, int left, int top, int right, int bottom) {
        Minecraft minecraft = Minecraft.getMinecraft();
        float u0 = (float) regionU / (float) textureWidth;
        float v0 = (float) regionV / (float) textureHeight;
        float u1 = (float) (regionU + regionWidth) / (float) textureWidth;
        float v1 = (float) (regionV + regionHeight) / (float) textureHeight;

        prepareHostImageState();
        minecraft.getTextureManager().bindTexture(texture);
        preparePlainTextureQuadState();
        applyImageBlendState();
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_F(1.0F, 1.0F, 1.0F, 1.0F);
        tessellator.addVertexWithUV(left, bottom, 0.0D, u0, v1);
        tessellator.addVertexWithUV(right, bottom, 0.0D, u1, v1);
        tessellator.addVertexWithUV(right, top, 0.0D, u1, v0);
        tessellator.addVertexWithUV(left, top, 0.0D, u0, v0);
        tessellator.draw();
    }

    private static void preparePlainTextureQuadState() {
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
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

/**
 * 宿主纹理资源可用性检查。
 */
interface HostTextureResourceChecker {

    /**
     * 判断指定纹理是否可由宿主资源系统解析。
     *
     * @param texture 纹理资源位置
     * @return 纹理是否可用
     */
    boolean isTextureAvailable(ResourceLocation texture);
}

/**
 * 基于 Minecraft 资源管理器的纹理可用性检查。
 */
final class MinecraftTextureResourceChecker implements HostTextureResourceChecker {

    @Override
    public boolean isTextureAvailable(ResourceLocation texture) {
        if (texture == null) {
            return false;
        }
        IResourceManager resourceManager = Minecraft.getMinecraft().getResourceManager();
        if (resourceManager == null) {
            return false;
        }
        InputStream stream = null;
        try {
            IResource resource = resourceManager.getResource(texture);
            if (resource == null) {
                return false;
            }
            stream = resource.getInputStream();
            return true;
        } catch (IOException ignored) {
            return false;
        } finally {
            IOUtils.closeQuietly(stream);
        }
    }
}
