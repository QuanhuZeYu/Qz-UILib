# ERROR-20260820 富文本硬换行在非 wrap 场景失效（真机目检修复）

## 现象

playground「富文本」页卡片 2 的演示 `硬换行：<b>第一行<br>第二行</b>` 真机渲染为单行
「第一行第二行」——`<br>` 与裸 `\n` 完全没产生换行。

## 根因

`<br>` 在解析层折叠为段文本内的 `\n`（有单测锁定），但**只有 wrap 路径处理它**：

1. `ScenePaintEngine` 旧逻辑 `wrapWidth>0 ? measurer.splitLines(...) : singletonList(text)`：
   非 wrap 时整段文本作为一条 TEXT 命令直达渲染层；
2. `DefaultFontRendererAdapter.prepareGlyphs` 对 `\n` 是 `continue`（零宽跳过）：
   非 wrap 时 `\n` 不产生 y 推进，文本连排在同一行。

单测 `shouldHonorHardLineBreakWithStyleContinuation` 走的是 `listFormattedStringToWidth`（wrap 路径），
非 wrap 路径从未有测试覆盖，漏洞一直存在。

## 修复

- `TextMeasureServiceSceneAdapter.splitLines`：`wrapWidth<=0` 时以 **Integer.MAX_VALUE 委托 wrap**——
  软换行不触发、硬换行仍拆行，且经 `serialize` 重建保证样式跨行续传（复用 wrap 全链路，零新拆行实现）；
- `ScenePaintEngine`：拆行统一走 `measurer.splitLines`（非 wrap 也拆硬换行，每行一条 TEXT 命令）；
- `SizingCalculator.leafTextHeight`：非 wrap 分支改为逐行行高求和（RAW/MINECRAFT 与旧 countLines 口径等价，
  富文本每行按行内最大显式字号），布局高度与绘制同口径。

## 教训

同一能力（拆行）在 wrap/非 wrap 两条路径各有实现时，测试只覆盖一条路径就会漏掉另一条。
本次收敛为单一入口（measurer.splitLines 全场景），并在三个测试层（adapter 委托、
painter 命令数、layout 高度）各加非 wrap 用例锁定。
