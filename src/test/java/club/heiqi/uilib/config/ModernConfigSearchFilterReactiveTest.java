package club.heiqi.uilib.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.config.Config;
import club.heiqi.config.ConfigFormat;
import club.heiqi.config.ConfigNode;
import club.heiqi.config.MutableConfig;
import club.heiqi.uilib.ui.component.UiComponentRuntime;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;

/**
 * {@link ModernConfigSearchFilter} 响应式路径（接入 {@link UiComponentRuntime}）的端到端测试。
 *
 * <p>验证真机试点的核心收益：搜索条件变化时，结果列表用 {@code forEach} 按 key（path+dirty）协调，
 * <b>只增删移动变化行、未变行复用 DOM 节点</b>（信条三，I5/I7），而非整列表 {@code clearChildren} 全量重建。
 * 同时验证三参构造器（无运行时）的命令式路径向后兼容。</p>
 */
public class ModernConfigSearchFilterReactiveTest {

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        ReactiveScheduler.get().reset();
    }

    private static ConfigNode sampleRoot() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON);
        config.set("server.host", "localhost");
        config.set("server.port", 8080);
        config.set("server.name", "prod");
        config.set("client.debug", true);
        return config.asImmutable();
    }

    private static ModernConfigSearchFilter reactiveFilter(UiDocument document, UiComponentRuntime runtime) {
        ModernConfigSearchIndex index = new ModernConfigSearchIndex(
                Collections.<ModernConfigPropertyBindings.ConfigPropertyBinding>emptyList(),
                Collections.<String, ModernConfigTemplateScreen.FieldSpec>emptyMap(), sampleRoot());
        return new ModernConfigSearchFilter(document, index, null, runtime);
    }

    /** 取结果容器下各行的 data-modern-config-search-path 序列（DOM 实际顺序）。 */
    private static List<String> rowPaths(ElementNode container) {
        List<String> paths = new ArrayList<>();
        for (DocumentNode child : container.getChildren()) {
            paths.add(((ElementNode) child).getAttribute("data-modern-config-search-path"));
        }
        return paths;
    }

    // ── 响应式路径：首帧 flush 后渲染全部结果 ───────────────────────────────────

    @Test
    public void reactivePathRendersRowsAfterFlush() {
        UiDocument document = UiDocument.create();
        UiComponentRuntime runtime = new UiComponentRuntime(document);
        ModernConfigSearchFilter filter = reactiveFilter(document, runtime);
        ElementNode container = filter.getResultsContainerForTest();

        // 构造期 forEach 已登记、resultsSignal 已 set，但 reconcile 要等 flush
        ReactiveScheduler.get().flush();
        // 索引含中间 MAP 节点（server、client）作为 OBJECT 条目，故 4 个 set 产生 6 条
        Assert.assertEquals("首帧 flush 后应渲染全部 6 条", 6, container.getChildCount());
        Assert.assertTrue(rowPaths(container).contains("server.port"));
    }

    // ── 响应式路径：缩小查询时未变行复用 DOM 节点 ────────────────────────────────

    @Test
    public void refiningQueryReusesUnchangedRowNodes() {
        UiDocument document = UiDocument.create();
        UiComponentRuntime runtime = new UiComponentRuntime(document);
        ModernConfigSearchFilter filter = reactiveFilter(document, runtime);
        ElementNode container = filter.getResultsContainerForTest();
        ReactiveScheduler.get().flush();

        // 记录 server.port 行的节点身份
        ElementNode portRowBefore = null;
        for (DocumentNode child : container.getChildren()) {
            if ("server.port".equals(((ElementNode) child).getAttribute("data-modern-config-search-path"))) {
                portRowBefore = (ElementNode) child;
            }
        }
        Assert.assertNotNull(portRowBefore);

        // 缩小查询到只剩 server.* —— server.port 仍在结果中，应复用同一 DOM 节点
        filter.applyQuery("server");
        ReactiveScheduler.get().flush();

        ElementNode portRowAfter = null;
        for (DocumentNode child : container.getChildren()) {
            if ("server.port".equals(((ElementNode) child).getAttribute("data-modern-config-search-path"))) {
                portRowAfter = (ElementNode) child;
            }
        }
        Assert.assertNotNull(portRowAfter);
        Assert.assertSame("未变行应复用同一 DOM 节点（不重建）", portRowBefore, portRowAfter);
        Assert.assertFalse("client.debug 应已被移出列表", rowPaths(container).contains("client.debug"));
    }

    // ── 响应式路径：清空查询恢复全部，且复用之前的行 ─────────────────────────────

    @Test
    public void clearingQueryRestoresRowsReusingNodes() {
        UiDocument document = UiDocument.create();
        UiComponentRuntime runtime = new UiComponentRuntime(document);
        ModernConfigSearchFilter filter = reactiveFilter(document, runtime);
        ElementNode container = filter.getResultsContainerForTest();
        ReactiveScheduler.get().flush();

        ElementNode portRowInitial = null;
        for (DocumentNode child : container.getChildren()) {
            if ("server.port".equals(((ElementNode) child).getAttribute("data-modern-config-search-path"))) {
                portRowInitial = (ElementNode) child;
            }
        }

        filter.applyQuery("server");
        ReactiveScheduler.get().flush();
        filter.applyQuery("");
        ReactiveScheduler.get().flush();

        Assert.assertEquals("清空查询应恢复全部 6 条", 6, container.getChildCount());
        ElementNode portRowFinal = null;
        for (DocumentNode child : container.getChildren()) {
            if ("server.port".equals(((ElementNode) child).getAttribute("data-modern-config-search-path"))) {
                portRowFinal = (ElementNode) child;
            }
        }
        // server.port 全程在结果中（query=server 与空查询都含它），节点应一直复用
        Assert.assertSame("全程命中的行应持续复用同一节点", portRowInitial, portRowFinal);
    }

    // ── dispose 清理：runtime.dispose 后协调 effect 停止 ─────────────────────────

    @Test
    public void disposeStopsReconciliation() {
        UiDocument document = UiDocument.create();
        UiComponentRuntime runtime = new UiComponentRuntime(document);
        ModernConfigSearchFilter filter = reactiveFilter(document, runtime);
        ElementNode container = filter.getResultsContainerForTest();
        ReactiveScheduler.get().flush();
        Assert.assertEquals(6, container.getChildCount());

        runtime.dispose();
        // dispose 递归清理：列表作用域 → 各行作用域，每行 onCleanup 把自身 DOM 摘除，容器清空。
        Assert.assertEquals("dispose 应拆除全部行（各行作用域 onCleanup 摘除 DOM）", 0, container.getChildCount());

        // dispose 后改查询，reconcile effect 已停，不再重新协调（容器保持空）。
        filter.applyQuery("server");
        ReactiveScheduler.get().flush();
        Assert.assertEquals("dispose 后协调应停止，不再重建行", 0, container.getChildCount());
    }

    // ── 向后兼容：三参构造器（无运行时）仍走命令式整列表重建 ──────────────────────

    @Test
    public void legacyConstructorStillRendersImperatively() {
        UiDocument document = UiDocument.create();
        ModernConfigSearchIndex index = new ModernConfigSearchIndex(
                Collections.<ModernConfigPropertyBindings.ConfigPropertyBinding>emptyList(),
                Collections.<String, ModernConfigTemplateScreen.FieldSpec>emptyMap(), sampleRoot());
        ModernConfigSearchFilter filter = new ModernConfigSearchFilter(document, index, null);
        ElementNode container = filter.getResultsContainerForTest();

        // 命令式路径：构造期即同步渲染，无需 flush
        Assert.assertEquals(6, container.getChildCount());
        filter.applyQuery("server");
        Assert.assertEquals("命令式路径应立即反映查询结果（含中间 MAP 节点 server）",
                Arrays.asList("server", "server.host", "server.name", "server.port"),
                rowPaths(container));
    }
}
