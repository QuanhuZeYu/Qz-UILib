package club.heiqi.uilib.ui.media;

import java.net.URI;
import java.util.Objects;

/** 来源只是值；构造和读取它均不加载任何适配或 native。 */
public final class MediaSource {
    public final URI uri;
    private MediaSource(URI uri) {
        this.uri = Objects.requireNonNull(uri, "uri");
        if (!uri.isAbsolute()) throw new IllegalArgumentException("媒体来源必须是绝对 URI");
    }
    public static MediaSource of(URI uri) { return new MediaSource(uri); }
    @Override public String toString() { return uri.toString(); }
}
