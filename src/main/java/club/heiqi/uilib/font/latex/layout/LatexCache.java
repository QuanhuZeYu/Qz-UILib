package club.heiqi.uilib.font.latex.layout;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.latex.LatexNode;
import club.heiqi.uilib.font.latex.LatexParser;

/**
 * LaTeX 布局结果缓存（M4）：key = (源码, 字号, 运行时版本, 字体类别)。
 *
 * <p>测量侧（TextLayoutService）与渲染侧（DefaultFontRendererAdapter）共享同一实例，
 * 热点公式（HUD 每帧渲染）零重解析零重布局；字体 reload 使 runtimeVersion 变化时
 * 旧条目自然 miss（不主动清扫，LRU 上限淘汰）。</p>
 */
public final class LatexCache {

    private static final LatexCache INSTANCE = new LatexCache();
    private static final int MAX_ENTRIES = 256;

    private final Map<Key, MathBox> cache =
            Collections.synchronizedMap(new LinkedHashMap<Key, MathBox>(64, 0.75F, true) {
                private static final long serialVersionUID = 1L;

                @Override
                protected boolean removeEldestEntry(Map.Entry<Key, MathBox> eldest) {
                    return size() > MAX_ENTRIES;
                }
            });

    private LatexCache() {
    }

    public static LatexCache getInstance() {
        return INSTANCE;
    }

    /**
     * 命中返回缓存盒；未命中解析 + 布局并缓存（布局阶段双重检查避免并发重复计算）。
     *
     * @param latexSource   TeX 源码
     * @param baseSizePx    公式正文字号（px）
     * @param runtimeVersion 字体运行时版本（reload 递增）
     * @param fontType      字体类别（宽度口径参与 key）
     * @param layoutService 布局引擎
     * @param metrics       度量注入（与 key 匹配的口径）
     * @return 布局盒
     */
    public MathBox getOrLayout(String latexSource, int baseSizePx, int runtimeVersion, FontType fontType,
            MathLayoutService layoutService, MathMetrics metrics) {
        Key key = new Key(latexSource, baseSizePx, runtimeVersion, fontType.ordinal());
        MathBox box = cache.get(key);
        if (box != null) {
            return box;
        }
        synchronized (this) {
            box = cache.get(key);
            if (box != null) {
                return box;
            }
            List<LatexNode> nodes = LatexParser.parse(latexSource);
            box = layoutService.layout(nodes, baseSizePx, metrics);
            cache.put(key, box);
            return box;
        }
    }

    /** 缓存条目数（诊断）。 */
    public int size() {
        return cache.size();
    }

    private static final class Key {
        private final String source;
        private final int size;
        private final int version;
        private final int fontTypeOrdinal;

        Key(String source, int size, int version, int fontTypeOrdinal) {
            this.source = source;
            this.size = size;
            this.version = version;
            this.fontTypeOrdinal = fontTypeOrdinal;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key)) {
                return false;
            }
            Key key = (Key) other;
            return size == key.size && version == key.version && fontTypeOrdinal == key.fontTypeOrdinal
                    && source.equals(key.source);
        }

        @Override
        public int hashCode() {
            int result = source.hashCode();
            result = 31 * result + size;
            result = 31 * result + version;
            result = 31 * result + fontTypeOrdinal;
            return result;
        }
    }
}
