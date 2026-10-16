package club.heiqi.uilib.ui.scene.video;

import club.heiqi.uilib.ui.media.MediaVideoFrame;
import club.heiqi.uilib.ui.media.MediaSnapshot;
import club.heiqi.uilib.ui.media.MediaSession;
import club.heiqi.uilib.ui.media.MediaFrameLease;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;



import club.heiqi.uilib.ui.scene.runtime.BindingActivation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 宿主 prepareResources 消费挂载 consumer；后台媒体线程没有状态域或 scene 联系。 */
public final class MediaViewPump implements AutoCloseable {
    private final Thread thread = Thread.currentThread();
    private final Map<MediaSession, List<Registration>> consumers = new LinkedHashMap<>();
    private boolean closed;

    public static final class Extent {
        public final int width, height;
        Extent(int width, int height) { this.width = width; this.height = height; }
        @Override public boolean equals(Object value) {
            return value instanceof Extent && ((Extent) value).width == width && ((Extent) value).height == height;
        }
        @Override public int hashCode() { return 31 * width + height; }
    }
    /** 每个独立视图持自己的呈现信号，不持有会话关闭权。 */
    public static final class View {
        private final ReactiveDomain domain;
        private final MediaSession session;
        private final Signal<MediaSnapshot> snapshot;
        private final Signal<MediaVideoFrame> frame;
        private final Signal<Extent> extent;
        private View(ReactiveDomain domain, MediaSession session) {
            this.domain = domain; this.session = session;
            MediaSnapshot initial = session.snapshot();
            snapshot = domain.signal(initial); frame = domain.signal(initial.video);
            extent = domain.signal(extentOf(initial));
        }
        // decoder.info可预读后续尺寸；当前几何必须与实际选帧保持同一事实。
        private static Extent extentOf(MediaSnapshot value) {
            MediaVideoFrame selected = value.video;
            return selected == null ? new Extent(value.info.width, value.info.height) : new Extent(selected.width, selected.height);
        }
        public ReadableSignal<MediaSnapshot> snapshot() { return snapshot; }
        public ReadableSignal<MediaVideoFrame> frame() { return frame; }
        public ReadableSignal<Extent> extent() { return extent; }
        private void publish(MediaSnapshot next, MediaVideoFrame after) {
            MediaVideoFrame before = frame.get();
            if (before == null ? after != null : after == null || !before.key.equals(after.key)) frame.set(after);
            extent.set(after == null ? new Extent(next.info.width, next.info.height) : new Extent(after.width, after.height));
            snapshot.set(next);
        }
    }
    public View view(ReactiveDomain domain, MediaSession session) {
        Objects.requireNonNull(domain, "domain").checkWrite();
        synchronized (consumers) { if (closed) throw new IllegalStateException("媒体视图泵已关闭"); }
        return new View(domain, Objects.requireNonNull(session, "session"));
    }
    public BindingActivation attach(View view, ReactiveDomain domain) {
        domain.checkWrite();
        if (view.domain != domain) throw new IllegalArgumentException("媒体视图与挂载域不一致");
        Registration registration = new Registration(view);
        List<Registration> peers;
        synchronized (consumers) {
            if (closed) throw new IllegalStateException("媒体视图泵已关闭");
            List<Registration> registrations = consumers.computeIfAbsent(view.session, ignored -> new ArrayList<>());
            registrations.add(registration); peers = new ArrayList<>(registrations);
        }
        MediaSnapshot snapshot = view.session.snapshot();
        Publication initial = null;
        // 初次挂载也共借同key联系，不能让多个视图占满预算而饿住正式Prepared。
        for (Registration peer : peers) { initial = peer.borrow(snapshot); if (initial != null) break; }
        if (initial == null) initial = new Publication(view.session, snapshot);
        try { registration.publish(initial); return registration; }
        catch (RuntimeException | Error failure) { registration.close(); throw failure; }
        finally { initial.close(); }
    }
    public int activeConsumers() {
        synchronized (consumers) { int count = 0; for (List<Registration> list : consumers.values()) count += list.size(); return count; }
    }
    public void prepareResources() {
        checkThread();
        Map<MediaSession, List<Registration>> active = new LinkedHashMap<>();
        synchronized (consumers) {
            if (closed) return;
            for (Map.Entry<MediaSession, List<Registration>> entry : consumers.entrySet())
                active.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        for (Map.Entry<MediaSession, List<Registration>> entry : active.entrySet()) {
            Publication next = new Publication(entry.getKey(), entry.getKey().snapshot());
            try { for (Registration registration : entry.getValue()) registration.offer(next); }
            finally { next.close(); }
        }
    }
    /** 同一会话一批发布只占一个精确租约；多个consumer与待执行动作共借，不复制像素。 */
    private static final class Publication {
        final MediaSnapshot snapshot;
        final MediaFrameLease lease;
        private int owners = 1;
        Publication(MediaSession session, MediaSnapshot snapshot) {
            this.snapshot = snapshot;
            lease = snapshot.video == null ? null : session.retainPresentation(snapshot.video.key);
        }
        synchronized Publication retain() {
            if (owners == 0) throw new IllegalStateException("媒体发布已释放");
            owners++; return this;
        }
        boolean active() { return lease != null && lease.active(); }
        synchronized void close() { if (--owners == 0 && lease != null) lease.close(); }
    }
    private final class Registration implements BindingActivation {
        final View view;
        private boolean active = true, scheduled;
        private Publication pending, published;
        Registration(View view) { this.view = view; }
        private synchronized Publication borrow(MediaSnapshot value) {
            if (!active || value.video == null) return null;
            if (published != null && published.active() && published.lease.key.equals(value.video.key)) return published.retain();
            if (pending != null && pending.active() && pending.lease.key.equals(value.video.key)) return pending.retain();
            return null;
        }
        void offer(Publication value) {
            synchronized (this) {
                if (!active) return;
                Publication previous = pending; pending = value.retain();
                if (previous != null) previous.close();
                if (scheduled) return;
                scheduled = true;
            }
            // 最多一个待发布动作；direct 域也只由合法宿主线程进入。
            view.domain.enqueue(() -> {
                synchronized (this) {
                    scheduled = false;
                    Publication next = pending; pending = null;
                    try { if (active && next != null) publish(next); }
                    finally { if (next != null) next.close(); }
                }
            });
        }
        private synchronized void publish(Publication next) {
            Publication selected = next.active() ? next : null;
            // snapshot与retain之间仍可能前移；不发布已经失去联系的帧，保留上次同代有效呈现到下次pump。
            if (selected == null && next.snapshot.video != null && published != null && published.active()
                    && published.snapshot.generation == next.snapshot.generation) selected = published;
            if (selected != null) selected.retain();
            Publication previous = published;
            try {
                view.publish(next.snapshot, selected == null ? null : selected.snapshot.video);
                published = selected;
            } catch (RuntimeException | Error failure) {
                if (selected != null) selected.close();
                throw failure;
            }
            if (previous != null) previous.close();
        }
        @Override public void refresh() {}
        @Override public void close() {
            synchronized (this) {
                active = false;
                if (pending != null) { pending.close(); pending = null; }
                if (published != null) { published.close(); published = null; }
            }
            synchronized (consumers) {
                List<Registration> list = consumers.get(view.session);
                if (list != null) { list.remove(this); if (list.isEmpty()) consumers.remove(view.session); }
            }
        }
    }
    private void checkThread() {
        if (Thread.currentThread() != thread) throw new IllegalStateException("媒体视图泵必须在宿主线程推进");
    }
    @Override public void close() {
        checkThread(); List<Registration> owned = new ArrayList<>();
        synchronized (consumers) {
            if (closed) return; closed = true;
            for (List<Registration> list : consumers.values()) owned.addAll(list);
        }
        for (Registration registration : owned) registration.close();
    }
}
