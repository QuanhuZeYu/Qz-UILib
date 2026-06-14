package club.heiqi.uilib.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import club.heiqi.config.Config;
import club.heiqi.config.ConfigFormat;
import club.heiqi.config.MutableConfig;
import club.heiqi.uilib.ui.dom.UiDocument;

/**
 * 现代配置页搜索集成链路间接测试。
 *
 * <p>验证 {@code ModernConfigTemplateScreen} 中无法直接测试的集成链路：
 * {@code collectIndexBindings}（展开 {@link ModernNestedCategoryBinding} 的嵌套子 binding）
 * 与 {@code changeListener → refreshSearchState}（草稿变更 → 刷新索引脏标记 → 刷新过滤结果）。</p>
 *
 * <p>纯 JVM 测试：不实例化 {@code ModernConfigTemplateScreen}，而是复制其集成逻辑并验证组件协作。</p>
 */
public class ModernConfigSearchIntegrationTest {

    /**
     * NestedCategoryBinding 的 resolveDescendantBindings 应展开所有叶子 binding，
     * 使搜索索引能覆盖嵌套结构的每个叶子路径。
     */
    @Test
    public void nestedDescendantsCoverAllLeafPaths() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("server.host", "localhost")
                .set("server.port", 8080)
                .set("server.name", "prod")
                .set("debug", true);
        ModernNestedCategoryBinding binding = new ModernNestedCategoryBinding(config, config.asImmutable(),
                Collections.<String, ModernConfigTemplateScreen.FieldSpec>emptyMap(), null);

        List<ModernConfigPropertyBindings.ConfigPropertyBinding> descendants =
                binding.resolveDescendantBindings("");

        Set<String> paths = new HashSet<String>();
        for (ModernConfigPropertyBindings.ConfigPropertyBinding descendant : descendants) {
            paths.add(descendant.getPath());
        }
        assertTrue("应包含 server.host", paths.contains("server.host"));
        assertTrue("应包含 server.port", paths.contains("server.port"));
        assertTrue("应包含 server.name", paths.contains("server.name"));
        assertTrue("应包含 debug", paths.contains("debug"));
    }

    /**
     * collectIndexBindings 模拟：展开嵌套 binding 后，结果同时包含聚合 binding 与所有叶子。
     */
    @Test
    public void collectIndexBindingsExpandsNestedAndKeepsAggregator() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("server.host", "localhost")
                .set("server.port", 8080);
        List<ModernConfigPropertyBindings.ConfigPropertyBinding> bindings =
                ModernConfigPropertyBindings.createBindings(config, Collections
                        .<ModernConfigTemplateScreen.FieldSpec>emptyList(), null);

        // 模拟 Screen.collectIndexBindings 的逻辑
        List<ModernConfigPropertyBindings.ConfigPropertyBinding> collected = collectIndexBindings(bindings);

        // 聚合 binding（NestedCategoryBinding，path="") + 叶子 binding
        assertFalse(collected.isEmpty());
        boolean hasAggregator = false;
        Set<String> leafPaths = new HashSet<String>();
        for (ModernConfigPropertyBindings.ConfigPropertyBinding b : collected) {
            if (b instanceof ModernNestedCategoryBinding) {
                hasAggregator = true;
            } else if (!b.getPath().isEmpty()) {
                leafPaths.add(b.getPath());
            }
        }
        assertTrue("应包含聚合 binding", hasAggregator);
        assertTrue("应包含叶子 server.host", leafPaths.contains("server.host"));
        assertTrue("应包含叶子 server.port", leafPaths.contains("server.port"));
    }

    /**
     * 集成链路：叶子 binding 草稿变更 → changeListener → refreshSearchState → 过滤结果反映新脏状态。
     *
     * <p>复制 {@code ModernConfigTemplateScreen.onDraftChangedInternal} 的链路：
     * listener.onDraftChanged → searchIndex.refreshDirtyMarkers + searchFilter.refresh。</p>
     */
    @Test
    public void changeListenerPipelineRefreshesFilterResults() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("server.port", 8080)
                .set("debug", true);
        ModernConfigTemplateScreen.FieldSpec portSpec = new ModernConfigTemplateScreen.FieldSpec("server.port")
                .setDefaultValue(9999);

        // 占位 holder，使 listener 能在 Screen 构造完成后反向调用索引与过滤组件
        final ModernConfigSearchIndex[] indexHolder = new ModernConfigSearchIndex[1];
        final ModernConfigSearchFilter[] filterHolder = new ModernConfigSearchFilter[1];

        // 复制 Screen.onDraftChangedInternal → refreshSearchState 链路
        ModernConfigPropertyBindings.ChangeListener listener = new ModernConfigPropertyBindings.ChangeListener() {
            @Override
            public void onDraftChanged() {
                if (indexHolder[0] != null) {
                    indexHolder[0].refreshDirtyMarkers();
                }
                if (filterHolder[0] != null) {
                    filterHolder[0].refresh();
                }
            }
        };

        List<ModernConfigPropertyBindings.ConfigPropertyBinding> bindings =
                ModernConfigPropertyBindings.createBindings(config,
                        Collections.singletonList(portSpec), listener);
        List<ModernConfigPropertyBindings.ConfigPropertyBinding> expanded = collectIndexBindings(bindings);

        ModernConfigSearchIndex index = new ModernConfigSearchIndex(expanded,
                Collections.<String, ModernConfigTemplateScreen.FieldSpec>emptyMap(), config.asImmutable());
        indexHolder[0] = index;
        ModernConfigSearchFilter filter = new ModernConfigSearchFilter(UiDocument.create(), index, null);
        filterHolder[0] = filter;

        // 初始无脏项：只看已修改应返回 0
        filter.applyModifiedOnly(true);
        assertEquals("初始应无脏项", 0, filter.getResultCount());

        // 找到 server.port 叶子 binding，触发草稿变更（restoreDefaultValue 改 draft 并 notifyDraftChanged）
        ModernConfigPropertyBindings.ConfigPropertyBinding portBinding = findLeafByPath(expanded, "server.port");
        assertNotNull(portBinding);
        assertTrue("server.port 声明了默认值，应可恢复", portBinding.canRestoreDefaultValue());
        portBinding.restoreDefaultValue();

        // listener 链路应已刷新索引脏标记 + 过滤结果
        assertTrue("草稿变更后应至少返回 1 项: " + filter.getResultCount(), filter.getResultCount() >= 1);
        boolean foundDirtyPort = false;
        for (ModernConfigSearchIndex.SearchEntry entry : filter.getResults()) {
            if ("server.port".equals(entry.getPath())) {
                assertTrue("server.port 应标记为脏", entry.isDirty());
                foundDirtyPort = true;
            }
        }
        assertTrue("过滤结果应包含脏的 server.port", foundDirtyPort);

        // 清除只看已修改后，结果应回到全量
        filter.applyModifiedOnly(false);
        assertTrue(filter.getResultCount() > 1);
    }

    /**
     * 集成链路：多个叶子 binding 变更时，refreshSearchState 应统一刷新所有脏标记。
     */
    @Test
    public void multipleLeafChangesAreReflectedAfterRefresh() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("a", 1)
                .set("b", 2)
                .set("c", 3);
        List<ModernConfigTemplateScreen.FieldSpec> fields = Arrays.asList(
                new ModernConfigTemplateScreen.FieldSpec("a").setDefaultValue(10),
                new ModernConfigTemplateScreen.FieldSpec("b").setDefaultValue(20));

        final ModernConfigSearchIndex[] indexHolder = new ModernConfigSearchIndex[1];
        ModernConfigPropertyBindings.ChangeListener listener = new ModernConfigPropertyBindings.ChangeListener() {
            @Override
            public void onDraftChanged() {
                if (indexHolder[0] != null) {
                    indexHolder[0].refreshDirtyMarkers();
                }
            }
        };

        List<ModernConfigPropertyBindings.ConfigPropertyBinding> bindings =
                ModernConfigPropertyBindings.createBindings(config, fields, listener);
        List<ModernConfigPropertyBindings.ConfigPropertyBinding> expanded = collectIndexBindings(bindings);
        ModernConfigSearchIndex index = new ModernConfigSearchIndex(expanded,
                ModernConfigPropertyBindings.indexFields(fields), config.asImmutable());
        indexHolder[0] = index;

        // 初始全部不脏
        assertEquals(0, index.search(null, null, true).size());

        // 两个叶子 binding 改变
        findLeafByPath(expanded, "a").restoreDefaultValue();
        findLeafByPath(expanded, "b").restoreDefaultValue();

        // refreshDirtyMarkers 在每次 listener 触发时已被调用，脏项应有 2 个
        List<ModernConfigSearchIndex.SearchEntry> dirty = index.search(null, null, true);
        assertEquals(2, dirty.size());
        Set<String> dirtyPaths = new HashSet<String>();
        for (ModernConfigSearchIndex.SearchEntry entry : dirty) {
            dirtyPaths.add(entry.getPath());
        }
        assertTrue(dirtyPaths.contains("a"));
        assertTrue(dirtyPaths.contains("b"));
    }

    /**
     * 复制 ModernConfigTemplateScreen.collectIndexBindings 的展开逻辑。
     */
    private static List<ModernConfigPropertyBindings.ConfigPropertyBinding> collectIndexBindings(
            List<ModernConfigPropertyBindings.ConfigPropertyBinding> bindings) {
        List<ModernConfigPropertyBindings.ConfigPropertyBinding> all =
                new ArrayList<ModernConfigPropertyBindings.ConfigPropertyBinding>();
        for (ModernConfigPropertyBindings.ConfigPropertyBinding binding : bindings) {
            if (binding instanceof ModernNestedCategoryBinding) {
                all.addAll(((ModernNestedCategoryBinding) binding).resolveDescendantBindings(""));
            }
            all.add(binding);
        }
        return all;
    }

    private static ModernConfigPropertyBindings.ConfigPropertyBinding findLeafByPath(
            List<ModernConfigPropertyBindings.ConfigPropertyBinding> bindings, String path) {
        for (ModernConfigPropertyBindings.ConfigPropertyBinding binding : bindings) {
            if (path.equals(binding.getPath()) && !(binding instanceof ModernNestedCategoryBinding)) {
                return binding;
            }
        }
        return null;
    }
}
