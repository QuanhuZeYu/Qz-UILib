package club.heiqi.uilib.host.client.font;

import club.heiqi.uilib.host.client.font.context.FontPlanSampling;
import club.heiqi.uilib.host.client.font.context.FontRenderContext;
import club.heiqi.uilib.host.client.font.context.RasterPolicy;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.text.RasterSampling;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import java.util.LinkedHashMap;

/** 按 logical 布局身份、位置和投影数值缓存有界采样结果，GPU 资源归回放范围。
 * 当前 tick 的混淆布局由 text.sample 生成；字体换代及 owner 关闭清空旧布局引用。 */
final class WorldFontSamplingMemo {
    // 容纳稳定文字工作集，保持整轮顺序扫描的缓存复用。
    private static final int LIMIT = 256;
    private final LinkedHashMap<Key, RasterSampling> values = new LinkedHashMap<>(LIMIT, .75f, true);
    private long revision;
    private final RasterPolicy policy;

    WorldFontSamplingMemo(RasterPolicy policy) {
        this.policy = policy;
    }

    RasterSampling sample(PaintCommand command, FontRenderContext context,
            WorldFontSampling.Source source) {
        if (command.kind != PaintCommand.Kind.TEXT || command.text == null)
            throw new IllegalArgumentException("采样需要文本计划");
        // revision 参与 key；消费时换代清理旧项，无后续消费时由资源通知退役。
        if (revision != source.revision) {
            values.clear();
            revision = source.revision;
        }
        Key key = new Key(command, source);
        RasterSampling result = values.get(key);
        if (result != null) {
            return result;
        }
        result = FontPlanSampling.sampling(command, context, policy);
        values.put(key, result);
        if (values.size() > LIMIT) {
            values.remove(values.keySet().iterator().next());
        }
        return result;
    }

    void clear() {
        values.clear();
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
