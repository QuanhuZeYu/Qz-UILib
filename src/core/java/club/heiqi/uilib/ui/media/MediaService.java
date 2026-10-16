package club.heiqi.uilib.ui.media;

import club.heiqi.uilib.ui.media.spi.MediaAudioSinkFactory;
import club.heiqi.uilib.ui.media.spi.MediaDecoderFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** 可交给页面/业务已有资源作用域；服务不绑定 scene，不初始化适配。 */
public final class MediaService implements AutoCloseable {
    private static final AtomicLong IDS=new AtomicLong();
    private final MediaDecoderFactory decoders;
    private final MediaAudioSinkFactory audio;
    private final MediaBudget budget;
    private final Map<Long,MediaSession> sessions=new LinkedHashMap<>();
    private boolean closed;
    public MediaService(MediaDecoderFactory decoders, MediaAudioSinkFactory audio, MediaBudget budget) {
        this.decoders=Objects.requireNonNull(decoders,"decoders");
        this.audio=Objects.requireNonNull(audio,"audio"); this.budget=Objects.requireNonNull(budget,"budget");
    }
    public synchronized MediaSession open(MediaSource source, boolean autoplay) {
        if (closed) throw new IllegalStateException("媒体服务已关闭");
        if (sessions.size()>=budget.sessions) throw new IllegalStateException("媒体会话预算已满");
        long id=IDS.incrementAndGet();
        MediaSession session=new MediaSession(this,id,Objects.requireNonNull(source,"source"),autoplay,decoders,audio,budget);
        sessions.put(id,session); session.start(); return session;
    }
    public MediaPresentation presented(MediaFrameKey key, long sampledNanos) {
        Objects.requireNonNull(key,"key"); MediaSession session;
        synchronized(this) { session=sessions.get(key.sessionId); }
        return session==null ? null : session.presented(key,sampledNanos);
    }
    /** GPU owner 只持服务与纯值 key，按归属会话取得有限呈现联系。 */
    public MediaFrameLease retainPresentation(MediaFrameKey key) {
        Objects.requireNonNull(key,"key"); MediaSession session;
        synchronized(this) { session=sessions.get(key.sessionId); }
        return session==null ? null : session.retainPresentation(key);
    }
    public synchronized int activeSessions() { return sessions.size(); }
    synchronized void released(long id) { sessions.remove(id); }
    @Override public void close() {
        ArrayList<MediaSession> owned;
        synchronized(this) { if (closed) return; closed=true; owned=new ArrayList<>(sessions.values()); }
        Throwable failure=null;
        for (MediaSession session:owned) try { session.close(); } catch (Throwable next) { failure=combine(failure,next); }
        if (failure!=null) throw new IllegalStateException("媒体服务释放失败",failure);
    }
    static Throwable combine(Throwable first,Throwable next) {
        if (first==null) return next;
        if (first!=next) first.addSuppressed(next);
        return first;
    }
}
