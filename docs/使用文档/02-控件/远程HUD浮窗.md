# 服务端下发远程 HUD 浮窗

`RemoteHudOverlays` 用于让服务端把安全子集 HTML 下发给客户端，并显示在 HUD 宿主上。
它适合服务端通知、需要按钮关闭的弹窗、自动消失提示和视频弹幕式消息。

远程 HUD 和 [远程页面](远程页面.md) 使用同一套 HTML/CSS/Form 解析能力：不执行 JavaScript，
不嵌入真实浏览器；HTML 会被解析为 `UiDocument`、样式表和 `Document*Control` 控件。

## 最小示例

需要用户关闭的弹窗：

```java
RemoteDocumentPage page = RemoteDocumentPage.builder("reward-dialog")
        .title("奖励已送达")
        .html("<div style=\"background:#111827;color:#e5e7eb;border:1px solid #38bdf8;border-radius:8px;\">"
                + "<div data-qz-hud-drag-handle=\"true\" style=\"padding:10px 56px 10px 12px;cursor:move;\">"
                + "奖励已送达 · 拖住这里移动</div>"
                + "<div style=\"padding:12px;\"><p>请确认领取。</p></div></div>")
        .build();

RemoteHudOverlays.open(player, RemoteHudOverlay.dialog("reward-dialog", page).build());
```

自动消失提示：

```java
RemoteDocumentPage page = RemoteDocumentPage.of("tip", "提示",
        "<div style=\"padding:8px;color:#e5e7eb;\">服务器保存成功</div>");

RemoteHudOverlays.open(player, RemoteHudOverlay.toast("save-tip", page)
        .durationMillis(3500L)
        .build());
```

弹幕：

```java
RemoteHudOverlays.open(player, RemoteHudOverlay.danmaku("notice-1",
        RemoteDocumentPage.of("danmaku", "弹幕", "<span>欢迎来到服务器</span>")).build());
```

## 模式

- `DIALOG`：居中浮窗，默认显示关闭按钮；在 HUD 可交互的宿主界面中可点击按钮、输入表单。
  宿主 shell 从创建开始就是 fixed 浮窗，并在真实 HUD 视口可用后完成初始居中；拖拽只更新已有 `left/top`，不会在第一次拖拽时切换定位模型。
  宿主只提供居中、拖拽和关闭按钮承载，不生成额外标题栏或可见父容器；页面内容的背景、边框、内边距等视觉外观由下发 HTML/CSS 决定。
  若页面内存在 `data-qz-hud-drag-handle="true"` 元素，会优先把它作为拖拽把手；否则整块解析内容作为兜底拖拽区域。
  下拉选择框展开后按顶层弹出层处理，点击选项会优先被 HUD 页面消费，不应穿透到下方按钮或原生界面。
- `TOAST`：角落提示，默认几秒后自动消失；默认不接收命中测试，适合作为纯展示通知。
- `DANMAKU`：从右向左移动的弹幕浮层；默认自动消失并不接收命中测试。弹幕外观由下发 HTML/CSS 决定，
  HUD 宿主只负责轨道、位移和生命周期，不额外绘制默认胶囊外壳。

HUD 的可见与交互规则仍遵循 `UiHudDocumentHost`：交互层在游戏内和容器类界面可见，
但只有容器/聊天等 HUD 可交互上下文且鼠标未被游戏抓取时才路由输入。

## 表单回调

远程 HUD 表单提交使用 `RemoteHudSubmitHandler`：

```java
RemoteHudOverlays.open(player, RemoteHudOverlay.dialog("rename-hud", page).build(),
        new RemoteHudSubmitHandler() {
            @Override
            public void onSubmit(RemoteHudSubmitEvent event) {
                String name = event.getFirstValue("name");
                event.reply(RemoteHudOverlay.toast("rename-result",
                        RemoteDocumentPage.of("rename-result", "完成",
                                "<p>已收到：" + escapeForHtml(name) + "</p>")).build(), null);
                event.dismiss();
            }
        });
```

`RemoteHudSubmitEvent` 会提供 `player`、`sessionId`、`overlayId`、`pageId`、`action`、`formId`
和 `Map<String, List<String>> values`。`reply(...)` 可继续打开 HUD 浮层，`dismiss()` 会关闭当前浮层。

## Session 生命周期

远程 HUD 复用远程页面的服务端 HTML session，默认有效期为 10 分钟，并同时覆盖 HTML 拉取和后续表单提交。
session 过期后服务端会向客户端发送带 `sessionId` 的关闭通知，只关闭对应 session 的 HUD；只有无 session 的强制关闭才按 `overlayId` 回退。
如果同一个 `overlayId` 被重新打开，旧 session 迟到的关闭或过期通知不会关闭新的 HUD。

## 资源与安全边界

资源策略沿用 `RemoteDocumentResourcePolicy`。`img src` 可按策略使用 Minecraft `ResourceLocation`
或 HTTP/HTTPS 图片；`a href="#id"` 保持页内跳转；HTTP/HTTPS 外链需要客户端确认后才会调用系统浏览器。

`script`、`iframe`、`object`、`embed`、`javascript:`、`data:`、`vbscript:` 等执行能力始终被禁止。

## 运行时验证

当前 `/qzuilib test` 进入 test 页面系统性重构期，旧“运行时远程 HUD”入口已清空。远程 HUD 交互 smoke 会按 `docs/开发者文档/specs/qzuilib-test-page-rebuild-plan.md` 的 RemoteNet 分组恢复：预期仍是打开远程 HUD 表单和弹幕，点击 HUD 内提交按钮后由服务端验证字段收集和 C2S 回调，并显示通过或失败结果。
