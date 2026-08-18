# Forge 加载界面（Splash）字体接管断链与跨上下文 GL 对象污染

## 现象

- `FontConfig.replaceOrigin` 开启时，Forge 加载界面（SplashProgress）文字不显示或回落原版字体；旧版 UILib 曾可正常接管。
- 历史遗留：旧版主菜单出现时偶发纯色纹理块/脏数据（Splash 渲染竞态，提交 30b8d40b 缓解）。

## 链路与根因

1.7.10 FML `SplashProgress` 在独立线程 + 独立 GL 上下文渲染（Display 尚未创建），文字经 `SplashFontRenderer extends FontRenderer`（只 override bindTexture/getResourceInputStream）→ `MixinFontRenderer` 注入对子类同样生效 → `FontRendererFallbackInvoker` 接管。

旧版 drawString 内同步上传（无渲染线程门槛）→ splash 线程在自身上下文内上传 + 渲染采样，形成自闭环，文字可以显示；代价是 splash 上下文创建的 shader/纹理与主上下文不共享，主菜单沿用即脏。

字符页批上传迁移到 RenderTick START 稳定阶段后，splash 阶段既无渲染循环也无上传入口：glyph 永不上传 → 收集不到 quad → 文字缺失；每次 drawString 重复失败路径后再回落原版。

## 修复（上下文不串 + 一次性重建）

- 未捕获阶段（`isRenderThreadCaptured() == false`）绘制路径按需调 `pumpWorldLoadUploads()`，在 splash 自身上下文内同步上传，恢复加载界面字体接管。
- `FontService` 记录未捕获阶段的上传线程（`lastUploadContextThread`，仅 renderThread 未捕获时记录）。
- 主渲染线程捕获的第一帧（RenderTick START）检测到异上下文 GL 活动 → 一次全量重建：字符页 reset（旧纹理 id 在主上下文 delete 是无害 no-op）+ 批渲染器/着色器置空惰性重建（创建点为惰性 getter）。
- worker/dispatcher 为纯 CPU 状态不重建，渲染侧按需重新提交 demand 自愈；P0-B 惰性清理（requestId 不归零）保证同版本重置无悬挂 token。
- 兜底：若 splash 上下文不支持本仓 shader（GLSL 120），flush 异常经 invoker 既有 catch 回落原版字体，游戏不崩。

## 验证

- 单测：未捕获泵记录上传线程/已捕获不记录；上下文切换触发字符页 reset、批渲染器释放置空、标记清空（Unsafe 轻量分配避免每实例 123MiB direct tables 压爆测试堆）。
- 真机：加载界面 UILib 字体显示、主菜单首帧一次性重建后正常（用户确认）。
