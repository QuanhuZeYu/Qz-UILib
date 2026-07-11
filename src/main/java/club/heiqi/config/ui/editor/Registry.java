package club.heiqi.config.ui.editor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 每个配置 screen 独立持有的 value editor registry。 */
public final class Registry {
    private final Map<String, ValueEditorProvider> providers = new LinkedHashMap<String, ValueEditorProvider>();
    private boolean frozen;

    /** 注册 provider；空 id、重复 id 或冻结后注册均立即失败。 */
    public void register(ValueEditorProvider provider) {
        if (frozen) throw new IllegalStateException("value editor registry is frozen");
        if (provider == null) throw new IllegalArgumentException("provider must not be null");
        String id = provider.id();
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("provider id must not be empty");
        if (providers.containsKey(id)) throw new IllegalArgumentException("duplicate value editor id: " + id);
        if (provider.codec() == null || provider.visualAdapter() == null) {
            throw new IllegalArgumentException("provider codec and visualAdapter must not be null: " + id);
        }
        providers.put(id, provider);
    }

    /** 冻结 registry；可重复调用。 */
    public void freeze() { frozen = true; }
    /** @return 是否已冻结 */
    public boolean isFrozen() { return frozen; }
    /** 查询 provider；缺失返回 null。 */
    public ValueEditorProvider find(String id) { return providers.get(id); }
    /** @return 注册项保序只读快照 */
    public Map<String, ValueEditorProvider> providers() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, ValueEditorProvider>(providers));
    }
}
