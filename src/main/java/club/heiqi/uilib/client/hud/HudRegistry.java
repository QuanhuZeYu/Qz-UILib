package club.heiqi.uilib.client.hud;

import club.heiqi.uilib.ui.hud.api.HudAvoidanceProvider;
import club.heiqi.uilib.ui.hud.api.HudInsets;
import club.heiqi.uilib.ui.hud.api.HudRegistration;
import club.heiqi.uilib.ui.hud.api.HudSpec;
import club.heiqi.uilib.ui.hud.api.HudWindowFactory;

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
    private final List<Registration> registrations = new ArrayList<Registration>();
    private long nextOrder;

    /** 注册 HUD 虚拟窗口，重复 id 立即失败。 */
    HudRegistration register(HudSpec spec, HudWindowFactory factory) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(factory, "factory");
        if (entries.containsKey(spec.getId())) throw new IllegalArgumentException("duplicate HUD id: " + spec.getId());
        Entry entry = new Entry(spec, factory, nextOrder++);
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

    /** 对注册表封板的不可变活动注册列表（按注册顺序）。 */
    List<Entry> frameEntries() {
        return Collections.unmodifiableList(new ArrayList<Entry>(entries.values()));
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

    /** 清空注册表并使所有已返回句柄失效；主要供服务整体关闭与测试使用。 */
    void clear() {
        entries.clear();
        avoidance.clear();
        for (Registration registration : new ArrayList<Registration>(registrations)) registration.invalidate();
        registrations.clear();
    }

    private static void requireId(String id) {
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("id must not be blank");
    }

    private HudRegistration registration(Runnable closer) {
        Registration registration = new Registration(closer);
        registrations.add(registration);
        return registration;
    }

    private final class Registration implements HudRegistration {
        private final Runnable closer;
        private final AtomicBoolean closed = new AtomicBoolean();
        private Registration(Runnable closer) { this.closer = closer; }
        @Override public void close() {
            if (closed.compareAndSet(false, true)) {
                closer.run();
                registrations.remove(this);
            }
        }
        @Override public boolean isClosed() { return closed.get(); }
        private void invalidate() { closed.set(true); }
    }

    /** 单个注册项：规格 + 内容工厂 + 注册顺序。 */
    static final class Entry {
        final HudSpec spec;
        final HudWindowFactory factory;
        final long registrationOrder;
        Entry(HudSpec spec, HudWindowFactory factory, long registrationOrder) {
            this.spec = spec; this.factory = factory; this.registrationOrder = registrationOrder;
        }
    }
}
