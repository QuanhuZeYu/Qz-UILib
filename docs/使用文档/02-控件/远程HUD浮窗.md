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
        .html("<h1>奖励已送达</h1><p>请确认领取。</p>")
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

- `DIALOG`：居中浮窗，默认显示关闭按钮；标题栏可拖拽移动；在 HUD 可交互的宿主界面中可点击按钮、输入表单。
- `TOAST`：角落提示，默认几秒后自动消失；默认不接收命中测试，适合作为纯展示通知。
- `DANMAKU`：从右向左移动的弹幕浮层；默认自动消失并不接收命中测试。

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

## 资源与安全边界

资源策略沿用 `RemoteDocumentResourcePolicy`。`img src` 可按策略使用 Minecraft `ResourceLocation`
或 HTTP/HTTPS 图片；`a href="#id"` 保持页内跳转；HTTP/HTTPS 外链需要客户端确认后才会调用系统浏览器。

`script`、`iframe`、`object`、`embed`、`javascript:`、`data:`、`vbscript:` 等执行能力始终被禁止。

## 运行时验证

`/qzuilib test` 的“运行时远程 HUD”会打开一个远程 HUD 表单和一条弹幕。点击 HUD 内提交按钮后，
服务端会验证字段收集和 C2S 回调，并用一个结果 toast 显示通过或失败。
