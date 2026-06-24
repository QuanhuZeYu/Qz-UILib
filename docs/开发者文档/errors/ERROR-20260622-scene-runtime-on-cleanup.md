# SceneRuntime.on 未随 Owner cleanup 自动退订

## 错误现象

`SceneRuntime.on` 的 Javadoc 已说明：若当前处于 `Owner` 作用域内，输入 handler 应自动登记退订回调，随组件卸载一并移除。但实现仅直接返回 `inputRouter.on(...)` 的 `InputBinding`，没有把 `binding.dispose()` 注册到当前 Owner cleanup。

## 触发场景

- 在 `runtime.mount(parent, () -> { ... })` 的组件 builder 内调用 `runtime.on(node, type, handler)`。
- 后续调用 `MountHandle.dispose()` 卸载该组件。
- 组件节点从树上移除，但 `SceneInputRouter` 的 handler registry 仍可能保留该节点和 handler 引用。

## 根本原因

`bind` 已通过 `Owner.current()` 把 effect 归属到 mount 作用域，但 `on` 是非响应式 handler 注册，不能自动被 Effect 生命周期管理；需要显式把 `InputBinding.dispose()` 写入当前 Owner 的 cleanup。Javadoc 与实现不一致，导致审查时容易误判生命周期已覆盖。

## 修复方案

- `SceneRuntime.on` 调用 `inputRouter.on(...)` 后读取 `Owner.current()`。
- 当前 Owner 存在时调用 `current.onCleanup(binding::dispose)`。
- 保留返回 `InputBinding`，支持调用方手动提前退订；`InputBinding.dispose()` 幂等，cleanup 重复调用安全。
- 新增 `SceneRuntimeTest.onInsideMountShouldBeDisposedWithMount`，断言 mount 内注册的 handler 可触发，且 `MountHandle.dispose()` 后对应 `InputBinding` 已退订。

## 预防措施

- 新增组件在 builder 内注册 `runtime.on`、`interactionState`、focus/cursor 等非 Effect 生命周期资源时，必须确认是否由 Owner cleanup 管理。
- 看到 Javadoc 声称自动清理时，应读实现或补回归测试验证，不能只信注释。
- 可卸载组件相关测试至少覆盖一次 `MountHandle.dispose()` 后资源退订状态。
