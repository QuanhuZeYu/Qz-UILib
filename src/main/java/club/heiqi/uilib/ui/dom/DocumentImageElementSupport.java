package club.heiqi.uilib.ui.dom;

import java.awt.image.BufferedImage;

import net.minecraft.util.ResourceLocation;

import club.heiqi.uilib.ui.image.DocumentRemoteImageCache;
import club.heiqi.uilib.ui.image.DocumentRemoteImageCache.Entry;
import club.heiqi.uilib.ui.image.DocumentRemoteImageCache.Status;
import club.heiqi.uilib.ui.image.HostImageSource;
import club.heiqi.uilib.ui.paint.DocumentCustomRenderer;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.style.cascade.ComputedStyle;
import club.heiqi.uilib.ui.style.props.UiFontStyle;
import club.heiqi.uilib.ui.style.props.UiFontWeight;
import club.heiqi.uilib.ui.style.props.UiObjectFit;
import club.heiqi.uilib.ui.style.cascade.UiStyleResolver;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.text.TextContentMode;

/**
 * 浏览器式 `img` 元素的最小宿主位图适配。
 */
public final class DocumentImageElementSupport {

    public static final int DEFAULT_INTRINSIC_WIDTH = 16;
    public static final int DEFAULT_INTRINSIC_HEIGHT = 16;
    private static final int FALLBACK_TEXT_PADDING_X = 4;
    private static final int FALLBACK_TEXT_PADDING_Y = 4;

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
                ResolvedImageSource resolvedSource = resolveSource(element);
                if (resolvedSource.hostImageSource == null) {
                    renderFallbackText(element, resolvedSource, context, contentLeft, contentTop, contentRight,
                            contentBottom);
                    return;
                }
                ImageContentRect targetRect = resolveObjectFitRect(element, resolvedSource.hostImageSource, contentLeft,
                        contentTop,
                        contentRight, contentBottom);
                context.pushClip(contentLeft, contentTop, contentRight, contentBottom);
                try {
                    context.drawHostImage(resolvedSource.hostImageSource, targetRect.left, targetRect.top, targetRect.right,
                            targetRect.bottom);
                } finally {
                    context.popClip();
                }
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

    private static ResolvedImageSource resolveSource(ElementNode element) {
        String src = element.getAttribute("src");
        if (isRemoteUrl(src)) {
            Entry entry = DocumentRemoteImageCache.getInstance().request(src, new Runnable() {
                @Override
                public void run() {
                    element.setAttribute("data-img-load-revision", String.valueOf(System.nanoTime()));
                }
            });
            BufferedImage image = entry.getImage();
            return image == null
                    ? new ResolvedImageSource(null, entry.getStatus())
                    : new ResolvedImageSource(HostImageSource.bufferedImage(image, src.trim()), entry.getStatus());
        }
        ResourceLocation texture = resolveResourceLocation(src);
        return texture == null
                ? new ResolvedImageSource(null, Status.FAILED)
                : new ResolvedImageSource(HostImageSource.texture(texture, 1, 1), Status.LOADED);
    }

    private static void renderFallbackText(ElementNode element, ResolvedImageSource resolvedSource,
            UiRenderContext context, int contentLeft, int contentTop, int contentRight, int contentBottom) {
        if (context == null || element == null) {
            return;
        }
        if (resolvedSource != null && resolvedSource.status != Status.FAILED) {
            return;
        }
        String fallbackText = normalizeFallbackText(element.getAttribute("alt"));
        if (fallbackText.isEmpty()) {
            return;
        }
        int availableWidth = Math.max(0, contentRight - contentLeft - FALLBACK_TEXT_PADDING_X * 2);
        if (availableWidth <= 0 || contentBottom <= contentTop) {
            return;
        }
        ComputedStyle style = UiStyleResolver.compute(element);
        String measuredText = fallbackText;
        int textWidth = context.measureTextWidth(measuredText, TextContentMode.UILIB_RAW);
        if (textWidth > availableWidth) {
            int approxRawWidth = Math.max(0, Math.round(availableWidth / 2.0F));
            measuredText = context.getFontRenderer().trimStringToWidth(measuredText, approxRawWidth);
            if (measuredText == null || measuredText.isEmpty()) {
                return;
            }
        }
        int textX = contentLeft + FALLBACK_TEXT_PADDING_X;
        int lineHeight = Math.max(context.getTextLineHeight(), 1);
        int centeredTop = contentTop + Math.max(0, (contentBottom - contentTop - lineHeight) / 2);
        int textY = centeredTop + FALLBACK_TEXT_PADDING_Y / 2;
        context.pushClip(contentLeft, contentTop, contentRight, contentBottom);
        try {
            context.drawText(measuredText, textX, textY, style.getTextColor(), false, TextContentMode.UILIB_RAW,
                    style.getFontWeight() == null ? UiFontWeight.NORMAL : style.getFontWeight(),
                    style.getFontStyle() == null ? UiFontStyle.NORMAL : style.getFontStyle());
        } finally {
            context.popClip();
        }
    }

    private static ImageContentRect resolveObjectFitRect(ElementNode element, HostImageSource source, int contentLeft,
            int contentTop, int contentRight, int contentBottom) {
        int boxWidth = Math.max(0, contentRight - contentLeft);
        int boxHeight = Math.max(0, contentBottom - contentTop);
        if (boxWidth <= 0 || boxHeight <= 0) {
            return new ImageContentRect(contentLeft, contentTop, contentLeft, contentTop);
        }
        ComputedStyle style = UiStyleResolver.compute(element);
        UiObjectFit objectFit = style.getObjectFit();
        if (objectFit == UiObjectFit.FILL) {
            return new ImageContentRect(contentLeft, contentTop, contentRight, contentBottom);
        }
        int intrinsicWidth = Math.max(1, resolveIntrinsicWidth(element));
        int intrinsicHeight = Math.max(1, resolveIntrinsicHeight(element));
        float fitScale = resolveObjectFitScale(objectFit, boxWidth, boxHeight, intrinsicWidth, intrinsicHeight);
        int targetWidth = Math.max(1, Math.round(intrinsicWidth * fitScale));
        int targetHeight = Math.max(1, Math.round(intrinsicHeight * fitScale));
        int left = contentLeft + (boxWidth - targetWidth) / 2;
        int top = contentTop + (boxHeight - targetHeight) / 2;
        return new ImageContentRect(left, top, left + targetWidth, top + targetHeight);
    }

    private static float resolveObjectFitScale(UiObjectFit objectFit, int boxWidth, int boxHeight,
            int intrinsicWidth, int intrinsicHeight) {
        float containScale = Math.min((float) boxWidth / (float) intrinsicWidth,
                (float) boxHeight / (float) intrinsicHeight);
        if (objectFit == UiObjectFit.CONTAIN) {
            return containScale;
        }
        if (objectFit == UiObjectFit.COVER) {
            return Math.max((float) boxWidth / (float) intrinsicWidth, (float) boxHeight / (float) intrinsicHeight);
        }
        if (objectFit == UiObjectFit.SCALE_DOWN) {
            return Math.min(1.0F, containScale);
        }
        return 1.0F;
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

    private static String normalizeFallbackText(String alt) {
        if (alt == null) {
            return "";
        }
        String trimmed = alt.trim();
        return trimmed.isEmpty() ? "" : trimmed;
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

    private static final class ResolvedImageSource {

        private final HostImageSource hostImageSource;
        private final Status status;

        private ResolvedImageSource(HostImageSource hostImageSource, Status status) {
            this.hostImageSource = hostImageSource;
            this.status = status == null ? Status.PENDING : status;
        }
    }

    private static final class ImageContentRect {

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;

        private ImageContentRect(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = Math.max(left, right);
            this.bottom = Math.max(top, bottom);
        }
    }
}
