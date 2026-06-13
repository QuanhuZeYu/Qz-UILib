package club.heiqi.uilib.ui.control;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

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
        DocumentBreadcrumbControl control = new DocumentBreadcrumbControl(UiDocument.create())
                .setSelectionHandler(new DocumentBreadcrumbControl.BreadcrumbSelectionHandler() {
                    @Override
                    public void onBreadcrumbPathSelected(String path) {
                        selectedPath.set(path);
                    }
                });

        control.setPath("server.database.pool");

        assertNotNull(findElementByAttribute(control.getElement(), "data-breadcrumb-segment", ""));
        assertNotNull(findElementByAttribute(control.getElement(), "data-breadcrumb-segment", "server.database"));
        control.selectPath("server");
        assertEquals("server", selectedPath.get());
        assertEquals("server", control.getPath());
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
