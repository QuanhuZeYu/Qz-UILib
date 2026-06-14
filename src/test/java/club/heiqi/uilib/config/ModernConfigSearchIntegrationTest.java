package club.heiqi.uilib.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * {@code collectDirtyMarkers}（收集已创建叶子绑定的脏状态，不触发延迟加载）
 * 与 {@code changeListener → refreshSearchState}（草稿变更 → 刷新索引脏标记 → 刷新过滤结果）。</p>
 *
 * <p>纯 JVM 测试：不实例化 {@code ModernConfigTemplateScreen}，而是复制其集成逻辑并验证组件协作。</p>
 */
public class ModernConfigSearchIntegrationTest {

    /**
     * NestedCategoryBinding 构造阶段不应立即创建所有叶子 binding，
     * 进入初始区块后才创建当前可见路径所需的叶子 binding。
     */
    @Test
    public void nestedDescendantsAreCreatedLazily() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("server.host", "localhost")
                .set("server.port", 8080)
                .set("server.name", "prod")
                .set("debug", true);
        ModernNestedCategoryBinding binding = new ModernNestedCategoryBinding(config, config.asImmutable(),
                Collections.<String, ModernConfigTemplateScreen.FieldSpec>emptyMap(), null);

        List<ModernConfigPropertyBindings.ConfigPropertyBinding> descendants =
                binding.resolveDescendantBindings("");
        assertTrue("构造阶段不应创建叶子 binding", descendants.isEmpty());

        binding.createSection(UiDocument.create(), ForgeConfigTemplateScreen.Theme.defaultTheme());
        descendants = binding.resolveDescendantBindings("");

        Set<String> paths = new HashSet<String>();
        for (ModernConfigPropertyBindings.ConfigPropertyBinding descendant : descendants) {
            paths.add(descendant.getPath());
        }
        assertTrue("初始根区块应包含直接叶子 debug", paths.contains("debug"));
        assertFalse("未进入 server 分类前不应创建 server.host", paths.contains("server.host"));

        binding.navigateTo("server");
        descendants = binding.resolveDescendantBindings("");
        paths.clear();
        for (ModernConfigPropertyBindings.ConfigPropertyBinding descendant : descendants) {
            paths.add(descendant.getPath());
        }
        assertTrue("进入 server 后应包含 server.host", paths.contains("server.host"));
        assertTrue("进入 server 后应包含 server.port", paths.contains("server.port"));
        assertTrue("进入 server 后应包含 server.name", paths.contains("server.name"));
    }

    /**
     * 搜索索引的 dirty provider 不应为了收集脏标记而展开嵌套 binding。
     */
    @Test
    public void dirtyProviderDoesNotExpandNestedBindings() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("server.host", "localhost")
                .set("server.port", 8080);
        List<ModernConfigPropertyBindings.ConfigPropertyBinding> bindings =
                ModernConfigPropertyBindings.createBindings(config, Collections
                        .<ModernConfigTemplateScreen.FieldSpec>emptyList(), null);

        Map<String, Boolean> dirtyByPath = collectDirtyMarkers(bindings);

        assertTrue("未渲染前不应产生任何叶子脏标记", dirtyByPath.isEmpty());
        assertTrue("聚合 binding 应保留", bindings.get(0) instanceof ModernNestedCategoryBinding);
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

        final ModernConfigSearchIndex[] indexHolder = new ModernConfigSearchIndex[1];
        final ModernConfigSearchFilter[] filterHolder = new ModernConfigSearchFilter[1];

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
        ModernNestedCategoryBinding nestedBinding = findNestedBinding(bindings);
        assertNotNull(nestedBinding);
        nestedBinding.createSection(UiDocument.create(), ForgeConfigTemplateScreen.Theme.defaultTheme());
        nestedBinding.navigateTo("server");

        ModernConfigSearchIndex index = new ModernConfigSearchIndex(new ModernConfigSearchIndex.DirtyStateProvider() {
            @Override
            public Map<String, Boolean> collectDirtyByPath() {
                return collectDirtyMarkers(bindings);
            }
        }, Collections.<String, ModernConfigTemplateScreen.FieldSpec>emptyMap(), config.asImmutable());
        indexHolder[0] = index;
        ModernConfigSearchFilter filter = new ModernConfigSearchFilter(UiDocument.create(), index, null);
        filterHolder[0] = filter;

        filter.applyModifiedOnly(true);
        assertEquals("初始应无脏项", 0, filter.getResultCount());

        ModernConfigPropertyBindings.ConfigPropertyBinding portBinding = findLeafByPath(
                nestedBinding.resolveDescendantBindings(""), "server.port");
        assertNotNull(portBinding);
        assertTrue("server.port 声明了默认值，应可恢复", portBinding.canRestoreDefaultValue());
        portBinding.restoreDefaultValue();

        assertTrue("草稿变更后应至少返回 1 项: " + filter.getResultCount(), filter.getResultCount() >= 1);
        boolean foundDirtyPort = false;
        for (ModernConfigSearchIndex.SearchEntry entry : filter.getResults()) {
            if ("server.port".equals(entry.getPath())) {
                assertTrue("server.port 应标记为脏", entry.isDirty());
                foundDirtyPort = true;
            }
        }
        assertTrue("过滤结果应包含脏的 server.port", foundDirtyPort);

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
        ModernNestedCategoryBinding nestedBinding = findNestedBinding(bindings);
        assertNotNull(nestedBinding);
        nestedBinding.createSection(UiDocument.create(), ForgeConfigTemplateScreen.Theme.defaultTheme());

        ModernConfigSearchIndex index = new ModernConfigSearchIndex(new ModernConfigSearchIndex.DirtyStateProvider() {
            @Override
            public Map<String, Boolean> collectDirtyByPath() {
                return collectDirtyMarkers(bindings);
            }
        }, ModernConfigPropertyBindings.indexFields(fields), config.asImmutable());
        indexHolder[0] = index;

        assertEquals(0, index.search(null, null, true).size());

        findLeafByPath(nestedBinding.resolveDescendantBindings(""), "a").restoreDefaultValue();
        findLeafByPath(nestedBinding.resolveDescendantBindings(""), "b").restoreDefaultValue();

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
     * 复制 ModernConfigTemplateScreen.collectDirtyMarkers 的脏标记收集逻辑。
     */
    private static Map<String, Boolean> collectDirtyMarkers(
            List<ModernConfigPropertyBindings.ConfigPropertyBinding> bindings) {
        Map<String, Boolean> markers = new LinkedHashMap<String, Boolean>();
        for (ModernConfigPropertyBindings.ConfigPropertyBinding binding : bindings) {
            if (binding instanceof ModernNestedCategoryBinding) {
                ((ModernNestedCategoryBinding) binding).collectDirtyMarkers(markers);
                continue;
            }
            markers.put(binding.getPath(), Boolean.valueOf(binding.isDirty()));
        }
        return markers;
    }

    private static ModernNestedCategoryBinding findNestedBinding(
            List<ModernConfigPropertyBindings.ConfigPropertyBinding> bindings) {
        for (ModernConfigPropertyBindings.ConfigPropertyBinding binding : bindings) {
            if (binding instanceof ModernNestedCategoryBinding) {
                return (ModernNestedCategoryBinding) binding;
            }
        }
        return null;
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
