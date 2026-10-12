package club.heiqi.uilib.ui.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 固定声明集合；重复名称和父子路径占用在创建时定位。 */
public final class ConfigSchema {
    private final List<ConfigKey<?>> keys;
    private ConfigSchema(ConfigKey<?>[] declarations) {
        List<ConfigKey<?>> copy = new ArrayList<>();
        for (ConfigKey<?> key : declarations) {
            Objects.requireNonNull(key, "key");
            for (ConfigKey<?> previous : copy)
                if (previous.name().equals(key.name()) || previous.name().startsWith(key.name() + ".") || key.name().startsWith(previous.name() + "."))
                    throw new IllegalArgumentException("配置路径冲突: " + previous.name() + " / " + key.name());
            copy.add(key);
        }
        keys = Collections.unmodifiableList(copy);
    }
    public static ConfigSchema of(ConfigKey<?>... keys) { return new ConfigSchema(Objects.requireNonNull(keys, "keys")); }
    public List<ConfigKey<?>> keys() { return keys; }

    void checkTree(Map<String, Object> tree) { checkBranch(tree, ""); }
    private void checkBranch(Map<?, ?> tree, String parent) {
        for (Map.Entry<?, ?> entry : tree.entrySet()) {
            String segment = String.valueOf(entry.getKey());
            String path = ConfigCodecs.child(parent, segment);
            // 点分只属于声明路径；YAML 骨架每层必须提供真正的单段映射键。
            // 已归属于对象/map codec 的子树由其负责，因此 map 内 string 键不受此限制。
            if (segment.indexOf('.') >= 0) throw new ConfigException(path, "声明路径必须使用嵌套 YAML 映射，不能使用点分字面键");
            boolean exact = false, branch = false;
            for (ConfigKey<?> key : keys) {
                exact |= key.name().equals(path);
                branch |= key.name().startsWith(path + ".");
            }
            if (exact) continue;
            if (!branch) throw new ConfigException(path, "未知配置字段");
            if (!(entry.getValue() instanceof Map)) throw new ConfigException(path, "需要对象映射");
            checkBranch((Map<?, ?>) entry.getValue(), path);
        }
    }
}
