package club.heiqi.config;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.comments.CommentLine;
import org.yaml.snakeyaml.comments.CommentType;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.nodes.AnchorNode;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

/**
 * YAML 配置加载器实现，基于 SnakeYAML 2.2 的 compose 路径。
 *
 * <p>用 {@link Yaml#compose(java.io.Reader)} 替换早期的 {@link Yaml#load(String)}，
 * 取得带注释的 {@link Node} 树，再递归转换成 {@link ConfigNode} 树。
 * 通过 {@link LoaderOptions#setProcessComments(boolean)} 开启注释处理，
 * 把 {@link CommentLine} 转成与 SnakeYAML 解耦的 {@link CommentMeta} 挂到对应节点，
 * 支持 round-trip 注释保真。</p>
 *
 * <p>compose 路径不解析锚点/别名/合并键（那是 construct 阶段的事），本加载器自行处理：
 * AnchorNode 解引用、{@code <<} 合并键展开，保证与原 load 行为等价。</p>
 *
 * <p>外部 API 签名保持不变，{@link ConfigSerializer} 与 {@link Config} 的调用处无需改动。
 * SnakeYAML 的包名在打包阶段由 shadow relocate 处理，编译期直接 import
 * {@code org.yaml.snakeyaml.*} 即可。</p>
 */
class YamlConfigLoader implements ConfigLoader {

    /** 复用的 SnakeYAML 实例，线程不安全，每次加载新建；此处仅持有配置好的实例 */
    private final Yaml yaml = createYaml();

    private static Yaml createYaml() {
        LoaderOptions options = new LoaderOptions();
        // 开启注释处理，compose 返回的 Node 树会携带 CommentLine
        options.setProcessComments(true);
        return new Yaml(options);
    }

    @Override
    public ConfigNode load(ConfigSource source) throws ConfigException {
        try {
            String content = source.read();
            return parseYaml(content);
        } catch (ConfigException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigException("Failed to parse YAML from " + source.getDescription(), e);
        }
    }

    @Override
    public ConfigFormat getFormat() {
        return ConfigFormat.YAML;
    }

    /**
     * 解析 YAML 内容为配置节点树。
     *
     * <p>空文档返回空 MAP（与早期实现保持一致，便于上层按 map 路径取值）；
     * compose 返回 null（纯注释或显式 null 文档）时同样返回空 MAP。</p>
     *
     * @param content YAML 文本
     * @return 配置节点
     * @throws ConfigException 如果 YAML 语法错误
     */
    private ConfigNode parseYaml(String content) throws ConfigException {
        if (content == null || content.trim().isEmpty()) {
            return new MapConfigNode(new LinkedHashMap<String, ConfigNode>());
        }

        Node root;
        try {
            root = yaml.compose(new StringReader(content));
        } catch (Exception e) {
            throw new ConfigException("YAML syntax error: " + e.getMessage(), e);
        }

        if (root == null) {
            // 文档只有注释或显式 null
            return new MapConfigNode(new LinkedHashMap<String, ConfigNode>());
        }

        return toConfigNode(root);
    }

    /**
     * 把 SnakeYAML 的 {@link Node} 树递归转换成 {@link ConfigNode}，
     * 并把 {@link CommentLine} 转成 {@link CommentMeta} 挂到对应节点。
     *
     * @param node SnakeYAML 节点
     * @return 对应的 ConfigNode
     */
    private ConfigNode toConfigNode(Node node) {
        // AnchorNode 解引用：锚点/别名都包装在 AnchorNode 里
        while (node instanceof AnchorNode) {
            node = ((AnchorNode) node).getRealNode();
        }

        ConfigNode result;
        if (node instanceof MappingNode) {
            result = mappingToConfigNode((MappingNode) node);
        } else if (node instanceof SequenceNode) {
            List<Node> items = ((SequenceNode) node).getValue();
            List<ConfigNode> list = new ArrayList<ConfigNode>(items.size());
            for (Node item : items) {
                list.add(toConfigNodeListItem(item));
            }
            result = new ListConfigNode(list);
        } else if (node instanceof ScalarNode) {
            result = scalarToConfigNode((ScalarNode) node);
        } else {
            // 未知节点类型退化为 null
            result = NullConfigNode.INSTANCE;
        }

        // 挂载注释元数据
        attachComments(result, node);
        return result;
    }

    /**
     * MappingNode 转 MapConfigNode，处理 {@code <<} 合并键。
     *
     * <p>YAML 合并语义：显式键优先于合并键；合并列表中前面的 map 优先于后面的。
     * 本方法先展开合并项作为默认值，再用显式键覆盖。</p>
     */
    private ConfigNode mappingToConfigNode(MappingNode mapping) {
        Map<String, ConfigNode> map = new LinkedHashMap<String, ConfigNode>();
        List<NodeTuple> tuples = mapping.getValue();

        // 第一遍：展开合并键（<<）作为默认值
        for (NodeTuple tuple : tuples) {
            Node keyNode = unwrap(tuple.getKeyNode());
            if (isMergeKey(keyNode)) {
                Node mergeSource = unwrap(tuple.getValueNode());
                mergeInto(map, mergeSource);
            }
        }

        // 第二遍：显式键覆盖
        for (NodeTuple tuple : tuples) {
            Node keyNode = unwrap(tuple.getKeyNode());
            if (isMergeKey(keyNode)) {
                continue;
            }
            String key = scalarKey(keyNode);
            ConfigNode valueNode = toConfigNode(tuple.getValueNode());
            // SnakeYAML 2.2 的 compose 把 key 上方的块注释挂到 key scalar 上，
            // 这里转移到 value ConfigNode，让 get(key) 能取到注释。
            if (keyNode instanceof ScalarNode) {
                CommentMeta keyBlock = mergeCommentLines(keyNode.getBlockComments());
                if (keyBlock != null && valueNode instanceof AbstractConfigNode) {
                    AbstractConfigNode abs = (AbstractConfigNode) valueNode;
                    if (abs.getBlockComment() == null) {
                        abs.setBlockComment(keyBlock);
                    }
                }
            }
            map.put(key, valueNode);
        }

        return new MapConfigNode(map);
    }

    /**
     * 转换 sequence 元素，处理 block-style mapping 元素的块注释归属。
     *
     * <p>SnakeYAML 2.2 的 compose 把 list 元素上方的块注释挂到元素内部第一个 key
     * scalar 上（而非元素 mapping 本身）。本方法在递归转换前把该注释提取出来，
     * 清空 key 上的注释避免 {@link #mappingToConfigNode} 重复转移，转换后挂到元素
     * ConfigNode 本身，符合"注释属于整个 list 元素"的直觉语义。</p>
     *
     * <p>scalar 元素的块注释由 compose 直接挂到 scalar 节点，{@link #attachComments}
     * 已处理，本方法不干预。</p>
     */
    private ConfigNode toConfigNodeListItem(Node item) {
        Node unwrapped = unwrap(item);
        CommentMeta itemBlock = null;
        if (unwrapped instanceof MappingNode) {
            List<NodeTuple> itemTuples = ((MappingNode) unwrapped).getValue();
            if (itemTuples != null && !itemTuples.isEmpty()) {
                Node firstKey = unwrap(itemTuples.get(0).getKeyNode());
                if (firstKey instanceof ScalarNode) {
                    itemBlock = mergeCommentLines(firstKey.getBlockComments());
                    if (itemBlock != null) {
                        // 清空，避免 mappingToConfigNode 重复转移到内部 value
                        ((ScalarNode) firstKey).setBlockComments(null);
                    }
                }
            }
        }
        ConfigNode itemNode = toConfigNode(item);
        if (itemBlock != null && itemNode instanceof AbstractConfigNode) {
            AbstractConfigNode abs = (AbstractConfigNode) itemNode;
            if (abs.getBlockComment() == null) {
                abs.setBlockComment(itemBlock);
            }
        }
        return itemNode;
    }

    /** 把合并源（map 或 list-of-maps）的键合并进目标 map，不覆盖已有键 */
    private void mergeInto(Map<String, ConfigNode> target, Node source) {
        if (source instanceof MappingNode) {
            for (NodeTuple t : ((MappingNode) source).getValue()) {
                String k = scalarKey(unwrap(t.getKeyNode()));
                if (!target.containsKey(k)) {
                    target.put(k, toConfigNode(t.getValueNode()));
                }
            }
        } else if (source instanceof SequenceNode) {
            // merge list of maps：按列表顺序，前面的优先
            for (Node item : ((SequenceNode) source).getValue()) {
                Node m = unwrap(item);
                if (m instanceof MappingNode) {
                    for (NodeTuple t : ((MappingNode) m).getValue()) {
                        String k = scalarKey(unwrap(t.getKeyNode()));
                        if (!target.containsKey(k)) {
                            target.put(k, toConfigNode(t.getValueNode()));
                        }
                    }
                }
            }
        }
    }

    /** 判断 key 是否为合并键 {@code <<} */
    private boolean isMergeKey(Node keyNode) {
        return keyNode instanceof ScalarNode && "<<".equals(((ScalarNode) keyNode).getValue());
    }

    /** 解包 AnchorNode，返回真实节点 */
    private Node unwrap(Node node) {
        while (node instanceof AnchorNode) {
            node = ((AnchorNode) node).getRealNode();
        }
        return node;
    }

    /** 取 mapping key 的字符串形式 */
    private String scalarKey(Node keyNode) {
        if (keyNode instanceof ScalarNode) {
            return ((ScalarNode) keyNode).getValue();
        }
        // 非标量 key（很少见）退化为 toString
        return String.valueOf(keyNode);
    }

    /**
     * ScalarNode 转 ConfigNode，按 tag 分发到对应标量类型。
     */
    private ConfigNode scalarToConfigNode(ScalarNode scalar) {
        Tag tag = scalar.getTag();
        String value = scalar.getValue();

        if (Tag.NULL.equals(tag)) {
            return NullConfigNode.INSTANCE;
        }
        if (Tag.STR.equals(tag)) {
            return new StringConfigNode(value);
        }
        if (Tag.INT.equals(tag)) {
            Number n = parseInteger(value);
            return n != null ? new NumberConfigNode(n) : new StringConfigNode(value);
        }
        if (Tag.FLOAT.equals(tag)) {
            Number n = parseFloat(value);
            return n != null ? new NumberConfigNode(n) : new StringConfigNode(value);
        }
        if (Tag.BOOL.equals(tag)) {
            return new BooleanConfigNode(parseBoolean(value));
        }
        // 其他 tag（timestamp、binary 等）退化为字符串，保留可读性
        return new StringConfigNode(value);
    }

    /** 解析整数：优先 Integer，超范围用 Long */
    private Number parseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e1) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e2) {
                return null;
            }
        }
    }

    /** 解析浮点：用 Double */
    private Number parseFloat(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 解析 YAML 1.1 布尔值：true/yes/on → true，false/no/off → false。
     * 其余退化为 false（与 SnakeYAML constructor 的 bool 行为对齐）。
     */
    private boolean parseBoolean(String value) {
        if (value == null) {
            return false;
        }
        String s = value.toLowerCase();
        return "true".equals(s) || "yes".equals(s) || "on".equals(s) || "1".equals(s);
    }

    /**
     * 把 SnakeYAML Node 上的三类注释（block/inline/end）转成 CommentMeta 挂到 ConfigNode。
     */
    private void attachComments(ConfigNode cfgNode, Node snakeNode) {
        if (!(cfgNode instanceof AbstractConfigNode)) {
            return;
        }
        AbstractConfigNode abs = (AbstractConfigNode) cfgNode;
        CommentMeta block = mergeCommentLines(snakeNode.getBlockComments());
        if (block != null) {
            abs.setBlockComment(block);
        }
        CommentMeta inline = mergeCommentLines(snakeNode.getInLineComments());
        if (inline != null) {
            abs.setInlineComment(inline);
        }
        CommentMeta end = mergeCommentLines(snakeNode.getEndComments());
        if (end != null) {
            abs.setEndComment(end);
        }
    }

    /**
     * 把一组 {@link CommentLine} 合并为单条 {@link CommentMeta}。
     *
     * <p>多行块注释用 {@code \n} 连接各行（去掉 {@code #} 前缀）；
     * 空行（{@link CommentType#BLANK_LINE}）在 value 中表现为空字符串行。
     * 类型取首个非 BLANK_LINE 的 CommentLine 类型；全为空行则类型为 BLANK_LINE。</p>
     */
    private CommentMeta mergeCommentLines(List<CommentLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }

        CommentType firstNonBlank = null;
        for (CommentLine line : lines) {
            CommentType t = line.getCommentType();
            if (t != CommentType.BLANK_LINE) {
                firstNonBlank = t;
                break;
            }
        }
        CommentMeta.CommentType metaType = mapType(firstNonBlank != null ? firstNonBlank : CommentType.BLANK_LINE);

        StringBuilder sb = new StringBuilder();
        int column = 0;
        for (int i = 0; i < lines.size(); i++) {
            CommentLine line = lines.get(i);
            if (i > 0) {
                sb.append("\n");
            }
            if (i == 0) {
                Mark start = line.getStartMark();
                if (start != null) {
                    column = start.getColumn();
                }
            }
            if (line.getCommentType() == CommentType.BLANK_LINE) {
                // 空行：空字符串行
                sb.append("");
            } else {
                sb.append(stripCommentPrefix(line.getValue()));
            }
        }

        if (metaType == CommentMeta.CommentType.BLANK_LINE) {
            return CommentMeta.blankLine(column);
        }
        return new CommentMeta(metaType, sb.toString(), column);
    }

    /** SnakeYAML CommentType → 本地 CommentMeta.CommentType */
    private CommentMeta.CommentType mapType(CommentType type) {
        if (type == CommentType.BLOCK) {
            return CommentMeta.CommentType.BLOCK;
        }
        if (type == CommentType.IN_LINE) {
            return CommentMeta.CommentType.IN_LINE;
        }
        return CommentMeta.CommentType.BLANK_LINE;
    }

    /** 去掉注释文本的 {@code #} 前缀和至多一个空格 */
    private String stripCommentPrefix(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw;
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        if (s.startsWith(" ")) {
            s = s.substring(1);
        }
        return s;
    }
}
