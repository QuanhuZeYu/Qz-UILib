package club.heiqi.config;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON 配置加载器实现，使用 Gson 解析 JSON 格式配置。
 */
class JsonConfigLoader implements ConfigLoader {

    private final Gson gson = new Gson();
    private final JsonParser parser = new JsonParser();

    @Override
    public ConfigNode load(ConfigSource source) throws ConfigException {
        try {
            String content = source.read();
            JsonElement root = parser.parse(content);
            return convertJsonElement(root);
        } catch (Exception e) {
            throw new ConfigException("Failed to parse JSON from " + source.getDescription(), e);
        }
    }

    @Override
    public ConfigFormat getFormat() {
        return ConfigFormat.JSON;
    }

    /**
     * 将 Gson 的 JsonElement 转换为 ConfigNode
     * 
     * @param element JSON 元素
     * @return 配置节点
     */
    private ConfigNode convertJsonElement(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return NullConfigNode.INSTANCE;
        }

        if (element.isJsonPrimitive()) {
            return convertJsonPrimitive(element.getAsJsonPrimitive());
        }

        if (element.isJsonObject()) {
            return convertJsonObject(element.getAsJsonObject());
        }

        if (element.isJsonArray()) {
            return convertJsonArray(element.getAsJsonArray());
        }

        return NullConfigNode.INSTANCE;
    }

    /**
     * 转换 JSON 原始类型
     * 
     * @param primitive JSON 原始值
     * @return 配置节点
     */
    private ConfigNode convertJsonPrimitive(JsonPrimitive primitive) {
        if (primitive.isBoolean()) {
            return new BooleanConfigNode(primitive.getAsBoolean());
        }

        if (primitive.isNumber()) {
            return new NumberConfigNode(primitive.getAsNumber());
        }

        if (primitive.isString()) {
            return new StringConfigNode(primitive.getAsString());
        }

        return NullConfigNode.INSTANCE;
    }

    /**
     * 转换 JSON 对象
     * 
     * @param object JSON 对象
     * @return 配置节点
     */
    private ConfigNode convertJsonObject(JsonObject object) {
        Map<String, ConfigNode> map = new HashMap<String, ConfigNode>();

        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            map.put(entry.getKey(), convertJsonElement(entry.getValue()));
        }

        return new MapConfigNode(map);
    }

    /**
     * 转换 JSON 数组
     * 
     * @param array JSON 数组
     * @return 配置节点
     */
    private ConfigNode convertJsonArray(JsonArray array) {
        List<ConfigNode> list = new ArrayList<ConfigNode>();

        for (JsonElement element : array) {
            list.add(convertJsonElement(element));
        }

        return new ListConfigNode(list);
    }
}
