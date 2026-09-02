package club.heiqi.uilib.internal.chat3.view;

import net.minecraft.util.IChatComponent;

/**
 * 一次聊天点击的结果:被点消息的服务端组件 + 该点命中的链接 URL(均可为 null)。
 *
 * <p><b>为什么不携带坐标。</b>{@code McScreenBridge} 的注释写死了事实:MC 回调给的坐标是
 * guiScale 缩放后的逻辑像素,"不能无损反推物理坐标";而 chat3 的 scene 几何活在物理像素空间
 * (宿主视口按 mc.displayWidth 物理像素喂入)。两套空间相减得到的"命中点"必然错位 ——
 * guiScale &gt; 1 时点击全部落空。命中判定因此完全在 scene 事件内完成(框架给的节点局部
 * 坐标),对外只交出**结果**。</p>
 *
 * <p><b>为什么是即时投递而不是暂存。</b>scene 的 CLICK 由 {@code SceneInputRouter} 在
 * POINTER_UP 合成,而 {@code GuiScreen.mouseClicked} 对应 POINTER_DOWN —— 同一次点击里
 * DOWN 早于 UP。旧实现在 DOWN 时取"上一次记下的账",于是第一次点击必然无反应、
 * 第二次点击打开的是第一次点的那条(真机表现:「要点第二下才有效」)。本类型只在
 * CLICK 发生的那一刻被构造并立即投递,不存在残留。</p>
 *
 * <p>两个字段各自独立:{@link #component()} 是原版语义(服务端下发的 clickEvent,
 * 优先级更高,{@code RUN_COMMAND} / {@code SUGGEST_COMMAND} 不弹确认框);
 * {@link #url()} 是我们链接化出来的跨度(开浏览器前必弹确认)。</p>
 */
public final class ChatLinkClick {

    private final IChatComponent component;
    private final String url;

    public ChatLinkClick(IChatComponent component, String url) {
        this.component = component;
        this.url = url;
    }

    /** 被点消息的组件(用于取服务端下发的 clickEvent);无组件时 null。 */
    public IChatComponent component() {
        return component;
    }

    /** 点中位置命中的链接完整 URL;未命中任何链接跨度时 null。 */
    public String url() {
        return url;
    }
}
