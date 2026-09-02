package club.heiqi.uilib.internal.chat3.view;

import java.util.Map;

import net.minecraft.util.IChatComponent;

import club.heiqi.uilib.internal.chat3.data.ChatLineRecord;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 一次聊天点击的结果:点中的消息节点 + 该点命中的链接 URL(均可为 null)。
 *
 * <p><b>为什么携带节点身份而不是坐标。</b>{@code McScreenBridge} 的注释写死了事实:MC 回调
 * 给的坐标是 guiScale 缩放后的逻辑像素,"不能无损反推物理坐标";而 chat3 的 scene 几何活在
 * 物理像素空间(宿主视口按 mc.displayWidth 物理像素喂入)。两套空间相减得到的"命中点"必然
 * 错位 —— guiScale &gt; 1 时点击全部落空。所以点击的判据只能是 scene 事件自己命中的
 * <b>节点身份</b>,不做任何坐标运算。</p>
 *
 * <p>服务端下发的 {@link IChatComponent} 由节点反查注册表得到,与 URL 字段各自独立:
 * 前者是原版语义(不弹确认框),后者是我们链接化出来的跨度(开浏览器前必弹确认)。</p>
 */
public final class ChatLinkClick {

    private static final ChatLinkClick EMPTY = new ChatLinkClick(null, null);

    private final SceneNode node;
    private final String url;

    ChatLinkClick(SceneNode node, String url) {
        this.node = node;
        this.url = url;
    }

    /** 无任何 scene 点击记录(点击落在所有消息节点之外)。 */
    public static ChatLinkClick empty() {
        return EMPTY;
    }

    /** 点中的消息节点;无节点时 null。 */
    public SceneNode node() {
        return node;
    }

    /** 点中位置命中的链接完整 URL;未命中任何链接跨度时 null。 */
    public String url() {
        return url;
    }

    /** 是否为空记录。 */
    public boolean isEmpty() {
        return node == null && url == null;
    }

    /**
     * 由点中节点反查该消息组件(注册表是节点身份的权威表,不用几何)。
     *
     * @param registry 消息节点 → 记录,与渲染期写入的同一张表
     * @return 该消息的组件;无节点、无记录时 null
     */
    public IChatComponent componentFrom(Map<SceneNode, ChatLineRecord> registry) {
        if (node == null || registry == null) {
            return null;
        }
        ChatLineRecord record = registry.get(node);
        return record == null ? null : record.getComponent();
    }
}
