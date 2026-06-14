package club.heiqi.uilib.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

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
 * 现代配置页搜索过滤组件测试，覆盖查询命中、类型过滤、只看已修改、跳转回调与刷新。
 *
 * <p>纯 JVM 测试：直接调用 {@link ModernConfigSearchFilter} 的 apply* 纯方法，
 * 不实例化 {@code GuiScreen}/{@code BaseScreen}，不依赖 Minecraft 类。</p>
 */
public class ModernConfigSearchFilterTest {

    @Test
    public void applyQueryReturnsMatchingEntries() {
        ModernConfigSearchFilter filter = createFilter(buildSampleRoot(), null);

        filter.applyQuery("port");

        assertEquals(1, filter.getResultCount());
        assertEquals("server.port", filter.getResults().get(0).getPath());
    }

    @Test
    public void applyQueryMatchesValueSummary() {
        ModernConfigSearchFilter filter = createFilter(buildSampleRoot(), null);

        filter.applyQuery("prod");

        assertEquals(1, filter.getResultCount());
        assertEquals("server.name", filter.getResults().get(0).getPath());
    }

    @Test
    public void emptyQueryReturnsAllEntries() {
        ModernConfigSearchFilter filter = createFilter(buildSampleRoot(), null);
        int initialCount = filter.getResultCount();
        assertTrue("初始应返回全部条目: " + initialCount, initialCount >= 5);

        filter.applyQuery("");
        assertEquals(initialCount, filter.getResultCount());

        filter.applyQuery(null);
        assertEquals(initialCount, filter.getResultCount());
    }

    @Test
    public void applyTypeFilterRestrictsToSingleCategory() {
        ModernConfigSearchFilter filter = createFilter(buildSampleRoot(), null);

        filter.applyTypeFilter(EnumSet.of(TemplateCategory.NUMBER));

        assertFalse(filter.getResultCount() == 0);
        for (SearchEntry entry : filter.getResults()) {
            assertEquals(TemplateCategory.NUMBER, entry.getCategory());
        }
    }

    @Test
    public void applyTypeFilterAcceptsMultipleCategories() {
        ModernConfigSearchFilter filter = createFilter(buildSampleRoot(), null);

        filter.applyTypeFilter(EnumSet.of(TemplateCategory.STRING, TemplateCategory.NUMBER));

        Set<TemplateCategory> seen = new HashSet<TemplateCategory>();
        for (SearchEntry entry : filter.getResults()) {
            assertTrue(entry.getCategory() == TemplateCategory.STRING
                    || entry.getCategory() == TemplateCategory.NUMBER);
            seen.add(entry.getCategory());
        }
        assertTrue("应至少命中 STRING 或 NUMBER", seen.contains(TemplateCategory.STRING)
                || seen.contains(TemplateCategory.NUMBER));
    }

    @Test
    public void applyTypeFilterWithNullClearsFilter() {
        ModernConfigSearchFilter filter = createFilter(buildSampleRoot(), null);
        int total = filter.getResultCount();

        filter.applyTypeFilter(EnumSet.of(TemplateCategory.BOOLEAN));
        int booleanOnly = filter.getResultCount();
        assertTrue(booleanOnly <= total);

        filter.applyTypeFilter(null);
        assertEquals(total, filter.getResultCount());
    }

    @Test
    public void applyModifiedOnlyKeepsDirtyEntries() {
        MutableConfig mutable = Config.createMutable(ConfigFormat.JSON);
        mutable.set("server.port", 8080);
        mutable.set("server.name", "prod");
        ConfigNode root = mutable.asImmutable();

        ModernConfigPropertyBindings.ConfigPropertyBinding dirtyBinding = mockBinding(mutable, "server.port", true);
        ModernConfigSearchIndex index = new ModernConfigSearchIndex(Arrays.asList(dirtyBinding),
                Collections.<String, ModernConfigTemplateScreen.FieldSpec>emptyMap(), root);
        UiDocument document = UiDocument.create();
        ModernConfigSearchFilter filter = new ModernConfigSearchFilter(document, index, null);

        filter.applyModifiedOnly(true);

        assertTrue("只看已修改应至少返回 1 项: " + filter.getResultCount(), filter.getResultCount() >= 1);
        for (SearchEntry entry : filter.getResults()) {
            assertTrue(entry.isDirty());
        }

        filter.applyModifiedOnly(false);
        assertTrue(filter.getResultCount() > 1);
    }

    @Test
    public void jumpToInvokesCallbackWithEntryPath() {
        final List<String> jumped = new ArrayList<String>();
        ModernConfigSearchFilter filter = createFilter(buildSampleRoot(),
                new Consumer<String>() {
                    @Override
                    public void accept(String path) {
                        jumped.add(path);
                    }
                });

        filter.applyQuery("server.port");
        assertEquals(1, filter.getResultCount());

        filter.jumpTo(0);
        assertEquals(1, jumped.size());
        assertEquals("server.port", jumped.get(0));
    }

    @Test
    public void jumpToOutOfBoundsIsIgnored() {
        final List<String> jumped = new ArrayList<String>();
        ModernConfigSearchFilter filter = createFilter(buildSampleRoot(),
                new Consumer<String>() {
                    @Override
                    public void accept(String path) {
                        jumped.add(path);
                    }
                });

        filter.jumpTo(-1);
        filter.jumpTo(999);
        assertEquals(0, jumped.size());
    }

    @Test
    public void nullHandlerDoesNotThrowOnJump() {
        ModernConfigSearchFilter filter = createFilter(buildSampleRoot(), null);

        filter.applyQuery("server.port");
        filter.jumpTo(0);
        assertEquals(1, filter.getResultCount());
    }

    @Test
    public void refreshReflectsUpdatedDirtyMarkers() {
        MutableConfig mutable = Config.createMutable(ConfigFormat.JSON);
        mutable.set("server.port", 8080);
        ConfigNode root = mutable.asImmutable();

        final boolean[] dirtyFlag = new boolean[] { false };
        ModernConfigPropertyBindings.ConfigPropertyBinding binding = mockDynamicBinding(mutable, "server.port",
                dirtyFlag);
        ModernConfigSearchIndex index = new ModernConfigSearchIndex(Arrays.asList(binding),
                Collections.<String, ModernConfigTemplateScreen.FieldSpec>emptyMap(), root);
        UiDocument document = UiDocument.create();
        ModernConfigSearchFilter filter = new ModernConfigSearchFilter(document, index, null);

        filter.applyModifiedOnly(true);
        assertEquals("初始无脏条目时应返回 0 项", 0, filter.getResultCount());

        dirtyFlag[0] = true;
        index.refreshDirtyMarkers();
        filter.refresh();

        assertTrue("刷新后应至少返回 1 项: " + filter.getResultCount(), filter.getResultCount() >= 1);
        boolean foundDirty = false;
        for (SearchEntry entry : filter.getResults()) {
            if ("server.port".equals(entry.getPath())) {
                assertTrue(entry.isDirty());
                foundDirty = true;
            }
        }
        assertTrue(foundDirty);
    }

    @Test
    public void combinedQueryAndTypeFilterNarrowsResults() {
        ModernConfigSearchFilter filter = createFilter(buildSampleRoot(), null);

        filter.applyQuery("server");
        filter.applyTypeFilter(EnumSet.of(TemplateCategory.NUMBER));

        assertEquals(1, filter.getResultCount());
        assertEquals("server.port", filter.getResults().get(0).getPath());
    }

    @Test
    public void combinedModifiedOnlyAndTypeFilter() {
        MutableConfig mutable = Config.createMutable(ConfigFormat.JSON);
        mutable.set("server.port", 8080);
        mutable.set("debug", true);
        ConfigNode root = mutable.asImmutable();

        ModernConfigPropertyBindings.ConfigPropertyBinding dirtyBinding = mockBinding(mutable, "server.port", true);
        ModernConfigSearchIndex index = new ModernConfigSearchIndex(Arrays.asList(dirtyBinding),
                Collections.<String, ModernConfigTemplateScreen.FieldSpec>emptyMap(), root);
        UiDocument document = UiDocument.create();
        ModernConfigSearchFilter filter = new ModernConfigSearchFilter(document, index, null);

        filter.applyModifiedOnly(true);
        filter.applyTypeFilter(EnumSet.of(TemplateCategory.NUMBER));

        assertEquals(1, filter.getResultCount());
        assertEquals("server.port", filter.getResults().get(0).getPath());
        assertTrue(filter.getResults().get(0).isDirty());
    }

    @Test
    public void categoryOptionLabelsContainAllPlusSevenCategories() {
        String[] labels = ModernConfigSearchFilter.getCategoryOptionLabels();

        assertEquals(8, labels.length);
        assertEquals("全部", labels[0]);
        assertEquals("源码", labels[6]);
        assertEquals("选择器", labels[7]);
    }

    @Test
    public void getElementReturnsConfiguredRoot() {
        ModernConfigSearchFilter filter = createFilter(buildSampleRoot(), null);

        ElementNode root = filter.getElement();
        assertNotNull(root);
        assertEquals("true", root.getAttribute("data-modern-config-search"));
    }

    @Test
    public void selectedCategoryMapsToSelectorIndex() {
        ModernConfigSearchFilter filter = createFilter(buildSampleRoot(), null);

        filter.setSelectedCategory(TemplateCategory.LIST);
        filter.applyTypeFilter(EnumSet.of(TemplateCategory.LIST));

        assertEquals(1, filter.getResultCount());
        assertEquals("tags", filter.getResults().get(0).getPath());
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

    private static ModernConfigSearchFilter createFilter(ConfigNode root, Consumer<String> handler) {
        ModernConfigSearchIndex index = new ModernConfigSearchIndex(
                Collections.<ModernConfigPropertyBindings.ConfigPropertyBinding>emptyList(),
                Collections.<String, ModernConfigTemplateScreen.FieldSpec>emptyMap(), root);
        UiDocument document = UiDocument.create();
        return new ModernConfigSearchFilter(document, index, handler);
    }

    private static ModernConfigPropertyBindings.ConfigPropertyBinding mockBinding(MutableConfig config,
            String path, final boolean dirty) {
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

    private static ModernConfigPropertyBindings.ConfigPropertyBinding mockDynamicBinding(MutableConfig config,
            String path, final boolean[] dirtyFlag) {
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
