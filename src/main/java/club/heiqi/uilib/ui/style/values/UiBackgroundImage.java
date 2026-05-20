package club.heiqi.uilib.ui.style.values;

import java.awt.image.BufferedImage;
import java.util.Objects;

import net.minecraft.util.ResourceLocation;

import club.heiqi.uilib.ui.image.HostImageSource;

/**
 * CSS-like background-image 单图值。
 *
 * <p>当前最小闭环只承载一张宿主图片源，绘制阶段拉伸填充元素 border box。</p>
 */
public final class UiBackgroundImage {

    private final HostImageSource source;

    private UiBackgroundImage(HostImageSource source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    /**
     * 使用宿主图片源创建背景图。
     *
     * @param source 宿主图片源
     * @return 背景图值
     */
    public static UiBackgroundImage of(HostImageSource source) {
        return new UiBackgroundImage(source);
    }

    /**
     * 使用整张 Minecraft 纹理创建背景图。
     *
     * @param texture 纹理资源
     * @param textureWidth 纹理宽度
     * @param textureHeight 纹理高度
     * @return 背景图值
     */
    public static UiBackgroundImage texture(ResourceLocation texture, int textureWidth, int textureHeight) {
        return of(HostImageSource.texture(texture, textureWidth, textureHeight));
    }

    /**
     * 使用资源标识创建整张 Minecraft 纹理背景图。
     *
     * @param textureId 资源标识，例如 {@code minecraft:textures/gui/options_background.png}
     * @param textureWidth 纹理宽度
     * @param textureHeight 纹理高度
     * @return 背景图值
     */
    public static UiBackgroundImage texture(String textureId, int textureWidth, int textureHeight) {
        String resolvedTextureId = Objects.requireNonNull(textureId, "textureId").trim();
        if (resolvedTextureId.isEmpty()) {
            throw new IllegalArgumentException("textureId cannot be empty");
        }
        return texture(new ResourceLocation(resolvedTextureId), textureWidth, textureHeight);
    }

    /**
     * 使用运行时位图创建背景图。
     *
     * @param image 位图
     * @param imageKey 稳定缓存键
     * @return 背景图值
     */
    public static UiBackgroundImage bufferedImage(BufferedImage image, String imageKey) {
        return of(HostImageSource.bufferedImage(image, imageKey));
    }

    /**
     * 返回宿主图片源。
     *
     * @return 宿主图片源
     */
    public HostImageSource getSource() {
        return source;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof UiBackgroundImage)) {
            return false;
        }
        UiBackgroundImage other = (UiBackgroundImage) obj;
        return source.equals(other.source);
    }

    @Override
    public int hashCode() {
        return source.hashCode();
    }
}
