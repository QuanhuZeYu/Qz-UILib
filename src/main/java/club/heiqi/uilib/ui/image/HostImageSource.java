package club.heiqi.uilib.ui.image;

import java.awt.image.BufferedImage;
import java.util.Objects;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import club.heiqi.uilib.ui.scene.image.SceneImageSource;

/**
 * 宿主图片源描述。
 *
 * <p>该类型只描述“画什么”，不暴露任何 OpenGL、FBO 或宿主绘制细节。
 * 文档作者可以把它当成 `img src` 的宿主版本：当前支持 Minecraft 物品、纹理与运行时位图。</p>
 */
public final class HostImageSource implements SceneImageSource {

    /** ItemStack 图片在跨帧缓存中的更新语义。 */
    public enum ItemPolicy {
        /** 至少每 500ms 重新读取一次调用方持有的 ItemStack。 */
        LIVE,
        /** 只在 source/session 纪元变化时重新栅格化。 */
        SNAPSHOT
    }

    /**
     * 宿主图片源类型。
     */
    public enum Kind {
        ITEM_STACK,
        TEXTURE,
        BUFFERED_IMAGE
    }

    private final Kind kind;
    private final ItemStack itemStack;
    private final ItemPolicy itemPolicy;
    private final ResourceLocation texture;
    private final BufferedImage bufferedImage;
    private final String imageKey;
    private final int textureWidth;
    private final int textureHeight;
    private final int regionU;
    private final int regionV;
    private final int regionWidth;
    private final int regionHeight;

    private HostImageSource(Kind kind, ItemStack itemStack, ItemPolicy itemPolicy, ResourceLocation texture, BufferedImage bufferedImage,
            String imageKey, int textureWidth, int textureHeight, int regionU, int regionV, int regionWidth,
            int regionHeight) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.itemStack = itemStack;
        this.itemPolicy = itemPolicy;
        this.texture = texture;
        this.bufferedImage = bufferedImage;
        this.imageKey = imageKey;
        this.textureWidth = Math.max(1, textureWidth);
        this.textureHeight = Math.max(1, textureHeight);
        this.regionU = Math.max(0, regionU);
        this.regionV = Math.max(0, regionV);
        this.regionWidth = Math.max(1, regionWidth);
        this.regionHeight = Math.max(1, regionHeight);
    }

    /**
     * 创建物品图片源。
     *
     * @param itemStack 运行时物品
     * @return 物品图片源
     */
    public static HostImageSource itemStack(ItemStack itemStack) {
        if (itemStack == null || itemStack.getItem() == null) {
            throw new IllegalArgumentException("itemStack must contain an item");
        }
        return new HostImageSource(Kind.ITEM_STACK, itemStack, ItemPolicy.LIVE, null, null, null, 16, 16, 0, 0, 16, 16);
    }

    /**
     * 创建生命周期内不随原 ItemStack 变化的静态图片源。
     *
     * @param itemStack 要在创建时复制的物品
     * @return 静态物品图片源
     */
    public static HostImageSource itemStackSnapshot(ItemStack itemStack) {
        if (itemStack == null || itemStack.getItem() == null) {
            throw new IllegalArgumentException("itemStack must contain an item");
        }
        return new HostImageSource(Kind.ITEM_STACK, itemStack.copy(), ItemPolicy.SNAPSHOT,
                null, null, null, 16, 16, 0, 0, 16, 16);
    }

    /**
     * 创建整张纹理图片源。
     *
     * @param texture 纹理资源
     * @param textureWidth 纹理宽度
     * @param textureHeight 纹理高度
     * @return 纹理图片源
     */
    public static HostImageSource texture(ResourceLocation texture, int textureWidth, int textureHeight) {
        return textureRegion(texture, textureWidth, textureHeight, 0, 0, textureWidth, textureHeight);
    }

    /**
     * 创建纹理区域图片源。
     *
     * @param texture 纹理资源
     * @param textureWidth 整张纹理宽度
     * @param textureHeight 整张纹理高度
     * @param regionU 区域左上角 U
     * @param regionV 区域左上角 V
     * @param regionWidth 区域宽度
     * @param regionHeight 区域高度
     * @return 纹理区域图片源
     */
    public static HostImageSource textureRegion(ResourceLocation texture, int textureWidth, int textureHeight,
            int regionU, int regionV, int regionWidth, int regionHeight) {
        ResourceLocation resolvedTexture = Objects.requireNonNull(texture, "texture");
        if (textureWidth <= 0 || textureHeight <= 0) {
            throw new IllegalArgumentException("texture size must be positive");
        }
        if (regionWidth <= 0 || regionHeight <= 0) {
            throw new IllegalArgumentException("texture region size must be positive");
        }
        return new HostImageSource(Kind.TEXTURE, null, null, resolvedTexture, null, null, textureWidth, textureHeight,
                regionU, regionV, regionWidth, regionHeight);
    }

    /**
     * 创建运行时位图图片源。
     *
     * @param image 位图
     * @param imageKey 稳定缓存键
     * @return 位图图片源
     */
    public static HostImageSource bufferedImage(BufferedImage image, String imageKey) {
        BufferedImage resolvedImage = Objects.requireNonNull(image, "image");
        if (resolvedImage.getWidth() <= 0 || resolvedImage.getHeight() <= 0) {
            throw new IllegalArgumentException("image size must be positive");
        }
        String resolvedImageKey = imageKey == null || imageKey.trim().isEmpty()
                ? "image-" + System.identityHashCode(resolvedImage)
                : imageKey.trim();
        return new HostImageSource(Kind.BUFFERED_IMAGE, null, null, null, resolvedImage, resolvedImageKey,
                resolvedImage.getWidth(), resolvedImage.getHeight(), 0, 0, resolvedImage.getWidth(),
                resolvedImage.getHeight());
    }

    public Kind getKind() {
        return kind;
    }

    public ItemStack getItemStack() {
        return itemStack == null ? null : itemStack.copy();
    }

    /** @return ItemStack 跨帧更新语义；非物品源返回 {@code null} */
    public ItemPolicy getItemPolicy() {
        return itemPolicy;
    }

    public ResourceLocation getTexture() {
        return texture;
    }

    public BufferedImage getBufferedImage() {
        return bufferedImage;
    }

    public String getImageKey() {
        return imageKey;
    }

    public int getTextureWidth() {
        return textureWidth;
    }

    public int getTextureHeight() {
        return textureHeight;
    }

    public int getRegionU() {
        return regionU;
    }

    public int getRegionV() {
        return regionV;
    }

    public int getRegionWidth() {
        return regionWidth;
    }

    public int getRegionHeight() {
        return regionHeight;
    }
}
