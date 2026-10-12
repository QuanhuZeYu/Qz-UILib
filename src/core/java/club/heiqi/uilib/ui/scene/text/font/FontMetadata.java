package club.heiqi.uilib.ui.scene.text.font;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import club.heiqi.uilib.ui.scene.text.FontStyle;

/** 只解析SFNT事实，不依赖AWT、宿主或文件名。范围错误交给字体资源入口诊断。 */
public final class FontMetadata {
    public final String family, subfamily;
    public final FontStyle style;
    public final Map<String, Axis> axes;

    public static final class Axis {
        public final float min, def, max;

        Axis(float min, float def, float max) {
            this.min = min;
            this.def = def;
            this.max = max;
        }

        public float clamp(float value) {
            return Math.max(min, Math.min(max, value));
        }
    }

    private FontMetadata(String family, String subfamily, FontStyle style, Map<String, Axis> axes) {
        this.family = family;
        this.subfamily = subfamily;
        this.style = style;
        this.axes = Collections.unmodifiableMap(axes);
    }

    public static int faceCount(ByteBuffer data) {
        ByteBuffer b = data.duplicate().order(ByteOrder.BIG_ENDIAN);
        return b.getInt(0) == 0x74746366 ? b.getInt(8) : 1;
    }

    public static FontMetadata inspect(ByteBuffer data, long index) {
        ByteBuffer b = data.duplicate().order(ByteOrder.BIG_ENDIAN);
        int count = faceCount(b);
        if (index < 0 || index >= count) throw new IllegalArgumentException("字体face索引越界");
        int base = b.getInt(0) == 0x74746366 ? b.getInt(12 + (int) index * 4) : 0;
        int signature = b.getInt(base);
        if (signature != 0x00010000
                && signature != 0x4f54544f
                && signature != 0x74727565
                && signature != 0x74797031)
            throw new IllegalArgumentException("不支持的SFNT签名: " + Integer.toHexString(signature));
        Map<String, Integer> tables = new LinkedHashMap<String, Integer>();
        for (int i = 0, n = u16(b, base + 4); i < n; i++) {
            int p = base + 12 + i * 16;
            tables.put(tag(b, p), b.getInt(p + 8));
        }
        Map<Integer, String> names = names(b, tables.get("name"));
        String family = names.containsKey(16) ? names.get(16) : names.get(1);
        if (family == null) throw new IllegalArgumentException("字体缺少family元数据");
        String sub = names.containsKey(17) ? names.get(17) : names.get(2);
        int weight = 400;
        FontStyle.Posture posture = FontStyle.Posture.NORMAL;
        Integer os = tables.get("OS/2"), head = tables.get("head");
        if (os != null) {
            weight = u16(b, os + 4);
            int flags = u16(b, os + 62);
            if ((flags & 512) != 0) posture = FontStyle.Posture.OBLIQUE;
            else if ((flags & 1) != 0) posture = FontStyle.Posture.ITALIC;
        } else if (head != null) {
            int flags = u16(b, head + 44);
            weight = (flags & 1) != 0 ? 700 : 400;
            if ((flags & 2) != 0) posture = FontStyle.Posture.ITALIC;
        }
        Map<String, Axis> axes = new LinkedHashMap<String, Axis>();
        Integer fvar = tables.get("fvar");
        if (fvar != null) {
            int offset = fvar + u16(b, fvar + 4), n = u16(b, fvar + 8), size = u16(b, fvar + 10);
            for (int i = 0; i < n; i++) {
                int p = offset + i * size;
                axes.put(tag(b, p), new Axis(fixed(b, p + 4), fixed(b, p + 8), fixed(b, p + 12)));
            }
        }
        return new FontMetadata(
                family, sub, new FontStyle(Math.max(1, Math.min(1000, weight)), posture), axes);
    }

    private static Map<Integer, String> names(ByteBuffer b, Integer table) {
        Map<Integer, String> names = new LinkedHashMap<Integer, String>();
        Map<Integer, Integer> priorities = new LinkedHashMap<Integer, Integer>();
        if (table == null) return names;
        int n = u16(b, table + 2), storage = table + u16(b, table + 4);
        for (int i = 0; i < n; i++) {
            int p = table + 6 + i * 12,
                    platform = u16(b, p),
                    language = u16(b, p + 4),
                    id = u16(b, p + 6);
            if (id != 1 && id != 2 && id != 16 && id != 17) continue;
            // 选择同一种稳定语言，避免本机显示语言变化把同family拆开；typographic names优先。
            int priority =
                    (platform == 3 ? 20 : platform == 0 ? 10 : 0) + (language == 0x409 ? 2 : 0);
            if (!priorities.containsKey(id) || priority > priorities.get(id)) {
                byte[] bytes = new byte[u16(b, p + 8)];
                ByteBuffer copy = b.duplicate();
                copy.position(storage + u16(b, p + 10));
                copy.get(bytes);
                names.put(
                        id,
                        new String(
                                bytes,
                                platform == 0 || platform == 3
                                        ? StandardCharsets.UTF_16BE
                                        : StandardCharsets.ISO_8859_1));
                priorities.put(id, priority);
            }
        }
        return names;
    }

    private static int u16(ByteBuffer b, int p) {
        return b.getShort(p) & 65535;
    }

    private static float fixed(ByteBuffer b, int p) {
        return b.getInt(p) / 65536f;
    }

    private static String tag(ByteBuffer b, int p) {
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) bytes[i] = b.get(p + i);
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    @Override
    public String toString() {
        return family + " / " + subfamily + " / " + style + " axes=" + axes.keySet();
    }
}
