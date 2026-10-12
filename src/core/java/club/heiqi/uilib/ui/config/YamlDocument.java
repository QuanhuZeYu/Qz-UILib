package club.heiqi.uilib.ui.config;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.comments.CommentLine;
import org.yaml.snakeyaml.comments.CommentType;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

/** 内部 YAML 表示层：只 compose 节点，从不调用任意类型 constructor。
 * 业务值与节点分离；merge 按映射键或列表索引复用节点，值变化也保留原注释。 */
final class YamlDocument {
    private MappingNode root;
    private final String source;
    private final Map<String, Node> locations = new LinkedHashMap<>();
    private final Map<String, Object> tree;

    private YamlDocument(MappingNode root, String source) {
        this.root = root;
        this.source = source;
        @SuppressWarnings("unchecked") Map<String, Object> parsed = (Map<String, Object>) readNode(root, "", Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>()));
        tree = parsed;
    }
    static YamlDocument empty() { return new YamlDocument(mapping(), "<defaults>"); }
    static YamlDocument read(Reader reader, String source) throws IOException {
        // 先按 Reader 合同读取，保留原始 IOException，避免解析库包装成运行时异常。
        StringBuilder text = new StringBuilder();
        char[] buffer = new char[4096];
        int count;
        while ((count = reader.read(buffer)) != -1) text.append(buffer, 0, count);
        LoaderOptions options = new LoaderOptions();
        options.setProcessComments(true);
        // 仅 compose：先保留 tag 节点以获得完整字段路径，再由 readNode 严格拒绝。
        // 不调用 load/constructor，因此输入 tag 不会创建 Java 对象。
        options.setTagInspector(tag -> true);
        Node node;
        try { node = new Yaml(options).compose(new StringReader(text.toString())); }
        catch (MarkedYAMLException failure) {
            throw new ConfigException("$", source + position(failure.getProblemMark()) + ": YAML 语法错误: " + failure.getProblem(), failure);
        } catch (RuntimeException failure) {
            throw new ConfigException("$", source + ": YAML 解析失败: " + failure.getMessage(), failure);
        }
        if (node == null) node = mapping();
        else if (Tag.COMMENT.equals(node.getTag()) || node instanceof ScalarNode && Tag.NULL.equals(node.getTag()) && ((ScalarNode) node).getValue().isEmpty()) {
            // compose 对只有注释的空文档返回 COMMENT mapping，注释仍属于默认文档。
            MappingNode empty = mapping();
            empty.setBlockComments(node.getBlockComments());
            empty.setInLineComments(node.getInLineComments());
            empty.setEndComments(node.getEndComments());
            node = empty;
        }
        if (!(node instanceof MappingNode)) throw new ConfigException("$", source + position(node.getStartMark()) + ": YAML 根必须是映射");
        return new YamlDocument((MappingNode) node, source);
    }
    Map<String, Object> tree() { return tree; }
    ConfigException locate(ConfigException failure) {
        String path = failure.path();
        Node node = locations.get(path);
        // 必填字段没有自己的节点时，定位最近存在的父对象。
        String parent = path;
        while (node == null && !parent.isEmpty()) {
            int dot = parent.lastIndexOf('.'), index = parent.lastIndexOf('[');
            int end = Math.max(dot, index);
            parent = end < 0 ? "" : parent.substring(0, end);
            node = locations.get(parent);
        }
        return new ConfigException(path, source + position(node == null ? null : node.getStartMark()) + ": " + failure.getMessage(), failure);
    }
    private ConfigException bad(String path, Node node, String message) {
        return new ConfigException(path.isEmpty() ? "$" : path, source + position(node.getStartMark()) + ": " + message);
    }
    private static String position(Mark mark) {
        return mark == null ? "" : ":" + (mark.getLine() + 1) + ":" + (mark.getColumn() + 1);
    }
    private Object readNode(Node node, String path, Set<Node> active) {
        locations.put(path, node);
        if (!active.add(node)) throw bad(path, node, "循环 YAML alias 不受支持");
        try {
            if (node instanceof MappingNode) {
                if (!Tag.MAP.equals(node.getTag())) throw bad(path, node, "不支持 YAML tag: " + node.getTag());
                Map<String, Object> result = new LinkedHashMap<>();
                for (NodeTuple tuple : ((MappingNode) node).getValue()) {
                    Node keyNode = tuple.getKeyNode();
                    if (!(keyNode instanceof ScalarNode) || !Tag.STR.equals(keyNode.getTag())) throw bad(path, keyNode, "映射键必须是 string");
                    String key = ((ScalarNode) keyNode).getValue();
                    String child = ConfigCodecs.child(path, key);
                    if (result.containsKey(key)) throw bad(child, keyNode, "重复映射键");
                    result.put(key, readNode(tuple.getValueNode(), child, active));
                }
                return Collections.unmodifiableMap(result);
            }
            if (node instanceof SequenceNode) {
                if (!Tag.SEQ.equals(node.getTag())) throw bad(path, node, "不支持 YAML tag: " + node.getTag());
                List<Object> result = new ArrayList<>();
                List<Node> input = ((SequenceNode) node).getValue();
                for (int i = 0; i < input.size(); i++) result.add(readNode(input.get(i), path + "[" + i + "]", active));
                return Collections.unmodifiableList(result);
            }
            if (!(node instanceof ScalarNode)) throw bad(path, node, "不支持的 YAML 节点");
            String value = ((ScalarNode) node).getValue();
            Tag tag = node.getTag();
            if (Tag.STR.equals(tag)) return value;
            if (Tag.NULL.equals(tag)) return null;
            if (Tag.BOOL.equals(tag)) {
                if ("true".equalsIgnoreCase(value)) return true;
                if ("false".equalsIgnoreCase(value)) return false;
                throw bad(path, node, "boolean 必须是 true 或 false");
            }
            try {
                if (Tag.INT.equals(tag)) return integer(value);
                if (Tag.FLOAT.equals(tag)) {
                    BigDecimal number = new BigDecimal(value.replace("_", ""));
                    if (!Double.isFinite(number.doubleValue()) || number.doubleValue() == 0.0 && number.signum() != 0)
                        throw bad(path, node, "浮点数超出 Double 范围");
                    return number;
                }
            } catch (NumberFormatException failure) { throw bad(path, node, "非法或非有限数值: " + value); }
            throw bad(path, node, "不支持 YAML tag: " + tag);
        } finally { active.remove(node); }
    }
    private static BigInteger integer(String text) {
        String value = text.replace("_", "");
        boolean negative = value.startsWith("-");
        if (negative || value.startsWith("+")) value = value.substring(1);
        int radix = 10;
        if (value.startsWith("0x")) { radix = 16; value = value.substring(2); }
        else if (value.startsWith("0b")) { radix = 2; value = value.substring(2); }
        else if (value.length() > 1 && value.startsWith("0")) { radix = 8; value = value.substring(1); }
        BigInteger number = new BigInteger(value, radix);
        return negative ? number.negate() : number;
    }
    void write(Map<String, Object> values, Map<String, String> comments, Writer writer) {
        root = (MappingNode) merge(root, values, "", comments);
        DumperOptions options = new DumperOptions();
        options.setProcessComments(true);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setAllowUnicode(true);
        new Yaml(options).serialize(root, writer);
    }
    private static MappingNode mapping() { return new MappingNode(Tag.MAP, new ArrayList<NodeTuple>(), DumperOptions.FlowStyle.BLOCK); }
    private static ScalarNode scalar(Tag tag, String value) { return new ScalarNode(tag, value, null, null, DumperOptions.ScalarStyle.PLAIN); }
    private Node merge(Node old, Object value, String path, Map<String, String> comments) {
        Node result;
        if (value instanceof Map) {
            MappingNode node = old instanceof MappingNode ? (MappingNode) old : mapping();
            Map<String, NodeTuple> previous = new LinkedHashMap<>();
            for (NodeTuple tuple : node.getValue()) previous.put(((ScalarNode) tuple.getKeyNode()).getValue(), tuple);
            List<NodeTuple> next = new ArrayList<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!(entry.getKey() instanceof String)) throw new ConfigException(path, "codec 输出映射键必须是 string");
                String key = (String) entry.getKey();
                String child = ConfigCodecs.child(path, key);
                NodeTuple tuple = previous.get(key);
                Node keyNode = tuple == null ? scalar(Tag.STR, key) : tuple.getKeyNode();
                Node childNode = merge(tuple == null ? null : tuple.getValueNode(), entry.getValue(), child, comments);
                if (tuple == null) addComment(keyNode, comments.get(child));
                next.add(new NodeTuple(keyNode, childNode));
            }
            boolean unchanged = next.size() == node.getValue().size();
            for (int i = 0; unchanged && i < next.size(); i++)
                unchanged = next.get(i).getKeyNode() == node.getValue().get(i).getKeyNode()
                        && next.get(i).getValueNode() == node.getValue().get(i).getValueNode();
            DumperOptions.FlowStyle style = node.getFlowStyle();
            // SnakeYaml 2.4 对含前置注释的 flow 集合会丢失缩进；保留注释，改用 block 表示。
            if (style == DumperOptions.FlowStyle.FLOW && hasComments(node)) style = DumperOptions.FlowStyle.BLOCK;
            result = unchanged && style == node.getFlowStyle() && !(style == DumperOptions.FlowStyle.BLOCK && hasInlineComments(node))
                    ? node : new MappingNode(Tag.MAP, next, style);
        } else if (value instanceof List) {
            SequenceNode node = old instanceof SequenceNode ? (SequenceNode) old : new SequenceNode(Tag.SEQ, new ArrayList<Node>(), DumperOptions.FlowStyle.BLOCK);
            List<Node> next = new ArrayList<>();
            List<?> input = (List<?>) value;
            for (int i = 0; i < input.size(); i++) next.add(merge(i < node.getValue().size() ? node.getValue().get(i) : null, input.get(i), path + "[" + i + "]", comments));
            boolean unchanged = next.size() == node.getValue().size();
            for (int i = 0; unchanged && i < next.size(); i++) unchanged = next.get(i) == node.getValue().get(i);
            DumperOptions.FlowStyle style = node.getFlowStyle();
            if (style == DumperOptions.FlowStyle.FLOW && hasComments(node)) style = DumperOptions.FlowStyle.BLOCK;
            result = unchanged && style == node.getFlowStyle() && !(style == DumperOptions.FlowStyle.BLOCK && hasInlineComments(node))
                    ? node : new SequenceNode(Tag.SEQ, next, style);
        } else {
            Tag tag;
            String text;
            if (value == null) { tag = Tag.NULL; text = "null"; }
            else if (value instanceof String) { tag = Tag.STR; text = (String) value; }
            else if (value instanceof Boolean) { tag = Tag.BOOL; text = value.toString(); }
            else if (value instanceof Integer || value instanceof Long || value instanceof BigInteger) { tag = Tag.INT; text = value.toString(); }
            else if (value instanceof Double || value instanceof Float || value instanceof BigDecimal) {
                if (!Double.isFinite(((Number) value).doubleValue())) throw new ConfigException(path, "codec 输出非有限数值");
                tag = Tag.FLOAT; text = value.toString();
            } else throw new ConfigException(path, "codec 输出不支持的对象树类型: " + value.getClass().getName());
            // 语义相同的标量直接复用；保留用户引号、数值写法及其注释。
            if (old instanceof ScalarNode && tag.equals(old.getTag()) && equivalent(old, value)) return old;
            result = scalar(tag, text);
        }
        if (old != null && old != result) {
            result.setBlockComments(old.getBlockComments());
            result.setInLineComments(old.getInLineComments());
            result.setEndComments(old.getEndComments());
            boolean blockContainer = result instanceof MappingNode && ((MappingNode) result).getFlowStyle() == DumperOptions.FlowStyle.BLOCK
                    || result instanceof SequenceNode && ((SequenceNode) result).getFlowStyle() == DumperOptions.FlowStyle.BLOCK;
            if (blockContainer && hasInlineComments(old)) {
                // Serializer 在集合结束后发 IN_LINE；Emitter 的 block 集合结束态只接受
                // 下个 NodeEvent，不能消费这个事件。将说明转为本集合前的 BLOCK 注释，
                // 保留文本和所属字段/列表项，不把说明迁到文档根或丢弃。
                List<CommentLine> nearby = new ArrayList<>();
                if (old.getBlockComments() != null) nearby.addAll(old.getBlockComments());
                for (CommentLine comment : old.getInLineComments())
                    nearby.add(new CommentLine(comment.getStartMark(), comment.getEndMark(), comment.getValue(), CommentType.BLOCK));
                result.setBlockComments(nearby);
                result.setInLineComments(Collections.emptyList());
            }
        }
        return result;
    }
    private static boolean hasInlineComments(Node node) {
        return node.getInLineComments() != null && !node.getInLineComments().isEmpty();
    }
    private static boolean hasComments(Node node) {
        return node.getBlockComments() != null && !node.getBlockComments().isEmpty()
                || node.getInLineComments() != null && !node.getInLineComments().isEmpty()
                || node.getEndComments() != null && !node.getEndComments().isEmpty();
    }
    private boolean equivalent(Node node, Object value) {
        Object previous = readNode(node, "", Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>()));
        if (previous instanceof Number && value instanceof Number)
            return new BigDecimal(previous.toString()).compareTo(new BigDecimal(value.toString())) == 0;
        return Objects.equals(previous, value);
    }
    private static void addComment(Node node, String text) {
        if (text == null || text.isEmpty()) return;
        List<CommentLine> lines = new ArrayList<>();
        for (String line : text.split("\n", -1)) lines.add(new CommentLine(null, null, " " + line, CommentType.BLOCK));
        node.setBlockComments(lines);
    }
}
