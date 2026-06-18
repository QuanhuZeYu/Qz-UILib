package club.heiqi.uilib.ui.control;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import club.heiqi.uilib.ui.component.UiComponentRuntime;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;

/**
 * `DocumentBreadcrumbControl` 的路径段渲染与回跳契约测试。
 */
public class DocumentBreadcrumbControlTest {

    @Test
    public void rendersPathSegmentsAndNotifiesJumpTarget() {
        final AtomicReference<String> selectedPath = new AtomicReference<String>("");
        UiDocument doc = UiDocument.create();
        UiComponentRuntime runtime = new UiComponentRuntime(doc);
        DocumentBreadcrumbControl control = new DocumentBreadcrumbControl(doc, runtime)
                .setSelectionHandler(new DocumentBreadcrumbControl.BreadcrumbSelectionHandler() {
                    @Override
                    public void onBreadcrumbPathSelected(String path) {
                        selectedPath.set(path);
                    }
                });

        control.setPath("server.database.pool");
        runtime.flush();

        assertNotNull(findElementByAttribute(control.getElement(), "data-breadcrumb-segment", ""));
        assertNotNull(findElementByAttribute(control.getElement(), "data-breadcrumb-segment", "server.database"));
        control.selectPath("server");
        runtime.flush();
        assertEquals("server", selectedPath.get());
        assertEquals("server", control.getPath());
    }

    /**
     * 同步契约：setPath 后立即 getPath 返回新值（读影子字段，无需 flush）。
     */
    @Test
    public void getPathReturnsNewValueImmediatelyAfterSetPath() {
        UiDocument doc = UiDocument.create();
        UiComponentRuntime runtime = new UiComponentRuntime(doc);
        DocumentBreadcrumbControl control = new DocumentBreadcrumbControl(doc, runtime);

        control.setPath("server.database");
        assertEquals("server.database", control.getPath());
    }

    /**
     * forEach keyed 复用证明 + 正向对照（批次 0 核心验收：必过）。
     *
     * <p>渲染 "a.b.c" → 换 "a.b.d" 后：根段、"a"、"a.b" 三段 wrapper 引用不变
     * （forEach keyed 复用，reconciler 稳定项零销毁重建）；原 "a.b.c" 段已移除，
     * "a.b.d" 段为新节点存在。这证明方向 A 的控件层协议化技术可行——控件不再全量重建。</p>
     */
    @Test
    public void shouldReuseStableSegmentsOnLocalPathChange() {
        UiDocument doc = UiDocument.create();
        UiComponentRuntime runtime = new UiComponentRuntime(doc);
        DocumentBreadcrumbControl control = new DocumentBreadcrumbControl(doc, runtime);

        // 渲染 "a.b.c"
        control.setPath("a.b.c");
        runtime.flush();

        ElementNode rootWrapper = findElementByAttribute(control.getElement(), "data-breadcrumb-segment", "");
        assertNotNull("根段应存在", rootWrapper);
        ElementNode aWrapper = findElementByAttribute(control.getElement(), "data-breadcrumb-segment", "a");
        assertNotNull("\"a\" 段应存在", aWrapper);
        ElementNode abWrapper = findElementByAttribute(control.getElement(), "data-breadcrumb-segment", "a.b");
        assertNotNull("\"a.b\" 段应存在", abWrapper);
        ElementNode abcWrapper = findElementByAttribute(control.getElement(), "data-breadcrumb-segment", "a.b.c");
        assertNotNull("\"a.b.c\" 段应存在", abcWrapper);

        // 改为 "a.b.d"（仅末段变）
        control.setPath("a.b.d");
        runtime.flush();

        ElementNode rootWrapperAfter = findElementByAttribute(control.getElement(), "data-breadcrumb-segment", "");
        ElementNode aWrapperAfter = findElementByAttribute(control.getElement(), "data-breadcrumb-segment", "a");
        ElementNode abWrapperAfter = findElementByAttribute(control.getElement(), "data-breadcrumb-segment", "a.b");

        // 复用断言（批次 0 核心，必过）：稳定三段 wrapper 引用 flush 前后不变。
        // 证明 forEach keyed 复用真实工作、reconciler 稳定项零销毁重建（方向 A 技术可行）。
        assertSame("根段 wrapper 应复用（key=\"\"）", rootWrapper, rootWrapperAfter);
        assertSame("\"a\" 段 wrapper 应复用（key=\"a\"）", aWrapper, aWrapperAfter);
        assertSame("\"a.b\" 段 wrapper 应复用（key=\"a.b\"）", abWrapper, abWrapperAfter);

        // 正向对照：原 "a.b.c" 段已移除、"a.b.d" 段为新节点。
        assertNull("\"a.b.c\" 段应已移除",
                findElementByAttribute(control.getElement(), "data-breadcrumb-segment", "a.b.c"));
        assertNotNull("\"a.b.d\" 段应存在（新节点）",
                findElementByAttribute(control.getElement(), "data-breadcrumb-segment", "a.b.d"));
    }

    /**
     * 【I7 正向锚点】DOM 层细粒度结构标脏：列表项增删不株连未变兄弟子树。
     *
     * <p>批次 0 叫停关口诊断结论（oracle ses 终审，解读 3 成立）：forEach keyed 复用了
     * 未变段的 wrapper 节点对象（见 {@link #shouldReuseStableSegmentsOnLocalPathChange}）。
     * 早期 DOM 层 {@code DocumentNode.recordStructuralMutation} → {@code markSubtreeLayoutMutation}
     * 曾从容器无条件向下递归，把未变兄弟及其全部后代的 layout/subtree 突变版本一并刷新，
     * 导致 layout 层 {@code resolveReusableLayoutBox} 的 version 闸门判定复用失败、稳定兄弟被
     * 真实重算——违反 I7（干净子树三阶段跳过）。</p>
     *
     * <p>方案 X 修复后：结构变更只对容器标自身 self + subtree 版本并向上冒泡刷祖先 subtree，
     * 不再递归整棵容器子树。真正受影响的兄弟由 layout 层闸门（约束/forced 维度变化）按需捕获重算，
     * 稳定兄弟子树版本不被株连。</p>
     *
     * <p><b>本测试断言 I7 正向达成</b>：仅末段变化（a.b.c → a.b.d）时，未变根段的子树版本
     * flush 前后保持不变，证明列表项增删不再污染稳定段子树。</p>
     */
    @Test
    public void stableSegmentSubtreeIsNotDirtiedByListMutation() {
        UiDocument doc = UiDocument.create();
        UiComponentRuntime runtime = new UiComponentRuntime(doc);
        DocumentBreadcrumbControl control = new DocumentBreadcrumbControl(doc, runtime);

        control.setPath("a.b.c");
        runtime.flush();

        ElementNode rootWrapper = findElementByAttribute(control.getElement(), "data-breadcrumb-segment", "");
        assertNotNull("根段应存在", rootWrapper);
        int rootVersionBefore = rootWrapper.__getSubtreeLayoutMutationVersion();

        // 仅末段变化（a.b.c → a.b.d）：根段几何完全未变。
        control.setPath("a.b.d");
        runtime.flush();

        ElementNode rootWrapperAfter = findElementByAttribute(control.getElement(), "data-breadcrumb-segment", "");
        assertSame("根段 wrapper 应复用", rootWrapper, rootWrapperAfter);
        int rootVersionAfter = rootWrapperAfter.__getSubtreeLayoutMutationVersion();

        // I7 正向：未变根段子树版本不被列表项增删株连。
        assertEquals("【I7】未变根段子树版本应不被列表增删污染（细粒度标脏）。"
                        + " flush前=" + rootVersionBefore + ", flush后=" + rootVersionAfter,
                rootVersionBefore, rootVersionAfter);
    }

    private static ElementNode findElementByAttribute(ElementNode element, String attributeName, String attributeValue) {
        if (attributeValue.equals(element.getAttribute(attributeName))) {
            return element;
        }
        for (DocumentNode child : element.getChildren()) {
            if (child instanceof ElementNode) {
                ElementNode found = findElementByAttribute((ElementNode) child, attributeName, attributeValue);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
