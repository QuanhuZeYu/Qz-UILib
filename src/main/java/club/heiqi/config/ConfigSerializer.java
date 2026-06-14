package club.heiqi.config;

/**
 * 配置序列化工具，把 {@link ConfigNode} 子树序列化为 JSON 或 YAML 文本。
 *
 * <p>本类是同包内的轻量入口，复用 {@link JsonConfigWriter} / {@link YamlConfigWriter}
 * 既有的转换与缩进逻辑，不复制转换代码，也不改动写入器的 public 写文件契约。</p>
 *
 * <p>能力边界与现有写入器对齐：YAML 输出不支持多行字符串和锚点引用；
 * 子树序列化时如果传入 {@code null} 或 {@link ConfigNode#isNull()}，
 * JSON 返回 {@code "null"}，YAML 返回空串。</p>
 */
public final class ConfigSerializer {

    private ConfigSerializer() {
    }

    /**
     * 把配置节点子树序列化为指定格式的文本。
     *
     * @param node 配置节点，允许为 null
     * @param format 目标格式，目前支持 {@link ConfigFormat#JSON} 与 {@link ConfigFormat#YAML}
     * @return 序列化后的文本；node 为 null 或空值时按 format 返回 {@code "null"} 或空串
     * @throws IllegalArgumentException 当 format 为 null 或暂不支持时
     */
    public static String toString(ConfigNode node, ConfigFormat format) {
        if (format == null) {
            throw new IllegalArgumentException("format must not be null");
        }
        switch (format) {
            case JSON:
                return new JsonConfigWriter().writeToString(node);
            case YAML:
                return new YamlConfigWriter().writeToString(node);
            default:
                throw new IllegalArgumentException("Unsupported config format: " + format);
        }
    }

    /**
     * 把文本按指定格式解析为配置节点子树。
     *
     * <p>本方法是 {@link #toString(ConfigNode, ConfigFormat)} 的反向入口，供源码编辑类模板
     * （如 5-C 的 {@code RawEditorPropertyBinding}）做语法校验与写回前转换使用。实现委托到
     * {@link Config#parse(String, ConfigFormat)}，对外暴露与 {@code toString} 对称的入口，
     * 便于 binding 只依赖 {@code ConfigSerializer} 单一外观。</p>
     *
     * <p>解析失败时抛出 {@link ConfigException}；调用方需自行捕获并反馈给 UI 错误层，
     * 不应让异常冒泡到事件循环。</p>
     *
     * @param text 配置文本；为 null 视为空串
     * @param format 目标格式，目前支持 {@link ConfigFormat#JSON} 与 {@link ConfigFormat#YAML}
     * @return 解析后的配置节点；空文本返回 {@code NullConfigNode} 或空 map（视格式而定）
     * @throws IllegalArgumentException 当 format 为 null 或暂不支持时
     * @throws ConfigException 当文本存在语法错误时
     */
    public static ConfigNode parse(String text, ConfigFormat format) throws ConfigException {
        if (format == null) {
            throw new IllegalArgumentException("format must not be null");
        }
        switch (format) {
            case JSON:
            case YAML:
                return Config.parse(text == null ? "" : text, format);
            default:
                throw new IllegalArgumentException("Unsupported config format: " + format);
        }
    }
}
