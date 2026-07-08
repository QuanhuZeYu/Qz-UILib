# 决策：Computed 派生内部传播 × I2 与 revisionSignal 全局 bump × I8 裁决

## 背景

P2-1 reviewer（`ses_0c50ad553ffe`）指出 `Computed.cell.applyAndNotify` 与 I2 绝对措辞存在张力；同期 U3 范围评估触出 `DraftSignalAdapter.revisionSignal` 全局 bump 与 I8 / 反模式「万能脏标记」精神一致的可能张力。两件事均挂账 `docs/诊断层/scene技术债.md:69-73` 与 `docs/反馈层/交接.md` §2，长期挂账累计成本已超一次裁决。本会话用户拍板：I2 选 A 修措辞正名派生内部传播；revisionSignal × I8 选 ④ 判非违反 + 本决策文档沉淀。

## 候选方案

### Computed × I2
- A 修 I2 措辞，正名派生内部传播（最终选择）
- B 在《偏离登记》登记一次预存隐性例外
- C 认定内在机制、不改不登记

### revisionSignal × I8
- ① U3 修掉 revisionSignal，改 DraftBuffer 内部状态精确驱动 Computed
- ② 登记 NORTH_STAR 偏离后保留
- ③ 补一条 reactive 层不变量
- ④ 判非违反 + 本决策文档沉淀（最终选择）

## 最终选择

### I2：修措辞正名派生内部传播（A）

I2 原措辞「**所有** signal 写入都经过中央事务，**没有任何**『绕过调度器直接生效』的写入」是绝对措辞。`Computed` 记忆化重算时 `cell.applyAndNotify`（`Signal.java:76-81`）直接写 `cell.value` 并 `markDirty` 订阅者，**不经过** `queueWrite` / `pendingWrites` / `TransactionLog`。字面看踩了 I2。

但机制上：①触发时机受调度器支配（`Computed.recompute` 是 Effect，跑在 flush 双通道 `runDirtyEffectsOneSweep` @`ReactiveScheduler.java:189-198` 内 + `MAX_FLUSH_PASSES` 不动点收敛 + 记忆化去重），非外部任意时机；②写入方向只读（`cell` 是 `private final` @`Computed.java:27`，`Computed` 不暴露 set，外部只能改源 signal 走 `Signal.set→queueWrite` 中央事务）；③可追溯性由源回放兑现（`ReactiveScheduler.java:111` 已注释「Computed 派生值不入日志，由源 signal 重放后自动重算」）。

I2 的精神是防外部写入散养（反模式「状态散养」@NORTH_STAR line 206），派生内部传播不在精神射程内。故修订为正名而非放松。修订后评审者按 I2 checklist 核对不再每次重复踩同一悬空点。是否决 B/C 的理由：

- 否 B：偏离登记为「待回填的活跃债」设计（NORTH_STAR line 269「登记只承载尚未回填的活跃偏离」，还清即移除）。Computed 是核心机制、永久存在、且正确，登记为偏离等于宣告「永远还不清的债」，名实不符，污染偏离登记语义。
- 否 C：靠代码注释 + 口头共识维持，宪章正文与代码字面冲突长期悬空，每次评审重复论证，累计成本已超一次修订。

最终措辞见 `NORTH_STAR.md` I2 条目（line 159 区）。

### revisionSignal 全局 bump × I8：判非违反 + 沉淀（④）

`DraftSignalAdapter.revisionSignal` 全局 bump（`DraftSignalAdapter.java:363-366`）让六处变更操作（`onFieldEdit`@272 / `seedFieldBaseline`@303 / `resetToCurrent`@318 / `resetFieldToDefault`@338 / `afterSaveSync`@348）任一都强制六类 Computed（订阅 revision @99/@109/@118/@125/@137/@150）全量重算。字面看与 I8 / 反模式「万能脏标记」精神一致（过度失效）。

但判非违反，四点决定性论证：

1. **I8 字面不管 reactive**：I8（`NORTH_STAR.md:168`）约束「布局结果、Display List 片段、合成层纹理」的缓存复用，reactive 层 Computed 记忆化不在其字面范围。
2. **危害被记忆化挡下游**：「万能脏标记」实质危害是「全量重排、性能假装优化」。revisionSignal bump 让六类 recompute Effect **重跑派生函数**，但 `Computed` 记忆化（`Computed.java:37` `!Objects.equals(next, cell.peek())` 才 `applyAndNotify`）使值不变时**不向下游 UI effect 传播** —— 渲染层零额外失效，不触发任何 LAYOUT/PAINT/COMPOSITE。危害被截在「重算派生函数」这一 CPU 层，未触达渲染分级失效层。
3. **代价可忽略**：config 字段数量级为几十，全量重算 = 几十次 `Objects.equals` + 一次 `validateAll`（`DraftBuffer.java:163`），非热路径、非每帧。
4. **精确化代价高且破国策**：①修掉它需 DraftBuffer 持订阅机制，违反 `docs/诊断层/Config模块.md:13`「config 核心层零 uilib 硬依赖」国策（这是 config 可独立运行的既定设计）；②收益虚——记忆化已挡下游传播，精确化省不下任何渲染成本（决策检查清单 §8 第 8 条「新增内存/机制换来哪一层跳过重算」答不上来）。

是否决其他三条的理由：

- 否 ①：破零 uilib 依赖国策 + 推翻 U2 已定 ConfigScreen 深化余地最小结论，收益虚代价实。
- 否 ②：同 I2-B 理由，名实不符，这是正确工程权衡不是待回填债。
- 否 ③：升格为不变量属重量级操作，当前 reactive 派生规模小、非热路径，补不变量属过度设计（YAGNI）。**保留升级路径**：未来 reactive 派生规模变大、变热路径，再评估升格 ③——这正是 AGENTS「错误上溯第 2 次出现才上溯补不变量」的精神。

## 影响范围

- `NORTH_STAR.md` I2 措辞已修订（本会话提交）。
- `docs/诊断层/scene技术债.md:69-73` 的 Computed × I2 张力挂账可销；本会话同步从交接 §2 移除 `Computed-I2隐性例外裁决` item。
- U3 范围裁决：oracle 推荐档 A「只收敛 renderer 样板，不动 Adapter/Computed 机制」——revisionSignal 不动，I8 张力保持现状即可。**U3 档 A 不再被 I2/I8 阻塞。**
- 反模式「万能脏标记」@NORTH_STAR line 204 不动，但其适用边界由本决策明确：约束渲染分级失效层，不约束 reactive 派生副作用层的「重算派生函数」型失效。
- 后续若 reactive 派生规模变大、变热路径，触升级评估即重新打开本决策文档追加「演进」段。

## 演进

- 2026-07-08：初次裁决。I2 选 A 修措辞正名派生内部传播；revisionSignal × I8 选 ④ 判非违反 + 本决策文档沉淀。触发原因：U3 范围评估触出前置阻塞，长期挂账成本超一次裁决。