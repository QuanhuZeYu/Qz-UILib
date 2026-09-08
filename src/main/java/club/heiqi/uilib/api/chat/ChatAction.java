package club.heiqi.uilib.api.chat;

import club.heiqi.uilib.ui.reactive.ReadableSignal;

/**
 * 聊天工具栏动作（候选 API，规划《聊天工具栏与HUD布局编辑》P1）。
 *
 * <p>动作只发布语义：{@link #getAction()} 在安全的主线程阶段执行；注册方通过
 * {@link ChatActionService} 增删，工具栏按 {@link #getOrder()} 排序渲染，隐藏动作不占位、
 * 禁用动作仍显示。首版文本按钮，图标与任意节点注入是后续扩展，不在本类型开放。</p>
 */
public final class ChatAction {
    private final String id;
    private final String label;
    private final String tooltip;
    private final int order;
    private final ReadableSignal<Boolean> visible;
    private final ReadableSignal<Boolean> enabled;
    private final Runnable action;

    private ChatAction(Builder builder) {
        if (builder.id == null || builder.id.trim().isEmpty()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (builder.label == null || builder.label.isEmpty()) {
            throw new IllegalArgumentException("label must not be empty");
        }
        if (builder.visible == null || builder.enabled == null || builder.action == null) {
            throw new IllegalArgumentException("visible/enabled/action must not be null");
        }
        this.id = builder.id;
        this.label = builder.label;
        this.tooltip = builder.tooltip;
        this.order = builder.order;
        this.visible = builder.visible;
        this.enabled = builder.enabled;
        this.action = builder.action;
    }

    /** 以全局唯一稳定 id 创建 builder。 */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    public String getId() { return id; }
    public String getLabel() { return label; }
    public String getTooltip() { return tooltip; }
    public int getOrder() { return order; }
    /** @return 可见性（主线程读取的响应式状态；false = 不占位） */
    public ReadableSignal<Boolean> getVisible() { return visible; }
    /** @return 可用性（主线程读取的响应式状态；false = 显示但禁用） */
    public ReadableSignal<Boolean> getEnabled() { return enabled; }
    /** @return 语义动作（执行失败只影响本动作） */
    public Runnable getAction() { return action; }

    /** 执行动作（工具栏回调入口；异常向上抛由工具栏隔离）。 */
    public void run() {
        action.run();
    }

    /** 聊天动作 builder。 */
    public static final class Builder {
        private final String id;
        private String label;
        private String tooltip;
        private int order;
        private ReadableSignal<Boolean> visible;
        private ReadableSignal<Boolean> enabled;
        private Runnable action;

        private Builder(String id) {
            this.id = id;
        }

        public Builder label(String value) { this.label = value; return this; }
        public Builder tooltip(String value) { this.tooltip = value; return this; }
        public Builder order(int value) { this.order = value; return this; }
        public Builder visible(ReadableSignal<Boolean> value) { this.visible = value; return this; }
        public Builder enabled(ReadableSignal<Boolean> value) { this.enabled = value; return this; }
        public Builder action(Runnable value) { this.action = value; return this; }
        public ChatAction build() { return new ChatAction(this); }
    }
}
