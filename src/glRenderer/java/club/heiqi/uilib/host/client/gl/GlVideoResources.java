package club.heiqi.uilib.host.client.gl;

import club.heiqi.uilib.ui.media.MediaFrameKey;
import club.heiqi.uilib.ui.media.MediaVideoFrame;
import club.heiqi.uilib.ui.media.MediaPresentation;
import club.heiqi.uilib.ui.media.MediaFrameLease;
import club.heiqi.uilib.ui.media.MediaService;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.video.VideoPlacement;

import static club.heiqi.uilib.host.client.gl.GlTokens.*;

import club.heiqi.uilib.ui.scene.layout.LayoutBox;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 视频 GPU owner；不进入静态图像缓存。仅 prepare 上传，Prepared 生命周期固定每个精确 key 的槽。 */
public final class GlVideoResources implements AutoCloseable {
    public static final int DEFAULT_TEXTURES = 8;
    public static final long DEFAULT_BYTES = 80L * 1024 * 1024;
    private final Thread thread = Thread.currentThread();
    private final GlApi gl;
    private final MediaService service;
    private final int maxTextures, scratchBytes;
    private final long maxBytes;
    private final List<Slot> slots = new ArrayList<>();
    private ByteBuffer scratch;
    private long bytes, peakBytes;
    private int peakTextures;
    private Frame active;
    private boolean closed;
    private static final class Slot {
        MediaFrameKey key;
        int texture, width, height;
        long bytes;
    }
    private static final class Entry {
        final Slot slot;
        final MediaFrameLease lease;
        long submittedNanos;
        boolean transferred;
        Entry(Slot slot, MediaFrameLease lease) { this.slot = slot; this.lease = lease; }
    }
    public GlVideoResources(GlApi gl, MediaService service) { this(gl, service, DEFAULT_TEXTURES, DEFAULT_BYTES); }
    public GlVideoResources(GlApi gl, MediaService service, int maxTextures, long maxBytes) {
        if (maxTextures < 1 || maxBytes < 4 || maxBytes > Integer.MAX_VALUE) throw new IllegalArgumentException("无效视频GPU预算");
        this.gl = Objects.requireNonNull(gl, "gl"); this.service = Objects.requireNonNull(service, "service");
        this.maxTextures = maxTextures; this.maxBytes = maxBytes; this.scratchBytes = (int) maxBytes;
    }
    private void check() {
        if (closed || Thread.currentThread() != thread) throw new IllegalStateException("视频GPU owner已关闭或不在GL线程");
    }
    public Frame prepare(PaintPlan plan, LayoutBox viewport) {
        check(); if (active != null) throw new IllegalStateException("先结束视频Frame");
        Frame frame = new Frame(); active = frame;
        try {
            Map<MediaFrameKey, MediaVideoFrame> demand = new LinkedHashMap<>();
            for (PaintCommand command : plan.resourceCommands())
                if (command.kind == PaintCommand.Kind.VIDEO && visible(command, viewport))
                    demand.putIfAbsent(command.video.frame.key, command.video.frame);
            long required = 0;
            // 先保留全部有效联系并 pin resident；不能上传第二帧时覆写第一帧。
            for (MediaVideoFrame image : demand.values()) {
                MediaFrameLease lease = service.retainPresentation(image.key);
                if (lease == null) continue;
                frame.leases.put(image.key, lease);
                required = Math.addExact(required, image.bytes());
                if (frame.leases.size() > maxTextures || required > maxBytes) throw new IllegalStateException("本计划视频纹理需求超预算");
                if (image.width > gl.glGetInteger(GL_MAX_TEXTURE_SIZE) || image.height > gl.glGetInteger(GL_MAX_TEXTURE_SIZE))
                    throw new IllegalArgumentException("视频超出GL纹理尺寸");
                for (Slot slot : slots) if (image.key.equals(slot.key)) { frame.entries.put(image.key, new Entry(slot, lease)); break; }
            }
            for (MediaVideoFrame image : demand.values()) {
                MediaFrameLease lease = frame.leases.get(image.key);
                if (lease == null || frame.entries.containsKey(image.key) || !lease.active()) continue;
                Slot slot = chooseSlot(frame, image.bytes());
                upload(slot, image);
                frame.entries.put(image.key, new Entry(slot, lease));
            }
            return frame;
        } catch (RuntimeException | Error failure) {
            try { frame.close(); } catch (Throwable next) { if (next != failure) failure.addSuppressed(next); }
            throw failure;
        }
    }
    private static boolean visible(PaintCommand command, LayoutBox viewport) {
        LayoutBox clip = command.clip == null ? viewport : command.clip.intersect(viewport);
        club.heiqi.uilib.ui.scene.video.VideoPlacement p = command.video;
        return clip.width > 0 && clip.height > 0 && p.width > 0 && p.height > 0
                && p.x < (double) clip.x + clip.width && p.y < (double) clip.y + clip.height
                && p.x + p.width > clip.x && p.y + p.height > clip.y;
    }
    private Slot chooseSlot(Frame frame, long required) {
        Slot selected = null;
        for (Slot slot : slots) if (!frame.pinned(slot)) { selected = slot; break; }
        if (selected == null) {
            if (slots.size() >= maxTextures) throw new IllegalStateException("视频纹理槽预算已满");
            selected = new Slot(); slots.add(selected);
        }
        // 超出的旧 resident 没有本 Frame pin，才能退休其存储。
        for (Slot slot : slots) {
            if (bytes - selected.bytes + required <= maxBytes) break;
            if (slot != selected && !frame.pinned(slot)) release(slot);
        }
        if (bytes - selected.bytes + required > maxBytes) throw new IllegalStateException("视频纹理字节预算已满");
        selected.key = null;
        return selected;
    }
    private void upload(Slot slot, MediaVideoFrame frame) {
        if (scratch == null) scratch = ByteBuffer.allocateDirect(scratchBytes);
        scratch.clear(); scratch.put(frame.pixels()); scratch.flip();
        // CPU 载荷为 straight RGBA；共享 COLOR_PREMULT shader 需要预乘，绝不修改发布像素。
        for (int i = 0; i < scratch.limit(); i += 4) {
            int alpha = scratch.get(i + 3) & 255;
            if (alpha != 255) for (int c = 0; c < 3; c++) scratch.put(i + c, (byte) (((scratch.get(i + c) & 255) * alpha + 127) / 255));
        }
        try (GlCpuUploadScope ignored = new GlCpuUploadScope(gl, GL_TEXTURE_2D)) {
            boolean allocate = slot.texture == 0 || slot.width != frame.width || slot.height != frame.height;
            if (slot.texture == 0) slot.texture = gl.glGenTextures();
            try {
                gl.glBindTexture(GL_TEXTURE_2D, slot.texture);
                if (allocate) {
                    gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                    gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                    gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                    gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                    gl.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, frame.width, frame.height, 0, GL_RGBA, GL_UNSIGNED_BYTE, scratch);
                    bytes += frame.bytes() - slot.bytes;
                    slot.bytes = frame.bytes(); slot.width = frame.width; slot.height = frame.height;
                } else gl.glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, frame.width, frame.height, GL_RGBA, GL_UNSIGNED_BYTE, scratch);
                slot.key = frame.key;
                peakBytes = Math.max(peakBytes, bytes); peakTextures = Math.max(peakTextures, textureCount());
            } catch (RuntimeException | Error failure) { release(slot); throw failure; }
        }
    }
    private void release(Slot slot) {
        if (slot.texture != 0) gl.glDeleteTextures(slot.texture);
        bytes -= slot.bytes; slot.texture = slot.width = slot.height = 0; slot.bytes = 0; slot.key = null;
    }
    /** 纯值只读观测；关闭后仍保留实际残留和峰值，不发起GL调用。 */
    public Snapshot snapshot() {
        if(Thread.currentThread()!=thread)throw new IllegalStateException("视频GPU快照不在所属线程");
        int textures=0;for(Slot slot:slots)if(slot.texture!=0)textures++;
        return new Snapshot(textures,bytes,scratch==null?0:scratch.capacity(),peakTextures,peakBytes,closed,maxTextures,maxBytes,scratchBytes);
    }
    public static final class Snapshot {
        public static final Snapshot EMPTY=new Snapshot(0,0,0,0,0,true,0,0,0);
        public final int textureCount,scratchBytes,peakTextures,maxTextures,scratchLimit;
        public final long retainedBytes,peakBytes,maxBytes;
        public final boolean closed;
        private Snapshot(int textures,long bytes,int scratch,int peakTextures,long peakBytes,boolean closed,int maxTextures,long maxBytes,int scratchLimit) {
            textureCount=textures;retainedBytes=bytes;scratchBytes=scratch;this.peakTextures=peakTextures;this.peakBytes=peakBytes;this.closed=closed;
            this.maxTextures=maxTextures;this.maxBytes=maxBytes;this.scratchLimit=scratchLimit;
        }
    }
    public long retainedBytes() { check(); return bytes; }
    public long peakBytes() { check(); return peakBytes; }
    public int textureCount() { check(); int count = 0; for (Slot slot : slots) if (slot.texture != 0) count++; return count; }
    public int peakTextures() { check(); return peakTextures; }
    public int scratchBytes() { check(); return scratch == null ? 0 : scratch.capacity(); }
    public final class Frame implements AutoCloseable {
        private final Map<MediaFrameKey, MediaFrameLease> leases = new LinkedHashMap<>();
        private final Map<MediaFrameKey, Entry> entries = new LinkedHashMap<>();
        private boolean done;
        private boolean pinned(Slot slot) { for (Entry entry : entries.values()) if (entry.slot == slot) return true; return false; }
        public boolean active(MediaFrameKey key) { Entry entry = entries.get(key); return !done && entry != null && entry.lease.active(); }
        public boolean ready(MediaFrameKey key) { return !done && entries.containsKey(key); }
        public GlQuadRenderer.Texture resolve(MediaFrameKey key) {
            check(); if (done) throw new IllegalStateException("视频Frame已结束");
            Entry entry = entries.get(key); if (entry == null || !key.equals(entry.slot.key)) throw new IllegalArgumentException("失效视频纹理引用");
            return new GlQuadRenderer.Texture(GL_TEXTURE_2D, entry.slot.texture);
        }
        void submitted(MediaFrameKey key, long now) {
            Entry entry = entries.get(key);
            if (entry != null && entry.submittedNanos == 0) entry.submittedNanos = now;
        }
        /** 只转交真实成功提交的联系；宿主交换票据可跨 Prepared.close，纹理 pin 不外逃。 */
        public List<Submission> takeSubmissions() {
            check(); if (done) throw new IllegalStateException("视频Frame已结束");
            List<Submission> result = new ArrayList<>();
            for (Entry entry : entries.values()) if (entry.submittedNanos != 0 && !entry.transferred) {
                entry.transferred = true; result.add(new Submission(service, entry.lease, entry.submittedNanos));
            }
            return result;
        }
        @Override public void close() {
            if (done) return; check(); done = true;
            try {
                for (Map.Entry<MediaFrameKey, MediaFrameLease> item : leases.entrySet()) {
                    Entry entry = entries.get(item.getKey());
                    if (entry == null || !entry.transferred) item.getValue().close();
                }
            } finally { entries.clear(); leases.clear(); active = null; }
        }
    }
    public static final class Submission implements AutoCloseable {
        public final MediaFrameKey key;
        public final long submittedNanos;
        private final MediaService service;
        private final MediaFrameLease lease;
        private boolean closed;
        private Submission(MediaService service, MediaFrameLease lease, long submittedNanos) {
            this.service = service; this.lease = lease; this.key = lease.key; this.submittedNanos = submittedNanos;
        }
        public MediaPresentation presented(long sampledNanos) {
            if (closed) return null;
            try { return lease.active() ? service.presented(key, sampledNanos) : null; }
            finally { close(); }
        }
        @Override public void close() { if (!closed) { closed = true; lease.close(); } }
    }
    @Override public void close() {
        if (closed) return; check();
        if (active != null) throw new IllegalStateException("先结束视频Frame");
        Throwable failure = null;
        for (Slot slot : slots) try { release(slot); } catch (Throwable next) { if (failure == null) failure = next; else if (failure != next) failure.addSuppressed(next); }
        // 失败的delete仍是所属实际残留，不能清表后向资源观测报告假零。
        slots.removeIf(slot -> slot.texture == 0); scratch = null; closed = true;
        if (failure instanceof Error) throw (Error) failure;
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
    }
}
