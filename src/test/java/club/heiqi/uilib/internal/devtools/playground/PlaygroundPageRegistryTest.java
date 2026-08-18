package club.heiqi.uilib.internal.devtools.playground;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 演示页注册表与页面构建契约测试。
 *
 * <p>资产：页面元信息（id/title/description）非空、id 唯一且小写；每页能在 headless
 * runtime 下构建出非空 scene 树（演示页构建逻辑可测）；默认清单不可变。
 * 纯视觉/交互细节（光标闪烁、soft wrap 视觉）不在此断言，由既有组件测试覆盖。</p>
 */
public class PlaygroundPageRegistryTest {

    private SceneRuntime runtime;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        runtime = new SceneRuntime(new FixedTextMeasurer());
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    @Test
    public void registryIsNotEmptyAndOrdered() {
        List<PlaygroundPage> pages = PlaygroundPageRegistry.defaultPages();
        Assert.assertFalse("至少一个页面", pages.isEmpty());
        Assert.assertEquals("默认页为首项（总览）", "home", pages.get(0).id());
        Assert.assertEquals("清单顺序稳定", pages.size(), PlaygroundPageRegistry.ids().size());
    }

    @Test
    public void pageIdsAreUniqueAndLowercase() {
        Set<String> seen = new HashSet<String>();
        for (PlaygroundPage page : PlaygroundPageRegistry.defaultPages()) {
            Assert.assertNotNull("id 非空", page.id());
            Assert.assertEquals("id 小写", page.id(), page.id().toLowerCase(java.util.Locale.ROOT));
            Assert.assertTrue("id 唯一: " + page.id(), seen.add(page.id()));
        }
    }

    @Test
    public void metadataIsPresentForAllPages() {
        for (PlaygroundPage page : PlaygroundPageRegistry.defaultPages()) {
            Assert.assertFalse("标题非空: " + page.id(), page.title().trim().isEmpty());
            Assert.assertFalse("说明非空: " + page.id(), page.description().trim().isEmpty());
        }
    }

    @Test
    public void lookupFindsRegisteredPagesAndMissesUnknown() {
        List<String> ids = PlaygroundPageRegistry.ids();
        for (String id : ids) {
            Assert.assertNotNull("lookup 命中: " + id, PlaygroundPageRegistry.lookup(id));
        }
        Assert.assertNull("未知 id 返回 null", PlaygroundPageRegistry.lookup("no-such-page"));
        Assert.assertNull("null id 返回 null", PlaygroundPageRegistry.lookup(null));
    }

    @Test
    public void defaultPagesIsImmutable() {
        List<PlaygroundPage> pages = PlaygroundPageRegistry.defaultPages();
        try {
            pages.add(PlaygroundPageRegistry.defaultPages().get(0));
            Assert.fail("默认清单不可变");
        } catch (UnsupportedOperationException expected) {
            // 预期不可变
        }
    }

    @Test
    public void everyPageBuildsNonNullTreeAndMountsCleanly() {
        for (PlaygroundPage page : PlaygroundPageRegistry.defaultPages()) {
            SceneNode parent = new SceneNode();
            SceneNode root = runtime.mount(parent, page.build(runtime)).getRoot();
            Assert.assertNotNull("页面构建非 null: " + page.id(), root);
            Assert.assertSame("根已挂入父节点: " + page.id(), root, parent.__getChildren().get(0));
            Assert.assertFalse("根有子节点: " + page.id(), root.__getChildren().isEmpty());
            runtime.flush();
        }
    }
}
