# 任务：字体异步核心与信号式重载

## 元数据

- ID：`20260801-font-async-core-signal-reload`
- 状态：`ACTIVE`
- Owner：OpenCode 内置 `build`
- 创建日期：`2026-08-01`
- 更新日期：`2026-08-03`
- 基线分支：`feat/ui-projection-material-item`
- 基线 HEAD：`c73426fe943d0876b7c0e262e14a1e227e4b002c`
- 上游依赖：无
- 下游影响：Qz-Miner 只消费 UILib；本任务不修改 Miner

## 用户目标

- 把异步字体管线作为字体引擎的核心架构，而不是附属线程池。
- 把 reload 改成信号式 desired-state reconciliation，防止客户端误触、资源包反复 reload 和高频竞态。
- 先写完整施工图，再按可验证纵切开始实施。

## 北极星

1. 渲染线程不等待 glyph raster worker；异步只影响像素何时可见，不改变同代布局 advance。
2. 外部调用只声明“字体运行时需要与最新 desired state 对齐”，不得命令式清 worker、atlas 或 GL。
3. 当前 frame 只观察一个字体 generation；旧任务、旧 upload 和旧测量结果不能写入新代。
4. 每个已接纳 glyph 请求必须收敛到 ready、no-bitmap、failed、cancelled/stale 或显式 retry，不得无 owner 悬挂。
5. demand queue、raster result bytes、atlas residency 与每帧 upload 都必须有界；可见需求优先但不能让后台请求永久饥饿。
6. GL 只由 render-thread owner 创建、提交和退休；worker 只产出不可变 CPU 结果。

## 已确认现状

- `FontService.reload(...)` 当前可能同步完整 reload、进入 `FontReloadDebouncer`，或在非允许线程被丢弃；调用合同不稳定。
- pending reload 可由 `tickMainThread()` 或任意 draw 的 `tickDrawStage()` 推进，允许同一 deferred batch 中途跨代。
- `FontReloadDebouncer` 采用 150ms quiet + 750ms 强制 max delay；资源 reload 风暴中仍可能被强制执行。
- Splash resource reload 当前在 signal 前直接 return，desired intent 会丢失。
- 配置 coordinator 已先在 CLIENT queue latest-wins 回灌 Authority，字体层再做第二层 request debounce。
- glyph worker 未捕获异常可留下无 in-flight 的 `GENERATING`；upload 出队后异常可留下无 queue record 的 `UPLOAD_PENDING`。
- `runtimeVersion`、dispatcher epoch、generation id 与 text measure epoch 分散存在，尚无原子 generation envelope。
- `GlyphRuntimeTables` 每代固定覆盖全 Unicode；双代原子切换前必须考虑瞬时内存，不能直接复制完整表作为首切。
- 任务开始前已有两份用户代码改动，必须保留且不暂存：
  - `src/main/java/club/heiqi/config/DefaultMutableConfig.java`
  - `src/test/java/club/heiqi/config/runtime/ConfigRawOverlayTest.java`

## 总体施工图

### Phase A：信号式 reload 控制平面

目标：先让所有 reload intent durable、latest-wins、single-flight，并把完整 reload 固定到 render frame 边界。

设计：

- 保留公共 `FontService.reload(FontReloadRequest)` 签名；语义改为任意线程只发布 signal，不初始化、不释放 GL、不同步完整 reload。
- 新增 package-private `FontReloadSignal`，维护 monotonic desired/applied sequence、最新 reason、合并数量、最后 signal 单调时间、in-flight ticket 与失败 backoff。
- signal 为 level-triggered：`desiredSequence > appliedSequence` 表示尚未收敛；重复事件只更新最新 desired，不形成命令队列。
- 采用 `System.nanoTime()` 对应的可注入单调时钟；普通 signal 需经历 quiet stability window，不再设置风暴中的强制 max delay。
- `tickMainThread()` 是唯一 reconcile owner：每个 RenderTick END 最多领取一个 ticket；成功才 acknowledge，失败保留 dirty 并 backoff。
- reconcile 期间到达的新 signal 不被本 ticket acknowledge，留给后续 frame。
- `tickDrawStage()` 只处理受预算约束的 upload，绝不推进 generation。
- resource callback 始终发布 signal；Splash 活跃时只延迟 reconcile，不丢 signal。
- shutdown 清理/封锁旧 signal lifecycle，重新 initialize 不得消费关停前的陈旧 intent。
- reason 只用于诊断，不决定控制流。

首切验收：

- 任意线程 signal 不执行 action，下一安全 render tick 才 reconcile。
- 高频 signal 在持续抖动期间不 reload，稳定后只执行最新合并结果。
- action 中到达新 signal 时，第一轮只 acknowledge 捕获 sequence，下一 frame 再执行。
- action 失败不 acknowledge，owner 必释放，backoff 后可重试。
- 并发 poll/reconcile 最多一个 in-flight；每 tick 最多一轮，无自旋。
- draw stage 不再执行 reload；Splash request 不丢失。

### Phase B：不可变 generation substrate

目标：把分散的 runtimeVersion 副本收口到一个活动代际 envelope，同时不立即复制双份 123MiB direct tables。

设计：

- 引入 `FontRuntimeSettings`，在 signal/reconcile 边界冻结影响匹配、metrics 和 raster 的配置；worker 不再读取 live `FontConfig`。
- 引入 `ActiveFontGeneration`，统一持有 generation version、settings、catalog snapshot、runtime tables 和 lifecycle。
- frame 开始时取得 generation snapshot；layout/paint/draw 在该 frame 内不换代。
- 首版 candidate 仍可在 render barrier 内串行构建，但必须先构造可验证 CPU 状态，再发布 active envelope。
- candidate 构建失败保留旧 active generation；成功后再退休旧资源。
- 后续配合稀疏/分块 glyph tables，才允许 active + retiring 双代长时间共存。

### Phase C：token 化 glyph 状态机

目标：单一 ledger 管理 request、worker、upload 和最终 residency，不再让 queue 与 primitive state 分裂。

设计：

- `GlyphRequestToken = generation + requestId + codepoint + FontType`，由 manager 在一次 claim 中原子返回。
- task/result/upload 共用不可变 token；删除 task 上的二阶段 `assignGenerationId()`。
- `queueUpload/fail/cancel/commitReady` 都比较完整 token 与 expected state。
- worker wrapper 捕获 matcher/generator/result handler 的所有未检查异常并 settle 当前 token。
- stale token 只能被拒绝，不能 markFailed/cancel 新请求；in-flight 移除比较 exact handle。
- upload dequeue 的成功、异常和 stale 都必须消费预算并进入明确状态。

状态目标：

```text
ABSENT -> QUEUED -> RASTERIZING -> UPLOAD_QUEUED -> UPLOADING -> RESIDENT
                    |                 |                |
                    +-> FAILED        +-> CANCELLED     +-> FAILED/RETRY

旁路终态：NO_BITMAP、FAILED_TERMINAL、CANCELLED_STALE
```

### Phase D：有界 demand scheduler

目标：让异步优势体现为可预测 time-to-visible，而不是无界 FIFO 后台任务。

设计：

- demand 级别：`VISIBLE`、`FOREGROUND`、`PREFETCH`、`WARMUP`。
- 同 token/key 重复提交只做 priority promotion；使用 aging 防止饥饿。
- CPU queue 按请求数有界，raster result mailbox 按 bitmap bytes 有界，为可见需求保留 admission。
- 明确单 worker 为默认策略；只有 P95 latency 与 CPU 数据证明收益后才扩并发。
- scene/HUD 在 replay 前批量发布整段文本 demand；vanilla adapter 在 draw 前先提交整串 missing glyph。

### Phase E：事务化 render upload 与 residency

目标：atlas slot、GL upload 和 ready metadata 成为可恢复提交，而不是出队后半成功。

设计：

- worker 只产出不可变 `GlyphUploadPlan`。
- render owner 按时间、bytes、attempt count 三重预算 drain。
- slot reservation、texture upload、metadata publish 分阶段；只有 GL 成功才发布 resident handle。
- atlas pressure 是独立状态，不冒充 glyph missing/failed。
- 若引入 eviction，atlas handle 必带 residency generation，冷页退休后需求可重新物化。

### Phase F：异步 generation 构建与 frame lease

目标：把字体发现、catalog 规划和 CPU candidate 构建搬离 render thread，并安全延迟退休旧资源。

设计：

- 后台 candidate 只读取 settings/resource snapshot，不读取 Minecraft/Forge/GL live 对象。
- commit ticket 携带 desired sequence、font generation 和 resource fingerprint；过期 candidate 直接丢弃。
- layout/paint/batch 持 generation lease；旧代只在 lease 归零后由 render thread 删除 GL。
- generation 数量有硬上限，正常只允许 active + retiring。

## Reload 信号状态机

```text
STABLE
  -> DIRTY
  -> WAITING_STABLE
  -> IN_FLIGHT(ticket.sequence)
  -> SUCCESS: applied = ticket.sequence
  -> STABLE 或 DIRTY（flight 中又有 signal）

IN_FLIGHT
  -> FAILURE: applied 不推进，进入 BACKOFF
  -> RETRY_READY 后重新领取最新 desired

SHUTDOWN
  -> 清空 pending/in-flight lifecycle
  -> 新 lifecycle initialize 后从干净 applied/desired 起点工作
```

## 线程与所有权

| 参与者 | 允许 | 禁止 |
|---|---|---|
| config/resource/外部线程 | 发布 immutable reload signal | initialize、worker reset、atlas/GL 操作 |
| render tick owner | reconcile、generation commit、upload、GL retire | 同 tick 自旋追赶持续 signal |
| draw path | 读取 frame generation、提交 demand、受预算 upload | reload/generation commit |
| glyph worker | AWT raster、发布 immutable result | GL、active generation mutation、读取 live config |
| shutdown hook | 停止 worker、封锁 lifecycle | 无 context 时删除 GL |

## 公共兼容策略

- Phase A 保留 `FontService.reload(FontReloadRequest)`、`FontReloadRequest(String)` 与 `getReason()`，避免下游链接破坏。
- `reload()` 返回前不再保证 runtime 已更新；这是 5.0 分支的异步语义收口，文档必须明确。
- 不在首切新增等待型 completion API；render path 和外部调用方都不得等待 reload。
- 不修改 YAML 持久格式、网络协议、版本或 Qz-Miner 接口。

## 首纵切写集

- `.opencode/tasks/INDEX.md`
- `.opencode/tasks/20260801-font-async-core-signal-reload.md`
- `src/main/java/club/heiqi/uilib/font/FontService.java`
- `src/main/java/club/heiqi/uilib/font/FontReloadSignal.java`（新增）
- `src/main/java/club/heiqi/uilib/font/FontReloadDebouncer.java`（删除）
- `src/main/java/club/heiqi/uilib/font/FontSplashReloadGuard.java`
- `src/main/java/club/heiqi/uilib/client/FontRenderTickListener.java`
- `src/main/java/club/heiqi/uilib/mixin/early/MixinFontRenderer.java`
- `src/test/java/club/heiqi/uilib/font/FontReloadSignalTest.java`（新增）
- `src/test/java/club/heiqi/uilib/font/FontReloadDebouncerTest.java`（删除）
- `src/test/java/club/heiqi/uilib/font/FontServiceLayoutRuntimeSmokeTest.java`
- `docs/诊断层/字体引擎代码地图.md`
- `docs/反馈层/错误预防.md`
- `docs/反馈层/交接.md`（只在首切完成后写业务状态）

若实现中必须修改范围外文件，先把原因和新验收写回本任务笔记，不静默扩张。

## 首纵切非目标

- 不在同一提交完成 generation envelope、glyph token、优先级队列、稀疏表、atlas eviction 或异步 scene layout。
- 不修改当前 glyph raster/quad/shader 视觉语义。
- 不把真实 GL、Splash、resource pack 或 dedicated server 运行态伪装为 JUnit/build 已证明。
- 不触碰任务开始前两份用户配置代码改动。

## 验证矩阵

### 纯 JVM

- signal：单调 sequence、latest reason、合并计数、quiet 边界、无 max-delay 强制执行。
- single-flight：并发 poll 只有一个 ticket；错误/AssertionError 后 owner 释放。
- handoff：signal-before-poll、signal-during-flight、signal-before-ack 均最终收敛。
- failure：不 acknowledge，backoff 前不重试，到期后领取最新 desired。
- lifecycle：reset 后旧 ticket 不能 acknowledge 新 lifecycle。

### FontService 无 GL 合同

- worker 调 `reload()` 只 signal，不初始化、不改 runtime version。
- `tickDrawStage()` 不 reconcile。
- 非 render shutdown 跳过 GL 资源释放。
- pending signal 与 shutdown/reinitialize 不串 lifecycle。

### 构建与运行态

- 本机执行 `call gradlew.bat --no-configuration-cache build`，覆盖编译、checkstyle、JUnit、classpath isolation 与 assemble。
- 不自动执行 `runClient*` / `runServer*`。
- F3+T、资源包连续切换、Splash 与真实 GL reload 保持 `INCOMPLETE`，交用户/CI 运行态验证。

## 风险与控制

- reload 从“部分同步”变为“统一异步 signal”：保留签名并更新文档，配置 coordinator 不依赖同步完成。
- quiet-only 策略在持续风暴中会一直保留旧 generation：这是防抖目标；旧代继续服务，风暴停止后收敛最新 desired。
- 首切仍由 `commitReloadLocked` 原地重建运行时，异常不具备事务 rollback：失败 signal 不丢，但部分内部推进风险留到 Phase B。
- `FontConfig.onConfigReload()` 当前在 signal 后立即推进配置快照；Phase A 只保证 intent durable，不宣称 active runtime 与 static snapshot 原子一致。
- render tick 首次线程捕获仍需 fail-closed；不得让测试或任意线程抢占 production render owner。

## 进度与证据

- 已读取 `AGENTS.md`、`NORTH_STAR.md`、文档导航、交接、错误预防和字体引擎代码地图。
- 已核对实时 Git：基线 HEAD `c73426fe`；两份任务外用户改动保持未暂存。
- 已完成 reload 调用链、tick/Splash/config 入口和 glyph 状态竞态的两路只读调查。
- 已完成 Phase A：`FontReloadSignal` 以 desired/applied sequence、quiet-only stability、single-flight ticket、
  flight handoff、失败指数 backoff 和 lifecycle gate 取代 `FontReloadDebouncer`。
- `FontService.reload()` 已改为任意线程只 signal；完整 commit 固定到 `RenderTick.END`，draw-stage 只允许已绑定
  render owner 上传；Splash 只延后 reconcile，不丢 resource intent。
- 已定义 `runtimeVersion` publish 为 Phase A commit point；commit 前 `RuntimeException` 保留 signal 并退避，commit 后
  demand 恢复/主动布局失效为 best-effort，不再反向重做整代。
- 已新增确定性 signal 状态机、并发 owner、flight handoff、失败退避、旧 lifecycle ticket 和 worker/draw 隔离测试。
- `2026-08-01` 执行 `call gradlew.bat --no-configuration-cache build`：`BUILD SUCCESSFUL`，编译、checkstyle、
  JUnit、classpath isolation、assemble 全部通过。
- Phase A 已提交为 `01c96839 [Refactor]: 引入字体重载信号核心`。
- 已完成 Phase B：`FontRuntimeSettings` 冻结 matcher/metrics/raster/page sampling 所需配置；CPU-only candidate
  在破坏旧代前完成字体发现、排序、catalog snapshot 与稳定 line metrics 构建。
- `ActiveFontGeneration` 已统一发布 runtime version、text measure epoch、settings、catalog、metrics、唯一
  `GlyphRuntimeTables` storage 与 lifecycle；`FontService` 不再分别发布 version/epoch 真值。
- 完整 Unicode direct tables 只在 `GlyphPageManager` 构造时分配一次；换代通过 generation RW barrier 原地清理并
  转移 storage，不建立 active/candidate 双表。layout 整次调用持 read scope，worker 只在 matcher cache 最终写入时
  短持 read lock 并二次核验 binding/lifecycle，render owner 持 service monitor。
- matcher/layout/worker/upload/page/draw 的 generation-sensitive 读取已迁移到 immutable settings/catalog；glyph upload
  不再改写 line metrics，异步完成只改变像素 residency，不改变同代 advance/line height。
- candidate 失败保持旧 active identity、lifecycle 和 table；单页 GL retire `RuntimeException` 被隔离记录，commit core
  只保留不可恢复 `Error` 风险。catalog candidate 在停止 worker 前验证，pre-commit dispatcher 失败会 best-effort 恢复旧 worker。
- dispatcher 首次 reset 只有在确认 terminated 后才释放 executor owner；超时保留 retiring pool，后续 render tick
  只做非阻塞终止探测，不能重复等待或创建并行替代 worker。singleton manager/matcher/layout/dispatcher 绑定不可伪造
  owner token，公开诊断对象的 generation/storage 写入口 fail-closed；renderer 改用只读 `GlyphRuntimeTablesView`，
  诊断面板可使用 `FontRuntimeDiagnosticsView`。
- candidate runtime version/measure epoch 在 pause/reset 前验证为 active 的严格后继；不可逆 publication core 已移除
  dispatcher 调用，active 发布后先写 durable worker recovery intent，再执行可失败 version binding/init。setter/init 故障
  均由后续 tick 在同一 generation 恢复，不重做 generation 或错误保留 signal。
- 已新增 settings 防御复制、envelope/lifecycle、多码点 layout write barrier、stale matcher publication、candidate
  prepare 后 rollback/无效 successor、table 单 owner/retire failure、bitmap upload metadata/metrics 隔离、executor timeout
  ownership/非阻塞复探测、dispatcher setter/init durable recovery、诊断 getter 写入拒绝与 warmup settings recapture 测试。
- `2026-08-02` 执行 `call gradlew.bat --no-configuration-cache build`：`BUILD SUCCESSFUL`，编译、checkstyle、
  JUnit、classpath isolation 与 assemble 全部通过。
- 已完成 Phase C：`GlyphRequestToken` 以 generation、requestId、codepoint、`FontType` 形成不可变完整身份；manager
  原子 claim 返回 token，task/result/pending upload 保留同一 token，删除二阶段 `assignGenerationId()` 与含混的
  generation-id request 命名。
- glyph ledger 已改为 `ABSENT -> QUEUED -> RASTERIZING -> UPLOAD_QUEUED -> UPLOADING ->`
  `RESIDENT/NO_BITMAP`，失败与 stale 取消均按完整 token + expected state 结算；旧 token 不能修改同码点新请求。
- dispatcher 的 submit/pause/reset admission 已由同一 monitor 线性化，pause 只停止接单，已接纳 worker 由
  generation epoch 决定 current；worker `RuntimeException/Error` 统一结算，in-flight finally 使用 exact task remove。
- upload 的 stale、成功与异常 dequeue 均消费预算；GL 前不发布 residency metadata，异常先进入 `FAILED` 再传播，
  draw-stage 即使异常也进入间隔/每秒限速账本。正常 transition 静默，异常首条日志携 token/stage/state/reason，
  相同 fingerprint 仅输出限频摘要，stale/rejected 只在 `fontRuntimeDebug` 下限量记录。
- 已新增原子 claim、同代/跨代 stale token、token identity、stale upload 预算、discard 结算、no-bitmap、upload
  `RuntimeException/Error` 零 metadata 发布、worker matcher/rasterizer/handler 异常、pause/reset admission、已接纳 worker、
  exact in-flight removal 与 draw-stage 异常限速回归；三轮独立审查最终无 P0/P1/P2。
- `2026-08-02` 在 Phase C 最终修复后再次执行 `call gradlew.bat --no-configuration-cache build`：
  `BUILD SUCCESSFUL`，编译、checkstyle、JUnit、classpath isolation 与 assemble 全部通过。
- 已完成 Phase D：内部 demand 统一为 `VISIBLE/FOREGROUND/PREFETCH/WARMUP`，既有公开
  `HIGH/NORMAL/LOW` 分别映射 `VISIBLE/FOREGROUND/PREFETCH`；reload recovery 使用 `FOREGROUND`。
- dispatcher 固定单 worker，claim 前以 1024 requests 硬上限和 256 `VISIBLE` reserve 做 admission；同 key
  promotion 保留原 token，queued promotion 与 dequeue 共用选择锁，500ms 动态 aging 防止低优先级永久饥饿。
- raster result mailbox 以 256 records 和 16 MiB bitmap bytes 双限额，为 `VISIBLE` 保留 32 records / 4 MiB；
  非 visible 压力在 manager 锁内重验 promotion 后立即结算 `FAILED`，避免阻塞唯一 worker，visible publisher
  可中断等待 render drain；record publication 失败会释放 reservation 并结算 token。
- `ScenePaintReplayer` 在任何命令前通过 additive default `UiRenderBackend.publishTextDemand(...)` 发布整份 plan
  的 raw 文本，`ScaledHudBackend` 原样转发；direct adapter 在受保护 lifecycle initialize 后、draw guard 前冻结
  formatted/raw segments、`§k` 替代码点、style 与 advance，按 `(codepoint, FontType)` 去重 visible demand。
- `FontRuntimeStats` 与限频诊断已覆盖 demand/mailbox capacity、high-water、promotion、wait/reject；新增确定性
  admission、promotion/dequeue、aging、单 worker reserve、bytes、interrupt/reset、整 plan 预发布和 adapter guard
  顺序回归。修复终审最终未发现并发域 P0/P1/P2；scene fake 的真实字体副作用隔离 P1 已修复并由目标测试覆盖。
- `2026-08-03` 执行 `call gradlew.bat --no-configuration-cache build`：`BUILD SUCCESSFUL`，编译、checkstyle、
  全量 JUnit、classpath isolation 与 assemble 全部通过。
- 已完成 Phase E：worker result 与 package-private `GlyphUploadPlan` 均冻结像素；mailbox record/bytes reservation
  覆盖 queued + render in-flight lease，discard 通过 epoch 线性化释放旧 reservation。
- render drain 同时受调用方 attempt、默认 2ms monotonic time 与 2 MiB bitmap bytes 约束；首个超 byte 项允许推进，
  stale record 同样消费 attempt/bytes/time 预算。
- atlas slot 使用可回滚 reservation；texture 初始化、像素写入、mipmap、token/epoch 复核、slot/page/residency metadata
  按提交顺序结算。post-write 或 GL state restore 失败先透明清槽；清理不可信时 quarantine 整页、拒绝 texture view、
  清除该页 residency，并在成功释放后重置 allocator/accounting。
- atlas residency 默认受 8 pages 与 512 MiB 双上限约束，byte accounting 包含完整 mip chain；pressure glyph 在 mailbox
  外进入独立 ledger，释放 mailbox 像素。quarantine 释放容量后解除旧 pressure，使后续 draw 可重新 claim。
- reload recovery 不再一次性越过 non-visible admission：未接纳尾部跨 render tick 保留，连续 reload 会合并尾部，
  pre-commit restore、post-commit worker recovery 与 shutdown 取消边界均有确定性测试。
- Phase E 目标测试、`checkstyleMain checkstyleTest` 与 `git diff --check` 已通过；两轮最终只读复审无 findings。
- `2026-08-03` 执行 `call gradlew.bat --no-configuration-cache build`：`BUILD SUCCESSFUL`，全量 JUnit、checkstyle、
  classpath isolation 与 assemble 全部通过。
- 未运行客户端、服务端、Splash、资源包或真实 GL；相关证据保持 `INCOMPLETE`。

## 唯一下一步

- 开始 Phase F 的异步 generation candidate 与 frame lease 设计；不把 fake GL 测试外推为真实 context 证据。
