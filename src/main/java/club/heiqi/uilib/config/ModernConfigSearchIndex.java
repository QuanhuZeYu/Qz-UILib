package club.heiqi.uilib.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import club.heiqi.config.ConfigNode;

/**
 * 现代配置页搜索索引。
 *
 * <p>纯数据/查询类，不参与草稿与脏状态写入。构造时一次性递归遍历 rootSnapshot
 * 构建 {@link SearchEntry} 列表，后续按 path/displayName/valueSummary 搜索、
 * 按 {@link TemplateCategory} 过滤、按是否已修改过滤。</p>
 *
 * <p>{@link ModernConfigPropertyBindings.ConfigPropertyBinding} 仅用于读取
 * {@code isDirty()} 与 {@code getPath()}；本类不依赖 RawEditor/EnhancedPicker
 * 等具体 binding 类型，只通过 {@code FieldSpec.templateHint} 识别高级类别。</p>
 */
public final class ModernConfigSearchIndex {

    private static final int VALUE_SUMMARY_MAX_LENGTH = 80;

    private final DirtyStateProvider dirtyStateProvider;
    private final Map<String, ModernConfigTemplateScreen.FieldSpec> fields;
    private List<SearchEntry> entries;
    private ConfigNode pendingRoot;

    /**
     * 创建搜索索引。
     *
     * <p>构造时仅保存快照引用，实际树遍历延迟到首次 {@link #search} 或 {@link #getEntries()} 调用时执行，
     * 避免页面打开时的同步构建开销。</p>
     *
     * @param bindings 已创建的字段绑定列表，仅用于读取 dirty/path；可为 null 或空
     * @param fields 字段规格按 path 索引，可为 null 或空
     * @param rootSnapshot 配置根节点快照，决定索引内容
     */
    public ModernConfigSearchIndex(List<ModernConfigPropertyBindings.ConfigPropertyBinding> bindings,
            Map<String, ModernConfigTemplateScreen.FieldSpec> fields, ConfigNode rootSnapshot) {
        this(new BindingDirtyStateProvider(bindings), fields, rootSnapshot);
    }

    ModernConfigSearchIndex(DirtyStateProvider dirtyStateProvider,
            Map<String, ModernConfigTemplateScreen.FieldSpec> fields, ConfigNode rootSnapshot) {
        this.dirtyStateProvider = dirtyStateProvider == null ? EmptyDirtyStateProvider.INSTANCE : dirtyStateProvider;
        this.fields = fields == null
                ? Collections.<String, ModernConfigTemplateScreen.FieldSpec>emptyMap()
                : new LinkedHashMap<String, ModernConfigTemplateScreen.FieldSpec>(fields);
        this.entries = new ArrayList<SearchEntry>();
        this.pendingRoot = rootSnapshot;
    }

    private void ensureBuilt() {
        if (pendingRoot != null) {
            rebuild(pendingRoot);
            pendingRoot = null;
        }
    }

    /**
     * 按查询与过滤条件搜索索引。
     *
     * @param query 查询字符串，大小写不敏感；为 null/空表示不限关键字
     * @param typeFilter 类型过滤集合；为 null/空表示不限类型
     * @param modifiedOnly true 时只保留 dirty=true 的条目
     * @return 不可变的命中条目列表，按 path 字典序排序
     */
    public List<SearchEntry> search(String query, Set<TemplateCategory> typeFilter, boolean modifiedOnly) {
        ensureBuilt();
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        boolean hasQuery = !normalizedQuery.isEmpty();
        boolean hasFilter = typeFilter != null && !typeFilter.isEmpty();
        List<SearchEntry> results = new ArrayList<SearchEntry>();
        for (SearchEntry entry : entries) {
            if (modifiedOnly && !entry.isDirty()) {
                continue;
            }
            if (hasFilter && !typeFilter.contains(entry.getCategory())) {
                continue;
            }
            if (hasQuery && !matchesQuery(entry, normalizedQuery)) {
                continue;
            }
            results.add(entry);
        }
        Collections.sort(results, new Comparator<SearchEntry>() {
            @Override
            public int compare(SearchEntry first, SearchEntry second) {
                return first.getPath().compareTo(second.getPath());
            }
        });
        return Collections.unmodifiableList(results);
    }

    /**
     * 重新查询所有 binding 的 dirty 状态并原地更新索引条目的脏标记。
     *
     * <p>必须在 binding 调用 applyDraft/restore 之后调用，以便搜索结果反映最新脏状态。</p>
     */
    public void refreshDirtyMarkers() {
        ensureBuilt();
        Map<String, Boolean> dirtyByPath = collectDirtyByPath();
        for (int i = 0; i < entries.size(); i++) {
            SearchEntry entry = entries.get(i);
            Boolean dirty = dirtyByPath.get(entry.getPath());
            boolean newDirty = dirty != null && dirty.booleanValue();
            if (entry.isDirty() != newDirty) {
                entries.set(i, entry.withDirty(newDirty));
            }
        }
    }

    /**
     * 用新的根节点重建索引。
     *
     * @param newRoot 新的配置根节点；为 null/空时清空索引
     */
    public void rebuild(ConfigNode newRoot) {
        Map<String, Boolean> dirtyByPath = collectDirtyByPath();
        List<SearchEntry> built = new ArrayList<SearchEntry>();
        collectEntries("", newRoot, "", built, dirtyByPath);
        this.entries = built;
    }

    /**
     * 获取当前所有索引条目（不可变副本）。
     *
     * @return 索引条目列表
     */
    public List<SearchEntry> getEntries() {
        ensureBuilt();
        return Collections.unmodifiableList(entries);
    }

    private Map<String, Boolean> collectDirtyByPath() {
        Map<String, Boolean> provided = dirtyStateProvider.collectDirtyByPath();
        if (provided != null) {
            return new HashMap<String, Boolean>(provided);
        }
        return Collections.emptyMap();
    }

    /**
     * 脏状态提供者接口，解耦搜索索引对绑定列表的直接依赖。
     *
     * <p>允许嵌套分类绑定在搜索索引刷新脏标记时只上报已创建的叶子绑定，
     * 不因全量展开而触发延迟加载。</p>
     */
    interface DirtyStateProvider {

        /**
         * 收集当前已创建绑定的脏状态。
         *
         * @return path 到 dirty 状态的映射；为 null 时视为无脏项
         */
        Map<String, Boolean> collectDirtyByPath();
    }

    private static final class EmptyDirtyStateProvider implements DirtyStateProvider {

        private static final EmptyDirtyStateProvider INSTANCE = new EmptyDirtyStateProvider();

        @Override
        public Map<String, Boolean> collectDirtyByPath() {
            return Collections.emptyMap();
        }
    }

    private static final class BindingDirtyStateProvider implements DirtyStateProvider {

        private final List<ModernConfigPropertyBindings.ConfigPropertyBinding> bindings;

        private BindingDirtyStateProvider(List<ModernConfigPropertyBindings.ConfigPropertyBinding> bindings) {
            this.bindings = bindings == null
                    ? Collections.<ModernConfigPropertyBindings.ConfigPropertyBinding>emptyList()
                    : new ArrayList<ModernConfigPropertyBindings.ConfigPropertyBinding>(bindings);
        }

        @Override
        public Map<String, Boolean> collectDirtyByPath() {
            Map<String, Boolean> map = new HashMap<String, Boolean>();
            for (ModernConfigPropertyBindings.ConfigPropertyBinding binding : bindings) {
                if (binding == null) {
                    continue;
                }
                map.put(binding.getPath(), Boolean.valueOf(binding.isDirty()));
            }
            return map;
        }
    }

    private void collectEntries(String path, ConfigNode node, String subtreeRoot, List<SearchEntry> out,
            Map<String, Boolean> dirtyByPath) {
        if (node == null || node.isNull()) {
            return;
        }
        ConfigNode.NodeType type = node.getType();
        if (!path.isEmpty()) {
            out.add(buildEntry(path, node, subtreeRoot, dirtyByPath));
        }
        if (type == ConfigNode.NodeType.MAP) {
            Map<String, ConfigNode> map = node.asMap();
            if (map == null || map.isEmpty()) {
                return;
            }
            // 进入 MAP 时，子节点的 subtreeRoot 更新为当前 MAP 的 path
            String nextSubtreeRoot = path;
            List<String> keys = new ArrayList<String>(map.keySet());
            Collections.sort(keys);
            for (String key : keys) {
                String childPath = path.isEmpty() ? key : path + "." + key;
                collectEntries(childPath, map.get(key), nextSubtreeRoot, out, dirtyByPath);
            }
            return;
        }
        if (type == ConfigNode.NodeType.LIST) {
            List<ConfigNode> list = node.asList();
            if (list == null || list.isEmpty()) {
                return;
            }
            // LIST 不更新 subtreeRoot，沿用父 MAP 的值
            for (int i = 0; i < list.size(); i++) {
                String childPath = path + "[" + i + "]";
                collectEntries(childPath, list.get(i), subtreeRoot, out, dirtyByPath);
            }
        }
    }

    private SearchEntry buildEntry(String path, ConfigNode node, String subtreeRoot,
            Map<String, Boolean> dirtyByPath) {
        ModernConfigTemplateScreen.FieldSpec fieldSpec = fields.get(path);
        TemplateCategory category = resolveCategory(node, fieldSpec);
        String templateTypeLabel = resolveTemplateTypeLabel(node, fieldSpec);
        String valueSummary = buildValueSummary(node);
        String displayName = resolveDisplayName(path, fieldSpec);
        String description = fieldSpec == null ? "" : normalizeWhitespace(fieldSpec.getDescription());
        boolean dirty = Boolean.TRUE.equals(dirtyByPath.get(path));
        return new SearchEntry(path, displayName, description, templateTypeLabel, valueSummary, dirty,
                subtreeRoot, category);
    }

    private static boolean matchesQuery(SearchEntry entry, String normalizedQuery) {
        if (containsIgnoreCase(entry.getPath(), normalizedQuery)) {
            return true;
        }
        if (containsIgnoreCase(entry.getDisplayName(), normalizedQuery)) {
            return true;
        }
        return containsIgnoreCase(entry.getValueSummary(), normalizedQuery);
    }

    private static boolean containsIgnoreCase(String text, String token) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return text.toLowerCase(Locale.ROOT).contains(token);
    }

    private static String buildValueSummary(ConfigNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        ConfigNode.NodeType type = node.getType();
        if (type == ConfigNode.NodeType.MAP) {
            Map<String, ConfigNode> map = node.asMap();
            int size = map == null ? 0 : map.size();
            return "object (" + size + " keys)";
        }
        if (type == ConfigNode.NodeType.LIST) {
            List<ConfigNode> list = node.asList();
            int size = list == null ? 0 : list.size();
            return "list (" + size + " items)";
        }
        String text = node.asString("");
        if (text == null) {
            return "";
        }
        if (text.length() <= VALUE_SUMMARY_MAX_LENGTH) {
            return text;
        }
        return text.substring(0, VALUE_SUMMARY_MAX_LENGTH - 3) + "...";
    }

    private static TemplateCategory resolveCategory(ConfigNode node, ModernConfigTemplateScreen.FieldSpec fieldSpec) {
        String hint = normalizeHint(fieldSpec == null ? "" : fieldSpec.getTemplateHint());
        if (isRawEditorHint(hint)) {
            return TemplateCategory.RAW_EDITOR;
        }
        if (isEnhancedPickerHint(hint)) {
            return TemplateCategory.ENHANCED_PICKER;
        }
        ConfigNode.NodeType type = node == null ? ConfigNode.NodeType.NULL : node.getType();
        switch (type) {
            case STRING:
                return TemplateCategory.STRING;
            case NUMBER:
                return TemplateCategory.NUMBER;
            case BOOLEAN:
                return TemplateCategory.BOOLEAN;
            case LIST:
                return TemplateCategory.LIST;
            case MAP:
                return TemplateCategory.OBJECT;
            default:
                return TemplateCategory.STRING;
        }
    }

    private static String resolveTemplateTypeLabel(ConfigNode node, ModernConfigTemplateScreen.FieldSpec fieldSpec) {
        String hint = normalizeHint(fieldSpec == null ? "" : fieldSpec.getTemplateHint());
        if (!hint.isEmpty()) {
            return hint;
        }
        ConfigNode.NodeType type = node == null ? ConfigNode.NodeType.NULL : node.getType();
        switch (type) {
            case STRING:
                return "字符串";
            case NUMBER:
                return "数字";
            case BOOLEAN:
                return "布尔";
            case MAP:
                return "对象";
            case LIST:
                return "列表";
            case NULL:
                return "空值";
            default:
                return type.name();
        }
    }

    private static String resolveDisplayName(String path, ModernConfigTemplateScreen.FieldSpec fieldSpec) {
        if (fieldSpec != null) {
            String label = fieldSpec.getLabel();
            if (label != null && !label.trim().isEmpty()) {
                return label.trim();
            }
        }
        return ModernConfigPropertyBindings.formatDisplayLabel(path);
    }

    private static boolean isRawEditorHint(String hint) {
        return "raw".equals(hint) || "raw-editor".equals(hint) || "code".equals(hint) || "source".equals(hint)
                || "json".equals(hint) || "json-editor".equals(hint) || "yaml".equals(hint)
                || "yaml-editor".equals(hint);
    }

    private static boolean isEnhancedPickerHint(String hint) {
        return "color".equals(hint) || "colour".equals(hint) || "hex".equals(hint) || "resource".equals(hint)
                || "asset".equals(hint) || "sound".equals(hint) || "audio".equals(hint);
    }

    private static String normalizeHint(String hint) {
        return hint == null ? "" : hint.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeWhitespace(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\r', ' ').replace('\n', ' ').trim().replaceAll("\\s+", " ");
    }

    /**
     * 索引条目类型，用于按类型过滤。
     */
    public enum TemplateCategory {
        /** 字符串叶子 */
        STRING,
        /** 数字叶子 */
        NUMBER,
        /** 布尔叶子 */
        BOOLEAN,
        /** 对象/Map */
        OBJECT,
        /** 列表 */
        LIST,
        /** 源码编辑模板（hint: raw/code/json/yaml 等） */
        RAW_EDITOR,
        /** 增强选择器模板（hint: color/resource/sound 等） */
        ENHANCED_PICKER
    }

    /**
     * 不可变的索引条目。
     */
    public static final class SearchEntry {

        private final String path;
        private final String displayName;
        private final String description;
        private final String templateTypeLabel;
        private final String valueSummary;
        private final boolean dirty;
        private final String subtreeRoot;
        private final TemplateCategory category;

        SearchEntry(String path, String displayName, String description, String templateTypeLabel,
                String valueSummary, boolean dirty, String subtreeRoot, TemplateCategory category) {
            this.path = path == null ? "" : path;
            this.displayName = displayName == null ? "" : displayName;
            this.description = description == null ? "" : description;
            this.templateTypeLabel = templateTypeLabel == null ? "" : templateTypeLabel;
            this.valueSummary = valueSummary == null ? "" : valueSummary;
            this.dirty = dirty;
            this.subtreeRoot = subtreeRoot == null ? "" : subtreeRoot;
            this.category = category == null ? TemplateCategory.STRING : category;
        }

        public String getPath() {
            return path;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }

        public String getTemplateTypeLabel() {
            return templateTypeLabel;
        }

        public String getValueSummary() {
            return valueSummary;
        }

        public boolean isDirty() {
            return dirty;
        }

        public String getSubtreeRoot() {
            return subtreeRoot;
        }

        public TemplateCategory getCategory() {
            return category;
        }

        SearchEntry withDirty(boolean newDirty) {
            if (newDirty == this.dirty) {
                return this;
            }
            return new SearchEntry(path, displayName, description, templateTypeLabel, valueSummary, newDirty,
                    subtreeRoot, category);
        }
    }
}
