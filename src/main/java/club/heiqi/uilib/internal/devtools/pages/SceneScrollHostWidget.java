package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.Invalidation;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;

/**
 * 新栈 ui.scene 滚动 demo 宿主 Widget —— Phase 4 批 4 步骤 B「滚动/视口基础设施地基」真机接入。
 *
 * <h3>滚动容器结构（地基组装，不碰引擎核心）</h3>
 * <pre>
 *  root (COLUMN, fillParentHeight, padding=20, gap=12)
 *   ├─ title  (说明文本「滚轮滚动长列表」)
 *   └─ viewport (scrollable=true + preferredHeight=240 钉死视口高 + 背景 + 圆角，裁剪窗口)
 *        └─ content (COLUMN, 非 scrollable, 20 条斑马纹条目，总高 > 240 触发滚动)
 *             ├─ item 0..N (ROW, padding, 交替背景, 左对齐文本)
 * </pre>
 *
 * <h3>滚动数据流（signal-first，守 I1/I11）</h3>
 * <p>滚轮 SCROLL 事件 → handler 读 wheelDelta → 重读 cachedLayout 算 maxScroll → clamp →
 * <b>只</b> {@code scrollSignal.set(clamped)}（handler 内绝不直接 setScrollOffsetY）；
 * bind effect 在 flush 时把 signal 值推给 {@code viewport::setScrollOffsetY}，
 * setter 内部 markGeometryDirty → paint 阶段对 scrollable 节点注入 -scrollOffsetY 平移 +
 * CLIP 固定视口窗口裁剪超出部分。layout 零重排（geometry 级失效守 I7）。</p>
 *
 * <h3>端到端 pipeline（对照 SceneControlsHostWidget）</h3>
 * <pre>
 *  drainFrame → layout① → route(queueWrite) → flush(apply+effect)
 *    → layout②(吸收 LAYOUT 脏) → paint → replay
 * </pre>
 */
public class SceneScrollHostWidget extends AbstractSceneHostWidget {

    /** 视口固定高度（像素），由 scrollable + preferredHeight 钉死，不被内容撑大 */
    private static final int VIEWPORT_HEIGHT = 240;

    /** 长列表条目数量（足够多使内容总高 > 视口高，触发滚动） */
    private static final int ITEM_COUNT = 20;

    /** 单条目固定高度（像素） */
    private static final int ITEM_HEIGHT = 32;

    // 深色系配色（与现有 scene 控件 demo 协调）
    private static final int VIEWPORT_BG = 0xFF0D1728;
    private static final int ITEM_BG_EVEN = 0xFF1E293B;
    private static final int ITEM_BG_ODD = 0xFF243B53;
    private static final int ITEM_TEXT_COLOR = 0xFFEAF1FF;
    private static final int TITLE_TEXT_COLOR = 0xFFC9D8F8;

    private final SceneNode root;

    /** 视口节点（scrollable，裁剪窗口 + 偏移注入锚点） */
    private final SceneNode viewport;

    /** 内容容器节点（viewport 唯一子，承载全部条目；其 LayoutBox.height 即 contentHeight） */
    private final SceneNode content;

    /** 纵向滚动偏移受控源（唯一状态源），bind 推给 viewport.setScrollOffsetY */
    private final Signal<Integer> scrollSignal;

    /**
     * 创建滚动 demo 宿主 Widget，注入平台输入源。
     *
     * @param inputSource 平台输入源，可为 null（退化模式，无真机滚轮）
     */
    public SceneScrollHostWidget(PlatformInputSource inputSource) {
        super(inputSource);

        // ===== root：纵向容器，铺满 host 全高 =====
        this.root = new SceneNode();
        root.setFillParentHeight(true);
        root.setFlexDirection(FlexDirection.COLUMN);
        root.setGap(12);
        root.setPadding(20);

        // ===== 标题说明文本（视口外） =====
        SceneNode title = new SceneNode();
        title.setText("滚轮滚动长列表（视口固定 " + VIEWPORT_HEIGHT + "px，内容超出被裁剪）");
        title.setTextColor(TITLE_TEXT_COLOR);
        title.setHitTestable(false);
        root.appendChild(title);

        // ===== viewport：scrollable + preferredHeight 钉死视口高 =====
        // scrollable=true 使布局引擎 computeHeight 走钉死分支直接返回 preferredHeight=240，
        // 不被内容总高（20×32=640）撑大；同时 scrollable 节点在 paint 阶段自动成为 CLIP 裁剪窗口。
        this.viewport = new SceneNode();
        viewport.setScrollable(true);
        viewport.setPreferredHeight(VIEWPORT_HEIGHT);
        viewport.setBackgroundColor(VIEWPORT_BG);
        viewport.setCornerRadius(6);
        root.appendChild(viewport);

        // ===== content：单一内容容器（非 scrollable，COLUMN 累加条目高） =====
        // 内容包在单一容器节点里，maxScroll 直接读 content.LayoutBox.getHeight() 作 contentHeight。
        this.content = new SceneNode();
        content.setFlexDirection(FlexDirection.COLUMN);
        viewport.appendChild(content);

        // ===== 长列表条目：斑马纹 + 左对齐文本 + padding =====
        for (int i = 0; i < ITEM_COUNT; i++) {
            SceneNode item = new SceneNode();
            item.setFlexDirection(FlexDirection.ROW);
            item.setPreferredHeight(ITEM_HEIGHT);
            item.setPadding(6, 10, 6, 10);
            item.setBackgroundColor((i % 2 == 0) ? ITEM_BG_EVEN : ITEM_BG_ODD);

            SceneNode label = new SceneNode();
            // 条目文本节点设 hitTestable=false 不影响 SCROLL 冒泡：SCROLL 命中走 hit-test 最深命中，
            // 文本节点 pointer-events:none 时命中穿透到 item（item 仍 hitTestable=true），
            // 再沿父链 bubble 冒泡到挂在 viewport 上的 handler。此处保留默认 hitTestable=true 即可，
            // 显式 false 仅为对齐控件标签装饰惯例（不影响冒泡到 viewport）。
            label.setText("列表条目 " + (i + 1) + " / " + ITEM_COUNT);
            label.setTextColor(ITEM_TEXT_COLOR);
            label.setHitTestable(false);
            item.appendChild(label);
            content.appendChild(item);
        }

        // ===== 滚动受控源 + bind（signal-first，geometry 级由 setScrollOffsetY 内部自标） =====
        // bind 的 Invalidation 枚举无 GEOMETRY 级，传 COMPOSITE 仅作声明/校验占位；
        // 真正失效级别由 viewport.setScrollOffsetY 内部 markGeometryDirty() 决定（bind 只负责推值）。
        this.scrollSignal = Signal.create(Integer.valueOf(0));
        runtime.bind(Invalidation.COMPOSITE, scrollSignal,
                v -> viewport.setScrollOffsetY(v.intValue()));

        // ===== SCROLL handler（handler 内零直接 setScrollOffsetY，maxScroll 每帧重算） =====
        runtime.on(viewport, SceneEventType.SCROLL, (ev, ctx) -> {
            // maxScroll 在 handler 内重读 getCachedLayout 重算（比闭包一次性算更稳，布局变化后不失准）
            LayoutBox vb = (LayoutBox) viewport.getCachedLayout();
            LayoutBox cb = (LayoutBox) content.getCachedLayout();
            if (vb == null || cb == null) {
                return;
            }
            int maxScroll = Math.max(0, cb.getHeight() - vb.getHeight());
            // 方向语义：向下滚 wheelDelta<0 → step>0 → offset 增大 → 内容上移（与 SceneScrollViewportTest 一致）
            int step = -ev.getWheelDelta();
            int clamped = Math.max(0, Math.min(maxScroll, scrollSignal.get().intValue() + step));
            // ★ 只写 signal，绝不在此直接 viewport.setScrollOffsetY（守 I1/I11 signal-first）
            scrollSignal.set(Integer.valueOf(clamped));
        });

        // 首次 flush，确保首帧有初始值
        runtime.flush();
    }

    @Override
    protected SceneNode getRoot() {
        return root;
    }

    // ==================== 测试探针（命名对齐项目 __ 前缀惯例，仅供单测断言） ====================

    /** @return 内部场景运行时（供测试造帧路由 + flush） */
    SceneRuntime __getRuntime() {
        return runtime;
    }

    /** @return 内部布局引擎（供测试 doLayout + I7 重排计数断言） */
    SceneLayoutEngine __getLayoutEngine() {
        return layoutEngine;
    }

    /** @return 内部绘制引擎（供测试 fragment 复用计数断言） */
    ScenePaintEngine __getPaintEngine() {
        return paintEngine;
    }

    /** @return 场景树根节点（供测试 layout/route 入口） */
    SceneNode __getRoot() {
        return root;
    }

    /** @return 视口节点（scrollable，供测试断言钉死视口高 + scrollOffsetY） */
    SceneNode __getViewport() {
        return viewport;
    }

    /** @return 内容容器节点（供测试断言 contentHeight 溢出） */
    SceneNode __getContent() {
        return content;
    }

    /** @return 纵向滚动受控源（供测试断言 signal-first 路径终值） */
    Signal<Integer> __getScrollSignal() {
        return scrollSignal;
    }

    /** @return 视口固定高度常量（供测试断言钉死值） */
    static int __getViewportHeight() {
        return VIEWPORT_HEIGHT;
    }
}
