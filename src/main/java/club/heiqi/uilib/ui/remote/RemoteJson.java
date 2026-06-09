package club.heiqi.uilib.ui.remote;

import java.lang.reflect.Type;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * 远程页面协议 JSON 工具。
 */
final class RemoteJson {

    private static final Gson GSON = new Gson();

    private RemoteJson() {}

    /**
     * 将对象编码为 JSON。
     *
     * @param value 对象
     * @return JSON 文本
     */
    static String toJson(Object value) {
        return GSON.toJson(value);
    }

    /**
     * 从 JSON 解码对象。
     *
     * @param json JSON 文本
     * @param type 类型
     * @param <T> 目标类型
     * @return 解码结果
     */
    static <T> T fromJson(String json, Class<T> type) {
        try {
            return GSON.fromJson(json == null ? "" : json, type);
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("远程页面协议 JSON 无效", exception);
        }
    }

    /**
     * 从 JSON 解码泛型对象。
     *
     * @param json JSON 文本
     * @param type 类型
     * @param <T> 目标类型
     * @return 解码结果
     */
    static <T> T fromJson(String json, Type type) {
        try {
            return GSON.fromJson(json == null ? "" : json, type);
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("远程页面协议 JSON 无效", exception);
        }
    }

    /**
     * 将 JSON 解析为对象节点。
     *
     * @param json JSON 文本
     * @return 对象节点
     */
    static JsonObject parseObject(String json) {
        try {
            JsonElement element = JsonParser.parseString(json == null ? "" : json);
            if (element == null || !element.isJsonObject()) {
                throw new IllegalArgumentException("远程页面协议 JSON 必须是对象");
            }
            return element.getAsJsonObject();
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("远程页面协议 JSON 无效", exception);
        }
    }
}
