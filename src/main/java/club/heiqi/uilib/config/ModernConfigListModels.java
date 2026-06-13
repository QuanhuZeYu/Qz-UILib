package club.heiqi.uilib.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import club.heiqi.config.ConfigException;
import club.heiqi.config.ConfigNode;

/**
 * 现代配置列表模板的形态分析与基础值转换工具。
 */
final class ModernConfigListModels {

    private ModernConfigListModels() {
    }

    /**
     * 分析列表节点可使用的 Batch 2 模板。
     *
     * @param node 配置节点
     * @return 列表分析结果
     */
    static ListAnalysis analyze(ConfigNode node) {
        if (node == null || node.getType() != ConfigNode.NodeType.LIST) {
            return ListAnalysis.unsupported("当前节点不是列表。");
        }
        List<ConfigNode> items = node.asList();
        if (items == null || items.isEmpty()) {
            return ListAnalysis.simple(ValueKind.STRING);
        }

        ListAnalysis tableAnalysis = analyzeTable(items);
        if (tableAnalysis.getTemplateKind() == TemplateKind.TABLE
                || allItemsAreMaps(items)) {
            return tableAnalysis;
        }

        ValueKind primitiveKind = resolvePrimitiveListKind(items);
        if (primitiveKind != null) {
            return ListAnalysis.simple(primitiveKind);
        }
        return ListAnalysis.unsupported("混合列表暂不编辑：当前批次仅支持 primitive list 或稳定列 map list。");
    }

    private static ListAnalysis analyzeTable(List<ConfigNode> items) {
        if (!allItemsAreMaps(items)) {
            return ListAnalysis.unsupported("混合列表暂不表格化：所有元素都需要是 map。");
        }
        List<String> columns = null;
        Map<String, ValueKind> columnKinds = new LinkedHashMap<String, ValueKind>();
        for (ConfigNode item : items) {
            Map<String, ConfigNode> row = item.asMap();
            if (row == null || row.isEmpty()) {
                return ListAnalysis.unsupported("表格列表至少需要一个稳定列。");
            }
            List<String> rowColumns = new ArrayList<String>(row.keySet());
            Collections.sort(rowColumns);
            if (columns == null) {
                columns = rowColumns;
            } else if (!columns.equals(rowColumns)) {
                return ListAnalysis.unsupported("表格列集合不稳定，暂不表格化。");
            }
            for (String column : rowColumns) {
                ConfigNode cell = row.get(column);
                ValueKind cellKind = ValueKind.fromNode(cell);
                if (cellKind == null) {
                    return ListAnalysis.unsupported("表格单元格包含嵌套结构，当前批次不内联编辑。");
                }
                if (cellKind == ValueKind.NULL) {
                    continue;
                }
                ValueKind previousKind = columnKinds.get(column);
                if (previousKind == null || previousKind == ValueKind.NULL) {
                    columnKinds.put(column, cellKind);
                } else if (previousKind != cellKind) {
                    return ListAnalysis.unsupported("表格列类型不稳定，暂不表格化。");
                }
            }
        }
        if (columns == null || columns.isEmpty()) {
            return ListAnalysis.unsupported("表格列表至少需要一个稳定列。");
        }
        for (String column : columns) {
            if (!columnKinds.containsKey(column) || columnKinds.get(column) == ValueKind.NULL) {
                columnKinds.put(column, ValueKind.STRING);
            }
        }
        return ListAnalysis.table(columns, columnKinds);
    }

    private static boolean allItemsAreMaps(List<ConfigNode> items) {
        for (ConfigNode item : items) {
            if (item == null || item.getType() != ConfigNode.NodeType.MAP) {
                return false;
            }
        }
        return !items.isEmpty();
    }

    private static ValueKind resolvePrimitiveListKind(List<ConfigNode> items) {
        ValueKind resolvedKind = null;
        for (ConfigNode item : items) {
            ValueKind itemKind = ValueKind.fromNode(item);
            if (itemKind == null) {
                return null;
            }
            if (itemKind == ValueKind.NULL) {
                continue;
            }
            if (resolvedKind == null || resolvedKind == ValueKind.NULL) {
                resolvedKind = itemKind;
            } else if (resolvedKind != itemKind) {
                return ValueKind.STRING;
            }
        }
        return resolvedKind == null ? ValueKind.STRING : resolvedKind;
    }

    static String formatNodeValue(ConfigNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        return node.asString("");
    }

    static Object convertNodeValue(ConfigNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.getType() == ConfigNode.NodeType.STRING) {
            return node.asString("");
        }
        if (node.getType() == ConfigNode.NodeType.BOOLEAN) {
            return Boolean.valueOf(node.asBoolean(false));
        }
        if (node.getType() == ConfigNode.NodeType.NUMBER) {
            return parseNodeNumber(node);
        }
        return node.asString("");
    }

    static Object convertRawPrimitiveValue(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        return String.valueOf(value);
    }

    static String formatRawPrimitiveValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    static ValueKind resolveRawPrimitiveKind(Object value, ValueKind fallbackKind) {
        if (value instanceof Number) {
            return ValueKind.NUMBER;
        }
        if (value instanceof Boolean) {
            return ValueKind.BOOLEAN;
        }
        if (value == null) {
            return ValueKind.NULL;
        }
        if (value instanceof String) {
            return ValueKind.STRING;
        }
        return fallbackKind == null ? ValueKind.STRING : fallbackKind;
    }

    static ParsedValue parseDraftValue(ValueKind kind, String rawText) {
        ValueKind resolvedKind = kind == null ? ValueKind.STRING : kind;
        String text = rawText == null ? "" : rawText;
        if (resolvedKind == ValueKind.NULL) {
            return text.trim().isEmpty() ? ParsedValue.ok(null) : ParsedValue.ok(text);
        }
        if (resolvedKind == ValueKind.STRING) {
            return ParsedValue.ok(text);
        }
        if (resolvedKind == ValueKind.BOOLEAN) {
            String normalized = text.trim().toLowerCase(java.util.Locale.ENGLISH);
            if ("true".equals(normalized) || "yes".equals(normalized) || "1".equals(normalized)) {
                return ParsedValue.ok(Boolean.TRUE);
            }
            if ("false".equals(normalized) || "no".equals(normalized) || "0".equals(normalized)) {
                return ParsedValue.ok(Boolean.FALSE);
            }
            return ParsedValue.error("必须是布尔值 true/false。");
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return ParsedValue.error("必须是有效数值。");
        }
        try {
            if (!trimmed.contains(".") && !trimmed.toLowerCase(java.util.Locale.ENGLISH).contains("e")) {
                return ParsedValue.ok(Long.valueOf(Long.parseLong(trimmed)));
            }
            return ParsedValue.ok(Double.valueOf(Double.parseDouble(trimmed)));
        } catch (NumberFormatException exception) {
            return ParsedValue.error("必须是有效数值。");
        }
    }

    private static Object parseNodeNumber(ConfigNode node) {
        String text = node.asString("").trim().toLowerCase(java.util.Locale.ENGLISH);
        if (!text.contains(".") && !text.contains("e")) {
            try {
                return Long.valueOf(node.asLong());
            } catch (ConfigException ignored) {
            }
        }
        return Double.valueOf(node.asDouble(0.0D));
    }

    enum TemplateKind {
        SIMPLE,
        TABLE,
        UNSUPPORTED
    }

    enum ValueKind {
        STRING,
        NUMBER,
        BOOLEAN,
        NULL;

        static ValueKind fromNode(ConfigNode node) {
            if (node == null || node.isNull() || node.getType() == ConfigNode.NodeType.NULL) {
                return NULL;
            }
            if (node.getType() == ConfigNode.NodeType.STRING) {
                return STRING;
            }
            if (node.getType() == ConfigNode.NodeType.NUMBER) {
                return NUMBER;
            }
            if (node.getType() == ConfigNode.NodeType.BOOLEAN) {
                return BOOLEAN;
            }
            return null;
        }
    }

    static final class ListAnalysis {

        private final TemplateKind templateKind;
        private final ValueKind primitiveKind;
        private final List<String> tableColumns;
        private final Map<String, ValueKind> tableColumnKinds;
        private final String unsupportedReason;

        private ListAnalysis(TemplateKind templateKind, ValueKind primitiveKind, List<String> tableColumns,
                Map<String, ValueKind> tableColumnKinds, String unsupportedReason) {
            this.templateKind = templateKind;
            this.primitiveKind = primitiveKind;
            this.tableColumns = Collections.unmodifiableList(new ArrayList<String>(tableColumns));
            this.tableColumnKinds = Collections.unmodifiableMap(new LinkedHashMap<String, ValueKind>(tableColumnKinds));
            this.unsupportedReason = unsupportedReason == null ? "" : unsupportedReason;
        }

        static ListAnalysis simple(ValueKind primitiveKind) {
            return new ListAnalysis(TemplateKind.SIMPLE, primitiveKind == null ? ValueKind.STRING : primitiveKind,
                    Collections.<String>emptyList(), Collections.<String, ValueKind>emptyMap(), "");
        }

        static ListAnalysis table(List<String> tableColumns, Map<String, ValueKind> tableColumnKinds) {
            return new ListAnalysis(TemplateKind.TABLE, ValueKind.STRING, tableColumns, tableColumnKinds, "");
        }

        static ListAnalysis unsupported(String reason) {
            return new ListAnalysis(TemplateKind.UNSUPPORTED, ValueKind.STRING, Collections.<String>emptyList(),
                    Collections.<String, ValueKind>emptyMap(), reason);
        }

        TemplateKind getTemplateKind() {
            return templateKind;
        }

        ValueKind getPrimitiveKind() {
            return primitiveKind;
        }

        List<String> getTableColumns() {
            return tableColumns;
        }

        Map<String, ValueKind> getTableColumnKinds() {
            return tableColumnKinds;
        }

        String getUnsupportedReason() {
            return unsupportedReason;
        }
    }

    static final class ParsedValue {

        private final Object value;
        private final String error;

        private ParsedValue(Object value, String error) {
            this.value = value;
            this.error = error;
        }

        static ParsedValue ok(Object value) {
            return new ParsedValue(value, null);
        }

        static ParsedValue error(String error) {
            return new ParsedValue(null, error);
        }

        boolean hasError() {
            return error != null && !error.isEmpty();
        }

        Object getValue() {
            return value;
        }

        String getError() {
            return error;
        }
    }
}
