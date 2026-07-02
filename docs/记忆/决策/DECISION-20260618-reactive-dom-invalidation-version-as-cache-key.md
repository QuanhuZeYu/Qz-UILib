# 决策：写侧标脏由属性自动推导，读侧版本号比对作为 I8 缓存实现保留

## 背景

NORTH_STAR 宪章第 4 节架构图行 9 描述 reactive→DOM 失效接入时写道：用 effect 标记**替代**现有 `layoutVersion/paintVersion` 命令式版本号模型。

2026-06-18 对 reactive→DOM 失效层接入做系统性审查（历史审查报告已清除），oracle 指出该表述存在**语义解释边界**问题，若按字面"删除一切版本号"理解会误删 I8 缓存实现。本决策固化对行 9 的精确解读，避免后续会话误判。

## 问题：版本号有两个不同侧面

宪章行 9 的"版本号模型"实际混合了两个独立概念：

1. **版本号 bump（写侧）**：`recordLayoutMutation` / `recordPaintMutation` 等——回答"谁标脏、标哪一级"。这是**命令式心智**：控件代码手动调 `recordXxxMutation` 声明"我改了，请重算"。
2. **版本号比对（读侧）**：消费端 `cachedLayoutVersion != layoutVersion → 重算`（`HtmlLikeDocumentWidget` resolveLayoutBox 等）——回答"缓存是否命中"。这是 **I8（按脏标记复用缓存）的合法实现机制**。

## 候选方案

1. **字面理解**：删除所有版本号字段与比对，effect 直接驱动重算。
2. **精确解读（本决策）**：行 9 要替代的是**写侧命令式 bump 心智**（控件手动 record），不是读侧版本比对；读侧版本号作为 I8 缓存键保留。

## 最终选择

**采纳方案 2。** 明确：

- 行 9「effect 标记替代命令式版本号模型」指替代**写侧命令式 bump**——属性写入的标脏改由属性自身（`StyleDeclarationSlot` / `updateProperty` 的 impact 标注）自动精确推导，控件/桥接层不再手动声明失效级别。
- **读侧版本号比对作为 I8「按脏标记复用缓存」的实现机制保留，不视为对行 9 的违背。** 版本号在收敛后退化为"自动维护的缓存比对量"——不再是控件需要手动维护的命令式状态，正好兑现行 9「替代命令式」的本意（命令式 bump 消失，版本号变成纯读侧缓存键）。

## 选择原因

- **读侧版本比对是 I8 的正确实现**：缓存命中判断需要一个"自上次缓存以来是否变脏"的标量，版本号正是这个标量的合法载体。删除它等于删除 I8 缓存能力，与宪章自相矛盾。
- **真正要根除的是写侧命令式心智**：信条二要求"更新粒度=单个属性"，控件不应手动调 `recordXxxMutation` 声明"我改了整个布局"。属性 setter 自带的 `UiStyleChangeListener` 自动链路已能按属性精确分级标脏（width→LAYOUT、bg→PAINT、transform→COMPOSITE），写侧命令式 bump 是冗余且易错的。
- **2026-06-18 P0 还债已据此落地**：删除 `UiComponentRuntime.createEffect/bind` 的手传 `impact` 参数与末尾全局 `markXxxDirty`，标脏完全交还属性 setter 自动链路；版本号比对保留为读侧缓存键。该修复同时根除了"桥接层全局二次标脏导致 root 整树失效"的 P0 性能 bug（违反 I4/I7/信条二）。

## 影响范围

- 后续 reactive→DOM 接入工作以本解读为准：写侧不引入新的命令式 bump 调用方，新增视觉写入路径应让 setter 自带节点级精确标脏。
- 读侧版本号比对（`HtmlLikeDocumentWidget` 的 resolveLayoutBox / resolvePaintLayoutBox / resolvePaintCommands）保留，属 I8 合法实现，不在"替代版本号"清理范围内。
- 唯一需保留的写侧手动标脏口子：若未来出现"effect body 改了视觉状态、却走了某条无自动标脏的写入路径"，正确做法是让该 setter 自带节点级标脏，**不得**靠桥接层全局 bump 兜底。当前代码库不存在此类路径。

## 后续注意事项

- 本决策触及宪章不变量解释边界，已经用户确认。
- 若后续认为应在宪章正文补一句对行 9 的澄清注记，属信条/不变量级改动，须再次经用户确认（AGENTS 规范第 0 节）。
