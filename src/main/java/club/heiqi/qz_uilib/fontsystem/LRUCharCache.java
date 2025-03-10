package club.heiqi.qz_uilib.fontsystem;

import java.util.LinkedHashMap;
import java.util.Map;

public class LRUCharCache extends LinkedHashMap<String, CharPage> {
    private static final int MAX_CAPACITY = 10000; // 最大容量

    public LRUCharCache() {
        super(16, 0.75f, true); // 初始容量, 负载因子, true表示按访问顺序排序
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<String, CharPage> eldest) {
        // 当容量超过最大值时，自动移除最久未使用的条目
        return size() > MAX_CAPACITY;
    }
}
