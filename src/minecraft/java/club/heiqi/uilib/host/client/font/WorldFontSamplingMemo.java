package club.heiqi.uilib.host.client.font;

import club.heiqi.uilib.host.client.font.context.FontPlanSampling;
import club.heiqi.uilib.host.client.font.context.FontRenderContext;
import club.heiqi.uilib.host.client.font.context.RasterPolicy;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.text.RasterSampling;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import java.util.LinkedHashMap;

/** 采样函数的有界数值memo，输入按值精确失效；不参与票据、绘制或管线生命周期。
 * logical布局只提供不可变墨迹；当前tick的混淆布局仍由text.sample重新产生。
 * 不保存context、pipeline、图集、UV或GL scope，字体换代通知及owner关闭清空旧布局引用。 */
final class WorldFontSamplingMemo {
    private static final int LIMIT = 128;
    private final LinkedHashMap<Key, RasterSampling> values = new LinkedHashMap<>(LIMIT, .75f, true);
    private long revision;
    private final RasterPolicy policy;

    WorldFontSamplingMemo(RasterPolicy policy) {
        this.policy = policy;
    }

    RasterSampling sample(PaintCommand command, FontRenderContext context,
            WorldFontSampling.Source source) {
        // #81临时探针：MC_SAMPLING_MEMO及真实缓存分支，性能报告定位后统一移除。
        long probeToken = FontPerformanceProbe.begin(4); // #81临时探针：MC_SAMPLING_MEMO。
        try {
            if (command.kind != PaintCommand.Kind.TEXT || command.text == null)
                throw new IllegalArgumentException("采样需要文本计划");
            // revision也属于key；此清理避免旧代际占据有界空间，通知负责无后续消费时的退役。
            if (revision != source.revision) {
                values.clear();
                revision = source.revision;
                FontPerformanceProbe.count(3, 1); // #81临时探针：MEMO_RESET。 // #81临时探针：MEMO_RESET实际换代清理。
            }
            Key key = new Key(command, source);
            RasterSampling result = values.get(key);
            if (result != null) {
                FontPerformanceProbe.count(0, 1); // #81临时探针：MEMO_HIT。
                return result;
            }
            FontPerformanceProbe.count(1, 1); // #81临时探针：MEMO_MISS。
            result = FontPlanSampling.sampling(command, context, policy);
            values.put(key, result);
            if (values.size() > LIMIT) {
                values.remove(values.keySet().iterator().next());
                FontPerformanceProbe.count(2, 1); // #81临时探针：MEMO_EVICT实际移除。
            }
            return result;
        } finally {
            FontPerformanceProbe.observe(0, values.size()); // #81临时探针：MEMO_SIZE。 // #81临时探针：MEMO_SIZE。
            FontPerformanceProbe.end(4, probeToken); // #81临时探针：MC_SAMPLING_MEMO。
        }
    }

    void clear() {
        values.clear();
        // #81临时探针：MEMO_RESET显式清理与MEMO_SIZE，性能报告定位后统一移除。
        FontPerformanceProbe.count(3, 1); // #81临时探针：MEMO_RESET。
        FontPerformanceProbe.observe(0, values.size()); // #81临时探针：MEMO_SIZE。
    }

    private static final class Key {
        final TextLayout logical;
        final int x, y, hash;
        final WorldFontSampling.Source source;

        Key(PaintCommand command, WorldFontSampling.Source source) {
            logical = command.text;
            x = command.box.x;
            y = command.box.y;
            this.source = source;
            hash = 31 * (31 * (31 * System.identityHashCode(logical) + x) + y) + source.valueHash;
        }

        @Override
        public int hashCode() { return hash; }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Key)) return false;
            Key key = (Key) other;
            return logical == key.logical && x == key.x && y == key.y && source.same(key.source);
        }
    }
}
