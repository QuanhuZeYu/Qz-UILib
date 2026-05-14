package club.heiqi.uilib.ui.dom;

import net.minecraft.util.ResourceLocation;

import club.heiqi.uilib.ui.image.HostImageSource;
import club.heiqi.uilib.ui.paint.DocumentCustomRenderer;
import club.heiqi.uilib.ui.render.UiRenderContext;

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
                HostImageSource source = resolveSource(element.getAttribute("src"));
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
        return resolvePositiveAttribute(element, "width", DEFAULT_INTRINSIC_WIDTH);
    }

    /**
     * 解析图片元素的首版固有高度。
     *
     * @param element 图片元素
     * @return 固有高度
     */
    public static int resolveIntrinsicHeight(ElementNode element) {
        return resolvePositiveAttribute(element, "height", DEFAULT_INTRINSIC_HEIGHT);
    }

    private static HostImageSource resolveSource(String src) {
        ResourceLocation texture = resolveResourceLocation(src);
        return texture == null ? null : HostImageSource.texture(texture, 1, 1);
    }

    private static ResourceLocation resolveResourceLocation(String src) {
        if (src == null) {
            return null;
        }
        String trimmed = src.trim();
        if (trimmed.isEmpty() || trimmed.indexOf("://") >= 0) {
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
}
