# 字体高频诊断日志误用 INFO 级导致日志爆炸

- **日期**：2026-06-26
- **影响**：单次客户端运行 `fml-client-latest.log` 膨胀至 272MB / 164 万行，几乎全为 `qz_uilib` 每帧刷屏日志

## 错误现象

用户反馈"噪音爆炸"，最近一次 client 运行日志异常庞大：

| 文件 | 大小 | 行数 |
|---|---|---|
| `run/client/logs/fml-client-latest.log` | 272 MB | 1,649,782 |
| `run/client/logs/fml-junk-earlystartup.log` | 80 MB | — |

`latest.log`（7KB，正常）与 `fml-client-latest.log`（272MB，异常）体积相差 4 万倍。后者尾部连续数千行均为两类 `qz_uilib` INFO 日志：

```
[Client thread/INFO] [qz_uilib/]: 提交字体批次：pageBatches=1 drawCalls=1 textureBinds=1 quadCount=20 internalMatrices=false
[Client thread/INFO] [qz_uilib/]: 字体运行统计[render_tick]: pendingUploads=0, readyGlyphs=224, ...
```

## 触发场景

配置项 `fontRuntimeDebug` 被设为 `true`（本意是"看字体调试信息"），随后正常进入游戏运行，渲染循环每帧触发：

1. `FontBatchRenderer.flush()` 每次 flush（每帧多次）打印"提交字体批次"。
2. `FontService.debugLogStats("render_tick")` 每 render tick 打印"字体运行统计"。

## 根本原因

**高频 per-frame 诊断日志误用 `INFO` 级，且绕过既有节流机制。**

1. **级别错配**：方法名 `debugLogStats` 本质是调试日志，却用 `MyMod.LOG.info(...)` 输出。log4j2 默认 root=INFO 阈值挡不住 INFO，开关一开即每帧刷屏。
2. **绕过节流**：项目已有 `FontRuntimeDiagnostics` 专门负责字体诊断节流（字形生成/上传/绘制 GL 状态分别有 16/16/32 条上限 + 计数器）。但这两处 per-frame 日志**直接调用 `MyMod.LOG.info`**，完全没走 `FontRuntimeDiagnostics`，导致没有任何采样或上限。
3. **开关语义被破坏**：`fontRuntimeDebug` 配置注释明确写"默认关闭，避免淹没其他 debug 输出"，但仅靠该布尔开关守卫的 INFO 日志一旦打开就会淹没一切——开关名暗示"调试"，实际却是"每帧 INFO 爆炸"。

## 修复方案

三重守卫（开关 + 级别 + 时间窗口采样），改动 3 文件：

### 1. `FontRuntimeDiagnostics.java`（新增采样入口）

新增两个采样方法，集中管理 per-frame 高频日志节流：

- `shouldLogRenderTickStats()`：`fontRuntimeDebug` 开关 + `LOG.isDebugEnabled()` + 1 秒时间窗口（CAS 线程安全）。
- `shouldLogFlushBatchStats()`：同上，独立计数器。

时间窗口用 `AtomicLong` + `compareAndSet` 实现，无锁线程安全，间隔 1000ms。

### 2. `FontService.java:531`（`debugLogStats`）

```java
// 修复前
if (!Config.fontRuntimeDebug) return;
MyMod.LOG.info("字体运行统计[{}]: {}", source, getRuntimeStats());

// 修复后
if (!FontRuntimeDiagnostics.shouldLogRenderTickStats()) return;
MyMod.LOG.debug("字体运行统计[{}]: {}", source, getRuntimeStats());
```

### 3. `FontBatchRenderer.java:386`（flush 批次日志）

```java
// 修复前
if (flushedQuadCount > 0 && Config.fontRuntimeDebug) {
    MyMod.LOG.info("提交字体批次：...");
}

// 修复后
if (flushedQuadCount > 0 && FontRuntimeDiagnostics.shouldLogFlushBatchStats()) {
    MyMod.LOG.debug("提交字体批次：...");
}
```

## 修复后行为

| 场景 | 修复前 | 修复后 |
|---|---|---|
| `fontRuntimeDebug=false`（默认） | 不打印 | 不打印 |
| `fontRuntimeDebug=true`，root=INFO | **每帧 INFO 刷屏**（272MB） | 不打印（DEBUG 被 INFO 阈值挡住） |
| `fontRuntimeDebug=true`，qz_uilib=DEBUG | 每帧 INFO 刷屏 | 每秒最多 1 条 DEBUG |

## 预防措施

1. **per-frame / 高频热路径日志禁止用 INFO 级**：任何在渲染循环、tick 循环、事件回调内可能每帧触发的日志，必须用 `DEBUG` 或 `TRACE` 级。方法名含 `debug` 字样的更不能 INFO。
2. **诊断日志必须走统一节流入口**：项目已有 `FontRuntimeDiagnostics`，新增诊断日志一律挂到该类，复用计数器/采样机制，禁止散落各处直接 `MyMod.LOG.info`。
3. **开关命名与语义一致**：`fontRuntimeDebug` 这类布尔开关守卫的日志，级别应与开关名暗示的"debug"一致，不能用 INFO 绕过 log4j2 阈值。
4. **时间窗口采样优于前 N 条上限**：对周期性运行统计（如 render_tick），"前 N 条"节流会丢失后续时段信息；时间窗口采样（每秒 1 条）能持续反映状态又不刷屏。
5. **日志体积自检**：开发期若发现 `fml-client-latest.log` 超过 10MB，应立即排查是否有 per-frame 日志泄漏，而非习以为常。
