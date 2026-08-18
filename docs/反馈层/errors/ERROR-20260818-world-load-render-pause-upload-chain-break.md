# 进入世界后界面文字一直不出现（世界加载窗口渲染帧停摆）

## 现象

- 从主菜单进入世界后，游戏内界面文字长时间不出现；Forge/世界加载画面基本不更新（只显示少量文字与纹理背景）。
- 修复前行为：文字最终缓慢恢复（吞吐瓶颈）；修复后进入世界第一帧文字即就绪。

## 链路与根因

进入世界链路（1.7.10 反编译源码）中存在两个渲染帧完全停摆的窗口：

1. `Minecraft.launchIntegratedServer` 的 `while (!theIntegratedServer.serverIsInRunLoop()) Thread.sleep(200)` —— 服务端启动等待循环，主线程空转数秒~数十秒；
2. `Minecraft.loadWorld → RenderGlobal.setWorldAndLoadRenderers` —— chunk 渲染器同步构建，主线程阻塞数秒。

两个窗口内 `runGameLoop` 停摆 → `RenderTickEvent`（START/END）不触发 → 帧驱动的字符页批上传静默；worker 产出的 glyph 只能在 mailbox 积压。而渲染侧 demand 只在 drawString 时提交，窗口内连需求都停止，进入世界第一帧才开始提交、生成、上传，叠加首帧帧率骤降，表现为文字长时间不出现。

旧版 drawString 内 drawStage 同步上传是另一条兜底通道，字符页批上传迁移到 RenderTick START 稳定阶段后该通道被移除，断链暴露。

## 修复

- 世界加载上传泵：`FontService.pumpWorldLoadUploads()`（复用批上传 `flushPendingUploads(64)`，不参与 reload/reconcile/租约，异常限频告警不传播）。
- `MixinMinecraftWorldLoadPump`：注入 `launchIntegratedServer` 的 `Thread.sleep` 等待点（每次循环泵一批）与 `loadWorld(WorldClient, String)` 入口（chunk 构建前排空残余）。
- 与 RenderTick START 批上传幂等共存（同一 flush 入口）。

## 验证

- 单测：泵未初始化静默/驱动 flush/异常吞并/批大小 64；mixin 注入点与调用契约源码测试；EarlyMixins 客户端列表断言。
- 真机：进入世界第一帧 HUD/聊天文字即出现（用户确认）。
