package club.heiqi.uilib.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import club.heiqi.config.ConfigNode;
import club.heiqi.config.MutableConfig;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 现代配置属性绑定工厂骨架。
 */
final class ModernConfigPropertyBindings {

    private static final int SUMMARY_MAX_LENGTH = 120;

    private ModernConfigPropertyBindings() {}

    /**
     * 创建只读路径绑定列表。
     *
     * @param config 可变配置对象
     * @return 只读路径绑定列表
     */
    static List<ReadOnlyPathBinding> createReadOnlyPathBindings(MutableConfig config) {
        List<ReadOnlyPathBinding> bindings = new ArrayList<ReadOnlyPathBinding>();
        if (config == null) {
            return bindings;
        }
        collectPathBindings("", config.asImmutable(), bindings);
        return bindings;
    }

    private static void collectPathBindings(String path, ConfigNode node, List<ReadOnlyPathBinding> bindings) {
        if (node == null) {
            return;
        }
        if (!path.isEmpty()) {
            bindings.add(new ReadOnlyPathBinding(path, formatType(node), formatSummary(node)));
        }
        if (node.getType() == ConfigNode.NodeType.MAP) {
            Map<String, ConfigNode> map = node.asMap();
            if (map == null || map.isEmpty()) {
                return;
            }
            List<String> keys = new ArrayList<String>(map.keySet());
            Collections.sort(keys);
            for (String key : keys) {
                collectPathBindings(path.isEmpty() ? key : path + "." + key, map.get(key), bindings);
            }
            return;
        }
        if (node.getType() == ConfigNode.NodeType.LIST) {
            List<ConfigNode> list = node.asList();
            if (list == null || list.isEmpty()) {
                return;
            }
            for (int index = 0; index < list.size(); index++) {
                collectPathBindings(path + "[" + index + "]", list.get(index), bindings);
            }
        }
    }

    private static String formatType(ConfigNode node) {
        if (node == null || node.isNull()) {
            return "NULL";
        }
        return node.getType().name();
    }

    private static String formatSummary(ConfigNode node) {
        if (node == null || node.isNull()) {
            return "null";
        }
        if (node.getType() == ConfigNode.NodeType.MAP) {
            Map<String, ConfigNode> map = node.asMap();
            int size = map == null ? 0 : map.size();
            return "子项 " + size + " 个";
        }
        if (node.getType() == ConfigNode.NodeType.LIST) {
            List<ConfigNode> list = node.asList();
            int size = list == null ? 0 : list.size();
            return "元素 " + size + " 个";
        }
        return truncate(node.asString(""));
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        String normalized = ForgeConfigTemplateScreen.normalizeInlineText(value);
        if (normalized.length() <= SUMMARY_MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, SUMMARY_MAX_LENGTH - 3) + "...";
    }

    /**
     * 只读路径绑定。
     */
    static final class ReadOnlyPathBinding {

        private final String path;
        private final String type;
        private final String summary;

        private ReadOnlyPathBinding(String path, String type, String summary) {
            this.path = path;
            this.type = type;
            this.summary = summary;
        }

        ElementNode createCard(UiDocument document, ForgeConfigTemplateScreen.Theme theme) {
            ElementNode card = document.div();
            card.setAttribute("data-modern-config-path", path);
            card.style()
                    .setPadding(UiStyleLength.px(14))
                    .setBackgroundColor(0xFF162132)
                    .setBorderColor(0xFF334155)
                    .setBorderWidth(UiStyleLength.px(1))
                    .setBorderRadius(UiStyleLength.px(14));

            ElementNode title = document.div();
            title.style().setTextColor(0xFFF8FAFC);
            title.appendText(path);
            card.append(title);

            ElementNode metadata = document.div();
            metadata.style().setMargin(UiStyleLength.px(6)).setTextColor(0xFF93C5FD);
            metadata.appendText("类型：" + type);
            card.append(metadata);

            ElementNode value = document.div();
            value.style()
                    .setDisplay(UiDisplay.FLEX)
                    .setFlexDirection(UiFlexDirection.COLUMN)
                    .setMargin(UiStyleLength.px(8))
                    .setTextColor(theme.mutedTextColor);
            value.appendText(summary.isEmpty() ? "空值" : summary);
            card.append(value);
            return card;
        }
    }
}
