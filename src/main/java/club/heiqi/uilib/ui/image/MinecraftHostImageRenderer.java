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
        if (!club.heiqi.uilib.Config.useDebug) {
            renderInternal(source, left, top, right, bottom);
            return;
        }
        // [临时诊断] 按 kind 计时单个宿主图片绘制，定位 CUSTOM ~14ms 落在 item 渲染/位图上传/纹理绘制哪条路径
        long start = System.nanoTime();
        renderInternal(source, left, top, right, bottom);
        recordHostImageProfile(source.getKind(), System.nanoTime() - start);
    }

    private void renderInternal(HostImageSource source, int left, int top, int right, int bottom) {
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

    /** [临时诊断] 宿主图片按 kind 分类耗时累计与节流日志的共享状态，仅 Config.useDebug 时生效。 */
    private static final Map<HostImageSource.Kind, long[]> HOST_IMAGE_PROFILE =
            new java.util.EnumMap<HostImageSource.Kind, long[]>(HostImageSource.Kind.class);
    private static long lastHostImageProfileLogNanos;
    /** [临时诊断] 本轮统计期内 BUFFERED_IMAGE 首次上传 DynamicTexture 的次数与累计耗时。 */
    private static int bufferedUploadCount;
    private static long bufferedUploadNanos;

    /**
     * [临时诊断] 累计单次宿主图片绘制耗时，并按 1 秒节流打印各 kind 的总耗时/次数与位图上传统计。
     *
     * @param kind 图片来源类型
     * @param nanos 本次绘制耗时（纳秒）
     */
    private static void recordHostImageProfile(HostImageSource.Kind kind, long nanos) {
        synchronized (HOST_IMAGE_PROFILE) {
            long[] slot = HOST_IMAGE_PROFILE.get(kind);
            if (slot == null) {
                slot = new long[2];
                HOST_IMAGE_PROFILE.put(kind, slot);
            }
            slot[0] += nanos;
            slot[1]++;
            long now = System.nanoTime();
            if (now - lastHostImageProfileLogNanos < 1_000_000_000L) {
                return;
            }
            lastHostImageProfileLogNanos = now;
            StringBuilder builder = new StringBuilder(128);
            boolean first = true;
            for (Map.Entry<HostImageSource.Kind, long[]> entry : HOST_IMAGE_PROFILE.entrySet()) {
                if (!first) {
                    builder.append(", ");
                }
                first = false;
                builder.append(entry.getKey().name()).append('=')
                        .append(String.format(java.util.Locale.ROOT, "%.2f", Double.valueOf(entry.getValue()[0] / 1_000_000.0D)))
                        .append("ms/").append(entry.getValue()[1]);
            }
            club.heiqi.uilib.MyMod.LOG.info("宿主图片绘制诊断: 各kind[{}], 位图上传 {}ms/{}次",
                    builder.toString(),
                    String.format(java.util.Locale.ROOT, "%.2f", Double.valueOf(bufferedUploadNanos / 1_000_000.0D)),
                    Integer.valueOf(bufferedUploadCount));
            HOST_IMAGE_PROFILE.clear();
            bufferedUploadCount = 0;
            bufferedUploadNanos = 0L;
        }
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

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            prepareHostImageState();
            RenderHelper.enableGUIStandardItemLighting();
            resolvedItemRenderer.zLevel = 0.0F;
            GL11.glTranslatef(offsetX, offsetY, 0.0F);
            GL11.glScalef(scale, scale, 1.0F);
            applyImageBlendState();
            resolvedItemRenderer.renderItemAndEffectIntoGUI(minecraft.fontRenderer, minecraft.renderEngine, stack, 0, 0);
            applyImageBlendState();
            resolvedItemRenderer.renderItemOverlayIntoGUI(minecraft.fontRenderer, minecraft.renderEngine, stack, 0, 0,
                    null);
        } finally {
            resolvedItemRenderer.zLevel = 0.0F;
            RenderHelper.disableStandardItemLighting();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
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
        // [临时诊断] 计量 DynamicTexture 上传（首次未命中缓存）耗时，确认 CUSTOM 大头是否为每帧重新上传
        long uploadStart = club.heiqi.uilib.Config.useDebug ? System.nanoTime() : 0L;
        ResourceLocation texture = Minecraft.getMinecraft().getTextureManager()
                .getDynamicTextureLocation("qz_img", new DynamicTexture(image));
        if (club.heiqi.uilib.Config.useDebug) {
            synchronized (HOST_IMAGE_PROFILE) {
                bufferedUploadCount++;
                bufferedUploadNanos += System.nanoTime() - uploadStart;
            }
        }
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

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
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
        } finally {
            GL11.glPopAttrib();
        }
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
