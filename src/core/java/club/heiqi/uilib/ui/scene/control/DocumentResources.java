package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.image.ImageOptions;
import club.heiqi.uilib.ui.scene.image.ImageService;
import club.heiqi.uilib.ui.scene.image.ImageSource;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** 文档图像引用与基准位置；结构化内容仍由现有图像来源signal提供，不把宿主载荷写进DSL。 */
public final class DocumentResources {
    public static final class ImageReference {
        public final ReadableSignal<ImageSource> source;
        public final ReadableSignal<ImageOptions> options;

        public ImageReference(
                ReadableSignal<ImageSource> source, ReadableSignal<ImageOptions> options) {
            this.source = Objects.requireNonNull(source, "source");
            this.options = Objects.requireNonNull(options, "options");
        }
    }

    public final ImageService images;
    public final ReadableSignal<String> base;
    private final Map<String, ImageReference> references;
    private final Function<String, ImageSource> items;

    public DocumentResources(
            ImageService images,
            ReadableSignal<String> base,
            Map<String, ImageReference> references,
            Function<String, ImageSource> items) {
        this.images = Objects.requireNonNull(images, "images");
        this.base = Objects.requireNonNull(base, "base");
        this.references = Collections.unmodifiableMap(new LinkedHashMap<>(references));
        this.items = items;
    }

    ImageReference reference(String source) {
        return references.get(source);
    }

    String target(String source) {
        String location = base.get();
        if (location == null || location.isEmpty()) return source;
        try {
            return URI.create(location).resolve(source).toString();
        } catch (IllegalArgumentException invalid) {
            return source;
        }
    }

    ImageSource source(String value) {
        return ImageSource.parse(target(value), items);
    }
}
