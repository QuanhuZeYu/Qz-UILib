package club.heiqi.uilib.ui.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 固定声明集合；重复名称及被其他声明占用的存储命名空间在创建时定位。 */
public final class ConfigSchema {
    private final List<ConfigKey<?>> keys;

    private ConfigSchema(ConfigKey<?>[] declarations) {
        List<ConfigKey<?>> copy = new ArrayList<>();
        for (ConfigKey<?> key : declarations) {
            Objects.requireNonNull(key, "key");
            for (ConfigKey<?> previous : copy) {
                if (previous.name().equals(key.name()))
                    throw new IllegalArgumentException("重复配置键: " + key.name());
                if (previous.codec().owns(previous.name(), key.name()) || key.codec().owns(key.name(), previous.name()))
                    throw new IllegalArgumentException("配置存储命名空间冲突: " + previous.name() + " / " + key.name());
            }
            copy.add(key);
        }
        keys = Collections.unmodifiableList(copy);
    }

    public static ConfigSchema of(ConfigKey<?>... keys) {
        return new ConfigSchema(Objects.requireNonNull(keys, "keys"));
    }

    public List<ConfigKey<?>> keys() { return keys; }

    void checkProperties(java.util.Properties properties) {
        for (String property : properties.stringPropertyNames()) {
            boolean known = false;
            for (ConfigKey<?> key : keys) {
                if (key.codec().owns(key.name(), property)) { known = true; break; }
            }
            if (!known) throw new IllegalArgumentException("未知配置键: " + property);
        }
    }
}
