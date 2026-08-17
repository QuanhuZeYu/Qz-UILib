package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.AnchorProvider;
import club.heiqi.uilib.ui.scene.overlay.AnchoredPortalLayout;
import club.heiqi.uilib.ui.scene.overlay.OverlayDismissPolicy;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * SceneContextMenu —— scene 右键上下文菜单组件。
 *
 * <h3>能力</h3>
 * <ul>
 *   <li>命令式 {@link #open}：在 host 局部坐标（通常为指针位置）打开菜单，经 portalAnchored
 *       提升为 overlay，自动选择向下/向上展开（{@code SceneAnchorResolver.resolveAuto} 边缘翻转），
 *       横向按 safeInset 收拢；</li>
 *   <li>关闭语义：ESC（router 全局 dismiss）、点击菜单外部、选择菜单项、{@link Handle#close()}；</li>
 *   <li>菜单项：label/enabled/分隔线；↑/↓ 循环高亮（跳过分隔线）、Enter 激活高亮项；</li>
 *   <li>打开即聚焦菜单承接键盘导航；关闭由 Handle 幂等（重复 close 无害）。</li>
 * </ul>
 *
 * <p>样式内置 SceneChromeTokens（BG_DEFAULT 底、SELECTION_BG/SELECTION_TEXT 高亮、TEXT_DISABLED
 * 禁用态、RADIUS_MD 圆角、1px 边框），不拆 primitive/wrapper——菜单无受控状态与 chrome 变体需求。</p>
 */
public final class SceneContextMenu {

    /** 菜单首选宽度（宽屏）。 */
    private static final int MENU_PREFERRED_WIDTH = 160;
    /** 菜单最小宽度（窄屏收窄下限）。 */
    private static final int MENU_MIN_WIDTH = 96;
    /** 菜单距宿主左右边缘的安全边距。 */
    private static final int MENU_SAFE_INSET = 8;
    /** 菜单内边距。 */
    private static final int MENU_PADDING = SceneChromeTokens.PAD_SM;
    /** 菜单项水平内边距。 */
    private static final int ITEM_PAD_H = 8;
    /** 菜单项垂直内边距。 */
    private static final int ITEM_PAD_V = 6;
    /** 分隔线高度（像素）。 */
    private static final int SEPARATOR_HEIGHT = 1;

    /** 纯静态工厂，禁止实例化。 */
    private SceneContextMenu() {
    }

    /**
     * 菜单项。
     *
     * @param label     显示文本
     * @param enabled   是否可激活（false 时点击/Enter 无效且文本灰显）
     * @param separator 是否为分隔线（label/onSelect 忽略）
     * @param onSelect  激活回调（点击或 Enter 高亮项时执行；separator/disabled 可为 null）
     */
    @Desugar
    public record MenuItem(String label, boolean enabled, boolean separator, Runnable onSelect) {
        public MenuItem {
            label = label == null ? "" : label;
        }

        /**
         * 创建启用菜单项。
         *
         * @param label    显示文本
         * @param onSelect 激活回调（不可为 null）
         * @return 菜单项
         */
        public static MenuItem of(String label, Runnable onSelect) {
            return new MenuItem(label, true, false, Objects.requireNonNull(onSelect, "onSelect"));
        }

        /**
         * 创建可指定启停态的菜单项。
         *
         * @param label    显示文本
         * @param enabled  是否可激活
         * @param onSelect 激活回调（enabled=false 时可为 null）
         * @return 菜单项
         */
        public static MenuItem of(String label, boolean enabled, Runnable onSelect) {
            return new MenuItem(label, enabled, false,
                    enabled ? Objects.requireNonNull(onSelect, "onSelect") : onSelect);
        }

        /**
         * 创建分隔线（静态工厂命名 divider 以避免与 record accessor separator() 冲突）。
         *
         * @return 分隔线菜单项
         */
        public static MenuItem divider() {
            return new MenuItem("", true, true, null);
        }
    }

    /**
     * 菜单打开句柄。
     *
     * <p>{@link #close()} 幂等；菜单已因 ESC/外部点击/选择关闭后再 close 无副作用。</p>
     */
    public static final class Handle {
        private final Runnable closeAction;
        private boolean open = true;

        private Handle(Runnable closeAction) {
            this.closeAction = closeAction;
        }

        /**
         * 关闭菜单（幂等）。
         */
        public void close() {
            closeAction.run();
        }

        /**
         * @return 菜单当前是否打开（dismiss 请求尚未物化前仍为 true）
         */
        public boolean isOpen() {
            return open;
        }

        private void markClosed() {
            open = false;
        }
    }

    /**
     * 在指定 host 局部坐标打开上下文菜单。
     *
     * @param rt    场景运行时
     * @param x     菜单锚点 X（host 局部坐标，通常为指针 X）
     * @param y     菜单锚点 Y（host 局部坐标，通常为指针 Y）
     * @param items 菜单项列表（防御性复制；可为空列表）
     * @return 打开句柄（close 幂等）
     */
    public static Handle open(SceneRuntime rt, int x, int y, List<MenuItem> items) {
        Objects.requireNonNull(rt, "rt");
        List<MenuItem> safeItems = items == null ? Collections.<MenuItem>emptyList()
                : SceneListOps.immutableCopy(items);
        // 可导航项下标（非分隔线）；disabled 项可高亮但不可激活
        List<Integer> navigable = new ArrayList<>();
        for (int i = 0; i < safeItems.size(); i++) {
            if (!safeItems.get(i).separator()) {
                navigable.add(Integer.valueOf(i));
            }
        }

        final Handle[] handleHolder = {null};
        Signal<Boolean> visible = Signal.create(Boolean.TRUE);
        // 键盘高亮（可导航项序）；null=无高亮（纯鼠标态）
        Signal<Integer> highlighted = Signal.create(null);
        Runnable closeAction = () -> {
            if (handleHolder[0] != null) {
                handleHolder[0].markClosed();
            }
            visible.set(Boolean.FALSE);
        };
        handleHolder[0] = new Handle(closeAction);

        AnchorProvider anchor = new AnchorProvider() {
            @Override
            public AnchorRect get() {
                return new AnchorRect(x, y, 1, 1);
            }
        };
        rt.portalAnchored(visible,
                () -> buildMenu(rt, safeItems, navigable, highlighted, closeAction),
                OverlayDismissPolicy.DEFAULT,
                closeAction,
                anchor,
                Collections.<SceneNode>emptySet(),
                new AnchoredPortalLayout(MENU_PREFERRED_WIDTH, MENU_MIN_WIDTH, MENU_SAFE_INSET));
        return handleHolder[0];
    }

    /**
     * 构建菜单 overlay root。
     */
    private static SceneNode buildMenu(SceneRuntime rt, List<MenuItem> items, List<Integer> navigable,
                                       Signal<Integer> highlighted, Runnable closeAction) {
        SceneNode menu = SceneNode.column();
        menu.setPadding(MENU_PADDING);
        menu.setBackgroundColor(SceneChromeTokens.BG_DEFAULT);
        menu.setBorderWidth(1);
        menu.setBorderColor(SceneChromeTokens.BORDER_DEFAULT);
        menu.setCornerRadius(SceneChromeTokens.RADIUS_MD);
        menu.setClipChildren(true);

        rt.focusable(menu, Signal.create(Boolean.TRUE));
        rt.on(menu, SceneEventType.KEY_DOWN, (ev, ctx) -> {
            if (ev.getKeyAction() != SceneKeyAction.PRESSED) {
                return;
            }
            SceneKey key = ev.getKey();
            if (key == SceneKey.ARROW_DOWN) {
                moveHighlight(navigable, highlighted, 1);
                ctx.stopPropagation();
            } else if (key == SceneKey.ARROW_UP) {
                moveHighlight(navigable, highlighted, -1);
                ctx.stopPropagation();
            } else if (key == SceneKey.ENTER) {
                activateHighlighted(items, navigable, highlighted, closeAction);
                ctx.stopPropagation();
            }
        });

        int navIndex = 0;
        for (int i = 0; i < items.size(); i++) {
            MenuItem item = items.get(i);
            if (item.separator()) {
                menu.appendChild(buildSeparator());
            } else {
                final int nav = navIndex++;
                menu.appendChild(buildItem(rt, item, nav, highlighted, closeAction));
            }
        }
        // 打开即聚焦菜单，承接 ↑/↓/Enter 键盘导航（ESC 由 router 全局 dismiss 处理）
        rt.requestFocus(menu);
        return menu;
    }

    /**
     * 移动键盘高亮（可导航项序，首尾循环）。
     *
     * @param navigable  可导航项下标列表
     * @param highlighted 高亮 signal
     * @param delta       +1 下移 / -1 上移
     */
    private static void moveHighlight(List<Integer> navigable, Signal<Integer> highlighted, int delta) {
        int n = navigable.size();
        if (n == 0) {
            return;
        }
        int current = highlighted.get() == null ? (delta > 0 ? -1 : 0) : highlighted.get().intValue();
        int next = current + delta;
        if (next < 0) {
            next = n - 1;
        }
        if (next >= n) {
            next = 0;
        }
        highlighted.set(Integer.valueOf(next));
    }

    /**
     * 激活键盘高亮项：enabled 才回调，随后关闭。
     */
    private static void activateHighlighted(List<MenuItem> items, List<Integer> navigable,
                                            Signal<Integer> highlighted, Runnable closeAction) {
        if (highlighted.get() == null || navigable.isEmpty()) {
            return;
        }
        int nav = highlighted.get().intValue();
        if (nav < 0 || nav >= navigable.size()) {
            return;
        }
        MenuItem item = items.get(navigable.get(nav).intValue());
        if (item.enabled() && item.onSelect() != null) {
            item.onSelect().run();
        }
        closeAction.run();
    }

    /**
     * 构建菜单项行（label + 高亮/禁用样式 + 点击激活）。
     */
    private static SceneNode buildItem(SceneRuntime rt, MenuItem item, int navIndex,
                                       Signal<Integer> highlighted, Runnable closeAction) {
        SceneNode row = SceneNode.row();
        row.setPadding(ITEM_PAD_V, ITEM_PAD_H, ITEM_PAD_V, ITEM_PAD_H);

        SceneNode label = new SceneNode();
        label.setText(item.label());
        label.setHitTestable(false);
        row.appendChild(label);

        Computed<Boolean> isHighlighted = Computed.create(() ->
                Boolean.valueOf(highlighted.get() != null && highlighted.get().intValue() == navIndex));
        rt.bindComputed(() -> Boolean.TRUE.equals(isHighlighted.get())
                        ? SceneChromeTokens.SELECTION_BG : SceneChromeTokens.BG_DEFAULT,
                row::setBackgroundColor);
        rt.bindComputed(() -> !item.enabled() ? SceneChromeTokens.TEXT_DISABLED
                        : Boolean.TRUE.equals(isHighlighted.get()) ? SceneChromeTokens.SELECTION_TEXT
                        : SceneChromeTokens.TEXT_PRIMARY,
                label::setTextColor);

        rt.on(row, SceneEventType.CLICK, (ev, ctx) -> {
            if (item.enabled() && item.onSelect() != null) {
                item.onSelect().run();
            }
            closeAction.run();
            ctx.stopPropagation();
        });
        return row;
    }

    /**
     * 构建分隔线（满宽 1px 边框色）。
     */
    private static SceneNode buildSeparator() {
        SceneNode separator = new SceneNode();
        separator.setPreferredHeight(SEPARATOR_HEIGHT);
        separator.setBackgroundColor(SceneChromeTokens.BORDER_DEFAULT);
        separator.setHitTestable(false);
        return separator;
    }
}
