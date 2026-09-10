package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.AnchorProvider;
import club.heiqi.uilib.ui.scene.overlay.AnchoredPortalLayout;
import club.heiqi.uilib.ui.scene.overlay.OverlayDismissPolicy;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.Binding;
import club.heiqi.uilib.ui.scene.runtime.ScenePortalHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceBinder;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * SceneContextMenu —— scene 右键上下文菜单组件。
 *
 * <h3>能力</h3>
 * <ul>
 *   <li>命令式 {@link #open}：在 host 局部坐标（通常为指针位置）打开菜单，经 portalAnchored
 *       提升为 overlay，自动选择向下/向上展开（{@code SceneAnchorResolver.resolveAuto} 边缘翻转），
 *       横向按 safeInset 收拢；</li>
 *   <li>关闭语义：ESC（router 全局 dismiss）、点击菜单外部、选择菜单项、{@link Handle#close()}；</li>
 *   <li>菜单项：label/enabled/分隔线；↑/↓ 循环高亮（跳过分隔线）、Enter 激活高亮项；
 *       指针 hover 进入菜单项即移动高亮（Enter 激活 hover 项、↑/↓ 从 hover 项继续，移出保留）；</li>
 *   <li>打开即聚焦菜单承接键盘导航；关闭由 Handle 幂等（重复 close 无害），关闭后 portal 作用域一并回收。</li>
 * </ul>
 *
 * <h3>外观归属：主题配方是唯一外观来源</h3>
 * <ul>
 *   <li>菜单面板：{@link SceneSurfaceBinder} 从 {@link SceneThemes#surface(SceneRuntime,
 *       SceneTheme.Role) OVERLAY 角色配方}派生 background/border/borderWidth/cornerRadius/
 *       backdrop/surfaceElevation，不再静态写实色底/边框/圆角。面板承接键盘焦点，缘色随配方
 *       {@code focusEdge}；</li>
 *   <li>菜单行：默认全透明露出浮层玻璃，hover/键盘高亮只做主题 accent 系半透明轻量覆盖，
 *       行内不各自采样滤镜（不调用表面绑定器，{@code getBackdrop() == null}）；文字取主题
 *       {@code foreground}、禁用取 {@code disabledForeground}；</li>
 *   <li>分隔线：取浮层配方缘色，随主题更新，不写死实色；</li>
 *   <li>布局常量继续用 {@link SceneChromeTokens}（主题不接管布局）。</li>
 * </ul>
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
    /** 恒真启用信号：菜单浮层不参与 disabled 语义，表面绑定仍需 enabled 通道。 */
    private static final ReadableSignal<Boolean> ALWAYS_ENABLED = () -> Boolean.TRUE;
    /** 菜单行默认背景（全透明，露出浮层玻璃底；行不各自采样滤镜）。 */
    private static final int ITEM_BG_TRANSPARENT = 0x00000000;
    /** 菜单行 hover 覆盖强度：主题 accent 的低透明度轻量覆盖。 */
    private static final int ITEM_HOVER_ALPHA = 0x1F;
    /** 菜单行高亮覆盖强度（指针 hover / 键盘 ↑↓ 同一高亮态）：主题选区背景半透明，明显强于 hover。 */
    private static final int ITEM_HIGHLIGHT_ALPHA = 0x59;

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
        /** 本菜单的 portal 句柄；{@link #open} 内回填，字号入口委托给它。 */
        private ScenePortalHandle portal;

        private Handle(Runnable closeAction) {
            this.closeAction = closeAction;
        }

        /** 回填 portal 句柄（open 内 portalAnchored 返回后调用一次）。 */
        private void attachPortal(ScenePortalHandle portal) {
            this.portal = portal;
        }

        /**
         * 设置菜单文字字号（构建期定值）。
         *
         * <p>菜单是浮层：没有挂载时就存在的控件根，入口在句柄上，与 {@code MountHandle.fontSize} 对称。
         * 未设置时菜单项文字沿用节点默认字号。</p>
         *
         * @param fontSizePx UI 像素字号
         * @return 本句柄（链式）
         */
        public Handle fontSize(int fontSizePx) {
            portal().fontSize(fontSizePx);
            return this;
        }

        /**
         * 设置菜单文字字号（运行时可调）。
         *
         * @param fontSize UI 像素字号信号；null = 不指定
         * @return 本句柄（链式）
         */
        public Handle fontSize(ReadableSignal<Integer> fontSize) {
            portal().fontSize(fontSize);
            return this;
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

        /** @return 委托的本菜单 portal 句柄（facade 不自持第二套声明） */
        private ScenePortalHandle portal() {
            if (portal == null) {
                throw new IllegalStateException("SceneContextMenu.Handle 尚未绑定 portal（open 未完成）");
            }
            return portal;
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
        final ScenePortalHandle[] portalHolder = {null};
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
        // 浮层字号：真值落点 = 内容根（menu）。内容懒建完成后由 ScenePortalRenderer 经
        // handle.__onContentRoot(root) 把层 2 声明写到内容根，菜单项文字沿父链继承。
        portalHolder[0] = rt.portalAnchored(visible,
                () -> buildMenu(rt, safeItems, navigable, highlighted, closeAction),
                OverlayDismissPolicy.DEFAULT,
                closeAction,
                anchor,
                Collections.<SceneNode>emptySet(),
                new AnchoredPortalLayout(MENU_PREFERRED_WIDTH, MENU_MIN_WIDTH, MENU_SAFE_INSET));

        // 关闭后回收 portal 作用域：本组件是一次性浮层（重开即新 open），不回收会让 visible 订阅
        // 常驻到 runtime.dispose()。卸载仍由 visible 信号驱动（handler 不直接挂卸浮层）：本观察者与
        // portal 自身 effect 在同一 flush 内按注册顺序执行——先卸载内容，再回收作用域，故关闭时序不变。
        final Binding[] reclaimer = {null};
        reclaimer[0] = rt.bind(visible, shown -> {
            if (Boolean.TRUE.equals(shown)) {
                return;
            }
            if (reclaimer[0] != null) {
                reclaimer[0].dispose();
            }
            if (portalHolder[0] != null) {
                portalHolder[0].dispose();
            }
        });
        handleHolder[0].attachPortal(portalHolder[0]);
        return handleHolder[0];
    }

    /**
     * 构建菜单 overlay root。
     */
    private static SceneNode buildMenu(SceneRuntime rt, List<MenuItem> items, List<Integer> navigable,
                                       Signal<Integer> highlighted, Runnable closeAction) {
        SceneNode menu = SceneNode.column();
        menu.setPadding(MENU_PADDING);
        menu.setClipChildren(true);

        // 浮层表面：OVERLAY 角色配方是面板外观唯一写入者（background/border/borderWidth/cornerRadius/
        // backdrop/surfaceElevation 全归它）。enabled 恒真：菜单不参与 disabled 语义；配方在 portal
        // 构建调用栈内取，延迟打开时继承来源主题。面板承接键盘焦点，缘色取配方 focusEdge。
        SceneInteractionState interaction = rt.interactionState(menu);
        // 三态必须在 requestFocus 之前显式声明关心：绑定器首次 flush 才读信号，而聚焦写入发生在
        // 构建期，focused signal 未提前创建时会被 writeFocused 的 null 短路吞掉。
        interaction.hovered();
        interaction.pressed();
        interaction.focused();
        ReadableSignal<SceneSurfaceStyle> overlaySurface =
                SceneThemes.surface(rt, SceneTheme.Role.OVERLAY);
        SceneSurfaceBinder.bind(rt, menu, overlaySurface, ALWAYS_ENABLED, interaction);

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
                menu.appendChild(buildSeparator(rt, overlaySurface));
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
     *
     * <p>行不做表面采样（不调 {@link SceneSurfaceBinder}）：默认全透明露出浮层玻璃底，
     * 指针 hover / 键盘高亮只做主题 accent 系半透明覆盖，故 {@code getBackdrop() == null}。</p>
     */
    private static SceneNode buildItem(SceneRuntime rt, MenuItem item, int navIndex,
                                       Signal<Integer> highlighted, Runnable closeAction) {
        SceneNode row = SceneNode.row();
        row.setPadding(ITEM_PAD_V, ITEM_PAD_H, ITEM_PAD_V, ITEM_PAD_H);

        SceneNode label = new SceneNode();
        label.setText(item.label());
        // 菜单项文字跟随浮层字号：真值在浮层内容根（menu 的层 2 声明），本节点不写声明 ⇒ 继承。
        label.setHitTestable(false);
        row.appendChild(label);

        // hover：指针进入项即移动键盘高亮（Enter 激活 hover 项、↑/↓ 从 hover 项继续；移出保留）。
        // ★ 必须先声明 hovered()（懒创建），否则 router 的 writeHovered null 短路，hover 恒 false。
        SceneInteractionState interaction = rt.interactionState(row);
        interaction.hovered();
        rt.bind(interaction.hovered(), hovered -> {
            if (Boolean.TRUE.equals(hovered)) {
                highlighted.set(Integer.valueOf(navIndex));
            }
        });

        // 行只做轻量状态覆盖：高亮取主题选区背景、hover 取主题 accent，均为半透明染色；
        // 不逐项采样滤镜，默认透明露出浮层玻璃底。
        ReadableSignal<Integer> accent = SceneThemes.accent(rt);
        ReadableSignal<Integer> selectionBackground = SceneThemes.selectionBackground(rt);
        rt.__bindAnimatedColor(() -> resolveItemBackground(
                        highlighted.get() != null && highlighted.get().intValue() == navIndex,
                        Boolean.TRUE.equals(interaction.hovered().get()),
                        accent.get(), selectionBackground.get()),
                row::setBackgroundColor, SceneChromeTokens.MOTION_FAST_MS);

        // 语义前景：启用取主题正文前景、禁用取主题禁用前景（主题切换自动重算）。
        ReadableSignal<Integer> foreground = SceneThemes.foreground(rt);
        ReadableSignal<Integer> disabledForeground = SceneThemes.disabledForeground(rt);
        rt.bindComputed(() -> item.enabled() ? foreground.get() : disabledForeground.get(),
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
     * 解析菜单行背景：键盘高亮 &gt; hover &gt; 全透明（露出浮层玻璃底）。
     *
     * @param highlighted         是否高亮（指针 hover 与键盘 ↑↓ 共用同一高亮态）
     * @param hovered             是否悬停
     * @param accent              主题强调色
     * @param selectionBackground 主题选区背景色
     * @return ARGB 背景色
     */
    private static int resolveItemBackground(boolean highlighted, boolean hovered,
                                             int accent, int selectionBackground) {
        if (highlighted) {
            return tint(selectionBackground, ITEM_HIGHLIGHT_ALPHA);
        }
        if (hovered) {
            return tint(accent, ITEM_HOVER_ALPHA);
        }
        return ITEM_BG_TRANSPARENT;
    }

    /**
     * 保留色 RGB、替换 alpha 通道（轻量覆盖用）。
     *
     * @param argb  源色
     * @param alpha 目标 alpha（0..255）
     * @return 替换 alpha 后的 ARGB
     */
    private static int tint(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    /**
     * 构建分隔线（满宽 1px，取浮层配方缘色，随主题更新）。
     */
    private static SceneNode buildSeparator(SceneRuntime rt, ReadableSignal<SceneSurfaceStyle> overlaySurface) {
        SceneNode separator = new SceneNode();
        separator.setPreferredHeight(SEPARATOR_HEIGHT);
        separator.setHitTestable(false);
        rt.bindComputed(() -> overlaySurface.get().getIdle().getEdge(), separator::setBackgroundColor);
        return separator;
    }
}
