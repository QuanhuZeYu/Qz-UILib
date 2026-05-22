# 2026-05-03 背包 deferred 物品批次状态泄漏

## 错误现象
- 快捷栏存在物品时，主背包槽位 tooltip 仍能命中，但主背包物品图标不渲染。

## 触发场景
- `inventory_overview` 页面同时存在快捷栏和主背包两个 `DocumentInventorySlotGridControl`。
- 快捷栏先登记并回放物品 deferred pass，主背包随后登记并回放第二个物品 deferred pass。

## 根本原因
- 主后置 deferred 回放只在整批 pass 外层绑定独立 FBO，没有在每个 pass 开始前重置 OpenGL 2D 状态或清理 depth。
- Minecraft 原版物品渲染会临时修改 depth、lighting、alpha、blend、texture 等状态并写入深度缓存，连续多个网格回放时前一个批次可能污染后一个批次。

## 修复方案
- `UiScreenHostSession` 在每个 deferred replay 前重置矩阵、颜色写入、blend、texture、alpha、lighting、cull、depth 状态，并清空 depth buffer，再回放该 pass 的 clip。
- `MinecraftInventorySlotGridItemRenderer` 在批次和单物品边界使用 GL attrib/matrix 保护，并显式准备原版 GUI 物品渲染所需状态。
- 增加同页两个占用网格都会登记和回放物品批次的回归测试。

## 预防措施
- 新增 deferred 宿主绘制能力时，必须保证每个 pass 自包含，不依赖前一个 pass 留下的 OpenGL 状态。
- 需要 depth 的宿主绘制只能在自己的批次内开启和写入，不能让深度内容跨批次影响后续 DOM/overlay/物品回放。
