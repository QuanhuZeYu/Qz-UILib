package club.heiqi.uilib.ui.remote;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 远程文档表单提交事件。
 */
public final class RemoteDocumentSubmitEvent {

    private final Object player;
    private final String sessionId;
    private final String pageId;
    private final String action;
    private final String formId;
    private final Map<String, List<String>> values;
    private final RemoteDocumentSubmitHandler replyHandler;

    RemoteDocumentSubmitEvent(Object player, String sessionId, String pageId, String action, String formId,
            Map<String, List<String>> values, RemoteDocumentSubmitHandler replyHandler) {
        this.player = player;
        this.sessionId = sessionId == null ? "" : sessionId;
        this.pageId = pageId == null ? "" : pageId;
        this.action = action == null ? "" : action;
        this.formId = formId == null ? "" : formId;
        this.values = deepCopy(values);
        this.replyHandler = replyHandler;
    }

    /**
     * 返回提交玩家对象，服务端通常是 EntityPlayerMP。
     *
     * @return 玩家对象
     */
    public Object getPlayer() {
        return player;
    }

    /**
     * 返回提交玩家并按调用方需要转换类型。
     *
     * @param <T> 玩家类型
     * @return 玩家对象
     */
    @SuppressWarnings("unchecked")
    public <T> T getPlayerAs() {
        return (T) player;
    }

    /**
     * 返回远程页面会话标识。
     *
     * @return 会话标识
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 返回页面业务标识。
     *
     * @return 页面业务标识
     */
    public String getPageId() {
        return pageId;
    }

    /**
     * 返回表单 action。
     *
     * @return action
     */
    public String getAction() {
        return action;
    }

    /**
     * 返回表单 id。
     *
     * @return form id
     */
    public String getFormId() {
        return formId;
    }

    /**
     * 返回表单字段值；同名字段以列表保留。
     *
     * @return 字段值
     */
    public Map<String, List<String>> getValues() {
        return values;
    }

    /**
     * 返回指定字段的第一个值。
     *
     * @param name 字段名
     * @return 第一个值；不存在时为空字符串
     */
    public String getFirstValue(String name) {
        List<String> list = values.get(name);
        return list == null || list.isEmpty() ? "" : list.get(0);
    }

    /**
     * 使用同一个提交处理器回复并打开新页面。
     *
     * @param page 新页面
     */
    public void reply(RemoteDocumentPage page) {
        reply(page, replyHandler);
    }

    /**
     * 使用指定提交处理器回复并打开新页面。
     *
     * @param page 新页面
     * @param handler 新页面提交处理器
     */
    public void reply(RemoteDocumentPage page, RemoteDocumentSubmitHandler handler) {
        RemoteDocumentPages.open(player, page, handler);
    }

    private static Map<String, List<String>> deepCopy(Map<String, List<String>> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, List<String>> copy = new LinkedHashMap<String, List<String>>();
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            List<String> list = entry.getValue();
            copy.put(entry.getKey(), Collections.unmodifiableList(list == null
                    ? Collections.<String>emptyList() : new ArrayList<String>(list)));
        }
        return Collections.unmodifiableMap(copy);
    }
}
