# GlyphGenerationDispatcherReloadBarrierTest 偶发失败(flaky)

## 日期
2026-08-23

## 现象
全量 build 偶发失败(3374 测试 1 failed):
```
GlyphGenerationDispatcherReloadBarrierTest > reloadBarrierWaitsForConcurrentQueueSelection FAILED
    java.lang.AssertionError at GlyphGenerationDispatcherReloadBarrierTest.java:418
```
同一提交未做任何改动,重跑 build 即通过(19 up-to-date + test 绿)。

## 排查结论
- 测试涉及字体 glyph 生成队列的并发/时序等待,断言依赖线程调度时序,属既有 flaky,与本次聊天 3.0 / 原生分辨率改造无关(改动不触字体管线)。
- 处理方式:重跑确认绿即可;若连续两次同点失败再深入定位。

## 提示
遇到该测试失败先重跑一次,不要按真实回归处理;若伴生其他字体测试失败才需排查。
