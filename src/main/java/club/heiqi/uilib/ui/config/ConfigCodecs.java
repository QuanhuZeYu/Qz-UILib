package club.heiqi.uilib.ui.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

/** 初期配置类型；Properties 使用 Reader/Writer 时保持 Unicode 字符。 */
public final class ConfigCodecs {
    private ConfigCodecs() {}

    private abstract static class Scalar<T> implements ConfigCodec<T> {
        public boolean owns(String name, String property) { return name.equals(property); }
        public T snapshot(T value) { return value; }
    }

    public static final ConfigCodec<Boolean> BOOLEAN = new Scalar<Boolean>() {
        public Boolean decode(Properties properties, String name) {
            String value = properties.getProperty(name);
            if (value == null) return null;
            if ("true".equals(value)) return true;
            if ("false".equals(value)) return false;
            throw new IllegalArgumentException("boolean 必须是 true 或 false");
        }
        public void encode(Properties properties, String name, Boolean value) {
            properties.setProperty(name, value.toString());
        }
    };

    public static final ConfigCodec<String> STRING = new Scalar<String>() {
        public String decode(Properties properties, String name) { return properties.getProperty(name); }
        public void encode(Properties properties, String name, String value) { properties.setProperty(name, value); }
    };

    public static final ConfigCodec<List<String>> STRING_LIST = new ConfigCodec<List<String>>() {
        public boolean owns(String name, String property) {
            return name.equals(property) || property.startsWith(name + ".");
        }
        public List<String> snapshot(List<String> value) {
            List<String> copy = new ArrayList<>(value.size());
            for (String item : value) copy.add(Objects.requireNonNull(item, "列表项"));
            return Collections.unmodifiableList(copy);
        }
        public List<String> decode(Properties properties, String name) {
            String countText = properties.getProperty(name + ".count");
            if (countText == null) {
                for (String property : properties.stringPropertyNames())
                    if (owns(name, property)) throw new IllegalArgumentException("列表缺少 count: " + property);
                return null;
            }
            int count;
            try { count = Integer.parseInt(countText); }
            catch (NumberFormatException failure) { throw new IllegalArgumentException("非法列表 count: " + countText, failure); }
            if (count < 0 || !Integer.toString(count).equals(countText))
                throw new IllegalArgumentException("非法列表 count: " + countText);
            // 先校验真实条目，不能让一个恶意 count 在验证前引发巨量分配。
            int actual = 0;
            for (String property : properties.stringPropertyNames()) {
                if (!owns(name, property) || property.equals(name + ".count")) continue;
                if (property.equals(name)) throw new IllegalArgumentException("非法列表项: " + property);
                String indexText = property.substring(name.length() + 1);
                int index;
                try { index = Integer.parseInt(indexText); }
                catch (NumberFormatException failure) { throw new IllegalArgumentException("非法列表项: " + property, failure); }
                if (index < 0 || index >= count || !Integer.toString(index).equals(indexText))
                    throw new IllegalArgumentException("非法列表项: " + property);
                actual++;
            }
            if (actual != count) throw new IllegalArgumentException("列表条目数不符: " + name + ".count=" + countText);
            List<String> values = new ArrayList<>(count);
            for (int i = 0; i < count; i++) values.add(properties.getProperty(name + "." + i));
            return snapshot(values);
        }
        public void encode(Properties properties, String name, List<String> value) {
            properties.setProperty(name + ".count", Integer.toString(value.size()));
            for (int i = 0; i < value.size(); i++) properties.setProperty(name + "." + i, value.get(i));
        }
    };
}
