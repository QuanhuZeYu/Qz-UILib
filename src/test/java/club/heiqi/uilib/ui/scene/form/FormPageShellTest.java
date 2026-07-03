package club.heiqi.uilib.ui.scene.form;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * {@link FormPageShell} 单元测试。
 *
 * <p>守护 {@code build} 的 {@code attachScroll} 分支契约：
 * attachScroll=false 时不建 scrollSignal / scrollbar（ConfigScreen 复用此分支自建 per-section
 * scroll），attachScroll=true 时挂滚动受控源 + 可视滚动条。并验证 root/titleBar/viewport/
 * scrollContainer 骨架结构与父子关系。</p>
 *
 * <p>分层：build 内部经 {@link SceneRuntime} 注册 scroll bind/handler 与 scrollbar，触 runtime
 * 子系统，属 L3 集成范畴。照 control 包测试范式（同为 runtime 消费方）直接 new SceneRuntime，
 * 放 form 同包（守测试体系约定 §1「其余子包按各自子系统归属」）。build 不跑 layout/flush，
 * 仅做节点装配与 effect 注册，无需真机字体度量。</p>
 */
public class FormPageShellTest {

    /** 场景运行时（build 消费方），每用例独立 new，避免跨用例污染。 */
    private SceneRuntime runtime;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        runtime = new SceneRuntime(new FixedTextMeasurer(8, 16));
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== attachScroll=false 分支（ConfigScreen 复用） ====================

    /** attachScroll=false：不创建滚动受控源，scrollSignal 为 null。 */
    @Test
    public void attachScrollFalseYieldsNullScrollSignal() {
        FormPageShell.Parts parts = FormPageShell.build(
                runtime, "标题", "副标题", false, FormTheme.defaultDark());
        Assert.assertNull("attachScroll=false 时 scrollSignal 应为 null", parts.scrollSignal());
    }

    /** attachScroll=false：不创建可视滚动条，scrollbarColumn 为 null。 */
    @Test
    public void attachScrollFalseYieldsNullScrollbarColumn() {
        FormPageShell.Parts parts = FormPageShell.build(
                runtime, "标题", "副标题", false, FormTheme.defaultDark());
        Assert.assertNull("attachScroll=false 时 scrollbarColumn 应为 null", parts.scrollbarColumn());
    }

    /**
     * attachScroll=false：scrollContainer 仅含 viewport 一个子（不含 scrollbar 列）。
     *
     * <p>这是 ConfigScreen 依赖的关键契约——ConfigScreen 随后自建 per-section scrollbar 并
     * appendChild 到 scrollContainer 得到 viewport + scrollbar 两子。若 shell 在 false 分支误建
     * scrollbar，ConfigScreen 的 scrollContainer 会变 3 子，该回归断言即挂。</p>
     */
    @Test
    public void attachScrollFalseScrollContainerHoldsViewportOnly() {
        FormPageShell.Parts parts = FormPageShell.build(
                runtime, "标题", "副标题", false, FormTheme.defaultDark());
        Assert.assertEquals("attachScroll=false 时 scrollContainer 仅含 viewport",
                1, parts.scrollContainer().__getChildren().size());
        Assert.assertSame("scrollContainer 唯一子应为 viewport",
                parts.viewport(), parts.scrollContainer().__getChildren().get(0));
    }

    // ==================== attachScroll=true 分支 ====================

    /** attachScroll=true：创建滚动受控源 + 可视滚动条，scrollSignal 与 scrollbarColumn 均非 null。 */
    @Test
    public void attachScrollTrueYieldsScrollSignalAndScrollbar() {
        FormPageShell.Parts parts = FormPageShell.build(
                runtime, "标题", "副标题", true, FormTheme.defaultDark());
        Assert.assertNotNull("attachScroll=true 时 scrollSignal 应非 null", parts.scrollSignal());
        Assert.assertNotNull("attachScroll=true 时 scrollbarColumn 应非 null", parts.scrollbarColumn());
    }

    /** attachScroll=true：scrollContainer 含 viewport + scrollbarColumn 两子，viewport 在前。 */
    @Test
    public void attachScrollTrueScrollContainerHoldsViewportAndScrollbar() {
        FormPageShell.Parts parts = FormPageShell.build(
                runtime, "标题", "副标题", true, FormTheme.defaultDark());
        Assert.assertEquals("attachScroll=true 时 scrollContainer 含 viewport + scrollbar",
                2, parts.scrollContainer().__getChildren().size());
        Assert.assertSame("scrollContainer 第一子为 viewport",
                parts.viewport(), parts.scrollContainer().__getChildren().get(0));
        Assert.assertSame("scrollContainer 第二子为 scrollbarColumn",
                parts.scrollbarColumn(), parts.scrollContainer().__getChildren().get(1));
    }

    // ==================== 骨架结构与父子关系 ====================

    /** root/viewport/scrollContainer 非 null，父子关系：root ⊃ scrollContainer ⊃ viewport。 */
    @Test
    public void skeletonNodesNonNullAndParentChildWired() {
        FormPageShell.Parts parts = FormPageShell.build(
                runtime, "标题", "副标题", false, FormTheme.defaultDark());
        Assert.assertNotNull("root 非 null", parts.root());
        Assert.assertNotNull("viewport 非 null", parts.viewport());
        Assert.assertNotNull("scrollContainer 非 null", parts.scrollContainer());
        Assert.assertSame("scrollContainer 父应为 root",
                parts.root(), parts.scrollContainer().__getParent());
        Assert.assertSame("viewport 父应为 scrollContainer",
                parts.scrollContainer(), parts.viewport().__getParent());
    }

    /** titleBar 为 root 首子，含标题文本；subtitle 非空时含副标题文本（共 2 个 text 子）。 */
    @Test
    public void titleBarIsFirstChildOfRootWithTitleText() {
        FormPageShell.Parts parts = FormPageShell.build(
                runtime, "我的标题", "我的副标题", false, FormTheme.defaultDark());
        SceneNode titleBar = parts.root().__getChildren().get(0);
        Assert.assertNotNull("titleBar（root 首子）非 null", titleBar);
        Assert.assertEquals("titleBar 含 title + subtitle 两个文本子",
                2, titleBar.__getChildren().size());
        Assert.assertEquals("titleBar 首子文本为标题",
                "我的标题", titleBar.__getChildren().get(0).getText());
        Assert.assertEquals("titleBar 次子文本为副标题",
                "我的副标题", titleBar.__getChildren().get(1).getText());
        Assert.assertNotSame("root 首子是 titleBar 而非 scrollContainer",
                parts.scrollContainer(), titleBar);
    }
}
