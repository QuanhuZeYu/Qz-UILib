package club.heiqi.uilib.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import club.heiqi.config.ConfigNode;
import club.heiqi.config.MutableConfig;
import club.heiqi.uilib.ui.control.DocumentButtonActionEvent;
import club.heiqi.uilib.ui.control.DocumentButtonActionHandler;
import club.heiqi.uilib.ui.control.DocumentButtonControl;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 现代配置普通对象字段绑定，以卡片方式内联渲染对象子项。
 */
final class ModernObjectPropertyBinding extends ModernConfigPropertyBindings.ConfigPropertyBinding {

    private final ObjectBindingContext context;
    private final int inlineDepth;

    ModernObjectPropertyBinding(MutableConfig config, String path, ConfigNode node,
            ModernConfigTemplateScreen.FieldSpec fieldSpec, ModernConfigTypeInference.Result inference,
            ModernConfigPropertyBindings.ChangeListener changeListener) {
        this(config, path, node, fieldSpec, inference, changeListener,
                new StandaloneObjectBindingContext(config, path, changeListener), 1);
    }

    ModernObjectPropertyBinding(MutableConfig config, String path, ConfigNode node,
            ModernConfigTemplateScreen.FieldSpec fieldSpec, ModernConfigTypeInference.Result inference,
            ModernConfigPropertyBindings.ChangeListener changeListener, ObjectBindingContext context, int inlineDepth) {
        super(config, path, node, fieldSpec, inference, changeListener);
        this.context = context;
        this.inlineDepth = Math.max(1, inlineDepth);
    }

    @Override
    protected ElementNode createEditorElement(UiDocument document, ForgeConfigTemplateScreen.Theme theme) {
        ElementNode root = document.div();
        root.setAttribute("data-modern-config-object-path", getPath());
        root.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(8));

        ConfigNode node = getCurrentNode();
        if (node == null || node.getType() != ConfigNode.NodeType.MAP || node.asMap() == null) {
            appendObjectSummary(document, root, "当前节点不是普通对象，已降级为摘要展示。");
            return root;
        }
        if (inlineDepth >= context.getMaxInlineDepth()) {
            appendDepthLimitNotice(document, root, theme);
            return root;
        }

        List<String> childPaths = context.collectDirectChildPaths(getPath());
        if (childPaths.isEmpty()) {
            appendObjectSummary(document, root, "当前对象没有子项。");
            return root;
        }
        for (String childPath : childPaths) {
            appendChildCard(document, root, theme, childPath);
        }
        return root;
    }

    @Override
    protected String buildHelperText() {
        String inherited = super.buildHelperText();
        String suffix = "普通对象内联编辑，默认最多展开 "
                + ModernConfigPropertyBindings.DEFAULT_OBJECT_INLINE_DEPTH + " 层。";
        return inherited.isEmpty() ? suffix : inherited + " " + suffix;
    }

    @Override
    boolean isDirty() {
        return getDirtyCount() > 0;
    }

    @Override
    int getDirtyCount() {
        int dirtyCount = 0;
        for (ModernConfigPropertyBindings.ConfigPropertyBinding binding : context.resolveDescendantBindings(getPath())) {
            dirtyCount += binding.getDirtyCount();
        }
        return dirtyCount;
    }

    @Override
    void restoreCurrentValue() {
        for (ModernConfigPropertyBindings.ConfigPropertyBinding binding : context.resolveDescendantBindings(getPath())) {
            binding.restoreCurrentValue();
        }
    }

    @Override
    void restoreDefaultValue() {
        for (ModernConfigPropertyBindings.ConfigPropertyBinding binding : context.resolveDescendantBindings(getPath())) {
            if (binding.canRestoreDefaultValue()) {
                binding.restoreDefaultValue();
            }
        }
    }

    @Override
    String validateDraft() {
        for (ModernConfigPropertyBindings.ConfigPropertyBinding binding : context.resolveDescendantBindings(getPath())) {
            String validationError = binding.validateDraft();
            if (validationError != null && !validationError.isEmpty()) {
                return binding.getDisplayName() + "：" + validationError;
            }
        }
        return null;
    }

    @Override
    void applyDraft() {
        for (ModernConfigPropertyBindings.ConfigPropertyBinding binding : context.resolveDescendantBindings(getPath())) {
            if (binding.isDirty()) {
                binding.applyDraft();
            }
        }
    }

    @Override
    boolean canRestoreDefaultValue() {
        for (ModernConfigPropertyBindings.ConfigPropertyBinding binding : context.resolveDescendantBindings(getPath())) {
            if (binding.canRestoreDefaultValue()) {
                return true;
            }
        }
        return false;
    }

    private void appendChildCard(UiDocument document, ElementNode root, ForgeConfigTemplateScreen.Theme theme,
            String childPath) {
        ConfigNode childNode = getConfig().get(childPath);
        ModernConfigTemplateScreen.FieldSpec childFieldSpec = context.resolveFieldSpec(childPath);
        ModernConfigTypeInference.Result inference = ModernConfigTypeInference.infer(childPath, childNode, childFieldSpec);
        if (inference.getTemplateType() == ModernConfigTypeInference.TemplateType.OBJECT) {
            ModernObjectPropertyBinding childBinding = new ModernObjectPropertyBinding(getConfig(), childPath, childNode,
                    childFieldSpec, inference, null, context, inlineDepth + 1);
            root.append(childBinding.createCard(document, theme));
            return;
        }
        ModernConfigPropertyBindings.ConfigPropertyBinding binding = context.resolveBinding(childPath);
        if (binding == null) {
            binding = ModernConfigPropertyBindings.createBinding(getConfig(), childPath, childNode, childFieldSpec,
                    inference, null);
        }
        root.append(binding.createCard(document, theme));
    }

    private void appendDepthLimitNotice(UiDocument document, ElementNode root, ForgeConfigTemplateScreen.Theme theme) {
        ElementNode notice = document.div();
        notice.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(8))
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(0xFF0F172A)
                .setBorderColor(0xFF334155)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(10))
                .setTextColor(0xFFCBD5E1);
        notice.appendText("已达到默认内联深度限制；" + ModernConfigPropertyBindings.formatSummary(getCurrentNode()));
        DocumentButtonControl expandButton = new DocumentButtonControl(document, "展开编辑")
                .setBackgroundColors(0xFF334155, 0xFF475569, 0xFF1E293B)
                .setFocusBorderColor(theme.focusBorderColor)
                .setTextColors(0xFFE2E8F0, 0xFF64748B)
                .setActionHandler(new DocumentButtonActionHandler() {
                    @Override
                    public void onAction(DocumentButtonActionEvent event) {
                        context.navigateTo(getPath());
                    }
                });
        expandButton.getElement().setAttribute("data-modern-config-expand-path", getPath());
        expandButton.getElement().style().setPadding(UiStyleLength.px(7));
        notice.append(expandButton.getElement());
        root.append(notice);
    }

    private void appendObjectSummary(UiDocument document, ElementNode root, String message) {
        ElementNode summary = document.div();
        summary.style()
                .setPadding(UiStyleLength.px(8))
                .setTextColor(0xFF94A3B8);
        summary.appendText(message);
        root.append(summary);
    }

    /**
     * 普通对象绑定依赖的嵌套上下文。
     */
    interface ObjectBindingContext {

        /**
         * 收集指定对象路径的直接子路径。
         *
         * @param path 对象路径
         * @return 直接子路径列表
         */
        List<String> collectDirectChildPaths(String path);

        /**
         * 解析指定路径的叶子绑定。
         *
         * @param path 配置路径
         * @return 叶子绑定；不存在时返回 null
         */
        ModernConfigPropertyBindings.ConfigPropertyBinding resolveBinding(String path);

        /**
         * 解析指定对象路径下的全部后代叶子绑定。
         *
         * @param path 对象路径
         * @return 后代叶子绑定列表
         */
        List<ModernConfigPropertyBindings.ConfigPropertyBinding> resolveDescendantBindings(String path);

        /**
         * 解析字段规格。
         *
         * @param path 配置路径
         * @return 字段规格；不存在时返回 null
         */
        ModernConfigTemplateScreen.FieldSpec resolveFieldSpec(String path);

        /**
         * 导航到指定对象路径。
         *
         * @param path 对象路径
         */
        void navigateTo(String path);

        /**
         * 返回最大内联深度。
         *
         * @return 最大内联深度
         */
        int getMaxInlineDepth();
    }

    private static final class StandaloneObjectBindingContext implements ObjectBindingContext {

        private final MutableConfig config;
        private final Map<String, ModernConfigPropertyBindings.ConfigPropertyBinding> bindingsByPath =
                new LinkedHashMap<String, ModernConfigPropertyBindings.ConfigPropertyBinding>();

        private StandaloneObjectBindingContext(MutableConfig config, String rootPath,
                ModernConfigPropertyBindings.ChangeListener changeListener) {
            this.config = config;
            collectLeafBindings(rootPath, config.get(rootPath), changeListener);
        }

        @Override
        public List<String> collectDirectChildPaths(String path) {
            ConfigNode node = config.get(path);
            if (node == null || node.getType() != ConfigNode.NodeType.MAP || node.asMap() == null) {
                return Collections.emptyList();
            }
            List<String> childPaths = new ArrayList<String>();
            List<String> keys = new ArrayList<String>(node.asMap().keySet());
            Collections.sort(keys);
            for (String key : keys) {
                childPaths.add(resolveChildPath(path, key));
            }
            return childPaths;
        }

        @Override
        public ModernConfigPropertyBindings.ConfigPropertyBinding resolveBinding(String path) {
            return bindingsByPath.get(normalizePath(path));
        }

        @Override
        public List<ModernConfigPropertyBindings.ConfigPropertyBinding> resolveDescendantBindings(String path) {
            List<ModernConfigPropertyBindings.ConfigPropertyBinding> bindings =
                    new ArrayList<ModernConfigPropertyBindings.ConfigPropertyBinding>();
            for (Map.Entry<String, ModernConfigPropertyBindings.ConfigPropertyBinding> entry : bindingsByPath.entrySet()) {
                if (isDescendantPath(path, entry.getKey())) {
                    bindings.add(entry.getValue());
                }
            }
            return bindings;
        }

        @Override
        public ModernConfigTemplateScreen.FieldSpec resolveFieldSpec(String path) {
            return null;
        }

        @Override
        public void navigateTo(String path) {
        }

        @Override
        public int getMaxInlineDepth() {
            return ModernConfigPropertyBindings.DEFAULT_OBJECT_INLINE_DEPTH;
        }

        private void collectLeafBindings(String path, ConfigNode node,
                ModernConfigPropertyBindings.ChangeListener changeListener) {
            if (node == null) {
                return;
            }
            if (node.getType() == ConfigNode.NodeType.MAP && node.asMap() != null
                    && !shouldUseLeafMapBinding(path, node)) {
                List<String> keys = new ArrayList<String>(node.asMap().keySet());
                Collections.sort(keys);
                for (String key : keys) {
                    collectLeafBindings(resolveChildPath(path, key), node.asMap().get(key), changeListener);
                }
                return;
            }
            ModernConfigTypeInference.Result inference = ModernConfigTypeInference.infer(path, node, null);
            bindingsByPath.put(path, ModernConfigPropertyBindings.createBinding(config, path, node, null, inference,
                    changeListener));
        }

        private static boolean shouldUseLeafMapBinding(String path, ConfigNode node) {
            if (normalizePath(path).isEmpty()) {
                return false;
            }
            ModernConfigTypeInference.Result inference = ModernConfigTypeInference.infer(path, node, null);
            return inference.getTemplateType() == ModernConfigTypeInference.TemplateType.PRESET_SELECTOR;
        }
    }

    private static String resolveChildPath(String parentPath, String childName) {
        String parent = normalizePath(parentPath);
        return parent.isEmpty() ? childName : parent + "." + childName;
    }

    private static boolean isDescendantPath(String parentPath, String childPath) {
        String parent = normalizePath(parentPath);
        String child = normalizePath(childPath);
        return parent.isEmpty() ? !child.isEmpty() : child.startsWith(parent + ".");
    }

    private static String normalizePath(String path) {
        return path == null ? "" : path.trim();
    }
}
