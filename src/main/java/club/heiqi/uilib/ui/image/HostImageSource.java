package club.heiqi.uilib.ui.image;

import java.awt.image.BufferedImage;
import java.util.Objects;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import club.heiqi.uilib.ui.diagnostic.UiPerfMarkers;
import club.heiqi.uilib.ui.scene.image.SceneImageSource;

/**
 * 宿主图片源描述。
 *
 * <p>该类型只描述“画什么”，不暴露任何 OpenGL、FBO 或宿主绘制细节。
 * 文档作者可以把它当成 `img src` 的宿主版本：当前支持静态 Minecraft 物品图标、纹理与运行时位图。</p>
 */
public final class HostImageSource implements SceneImageSource {

    /**
     * 宿主图片源类型。
     */
    public enum Kind {
        ITEM_ICON,
        TEXTURE,
        BUFFERED_IMAGE
    }

    private final Kind kind;
    private final ItemStack itemIconStack;
    private final ResourceLocation texture;
    private final BufferedImage bufferedImage;
    private final String imageKey;
    private final int textureWidth;
    private final int textureHeight;
    private final int regionU;
    private final int regionV;
    private final int regionWidth;
    private final int regionHeight;
    /**
     * 显式渲染分级注册键（可空）。
     *
     * <p>{@code null} 表示沿用「物品注册名:meta」自算口径；非 {@code null} 时 {@link #registryKey()}
     * 直接返回该键。用途：候选域键与物品域键不同的消费者（选择器候选/变体）需要按候选 key 直接分级
     * （ADR §5.1 / Z-2、Z-3）；本类为 final 且渲染入口只认本类型，故只能由显式键工厂提供接缝。</p>
     */
    private final String explicitRegistryKey;

    private HostImageSource(Kind kind, ItemStack itemIconStack, ResourceLocation texture, BufferedImage bufferedImage,
            String imageKey, int textureWidth, int textureHeight, int regionU, int regionV, int regionWidth,
            int regionHeight, String explicitRegistryKey) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.itemIconStack = itemIconStack;
        this.texture = texture;
        this.bufferedImage = bufferedImage;
        this.imageKey = imageKey;
        this.textureWidth = Math.max(1, textureWidth);
        this.textureHeight = Math.max(1, textureHeight);
        this.regionU = Math.max(0, regionU);
        this.regionV = Math.max(0, regionV);
        this.regionWidth = Math.max(1, regionWidth);
        this.regionHeight = Math.max(1, regionHeight);
        this.explicitRegistryKey = explicitRegistryKey == null || explicitRegistryKey.trim().isEmpty()
                ? null
                : explicitRegistryKey.trim();
    }

    /**
     * 创建生命周期内固定的物品图标源。
     *
     * <p>工厂在返回前执行完整 {@link ItemStack#copy()}，后续不再读取调用方持有的可变实例。</p>
     *
     * <p><b>埋点归属（本工厂不埋点）</b>：{@link UiPerfMarkers#COUNTER_IMAGE_ICON_CREATED} 的
     * 口径是「宿主物品图标源的物化次数」，但采样门控必须取自环境端口，而本类是无环境引用的静态工厂
     * （见 {@code UiEnvironment} 的值读纪律）—— 故计数由<b>持有诊断环境的调用方</b>
     * （图标缓存 / 装配层）在采样作用域内记录，本方法只负责创建。</p>
     *
     * @param itemStack 要在创建时复制的物品
     * @return 静态物品图标源
     */
    public static HostImageSource itemIcon(ItemStack itemStack) {
        return itemIcon(itemStack, null);
    }

    /**
     * 创建生命周期内固定的物品图标源，并显式指定渲染分级注册键。
     *
     * <p>与 {@link #itemIcon(ItemStack)} 的唯一差别是分级键：{@code explicitRegistryKey} 非空时
     * {@link #registryKey()} 直接返回它，不再按「物品注册名:meta」自算。这解决「候选域键 ≠ 物品域键」
     * （方块注册名 vs 物品注册名 + meta）时回退集合命中不了、meta 粒度丢失的问题（ADR §5.1 的 S-1 根因）。</p>
     *
     * <p>渲染语义完全不变：本类仍是 final，渲染入口仍按 {@code instanceof HostImageSource} 分派，
     * 快照语义（创建时 {@link ItemStack#copy()}）与图像内容不随键变化。</p>
     *
     * @param itemStack 要在创建时复制的物品
     * @param explicitRegistryKey 显式分级键；{@code null} 或空白表示沿用自算口径
     * @return 静态物品图标源
     */
    public static HostImageSource itemIcon(ItemStack itemStack, String explicitRegistryKey) {
        if (itemStack == null || itemStack.getItem() == null) {
            throw new IllegalArgumentException("itemStack must contain an item");
        }
        return new HostImageSource(Kind.ITEM_ICON, itemStack.copy(), null, null, null,
                16, 16, 0, 0, 16, 16, explicitRegistryKey);
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
        return new HostImageSource(Kind.TEXTURE, null, resolvedTexture, null, null, textureWidth, textureHeight,
                regionU, regionV, regionWidth, regionHeight, null);
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
        return new HostImageSource(Kind.BUFFERED_IMAGE, null, null, resolvedImage, resolvedImageKey,
                resolvedImage.getWidth(), resolvedImage.getHeight(), 0, 0, resolvedImage.getWidth(),
                resolvedImage.getHeight(), null);
    }

    public Kind getKind() {
        return kind;
    }

    /**
     * 返回与 source 内部快照隔离的物品副本。
     *
     * @return 物品图标快照副本；非物品图标源返回 {@code null}
     */
    public ItemStack getItemIconStack() {
        return itemIconStack == null ? null : itemIconStack.copy();
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

    /**
     * 渲染分级注册键：物品图标 = {@code 注册名:meta}（跨 ItemStack 副本稳定），
     * 其余种类返回 null（不参与物品渲染分级）。
     *
     * @return 稳定注册键或 {@code null}
     */
    @Override
    public String registryKey() {
        if (explicitRegistryKey != null) {
            return explicitRegistryKey;
        }
        if (kind != Kind.ITEM_ICON || itemIconStack == null) {
            return null;
        }
        net.minecraft.item.Item item = itemIconStack.getItem();
        if (item == null) {
            return null;
        }
        Object name = net.minecraft.item.Item.itemRegistry.getNameForObject(item);
        if (name == null) {
            return null;
        }
        return name + ":" + itemIconStack.getItemDamage();
    }
}
