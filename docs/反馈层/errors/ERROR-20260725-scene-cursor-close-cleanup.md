# scene 关闭后系统光标样式残留

## 现象

scene 宿主界面在指针、文本、隐藏等非默认光标生效时关闭，下一界面可能继续显示上一界面的系统光标，直到后续交互解析出不同样式才恢复。

## 触发场景

`AbstractSceneHostWidget` 已通过 `CursorBackendProvider` 为 `SceneRuntime` 绑定平台光标后端，指针在关闭前命中声明了非默认 `SceneCursor` 的节点；随后 `McScreenBridge.onGuiClosed` 经 surface 进入 `SceneRuntime.dispose()`。关闭过程中不再产生移出事件，也不会再有一帧 signal flush。

## 根因

`SceneRuntime.bindCursor` 只建立 `cursorSignal -> backend.apply` 的响应式 effect，而 `dispose()` 只释放 root Owner。平台系统光标是宿主进程级状态，不会随 effect 退订自动恢复；若尝试在下一界面把初值 `DEFAULT` 写回 signal，还会被 signal 同值去重或宿主缓存短路吸收，真实平台光标因此继续停留在旧样式。

## 修复

每次 `bindCursor` 都把一个幂等复位动作登记到 root runtime 生命周期：关闭时直接调用对应后端的 `forceApply(SceneCursor.DEFAULT)`，绕过普通 apply 与宿主同值缓存，且不修改 `cursorSignal`。`SceneRuntime.dispose()` 再以 `finally` 兜底执行同一复位器，保证 Owner 子树或 effect 清理抛错时仍会尝试扫尾；复位器在调用后端前即标记已尝试，使正常 cleanup、finally 与重复 dispose 最多下发一次。

新增 runtime 层 fake backend 回归测试，分别记录普通 apply 与 forceApply，并覆盖非默认光标关闭、多个后端、重复 dispose、未绑定后端及 Owner cleanup 失败路径。测试源码按 agent 本机禁令未运行，等待 CI 或用户执行。

## 预防

- 系统光标属于平台生命周期资源；scene screen close 必须在 runtime 关闭边界强制恢复默认值，不能依赖 hover 移出、signal flush 或普通 apply 去重链路。
- 关闭扫尾不得改写 `cursorSignal`，必须使用绕过宿主缓存的 `forceApply(DEFAULT)`，并保证异常路径可达且重复 dispose 幂等。
- 本次只收口 screen close。portal、条件节点或动态列表在同一 screen 内卸载后留下的 hover/cursor，需要独立设计结构变化后的重命中与重算，不能把关闭复位外推为已解决。
