# HostImage 在 Core Profile 误判 legacy 固定管线能力

> 历史记录：本记录描述的 FBO 栅格化与状态围栏机制（含 `HostImageGlStateGuard`）已随 2026-08-13 物品渲染上层替换改造（提交 323d25da）整体删除，保留为演进记录。

## 错误现象

Angelica/LWJGL3ify 的 OpenGL Core Profile 下，配置页首个 HostImage 曾在状态恢复阶段先后报
`texture stack underflow 0 < 1` 与 `server-attrib stack underflow 0 < 1`，随后按 fail-closed 合同中止
当前 UI 帧。栈能力逐项降级后，所有 ItemStack 又在 capture 阶段报
`operation=capture.texture-bindings gl-error=1280`，没有任何快照成功发布，表现为对象组 Picker 图标全空白。

## 触发场景

HostImage 栅格化 ItemStack 时，驱动对已移除的固定管线 texture matrix、server attribute stack 或
client attribute stack 深度查询返回 0，或以 GL error 表示对应能力不可用。
栈问题越过后，同一 Core Profile 会对已移除的 `GL_CLIENT_ACTIVE_TEXTURE` 查询返回
`GL_INVALID_ENUM`；`GL_ACTIVE_TEXTURE`、server texture unit 与 `GL_TEXTURE_BINDING_2D` 仍然合法。

## 根本原因

状态围栏无条件把各固定管线栈当作可用能力：捕获深度后继续 push、normalize 与 pop；texture matrix
还会读取并参与 drift 比较。Core Profile 的 0 不是合法栈深度，而是对应子能力不可用；围栏却把它当成
真实入口深度，恢复时形成 `0 < 1` 的伪下溢。后续能力感知修复又让内置 Minecraft renderer 与
强制外层 guard 各自建立同类 attrib 围栏：内层恢复失败先抛异常，外层仍可能恢复自己的栈帧并把真正
状态污染误报为 `stage=render,recovered=true`；内层二次探测还会清除外层 capture 后留下的 GL error。
问题与具体物品（包括 `minecraft:log`）无关。LWJGL 暴露 `GL13` 符号或 context 声明 OpenGL 1.3，
不代表 compatibility-only 的 client-active texture API 仍受当前 profile 支持；把它与 server binding
聚合在同一检查点，导致一个 legacy client 子状态阻断全部合法 server texture 捕获。

## 修复方案

- 在其他捕获操作前按顺序探测 texture matrix、server attribute stack 与 client attribute stack：texture
  depth 查询无 GL error 且值 `>= 1` 才可用；server/client attrib 的合法初始 depth 可以是 `0`，查询无错即表示可用。
- 入口 GL error 已由总围栏预检保证为空；探测报错时只清理由该查询产生的错误队列，避免污染后续围栏。
- 任一必需 legacy 围栏不可用时立即停止后续 capture，执行无净状态变化的恢复并返回 `UNAVAILABLE`；
  不调用不可信 ItemStack renderer，也不继续查询已知不可用的固定管线 API。未知 probe error 仍返回
  `HOST_STATE_LOST`。
- attrib 能力探测、push/pop、normalize 只保留在不可绕过的外层 `HostImageGlStateGuard`（已随上层替换删除）；内置 Minecraft
  renderer 直接执行既有绘制逻辑，不再二次探测或建立同类恢复边界。
- 支持路径仍保留完整恢复验证；renderer 弹掉围栏栈帧时继续报告不可恢复并中止帧。
- 为 client-active texture 增加独立运行态 probe：query 成功后用原值试调 setter，确保探测成功时状态不变；
  query `GL_INVALID_ENUM` 或 setter 明确返回 `GL_INVALID_ENUM`/`GL_INVALID_OPERATION` 时，排空本次 probe
  错误并只标记该子能力不可用。其它未知错误携带 `client-active-query/setter-gl-error` 证据中止 capture。
- client 子能力不可用时，capture/restore 均不再调用其普通路径；已捕获的 server active texture、
  texture0/入口 active unit 的 2D binding 会回到入口值，随后以 `UNAVAILABLE` 拒绝 delegate。支持路径仍对
  server/client texture、stack depth、program、VAO/VBO/EBO、FBO/renderbuffer 与矩阵做恢复后验证。

## 预防措施

固定管线与 legacy client-state 必须逐子能力探测，不得由版本位或一个 stack 的结果推断另一个；每项都要把合法值域与紧邻查询的
GL error 一并作为能力门。同一生产调用链只能有一个强制恢复边界，禁止 renderer 嵌套同类围栏；否则
内层诊断会被外层恢复语义降级。recognized capability 缺失只降级当前图片为 `UNAVAILABLE`，不得关闭整个
UI guard，也不得吞掉未知 GL error 或帧中止异常。query/setter probe 应使用原值保证无净状态变化，并在 capture 与
restore 共用同一能力快照，禁止 restore 再乐观重试已判定不支持的 API。

## 验证与待验收

自动化覆盖 texture/server/client depth=0、查询错误并清理、depth>=1 完整保护、强制外层包装器与
delegate 的真实组合：普通 renderer 异常恢复后为 `UNAVAILABLE`，attrib 栈帧被破坏时固定为
`HOST_STATE_LOST` 并触发 `ABORT_FRAME`；结构测试锁定 Minecraft delegate 不再声明内层围栏。
后续细粒度诊断已把 31 条限频 warning 全部定位到
`phase=capture operation=capture.texture-bindings gl-error=1280`，覆盖多个原版与模组 registry，证明不是
公平队列或毒物品。自动化现覆盖 client query INVALID_ENUM 降级并排空、compatibility query+same-value
setter 与对称恢复、setter 不支持时拒绝 delegate、未知错误 fail-closed、入口错误不进入 probe、server binding
恢复，以及 server binding drift 不可恢复。仍待用户实机确认受支持 profile 的 ItemStack 缓存恢复显示，日志不再出现
client-active/capture.texture-bindings 1280；若出现新的细粒度 operation，再按首错证据定点处理。
