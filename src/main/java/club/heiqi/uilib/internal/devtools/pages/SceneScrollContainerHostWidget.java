package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.scene.host.AbstractSceneHostWidget;

import club.heiqi.uilib.ui.scene.control.SceneScrollContainer;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * SceneScrollContainer.attach 一行工厂的样板验收 demo。
 *
 * <p>演示对齐 Compose Box+align / Flutter Scrollbar 模式的「一行建带可视滚动条滚动容器」。</p>
 * <ul>
 *   <li><b>本页</b>：{@code SceneScrollContainer.attach(...)} 一行建出 container + viewport +
 *       content + scrollbar，container 在 root COLUMN 里 flexGrow=1 撑满剩余高。</li>
 * </ul>
 *
 * <h3>结构</h3>
 * <pre>
 *  root (COLUMN, fillParentHeight, padding=24, gap=12, 半透明背景)
 *   ├─ title  (说明文本)
 *   └─ container (ROW, flexGrow=1)   ← attach 一行建出
 *        ├─ viewport (COLUMN, scrollable, clip, flexGrow=1)
 *        │     └─ content (COLUMN)   ← 40 条 item，超出视口触发 scrollbar 显示
 *        └─ scrollbar column (右侧，有溢出时 barWidth 宽，无溢出时宽=0 不占布局)
 * </pre>
 *
 * <h3>关键验证点</h3>
 * <ul>
 *   <li>scrollbar 内部订阅 rt.layoutDoneSignal()，host 桥接 epoch 驱动 thumb 几何随 layout 更新；</li>
 *   <li>40 条 item 总高 > viewport 高，maxScrollY > 0，scrollbar column 宽 = barWidth 可见；</li>
 *   <li>滚轮滚动 viewport，thumb 经 COMPOSITE 级 translateY 平移跟随。</li>
 * </ul>
 */
final class SceneScrollContainerHostWidget extends AbstractSceneHostWidget {

    /** 长列表条目数量（足够多使内容总高 > 视口高，触发滚动 + scrollbar 显示） */
    private static final int ITEM_COUNT = 40;

    /** 单条目固定高度（像素） */
    private static final int ITEM_HEIGHT = 28;

    // 深色系配色（与现有 scene 控件 demo 协调）
    private static final int TITLE_TEXT_COLOR = 0xFFFFFFFF;
    private static final int ITEM_TEXT_COLOR = 0xFFB8C2CC;

    private final SceneNode root;

    /**
     * 创建 SceneScrollContainer.attach demo 宿主 Widget。
     *
     * @param inputSource 平台输入源，可为 null（退化模式，无真机滚轮）
     */
    SceneScrollContainerHostWidget(PlatformInputSource inputSource) {
        super(inputSource);
        this.root = new SceneNode();
        root.setFillParentHeight(true);
        root.setFillParentWidth(true);
        root.setFlexDirection(FlexDirection.COLUMN);
        root.setPadding(24);
        root.setGap(12);
        root.setBackgroundColor(0xCC20242B);

        // ===== 标题 =====
        SceneNode title = new SceneNode();
        title.setText("SceneScrollContainer.attach Demo（一行建带 bar 滚动容器）");
        title.setTextColor(TITLE_TEXT_COLOR);
        title.setPreferredHeight(28);
        title.setHitTestable(false);
        root.appendChild(title);

        // ===== 核心：一行建带可视滚动条的滚动容器并挂到 root =====
        // scrollbar 内部订阅 rt.layoutDoneSignal()，host 桥接 epoch 驱动 thumb 几何更新。
        SceneScrollContainer.attach(runtime, root, content -> {
            for (int i = 0; i < ITEM_COUNT; i++) {
                SceneNode item = SceneNode.row();
                item.setPreferredHeight(ITEM_HEIGHT);
                item.setPadding(6, 10, 6, 10);

                SceneNode label = new SceneNode();
                label.setText("Item " + i + " / " + ITEM_COUNT + " — scroll to see more");
                label.setTextColor(ITEM_TEXT_COLOR);
                label.setHitTestable(false);
                item.appendChild(label);
                content.appendChild(item);
            }
        });

        // 首次 flush，确保首帧有初始值
        runtime.flush();
    }

    @Override
    protected SceneNode getRoot() {
        return root;
    }
}
