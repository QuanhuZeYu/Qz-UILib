package club.heiqi.uilib.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import club.heiqi.config.Config;
import club.heiqi.config.ConfigFormat;
import club.heiqi.config.ConfigNode;
import club.heiqi.config.MutableConfig;
import club.heiqi.uilib.config.ModernConfigSearchIndex.SearchEntry;
import club.heiqi.uilib.config.ModernConfigSearchIndex.TemplateCategory;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 现代配置搜索索引测试，覆盖递归遍历、查询匹配、类型过滤与 dirty 标记。
 */
public class ModernConfigSearchIndexTest {

    @Test
    public void searchByPathReturnsMatchingEntry() {
        ConfigNode root = buildSampleRoot();
        ModernConfigSearchIndex index = new ModernConfigSearchIndex(
                Collections.<ModernConfigPropertyBindings.ConfigPropertyBinding>emptyList(),
                Collections.<String, ModernConfigTemplateScreen.FieldSpec>emptyMap(), root);

        List<SearchEntry> results = index.search("port", null, false);

        assertEquals(1, results.size());
        assertEquals("server.port", results.get(0).getPath());
    }

    @Test
    public void searchByValueSummaryMatches() {
        ConfigNode root = buildSampleRoot();
        ModernConfigSearchIndex index = new ModernConfigSearchIndex(
                Collections.<ModernConfigPropertyBindings.ConfigPropertyBinding>emptyList(),
                Collections.<String, ModernConfigTemplateScreen.FieldSpec>emptyMap(), root);

        List<SearchEntry> results = index.search("prod", null, false);

        assertEquals(1, results.size());
        assertEquals("server.name", results.get(0).getPath());
    }

    @Test
    public void searchCaseInsensitive() {
        ConfigNode root = buildSampleRoot();
        ModernConfigSearchIndex index = new ModernConfigSearchIndex(
                Collections.<ModernConfigPropertyBindings.ConfigPropertyBinding>emptyList(),
                Collections.<String, ModernConfigTemplateScreen.FieldSpec>emptyMap(), root);

        List<SearchEntry> upper = index.search("PROD", null, false);
        assertEquals(1, upper.size());
    }

    @Test
    public void emptyQueryReturnsAllEntries() {
        ConfigNode root = buildSampleRoot();
        ModernConfigSearchIndex index = new ModernConfigSearchIndex(
                Collections.<ModernConfigPropertyBindings.ConfigPropertyBinding>emptyList(),
                Collections.<String, ModernConfigTemplateScreen.FieldSpec>emptyMap(), root);

        List<SearchEntry> results = index.search(null, null, false);

        // 至少应包含 server, server.host, server.port, server.name, debug, tags 这些非空节点
        assertTrue("空查询应返回所有条目，实际: " + results.size(), results.size() >= 5);
        // 结果按 path 字典序
        for (int i = 1; i < results.size(); i++) {
            assertTrue(results.get(i - 1).getPath().compareTo(results.get(i).getPath()) <= 0);
        }
    }

    @Test
    public void typeFilterKeepsOnlyRequestedCategories() {
        ConfigNode root = buildSampleRoot();
        ModernConfigSearchIndex index = new ModernConfigSearchIndex(
                Collections.<ModernConfigPropertyBindings.ConfigPropertyBinding>emptyList(),
                Collections.<String, ModernConfigTemplateScreen.FieldSpec>emptyMap(), root);

        List<SearchEntry> objects = index.search(null,
                EnumSet.of(TemplateCategory.OBJECT), false);
        for (SearchEntry entry : objects) {
            assertEquals(TemplateCategory.OBJECT, entry.getCategory());
        }
        assertTrue("应至少有一个 OBJECT 条目", !objects.isEmpty());

        List<SearchEntry> strings = index.search(null,
                EnumSet.of(TemplateCategory.STRING), false);
        assertFalse(strings.isEmpty());
        for (SearchEntry entry : strings) {
            assertEquals(TemplateCategory.STRING, entry.getCategory());
        }

        List<SearchEntry> lists = index.search(null,
                EnumSet.of(TemplateCategory.LIST), false);
        assertEquals(1, lists.size());
        assertEquals("tags", lists.get(0).getPath());
    }

    @Test
    public void typeFilterRecognizesRawEditorAndEnhancedPickerHints() {
        MutableConfig mutable = Config.createMutable(ConfigFormat.JSON);
        mutable.set("payload", "{}");
        mutable.set("color", "#FF8800");
        ConfigNode root = mutable.asImmutable();

        Map<String, ModernConfigTemplateScreen.FieldSpec> fields =
                new LinkedHashMap<String, ModernConfigTemplateScreen.FieldSpec>();
        fields.put("payload", new ModernConfigTemplateScreen.FieldSpec("payload").setTemplateHint("json"));
        fields.put("color", new ModernConfigTemplateScreen.FieldSpec("color").setTemplateHint("color"));

        ModernConfigSearchIndex index = new ModernConfigSearchIndex(
                Collections.<ModernConfigPropertyBindings.ConfigPropertyBinding>emptyList(), fields, root);

        List<SearchEntry> raw = index.search(null, EnumSet.of(TemplateCategory.RAW_EDITOR), false);
        assertEquals(1, raw.size());
        assertEquals("payload", raw.get(0).getPath());

        List<SearchEntry> picker = index.search(null, EnumSet.of(TemplateCategory.ENHANCED_PICKER), false);
        assertEquals(1, picker.size());
        assertEquals("color", picker.get(0).getPath());
    }

    @Test
    public void modifiedOnlyKeepsDirtyEntries() {
        MutableConfig mutable = Config.createMutable(ConfigFormat.JSON);
        mutable.set("server.port", 8080);
        mutable.set("server.name", "prod");
        ConfigNode root = mutable.asImmutable();

        ModernConfigPropertyBindings.ConfigPropertyBinding dirtyBinding = mockBinding(mutable, "server.port", true);
        ModernConfigSearchIndex index = new ModernConfigSearchIndex(
                Collections.singletonList(dirtyBinding),
                Collections.<String, ModernConfigTemplateScreen.FieldSpec>emptyMap(), root);

        List<SearchEntry> modified = index.search(null, null, true);
        assertEquals(1, modified.size());
        assertEquals("server.port", modified.get(0).getPath());
        assertTrue(modified.get(0).isDirty());

        List<SearchEntry> all = index.search(null, null, false);
        assertTrue(all.size() > 1);
    }

    @Test
    public void subtreeRootTracksNearestMapAncestor() {
        ConfigNode root = buildSampleRoot();
        ModernConfigSearchIndex index = new ModernConfigSearchIndex(
                Collections.<ModernConfigPropertyBindings.ConfigPropertyBinding>emptyList(),
                Collections.<String, ModernConfigTemplateScreen.FieldSpec>emptyMap(), root);

        SearchEntry serverPort = findEntry(index, "server.port");
        assertEquals("server", serverPort.getSubtreeRoot());

        SearchEntry serverHost = findEntry(index, "server.host");
        assertEquals("server", serverHost.getSubtreeRoot());

        SearchEntry debug = findEntry(index, "debug");
        // debug 在根 map 下，subtreeRoot 应为根 map 的 path 即空串
        assertEquals("", debug.getSubtreeRoot());
    }

    @Test
    public void valueSummaryIsTruncatedAtEightyChars() {
        MutableConfig mutable = Config.createMutable(ConfigFormat.JSON);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 120; i++) {
            sb.append('x');
        }
        mutable.set("long", sb.toString());
        ConfigNode root = mutable.asImmutable();

        ModernConfigSearchIndex index = new ModernConfigSearchIndex(
                Collections.<ModernConfigPropertyBindings.ConfigPropertyBinding>emptyList(),
                Collections.<String, ModernConfigTemplateScreen.FieldSpec>emptyMap(), root);

        SearchEntry entry = findEntry(index, "long");
        assertTrue("截断后应不超过 80 字符: " + entry.getValueSummary().length(),
                entry.getValueSummary().length() <= 80);
        assertTrue(entry.getValueSummary().endsWith("..."));
    }

    @Test
    public void mapAndListValueSummariesUseStructuredForm() {
        ConfigNode root = buildSampleRoot();
        ModernConfigSearchIndex index = new ModernConfigSearchIndex(
                Collections.<ModernConfigPropertyBindings.ConfigPropertyBinding>emptyList(),
                Collections.<String, ModernConfigTemplateScreen.FieldSpec>emptyMap(), root);

        SearchEntry server = findEntry(index, "server");
        assertTrue(server.getValueSummary().startsWith("object ("));
        assertTrue(server.getValueSummary().contains("keys"));

        SearchEntry tags = findEntry(index, "tags");
        assertTrue(tags.getValueSummary().startsWith("list ("));
        assertTrue(tags.getValueSummary().contains("items"));
    }

    @Test
    public void rebuildReindexesNewRoot() {
        MutableConfig first = Config.createMutable(ConfigFormat.JSON);
        first.set("a", 1);
        ConfigNode firstRoot = first.asImmutable();

        ModernConfigSearchIndex index = new ModernConfigSearchIndex(
                Collections.<ModernConfigPropertyBindings.ConfigPropertyBinding>emptyList(),
                Collections.<String, ModernConfigTemplateScreen.FieldSpec>emptyMap(), firstRoot);
        assertFalse(index.search("a", null, false).isEmpty());
        assertTrue(index.search("b", null, false).isEmpty());

        MutableConfig second = Config.createMutable(ConfigFormat.JSON);
        second.set("b", 2);
        index.rebuild(second.asImmutable());

        assertFalse(index.search("b", null, false).isEmpty());
        assertTrue(index.search("a", null, false).isEmpty());
    }

    @Test
    public void refreshDirtyMarkersUpdatesDirtyState() {
        MutableConfig mutable = Config.createMutable(ConfigFormat.JSON);
        mutable.set("server.port", 8080);
        ConfigNode root = mutable.asImmutable();

        boolean[] dirtyFlag = new boolean[] { false };
        ModernConfigPropertyBindings.ConfigPropertyBinding dynamicBinding =
                mockDynamicDirtyBinding(mutable, "server.port", dirtyFlag);

        ModernConfigSearchIndex index = new ModernConfigSearchIndex(
                Collections.singletonList(dynamicBinding),
                Collections.<String, ModernConfigTemplateScreen.FieldSpec>emptyMap(), root);

        assertFalse(findEntry(index, "server.port").isDirty());

        dirtyFlag[0] = true;
        index.refreshDirtyMarkers();
        assertTrue(findEntry(index, "server.port").isDirty());

        dirtyFlag[0] = false;
        index.refreshDirtyMarkers();
        assertFalse(findEntry(index, "server.port").isDirty());
    }

    @Test
    public void templateTypeLabelUsesHintWhenAvailable() {
        MutableConfig mutable = Config.createMutable(ConfigFormat.JSON);
        mutable.set("payload", "{}");
        ConfigNode root = mutable.asImmutable();

        Map<String, ModernConfigTemplateScreen.FieldSpec> fields =
                Collections.singletonMap("payload",
                        new ModernConfigTemplateScreen.FieldSpec("payload").setTemplateHint("json-editor"));

        ModernConfigSearchIndex index = new ModernConfigSearchIndex(
                Collections.<ModernConfigPropertyBindings.ConfigPropertyBinding>emptyList(), fields, root);

        SearchEntry payload = findEntry(index, "payload");
        assertEquals("json-editor", payload.getTemplateTypeLabel());
    }

    @Test
    public void templateTypeLabelFallsBackToChineseForPrimitives() {
        ConfigNode root = buildSampleRoot();
        ModernConfigSearchIndex index = new ModernConfigSearchIndex(
                Collections.<ModernConfigPropertyBindings.ConfigPropertyBinding>emptyList(),
                Collections.<String, ModernConfigTemplateScreen.FieldSpec>emptyMap(), root);

        assertEquals("数字", findEntry(index, "server.port").getTemplateTypeLabel());
        assertEquals("字符串", findEntry(index, "server.name").getTemplateTypeLabel());
        assertEquals("布尔", findEntry(index, "debug").getTemplateTypeLabel());
        assertEquals("对象", findEntry(index, "server").getTemplateTypeLabel());
        assertEquals("列表", findEntry(index, "tags").getTemplateTypeLabel());
    }

    @Test
    public void listItemsAreIndexedWithPathSubscript() {
        ConfigNode root = buildSampleRoot();
        ModernConfigSearchIndex index = new ModernConfigSearchIndex(
                Collections.<ModernConfigPropertyBindings.ConfigPropertyBinding>emptyList(),
                Collections.<String, ModernConfigTemplateScreen.FieldSpec>emptyMap(), root);

        // tags[0]、tags[1]、tags[2] 都应被索引
        assertNotNull(findEntryOrNull(index, "tags[0]"));
        assertNotNull(findEntryOrNull(index, "tags[1]"));
        assertNotNull(findEntryOrNull(index, "tags[2]"));
        // list 子项的 subtreeRoot 应继承自父 MAP（这里是根 map，subtreeRoot 为空）
        assertEquals("", findEntry(index, "tags[0]").getSubtreeRoot());
    }

    private static ConfigNode buildSampleRoot() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON);
        config.set("server.host", "localhost");
        config.set("server.port", 8080);
        config.set("server.name", "prod");
        config.set("debug", true);
        config.set("tags", Arrays.asList("alpha", "beta", "gamma"));
        return config.asImmutable();
    }

    private static SearchEntry findEntry(ModernConfigSearchIndex index, String path) {
        SearchEntry entry = findEntryOrNull(index, path);
        assertNotNull("未找到路径 " + path + " 对应的索引条目", entry);
        return entry;
    }

    private static SearchEntry findEntryOrNull(ModernConfigSearchIndex index, String path) {
        for (SearchEntry entry : index.getEntries()) {
            if (path.equals(entry.getPath())) {
                return entry;
            }
        }
        return null;
    }

    private static ModernConfigPropertyBindings.ConfigPropertyBinding mockBinding(MutableConfig config,
            String path, boolean dirty) {
        return new ModernConfigPropertyBindings.ConfigPropertyBinding(config, path, config.get(path), null,
                ModernConfigTypeInference.infer(path, config.get(path), null), null) {
            @Override
            protected ElementNode createEditorElement(UiDocument document, ForgeConfigTemplateScreen.Theme theme) {
                return null;
            }

            @Override
            boolean isDirty() {
                return dirty;
            }

            @Override
            void restoreCurrentValue() {
            }

            @Override
            void restoreDefaultValue() {
            }

            @Override
            String validateDraft() {
                return "";
            }

            @Override
            void applyDraft() {
            }
        };
    }

    private static ModernConfigPropertyBindings.ConfigPropertyBinding mockDynamicDirtyBinding(
            MutableConfig config, String path, final boolean[] dirtyFlag) {
        return new ModernConfigPropertyBindings.ConfigPropertyBinding(config, path, config.get(path), null,
                ModernConfigTypeInference.infer(path, config.get(path), null), null) {
            @Override
            protected ElementNode createEditorElement(UiDocument document, ForgeConfigTemplateScreen.Theme theme) {
                return null;
            }

            @Override
            boolean isDirty() {
                return dirtyFlag[0];
            }

            @Override
            void restoreCurrentValue() {
            }

            @Override
            void restoreDefaultValue() {
            }

            @Override
            String validateDraft() {
                return "";
            }

            @Override
            void applyDraft() {
            }
        };
    }
}
