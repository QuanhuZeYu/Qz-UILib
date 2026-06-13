package club.heiqi.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * YAML 配置加载器实现。
 * 
 * 注意：这是一个简化的 YAML 解析器，支持基本的 YAML 语法。
 * 对于复杂的 YAML 特性（如锚点、别名、多行字符串等），建议使用专业的 YAML 库。
 */
class YamlConfigLoader implements ConfigLoader {

    @Override
    public ConfigNode load(ConfigSource source) throws ConfigException {
        try {
            String content = source.read();
            return parseYaml(content);
        } catch (Exception e) {
            throw new ConfigException("Failed to parse YAML from " + source.getDescription(), e);
        }
    }

    @Override
    public ConfigFormat getFormat() {
        return ConfigFormat.YAML;
    }

    /**
     * 解析 YAML 内容
     * 
     * @param content YAML 内容
     * @return 配置节点
     * @throws ConfigException 如果解析失败
     */
    private ConfigNode parseYaml(String content) throws ConfigException {
        String[] lines = content.split("\n");
        List<String> processedLines = new ArrayList<String>();

        // 预处理：移除注释和空行
        for (String line : lines) {
            // 移除注释
            int commentIndex = line.indexOf('#');
            if (commentIndex >= 0) {
                line = line.substring(0, commentIndex);
            }

            // 保留有内容的行
            if (!line.trim().isEmpty()) {
                processedLines.add(line);
            }
        }

        if (processedLines.isEmpty()) {
            return new MapConfigNode(new HashMap<String, ConfigNode>());
        }

        return parseLines(processedLines, 0, processedLines.size(), 0);
    }

    /**
     * 解析行范围
     * 
     * @param lines 所有行
     * @param start 起始行索引
     * @param end 结束行索引（不包含）
     * @param baseIndent 基础缩进级别
     * @return 配置节点
     * @throws ConfigException 如果解析失败
     */
    private ConfigNode parseLines(List<String> lines, int start, int end, int baseIndent) throws ConfigException {
        if (start >= end) {
            return NullConfigNode.INSTANCE;
        }

        String firstLine = lines.get(start);
        int firstIndent = getIndent(firstLine);

        // 检查是否为列表
        if (firstLine.trim().startsWith("-")) {
            return parseList(lines, start, end, firstIndent);
        }

        // 否则解析为映射
        return parseMap(lines, start, end, firstIndent);
    }

    /**
     * 解析映射
     * 
     * @param lines 所有行
     * @param start 起始行索引
     * @param end 结束行索引（不包含）
     * @param baseIndent 基础缩进级别
     * @return 配置节点
     * @throws ConfigException 如果解析失败
     */
    private ConfigNode parseMap(List<String> lines, int start, int end, int baseIndent) throws ConfigException {
        Map<String, ConfigNode> map = new LinkedHashMap<String, ConfigNode>();

        int i = start;
        while (i < end) {
            String line = lines.get(i);
            int indent = getIndent(line);

            if (indent < baseIndent) {
                break;
            }

            if (indent > baseIndent) {
                i++;
                continue;
            }

            String trimmed = line.trim();
            int colonIndex = trimmed.indexOf(':');

            if (colonIndex < 0) {
                throw new ConfigException("Invalid YAML line (missing colon): " + line);
            }

            String key = trimmed.substring(0, colonIndex).trim();
            String valueStr = trimmed.substring(colonIndex + 1).trim();

            ConfigNode value;

            if (valueStr.isEmpty()) {
                // 值在下一行（嵌套结构）
                int childStart = i + 1;
                int childEnd = findNextSiblingLine(lines, childStart, end, baseIndent);
                value = parseLines(lines, childStart, childEnd, indent + 2);
                i = childEnd;
            } else {
                // 值在同一行
                value = parseValue(valueStr);
                i++;
            }

            map.put(key, value);
        }

        return new MapConfigNode(map);
    }

    /**
     * 解析列表
     * 
     * @param lines 所有行
     * @param start 起始行索引
     * @param end 结束行索引（不包含）
     * @param baseIndent 基础缩进级别
     * @return 配置节点
     * @throws ConfigException 如果解析失败
     */
    private ConfigNode parseList(List<String> lines, int start, int end, int baseIndent) throws ConfigException {
        List<ConfigNode> list = new ArrayList<ConfigNode>();

        int i = start;
        while (i < end) {
            String line = lines.get(i);
            int indent = getIndent(line);

            if (indent < baseIndent) {
                break;
            }

            if (indent > baseIndent) {
                i++;
                continue;
            }

            String trimmed = line.trim();
            if (!trimmed.startsWith("-")) {
                break;
            }

            String valueStr = trimmed.substring(1).trim();

            ConfigNode value;

            if (valueStr.isEmpty()) {
                // 值在下一行（嵌套结构）
                int childStart = i + 1;
                int childEnd = findNextSiblingLine(lines, childStart, end, baseIndent);
                value = parseLines(lines, childStart, childEnd, indent + 2);
                i = childEnd;
            } else {
                // 值在同一行
                value = parseValue(valueStr);
                i++;
            }

            list.add(value);
        }

        return new ListConfigNode(list);
    }

    /**
     * 查找下一个同级行的索引
     * 
     * @param lines 所有行
     * @param start 起始行索引
     * @param end 结束行索引（不包含）
     * @param targetIndent 目标缩进级别
     * @return 下一个同级行的索引
     */
    private int findNextSiblingLine(List<String> lines, int start, int end, int targetIndent) {
        for (int i = start; i < end; i++) {
            int indent = getIndent(lines.get(i));
            if (indent <= targetIndent) {
                return i;
            }
        }
        return end;
    }

    /**
     * 解析单个值
     * 
     * @param valueStr 值字符串
     * @return 配置节点
     */
    private ConfigNode parseValue(String valueStr) {
        // 移除引号
        if ((valueStr.startsWith("\"") && valueStr.endsWith("\"")) ||
            (valueStr.startsWith("'") && valueStr.endsWith("'"))) {
            return new StringConfigNode(valueStr.substring(1, valueStr.length() - 1));
        }

        // 布尔值
        if ("true".equalsIgnoreCase(valueStr) || "yes".equalsIgnoreCase(valueStr)) {
            return new BooleanConfigNode(true);
        }
        if ("false".equalsIgnoreCase(valueStr) || "no".equalsIgnoreCase(valueStr)) {
            return new BooleanConfigNode(false);
        }

        // null 值
        if ("null".equalsIgnoreCase(valueStr) || "~".equals(valueStr)) {
            return NullConfigNode.INSTANCE;
        }

        // 数字
        try {
            if (valueStr.contains(".")) {
                return new NumberConfigNode(Double.parseDouble(valueStr));
            } else {
                long longValue = Long.parseLong(valueStr);
                if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                    return new NumberConfigNode((int) longValue);
                }
                return new NumberConfigNode(longValue);
            }
        } catch (NumberFormatException e) {
            // 不是数字，作为字符串处理
        }

        // 默认为字符串
        return new StringConfigNode(valueStr);
    }

    /**
     * 获取行的缩进级别（空格数）
     * 
     * @param line 行内容
     * @return 缩进级别
     */
    private int getIndent(String line) {
        int indent = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ' ') {
                indent++;
            } else {
                break;
            }
        }
        return indent;
    }
}
