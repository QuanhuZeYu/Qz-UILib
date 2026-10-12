package club.heiqi.uilib.ui.scene.text.math;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 固定参照的数学/文本符号事实，不依 Unicode 外观猜原子类别或字体。 font=main/ams 是参照事实类别，不是宿主字体绑定；生产字形选择仍由 UILib 字体管线负责。 */
public final class MathSymbols {
    private MathSymbols() {}

    /** 原始 mode=math/text 与 token（命令含反斜杠）查询；未知或错 mode 返回 null。 */
    public static Entry find(String mode, String token) {
        Map<String, Entry> entries = Facts.BY_MODE.get(mode);
        return entries == null ? null : entries.get(token);
    }

    /** 全部条目与别名，包括普通 Unicode token、不可见语义条目；不可变。 */
    public static List<Entry> entries() {
        return Facts.ALL;
    }

    public static List<Entry> entries(String mode) {
        List<Entry> entries = Facts.LISTS.get(mode);
        return entries == null ? Collections.<Entry>emptyList() : entries;
    }

    public static final class Entry {
        public final String mode, token, font, group, visibility;

        /** null 表示此 token 没有替换字形（例如 \nobreak）；不能丢掉 token 或改为空字符。 */
        public final String replace;

        public final MathCommandCatalog.Source source;

        private Entry(String[] row) {
            MathCommandCatalog.exactColumns(row, 9);
            mode = MathCommandCatalog.required(row[0]);
            token = MathCommandCatalog.required(row[1]);
            font = MathCommandCatalog.required(row[2]);
            group = MathCommandCatalog.required(row[3]);
            replace = row[4];
            visibility = MathCommandCatalog.required(row[5]);
            source = MathCommandCatalog.source(row, 6);
            if (!"math".equals(mode) && !"text".equals(mode))
                throw new IllegalStateException("未知数学mode: " + mode);
        }
    }

    private static final class Facts {
        static final Map<String, Map<String, Entry>> BY_MODE = read();
        static final Map<String, List<Entry>> LISTS = lists();
        static final List<Entry> ALL = all();

        private static Map<String, Map<String, Entry>> read() {
            Map<String, Map<String, Entry>> modes = new LinkedHashMap<String, Map<String, Entry>>();
            for (String[] row : MathCommandCatalog.readFacts("symbols.tsv")) {
                Entry e = new Entry(row);
                Map<String, Entry> entries = modes.get(e.mode);
                if (entries == null) {
                    entries = new LinkedHashMap<String, Entry>();
                    modes.put(e.mode, entries);
                }
                if (entries.put(e.token, e) != null)
                    throw new IllegalStateException("重复数学符号: " + e.mode + " " + e.token);
            }
            Map<String, Map<String, Entry>> result =
                    new LinkedHashMap<String, Map<String, Entry>>();
            for (Map.Entry<String, Map<String, Entry>> mode : modes.entrySet())
                result.put(mode.getKey(), Collections.unmodifiableMap(mode.getValue()));
            return Collections.unmodifiableMap(result);
        }

        private static Map<String, List<Entry>> lists() {
            Map<String, List<Entry>> result = new LinkedHashMap<String, List<Entry>>();
            for (Map.Entry<String, Map<String, Entry>> mode : BY_MODE.entrySet())
                result.put(
                        mode.getKey(),
                        Collections.unmodifiableList(
                                new ArrayList<Entry>(mode.getValue().values())));
            return Collections.unmodifiableMap(result);
        }

        private static List<Entry> all() {
            List<Entry> result = new ArrayList<Entry>();
            for (List<Entry> entries : LISTS.values()) result.addAll(entries);
            return Collections.unmodifiableList(result);
        }
    }
}
