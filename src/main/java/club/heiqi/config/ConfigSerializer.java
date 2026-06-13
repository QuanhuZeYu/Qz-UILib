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
}
