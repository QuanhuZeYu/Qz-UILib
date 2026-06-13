package club.heiqi.uilib.ui.control;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;

/**
 * `DocumentTreeViewControl` 的折叠、展开和当前节点契约测试。
 */
public class DocumentTreeViewControlTest {

    @Test
    public void supportsSelectionHighlightAndCollapseState() {
        UiDocument document = UiDocument.create();
        final AtomicReference<String> selectedPath = new AtomicReference<String>("");
        DocumentTreeViewControl control = new DocumentTreeViewControl(document, Arrays.asList(
                new DocumentTreeViewControl.TreeNode("", "根配置", Arrays.asList(
                        new DocumentTreeViewControl.TreeNode("server", "Server", Arrays.asList(
                                new DocumentTreeViewControl.TreeNode("server.database", "Database",
                                        Collections.<DocumentTreeViewControl.TreeNode>emptyList())))))))
                .setSelectionHandler(new DocumentTreeViewControl.TreeSelectionHandler() {
                    @Override
                    public void onTreePathSelected(String path) {
                        selectedPath.set(path);
                    }
                });

        control.setCurrentPath("server.database");
        assertEquals("true", findElementByAttribute(control.getElement(), "data-tree-node-path",
                "server.database").getAttribute("data-tree-node-current"));

        control.toggleCollapsed("server");
        assertTrue(control.isCollapsed("server"));
        control.expandPath("server.database");
        assertFalse(control.isCollapsed("server"));

        control.selectPath("server");
        assertEquals("server", selectedPath.get());
        assertNotNull(findElementByAttribute(control.getElement(), "data-tree-toggle-path", "server"));
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
