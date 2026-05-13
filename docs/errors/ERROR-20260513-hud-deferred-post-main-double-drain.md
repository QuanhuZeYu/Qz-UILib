# HUD deferred post-main 双重 drain 导致回放丢失

## 错误现象

- HUD 场景下存在 deferred post-main pass 时，回放批次会被提前清空，导致后续实际回放为空。
- 现象表现为依赖 deferred post-main 的宿主补绘内容静默消失。

## 触发场景

- `UiHudDocumentHost.flushDeferredPostMainPasses(...)` 先对 `UiRenderContext` 执行一次 `drainDeferredPostMainPasses()`。
- 随后又调用 `DocumentHostRenderSupport.flushDeferredPostMainPasses(...)`。
- 共享支持层内部再次执行 `drainDeferredPostMainPasses()`，形成双重消费。

## 根本原因

- 抽象共享渲染逻辑时，没有把“谁负责消费 deferred 队列”明确收敛到单一入口。
- HUD 包装层和共享支持层都保留了 drain 动作，导致第一层把批次清空后，第二层拿到的是空队列。

## 修复方案

- 引入一次性 `DeferredPostMainReplayBatch`，由共享层统一从 `UiRenderContext` 中提取。
- HUD 与 Screen 宿主都只负责提取批次和准备离屏目标，不再直接 drain 原队列。
- 共享层新增无 GL 的 batch replay 入口，便于 JVM 测试直接验证“真实消费 + notifyMainLayerContentChanged()”语义。

## 预防措施

- 共享渲染抽象中，队列消费动作只能保留一个拥有者，其他层只能接收已提取批次。
- 对“提取 + 回放 + 通知”这类流程补充最小回归测试，避免重构时再次把消费职责拆散。
