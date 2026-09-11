package club.heiqi.uilib.ui.hud.api;

import java.util.Objects;

/**
 * 可编辑 HUD 目标（公共契约，候选 API）：第三方 Mod 声明「某个 HUD 可以在聊天输入屏的
 * 编辑子模式里被预览与拖动布局」。
 *
 * <p>本类是不可变值对象：注册进 {@link HudEditService} 后不再变化。放置真值仍属
 * {@link HudLayoutService}（会话内唯一事实源），本类只提供三件编辑期所需的事实：</p>
 * <ul>
 *   <li>{@link #getPreviewFactory()}：编辑期预览内容根，与 {@link HudWindowFactory} 同契约
 *       （{@code build(SceneRuntime)} 返回内容根 {@code SceneNode}）；</li>
 *   <li>{@link #getDefaultPlacement()}：无用户覆盖时的起始放置（builder 缺省 =
 *       {@link #DEFAULT_MARGIN_PX} 的左下角默认，与 {@link HudSpec} 默认 margin 同值）；</li>
 *   <li>{@link #getToolbarSpec()}：预览浮层是否也装配外接工具栏（null = 不挂；非 null 时
 *       由编辑宿主从 {@link HudToolbarService} 取同一 hudId 的工具栏工厂装配，工具栏因此
 *       成为预览外框的一部分，参与 {@link HudLayoutResolver#clamp} 口径）。</li>
 * </ul>
 *
 * <p>与 {@link HudSpec} 的关系：{@code HudSpec} 描述「关闭态 HUD 窗口怎么渲染与放置」，
 * 本类只描述「编辑期怎么预览与拖动」。同一 hudId 允许两处独立注册；第三方不注册本类时
 * 既有的 HUD 行为完全不变（编辑期不出现该目标的预览）。</p>
 */
public final class HudEditTarget {

    /**
     * 缺省放置的兜底 margin（logical px）。
     *
     * <p>数值与 {@link HudSpec} builder 的默认 margin 相同：调用方不声明默认放置时，
     * 编辑预览从「左下角 + 8px」起步，与既有 HUD 默认外观一致。</p>
     */
    public static final int DEFAULT_MARGIN_PX = 8;

    private final String hudId;
    private final HudWindowFactory previewFactory;
    private final HudPlacement defaultPlacement;
    private final HudToolbarSpec toolbarSpec;

    private HudEditTarget(Builder builder) {
        this.hudId = builder.hudId;
        this.previewFactory = builder.previewFactory;
        this.defaultPlacement = builder.defaultPlacement == null
                ? HudPlacement.defaultOf(HudAnchor.BOTTOM_LEFT, DEFAULT_MARGIN_PX)
                : builder.defaultPlacement;
        this.toolbarSpec = builder.toolbarSpec;
    }

    /**
     * 创建目标 builder。
     *
     * @param hudId 目标 HUD id（非空白；与 {@link HudSpec#getId()} 同域）
     * @return builder
     */
    public static Builder builder(String hudId) {
        return new Builder(hudId);
    }

    /** @return 目标 HUD id */
    public String getHudId() {
        return hudId;
    }

    /** @return 编辑期预览内容根工厂（恒非 null：build 时已校验） */
    public HudWindowFactory getPreviewFactory() {
        return previewFactory;
    }

    /** @return 无用户覆盖时的起始放置（恒非 null） */
    public HudPlacement getDefaultPlacement() {
        return defaultPlacement;
    }

    /** @return 预览浮层外接工具栏规格；null = 预览不挂工具栏 */
    public HudToolbarSpec getToolbarSpec() {
        return toolbarSpec;
    }

    @Override
    public String toString() {
        return "HudEditTarget{" + hudId + ", default=" + defaultPlacement
                + ", toolbar=" + (toolbarSpec != null) + '}';
    }

    /** HUD 编辑目标 builder。 */
    public static final class Builder {

        private final String hudId;
        private HudWindowFactory previewFactory;
        private HudPlacement defaultPlacement;
        private HudToolbarSpec toolbarSpec;

        private Builder(String hudId) {
            if (hudId == null || hudId.trim().isEmpty()) {
                throw new IllegalArgumentException("hudId must not be blank");
            }
            this.hudId = hudId;
        }

        /** 设置预览内容根工厂（必填，缺失时 {@link #build()} 明确拒绝）。 */
        public Builder previewFactory(HudWindowFactory value) {
            this.previewFactory = value;
            return this;
        }

        /** 设置无用户覆盖时的起始放置（缺省 = 左下角 + {@link #DEFAULT_MARGIN_PX}）。 */
        public Builder defaultPlacement(HudPlacement value) {
            this.defaultPlacement = value;
            return this;
        }

        /** 设置预览浮层外接工具栏规格；不设置 = 预览不挂工具栏。 */
        public Builder toolbarSpec(HudToolbarSpec value) {
            this.toolbarSpec = value;
            return this;
        }

        /**
         * 构建不可变目标。
         *
         * @return 编辑目标
         * @throws NullPointerException 预览工厂缺失（预览必须有内容根，不静默降级为空）
         */
        public HudEditTarget build() {
            Objects.requireNonNull(previewFactory, "previewFactory");
            return new HudEditTarget(this);
        }
    }
}
