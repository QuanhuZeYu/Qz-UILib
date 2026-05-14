package club.heiqi.uilib.ui.dom;

import java.awt.image.BufferedImage;

import net.minecraft.util.ResourceLocation;

import club.heiqi.uilib.ui.image.DocumentRemoteImageCache;
import club.heiqi.uilib.ui.image.HostImageSource;
import club.heiqi.uilib.ui.paint.DocumentCustomRenderer;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.style.ComputedStyle;
import club.heiqi.uilib.ui.style.UiStyleLength;

/**
 * 浏览器式 `img` 元素的最小宿主位图适配。
 */
public final class DocumentImageElementSupport {

    public static final int DEFAULT_INTRINSIC_WIDTH = 16;
    public static final int DEFAULT_INTRINSIC_HEIGHT = 16;

    private DocumentImageElementSupport() {}

    /**
     * 判断标签名是否为 `img`。
     *
     * @param tagName 标签名
     * @return 是否为图片标签
     */
    public static boolean isImageTag(String tagName) {
        return "img".equals(tagName);
    }

    /**
     * 为图片元素挂接自动绘制能力。
     *
     * @param element 图片元素
     */
    public static void attach(ElementNode element) {
        element.setCustomRenderer(new DocumentCustomRenderer() {
            @Override
            public void render(UiRenderContext context, int contentLeft, int contentTop, int contentRight,
                    int contentBottom) {
                HostImageSource source = resolveSource(element);
                if (source == null) {
                    return;
                }
                context.drawHostImage(source, contentLeft, contentTop, contentRight, contentBottom);
            }
        });
    }

    /**
     * 解析图片元素的首版固有宽度。
     *
     * @param element 图片元素
     * @return 固有宽度
     */
    public static int resolveIntrinsicWidth(ElementNode element) {
        ImageSize remoteSize = resolveRemoteImageSize(element);
        if (remoteSize != null) {
            return remoteSize.width;
        }
        return resolvePositiveAttribute(element, "width", DEFAULT_INTRINSIC_WIDTH);
    }

    /**
     * 解析图片元素的首版固有高度。
     *
     * @param element 图片元素
     * @return 固有高度
     */
    public static int resolveIntrinsicHeight(ElementNode element) {
        ImageSize remoteSize = resolveRemoteImageSize(element);
        if (remoteSize != null) {
            return remoteSize.height;
        }
        return resolvePositiveAttribute(element, "height", DEFAULT_INTRINSIC_HEIGHT);
    }

    /**
     * 按浏览器 replaced element 心智解析内容宽度。
     *
     * @param element 图片元素
     * @param computedStyle 计算样式
     * @param containingWidth 包含块宽度
     * @param autoContentWidth auto 回退宽度
     * @return 内容宽度
     */
    public static int resolveContentWidth(ElementNode element, ComputedStyle computedStyle, int containingWidth,
            int autoContentWidth) {
        if (!isAuto(computedStyle.getWidth())) {
            return Math.max(0, computedStyle.getWidth().resolve(containingWidth, autoContentWidth));
        }
        if (!isAuto(computedStyle.getHeight())) {
            int resolvedHeight = Math.max(0, computedStyle.getHeight().resolve(0, resolveIntrinsicHeight(element)));
            return scaleWidthForHeight(element, resolvedHeight);
        }
        return Math.min(resolveIntrinsicWidth(element), autoContentWidth);
    }

    /**
     * 按浏览器 replaced element 心智解析内容高度。
     *
     * @param element 图片元素
     * @param computedStyle 计算样式
     * @return 内容高度
     */
    public static int resolveContentHeight(ElementNode element, ComputedStyle computedStyle, int contentWidth) {
        if (!isAuto(computedStyle.getHeight())) {
            return Math.max(0, computedStyle.getHeight().resolve(0, resolveIntrinsicHeight(element)));
        }
        if (!isAuto(computedStyle.getWidth())) {
            return scaleHeightForWidth(element, contentWidth);
        }
        return resolveIntrinsicHeight(element);
    }

    private static HostImageSource resolveSource(ElementNode element) {
        String src = element.getAttribute("src");
        if (isRemoteUrl(src)) {
            DocumentRemoteImageCache.Entry entry = DocumentRemoteImageCache.getInstance().request(src, new Runnable() {
                @Override
                public void run() {
                    element.setAttribute("data-img-load-revision", String.valueOf(System.nanoTime()));
                }
            });
            BufferedImage image = entry.getImage();
            return image == null ? null : HostImageSource.bufferedImage(image, src.trim());
        }
        ResourceLocation texture = resolveResourceLocation(src);
        return texture == null ? null : HostImageSource.texture(texture, 1, 1);
    }

    private static ImageSize resolveRemoteImageSize(ElementNode element) {
        String src = element.getAttribute("src");
        if (!isRemoteUrl(src)) {
            return null;
        }
        DocumentRemoteImageCache.Entry entry = DocumentRemoteImageCache.getInstance().request(src, new Runnable() {
            @Override
            public void run() {
                element.setAttribute("data-img-load-revision", String.valueOf(System.nanoTime()));
            }
        });
        BufferedImage image = entry.getImage();
        return image == null ? null : new ImageSize(image.getWidth(), image.getHeight());
    }

    private static ResourceLocation resolveResourceLocation(String src) {
        if (src == null) {
            return null;
        }
        String trimmed = src.trim();
        if (trimmed.isEmpty() || isRemoteUrl(trimmed) || trimmed.indexOf("://") >= 0) {
            return null;
        }
        int namespaceSeparator = trimmed.indexOf(':');
        if (namespaceSeparator < 0) {
            return new ResourceLocation(trimmed);
        }
        String namespace = trimmed.substring(0, namespaceSeparator).trim();
        String path = trimmed.substring(namespaceSeparator + 1).trim();
        if (namespace.isEmpty() || path.isEmpty()) {
            return null;
        }
        return new ResourceLocation(namespace, path);
    }

    private static int resolvePositiveAttribute(ElementNode element, String attributeName, int fallback) {
        String value = element.getAttribute(attributeName);
        if (value == null) {
            return fallback;
        }
        try {
            int parsedValue = Integer.parseInt(value.trim());
            return parsedValue > 0 ? parsedValue : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean isRemoteUrl(String src) {
        if (src == null) {
            return false;
        }
        String trimmed = src.trim();
        return trimmed.regionMatches(true, 0, "http://", 0, 7)
                || trimmed.regionMatches(true, 0, "https://", 0, 8);
    }

    private static boolean isAuto(UiStyleLength length) {
        return length == null || length.getType() == UiStyleLength.Type.AUTO;
    }

    private static int scaleWidthForHeight(ElementNode element, int height) {
        int intrinsicHeight = Math.max(1, resolveIntrinsicHeight(element));
        int intrinsicWidth = Math.max(1, resolveIntrinsicWidth(element));
        return Math.max(1, Math.round(height * (float) intrinsicWidth / (float) intrinsicHeight));
    }

    private static int scaleHeightForWidth(ElementNode element, int width) {
        int intrinsicWidth = Math.max(1, resolveIntrinsicWidth(element));
        int intrinsicHeight = Math.max(1, resolveIntrinsicHeight(element));
        return Math.max(1, Math.round(width * (float) intrinsicHeight / (float) intrinsicWidth));
    }

    private static final class ImageSize {

        private final int width;
        private final int height;

        private ImageSize(int width, int height) {
            this.width = Math.max(1, width);
            this.height = Math.max(1, height);
        }
    }
}
