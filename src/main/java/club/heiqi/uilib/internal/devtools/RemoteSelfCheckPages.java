package club.heiqi.uilib.internal.devtools;

import java.util.List;
import java.util.Map;

import club.heiqi.uilib.ui.remote.RemoteDocumentPage;
import club.heiqi.uilib.ui.remote.RemoteDocumentPages;
import club.heiqi.uilib.ui.remote.RemoteDocumentResourcePolicy;
import club.heiqi.uilib.ui.remote.RemoteDocumentSubmitEvent;
import club.heiqi.uilib.ui.remote.RemoteDocumentSubmitHandler;
import club.heiqi.uilib.ui.remote.RemoteHudOverlay;
import club.heiqi.uilib.ui.remote.RemoteHudOverlays;
import club.heiqi.uilib.ui.remote.RemoteHudSubmitEvent;
import club.heiqi.uilib.ui.remote.RemoteHudSubmitHandler;

/**
 * 远程页面与 HUD 网络自检页面构造器。
 */
final class RemoteSelfCheckPages {

    private RemoteSelfCheckPages() {}

    /**
     * 打开远程页面 smoke 自检。
     *
     * @param player 目标玩家
     * @param checkId 自检标识
     * @return 远程会话标识
     */
    static String openRemotePageSmoke(Object player, final String checkId) {
        return RemoteDocumentPages.open(player, buildRemotePageSmokePage(checkId),
                new RemoteDocumentSubmitHandler() {
                    @Override
                    public void onSubmit(RemoteDocumentSubmitEvent event) {
                        handleRemotePageSmokeSubmit(event, checkId);
                    }
                });
    }

    /**
     * 打开远程 HUD smoke 自检。
     *
     * @param player 目标玩家
     * @param checkId 自检标识
     * @return 远程 HUD 会话标识
     */
    static String openRemoteHudSmoke(Object player, final String checkId) {
        String sessionId = RemoteHudOverlays.open(player, buildRemoteHudSmokeOverlay(checkId),
                new RemoteHudSubmitHandler() {
                    @Override
                    public void onSubmit(RemoteHudSubmitEvent event) {
                        handleRemoteHudSmokeSubmit(event, checkId);
                    }
                });
        RemoteHudOverlays.open(player, buildRemoteHudDanmakuOverlay(checkId), null);
        return sessionId;
    }

    /**
     * 创建远程页面运行时 smoke 页面。
     *
     * @param checkId 自检标识
     * @return 远程页面
     */
    private static RemoteDocumentPage buildRemotePageSmokePage(String checkId) {
        return RemoteDocumentPage.builder("qz-runtime-remote-page")
                .title("远程页面运行时自检")
                .resourcePolicy(RemoteDocumentResourcePolicy.LOCAL_RESOURCES_ONLY)
                .metadata("checkId", checkId)
                .html(buildRemotePageSmokeHtml(checkId))
                .build();
    }

    /**
     * 生成远程页面 smoke HTML。
     *
     * @param checkId 自检标识
     * @return HTML 文本
     */
    private static String buildRemotePageSmokeHtml(String checkId) {
        String escapedCheckId = escapeHtml(checkId);
        return "<html><head><title>远程页面运行时自检</title><style>"
                + ".smoke{box-sizing:border-box;width:100%;padding:14px;background-color:#0f172a;color:#e5e7eb;}"
                + ".hint{color:#bfdbfe;margin:6px 0;}"
                + ".field{margin:8px 0 4px 0;color:#cbd5e1;}"
                + "input,textarea,select{width:calc(100% - 8px);margin:4px 0;padding:6px;}"
                + "button{margin-top:10px;padding:8px 12px;}"
                + "</style></head><body><div class=\"smoke\">"
                + "<h1>远程页面运行时自检</h1>"
                + "<p class=\"hint\">这页由服务端通过 RemoteDocumentPages.open 下发，客户端会用 Stream 拉取 HTML。</p>"
                + "<p class=\"hint\">保持默认值，点击提交即可验证表单收集与 C2S 回传。</p>"
                + "<form id=\"remote-smoke-form\" action=\"runtime-remote-submit\">"
                + "<input type=\"hidden\" name=\"checkId\" value=\"" + escapedCheckId + "\">"
                + "<input type=\"hidden\" name=\"disabledField\" value=\"blocked\" disabled>"
                + "<p class=\"field\">只读文本字段</p>"
                + "<input type=\"text\" name=\"textEcho\" value=\"text-ok\" readonly maxlength=\"32\">"
                + "<input type=\"checkbox\" name=\"flag\" value=\"checked-ok\" checked data-label=\"复选框字段\">"
                + "<input type=\"radio\" name=\"mode\" value=\"ignored\" data-label=\"未选模式\">"
                + "<input type=\"radio\" name=\"mode\" value=\"selected-ok\" checked data-label=\"已选模式\">"
                + "<p class=\"field\">只读多行文本</p>"
                + "<textarea name=\"note\" readonly maxlength=\"64\">textarea-ok</textarea>"
                + "<p class=\"field\">选择字段</p>"
                + "<select name=\"phase\"><option value=\"wrong\">错误项</option>"
                + "<option value=\"stream-ok\" selected>Stream 已拉取</option></select>"
                + "<p><a href=\"#submit-area\">跳到提交按钮</a></p>"
                + "<button id=\"submit-area\" type=\"submit\" name=\"submitter\" value=\"提交运行时自检\"></button>"
                + "</form></div></body></html>";
    }

    /**
     * 校验远程页面 smoke 表单提交。
     *
     * @param event 提交事件
     * @param checkId 自检标识
     */
    private static void handleRemotePageSmokeSubmit(RemoteDocumentSubmitEvent event, String checkId) {
        StringBuilder problems = new StringBuilder();
        requireSubmitted(problems, "pageId", "qz-runtime-remote-page", event.getPageId());
        requireSubmitted(problems, "action", "runtime-remote-submit", event.getAction());
        requireSubmitted(problems, "formId", "remote-smoke-form", event.getFormId());
        requireSubmitted(problems, "checkId", checkId, event.getFirstValue("checkId"));
        requireSubmitted(problems, "textEcho", "text-ok", event.getFirstValue("textEcho"));
        requireSubmitted(problems, "note", "textarea-ok", event.getFirstValue("note"));
        requireSubmitted(problems, "phase", "stream-ok", event.getFirstValue("phase"));
        requireSubmitted(problems, "submitter", "提交运行时自检", event.getFirstValue("submitter"));
        if (!hasValue(event.getValues(), "flag", "checked-ok")) {
            appendSubmitProblem(problems, "flag 未提交 checked-ok");
        }
        if (!hasValue(event.getValues(), "mode", "selected-ok")) {
            appendSubmitProblem(problems, "mode 未提交 selected-ok");
        }
        if (hasValue(event.getValues(), "mode", "ignored")) {
            appendSubmitProblem(problems, "未选中的 radio 被提交");
        }
        if (event.getValues().containsKey("disabledField")) {
            appendSubmitProblem(problems, "disabled 字段不应被提交");
        }
        event.reply(buildRemotePageSmokeResultPage(checkId, problems.length() == 0, problems.toString()), null);
    }

    /**
     * 创建远程页面 smoke 结果页。
     *
     * @param checkId 自检标识
     * @param success 是否通过
     * @param detail 失败详情
     * @return 结果页
     */
    private static RemoteDocumentPage buildRemotePageSmokeResultPage(String checkId, boolean success, String detail) {
        String title = success ? "远程页面自检通过" : "远程页面自检失败";
        String body = success
                ? "<h1>远程页面运行时自检通过</h1>"
                        + "<p>HTML Stream 拉取、解析、表单收集和 C2S 提交回调均已完成。</p>"
                : "<h1>远程页面运行时自检失败</h1><p>" + escapeHtmlLines(detail) + "</p>";
        return RemoteDocumentPage.builder("qz-runtime-remote-page-result")
                .title(title)
                .resourcePolicy(RemoteDocumentResourcePolicy.LOCAL_RESOURCES_ONLY)
                .metadata("checkId", checkId)
                .html("<html><body><div style=\"padding:16px;background-color:#0f172a;color:#e5e7eb;\">"
                        + body + "<p>checkId: " + escapeHtml(checkId) + "</p></div></body></html>")
                .build();
    }

    /**
     * 创建远程 HUD 运行时 smoke 浮层。
     *
     * @param checkId 自检标识
     * @return 远程 HUD 浮层
     */
    private static RemoteHudOverlay buildRemoteHudSmokeOverlay(String checkId) {
        return RemoteHudOverlay.dialog("qz-runtime-remote-hud-dialog", buildRemoteHudSmokePage(checkId))
                .metadata("checkId", checkId)
                .build();
    }

    /**
     * 创建远程 HUD 弹幕浮层。
     *
     * @param checkId 自检标识
     * @return 远程 HUD 浮层
     */
    private static RemoteHudOverlay buildRemoteHudDanmakuOverlay(String checkId) {
        return RemoteHudOverlay.danmaku("qz-runtime-remote-hud-danmaku", buildRemoteHudDanmakuPage(checkId))
                .metadata("checkId", checkId)
                .build();
    }

    /**
     * 创建远程 HUD smoke 页面。
     *
     * @param checkId 自检标识
     * @return 远程页面
     */
    private static RemoteDocumentPage buildRemoteHudSmokePage(String checkId) {
        return RemoteDocumentPage.builder("qz-runtime-remote-hud-page")
                .title("远程 HUD 运行时自检")
                .resourcePolicy(RemoteDocumentResourcePolicy.LOCAL_RESOURCES_ONLY)
                .metadata("checkId", checkId)
                .html(buildRemoteHudSmokeHtml(checkId))
                .build();
    }

    /**
     * 创建远程 HUD 弹幕页面。
     *
     * @param checkId 自检标识
     * @return 远程页面
     */
    private static RemoteDocumentPage buildRemoteHudDanmakuPage(String checkId) {
        return RemoteDocumentPage.builder("qz-runtime-remote-hud-danmaku-page")
                .title("远程 HUD 弹幕自检")
                .resourcePolicy(RemoteDocumentResourcePolicy.LOCAL_RESOURCES_ONLY)
                .metadata("checkId", checkId)
                .html("<div style=\"padding:6px 10px;background-color:#0f172a;color:#e0f2fe;\">"
                        + "HUD 弹幕已发送，checkId: " + escapeHtml(checkId) + "</div>")
                .build();
    }

    /**
     * 生成远程 HUD smoke HTML。
     *
     * @param checkId 自检标识
     * @return HTML 文本
     */
    private static String buildRemoteHudSmokeHtml(String checkId) {
        String escapedCheckId = escapeHtml(checkId);
        return "<html><head><title>远程 HUD 运行时自检</title><style>"
                + ".hud{box-sizing:border-box;width:100%;background-color:#111827;color:#e5e7eb;border:1px solid #38bdf8;border-radius:8px;overflow:visible;}"
                + ".drag-handle{box-sizing:border-box;width:100%;padding:10px 56px 10px 12px;background-color:#1f2937;color:#e0f2fe;cursor:move;}"
                + ".hud-body{box-sizing:border-box;width:100%;padding:12px 14px 14px 14px;}"
                + ".hint{color:#bfdbfe;margin:6px 0;}"
                + ".field{margin:8px 0 4px 0;color:#cbd5e1;}"
                + ".probe{box-sizing:border-box;margin:8px 0;padding:8px;border:1px solid #334155;border-radius:6px;overflow:visible;}"
                + ".probe-title{margin:0 0 4px 0;color:#e0f2fe;}"
                + ".overlap-probe{position:relative;min-height:78px;overflow:visible;}"
                + ".under-button{margin-top:4px;background-color:#312e81;color:#e0e7ff;}"
                + ".clip-probe{height:48px;overflow:hidden;background-color:#0f172a;}"
                + "input,textarea,select{width:calc(100% - 8px);margin:4px 0;padding:6px;}"
                + "button{margin-top:10px;padding:8px 12px;}"
                + "</style></head><body><div class=\"hud\">"
                + "<div class=\"drag-handle\" data-qz-hud-drag-handle=\"true\">远程 HUD 运行时自检 · 拖住这里移动</div>"
                + "<div class=\"hud-body\">"
                + "<p class=\"hint\">这页由服务端通过 RemoteHudOverlays.open 下发，客户端会用 Stream 拉取 HTML。</p>"
                + "<p class=\"hint\">phase 保持默认；将三个对比下拉改为通过项后提交，可验证 HUD top-layer 命中。</p>"
                + "<form id=\"remote-hud-smoke-form\" action=\"runtime-hud-submit\">"
                + "<input type=\"hidden\" name=\"checkId\" value=\"" + escapedCheckId + "\">"
                + "<p class=\"field\">只读文本字段</p>"
                + "<input type=\"text\" name=\"textEcho\" value=\"text-ok\" readonly maxlength=\"32\">"
                + "<input type=\"checkbox\" name=\"flag\" value=\"checked-ok\" checked data-label=\"复选框字段\">"
                + "<input type=\"radio\" name=\"mode\" value=\"ignored\" data-label=\"未选模式\">"
                + "<input type=\"radio\" name=\"mode\" value=\"selected-ok\" checked data-label=\"已选模式\">"
                + "<p class=\"field\">只读多行文本</p>"
                + "<textarea name=\"note\" readonly maxlength=\"64\">textarea-ok</textarea>"
                + "<p class=\"field\">选择字段</p>"
                + "<select name=\"phase\"><option value=\"wrong\">错误项</option>"
                + "<option value=\"hud-ok\" selected>HUD 已渲染</option></select>"
                + "<div class=\"probe\"><p class=\"probe-title\">普通下拉对比</p>"
                + "<select name=\"normalProbe\"><option value=\"normal-wrong\">普通下拉未通过</option>"
                + "<option value=\"normal-ok\">普通下拉通过</option></select></div>"
                + "<div class=\"probe overlap-probe\"><p class=\"probe-title\">覆盖按钮下拉对比</p>"
                + "<select name=\"overlapProbe\"><option value=\"overlap-wrong\">覆盖下拉未通过</option>"
                + "<option value=\"overlap-ok\">覆盖下拉通过</option></select>"
                + "<button class=\"under-button\" type=\"button\" name=\"underButton\" value=\"下层按钮\">下层按钮</button></div>"
                + "<div class=\"probe clip-probe\"><p class=\"probe-title\">裁剪容器下拉对比</p>"
                + "<select name=\"clippedProbe\"><option value=\"clipped-wrong\">裁剪下拉未通过</option>"
                + "<option value=\"clipped-ok\">裁剪下拉通过</option></select></div>"
                + "<p><a href=\"#submit-area\">跳到提交按钮</a></p>"
                + "<button id=\"submit-area\" type=\"submit\" name=\"submitter\" value=\"提交远程 HUD 自检\"></button>"
                + "</form></div></div></body></html>";
    }

    /**
     * 校验远程 HUD smoke 表单提交。
     *
     * @param event 提交事件
     * @param checkId 自检标识
     */
    private static void handleRemoteHudSmokeSubmit(RemoteHudSubmitEvent event, String checkId) {
        StringBuilder problems = new StringBuilder();
        requireSubmitted(problems, "overlayId", "qz-runtime-remote-hud-dialog", event.getOverlayId());
        requireSubmitted(problems, "pageId", "qz-runtime-remote-hud-page", event.getPageId());
        requireSubmitted(problems, "action", "runtime-hud-submit", event.getAction());
        requireSubmitted(problems, "formId", "remote-hud-smoke-form", event.getFormId());
        requireSubmitted(problems, "checkId", checkId, event.getFirstValue("checkId"));
        requireSubmitted(problems, "textEcho", "text-ok", event.getFirstValue("textEcho"));
        requireSubmitted(problems, "note", "textarea-ok", event.getFirstValue("note"));
        requireSubmitted(problems, "phase", "hud-ok", event.getFirstValue("phase"));
        requireSubmitted(problems, "normalProbe", "normal-ok", event.getFirstValue("normalProbe"));
        requireSubmitted(problems, "overlapProbe", "overlap-ok", event.getFirstValue("overlapProbe"));
        requireSubmitted(problems, "clippedProbe", "clipped-ok", event.getFirstValue("clippedProbe"));
        requireSubmitted(problems, "submitter", "提交远程 HUD 自检", event.getFirstValue("submitter"));
        if (!hasValue(event.getValues(), "flag", "checked-ok")) {
            appendSubmitProblem(problems, "flag 未提交 checked-ok");
        }
        if (!hasValue(event.getValues(), "mode", "selected-ok")) {
            appendSubmitProblem(problems, "mode 未提交 selected-ok");
        }
        if (hasValue(event.getValues(), "mode", "ignored")) {
            appendSubmitProblem(problems, "未选中的 radio 被提交");
        }
        event.reply(buildRemoteHudSmokeResultOverlay(checkId, problems.length() == 0, problems.toString()), null);
        event.dismiss();
    }

    /**
     * 创建远程 HUD smoke 结果浮层。
     *
     * @param checkId 自检标识
     * @param success 是否通过
     * @param detail 失败详情
     * @return 结果浮层
     */
    private static RemoteHudOverlay buildRemoteHudSmokeResultOverlay(String checkId, boolean success, String detail) {
        String title = success ? "远程 HUD 自检通过" : "远程 HUD 自检失败";
        String body = success
                ? "<h1>远程 HUD 运行时自检通过</h1>"
                        + "<p>HTML Stream 拉取、解析、表单收集和 C2S 提交回调均已完成。</p>"
                : "<h1>远程 HUD 运行时自检失败</h1><p>" + escapeHtmlLines(detail) + "</p>";
        RemoteDocumentPage page = RemoteDocumentPage.builder("qz-runtime-remote-hud-result")
                .title(title)
                .resourcePolicy(RemoteDocumentResourcePolicy.LOCAL_RESOURCES_ONLY)
                .metadata("checkId", checkId)
                .html("<html><body><div style=\"padding:16px;background-color:#0f172a;color:#e5e7eb;\">"
                        + body + "<p>checkId: " + escapeHtml(checkId) + "</p></div></body></html>")
                .build();
        return RemoteHudOverlay.toast("qz-runtime-remote-hud-result", page)
                .durationMillis(6000L)
                .build();
    }

    /**
     * 校验单个字段的首值。
     */
    private static void requireSubmitted(StringBuilder problems, String name, String expected, String actual) {
        if (!expected.equals(actual)) {
            appendSubmitProblem(problems, name + " 不一致: expected=" + expected + ", actual=" + actual);
        }
    }

    /**
     * 判断字段是否包含指定值。
     */
    private static boolean hasValue(Map<String, List<String>> values, String name, String expected) {
        List<String> list = values.get(name);
        return list != null && list.contains(expected);
    }

    /**
     * 追加提交校验问题。
     */
    private static void appendSubmitProblem(StringBuilder problems, String message) {
        if (problems.length() > 0) {
            problems.append('\n');
        }
        problems.append("- ").append(message);
    }

    /**
     * HTML 转义普通文本。
     */
    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * HTML 转义多行文本，并保留换行展示。
     */
    private static String escapeHtmlLines(String value) {
        return escapeHtml(value).replace("\n", "<br>");
    }


}
