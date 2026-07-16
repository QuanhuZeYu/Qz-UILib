# HostImage GL 状态污染与无预算栅格化

## 错误现象

Picker 同屏约 20 个 ItemStack 时，每帧重复执行第三方 `RenderItem`；单图异常可能泄漏 FBO、viewport、clip、program、buffer 或矩阵状态，并被 scene 回放器静默吞掉后继续污染后续 UI。

## 触发场景

`PaintCommand.IMAGE` 每帧 replay，旧实现逐图借整屏 scratch FBO；Forge `IItemRenderer(INVENTORY)` 可执行任意模组 renderer，`glPushAttrib` 不覆盖 client attrib、program、buffer、分离 FBO 与矩阵栈。

## 根本原因

缓存只覆盖 Display List 命令构建，不覆盖宿主像素栅格；缓存与预算误放在每帧新建的 `UiRenderContext` 无法跨帧。旧 FBO 生命周期没有覆盖所有阶段的异常安全，且恢复结果没有显式 outcome。

## 修复方案

由跨帧 compositor 持有 identity+尺寸 LRU、2 次/2ms 帧预算、公平队列和 5 秒失败冷却；ItemStack 在小型 FBO 中经完整能力感知状态围栏栅格化，成功且 recovered 才发布。恢复失败抛帧中止信号；clip 外请求在排队前剔除。

## 预防措施

新增宿主 renderer 时必须提供可注入状态访问缝及正常/异常恢复测试；事务测试覆盖 ensure/begin/render/end/composite/release，缓存测试覆盖预算、公平、identity/尺寸、LRU、LIVE/SNAPSHOT、失败冷却与 close。日志只在失败/漂移时输出 kind、registry/meta、stage、error/drift、recovered，禁止 NBT、displayName 与逐帧堆栈。
