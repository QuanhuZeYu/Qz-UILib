package club.heiqi.uilib.ui.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** 旧 Properties 的单次读取边界。保持旧 boolean/string/count 索引列表语义，无写入口。 */
final class LegacyProperties {
    private LegacyProperties() {}
    static Map<ConfigKey<?>, Object> read(ConfigSchema schema, Properties input) {
        Set<String> consumed = new HashSet<>();
        Map<ConfigKey<?>, Object> values = new LinkedHashMap<>();
        for (ConfigKey<?> key : schema.keys()) values.put(key, readKey(key, input, consumed));
        for (String property : input.stringPropertyNames())
            if (!consumed.contains(property)) throw new ConfigException(property, "未知旧 Properties 配置键");
        return values;
    }
    private static <T> T readKey(ConfigKey<T> key, Properties input, Set<String> consumed) {
        String name = key.name();
        ConfigCodec<T> codec = key.codec();
        Object value;
        if (codec == ConfigCodecs.STRING_LIST) {
            String countText = input.getProperty(name + ".count");
            List<String> properties = new ArrayList<>();
            for (String property : input.stringPropertyNames())
                if (property.equals(name) || property.startsWith(name + ".")) properties.add(property);
            if (countText == null) {
                if (!properties.isEmpty()) throw new ConfigException(name, "列表缺少 count: " + properties.get(0));
                return key.checked(key.defaultValue());
            }
            int count;
            try { count = Integer.parseInt(countText); }
            catch (NumberFormatException failure) { throw new ConfigException(name, "非法列表 count: " + countText, failure); }
            if (count < 0 || !Integer.toString(count).equals(countText)) throw new ConfigException(name, "非法列表 count: " + countText);
            int actual = 0;
            for (String property : properties) {
                consumed.add(property);
                if (property.equals(name + ".count")) continue;
                if (property.equals(name)) throw new ConfigException(name, "非法列表项: " + property);
                String indexText = property.substring(name.length() + 1);
                int index;
                try { index = Integer.parseInt(indexText); }
                catch (NumberFormatException failure) { throw new ConfigException(name, "非法列表项: " + property, failure); }
                if (index < 0 || index >= count || !Integer.toString(index).equals(indexText)) throw new ConfigException(name, "非法列表项: " + property);
                actual++;
            }
            // 按真实条目数校验后才分配；保留旧格式对非规范索引的拒绝语义。
            if (actual != count) throw new ConfigException(name, "列表条目数不符: " + name + ".count=" + countText);
            List<String> items = new ArrayList<>();
            for (int i = 0; i < count; i++) items.add(input.getProperty(name + "." + i));
            value = items;
        } else {
            String text = input.getProperty(name);
            if (text == null) return key.checked(key.defaultValue());
            consumed.add(name);
            if (codec == ConfigCodecs.STRING) value = text;
            else if (codec == ConfigCodecs.BOOLEAN) {
                if ("true".equals(text)) value = true;
                else if ("false".equals(text)) value = false;
                else throw new ConfigException(name, "boolean 必须是 true 或 false");
            } else throw new ConfigException(name, "此声明没有旧 Properties 编码，不能迁移");
        }
        return key.read(value);
    }
}
