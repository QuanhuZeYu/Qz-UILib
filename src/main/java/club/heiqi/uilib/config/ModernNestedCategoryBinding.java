package club.heiqi.uilib.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import club.heiqi.config.ConfigNode;
import club.heiqi.config.MutableConfig;
import club.heiqi.uilib.ui.control.DocumentBreadcrumbControl;
import club.heiqi.uilib.ui.control.DocumentTreeViewControl;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiFlexWrap;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 现代配置嵌套分类绑定，负责树形导航、面包屑和当前对象内容渲染。
 */
final class ModernNestedCategoryBinding extends ModernConfigPropertyBindings.ConfigPropertyBinding
        implements ModernObjectPropertyBinding.ObjectBindingContext {

    private final Map<String, ModernConfigTemplateScreen.FieldSpec> fieldsByPath;
    private final ModernConfigPropertyBindings.ChangeListener changeListener;
    private final Map<String, ModernConfigPropertyBindings.ConfigPropertyBinding> bindingsByPath =
            new LinkedHashMap<String, ModernConfigPropertyBindings.ConfigPropertyBinding>();
    private final Set<String> mapPaths = new LinkedHashSet<String>();
    private String currentPath = "";
    private DocumentTreeViewControl treeControl;
    private DocumentBreadcrumbControl breadcrumbControl;
    private ElementNode contentElement;
    private ForgeConfigTemplateScreen.Theme currentTheme = ForgeConfigTemplateScreen.Theme.defaultTheme();

    ModernNestedCategoryBinding(MutableConfig config, ConfigNode root,
            Map<String, ModernConfigTemplateScreen.FieldSpec> fieldsByPath,
            ModernConfigPropertyBindings.ChangeListener changeListener) {
        super(config, "", root, null, ModernConfigTypeInference.infer("", root, null), changeListener);
        this.fieldsByPath = fieldsByPath == null
                ? Collections.<String, ModernConfigTemplateScreen.FieldSpec>emptyMap()
                : new LinkedHashMap<String, ModernConfigTemplateScreen.FieldSpec>(fieldsByPath);
        this.changeListener = changeListener;
        rebuildModel();
    }

    @Override
    protected ElementNode createEditorElement(UiDocument document, ForgeConfigTemplateScreen.Theme theme) {
        return createSection(document, theme);
    }

    /**
     * 创建嵌套结构独立区块。
     *
     * @param document 目标文档
     * @param theme 当前主题
     * @return 区块根元素
     */
    ElementNode createSection(UiDocument document, ForgeConfigTemplateScreen.Theme theme) {
        currentTheme = theme == null ? ForgeConfigTemplateScreen.Theme.defaultTheme() : theme;
        ElementNode section = document.element("section");
        section.setAttribute("data-modern-config-nested", "true");
        section.style()
                .setPadding(UiStyleLength.px(16))
                .setBackgroundColor(currentTheme.categoryCardBackgroundColor)
                .setBorderColor(currentTheme.categoryCardBorderColor)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(18));

        ElementNode header = document.div();
        header.style().setMargin(UiStyleLength.px(4));
        header.appendText("嵌套配置结构");
        ElementNode description = document.div();
        description.style().setMargin(UiStyleLength.px(6))
                .setTextColor(currentTheme.categoryDescriptionTextColor);
        description.appendText("通过树形导航和面包屑定位 map 节点；普通对象在卡片内联编辑，超深层级通过展开编辑进入子节点。");
        header.append(description);
        section.append(header);

        ElementNode layout = document.div();
        layout.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setAlignItems(UiAlignItems.STRETCH)
                .setColumnGap(UiStyleLength.px(12))
                .setRowGap(UiStyleLength.px(12))
                .setMargin(UiStyleLength.px(8));
        layout.append(createTreePane(document));
        layout.append(createContentPane(document));
        section.append(layout);
        refreshNavigationControls();
        refreshContent();
        return section;
    }

    @Override
    boolean isDirty() {
        return getDirtyCount() > 0;
    }

    @Override
    int getDirtyCount() {
        int dirtyCount = 0;
        for (ModernConfigPropertyBindings.ConfigPropertyBinding binding : bindingsByPath.values()) {
            dirtyCount += binding.getDirtyCount();
        }
        return dirtyCount;
    }

    @Override
    void restoreCurrentValue() {
        rebuildModel();
        refreshNavigationControls();
        refreshContent();
    }

    @Override
    void restoreDefaultValue() {
        for (ModernConfigPropertyBindings.ConfigPropertyBinding binding : bindingsByPath.values()) {
            if (binding.canRestoreDefaultValue()) {
                binding.restoreDefaultValue();
            }
        }
    }

    @Override
    String validateDraft() {
        for (ModernConfigPropertyBindings.ConfigPropertyBinding binding : bindingsByPath.values()) {
            String validationError = binding.validateDraft();
            binding.setValidationError(validationError);
            if (validationError != null && !validationError.isEmpty()) {
                return binding.getDisplayName() + "：" + validationError;
            }
        }
        return null;
    }

    @Override
    void applyDraft() {
        for (ModernConfigPropertyBindings.ConfigPropertyBinding binding : bindingsByPath.values()) {
            if (binding.isDirty()) {
                binding.applyDraft();
            }
        }
    }

    @Override
    boolean canRestoreDefaultValue() {
        for (ModernConfigPropertyBindings.ConfigPropertyBinding binding : bindingsByPath.values()) {
            if (binding.canRestoreDefaultValue()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> collectDirectChildPaths(String path) {
        String parentPath = normalizePath(path);
        Set<String> childPaths = new LinkedHashSet<String>();
        ConfigNode node = getConfig().get(parentPath);
        if (node != null && node.getType() == ConfigNode.NodeType.MAP && node.asMap() != null) {
            List<String> keys = new ArrayList<String>(node.asMap().keySet());
            Collections.sort(keys);
            for (String key : keys) {
                childPaths.add(resolveChildPath(parentPath, key));
            }
        }
        for (String fieldPath : fieldsByPath.keySet()) {
            String directChildPath = resolveDirectChildPath(parentPath, fieldPath);
            if (!directChildPath.isEmpty()) {
                childPaths.add(directChildPath);
            }
        }
        List<String> result = new ArrayList<String>(childPaths);
        Collections.sort(result);
        return result;
    }

    @Override
    public ModernConfigPropertyBindings.ConfigPropertyBinding resolveBinding(String path) {
        return bindingsByPath.get(normalizePath(path));
    }

    @Override
    public List<ModernConfigPropertyBindings.ConfigPropertyBinding> resolveDescendantBindings(String path) {
        String parentPath = normalizePath(path);
        List<ModernConfigPropertyBindings.ConfigPropertyBinding> bindings =
                new ArrayList<ModernConfigPropertyBindings.ConfigPropertyBinding>();
        for (Map.Entry<String, ModernConfigPropertyBindings.ConfigPropertyBinding> entry : bindingsByPath.entrySet()) {
            if (isDescendantPath(parentPath, entry.getKey())) {
                bindings.add(entry.getValue());
            }
        }
        return bindings;
    }

    @Override
    public ModernConfigTemplateScreen.FieldSpec resolveFieldSpec(String path) {
        return fieldsByPath.get(normalizePath(path));
    }

    @Override
    public void navigateTo(String path) {
        String targetPath = normalizePath(path);
        if (!isKnownMapPath(targetPath)) {
            targetPath = parentPath(targetPath);
        }
        currentPath = targetPath;
        refreshNavigationControls();
        refreshContent();
    }

    @Override
    public int getMaxInlineDepth() {
        return ModernConfigPropertyBindings.DEFAULT_OBJECT_INLINE_DEPTH;
    }

    private ElementNode createTreePane(UiDocument document) {
        ElementNode pane = document.div();
        pane.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(8))
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(0xFF0F172A)
                .setBorderColor(0xFF334155)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(14))
                .setWidth(UiStyleLength.px(250));
        ElementNode title = document.div();
        title.style().setTextColor(0xFFF8FAFC);
        title.appendText("结构导航");
        pane.append(title);
        treeControl = new DocumentTreeViewControl(document, buildTreeNodes())
                .setSelectionHandler(new DocumentTreeViewControl.TreeSelectionHandler() {
                    @Override
                    public void onTreePathSelected(String path) {
                        navigateTo(path);
                    }
                });
        treeControl.setCurrentPath(currentPath);
        pane.append(treeControl.getElement());
        return pane;
    }

    private ElementNode createContentPane(UiDocument document) {
        ElementNode pane = document.div();
        pane.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(10))
                .setFlexGrow(1.0F)
                .setMinWidth(UiStyleLength.px(320));
        breadcrumbControl = new DocumentBreadcrumbControl(document)
                .setSelectionHandler(new DocumentBreadcrumbControl.BreadcrumbSelectionHandler() {
                    @Override
                    public void onBreadcrumbPathSelected(String path) {
                        navigateTo(path);
                    }
                });
        pane.append(breadcrumbControl.getElement());
        contentElement = document.div();
        contentElement.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(10));
        pane.append(contentElement);
        return pane;
    }

    private void refreshNavigationControls() {
        if (!isKnownMapPath(currentPath)) {
            currentPath = "";
        }
        if (treeControl != null) {
            treeControl.setNodes(buildTreeNodes()).setCurrentPath(currentPath);
        }
        if (breadcrumbControl != null) {
            breadcrumbControl.setPath(currentPath);
        }
    }

    private void refreshContent() {
        if (contentElement == null) {
            return;
        }
        UiDocument document = contentElement.getOwnerDocument();
        contentElement.clearChildren();
        appendCurrentHeader(document);
        List<String> childPaths = collectDirectChildPaths(currentPath);
        if (childPaths.isEmpty()) {
            appendEmptyCurrentNode(document);
            return;
        }
        for (String childPath : childPaths) {
            appendCurrentChild(document, childPath);
        }
    }

    private void appendCurrentHeader(UiDocument document) {
        ElementNode header = document.div();
        header.style()
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(0xFF0F172A)
                .setBorderColor(0xFF334155)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(12))
                .setTextColor(0xFFE2E8F0);
        header.appendText((currentPath.isEmpty() ? "根配置" : currentPath) + " | "
                + ModernConfigPropertyBindings.formatSummary(getConfig().get(currentPath)));
        contentElement.append(header);
    }

    private void appendEmptyCurrentNode(UiDocument document) {
        ElementNode empty = document.div();
        empty.style()
                .setPadding(UiStyleLength.px(10))
                .setTextColor(0xFF94A3B8);
        empty.appendText("当前节点没有可展示的子项。");
        contentElement.append(empty);
    }

    private void appendCurrentChild(UiDocument document, String childPath) {
        ConfigNode childNode = getConfig().get(childPath);
        ModernConfigTemplateScreen.FieldSpec childFieldSpec = resolveFieldSpec(childPath);
        ModernConfigTypeInference.Result inference = ModernConfigTypeInference.infer(childPath, childNode, childFieldSpec);
        if (isKnownMapPath(childPath) || inference.getTemplateType() == ModernConfigTypeInference.TemplateType.OBJECT) {
            ModernObjectPropertyBinding objectBinding = new ModernObjectPropertyBinding(getConfig(), childPath,
                    childNode, childFieldSpec, inference, null, this, 1);
            contentElement.append(objectBinding.createCard(document, currentTheme));
            return;
        }
        ModernConfigPropertyBindings.ConfigPropertyBinding binding = resolveBinding(childPath);
        if (binding == null) {
            binding = ModernConfigPropertyBindings.createBinding(getConfig(), childPath, childNode, childFieldSpec,
                    inference, null);
        }
        contentElement.append(binding.createCard(document, currentTheme));
    }

    private void rebuildModel() {
        bindingsByPath.clear();
        mapPaths.clear();
        ConfigNode root = getConfig().asImmutable();
        collectMapPaths("", root);
        collectLeafBindings("", root);
        includeFieldSpecPaths();
    }

    private void collectMapPaths(String path, ConfigNode node) {
        if (node == null || node.getType() != ConfigNode.NodeType.MAP || node.asMap() == null) {
            return;
        }
        if (!normalizePath(path).isEmpty() && shouldUseLeafMapBinding(path, node)) {
            return;
        }
        mapPaths.add(normalizePath(path));
        List<String> keys = new ArrayList<String>(node.asMap().keySet());
        Collections.sort(keys);
        for (String key : keys) {
            collectMapPaths(resolveChildPath(path, key), node.asMap().get(key));
        }
    }

    private void collectLeafBindings(String path, ConfigNode node) {
        if (node == null) {
            return;
        }
        if (node.getType() == ConfigNode.NodeType.MAP && node.asMap() != null
                && !shouldUseLeafMapBinding(path, node)) {
            List<String> keys = new ArrayList<String>(node.asMap().keySet());
            Collections.sort(keys);
            for (String key : keys) {
                collectLeafBindings(resolveChildPath(path, key), node.asMap().get(key));
            }
            return;
        }
        addLeafBinding(path, node);
    }

    private void includeFieldSpecPaths() {
        for (String fieldPath : fieldsByPath.keySet()) {
            String normalizedPath = normalizePath(fieldPath);
            String parent = parentPath(normalizedPath);
            while (!parent.isEmpty()) {
                mapPaths.add(parent);
                parent = parentPath(parent);
            }
            if (!bindingsByPath.containsKey(normalizedPath) && !isKnownMapPath(normalizedPath)) {
                addLeafBinding(normalizedPath, getConfig().get(normalizedPath));
            }
        }
    }

    private void addLeafBinding(String path, ConfigNode node) {
        String normalizedPath = normalizePath(path);
        if (normalizedPath.isEmpty() || bindingsByPath.containsKey(normalizedPath)) {
            return;
        }
        ModernConfigTemplateScreen.FieldSpec fieldSpec = fieldsByPath.get(normalizedPath);
        ModernConfigTypeInference.Result inference = ModernConfigTypeInference.infer(normalizedPath, node, fieldSpec);
        if (inference.getTemplateType() == ModernConfigTypeInference.TemplateType.OBJECT) {
            mapPaths.add(normalizedPath);
            return;
        }
        bindingsByPath.put(normalizedPath, ModernConfigPropertyBindings.createBinding(getConfig(), normalizedPath, node,
                fieldSpec, inference, changeListener));
    }

    private boolean shouldUseLeafMapBinding(String path, ConfigNode node) {
        String normalizedPath = normalizePath(path);
        if (normalizedPath.isEmpty()) {
            return false;
        }
        ModernConfigTemplateScreen.FieldSpec fieldSpec = fieldsByPath.get(normalizedPath);
        ModernConfigTypeInference.Result inference = ModernConfigTypeInference.infer(normalizedPath, node, fieldSpec);
        return inference.getTemplateType() == ModernConfigTypeInference.TemplateType.KEY_VALUE_MAP
                || inference.getTemplateType() == ModernConfigTypeInference.TemplateType.PRESET_SELECTOR;
    }

    private List<DocumentTreeViewControl.TreeNode> buildTreeNodes() {
        List<DocumentTreeViewControl.TreeNode> nodes = new ArrayList<DocumentTreeViewControl.TreeNode>();
        nodes.add(buildTreeNode(""));
        return nodes;
    }

    private DocumentTreeViewControl.TreeNode buildTreeNode(String path) {
        List<String> childMapPaths = collectDirectChildMapPaths(path);
        List<DocumentTreeViewControl.TreeNode> children = new ArrayList<DocumentTreeViewControl.TreeNode>();
        for (String childPath : childMapPaths) {
            children.add(buildTreeNode(childPath));
        }
        return new DocumentTreeViewControl.TreeNode(path, resolveTreeLabel(path), children);
    }

    private List<String> collectDirectChildMapPaths(String parentPath) {
        List<String> childPaths = new ArrayList<String>();
        for (String mapPath : mapPaths) {
            if (mapPath.isEmpty() || !parentPath(mapPath).equals(normalizePath(parentPath))) {
                continue;
            }
            childPaths.add(mapPath);
        }
        Collections.sort(childPaths);
        return childPaths;
    }

    private String resolveTreeLabel(String path) {
        String normalizedPath = normalizePath(path);
        if (normalizedPath.isEmpty()) {
            return "根配置";
        }
        ModernConfigTemplateScreen.FieldSpec fieldSpec = fieldsByPath.get(normalizedPath);
        if (fieldSpec != null && !fieldSpec.getLabel().isEmpty()) {
            return fieldSpec.getLabel();
        }
        return ModernConfigPropertyBindings.formatDisplayLabel(leafName(normalizedPath));
    }

    private boolean isKnownMapPath(String path) {
        return mapPaths.contains(normalizePath(path));
    }

    private static String resolveDirectChildPath(String parentPath, String descendantPath) {
        String parent = normalizePath(parentPath);
        String descendant = normalizePath(descendantPath);
        if (descendant.isEmpty() || descendant.equals(parent)) {
            return "";
        }
        String suffix;
        if (parent.isEmpty()) {
            suffix = descendant;
        } else if (descendant.startsWith(parent + ".")) {
            suffix = descendant.substring(parent.length() + 1);
        } else {
            return "";
        }
        int dotIndex = suffix.indexOf('.');
        String childName = dotIndex < 0 ? suffix : suffix.substring(0, dotIndex);
        return resolveChildPath(parent, childName);
    }

    private static String resolveChildPath(String parentPath, String childName) {
        String parent = normalizePath(parentPath);
        String child = childName == null ? "" : childName.trim();
        return parent.isEmpty() ? child : parent + "." + child;
    }

    private static boolean isDescendantPath(String parentPath, String childPath) {
        String parent = normalizePath(parentPath);
        String child = normalizePath(childPath);
        return parent.isEmpty() ? !child.isEmpty() : child.startsWith(parent + ".");
    }

    private static String parentPath(String path) {
        String normalizedPath = normalizePath(path);
        int dotIndex = normalizedPath.lastIndexOf('.');
        return dotIndex <= 0 ? "" : normalizedPath.substring(0, dotIndex);
    }

    private static String leafName(String path) {
        String normalizedPath = normalizePath(path);
        int dotIndex = normalizedPath.lastIndexOf('.');
        return dotIndex < 0 ? normalizedPath : normalizedPath.substring(dotIndex + 1);
    }

    private static String normalizePath(String path) {
        return path == null ? "" : path.trim();
    }
}
