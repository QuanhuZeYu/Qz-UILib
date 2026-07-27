# 决策：experimental long Entry 容器

## 状态

**设计定稿，尚未实现。** 本能力是 experimental，不进入 v4.x LTS 稳定 API，不承诺 4.x 补丁兼容；本文件不改变 `NORTH_STAR.md`、稳定边界或既有 API。

## 背景

Issue [#66](https://github.com/QuanhuZeYu/Qz-UILib/issues/66) 需要一个能与 scene 并置、又能承载超过 vanilla `int` 数量范围的容器视觉。现有 `InventorySlotSnapshot` 直接持有 live `ItemStack`，`SlotContentSnapshot` 是视觉快照；两者都不是新的存储真值。生产宿主仍是 `McScreenBridge extends GuiScreen`，没有 `GuiContainer` scene host。

目标是先固定可拆分的纯数据契约，不把一次实验方案写入稳定边界，也不在设计阶段假定 Java、网络或游戏内实现已经存在。

## 候选方案

1. **继续扩展 vanilla Slot/ItemStack**：改数量类型并让 scene 直接认识 MC。否决，无法表达 long 真值，且穿透平台边界。
2. **引入 fake Slot 或通用库存/事务引擎**：把现有 vanilla 交互模型泛化。否决，职责过宽且会把容器身份、库存仲裁和失败恢复混在一起。
3. **纯数据 Entry + backend storage port + scene 投影 + MC adapter**：Entry 只描述稳定身份和数量，backend 决定容量/merge，host 只做平台转换。选择此方案。

## 最终选择

- **Entry 不等于 Slot**：`EntryKey` 是 backend 不透明稳定 key；Entry 没有玩家槽 index、vanilla Slot 生命周期或假 Slot 语义。
- **纯数据核心与 MC adapter 分离**：model、storage、operation、presentation 不 import Minecraft、Forge 或 LWJGL；`ItemDescriptor` 是不可变值，payload 做 defensive copy。
- **storage / render / operation 三向解耦**：storage 只提供 snapshot、insert、extract；presentation 只把 descriptor 转成图标/名称/tooltip；operation 只描述 semantic intent。没有 raw click、scene、玩家槽、掉落、网络或事务框架进入 storage port。
- **容量与 identity 由 backend 定义**：核心不持一个 global capacity，也不凭 ItemDescriptor 结构相等决定 merge。每次操作由 backend 按其规则处理。
- **server current state 权威**：服务端以当前 snapshot 和当前玩家库存重读 intent；stale key 返回 no-change/latest snapshot，不按列表 index 操作，不自动 retry。
- **semantic intent**：host 把 raw button/keycode/坐标转换为有限的 `LongContainerIntent`；intent 不携带客户端 itemMax、最终 requested amount 或 clicked key 的 deposit。
- **experimental 兼容等级**：新包名隔离，允许 P1-P3 在源码证据和消费方反馈后修订；不修改旧 `McScreenBridge`、`UiSurface`、`InventorySlotSnapshot`、`SlotContentSnapshot`。
- **#66 sibling host**：未来 `GuiContainer` host 与旧 `McScreenBridge`/`UiSurface` 并列；通过同步只读 claim 和 phase hook 取得唯一输入 owner 与 PaintPlan 分相，不复制 vanilla draw loop、不反射/复制 vanilla 私有状态、不 double-dispatch。

## 选择原因

该切分守住 I1-I13：UI 仍是 confirmed state 的纯投影，动态列表只在 EntryKey 范围 keyed reconcile，handler 只上抛 signal/intent，PaintPlan 仍是唯一 replay 合同，MC 类型止于 host 边界。long 数量只存在于纯数据与 backend 端，避免把 `ItemStack` 的 int 限制伪装成通用能力。

用户已确认的正常路径可以由服务端主线程上的直接操作闭环，因此首版不建设 WAL、journal、escrow、retry ledger、revision CAS 或跨重启一致性机制。边际故障记录为残余风险，待真实反馈再开补丁任务。

## 影响范围

- 新增根包建议为 `club.heiqi.uilib.ui.container.experimental`，按 `model`、`storage`、`operation`、`presentation`、`scene`、`minecraft` 分层。
- P1 只新增纯数据契约；P2 新增 scene wrapper 及测试；P3 新增 MC codec/controller 与 #66 sibling host 及消费方/CI 矩阵。
- 既有 LTS API、版本、依赖坐标、wire protocol、Qz-Miner 和 Qz-Storage 不在本次设计写集内。
- `itemMax > 127` 不由 UILib 加 cap；原版网络/NBT 路径的兼容性是已知残余风险。

## 非目标与风险投入边界

本设计不实现存储 backend、网络协议、scene 控件、Minecraft codec、GuiContainer host 或下游接入；不承诺断电/进程崩溃一致性。正常路径以服务端权威、单容器最多一个在途变更请求和真实 confirmed carried 守恒为边界。未知第三方越界、极端协议值和进程崩溃不在首版预建 production 机制内。

## 演进

- 后续实施任务可在读取真实源码、MC mappings、CI 或消费方反馈后修订 experimental spec；修订必须保留 Entry≠Slot、纯数据/MC 边界和 server current state 权威，且不得默改稳定 API。
