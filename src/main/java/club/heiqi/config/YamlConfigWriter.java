package club.heiqi.config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.comments.CommentLine;
import org.yaml.snakeyaml.comments.CommentType;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

/**
 * YAML 配置写入器实现，基于 SnakeYAML 2.2 的 serialize 路径。
 *
 * <p>用 {@code Yaml.serialize(Node, Writer)} 替换早期的 {@link Yaml#dump(Object)}，
 * 把 {@link ConfigNode} 树重新构建成 SnakeYAML 的 {@link Node} 树（带 {@link Tag}），
 * 并从 {@link CommentMeta} 重建 {@link CommentLine} 挂到对应节点，
 * 通过 {@link DumperOptions#setProcessComments(boolean)} 开启注释输出，
 * 实现 round-trip 注释保真。</p>
 *
 * <p>外部 API 签名保持不变：{@link #write(ConfigNode, ConfigSource)} 与
 * {@link #writeToString(ConfigNode)} 仍可被 {@link ConfigSerializer} 等同包组件直接复用。</p>
 *
 * <p>DumperOptions 固定为 2 空格缩进、不写文档开始/结束标记、块样式。</p>
 */
class YamlConfigWriter implements ConfigWriter {

    /** 复用的 SnakeYAML 实例，配置见 {@link #createYaml()} */
    private final Yaml yaml = createYaml();

    @Override
    public void write(ConfigNode node, ConfigSource target) throws ConfigException {
        String text = writeToString(node);

        if (target instanceof FileConfigSource) {
            File file = ((FileConfigSource) target).getFile();
            try {
                FileOutputStream fos = new FileOutputStream(file);
                try {
                    OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
                    writer.write(text);
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
     * <p>节点为 null 或 {@link ConfigNode#isNull()} 时返回空串（YAML 中空文档视为 null）。
     * 内部把 ConfigNode 树构建成 SnakeYAML Node 树并 {@link Yaml#serialize} 输出，
     * 携带的 {@link CommentMeta} 会还原为注释。</p>
     *
     * @param node 配置节点
     * @return YAML 文本
     */
    String writeToString(ConfigNode node) {
        if (node == null || node.isNull()) {
            return "";
        }

        Node snakeNode = toSnakeYamlNode(node);
        StringWriter writer = new StringWriter();
        yaml.serialize(snakeNode, writer);
        return writer.toString();
    }

    /**
     * 创建配置好的 SnakeYAML 实例。
     *
     * <p>DumperOptions：2 空格缩进、不写文档开始/结束标记、块样式、开启注释处理。</p>
     */
    private static Yaml createYaml() {
        DumperOptions options = new DumperOptions();
        options.setIndent(2);
        options.setExplicitStart(false);
        options.setExplicitEnd(false);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        // 开启注释输出，serialize 时会写出 CommentLine
        options.setProcessComments(true);
        return new Yaml(options);
    }

    /**
     * 把 {@link ConfigNode} 树递归构建成 SnakeYAML 的 {@link Node} 树，
     * 并把 {@link CommentMeta} 还原为 {@link CommentLine} 挂到对应节点。
     */
    private Node toSnakeYamlNode(ConfigNode node) {
        if (node == null || node.isNull()) {
            ScalarNode nullNode = new ScalarNode(Tag.NULL, "null", null, null, DumperOptions.ScalarStyle.PLAIN);
            attachComments(nullNode, node);
            return nullNode;
        }

        switch (node.getType()) {
            case MAP: {
                List<NodeTuple> tuples = new ArrayList<NodeTuple>();
                Map<String, ConfigNode> map = node.asMap();
                if (map != null) {
                    for (Map.Entry<String, ConfigNode> entry : map.entrySet()) {
                        ScalarNode keyNode = new ScalarNode(Tag.STR, entry.getKey(), null, null,
                                DumperOptions.ScalarStyle.PLAIN);
                        Node valueNode = toSnakeYamlNode(entry.getValue());
                        // SnakeYAML 语义：key 上方的块注释挂到 key scalar，serialize 时
                        // 输出在 key 之前。ConfigNode 层把块注释挂到 value（便于 get(key)
                        // 访问），这里还原到 key scalar，保证 round-trip 注释位置正确。
                        List<CommentLine> valueBlock = valueNode.getBlockComments();
                        if (valueBlock != null && !valueBlock.isEmpty()) {
                            keyNode.setBlockComments(valueBlock);
                            valueNode.setBlockComments(null);
                        }
                        tuples.add(new NodeTuple(keyNode, valueNode));
                    }
                }
                MappingNode mappingNode = new MappingNode(Tag.MAP, tuples, DumperOptions.FlowStyle.BLOCK);
                attachComments(mappingNode, node);
                return mappingNode;
            }
            case LIST: {
                List<Node> items = new ArrayList<Node>();
                List<ConfigNode> list = node.asList();
                if (list != null) {
                    for (ConfigNode item : list) {
                        items.add(toSnakeYamlNode(item));
                    }
                }
                SequenceNode sequenceNode = new SequenceNode(Tag.SEQ, items, DumperOptions.FlowStyle.BLOCK);
                attachComments(sequenceNode, node);
                return sequenceNode;
            }
            case STRING: {
                String str = node.asString();
                ScalarNode scalarNode = new ScalarNode(Tag.STR, str != null ? str : "", null, null,
                        DumperOptions.ScalarStyle.PLAIN);
                attachComments(scalarNode, node);
                return scalarNode;
            }
            case NUMBER: {
                Object raw = getRawValueInternal(node);
                String numStr;
                Tag numTag;
                if (raw instanceof Number) {
                    numStr = formatNumber((Number) raw);
                    numTag = isIntegerNumber(raw) ? Tag.INT : Tag.FLOAT;
                } else {
                    numStr = String.valueOf(raw);
                    numTag = Tag.STR;
                }
                ScalarNode scalarNode = new ScalarNode(numTag, numStr, null, null,
                        DumperOptions.ScalarStyle.PLAIN);
                attachComments(scalarNode, node);
                return scalarNode;
            }
            case BOOLEAN: {
                ScalarNode scalarNode = new ScalarNode(Tag.BOOL, String.valueOf(node.asBoolean(false)), null, null,
                        DumperOptions.ScalarStyle.PLAIN);
                attachComments(scalarNode, node);
                return scalarNode;
            }
            case NULL:
            default: {
                ScalarNode nullNode = new ScalarNode(Tag.NULL, "null", null, null, DumperOptions.ScalarStyle.PLAIN);
                attachComments(nullNode, node);
                return nullNode;
            }
        }
    }

    /**
     * 把 ConfigNode 上的 {@link CommentMeta} 还原为 {@link CommentLine} 列表挂到 SnakeYAML Node。
     *
     * <p>多行块注释按 {@code \n} 拆成多行 CommentLine；空字符串行还原为
     * {@link CommentType#BLANK_LINE}。无 CommentMeta 的节点不挂 CommentLine。</p>
     */
    private void attachComments(Node snakeNode, ConfigNode cfgNode) {
        if (cfgNode == null) {
            return;
        }
        List<CommentLine> block = toCommentLines(cfgNode.getBlockComment());
        if (block != null) {
            snakeNode.setBlockComments(block);
        }
        List<CommentLine> inline = toCommentLines(cfgNode.getInlineComment());
        if (inline != null) {
            snakeNode.setInLineComments(inline);
        }
        List<CommentLine> end = toCommentLines(cfgNode.getEndComment());
        if (end != null) {
            snakeNode.setEndComments(end);
        }
    }

    /**
     * 把单条 {@link CommentMeta} 转成 {@link CommentLine} 列表。
     *
     * @return CommentLine 列表；meta 为 null 时返回 null（不挂注释）
     */
    private List<CommentLine> toCommentLines(CommentMeta meta) {
        if (meta == null) {
            return null;
        }
        List<CommentLine> lines = new ArrayList<CommentLine>();

        if (meta.getType() == CommentMeta.CommentType.BLANK_LINE) {
            lines.add(new CommentLine(null, null, "", CommentType.BLANK_LINE));
            return lines;
        }

        CommentType syType = meta.getType() == CommentMeta.CommentType.BLOCK
                ? CommentType.BLOCK
                : CommentType.IN_LINE;
        String value = meta.getValue();
        if (value == null) {
            value = "";
        }
        // 按 \n 拆行，空字符串行还原为 BLANK_LINE
        String[] parts = value.split("\n", -1);
        for (String part : parts) {
            if (part.isEmpty()) {
                lines.add(new CommentLine(null, null, "", CommentType.BLANK_LINE));
            } else {
                // CommentLine 的 value 需含 # 前缀
                lines.add(new CommentLine(null, null, "# " + part, syType));
            }
        }
        return lines;
    }

    /**
     * 取出节点的原始值对象。ConfigNode 接口未暴露 getRawValue，
     * 这里通过 AbstractConfigNode 的包级方法获取。
     */
    private Object getRawValueInternal(ConfigNode node) {
        if (node instanceof AbstractConfigNode) {
            return ((AbstractConfigNode) node).getRawValue();
        }
        return null;
    }

    /** 判断 Number 是否为整数类型（Integer/Long/BigInteger 等） */
    private boolean isIntegerNumber(Object raw) {
        return raw instanceof Integer
                || raw instanceof Long
                || raw instanceof java.math.BigInteger
                || raw instanceof Short
                || raw instanceof Byte;
    }

    /** 格式化数字为 YAML 文本：整数去小数点，浮点用 toString */
    private String formatNumber(Number number) {
        // 浮点类型直接 toString；整数类型直接 toString
        // 注意：Double 的 3.14159 → "3.14159"，Long 的 9000000000 → "9000000000"
        return number.toString();
    }
}
