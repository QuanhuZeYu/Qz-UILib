# HUD 输入桥接口与静态辅助方法重载冲突

## 错误现象

为收口 input 包对 HUD 宿主的反向依赖时，让 `UiHudDocumentHost` 实现新增的输入参与者接口，并把接口方法命名为 `isInteractiveInputEnabled(...)`。主代码修正一次显式转型后可编译，但测试代码中 `UiHudDocumentHost.isInteractiveInputEnabled(null, null, false)` 仍被 Java
解析到新增实例方法，导致 `compileTestJava` 报“无法从静态上下文中引用非静态方法”。

## 触发场景

- 目标类已有 public static 辅助方法，且测试使用 `null` 参数覆盖边界。
- 后续为该目标类补充接口实现时，新增实例方法与静态方法同名、参数数量一致，且参数类型更具体。

## 根本原因

Java 方法解析会先按名称和参数候选集匹配；`null` 实参能匹配更具体的引用类型签名。新增实例方法虽然不能从静态上下文调用，但仍会成为更优候选，最终在静态调用点触发编译错误。

## 修复方案

将输入参与者接口方法改名为 `isHostInputCaptureEnabled(...)`，避免与 `UiHudDocumentHost.isInteractiveInputEnabled(...)` 静态辅助入口形成同名重载；`UiHostInputCoordinator` 只调用接口新名称，HUD 原有静态测试入口保持不变。

## 预防措施

- 给已有宿主类补接口时，先搜索类内 public static 测试辅助方法，避免新增同名实例方法。
- 如果必须复用相近语义，接口方法名应描述“桥接场景”，不要直接复刻宿主类原有静态工具名。
- 修改后同时运行 `compileJava` 和 `test`，因为主代码与测试代码中的 `null` 调用可能触发不同的重载解析结果。
