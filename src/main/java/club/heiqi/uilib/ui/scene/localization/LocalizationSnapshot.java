package club.heiqi.uilib.ui.scene.localization;

import java.util.Collections;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** 一次已完成加载的不可变语言资料；表和显式格式 locale 共同捕获。
 * 格式合同采用 Java8 Formatter，数值模板原样保留，求值不读取全局环境。 */
public final class LocalizationSnapshot {
    private final String language;
    private final Map<String, String> translations;
    private final Locale formatLocale;

    public LocalizationSnapshot(String language, Map<String, String> translations, Locale formatLocale) {
        this.language = requireKey(language);
        this.formatLocale = Objects.requireNonNull(formatLocale, "formatLocale");
        Map<String, String> copy = new HashMap<>(Objects.requireNonNull(translations, "translations"));
        for (Map.Entry<String, String> entry : copy.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "translation key");
            Objects.requireNonNull(entry.getValue(), "translation template");
        }
        this.translations = Collections.unmodifiableMap(copy);
    }
    public String language() { return language; }
    public Map<String, String> translations() { return translations; }
    public Locale formatLocale() { return formatLocale; }
    public String format(String key, Object... arguments) {
        requireKey(key);
        Objects.requireNonNull(arguments, "arguments");
        String template = translations.get(key);
        if (template == null) return key;
        try { return String.format(formatLocale, template, arguments); }
        catch (IllegalFormatException invalid) { return "Format error: " + template; }
    }
    static String requireKey(String key) {
        if (key == null || key.isEmpty()) throw new IllegalArgumentException("语言标识或翻译键不能为空");
        return key;
    }
}
