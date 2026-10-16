package club.heiqi.uilib.ui.scene.text.markdown;

import club.heiqi.uilib.ui.scene.text.DocumentContent;
import club.heiqi.uilib.ui.scene.text.ParagraphContent;
import club.heiqi.uilib.ui.scene.text.StyledText;
import club.heiqi.uilib.ui.scene.text.TableContent;
import club.heiqi.uilib.ui.scene.text.TextContent;
import club.heiqi.uilib.ui.scene.text.TextInput;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyleOverride;
import club.heiqi.uilib.ui.scene.text.TextStyleOverrideSpan;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.composer.Composer;
import org.yaml.snakeyaml.emitter.Emitter;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.events.DocumentStartEvent;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.parser.ParserImpl;
import org.yaml.snakeyaml.reader.ReaderException;
import org.yaml.snakeyaml.reader.StreamReader;
import org.yaml.snakeyaml.resolver.Resolver;
import org.yaml.snakeyaml.scanner.ScannerImpl;
import org.yaml.snakeyaml.serializer.Serializer;
import org.yaml.snakeyaml.tokens.AliasToken;
import org.yaml.snakeyaml.tokens.Token;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 前缀识别及 YAML 到共同内容的转换；图值与显示见 specs/content/uilib-markdown-spec.md 第 3 节。 */
final class FrontMatter {
    final String masked;
    final List<DocumentContent.Block> blocks = new ArrayList<>();
    private final TextInput input;
    private final String yaml;
    private final int contentStart, prefixEnd;

    private FrontMatter(
            TextInput input,
            int contentStart,
            int contentEnd,
            int prefixEnd,
            List<TextLayout.Diagnostic> diagnostics) {
        this.input = input;
        this.contentStart = contentStart;
        this.prefixEnd = prefixEnd;
        // yaml 2.9 在 EOF 仍保留 block scalar 的 clip LF；SnakeYAML 需要真实终换行。
        // 闭分隔前的换行原本就在输入中，保留它用于解析与 Mark，绝不为正文挪动坐标。
        yaml = input.text.substring(contentStart, contentEnd).replace('\0', '\ufffd');
        char[] mask = input.text.toCharArray();
        // 两次成熟 Markdown 解析共用同宽 mask，源码和所有行结束符保持原坐标。
        for (int i = 0; i < prefixEnd; i++) if (mask[i] != '\r' && mask[i] != '\n') mask[i] = ' ';
        masked = new String(mask);
        try {
            LoaderOptions options = new LoaderOptions();
            options.setTagInspector(tag -> true);
            boolean legacy = false;
            Deque<AliasToken> aliases = new ArrayDeque<>();
            // 指令和 alias 位置来自成熟事件/Token；不自行解析 YAML 字符词法。
            ParserImpl probe = new ParserImpl(new StreamReader(yaml), options);
            while (probe.peekEvent() != null) {
                Event event = probe.getEvent();
                if (event instanceof DocumentStartEvent)
                    legacy =
                            ((DocumentStartEvent) event).getVersion() == DumperOptions.Version.V1_1;
            }
            ScannerImpl scanner = new ScannerImpl(new StreamReader(yaml), options);
            while (scanner.checkToken()) {
                Token token = scanner.getToken();
                if (token instanceof AliasToken) aliases.add((AliasToken) token);
            }
            Node root =
                    new Composer(
                                    new ParserImpl(new StreamReader(yaml), options),
                                    legacy ? new LegacyResolver() : new CoreResolver(),
                                    options)
                            .getSingleNode();
            if (root == null) return;
            Value value = new GraphValues(legacy, aliases).convert(root);
            DocumentContent.Block table = new ValueDisplay().table(value);
            if (table != null) blocks.add(table);
        } catch (MarkedYAMLException failure) {
            int start = position(failure.getProblemMark());
            fail(
                    diagnostics,
                    start,
                    Math.min(
                            input.text.length(),
                            start
                                    + (start < input.text.length()
                                            ? Character.charCount(input.text.codePointAt(start))
                                            : 0)),
                    failure.getProblem());
        } catch (ReaderException failure) {
            int start = position(failure.getPosition());
            int end =
                    start < input.text.length()
                            ? Math.min(
                                    prefixEnd,
                                    start + Character.charCount(input.text.codePointAt(start)))
                            : start;
            // 固定库容忍的控制字符可能超出成熟 Java 解析器边界；显式报告差异，保留原文与后文。
            fail(diagnostics, start, end, "成熟 YAML 解析器无法接受该字符：" + failure.getMessage());
        } catch (YAMLException failure) {
            // 无 Mark 的成熟资源/解析限制仍归输入诊断；不吞任意程序异常。
            fail(diagnostics, 0, prefixEnd, "成熟 YAML 解析器无法处理该前缀：" + failure.getMessage());
        } catch (ConversionFailure failure) {
            fail(diagnostics, start(failure.node), end(failure.node), failure.getMessage());
        }
    }

    static FrontMatter read(TextInput input, int[] lines, List<TextLayout.Diagnostic> diagnostics) {
        if (lines.length < 2 || !marker(input.text, 0, lines[1])) return null;
        for (int i = 1; i < lines.length; i++) {
            int end = i + 1 < lines.length ? lines[i + 1] : input.text.length();
            if (marker(input.text, lines[i], end))
                return new FrontMatter(input, lines[1], lines[i], end, diagnostics);
        }
        return null;
    }

    private static boolean marker(String raw, int start, int end) {
        while (end > start && whitespace(raw.charAt(end - 1))) end--;
        return end - start == 3 && raw.startsWith("---", start);
    }

    private static boolean whitespace(char ch) {
        // 对齐 ECMAScript 的尾部空白集合，BOM 只在尾部算空白，首行仍必须从 '-' 开始。
        return ch >= '\t' && ch <= '\r'
                || ch == ' '
                || ch == '\u00a0'
                || ch == '\u1680'
                || ch >= '\u2000' && ch <= '\u200a'
                || ch == '\u2028'
                || ch == '\u2029'
                || ch == '\u202f'
                || ch == '\u205f'
                || ch == '\u3000'
                || ch == '\ufeff';
    }

    /** 消费 Composer 节点、schema 与顺序 alias token，只归一化值图及其出现点。 */
    private final class GraphValues {
        private final boolean legacy;
        private final Resolver resolver;
        private final Deque<AliasToken> aliases;
        private final IdentityHashMap<Node, Value> converted = new IdentityHashMap<>();

        GraphValues(boolean legacy, Deque<AliasToken> aliases) {
            this.legacy = legacy;
            this.resolver = legacy ? new LegacyResolver() : new CoreResolver();
            this.aliases = aliases;
        }

        private Value convert(Node node) {
            Value previous = converted.get(node);
            if (previous != null) {
                AliasToken token = aliases.pollFirst();
                if (token == null) throw new IllegalStateException("成熟 YAML alias 来源与 compose 树不一致");
                return previous.at(position(token.getStartMark()), position(token.getEndMark()));
            }
            Value value = new Value(node, null, start(node), end(node));
            converted.put(node, value);
            // 先登记容器占位，再遍历子节点；循环引用与后续 alias 共享同一 data 身份。
            if (node instanceof MappingNode) mapping((MappingNode) node, value);
            else if (node instanceof SequenceNode) {
                List<Value> items = new ArrayList<>();
                value.data = items;
                for (Node child : ((SequenceNode) node).getValue()) items.add(convert(child));
            } else if (node instanceof ScalarNode) value.data = scalar((ScalarNode) node);
            else throw new ConversionFailure(node, "无法转换的 YAML 节点");
            return value;
        }

        private void mapping(MappingNode node, Value value) {
            if (Tag.SET.equals(node.getTag())) {
                Set<Value> members = new LinkedHashSet<>();
                value.data = members;
                for (NodeTuple tuple : node.getValue()) {
                    Value key = convert(tuple.getKeyNode());
                    members.add(key);
                    Value member = convert(tuple.getValueNode());
                    if (member.data != null)
                        throw new ConversionFailure(member.node, "YAML set 成员必须具有空值");
                }
                return;
            }
            Map<String, Entry> entries = new LinkedHashMap<>();
            value.data = entries;
            Set<Object> keys = new HashSet<>();
            for (NodeTuple tuple : node.getValue()) {
                Value key = convert(tuple.getKeyNode());
                // YAML 重复以解析值类型判定，随后 JS 属性名碰撞采用最后值。
                Object identity =
                        key.data instanceof Double && (Double) key.data == 0 ? 0.0 : key.data;
                if (!(identity instanceof Map || identity instanceof List) && !keys.add(identity))
                    throw new ConversionFailure(key.node, "重复 YAML 映射键");
                String label = ValueFormat.keyLabel(key);
                Value child = convert(tuple.getValueNode());
                // 先按原树顺序转换完 alias occurrence，再归一化成熟节点的 merge 语义。
                // 默认 core 的 << 是普通字符串；只有 1.1 Resolver 给出的 MERGE tag 生效。
                if (legacy && Tag.MERGE.equals(key.node.getTag())) merge(entries, child);
                else entries.put(label, new Entry(label, key, child));
            }
        }

        @SuppressWarnings("unchecked")
        private void merge(Map<String, Entry> target, Value value) {
            if (value.data instanceof Map) {
                // YAML merge 序列的第一张 map 优先；已有显式键保留，后续显式键仍正常覆盖。
                for (Entry entry : new ArrayList<>(((Map<String, Entry>) value.data).values()))
                    target.putIfAbsent(entry.label, entry);
            } else if (value.data instanceof List) {
                for (Value item : (List<Value>) value.data) {
                    if (!(item.data instanceof Map))
                        throw new ConversionFailure(item.node, "YAML merge 序列成员必须为映射");
                    merge(target, item);
                }
            } else throw new ConversionFailure(value.node, "YAML merge 值必须为映射或映射序列");
        }

        private Object scalar(ScalarNode node) {
            String text = node.getValue();
            Tag tag = node.getTag();
            // 固定库的显式基础 tag 仍须匹配 schema test；未匹配时 warning 后保留字符串。
            // 校验走成熟 Resolver，同一规则同时覆盖隐式值、显式 tag 与根空值。
            if ((Tag.NULL.equals(tag)
                            || Tag.BOOL.equals(tag)
                            || Tag.INT.equals(tag)
                            || Tag.FLOAT.equals(tag))
                    && !tag.equals(resolver.resolve(NodeId.scalar, text, true))) return text;
            if (Tag.STR.equals(tag)) return text;
            if (Tag.NULL.equals(tag)) return null;
            if (Tag.BOOL.equals(tag))
                return legacy
                        ? text.matches("y|Y|yes|Yes|YES|true|True|TRUE|on|On|ON")
                        : text.equalsIgnoreCase("true");
            if (Tag.INT.equals(tag)) {
                String digits = legacy ? text.replace("_", "") : text;
                boolean negative = digits.startsWith("-");
                if (digits.startsWith("-") || digits.startsWith("+")) digits = digits.substring(1);
                int radix = 10;
                if (digits.startsWith("0x")) {
                    radix = 16;
                    digits = digits.substring(2);
                } else if (digits.startsWith("0o")) {
                    radix = 8;
                    digits = digits.substring(2);
                } else if (legacy && digits.startsWith("0b")) {
                    radix = 2;
                    digits = digits.substring(2);
                } else if (legacy && digits.length() > 1 && digits.startsWith("0")) {
                    radix = 8;
                    digits = digits.substring(1);
                }
                if (digits.contains(":")) return sexagesimal(text);
                try {
                    double number = new BigInteger(digits, radix).doubleValue();
                    return negative ? -number : number;
                } catch (NumberFormatException failure) {
                    throw new ConversionFailure(node, "非法 YAML 整数");
                }
            }
            if (Tag.FLOAT.equals(tag)) {
                if (text.toLowerCase(Locale.ROOT).endsWith("nan")) return Double.NaN;
                if (text.toLowerCase(Locale.ROOT).endsWith("inf"))
                    return text.startsWith("-") ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
                if (legacy && text.contains(":")) return sexagesimal(text);
                try {
                    return Double.parseDouble(legacy ? text.replace("_", "") : text);
                } catch (NumberFormatException failure) {
                    throw new ConversionFailure(node, "非法 YAML 浮点数");
                }
            }
            if (Tag.TIMESTAMP.equals(tag)) return timestamp(node);
            if (Tag.BINARY.equals(tag)) {
                byte[] data;
                try {
                    data = Base64.getMimeDecoder().decode(text);
                } catch (IllegalArgumentException failure) {
                    throw new ConversionFailure(node, "非法 YAML binary");
                }
                Binary buffer = new Binary();
                Value type = new Value(node, "Buffer", start(node), end(node));
                buffer.put("type", new Entry("type", type, type));
                List<Value> bytes = buffer.bytes;
                for (byte item : data)
                    bytes.add(new Value(node, (double) (item & 255), start(node), end(node)));
                Value values = new Value(node, bytes, start(node), end(node));
                buffer.put("data", new Entry("data", values, values));
                return buffer;
            }
            // 固定库对未知 tag 发 warning 并保留其基础值，不调用 Java 对象构造器。
            return text;
        }

        private double sexagesimal(String text) {
            boolean negative = text.startsWith("-");
            String digits = text.replace("_", "");
            if (digits.startsWith("-") || digits.startsWith("+")) digits = digits.substring(1);
            double result = 0;
            for (String part : digits.split(":")) result = result * 60 + Double.parseDouble(part);
            return negative ? -result : result;
        }

        private Timestamp timestamp(ScalarNode node) {
            Matcher match =
                    Pattern.compile(
                                    "^([0-9]{4})-([0-9]{1,2})-([0-9]{1,2})(?:(?:t|T|[ \\t]+)([0-9]{1,2}):([0-9]{1,2}):([0-9]{1,2})(\\.[0-9]+)?(?:[ \\t]*(Z|[-+][012]?[0-9](?::[0-9]{2})?))?)?$")
                            .matcher(node.getValue());
            if (!match.matches()) throw new ConversionFailure(node, "非法 YAML timestamp");
            int year = Integer.parseInt(match.group(1));
            if (year < 100) year += 1900;
            LocalDateTime date =
                    LocalDate.of(year, 1, 1)
                            .atStartOfDay()
                            .plusMonths(Long.parseLong(match.group(2)) - 1)
                            .plusDays(Long.parseLong(match.group(3)) - 1);
            if (match.group(4) != null)
                date =
                        date.plusHours(Long.parseLong(match.group(4)))
                                .plusMinutes(Long.parseLong(match.group(5)))
                                .plusSeconds(Long.parseLong(match.group(6)));
            long millis =
                    match.group(7) == null
                            ? 0
                            : Long.parseLong((match.group(7).substring(1) + "00").substring(0, 3));
            Instant instant = date.toInstant(ZoneOffset.UTC).plusMillis(millis);
            String zone = match.group(8);
            if (zone != null && !zone.equals("Z")) {
                double offset = sexagesimal(zone);
                // 有冒号的偏移已是分钟；无冒号才是小时，不能按数值大小猜单位。
                if (zone.indexOf(':') < 0) offset *= 60;
                instant = instant.minusMillis((long) (offset * 60000));
            }
            return new Timestamp(
                    DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'")
                            .withZone(ZoneOffset.UTC)
                            .format(instant));
        }
    }

    /** 只把已归一化的值与出现范围转换为共同内容；列表的 active 是当前展开路径。 */
    private final class ValueDisplay {
        private DocumentContent.Block table(Value value) {
            if (value.data == null) return null;
            List<TableContent.Row> rows = new ArrayList<>();
            if (value.data instanceof Binary) {
                Binary binary = (Binary) value.data;
                for (int i = 0; i < binary.bytes.size(); i++)
                    rows.add(row(Integer.toString(i), value, binary.bytes.get(i)));
            } else if (value.data instanceof Map) {
                for (Entry entry : ValueFormat.entries(value))
                    rows.add(row(entry.label, entry.key, entry.value));
            } else if (!(value.data instanceof Set)) rows.add(row("", value, value));
            return rows.isEmpty()
                    ? null
                    : DocumentContent.Block.table(
                            new TableContent(rows), sourceStart(0), sourceEnd(prefixEnd));
        }

        private TableContent.Row row(String label, Value key, Value value) {
            TableContent.Cell heading =
                    new TableContent.Cell(
                            Collections.singletonList(paragraph(label, key.start, key.end, false)),
                            TableContent.Alignment.DEFAULT,
                            true,
                            sourceStart(key.start),
                            sourceEnd(key.end));
            TableContent.Cell cell =
                    new TableContent.Cell(
                            display(value, Collections.newSetFromMap(new IdentityHashMap<>())),
                            TableContent.Alignment.DEFAULT,
                            false,
                            sourceStart(value.start),
                            sourceEnd(value.end));
            return new TableContent.Row(
                    Arrays.asList(heading, cell),
                    sourceStart(Math.min(key.start, value.start)),
                    sourceEnd(Math.max(key.end, value.end)));
        }

        @SuppressWarnings("unchecked")
        private List<DocumentContent.Block> display(Value value, Set<Object> active) {
            if (value.data == null) return Collections.emptyList();
            if (value.data instanceof List) {
                List<Value> items = (List<Value>) value.data;
                if (items.isEmpty()) return Collections.emptyList();
                if (!active.add(items)) throw new ConversionFailure(value.node, "循环 YAML 序列无法展开为有限列表");
                List<DocumentContent.Block> children = new ArrayList<>();
                for (Value item : items) {
                    // alias 的 cell 与生成内容共享引用自身来源，不倒退到定义位置。
                    Value occurrence =
                            value.start != start(value.node)
                                    ? item.at(value.start, value.end)
                                    : item;
                    children.add(
                            DocumentContent.Block.listItem(
                                    display(occurrence, active),
                                    sourceStart(occurrence.start),
                                    sourceEnd(occurrence.end)));
                }
                active.remove(items);
                return Collections.singletonList(
                        DocumentContent.Block.list(
                                children,
                                false,
                                1,
                                true,
                                sourceStart(value.start),
                                sourceEnd(value.end)));
            }
            boolean object =
                    value.data instanceof Map
                            || value.data instanceof Set
                            || value.data instanceof Timestamp;
            String text = object ? ValueFormat.dump(value, false) : ValueFormat.scalarText(value.data);
            return text.isEmpty()
                    ? Collections.emptyList()
                    : Collections.singletonList(paragraph(text, value.start, value.end, object));
        }
    }

    /** 值的属性顺序、JS 标量文本与 canonical YAML；不消费 alias token 或内容来源。 */
    private static final class ValueFormat {
        private static String keyLabel(Value key) {
            return key.data instanceof Map || key.data instanceof List
                    ? dump(key, true)
                    : scalarText(key.data);
        }

        @SuppressWarnings("unchecked")
        private static List<Entry> entries(Value value) {
            List<Entry> entries = new ArrayList<>(((Map<String, Entry>) value.data).values());
            entries.sort(
                    (a, b) -> {
                        long x = index(a.label), y = index(b.label);
                        return x < 0 ? y < 0 ? 0 : 1 : y < 0 ? -1 : Long.compare(x, y);
                    });
            return entries;
        }

        private static long index(String text) {
            if (!text.matches("0|[1-9][0-9]{0,9}")) return -1;
            long index = Long.parseLong(text);
            return index < 4294967295L ? index : -1;
        }

        private static String scalarText(Object value) {
            return value == null
                    ? ""
                    : value instanceof Double ? number((Double) value) : value.toString();
        }

        private static String dump(Value value, boolean flow) {
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(
                    flow ? DumperOptions.FlowStyle.FLOW : DumperOptions.FlowStyle.BLOCK);
            options.setIndent(2);
            // Set stringify 的根是序列，根列表不缩进；对象内部列表仍采用固定参照的两格缩进。
            options.setIndicatorIndent(value.data instanceof Set || value.data instanceof List ? 0 : 2);
            options.setIndentWithIndicator(true);
            options.setWidth(80);
            options.setSplitLines(false);
            final int[] nextAnchor = {0};
            options.setAnchorGenerator(node -> "a" + ++nextAnchor[0]);
            StringWriter writer = new StringWriter();
            try {
                Serializer serializer =
                        new Serializer(new Emitter(writer, options), new CoreResolver(), options, null);
                serializer.open();
                serializer.serialize(canonical(value, new IdentityHashMap<>(), flow));
                serializer.close();
            } catch (IOException failure) {
                throw new IllegalStateException("内存 YAML 序列化失败", failure);
            }
            String result = writer.toString();
            int end = result.length();
            while (end > 0 && whitespace(result.charAt(end - 1))) end--;
            result = result.substring(0, end);
            if (flow && result.startsWith("[") && result.endsWith("]") && result.length() > 2)
                result = "[ " + result.substring(1, result.length() - 1) + " ]";
            if (flow && result.startsWith("{") && result.endsWith("}") && result.length() > 2)
                result = "{ " + result.substring(1, result.length() - 1) + " }";
            return result;
        }

        @SuppressWarnings("unchecked")
        private static Node canonical(Value value, IdentityHashMap<Object, Node> seen, boolean flow) {
            Object data = value.data;
            if (data instanceof Map || data instanceof List) {
                // canonical 的 seen 按共享 data 建图；与阅读列表的当前 active 路径分开。
                Node previous = seen.get(data);
                if (previous != null) return previous;
                DumperOptions.FlowStyle style =
                        flow ? DumperOptions.FlowStyle.FLOW : DumperOptions.FlowStyle.BLOCK;
                if (data instanceof Map) {
                    List<NodeTuple> tuples = new ArrayList<>();
                    MappingNode result = new MappingNode(Tag.MAP, tuples, style);
                    seen.put(data, result);
                    for (Entry entry : entries(value))
                        tuples.add(
                                new NodeTuple(
                                        stringNode(entry.label, true, flow),
                                        canonical(entry.value, seen, flow)));
                    return result;
                }
                List<Node> items = new ArrayList<>();
                SequenceNode result = new SequenceNode(Tag.SEQ, items, style);
                seen.put(data, result);
                for (Value child : (List<Value>) data) items.add(canonical(child, seen, flow));
                return result;
            }
            if (data instanceof Set) {
                Node previous = seen.get(data);
                if (previous != null) return previous;
                List<Node> items = new ArrayList<>();
                SequenceNode result =
                        new SequenceNode(
                                Tag.SEQ,
                                items,
                                flow ? DumperOptions.FlowStyle.FLOW : DumperOptions.FlowStyle.BLOCK);
                seen.put(data, result);
                for (Value member : (Set<Value>) data) items.add(canonical(member, seen, flow));
                return result;
            }
            if (data instanceof Timestamp)
                return new ScalarNode(
                        Tag.STR, data.toString(), null, null, DumperOptions.ScalarStyle.PLAIN);
            if (data instanceof Double) {
                double number = (Double) data;
                String text =
                        Double.isNaN(number)
                                ? ".nan"
                                : Double.isInfinite(number)
                                        ? number < 0 ? "-.inf" : ".inf"
                                        : number(number);
                return new ScalarNode(
                        new CoreResolver().resolve(NodeId.scalar, text, true),
                        text,
                        null,
                        null,
                        DumperOptions.ScalarStyle.PLAIN);
            }
            if (data instanceof Boolean || data == null)
                return new ScalarNode(
                        data == null ? Tag.NULL : Tag.BOOL,
                        data == null ? "null" : data.toString(),
                        null,
                        null,
                        DumperOptions.ScalarStyle.PLAIN);
            return stringNode(data.toString(), false, flow);
        }

        private static Node stringNode(String text, boolean key, boolean flow) {
            DumperOptions.ScalarStyle style = DumperOptions.ScalarStyle.PLAIN;
            if (!key && !flow && text.contains("\n")) style = DumperOptions.ScalarStyle.LITERAL;
            else if (text.isEmpty()
                    || text.matches(
                            "(?s)^[\\n\\t ,\\[\\]{}#&*!|>'\"%@`].*|^[?-]$|^[?-][ \\t].*|.*[\\n:][ \\t].*|.*[ \\t]\\n.*|.*[\\n\\t ]#.*|.*[\\n\\t :]$")
                    || key && text.contains("\n")
                    || flow && text.matches("(?s).*[\\[\\]{},].*")
                    || !Tag.STR.equals(new CoreResolver().resolve(NodeId.scalar, text, true)))
                style = DumperOptions.ScalarStyle.DOUBLE_QUOTED;
            return new ScalarNode(Tag.STR, text, null, null, style);
        }

        private static String number(double value) {
            if (Double.isNaN(value)) return "NaN";
            if (Double.isInfinite(value)) return value < 0 ? "-Infinity" : "Infinity";
            if (value == 0) return "0";
            // Java8 Double.toString 的最短表示不总等于 JS；先从精确二进制值取最近十进制。
            // 最多 17 位足以 round-trip binary64；最短候选的中点沿 ties-to-even 选取。
            BigDecimal exact = new BigDecimal(value), decimal = exact;
            for (int precision = 1; precision <= 17; precision++) {
                BigDecimal candidate = exact.round(new MathContext(precision, RoundingMode.HALF_EVEN));
                if (candidate.doubleValue() == value) {
                    decimal = candidate.stripTrailingZeros();
                    break;
                }
            }
            if (Math.abs(value) >= 1e-6 && Math.abs(value) < 1e21) return decimal.toPlainString();
            return decimal.toString().replace("E", "e");
        }
    }

    private static final class Value {
        final Node node;
        // 容器 data 是图身份；start/end 只表示本次出现，不因 alias 复制容器。
        Object data;
        final int start, end;

        Value(Node node, Object data, int start, int end) {
            this.node = node;
            this.data = data;
            this.start = start;
            this.end = end;
        }

        Value at(int start, int end) {
            return new Value(node, data, start, end);
        }
    }

    private static final class Entry {
        final String label;
        final Value key, value;

        Entry(String label, Value key, Value value) {
            this.label = label;
            this.key = key;
            this.value = value;
        }
    }

    /** 同一 binary 值在 Object.entries 根表与嵌套 stringify 中有不同 JS 对象视图。 */
    private static final class Binary extends LinkedHashMap<String, Entry> {
        final List<Value> bytes = new ArrayList<>();
    }

    private static final class Timestamp {
        final String iso;

        Timestamp(String iso) {
            this.iso = iso;
        }

        @Override
        public String toString() {
            return iso;
        }
    }

    private DocumentContent.Block paragraph(String text, int start, int end, boolean mono) {
        int[] starts = new int[text.length()], ends = new int[text.length()];
        // 生成值允许完整范围共享；每个 cell 独立映射，不按显示字符串回搜来源。
        Arrays.fill(starts, start);
        Arrays.fill(ends, end);
        List<TextInput.SourceSegment> gaps = new ArrayList<>();
        if (start > 0) gaps.add(new TextInput.SourceSegment(0, 0, 0, start, true));
        if (end < input.text.length())
            gaps.add(
                    new TextInput.SourceSegment(
                            text.length(), text.length(), end, input.text.length(), true));
        if (text.isEmpty() && end > start)
            gaps.add(new TextInput.SourceSegment(0, 0, start, end, true));
        TextInput mapped = TextInput.fromMapped(input, text, starts, ends, gaps);
        TextStyleOverride style =
                mono ? MarkdownStyleIntent.INHERIT.mono().declaration : TextStyleOverride.INHERIT;
        List<TextStyleOverrideSpan> spans =
                text.isEmpty()
                        ? Collections.emptyList()
                        : Collections.singletonList(
                                new TextStyleOverrideSpan(0, text.length(), style));
        StyledText styled =
                new StyledText(mapped, input.text, spans, style, Collections.emptyList());
        TextContent content =
                TextContent.paragraph(
                        new ParagraphContent(
                                styled, Collections.emptyList(), Collections.emptyList()));
        return DocumentContent.Block.paragraph(content, sourceStart(start), sourceEnd(end));
    }

    private void fail(List<TextLayout.Diagnostic> diagnostics, int start, int end, String message) {
        blocks.clear();
        diagnostics.add(
                new TextLayout.Diagnostic(
                        TextLayout.Diagnostic.Kind.INVALID_INPUT,
                        sourceStart(start),
                        sourceEnd(end),
                        "Frontmatter YAML: " + message));
        blocks.add(paragraph("Frontmatter 解析失败：" + message, 0, prefixEnd, false));
        blocks.add(paragraph(input.text.substring(0, prefixEnd), 0, prefixEnd, true));
    }

    private int position(Mark mark) {
        return mark == null ? contentStart : position(mark.getIndex());
    }

    private int position(int codePointIndex) {
        return contentStart
                + yaml.offsetByCodePoints(
                        0,
                        Math.max(
                                0,
                                Math.min(codePointIndex, yaml.codePointCount(0, yaml.length()))));
    }

    private int start(Node node) {
        return position(node.getStartMark());
    }

    private int end(Node node) {
        return position(node.getEndMark());
    }

    private int sourceStart(int offset) {
        return input.sourceRight(offset);
    }

    private int sourceEnd(int offset) {
        return input.sourceLeft(offset);
    }

    private static final class ConversionFailure extends RuntimeException {
        final Node node;

        ConversionFailure(Node node, String message) {
            super(message);
            this.node = node;
        }
    }

    /** SnakeYAML 的 1.1 bool 少了 yaml 2.9 的短形式；只在成熟 schema 接缝补齐正式规则。 */
    private static final class LegacyResolver extends Resolver {
        @Override
        protected void addImplicitResolvers() {
            super.addImplicitResolvers();
            addImplicitResolver(Tag.BOOL, Pattern.compile("^(?:y|Y|n|N)$"), "yYnN");
        }
    }

    /** 使用成熟 Resolver 接缝替换 schema；正则来自固定 yaml 2.9 core，而非逐字面补丁。 */
    private static final class CoreResolver extends Resolver {
        @Override
        protected void addImplicitResolvers() {
            addImplicitResolver(
                    Tag.BOOL, Pattern.compile("^(?:[Tt]rue|TRUE|[Ff]alse|FALSE)$"), "tTfF");
            addImplicitResolver(
                    Tag.INT,
                    Pattern.compile("^(?:0o[0-7]+|0x[0-9a-fA-F]+|[-+]?[0-9]+)$"),
                    "-+0123456789");
            addImplicitResolver(
                    Tag.FLOAT,
                    Pattern.compile(
                            "^(?:[-+]?\\.(?:inf|Inf|INF)|\\.(?:nan|NaN|NAN)|[-+]?(?:\\.[0-9]+|[0-9]+(?:\\.[0-9]*)?)[eE][-+]?[0-9]+|[-+]?(?:\\.[0-9]+|[0-9]+\\.[0-9]*))$"),
                    "-+0123456789.");
            addImplicitResolver(Tag.NULL, Pattern.compile("^(?:~|[Nn]ull|NULL)?$"), "~nN\0");
        }
    }
}
