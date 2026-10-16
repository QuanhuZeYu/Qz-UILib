package club.heiqi.uilib.ui.scene.video;

import club.heiqi.uilib.ui.media.MediaVideoFrame;
import club.heiqi.uilib.ui.media.MediaSnapshot;
import club.heiqi.uilib.ui.media.MediaSession;
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
        private void publish(MediaSnapshot next) {
            MediaVideoFrame before = frame.get(), after = next.video;
            if (before == null ? after != null : after == null || !before.key.equals(after.key)) frame.set(after);
            extent.set(extentOf(next));
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
        synchronized (consumers) {
            if (closed) throw new IllegalStateException("媒体视图泵已关闭");
            consumers.computeIfAbsent(view.session, ignored -> new ArrayList<>()).add(registration);
        }
        try { view.publish(view.session.snapshot()); return registration; }
        catch (RuntimeException | Error failure) { registration.close(); throw failure; }
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
            MediaSnapshot snapshot = entry.getKey().snapshot();
            for (Registration registration : entry.getValue()) registration.offer(snapshot);
        }
    }
    private final class Registration implements BindingActivation {
        final View view;
        private boolean active = true, scheduled;
        private MediaSnapshot pending;
        Registration(View view) { this.view = view; }
        void offer(MediaSnapshot value) {
            synchronized (this) {
                if (!active) return;
                pending = value;
                if (scheduled) return;
                scheduled = true;
            }
            // 最多一个待发布动作；direct 域也只由合法宿主线程进入。
            view.domain.enqueue(() -> {
                synchronized (this) {
                    scheduled = false;
                    MediaSnapshot next = pending; pending = null;
                    if (active && next != null) view.publish(next);
                }
            });
        }
        @Override public void refresh() {}
        @Override public void close() {
            synchronized (this) { active = false; pending = null; }
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
