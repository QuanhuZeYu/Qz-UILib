package club.heiqi.uilib.host.client.font;

import club.heiqi.uilib.host.client.font.context.FontPlanSampling;
import club.heiqi.uilib.host.client.font.context.FontRenderContext;
import club.heiqi.uilib.host.client.font.context.RasterPolicy;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.text.RasterSampling;
import java.util.IdentityHashMap;
import java.util.Objects;

/** MC渲染规格3.2的原票据范围内复用纯CPU采样事实；不拥有context、GL或已采样布局。
 * 原command身份保持逻辑墨迹不变，消费、票据退役和帧结束均移除事实。 */
final class WorldFontSampling {
    private final RasterPolicy policy;
    private final int limit;
    private final IdentityHashMap<PaintCommand, Entry> entries = new IdentityHashMap<>();

    WorldFontSampling(RasterPolicy policy, int limit) {
        this.policy = Objects.requireNonNull(policy, "policy");
        if (limit < 1 || limit > 4096) throw new IllegalArgumentException("采样事实容量须位于1..4096");
        this.limit = limit;
    }

    static final class Source {
        private final float[] mvp;
        private final int[] viewport;
        private final long revision;

        private Source(FontRenderContext context) {
            mvp = context.projection.mvp();
            viewport = context.projection.viewport();
            revision = context.revision;
        }

        static Source capture(FontRenderContext context) {
            return new Source(Objects.requireNonNull(context, "context"));
        }

        private boolean same(Source other) {
            if (this == other) return true;
            if (revision != other.revision) return false;
            for (int i = 0; i < mvp.length; i++)
                if (Float.floatToRawIntBits(mvp[i]) != Float.floatToRawIntBits(other.mvp[i]))
                    return false;
            for (int i = 0; i < viewport.length; i++)
                if (viewport[i] != other.viewport[i]) return false;
            return true;
        }
    }

    private static final class Entry {
        final Source source;
        final RasterSampling sampling;

        Entry(Source source, RasterSampling sampling) {
            this.source = source;
            this.sampling = sampling;
        }
    }

    RasterSampling sample(PaintCommand command, FontRenderContext context, Source source,
            boolean remember) {
        Objects.requireNonNull(source, "source");
        if (!remember) {
            // 先消费再比较；不同投影必须正常重算，旧事实不能留给另一票据误用。
            Entry entry = entries.remove(command);
            if (entry != null && entry.source.same(source)) return entry.sampling;
        }
        RasterSampling result = FontPlanSampling.sampling(command, context, policy);
        if (remember && (entries.size() < limit || entries.containsKey(command)))
            entries.put(command, new Entry(source, result));
        return result;
    }

    void forget(PaintPlan plan) {
        for (PaintCommand command : plan.commands()) entries.remove(command);
    }

    void clear() {
        entries.clear();
    }
}
