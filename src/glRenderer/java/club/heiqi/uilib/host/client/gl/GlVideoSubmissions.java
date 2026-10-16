package club.heiqi.uilib.host.client.gl;

import club.heiqi.uilib.ui.media.MediaFrameKey;
import club.heiqi.uilib.ui.media.MediaPresentation;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 单个宿主呈现批次的有界联系；Prepared GPU pin 与跨交换的媒体租约寿命分开。 */
public final class GlVideoSubmissions implements AutoCloseable {
    private final int limit;
    private final Map<MediaFrameKey, GlVideoResources.Submission> pending = new LinkedHashMap<>();
    public GlVideoSubmissions(int limit) {
        if (limit < 1) throw new IllegalArgumentException("无效媒体提交票据预算");
        this.limit = limit;
    }
    public void add(List<GlVideoResources.Submission> tickets) {
        Set<MediaFrameKey> keys = new HashSet<>(pending.keySet());
        for (GlVideoResources.Submission ticket : tickets) keys.add(ticket.key);
        if (keys.size() > limit) {
            for (GlVideoResources.Submission ticket : tickets) ticket.close();
            throw new IllegalStateException("媒体提交票据预算已满");
        }
        for (GlVideoResources.Submission ticket : tickets) {
            if (pending.containsKey(ticket.key)) ticket.close();
            else pending.put(ticket.key, ticket);
        }
    }
    public List<MediaPresentation> complete(long sampledNanos) {
        List<GlVideoResources.Submission> owned = new ArrayList<>(pending.values()); pending.clear();
        List<MediaPresentation> results = new ArrayList<>(); Throwable failure = null;
        for (GlVideoResources.Submission ticket : owned) try {
            if (sampledNanos < ticket.submittedNanos) throw new IllegalArgumentException("呈现时刻早于GPU提交");
            MediaPresentation value = ticket.presented(sampledNanos); if (value != null) results.add(value);
        } catch (Throwable next) { if (failure == null) failure = next; else if (failure != next) failure.addSuppressed(next); }
        finally { ticket.close(); }
        if (failure instanceof Error) throw (Error) failure;
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        return Collections.unmodifiableList(results);
    }
    public int pendingCount() { return pending.size(); }
    @Override public void close() {
        List<GlVideoResources.Submission> owned = new ArrayList<>(pending.values()); pending.clear();
        Throwable failure = null;
        for (GlVideoResources.Submission ticket : owned) try { ticket.close(); }
        catch (Throwable next) { if (failure == null) failure = next; else if (failure != next) failure.addSuppressed(next); }
        if (failure instanceof Error) throw (Error) failure;
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
    }
}
