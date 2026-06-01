package club.heiqi.uilib.ui.dom;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import club.heiqi.uilib.ui.style.props.UiFontStyle;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.text.TextContentMode;

/**
 * `UiDocument` 文档树基础契约测试。
 */
public class UiDocumentTest {

    /**
     * 验证作者入口可以创建稳定元素树与文本节点。
     */
    @Test
    public void shouldCreateElementTreeThroughAuthoringApi() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode panel = document.div()
                .setAttribute("ID", "main-panel")
                .setAttribute("role", "page");
        TextNode title = document.text("标题");

        int initialVersion = document.getMutationVersion();
        root.append(panel);
        panel.append(title);

        Assert.assertEquals("document", root.getTagName());
        Assert.assertEquals(DocumentNodeType.ELEMENT, panel.getNodeType());
        Assert.assertEquals("div", panel.getTagName());
        Assert.assertTrue(root.__getElementUid() > 0L);
        Assert.assertTrue(panel.__getElementUid() > 0L);
        Assert.assertTrue(root.__getElementUid() != panel.__getElementUid());
        Assert.assertEquals("main-panel", panel.getAttribute("id"));
        Assert.assertEquals("page", panel.getAttribute("role"));
        Assert.assertSame(root, panel.getParent());
        Assert.assertSame(panel, title.getParent());
        Assert.assertEquals(DocumentNodeType.TEXT, title.getNodeType());
        Assert.assertEquals("标题", title.getText());
        Assert.assertTrue(document.getMutationVersion() > initialVersion);
    }

    /**
     * 验证常见 HTML-like 标签拥有可运行的基础语义，而不只是标签字符串。
     */
    @Test
    public void shouldExposeBasicNativeElementSemantics() {
        UiDocument document = UiDocument.create();
        ElementNode button = document.button();
        ElementNode input = document.input();
        ElementNode textarea = document.textarea();
        ElementNode select = document.select();
        ElementNode option = document.option();
        ElementNode image = document.img().setAttribute("alt", "玩家头像");
        ElementNode hiddenImage = document.img().setAttribute("alt", "装饰").setAttribute("aria-hidden", "true");
        button.appendText("确认");
        option.appendText("候选项");

        Assert.assertTrue(button.isFocusable());
        Assert.assertTrue(input.isFocusable());
        Assert.assertTrue(textarea.isFocusable());
        Assert.assertTrue(select.isFocusable());
        Assert.assertFalse(option.isFocusable());
        Assert.assertFalse(image.isFocusable());
        Assert.assertEquals("button", button.getSemanticRole());
        Assert.assertEquals("textbox", input.getSemanticRole());
        Assert.assertEquals("textbox", textarea.getSemanticRole());
        Assert.assertEquals("combobox", select.getSemanticRole());
        Assert.assertEquals("option", option.getSemanticRole());
        Assert.assertEquals("true", textarea.getAttribute("aria-multiline"));
        Assert.assertEquals("img", image.getSemanticRole());
        Assert.assertEquals("确认", button.getAccessibleLabel());
        Assert.assertEquals("候选项", option.getAccessibleLabel());
        Assert.assertEquals("玩家头像", image.getAccessibleLabel());
        Assert.assertEquals("", hiddenImage.getAccessibleLabel());
    }

    /**
     * 验证 a 元素只在拥有非空 href 时才具备链接语义与默认焦点能力。
     */
    @Test
    public void shouldResolveAnchorLinkSemanticsFromHref() {
        UiDocument document = UiDocument.create();
        ElementNode anchor = document.a();

        Assert.assertFalse(anchor.isFocusable());
        Assert.assertNull(anchor.getSemanticRole());

        anchor.setAttribute("href", "https://example.test/docs");

        Assert.assertTrue(anchor.isFocusable());
        Assert.assertEquals("link", anchor.getSemanticRole());

        anchor.removeAttribute("href");

        Assert.assertFalse(anchor.isFocusable());
        Assert.assertNull(anchor.getSemanticRole());
    }

    /**
     * 验证 append 语义会在同一文档内移动已有子节点。
     */
    @Test
    public void shouldMoveExistingChildWhenAppendingAgain() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode first = document.div().setAttribute("id", "first");
        ElementNode second = document.div().setAttribute("id", "second");
        long firstElementUid = first.__getElementUid();

        root.append(first).append(second);
        second.append(first);

        Assert.assertEquals(1, root.getChildCount());
        Assert.assertSame(second, root.getFirstChild());
        Assert.assertEquals(1, second.getChildCount());
        Assert.assertSame(first, second.getFirstChild());
        Assert.assertSame(second, first.getParent());
        Assert.assertEquals(firstElementUid, first.__getElementUid());
    }

    /**
     * 验证兄弟节点访问器始终反映当前文档树顺序。
     */
    @Test
    public void shouldExposeSiblingAccessorsInCurrentTreeOrder() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode first = document.div();
        TextNode middle = document.text("middle");
        ElementNode last = document.span();

        root.append(first).append(middle).append(last);

        Assert.assertNull(first.getPreviousSibling());
        Assert.assertSame(middle, first.getNextSibling());
        Assert.assertSame(first, middle.getPreviousSibling());
        Assert.assertSame(last, middle.getNextSibling());
        Assert.assertSame(middle, last.getPreviousSibling());
        Assert.assertNull(last.getNextSibling());

        root.removeChild(middle);

        Assert.assertSame(last, first.getNextSibling());
        Assert.assertSame(first, last.getPreviousSibling());
        Assert.assertNull(middle.getPreviousSibling());
        Assert.assertNull(middle.getNextSibling());
    }

    /**
     * 验证内部元素唯一身份不会复用 HTML id 属性语义。
     */
    @Test
    public void shouldAssignInternalElementUidsSeparatelyFromHtmlIdAttribute() {
        UiDocument firstDocument = UiDocument.create();
        UiDocument secondDocument = UiDocument.create();
        ElementNode first = firstDocument.div().setAttribute("id", "same-html-id");
        ElementNode second = firstDocument.div().setAttribute("id", "same-html-id");
        ElementNode otherDocumentElement = secondDocument.div().setAttribute("id", "same-html-id");

        Assert.assertEquals("same-html-id", first.getAttribute("id"));
        Assert.assertEquals("same-html-id", second.getAttribute("id"));
        Assert.assertTrue(first.__getElementUid() != second.__getElementUid());
        Assert.assertTrue(first.__getElementUid() != otherDocumentElement.__getElementUid());
        Assert.assertFalse(first.hasAttribute("__elementUid"));
    }

    /**
     * 验证文档会区分 layout 失效与 paint-only 失效版本。
     */
    @Test
    public void shouldTrackLayoutAndPaintMutationVersionsSeparately() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        int initialMutationVersion = document.getMutationVersion();
        int initialLayoutVersion = document.getLayoutVersion();
        int initialPaintVersion = document.getPaintVersion();

        root.style().setBackgroundColor(0xFF223344);

        Assert.assertTrue(document.getMutationVersion() > initialMutationVersion);
        Assert.assertEquals(initialLayoutVersion, document.getLayoutVersion());
        Assert.assertTrue(document.getPaintVersion() > initialPaintVersion);

        int paintOnlyLayoutVersion = document.getLayoutVersion();
        int paintOnlyPaintVersion = document.getPaintVersion();
        root.style().setWidth(UiStyleLength.px(40));

        Assert.assertTrue(document.getLayoutVersion() > paintOnlyLayoutVersion);
        Assert.assertTrue(document.getPaintVersion() > paintOnlyPaintVersion);

        int fontStyleLayoutVersion = document.getLayoutVersion();
        root.style().setFontStyle(UiFontStyle.ITALIC);

        Assert.assertTrue(document.getLayoutVersion() > fontStyleLayoutVersion);
    }

    /**
     * 验证文档树会拒绝跨文档挂接与循环挂接。
     */
    @Test
    public void shouldRejectCrossDocumentNodesAndCycles() {
        UiDocument firstDocument = UiDocument.create();
        UiDocument secondDocument = UiDocument.create();
        ElementNode root = firstDocument.getRootElement();
        ElementNode child = firstDocument.div();
        ElementNode grandChild = firstDocument.div();

        root.append(child);
        child.append(grandChild);

        try {
            root.append(secondDocument.div());
            Assert.fail("跨文档节点不应允许挂接");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("another UiDocument"));
        }

        try {
            grandChild.append(child);
            Assert.fail("祖先节点不应允许挂到后代下方");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("ancestor"));
        }

        try {
            firstDocument.div().append(root);
            Assert.fail("文档根元素不应允许被挂到孤立节点下方");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("root element"));
        }
    }

    /**
     * 验证文本节点保持叶子节点语义，并能记录文本变更。
     */
    @Test
    public void shouldKeepTextNodeLeafAndRecordTextMutation() {
        UiDocument document = UiDocument.create();
        TextNode textNode = document.text(null);
        int initialVersion = document.getMutationVersion();

        Assert.assertEquals("", textNode.getText());
        textNode.setText("正文");

        Assert.assertEquals("正文", textNode.getText());
        Assert.assertTrue(document.getMutationVersion() > initialVersion);

        try {
            textNode.appendChild(document.span());
            Assert.fail("文本节点不应允许子节点");
        } catch (UnsupportedOperationException expected) {
            Assert.assertTrue(expected.getMessage().contains("cannot contain children"));
        }
    }

    /**
     * 验证作者入口提供语义化的 raw / Minecraft 文本快捷方法。
     */
    @Test
    public void shouldProvideSemanticTextFactoriesAndAppenders() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();

        TextNode rawText = document.rawText("价格：§a100金币");
        TextNode minecraftText = document.minecraftText("§a100金币");
        TextNode appendedRawText = root.appendRawText("原样 §k 文本");
        TextNode appendedMinecraftText = root.appendMinecraftText("§a绿色文本");

        Assert.assertEquals(TextContentMode.UILIB_RAW, rawText.getTextContentMode());
        Assert.assertEquals(TextContentMode.MINECRAFT_FORMATTED, minecraftText.getTextContentMode());
        Assert.assertEquals(TextContentMode.UILIB_RAW, appendedRawText.getTextContentMode());
        Assert.assertEquals(TextContentMode.MINECRAFT_FORMATTED, appendedMinecraftText.getTextContentMode());
    }

    /**
     * 验证文档支持语义化切换后续文本节点的默认解析模式。
     */
    @Test
    public void shouldProvideSemanticDefaultTextModeSwitchers() {
        UiDocument document = UiDocument.create();

        document.useMinecraftTextByDefault();
        TextNode minecraftText = document.text("§a100金币");
        document.useRawTextByDefault();
        TextNode rawText = document.text("§a100金币");

        Assert.assertEquals(TextContentMode.MINECRAFT_FORMATTED, minecraftText.getTextContentMode());
        Assert.assertEquals(TextContentMode.UILIB_RAW, rawText.getTextContentMode());
    }

    /**
     * 验证 getAccessibleLabel 递归收集文本时跳过 aria-hidden 后代子树。
     */
    @Test
    public void shouldSkipAriaHiddenDescendantsInAccessibleLabel() {
        UiDocument document = UiDocument.create();
        ElementNode button = document.button();
        button.appendText("保存");
        ElementNode decoration = document.span().setAttribute("aria-hidden", "true");
        decoration.appendText("隐藏文本");
        button.append(decoration);

        // 只包含非隐藏文本
        Assert.assertEquals("保存", button.getAccessibleLabel());

        // 多层嵌套 aria-hidden 子树也跳过
        ElementNode outerHidden = document.div().setAttribute("aria-hidden", "true");
        ElementNode innerSpan = document.span();
        innerSpan.appendText("深层隐藏");
        outerHidden.append(innerSpan);
        button.append(outerHidden);
        Assert.assertEquals("保存", button.getAccessibleLabel());

        // 自身 aria-hidden=true 时返回空字符串
        button.setAttribute("aria-hidden", "true");
        Assert.assertEquals("", button.getAccessibleLabel());
    }

    /**
     * 验证 cloneNode 会复制结构、属性与样式，但不会复制运行时监听器。
     */
    @Test
    public void shouldCloneNodeStructureAttributesAndStyleWithoutRuntimeListeners() {
        UiDocument document = UiDocument.create();
        ElementNode source = document.div()
                .setId("source")
                .setClassName("card active")
                .setAttribute("data-kind", "panel");
        source.style()
                .setWidth(UiStyleLength.px(80))
                .setPadding(UiStyleLength.px(6));
        source.addEventListener("save", new DocumentCustomEventHandler() {
            @Override
            public boolean onEvent(DocumentCustomEvent event) {
                return true;
            }
        });
        source.appendText("标题");
        ElementNode child = document.span().setAttribute("role", "note");
        child.appendText("说明");
        source.append(child);

        ElementNode shallowClone = (ElementNode) source.cloneNode();
        ElementNode deepClone = (ElementNode) source.cloneNode(true);

        Assert.assertEquals("source", shallowClone.getId());
        Assert.assertEquals("card active", shallowClone.getClassName());
        Assert.assertEquals("panel", shallowClone.getAttribute("data-kind"));
        Assert.assertEquals(UiStyleLength.px(80), shallowClone.style().getWidth());
        Assert.assertEquals(0, shallowClone.getChildCount());
        Assert.assertTrue(shallowClone.__getRegisteredCustomEventTypes().isEmpty());

        Assert.assertEquals(2, deepClone.getChildCount());
        Assert.assertTrue(deepClone.getFirstChild() instanceof TextNode);
        Assert.assertTrue(deepClone.getLastChild() instanceof ElementNode);
        Assert.assertEquals("标题", ((TextNode) deepClone.getFirstChild()).getText());
        Assert.assertEquals("note", ((ElementNode) deepClone.getLastChild()).getAttribute("role"));
        Assert.assertTrue(deepClone.__getRegisteredCustomEventTypes().isEmpty());
    }

    /**
     * 验证 DocumentFragment 会在 DOM 操作时按浏览器语义展开子节点。
     */
    @Test
    public void shouldExpandDocumentFragmentDuringDomInsertion() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode first = document.div().setId("first");
        ElementNode anchor = document.div().setId("anchor");
        ElementNode last = document.div().setId("last");
        root.append(first).append(anchor).append(last);

        DocumentFragmentNode fragment = document.createDocumentFragment();
        ElementNode insertedA = document.span().setId("inserted-a");
        ElementNode insertedB = document.span().setId("inserted-b");
        fragment.appendChild(insertedA);
        fragment.appendChild(insertedB);

        root.insertBefore(fragment, anchor);

        Assert.assertEquals(0, fragment.getChildCount());
        Assert.assertEquals(Arrays.asList("first", "inserted-a", "inserted-b", "anchor", "last"),
                collectElementIds(root));

        DocumentFragmentNode replaceFragment = document.createDocumentFragment();
        replaceFragment.appendChild(document.div().setId("replace-a"));
        replaceFragment.appendChild(document.div().setId("replace-b"));
        root.replaceChild(replaceFragment, last);

        Assert.assertEquals(Arrays.asList("first", "inserted-a", "inserted-b", "anchor", "replace-a",
                "replace-b"), collectElementIds(root));
    }

    /**
     * 验证同父节点移动到后方参考节点前时不会因旧索引产生偏移。
     */
    @Test
    public void shouldMoveExistingChildBeforeLaterSiblingWithoutIndexDrift() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode first = document.div().setId("first");
        ElementNode second = document.div().setId("second");
        ElementNode third = document.div().setId("third");
        root.append(first).append(second).append(third);

        DocumentNode inserted = root.insertBefore(first, third);

        Assert.assertSame(first, inserted);
        Assert.assertEquals(Arrays.asList("second", "first", "third"), collectElementIds(root));
    }

    /**
     * 验证同父节点 replaceChild 使用已存在且靠前的新节点时仍替换正确旧节点。
     */
    @Test
    public void shouldReplaceLaterSiblingWithExistingEarlierChildWithoutIndexDrift() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode first = document.div().setId("first");
        ElementNode second = document.div().setId("second");
        ElementNode third = document.div().setId("third");
        root.append(first).append(second).append(third);

        DocumentNode replaced = root.replaceChild(first, third);

        Assert.assertSame(third, replaced);
        Assert.assertNull(third.getParent());
        Assert.assertEquals(Arrays.asList("second", "first"), collectElementIds(root));
    }

    /**
     * 验证 appendChild / insertBefore 返回被插入节点，而不是父节点。
     */
    @Test
    public void shouldReturnInsertedNodeFromAppendAndInsertBefore() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode first = document.div().setId("first");
        ElementNode second = document.div().setId("second");

        Assert.assertSame(first, root.appendChild(first));
        Assert.assertSame(second, root.insertBefore(second, first));
        Assert.assertEquals(Arrays.asList("second", "first"), collectElementIds(root));
    }

    /**
     * 验证 removeChild 返回被移除节点，且非直接子节点按 DOM 语义报错。
     */
    @Test
    public void shouldReturnRemovedNodeAndRejectNonChildFromRemoveChild() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div().setId("child");
        ElementNode other = document.div().setId("other");
        root.append(child);

        DocumentNode removed = root.removeChild(child);

        Assert.assertSame(child, removed);
        Assert.assertNull(child.getParent());

        try {
            root.removeChild(other);
            Assert.fail("非直接子节点不应静默移除");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("not a child"));
        }
    }

    /**
     * 验证 disabled 作为 HTML 布尔属性只要存在就表示禁用。
     */
    @Test
    public void shouldTreatDisabledFalseAttributeAsDisabled() {
        UiDocument document = UiDocument.create();
        ElementNode button = document.button().setAttribute("disabled", "false");

        Assert.assertTrue(button.isDisabled());

        button.removeAttribute("disabled");

        Assert.assertFalse(button.isDisabled());
    }

    /**
     * 验证页面作者可以创建并派发自定义事件，且三阶段传播链路可用。
     */
    @Test
    public void shouldDispatchCustomEventThroughCaptureTargetAndBubble() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode parent = document.div().setId("parent");
        ElementNode child = document.div().setId("child");
        root.append(parent);
        parent.append(child);

        final List<String> phases = new ArrayList<String>();
        parent.addEventListener("save", new DocumentCustomEventHandler() {
            @Override
            public boolean onEvent(DocumentCustomEvent event) {
                phases.add("capture:" + event.getCurrentTarget().getId() + ":" + event.getEventPhase());
                return false;
            }
        }, true);
        child.addEventListener("save", new DocumentCustomEventHandler() {
            @Override
            public boolean onEvent(DocumentCustomEvent event) {
                phases.add("target:" + event.getCurrentTarget().getId() + ":" + event.getEventPhase());
                Assert.assertEquals(child, event.getTarget());
                Assert.assertEquals("payload", event.getDetail());
                event.preventDefault();
                return false;
            }
        });
        parent.addEventListener("save", new DocumentCustomEventHandler() {
            @Override
            public boolean onEvent(DocumentCustomEvent event) {
                phases.add("bubble:" + event.getCurrentTarget().getId() + ":" + event.getEventPhase());
                return false;
            }
        });

        boolean result = child.dispatchEvent(new DocumentCustomEvent("save", "payload", true, true));

        Assert.assertFalse(result);
        Assert.assertEquals(Arrays.asList(
                "capture:parent:CAPTURING",
                "target:child:AT_TARGET",
                "bubble:parent:BUBBLING"), phases);
    }

    private static List<String> collectElementIds(ElementNode parent) {
        List<String> ids = new ArrayList<String>();
        for (DocumentNode child : parent.getChildren()) {
            if (child instanceof ElementNode) {
                ids.add(((ElementNode) child).getId());
            }
        }
        return ids;
    }
}
