package club.heiqi.uilib.ui.scene.localization;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Consumer;

/** UILib 独立语言流：完整加载后才发布，选择与同语言重载都形成新资料事实。
 * 格式 locale 显式固定；不补其他语言、不监听平台，也不在失败后重试或回滚。 */
public final class LocalizationFlow implements LocalizationSource, AutoCloseable {
    private final Locale formatLocale;
    private final LocalizationResource[] resources;
    private final MutableLocalizationSource source;

    public LocalizationFlow(String language, Locale formatLocale, LocalizationResource... resources) throws IOException {
        this.formatLocale = Objects.requireNonNull(formatLocale, "formatLocale");
        this.resources = Objects.requireNonNull(resources, "resources").clone();
        for (LocalizationResource resource : this.resources) Objects.requireNonNull(resource, "resource");
        source = new MutableLocalizationSource(load(language));
    }
    @Override public synchronized LocalizationSnapshot current() { return source.current(); }
    @Override public synchronized Subscription subscribe(Consumer<LocalizationSnapshot> listener) { return source.subscribe(listener); }
    public synchronized void select(String language) throws IOException {
        source.current(); // 先拒绝关闭后的操作，不能在已关闭流上重新打开资料。
        source.publish(load(language));
    }
    public synchronized void reload() throws IOException {
        // current 是唯一语言事实；通知中的重入 select 不会被外层平行字段覆盖回旧语言。
        select(source.current().language());
    }
    private LocalizationSnapshot load(String language) throws IOException {
        LocalizationResource.requireLanguage(language);
        Map<String, String> translations = new HashMap<>();
        for (int index = 0; index < resources.length; index++) {
            LocalizationResource resource = resources[index];
            Properties properties = new Properties();
            try (Reader reader = new InputStreamReader(Objects.requireNonNull(resource.open(language), "resource input"), StandardCharsets.UTF_8)) {
                properties.load(reader);
                for (String key : properties.stringPropertyNames())
                    translations.put(LocalizationSnapshot.requireKey(key), properties.getProperty(key));
            } catch (IOException | IllegalArgumentException failure) {
                throw new IOException("语言资料加载失败: " + language + ".properties，resource[" + index + "]=" + resource, failure);
            }
        }
        return new LocalizationSnapshot(language, translations, formatLocale);
    }
    @Override public synchronized void close() { source.close(); }
}
