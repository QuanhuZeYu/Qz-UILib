package club.heiqi.uilib.ui.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;

/** 应用持有的配置权威值；构造和全部操作均在所属 ReactiveDomain 内执行。
 * 无自持订阅或线程；订阅释放交给使用者的 Owner。加载先全量验证，再发布整份快照。
 * I/O 与非法配置沿调用通道暴露；已经发布之后的观察者异常遵循状态域原有失败语义。 */
public final class ConfigSession {
    private final ReactiveDomain domain;
    private final ConfigSchema schema;
    private final Map<ConfigKey<?>, Entry<?>> entries = new LinkedHashMap<>();
    private Map<ConfigKey<?>, Object> values;

    private final class Entry<T> implements ReadableSignal<T> {
        final ConfigKey<T> key;
        final Signal<T> notification;

        Entry(ConfigKey<T> key) {
            this.key = key;
            notification = domain.signal(key.defaultValue());
        }

        public T get() {
            // Signal 只负责追踪及键级通知；完整批次的值先进入 values。
            notification.get();
            return value(key);
        }

        void publish() { notification.set(value(key)); }
    }

    public ConfigSession(ReactiveDomain domain, ConfigSchema schema) {
        this.domain = Objects.requireNonNull(domain, "domain");
        domain.checkAccess();
        this.schema = Objects.requireNonNull(schema, "schema");
        Map<ConfigKey<?>, Object> initial = new LinkedHashMap<>();
        for (ConfigKey<?> key : schema.keys()) {
            initial.put(key, key.defaultValue());
            addEntry(key);
        }
        values = Collections.unmodifiableMap(initial);
    }

    private <T> void addEntry(ConfigKey<T> key) { entries.put(key, new Entry<>(key)); }

    @SuppressWarnings("unchecked")
    private <T> T value(ConfigKey<T> key) { return (T) values.get(key); }

    @SuppressWarnings("unchecked")
    private <T> Entry<T> entry(ConfigKey<T> key) {
        domain.checkAccess();
        Objects.requireNonNull(key, "key");
        Entry<?> entry = entries.get(key);
        if (entry == null) throw new IllegalArgumentException("未声明配置键: " + key.name());
        return (Entry<T>) entry;
    }

    public <T> T get(ConfigKey<T> key) { return entry(key).get(); }
    public <T> ReadableSignal<T> signal(ConfigKey<T> key) { return entry(key); }

    public <T> void set(ConfigKey<T> key, T value) {
        domain.checkWrite();
        entry(key);
        T next = domain.readOnly(() -> key.checked(value));
        if (Objects.equals(values.get(key), next)) return;
        Map<ConfigKey<?>, Object> changed = new LinkedHashMap<>(values);
        changed.put(key, next);
        publish(changed);
    }

    public void load(Path path) throws IOException {
        domain.checkWrite();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            load(reader);
        }
    }

    /** Reader 的关闭由调用者负责；缺失声明重置为声明默认值。 */
    public void load(Reader reader) throws IOException {
        domain.checkWrite();
        Properties properties = new Properties();
        properties.load(Objects.requireNonNull(reader, "reader"));
        Map<ConfigKey<?>, Object> decoded = domain.readOnly(() -> {
            schema.checkProperties(properties);
            Map<ConfigKey<?>, Object> next = new LinkedHashMap<>();
            for (ConfigKey<?> key : schema.keys()) next.put(key, key.decode(properties));
            return next;
        });
        publish(decoded);
    }

    private void publish(Map<ConfigKey<?>, Object> next) {
        if (values.equals(next)) return;
        values = Collections.unmodifiableMap(new LinkedHashMap<>(next));
        // 域先完成所有键的依赖失效，再同步通知观察者；直接值及派生读取属于同一批次。
        domain.batch(() -> { for (Entry<?> entry : entries.values()) entry.publish(); });
    }

    public void save(Path path) throws IOException {
        domain.checkAccess();
        // 先编码，避免自定义 codec 的非法输出在打开文件后才被发现。
        Properties properties = encode();
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            properties.store(writer, "UILib");
        }
    }

    /** Writer 保持打开；Properties.store 刷新内容，不隐含安装持久化监听。 */
    public void save(Writer writer) throws IOException {
        domain.checkAccess();
        encode().store(Objects.requireNonNull(writer, "writer"), "UILib");
    }

    private Properties encode() {
        return domain.readOnly(() -> {
            Properties properties = new Properties();
            for (ConfigKey<?> key : schema.keys()) encodeKey(properties, key);
            schema.checkProperties(properties);
            return properties;
        });
    }

    private <T> void encodeKey(Properties properties, ConfigKey<T> key) {
        try {
            key.codec().encode(properties, key.name(), value(key));
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("配置键 " + key.name() + ": " + failure.getMessage(), failure);
        }
    }
}
