package club.heiqi.uilib.ui.media.spi;

import java.util.concurrent.CancellationException;

public interface MediaCancellation {
    boolean cancelled();
    default void check() { if (cancelled()) throw new CancellationException("媒体操作已取消"); }
}
