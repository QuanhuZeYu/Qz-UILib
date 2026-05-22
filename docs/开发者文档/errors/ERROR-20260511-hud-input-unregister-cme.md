# HUD 输入分发期间即时注销触发 ConcurrentModificationException

## 错误现象

- 交互 HUD 在点击回调里立即执行 `unregister()` 时，`UiHudDocumentHost.handleInputFrame(...)` 会抛出 `ConcurrentModificationException`。
- 崩溃点位于 HUD 输入分发对内部 `entries` 列表的 for-each 遍历期间。

## 触发场景

- `UiHudDocumentHost` 正在路由 `INTERACTIVE` HUD 的输入事件。
- HUD 内按钮、关闭入口或其他点击回调在同一调用栈里直接注销当前 HUD。
- `unregister()` 立即修改了宿主当前正在遍历的 `entries` 注册表。

## 根本原因

- 宿主把公开回调执行建立在可变 `ArrayList` 的 fail-fast for-each 迭代上。
- 回调内即时注销是公开 API 的合理使用方式，但宿主没有做快照遍历或延迟移除防御。

## 修复方案

- `UiHudDocumentHost` 输入分发改为遍历 `entries` 快照，而不是直接迭代原列表。
- 每次路由前补充存活检查，已在回调中注销的条目直接跳过，避免同帧继续路由已失效 HUD。
- 补充回归测试，覆盖“点击回调内立即注销 HUD”场景。

## 预防措施

- 后续所有宿主级注册表分发（HUD、Screen、诊断入口等）只要允许回调修改注册关系，就不能直接使用可变集合的 fail-fast 遍历。
- 为这类回调重入/自移除场景保留最小回归测试，防止后续重构再次把遍历退回原列表。
