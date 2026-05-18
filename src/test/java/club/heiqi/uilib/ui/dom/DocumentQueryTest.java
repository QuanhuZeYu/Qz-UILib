package club.heiqi.uilib.ui.dom;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * DOM 查询方法（getElementById/querySelector/querySelectorAll）的契约测试。
 */
public class DocumentQueryTest {

    @Test
    public void getElementByIdFindsElement() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode panel = document.div();
        panel.setId("main-panel");
        root.append(panel);

        ElementNode found = document.getElementById("main-panel");
        Assert.assertSame(panel, found);
    }

    @Test
    public void getElementByIdReturnsNullWhenNotFound() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode panel = document.div();
        panel.setId("panel");
        root.append(panel);

        Assert.assertNull(document.getElementById("nonexistent"));
    }

    @Test
    public void getElementByIdFindsNestedElement() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode outer = document.div();
        ElementNode inner = document.div();
        inner.setId("deep-element");
        outer.append(inner);
        root.append(outer);

        Assert.assertSame(inner, document.getElementById("deep-element"));
    }

    @Test
    public void getElementByIdNullOrEmptyReturnsNull() {
        UiDocument document = UiDocument.create();
        Assert.assertNull(document.getElementById(null));
        Assert.assertNull(document.getElementById(""));
    }

    @Test
    public void querySelectorByClass() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode div1 = document.div();
        div1.setClassName("highlight");
        ElementNode div2 = document.div();
        div2.setClassName("normal");
        root.append(div1);
        root.append(div2);

        ElementNode found = document.querySelector(".highlight");
        Assert.assertSame(div1, found);
    }

    @Test
    public void querySelectorByTag() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode button = document.button();
        ElementNode span = document.span();
        root.append(button);
        root.append(span);

        Assert.assertSame(button, document.querySelector("button"));
        Assert.assertSame(span, document.querySelector("span"));
    }

    @Test
    public void querySelectorById() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode panel = document.div();
        panel.setId("title");
        root.append(panel);

        Assert.assertSame(panel, document.querySelector("#title"));
    }

    @Test
    public void querySelectorCompound() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode div1 = document.div();
        div1.setClassName("panel");
        ElementNode div2 = document.div();
        div2.setClassName("panel active");
        root.append(div1);
        root.append(div2);

        // div.panel.active 只匹配 div2
        Assert.assertSame(div2, document.querySelector("div.panel.active"));
    }

    @Test
    public void querySelectorReturnsNullWhenNoMatch() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.append(document.div());

        Assert.assertNull(document.querySelector(".nonexistent"));
    }

    @Test
    public void querySelectorAllReturnsAllMatches() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode div1 = document.div();
        div1.setClassName("item");
        ElementNode div2 = document.div();
        div2.setClassName("item");
        ElementNode div3 = document.div();
        div3.setClassName("other");
        root.append(div1);
        root.append(div2);
        root.append(div3);

        List<ElementNode> results = document.querySelectorAll(".item");
        Assert.assertEquals(2, results.size());
        Assert.assertSame(div1, results.get(0));
        Assert.assertSame(div2, results.get(1));
    }

    @Test
    public void querySelectorAllReturnsEmptyWhenNoMatch() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.append(document.div());

        List<ElementNode> results = document.querySelectorAll(".nonexistent");
        Assert.assertTrue(results.isEmpty());
    }

    @Test
    public void querySelectorAllDepthFirst() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode parent = document.div();
        parent.setClassName("target");
        ElementNode child = document.div();
        child.setClassName("target");
        parent.append(child);
        root.append(parent);

        ElementNode sibling = document.div();
        sibling.setClassName("target");
        root.append(sibling);

        List<ElementNode> results = document.querySelectorAll(".target");
        Assert.assertEquals(3, results.size());
        // 深度优先：parent -> child -> sibling
        Assert.assertSame(parent, results.get(0));
        Assert.assertSame(child, results.get(1));
        Assert.assertSame(sibling, results.get(2));
    }

    @Test
    public void getElementsByTagName() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode btn1 = document.button();
        ElementNode btn2 = document.button();
        ElementNode div = document.div();
        root.append(btn1);
        root.append(div);
        root.append(btn2);

        List<ElementNode> buttons = document.getElementsByTagName("button");
        Assert.assertEquals(2, buttons.size());
        Assert.assertSame(btn1, buttons.get(0));
        Assert.assertSame(btn2, buttons.get(1));
    }

    @Test
    public void getElementsByClassName() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode div1 = document.div();
        div1.setClassName("active primary");
        ElementNode div2 = document.div();
        div2.setClassName("active");
        ElementNode div3 = document.div();
        div3.setClassName("inactive");
        root.append(div1);
        root.append(div2);
        root.append(div3);

        List<ElementNode> actives = document.getElementsByClassName("active");
        Assert.assertEquals(2, actives.size());
        Assert.assertSame(div1, actives.get(0));
        Assert.assertSame(div2, actives.get(1));
    }

    @Test
    public void querySelectorNullOrEmptyReturnsNull() {
        UiDocument document = UiDocument.create();
        Assert.assertNull(document.querySelector(null));
        Assert.assertNull(document.querySelector(""));
    }

    @Test
    public void querySelectorAllNullOrEmptyReturnsEmpty() {
        UiDocument document = UiDocument.create();
        Assert.assertTrue(document.querySelectorAll(null).isEmpty());
        Assert.assertTrue(document.querySelectorAll("").isEmpty());
    }
}
