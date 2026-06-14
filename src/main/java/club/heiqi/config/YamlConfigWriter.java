package club.heiqi.config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * YAML 配置写入器实现
 */
class YamlConfigWriter implements ConfigWriter {

    @Override
    public void write(ConfigNode node, ConfigSource target) throws ConfigException {
        StringBuilder yaml = new StringBuilder();
        writeNode(node, yaml, 0);

        // 写入文件
        if (target instanceof FileConfigSource) {
            File file = ((FileConfigSource) target).getFile();
            try {
                FileOutputStream fos = new FileOutputStream(file);
                try {
                    OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
                    writer.write(yaml.toString());
                    writer.flush();
                    writer.close();
                } finally {
                    fos.close();
                }
            } catch (IOException e) {
                throw new ConfigException("Failed to write YAML to file: " + file.getAbsolutePath(), e);
            }
        } else {
            throw new ConfigException("Unsupported config source type for writing: " + target.getClass().getName());
        }
    }

    @Override
    public ConfigFormat getFormat() {
        return ConfigFormat.YAML;
    }

    /**
     * 将配置节点序列化为 YAML 文本。
     *
     * <p>仅复用本写入器内部的 2 空格缩进策略，输出与 {@link #write} 一致。
     * 节点为 null 或 {@link ConfigNode#isNull()} 时返回空串（YAML 中空文档视为 null）。</p>
     *
     * @param node 配置节点
     * @return YAML 文本
     */
    String writeToString(ConfigNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        writeNode(node, builder, 0);
        return builder.toString();
    }

    /**
     * 写入节点
     * 
     * @param node 配置节点
     * @param builder 字符串构建器
     * @param indent 缩进级别
     */
    private void writeNode(ConfigNode node, StringBuilder builder, int indent) {
        if (node.isNull()) {
            builder.append("null");
            return;
        }

        switch (node.getType()) {
            case STRING:
                writeString(node.asString(), builder);
                break;

            case NUMBER:
                try {
                    // 尝试作为整数
                    long longValue = node.asLong();
                    builder.append(longValue);
                } catch (ConfigException e) {
                    // 作为浮点数
                    builder.append(node.asDouble(0.0));
                }
                break;

            case BOOLEAN:
                builder.append(node.asBoolean(false));
                break;

            case LIST:
                writeList(node.asList(), builder, indent);
                break;

            case MAP:
                writeMap(node.asMap(), builder, indent);
                break;
        }
    }

    /**
     * 写入字符串值
     * 
     * @param value 字符串值
     * @param builder 字符串构建器
     */
    private void writeString(String value, StringBuilder builder) {
        if (value == null) {
            builder.append("null");
            return;
        }

        // 如果包含特殊字符，使用引号
        if (needsQuotes(value)) {
            builder.append('"').append(escapeString(value)).append('"');
        } else {
            builder.append(value);
        }
    }

    /**
     * 判断字符串是否需要引号
     * 
     * @param value 字符串值
     * @return 如果需要引号返回 true
     */
    private boolean needsQuotes(String value) {
        if (value.isEmpty()) {
            return true;
        }

        // 检查是否为布尔值或数字
        if (value.equals("true") || value.equals("false") ||
            value.equals("yes") || value.equals("no") ||
            value.equals("null") || value.equals("~")) {
            return true;
        }

        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            // 不是数字
        }

        // 检查特殊字符
        return value.contains(":") || value.contains("#") ||
               value.contains("\n") || value.contains("\"") ||
               value.startsWith(" ") || value.endsWith(" ");
    }

    /**
     * 转义字符串
     * 
     * @param value 原始字符串
     * @return 转义后的字符串
     */
    private String escapeString(String value) {
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    /**
     * 写入列表
     * 
     * @param list 列表
     * @param builder 字符串构建器
     * @param indent 缩进级别
     */
    private void writeList(List<ConfigNode> list, StringBuilder builder, int indent) {
        if (list == null || list.isEmpty()) {
            builder.append("[]");
            return;
        }

        for (ConfigNode item : list) {
            builder.append('\n');
            writeIndent(builder, indent);
            builder.append("- ");

            if (item.getType() == ConfigNode.NodeType.MAP || item.getType() == ConfigNode.NodeType.LIST) {
                // 复杂类型需要换行
                writeNode(item, builder, indent + 2);
            } else {
                // 简单类型直接跟在 - 后面
                writeNode(item, builder, indent + 2);
            }
        }
    }

    /**
     * 写入映射表
     * 
     * @param map 映射表
     * @param builder 字符串构建器
     * @param indent 缩进级别
     */
    private void writeMap(Map<String, ConfigNode> map, StringBuilder builder, int indent) {
        if (map == null || map.isEmpty()) {
            builder.append("{}");
            return;
        }

        boolean first = true;
        for (Map.Entry<String, ConfigNode> entry : map.entrySet()) {
            if (!first) {
                builder.append('\n');
            }
            first = false;

            writeIndent(builder, indent);
            builder.append(entry.getKey()).append(":");

            ConfigNode value = entry.getValue();
            if (value.getType() == ConfigNode.NodeType.MAP) {
                // MAP 类型需要换行并递归写入
                builder.append('\n');
                writeNode(value, builder, indent + 2);
            } else if (value.getType() == ConfigNode.NodeType.LIST) {
                // LIST 类型需要换行
                writeNode(value, builder, indent + 2);
            } else {
                // 简单类型直接跟在 : 后面
                builder.append(' ');
                writeNode(value, builder, indent + 2);
            }
        }
    }

    /**
     * 写入缩进
     * 
     * @param builder 字符串构建器
     * @param indent 缩进级别
     */
    private void writeIndent(StringBuilder builder, int indent) {
        for (int i = 0; i < indent; i++) {
            builder.append(' ');
        }
    }
}
