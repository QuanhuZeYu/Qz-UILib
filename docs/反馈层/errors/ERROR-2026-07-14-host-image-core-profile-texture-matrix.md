# HostImage 在 Core Profile 误判固定管线栈能力

## 错误现象

Angelica/LWJGL3ify 的 OpenGL Core Profile 下，配置页首个 HostImage 曾在状态恢复阶段先后报
`texture stack underflow 0 < 1` 与 `server-attrib stack underflow 0 < 1`，随后按 fail-closed 合同中止
当前 UI 帧。

## 触发场景

HostImage 栅格化 ItemStack 时，驱动对已移除的固定管线 texture matrix、server attribute stack 或
client attribute stack 深度查询返回 0，或以 GL error 表示对应能力不可用。

## 根本原因

状态围栏无条件把各固定管线栈当作可用能力：捕获深度后继续 push、normalize 与 pop；texture matrix
还会读取并参与 drift 比较。Core Profile 的 0 不是合法栈深度，而是对应子能力不可用；围栏却把它当成
真实入口深度，恢复时形成 `0 < 1` 的伪下溢。后续能力感知修复又让内置 Minecraft renderer 与
强制外层 guard 各自建立同类 attrib 围栏：内层恢复失败先抛异常，外层仍可能恢复自己的栈帧并把真正
状态污染误报为 `stage=render,recovered=true`；内层二次探测还会清除外层 capture 后留下的 GL error。
问题与具体物品（包括 `minecraft:log`）无关。

## 修复方案

- 在其他捕获操作前分别探测 texture matrix、server attribute stack 与 client attribute stack：各自深度
  查询无 GL error 且值 `>= 1` 才启用对应子围栏。
- 入口 GL error 已由总围栏预检保证为空；探测报错时只清理由该查询产生的错误队列，避免污染后续围栏。
- 能力不可用时仅跳过对应子围栏的 push/pop/normalize；texture matrix 还跳过读取与 drift 比较。其它
  MODELVIEW、PROJECTION、program、FBO、buffer 等保护不变，server/client 能力不互相代替。
- attrib 能力探测、push/pop、normalize 只保留在不可绕过的外层 `HostImageGlStateGuard`；内置 Minecraft
  renderer 直接执行既有绘制逻辑，不再二次探测或建立同类恢复边界。
- 支持路径仍保留完整恢复验证；renderer 弹掉围栏栈帧时继续报告不可恢复并中止帧。

## 预防措施

固定管线栈必须逐子能力探测，不得由一个 stack 的结果推断另一个；每项都要把合法值域与紧邻查询的
GL error 一并作为能力门。同一生产调用链只能有一个强制恢复边界，禁止 renderer 嵌套同类围栏；否则
内层诊断会被外层恢复语义降级。能力降级应收窄到最小子围栏，不得关闭整个 guard，也不得吞掉
renderer 或帧中止异常。

## 验证与待验收

自动化覆盖 texture/server/client depth=0、查询错误并清理、depth>=1 完整保护、强制外层包装器与
delegate 的真实组合：普通 renderer 异常可恢复，attrib 栈帧被破坏时固定为
`stage=restore,recovered=false` 并触发 `ABORT_FRAME`；结构测试锁定 Minecraft delegate 不再声明内层围栏。
仍待用户在 Angelica/LWJGL3ify Core Profile 中打开 `QzMinerConfigGUI`，确认物品图标可渐进栅格化且日志
不再出现 `texture/server-attrib/client-attrib stack underflow` 或 `exit-gl-error`。
