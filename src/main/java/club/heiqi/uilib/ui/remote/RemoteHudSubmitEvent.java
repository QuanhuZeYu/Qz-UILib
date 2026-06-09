package club.heiqi.uilib.ui.remote;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 远程 HUD 表单提交事件。
 */
public final class RemoteHudSubmitEvent {

    private final Object player;
    private final String sessionId;
    private final String overlayId;
    private final String pageId;
    private final String action;
    private final String formId;
    private final Map<String, List<String>> values;
    private final RemoteHudSubmitHandler replyHandler;

    RemoteHudSubmitEvent(Object player, String sessionId, String overlayId, String pageId, String action,
            String formId, Map<String, List<String>> values, RemoteHudSubmitHandler replyHandler) {
        this.player = player;
        this.sessionId = safe(sessionId);
        this.overlayId = safe(overlayId);
        this.pageId = safe(pageId);
        this.action = safe(action);
        this.formId = safe(formId);
        this.values = deepCopy(values);
        this.replyHandler = replyHandler;
    }

    public Object getPlayer() {
        return player;
    }

    @SuppressWarnings("unchecked")
    public <T> T getPlayerAs() {
        return (T) player;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getOverlayId() {
        return overlayId;
    }

    public String getPageId() {
        return pageId;
    }

    public String getAction() {
        return action;
    }

    public String getFormId() {
        return formId;
    }

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
     * 使用同一个提交处理器回复并打开新的 HUD 浮层。
     *
     * @param overlay 新浮层
     */
    public void reply(RemoteHudOverlay overlay) {
        reply(overlay, replyHandler);
    }

    /**
     * 使用指定提交处理器回复并打开新的 HUD 浮层。
     *
     * @param overlay 新浮层
     * @param handler 新浮层提交处理器
     */
    public void reply(RemoteHudOverlay overlay, RemoteHudSubmitHandler handler) {
        RemoteHudOverlays.open(player, overlay, handler);
    }

    /**
     * 替换当前 overlayId 对应的 HUD 浮层。
     *
     * @param overlay 新浮层
     */
    public void replace(RemoteHudOverlay overlay) {
        reply(overlay);
    }

    /**
     * 关闭当前 HUD 浮层。
     */
    public void dismiss() {
        RemoteHudOverlays.dismissSession(player, overlayId, sessionId);
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

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
