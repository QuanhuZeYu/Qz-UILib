# ERROR-20260622-scene-screen-switch-enqueue

## 错误现象

实现 `SceneTestHubScreen` 时，初版在 `drawScreen` 渲染回调末尾直接调用 `Minecraft.displayGuiScreen(...)` 打开子 demo。

## 触发场景

新栈 test hub 在渲染帧内消费 scene 按钮写入的导航 signal，并立即切换到 `SceneDemoScreen`、`SceneControlsDemoScreen`、`SceneScrollDemoScreen` 或 `SceneTableDemoScreen`。

## 根本原因

`displayGuiScreen(...)` 会关闭当前 screen，并触发当前 `McScreenBridge.onGuiClosed()` 释放 `UiSurface`、FBO 合成器和快照资源。若该调用发生在 `drawScreen` 栈内，就会在当前渲染帧尚未完全退出时关闭并 dispose 自己，破坏既有“GUI 打开延迟到帧外”的生命周期约定。

本次还伴随一个前提表述错误：MC 关闭旧 screen 释放的是 GL/FBO 等运行资源，不是释放 Java 对象；`returnScreen` 持有旧实例不构成 use-after-free。准确问题是返回链语义和资源重建时机，而不是悬垂指针。

## 修复方案

`SceneTestHubScreen.openRequestedDemo()` 改为先创建目标 screen，再通过 `UiScreenManager.getInstance().enqueue(...)` 延迟到帧外执行 `displayGuiScreen(...)`，与既有 demo `openDemo()` 模式保持一致。

## 预防措施

- scene 新栈或 MC screen 适配层中，任何由渲染/输入回调触发的切屏都必须走 `UiScreenManager.enqueue`。
- 不在 `drawScreen`、paint replay 或 scene route 栈内直接调用 `displayGuiScreen(...)`。
- 分析 MC screen 生命周期时区分“Java 对象引用仍存活”和“screen 资源已在 onGuiClosed 中释放”，避免把返回链问题误判为内存悬垂问题。
