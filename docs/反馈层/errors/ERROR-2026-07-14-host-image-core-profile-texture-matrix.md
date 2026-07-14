# HostImage 在 Core Profile 误判 texture matrix 栈下溢

## 错误现象

Angelica/LWJGL3ify 的 OpenGL Core Profile 下，配置页首个 HostImage 在状态恢复阶段报
`texture stack underflow 0 < 1`，随后按 fail-closed 合同中止当前 UI 帧。

## 触发场景

HostImage 栅格化 ItemStack 时，驱动对已移除的固定管线 `GL_TEXTURE_STACK_DEPTH` 查询返回 0，
或以 GL error 表示该能力不可用。

## 根本原因

状态围栏无条件把 texture matrix 当作可用能力：捕获深度后继续读取、push、normalize、pop 和 drift
比较。Core Profile 的 0 不是合法栈深度，而是能力不可用；围栏却把它当成真实入口深度，恢复时形成
`0 < 1` 的伪下溢。问题与具体物品（包括 `minecraft:log`）无关。

## 修复方案

- 在其他捕获操作前独立探测 texture matrix：深度查询无 GL error 且值 `>= 1` 才启用子围栏。
- 入口 GL error 已由总围栏预检保证为空；探测报错时只清理由该查询产生的错误队列，避免污染后续围栏。
- 能力不可用时仅跳过 texture matrix 的读取、push/pop、normalize 与 drift 比较；MODELVIEW、PROJECTION、
  attrib、program、FBO、buffer 等保护不变。
- 支持路径仍保留完整恢复验证；renderer 弹掉围栏栈帧时继续报告不可恢复并中止帧。

## 预防措施

固定管线能力不得仅凭查询返回值存在就乐观使用；必须把合法值域与紧邻查询的 GL error 一并作为能力门。
能力降级应收窄到最小子围栏，不得关闭整个 guard，也不得吞掉帧中止异常。

## 验证与待验收

自动化覆盖 depth=0、查询错误并清理、depth>=1 完整保护及支持路径真实下溢四类情况。
仍待用户在 Angelica/LWJGL3ify Core Profile 中打开 `QzMinerConfigGUI`，确认物品图标可渐进栅格化且日志
不再出现 `texture stack underflow`。
