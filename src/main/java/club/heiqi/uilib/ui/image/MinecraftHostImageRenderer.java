package club.heiqi.uilib.ui.image;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import org.apache.commons.io.IOUtils;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;

import club.heiqi.uilib.internal.image.HostImageResourceEpoch;

/**
 * 基于 Minecraft 运行时的普通 texture/bitmap 渲染实现。
 */
public final class MinecraftHostImageRenderer implements HostImageRenderer {

    private final Map<String, ResourceLocation> dynamicImageTextures = new HashMap<String, ResourceLocation>();
    private final HostTextureResourceChecker textureResourceChecker;
    private final DynamicImageTextureAccess dynamicImageTextureAccess;
    private int resourceEpoch = HostImageResourceEpoch.current();

    /**
     * 创建使用 Minecraft 资源管理器检查纹理可用性的宿主图片渲染器。
     */
    public MinecraftHostImageRenderer() {
        this(new MinecraftTextureResourceChecker(), new MinecraftDynamicImageTextureAccess());
    }

    /**
     * 创建注入纹理资源检查器的宿主图片渲染器。
     *
     * @param textureResourceChecker 纹理资源检查器
     */
    MinecraftHostImageRenderer(HostTextureResourceChecker textureResourceChecker) {
        this(textureResourceChecker, new MinecraftDynamicImageTextureAccess());
    }

    /** 创建可注入动态纹理生命周期访问器的测试实例。 */
    MinecraftHostImageRenderer(HostTextureResourceChecker textureResourceChecker,
            DynamicImageTextureAccess dynamicImageTextureAccess) {
        this.textureResourceChecker = textureResourceChecker == null
                ? new MinecraftTextureResourceChecker()
                : textureResourceChecker;
        this.dynamicImageTextureAccess = dynamicImageTextureAccess == null
                ? new MinecraftDynamicImageTextureAccess()
                : dynamicImageTextureAccess;
    }

    @Override
    public void render(HostImageSource source, int left, int top, int right, int bottom) {
        clearAfterResourceReload();
        if (source == null || right <= left || bottom <= top) {
            return;
        }
        if (source.getKind() == HostImageSource.Kind.BUFFERED_IMAGE) {
            renderBufferedImage(source, left, top, right, bottom);
            return;
        }
        if (source.getKind() == HostImageSource.Kind.TEXTURE) {
            renderTexture(source, left, top, right, bottom);
        }
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

    ResourceLocation resolveDynamicImageTexture(HostImageSource source) {
        String imageKey = source.getImageKey();
        ResourceLocation cachedTexture = dynamicImageTextures.get(imageKey);
        if (cachedTexture != null) {
            return cachedTexture;
        }
        BufferedImage image = source.getBufferedImage();
        if (image == null) {
            return null;
        }
        ResourceLocation texture = dynamicImageTextureAccess.create("qz_img", image);
        dynamicImageTextures.put(imageKey, texture);
        return texture;
    }

    /** 删除本 renderer 上传的全部动态位图纹理。 */
    @Override
    public void close() {
        clearDynamicImageTextures();
    }

    private void clearAfterResourceReload() {
        int currentResourceEpoch = HostImageResourceEpoch.current();
        if (currentResourceEpoch == resourceEpoch) {
            return;
        }
        clearDynamicImageTextures();
        resourceEpoch = currentResourceEpoch;
    }

    private void clearDynamicImageTextures() {
        Throwable firstFailure = null;
        Iterator<Map.Entry<String, ResourceLocation>> iterator = dynamicImageTextures.entrySet().iterator();
        while (iterator.hasNext()) {
            ResourceLocation texture = iterator.next().getValue();
            try {
                dynamicImageTextureAccess.delete(texture);
                iterator.remove();
            } catch (RuntimeException exception) {
                firstFailure = appendFailure(firstFailure, exception);
            } catch (LinkageError error) {
                firstFailure = appendFailure(firstFailure, error);
            } catch (Error error) {
                firstFailure = appendFailure(firstFailure, error);
            }
        }
        if (firstFailure instanceof RuntimeException) {
            throw (RuntimeException) firstFailure;
        }
        if (firstFailure instanceof LinkageError) {
            throw (LinkageError) firstFailure;
        }
        if (firstFailure instanceof Error) {
            throw (Error) firstFailure;
        }
    }

    private static Throwable appendFailure(Throwable firstFailure, Throwable nextFailure) {
        if (firstFailure == null) {
            return nextFailure;
        }
        if (isFatal(nextFailure) && !isFatal(firstFailure)) {
            if (firstFailure != nextFailure) nextFailure.addSuppressed(firstFailure);
            return nextFailure;
        }
        if (firstFailure != nextFailure) firstFailure.addSuppressed(nextFailure);
        return firstFailure;
    }

    private static boolean isFatal(Throwable failure) {
        return failure instanceof Error && !(failure instanceof LinkageError);
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

/** 动态 bitmap texture 的最小创建/删除访问面。 */
interface DynamicImageTextureAccess {
    ResourceLocation create(String key, BufferedImage image);
    void delete(ResourceLocation texture);
}

/** Minecraft TextureManager 的动态纹理生命周期访问器。 */
final class MinecraftDynamicImageTextureAccess implements DynamicImageTextureAccess {
    @Override
    public ResourceLocation create(String key, BufferedImage image) {
        return Minecraft.getMinecraft().getTextureManager()
                .getDynamicTextureLocation(key, new DynamicTexture(image));
    }

    @Override
    public void delete(ResourceLocation texture) {
        Minecraft.getMinecraft().getTextureManager().deleteTexture(texture);
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
