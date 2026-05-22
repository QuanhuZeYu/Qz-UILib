# 2026-05-15 字体重载生成链路屏障

## 错误现象

- 更换字体排序后，同一批中文字符中部分字符会更新到最新字体排序，部分字符仍表现为旧字体。
- 现象集中在字体配置重载与异步字形生成链路交错的窗口，容易被误判为单纯字体 fallback。

## 触发场景

- 字体排序连续变化或字体运行时发生 reload debounce/coalesce。
- 字符绘制请求、后台字形生成、待上传队列和 GL 字形页清理发生在同一段时间内。

## 根本原因

- 字体 reload 缺少显式“代际屏障”概念，旧生成链路暂停、旧 pending 上传丢弃、旧批渲染资源清理、新运行时开放请求这些状态边界不够明确。
- 仅依赖 runtimeVersion 和 generationId 被动拒绝迟到结果，虽然能阻止旧结果写入新页，但不便于确认 reload 完成节点，也不便于排查旧视觉是否来自当前字形页。

## 修复方案

- `FontService.performReloadLocked()` 在真正重载期间进入 `RELOADING` 状态，先暂停并 reset 字形生成调度器，清空旧 pending 上传和渲染资源，再重建字体目录、字符页与 dispatcher。
- `GlyphGenerationDispatcher` 增加重载屏障状态，屏障期间外部 `submit()` 直接丢弃，重新 `initialize()` 后再开放请求。
- `GlyphPage` 绑定创建时的 `runtimeVersion`，`GlyphPageManager` 创建新页时写入当前运行时版本，分配页时跳过非当前版本页面，便于保证 GL 页和 CPU 运行时状态同代。
- 保留 generation handoff：旧 generation 中未完成的可恢复字符在新 dispatcher 初始化后按新 runtimeVersion 重新提交。

## 预防措施

- 不要把 reload 完成理解为“收到请求”或“配置已写入”；reload 完成节点应是旧生成链路已废弃、旧资源已清理、新 runtime 已开放生成请求。
- 不要强等旧 worker 物理退出；应通过 runtimeVersion、generationId 和重载屏障使迟到结果无权写入当前状态。
- 若 reload 完成后仍能看到旧字，优先判断旧视觉是否来自 framebuffer/paint cache/非 UILib 绘制路径，而不是当前 `readyPages -> GlyphPage` 路径。
