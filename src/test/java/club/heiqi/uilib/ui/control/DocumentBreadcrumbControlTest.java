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
     * 【已知地基债回归锚点】DOM 层粗粒度结构标脏：列表项增删污染未变兄弟子树。
     *
     * <p>批次 0 叫停关口诊断结论（oracle ses 终审，解读 3 成立）：forEach keyed 复用了
     * 未变段的 wrapper 节点对象（见 {@link #shouldReuseStableSegmentsOnLocalPathChange}），
     * 但对容器执行 removeChild/insertBefore 时，{@code DocumentNode.recordStructuralMutation}
     * → {@code markSubtreeLayoutMutation} 会从容器无条件向下递归，把**未变兄弟及其全部后代**的
     * layout/subtree 突变版本一并刷新。导致 layout 层 {@code resolveReusableLayoutBox} 的 version
     * 闸门判定复用失败，未变兄弟被真实重算——I7（干净子树被跳过）在此场景未达成。</p>
     *
     * <p>该债**先验存在**（原全量重建模式下同样整树标脏，只是被"反正都要重建"淹没），
     * **不是方向 A / 控件层引入的**，正确性无损（重算结果与跳过一致），属性能局部债。
     * 修复方向：reconciler 批量提交 API 绕过逐次 append/remove 的粗粒度标脏。
     * 见 docs/开发者文档/errors/README.md 对应条目 + NORTH_STAR 偏离登记。</p>
     *
     * <p><b>本测试断言"债当前存在"</b>：未变兄弟子树版本在列表项增删后确实被刷新。
     * 待 DOM 层修复后，此断言会翻转失败 —— 那是预期信号，提示把本测试改为正向 I7 断言
     * 并清理偏离登记。</p>
     */
    @Test
    public void documentsKnownCoarseSubtreeDirtyMarkingDebt() {
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

        // 已知债现状：未变根段子树版本被列表项增删污染（被刷新）。
        // DOM 层修复后此断言翻转 → 届时改为 assertEquals 验证 I7 达成。
        assertTrue("【已知债】未变根段子树版本应被列表增删污染（当前粗粒度标脏）。"
                        + "若此断言失败说明 DOM 标脏粒度债已修复，请将本测试改为 I7 正向断言。"
                        + " flush前=" + rootVersionBefore + ", flush后=" + rootVersionAfter,
                rootVersionAfter != rootVersionBefore);
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
