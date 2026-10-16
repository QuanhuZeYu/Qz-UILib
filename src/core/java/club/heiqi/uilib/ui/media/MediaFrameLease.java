package club.heiqi.uilib.ui.media;

import java.util.concurrent.atomic.AtomicBoolean;

/** Prepared/回放持有的有限提交联系；不拥有像素或 native，关闭后解除本次持有。 */
public final class MediaFrameLease implements AutoCloseable {
    public final MediaFrameKey key;
    private final MediaSession session;
    private final AtomicBoolean closed=new AtomicBoolean();
    MediaFrameLease(MediaSession session,MediaFrameKey key) {this.session=session;this.key=key;}
    /** draw/提交之前再次检查；seek/source/close 立即使旧代联系失效。 */
    public boolean active() {return !closed.get() && session.presentationActive(key);}
    @Override public void close() {if(closed.compareAndSet(false,true)) session.releasePresentation(key);}
}
