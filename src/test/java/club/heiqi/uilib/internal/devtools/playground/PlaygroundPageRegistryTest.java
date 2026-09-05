package club.heiqi.uilib.internal.devtools.playground;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.font.layout.TextSegment;
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

    // ==================== M8 页面级锁：围栏底色「块内统一宽」读自 L2 ====================

    /** 与 MarkdownPage.CODE_BG_PAD_PX 同值：本页只有 CODE 行节点带左右内衬。 */
    private static final int CODE_SIDE_PAD = 3;
    /** 反 ∅ 地板：参与比较的 CODE 行节点数。 */
    private static final int MIN_CODE_NODES = 4;
    /** 反 ∅ 地板：参与比较的围栏块数。 */
    private static final int MIN_CODE_BLOCKS = 2;

    /**
     * 页面级真断言（不是拿 L2 断言冒充）：headless 构造注册表里的 markdown 页，遍历<b>已装配的
     * scene 节点</b>，断言同一围栏块内全部 CODE 行节点的 {@code getPreferredWidth()} 彼此相等。
     *
     * <p>用户在 game 里看到的「底色右缘参差」出在这一页：旧实现每行宽 = 该行自身文字宽 + 内衬，
     * 完全没有块口径。M8 起改读 {@code MarkdownLayoutLine#getBlockContentWidthPx()}（L2 单一真相）。</p>
     *
     * <p>识别方式：本页只有 CODE 行节点同时具备「非空段流 + 左右内衬 + 非零背景色」——真横线节点
     * 无段流、普通行无内衬。同一父节点内的连续命中段即一个围栏块。<b>正对照</b>：同页两个围栏块的
     * 统一宽必须互不相等（钉住「按块取值」，排除「全局常量宽 / 铺满容器」也能蒙过）。
     * <b>反 ∅ 地板</b>：块数与节点数各设下限。</p>
     *
     * <p>刻意并入本类（基线就已构造 markdown 页）而不是新开测试类：新开类会改变同 JVM 内
     * 「谁先预热真实字体测量」的次序，把 {@code PlaygroundButtonRowLayoutTest} 的既有
     * 跨测试测量预热耦合放大成可见 flaky（本轮已实测归因，见交付报告）。</p>
     */
    @Test
    public void markdownPageCodeBlockLineNodesShareWidthPerBlock() {
        PlaygroundPage page = PlaygroundPageRegistry.lookup("markdown");
        Assert.assertNotNull("注册表含 markdown 页", page);
        SceneNode shell = page.build(runtime).get();
        Assert.assertNotNull("markdown 页可 headless 构造", shell);

        List<List<SceneNode>> blocks = codeBlocksOf(shell);
        int codeNodes = 0;
        for (int b = 0; b < blocks.size(); b++) {
            List<SceneNode> block = blocks.get(b);
            int first = block.get(0).getPreferredWidth();
            Assert.assertTrue("块内统一宽必须 > 0: " + first, first > 0);
            for (SceneNode node : block) {
                Assert.assertEquals("同一围栏块内所有 CODE 行节点宽度必须一致（旧逐行自字宽"
                                + "口径在此会红）: 块首=" + first + " 本行=" + node.getPreferredWidth()
                                + " 文本=<" + segmentsText(node) + '>',
                        first, node.getPreferredWidth());
                codeNodes++;
            }
        }
        Assert.assertTrue("反 ∅ 地板：围栏块数 >= " + MIN_CODE_BLOCKS + "，实测 " + blocks.size(),
                blocks.size() >= MIN_CODE_BLOCKS);
        Assert.assertTrue("反 ∅ 地板：CODE 行节点数 >= " + MIN_CODE_NODES + "，实测 " + codeNodes,
                codeNodes >= MIN_CODE_NODES);
        Assert.assertTrue("正对照：两个围栏块的统一宽必须互不相等（否则不是按块取值）: "
                        + blocks.get(0).get(0).getPreferredWidth() + " vs "
                        + blocks.get(1).get(0).getPreferredWidth(),
                blocks.get(0).get(0).getPreferredWidth() != blocks.get(1).get(0).getPreferredWidth());
    }

    /** 递归收集「CODE 行节点」，按同一父节点内的连续段归块。 */
    private static List<List<SceneNode>> codeBlocksOf(SceneNode root) {
        List<List<SceneNode>> blocks = new ArrayList<List<SceneNode>>();
        collectCodeBlocks(root, blocks);
        return blocks;
    }

    private static void collectCodeBlocks(SceneNode node, List<List<SceneNode>> blocks) {
        List<SceneNode> children = node.__getChildren();
        int i = 0;
        while (i < children.size()) {
            if (isCodeLineNode(children.get(i))) {
                List<SceneNode> run = new ArrayList<SceneNode>();
                while (i < children.size() && isCodeLineNode(children.get(i))) {
                    run.add(children.get(i));
                    i++;
                }
                blocks.add(run);
                continue;
            }
            collectCodeBlocks(children.get(i), blocks);
            i++;
        }
    }

    private static boolean isCodeLineNode(SceneNode node) {
        List<TextSegment> segments = node.getSegments();
        return segments != null && !segments.isEmpty()
                && node.getPaddingLeft() == CODE_SIDE_PAD
                && node.getPaddingRight() == CODE_SIDE_PAD
                && node.getBackgroundColor() != 0;
    }

    private static String segmentsText(SceneNode node) {
        StringBuilder sb = new StringBuilder();
        for (TextSegment segment : node.getSegments()) {
            sb.append(segment.getText());
        }
        return sb.toString();
    }
}
