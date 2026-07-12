package club.heiqi.uilib.client.hud;

import club.heiqi.uilib.ui.hud.api.HudAvoidanceProvider;
import club.heiqi.uilib.ui.hud.api.HudInsets;
import club.heiqi.uilib.ui.hud.api.HudRegistration;
import club.heiqi.uilib.ui.hud.api.HudSnapshot;
import club.heiqi.uilib.ui.hud.api.HudSnapshotProvider;
import club.heiqi.uilib.ui.hud.api.HudSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** HUD 注册表；所有结构变更由客户端主线程串行执行，遍历使用封板快照。 */
final class HudRegistry {
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<String, Entry>();
    private final LinkedHashMap<String, HudAvoidanceProvider> avoidance = new LinkedHashMap<String, HudAvoidanceProvider>();
    private long nextOrder;

    /** 注册 HUD，重复 id 立即失败。 */
    HudRegistration register(HudSpec spec, HudSnapshotProvider provider) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(provider, "provider");
        if (entries.containsKey(spec.getId())) throw new IllegalArgumentException("duplicate HUD id: " + spec.getId());
        Entry entry = new Entry(spec, provider, nextOrder++);
        entries.put(spec.getId(), entry);
        return registration(() -> entries.remove(spec.getId(), entry));
    }

    /** 注册外部占位 provider。 */
    HudRegistration registerAvoidance(String id, HudAvoidanceProvider provider) {
        requireId(id);
        Objects.requireNonNull(provider, "provider");
        if (avoidance.containsKey(id)) throw new IllegalArgumentException("duplicate avoidance id: " + id);
        avoidance.put(id, provider);
        return registration(() -> avoidance.remove(id, provider));
    }

    /** 对注册表封板后读取 provider，单个 provider 异常不影响其它 HUD。 */
    List<FrameEntry> snapshot(Consumer<RuntimeException> errorSink) {
        ArrayList<Entry> copy = new ArrayList<Entry>(entries.values());
        ArrayList<FrameEntry> result = new ArrayList<FrameEntry>();
        for (Entry entry : copy) {
            try {
                HudSnapshot value = entry.provider.snapshot();
                if (value != null && !value.isEmpty()) result.add(new FrameEntry(entry.spec, value, entry.registrationOrder));
            } catch (RuntimeException exception) {
                if (errorSink != null) errorSink.accept(exception);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** 汇总封板后的已知占位；异常 provider 当帧忽略。 */
    HudInsets avoidanceInsets(Consumer<RuntimeException> errorSink) {
        HudInsets result = HudInsets.NONE;
        for (HudAvoidanceProvider provider : new ArrayList<HudAvoidanceProvider>(avoidance.values())) {
            try {
                HudInsets value = provider.getInsets();
                if (value != null) result = result.plus(value);
            } catch (RuntimeException exception) {
                if (errorSink != null) errorSink.accept(exception);
            }
        }
        return result;
    }

    /** 清空世界生命周期资源；现有调用方需在下个世界重新注册。 */
    void clear() { entries.clear(); avoidance.clear(); }

    private static void requireId(String id) {
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("id must not be blank");
    }

    private static HudRegistration registration(Runnable closer) {
        AtomicBoolean closed = new AtomicBoolean();
        return new HudRegistration() {
            @Override public void close() { if (closed.compareAndSet(false, true)) closer.run(); }
            @Override public boolean isClosed() { return closed.get(); }
        };
    }

    private static final class Entry {
        private final HudSpec spec;
        private final HudSnapshotProvider provider;
        private final long registrationOrder;
        private Entry(HudSpec spec, HudSnapshotProvider provider, long registrationOrder) {
            this.spec = spec; this.provider = provider; this.registrationOrder = registrationOrder;
        }
    }

    /** 单帧不可变注册内容。 */
    static final class FrameEntry {
        final HudSpec spec;
        final HudSnapshot snapshot;
        final long registrationOrder;
        FrameEntry(HudSpec spec, HudSnapshot snapshot, long registrationOrder) {
            this.spec = spec; this.snapshot = snapshot; this.registrationOrder = registrationOrder;
        }
    }
}
