package club.heiqi.uilib.ui.remote;

import java.util.List;
import java.util.Map;

/**
 * 远程 HTML 表单提交出口。
 *
 * <p>远程页面与远程 HUD 共用同一套 HTML/Form 解析语义，但提交回传的网络通道不同，
 * 因此由解析器持有该轻量出口来保持控件行为一致。</p>
 */
interface RemoteFormSubmitSink {

    /**
     * 提交表单字段。
     *
     * @param sessionId 远程会话标识
     * @param pageId 页面业务标识
     * @param action 表单 action
     * @param formId 表单 id
     * @param values successful controls 字段值
     */
    void submit(String sessionId, String pageId, String action, String formId,
            Map<String, List<String>> values);

    /**
     * 提交携带远程 UI surface 与 revision 的表单字段。
     *
     * @param sessionId 远程会话标识
     * @param surfaceId surface 标识
     * @param contentRevision 内容版本
     * @param pageId 页面业务标识
     * @param action 表单 action
     * @param formId 表单 id
     * @param values successful controls 字段值
     */
    default void submit(String sessionId, String surfaceId, long contentRevision, String pageId,
            String action, String formId, Map<String, List<String>> values) {
        submit(sessionId, pageId, action, formId, values);
    }
}
