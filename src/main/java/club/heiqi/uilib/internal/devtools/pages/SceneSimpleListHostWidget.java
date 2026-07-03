package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.scene.host.AbstractSceneHostWidget;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.control.SceneSimpleList;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 新栈 ui.scene SimpleList demo 宿主 Widget。
 */
public class SceneSimpleListHostWidget extends AbstractSceneHostWidget {

    private static final int STATUS_HEIGHT = 34;
    private static final int LIST_HEIGHT = 190;

    private final SceneNode root;
    private final SceneNode viewport;
    private final SceneNode scrollContainer;
    private final SceneNode scrollbarColumn;
    private final SceneNode content;
    private final Signal<Integer> scrollSignal;
    private final Signal<List<SceneSimpleList.ListItem>> basicItems;
    private final Signal<List<SceneSimpleList.ListItem>> boundedItems;
    private final Signal<Integer> changeCount;

    /**
     * 创建 SimpleList demo 宿主 Widget，注入平台输入源。
     *
     * @param inputSource 平台输入源，可为 null（退化模式，无真机滚轮）
     */
    public SceneSimpleListHostWidget(PlatformInputSource inputSource) {
        super(inputSource);
        this.basicItems = Signal.create(taskItems());
        this.boundedItems = Signal.create(boundedTaskItems());
        this.changeCount = Signal.create(Integer.valueOf(0));

        SceneDemoPageShell.Parts parts = SceneDemoPageShell.build(runtime,
                "SceneSimpleList Demo",
                "动态字符串列表 · keyed 行复用 · 增删边界 · 行内文本输入",
                true, r -> r.appendChild(createStatusBar()));
        this.root = parts.root();
        this.viewport = parts.viewport();
        this.scrollContainer = parts.scrollContainer();
        this.scrollbarColumn = parts.scrollbarColumn();
        this.scrollSignal = parts.scrollSignal();
        this.content = createContent();
        viewport.appendChild(content);

        content.appendChild(createListCard("基础用法", "增删编辑任务条目，最多 20 条。",
                basicItems, "输入任务名称", 0, 20));
        content.appendChild(createListCard("边界测试", "最少保留 3 条，最多 10 条，用于验证按钮禁用态。",
                boundedItems, "输入边界任务", 3, 10));

        runtime.flush();
    }

    /**
     * 创建视口内容容器。
     *
     * @return 内容节点
     */
    private SceneNode createContent() {
        SceneNode node = SceneNode.column();
        node.setGap(14);
        return node;
    }

    /**
     * 创建底部状态条。
     *
     * @return 状态条节点
     */
    private SceneNode createStatusBar() {
        SceneNode row = SceneNode.row();
        row.setPreferredHeight(STATUS_HEIGHT);
        row.setGap(10);
        row.appendChild(badge(Computed.create(() -> "当前行数：" + totalCount()), SceneDemoTokens.OK_COLOR));
        row.appendChild(badge(Computed.create(() -> "onItemsChanged：" + changeCount.get()), SceneDemoTokens.DIRTY_COLOR));
        return row;
    }

    /**
     * 创建 SimpleList 卡片。
     *
     * @param title 卡片标题
     * @param helper 帮助说明
     * @param items 列表 signal
     * @param placeholder 输入占位文本
     * @param minItems 最小条目数
     * @param maxItems 最大条目数
     * @return 卡片节点
     */
    private SceneNode createListCard(String title, String helper, Signal<List<SceneSimpleList.ListItem>> items,
            String placeholder, int minItems, int maxItems) {
        SceneNode card = createCardShell(title, helper);
        SceneSimpleList.Props props = SceneSimpleList.Props.builder(items)
                .label(title + "列表")
                .placeholder(placeholder)
                .minItems(minItems)
                .maxItems(maxItems)
                .onItemsChanged(next -> incrementChangeCount())
                .build();
        MountHandle handle = runtime.mount(card, SceneSimpleList.create(runtime, props));
        handle.getRoot().setPreferredHeight(LIST_HEIGHT);
        return card;
    }

    /**
     * 创建卡片外壳。
     *
     * @param title 卡片标题
     * @param helper 帮助说明
     * @return 卡片节点
     */
    private SceneNode createCardShell(String title, String helper) {
        return SceneDemoCards.cardShell(title, helper);
    }

    /**
     * 创建徽标节点。
     *
     * @param label 文案源
     * @param color 颜色
     * @return 徽标节点
     */
    private SceneNode badge(Computed<String> label, int color) {
        SceneNode node = new SceneNode();
        node.setWidthSizing(SceneNode.WidthSizing.SHRINK);
        node.setPadding(8);
        node.setCornerRadius(999);
        node.setBorderWidth(1);
        node.setBorderColor(color);
        node.setBackgroundColor(SceneDemoTokens.READOUT_BG);
        node.setHitTestable(false);
        SceneNode textNode = SceneDemoCards.text("", color);
        node.appendChild(textNode);
        runtime.bind(label, textNode::setText);
        return node;
    }

    /** 递增列表变更计数。 */
    private void incrementChangeCount() {
        changeCount.set(Integer.valueOf(changeCount.get().intValue() + 1));
    }

    /**
     * 获取两组列表总行数。
     *
     * @return 总行数
     */
    private int totalCount() {
        return safeSize(basicItems.get()) + safeSize(boundedItems.get());
    }

    /**
     * 获取安全列表长度。
     *
     * @param items 列表
     * @return 列表长度
     */
    private static int safeSize(List<SceneSimpleList.ListItem> items) {
        return items == null ? 0 : items.size();
    }

    /**
     * 创建基础任务示例数据。
     *
     * @return 任务列表
     */
    private static List<SceneSimpleList.ListItem> taskItems() {
        return Collections.unmodifiableList(Arrays.asList(
                new SceneSimpleList.ListItem("整理材质包发布清单"),
                new SceneSimpleList.ListItem("确认按钮 hover 色"),
                new SceneSimpleList.ListItem("补充配置迁移说明"),
                new SceneSimpleList.ListItem("复测滚轮滚动边界"),
                new SceneSimpleList.ListItem("记录真机输入问题"),
                new SceneSimpleList.ListItem("同步 demo 截图")));
    }

    /**
     * 创建边界任务示例数据。
     *
     * @return 任务列表
     */
    private static List<SceneSimpleList.ListItem> boundedTaskItems() {
        return Collections.unmodifiableList(Arrays.asList(
                new SceneSimpleList.ListItem("保底任务 A"),
                new SceneSimpleList.ListItem("保底任务 B"),
                new SceneSimpleList.ListItem("保底任务 C"),
                new SceneSimpleList.ListItem("可删除任务 D"),
                new SceneSimpleList.ListItem("可删除任务 E")));
    }

    @Override
    protected SceneNode getRoot() {
        return root;
    }

    /** @return 滚动容器节点（ROW：viewport + scrollbarColumn） */
    SceneNode __getScrollContainer() {
        return scrollContainer;
    }

    /** @return 滚动条列节点（scrollContainer 内 viewport 右侧独立列） */
    SceneNode __getScrollbarColumn() {
        return scrollbarColumn;
    }
}
