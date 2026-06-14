package club.heiqi.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * JSON 配置写入器实现
 */
class JsonConfigWriter implements ConfigWriter {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public void write(ConfigNode node, ConfigSource target) throws ConfigException {
        JsonElement element = convertToJsonElement(node);
        String json = gson.toJson(element);

        // 写入文件

        // 写入文件
        if (target instanceof FileConfigSource) {
            File file = ((FileConfigSource) target).getFile();
            try {
                FileOutputStream fos = new FileOutputStream(file);
                try {
                    OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
                    writer.write(json);
                    writer.flush();
                    writer.close();
                } finally {
                    fos.close();
                }
            } catch (IOException e) {
                throw new ConfigException("Failed to write JSON to file: " + file.getAbsolutePath(), e);
            }
        } else {
            throw new ConfigException("Unsupported config source type for writing: " + target.getClass().getName());
        }
    }

    @Override
    public ConfigFormat getFormat() {
        return ConfigFormat.JSON;
    }

    /**
     * 将配置节点序列化为 JSON 文本。
     *
     * <p>仅复用本写入器内部的 Gson + 缩进打印策略，输出与 {@link #write} 一致。
     * 节点为 null 或 {@link ConfigNode#isNull()} 时返回 {@code "null"}。</p>
     *
     * @param node 配置节点
     * @return JSON 文本
     */
    String writeToString(ConfigNode node) {
        if (node == null || node.isNull()) {
            return "null";
        }
        return gson.toJson(convertToJsonElement(node));
    }

    /**
     * 将 ConfigNode 转换为 JsonElement
     * 
     * @param node 配置节点
     * @return JSON 元素
     */
    private JsonElement convertToJsonElement(ConfigNode node) {
        if (node.isNull()) {
            return com.google.gson.JsonNull.INSTANCE;
        }

        switch (node.getType()) {
            case STRING:
                return new JsonPrimitive(node.asString());

            case NUMBER:
                try {
                    // 尝试作为整数
                    long longValue = node.asLong();
                    if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                        return new JsonPrimitive((int) longValue);
                    }
                    return new JsonPrimitive(longValue);
                } catch (ConfigException e) {
                    // 作为浮点数
                    return new JsonPrimitive(node.asDouble(0.0));
                }

            case BOOLEAN:
                return new JsonPrimitive(node.asBoolean(false));

            case LIST:
                JsonArray array = new JsonArray();
                List<ConfigNode> list = node.asList();
                if (list != null) {
                    for (ConfigNode item : list) {
                        array.add(convertToJsonElement(item));
                    }
                }
                return array;

            case MAP:
                JsonObject object = new JsonObject();
                Map<String, ConfigNode> map = node.asMap();
                if (map != null) {
                    for (Map.Entry<String, ConfigNode> entry : map.entrySet()) {
                        object.add(entry.getKey(), convertToJsonElement(entry.getValue()));
                    }
                }
                return object;

            default:
                return com.google.gson.JsonNull.INSTANCE;
        }
    }
}
